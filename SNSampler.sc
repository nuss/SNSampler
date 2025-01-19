SNSampler {
	classvar <all;
	var <name, <numBuffers, <bufLength, <server, <>touchOSC, <ins, <inKeys;
	var <recorder, <buffers, <backupBuffers, filledBuffers, bufnums;
	// sampling status etc.
	var <statusController, <statusModel, recBufIns, <recBufInsController, <recBufInsModel;
	var loopLengths, <loopLengthsModel, <loopLengthsController;
	var <samplingController, <samplingModel, onTime, offTime, blink;
	var <insController, <insModel, inBusses;
	// counters, used for naming ins in external GUIs
	// see addKeyboardIns
	var additionalIns=1, keyboardIns=1, keyboardEffectsIns=1;
	var scopeBus, scopeWindow;
	var <>controllerKeys;
	var <>doneAction;

	*initClass {
		all = ();
	}

	*new { |name=\Sampler, numBuffers=5, bufLength=60, server, touchOSC, ins|
		server ?? { server = Server.default };
		^super.newCopyArgs(
			name.asSymbol,
			numBuffers,
			bufLength,
			server,
			touchOSC,
		).init(ins);
	}

	init {
		var insSpec = \audioin.asSpec;

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
		ins ?? {
			inBusses = (insSpec.minval..insSpec.maxval);
			"input busses: %".format(inBusses).postln;
			inKeys = inBusses.collect(_.asSymbol);
			ins = inBusses.collect { |bus, i| inKeys[i] -> bus }.asEvent;
		};
		this.prSetUpControllers;
		server.waitForBoot {
			buffers = Buffer.allocConsecutive(numBuffers, server, bufLength * server.sampleRate, completionMessage: { |b, i|
				bufnums[i] = b.bufnum;
			});
			recorder = NodeProxy.audio(server, 1).pause;
			scopeBus = Bus.audio(server, inBusses.size);
			this.scope;
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
		if (recBufIns.size == 0) {
			"Please select at least one buffer for recording!".error;
			^nil;
		} {
			if (samplingModel.value == bool.not) {
				samplingModel.value_(bool).changedKeys(this.controllerKeys)
			}
		}
	}

	scope {
		if (scopeWindow.isNil or: { scopeWindow.window.isClosed }) {
			{
				// scopeWindow = Stethoscope(server, numChannels, );
				scopeWindow = Stethoscope(server, ins.size, scopeBus.index);
				Stethoscope.ugenScopes.add(scopeWindow);
				scopeWindow.window.onClose_({
					scopeWindow.free;
					Stethoscope.ugenScopes.remove(scopeWindow);
				});
				scopeWindow.window
				.bounds_(Rect(0, Window.screenBounds.height, 200, 400))
				.name_(name ++ " in");
			}.defer(0.001);
		}
	}

	// name must be a CVCenterKeyboard instance's name
	// if an effect chain has been added its output can be recorded by setting recordEffects to true
	addKeyboardIns { |name, numChannels=2, recordEffects=false|
		var bus, proxy, thisInKeys, thisInBusses;
		name = name.asSymbol;
		if (CVCenterKeyboard.at(name).notNil) {
			if (recordEffects) {
				if (CVCenterKeyboard.at(name).outProxy.notNil) {
					bus = CVCenterKeyboard.at(name).outProxy.bus;
					numChannels = bus.numChannels;
					thisInKeys = numChannels.collect { |i| "kf%[%]".format(keyboardEffectsIns, i+1).asSymbol };
					keyboardEffectsIns = keyboardEffectsIns + 1;
				} {
					"CVCenterKeyboard.at('%') has no effects chain added!".format(name).error;
					^nil;
				}
				// proxy.source = { In.ar(CVCenterKeyboard.at(name).outProxy.bus.index, numChannels) }
			} {
				"CVCenterKeyboard.at(name).out: %".format(CVCenterKeyboard.at(name).out).postln;
				bus = Bus.audio(server, numChannels);
				proxy = NodeProxy.audio(server, numChannels);
				proxy.source = { In.ar(CVCenterKeyboard.at(name).out, numChannels) };
				proxy.play(bus.index);
				thisInKeys = numChannels.collect { |i| "k%[%]".format(keyboardIns, i+1).asSymbol };
				keyboardIns = keyboardIns + 1;
			};
			inKeys = inKeys.addAll(thisInKeys);
			thisInBusses = numChannels.collect { |i| bus.index + i };
			inBusses = inBusses.addAll(thisInBusses);
			ins = inBusses.collect { |bus, i| inKeys[i] -> bus }.asEvent;
			insModel.value_([inKeys, inBusses]).changedKeys(this.controllerKeys);
		} {
			"CVCenterKeyboard.at('%') does not exist!".format(name).error;
		}
	}

	// inputs from private busses
	addInputs { |inBus, inputName, numChannels=2, synthOut=0|

	}

	// only reset buffers reserved for writing
	reset { |index, doneAction|
		fork({
			if (index.isNil) {
				buffers.do { |buf, i|
					if (bufnums.includes(buf.bufnum)) {
						buf.zero;
						"buffer % zeroed".format(i).inform;
						loopLengths[i] = bufLength;
					} {
						backupBuffers[i] !? {
							backupBuffers[i].buffer.zero;
							backupBuffers[i].length = bufLength;
						}
					};
				};
				filledBuffers.clear;
			} {
				if (bufnums.includes(buffers[index].bufnum)) {
					buffers[index].zero;
					"buffer % zeroed".format(index).inform;
					loopLengths[index] = bufLength;
				} {
					backupBuffers[index] !? {
						backupBuffers[index].buffer.zero;
						"backup buffer % zeroed".format(index).inform;
						backupBuffers[index].length = bufLength;
					}
				};
				filledBuffers.remove(index.asSymbol);
			};
			statusModel.value_(filledBuffers).changedKeys(this.controllerKeys);
			loopLengthsModel.value_(loopLengths).changedKeys(this.controllerKeys);
			if (doneAction.isFunction) {
				doneAction.value;
			}
		}, AppClock)
	}

	/*prCreateWidgets {
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
	}*/

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
		var sig, audioIn;

		recBufIns.put(bufIndex.asSymbol, in);
		recBufInsModel.value_(recBufIns).changedKeys(this.controllerKeys);
		// "in: %, firstPrivateBus: %".format(in, server.options.firstPrivateBus).postln;
		audioIn = if (in >= server.options.firstPrivateBus) { In } { SoundIn };
		^{
			sig = audioIn.ar(in);
			BufWr.ar(sig, buffers[bufIndex].bufnum,
				Phasor.ar(0, BufRateScale.kr(buffers[bufIndex].bufnum), 0, BufFrames.kr(buffers[bufIndex].bufnum))
			);
			Out.ar(scopeBus.index + in, sig);
		}
	}

	prSetUpControllers {
		var length, bufIndices, bufIndex, bufnums, bufnum, bufPprefix;
		var isSampling = false;

		samplingModel = Ref(isSampling);
		samplingController = SimpleController(samplingModel);
		samplingController.put(\sampler, { |changer, what|

			isSampling = changer.value;
			if (isSampling) {
				if (recBufIns.size > 0) {
					"start sampling, recBufIns: %".format(recBufIns).postln;

					recBufIns.do { |i|
						i = i.asInteger;
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
					};
					onTime = Main.elapsedTime;
					// if index is nil the buffer has likely been replaced by a pre-recorded one
					// if buffer has been backed up, restore buffers with backed up buffer
					recorder.resume;
				} {
					"Please define at least one input and one buffer to be recorded to!".error;
				}
			} {
				"finish sampling".postln;
				offTime = Main.elapsedTime;
				// important! remove sources before pausing!
				// otherwise NodeProxy won't be initialized correctly for next recording
				// empirically found out...
				recorder.removeAt.pause;
				length = offTime - onTime;
				(length < 0.1).if { length = 0.1 };
				// "stop sampling, index: %, buffer length: %\n".postf(bufIndex, length);
				recBufIns.keys.collect { |i|
					if (length > bufLength) {
						loopLengths[i.asInteger] = bufLength;
					} {
						loopLengths[i.asInteger] = length;
					}
				};
				loopLengthsModel.value_(loopLengths).changedKeys(this.controllerKeys);
				filledBuffers.addAll(recBufIns.keys);
				statusModel.value_(filledBuffers).changedKeys(this.controllerKeys);
				this.doneAction.value(recBufIns.keys.asArray.asInteger.sort, loopLengths);
				recBufIns.clear;
				onTime = nil;
			}
		});

		statusModel = Ref(filledBuffers);
		statusController = SimpleController(statusModel);

		recBufInsModel = Ref(recBufIns);
		recBufInsController = SimpleController(recBufInsModel);

		loopLengthsModel = Ref(loopLengths);
		loopLengthsController = SimpleController(loopLengthsModel);
		/*loopLengthsController.put(\sampler, { |changer, what|
			changer.value.postln
		});*/

		insModel = Ref([inKeys, inBusses]);
		insController = SimpleController(insModel);
		insController.put(\sampler, { |changer, what|
			var numChannels = changer.value[1].maxItem - changer.value[1].minItem +1;
			var index = scopeBus.index;
			"input channels: %".format(changer.value[1]).postln;
			scopeBus.free;
			scopeBus = Bus(index: index, numChannels: numChannels, server: server);
			if (scopeWindow.notNil and: { scopeWindow.window.isClosed.not }) {
				// scopeWindow.index_(changer.value[1].minItem);
				scopeWindow.numChannels_(numChannels);
			}
		})
	}

}
