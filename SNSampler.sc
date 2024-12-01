SNSampler : AbstractSNSampler {
	classvar <all;
	var <name, <numBuffers, <bufLength, /*<numChannels, */<server, <>touchOSC, <>touchOSCPanel, <>buffersPanel;
	var <recorder, <buffers, <backupBuffers, <loopLengths, bufnums;
	// sampling status etc.
	var <setupController, <setupModel, <recBufIns;
	var <samplingController, <samplingModel, onTime, offTime, blink;
	var <inBus, soundIn, scopeBus, scopeWindow;
	var <>controllerKeys;
	var <>doneAction;

	*initClass {
		all = ();
	}

	*new { |name=\Sampler, numBuffers=5, bufLength=60, server, touchOSC, touchOSCPanel=1, buffersPanel=4|
		server ?? { server = Server.default };
		^super.newCopyArgs(
			name.asSymbol,
			numBuffers,
			bufLength,
			server,
			touchOSC,
			touchOSCPanel,
			buffersPanel
		).init;
	}

	init {
		"server: %".format(server).postln;
		if (all.includesKey(name)) {
			Error("A sampler under the name '%' already exists".format(name)).throw;
		};
		all.put(name, this);
		this.controllerKeys = [\sampler];
		loopLengths = bufLength ! numBuffers;
		bufnums = Array.newClear(numBuffers);
		backupBuffers = nil ! numBuffers;
		recBufIns = ();
		this.prSamplingSetup;
		this.prSamplingController;
		server.waitForBoot {
			buffers = Buffer.allocConsecutive(numBuffers, server, bufLength * server.sampleRate, completionMessage: { |b, i|
				bufnums[i] = b.bufnum;
			});
			recorder = NodeProxy.audio(server, 1).pause;
			"SNSampler: recorder initialized\nBuffers: %".format(buffers).postln;
		}
	}

	prepareRecording { |activate=true, bufIndex=0, in=0, doneAction|
		"activate: %, bufIndex: %, in: %".format(activate, bufIndex, in).postln;
		if (activate and: { recBufIns.keys.includes(bufIndex.asSymbol) }) {
			"buffer at index % (bufnum: %) already reserved for recording".format(bufIndex, buffers[bufIndex].bufnum).error;
			^nil;
		};
		doneAction !? { this.doneAction_(doneAction) };
		if (activate) {
			// buffers will always be 1 channel only
			recorder.put(bufIndex, this.prRecorderFunc(in, bufIndex));
		} {
			"recBufIns: %".format(recBufIns).postln;
			recorder.removeAt(bufIndex);
		};
		// 	// scopeBus = Bus.audio(server, numChannels);
		// 	// Out.ar(scopeBus.index, soundIn);
		// 	rawIn!2 * \bypassAmp.kr(0);
		// };

		// this.scope;
		// this.prCreateWidgets;

		// oscDisplay = { |addr, mode, bufIndex, panelPrefix|
		// 	blink ?? {
		// 		blink = fork({
		// 			loop {
		// 				// "blink".postln;
		// 				addr.sendMsg("%/sample_buf_%".format(panelPrefix, bufIndex), 0);
		// 				1.wait;
		// 				addr.sendMsg("%/sample_buf_%".format(panelPrefix, bufIndex), 1);
		// 				1.wait
		// 			}
		// 		}, AppClock);
		// 	};
		//
		// 	switch(mode)
		// 	{ \blink } { blink.play(AppClock) }
		// 	{ \written } {
		// 		blink.reset.stop;
		// 		// "written".postln;
		// 		addr.sendMsg("%/sample_buf_%".format(panelPrefix, bufIndex), 1)
		// 	};
		// };
	}

	sample { |bool|
		samplingModel.value_(bool).changedKeys(this.controllerKeys)
	}

	scope {
		if (scopeWindow.isNil or: { scopeWindow.window.isClosed }) {
			{
				// scopeWindow = Stethoscope(server, numChannels, scopeBus.index);
				scopeWindow = Stethoscope(server, 1, scopeBus.index);
				Stethoscope.ugenScopes.add(scopeWindow);
				scopeWindow.window.onClose_({
					scopeWindow.free;
					// scopeBus.free;
					Stethoscope.ugenScopes.remove(scopeWindow);
				});
				scopeWindow.window.name_(name ++ " in");
			}.defer(0.001);
		}
	}

	// only reset buffers reserved for writing
	reset { |index, doneAction|
		fork({
			if (index.isNil) {
				buffers.do { |buf, i|
					if (bufnums.includes(buf.bufnum)) {
						buf.zero;
						"buffer % zeroed".format(i).inform;
						loopLengths[i] = 0.1;
					} {
						backupBuffers[i] !? {
							backupBuffers[i].buffer.zero;
							backupBuffers[i].length = 0.1;
						}
					}
				}
			} {
				if (bufnums.includes(buffers[index].bufnum)) {
					buffers[index].zero;
					"buffer % zeroed".format(index).inform;
					loopLengths[index] = 0.1;
				} {
					backupBuffers[index] !? {
						backupBuffers[index].buffer.zero;
						"backup buffer % zeroed".format(index).inform;
						backupBuffers[index].length = 0.1;
					}
				}
			};
			if (doneAction.isFunction) {
				doneAction.value;
			}
		}, AppClock)
	}

	inBus_ { |in=0|
		recorder.set(\in, in);
	}

	prCreateWidgets {
		var prefix;

		if (touchOSCPanel.notNil) {
			prefix = "/" ++ touchOSCPanel;
		} {
			prefix = "";
		};

		numBuffers.do { |i|
			this.cvCenterAddWidget("-buf%reset".format(i), 0, #[0, 1, \lin, 1],
				"{ |cv|
					var sampler = SNSampler.all['%'],
						osc = sampler.touchOSC;
					if (osc.notNil and: { osc.class === NetAddr }) {
						osc.sendMsg(\"%/sample_buf_%\", 0);
					};
					sampler.reset(%);
				}".format(name, prefix, i, i),
				(name ++ \Sampler).asSymbol
			).oscConnect(touchOSC.ip, nil, "%/sampler_zero_buffer_%".format(prefix, i))
			.setOscInputConstraints(Point(0, 1));
		};
		this.cvCenterAddWidget("-resetAll", 0, #[0, 1, \lin, 1],
			"{ |cv|
				var sampler = SNSampler.all['%'],
					osc = sampler.touchOSC;
				sampler.reset;
				if (osc.notNil and: { osc.class === NetAddr }) {
					%.do { |n| osc.sendMsg(\"%/sample_buf_\" ++ n, 0) }
				}
			}".format(name, numBuffers, prefix),
			(name ++ \Sampler).asSymbol,
			midiMode: 0, softWithin: 0
		).oscConnect(touchOSC.ip, nil, "%/reset_all_samples".format(prefix))
		.setOscInputConstraints(Point(0, 1));
		this.cvCenterAddWidget("-start/stop", 0, #[0, 1, \lin, 1, 0],
			"{ |cv|
				var sampler = SNSampler.all['%'],
					osc = sampler.touchOSC;
				sampler.sample(cv.input.booleanValue, sampler.recBufnum);
				if (osc.notNil and: { osc.class === NetAddr }) {
					osc.sendMsg(\"%/start_stop_sampling\", cv.input);
				}
			}".format(name, prefix),
			(name ++ \Sampler).asSymbol,
			midiMode: 0, softWithin: 0
		).oscConnect(touchOSC.ip, nil, "%/start_stop_sampling".format(prefix))
		.setOscInputConstraints(Point(0, 1));
		this.cvCenterAddWidget("-in", inBus, \in,
			"{ |cv|
				var sampler = SNSampler.all['%'],
					osc = sampler.touchOSC;
				sampler.inBus_(cv.value);
				if (osc.notNil and: { osc.class === NetAddr }) {
					osc.sendMsg(\"%/set_in_bus\", cv.input);
					osc.sendMsg(\"%/in_bus_num\", cv.value.asInteger);
				}
			}".format(name, prefix, prefix),
			(name ++ \Sampler).asSymbol
		).oscConnect(touchOSC.ip, nil, "%/set_in_bus".format(prefix))
		.setOscInputConstraints(Point(0, 1));
		this.cvCenterAddWidget("-set bufnum", 0, [0, numBuffers - 1, \lin, 1, 0],
			"{ |cv|
				var sampler = SNSampler.all['%'],
					osc = sampler.touchOSC;
				sampler.recBufnum_(sampler.buffers[cv.value].bufnum);
				(\"recording to bufnum \" + sampler.buffers[cv.value]).postln;
				if (osc.notNil and: { osc.class === NetAddr }) {
					osc.sendMsg(\"%/set_next_samplebuffer\", cv.input);
					osc.sendMsg(\"%/next_bufnum\", cv.value.asInteger);
				}
			}".format(name, prefix, prefix),
			(name ++ \Sampler).asSymbol
		).oscConnect(touchOSC.ip, nil, "%/set_next_samplebuffer".format(prefix))
		.setOscInputConstraints(Point(0, 1));
		this.cvCenterAddWidget("-compressor", 0, nil,
			"{ |cv|
				var sampler = SNSampler.all['%'],
					osc = sampler.touchOSC;
				sampler.set('compress', cv.value);
				if (osc.notNil and: { osc.class === NetAddr }) {
					osc.sendMsg(\"%/sample_compressor_amp\", cv.input);
				}
			}".format(name, prefix),
			(name ++ \Sampler).asSymbol,
			midiMode: 0, softWithin: 0
		).oscConnect(touchOSC.ip, nil, "%/sample_compressor_amp".format(prefix))
		.setOscInputConstraints(Point(0, 1));
		this.cvCenterAddWidget("-compThresh", 0.5, nil,
			"{ |cv|
				SNSampler.all['%'].recorder.set(\\compThresh, cv.value)
			}".format(name),
			(name ++ ' in compressor').asSymbol
		);
		this.cvCenterAddWidget("-clampTime", 0.01, nil,
			"{ |cv|
				SNSampler.all['%'].recorder.set(\\clampTime, cv.value)
			}".format(name),
			(name ++ ' in compressor').asSymbol
		);
		this.cvCenterAddWidget("-slopeBelow", 1.0, nil,
			"{ |cv|
				SNSampler.all['%'].recorder.set(\\slopeBelow, cv.value)
			}".format(name),
			(name ++ ' in compressor').asSymbol
		);
		this.cvCenterAddWidget("-slopeAbove", 0.5, nil,
			"{ |cv|
				SNSampler.all['%'].recorder.set(\\slopeAbove, cv.value)
			}".format(name),
			(name ++ ' in compressor').asSymbol
		);
		this.cvCenterAddWidget("-relaxTime", 0.01, nil,
			"{ |cv|
				SNSampler.all['%'].recorder.set(\\relaxTime, cv.value)
			}".format(name),
			(name ++ ' in compressor').asSymbol
		);
		this.cvCenterAddWidget("-bypass-amp", 0.0, \amp,
			"{ |cv|
				var sampler = SNSampler.all['%'],
					osc = sampler.touchOSC;
				sampler.recorder.set(\\bypassAmp, cv.value);
				if (osc.notNil and: { osc.class === NetAddr }) {
					osc.sendMsg(\"%/sampler_bypass\", cv.input);
				}
			}".format(name, prefix),
			(name ++ \Sampler).asSymbol,
			midiMode: 0, softWithin: 0
		).oscConnect(touchOSC.ip, nil, "%/sampler_bypass".format(prefix))
		.setOscInputConstraints(Point(0, 1));
		this.cvCenterAddWidget("-setCompressor", 0, #[0, 1, \lin, 1],
			"{ |cv|
				SNSampler.all['%'].setInputCompressor
			}".format(name),
			(name ++ \Sampler).asSymbol
		);
	}

	quit {
		recorder.clear;
		buffers.do { |b|
			b.close.free;
		};
		scopeWindow.quit;
		scopeWindow = nil;
		all[name] = nil;
	}

	prRecorderFunc { |in, bufIndex|
		recBufIns.put(bufIndex.asSymbol, in);
		setupModel.value_(recBufIns).changedKeys(this.controllerKeys);
		^{
			BufWr.ar(SoundIn.ar(in), buffers[bufIndex].bufnum,
				Phasor.ar(0, BufRateScale.kr(buffers[bufIndex].bufnum), 0, BufFrames.kr(buffers[bufIndex].bufnum))
			)
		}
	}

	prSamplingController {
		var length, bufIndices, bufIndex, bufnums, bufnum, bufPprefix;
		var isSampling = false;

		// var oscDisplay, prefix;

		// if (touchOSCPanel.notNil) {
		// 	prefix = "/" ++ touchOSCPanel;
		// } {
		// 	prefix = "";
		// };

		samplingModel = Ref(isSampling);
		samplingController = SimpleController(samplingModel);
		samplingController.put(this.controllerKeys[0], { |changer, what|

			// if (buffersPanel.notNil) {
			// 	bufPprefix = "/" ++ this.buffersPanel;
			// } {
			// 	bufPprefix = "";
			// };

			isSampling = changer.value;
			if (isSampling) {
				if (recBufIns.size > 0) {
					"start sampling".postln;
					/*recordBufIndices.do { |i|
						bufIndex = backupBuffers.detectIndex { |buf|
							buf.notNil and: { buf.buffer.bufnum == buffers[i].bufnum }
						};
						// "bufIndex: %".format(bufIndex).postln;
						bufIndex !? {
							buffers[i] = backupBuffers[bufIndex].buffer;
							backupBuffers[bufIndex] = nil;
						}
						// if (this.touchOSC.notNil and: { this.touchOSC.class === NetAddr}) {
						// touchOSC.sendMsg(bufPprefix ++ "/switch_ext_buf" ++ (bufIndex+1), 0);
					// }
					};*/
					onTime = Main.elapsedTime;
					// if index is nil the buffer has likely been replaced by a pre-recorded one
					// if buffer has been backed up, restore buffers with backed up buffer
					recorder.resume;
					// if (touchOSC.class === NetAddr) {
					// 	oscDisplay.(touchOSC, \blink, bufIndex, prefix)
					// };
				} {
					"Please define at least one input and one buffer to be recorded to!".error;
				}
			} {
				var amps, durs, ends, iLoopLengths;
				"finish sampling".postln;
				offTime = Main.elapsedTime;
				// important! remove sources before pausing!
				// otherwise NodeProxy won't be initialized correctly for next recording
				// empirically found out...
				recorder.removeAt.pause;
				length = offTime - onTime;
				(length < 0.1).if { length = 0.1 };
				// "stop sampling, index: %, buffer length: %\n".postf(bufIndex, length);
				iLoopLengths = recBufIns.keys.collect { |i|
					if (length > bufLength) {
						bufLength;
					} {
						length;
					}
				};
				this.doneAction.value(recBufIns);
				// if (touchOSC.class === NetAddr) {
				// 	oscDisplay.(touchOSC, \written, bufIndex, prefix)
				// };
				recBufIns.clear;
				onTime = nil;
			}
		})
	}

	prSamplingSetup {
		setupModel = Ref(recBufIns);
		setupController = SimpleController(setupModel);
	}

}
