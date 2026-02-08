# Code Review: better-pulsar

Reviewer: Claude Opus 4.6 (audit only — no code modifications)
Date: 2026-02-07
Branch: `scripted-updates`

---

## Commit 1: `e0d470b` — "Initial commit: better-pulsar norns script"

**Files**: `.gitignore`, `README.md`, `better-pulsar.lua` (633 lines), `lib/Engine_BetterPulsar.sc` (350 lines)

---

### SuperCollider Engine (`lib/Engine_BetterPulsar.sc`)

#### Correctness

1. **CroneEngine structure**: Correct. Inherits from `CroneEngine`, uses `alloc` for setup and `free` for cleanup. File naming follows the `Engine_Name.sc` convention. Uses `context.out_b` for output bus and `context.xg` for execution group — both correct norns patterns.

2. **Buffer allocation and filling**: Correct. 10 pulsaret waveforms + 5 window functions allocated with `Buffer.alloc`, filled with `loadCollection` or `sine1`, with `server.sync` calls between allocation and filling. Good practice.

3. **Pulsaret waveform math**: All 10 waveforms generate correctly:
   - Sine harmonics via `sine1` — correct
   - Sinc function — correct (`sin(pi*x)/(pi*x)` with zero-check)
   - Triangle, sawtooth, square, pulse — all correct bipolar signals
   - Formant — normalized correctly (`/1.75` for the sum `1 + 0.5 + 0.25`)
   - Noise burst with fixed seed — correct for reproducibility

4. **Window functions**: All 5 are correct:
   - Rectangular, Gaussian, Hann (`Signal.hanningWindow`), exp decay, linear decay

5. **Pulsar synthesis core logic** (`\betterPulsar`): Correct implementation of Roads' model:
   - `Phasor.ar` for phase, `Trig1.ar` for cycle detection
   - Duty cycle from formant ratio (`hz/formantHz`) or manual — correct
   - `inPulsaret = phase < actualDuty` — correct gating
   - Buffer crossfade via `LinXFade2` — correct
   - Stochastic masking via `TRand` — correct
   - `LeakDC` and `tanh` safety — good

6. **`\betterPulsarLite`**: Correct simplified version. Fixed sine pulsaret, no crossfading, lower CPU.

#### Bugs

1. **BUG — `maskSeed` argument declared but never used** (line 120 in committed version). The `maskSeed` arg is declared in `\betterPulsar` but has no effect — `TRand` uses its own internal random state. This is harmless dead code but misleading.

2. **BUG — `pulsaretBufNum` and `windowBufNum` declared but unused** (lines 127, committed). These variables are declared in `\betterPulsar` but never assigned. The actual buffer numbers use `pulsaretBufNum1`/`pulsaretBufNum2`. Again harmless but untidy — SC will allocate memory for unused variables.

3. **BUG — `period`, `silenceRatio`, `pulsaretLen`, `pulsaretSig`, `windowSig`, `masked` declared but unused** (lines 124-126, committed). Multiple unused variable declarations. SC doesn't optimize these away.

4. **BUG — `\betterPulsarLite` ignores attack/release args**. The `Env.asr` uses hardcoded `0.001` attack and `0.1` release (line 351), ignoring the Lua params. If a user adjusts attack/release and then the engine falls back to lite mode (if that path were ever used), the envelope won't match expectations.

5. **POTENTIAL BUG — Phasor trigger race condition**. `Trig1.ar(phase < 0.01, SampleDur.ir)` uses a threshold of 0.01. At very high fundamental frequencies (e.g., `hz = 5000`, so 5000 cycles/sec at 48kHz sample rate), the phase increment per sample is `5000/48000 = 0.104`. This means the phase could jump from ~0.9 to ~0.004 in a single sample, and the `< 0.01` window is only ~1 sample wide — it should still trigger correctly. But at extreme frequencies approaching Nyquist, the trigger could become unreliable. For the practical range (up to ~5kHz), this should be fine.

