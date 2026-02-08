-- better-pulsar
-- v1.0.0 @seanrooney
--
-- pulsar synthesis
-- for norns shield
--
-- MIDI note -> fundamental
-- velocity -> amplitude
--
-- Page 1 (main):
--   E1: formant  E2: duty  E3: masking
--   K2: next page  K3: next pulsaret
--
-- Page 2 (params):
--   E1: pulsaret  E2: window  E3: amp
--   K2: next page
--
-- Page 3 (advanced):
--   E1: formants  E2: poly  E3: glide
--   K2: next page  K3: toggle sample
--
-- Page 4 (demo):
--   E1: tempo  E2: scale  E3: length
--   K2: next page  K3: toggle demo
--
-- Default MIDI CC mappings:
--   CC 32: formant hz
--   CC 33: duty cycle
--   CC 34: amplitude
--   CC 35: pan
--   CC 36: masking
--   CC 37: pulsaret
--   CC 38: window
--   CC 39: attack
--   CC 40: release
--   CC 41: formant 2 hz
--   CC 42: formant 3 hz
--   CC 43: pan 2
--   CC 44: pan 3
--   CC 45: sample rate
--   CC 46: glide
--   CC 47: burst on

engine.name = "BetterPulsar"

local musicutil = require "musicutil"

-- Simple LFO implementation (self-contained, no external library needed)
local SimpleLFO = {}
SimpleLFO.__index = SimpleLFO

function SimpleLFO:new(params)
  local o = setmetatable({}, self)
  o.period = params.period or 1
  o.min = params.min or 0
  o.max = params.max or 1
  o.action = params.action or function() end
  o.running = false
  o.clock_id = nil
  o.phase = 0
  return o
end

function SimpleLFO:start()
  if self.running then return end
  self.running = true
  self.clock_id = clock.run(function()
    while self.running do
      -- Sine wave: raw is 0-1, phase is 0-1
      local raw = (math.sin(self.phase * 2 * math.pi) + 1) / 2
      local scaled = self.min + raw * (self.max - self.min)
      self.action(scaled, raw)
      clock.sleep(1/30)  -- 30 Hz update rate
      self.phase = (self.phase + (1/30) / self.period) % 1
    end
  end)
end

function SimpleLFO:stop()
  self.running = false
  if self.clock_id then
    clock.cancel(self.clock_id)
    self.clock_id = nil
  end
end

function SimpleLFO:set(key, value)
  if key == "period" then
    self.period = value
  elseif key == "min" then
    self.min = value
  elseif key == "max" then
    self.max = value
  end
end

-- State
local current_note = nil
local note_hz = 110
local velocity = 100
local midi_device = nil

-- Demo mode state
local demo_clock = nil
local demo_sequence = {}
local demo_step = 1
local demo_root = 48  -- C3
local demo_octaves = 2

-- Time-scale bridging state
local bridge_clock = nil
local bridge_active = false

-- LFO objects
local lfo_formant = nil
local lfo_duty = nil
local lfo_masking = nil
local lfo_pan = nil

-- UI state
local current_page = 1
local cpu_load = 0  -- Audio CPU percentage
local num_pages = 4
local page_names = {"main", "params", "advanced", "demo"}

-- Pulsaret waveform names
local pulsaret_names = {
  "sine",
  "sine x2",
  "sine x3",
  "sinc",
  "triangle",
  "sawtooth",
  "square",
  "formant",
  "pulse",
  "noise"
}

-- Window function names
local window_names = {
  "rectangular",
  "gaussian",
  "hann",
  "exp decay",
  "linear decay"
}

-- Scale names for demo mode
local scale_names = {}
for i = 1, #musicutil.SCALES do
  table.insert(scale_names, musicutil.SCALES[i].name)
end

-- MIDI CC mappings (adjust to your controller)
-- Default: CC 32-47 consecutively
local CC = {
  formant     = 32,
  duty_cycle  = 33,
  amplitude   = 34,
  pan         = 35,
  masking     = 36,
  pulsaret    = 37,
  window      = 38,
  attack      = 39,
  release     = 40,
  formant2    = 41,
  formant3    = 42,
  pan2        = 43,
  pan3        = 44,
  sample_rate = 45,
  glide       = 46,
  burst_on    = 47
}

