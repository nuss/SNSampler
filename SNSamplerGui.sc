SNSamplerGui {
	classvar <all;
	var <sampler, <window, <insScope, <recordInsScope;
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
		SynthDef(\scopeIns, { |bufnum|
			var inBusses = Array.series(sampler.server.options.numInputBusChannels, 0);
			ScopeOut2.ar(SoundIn.ar(inBusses), bufnum);
		}).play(
			target: RootNode(sampler.server),
			args: [\bufnum, sampler.insBuffer],
			addAction: \addToTail
		);
		SynthDef(\scopeRecIns, { |bufnum|
			var inBusses = Array.series(sampler.server.options.numInputBusChannels, 0);
			ScopeOut2.ar(In.ar(sampler.scopeBus, inBusses.size), bufnum)
		}).play(
			target: RootNode(sampler.server),
			args: [\bufnum, sampler.recInsBuffer],
			addAction: \addToTail
		);

		rect ?? { rect = 800@600 };
		window = Window(sampler.name, rect);
		insScope = ScopeView()
		.bufnum_(sampler.insBuffer.bufnum)
		.server_(sampler.server)
		.style_(0);
		recordInsScope = ScopeView()
		// .bufnum_(sampler.buffers.bufnum)
		.bufnum_(sampler.recInsBuffer.bufnum)
		.server_(sampler.server)
		.style_(0);
		window.layout_(
			HLayout(
				VLayout(
					StaticText().string_("Ins"), insScope
				),
				VLayout(
					StaticText().string_("Recording ins"), recordInsScope
				)
			)
		);
		window.layout.margins = [2, 2, 2, 2];
		[insScope, recordInsScope].do(_.start);
		CmdPeriod.doOnce({
			window.close;
			[sampler.insBuffer, sampler.recInsBuffer].do(_.free);
		})
	}

	front { window.front }
}