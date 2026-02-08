# better-pulsar

A pulsar synthesis instrument for monome norns.

## Overview

better-pulsar is a monophonic synthesizer that implements pulsar synthesis, a technique developed by Curtis Roads for generating complex, evolving timbres from simple waveforms. The instrument is designed for real-time performance with full MIDI control.

## What is Pulsar Synthesis?

Pulsar synthesis generates sound by repeating short waveforms called **pulsarets** at a controllable rate (the **fundamental frequency**). Each pulsaret is shaped by a **window function** (envelope), and the duration of the pulsaret relative to the period creates the characteristic timbre.

Key concepts:
- **Fundamental frequency**: The repetition rate of the pulsar train (perceived pitch)
- **Formant frequency**: Controls the pulsaret duration, creating formant-like resonances
- **Pulsaret**: The basic waveform repeated each cycle (sine, triangle, sinc, etc.)
- **Window**: Envelope applied to each pulsaret (gaussian, hann, exponential, etc.)
- **Duty cycle**: Ratio of pulsaret duration to period (derived from fundamental/formant ratio)
- **Masking**: Random omission of pulses for rhythmic/textural effects

When the fundamental frequency drops below 20 Hz, individual pulses become audible, blurring the line between rhythm and pitch.

## Features

- **10 pulsaret waveforms** with smooth morphing between shapes
- **5 window functions** with smooth morphing between envelopes
- **Formant frequency control** independent of fundamental pitch
- **Manual duty cycle mode** for direct pulse width control
- **Pulse masking** for rhythmic patterns and texture
- **Full MIDI control** with configurable CC mappings
- **Demo mode** with random sequence generation
- **3-page UI** with visual waveform display

## Pulsaret Waveforms

| Index | Name | Character |
|-------|------|-----------|
| 0 | sine | Pure, clean fundamental |
| 1 | sine x2 | Two cycles, brighter |
| 2 | sine x3 | Three cycles, richer harmonics |
| 3 | sinc | Bandlimited impulse, sharp attack |
| 4 | triangle | Soft, odd harmonics |
| 5 | sawtooth | Buzzy, all harmonics |
| 6 | square | Hollow, odd harmonics |
| 7 | formant | Vocal-like, 3 partials |
| 8 | pulse | Narrow 25% duty, nasal |
| 9 | noise | Textural, percussive |

## Window Functions

| Index | Name | Character |
|-------|------|-----------|
| 0 | rectangular | No shaping, harsh |
| 1 | gaussian | Smooth, natural |
| 2 | hann | Classic grain envelope |
| 3 | exp decay | Percussive, sharp attack |
| 4 | linear decay | Plucked quality |

## Controls

### Page 1: Main

| Control | Function |
|---------|----------|
| E1 | Formant frequency |
| E2 | Duty cycle |
| E3 | Masking amount |
| K2 | Next page |
| K3 | Next pulsaret |

### Page 2: Parameters

| Control | Function |
|---------|----------|
| E1 | Pulsaret morph |
| E2 | Formant frequency |
| E3 | Amplitude |
| K2 | Next page |

### Page 3: Demo Mode

| Control | Function |
|---------|----------|
| E1 | Tempo |
| E2 | Scale selection |
| E3 | Sequence length |
| K2 | Next page |
| K3 | Toggle demo on/off |

### MIDI

**Notes**: MIDI notes control fundamental frequency, velocity controls amplitude.

**Default CC Mappings** (configurable in PARAMS):

| CC | Parameter |
|----|-----------|
| 1 | Formant frequency |
| 2 | Duty cycle |
| 7 | Amplitude |
| 10 | Pan |
| 11 | Masking |
| 14 | Pulsaret (morph) |
| 15 | Window (morph) |
| 72 | Release time |
| 73 | Attack time |

## Parameters

### Pulsar Synthesis
- **formant hz** (20-4000 Hz): Formant frequency
- **duty cycle** (0.01-1.0): Manual duty cycle
- **duty mode**: Formant ratio or manual
- **pulsaret** (0-9): Waveform with morphing
- **window** (0-4): Envelope with morphing
- **masking** (0-1): Pulse omission probability
- **pan** (-1 to 1): Stereo position
- **attack** (0.001-2s): Amplitude attack
- **release** (0.01-4s): Amplitude release
- **amplitude** (0-1): Output level

### MIDI
- **midi device** (1-16): MIDI input device slot
- **midi channel** (1-16): MIDI input channel

### Demo Mode
- **demo mode**: Off/on
- **scale**: Scale selection (all musicutil scales)
- **tempo** (40-240 BPM): Sequence tempo
- **root note** (36-72): Scale root
- **sequence length** (1-64): Number of steps
- **regenerate sequence**: Create new random sequence

## Installation

1. Copy the `better-pulsar` folder to `dust/code/` on your norns
2. Restart norns or run `SYSTEM > RESTART` to compile the SuperCollider engine
3. Select `better-pulsar` from the script menu

### Via SFTP

```bash
scp -r better-pulsar we@norns.local:/home/we/dust/code/
ssh we@norns.local "systemctl restart norns-sclang"
```

## Requirements

- monome norns (tested on norns shield)
- MIDI controller (optional, for external control)

## Credits & References

### Pulsar Synthesis Theory

- **Curtis Roads** - Pioneer of pulsar synthesis and microsound techniques
  - "Microsound" (MIT Press, 2001)
  - "Sound Composition with Pulsars" - foundational paper on pulsar synthesis techniques

### Implementation References

- **Nathan Ho** - [Pulsar Synthesis](https://nathan.ho.name/posts/pulsar-synthesis/) - Clear explanation of pulsar synthesis concepts and parameters

- **SuperCollider Forum** - [Pulsar Synthesis Discussion](https://scsynth.org/t/pulsar-synthesis/1618) - Implementation approaches and code examples

- **Rodney DuPlessis** - [pd-pulsar](https://github.com/rodneydup/pd-pulsar) - Pure Data implementation of pulsar synthesis

- **Alberto de Campo** - SuperCollider Book Chapter 16, granular synthesis techniques

### Platform

- **monome** - [norns](https://monome.org/docs/norns/) sound computer platform
- **SuperCollider** - Audio synthesis engine

## License

This project is open source. Feel free to use, modify, and distribute.

## Author

Created with assistance from Claude (Anthropic).
