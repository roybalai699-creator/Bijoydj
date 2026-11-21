# BIJOY DJ — FEEL THE BEAT

Professional Android 4-Channel Dual-Deck DJ Mixing Studio built with **Kotlin**, **Jetpack Compose (Material 3)**, **MVVM + Repository**, **Room SQLite (15 Tables)**, and a **low-latency stereo `AudioTrack` PCM DSP engine**.

## Key Features
- **Exact Hardware-Style Dual-Deck + 4-Channel Central Mixer**
  - **Left & Right Decks (Deck A & Deck B)**: Large brushed silver metallic jog wheels with blue RGB LED halo, central `DJ` emblem, `VINYL` / `CDJ` / `SEARCH` modes, Slip Mode, Loop Bar (`IN`, `OUT`, `1/2`, `1`, `2`, `4`, `8`, `16`), `RELOOP / EXIT`, Pitch Bend `-/+`, Key Lock, vertical Pitch/Tempo Slider (`±6%` to `±100%`), and 2×4 colored Performance Pads (`HOT CUE`, `SAMPLER`, `ROLL`, `SLICER`, `LOOP`, `CUE LOOP`).
  - **Central 4-Channel Mixer (`CH 1–4`)**: Gain, Treble, Mid, Bass, Filter, Cue, per-channel VU meters, Channel Volume Faders, Crossfader Assign (`A / THRU / B`), Headphone Vol/Mix, Master Meter, FX Depth, and Hardware Crossfader.
- **15-Band / 10-Band / 5-Band / 3-Band Equalizer & 24 DJ Effects**
  - Switchable 3 / 5 / 10 / 15-band graphic/parametric EQ with 11 built-in presets (`Normal`, `Bass Boost`, `Deep Bass`, `Vocal Boost`, `Club`, `EDM`, `Rock`, `Pop`, `Classical`, `Acoustic`, `Custom`) plus custom user preset saving.
  - 24 real-time DSP effects (`Echo`, `Reverb`, `Delay`, `Flanger`, `Phaser`, `Chorus`, `Distortion`, `Filter`, `Low-Pass`, `High-Pass`, `Beat Repeat`, `Roll`, `Backspin`, `Brake`, `Vinyl Stop`, `Noise`, `Gate`, `Compressor`, `Limiter`, `Tremolo`, `Stutter`, `Bit Crusher`, `Trans`, `Off`) routable to `Deck A`, `Deck B`, `Channel 3`, `Channel 4`, or `Master`, plus Booth Volume and Master Limiter.
- **16-Pad Banked Sampler, Categories & Pitch Control**
  - 16 studio SFX pads across DJ categories (`Drums`, `Kicks`, `Snares`, `Claps`, `Bass`, `Risers`, `Impacts`, `Vocal FX`, `Crowd`, `Siren`, `Scratch`, `DJ Drops`), per-pad pitch (`0.5x–2.0x`), custom audio import via SAF, pad looping, Sampler Banks A–D, and BPM-locked Sampler Roll.
- **AI Remix Studio, 4-Stem Separator, Auto DJ & Assistant (Firebase AI Logic — Gemini 3.8 Flash + App Check)**
  - **AI Song Remix Studio**: 10-step guided workflow across 15 remix styles with track analysis, Gemini remix planning, separate non-destructive WAV rendering, and A/B original-vs-remix preview.
  - **Sad Song → DJ Converter**: 11 emotional-to-DJ presets and 10 studio parameter sliders with drop riser and beat overlay.
  - **On-Device DSP 4-Stem Separator**: Spectral & mid-side extraction of `Vocals`, `Drums`, `Bass`, and `Other` with per-stem Mute/Solo, Vocals Only / Instrumental Only quick modes, and single-stem WAV export.
  - **AI Auto DJ & Beat Pattern Generator**: 3 Auto DJ modes, 8 transition styles, interactive 16-step Kick/Snare/Hat sequencer, and conversational/voice **BIJOY AI DJ Assistant** requiring explicit user approval before modifying mixer controls.
- **Recording Studio, Music Library & Cloud Sources**
  - Recording source selector (`Master Output`, `Deck A`, `Deck B`, `Live Mix + Mic`), WAV recording + M4A/AAC export, track renaming, Folders/Playlists CRUD, JSON Backup/Restore, Layout Studio, and OAuth 2.0 PKCE + AES-256-GCM encrypted API Key Manager.

## Build & Run
1. Open in Android Studio or run via Gradle:
   ```bash
   gradle :app:assembleDebug :app:testDebugUnitTest
   ```
2. Build artifacts are output to:
   - `app/build/outputs/apk/debug/app-debug.apk`
   - `.build-outputs/app-debug.apk`

## API Credentials & Security
- Do **not** hardcode API keys or client secrets in source files.
- Configure runtime API keys via the in-app **Music Sources & API Setup** screen (stored encrypted in Android Keystore) or AI Studio Secrets / `.env` (`BuildConfig`).
