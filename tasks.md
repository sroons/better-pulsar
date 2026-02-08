# Better Pulsar — Tasks

## 1. Multiple formants with independent panning
Run 2-3 pulsar generators at the same fundamental frequency but different formant frequencies, each with independent pan position. This is the signature advanced pulsar synthesis technique from Roads' Section 3.1 — spatially separated formants within a single tone. Dramatically richer spectra and spatial imagery.

## 2. Convolution with samples
Convolve pulsar trains with recorded sounds (Roads' Section 3.3). At infrasonic rates, each pulsar is replaced by a copy of the sample, creating rhythmic patterns. At audio rates, the sample is filtered through the pulsar train's comb-like spectrum. Two effects: filtering imposed by the time-varying pulsar train, and overlapping effects from short fundamental periods.

## 3. Wavetable morphing
Crossfade between pulsaret waveforms continuously rather than switching discretely. Map a CC to blend smoothly between e.g. sine and sinc, or triangle and sine x3. Enables timbral animation that's impossible with discrete waveform switching.

## 4. Time-scale bridging gestures
Automated envelopes that sweep fp from infrasonic (<20 Hz) up into audio rate, so a rhythm accelerates into a pitched tone in a single gesture. This is pulsar synthesis's defining trick — revealing the continuum between rhythm and pitch that is normally hidden.

## 5. Burst masking
Regular on/off masking patterns (e.g., 4 pulsarets on, 2 silent) as described in Roads' Section 3.2. More structured and rhythmic than stochastic masking. Produces AM effects on the timbre and divides the fundamental by a subharmonic factor. Great for the rhythm-to-pitch crossover zone.

## 6. Custom pulsaret loading
Load a short audio sample from a file as the pulsaret waveform. Read from a buffer instead of the pre-built wavetables. Opens up a huge timbral range beyond the five built-in waveforms.

## 7. LFO modulation
Route LFOs to formant frequency, duty cycle, masking, and pan for evolving textures. Norns has a built-in `lfo` library. Adds movement and life to sustained tones without manual CC tweaking.

## 8. Reverb send
Route output through norns' built-in reverb. Adds spatial depth and makes pulsar tones sit in a space. Simple to implement, significant perceptual improvement especially for sparse/rhythmic pulsar trains.

## 9. Polyphony
Multiple simultaneous voices with voice allocation. Enables chords and overlapping notes. Requires voice group management in the SC engine and note stealing/allocation logic in Lua.

## 10. Portamento/glide
Smooth pitch transitions between MIDI notes instead of instant jumps. Just a `Lag` on `hz` in the SynthDef with a controllable glide time. Subtle but expressive for melodic playing.
