SNSampler : AbstractSNSampler {
	classvar <all;
	var <name, <clock, <>tempo, <beatsPerBar, <numBars, <numBuffers, <server, <inBus;
	var <buffers, <recorder, <metronome, <buffersAllocated = false;
	var recorder, <isPlaying = false;
	var nameString, <activeBuffers, activeBuffersSeq;
	var <window;

	*new { |name, clock, tempo=1, beatsPerBar=4, numBars=1, numBuffers=5, server, oscFeedbackAddr|
		^super.newCopyArgs(name, clock, tempo, beatsPerBar, numBars, numBuffers, server).init(oscFeedbackAddr);
	}

	init { |oscFeedbackAddr|
		oscFeedbackAddr !? {
			if (oscFeedbackAddr.class !== NetAddr) {
				Error("If supplied, oscFeedbackAddr must be a NetAddr. Given: %\n".format(oscFeedbackAddr));
			} {
				this.class.oscFeedbackAddr_(oscFeedbackAddr);
			}
		};
		all ?? { all = () };
		if (name.isNil) {
			name = ("sampler" + (all.size + 1)).asSymbol
		} {
			if (all.includesKey(name.asSymbol)) {
				Error("A sampler under the given name already exists").throw;
			}
		};
		all = all.put(name.asSymbol, this);
		nameString = name.asString;

		server ?? { server = Server.default };
		clock ?? {
			// create a new clock, compensating latency
			clock = TempoClock(this.tempo, 0, server.latency.neg);
		};

		// mark all buffers active on start
		activeBuffers = 1!numBuffers;
	}

	start {
		var bufnums;
		var onOffFunc, feedbackFunc;
		var onOff, bufSetter;
		var soundIn;

		if (isPlaying.not) {
			if (server.serverRunning) {
				server.bind {
					buffers = Buffer.allocConsecutive(
						numBuffers,
						server,
						(server.sampleRate * beatsPerBar * numBars).asInteger,
						completionMessage: { |buf| "buffer % allocated\n".postf(buf.bufnum) };
					);
					bufnums = buffers.collect(_.bufnum);

					server.sync;
					buffersAllocated = true;

					recorder = NodeProxy.audio(server, 1).clock_(clock).quant_([beatsPerBar, 0, 0, 1]);
					recorder[0] = {
						var sig = LeakDC.ar(SoundIn.ar(\in.kr(0)));
						soundIn = Compander.ar(
							sig, sig,
							\compThresh.kr(0.5),
							\slopeBelow.kr(1.0),
							\slopeAbove.kr(0.5),
							\clampTime.kr(0.01),
							\relaxTime.kr(0.01)
						).scope("sampler in");
						BufWr.ar(
							soundIn,
							\bufnum.kr(0),
							// Phasor will ramp from start to end and then jump back to start
							// if this.tempo != 1 we have to move through the buffer at a different rate
							Phasor.ar(0, BufRateScale.kr(\bufnum.kr(0), this.tempo), 0, BufFrames.kr(\bufnum.kr(0)))
						);
						// play silently
						Silent.ar;
					};

					this.schedule;

					CVCenter.cvWidgets[(nameString + "level").asSymbol].oscConnect(
						this.class.oscFeedbackAddr.ip,
						name: '/sampler/inlevel'
					);
					CVCenter.cvWidgets[(nameString + "tempo").asSymbol].oscConnect(
						this.class.oscFeedbackAddr.ip,
						name: '/sampler/tempo'
					);

					onOffFunc = "{ |cv| if (cv.value == 1) {
						SNSampler.all['%'].resume;
						SNSampler.all['%'].schedule;
					} { SNSampler.all['%'].pause }}".format(name, name, name);
					onOff = this.cvCenterAddWidget(" on/off", 1, #[0, 1, \lin, 1.0], onOffFunc, 0, 0);
					onOff.addAction('sampler toggle feedback', { |cv| SNSampler.oscFeedbackAddr.sendMsg('/sampler/toggle', cv.input) });
					onOff.oscConnect(this.class.oscFeedbackAddr.ip, name: '/sampler/toggle', oscMsgIndex: 1);


					bufSetter = this.cvCenterAddWidget(" bufSet", this.activeBuffers, [0!numBuffers, 1!numBuffers, \lin, 1.0], midiMode: 0, softWithin: 0);
					bufnums.do { |bufnum|
						bufSetter.oscConnect(
							this.class.oscFeedbackAddr.ip.postln,
							// nil,
							name: "/buf/set/%".format(bufnum + 1),
							slot: bufnum
						)
					};

					// bufSetFunc = "SNSampler.all['%'].activeBuffers_(cv.input);".format(name);
					bufSetter.addAction('set active buffers', "{ |cv|
						SNSampler.all['%'].activeBuffers_(cv.input);
					}".format(name));

					feedbackFunc = "{ |cv|\n";
					activeBuffers.do { |state, bufnum|
						feedbackFunc = feedbackFunc ++
						"\nSNSampler.oscFeedbackAddr.sendMsg('/buf/set/%', cv.input[%]);".format(bufnum + 1, bufnum);
					};
					feedbackFunc = feedbackFunc ++ "\n}";
					bufSetter.addAction('feedback', feedbackFunc);

					this.addMetronome(out: 0, numChannels: 1, amp: 0);
					CVCenter.cvWidgets[(nameString + "level").asSymbol].addAction('level feedback', { |cv|
						SNSampler.oscFeedbackAddr.sendMsg('/sampler/inlevel', cv.input);
					});
					CVCenter.cvWidgets[(nameString + "tempo").asSymbol].addAction('tempo feedback', { |cv|
						SNSampler.oscFeedbackAddr.sendMsg('/sampler/tempo', cv.input);
					});
					this.class.oscFeedbackAddr.sendMsg('/sampler/toggle', 1)
					.sendMsg('/sampler/inlevel', CVCenter.at((nameString + "level").asSymbol).input)
					.sendMsg('/sampler/tempo', CVCenter.at((nameString + "tempo").asSymbol).input);


					bufnums.do { |bn|
						OSCdef(("buf" + bn).asSymbol, { this.zero(bn) }, ("/buf" ++ bn ++ "/zero").asSymbol);
					};

					// recorder.play;
					isPlaying = recorder.isPlaying;
				}
			} {
				"Please boot server '%' before starting the sampler.".format(server).warn;
			}
		} {
			this.resume;
			this.class.oscFeedbackAddr.sendMsg('/sampler/toggle', 1)
			.sendMsg('/sampler/inlevel', CVCenter.at((nameString + "level").asSymbol).input)
			.sendMsg('/sampler/tempo', CVCenter.at((nameString + "tempo").asSymbol).input);
		}
	}

	stop {
		recorder !? {
			recorder.stop;
			buffers.collect(_.bufnum).do { |bn|
				OSCdef(("buf" ++ bn).asSymbol).free;
			};
			isPlaying = recorder.isPlaying;
		}
	}

	pause {
		recorder !? {
			recorder.pause;
		}
	}

	resume {
		recorder !? {
			// make sure sampler and metronome are in sync
			this.schedule;
			recorder.resume;
		}
	}

	// schedule sampling, post current off beat if post == true
	schedule { |post=false|
		var bufnums, trace;
		var initiallyActive;

		if (buffers.isNil) {
			"Can't schedule as no buffers have been allocated yet".warn;
		} {
			bufnums = buffers.collect(_.bufnum);
		};

		activeBuffersSeq ?? {
			initiallyActive = 1.0!numBuffers;
			this.activeBuffers_(initiallyActive);
			bufnums.do{ |bn|
				this.class.oscFeedbackAddr.sendMsg("/buf/set/"++(bn+1), 1);
			}
		};

		trace ?? { trace = PatternProxy.new };
		if (post) {
			trace.setSource(
				Pfunc { |e|
					"beatsPerBar:" + e.dur ++ "," + nameString + "beat:" + clock.beatInBar ++ ", buffer:" + e.bufnum
				}.trace
			);
		} {
			trace.setSource(0);
		};

		recorder !? {
			recorder[1] = \set -> Pbind(
				\dur, beatsPerBar * numBars,
				\in, CVCenter.use(
					(nameString + "in").asSymbol,
					ControlSpec(0, server.options.firstPrivateBus-1, \lin, 1.0),
					tab: name
				),
				\inputLevel, CVCenter.use(
					(nameString + "level").asSymbol,
					\amp,
					0.5,
					name
				),
				\compThresh, CVCenter.use(
					(nameString + "compressor threshold").asSymbol,
					value: 1.0,
					tab: name
				),
				\slopeBelow, CVCenter.use(
					(nameString + "slope below").asSymbol,
					value: 0.3,
					tab: name
				),
				\slopeAbove, CVCenter.use(
					(nameString + "slope above").asSymbol,
					ControlSpec(0.0, 2.0, \lin, 0.0, 1.0),
					tab: name
				),
				\clampTime, CVCenter.use(
					(nameString + "clamp time").asSymbol,
					ControlSpec(0.001, 0.1),
					0.001,
					name
				),
				\relaxTime, CVCenter.use(
					(nameString + "relax time").asSymbol,
					ControlSpec(0.002, 0.2),
					0.2,
					tab: name
				),
				\bufnum, activeBuffersSeq,
				\tempo, CVCenter.use(
					nameString + "tempo",
					#[1, 4, \lin],
					this.tempo,
					name
				),
				\feedback, Pfunc { |ev|
					buffers.do { |b, i|
						if (b.bufnum == ev.bufnum) {
							this.class.oscFeedbackAddr.sendMsg("/buf"++ev.bufnum++"/recording", 1);
						} {
							this.class.oscFeedbackAddr.sendMsg("/buf"++b.bufnum++"/recording", 0);
						}
					}
				},
				\trace, trace
			);
			CVCenter.addActionAt(
				(nameString + "tempo").asSymbol,
				'set tempo',
				"{ |cv| SNSampler.all['" ++ nameString ++ "'].tempo = cv.value }"
			)
		}
	}

	allocateBuffers { |numBuffers|
		// pause sampler first
		if (CVCenter.at((nameString + "on/off").asSymbol).notNil) {
			CVCenter.at((nameString + "on/off").asSymbol).input_(0)
		} {
			Error("The sampler could not be paused. Did you accidently remove the widget '" ++ nameString + "on/off'?").throw;
		};
		buffers.do{ |buf| buf.close({ |b| b.freeMsg })};
		buffers = nil;
		buffersAllocated = false;
		// allocation is asynchronous
		server.bind {
			buffers = Buffer.allocConsecutive(
				numBuffers,
				server,
				(server.sampleRate * beatsPerBar * numBars).asInteger
			);
			server.sync;

			buffersAllocated = true;
			// resume sampler
			CVCenter.at((nameString + "on/off").asSymbol).input_(0);
		}
	}

	zero { |bufnum|
		var buffer;

		if (bufnum.isNil) {
			buffers.do(_.zero);
		} {
			buffer = buffers.detect{ |b| b.bufnum == bufnum };
			if (buffer.notNil) {
				buffer.zero({ "buffer % zeroed\n".postf(bufnum) });
			} {
				"buffer at bufnum % does not exist!\n".format(bufnum).warn;
			}
		}
	}

	clear {
		recorder.clear;
		metronome !? {
			Pdef(metronome.name.asSymbol).clear;
			metronome.clear;
		};
		buffers.do({ |b| b.close(_.free) });
		buffersAllocated = false;
		CVCenter.removeAtTab(name);
		all[all.findKeyForValue(this)] = nil;
	}

	server_ { |server|
		// TODO
	}

	// GUI
	front { |parent, rect|
		var rectProps = rect !? {
			[rect.left, rect.top, rect.width, rect.height]
		};

		if (window.isNil or:{ window.isClosed }) {
			SNSamplerGui(this, parent, *rectProps);
			window = parent;
		} { window.front };
	}

	// an acoustic metronome
	addMetronome { |out, numChannels, amp|
		metronome ?? {
			metronome = SNMetronome(
				this, clock, this.tempo, beatsPerBar, server,
				out, numChannels, amp
			)
		}
	}

	removeMetronome {
		metronome.clear;
		metronome = nil;
	}

	// set recording buffers and update recording sequence accordingly
	activeBuffers_ { |input|
		var bufnums = buffers.collect(_.bufnum);

		activeBuffersSeq ?? {
			activeBuffersSeq = PatternProxy.new;
		};

		input.indexOf(1.0) !? {
			activeBuffers = input;
			activeBuffersSeq.setSource(Pseq(bufnums[activeBuffers.selectIndices{|it, i| it > 0}], inf));
		}
	}

	// run unit tests
	*test {
		TestSNSampler.runTest("TestSNSampler:test_init");
		TestSNSampler.runTest("TestSNSampler:test_start");
	}
}