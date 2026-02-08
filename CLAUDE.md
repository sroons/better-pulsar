# Pulsar Synthesis Project - Knowledge Base

## Reference: "Sound Composition with Pulsars" by Curtis Roads (AES, 2001)

Source PDF: `/Users/seanrooney/Dev/pulsar/SoundCompwithPulsars.pdf`
Published in: J. Audio Eng. Soc., Vol. 49, No. 3, 2001 March

Curtis Roads, AES Honorary Member
Center for Research in Electronic Art Technology (CREATE), Media Arts and Technology Program and Department of Music, University of California, Santa Barbara, CA 93106, USA

---

### Abstract

Pulsar synthesis (PS) is a method of electronic music synthesis based on the generation of trains of sonic particles. It can produce either rhythms or tones as it crosses perceptual time spans. The basic method generates sounds similar to vintage electronic music sonorities. Advanced techniques include multiple formant and fundamental frequency control, pulsar masking, and convolution with sampled sounds. Together with Alberto de Campo, the author designed a program for pulsar synthesis called PulsarGenerator. Applications of pulsar synthesis in compositions by the author are noted.

---

### 0. Introduction

- In July 1997, a sound from a distant astronomical source was detected at the radio telescope at Jodrell Bank in the form of a series of periodic magnetic impulses separated by 1.187 seconds. Scientists recognized the source as a rotating neutron star (pulsar).
- All forms of music composition — from the freely improvised to the formally organized — are constrained by the available means of sound production. Innovations in the domain of sound come from a desire to enrich compositional possibilities.
- Pulsar synthesis melds established principles within a new paradigm. In its basic form, it generates electronic pulses and pitched tones similar to those produced by vintage analog instruments such as the Ondes Martenot and the Hohner Elektronium (introduced in 1950s).
- The technique, however, goes beyond vintage capabilities: it accesses the advantages of precise programmability, control, waveform flexibility, graphical interface, and extensibility.
- In its advanced form, pulsar synthesis generates a world of rhythmically structured crossfaded sampled sounds.
- Pulsar synthesis belongs to a larger family of methods known as particle synthesis techniques, one example of which is granular synthesis.

---

### 1. Basic Pulsar Synthesis

#### 1.1 Anatomy of a Pulsar

- A single pulsar is a particle of sound consisting of a brief burst of energy called a **pulsaret** `w` of duration `d`, followed by a silent time interval `s`.
- The duration of a pulsar = `p = d + s`, where `p` is the pulsar period, `d` is the duty cycle, and `s` is silent.
- Repetitions of the pulsar signal form a **pulsar train**.
- Key parameters:
  - `fp` = frequency corresponding to the repetition period `p` (= 1/p)
  - `fd` = frequency corresponding to the duty cycle `d` (= 1/d)
  - Typical range of `fp` is between 1 and 5 kHz
  - Typical range of `fd` is above 80 Hz

- In pulsar synthesis, both `fp` and `fd` are continuously variable quantities. They are controlled by separate envelopes that span a train of pulsars.
- **Key insight**: The train is the unit of musical organization on the time scale of notes and phrases. A pulsar train can last anywhere from a few hundred milliseconds to a minute or more.
- Notice that the duty ratio `d/s` or `d/p` ratio varies while `p` remains constant. In effect, one can simultaneously manipulate both **fundamental frequency** (the rate of pulsar emission) and what could be called a **formant frequency** (corresponding to the duty cycle).
  - Lowering the fundamental means increasing `s` and raising the fundamental means decreasing `s`.

#### 1.2 Pulsaret-Width Modulation

- **Pulse-width modulation (PWM)** is a well-known analog synthesis effect that occurs when the duty cycle of a rectangular pulse varies while the fundamental frequency remains constant.
- At the extremes of PWM, the signal is either constant DC (when `d = 0`) or a signal of zero amplitude (`d = 1`).
- **Pulsaret-width modulation (PaWM)** extends and improves upon PWM:
  - First, the pulsaret waveform can be any arbitrary waveform (not just rectangular).
  - Second, it allows the duty cycle frequency to pass through and below the fundamental frequency.
  - When `d > p`: the duty cycle is longer than the fundamental period, meaning only the first quadrant of the sine wave repeats.
- **Edge factor**: A user-controlled crossfade time around the cutoff point controls whether the edge is smooth or abrupt.
- **Overlapping PaWM (OPaWM)**: An alternate mode where the fundamental frequency is interpreted as the rate of pulsar emission, independent of the pulsar duty cycle. Pulsarets can overlap temporally.

#### 1.3 Synthesis across Time Scales

- Pulsar synthesis operates within and between musical time scales.
- At a variable rate, it crosses the continuum spanning the infrasonic, rhythmic, and audio frequencies.
- When the distance between successive impulses is less than about one-twentieth of a second, the human hearing mechanism causes the impulses to fuse into a continuous tone — this is the **forward masking effect**.
- At very low frequencies (observed at the range between 20 and 30 Hz), it is difficult to distinguish the precise pitch of a continuous tone; **pitch perception** takes hold at about 40 Hz, depending on the waveform. This corresponds to approximately 25 ms (i.e., `fp = 40 Hz`).

---

### 2. Spectra of Basic Pulsar Synthesis

- The spectrum of the pulsar stream is the convolution product of `w` and `i`, based on frequency by `Fd` and `Fp`.
- Since `w` and `i` can be arbitrary waveforms, and `Fd` and `Fp` can vary continuously, the range of spectra produced is large.
- When the formant frequency is set at a specific frequency (e.g., 1 kHz), this spreads energy in that region of the spectrum.
- **The pulsaret waveform `w` can be considered a template of spectrum shape** that repeats at the equivalent fundamental frequency `fp`.

---

### 3. Advanced Pulsar Synthesis

The advanced technique adds several features:

#### 3.1 Multiple Pulsar Generators

A pulsar generator has seven parameters:
1. Pulsar train duration
2. Pulsar train fundamental frequency envelope `fp`
3. Pulsaret formant frequency envelope `fd`
4. Pulsaret waveform `w`
5. Pulsaret envelope `e`
6. Pulsar train amplitude envelope `a`
7. Spatial position envelope

