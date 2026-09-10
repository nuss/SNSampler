AbstractSNSampler {
	classvar /*<>synthDescLib = \SN, */<>oscFeedbackAddr;

	*initClass {
		// grain synth for pattern replay
		SynthDef(\grain, { |bufnum=0, t_trig=0, start=0, end=1, out=0, brate=1, tempo=1, atk=0.1, sust=1, rel=0.7, curve=(-4), gate=1, amp=1.0|
			var env = EnvGen.ar(Env.asr(atk, sust, rel, curve), gate, doneAction: Done.freeSelf);
			var outp = BufRd.ar(
				1, bufnum,
				Phasor.ar(t_trig, BufRateScale.kr(bufnum) * brate * tempo, start * BufFrames.kr(bufnum), end * BufFrames.kr(bufnum)),
				interpolation: 1
			);
			OffsetOut.ar(out, outp * env * amp);
		}).add;

		SynthDef(\dgrain, { |bufnum=0, t_trig=0, ddur=1.0, out=0, brate=1.0, tempo=1.0, atk, sust, rel, curve, gate=1.0, amp|
			var env = DemandEnvGen.ar(
				Dseq(\levels.kr(1!6)),
				Dseq(\durs.kr(1!5)),
				Dseq(\curve.kr(0!5.0)),
				\gate.kr(1),
				\reset.kr(0)
			);

		}).add;
	}
}

/*
(
    {
        var freq;
        freq = DemandEnvGen.kr(
                Dseq([Dseries(400, 200, 5), 500, 800, 530, 4000, 900], 2),
                Dseq([0.2, 0.1, 0.2, 0.3, 0.1], inf),
                Dseq([1, 0, 0, 6, 1, 1, 0, 2], inf), // shapes
                0,
                MouseX.kr > 0.5, // gate
                MouseButton.kr > 0.5, // reset
                doneAction: Done.none
            );
        SinOsc.ar(freq * [1, 1.001]) * 0.1

    }.play;
)
*/