6. **POTENTIAL BUG — No `server.sync` after SynthDef additions**. There is a `server.sync` after buffer filling but the SynthDefs are added *after* that sync. The final `server.sync` (line 357, current) comes after all SynthDefs, which is correct. However, commands that reference SynthDefs (like `\noteOn`) are registered after the sync, so there's a theoretical race if `noteOn` is called before the server finishes compiling. In practice, `params:bang()` in Lua triggers after `init()` completes, and the engine `alloc` finishes synchronously from norns' perspective, so this is very unlikely to cause issues.

#### Performance

1. **Buffer read at audio rate**: Each `BufRd.ar` call with interpolation mode 4 (cubic) is moderately expensive. The `\betterPulsar` SynthDef uses 4 `BufRd.ar` calls (2 pulsaret + 2 window), each with cubic interpolation. This is reasonable for norns shield but could be a concern with many simultaneous instances.

2. **`Select.kr` with 10-element array**: `Select.kr(pulsaretIdx1, pulsaretBufs.collect(_.bufnum))` evaluates all 10 buffer numbers at control rate even though only one is selected. This is a known SC pattern and acceptable for 10 elements, but it does scale linearly.

3. **`tanh` soft clipping**: `(sig * 0.8).tanh` is a relatively cheap waveshaper. Good choice over `Limiter` for single-voice use.

4. **No `Lag` on formant/duty parameters**: Changes to `formantHz` or `dutyCycle` during a note will cause immediate jumps in the duty cycle calculation, potentially producing clicks. Consider `Lag.kr` on these for smoother transitions (noted, not a bug per se).

#### Missed Requirements (vs. Roads' pulsar synthesis theory)

1. **No burst masking**: Only stochastic masking is implemented. Roads' Section 3.2 describes deterministic burst masking (e.g., 4-on-2-off patterns) which produces subharmonic AM effects. The current `TRand`-based masking is purely probabilistic.

2. **No PaWM (Pulsaret-Width Modulation)**: Roads describes the case where `d > p` (duty cycle exceeds period), causing overlapping pulsarets. The current implementation clips duty to `[0.01, 1.0]`, preventing this regime entirely.

3. **No edge factor control**: Roads describes a crossfade parameter at the pulsaret cutoff point. The current implementation has a hard gate (`inPulsaret = phase < actualDuty`), which can produce clicks at the pulsaret boundary, especially with rectangular windows.

---

### Lua Script (`better-pulsar.lua`)

#### Correctness

1. **Script header comments**: Correct norns format — first 3 lines become the script description in the selector.

2. **`engine.name`**: Set before any callbacks — correct.

3. **`params:bang()` in `init()`**: Called at line 245 — correct, ensures all param actions fire and sync state to the SC engine.

4. **MIDI handling**: Correct use of `midi.connect()`, `midi.to_msg()`, channel filtering, note on/off, CC routing.

5. **`cleanup()` function**: Correctly cancels demo clock and redraw clock. Good practice.

6. **`clock.run` for redraw**: Running `redraw()` at 15fps via `clock.sleep(1/15)` — correct pattern for norns.

7. **`musicutil` usage**: Correct for `note_num_to_freq`, `note_num_to_name`, `generate_scale`, and `SCALES`.

#### Bugs

1. **BUG — Demo mode uses `clock.sleep` instead of `clock.sync`** (line 641, 647, 650). The demo sequencer uses `clock.sleep(step_time)` with manual BPM-to-seconds conversion. This means it does NOT sync to the norns global clock or respond to tempo changes from Link/MIDI clock sources. It also drifts over time due to cumulative floating-point sleep inaccuracy. Should use `clock.sync(beats)` for tempo-synced sequencing.

