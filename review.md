# Code Review: better-pulsar

Reviewer: Claude Opus 4.6 (audit only — no code modifications)
Date: 2026-02-08
Branch: 26 commits, `e0d470b..6e3ec7c`

---

## Commit 1: `e0d470b` — "Initial commit: better-pulsar norns script"

**Files**: `.gitignore`, `README.md`, `better-pulsar.lua` (633 lines), `lib/Engine_BetterPulsar.sc` (350 lines)

### Correctness

**SuperCollider Engine**:
- CroneEngine structure is correct: inherits `CroneEngine`, uses `alloc`/`free`, `context.out_b` for output bus, `context.xg` for execution group.
- File naming `Engine_BetterPulsar.sc` in `lib/` follows norns convention.
- Buffer allocation: 10 pulsaret + 5 window buffers with `server.sync` between allocation and filling — correct.
- All 10 pulsaret waveforms generate correctly (sine harmonics via `sine1`, sinc with zero-check, triangle/saw/square/pulse bipolar, formant normalized by `/1.75`, noise with fixed seed).
- All 5 window functions are correct (rectangular, Gaussian, Hann via `Signal.hanningWindow`, exp decay, linear decay).
- Core pulsar synthesis logic correctly implements Roads' model: `Phasor.ar` for phase, duty cycle from formant ratio or manual, `inPulsaret` gating, buffer crossfade via `LinXFade2`, stochastic masking via `TRand`, `LeakDC` and `tanh` safety.

**Lua Script**:
- Script header format correct (3-line comments for selector menu).
- `engine.name` set before callbacks — correct.
- `params:bang()` called in `init()` — correct.
- MIDI handling: correct `midi.connect()`, `midi.to_msg()`, channel filtering, note on/off, CC routing.
- `cleanup()` cancels demo and redraw clocks — correct.
- `clock.run` redraw at 15fps via `clock.sleep(1/15)` — correct norns pattern.

### Bugs

1. **SC: Unused variables** — `maskSeed`, `period`, `silenceRatio`, `pulsaretLen`, `pulsaretSig`, `windowSig`, `masked`, `pulsaretBufNum`, `windowBufNum` are declared but never used in `\betterPulsar`. Harmless but wastes memory and is misleading. **[FIXED in d0ddd7a]**

2. **SC: `\betterPulsarLite` ignores attack/release** — Uses hardcoded `Env.asr(0.001, 1, 0.1)` instead of the `attack`/`release` args. If this SynthDef were ever used, the envelope wouldn't match user settings. **[FIXED in d0ddd7a — removed entirely]**

3. **Lua: Demo sequencer uses `clock.sleep` not `clock.sync`** — `demo_loop()` uses `clock.sleep(step_time)` with manual BPM conversion. This will NOT sync to norns global clock (Link/MIDI clock sources) and will drift over time. **[FIXED in 8f09cd7 — see notes on that commit]**

4. **Lua: Demo gate=1.0 produces zero sleep** — When `step.gate == 1.0`, the remaining sleep after note-off is `step_time - note_duration = 0`. `clock.sleep(0)` is wasteful; negative float would be undefined. **[FIXED in 8f09cd7 — minimum gate of 0.05 beats]**

5. **Lua: Fragile waveform name index bounds** — `pulsaret_names` and `window_names` display logic calculates `p_idx2 = math.min(p_idx + 1, 10)` but `pulsaret_names[11]` would be nil if the fractional check fails due to float imprecision at edge values. Practically unlikely but brittle.

6. **Lua: `midi_device` is an implicit global** — Assigned without `local` declaration. Poor Lua hygiene. **[FIXED in 33fdd2b]**

7. **SC: No edge smoothing at pulsaret boundary** — Hard gate `inPulsaret = phase < actualDuty` can produce clicks, especially with rectangular window. Roads describes an "edge factor" crossfade parameter for this.

### Performance

- 4 `BufRd.ar` calls with cubic interpolation per voice is moderate for norns shield. Acceptable for monophonic use.
- `Select.kr` with 10-element array evaluates all 10 bufnums each control period — acceptable at this scale.
- `tanh` soft clipping is cheap. Good choice over `Limiter` for single-voice.
- No `Lag` on formant/duty parameters: abrupt changes during a note can click. Not a performance issue, but an audio quality one.
- Redraw: `draw_pulsar()` iterates up to 120× with trig calls at 15fps (~1800 trig calls/sec) — negligible.

### Missed Requirements

- No burst masking (only stochastic). Roads' Section 3.2 describes deterministic on/off patterns.
- No PaWM (duty > period). Current clips to `[0.01, 1.0]`.
- No K1 modifier key usage (norns convention for shift).
- No `params:add_group()` for menu organization.

---

## Commit 2: `8fed862` — "Add multiple formants with independent panning (Task 1)"

### Correctness