- The individual pulsar train is the simplest case. To synthesize richer spectra containing multiple formants, we can add several pulsar trains with the same fundamental frequency but with different formant frequencies `fd`.
- One envelope controls their common fundamental frequency; **two or more separate envelopes** control their formant frequencies.
- **Unique feature**: Each formant can follow its own spatial path, leading to separate spatial imagery within a single tone or rhythmic phrase.

#### 3.2 Pulsar Masking, Subharmonics, and Long-Tone Pulsars

- A pulsar generator emits a monotonous sequence of pulsars where the rate of emission can vary over time according to the fundamental frequency envelope `fp`.
- **Pulsar masking** breaks up the stream by introducing intermittences (regular or irregular) into the monotonous sequence. It deletes individual pulsarets, leaving an interval of silence in their place.
- Three forms of masking:
  1. **Burst masking**: Resembles the burst generators of classic electronic music studios. Produces an AM effect on the timbre. Divides the fundamental frequency by a subharmonic factor `b × p`. E.g., `b/p` ratio of 4:2 produces alternating sequences of four pulsarets and two silent periods: `1110011100111001110011`.
  2. **Channel masking**: Distributes pulsars in multichannel output (e.g., selectively masking pulsars in two channels, creating a stereo within a phrase).
  3. **Stochastic masking**: Introduces random intermittences into the regular stream of pulsars. A weighted probability determines whether a pulse will be emitted at a particular point.

#### 3.3 Transformation of Sampled Sounds by Convolution with Pulsars

- The technique of pulsar synthesis can be harnessed as a method of sound transformation through **convolution**.
- Any series of impulses convolved with a brief sound maps that sound into the time pattern of the impulses.
- If the pulsar form frequency is in the infrasonic range, each pulsar is replaced by a copy of the sampled sound object, creating a **rhythmic pattern**.
- The convolution of a rhythmic pattern with a sound object causes each impulse to be replaced by a filtered copy of the sampled object.
- In convolution, each pulsar represents the impulse response of a filter.
- **Two effects from convolution**:
  1. Filtering effects imposed by the time-varying pulsar train
  2. Overlapping effects caused by convolution with pulsar trains whose fundamental period is shorter than the duration of the sampled sound

---

### 4. Implementations of Pulsar Synthesis

- Original implementation of pulsar synthesis dates to 1995, using James McCartney's Synth-O-Matic, a programmable sound synthesis environment for Apple Macintosh computers.
- In 1996, McCartney replaced Synth-O-Matic with **SuperCollider 1** — an object-oriented programming language with an efficient MacOS runtime system. Using SuperCollider 1, a real-time implementation was developed for pulsar synthesis in 1997.
- Based on the improved **SuperCollider 2** (1998), Alberto de Campo and Roads developed a new realization of pulsar synthesis.
- **PulsarGenerator**: A program designed by CREATE, distributed by the Center for New Music and Audio Technology, University of California, Berkeley. Available at www.create.ucsb.edu.
  - Notice the control envelopes for synthesis variables. These envelopes can be designed in advance of synthesis, or manipulated in real time as the instrument plays.
  - The program lets one crossfade at a variable rate between multiple settings, which takes real-time performance with the PulsarGenerator to another level of synthesis control.
- **Performance metrics** (circa 2001): At an infrasonic rate of 20 pulsars per second, the PulsarGenerator consumes about 3.6% of a single-processor Apple G4 running at 500 MHz clock speed. At a high audio rate (e.g., three-formant instrument emitting 8000 pulsars per second, `fp` of 2.4 kHz), the application consumes approximately 45% of the processor.

---

### 5. Composing with Pulsars

- **Half-life**, composed in 1998-1999, is a 3-minute pulsar train that varies widely. Most sounds in the rest of the work were derived from this source.
- *Half-life* extends the pulsar material through processes of granulation, micromontage, granular pitch shifting, resonating feedback echo, individual pulsar amplitude shaping, and selective reverberation.
- **Earth Voices** (2000) and **Eleventh Vortex** (2001) continue in this direction.

---

### 6. Musical Applications of Pulsar Synthesis

- Developed pulsar synthesis in the course of realizing *Clang-tint* (1994), an electronic music composition commissioned by the Japanese Ministry of Culture (Bunka-cho).
- The second movement of the work, entitled *Organic Textures*, focuses on expressive phrasing. It combines bursts of insect, animal, and bird calls with electronic pulsar tones.
- The electronic sound palette is based on pulsar synthesis in multiple forms: pulsating blips, elevated formant tones, and clouds of asynchronous pulsars.

---

### 7. Conclusions

- Music transpires on multiple time scales, from a high level of macrostructure down to a period of individual sound objects or notes. Below this level is another hierarchy of time scales.
- Here are the microscopic particles: rectangular, Gaussian, wavelets, and pulsars. **Impulse generators as an effective means of sound synthesis** was established decades ago in the analog electronic studio.
- By comparison, digital pulsar synthesis offers a flexible choice of waveforms and envelopes, increased precision, and graphical programmable control.
- Unlike the typical wave-oriented synthesis techniques (such as additive synthesis), the notion of rhythm is built into techniques based on particles.
- Pulsar synthesis offers a seamless link between the time scales of individual particle rhythm, periodic pitches, and the meso- or phrase-level of composition.
- Another novel feature of this technique is the generation of multiple independent formant trajectories, each of which follows its own spatial path.

---

### Appendix: Analysis of Pulsaret Envelope Spectra

#### A.1 Spectrum of a Rectangular Pulsaret Envelope

Assuming sampling rate is infinite and pulsaret envelope is an ideal rectangular impulse:

```
Rect(t) = { 1,  for |t| < τ/2
           { 0,  otherwise
```