function init()
  -- Initialize params
  params:add_separator("pulsar synthesis")

  params:add_control("formant_hz", "formant hz",
    controlspec.new(20, 4000, "exp", 0, 440, "hz"))
  params:set_action("formant_hz", function(v)
    -- Only send directly if LFO is not active (LFO handles modulated sending)
    if params:get("lfo_formant_enabled") ~= 2 then
      engine.formantHz(v)
    end
  end)

  params:add_control("duty_cycle", "duty cycle",
    controlspec.new(0.01, 1.0, "lin", 0.01, 0.5, ""))
  params:set_action("duty_cycle", function(v)
    -- Only send directly if LFO is not active
    if params:get("lfo_duty_enabled") ~= 2 then
      engine.dutyCycle(v)
    end
  end)

  params:add_option("use_duty_cycle", "duty mode", {"formant ratio", "manual"}, 1)
  params:set_action("use_duty_cycle", function(v)
    engine.useDutyCycle(v - 1)
  end)

  params:add_control("pulsaret", "pulsaret",
    controlspec.new(0, 9, "lin", 0, 0, ""))
  params:set_action("pulsaret", function(v)
    engine.pulsaret(v)
  end)

  params:add_control("window", "window",
    controlspec.new(0, 4, "lin", 0, 1, ""))
  params:set_action("window", function(v)
    engine.window(v)
  end)

  params:add_control("masking", "masking",
    controlspec.new(0, 1.0, "lin", 0.01, 0, ""))
  params:set_action("masking", function(v)
    -- Only send directly if LFO is not active
    if params:get("lfo_masking_enabled") ~= 2 then
      engine.masking(v)
    end
  end)

  -- Burst masking
  params:add_option("use_burst", "masking mode", {"stochastic", "burst"}, 1)
  params:set_action("use_burst", function(v)
    engine.useBurst(v - 1)
  end)

  params:add_number("burst_on", "burst on", 1, 16, 4)
  params:set_action("burst_on", function(v)
    engine.burstOn(v)
  end)

  params:add_number("burst_off", "burst off", 1, 16, 2)
  params:set_action("burst_off", function(v)
    engine.burstOff(v)
  end)

  params:add_control("pan", "pan",
    controlspec.new(-1.0, 1.0, "lin", 0.01, 0, ""))
  params:set_action("pan", function(v)
    -- Only send directly if LFO is not active
    if params:get("lfo_pan_enabled") ~= 2 then
      engine.pan(v)
    end
  end)

  params:add_control("attack", "attack",
    controlspec.new(0.001, 2.0, "exp", 0, 0.01, "s"))
  params:set_action("attack", function(v)
    print(string.format("attack: %.3f", v))
    engine.attack(v)
  end)

  params:add_control("release", "release",
    controlspec.new(0.01, 4.0, "exp", 0, 0.3, "s"))
  params:set_action("release", function(v)
    print(string.format("release: %.3f", v))
    engine.release(v)
  end)

  params:add_control("amp", "amplitude",
    controlspec.new(0, 1.0, "lin", 0.01, 0.7, ""))
  params:set_action("amp", function(v)
    engine.amp(v)
  end)

  -- Multi-formant parameters
  params:add_separator("multi-formant")

  params:add_number("formant_count", "formant count", 1, 3, 1)
  params:set_action("formant_count", function(v)
    engine.formantCount(v)
    -- Warn about CPU usage with poly + multi-formant
    if v > 1 and params:get("poly_mode") == 2 then
      print("WARNING: Multi-formant with poly mode may exceed norns CPU capacity")
    end
  end)

  params:add_control("formant2_hz", "formant 2 hz",
    controlspec.new(20, 4000, "exp", 0, 660, "hz"))
  params:set_action("formant2_hz", function(v)
    engine.formant2Hz(v)
  end)

  params:add_control("formant3_hz", "formant 3 hz",
    controlspec.new(20, 4000, "exp", 0, 880, "hz"))
  params:set_action("formant3_hz", function(v)
    engine.formant3Hz(v)
  end)

  params:add_control("pan2", "pan 2",
    controlspec.new(-1.0, 1.0, "lin", 0.01, -0.5, ""))
  params:set_action("pan2", function(v)
    engine.pan2(v)
  end)

  params:add_control("pan3", "pan 3",
    controlspec.new(-1.0, 1.0, "lin", 0.01, 0.5, ""))
  params:set_action("pan3", function(v)
    engine.pan3(v)
  end)

  -- Sample convolution parameters
  params:add_separator("sample pulsaret")

  params:add_option("use_sample", "use sample", {"off", "on"}, 1)
  params:set_action("use_sample", function(v)
    engine.useSample(v - 1)
  end)

  params:add_file("sample_file", "sample file")
  params:set_action("sample_file", function(v)
    if v ~= "" and v ~= nil then
      engine.loadSample(v)
    end
  end)

  params:add_control("sample_rate", "sample rate",
    controlspec.new(0.25, 4.0, "exp", 0, 1.0, "x"))
  params:set_action("sample_rate", function(v)
    engine.sampleRate(v)
  end)

  params:add_separator("midi")

  params:add_number("midi_device", "midi device", 1, 16, 4)
  params:set_action("midi_device", function(v)
    midi_device = midi.connect(v)
    midi_device.event = midi_event
  end)
  params:add_number("midi_channel", "midi channel", 1, 16, 1)

  -- CC mapping params
  params:add_separator("cc mapping")
  params:add_number("cc_formant", "formant cc", 0, 127, CC.formant)
  params:add_number("cc_duty", "duty cycle cc", 0, 127, CC.duty_cycle)
  params:add_number("cc_amp", "amplitude cc", 0, 127, CC.amplitude)
  params:add_number("cc_pan", "pan cc", 0, 127, CC.pan)
  params:add_number("cc_masking", "masking cc", 0, 127, CC.masking)
  params:add_number("cc_pulsaret", "pulsaret cc", 0, 127, CC.pulsaret)
  params:add_number("cc_window", "window cc", 0, 127, CC.window)
  params:add_number("cc_attack", "attack cc", 0, 127, CC.attack)
  params:add_number("cc_release", "release cc", 0, 127, CC.release)
  params:add_number("cc_formant2", "formant 2 cc", 0, 127, CC.formant2)
  params:add_number("cc_formant3", "formant 3 cc", 0, 127, CC.formant3)
  params:add_number("cc_pan2", "pan 2 cc", 0, 127, CC.pan2)
  params:add_number("cc_pan3", "pan 3 cc", 0, 127, CC.pan3)
  params:add_number("cc_sample_rate", "sample rate cc", 0, 127, CC.sample_rate)
  params:add_number("cc_glide", "glide cc", 0, 127, CC.glide)
  params:add_number("cc_burst_on", "burst on cc", 0, 127, CC.burst_on)

  -- Demo mode params
  params:add_separator("demo mode")
  params:add_option("demo_mode", "demo mode", {"off", "on"}, 1)
  params:set_action("demo_mode", function(v)
    if v == 2 then
      start_demo()
    else
      stop_demo()
    end
  end)

  params:add_option("demo_scale", "scale", scale_names, 1)
  params:set_action("demo_scale", function(v)
    if params:get("demo_mode") == 2 then
      generate_sequence()
    end
  end)

  params:add_number("demo_tempo", "tempo", 40, 240, 120)
  params:set_action("demo_tempo", function(v)
    -- Update norns system clock tempo
    params:set("clock_tempo", v)
  end)

  params:add_number("demo_root", "root note", 36, 72, 48)
  params:set_action("demo_root", function(v)
    demo_root = v
    if params:get("demo_mode") == 2 then
      generate_sequence()
    end
  end)

  params:add_number("demo_length", "sequence length", 1, 64, 8)
  params:set_action("demo_length", function(v)
    if params:get("demo_mode") == 2 then
      generate_sequence()
    end
  end)

  params:add_trigger("demo_regenerate", "regenerate sequence")
  params:set_action("demo_regenerate", function()
    generate_sequence()
    demo_step = 1
  end)

  -- Time-scale bridging parameters
  params:add_separator("time-scale bridge")

  params:add_control("bridge_start_hz", "start hz",
    controlspec.new(0.5, 20, "exp", 0, 2, "hz"))

  params:add_control("bridge_end_hz", "end hz",
    controlspec.new(20, 500, "exp", 0, 110, "hz"))

  params:add_control("bridge_duration", "duration",
    controlspec.new(0.5, 30, "exp", 0, 4, "s"))

  params:add_option("bridge_curve", "curve", {"linear", "exponential", "logarithmic"}, 2)

  params:add_option("bridge_direction", "direction", {"up (rhythm>pitch)", "down (pitch>rhythm)"}, 1)

  params:add_trigger("bridge_trigger", "trigger bridge")
  params:set_action("bridge_trigger", function()
    trigger_bridge()
  end)

  -- LFO parameters
  params:add_separator("lfo modulation")

  -- Formant LFO
  params:add_option("lfo_formant_enabled", "formant lfo", {"off", "on"}, 1)
  params:set_action("lfo_formant_enabled", function(v)
    if v == 2 then
      lfo_formant:start()
    else
      lfo_formant:stop()
      engine.formantHz(params:get("formant_hz"))
    end
  end)
  params:add_control("lfo_formant_rate", "formant lfo rate",
    controlspec.new(0.01, 10, "exp", 0, 0.5, "hz"))
  params:set_action("lfo_formant_rate", function(v)
    lfo_formant:set("period", 1/v)
  end)
  params:add_control("lfo_formant_depth", "formant lfo depth",
    controlspec.new(0, 1, "lin", 0.01, 0.3, ""))

  -- Duty cycle LFO
  params:add_option("lfo_duty_enabled", "duty lfo", {"off", "on"}, 1)
  params:set_action("lfo_duty_enabled", function(v)
    if v == 2 then
      lfo_duty:start()
    else
      lfo_duty:stop()
      engine.dutyCycle(params:get("duty_cycle"))
    end
  end)
  params:add_control("lfo_duty_rate", "duty lfo rate",
    controlspec.new(0.01, 10, "exp", 0, 0.25, "hz"))
  params:set_action("lfo_duty_rate", function(v)
    lfo_duty:set("period", 1/v)
  end)
  params:add_control("lfo_duty_depth", "duty lfo depth",
    controlspec.new(0, 1, "lin", 0.01, 0.3, ""))

  -- Masking LFO
  params:add_option("lfo_masking_enabled", "masking lfo", {"off", "on"}, 1)
  params:set_action("lfo_masking_enabled", function(v)
    if v == 2 then
      lfo_masking:start()
    else
      lfo_masking:stop()
      engine.masking(params:get("masking"))
    end
  end)
  params:add_control("lfo_masking_rate", "masking lfo rate",
    controlspec.new(0.01, 10, "exp", 0, 0.1, "hz"))
  params:set_action("lfo_masking_rate", function(v)
    lfo_masking:set("period", 1/v)
  end)
  params:add_control("lfo_masking_depth", "masking lfo depth",
    controlspec.new(0, 1, "lin", 0.01, 0.5, ""))

  -- Pan LFO
  params:add_option("lfo_pan_enabled", "pan lfo", {"off", "on"}, 1)
  params:set_action("lfo_pan_enabled", function(v)
    if v == 2 then
      lfo_pan:start()
    else
      lfo_pan:stop()
      engine.pan(params:get("pan"))
    end
  end)
  params:add_control("lfo_pan_rate", "pan lfo rate",
    controlspec.new(0.01, 10, "exp", 0, 0.2, "hz"))
  params:set_action("lfo_pan_rate", function(v)
    lfo_pan:set("period", 1/v)
  end)
  params:add_control("lfo_pan_depth", "pan lfo depth",
    controlspec.new(0, 1, "lin", 0.01, 0.5, ""))

  -- Initialize LFOs
  init_lfos()

  -- Polyphony parameters
  params:add_separator("polyphony")

  params:add_option("poly_mode", "mode", {"mono", "poly (4 voices)"}, 1)
  params:set_action("poly_mode", function(v)
    engine.polyMode(v - 1)
    -- Warn about CPU usage with poly + multi-formant
    if v == 2 and params:get("formant_count") > 1 then
      print("WARNING: Poly mode with multi-formant may exceed norns CPU capacity")
    end
  end)

  params:add_control("glide", "glide time",
    controlspec.new(0, 2.0, "lin", 0.01, 0, "s"))
  params:set_action("glide", function(v)
    engine.glide(v)
  end)

  -- Reverb parameters (uses norns built-in reverb)
  -- Note: Some reverb functions may not exist on all norns versions
  params:add_separator("reverb")

  params:add_control("reverb_mix", "reverb mix",
    controlspec.new(0, 1, "lin", 0.01, 0.1, ""))
  params:set_action("reverb_mix", function(v)
    -- level_eng_rev controls engine-to-reverb send level
    if audio.level_eng_rev then audio.level_eng_rev(v) end
  end)

  params:add_control("reverb_damp", "reverb damp",
    controlspec.new(0, 1, "lin", 0.01, 0.5, ""))
  params:set_action("reverb_damp", function(v)
    if audio.rev_damp then audio.rev_damp(v) end
  end)

  params:add_control("reverb_size", "reverb size",
    controlspec.new(0.5, 5, "lin", 0.01, 2.0, "s"))
  params:set_action("reverb_size", function(v)
    if audio.rev_time then audio.rev_time(v) end
  end)

  -- Connect MIDI
  midi_device = midi.connect(params:get("midi_device" ))
  midi_device.event = midi_event

  -- Ensure reverb is active (if function exists)
  if audio.rev_on then audio.rev_on() end

  -- Bang all params to sync engine
  params:bang()

  -- Start redraw clock
  redraw_clock = clock.run(function()
    while true do
      clock.sleep(1/15)
      redraw()
    end
  end)

  -- Start CPU monitoring using norns poll system
  local cpu_poll = poll.set("cpu_avg")
  if cpu_poll then
    cpu_poll.callback = function(val)
      cpu_load = util.round(val, 1)
    end
    cpu_poll.time = 0.5
    cpu_poll:start()
  end