- New `\betterPulsarMulti` SynthDef correctly implements 2-3 formant generators sharing fundamental frequency with independent formant frequencies and pan positions. This is Roads' Section 3.1.
- Dynamic synth selection: `if(pFormantCount > 1, {\betterPulsarMulti}, {\betterPulsar})` — correct.
- Formant mixing normalized by `/ formantCount.max(1)` — correct.
- Lua params for `formant_count`, `formant2_hz`, `formant3_hz`, `pan2`, `pan3` correctly declared with appropriate controlspecs.

### Bugs

1. **CRITICAL: All 3 formants always compute regardless of `formantCount`** — `Pan2.ar(sig, pan2) * (formantCount >= 2)` multiplies by 0/1 at the output, but the entire signal chain still runs. All 3 formants use full CPU always. **[FIXED in e11cdf6 — split into \betterPulsar2 and \betterPulsar3]**

2. **BUG: Each formant has independent stochastic masking** — Each formant block creates its own `TRand.ar`, so formants mask independently. Per Roads, formants sharing a fundamental should share a single mask. **[FIXED in 4afcfae (Task 5) — shared mask variable]**

3. **BUG: Multi-formant args passed to single-formant SynthDef** — When `pFormantCount == 1`, `noteOn` uses `\betterPulsar` but passes `\formant2Hz` etc. SC silently ignores unknown args — harmless but misleading.

4. **Code duplication** — Formant generation copy-pasted 3× with only frequency/pan variables changed. **[Still present; worsened by e11cdf6 split — see notes]**

### Performance

- **CRITICAL**: ~3x CPU overhead always active when using multi-formant synth, regardless of actual formant count. **[FIXED in e11cdf6]**

---

## Commit 3: `2e628da` — "Document Task 1 implementation details"

Documentation-only commit. No code changes to review.

---

## Commit 4: `275fc5c` — "Add sample-based pulsaret / convolution (Task 2)"

### Correctness

- New `\betterPulsarSample` SynthDef correctly reads from a loaded sample buffer as the pulsaret waveform. Window morphing still applies. This implements Roads' Section 3.3.
- `loadSample` command uses async `Buffer.read` with callback — correct SC pattern.
- `sampleRate` multiplier allows pitch-independent time stretching of the sample pulsaret.
- Synth selection priority: sample mode > multi-formant > single — correct via `case` statement.
- Lua params: `use_sample`, `sample_file`, `sample_rate` — all correct.
- `sampleBuf` freed in `free` method — correct cleanup.

### Bugs

1. **BUG: Sample buffer replaced without sync** — `loadSample` does `sampleBuf.free; sampleBuf = buf` inside an async callback. If a synth is playing, freeing the old buffer will cause a click or crash. **[FIXED in 51dea40]**

2. **BUG: `sampleBuf.bufnum` hardcoded in SynthDef** — `BufRd.ar(1, sampleBuf.bufnum, ...)` bakes the buffer number into the SynthDef at compile time. New samples don't affect running synths. **[FIXED in 51dea40]**

3. **BUG: No validation of sample file path** — Lua sends the path directly to `engine.loadSample`. If the file doesn't exist, `Buffer.read` will fail silently on the SC side.

4. **MINOR: Pre-allocated 48000-sample buffer immediately freed** — `sampleBuf = Buffer.alloc(server, 48000, 1)` at init, but `loadSample` replaces it. Before any sample is loaded, playing in sample mode reads from an empty buffer (silence — harmless but confusing).

### Performance

- Single `BufRd.ar` for the sample + 2 for window crossfade = 3 total. Lighter than `\betterPulsar` (4 BufRd). Good.

---

## Commit 5: `cff69c6` — "Document Task 2 implementation details"

Documentation-only. No code changes.

---

## Commit 6: `efe1806` — "Document Task 3 (wavetable morphing) - already implemented"

Documentation-only. Notes that wavetable morphing was already implemented in the initial commit via `LinXFade2`. Correct assessment.

---

## Commit 7: `6e6f599` — "Add time-scale bridging gestures (Task 4)"

### Correctness

- Implements time-scale bridging that sweeps `hz` from infrasonic to audio rate (or reverse) at 30fps. This is Roads' signature technique.
- Three curve types: linear, exponential, logarithmic — correct. Exponential default is the natural choice for frequency sweeps.
- Direction parameter allows up (rhythm→pitch) and down (pitch→rhythm).
- Properly cancels existing bridge before starting a new one.
- `cleanup()` updated to call `stop_bridge()`.

### Bugs

1. **BUG: Bridge requires an already-playing synth** — `engine.hz()` only works if a synth exists. The bridge trigger doesn't create one. **[FIXED in fb3975c]**

