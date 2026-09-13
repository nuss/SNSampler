SNSamplerGui {
	classvar <all;
	var <sampler, <window, <ins, <recordIns;
	var <startStopButton, <pauseResumeButton, <tempoKnob, <inputLevelKnob, <beatsPerBarNB, <numBufsNB;
	var <serverList, <serverBoot;

	*initClass {
		all = ()
	}

	*new { |sampler, rect|
		if (sampler.isNil or:{ sampler.class !== SNSampler}) {
			Error("A valid MySampler must be provided as first argument to SNSamplerGui.new!").throw;
		};
		^super.newCopyArgs(sampler).init(rect);
	}

	init { |rect|
		var audioIn = SynthDef(\scopeIns, { |bufnum|
			var inBusses = Array.series(sampler.server.options.numInputBusChannels, 0);
			var ins = SoundIn.ar(inBusses)/*.scope(name: \ins)*/;
			ScopeOut2.ar(ins, bufnum);
		}).play(
			target: RootNode(sampler.server),
			args: [\bufnum, sampler.insBuffer],
			addAction: \addToTail
		);

		rect ?? { rect = 800@600 };
		window = Window(sampler.name, rect);
		ins = ScopeView()
		.bufnum_(sampler.insBuffer.bufnum)
		.server_(sampler.server)
		.style_(0);
		recordIns = ScopeView();
		window.layout_(
			HLayout(
				VLayout(
					StaticText().string_("Ins"), ins
				),
				VLayout(
					StaticText().string_("Recording ins"), recordIns
				)
			)
		);
		window.layout.margins = [2, 2, 2, 2];
		[ins, recordIns].do(_.start);
	}

	front { window.front }
}