end

function midi_event(data)
  local msg = midi.to_msg(data)
  local ch = params:get("midi_channel")

  if msg.ch ~= ch then return end

  if msg.type == "note_on" then
    print(string.format("MIDI note_on: %d vel:%d", msg.note, msg.vel))
    current_note = msg.note
    note_hz = musicutil.note_num_to_freq(msg.note)
    velocity = msg.vel
    engine.noteOn(msg.note, msg.vel)

  elseif msg.type == "note_off" then
    print(string.format("MIDI note_off: %d", msg.note))
    -- In poly mode, always send noteOff with note number
    -- In mono mode, only release if it's the held note
    if params:get("poly_mode") == 2 then
      engine.noteOff(msg.note)
    elseif msg.note == current_note then
      engine.noteOff(msg.note)
      current_note = nil
    end

  elseif msg.type == "cc" then
    print(string.format("MIDI cc: %d val:%d", msg.cc, msg.val))
    handle_cc(msg.cc, msg.val)
  end
end

function handle_cc(cc, val)
  local v = val / 127

  if cc == params:get("cc_formant") then
    -- Map 0-127 to 20-4000 Hz exponentially
    local hz = 20 * math.pow(200, v)
    params:set("formant_hz", hz)

  elseif cc == params:get("cc_duty") then
    params:set("duty_cycle", 0.01 + v * 0.99)

  elseif cc == params:get("cc_amp") then
    params:set("amp", v)

  elseif cc == params:get("cc_pan") then
    params:set("pan", v * 2 - 1)

  elseif cc == params:get("cc_masking") then
    params:set("masking", v)

  elseif cc == params:get("cc_pulsaret") then
    params:set("pulsaret", v * 9)

  elseif cc == params:get("cc_window") then
    params:set("window", v * 4)

  elseif cc == params:get("cc_attack") then
    local att = 0.001 * math.pow(2000, v)
    params:set("attack", att)

  elseif cc == params:get("cc_release") then
    local rel = 0.01 * math.pow(400, v)
    params:set("release", rel)

  elseif cc == params:get("cc_formant2") then
    -- Map 0-127 to 20-4000 Hz exponentially
    local hz = 20 * math.pow(200, v)
    params:set("formant2_hz", hz)

  elseif cc == params:get("cc_formant3") then
    -- Map 0-127 to 20-4000 Hz exponentially
    local hz = 20 * math.pow(200, v)
    params:set("formant3_hz", hz)

  elseif cc == params:get("cc_pan2") then
    params:set("pan2", v * 2 - 1)

  elseif cc == params:get("cc_pan3") then
    params:set("pan3", v * 2 - 1)

  elseif cc == params:get("cc_sample_rate") then
    -- Map 0-127 to 0.25-4.0 exponentially
    local rate = 0.25 * math.pow(16, v)
    params:set("sample_rate", rate)

  elseif cc == params:get("cc_glide") then
    -- Map 0-127 to 0-2 seconds
    params:set("glide", v * 2)

  elseif cc == params:get("cc_burst_on") then
    -- Map 0-127 to 1-16
    params:set("burst_on", math.floor(v * 15) + 1)
  end