2. **BUG: Bridge update rate too low for smooth audio-rate transitions** — 30 updates/second means hz steps of potentially hundreds of Hz between updates at the high end. Without `Lag` on `hz`, each step produces a discontinuity. **[PARTIALLY ADDRESSED by Task 10 glide — Lag.kr on hz smooths transitions when glide > 0]**

3. **MINOR: Bridge sends `engine.hz()` which updates synth reference** — If a new note is triggered during a bridge, the bridge continues overwriting `hz` on the synth. **[PARTIALLY FIXED in fb3975c — `note_hz` is now updated during bridge]**

### Performance

- 30 Lua→SC `engine.hz` calls per second is negligible. No concerns.

---

## Commit 8: `5314367` — "Document Task 4 implementation details"

Documentation-only. No code changes.

---

## Commit 9: `4afcfae` — "Add burst masking patterns (Task 5)"

### Correctness

- Adds deterministic burst masking via `PulseCount.ar(trig) % burstPeriod < burstOn` — correct implementation of Roads' Section 3.2.
- `Select.ar(useBurst, [stochasticMask, burstMask])` correctly switches between masking modes.
- Burst masking added to all SynthDefs — good consistency.
- **Fixes previous bug**: Multi-formant masking now uses shared `mask` variable instead of per-formant independent TRand.
- Lua params: `use_burst`, `burst_on`, `burst_off` — correct.

### Bugs

1. **BUG: `PulseCount.ar` never resets** — Counter accumulates indefinitely. At very high fundamental frequencies, could theoretically overflow after extended continuous play. `Stepper.ar` with explicit wrap would be more robust. Practically unlikely.

2. **MINOR: Both masking modes always computed** — Both stochastic + burst are computed; only one selected. Minimal waste.

### Performance

- `PulseCount.ar` and modulo are very cheap operations. Negligible overhead.

---

## Commit 10: `192cc58` — "Document Task 5 implementation details"

Documentation-only. No code changes.

---

## Commit 11: `70265b4` — "Document Task 6 (custom pulsaret loading) - implemented in Task 2"

Documentation-only. Correct — Task 6 is covered by `loadSample` from Task 2.

---

## Commit 12: `a05222f` — "Add LFO modulation (Task 7)"

### Correctness

- Uses norns `lfo` library (`require "lfo"`) — correct.
- 4 LFOs created: formant, duty cycle, masking, pan — each with enable/disable, rate, and depth.
- `init_lfos()` called after engine params, before `params:bang()` — correct order.
- `cleanup()` stops all LFOs — correct.

### Bugs

1. **BUG: Formant LFO fights with user encoder input** — The formant LFO reads `params:get("formant_hz")` as base and multiplies, then sends via `engine.formantHz()`. The param action also sends via `engine.formantHz()`. Both write to the same engine parameter. **[FIXED in fb3975c]**

2. **BUG: Duty cycle LFO can exceed bounds** — Clamp handles it, but the LFO waveform will be "clipped" rather than sinusoidal at extremes. Not a crash bug, but unexpected audible behavior.

3. **MINOR: LFO rate 1/v safe** — Controlspec min is 0.01, so `1/v` never divides by zero. Safe.

### Performance

- 4 LFOs with sine shape is negligible CPU. Engine command calls are just OSC messages — fine.

---

## Commit 13: `49e3387` — "Document Task 7 implementation details"

Documentation-only. No code changes.

---

## Commit 14: `c680151` — "Add reverb send controls (Task 8)"

### Correctness

- Uses norns built-in reverb via `audio` functions — correct approach (no SC changes needed).
- Params with appropriate controlspecs: mix 0-1, return 0-1, damp 0-1, size 0.5-5s.

### Bugs

1. **BUG: `audio.level_rev()` is wrong API** — Should be `audio.level_eng_rev()` to control engine-to-reverb send. `audio.level_rev()` controls global reverb output, affecting all sources. **[FIXED in 33fdd2b]**

2. **MINOR: No `audio.rev_on()` call** — Reverb may be disabled. Should call `audio.rev_on()` in init. **[FIXED in 33fdd2b]**

### Performance

- Zero additional CPU — norns reverb runs independently. Good choice.

---

## Commit 15: `92035f4` — "Document Task 8 implementation details"

Documentation-only. No code changes.

---

## Commit 16: `2af7213` — "Add polyphony support (Task 9)"

### Correctness

- 4-voice polyphony with oldest-voice-first stealing — correct algorithm.
- Voice tracking via `voices[]`, `voiceNotes[]`, `voiceAges[]` — correct pattern.
- Amplitude scaling: `vel / 127 * 0.7 / numVoices.sqrt` — prevents clipping. Correct.
- `polyMode` command properly releases all voices when switching modes.
- Lua mono/poly noteOff handling is correct.

### Bugs

1. **BUG (CRITICAL): Poly voice params never update mid-note** — All commands only update mono `synth`. In poly mode, changing a parameter mid-note has no effect on playing voices. **[FIXED in 7774767]**