2. **BUG — `demo_loop` gate timing can produce zero/negative sleep** (lines 639-647). If `step.gate == 1.0`, then `note_duration = step_time * 1.0 = step_time`, and the remaining sleep is `step_time - note_duration = 0`. A `clock.sleep(0)` should yield the coroutine immediately but is technically wasteful. More importantly, if floating-point imprecision makes it slightly negative, behavior is undefined.

3. **BUG — No bounds check on `pulsaret_names` index** (line 408). `p_idx` is calculated as `math.floor(p) + 1`. If `p = 9` (max), then `p_idx = 10`, and `p_idx2 = math.min(p_idx + 1, 10) = 10`. Accessing `pulsaret_names[11]` would be nil, but since `p_frac < 0.01` at integer values, it takes the `wave_str = pulsaret_names[p_idx]` path (which is `pulsaret_names[10]` = "noise"). This works, but the off-by-one reasoning is fragile.

4. **BUG — `window_names` index can overflow** (line 416-424). Same pattern as above. With `window` max at 4, `w_idx = math.floor(4) + 1 = 5`, and `window_names[5]` = "linear decay" (valid). But `w_idx2 = math.min(w_idx + 1, 5) = 5`, so `window_names[6]` would be accessed in the morphing display path. However, this only triggers when `w_frac > 0.01` at `w = 4.x`, which shouldn't happen since the controlspec max is 4 with no step. Still, if a CC maps to exactly 127 producing 4.0, and floating point makes it 4.0001, this would access a nil index and display "win: line>nil".

5. **MINOR — `midi_device` is global** (line 241, 182-183). The variable `midi_device` is assigned without `local` declaration in `init()`, making it an implicit global. This works but is poor Lua hygiene and could clash with other scripts if they use the same global name (unlikely in norns but still).

6. **MINOR — E1 on page 1 controls formant, E1 on page 2 controls pulsaret** (lines 318-319, 328-329). This is a UX choice, not a bug, but E1 is typically reserved for "meta" navigation (like page switching or volume) on norns. Having E1 do different things on different pages without a page-switch function assigned to E1 breaks the norns convention where K2/K3 are the primary action keys and E1 is secondary.

#### Performance

1. **Redraw at 15fps**: Acceptable. The `draw_pulsar()` function iterates `pulsaret_width` times (up to 120 iterations) doing sine/exp calculations per frame. At 15fps this is ~1800 trig calls/sec — negligible on norns.

2. **CC handler uses chained `if/elseif`** (lines 282-312): 9 comparisons per CC message. With active CC streams, this is called frequently. A lookup table would be more efficient, but 9 branches is fine in practice.

3. **`params:get()` called in `redraw()`**: Multiple `params:get()` calls per frame (lines 403, 415, 430-434, 452-458, 494-498, etc.). These are dictionary lookups internally and are fast, but caching frequently-read values in local variables within `redraw()` would be marginally faster.

#### Missed Requirements

1. **No `cleanup()` for MIDI device**: The `cleanup()` function cancels clocks but doesn't disconnect or nil the MIDI device. Not strictly required (norns handles this), but good practice.

2. **No parameter groups**: The params are added with separators but no `params:add_group()`. Using groups would make the PARAMS menu more navigable, especially with the growing number of parameters.

3. **No K1 handling**: K1 is not used for anything. Conventional norns scripts use K1 as a modifier (shift) key. This is a missed opportunity for expanding control without adding pages.

---

### File Structure

#### Correctness

1. **Layout**: `better-pulsar.lua` at root, `lib/Engine_BetterPulsar.sc` in `lib/` — correct norns convention.
2. **`.gitignore`**: Covers `.DS_Store`, `.vscode/`, `.claude/`, swap files — good.

#### Missed

1. **No `lib/` Lua engine wrapper**: The norns engine study pattern recommends a separate `lib/betterpulsar_engine.lua` that encapsulates controlspecs and `add_params()`. The current script does this inline, which works but makes the engine less reusable by other scripts.

---

## Uncommitted Changes: Multi-formant addition (`lib/Engine_BetterPulsar.sc` modified + `better-pulsar.lua` staged params)