end

function enc(n, d)
  if current_page == 1 then
    -- Main page
    if n == 1 then
      params:delta("formant_hz", d * 2)
    elseif n == 2 then
      params:delta("duty_cycle", d * 0.01)
    elseif n == 3 then
      params:delta("masking", d * 0.02)
    end
  elseif current_page == 2 then
    -- Params page
    if n == 1 then
      params:delta("pulsaret", d * 0.1)
    elseif n == 2 then
      params:delta("window", d * 0.1)
    elseif n == 3 then
      params:delta("amp", d * 0.02)
    end
  elseif current_page == 3 then
    -- Advanced page
    if n == 1 then
      params:delta("formant_count", d)
    elseif n == 2 then
      params:delta("poly_mode", d)
    elseif n == 3 then
      params:delta("glide", d * 0.02)
    end
  elseif current_page == 4 then
    -- Demo page
    if n == 1 then
      params:delta("demo_tempo", d)
    elseif n == 2 then
      params:delta("demo_scale", d)
    elseif n == 3 then
      params:delta("demo_length", d)
    end
  end
end

function key(n, z)
  if z == 1 then
    if n == 2 then
      -- Cycle through pages
      current_page = current_page + 1
      if current_page > num_pages then
        current_page = 1
      end
    elseif n == 3 then
      -- Page-specific K3 actions
      if current_page == 1 then
        -- Main page: next pulsaret
        local p = params:get("pulsaret")
        params:set("pulsaret", util.clamp(p + 1, 0, 9))
      elseif current_page == 3 then
        -- Advanced page: toggle sample mode
        local sm = params:get("use_sample")
        params:set("use_sample", sm == 1 and 2 or 1)
      elseif current_page == 4 then
        -- Demo page: toggle demo on/off
        local dm = params:get("demo_mode")
        params:set("demo_mode", dm == 1 and 2 or 1)
      end
    end
  end