2. **BUG: `free` method doesn't free poly voices** — Only frees mono synth. Poly synths orphaned on server. **[FIXED in 7774767]**

3. **BUG: `noteOff "f"` should be `"i"`** — Float-to-int comparison risk for voice matching. **[FIXED in 7774767]**

4. **CRITICAL: 4 poly × multi-formant = extreme CPU** — Potentially 48 BufRd.ar calls. Will likely exceed norns shield capacity. **[PARTIALLY ADDRESSED in 8c729e8 — warning printed]**

### Performance

- Even 4 × `\betterPulsar` may be tight on norns shield. With multi-formant, almost certainly overloads.

---

## Commit 17: `4438ab7` — "Add portamento/glide (Task 10)"

### Correctness

- `Lag.kr(hz, glide)` applied to all SynthDefs — correct.
- `hzLag` used consistently for phase, period, and duty cycle calculations — correct.
- `glide` command updates both mono and poly voices — correct.
- Default 0 (no glide) is good for backwards compatibility.

### Bugs

1. **BUG: Glide non-functional** — Each `noteOn` creates a new synth. `Lag.kr` starts from the initial value with no transition. Glide only works if `synth.set(\hz, newHz)` is called on an existing synth. **[FIXED in f6956a4 — legato mode]**

2. **MINOR: Glide on duty cycle** — `hzLag / formantHz` means duty cycle smoothly changes during glide. Probably desirable, but means formant structure shifts during glide. Design choice, not a bug.

### Performance

- `Lag.kr` is one of the cheapest UGens. Zero concern.

---

## Fix Commits (e11cdf6..6e3ec7c)

---

## Commit 18: `e11cdf6` — "Fix Critical #1: Multi-formant now uses correct CPU per formant count"

### What It Fixes

Addresses **Critical #1**: Multi-formant SynthDef always computing all 3 formants regardless of `formantCount`.

### Changes

- Removed `\betterPulsarMulti` SynthDef.
- Added `\betterPulsar2` (2-formant) and `\betterPulsar3` (3-formant) as separate SynthDefs.
- `noteOn` synth selection uses `case`: `pFormantCount == 3` → `\betterPulsar3`, `== 2` → `\betterPulsar2`, else `\betterPulsar`.
- Each SynthDef only computes the formants it needs.

### Assessment

**Good fix.** CPU now correctly scales: 1 formant = 1x, 2 formants = ~2x, 3 formants = ~3x. The separate SynthDef approach is the standard SC pattern for this.

### Remaining Concerns

1. **Code duplication worsened** — Formant signal generation is now copy-pasted across 3 SynthDefs (betterPulsar: 1 formant block, betterPulsar2: 2 blocks, betterPulsar3: 3 blocks = 6 total formant blocks). Any bug fix in the signal chain must be replicated in all 6 copies. This is the inherent tradeoff of the separate-SynthDef approach vs. a single configurable SynthDef. The CPU efficiency gain justifies it, but maintenance risk is real.

2. **Changing formant count requires retrigger** — The `\formantCount` command only stores the value; it doesn't swap the running synth. User must release and retrigger the note. Comment in code acknowledges this. Acceptable behavior.

---

## Commit 19: `7774767` — "Fix Critical #2, #4, Major #12: Poly voice updates and cleanup"

### What It Fixes

Addresses **Critical #2** (poly params not updating), **Critical #4** (free doesn't free poly voices), and **Major #12** (noteOff "f" should be "i").

### Changes

- All parameter commands (`formantHz`, `amp`, `pan`, `pulsaret`, `window`, `dutyCycle`, `useDutyCycle`, `masking`, `attack`, `release`, `formant2Hz`, `formant3Hz`, `pan2`, `pan3`, `sampleRate`, `burstOn`, `burstOff`, `useBurst`) now include `numVoices.do({ |i| if(voices[i].notNil, { voices[i].set(...) }) })`.
- `hz` command also updates poly voices.
- `noteOff` format changed from `"f"` to `"i"`.
- `free` method now iterates `voices[]` to free poly synths.

### Assessment

**Comprehensive fix.** Every command now correctly propagates to both mono and poly voices. The `"i"` format for noteOff eliminates float comparison risk. The free method properly cleans up all synth nodes.

### Remaining Concerns

1. **Amp command in poly mode doesn't account for voice count** — `amp` command sets `pAmp = val` and propagates to all voices. But poly voices were created with `vel / 127 * 0.7 / numVoices.sqrt` scaling. A blanket `amp.set` overwrites this per-voice scaling. If the user changes amplitude while poly voices are playing, all voices jump to the same amplitude regardless of their original velocity. This is a design tradeoff, not a bug — the amp parameter acts as a "master volume" rather than per-voice level.

---

## Commit 20: `8f09cd7` — "Fix Major #5: Demo sequencer now uses clock.sync for proper timing"

