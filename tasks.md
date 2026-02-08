# Better Pulsar — Tasks

## 1. Multiple formants with independent panning
Run 2-3 pulsar generators at the same fundamental frequency but different formant frequencies, each with independent pan position. This is the signature advanced pulsar synthesis technique from Roads' Section 3.1 — spatially separated formants within a single tone. Dramatically richer spectra and spatial imagery.

###IMPLEMENTATION DETAILS:
Added new `betterPulsarMulti` SynthDef that runs up to 3 parallel formant generators sharing the same fundamental frequency, pulsaret waveform, and window function. Each formant has its own frequency (formant_hz, formant2_hz, formant3_hz) and pan position (pan, pan2, pan3). The `formant_count` parameter (1-3) controls how many formants are active; when set to 1, the original single-formant synth is used for optimal CPU efficiency. New Lua parameters added under "multi-formant" separator. Default frequencies follow harmonic series (440, 660, 880 Hz) and default pans spread across stereo field (center, left, right).

**Performance Impact:** HIGH. Each additional formant adds approximately 50% CPU overhead due to duplicate buffer reads and signal processing. With 3 formants, expect ~150% more CPU usage than single formant. On RPi4, recommend limiting to 2 formants when using other DSP-heavy features.

## 2. Convolution with samples
Convolve pulsar trains with recorded sounds (Roads' Section 3.3). At infrasonic rates, each pulsar is replaced by a copy of the sample, creating rhythmic patterns. At audio rates, the sample is filtered through the pulsar train's comb-like spectrum. Two effects: filtering imposed by the time-varying pulsar train, and overlapping effects from short fundamental periods.

###IMPLEMENTATION DETAILS:
Added `betterPulsarSample` SynthDef that uses a loaded audio sample as the pulsaret waveform instead of the built-in wavetables. The sample is read at a rate determined by the duty cycle, so the formant frequency controls how much of the sample plays per pulse. At infrasonic fundamental frequencies, each pulse is an audible grain of the sample; at audio rates, the pulsar train creates comb-filtering effects. The `sample_rate` parameter (0.25x-4x) allows pitch-shifting the sample. A file picker in params allows loading any audio file from the norns filesystem. The `use_sample` toggle switches between sample and wavetable modes.

**Performance Impact:** MODERATE. Sample buffer reads are computationally similar to wavetable reads. Primary concern is memory - samples up to 1 second at 48kHz are supported. Recommend using short samples for best results on RPi4. Loading very large samples may cause brief audio glitches.

## 3. Wavetable morphing
Crossfade between pulsaret waveforms continuously rather than switching discretely. Map a CC to blend smoothly between e.g. sine and sinc, or triangle and sine x3. Enables timbral animation that's impossible with discrete waveform switching.

###IMPLEMENTATION DETAILS:
ALREADY IMPLEMENTED prior to task list creation. The pulsaret parameter is continuous (0.0-9.0) with crossfading between adjacent waveforms using LinXFade2. Similarly, the window parameter (0.0-4.0) morphs between adjacent window functions. MIDI CC14 sweeps through all pulsaret waveforms smoothly, CC15 sweeps through windows. The UI displays morphing state (e.g., "sine>sinx" when between sine and sinc waveforms).

**Performance Impact:** LOW. Crossfading requires reading from two buffers instead of one and a simple mix operation - negligible overhead on RPi4.

## 4. Time-scale bridging gestures
Automated envelopes that sweep fp from infrasonic (<20 Hz) up into audio rate, so a rhythm accelerates into a pitched tone in a single gesture. This is pulsar synthesis's defining trick — revealing the continuum between rhythm and pitch that is normally hidden.

###IMPLEMENTATION DETAILS:
Added a Lua-based gesture system with configurable parameters: start frequency (0.5-20 Hz for audible pulses), end frequency (20-500 Hz for pitched tones), duration (0.5-30 seconds), curve type (linear/exponential/logarithmic), and direction (up or down). The bridge is triggered via params menu and runs a clock coroutine at 30Hz that smoothly updates the engine's hz parameter. Can be used while a note is held to transform a rhythmic pulse train into a sustained pitch. Exponential curve sounds most natural for pitch perception.

**Performance Impact:** MINIMAL. The bridge runs in Lua as a clock coroutine at 30 updates/second, sending single OSC messages to the engine. No additional DSP overhead on RPi4.

## 5. Burst masking
Regular on/off masking patterns (e.g., 4 pulsarets on, 2 silent) as described in Roads' Section 3.2. More structured and rhythmic than stochastic masking. Produces AM effects on the timbre and divides the fundamental by a subharmonic factor. Great for the rhythm-to-pitch crossover zone.

###IMPLEMENTATION DETAILS:
Added burst masking mode using PulseCount to track pulse number within a repeating cycle. Parameters: burst_on (1-16 pulses to play) and burst_off (1-16 pulses to silence). The pattern cycles continuously, creating subharmonic division of the fundamental. For example, 4 on / 2 off at 120 Hz creates an effective 20 Hz amplitude modulation (120/(4+2)). A toggle switches between stochastic (random probability) and burst (deterministic pattern) masking modes. Implemented in all three synth variants (single, multi-formant, and sample).

**Performance Impact:** MINIMAL. Uses PulseCount UGen and simple modulo arithmetic. No measurable CPU overhead on RPi4.

## 6. Custom pulsaret loading
Load a short audio sample from a file as the pulsaret waveform. Read from a buffer instead of the pre-built wavetables. Opens up a huge timbral range beyond the five built-in waveforms.

###IMPLEMENTATION DETAILS:
IMPLEMENTED AS PART OF TASK 2. The sample pulsaret feature (use_sample toggle, sample_file picker, sample_rate control) allows loading any audio file as a custom pulsaret. When enabled, the loaded sample replaces the built-in wavetables. The sample_rate parameter (0.25x-4x) allows pitch adjustment. Short samples (< 1 second) work best for typical pulsaret duty cycles.

**Performance Impact:** See Task 2 - MODERATE, primarily memory-related.

## 7. LFO modulation
Route LFOs to formant frequency, duty cycle, masking, and pan for evolving textures. Norns has a built-in `lfo` library. Adds movement and life to sustained tones without manual CC tweaking.

###IMPLEMENTATION DETAILS:
Added four independent LFOs using norns' built-in lfo library: formant (modulates frequency 0.5x-2x around base value), duty cycle (modulates pulse width), masking (modulates omission probability), and pan (auto-pans across stereo field). Each LFO has enable/disable toggle, rate control (0.01-10 Hz), and depth control (0-1). All use sine waveform for smooth modulation. LFOs continue running independent of note state, allowing evolving textures even during sustained notes. Parameters organized under "lfo modulation" separator.

**Performance Impact:** LOW. LFOs run in Lua at norns' default update rate (~60Hz) and send OSC messages to the engine. No additional DSP processing on RPi4. Four simultaneous LFOs add negligible CPU load.

## 8. Reverb send
Route output through norns' built-in reverb. Adds spatial depth and makes pulsar tones sit in a space. Simple to implement, significant perceptual improvement especially for sparse/rhythmic pulsar trains.

###IMPLEMENTATION DETAILS:
Exposed norns' built-in reverb controls through script parameters: reverb mix (dry/wet balance), reverb return (return level from reverb bus), reverb damp (high frequency damping), and reverb size (reverb decay time 0.5-5 seconds). Uses norns audio.level_rev(), audio.rev_return_level(), audio.rev_damp(), and audio.rev_time() functions. Default settings provide subtle ambience; increase mix and size for more dramatic spatial effects.

**Performance Impact:** NONE additional. Norns' reverb runs on a dedicated audio bus regardless of script settings - this simply exposes the controls. The built-in reverb is already optimized for RPi4.

## 9. Polyphony
Multiple simultaneous voices with voice allocation. Enables chords and overlapping notes. Requires voice group management in the SC engine and note stealing/allocation logic in Lua.

###IMPLEMENTATION DETAILS:
Added 4-voice polyphony with voice allocation in the SuperCollider engine. Voice management uses three arrays: `voices` (synth instances), `voiceNotes` (MIDI note for each voice), and `voiceAges` (counter for voice stealing). The `noteOn` command finds a free voice or steals the oldest voice when all are in use. The `noteOff` command now takes a note number argument to release the correct voice in poly mode. Amplitude is scaled by `1/sqrt(numVoices)` in poly mode to prevent clipping when playing chords. A `polyMode` command toggles between mono (0) and poly (1) modes, releasing all voices when switching. Lua script adds mode parameter under "polyphony" separator.

**Performance Impact:** MODERATE-HIGH. In poly mode with 4 voices playing simultaneously, CPU usage is approximately 4x the single-voice usage. Combined with multi-formant mode (3 formants), this could stress RPi4 significantly (12x base CPU). Recommend using single formant mode when playing polyphonic chords, or limiting to 2-voice chords with multi-formant. The amplitude scaling prevents audio clipping but reduces overall volume.

## 10. Portamento/glide
Smooth pitch transitions between MIDI notes instead of instant jumps. Just a `Lag` on `hz` in the SynthDef with a controllable glide time. Subtle but expressive for melodic playing.