end

function redraw()
  screen.clear()

  if current_page == 1 then
    redraw_main()
  elseif current_page == 2 then
    redraw_params()
  elseif current_page == 3 then
    redraw_advanced()
  elseif current_page == 4 then
    redraw_demo()
  end

  screen.update()
end

function redraw_main()
  -- Title
  screen.level(15)
  screen.move(64, 8)
  screen.text_center("better-pulsar")

  -- Current note
  screen.level(current_note and 15 or 3)
  screen.move(4, 20)
  if current_note then
    screen.text(musicutil.note_num_to_name(current_note, true))
    screen.move(32, 20)
    screen.text(string.format("%.1f hz", note_hz))
  else
    screen.text("--")
  end

  -- Pulsaret and window
  screen.level(10)
  screen.move(4, 32)
  local p = params:get("pulsaret")
  local p_idx = math.floor(p) + 1
  local p_frac = p % 1
  local wave_str
  if p_frac < 0.01 then
    wave_str = pulsaret_names[p_idx]
  else
    local p_idx2 = math.min(p_idx + 1, 10)
    wave_str = string.format("%s>%s", pulsaret_names[p_idx]:sub(1,4), pulsaret_names[p_idx2]:sub(1,4))
  end
  screen.text("wave: " .. wave_str)
  screen.move(4, 42)
  local w = params:get("window")
  local w_idx = math.floor(w) + 1
  local w_frac = w % 1
  local win_str
  if w_frac < 0.01 then
    win_str = window_names[w_idx]
  else
    local w_idx2 = math.min(w_idx + 1, 5)
    win_str = string.format("%s>%s", window_names[w_idx]:sub(1,4), window_names[w_idx2]:sub(1,4))
  end
  screen.text("win: " .. win_str)

  -- Parameters
  screen.level(7)
  screen.move(80, 32)
  screen.text(string.format("form: %.0f", params:get("formant_hz")))
  screen.move(80, 42)
  screen.text(string.format("duty: %.2f", params:get("duty_cycle")))
  screen.move(80, 52)
  screen.text(string.format("mask: %.2f", params:get("masking")))

  -- Visual representation of pulsar
  draw_pulsar()

  -- Page indicator and CPU
  draw_page_indicator()
  draw_cpu()