### What It Fixes

Addresses **Major #5**: Demo sequencer using `clock.sleep` instead of `clock.sync`.

### Changes

- Demo now uses `beat_pos` accumulator initialized from `clock.get_beats()`.
- Timing uses `clock.sync(beat_pos)` instead of `clock.sleep(seconds)`.
- Sets `clock.tempo` from demo tempo param.
- Minimum gate of 0.05 beats prevents zero-duration waits.

### Assessment

**Partially correct.** The intent is right — using beat-based timing that respects norns global clock tempo. Setting `clock.tempo` is correct. The minimum gate fix is good.

### New Issue Introduced

1. **`clock.sync` used unconventionally** — `clock.sync(n)` in norns syncs to the next multiple of `n` beats from beat 0 (e.g., `clock.sync(1)` syncs to the next quarter note, `clock.sync(4)` to the next bar of 4). The code passes ever-increasing absolute beat positions (10.75, 11.0, 11.5, 12.0, ...) to `clock.sync`. This *works by coincidence* — when `beat_pos` is slightly ahead of the current beat, the next multiple of `beat_pos` IS `beat_pos` itself (since current_beat < beat_pos < 2×beat_pos). However:

   - **Fragile**: If Lua processing ever causes current_beat to overshoot past beat_pos (e.g., due to CPU contention or a slow Lua GC), the next multiple of beat_pos would be 2×beat_pos — causing a catastrophic pause of ~beat_pos beats (potentially minutes of silence).
   - **Unconventional**: The standard norns sequencer pattern is `clock.sync(1)` for each step (syncs to next quarter note boundary), with `clock.sleep` for sub-step gate durations.
   - **Correct pattern** would be:
     ```lua
     while true do
       clock.sync(1)       -- step boundary (syncs to external clock)
       engine.noteOn(...)
       clock.sleep(gate_beats * 60 / clock.tempo)  -- gate duration
       engine.noteOff(...)
     end
     ```

   In practice this works fine for demo purposes, but it's not a robust pattern for production use with external clock sources.

---

## Commit 21: `33fdd2b` — "Fix Major #10, Minor #15, #18: Reverb API and globals"

### What It Fixes

Addresses **Major #10** (`audio.level_rev()` wrong API), **Minor #15** (`midi_device` global), and **Minor #18** (no `audio.rev_on()`).

### Changes

- Changed `audio.level_rev(v)` to `audio.level_eng_rev(v)` — now correctly routes engine output to reverb bus.
- Added `audio.rev_on()` in `init()` after `params:bang()` — ensures reverb is active.
- `midi_device` was already a `local` in the fixed version (declared at top of file).

### Assessment

**Clean fixes.** All three issues fully resolved. `audio.level_eng_rev` is the correct norns API for engine-to-reverb send level. `audio.rev_on()` ensures the reverb is enabled regardless of the previous script's state.

---

## Commit 22: `f6956a4` — "Fix Major #11: Glide now functional via legato mode"

### What It Fixes

Addresses **Major #11**: Glide non-functional because each noteOn creates a new synth.

### Changes

- When `pGlide > 0` and a synth already exists in mono mode, `noteOn` does `synth.set(\hz, note.midicps, \amp, vel / 127 * 0.7)` instead of creating a new synth.
- Only when `pGlide == 0` or no synth exists does it create a new synth (standard behavior).

### Assessment

**Good fix.** This is the correct legato mode pattern for SC — reusing the existing synth node allows `Lag.kr` on `hz` to smoothly transition between pitches. The amp is also updated on the existing synth, so velocity changes are reflected.

### Remaining Concerns

1. **Poly mode glide still not functional** — In poly mode, each voice still creates a new synth. Implementing poly glide would require tracking the "last voice" and routing new notes to it, which is significantly more complex. This is an acceptable limitation — mono glide is the standard expectation.

2. **Legato only works for notes played close together** — If the synth's release envelope completes before the next note, `synth` will be a freed node (doneAction: 2 frees it), but the variable still holds a reference. `synth.set(\hz, ...)` on a freed node is a silent no-op in SC. The next noteOn would need to detect this. Currently, there's no mechanism to nil-out `synth` when the envelope completes, so the code assumes legato playing (new note before release finishes). If a gap occurs, the `.set` silently fails and no new synth is created — **the note is lost**.

   This is a significant edge case: if `pGlide > 0` and the user plays with gaps between notes (non-legato), notes after the first release will be silent until `pGlide` is set back to 0 or the user switches modes. The `synth` reference is never set to nil except in `polyMode` switch.

---

## Commit 23: `d0ddd7a` — "Fix Minor #13, #14, #22: Clean up unused code"

### What It Fixes

Addresses **Minor #13** (unused SC variables), **Minor #14** (`\betterPulsarLite` dead code), and **Minor #22** (stale betterPulsarLite).