It starts at a definite instant of time, and may be considered to have decayed to zero after a fixed length of time `τ` (the duty cycle). If the repetition period is infinite, the pulse is reduced to a single isolated transient; in this ideal case the repetition period is `∞` and the fundamental frequency of the Fourier spectrum is 0 Hz. The spectrum consists of harmonics of terms infinitely close to one another on a frequency scale, producing a continuous spectrum.

Since the energy of an ideal impulse is infinite, these spectrum components must be infinitesimally small. The spectrum shape is the sinc function:

```
Rect(f) = |sin πf| / |πf|
```

The discrete energy peaks at `f = 0.5/τ, 1.5/τ, 2.5/τ, 3.5/τ, ...` and the energy zeros at `f = 1/τ, 2/τ, 3/τ, ...` which repeat at the time function. With its high peaks and strong nulls, the presence of specific frequencies in the spectrum depends greatly on the chosen formant frequency. Formant frequencies of 450 and 500 Hz, for example, distribute the spectral energy into different specific frequencies, even though they sound rather similar.

#### A.2 Spectrum of a Linear Decay Pulsaret Envelope

A linear decay envelope has a sharp attack followed by a decaying tail, reaching 0 at the end of its duty cycle `d`:

```
Lindec(t) = { 1 - t/d,  for 0 ≤ t ≤ d
            { 0,         otherwise
```

Its spectrum is:

```
Lindec(f) = d/(2πf)² × |1 - e^(-j2πfd)| = (1/d) × |1 + (1 - e^(-j2πfd))/(j2πfd)|
```

#### A.3 Spectrum of an Exponential Decay Pulsaret Envelope

An exponential decay (expdec) pulsaret envelope falls quickly from its initial peak to arrive at zero at the end of the pulsaret period:

```
Expdec(t) = { e^(-αt),  for t ≥ 0
            { 0,         for t < 0
```

Its spectrum is:

```
Expdec(f) = 1 / (α + j2πf)
```

This spectrum is a smooth continuum of frequencies without null points, sloping off at a rate of about -12 dB per octave. This is less steep than the rectangular pulsaret envelope, which accounts for the breadth of the exponential decay spectrum.

#### A.4 Spectrum of a Linear Attack Pulsaret Envelope

A linear attack (linatk) envelope reaches its peak at the end of the pulsaret duty cycle `d`:

```
Linatk(t) = { t/d,  for 0 ≤ t ≤ d
            { 0,    otherwise
```

#### A.5 Spectrum of an Exponential Attack Pulsaret Envelope

An exponential attack envelope (expatk) reaches its peak with a sweep at the end of the pulsaret period:

```
Expatk(t) = { e^(-α(τ-t)),  for t ≤ τ
            { 0,            otherwise
```

In shape, the expatk spectrum is the same as the exponential decay, but temporally reversed.

#### A.6 Spectrum of a Gaussian Pulsaret Envelope

A Gaussian pulsaret envelope can be defined as:

```
Gauss(t) = e^(-t²)
```

Its Fourier transform is an eigenfunction of its time-domain function:

```
Gauss(f) = e^(-πf²)
```

The spectral effect of the Gaussian pulsaret envelope is to produce a formant spectrum at a tight band. An inspection of the sinepoints reveals that beyond the band the spectrum drops off at a rate of approximately -30 dB per octave.

---

### Key References

- [1] Roads, C. "The Computer Music Tutorial" (MIT Press, Cambridge, MA, 1996)
- [2] Roads, C. "Microsound" (MIT Press, Cambridge, MA, 2001)
- [3] Roads, C. "Automated Granular Synthesis of Sound," Computer Music J., vol. 2, no. 2, pp. 61-62 (1978)
- [4] Roads, C. "Asynchronous Granular Synthesis," in Representations of Musical Signals, G. De Poli, A. Piccialli, and C. Roads, Eds. (MIT Press, Cambridge, MA, 1991), pp. 143-185
- [5] McCartney, J. "SuperCollider, Synth-O-Matic" and SuperCollider 2 software (www.audiosynth.com)

---

### SuperCollider Forum Examples

Source: https://scsynth.org/t/pulsar-synthesis/1618

See `pulsar_synthesis_examples.scd` in this directory for SuperCollider code examples including:
- `\gran` SynthDef (TGrains-based granulator)
- Single synth examples with various parameter combinations
- Long overlapping pulsar stream patterns (Pbind)
- Short overlapping pulsar stream patterns
- Advanced event correspondence patterns with running average trigRate

---

### Key Synthesis Parameters Summary

| Parameter | Symbol | Description | Typical Range |
|-----------|--------|-------------|---------------|
| Fundamental frequency | `fp` | Rate of pulsar emission (1/p) | 1 Hz - 5 kHz |
| Formant frequency | `fd` | Frequency corresponding to duty cycle (1/d) | > 80 Hz |
| Duty cycle | `d` | Duration of the pulsaret | Variable |
| Silent interval | `s` | Silence between pulsarets | Variable |
| Pulsar period | `p` | Total period = d + s | Variable |
| Pulsaret waveform | `w` | Shape of individual pulsaret | Sine, rect, Gaussian, etc. |
| Pulsaret envelope | `e` | Amplitude envelope of pulsaret | Rect, lindec, expdec, Gaussian |
| Train envelope | `a` | Overall amplitude envelope of train | Variable |
| Edge factor | - | Crossfade smoothness at cutoff | 0 (sharp) to 1 (smooth) |
| Burst masking ratio | `b/p` | Ratio of active to silent pulsars | e.g., 4:2, 3:1 |

---

## Reference: "Microsound" by Curtis Roads (MIT Press, 2001) — Key Concepts Summary

Book: *Microsound*, Curtis Roads, MIT Press, 2001, ~550 pages.
This summary covers concepts most relevant to pulsar synthesis implementation.

---

### Time Scales in Music

Roads organizes sound into a hierarchy of time scales, which is central to understanding where pulsar synthesis operates:

| Time Scale | Duration | Examples |
|------------|----------|----------|
| Infinite | Unbounded | Conceptual drone, eternal tone |
| Supra | Minutes to hours | Overall form of a composition |
| Macro | Seconds to minutes | Sections, phrases |
| Meso | ~100ms to several seconds | Notes, sound objects, textures |
| Sound object | ~100ms to several seconds | Individual recognizable sonic events |
| Micro | <100ms down to ~1ms | Grains, pulsars, wavelets, glitch particles |
| Sample | ~1/44100s (~23μs at 44.1kHz) | Individual digital audio samples |
| Subsample | < 1 sample period | Theoretical, relates to interpolation |
| Infinitesimal | Approaches 0 | Mathematical limit |

**Key insight for pulsar synthesis**: Pulsars exist primarily at the micro time scale but their trains span the meso and macro scales. The fundamental frequency `fp` controls whether we perceive rhythm (infrasonic, <20 Hz) or pitch (audio rate, >20 Hz). This ability to cross the boundary between rhythm and pitch within a single gesture is one of the defining characteristics of pulsar synthesis.

---

### Particle Synthesis — The Family

Pulsar synthesis belongs to a family of **particle-based** synthesis methods. Roads surveys these extensively:

#### Granular Synthesis
- Originated conceptually with Dennis Gabor (1946-1947) and Iannis Xenakis (1960).
- Decomposes sound into small temporal quanta called **grains** (typically 1-100ms).
- Each grain has an amplitude envelope (commonly Gaussian or raised cosine).
- Grains can be organized synchronously (regular spacing) or asynchronously (stochastic timing).
- Parameters: grain duration, density (grains/sec), waveform, frequency, spatial position, amplitude envelope.
- **Distinction from pulsar synthesis**: Granular synthesis typically uses overlapping grains to create continuous textures. Pulsar synthesis explicitly controls the silence between particles (the `s` parameter), making the gap a compositional element rather than an artifact.

#### Grainlet Synthesis
- Extension of granular synthesis using very short grains (~1-2ms) that approach impulse-like behavior.
- At these durations, the grain envelope strongly colors the spectrum.
- Transitional technique between granular and pulsar methods.

#### Trainlet Synthesis
- Based on trains of band-limited impulses.
- Each trainlet is a brief burst of a periodic waveform.
- More directly related to classic electronic pulse generators.

#### Pulsar Synthesis (as situated in the particle family)
- Distinguished from other particle methods by:
  1. Explicit separation of pulsaret waveform `w` and pulsaret envelope `e`
  2. Independent control of fundamental frequency `fp` and formant frequency `fd`
  3. The silence interval `s` as a first-class parameter
  4. Ability to cross time scale boundaries (rhythm ↔ pitch) continuously
  5. Built-in masking operations for rhythmic structuring

---

### Granular Synthesis Techniques (Relevant to Implementation)

#### Synchronous Granular Synthesis
- Grains emitted at a regular rate determined by a fundamental frequency.
- Grain rate = pitch. Grain waveform and envelope = timbre/formant.
- This is the closest granular technique to basic pulsar synthesis.
- **Direct parallel to pulsar synthesis**: When overlap = 0 and grains don't overlap, synchronous granular synthesis is essentially equivalent to basic pulsar synthesis.

#### Asynchronous Granular Synthesis
- Grain emission times are stochastic (governed by probability distributions).
- Produces cloud-like textures without definite pitch.
- Density parameter controls grains per second (analogous to stochastic pulsar masking).
- Can be related to pulsar synthesis with stochastic masking applied.

#### Quasi-Synchronous Granular Synthesis
- A middle ground: grain rate has a deterministic center with random jitter.
- Produces pitch with roughness or "chorus" quality.
- Analogous to pulsar synthesis with `trigRateDev` parameter (as in the SuperCollider examples).

#### Granulation of Sampled Sounds
- Buffer granulation: reading grains from a pre-recorded buffer at varying positions, rates, and densities.
- The `pos` parameter in the SuperCollider `\gran` SynthDef corresponds to this — scrubbing through a buffer while emitting pulsars.
- Time-stretching and pitch-shifting emerge naturally from decoupling grain emission rate from buffer read position.

---

### Microsound Morphology — Particle Shapes and Envelopes

Roads catalogs the waveforms and envelopes applicable to micro-level synthesis:

#### Pulsaret Waveforms (`w`)
- **Sinusoidal**: Pure tone pulsaret; spectrum is clean with energy concentrated at the formant frequency.
- **Cosine**: Similar to sine but phase-shifted; useful for avoiding clicks at pulsaret boundaries.
- **Rectangular/Square**: Rich harmonic content; produces the classic PWM-style timbres.
- **Sawtooth**: Bright, buzzy character; all harmonics present.
- **Triangle**: Softer than sawtooth; odd harmonics only, falling off more steeply.
- **Arbitrary wavetable**: Any single-cycle waveform loaded from a buffer.
- **Sampled extract**: A short segment of a recorded sound used as the pulsaret.

#### Pulsaret Envelopes (`e`) — Spectral Characteristics
(Complements the spectral analysis in the AES paper appendix)

