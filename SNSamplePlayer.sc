SNSamplePlayer : AbstractSNSampler {
	classvar <all;
	var <name, <bufLength, <mode, <numOutChannels, <>touchOSC, <>touchOSCPanel;
	var <>buffers, numBuffers, <group;
	var <server, <loopLengths;
	var <debug = false;
	var looperName, outName, <looperPlayer, <out;
	var trace;

	*new { |name=\Looper, bufLength=60, mode=\grain, numOutChannels=2, server, touchOSC, touchOSCPanel=1|
		^super.newCopyArgs(
			name.asSymbol,
			bufLength,
			mode.asSymbol,
			numOutChannels,
			touchOSC,
			touchOSCPanel
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
		// var now, previous, diff;
		if (bool) {
			trace.setSource(
				Pfunc { |e|
					"index: %, bufnum: %, dur: %, start: %, end: %\n".format(e.channelOffset, e.bufnum, e.dur, e.start, e.end)
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
		var prefix;

		if (touchOSCPanel.notNil) {
			prefix = "/" ++ touchOSCPanel;
		} {
			prefix = "";
		};

		// basic controls: start stop etc.
		CVCenter.use((name ++ \PauseResume).asSymbol, \false.asSpec, tab: (name ++ \Controls).asSymbol);
		CVCenter.addActionAt((name ++ \PauseResume).asSymbol, 'looper pause/resume', "{ |cv|
			var player = SNSamplePlayer.all['%'],
				osc = player.touchOSC;
			if (cv.input.asBoolean) { player.resume } { player.pause };
			if (osc.notNil and: { osc.class === NetAddr }) {
				osc.sendMsg(\"%/looper_pause_resume\", cv.input)
			}
		}".format(name, prefix));
		CVCenter.cvWidgets[(name ++ \PauseResume).asSymbol].oscConnect(touchOSC.ip, nil, "%/looper_pause_resume".format(prefix)).setOscInputConstraints(Point(0, 1));

		CVCenter.use((name ++ \Debug).asSymbol, \false.asSpec, tab: (name ++ \Controls).asSymbol);
		CVCenter.addActionAt((name ++ \Debug).asSymbol, 'debug start/stop', "{ |cv|
			var player = SNSamplePlayer.all['%'],
				osc = player.touchOSC;
			player.debug_(cv.input.asBoolean);
			if (osc.notNil and: { osc.class === NetAddr }) {
				osc.sendMsg(\"%/looper_debug\", cv.input)
			}
		}".format(name, prefix));
		CVCenter.cvWidgets[(name ++ \Debug).asSymbol].oscConnect(touchOSC.ip, nil, "%/looper_debug".format(prefix)).setOscInputConstraints(Point(0, 1));

		CVCenter.use((name ++ \FreeNodes).asSymbol, \false.asSpec, (name ++ \Controls).asSymbol);
		CVCenter.addActionAt((name ++ \FreeNodes).asSymbol, 'free hanging nodes', "{ |cv|
			var player = SNSamplePlayer.all['%'],
				osc = player.touchOSC;
		    if (cv.value.asBoolean) {
				player.freeHangingNodes;
		    };
			if (osc.notNil and: { osc.class === NetAddr }) {
				osc.sendMsg(\"%/free_hanging_nodes\", cv.input)
			}
		}".format(name, prefix));
		CVCenter.cvWidgets[(name ++ \FreeNodes).asSymbol].oscConnect(touchOSC.ip, nil, "%/free_hanging_nodes".format(prefix)).setOscInputConstraints(Point(0, 1));

		CVCenter.use((name ++ \ReinitGrains).asSymbol, \false.asSpec, (name ++ \Controls).asSymbol);
		CVCenter.addActionAt((name ++ \ReinitGrains).asSymbol, 'reinit grains', "{ |cv|
			var player = SNSamplePlayer.all['%'],
				osc = player.touchOSC;
			CVCenter.widgetsAtTab('%').do { |w|
    			if (CVCenter.cvWidgets[w].respondsTo(\\split)) {
        			CVCenter.cvWidgets[w].unsplit;
        			CVCenter.cvWidgets[w].split;
    			}
			};
			player.initDef;
			if (osc.notNil and: { osc.class === NetAddr }) {
				osc.sendMsg(\"%/looper_reinit_grains\", cv.input)
			}
		}".format(name, looperName, prefix));
		CVCenter.cvWidgets[(name ++ \ReinitGrains).asSymbol].oscConnect(touchOSC.ip, nil, "%/looper_reinit_grains".format(prefix)).setOscInputConstraints(Point(0, 1));

		CVCenter.use((name ++ \ResetSpecs).asSymbol, \false.asSpec, (name ++ \Controls).asSymbol);
		CVCenter.addActionAt((name ++ \ResetSpecs).asSymbol, 'reset specs', "{ |cv|
			var osc = SNSamplePlayer.all['%'].touchOSC;
			defer {
				var name = ('%' ++ 'Dur').asSymbol;
				CVCenter.cvWidgets[name].setSpec(#[0.1, 0.1]);
				CVCenter.at(name).value_(0.1!CVCenter.at(name).size);
			};
			defer {
				var name = ('%' ++ 'End').asSymbol;
				CVCenter.cvWidgets[name].setSpec(#[0, 1, \lin, 0, 1]);
				CVCenter.at(name).value_(1.0!CVCenter.at(name).size);
			};
			defer {
				var name = ('%' ++ 'Rate').asSymbol;
				CVCenter.cvWidgets[name].setSpec(#[-2, 2, \lin, 0, 1]);
				CVCenter.at(name).value_(1.0!CVCenter.at(name).size);
			};
			defer {
				var name = ('%' ++ 'Start').asSymbol;
				CVCenter.cvWidgets[name].setSpec;
				CVCenter.at(name).value_(0.0!CVCenter.at(name).size);
			};
			defer {
				var name = ('%' ++ 'Curve').asSymbol;
				CVCenter.cvWidgets[name].setSpec(#[-4, 4]);
				CVCenter.at(name).value_(-4!CVCenter.at(name).size);
			};
			['Atk', 'Rel'].do { |name|
				defer {
					var n = ('%' ++ name).asSymbol;
					CVCenter.cvWidgets[n].setSpec(#[0.02, 3, \exp]);
					CVCenter.at(n).value_(0.02!CVCenter.at(n).size);
				};
			};
			if (osc.notNil and: { osc.class === NetAddr }) {
				osc.sendMsg(\"%/looper_reset_specs\", cv.input)
			}
		}".format(name, name, name, name, name, name, name, prefix));
		CVCenter.cvWidgets[(name ++ \ResetSpecs).asSymbol].oscConnect(touchOSC.ip, nil, "%/looper_reset_specs".format(prefix)).setOscInputConstraints(Point(0, 1));

		CVCenter.use((name ++ \ChanAmps).asSymbol, \amp ! numOutChannels, 1.0, (name ++ \Out).asSymbol);
		CVCenter.addActionAt((name ++ \ChanAmps).asSymbol, 'set channel amps', "{ |cv|
			var player = SNSamplePlayer.all['%'],
				osc = player.touchOSC;
			player.out.set(('%' ++ 'ChanAmps').asSymbol, cv.value);
			if (osc.notNil and: { osc.class === NetAddr }) {
				player.numOutChannels.do { |i|
					osc.sendMsg(\"%/looper_chan_amps/\" ++ (i+1), cv.input[i])
				}
			}
		}".format(name, name, prefix));
		numOutChannels.do { |i|
			CVCenter.cvWidgets[(name ++ \ChanAmps).asSymbol].oscConnect(touchOSC.ip, nil, "%/looper_chan_amps/%".format(prefix, i+1), slot: i)
			.setOscInputConstraints(Point(0, 1), i);
		};

		// TouchOSC fader expected on panel 2
		CVCenter.use((name ++ \Amp).asSymbol, \amp, 1.0, (name ++ \Out).asSymbol);
		CVCenter.addActionAt((name ++ \Amp).asSymbol, 'looper out volume', "{ |cv|
			var player = SNSamplePlayer.all['%'],
				osc = player.touchOSC;
			player.out.set('amp', cv.value);
			if (osc.notNil and: { osc.class === NetAddr }) {
				osc.sendMsg(\"%/looper_out_volume\", cv.input)
			}
		}".format(name, prefix));
		CVCenter.cvWidgets[(name ++ \Amp).asSymbol].oscConnect(touchOSC.ip, nil, "%/looper_out_volume".format(prefix))
		.setOscInputConstraints(Point(0, 1));

		// TouchOSC fader expected on panel 2
		CVCenter.use((name ++ \AmpWet).asSymbol, nil, 1.0, (name ++ \Out).asSymbol);
		CVCenter.addActionAt((name ++ \AmpWet), 'amp dry/wet', "{ |cv|
			var player = SNSamplePlayer.all['%'],
				osc = player.touchOSC;
			player.out.set(('wet' ++ %).asSymbol, cv.value);
			if (osc.notNil and: { osc.class === NetAddr }) {
				osc.sendMsg(\"%/looper_out_volume_dry_wet\", cv.input)
			}
		}".format(name, volumeControl.asInteger, prefix));
		CVCenter.cvWidgets[(name ++ \AmpWet).asSymbol].oscConnect(touchOSC.ip, nil, "%/looper_out_volume_dry_wet".format(prefix))
		.setOscInputConstraints(Point(0, 1));

		// TouchOSC rotary expected on panel 2
		CVCenter.use((name ++ \Center).asSymbol, \pan, tab: (name ++ \Out).asSymbol);
		// update rotary on panel 2 AND slider on panel 1
		CVCenter.addActionAt((name ++ \Center).asSymbol, 'set center', "{ |cv|
			var player = SNSamplePlayer.all['%'],
				osc = player.touchOSC;
			player.out.set('center', cv.value);
			if (osc.notNil and: { osc.class === NetAddr }) {
				osc.sendMsg(\"%/looper_center_rotary\", cv.input)
			}
		}".format(name, prefix));
		CVCenter.cvWidgets[(name ++ \Center).asSymbol].oscConnect(touchOSC.ip, nil, "%/looper_center_rotary".format(prefix))
		.setOscInputConstraints(Point(0, 1));

		// TouchOSC rotary expected on panel 2
		CVCenter.use((name ++ \Spread).asSymbol, nil, tab: (name ++ \Out).asSymbol);
		// update rotary on panel 2 AND slider on panel 1
		CVCenter.addActionAt((name ++ \Spread).asSymbol, 'set spread', "{ |cv|
			var player = SNSamplePlayer.all['%'],
				osc = player.touchOSC;
			player.out.set(('%' ++ 'Spread').asSymbol, cv.value);
			if (osc.notNil and: { osc.class === NetAddr }) {
				osc.sendMsg(\"%/looper_spread_rotary\", cv.input)
			}
		}".format(name, name, prefix));
		CVCenter.cvWidgets[(name ++ \Spread).asSymbol].oscConnect(touchOSC.ip, nil, "%/looper_spread_rotary".format(prefix))
		.setOscInputConstraints(Point(0, 1));

		// if numOutChannels > 2 SplayAz is used nstead of Splay
		if (numOutChannels > 2) {
			CVCenter.use((name ++ \Width).asSymbol, #[1, numOutChannels], 2, (name ++ \Out).asSymbol);
			CVCenter.addActionAt((name ++ \Width).asSymbol, 'set width', "{ |cv|
				var player = SNSamplePlayer.all['%'],
					osc = player.touchOSC;
				player.out.set(('%' ++ 'Width').asSymbol, cv.value);
				if (osc.notNil and: { osc.class === NetAddr }) {
					osc.sendMsg(\"%/looper_width_rotary\", cv.input)
				}
			}".format(name, name, prefix));
			CVCenter.cvWidgets[(name ++ \Width)].oscConnect(touchOSC.ip, nil, "%/looper_width_rotary".format(prefix))
			.setOscInputConstraints(Point(0, 1));

			CVCenter.use((name ++ \Orientation).asSymbol, #[1, numOutChannels], 2, (name ++ \Out).asSymbol);
			CVCenter.addActionAt((name ++ \Orientation).asSymbol, 'set orientation', "{ |cv|
				var player = SNSamplePlayer.all['%'],
					osc = player.touchOSC;
				player.out.set(('%' ++ 'Orientation').asSymbol, cv.value);
				if (osc.notNil and: { osc.class === NetAddr }) {
					osc.sendMsg(\"%/looper_orientation_rotary\", cv.input)
				}
			}".format(name, prefix));
			CVCenter.cvWidgets[(name ++ \Orientation)].oscConnect(touchOSC.ip, nil, "%/looper_orientation_rotary".format(prefix))
			.setOscInputConstraints(Point(0, 1));
		};

		// mode \grain specific - should go into initDef
		CVCenter.use((name ++ "Start").asSymbol, [0!numBuffers, loopLengths/bufLength], tab: looperName);
		CVCenter.use((name ++ "End").asSymbol, [0!numBuffers, loopLengths/bufLength], loopLengths/bufLength, looperName);
		CVCenter.use((name ++ "Rate").asSymbol, #[-2, 2] ! numBuffers, 1.0, tab: looperName);
		CVCenter.use((name ++ "GrainAmp").asSymbol, \amp ! numBuffers, tab: looperName);
		CVCenter.use((name ++ \GrainAmpLag).asSymbol, \ampx4 ! numBuffers, tab: looperName);
		CVCenter.use((name ++ \Legato).asSymbol, #[0.0, 1.0] ! numBuffers, 1.0, tab: looperName);

		this.prInitPatternPlayer;

		Ndef(outName).mold(numOutChannels, \audio, \elastic);
		group !? {
			Ndef(outName).group_(ParGroup.new)
		};

		if (numOutChannels <= 2) {
			Ndef(outName)[0] = {
				Splay.ar(
					\in.ar(0 ! numBuffers),
					(name ++ \Spread).asSymbol.kr(0.5),
					1,
					(name ++ \Center).asSymbol.kr(0.0)
				) * (name ++ \ChanAmps).asSymbol.kr(1!numOutChannels)
			}
		} {
			Ndef(outName)[0] = {
				SplayAz.ar(
					numOutChannels,
					\in.ar(0 ! numBuffers),
					(name ++ \Spread).asSymbol.kr(0.5),
					1,
					(name ++ \Width).asSymbol.kr(2),
					(name ++ \Center).asSymbol.kr(0.0),
					(name ++ \Orientation).asSymbol.kr(0.0)
				) * (name ++ \ChanAmps).asSymbol.kr(1!numOutChannels)
			}
		};

		out = Ndef(outName); // make out public

		Ndef(outName) <<> Ndef(looperName);
		Ndef(outName)[volumeControl] = \filter -> { |in|
			// global amplitude over all channels
			in * (name ++ \Amp).asSymbol.kr(1)
		};
		// Ndef(outName).cvcGui(false, excemptArgs: [\in]);
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

	initDef { |argMode, bufferArray|
		var def;

		mode ?? { mode = argMode };
		bufferArray !? {
			if (bufferArray.size != numBuffers) {
				Error("The number od buffers passed to initDef must equal numBuffers!").throw;
			} {
				this.buffers = bufferArray;
			}
		};

		switch(mode,
			\grain, {
				CVCenter.use((name ++ "Atk").asSymbol, #[0.02, 3, \exp] ! numBuffers, tab: looperName);
				CVCenter.use((name ++ "Sust").asSymbol, #[0.1, 1.0] ! numBuffers, 1, tab: looperName);
				CVCenter.use((name ++ "Rel").asSymbol, #[0.02, 3, \exp] ! numBuffers, tab: looperName);
				CVCenter.use((name ++ "Curve").asSymbol, #[-4, 4] ! numBuffers, 0, tab: looperName);
				CVCenter.use((name ++ "Dur").asSymbol, [0.1!numBuffers, loopLengths], 0.1 ! numBuffers, tab: looperName);

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
							\legato, CVCenter.cvWidgets[(name ++ "Legato").asSymbol].split[i],
							\channelOffset, i,
							\trace, trace
						)
					} ! numBuffers)
				)
			},
			\mono, {
				CVCenter.use((name ++ "Dur").asSymbol, [0.1!numBuffers, loopLengths], 0.1 ! numBuffers, looperName);
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
				CVCenter.use((name ++ \Rtrig).asSymbol, #[0.0002, 1.1, \exp] ! numBuffers, tab: looperName);
				CVCenter.addActionAt((name ++ \Rtrig).asSymbol, 'set rate trigger',
					"{ |cv| Ndef('%').set(\\rtrigs, cv.value) }".format(looperName);
				);
				CVCenter.use((name ++ \Rthresh).asSymbol,#[0.0001, 1.0, \exp] ! numBuffers, tab: looperName);
				CVCenter.addActionAt((name ++ \Rthresh).asSymbol, 'set rate threshhold',
					"{ |cv| Ndef('%').set(\\rthreshs, cv.value) }".format(looperName)
				);
				CVCenter.use((name ++ \Strig).asSymbol, #[0.0002, 1.1, \exp] ! numBuffers, tab: looperName);
				CVCenter.addActionAt((name ++ \Strig).asSymbol, 'set start trigger',
					"{ |cv| Ndef('%').set(\\strigs, cv.value) }".format(looperName)
				);
				CVCenter.use((name ++ \Sthresh).asSymbol,#[0.0001, 1.0, \exp] ! numBuffers, tab: looperName);
				CVCenter.addActionAt((name ++ \Sthresh).asSymbol, 'set start treshhold',
					"{ |cv| Ndef('%').set(\\sthreshs, cv.value) }".format(looperName)
				);
				CVCenter.use((name ++ \Etrig).asSymbol, #[0.0002, 1.1, \exp] ! numBuffers, tab: looperName);
				CVCenter.addActionAt((name ++ \Etrig).asSymbol, 'set end trigger',
					"{ |cv| Ndef('%').set(\\etrigs, cv.value) }".format(looperName);
				);
				CVCenter.use((name ++ \Ethresh).asSymbol,#[0.0001, 1.0, \exp] ! numBuffers, tab: looperName);
				CVCenter.addActionAt((name ++ \Ethresh).asSymbol, 'set end threshhold',
					"{ |cv| Ndef('%').set(\\ethreshs, cv.value) }".format(looperName);
				);

				CVCenter.addActionAt((name ++ \Rate).asSymbol, 'set rates',
					"{ |cv| Ndef('%').set(\\rates, cv.value ) }".format(looperName)
				);
				CVCenter.addActionAt((name ++ \Start).asSymbol, 'set starts',
					"{ |cv| Ndef('%').set(\\starts, cv.value ) }".format(looperName)
				);
				CVCenter.addActionAt((name ++ \End).asSymbol, 'set ends',
					"{ |cv| Ndef('%').set(\\ends, cv.value ) }".format(looperName)
				);
				CVCenter.addActionAt((name ++ \GrainAmp).asSymbol, 'set channel amps',
					"{ |cv| Ndef('%').set(\\grainAmp, cv.value) }".format(looperName)
				);
				CVCenter.addActionAt((name ++ \GrainAmpLag).asSymbol, 'set channel amp lags',
					"{ |cv| Ndef('%').set(\\grainAmpLag, cv.value) }".format(looperName)
				);

				def = {
					var trigs, rates, starts, ends, out,
					ratesTrigs, ratesThreshs, startsTrigs,
					startsThreshs, endsTrigs, endsThreshs;

					trigs = \t_trigs.tr(0 ! numBuffers);
					rates = \rates.kr(1 ! numBuffers);
					ratesTrigs = \rtrigs.tr(0.0 ! numBuffers);
					ratesThreshs = \rthreshs.kr(1 ! numBuffers);
					starts = \starts.kr(0.0 ! numBuffers);
					startsTrigs = \strigs.tr(0.0 ! numBuffers);
					startsThreshs = \sthreshs.kr(1 ! numBuffers);
					ends = \ends.kr(1 ! numBuffers);
					endsTrigs = \etrigs.tr(0.0 ! numBuffers);
					endsThreshs = \ethreshs.kr(1 ! numBuffers);

					out = this.buffers.collect { |b, i|
						BufRd.ar(
							1, b.bufnum,
							Phasor.ar(
								trigs[i],
								BufRateScale.kr(b.bufnum) * Latch.kr(rates[i], Trig.kr(ratesTrigs[i] > ratesThreshs[i]))/*.poll(label: \rate)*/,
								// starts[i] * BufFrames.kr(b.bufnum),
								// ends[i].poll(label: \end) * BufFrames.kr(b.bufnum)
								Latch.kr(starts[i], Trig.kr(startsTrigs[i] > startsThreshs[i]))/*.poll(label: \start)*/ * BufFrames.kr(b.bufnum),
								Latch.kr(ends[i], Trig.kr(endsTrigs[i] > endsThreshs[i]))/*.poll(label: \end)*/ * BufFrames.kr(b.bufnum)
							)
						)
					};
					out * \grainAmp.kr(1.0 ! numBuffers, \grainAmpLag.kr(0.1 ! numBuffers));
				}
			}
		)

		^def;
	}

	setLoopMaxLength { |index, length|
		var durName = (name ++ \Dur).asSymbol;
		var startName = (name ++ \Start).asSymbol;
		var endName = (name ++ \End).asSymbol;
		var strigName = (name ++ \Strig).asSymbol;
		var sthreshName = (name ++ \Sthresh).asSymbol;
		var etrigName = (name ++ \Etrig).asSymbol;
		var ethreshName = (name ++ \Sthresh).asSymbol;
		var durWdgt, durCV, durSpec;
		var startWdgt, startCV, startSpec;
		var endWdgt, endCV, endSpec;
		var val, maxval;

		startWdgt = CVCenter.cvWidgets[startName];
		endWdgt = CVCenter.cvWidgets[endName];
		startCV = CVCenter.at(startName);
		endCV = CVCenter.at(endName);
		startSpec = startCV.spec;
		endSpec = endCV.spec;

		val = startCV.value;
		maxval = startSpec.maxval;
		maxval[index] = length / bufLength;
		startSpec.maxval_(maxval);
		val[index] = 0; // start
		CVCenter.at(startName).value_(val);

		val = endCV.value;
		maxval = endSpec.maxval;
		maxval[index] = length / bufLength;
		endSpec.maxval_(maxval);
		val[index] = maxval[index]; // end
		CVCenter.at(endName).value_(val);

		if (mode != \ndef) {
			durWdgt = CVCenter.cvWidgets[durName];
			durCV = CVCenter.at(durName);
			durSpec = durWdgt.getSpec;
			val = durCV.value;
			maxval = durSpec.maxval;

			maxval[index] = length;
			durSpec.maxval_(maxval);
			val[index] = maxval[index];
			CVCenter.at(durName).value_(val);
		} {
			val = 0 ! numBuffers;
			val[index] = 1.1;
			Ndef(looperName).set(
				\rtrigs, val,
				\strigs, val,
				\etrigs, val
			)
		};
		if (mode === \grain) {
			"looper at index %: start: %, end: %, duration: %\n".postf(
				index,
				CVCenter.at(startName).value[index],
				CVCenter.at(endName).value[index],
				CVCenter.at(durName).value[index]
			)
		}
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

	quit { |fadeTime=0.2|
		Ndef(outName).clear(fadeTime);
		fork {
			fadeTime.wait;
			[Ndef(looperName), Pdef(looperName)].do(_.clear);
			all[name] = nil;
		}
	}

	freeHangingNodes {
		if (mode == \grain) {
			Ndef(looperName).group.deepFree
		}
	}

	/*resetWidgets { |index|
		if (index.isNil) {
			CVCenter.cvWidgets[(name ++ "Start").asSymbol].setSpec()
		}
	}*/
}