SNSampler : AbstractSNSampler {
	classvar <all;
	var <numBuffers, <bufLength, <numChannels, <server;
	var <name, <recorder, <buffers, <loopLengths, usedBuffers, <>doneAction;
	var <isSampling = false, samplingController, samplingModel, onTime, offTime;
	var <>randomBufferSelect = false;

	*new { |name=\Sampler, numBuffers=5, bufLength=60, numChannels=1, server, oscFeedbackAddress|
		^super.newCopyArgs(
			numBuffers,
			bufLength,
			numChannels,
			server ? Server.default;
		).init(name, oscFeedbackAddress);
	}

	init { |argName, oscFeedbackAddress|
		all ? all = ();
		name = argName.asSymbol;
		if (all.includesKey(name)) {
			Error("A looper under the name '%' already exists".format(name)).throw;
		};
		all = all.put(name.asSymbol, this);
		oscFeedbackAddr !? {
			if (oscFeedbackAddr.class !== NetAddr) {
				Error("If supplied, oscFeedbackAddr must be a NetAddr. Given: %\n".format(oscFeedbackAddr));
			} {
				this.class.oscFeedbackAddr_(oscFeedbackAddr);
			}
		};
	}

	initSampler {
		server.waitForBoot {
			var in;
			buffers = Buffer.allocConsecutive(numBuffers, server, bufLength * server.sampleRate, numChannels);
			loopLengths = bufLength ! numBuffers;
			usedBuffers = false ! numBuffers;
			server.sync;
			recorder = NodeProxy.audio(server, numChannels).pause;
			if (numChannels < 2) { in = 0 } { in = 0!numChannels };
			recorder[0] = {
				var soundIn = SoundIn.ar(\in.kr(in)).scope("looper in");
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
				soundIn * \bypassAmp.kr(1);
			};

			this.prCreateWidgets;

			samplingModel = Ref(isSampling);
			samplingController = SimpleController(samplingModel);

			samplingController.put(\value, { |changer, what|
				var length, nextBuf, bufIndex;
				isSampling = changer.value;
				if (isSampling) {
					"start sampling".postln;
					onTime = Main.elapsedTime;
					recorder.resume;
					// CVCenter.at((name ++ "-start/stop").asSymbol).value_(1);
				} {
					var bufnum, amps, durs, ends;
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
					// usedBuffers.postln;
					length = offTime - onTime;
					"stop sampling, buffer length: %\n".postf(length);
					if (length > bufLength) {
						loopLengths[bufIndex] = bufLength;
					} {
						loopLengths[bufIndex] = length;
					};
					// this.prSetSpecConstraints(bufIndex, loopLengths[bufIndex]);
					// Ndef((name ++ "Out").asSymbol).play;
					// this.prSetCVValues(bufIndex);
					if (this.randomBufferSelect.not) {
						nextBuf = bufIndex + 1 % numBuffers;
					} {
						nextBuf = usedBuffers.selectIndex{ |bool| bool == false }.choose;
					};
					recorder.set(\bufnum, buffers[nextBuf].bufnum);
					CVCenter.at((name ++ "-set bufnum").asSymbol).value_(nextBuf);
					onTime = nil;
				}
			})

		}
	}

	sample { |bool|
		samplingModel.value_(bool).changed(\value)
	}

	resetBuffers {
		buffers.do(_.zero);
	}

	prCreateWidgets {
		CVCenter.scv.samplers ?? {
			CVCenter.scv.samplers = ();
		};
		CVCenter.scv.samplers.put(name, this);
		this.cvCenterAddWidget("-start/stop", 0, #[0, 1, \lin, 1, 0],
			"{ |cv| CVCenter.scv.samplers['%'].sample(cv.input.booleanValue) }".format(name),
			0, 0
		);
		this.cvCenterAddWidget("-in", 0, \in,
			"{ |cv|
				CVCenter.scv.samplers['%'].recorder.set(\\in, cv.value)
			}".format(name)
		);
		this.cvCenterAddWidget("-set bufnum", 0, [0, numBuffers - 1, \lin, 1, 0], { |cv|
			recorder.set(\bufnum, buffers[cv.value].bufnum);
		});
	}
}