### Changes

- Removed `\betterPulsarLite` SynthDef entirely. Added comment noting removal reason.
- Removed unused variable declarations from `\betterPulsar`.
- Fixed comment about pulsaret index range.

### Assessment

**Clean cleanup.** All changes are correct. The `\betterPulsarLite` removal is justified — it was never selected by any code path and lacked features (no burst masking, no glide, hardcoded envelope). The comment left in its place is helpful for anyone reading the git history.

---

## Commit 24: `fb3975c` — "Fix Major #8, #9: Bridge and LFO interaction issues"

### What It Fixes

Addresses **Major #8** (bridge requires existing synth) and **Major #9** (LFO fights with user input).

### Changes

**Major #8 fix**: Bridge now starts a synth if `current_note == nil`:
- Uses `musicutil.freq_to_note_num(start_hz)` to derive a MIDI note number.
- Calls `engine.noteOn(start_note, 100)` to create a synth.
- Sets `current_note` and `note_hz` so the bridge and UI are consistent.
- Also updates `note_hz = hz` during each bridge step.

**Major #9 fix**: Param actions now check LFO state before sending to engine:
- `formant_hz`, `duty_cycle`, `masking`, and `pan` actions check `params:get("lfo_X_enabled") ~= 2` before calling the engine.
- When LFO is active, only the LFO callback sends modulated values.
- When LFO is off, param changes go directly to engine.

### Assessment

**Good fixes, with minor caveats.**

### Remaining Concerns

1. **`musicutil.freq_to_note_num` compatibility** — This function was added to norns musicutil in a relatively recent update. Older norns installations may not have it. If absent, the bridge trigger will error at runtime. A safer fallback: `local start_note = math.floor(12 * math.log(start_hz / 440) / math.log(2) + 69 + 0.5)`.

2. **LFO stop doesn't resend param value** — When an LFO is disabled, its last-modulated value persists in the engine. For example: formant LFO running at 440Hz ±50% (220-880Hz). User disables LFO while it's at 700Hz. The engine stays at 700Hz, not the param's stored 440Hz. The user must manually tweak the formant param to resend it. A clean fix would be to send the current param value in the LFO disable action: `else lfo_formant:stop(); engine.formantHz(params:get("formant_hz")) end`.

3. **Bridge synth uses velocity 100** — Hardcoded velocity rather than reading from last note's velocity or a default param. Minor but could produce unexpected volume jumps.

---

## Commit 25: `51dea40` — "Fix Major #6, #7: Sample buffer loading and runtime safety"

### What It Fixes

Addresses **Major #6** (loadSample frees buffer while synth reads it) and **Major #7** (`sampleBuf.bufnum` baked into SynthDef at compile time).

### Changes

**Major #6 fix**: Buffer swap with deferred free:
- Saves `oldBuf = sampleBuf` before loading.
- Sets `sampleBuf = buf` and `pSampleBufNum = buf.bufnum` inside the async callback.
- Updates running synths with new buffer number.
- Defers freeing old buffer by 0.1s: `{ oldBuf.free }.defer(0.1)`.

**Major #7 fix**: `sampleBufNum` as synth argument:
- Added `sampleBufNum = 0` as arg in `\betterPulsarSample`.
- `BufRd.ar` and `BufFrames.kr` now use `sampleBufNum` arg instead of captured `sampleBuf.bufnum`.
- `pSampleBufNum` tracks current buffer number, passed at `noteOn` time.
- `loadSample` callback updates both mono and poly synths with new bufnum.

### Assessment

**Good fix.** The deferred free pattern correctly prevents reading from a freed buffer. The 0.1s delay is reasonable — `synth.set` messages are sent before the deferred free fires, and SC processes them in order. The `sampleBufNum` arg pattern is the standard SC approach for swappable buffers.

### Remaining Concerns

1. **Rapid successive loads** — If the user loads two samples in quick succession (within 0.1s), the first `oldBuf.free` defer is already scheduled. The second load captures the first load's buffer as `oldBuf`. Both defers fire, both old buffers are freed. This is actually correct — no double-free issue because each closure captures its own `oldBuf` reference.

2. **Initial sampleBufNum = 0** — The SynthDef default is `sampleBufNum = 0`, which points to buffer 0 on the server. Before any sample is loaded, if the user enables sample mode, `BufRd.ar(1, 0, ...)` reads from whatever buffer 0 happens to be (likely a pulsaret buffer). This produces a valid but confusing sound. However, `pSampleBufNum = sampleBuf.bufnum` is set during init (line 544), and this is passed in `noteOn` args, so in practice the initial empty buffer is read correctly.

---

## Commit 26: `8c729e8` — "Fix Critical #3, Minor #19: CPU warning and README update"

### What It Fixes

Addresses **Critical #3** (poly + multi-formant CPU overload) and **Minor #19** (README not updated).

