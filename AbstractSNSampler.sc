AbstractSNSampler {
	classvar /*<>synthDescLib = \SN, */<>oscFeedbackAddr;

	*initClass {
		// grain synth for pattern replay
		SynthDef(\grain, { |bufnum=0, t_trig=0, start=0, end=1, out=0, brate=1, tempo=1, atk=0.1, sust=1, rel=0.7, curve=(-4), gate=1, amp=1.0|
			var env = EnvGen.ar(Env.asr(atk, sust, rel, curve), gate, doneAction: Done.freeSelf);
			var outp = BufRd.ar(
				1, bufnum,
				Phasor.ar(t_trig, BufRateScale.kr(bufnum) * brate * tempo, start * BufFrames.kr(bufnum), end * BufFrames.kr(bufnum))
			);
			OffsetOut.ar(out, outp * env * amp);
		}).add;
	}
}