end

function redraw_params()
  -- Title
  screen.level(15)
  screen.move(64, 8)
  screen.text_center("parameters")

  -- Left column
  screen.level(10)
  screen.move(4, 20)
  screen.text(string.format("pulsaret: %.1f", params:get("pulsaret")))
  screen.move(4, 30)
  screen.text(string.format("window: %.1f", params:get("window")))
  screen.move(4, 40)
  screen.text(string.format("formant: %.0f hz", params:get("formant_hz")))
  screen.move(4, 50)
  screen.text(string.format("duty: %.2f", params:get("duty_cycle")))

  -- Right column
  screen.move(70, 20)
  screen.text(string.format("amp: %.2f", params:get("amp")))
  screen.move(70, 30)
  screen.text(string.format("pan: %.2f", params:get("pan")))
  screen.move(70, 40)
  screen.text(string.format("mask: %.2f", params:get("masking")))
  screen.move(70, 50)
  screen.text(string.format("atk: %.3f", params:get("attack")))

  -- Hints
  screen.level(5)
  screen.move(4, 62)
  screen.text("E1:wave E2:win E3:amp K2:page")

  -- Page indicator and CPU
  draw_page_indicator()
  draw_cpu()
end

function redraw_advanced()
  -- Title
  screen.level(15)
  screen.move(64, 8)
  screen.text_center("advanced")

  -- Left column - multi-formant & poly
  screen.level(10)
  screen.move(4, 20)
  screen.text(string.format("formants: %d", params:get("formant_count")))
  screen.move(4, 30)
  local poly_mode = params:get("poly_mode") == 1 and "mono" or "poly"
  screen.text(string.format("mode: %s", poly_mode))
  screen.move(4, 40)
  screen.text(string.format("glide: %.2fs", params:get("glide")))
  screen.move(4, 50)
  local sample_on = params:get("use_sample") == 2
  screen.text(string.format("sample: %s", sample_on and "on" or "off"))

  -- Right column - formant frequencies
  screen.move(70, 20)
  screen.text(string.format("f1: %.0f", params:get("formant_hz")))
  screen.move(70, 30)
  screen.text(string.format("f2: %.0f", params:get("formant2_hz")))
  screen.move(70, 40)
  screen.text(string.format("f3: %.0f", params:get("formant3_hz")))
  screen.move(70, 50)
  screen.text(string.format("rate: %.2fx", params:get("sample_rate")))

  -- Hints
  screen.level(5)
  screen.move(4, 62)
  screen.text("E1:form# E2:poly E3:glide K3:samp")

  -- Page indicator and CPU
  draw_page_indicator()
  draw_cpu()
end