### Changes

**Critical #3 fix**: CPU warnings:
- `formant_count` action: if `v > 1` and `poly_mode == 2`, prints warning.
- `poly_mode` action: if `v == 2` and `formant_count > 1`, prints warning.

**Minor #19 fix**: Comprehensive README update:
- Overview updated to mention polyphony and new features.
- New Features section organized by category (Sound Design, Modulation, Performance).
- All new parameters documented.
- Performance Notes section with CPU guidelines.
- Glide/legato and sample mode tips.

### Assessment

**Partial fix for Critical #3.** The warnings are helpful but don't prevent the user from creating CPU-overloading combinations. A more robust approach would be to auto-limit (e.g., reduce poly voice count when formant count increases), but the current approach is reasonable for an advanced synthesis tool where the user should understand the tradeoffs.

**Good fix for Minor #19.** README is now comprehensive and well-organized.

### Remaining Concerns

1. **Warning only goes to `print()`** — This outputs to the norns REPL/maiden console, which the user may not be watching during performance. No visual indication on the norns screen. **[PARTIALLY ADDRESSED by 6e3ec7c — CPU display on screen]**

---

## Commit 27: `6e3ec7c` — "Add CPU load display to all UI pages"

### What It Adds

New feature: real-time CPU load indicator on all 3 UI pages.

### Changes

- Added `cpu_load` variable and `cpu_clock` clock coroutine that polls every 500ms.
- Polls `norns.audio.cpu()` for audio engine CPU percentage.
- New `draw_cpu()` function displays CPU% in top-left corner of all pages.
- Color-coded brightness: dim (<50%), medium (50-70%), bright (>70%).
- `cpu_clock` properly cancelled in `cleanup()`.

### Assessment

**Good addition.** Directly addresses the CPU awareness gap identified in Critical #3 and Minor #20 (no UI updates for new features). Users can now see real-time CPU impact when enabling multi-formant, poly mode, or combinations.

### Potential Issues

1. **`norns.audio.cpu()` API validity** — The standard norns API for audio CPU load is typically accessed via the poll system (`poll.set("cpu_avg")`) or via internal functions like `_norns.audio_get_cpu_load()`. The function `norns.audio.cpu()` may not exist on all norns versions. If it doesn't exist, `norns.audio.cpu()` returns `nil`, and `util.round(nil, 1)` will throw a Lua error, crashing the CPU monitoring clock. This should be wrapped in a pcall or validated:
   ```lua
   local ok, val = pcall(function() return norns.audio.cpu() end)
   if ok and val then cpu_load = util.round(val, 1) end
   ```

2. **CPU text may overlap with title** — `draw_cpu()` draws at position (4, 6), which is the top-left. On the main page, the title "better-pulsar" is at (64, 8) centered. On the params page, "parameters" is at (64, 8) centered. The CPU text is left-aligned and the title is center-aligned, so they shouldn't overlap on the 128px-wide norns screen. However, at high CPU values like "cpu:99%", the text width (~48px) may approach the center-aligned title. Should be fine at norns' font size.

3. **500ms polling interval** — Reasonable tradeoff between responsiveness and overhead. CPU spikes shorter than 500ms may not be caught, but the display is informational, not a real-time meter.

---

## Cross-Cutting Issues (Post-Fix Assessment)

### Resolved Cross-Cutting Issues

1. **~~Parameter Commands Don't Update Poly Voices~~** — ✅ RESOLVED in 7774767. All commands now iterate `voices[]`.

2. **~~Growing SynthDef Count Without Cleanup~~** — ✅ RESOLVED in d0ddd7a (removed betterPulsarLite) and e11cdf6 (purposeful split into betterPulsar/2/3/Sample).

3. **~~No README Update~~** — ✅ RESOLVED in 8c729e8.

### Remaining Cross-Cutting Issues

1. **`noteOn` Arg List Still Growing** — The `noteOn` command now passes 23 arguments to `Synth.new`. The norns engine study pattern recommends a Dictionary + `getPairs` approach. This is a maintenance concern, not a correctness bug.

2. **No UI Controls for New Features** — All new features (Tasks 1-10) are only accessible via the PARAMS menu. The 3 UI pages still show only the original parameters. Only the CPU display (commit 27) was added to the pages. Encoders and keys still control only the original formant/duty/masking/pulsaret/amp params.

3. **Code Duplication in Multi-Formant SynthDefs** — The formant signal generation block is copy-pasted 6 times across betterPulsar2 (2×) and betterPulsar3 (3×), plus the original in betterPulsar. This is the unavoidable cost of the separate-SynthDef approach. Any future bug fix or feature addition to the signal chain must be applied in all 6 places plus betterPulsarSample.

