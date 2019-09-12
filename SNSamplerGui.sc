SNSamplerGui {
	classvar <all;
	var <sampler, <window, left, top, width, height;
	var <startStopButton, <pauseResumeButton, <tempoKnob, <inputLevelKnob, <beatsPerBarNB, <numBufsNB;
	var <serverList, <serverBoot;

	*new { |sampler, window, left, top, width, height|
		if (sampler.isNil or:{ sampler.class !== SNSampler}) {
			Error("A valid MySampler must be provided as first argument to MySamplerGui.new!").throw;
		};
		^super.newCopyArgs(sampler, window, left, top, width, height).init;
	}

	init {
		var samplerName = sampler.name.asString;
		var warningString = "The CVCenter widget '%' seems to have been removed!";
		var servers = Server.all.asArray;
		var serverNames = servers.collect(_.name);
		var tempoText, inputLevelText;
		var tempoGroup, inputGroup;

		all ?? { all = () };
		all.put(sampler.name.asSymbol, this);
		left ?? { left = 0 };
		top ?? { top = 0 };
		width ?? { width = 500 };
		height ?? { height = 400 };
		if (window.isNil or:{ window.isClosed }) {
			window = Window(sampler.name, Rect(top, left, width, height), false);
		};

		serverList = PopUpMenu()
		.items_(serverNames)
		.value_(servers.indexOf(sampler.server ? Server.default))
		.action_({ |p| sampler.server_(p.item) });

		serverBoot = Button().states_([
			["boot server", Color.white, Color.blue],
			["quit server", Color.black, Color(1.0, 0.65, 0.1)]
		]).action_({ |b|
			switch (b.value,
				1, {
					sampler.server.boot;
					sampler.start;
				},
				0, {
					sampler.stop;
					sampler.server.quit;
				}
			)
		}).value_(sampler.server.serverRunning.asInteger);

		pauseResumeButton = Button().states_([
			["resume", Color.black, Color.green],
			["pause", Color.white, Color.red]
		]);
		if (CVCenter.at((samplerName + "on/off").asSymbol).notNil) {
			CVCenter.at((samplerName + "on/off").asSymbol).connect(pauseResumeButton);
		} {
			warningString.format(samplerName + "on/off").warn;
		};

		tempoKnob = Knob().mode_(\vert);
		if (CVCenter.at((samplerName + "tempo").asSymbol).notNil) {
			CVCenter.at((samplerName + "tempo").asSymbol).connect(tempoKnob);
		} {
			warningString.format(samplerName + "tempo").warn;
		};

		tempoText = StaticText().string_("tempo");

		tempoGroup = VLayout(tempoKnob, tempoText);

		inputLevelKnob = Knob().mode_(\vert);
		if (CVCenter.at((samplerName + "level").asSymbol).notNil) {
			CVCenter.at((samplerName + "level").asSymbol).connect(inputLevelKnob);
		} {
			warningString.format(samplerName + "level").warn;
		};

		inputLevelText = StaticText().string_("input level");

		inputGroup = VLayout(inputLevelKnob, inputLevelText);

		beatsPerBarNB = NumberBox().step_(1.0).clipLo_(1).action_({ |nb|
			sampler.allocateBuffers(nb.value.asInteger);
		});

		window.layout = VLayout(
			HLayout(serverList, serverBoot),
			HLayout(pauseResumeButton, beatsPerBarNB, tempoGroup, inputGroup)
		);

		window.front;
	}
}