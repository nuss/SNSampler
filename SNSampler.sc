SNSampler {
	classvar <all;
	var <name, <numBuffers, <bufLength, /*<numChannels, */<server, <>touchOSC, <>touchOSCPanel, <>buffersPanel;
	var <recorder, <buffers, <backupBuffers, <loopLengths, filledBuffers, bufnums;
	// sampling status etc.
	var <statusController, <statusModel, recBufIns, <recBufInsController, <recBufInsModel;
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
		if (all.includesKey(name)) {
			Error("A sampler under the name '%' already exists".format(name)).throw;
		};
		all.put(name, this);
		this.controllerKeys = [\sampler];
		loopLengths = bufLength ! numBuffers;
		bufnums = Array.newClear(numBuffers);
		filledBuffers = Set.new;
		backupBuffers = nil ! numBuffers;
		recBufIns = ();
		this.prSetUpControllers;
		server.waitForBoot {
			buffers = Buffer.allocConsecutive(numBuffers, server, bufLength * server.sampleRate, completionMessage: { |b, i|
				bufnums[i] = b.bufnum;
			});
			recorder = NodeProxy.audio(server, 1).pause;
			"SNSampler: recorder initialized\nBuffers: %".format(buffers).postln;
		}
	}

	prepareRecording { |activate=true, bufIndex=0, in=0, doneAction|
		if (activate and: { recBufIns.keys.includes(bufIndex.asSymbol) }) {
			"buffer at index % (bufnum: %) already reserved for recording".format(bufIndex, buffers[bufIndex].bufnum).error;
			^nil;
		};
		doneAction !? { this.doneAction_(doneAction) };
		if (activate) {
			// buffers will always be 1 channel only
			recorder.put(bufIndex, this.prRecorderFunc(in, bufIndex));
		} {
			recBufIns[bufIndex.asSymbol] = nil;
			recBufInsModel.value_(recBufIns).changedKeys(this.controllerKeys);
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
		if (samplingModel.value == bool.not) {
			samplingModel.value_(bool).changedKeys(this.controllerKeys)
		}
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
					};
				};
				filledBuffers.clear;
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
				};
				filledBuffers.remove(index.asSymbol);
			};
			statusModel.value_(filledBuffers).changedKeys(this.controllerKeys);
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
		recBufInsModel.value_(recBufIns).changedKeys(this.controllerKeys);
		^{
			BufWr.ar(SoundIn.ar(in), buffers[bufIndex].bufnum,
				Phasor.ar(0, BufRateScale.kr(buffers[bufIndex].bufnum), 0, BufFrames.kr(buffers[bufIndex].bufnum))
			)
		}
	}

	prSetUpControllers {
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
		samplingController.put(\sampler, { |changer, what|

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
				var iLoopLengths;
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
				filledBuffers.addAll(recBufIns.keys);
				statusModel.value_(filledBuffers).changedKeys(this.controllerKeys);
				this.doneAction.value(recBufIns);
				// if (touchOSC.class === NetAddr) {
				// 	oscDisplay.(touchOSC, \written, bufIndex, prefix)
				// };
				recBufIns.clear;
				onTime = nil;
			}
		});

		statusModel = Ref(filledBuffers);
		statusController = SimpleController(statusModel);

		recBufInsModel = Ref(recBufIns);
		recBufInsController = SimpleController(recBufInsModel);
	}

}
