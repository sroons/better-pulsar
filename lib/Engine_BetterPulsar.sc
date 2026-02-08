// Engine_BetterPulsar
// Pulsar synthesis engine for Norns
// Based on Curtis Roads' pulsar synthesis techniques

Engine_BetterPulsar : CroneEngine {
    var <synth;
    var <pulsaretBufs;
    var <windowBufs;
    var <sampleBuf;  // Buffer for loaded sample pulsaret

    // Polyphony
    var <voices;        // Array of synth instances
    var <voiceNotes;    // Note number for each voice
    var <voiceAges;     // Age counter for voice stealing
    var <numVoices;
    var <voiceCounter;

    // Stored parameter state (persists across noteOn/noteOff)
    var pFormantHz, pAmp, pPan, pPulsaret, pWindow;
    var pDutyCycle, pUseDutyCycle, pMasking, pAttack, pRelease;
    // Multi-formant parameters
    var pFormant2Hz, pFormant3Hz, pPan2, pPan3, pFormantCount;
    // Sample convolution parameters
    var pUseSample, pSampleRate;
    // Burst masking parameters
    var pBurstOn, pBurstOff, pUseBurst;
    // Polyphony parameters
    var pPolyMode, pGlide;

    *new { arg context, doneCallback;
        ^super.new(context, doneCallback);
    }

    alloc {
        var server = context.server;
        var numSamples = 2048;

        // Pre-allocate buffers for pulsaret waveforms
        pulsaretBufs = Array.fill(10, { Buffer.alloc(server, numSamples, 1) });

        // Pre-allocate buffers for window functions
        windowBufs = Array.fill(5, { Buffer.alloc(server, numSamples, 1) });

        // Sample buffer for loaded pulsaret (up to 1 second at 48kHz)
        sampleBuf = Buffer.alloc(server, 48000, 1);

        server.sync;

        // Fill pulsaret buffers
        // 0: Single sine
        pulsaretBufs[0].sine1([1.0]);

        // 1: Two sine cycles
        pulsaretBufs[1].sine1([0, 1.0]);

        // 2: Three sine cycles
        pulsaretBufs[2].sine1([0, 0, 1.0]);

        // 3: Sinc function (bandlimited impulse)
        pulsaretBufs[3].loadCollection(Array.fill(numSamples, { |i|
            var x = (i - (numSamples/2)) / (numSamples/16);
            if(x == 0, { 1.0 }, { sin(pi * x) / (pi * x) });
        }));

        // 4: Triangle
        pulsaretBufs[4].loadCollection(Array.fill(numSamples, { |i|
            var phase = i / numSamples;
            if(phase < 0.5, { phase * 2 }, { 2 - (phase * 2) }) * 2 - 1;
        }));

        // 5: Sawtooth
        pulsaretBufs[5].loadCollection(Array.fill(numSamples, { |i|
            (i / numSamples) * 2 - 1;
        }));

        // 6: Square
        pulsaretBufs[6].loadCollection(Array.fill(numSamples, { |i|
            if((i / numSamples) < 0.5, { 1.0 }, { -1.0 });
        }));

        // 7: Formant (vocal-like, sum of 3 sine partials)
        pulsaretBufs[7].loadCollection(Array.fill(numSamples, { |i|
            var phase = i / numSamples * 2pi;
            (sin(phase) + (0.5 * sin(2 * phase)) + (0.25 * sin(3 * phase))) / 1.75;
        }));

        // 8: Pulse (narrow, 25% duty)
        pulsaretBufs[8].loadCollection(Array.fill(numSamples, { |i|
            if((i / numSamples) < 0.25, { 1.0 }, { -1.0 });
        }));

        // 9: Noise burst (random values, same each load)
        thisThread.randSeed = 12345;
        pulsaretBufs[9].loadCollection(Array.fill(numSamples, { 1.0.rand2 }));

        server.sync;

        // Fill window buffers
        // 0: Rectangular (no window)
        windowBufs[0].loadCollection(Array.fill(numSamples, { 1.0 }));

        // 1: Gaussian
        windowBufs[1].loadCollection(Array.fill(numSamples, { |i|
            var x = (i - (numSamples/2)) / (numSamples/4);
            exp(-0.5 * x * x);
        }));

        // 2: Hann
        windowBufs[2].loadCollection(Signal.hanningWindow(numSamples));

        // 3: Exponential decay
        windowBufs[3].loadCollection(Array.fill(numSamples, { |i|
            var phase = i / numSamples;
            exp(-4 * phase);
        }));

        // 4: Linear decay
        windowBufs[4].loadCollection(Array.fill(numSamples, { |i|
            1 - (i / numSamples);
        }));

        server.sync;

        // Main pulsar synth
        SynthDef(\betterPulsar, {
            arg out = 0,
                hz = 110,           // fundamental frequency
                formantHz = 440,    // formant frequency
                amp = 0.5,
                pan = 0,
                gate = 1,
                pulsaret = 0,       // pulsaret waveform index (0-4)
                window = 1,         // window function index (0-4)
                dutyCycle = 0.5,    // manual duty cycle override (0-1)
                useDutyCycle = 0,   // 0 = use formant, 1 = use manual duty cycle
                masking = 0,        // probability of pulse omission (0-1)
                maskSeed = 0,       // seed for masking randomness
                attack = 0.001,
                release = 0.1,
                burstOn = 4,        // pulses on in burst pattern
                burstOff = 2,       // pulses off in burst pattern
                useBurst = 0,       // 0 = stochastic masking, 1 = burst masking
                glide = 0;          // portamento time in seconds

            var trig, phase, pulsaretPhase, pulsaretLen, pulsaretSig, windowSig;
            var hzLag;
            var period, actualDuty, silenceRatio, inPulsaret;
            var mask, burstMask, stochasticMask, env, sig;
            var pulsaretBufNum, windowBufNum;
            var pulsaretIdx1, pulsaretIdx2, pulsaretMix;
            var pulsaretSig1, pulsaretSig2;
            var pulsaretBufNum1, pulsaretBufNum2;
            var windowIdx1, windowIdx2, windowMix;
            var windowSig1, windowSig2;
            var windowBufNum1, windowBufNum2;
            var pulseCount, burstPeriod, burstPhase;

            // Apply portamento/glide to hz
            hzLag = Lag.kr(hz, glide);

            // Calculate period and duty cycle
            period = hzLag.reciprocal;

            // Duty cycle: either from formant ratio or manual
            actualDuty = Select.kr(useDutyCycle, [
                (hzLag / formantHz).clip(0.01, 1.0),  // formant-derived
                dutyCycle.clip(0.01, 1.0)            // manual
            ]);

            // Main phase (0-1 over one period at fundamental freq)
            phase = Phasor.ar(0, hzLag * SampleDur.ir, 0, 1);

            // Trigger at start of each cycle
            trig = Trig1.ar(phase < 0.01, SampleDur.ir);

            // Masking: stochastic or burst pattern
            stochasticMask = TRand.ar(0, 1, trig) > masking;

            // Burst masking: count pulses and create on/off pattern
            burstPeriod = burstOn + burstOff;
            pulseCount = PulseCount.ar(trig) % burstPeriod;
            burstMask = pulseCount < burstOn;

            // Select masking mode
            mask = Select.ar(useBurst, [stochasticMask, burstMask]);

            // Determine if we're in the pulsaret portion of the cycle
            inPulsaret = phase < actualDuty;

            // Phase within the pulsaret (0-1), only advances during pulsaret portion
            pulsaretPhase = (phase / actualDuty).clip(0, 0.999);

            // Select buffers with crossfade for pulsaret
            // Calculate crossfade between adjacent waveforms
            pulsaretIdx1 = pulsaret.floor.clip(0, 9);
            pulsaretIdx2 = (pulsaretIdx1 + 1).clip(0, 9);
            pulsaretMix = pulsaret.frac;

            pulsaretBufNum1 = Select.kr(pulsaretIdx1, pulsaretBufs.collect(_.bufnum));
            pulsaretBufNum2 = Select.kr(pulsaretIdx2, pulsaretBufs.collect(_.bufnum));

            // Calculate crossfade between adjacent windows
            windowIdx1 = window.floor.clip(0, 4);
            windowIdx2 = (windowIdx1 + 1).clip(0, 4);
            windowMix = window.frac;

            windowBufNum1 = Select.kr(windowIdx1, windowBufs.collect(_.bufnum));
            windowBufNum2 = Select.kr(windowIdx2, windowBufs.collect(_.bufnum));

            // Read from both pulsaret buffers and crossfade
            pulsaretSig1 = BufRd.ar(1, pulsaretBufNum1, pulsaretPhase * BufFrames.kr(pulsaretBufNum1), 1, 4);
            pulsaretSig2 = BufRd.ar(1, pulsaretBufNum2, pulsaretPhase * BufFrames.kr(pulsaretBufNum2), 1, 4);
            pulsaretSig = LinXFade2.ar(pulsaretSig1, pulsaretSig2, pulsaretMix * 2 - 1);

            // Read from both window buffers and crossfade
            windowSig1 = BufRd.ar(1, windowBufNum1, pulsaretPhase * BufFrames.kr(windowBufNum1), 1, 4);
            windowSig2 = BufRd.ar(1, windowBufNum2, pulsaretPhase * BufFrames.kr(windowBufNum2), 1, 4);
            windowSig = LinXFade2.ar(windowSig1, windowSig2, windowMix * 2 - 1);

            // Apply window to pulsaret
            sig = pulsaretSig * windowSig;

            // Gate during silence portion
            sig = sig * inPulsaret;

            // Apply masking
            sig = sig * Lag.ar(mask, 0.001);

            // Amplitude envelope
            env = EnvGen.ar(Env.asr(attack, 1, release), gate, doneAction: 2);

            // Final output
            sig = sig * env * amp;

            // DC blocking
            sig = LeakDC.ar(sig);

            // Soft clip for safety
            sig = (sig * 0.8).tanh;

            Out.ar(out, Pan2.ar(sig, pan));
        }).add;

        // 2-formant pulsar synth with independent panning
        // CPU: ~2x single formant
        SynthDef(\betterPulsar2, {
            arg out = 0,
                hz = 110,
                formantHz = 440,
                formant2Hz = 660,
                amp = 0.5,
                pan = 0,
                pan2 = -0.5,
                gate = 1,
                pulsaret = 0,
                window = 1,
                dutyCycle = 0.5,
                useDutyCycle = 0,
                masking = 0,
                burstOn = 4,
                burstOff = 2,
                useBurst = 0,
                attack = 0.001,
                release = 0.1,
                glide = 0;

            var phase, trig, env, hzLag;
            var sig1, sig2, sigOut;
            var pulsaretIdx1, pulsaretIdx2, pulsaretMix;
            var pulsaretBufNum1, pulsaretBufNum2;
            var windowIdx1, windowIdx2, windowMix;
            var windowBufNum1, windowBufNum2;
            var stochasticMask, burstMask, mask, pulseCount, burstPeriod;

            hzLag = Lag.kr(hz, glide);
            phase = Phasor.ar(0, hzLag * SampleDur.ir, 0, 1);
            trig = Trig1.ar(phase < 0.01, SampleDur.ir);

            // Shared masking for all formants
            stochasticMask = TRand.ar(0, 1, trig) > masking;
            burstPeriod = burstOn + burstOff;
            pulseCount = PulseCount.ar(trig) % burstPeriod;
            burstMask = pulseCount < burstOn;
            mask = Select.ar(useBurst, [stochasticMask, burstMask]);

            // Buffer selection (shared across formants)
            pulsaretIdx1 = pulsaret.floor.clip(0, 9);
            pulsaretIdx2 = (pulsaretIdx1 + 1).clip(0, 9);
            pulsaretMix = pulsaret.frac;
            pulsaretBufNum1 = Select.kr(pulsaretIdx1, pulsaretBufs.collect(_.bufnum));
            pulsaretBufNum2 = Select.kr(pulsaretIdx2, pulsaretBufs.collect(_.bufnum));

            windowIdx1 = window.floor.clip(0, 4);
            windowIdx2 = (windowIdx1 + 1).clip(0, 4);
            windowMix = window.frac;
            windowBufNum1 = Select.kr(windowIdx1, windowBufs.collect(_.bufnum));
            windowBufNum2 = Select.kr(windowIdx2, windowBufs.collect(_.bufnum));

            // Formant 1
            sig1 = {
                var duty, inPulsaret, pulsaretPhase, pSig, pSig1, pSig2, wSig, wSig1, wSig2, sig;
                duty = Select.kr(useDutyCycle, [(hzLag / formantHz).clip(0.01, 1.0), dutyCycle.clip(0.01, 1.0)]);
                inPulsaret = phase < duty;
                pulsaretPhase = (phase / duty).clip(0, 0.999);
                pSig1 = BufRd.ar(1, pulsaretBufNum1, pulsaretPhase * BufFrames.kr(pulsaretBufNum1), 1, 4);
                pSig2 = BufRd.ar(1, pulsaretBufNum2, pulsaretPhase * BufFrames.kr(pulsaretBufNum2), 1, 4);
                pSig = LinXFade2.ar(pSig1, pSig2, pulsaretMix * 2 - 1);
                wSig1 = BufRd.ar(1, windowBufNum1, pulsaretPhase * BufFrames.kr(windowBufNum1), 1, 4);
                wSig2 = BufRd.ar(1, windowBufNum2, pulsaretPhase * BufFrames.kr(windowBufNum2), 1, 4);
                wSig = LinXFade2.ar(wSig1, wSig2, windowMix * 2 - 1);
                sig = pSig * wSig * inPulsaret * Lag.ar(mask, 0.001);
                Pan2.ar(sig, pan);
            }.value;

            // Formant 2
            sig2 = {
                var duty, inPulsaret, pulsaretPhase, pSig, pSig1, pSig2, wSig, wSig1, wSig2, sig;
                duty = Select.kr(useDutyCycle, [(hzLag / formant2Hz).clip(0.01, 1.0), dutyCycle.clip(0.01, 1.0)]);
                inPulsaret = phase < duty;
                pulsaretPhase = (phase / duty).clip(0, 0.999);
                pSig1 = BufRd.ar(1, pulsaretBufNum1, pulsaretPhase * BufFrames.kr(pulsaretBufNum1), 1, 4);
                pSig2 = BufRd.ar(1, pulsaretBufNum2, pulsaretPhase * BufFrames.kr(pulsaretBufNum2), 1, 4);
                pSig = LinXFade2.ar(pSig1, pSig2, pulsaretMix * 2 - 1);
                wSig1 = BufRd.ar(1, windowBufNum1, pulsaretPhase * BufFrames.kr(windowBufNum1), 1, 4);
                wSig2 = BufRd.ar(1, windowBufNum2, pulsaretPhase * BufFrames.kr(windowBufNum2), 1, 4);
                wSig = LinXFade2.ar(wSig1, wSig2, windowMix * 2 - 1);
                sig = pSig * wSig * inPulsaret * Lag.ar(mask, 0.001);
                Pan2.ar(sig, pan2);
            }.value;

            sigOut = (sig1 + sig2) / 2;
            env = EnvGen.ar(Env.asr(attack, 1, release), gate, doneAction: 2);
            sigOut = LeakDC.ar(sigOut * env * amp);
            sigOut = (sigOut * 0.8).tanh;

            Out.ar(out, sigOut);
        }).add;

        // 3-formant pulsar synth with independent panning
        // CPU: ~3x single formant
        SynthDef(\betterPulsar3, {
            arg out = 0,
                hz = 110,
                formantHz = 440,
                formant2Hz = 660,
                formant3Hz = 880,
                amp = 0.5,
                pan = 0,
                pan2 = -0.5,
                pan3 = 0.5,
                gate = 1,
                pulsaret = 0,
                window = 1,
                dutyCycle = 0.5,
                useDutyCycle = 0,
                masking = 0,
                burstOn = 4,
                burstOff = 2,
                useBurst = 0,
                attack = 0.001,
                release = 0.1,
                glide = 0;

            var phase, trig, env, hzLag;
            var sig1, sig2, sig3, sigOut;
            var pulsaretIdx1, pulsaretIdx2, pulsaretMix;
            var pulsaretBufNum1, pulsaretBufNum2;
            var windowIdx1, windowIdx2, windowMix;
            var windowBufNum1, windowBufNum2;
            var stochasticMask, burstMask, mask, pulseCount, burstPeriod;

            hzLag = Lag.kr(hz, glide);
            phase = Phasor.ar(0, hzLag * SampleDur.ir, 0, 1);
            trig = Trig1.ar(phase < 0.01, SampleDur.ir);

            // Shared masking for all formants
            stochasticMask = TRand.ar(0, 1, trig) > masking;
            burstPeriod = burstOn + burstOff;
            pulseCount = PulseCount.ar(trig) % burstPeriod;
            burstMask = pulseCount < burstOn;
            mask = Select.ar(useBurst, [stochasticMask, burstMask]);

            // Buffer selection (shared across formants)
            pulsaretIdx1 = pulsaret.floor.clip(0, 9);
            pulsaretIdx2 = (pulsaretIdx1 + 1).clip(0, 9);
            pulsaretMix = pulsaret.frac;
            pulsaretBufNum1 = Select.kr(pulsaretIdx1, pulsaretBufs.collect(_.bufnum));
            pulsaretBufNum2 = Select.kr(pulsaretIdx2, pulsaretBufs.collect(_.bufnum));

            windowIdx1 = window.floor.clip(0, 4);
            windowIdx2 = (windowIdx1 + 1).clip(0, 4);
            windowMix = window.frac;
            windowBufNum1 = Select.kr(windowIdx1, windowBufs.collect(_.bufnum));
            windowBufNum2 = Select.kr(windowIdx2, windowBufs.collect(_.bufnum));

            // Formant 1
            sig1 = {
                var duty, inPulsaret, pulsaretPhase, pSig, pSig1, pSig2, wSig, wSig1, wSig2, sig;
                duty = Select.kr(useDutyCycle, [(hzLag / formantHz).clip(0.01, 1.0), dutyCycle.clip(0.01, 1.0)]);
                inPulsaret = phase < duty;
                pulsaretPhase = (phase / duty).clip(0, 0.999);
                pSig1 = BufRd.ar(1, pulsaretBufNum1, pulsaretPhase * BufFrames.kr(pulsaretBufNum1), 1, 4);
                pSig2 = BufRd.ar(1, pulsaretBufNum2, pulsaretPhase * BufFrames.kr(pulsaretBufNum2), 1, 4);
                pSig = LinXFade2.ar(pSig1, pSig2, pulsaretMix * 2 - 1);
                wSig1 = BufRd.ar(1, windowBufNum1, pulsaretPhase * BufFrames.kr(windowBufNum1), 1, 4);
                wSig2 = BufRd.ar(1, windowBufNum2, pulsaretPhase * BufFrames.kr(windowBufNum2), 1, 4);
                wSig = LinXFade2.ar(wSig1, wSig2, windowMix * 2 - 1);
                sig = pSig * wSig * inPulsaret * Lag.ar(mask, 0.001);
                Pan2.ar(sig, pan);
            }.value;

            // Formant 2
            sig2 = {
                var duty, inPulsaret, pulsaretPhase, pSig, pSig1, pSig2, wSig, wSig1, wSig2, sig;
                duty = Select.kr(useDutyCycle, [(hzLag / formant2Hz).clip(0.01, 1.0), dutyCycle.clip(0.01, 1.0)]);
                inPulsaret = phase < duty;
                pulsaretPhase = (phase / duty).clip(0, 0.999);
                pSig1 = BufRd.ar(1, pulsaretBufNum1, pulsaretPhase * BufFrames.kr(pulsaretBufNum1), 1, 4);
                pSig2 = BufRd.ar(1, pulsaretBufNum2, pulsaretPhase * BufFrames.kr(pulsaretBufNum2), 1, 4);
                pSig = LinXFade2.ar(pSig1, pSig2, pulsaretMix * 2 - 1);
                wSig1 = BufRd.ar(1, windowBufNum1, pulsaretPhase * BufFrames.kr(windowBufNum1), 1, 4);
                wSig2 = BufRd.ar(1, windowBufNum2, pulsaretPhase * BufFrames.kr(windowBufNum2), 1, 4);
                wSig = LinXFade2.ar(wSig1, wSig2, windowMix * 2 - 1);
                sig = pSig * wSig * inPulsaret * Lag.ar(mask, 0.001);
                Pan2.ar(sig, pan2);
            }.value;

            // Formant 3
            sig3 = {
                var duty, inPulsaret, pulsaretPhase, pSig, pSig1, pSig2, wSig, wSig1, wSig2, sig;
                duty = Select.kr(useDutyCycle, [(hzLag / formant3Hz).clip(0.01, 1.0), dutyCycle.clip(0.01, 1.0)]);
                inPulsaret = phase < duty;
                pulsaretPhase = (phase / duty).clip(0, 0.999);
                pSig1 = BufRd.ar(1, pulsaretBufNum1, pulsaretPhase * BufFrames.kr(pulsaretBufNum1), 1, 4);
                pSig2 = BufRd.ar(1, pulsaretBufNum2, pulsaretPhase * BufFrames.kr(pulsaretBufNum2), 1, 4);
                pSig = LinXFade2.ar(pSig1, pSig2, pulsaretMix * 2 - 1);
                wSig1 = BufRd.ar(1, windowBufNum1, pulsaretPhase * BufFrames.kr(windowBufNum1), 1, 4);
                wSig2 = BufRd.ar(1, windowBufNum2, pulsaretPhase * BufFrames.kr(windowBufNum2), 1, 4);
                wSig = LinXFade2.ar(wSig1, wSig2, windowMix * 2 - 1);
                sig = pSig * wSig * inPulsaret * Lag.ar(mask, 0.001);
                Pan2.ar(sig, pan3);
            }.value;

            sigOut = (sig1 + sig2 + sig3) / 3;
            env = EnvGen.ar(Env.asr(attack, 1, release), gate, doneAction: 2);
            sigOut = LeakDC.ar(sigOut * env * amp);
            sigOut = (sigOut * 0.8).tanh;

            Out.ar(out, sigOut);
        }).add;

        // Sample-based pulsar synth (Roads Section 3.3)
        // Uses loaded sample as pulsaret waveform
        // At infrasonic rates: each pulse plays a copy of the sample
        // At audio rates: sample is filtered through pulsar train's comb spectrum
        SynthDef(\betterPulsarSample, {
            arg out = 0,
                hz = 110,
                formantHz = 440,
                amp = 0.5,
                pan = 0,
                gate = 1,
                window = 1,
                dutyCycle = 0.5,
                useDutyCycle = 0,
                masking = 0,
                burstOn = 4,
                burstOff = 2,
                useBurst = 0,
                attack = 0.001,
                release = 0.1,
                sampleRate = 1.0,  // Playback rate multiplier
                glide = 0;

            var phase, trig, actualDuty, inPulsaret, pulsaretPhase, hzLag;
            var sampleSig, windowSig, windowSig1, windowSig2;
            var windowIdx1, windowIdx2, windowMix, windowBufNum1, windowBufNum2;
            var stochasticMask, burstMask, mask, pulseCount, burstPeriod;
            var env, sig;
            var sampleFrames, samplePhase;

            // Apply portamento/glide to hz
            hzLag = Lag.kr(hz, glide);

            actualDuty = Select.kr(useDutyCycle, [
                (hzLag / formantHz).clip(0.01, 1.0),
                dutyCycle.clip(0.01, 1.0)
            ]);

            phase = Phasor.ar(0, hzLag * SampleDur.ir, 0, 1);
            trig = Trig1.ar(phase < 0.01, SampleDur.ir);

            // Masking with burst support
            stochasticMask = TRand.ar(0, 1, trig) > masking;
            burstPeriod = burstOn + burstOff;
            pulseCount = PulseCount.ar(trig) % burstPeriod;
            burstMask = pulseCount < burstOn;
            mask = Lag.ar(Select.ar(useBurst, [stochasticMask, burstMask]), 0.001);
            inPulsaret = phase < actualDuty;
            pulsaretPhase = (phase / actualDuty).clip(0, 0.999);

            // Read from sample buffer
            sampleFrames = BufFrames.kr(sampleBuf.bufnum);
            samplePhase = pulsaretPhase * sampleFrames * sampleRate;
            sampleSig = BufRd.ar(1, sampleBuf.bufnum, samplePhase.clip(0, sampleFrames - 1), 0, 4);

            // Window morphing
            windowIdx1 = window.floor.clip(0, 4);
            windowIdx2 = (windowIdx1 + 1).clip(0, 4);
            windowMix = window.frac;
            windowBufNum1 = Select.kr(windowIdx1, windowBufs.collect(_.bufnum));
            windowBufNum2 = Select.kr(windowIdx2, windowBufs.collect(_.bufnum));
            windowSig1 = BufRd.ar(1, windowBufNum1, pulsaretPhase * BufFrames.kr(windowBufNum1), 1, 4);
            windowSig2 = BufRd.ar(1, windowBufNum2, pulsaretPhase * BufFrames.kr(windowBufNum2), 1, 4);
            windowSig = LinXFade2.ar(windowSig1, windowSig2, windowMix * 2 - 1);

            sig = sampleSig * windowSig * inPulsaret * mask;
            env = EnvGen.ar(Env.asr(attack, 1, release), gate, doneAction: 2);
            sig = LeakDC.ar(sig * env * amp).tanh;

            Out.ar(out, Pan2.ar(sig, pan));
        }).add;

        // Simpler, more efficient pulsar synth for when CPU is constrained
        SynthDef(\betterPulsarLite, {
            arg out = 0,
                hz = 110,
                formantHz = 440,
                amp = 0.5,
                pan = 0,
                gate = 1,
                window = 1,
                masking = 0;

            var trig, phase, duty, inPulsaret;
            var pulsaretPhase, sig, windowSig, mask, env;
            var windowBufNum;

            duty = (hz / formantHz).clip(0.01, 1.0);
            phase = Phasor.ar(0, hz * SampleDur.ir, 0, 1);
            trig = Trig1.ar(phase < 0.01, SampleDur.ir);

            mask = TRand.ar(0, 1, trig) > masking;
            inPulsaret = phase < duty;
            pulsaretPhase = (phase / duty).clip(0, 0.999);

            // Simple sine pulsaret
            sig = SinOsc.ar(0, pulsaretPhase * 2pi);

            // Window from buffer
            windowBufNum = Select.kr(window.round, windowBufs.collect(_.bufnum));
            windowSig = BufRd.ar(1, windowBufNum, pulsaretPhase * BufFrames.kr(windowBufNum), 1, 4);

            sig = sig * windowSig * inPulsaret * Lag.ar(mask, 0.001);

            env = EnvGen.ar(Env.asr(0.001, 1, 0.1), gate, doneAction: 2);
            sig = LeakDC.ar(sig * env * amp).tanh;

            Out.ar(out, Pan2.ar(sig, pan));
        }).add;

        server.sync;

        // Initialize parameter defaults
        pFormantHz = 440;
        pAmp = 0.5;
        pPan = 0;
        pPulsaret = 0;
        pWindow = 1;
        pDutyCycle = 0.5;
        pUseDutyCycle = 0;
        pMasking = 0;
        pAttack = 0.001;
        pRelease = 0.1;
        // Multi-formant defaults
        pFormant2Hz = 660;
        pFormant3Hz = 880;
        pPan2 = -0.5;
        pPan3 = 0.5;
        pFormantCount = 1;
        // Sample defaults
        pUseSample = 0;
        pSampleRate = 1.0;
        // Burst defaults
        pBurstOn = 4;
        pBurstOff = 2;
        pUseBurst = 0;
        // Polyphony defaults
        pPolyMode = 0;  // 0 = mono, 1 = poly
        pGlide = 0.0;
        numVoices = 4;
        voices = Array.fill(numVoices, { nil });
        voiceNotes = Array.fill(numVoices, { -1 });
        voiceAges = Array.fill(numVoices, { 0 });
        voiceCounter = 0;

        // Commands
        this.addCommand(\noteOn, "ff", { arg msg;
            var note = msg[1];
            var vel = msg[2];
            var synthName, voiceIdx, newSynth;

            // Select synth based on mode - separate SynthDefs for CPU efficiency
            synthName = case
                { pUseSample > 0 } { \betterPulsarSample }
                { pFormantCount == 3 } { \betterPulsar3 }
                { pFormantCount == 2 } { \betterPulsar2 }
                { true } { \betterPulsar };

            if(pPolyMode == 0, {
                // Mono mode
                if(synth.notNil, { synth.set(\gate, 0) });
                synth = Synth(synthName, [
                    \out, context.out_b,
                    \hz, note.midicps,
                    \formantHz, pFormantHz,
                    \formant2Hz, pFormant2Hz,
                    \formant3Hz, pFormant3Hz,
                    \amp, vel / 127 * 0.7,
                    \pan, pPan,
                    \pan2, pPan2,
                    \pan3, pPan3,
                    \formantCount, pFormantCount,
                    \pulsaret, pPulsaret,
                    \window, pWindow,
                    \dutyCycle, pDutyCycle,
                    \useDutyCycle, pUseDutyCycle,
                    \masking, pMasking,
                    \attack, pAttack,
                    \release, pRelease,
                    \sampleRate, pSampleRate,
                    \burstOn, pBurstOn,
                    \burstOff, pBurstOff,
                    \useBurst, pUseBurst,
                    \glide, pGlide,
                    \gate, 1
                ], context.xg);
            }, {
                // Poly mode - find free voice or steal oldest
                voiceIdx = nil;

                // First, look for free voice
                numVoices.do({ |i|
                    if(voiceIdx.isNil && voiceNotes[i] == -1, {
                        voiceIdx = i;
                    });
                });

                // If no free voice, steal oldest (lowest age)
                if(voiceIdx.isNil, {
                    var minAge = inf;
                    numVoices.do({ |i|
                        if(voiceAges[i] < minAge, {
                            minAge = voiceAges[i];
                            voiceIdx = i;
                        });
                    });
                    // Release stolen voice
                    if(voices[voiceIdx].notNil, {
                        voices[voiceIdx].set(\gate, 0);
                    });
                });

                // Create new synth on this voice
                voiceCounter = voiceCounter + 1;
                voiceAges[voiceIdx] = voiceCounter;
                voiceNotes[voiceIdx] = note;

                newSynth = Synth(synthName, [
                    \out, context.out_b,
                    \hz, note.midicps,
                    \formantHz, pFormantHz,
                    \formant2Hz, pFormant2Hz,
                    \formant3Hz, pFormant3Hz,
                    \amp, vel / 127 * 0.7 / numVoices.sqrt,  // Scale amplitude for polyphony
                    \pan, pPan,
                    \pan2, pPan2,
                    \pan3, pPan3,
                    \formantCount, pFormantCount,
                    \pulsaret, pPulsaret,
                    \window, pWindow,
                    \dutyCycle, pDutyCycle,
                    \useDutyCycle, pUseDutyCycle,
                    \masking, pMasking,
                    \attack, pAttack,
                    \release, pRelease,
                    \sampleRate, pSampleRate,
                    \burstOn, pBurstOn,
                    \burstOff, pBurstOff,
                    \useBurst, pUseBurst,
                    \glide, pGlide,
                    \gate, 1
                ], context.xg);
                voices[voiceIdx] = newSynth;
            });
        });

        this.addCommand(\noteOff, "i", { arg msg;
            var note = msg[1];
            if(pPolyMode == 0, {
                // Mono mode
                if(synth.notNil, { synth.set(\gate, 0) });
            }, {
                // Poly mode - find and release voice playing this note
                numVoices.do({ |i|
                    if(voiceNotes[i] == note, {
                        if(voices[i].notNil, {
                            voices[i].set(\gate, 0);
                        });
                        voiceNotes[i] = -1;
                    });
                });
            });
        });

        // Legacy noteOff without note argument (mono compatibility)
        this.addCommand(\noteOffMono, "", { arg msg;
            if(synth.notNil, { synth.set(\gate, 0) });
        });

        this.addCommand(\hz, "f", { arg msg;
            var val = msg[1];
            if(synth.notNil, { synth.set(\hz, val) });
            numVoices.do({ |i| if(voices[i].notNil, { voices[i].set(\hz, val) }) });
        });

        this.addCommand(\formantHz, "f", { arg msg;
            var val = msg[1];
            pFormantHz = val;
            if(synth.notNil, { synth.set(\formantHz, val) });
            numVoices.do({ |i| if(voices[i].notNil, { voices[i].set(\formantHz, val) }) });
        });

        this.addCommand(\amp, "f", { arg msg;
            var val = msg[1];
            pAmp = val;
            if(synth.notNil, { synth.set(\amp, val) });
            numVoices.do({ |i| if(voices[i].notNil, { voices[i].set(\amp, val) }) });
        });

        this.addCommand(\pan, "f", { arg msg;
            var val = msg[1];
            pPan = val;
            if(synth.notNil, { synth.set(\pan, val) });
            numVoices.do({ |i| if(voices[i].notNil, { voices[i].set(\pan, val) }) });
        });

        this.addCommand(\pulsaret, "f", { arg msg;
            var val = msg[1];
            pPulsaret = val;
            if(synth.notNil, { synth.set(\pulsaret, val) });
            numVoices.do({ |i| if(voices[i].notNil, { voices[i].set(\pulsaret, val) }) });
        });

        this.addCommand(\window, "f", { arg msg;
            var val = msg[1];
            pWindow = val;
            if(synth.notNil, { synth.set(\window, val) });
            numVoices.do({ |i| if(voices[i].notNil, { voices[i].set(\window, val) }) });
        });

        this.addCommand(\dutyCycle, "f", { arg msg;
            var val = msg[1];
            pDutyCycle = val;
            if(synth.notNil, { synth.set(\dutyCycle, val) });
            numVoices.do({ |i| if(voices[i].notNil, { voices[i].set(\dutyCycle, val) }) });
        });

        this.addCommand(\useDutyCycle, "i", { arg msg;
            var val = msg[1];
            pUseDutyCycle = val;
            if(synth.notNil, { synth.set(\useDutyCycle, val) });
            numVoices.do({ |i| if(voices[i].notNil, { voices[i].set(\useDutyCycle, val) }) });
        });

        this.addCommand(\masking, "f", { arg msg;
            var val = msg[1];
            pMasking = val;
            if(synth.notNil, { synth.set(\masking, val) });
            numVoices.do({ |i| if(voices[i].notNil, { voices[i].set(\masking, val) }) });
        });

        this.addCommand(\attack, "f", { arg msg;
            var val = msg[1];
            pAttack = val;
            if(synth.notNil, { synth.set(\attack, val) });
            numVoices.do({ |i| if(voices[i].notNil, { voices[i].set(\attack, val) }) });
        });

        this.addCommand(\release, "f", { arg msg;
            var val = msg[1];
            pRelease = val;
            if(synth.notNil, { synth.set(\release, val) });
            numVoices.do({ |i| if(voices[i].notNil, { voices[i].set(\release, val) }) });
        });

        // Multi-formant commands
        this.addCommand(\formant2Hz, "f", { arg msg;
            var val = msg[1];
            pFormant2Hz = val;
            if(synth.notNil, { synth.set(\formant2Hz, val) });
            numVoices.do({ |i| if(voices[i].notNil, { voices[i].set(\formant2Hz, val) }) });
        });

        this.addCommand(\formant3Hz, "f", { arg msg;
            var val = msg[1];
            pFormant3Hz = val;
            if(synth.notNil, { synth.set(\formant3Hz, val) });
            numVoices.do({ |i| if(voices[i].notNil, { voices[i].set(\formant3Hz, val) }) });
        });

        this.addCommand(\pan2, "f", { arg msg;
            var val = msg[1];
            pPan2 = val;
            if(synth.notNil, { synth.set(\pan2, val) });
            numVoices.do({ |i| if(voices[i].notNil, { voices[i].set(\pan2, val) }) });
        });

        this.addCommand(\pan3, "f", { arg msg;
            var val = msg[1];
            pPan3 = val;
            if(synth.notNil, { synth.set(\pan3, val) });
            numVoices.do({ |i| if(voices[i].notNil, { voices[i].set(\pan3, val) }) });
        });

        this.addCommand(\formantCount, "i", { arg msg;
            pFormantCount = msg[1];
            // Note: changing formant count requires note retrigger to switch synth
        });

        // Sample commands
        this.addCommand(\loadSample, "s", { arg msg;
            var path = msg[1].asString;
            Buffer.read(context.server, path, action: { |buf|
                // Copy loaded buffer data to our pre-allocated sample buffer
                sampleBuf.free;
                sampleBuf = buf;
                ("Loaded sample: " ++ path).postln;
            });
        });

        this.addCommand(\useSample, "i", { arg msg;
            pUseSample = msg[1];
            // Note: changing mode requires note retrigger to switch synth
        });

        this.addCommand(\sampleRate, "f", { arg msg;
            var val = msg[1];
            pSampleRate = val;
            if(synth.notNil, { synth.set(\sampleRate, val) });
            numVoices.do({ |i| if(voices[i].notNil, { voices[i].set(\sampleRate, val) }) });
        });

        // Burst masking commands
        this.addCommand(\burstOn, "i", { arg msg;
            var val = msg[1];
            pBurstOn = val;
            if(synth.notNil, { synth.set(\burstOn, val) });
            numVoices.do({ |i| if(voices[i].notNil, { voices[i].set(\burstOn, val) }) });
        });

        this.addCommand(\burstOff, "i", { arg msg;
            var val = msg[1];
            pBurstOff = val;
            if(synth.notNil, { synth.set(\burstOff, val) });
            numVoices.do({ |i| if(voices[i].notNil, { voices[i].set(\burstOff, val) }) });
        });

        this.addCommand(\useBurst, "i", { arg msg;
            var val = msg[1];
            pUseBurst = val;
            if(synth.notNil, { synth.set(\useBurst, val) });
            numVoices.do({ |i| if(voices[i].notNil, { voices[i].set(\useBurst, val) }) });
        });

        // Polyphony commands
        this.addCommand(\polyMode, "i", { arg msg;
            pPolyMode = msg[1];
            // When switching modes, release all voices
            if(synth.notNil, { synth.set(\gate, 0); synth = nil; });
            numVoices.do({ |i|
                if(voices[i].notNil, {
                    voices[i].set(\gate, 0);
                    voices[i] = nil;
                });
                voiceNotes[i] = -1;
                voiceAges[i] = 0;
            });
            voiceCounter = 0;
        });

        this.addCommand(\glide, "f", { arg msg;
            pGlide = msg[1];
            if(synth.notNil, { synth.set(\glide, msg[1]) });
            // Update all poly voices too
            numVoices.do({ |i|
                if(voices[i].notNil, {
                    voices[i].set(\glide, msg[1]);
                });
            });
        });

        // No initial synth — first noteOn creates it.
        // params:bang() in Lua will store values here via commands above.
        synth = nil;
    }

    free {
        // Free mono synth
        if(synth.notNil, { synth.free });
        // Free all poly voices
        numVoices.do({ |i|
            if(voices[i].notNil, { voices[i].free });
        });
        // Free buffers
        pulsaretBufs.do({ |buf| buf.free });
        windowBufs.do({ |buf| buf.free });
        if(sampleBuf.notNil, { sampleBuf.free });
    }
}