function redraw_demo()
  -- Title
  screen.level(15)
  screen.move(64, 8)
  screen.text_center("demo mode")

  -- Demo status
  local demo_on = params:get("demo_mode") == 2
  screen.level(demo_on and 15 or 5)
  screen.move(64, 18)
  screen.text_center(demo_on and "[ PLAYING ]" or "[ STOPPED ]")

  -- Settings
  screen.level(10)
  screen.move(4, 30)
  screen.text(string.format("tempo: %d bpm", params:get("demo_tempo")))
  screen.move(4, 40)
  screen.text(string.format("scale: %s", scale_names[params:get("demo_scale")]))
  screen.move(4, 50)
  screen.text(string.format("length: %d steps", params:get("demo_length")))

  -- Right side
  screen.move(80, 30)
  screen.text(string.format("root: %s", musicutil.note_num_to_name(params:get("demo_root"), true)))
  screen.move(80, 40)
  screen.text(string.format("step: %d/%d", demo_step, #demo_sequence > 0 and #demo_sequence or params:get("demo_length")))

  -- Key hints
  screen.level(5)
  screen.move(4, 62)
  screen.text("K2:page K3:on/off E1:tempo")

  -- Page indicator and CPU
  draw_page_indicator()
  draw_cpu()
end

function draw_page_indicator()
  for i = 1, num_pages do
    local x = 128 - (num_pages - i + 1) * 8
    if i == current_page then
      screen.level(15)
      screen.rect(x, 1, 6, 4)
      screen.fill()
    else
      screen.level(5)
      screen.rect(x, 1, 6, 4)
      screen.stroke()
    end
  end
end

function draw_cpu()
  -- CPU load indicator in top-left corner
  local cpu_color = 5  -- dim by default
  if cpu_load > 70 then
    cpu_color = 15  -- bright when high
  elseif cpu_load > 50 then
    cpu_color = 10  -- medium when moderate
  end
  screen.level(cpu_color)
  screen.move(4, 6)
  screen.text(string.format("cpu:%.0f%%", cpu_load))
end

function draw_pulsar()
  local x_start = 4
  local y_center = 58
  local width = 120
  local height = 6

  local duty = params:get("duty_cycle")
  local pulsaret_width = math.floor(width * duty)

  screen.level(3)
  -- Baseline
  screen.move(x_start, y_center)
  screen.line(x_start + width, y_center)
  screen.stroke()

  -- Pulsaret envelope approximation with morphing
  screen.level(15)
  local win = params:get("window")
  local win_idx = math.floor(win)
  local win_frac = win % 1

  for i = 0, pulsaret_width do
    local phase = i / pulsaret_width

    -- Calculate envelope for current window
    local env1 = calc_window_env(win_idx, phase)
    local env2 = calc_window_env(win_idx + 1, phase)
    local env = env1 * (1 - win_frac) + env2 * win_frac

    local sine = math.sin(phase * 2 * math.pi)
    local y = y_center - (sine * env * height)
    if i == 0 then
      screen.move(x_start + i, y)
    else
      screen.line(x_start + i, y)
    end
  end
  screen.stroke()
end

function calc_window_env(win_idx, phase)
  if win_idx == 0 then -- rectangular
    return 1
  elseif win_idx == 1 then -- gaussian
    local x = (phase - 0.5) * 4
    return math.exp(-0.5 * x * x)
  elseif win_idx == 2 then -- hann
    return 0.5 * (1 - math.cos(2 * math.pi * phase))
  elseif win_idx == 3 then -- exp decay
    return math.exp(-4 * phase)
  elseif win_idx >= 4 then -- linear decay
    return 1 - phase
  end
  return 1
end

-- Demo mode functions
function generate_sequence()
  demo_sequence = {}
  local scale_num = params:get("demo_scale")
  local scale_notes = musicutil.generate_scale(demo_root, scale_names[scale_num], demo_octaves)
  local seq_length = params:get("demo_length")

  -- Gate lengths: short, medium, long
  local gate_options = {0.25, 0.5, 0.75, 1.0}

  for i = 1, seq_length do
    -- 20% chance of rest
    if math.random() < 0.2 then
      table.insert(demo_sequence, {note = nil, rest = true, gate = 0})
    else
      local note = scale_notes[math.random(#scale_notes)]
      local vel = math.random(60, 120)
      local gate = gate_options[math.random(#gate_options)]
      table.insert(demo_sequence, {note = note, vel = vel, rest = false, gate = gate})
    end
  end
  demo_step = 1
end

function start_demo()
  stop_demo()
  generate_sequence()
  -- Set clock tempo from demo tempo
  params:set("clock_tempo", params:get("demo_tempo"))
  demo_clock = clock.run(demo_loop)
end

function stop_demo()
  if demo_clock then
    clock.cancel(demo_clock)
    demo_clock = nil
  end
  -- Release all notes (use 0 as dummy note in mono mode)
  engine.noteOff(current_note or 0)
  current_note = nil
end

function demo_loop()
  -- Use clock.sync with durations (subdivisions) for proper timing
  local glide_enabled = params:get("glide") > 0

  while true do
    local step = demo_sequence[demo_step]
    -- 1 step = 1 beat (quarter note)
    local step_beats = 1

    if not step.rest then
      current_note = step.note
      note_hz = musicutil.note_num_to_freq(step.note)
      velocity = step.vel
      engine.noteOn(step.note, step.vel)

      -- Hold note for gate duration (fraction of beat)
      local gate_beats = step_beats * step.gate
      -- Ensure minimum gate to avoid zero-duration waits
      gate_beats = math.max(gate_beats, 0.05)
      clock.sync(gate_beats)

      -- Note off (skip if glide enabled for legato effect)
      if not glide_enabled then
        engine.noteOff(step.note)
        current_note = nil
      end

      -- Wait remaining step time (if gate < 1.0)
      local remaining = step_beats - gate_beats
      if remaining > 0.01 then
        clock.sync(remaining)
      end
    else
      -- Rest - always release note
      if current_note then
        engine.noteOff(current_note)
        current_note = nil
      end
      clock.sync(step_beats)
    end

    demo_step = demo_step + 1
    if demo_step > #demo_sequence then
      demo_step = 1
      -- Re-check glide setting each loop
      glide_enabled = params:get("glide") > 0
    end
  end
end

-- LFO initialization
function init_lfos()
  -- Formant LFO - modulates formant frequency
  lfo_formant = SimpleLFO:new{
    min = 0,
    max = 1,
    period = 2,
    action = function(scaled, raw)
      local depth = params:get("lfo_formant_depth")
      local base = params:get("formant_hz")
      -- Modulate around base value: 0.5x to 2x
      local mult = 1 + ((raw - 0.5) * 2 * depth)
      engine.formantHz(base * mult)
    end
  }

  -- Duty cycle LFO
  lfo_duty = SimpleLFO:new{
    min = 0,
    max = 1,
    period = 4,
    action = function(scaled, raw)
      local depth = params:get("lfo_duty_depth")
      local base = params:get("duty_cycle")
      local mod = base + ((raw - 0.5) * depth)
      engine.dutyCycle(util.clamp(mod, 0.01, 1.0))
    end
  }

  -- Masking LFO
  lfo_masking = SimpleLFO:new{
    min = 0,
    max = 1,
    period = 10,
    action = function(scaled, raw)
      local depth = params:get("lfo_masking_depth")
      engine.masking(raw * depth)
    end
  }

  -- Pan LFO
  lfo_pan = SimpleLFO:new{
    min = -1,
    max = 1,
    period = 5,
    action = function(scaled, raw)
      local depth = params:get("lfo_pan_depth")
      engine.pan(scaled * depth)
    end
  }
end

-- Time-scale bridging functions
function trigger_bridge()
  -- Cancel any existing bridge
  if bridge_clock then
    clock.cancel(bridge_clock)
  end

  bridge_active = true
  bridge_clock = clock.run(function()
    local start_hz = params:get("bridge_start_hz")
    local end_hz = params:get("bridge_end_hz")
    local duration = params:get("bridge_duration")
    local curve = params:get("bridge_curve")
    local direction = params:get("bridge_direction")

    -- Swap for downward direction
    if direction == 2 then
      start_hz, end_hz = end_hz, start_hz
    end

    -- Start a synth if none is playing (use MIDI note 60 = C4 as base)
    -- The hz will be overwritten immediately, but we need a synth to exist
    if current_note == nil then
      local start_note = musicutil.freq_to_note_num(start_hz)
      engine.noteOn(start_note, 100)
      current_note = start_note
      note_hz = start_hz
    end

    local steps = math.floor(duration * 30)  -- 30 updates per second
    local step_time = duration / steps

    for i = 0, steps do
      local t = i / steps  -- 0 to 1

      local hz
      if curve == 1 then  -- linear
        hz = start_hz + (end_hz - start_hz) * t
      elseif curve == 2 then  -- exponential
        hz = start_hz * math.pow(end_hz / start_hz, t)
      else  -- logarithmic
        local log_start = math.log(start_hz)
        local log_end = math.log(end_hz)
        hz = math.exp(log_start + (log_end - log_start) * (1 - math.pow(1 - t, 2)))
      end

      note_hz = hz
      engine.hz(hz)
      clock.sleep(step_time)
    end

    bridge_active = false
    bridge_clock = nil
  end)
end

function stop_bridge()
  if bridge_clock then
    clock.cancel(bridge_clock)
    bridge_clock = nil
  end
  bridge_active = false
end

function cleanup()
  stop_demo()
  stop_bridge()
  -- Stop LFOs
  if lfo_formant then lfo_formant:stop() end
  if lfo_duty then lfo_duty:stop() end
  if lfo_masking then lfo_masking:stop() end
  if lfo_pan then lfo_pan:stop() end
  if redraw_clock then
    clock.cancel(redraw_clock)
  end
end