4. **Legato Mode Stale Synth Reference** — When `pGlide > 0`, mono mode reuses the existing synth via `.set(\hz, ...)`. But when the envelope completes (doneAction: 2), SC frees the synth node while Lua's `synth` variable still holds the reference. Subsequent notes with glide will `.set` on a freed node (silent no-op), causing lost notes. This only manifests with non-legato playing when glide > 0.

5. **No Pulsaret Edge Smoothing** — Original Minor #16. Hard gate at pulsaret boundary. Not addressed by any fix commit.

---

## Summary Table

### Original Issues — Resolution Status

#### Critical Issues

| # | Commit | Issue | Status |
|---|--------|-------|--------|
| 1 | `8fed862` (T1) | Multi-formant always computes 3 formants (~3x CPU) | ✅ FIXED in `e11cdf6` |
| 2 | `2af7213` (T9) | Poly voice params never update mid-note | ✅ FIXED in `7774767` |
| 3 | `2af7213` (T9) | Poly × multi-formant = extreme CPU, no guard | ⚠️ WARNING added in `8c729e8`, display in `6e3ec7c` |
| 4 | `2af7213` (T9) | `free` doesn't free poly voices | ✅ FIXED in `7774767` |

#### Major Issues

| # | Commit | Issue | Status |
|---|--------|-------|--------|
| 5 | `e0d470b` | Demo uses `clock.sleep` — no clock sync, drift | ⚠️ Fixed in `8f09cd7` but uses `clock.sync` unconventionally |
| 6 | `275fc5c` (T2) | `loadSample` frees buffer while synth reads | ✅ FIXED in `51dea40` |
| 7 | `275fc5c` (T2) | `sampleBuf.bufnum` baked into SynthDef | ✅ FIXED in `51dea40` |
| 8 | `6e6f599` (T4) | Bridge requires existing synth | ✅ FIXED in `fb3975c` |
| 9 | `a05222f` (T7) | LFO fights with user input | ✅ FIXED in `fb3975c` |
| 10 | `c680151` (T8) | Wrong reverb API (`level_rev` not `level_eng_rev`) | ✅ FIXED in `33fdd2b` |
| 11 | `4438ab7` (T10) | Glide non-functional (new synth each note) | ✅ FIXED in `f6956a4` |
| 12 | `2af7213` (T9) | `noteOff "f"` float comparison risk | ✅ FIXED in `7774767` |

#### Minor Issues

| # | Commit | Issue | Status |
|---|--------|-------|--------|
| 13 | `e0d470b` | Unused SC variables | ✅ FIXED in `d0ddd7a` |
| 14 | `e0d470b` | `\betterPulsarLite` dead code | ✅ FIXED in `d0ddd7a` |
| 15 | `e0d470b` | `midi_device` implicit global | ✅ FIXED in `33fdd2b` |
| 16 | `e0d470b` | No pulsaret edge smoothing | NOT ADDRESSED |
| 17 | `8fed862` (T1) | Multi-formant code duplication | WORSENED (now 6 copies) |
| 18 | `c680151` (T8) | No `audio.rev_on()` | ✅ FIXED in `33fdd2b` |
| 19 | All | No README updates | ✅ FIXED in `8c729e8` |
| 20 | All | No UI page updates for new features | ⚠️ CPU display added in `6e3ec7c`; param controls still original only |
| 21 | All | `noteOn` arg list unsustainable (23 args) | NOT ADDRESSED |
| 22 | All | `\betterPulsarLite` stale dead code | ✅ FIXED in `d0ddd7a` |

### New Issues Introduced by Fix Commits

| Severity | Commit | Issue |
|----------|--------|-------|
| Major | `f6956a4` | Legato mode stale synth ref: non-legato playing with glide>0 silently drops notes |
| Moderate | `8f09cd7` | `clock.sync` used with absolute beat positions instead of subdivisions — fragile timing |
| Moderate | `fb3975c` | LFO disable doesn't resend param value — engine keeps last modulated value |
| Moderate | `6e3ec7c` | `norns.audio.cpu()` may not exist on all norns versions — unguarded, will crash cpu_clock |
| Minor | `fb3975c` | `musicutil.freq_to_note_num` may not exist on older norns installations |
| Minor | `fb3975c` | Bridge synth uses hardcoded velocity 100 |

### Overall Assessment

The fix commits successfully resolve 15 of 22 original issues, including all 4 critical issues (3 fully, 1 with warnings). The codebase is significantly more robust than before the fixes. The remaining concerns are primarily:

1. **Legato stale reference** (Major) — Most impactful new bug. Can cause silent note loss.
2. **API compatibility** (Moderate) — `norns.audio.cpu()` and `musicutil.freq_to_note_num` should be validated.
3. **Maintenance burden** (Minor) — 6 formant signal chain copies, 23-arg noteOn. Functional but fragile for future development.
4. **Clock sync pattern** (Moderate) — Works in practice but unconventional. Standard norns pattern would be more robust.
