SNSamplePlayer : AbstractSNSampler {
	classvar <all;
	var <name, <bufLength, <>mode;
	var <>buffers, numBuffers;
	var <server, <loopLengths;
	var <debug = false;
	var looperName, outName, <looperPlayer;
	var trace;

	*new { |name=\Looper, bufLength=60, mode=\grain, server|
		^super.newCopyArgs(
			name.asSymbol,
			bufLength,
			mode
		).init(server);
	}

	init { |server|
		all ?? { all = () };
		all.put(name, this);
		server ?? { server = Server.default };
		looperName = (name ++ \Loops).asSymbol;
		outName = (name ++ \Out).asSymbol;
		trace = PatternProxy.new;
	}

	debug_ { |bool|
		if (bool) {
			trace.setSource(
				Pfunc { |e|
					"index: %, bufnum: %, dur: %\n".format(e.channelOffset, e.bufnum, e.dur)
				}.trace
			)
		} {
			trace.setSource(0)
		};
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
		// this.prInitPatternPlayer(bufferArray);
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

	prSetUpControls { |volumeControl|
		CVCenter.use((name ++ "Start").asSymbol, [0!numBuffers, loopLengths/bufLength], tab: looperName);
		CVCenter.use((name ++ "End").asSymbol, [0!numBuffers, loopLengths/bufLength], loopLengths/bufLength, looperName);
		CVCenter.use((name ++ "Rate").asSymbol, #[-2, 2] ! numBuffers, 1.0, tab: looperName);
		CVCenter.use((name ++ "Atk").asSymbol, #[0.02, 3, \exp] ! numBuffers, tab: looperName);
		CVCenter.use((name ++ "Sust").asSymbol, #[0.1, 1.0] ! numBuffers, 1, looperName);
		CVCenter.use((name ++ "Rel").asSymbol, #[0.02, 3, \exp] ! numBuffers, tab: looperName);
		// CVCenter.use((name ++ "Dec").asSymbol, #[0.02, 7, \exp] ! numBuffers, tab: looperName);
		CVCenter.use((name ++ "Curve").asSymbol, #[-4, 4] ! numBuffers, 0, looperName);
		// should empty loops loop at minimal duration? Samples will only become audible after current loop has finished.
		// It seems to be impossible to restart loops inbetween ;\
		CVCenter.use((name ++ "Dur").asSymbol, [0.1!numBuffers, loopLengths], 0.1 ! numBuffers, looperName);
		// CVCenter.use((name ++ "Dur").asSymbol, [0.1!numBuffers, loopLengths], loopLengths, looperName);
		CVCenter.use((name ++ "GrainAmp").asSymbol, \amp ! numBuffers, tab: looperName);

		this.prInitPatternPlayer;

		Ndef(outName)[0] = {
			Splay.ar(\in.ar(0 ! numBuffers), (name ++ \Spread).asSymbol.kr(0.5), 1, (name ++ \Center).asSymbol.kr(0.0))
		};

		Spec.add((name ++ \Center).asSymbol, \pan);
		Spec.add((name ++ \Amp).asSymbol, \amp);

		Ndef(outName) <<> Ndef(looperName);
		Ndef(outName)[volumeControl] = \filter -> { |in|
			in * (name ++ \Amp).asSymbol.kr(1)
		};
		Ndef(outName).cvcGui(false, outName, excemptArgs: [\in]);
	}

	prInitPatternPlayer { |bufferArray|
		var source;

		bufferArray !? {
			this.buffers = bufferArray;
			numBuffers = this.buffers.size;
		};

		// CVCenter.at((name ++ \Dur).asSymbol).spec_([0.1!numBuffers, loopLengths].asSpec);
		// CVCenter.at((name ++ \Start).asSymbol).spec_([0!numBuffers, loopLengths/bufLength].asSpec);
		// CVCenter.at((name ++ \End).asSymbol).spec_([0!numBuffers, loopLengths/bufLength, \lin, 0.0, loopLengths/bufLength].asSpec);

		source = this.initDef;

		Ndef(looperName).mold(numBuffers, \audio, \elastic);
		Ndef(looperName)[0] = source;
		Ndef(looperName).pause;
		looperPlayer = Ndef(looperName);
	}

	initDef { |mode, bufferArray|
		var def;

		mode !? { this.mode(mode) };
		bufferArray !? {
			if (bufferArray.size != numBuffers) {
				Error("The number od buffers passed to initDef must equal numBuffers!").throw;
			} {
				this.buffers = bufferArray;
			}
		};

		switch(this.mode,
			\grain, {
				CVCenter.at((name ++ "Atk").asSymbol) ?? {
					CVCenter.use((name ++ "Atk").asSymbol, #[0.02, 3, \exp] ! numBuffers, tab: looperName);
				};
				CVCenter.at((name ++ "Sust").asSymbol) ?? {
					CVCenter.use((name ++ "Sust").asSymbol, #[0.1, 1.0] ! numBuffers, tab: looperName);
				};
				CVCenter.at((name ++ "Rel").asSymbol) ?? {
					CVCenter.use((name ++ "Rel").asSymbol, #[0.02, 3, \exp] ! numBuffers, tab: looperName);
				};

				def = Pdef(looperName,
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
							// \dec, CVCenter.cvWidgets[(name ++ "Dec").asSymbol].split[i],
							\curve, CVCenter.cvWidgets[(name ++ "Curve").asSymbol].split[i],
							\dur, CVCenter.cvWidgets[(name ++ "Dur").asSymbol].split[i],
							\amp, CVCenter.cvWidgets[(name ++ "GrainAmp").asSymbol].split[i],
							\channelOffset, i,
							\trace, trace
						)
					} ! numBuffers)
				)
			},
			\mono, {
				def = Pdef(looperName,
					Ppar({ |i|
						Pmono(
							\grain,
							\bufnum, buffers[i].bufnum,
							\start, CVCenter.cvWidgets[(name ++ "Start").asSymbol].split[i],
							\end, CVCenter.cvWidgets[(name ++ "End").asSymbol].split[i],
							\rate, CVCenter.cvWidgets[(name ++ "Rate").asSymbol].split[i],
							\dur, CVCenter.cvWidgets[(name ++ "Dur").asSymbol].split[i],
							\amp, CVCenter.cvWidgets[(name ++ "GrainAmp").asSymbol].split[i],
							\channelOffset, i,
							\trace, trace
						)
					} ! numBuffers)
				)
			},
			\ndef, {
				CVCenter.use((name ++ \Rtrig).asSymbol, \trig ! numBuffers, tab: looperName);
				CVCenter.addActionAt((name ++ \Rtrig).asSymbol, 'set rate trigger',
					"{ |cv| Ndef('%').set(\\rtrigs, cv.value) }".format(looperName);
				);
				CVCenter.use((name ++ \Rthresh).asSymbol, #[10, 10000, \exp] ! numBuffers, tab: looperName);
				CVCenter.addActionAt((name ++ \Rthresh).asSymbol, 'set rate threshhold',
					"{ |cv| Ndef('%').set(\\rthreshs, cv.value) }".format(looperName)
				);
				CVCenter.use((name ++ \Strig).asSymbol, \trig ! numBuffers, tab: looperName);
				CVCenter.addActionAt((name ++ \Strig).asSymbol, 'set start trigger',
					"{ |cv| Ndef('%').set(\\strigs, cv.value) }".format(looperName)
				);
				CVCenter.use((name ++ \Sthresh).asSymbol, #[10, 10000, \exp] ! numBuffers, tab: looperName);
				CVCenter.addActionAt((name ++ \Sthresh).asSymbol, 'set start treshhold',
					"{ |cv| Ndef('%').set(\\sthreshs, cv.value) }".format(looperName)
				);
				CVCenter.use((name ++ \Etrig).asSymbol, \trig ! numBuffers, tab: looperName);
				CVCenter.addActionAt((name ++ \Etrig).asSymbol, 'set end trigger',
					"{ |cv| Ndef('%').set(\\etrigs, cv.value) }".format(looperName);
				);
				CVCenter.use((name ++ \Ethresh).asSymbol, #[10, 10000, \exp] ! numBuffers, tab: looperName);
				CVCenter.addActionAt((name ++ \Ethresh).asSymbol, 'set end threshhold',
					"{ |cv| Ndef('%').set(\\ethreshs, cv.value) }".format(looperName);
				);

				CVCenter.addActionAt((name ++ \Rate).asSymbol, 'set rates',
					"{ |cv|
						Ndef('%').set(\\rates, cv.value )
					}".format(looperName)
				);
				CVCenter.addActionAt((name ++ \Start).asSymbol, 'set starts',
					"{ |cv|
						Ndef('%').set(\\starts, cv.value )
					}".format(looperName)
				);
				CVCenter.addActionAt((name ++ \End).asSymbol, 'set ends',
					"{ |cv|
						Ndef('%').set(\\ends, cv.value )
					}".format(looperName)
				);
				def = {
					var trigs, rates, starts, ends, out,
					ratesTrigs, ratesThreshs, startsTrigs,
					startsThreshs, endsTrigs, endsThreshs;

					trigs = \t_trigs.kr(0 ! numBuffers);
					rates = \rates.kr(1 ! numBuffers);
					ratesTrigs = \rtrigs.kr(0 ! numBuffers);
					ratesThreshs = \rthreshs.kr(100 ! numBuffers);
					starts = \starts.kr(0.0 ! numBuffers);
					startsTrigs = \strigs.kr(0 ! numBuffers);
					startsThreshs = \sthreshs.kr(100 ! numBuffers);
					ends = \ends.kr(1.0 ! numBuffers);
					endsTrigs = \etrigs.kr(0 ! numBuffers);
					endsThreshs = \ethreshs.kr(100 ! numBuffers);

					out = this.buffers.collect { |b, i|
						BufRd.ar(
							1, b.bufnum,
							Phasor.ar(
								trigs[i],
								BufRateScale.kr(b.bufnum) * Latch.kr(rates[i], Trig.kr(ratesTrigs[i] > ratesThreshs[i])),
								Latch.kr(starts[i], Trig.kr(startsTrigs[i] > startsThreshs[i])) * BufFrames.kr(b.bufnum),
								Latch.kr(ends[i], Trig.kr(endsTrigs[i] > endsThreshs[i])) * BufFrames.kr(b.bufnum)
							)
						)
					};
					out;
				}
			}
		)

		^def;
	}

	setLoopMaxLength { |index, length|
		var durName = (name ++ \Dur).asSymbol,
		durWdgt = CVCenter.cvWidgets[durName],
		durCV = CVCenter.at(durName),
		durSpec = durWdgt.getSpec,
		val = durCV.value,
		maxval = durSpec.maxval;

		maxval[index] = length;
		durSpec.maxval_(maxval);
		val[index] = maxval[index];
		CVCenter.at(durName).value_(val);
		// Pdef(looperName).reset;
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
			[Ndef(looperName), Pdef(looperName)].do(_.clear);
			all[name] = nil;
		}
	}


}