| Envelope | Character | Spectral Shape | Bandwidth | Best For |
|----------|-----------|---------------|-----------|----------|
| Rectangular | Sharp on/off | Sinc function (strong nulls) | Wide, with nulls | Harsh, buzzy tones |
| Gaussian | Smooth bell curve | Gaussian (no nulls) | Narrow, tight band | Clean formants |
| Exponential decay | Sharp attack, gradual fade | Smooth rolloff, -12dB/oct | Medium-wide | Plucked/percussive |
| Linear decay | Sharp attack, linear fade | Moderate rolloff | Medium | General purpose |
| Raised cosine | Smooth onset and offset | Similar to Gaussian | Medium-narrow | Click-free particles |
| Expodec (Roads' term) | Fast attack, exponential decay | Smooth, no nulls | Wide | Transient-rich tones |
| FOF-style | Models vocal formants | Resonant peak | Narrow | Vocal/formant synthesis |

**Implementation note**: The choice of envelope `e` has as much impact on the perceived timbre as the choice of waveform `w`. Gaussian envelopes produce the cleanest, most tonal results. Rectangular envelopes produce the widest bandwidth and harshest textures.

---

### Density and Cloud Textures

Roads defines several density regimes relevant to particle synthesis:

- **Sparse density** (<50 particles/sec): Individual particles are audible as discrete events. Rhythmic patterns dominate perception.
- **Medium density** (50-200 particles/sec): Transitional zone. Pitch begins to emerge but texture is rough. This is where the rhythm-to-pitch crossover happens.
- **High density** (200-1000 particles/sec): Clear pitch with timbral character determined by particle shape. This is the core operating range of tonal pulsar synthesis.
- **Very high density** (>1000 particles/sec): Bright, buzzy, or noisy depending on regularity. Formant frequencies become very prominent.

For pulsar synthesis specifically:
- At `fp < 20 Hz`: clearly rhythmic, individual pulsarets heard as clicks/pops
- At `fp ≈ 20-40 Hz`: transition zone, motor-like buzzing
- At `fp > 40 Hz`: pitched tone emerges, timbre controlled by `fd`, `w`, and `e`
- At `fp > 1000 Hz`: high-pitched, the pulsaret envelope/waveform dominates perception

---

### Spatial Distribution of Microsound

- Individual particles can be assigned spatial positions independently.
- In pulsar synthesis, each formant stream (each pulsar generator) can have its own spatial trajectory.
- This creates the possibility of **spatially separated formants** — a single "instrument" whose timbral components emanate from different locations.
- Techniques include:
  - Per-particle panning (as in the `Dseq([-1, 1], inf)` alternating pan in the SC examples)
  - Spatial envelopes that sweep particle streams across the stereo/multichannel field
  - Density-dependent spatialization (denser clouds spread wider)

---

### Frequency-Domain Perspective

Roads discusses the spectral implications of microsound extensively:

- **Short-time Fourier transform (STFT)** analysis of pulsar trains reveals the formant structure directly.
- The spectrum of a pulsar train is periodic, with harmonics spaced at `fp` Hz apart.
- The **spectral envelope** (overall shape) is determined by the pulsaret waveform `w` and envelope `e`.
- The **formant peak** appears at frequency `fd = 1/d`.
- Changing `d` while keeping `p` constant shifts the formant without changing the pitch — this is the core spectral manipulation of pulsar synthesis.
- Convolution in the time domain = multiplication in the frequency domain. So convolving a pulsar train with a sampled sound multiplies their spectra, filtering the sample through the pulsar train's comb-like spectrum.

---

### Compositional Strategies with Microsound

Roads outlines several approaches to organizing microsound in compositions:

#### Sound Mass / Cloud Composition
- Treating dense collections of particles as unified textural objects.
- Parameters of the cloud (density, frequency band, spatial width, amplitude) are the compositional variables.
- Xenakis' stochastic music is a precedent.

#### Particle Streams and Trains
- Linear sequences of particles forming melodic or rhythmic lines.
- Pulsar trains are a specific case of this.
- Multiple simultaneous streams create counterpoint at the particle level.

#### Time-Scale Bridging
- Composing gestures that traverse time scales — a rhythm that accelerates into a pitch, or a tone that decelerates into a pulse pattern.
- This is one of pulsar synthesis's signature capabilities.
- Musically powerful because it reveals the continuum between rhythm and pitch that is normally hidden.

#### Micromontage
- Assembling compositions from very short sound fragments.
- Can use pulsar trains as raw material, then further process via granulation, filtering, reverb.
- Roads' *Half-life* uses this approach extensively.

---

### Implementation Considerations (from Roads' Discussion)

#### Real-Time vs. Non-Real-Time
- Early implementations were non-real-time (score-based rendering).
- SuperCollider enabled real-time pulsar synthesis starting in 1997.
- Real-time is essential for performance and interactive exploration.
- CPU cost scales with: number of simultaneous pulsar generators × emission rate × complexity of waveform lookup.

#### Parameter Interpolation
- All pulsar parameters should be continuously interpolatable (no discontinuities).
- Envelope breakpoint functions or LFOs control parameter evolution over the duration of a train.
- Abrupt parameter changes cause audible artifacts (clicks, glitches) — which may or may not be desirable.

#### Avoiding Aliasing
- At high `fd` values, the pulsaret waveform may contain energy above the Nyquist frequency.
- Band-limited waveforms or oversampling can mitigate this.
- Gaussian and smooth envelopes naturally suppress high-frequency energy, reducing aliasing risk.

#### Buffer-Based Implementation
- Store pulsaret waveforms and envelopes in wavetables/buffers.
- Read from them at rates determined by `fd`.
- Crossfade between different waveform/envelope buffers for timbral morphing.
- This is the approach used by PulsarGenerator and most SuperCollider implementations.

---

### Connections to Other Synthesis Methods

| Method | Relationship to Pulsar Synthesis |
|--------|--------------------------------|
| Additive synthesis | Pulsar trains produce harmonic spectra; multiple generators ≈ additive partials with independent envelopes |
| Subtractive synthesis | Pulsaret waveform acts as source; pulsaret envelope acts as filter (spectral shaping) |
| FM synthesis | Modulating `fp` or `fd` at audio rates can produce FM-like sidebands |
| Wavelet synthesis | Wavelets are similar to pulsarets; wavelet synthesis uses time-frequency atoms analogously |
| FOF synthesis | Formant synthesis via impulse-excited decaying sinusoids; closely related to pulsar synthesis with expodec envelopes |
| Physical modeling | Pulsar trains can approximate excitation signals for physical models (e.g., bowed string = regular impulse train) |
| Wavetable synthesis | Pulsaret waveform lookup is essentially wavetable reading; the silent gap `s` is the key difference |

---

## Norns Platform Reference

Source: https://monome.org/docs/norns/

---

### Maiden (Browser IDE)

Source: https://monome.org/docs/norns/maiden/

Maiden is a browser-based editor and project manager for norns, accessible at `norns.local` (or `IP-ADDRESS/maiden/` on Windows).

#### File System

Maiden can only read/write files within `/home/we/dust/`:
- `/home/we/dust/audio` — audio files
- `/home/we/dust/code` — script code
- `/home/we/dust/data` — script data / psets

Other system files require SFTP access.

#### Key Paths

| Path | Contents |
|------|----------|
| `/home/we/dust/code/` | User scripts |
| `/home/we/dust/audio/` | Audio files for scripts |
| `/home/we/dust/data/` | Script data, psets |
| `/home/we/norns/sc/engines/` | System SuperCollider engines |
| `/home/we/.local/share/SuperCollider/Extensions` | SC extensions |
| `/usr/local/share/SuperCollider/SCClassLibrary` | SC class library |

#### REPL

Two tabs in the bottom panel:
- **matron** — Lua REPL (main scripting environment)
- **sc** — SuperCollider engine REPL

Commands:
- `;restart` — restart matron (when menu becomes inaccessible)
- `;install https://github.com/USERNAME/PROJECT` — install a script from GitHub

#### Editor Shortcuts

| Shortcut | Action |
|----------|--------|
| `CMD/CTRL-S` | Save |
| `CMD/CTRL-P` | Play (run current script) |
| `CMD/CTRL-.` | Stop |
| `CMD/CTRL-Return` | Execute selected line(s) in REPL |

Editor modes: default, vim, emacs (configurable via gear icon).

#### Installing Scripts

From maiden REPL:
```
;install https://github.com/USERNAME/PROJECT-NAME
```

If a project contains a SuperCollider engine (`lib` tag), **device restart is mandatory** after installation.

Manual installation: download ZIP, remove `-main`/`-master` suffix from folder name, transfer to `/home/we/dust/code/` via SFTP/SMB.

#### Troubleshooting

Common errors:
- `error: load fail` — syntax error in script
- `error: missing <EngineName>` — engine not found, restart needed
- `error: SUPERCOLLIDER FAIL` — SC engine crash
- `ERROR: duplicate Class found` — two copies of same engine; delete duplicate from `/home/we/dust/code/`

System logs via SSH:
```bash
ssh we@norns.local
journalctl -f
```

#### CLI Access

```bash
ssh we@norns.local
maiden repl          # interactive REPL
maiden/maiden        # CLI tool
```

CLI commands: `catalog` (init/list/update), `project` (install/list/push/remove/update), `repl`, `server`, `version`.

---

### Norns Script Structure

Source: https://monome.org/docs/norns/study-1/

#### Minimal Script Template

```lua
-- script name
-- description
-- by author

engine.name = "EngineNameHere"

function init()
  -- runs once at script load
  -- initialize variables, params, clocks
end

function key(n, z)
  -- n = key number (1, 2, 3)
  -- z = state (1 = pressed, 0 = released)
end

function enc(n, d)
  -- n = encoder number (1, 2, 3)
  -- d = delta (positive = clockwise, negative = counterclockwise)
end

function redraw()
  -- called to refresh the screen
  screen.clear()
  -- draw here
  screen.update()
end

function cleanup()
  -- runs when script is unloaded
end
```

Comments at the top of the script (lines starting with `--`) appear in the script selector menu.

#### Key Lua Basics for Norns

- Variables are **global by default**; use `local` for local scope
- String concatenation: `..`
- Not-equal operator: `~=`
- `util.clamp(value, min, max)` — constrain value to range
- `util.wrap(value, min, max)` — wrap value within range
- `math.random(n)` — random integer 1 to n
- `engine.list_commands()` — show available engine commands

---

### Screen Drawing

Source: https://monome.org/docs/norns/study-2/

```lua
function redraw()
  screen.clear()
  screen.level(15)           -- brightness 0-15
  screen.move(x, y)          -- set cursor position
  screen.text("hello")       -- draw text
  screen.font_face(1)        -- set font
  screen.font_size(8)        -- set size
  screen.line(x2, y2)        -- line to point
  screen.line_rel(dx, dy)    -- relative line
  screen.rect(x, y, w, h)   -- rectangle
  screen.circle(x, y, r)    -- circle
  screen.stroke()            -- draw outline
  screen.fill()              -- fill shape
  screen.aa(1)               -- anti-aliasing on
  screen.update()            -- push to display
end
```

Screen is 128x64 pixels. Coordinates: (1,1) = top-left.

---

### Parameters System

Source: https://monome.org/docs/norns/study-3/

#### Parameter Types

```lua
-- number
params:add_number("id", "name", min, max, default)

-- option (dropdown)
params:add_option("id", "name", {"opt1", "opt2", "opt3"}, default_index)

-- control (with controlspec)
params:add_control("id", "name", controlspec.new(min, max, curve, step, default, unit))

-- binary (momentary/toggle/trigger)
params:add_binary("id", "name", "toggle", default)

-- trigger
params:add_trigger("id", "name")

-- separator / group
params:add_separator("label")
params:add_group("name", count)

-- file
params:add_file("id", "name", default_path)

-- text
params:add_text("id", "name", default_text)

-- table-based declaration
params:add{
  type = "control",
  id = "cutoff",
  name = "cutoff",
  controlspec = controlspec.new(50, 5000, 'exp', 0, 555, 'hz'),
  action = function(x) engine.cutoff(x) end
}
```

#### Parameter Operations

```lua
params:set("id", value)        -- set value
params:get("id")               -- get value
params:delta("id", amount)     -- increment/decrement (clamped to range)
params:string("id")            -- formatted string with units
params:bang()                  -- trigger ALL param actions (call after init or pset load)
params:set_action("id", fn)    -- set/change action callback
```

**Important**: `params:bang()` must be called after `params:read()` (loading psets) to trigger actions.

#### Controlspec Presets

```lua
controlspec.FREQ       -- 20-20000 Hz, exponential, default 440
controlspec.WIDEFREQ   -- 0.1-20000 Hz
controlspec.AMP        -- 0-1, default 0
controlspec.PAN        -- -1 to 1, default 0
controlspec.UNIPOLAR   -- 0-1, linear, default 0
controlspec.BIPOLAR    -- -1 to 1, linear, default 0
```

#### PSETs (Parameter Sets)

```lua
params:write(slot_number, "name")  -- save
params:read(slot_number)           -- load
params:bang()                      -- apply loaded values
```

Files stored in `/home/we/dust/data/scriptname/`

---

### Metro (Timer)

Source: https://monome.org/docs/norns/study-3/

```lua
-- create
my_metro = metro.init()
my_metro.time = 1              -- interval in seconds
my_metro.count = -1            -- -1 = infinite, or set target
my_metro.event = function(stage)
  -- called on each tick
  -- stage = current count
end

-- control
my_metro:start()
my_metro:stop()

-- shorthand
my_metro = metro.init(callback_fn, time, count)
```

---

### Clock System

Source: https://monome.org/docs/norns/clocks/

#### Coroutines

```lua
-- start a clock coroutine (returns ID)
my_clock_id = clock.run(function()
  while true do
    -- do stuff
    clock.sync(1)      -- wait for next beat
    -- or
    clock.sleep(0.5)   -- wait 0.5 seconds
  end
end)

-- cancel
clock.cancel(my_clock_id)
```

Up to 100 coroutines can run simultaneously.

#### Timing

```lua
clock.sync(beats)              -- wait until next beat subdivision (tempo-synced)
clock.sync(beats, offset)      -- with offset (for swing)
clock.sleep(seconds)           -- wait fixed duration (not tempo-synced)
clock.get_beats()              -- current beat count
clock.get_tempo()              -- current BPM
clock.get_beat_sec()           -- beat duration in seconds
```

#### Transport

```lua
clock.transport.start = function()
  -- called when transport starts
end

clock.transport.stop = function()
  -- called when transport stops
end
```

#### Clock Sources

- `internal` — norns provides tempo
- `midi` — external MIDI clock
- `link` — Ableton Link (wireless sync)
- `crow` — modular sync via crow

Set programmatically: `params:set("clock_tempo", 120)`

---

### MIDI

Source: https://monome.org/docs/norns/study-4/

```lua
m = midi.connect(port)  -- port 1-16, default 1

-- receiving
m.event = function(data)
  local d = midi.to_msg(data)
  if d.type == "note_on" then
    -- d.note, d.vel, d.ch
  elseif d.type == "note_off" then
    -- d.note, d.vel, d.ch
  elseif d.type == "cc" then
    -- d.cc, d.val, d.ch
  elseif d.type == "pitchbend" then
    -- d.val
  end
end

-- sending
m:note_on(note, velocity, channel)
m:note_off(note, velocity, channel)
m:cc(cc_number, value, channel)
```

Message types: `note_on`, `note_off`, `cc`, `pitchbend`, `key_pressure`, `channel_pressure`

---

### Grid

Source: https://monome.org/docs/norns/study-4/

```lua
g = grid.connect()

-- LED control
g:led(x, y, brightness)  -- brightness 0-15
g:all(0)                  -- clear all
g:refresh()               -- update display

-- key input
g.key = function(x, y, z)
  -- x,y = position (1,1 = top-left)
  -- z = 1 (press) or 0 (release)
end
```

---

### OSC (Open Sound Control)

Source: https://monome.org/docs/norns/study-5/

```lua
-- sending
osc.send({"host", port}, "/path", {arg1, arg2, ...})

-- receiving
osc.event = function(path, args, from)
  -- path = string like "/x"
  -- args = table of values
  -- from = {host, port}
end
```

Default norns OSC port: 10111

---

### System Polls

Source: https://monome.org/docs/norns/study-5/

```lua
-- amplitude tracking
p = poll.set("amp_in_l")
p.time = 0.1              -- update interval in seconds
p.callback = function(val)
  -- val = amplitude value
end
p:start()
p:stop()
```

Available polls: `amp_in_l`, `amp_in_r`, `amp_out_l`, `amp_out_r`, `pitch_in_l`, `pitch_in_r`

---

### File I/O

Source: https://monome.org/docs/norns/study-5/

```lua
-- table save/load
tab.save(my_table, filepath)
my_table = tab.load(filepath)

-- directory scanning
files = util.scandir(path)

-- raw file I/O
f = io.open(filepath, "r")  -- read
content = f:read("*all")
f:close()

f = io.open(filepath, "w")  -- write
f:write(data)
f:close()
```

---

### Softcut (Buffer/Looper System)

Source: https://monome.org/docs/norns/softcut/

6 mono voices, 2 mono buffers (each ~5 min 49s at 48kHz). All audio files must be 48kHz.

#### Basic Playback

```lua
softcut.enable(voice, 1)
softcut.buffer(voice, buf)
softcut.level(voice, 1.0)
softcut.loop(voice, 1)
softcut.loop_start(voice, start_sec)
softcut.loop_end(voice, end_sec)
softcut.position(voice, pos_sec)
softcut.rate(voice, 1.0)          -- 1.0 = normal, -1.0 = reverse, 2.0 = double speed
softcut.play(voice, 1)
```

#### Recording

```lua
softcut.rec(voice, 1)                               -- enable recording
audio.level_adc_cut(1.0)                             -- route hardware input to softcut
softcut.level_input_cut(input_ch, voice, 1.0)        -- per-voice input routing
softcut.pre_level(voice, 0.5)                        -- preserve existing buffer (0=overwrite, 1=keep all)
softcut.rec_level(voice, 1.0)                        -- incoming signal level
```

#### Filtering

```lua
softcut.post_filter_fc(voice, freq_hz)    -- cutoff frequency
softcut.post_filter_rq(voice, q)          -- resonance
softcut.post_filter_lp(voice, level)      -- lowpass amount
softcut.post_filter_hp(voice, level)      -- highpass amount
softcut.post_filter_bp(voice, level)      -- bandpass amount
softcut.post_filter_dry(voice, level)     -- dry signal amount
```

#### Buffer File Operations

```lua
softcut.buffer_read_mono(file, start_src, start_dst, dur, ch_src, ch_dst)
softcut.buffer_read_stereo(file, start_src, start_dst, dur)
softcut.buffer_write_mono(file, start, dur, ch)
softcut.buffer_write_stereo(file, start, dur)
softcut.buffer_copy_mono(src_buf, dst_buf, start_src, start_dst, dur, fade, reverse)
softcut.buffer_clear()
softcut.buffer_clear_channel(ch)
softcut.buffer_clear_region(ch, start, dur)
```

#### Cross-Patching Voices

```lua
softcut.level_cut_cut(source_voice, dest_voice, level)  -- route voice output to another voice input
```

#### Position Polling

```lua
softcut.phase_quant(voice, time)           -- set reporting interval
softcut.event_phase(function(voice, pos)
  -- called at reporting interval with current position
end)
```

#### Smoothing

```lua
softcut.fade_time(voice, time)             -- crossfade on position jumps
softcut.rate_slew_time(voice, time)        -- smooth rate changes
softcut.level_slew_time(voice, time)       -- smooth level changes
```

---

### Building SuperCollider Engines for Norns

Source: https://monome.org/docs/norns/engine-study-1/ and engine-study-2/

#### CroneEngine Template

File must be named `Engine_YourName.sc` and placed in `lib/` folder of your script.

```supercollider
Engine_YourName : CroneEngine {
  var params;

  alloc {
    // Define SynthDefs
    SynthDef("YourSynth", {
      arg freq = 440, amp = 0.5, out = 0;
      var sig = SinOsc.ar(freq) * amp;
      var env = EnvGen.kr(Env.perc, doneAction: 2);
      Out.ar(out, Pan2.ar(sig * env));
    }).add;

    // Optional: wait for SynthDef to be ready
    // Server.default.sync;

    // Parameter state dictionary
    params = Dictionary.newFrom([
      \amp, 0.5,
      \cutoff, 1000,
      \release, 0.4
    ]);

    // Register commands (Lua can call these)
    // Format strings: "f" = float, "i" = int, "s" = string
    // Multiple args: "ff", "sf", "iff", etc.

    // Auto-generate commands from params dictionary
    params.keysDo({ arg key;
      this.addCommand(key, "f", { arg msg;
        params[key] = msg[1];
      });
    });

    // Note trigger command
    this.addCommand("hz", "f", { arg msg;
      Synth.new("YourSynth", [\freq, msg[1]] ++ params.getPairs);
    });
  }

  free {
    // cleanup
  }
}
```

#### Lua Engine Library Pattern

Companion file `lib/yourname_engine.lua`:

```lua
local YourName = {}
local Formatters = require 'formatters'

local specs = {
  ["amp"] = controlspec.new(0, 2, "lin", 0, 1, ""),
  ["cutoff"] = controlspec.new(0.1, 20000, 'exp', 0, 1300, "Hz"),
  ["release"] = controlspec.new(0.003, 8, "exp", 0, 1, "s")
}

local param_names = {"amp", "cutoff", "release"}

function YourName.add_params()
  params:add_group("YourName", #param_names)
  for i = 1, #param_names do
    local p = param_names[i]
    params:add{
      type = "control",
      id = "YourName_" .. p,
      name = p,
      controlspec = specs[p],
      action = function(x) engine[p](x) end
    }
  end
  params:bang()
end

function YourName.trig(hz)
  if hz ~= nil then
    engine.hz(hz)
  end
end

return YourName
```

#### Host Script Usage

```lua
engine.name = 'YourName'
myengine = include('yourscript/lib/yourname_engine')

function init()
  myengine.add_params()
end
```

#### Advanced: Voice Groups and Polyphony

```supercollider
// In alloc:
var voiceGroup = Group.new(s);

// Trigger into group
this.addCommand("hz", "f", { arg msg;
  Synth.new("YourSynth", [\freq, msg[1]] ++ params.getPairs, voiceGroup);
});

// Set params on all active voices
params.keysDo({ arg key;
  this.addCommand(key, "f", { arg msg;
    params[key] = msg[1];
    voiceGroup.set(key, msg[1]);  // update live voices
  });
});

// Cleanup
freeAllNotes { voiceGroup.set(\stopGate, -1.05); }
free { voiceGroup.free; }
```

#### Parameter Smoothing in SynthDefs

```supercollider
var smoothed_freq = freq.lag3(freq_slew);
var smoothed_amp = amp.lag3(amp_slew);
```

Use `Lag3` for CPU-efficient parameter smoothing on active voices.

#### Safety

```supercollider
Server.default.options.safetyClipThreshold = 1;  // clip output
// Always use a Limiter on the master bus
```

#### File Organization for Distribution

```
your_script/
  lib/
    Engine_YourName.sc       -- SuperCollider engine
    yourname_engine.lua      -- Lua engine library
  your_script.lua            -- main script
```

---

### Useful Libraries

| Library | Require Path | Purpose |
|---------|-------------|---------|
| musicutil | `require 'musicutil'` | Scales, note conversion, chords |
| sequins | `require 'sequins'` | Step sequencer building |
| lattice | `require 'lattice'` | Superclock-based sequencers |
| lfo | `require 'lfo'` | Low-frequency oscillators for modulation |
| er | `require 'er'` | Euclidean rhythm generation |
| ui | `require 'ui'` | UI widgets (dials, sliders, etc.) |
| formatters | `require 'formatters'` | Parameter display formatting |
| tabutil | `require 'tabutil'` | Table utilities |
| util | (global) | `clamp`, `wrap`, `round`, `time`, `scandir` |

---

### Live Coding Example (from Maiden docs)

```lua
engine.load('PolyPerc')

base = 440

function notes()
  while true do
    engine.pan(math.random(-1,1))
    clock.sync(1)
    engine.hz(base * math.random(3))
    clock.sync(1/3)
    engine.hz(base / math.random(6))
    clock.sync(2/3)
    engine.hz((base*3) / math.random(6))
  end
end

seq = clock.run(notes)
clock.cancel(seq)

params:set("clock_tempo", 94 * math.random(3))
```

---

### API Reference

Full onboard API docs accessible at `norns.local/doc` or via the `?` icon in maiden.
