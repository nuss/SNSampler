SNMetronome : AbstractSNSampler {
	classvar <all;
	var <parent, <clock, <tempo, <beatsPerBar, <server;
	var <name;
	var pdef;

	*new { |parent, clock, tempo=1, beatsPerBar=4, server, out=0, numChannels=1, amp=1|
		^super.newCopyArgs(parent, clock, tempo, beatsPerBar, server).init(out, numChannels, amp);
	}

	init { |out, numChannels, amp|
		all ?? { all = () };
		if (parent.isNil or:{ parent.respondsTo(\name).not }) {
			"no name given or parent does not understand 'name'".postln;
			name = ("metronome" + (all.size + 1)).asSymbol;
		} {
			if (all.includesKey(parent.name.asSymbol)) {
				Error("A metronome under the given name already exists").throw;
			} { name = parent.name };
		};
		all.put(name.asSymbol, this);
		clock ?? {
			// create a new clock, compensating latency
			clock = TempoClock(tempo, 0, server.latency.neg);
		};

		server.bind {
			var wdgtFunc;

			SynthDescLib.at(this.class.synthDescLib) ?? {
				// store the metronome in a separate SynthDescLib in order to avoid name clashes
				SynthDescLib(this.class.synthDescLib, [server]);
			};
			SynthDef(name.asSymbol, {
				var env = EnvGen.ar(Env.perc(0.001, 0.1), doneAction: 2);
				Out.ar(\out.kr(out), SinOsc.ar(\freq.kr(330) ! numChannels, mul: env * \baseAmp.kr * \amp.kr(amp)));
			}).add(this.class.synthDescLib);
			server.sync;
			pdef = this.schedule;
			Pdef.all.detect({ |p| pdef === p }).key.postln;
			pdef.play(clock);
			// add a CVWidget for pausing/resuming the metronome
			wdgtFunc = "{ |cv|
				if (cv.input > 0) {
					SNMetronome.all['%'].schedule;
					Pdef('%').resume;
				} { Pdef('%').pause }
			}".format(name, name, name);
			this.cvCenterAddWidget(" metro on/off", 0, #[0, 1, \lin, 1.0], wdgtFunc, 0, 0);
		};
	}

	clear {
		Pdef(name).clear;
		all.removeAt(name);
	}

	schedule { |post=false, out=0, amp=0, tabName|
		var trace;

		tabName ?? {
			tabName = name.asSymbol;
		};

		trace ?? { trace = PatternProxy.new.quant_([beatsPerBar, 0, 0, 1]) };
		if (post) {
			trace.setSource(Pfunc { "metronome" + name + "beat:" + clock.beatInBar }.trace);
		} {
			trace.setSource(0);
		};

		^Pdef(tabName,
			Pbind(
				\synthLib, SynthDescLib.all[this.class.synthDescLib],
				\instrument, name.asSymbol,
				\freq, Pseq([440] ++ (330 ! (beatsPerBar - 1)), inf),
				\dur, 1,
				\baseAmp, Pseq([1] ++ (0.7 ! (beatsPerBar - 1)), inf),
				\amp, CVCenter.use((name.asString + "metroAmp").asSymbol, value: amp, tab: tabName),
				\out, out,
				\trace, trace
			)
		).quant_([beatsPerBar, 0, 0, 1])
	}

}