**Addresses Task #1 from `tasks.md`**: "Multiple formants with independent panning"

---

### SuperCollider Changes

#### Correctness

1. **New `\betterPulsarMulti` SynthDef**: Implements 2-3 formant generators sharing the same fundamental frequency but with independent formant frequencies and pan positions. This correctly implements Roads' Section 3.1.

2. **Dynamic synth selection** (line 381): `if(pFormantCount > 1, {\betterPulsarMulti}, {\betterPulsar})` — correct. Falls back to the lighter single-formant synth when only 1 formant is needed.

3. **Formant mixing with normalization** (line 306): `(sig1 + sig2 + sig3) / formantCount.max(1)` — correct amplitude scaling.

4. **Multi-formant params passed to `noteOn`** (lines 387-393): All new params (`formant2Hz`, `formant3Hz`, `pan2`, `pan3`, `formantCount`) are included — correct.

#### Bugs

1. **BUG (CRITICAL) — Formant count gating uses multiplication by boolean at audio rate** (lines 286, 302). `Pan2.ar(sig, pan2) * (formantCount >= 2)` relies on SC treating the boolean comparison result as 0 or 1 at control rate, which is valid SC behavior. However, when `formantCount < 2`, sig2 is still **fully computed** (all BufRd, LinXFade2, masking, etc.) — it's just multiplied by zero at the output. This means **all 3 formants always run at full CPU cost regardless of `formantCount`**. The inactive formants are computed and then thrown away. This directly contradicts the comment "Each additional formant adds ~50% CPU overhead" — in reality, all 3 always run.

2. **BUG — Each formant generates its own independent masking** (lines 262, 278, 294). Each formant block calls `TRand.ar(0, 1, trig) > masking` independently, meaning each formant's pulses are masked at different random points. Per Roads, formants sharing a fundamental should be masked together (they're components of the same pulsar, not independent streams). This creates an unintended texture where formants drop in and out independently.

3. **BUG — `\formantCount` command doesn't update running synth** (lines 484-487). The comment says "changing formant count requires note retrigger to switch synth" — this is by design, but it means if a user changes formant count mid-note, nothing audible happens until the next `noteOn`. However, the Lua side has no indication of this to the user. More critically, if `formantCount` is changed to 1 while a `\betterPulsarMulti` synth is playing, the synth keeps running all 3 formants (just outputting only formant 1 divided by 1). There's no way to switch to the lighter `\betterPulsar` without retriggering.

4. **BUG — Multi-formant synth passes `formant2Hz`/`formant3Hz` to single-formant synth** (lines 387-388). When `pFormantCount == 1`, the `\betterPulsar` SynthDef is used, but the `noteOn` command still passes `\formant2Hz`, `\formant3Hz`, `\pan2`, `\pan3`, `\formantCount` in the args array. SuperCollider silently ignores unknown arguments in `Synth.new`, so this doesn't cause an error, but it's wasteful and could be confusing during debugging.

5. **BUG — Code duplication creates maintenance risk**. The formant generation code is copy-pasted 3 times (lines 257-271, 273-287, 289-303) with only the formant frequency and pan variables changed. If a bug is found in one block, it must be fixed in all three. SC supports functions within SynthDefs, but this `.value` block pattern doesn't allow parameterization easily. A better approach would be using `3.collect` with an array of frequencies/pans.

#### Performance

1. **CRITICAL — 3x CPU overhead always active**. As noted in bug #1, all three formants compute at all times. On norns shield (RPi CM3+), this is significant. The `\betterPulsar` single-formant SynthDef uses 4 `BufRd.ar` calls. The multi-formant version uses 12 `BufRd.ar` calls (4 per formant) + 3 `Pan2.ar` + 3 `LinXFade2.ar` + 3 `Lag.ar` for masking. This is roughly 3x the CPU of the single-formant version, always, even when only 1 formant is audible.

