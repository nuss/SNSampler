SNSampler : AbstractSNSampler {
	classvar <all;
	var <name, numBuffers, <bufLength, /*<numChannels, */<server, <>touchOSC, <>touchOSCPanel, <>buffersPanel;
	var <recorder, <buffers, <backupBuffers, <loopLengths, <usedBuffers, <isSetUp = false, <lastBufnum, bufnums, recordIns, recordBuffers;
	var <isSampling = false, samplingController, samplingModel, onTime, offTime, blink;
	var <>randomBufferSelect = false;
	var <inBus, soundIn, scopeBus, scopeWindow;
	var controllerKeys;
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
		controllerKeys = [];
		if (all.includesKey(name)) {
			Error("A sampler under the name '%' already exists".format(name)).throw;
		};
		all.put(name, this);
		loopLengths = bufLength ! numBuffers;
		bufnums = Array.newClear(numBuffers);
		backupBuffers = nil ! numBuffers;
		usedBuffers = false ! numBuffers;
		recordBuffers = List();
		server.waitForBoot {
			buffers = Buffer.allocConsecutive(numBuffers, server, bufLength * server.sampleRate, completionMessage: { |b, i|
				bufnums[i] = b.bufnum;
			});
			recorder = NodeProxy.audio(server, 1).pause.play;
			"SNSampler: recorder initialized\nBuffers: %".format(buffers).postln;
		}
	}

	prepareRecording { |bufIndex=0, in=0, doneAction|
		var oscDisplay, prefix;
		var samplingLocked = false;

		if (recordBuffers.includes(buffers[bufIndex].bufnum)) {
			"buffer at index % (bufnum: %) already reserved for recording".format(bufIndex, buffers[bufIndex].bufnum).error;
			^nil;
		};

		doneAction !? { this.doneAction_(doneAction) };
		if (isSetUp == false) {
			// buffers will always be 1 channel only
			this.prRecorderFunc(in, buffers[bufIndex].bufnum, 0);
			// 	// scopeBus = Bus.audio(server, numChannels);
			// 	// Out.ar(scopeBus.index, soundIn);
			// 	rawIn!2 * \bypassAmp.kr(0);
			// };

			this.scope;
			this.prCreateWidgets;
			isSetUp = true;

			samplingModel = Ref(isSampling);
			samplingController = SimpleController(samplingModel);

			if (touchOSCPanel.notNil) {
				prefix = "/" ++ touchOSCPanel;
			} {
				prefix = "";
			};

			oscDisplay = { |addr, mode, bufIndex, panelPrefix|
				blink ?? {
					blink = fork({
						loop {
							// "blink".postln;
							addr.sendMsg("%/sample_buf_%".format(panelPrefix, bufIndex), 0);
							1.wait;
							addr.sendMsg("%/sample_buf_%".format(panelPrefix, bufIndex), 1);
							1.wait
						}
					}, AppClock);
				};

				switch(mode)
				{ \blink } { blink.play(AppClock) }
				{ \written } {
					blink.reset.stop;
					// "written".postln;
					addr.sendMsg("%/sample_buf_%".format(panelPrefix, bufIndex), 1)
				};
			};

			samplingController.put(\value, { |changer, what|
				var length, nextBuf, bufIndex, bufnum, bufPprefix;

				"samplingModel: %".format(changer.value).postln;
				if (buffersPanel.notNil) {
					bufPprefix = "/" ++ this.buffersPanel;
				} {
					bufPprefix = "";
				};

				isSampling = changer.value[0];
				changer.value[1] !? { bufnum = changer.value[1] };
				if (isSampling) {
					if (samplingLocked.not) {
						"start sampling".postln;
						onTime = Main.elapsedTime;
						recorder.resume;
						if (changer.value[1].isNil) {
							bufnum = recorder.get(\bufnum);
							// bufnum will be advanced on stop
							// for the user's convenience store the just used bufnum to a variable
							lastBufnum = bufnum;
						};
						bufIndex = buffers.detectIndex{ |buf| buf.bufnum == bufnum };
						// if index is nil the buffer has likely been replaced by a pre-recorded one
						// if buffer has been backed up, restore buffers with backed up buffer
						bufIndex ?? {
							bufIndex = backupBuffers.detectIndex { |buf|
								buf.notNil and: { buf.buffer.bufnum == bufnum }
							};
							buffers[bufIndex] = backupBuffers[bufIndex].buffer;
							backupBuffers[bufIndex] = nil;
							if (this.touchOSC.notNil and: { this.touchOSC.class === NetAddr}) {
								touchOSC.sendMsg(bufPprefix ++ "/switch_ext_buf" ++ (bufIndex+1), 0);
							}
						};
						if (touchOSC.class === NetAddr) {
							oscDisplay.(touchOSC, \blink, bufIndex, prefix)
						};
						samplingLocked = true;
					}
				} {
					var amps, durs, ends;
					if (samplingLocked) {
						offTime = Main.elapsedTime;
						recorder.pause;
						// reset phasor before next sampling
						recorder.set(\trig, 1);
						bufnum = recorder.get(\bufnum);
						// bufffers may begin with other bufnums than 0,
						// so we use the index in the buffers array
						bufIndex = buffers.detectIndex{ |buf| buf.bufnum == bufnum };
						usedBuffers[bufIndex] = true;
						// reset if all buffers have been filled already
						if (usedBuffers.select { |bool| bool == true }.size == numBuffers) {
							usedBuffers = false ! numBuffers;
						};
						length = offTime - onTime;
						(length < 0.1).if { length = 0.1 };
						"stop sampling, index: %, buffer length: %\n".postf(bufIndex, length);
						if (length > bufLength) {
							loopLengths[bufIndex] = bufLength;
						} {
							loopLengths[bufIndex] = length;
						};
						this.doneAction.value(bufIndex, loopLengths[bufIndex]);
						if (this.randomBufferSelect.not) {
							nextBuf = bufIndex + 1 % numBuffers;
						} {
							nextBuf = usedBuffers.selectIndex{ |bool| bool == false }.choose;
						};
						// "next sample buffer: %".format(nextBuf).postln;

						if (touchOSC.class === NetAddr) {
							oscDisplay.(touchOSC, \written, bufIndex, prefix)
						};

						recorder.set(\bufnum, buffers[nextBuf].bufnum);
						"next bufnum: %".format(recorder.get(\bufnum)).postln;
						CVCenter.at((name ++ "-set bufnum").asSymbol).value_(nextBuf);
						onTime = nil;
						samplingLocked = false;
					}
				}
			})
		} {
			"sampler '%' already set up!".format(name).inform;
		}
	}

	sample { |bool, bufnum|
		if (controllerKeys.includes(\value).not) {
			controllerKeys = controllerKeys.add(\value)
		};
		samplingModel.value_([bool, bufnum]).changedKeys(controllerKeys)
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

	recBufnum_ { |bufnum=0|
		recorder.set(\bufnum, bufnum);
	}

	recBufnum {
		^recorder.get(\bufnum);
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

	prRecorderFunc { |in, bufnum, trig|
		^{
			BufWr.ar(SoundIn.ar(in), bufnum,
				Phasor.ar(\trig.tr(0), BufRateScale.kr(bufnum), 0, BufFrames.kr(bufnum))
			)
		}
	}

}
