AbstractSNSampler {
	classvar /*<>synthDescLib = \SN, */<>oscFeedbackAddr;

	*initClass {
		// grain synth for pattern replay
		SynthDef(\grain, { |bufnum=0, t_trig=0, start=0, end=1, out=0, rate=1, tempo=1, atk=0.1, sust=1, rel=0.7, curve=(-4), gate=1|
			var env = EnvGen.ar(Env.asr(atk, sust, rel, curve), gate, doneAction: 2);
			var outp = BufRd.ar(
				1, bufnum,
				Phasor.ar(t_trig, BufRateScale.kr(bufnum) * rate * tempo, start * BufFrames.kr(bufnum), end * BufFrames.kr(bufnum)),
				BufFrames.kr(bufnum)
			);
			Out.ar(out, outp * env * \grainAmp.kr(0.5));
		}).add;
	}

	// handle widget creation in CVCenter
	cvCenterAddWidget { |suffix="", value, spec, func, midiMode, softWithin|
		var wdgtName = (this.name.asString ++ suffix).asSymbol;
		CVCenter.all[wdgtName] ?? {
			CVCenter.use(wdgtName, spec, value, this.name);
			func !? {
				if (func.class == String) {
					func = func.interpret;
				};
				if (func.isFunction) {
					CVCenter.addActionAt(wdgtName, suffix, func);
				}
			}
		};

		switch (CVCenter.cvWidgets[wdgtName].class,
			CVWidgetKnob, {
				midiMode !? {
					CVCenter.cvWidgets[wdgtName].setMidiMode(midiMode);
				};
				softWithin !? {
					CVCenter.cvWidgets[wdgtName].setSoftWithin(softWithin);
				};
			},
			CVWidget2D, {
				#[lo, hi].do{ |sl|
					midiMode !? { CVCenter.cvWidgets[wdgtName].setMidiMode(midiMode, sl) };
					softWithin !? { CVCenter.cvWidgets[wdgtName].setSoftWithin(softWithin, sl) };
				}
			},
			CVWidgetMS, {
				CVenter.at(wdgtName).spec.size.do { |i|
					midiMode !? { CVCenter.cvWidgets[wdgtName].setMidiMode(midiMode, i) };
					softWithin !? { CVCenter.cvWidgets[wdgtName].setSoftWithin(softWithin, i) };
				}
			}
		)

		^CVCenter.cvWidgets[wdgtName];
	}
}