2. **Shared masking TRand**: Three separate `TRand.ar` instances are created (one per formant block), each triggered by the same `trig` signal. This triples the random number generation when a single shared mask value would suffice.

3. **Division by `formantCount.max(1)` at audio rate** (line 306). This division happens per-sample. Since `formantCount` only changes at control rate, this could be `formantCount.max(1).reciprocal` pre-computed at control rate and multiplied instead. Minor optimization.

### Lua Changes (Multi-formant params in `better-pulsar.lua`)

#### Correctness

1. **New params correctly declared**: `formant_count`, `formant2_hz`, `formant3_hz`, `pan2`, `pan3` all have appropriate controlspecs and actions that call the matching engine commands.

2. **`formantCount` uses `"i"` (integer) format string** in SC command, and the Lua side uses `params:add_number` with integer range 1-3 — correct match.

#### Bugs

1. **MINOR — Multi-formant params not exposed to MIDI CC**. The new formant2/formant3/pan2/pan3 params have no CC mappings in the `handle_cc` function or CC mapping params. Users can't control them from a MIDI controller without editing the script.

2. **MINOR — Multi-formant params not shown on any UI page**. None of the 3 `redraw_*` functions display or allow encoder control of the new multi-formant parameters. They're only accessible via the PARAMS menu.

3. **MINOR — No separator before multi-formant params would be more idiomatic as `params:add_group`**.

---

## Summary of All Issues

### Critical

| # | File | Issue |
|---|------|-------|
| 1 | Engine_BetterPulsar.sc | Multi-formant SynthDef always computes all 3 formants regardless of `formantCount`, wasting ~2x CPU when only 1 formant is needed |
| 2 | Engine_BetterPulsar.sc | Each formant has independent stochastic masking — should share a single mask per Roads' model |

### Major

| # | File | Issue |
|---|------|-------|
| 3 | Engine_BetterPulsar.sc | `formantCount` command doesn't affect running synth and can't switch SynthDef mid-note |
| 4 | better-pulsar.lua | Demo sequencer uses `clock.sleep` instead of `clock.sync` — won't sync to external clock, drifts over time |
| 5 | Engine_BetterPulsar.sc | Multi-formant code is copy-pasted 3x with no parameterization — maintenance risk |

### Minor

| # | File | Issue |
|---|------|-------|
| 6 | Engine_BetterPulsar.sc | `maskSeed`, `period`, `silenceRatio`, `pulsaretLen`, `pulsaretSig`, `windowSig`, `masked`, `pulsaretBufNum`, `windowBufNum` — unused variables |
| 7 | Engine_BetterPulsar.sc | `\betterPulsarLite` ignores attack/release params |
| 8 | Engine_BetterPulsar.sc | Passing multi-formant args to single-formant SynthDef (silently ignored, but wasteful) |
| 9 | better-pulsar.lua | `midi_device` is an implicit global |
| 10 | better-pulsar.lua | Demo gate=1.0 produces `clock.sleep(0)` |
| 11 | better-pulsar.lua | Fragile index bounds in waveform/window name display |
| 12 | better-pulsar.lua | Multi-formant params have no MIDI CC mapping or UI page exposure |
| 13 | better-pulsar.lua | No `params:add_group()` usage for menu organization |
| 14 | better-pulsar.lua | No edge smoothing at pulsaret boundary (can click with rectangular window) |

### Missing from Tasks (not yet implemented)

Per `tasks.md`, the following remain unaddressed:
- Task 2: Convolution with samples
- Task 3: Wavetable morphing (partially done — crossfade exists but not CC-mapped as a continuous morph target)
- Task 4: Time-scale bridging gestures
- Task 5: Burst masking (only stochastic masking exists)
- Task 6: Custom pulsaret loading
- Task 7: LFO modulation
- Task 8: Reverb send
- Task 9: Polyphony
- Task 10: Portamento/glide
