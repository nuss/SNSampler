SNSampler : AbstractSNSampler {
	classvar <all;
	var <numBuffers, <bufLength, <numChannels, <>bypassOut, <server, <>touchOSC, <>touchOSCPanel;
	var <name, <recorder, <buffers, <loopLengths, <usedBuffers, <>doneAction, <isSetUp = false;
	var <isSampling = false, samplingController, samplingModel, onTime, offTime, blink;
	var <>randomBufferSelect = false;
	var <>inBus;
	var controllerKeys;
	var <>doneAction;

	*new { |name=\Sampler, numBuffers=5, bufLength=60, numChannels=1, out=0, server, touchOSC, touchOSCPanel=1, oscFeedbackAddress|
		server ?? { server = Server.default };
		^super.newCopyArgs(
			numBuffers,
			bufLength,
			numChannels,
			out,
			server,
			touchOSC,
			touchOSCPanel
		).init(name, oscFeedbackAddress);
	}

	init { |argName, oscFeedbackAddress|
		// [numBuffers, bufLength, numChannels, bypassOut, touchOSC, touchOSCPanel, server].postln;
		all ?? { all = () };
		controllerKeys = [];
		name = argName.asSymbol;
		if (all.includesKey(name)) {
			Error("A sampler under the name '%' already exists".format(name)).throw;
		};
		all = all.put(name.asSymbol, this);
		loopLengths = bufLength ! numBuffers;
		oscFeedbackAddr !? {
			if (oscFeedbackAddr.class !== NetAddr) {
				Error("If supplied, oscFeedbackAddr must be a NetAddr. Given: %\n".format(oscFeedbackAddr));
			} {
				this.class.oscFeedbackAddr_(oscFeedbackAddr);
			}
		};
	}

	setupSampler { |in=0, doneAction|
		var oscDisplay, prefix;

		doneAction !? { this.doneAction_(doneAction) };
		if (isSetUp.not) {
			server.waitForBoot {
				buffers = Buffer.allocConsecutive(numBuffers, server, bufLength * server.sampleRate, numChannels);
				loopLengths = bufLength ! numBuffers;
				usedBuffers = false ! numBuffers;
				server.sync;
				recorder = NodeProxy.audio(server, 2).pause.play;
				if (numChannels < 2) { inBus = in } { inBus = in ! numChannels };
				recorder[0] = {
					var soundIn, rawIn = SoundIn.ar(\in.kr(inBus));
					soundIn = XFade2.ar(
						rawIn,
						Compander.ar(
							rawIn, rawIn,
							\compThresh.kr(0.5),
							\slopeBelow.kr(1.0),
							\slopeAbove.kr(0.5),
							\clampTime.kr(0.01),
							\relaxTime.kr(0.01)
						),
						\compress.kr(0)
					).scope(name ++ " in");
					BufWr.ar(
						soundIn,
						\bufnum.kr(0),
						Phasor.ar(
							\trig.tr(0),
							BufRateScale.kr(\bufnum.kr(buffers[0].bufnum)),
							0,
							BufFrames.kr(\bufnum.kr(0))
						)
					);
					rawIn!2 * \bypassAmp.kr(0);
				};

				this.prCreateWidgets;
				isSetUp = true;

				samplingModel = Ref(isSampling);
				samplingController = SimpleController(samplingModel);

				if (touchOSCPanel.notNil) {
					prefix = "/" ++ touchOSCPanel;
				} {
					prefix = "";
				};

				oscDisplay = { |mode, bufIndex, panelPrefix|
					blink ?? {
						blink = fork({
							loop {
								touchOSC.sendMsg("%/sample_buf_%".format(panelPrefix, bufIndex), 0);
								1.wait;
								touchOSC.sendMsg("%/sample_buf_%".format(panelPrefix, bufIndex), 1);
								1.wait
							}
						}, AppClock);
					};

					switch(mode)
					{ \blink } { blink.play }
					{ \written } {
						blink.reset.stop;
						touchOSC.sendMsg("%/sample_buf_%".format(panelPrefix, bufIndex), 1)
					};
				};

				samplingController.put(\value, { |changer, what|
					var length, nextBuf, bufIndex, bufnum;
					isSampling = changer.value;
					if (isSampling) {
						"start sampling".postln;
						onTime = Main.elapsedTime;
						recorder.resume;
						bufnum = recorder.get(\bufnum);
						bufIndex = buffers.detectIndex{ |buf| buf.bufnum == bufnum };
						[touchOSC, touchOSC.class].postln;
						if (touchOSC.class === NetAddr) {
							oscDisplay.(\blink, bufIndex, prefix)
						};
					} {
						var amps, durs, ends;
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

						if (touchOSC.class === NetAddr) {
							oscDisplay.(\written, bufIndex, prefix)
						};

						recorder.set(\bufnum, buffers[nextBuf].bufnum);
						CVCenter.at((name ++ "-set bufnum").asSymbol).value_(nextBuf);
						onTime = nil;
					}
				})
			}
		} {
			"sampler '%' already set up!".format(name).inform;
		}
	}

	sample { |bool|
		if (controllerKeys.includes(\value).not) {
			controllerKeys = controllerKeys.add(\value)
		};
		samplingModel.value_(bool).changedKeys(controllerKeys)
	}

	reset { |index, doneAction|
		fork({
			if (index.isNil) {
				buffers.do(_.zero);
				loopLengths = 0.1 ! numBuffers;
			} {
				buffers[index].zero;
				loopLengths[index] = 0.1;
			};
			if (doneAction.isFunction) {
				doneAction.value;
			}
		}, AppClock)
	}

	setBufnum { |bufnum=0|
		recorder.set(\bufnum, bufnum);
	}

	prCreateWidgets {
		var prefix;



		CVCenter.scv.samplers ?? {
			CVCenter.scv.samplers = ();
		};
		CVCenter.scv.samplers.put(name, this);

		if (touchOSCPanel.notNil) {
			prefix = "/" ++ touchOSCPanel;
		} {
			prefix = "";
		};

		numBuffers.do { |i|
			this.cvCenterAddWidget("-buf%reset".format(i), 0, #[0, 1, \lin, 1],
				"{ |cv|
					CVCenter.scv.samplers['%'].touchOSC !? {
						if (CVCenter.scv.samplers['%'].touchOSC.class === NetAddr) {
							CVCenter.scv.samplers['%'].touchOSC.sendMsg(\"%/sample_buf_%\", 0);
						};
						CVCenter.scv.samplers['%'].reset(%)
					}
				}".format(name, name, name, prefix, i, name, i)
			)
		};
		this.cvCenterAddWidget("-resetAll", 0, #[0, 1, \lin, 1],
			"{ |cv| CVCenter.scv.samplers['%'].reset }".format(name),
			midiMode: 0, softWithin: 0
		);
		this.cvCenterAddWidget("-start/stop", 0, #[0, 1, \lin, 1, 0],
			"{ |cv| CVCenter.scv.samplers['%'].sample(cv.input.booleanValue) }".format(name),
			midiMode: 0, softWithin: 0
		);
		this.cvCenterAddWidget("-in", inBus, \in,
			"{ |cv|
				CVCenter.scv.samplers['%'].recorder.set(\\in, cv.value);
				CVCenter.scv.samplers['%'].inBus_(cv.value);
			}".format(name)
		);
		this.cvCenterAddWidget("-set bufnum", 0, [0, numBuffers - 1, \lin, 1, 0],
			"{ |cv|
				CVCenter.scv.samplers['%'].setBufnum(cv.value);
			}".format(name)
		);
		this.cvCenterAddWidget("-compressor", 0, nil,
			"{ |cv|
				CVCenter.scv.samplers['%'].recorder.set(\\compress, cv.value)
			}".format(name),
			midiMode: 0, softWithin: 0
		);
		this.cvCenterAddWidget("-compThresh", 0.5, nil,
			"{ |cv|
				CVCenter.scv.samplers['%'].recorder.set(\\compThresh, cv.value)
			}".format(name),
			'in compressor'
		);
		this.cvCenterAddWidget("-clampTime", 0.01, nil,
			"{ |cv|
				CVCenter.scv.samplers['%'].recorder.set(\\clampTime, cv.value)
			}".format(name),
			'in compressor'
		);
		this.cvCenterAddWidget("-slopeBelow", 1.0, nil,
			"{ |cv|
				CVCenter.scv.samplers['%'].recorder.set(\\slopeBelow, cv.value)
			}".format(name),
			'in compressor'
		);
		this.cvCenterAddWidget("-slopeAbove", 0.5, nil,
			"{ |cv|
				CVCenter.scv.samplers['%'].recorder.set(\\slopeAbove, cv.value)
			}".format(name),
			'in compressor'
		);
		this.cvCenterAddWidget("-relaxTime", 0.01, nil,
			"{ |cv|
				CVCenter.scv.samplers['%'].recorder.set(\\relaxTime, cv.value)
			}".format(name),
			'in compressor'
		);
		this.cvCenterAddWidget("-bypass-amp", 0.0, \amp,
			"{ |cv|
				CVCenter.scv.samplers['%'].recorder.set(\\bypassAmp, cv.value)
			}".format(name),
			midiMode: 0, softWithin: 0
		);
		this.cvCenterAddWidget("-setCompressor", 0, #[0, 1, \lin, 1],
			"{ |cv|
				CVCenter.scv.samplers['%'].setInputCompressor
			}".format(name), name
		);
	}

	// empirically gained defaults
	setInputCompressor { |thresh=0.5, slopeBelow=0.03, slopeAbove=0.15, clampTime=0.01, relaxTime=0.23|
		CVCenter.at((name ++ '-compThresh').asSymbol).value_(thresh);
		CVCenter.at((name ++ '-slopeBelow').asSymbol).value_(slopeBelow);
		CVCenter.at((name ++ '-slopeAbove').asSymbol).value_(slopeAbove);
		CVCenter.at((name ++ '-clampTime').asSymbol).value_(clampTime);
		CVCenter.at((name ++ '-relaxTime').asSymbol).value_(relaxTime);
	}

}
