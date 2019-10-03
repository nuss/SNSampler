SNSamplePlayer : AbstractSNSampler {
	classvar <all;
	var <name, <bufLength;
	var <>buffers, numBuffers;
	var <server, <loopLengths;
	var <>debug = false;
	var looperName, outName;

	*new { |name=\Looper, bufLength=60, server|
		^super.newCopyArgs(
			name.asSymbol,
			bufLength,
		).init(server);
	}

	init { |server|
		all ?? { all = () };
		all.put(name, this);
		server ?? { server = Server.default };
		looperName = (name ++ \Loops).asSymbol;
		outName = (name ++ \Out).asSymbol;
	}

	setupPlayer { |bufferArray, volumeControlNode=1000|
		if (bufferArray.isNil) {
			Error("An array of consecutive buffers must be provided for setting up a player").throw;
		} {
			numBuffers = bufferArray.size;
		};

		this.buffers = bufferArray;

		loopLengths = bufLength ! numBuffers;
		this.prSetUpControls(volumeControlNode);
		// this.initPdef(bufferArray);
	}

	prSetSpecConstraints { |index, length|
		CVCenter.at((name ++ \Start).asSymbol).spec.maxval[index] = length / bufLength;
		CVCenter.at((name ++ \End).asSymbol).spec.maxval[index] = length / bufLength;
		// "before - index: %, length: %, loopCVs.dur.spec.maxval[index]: %\n".postf(index, length, loopCVs.dur.spec.maxval[index]);
		CVCenter.at((name ++ \Dur).asSymbol).spec.maxval[index] = length;
		// "after - index: %, length: %, loopCVs.dur.spec.maxval[index]: %\n".postf(index, length, loopCVs.dur.spec.maxval[index]);

		if (this.debug) {
			"buffer index: %\ndur maxval: %\n".postf(
				index,
				CVCenter.at((name ++ \Start).asSymbol).spec.maxval,
				CVCenter.at((name ++ \End).asSymbol).spec.maxval,
				CVCenter.at((name ++ \Dur).asSymbol).spec.maxval
			)
		}
	}

	prSetCVValues { |bufIndex|
		var amps, durs, ends;
		amps = CVCenter.at((name ++ \GrainAmp).asSymbol).value;
		amps[bufIndex] = 1;
		CVCenter.at((name ++ \GrainAmp).asSymbol).value_(amps);
		durs = CVCenter.at((name ++ \Dur).asSymbol).value;
		durs[bufIndex] = loopLengths[bufIndex];
		CVCenter.at((name ++ \Dur).asSymbol).value_(durs);
		CVCenter.at((name ++ \End).asSymbol).input_(1);

		if (this.debug) {
			"buffer index: %\ndurs value: %\nends value: %\n".postf(
				bufIndex,
				CVCenter.at((name ++ \Dur).asSymbol).value,
				CVCenter.at((name ++ \End).asSymbol).value
			)
		}
	}

	prSetUpControls { |volumeControl|
		CVCenter.use((name ++ "Start").asSymbol, [0!numBuffers, loopLengths/bufLength], tab: looperName);
		CVCenter.use((name ++ "End").asSymbol, [0!numBuffers, loopLengths/bufLength], loopLengths/bufLength, looperName);
		CVCenter.use((name ++ "Rate").asSymbol, #[-2, 2] ! numBuffers, 1.0, tab: looperName);
		CVCenter.use((name ++ "Atk").asSymbol, #[0.02, 3, \exp] ! numBuffers, tab: looperName);
		CVCenter.use((name ++ "Sust").asSymbol, #[0.1, 1.0] ! numBuffers, 1, looperName);
		CVCenter.use((name ++ "Rel").asSymbol, #[0.02, 3, \exp] ! numBuffers, tab: looperName);
		CVCenter.use((name ++ "Dec").asSymbol, #[0.02, 7, \exp] ! numBuffers, tab: looperName);
		CVCenter.use((name ++ "Curve").asSymbol, #[-4, 4] ! numBuffers, 0, looperName);
		CVCenter.use((name ++ "Dur").asSymbol, [0.1!numBuffers, loopLengths], loopLengths, looperName);
		CVCenter.use((name ++ "GrainAmp").asSymbol, \amp ! numBuffers, tab: looperName);

		this.initPdef(this.buffers);

		Ndef(outName)[0] = {
			Splay.ar(\in.ar(0 ! numBuffers), (name ++ \Spread).asSymbol.kr(0.5), 1, (name ++ \Center).asSymbol.kr(0.0))
		};

		Spec.add((name ++ \Center).asSymbol, \pan);
		Spec.add((name ++ \Amp).asSymbol, \amp);
		Ndef(outName).cvcGui(prefix: outName, excemptArgs: [\in]);

		Ndef(outName) <<> Ndef(looperName);
		Ndef(outName)[volumeControl] = \filter -> { |in|
			in * (name ++ \Amp).asSymbol.kr(1)
		}
	}

	initPdef { |bufferArray|
		var trace = PatternProxy.new;

		bufferArray !? {
			this.buffers = bufferArray;
			numBuffers = this.buffers.size;
		};

		if (this.debug) {
			trace.setSource(
				Pfunc { |e|
					"index: %, bufnum: %, dur: %\n".format(e.channelOffset, e.bufnum, e.dur)
				}.trace
			)
		} {
			trace.setSource(0)
		};

		// CVCenter.at((name ++ \Dur).asSymbol).spec_([0.1!numBuffers, loopLengths].asSpec);
		// CVCenter.at((name ++ \Start).asSymbol).spec_([0!numBuffers, loopLengths/bufLength].asSpec);
		// CVCenter.at((name ++ \End).asSymbol).spec_([0!numBuffers, loopLengths/bufLength, \lin, 0.0, loopLengths/bufLength].asSpec);

		Pdef(looperName,
			Ppar({ |i|
				Pbind(
					\instrument, \grain,
					// buffers in the buffers array may start with a bufnum higher than 0
					// so we check the bufnum by addressing the buffer at index i
					\bufnum, buffers[i].bufnum,
					\start, CVCenter.cvWidgets[(name ++ "Start").asSymbol].split[i],
					\end, CVCenter.cvWidgets[(name ++ "End").asSymbol].split[i],
					\rate, CVCenter.cvWidgets[(name ++ "Rate").asSymbol].split[i],
					\atk, CVCenter.cvWidgets[(name ++ "Atk").asSymbol].split[i],
					\sust, CVCenter.cvWidgets[(name ++ "Sust").asSymbol].split[i],
					\rel, CVCenter.cvWidgets[(name ++ "Rel").asSymbol].split[i],
					\dec, CVCenter.cvWidgets[(name ++ "Dec").asSymbol].split[i],
					\curve, CVCenter.cvWidgets[(name ++ "Curve").asSymbol].split[i],
					\dur, CVCenter.cvWidgets[(name ++ "Dur").asSymbol].split[i],
					\amp, CVCenter.cvWidgets[(name ++ "GrainAmp").asSymbol].split[i],
					\channelOffset, i,
					\trace, trace
				)
			} ! numBuffers)
		);

		Ndef(looperName).mold(numBuffers, \audio, \elastic);
		Ndef(looperName)[0] = Pdef(looperName);
	}

	setLoopMaxLength { |index, length|
		var maxval = CVCenter.cvWidgets[(name ++ \Dur).asSymbol].getSpec.maxval;
		maxval[index] = length;
		CVCenter.cvWidgets[(name ++ \Dur).asSymbol].getSpec.maxval_(maxval);
	}

	play {
		Ndef(outName).play;
	}

	pause {
		Ndef(looperName).pause;
	}

	resume {
		Ndef(looperName).resume;
	}

	clear { |fadeTime=0.2|
		Ndef(outName).clear(fadeTime);
		fork {
			fadeTime.wait;
			Ndef(looperName).clear;
			Pdef(looperName).clear;
			all[name] = nil;
		}
	}


}