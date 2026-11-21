package com.example.audio

enum class DeckId { A, B }

enum class CrossfaderCurve(val displayName: String) {
    LINEAR("Linear"),
    CONSTANT_POWER("Constant Power"),
    SHARP_CUT("Scratch Cut")
}

enum class EqPreset(
    val displayName: String,
    val bassDb: Float,
    val midDb: Float,
    val trebleDb: Float
) {
    FLAT("Flat", 0f, 0f, 0f),
    BASS_BOOST("Bass Boost", 8f, -1f, 1f),
    TREBLE_BOOST("Treble Boost", 0f, 1f, 8f),
    VOCAL("Vocal", -2f, 6f, 3f),
    CLUB("Club", 6f, 1f, 5f),
    DEEP("Deep", 9f, -3f, -2f),
    CUSTOM("Custom", 0f, 0f, 0f)
}

enum class DjEffectType(val displayName: String) {
    NONE("Off"),
    ECHO("Echo"),
    REVERB("Reverb"),
    FLANGER("Flanger"),
    PHASER("Phaser"),
    DELAY("Delay"),
    FILTER("Filter Sweep"),
    ROLL("Roll"),
    BRAKE("Vinyl Brake"),
    REVERSE("Reverse"),
    BEAT_REPEAT("Beat Repeat"),
    DISTORTION("Distortion")
}

enum class EffectTarget(val displayName: String) {
    DECK_A("Deck A"),
    DECK_B("Deck B"),
    MASTER("Master")
}

enum class SoundColorFxType(val displayName: String, val shortLabel: String) {
    SPACE("Space", "SPACE"),
    DUB_ECHO("Dub Echo", "ECHO"),
    SWEEP("Sweep", "SWEEP"),
    NOISE("Noise", "NOISE"),
    CRUSH("Crush", "CRUSH"),
    FILTER("Filter", "FILTER")
}

enum class LayoutMode(val displayName: String) {
    STANDARD("Standard Mode"),
    COMPACT("Compact Mode"),
    FULLSCREEN("Fullscreen Mixer")
}

enum class AppSection(val title: String) {
    DJ_MIXER("DJ Mixer"),
    MUSIC_LIBRARY("Music Library"),
    MUSIC_SOURCES("Music Sources"),
    PLAYLISTS("Playlists"),
    FOLDERS("Folders"),
    FAVORITES("Favorites"),
    HISTORY("History"),
    RECORDINGS("Recordings"),
    SOUND_EFFECTS("Sound Effects"),
    SAMPLER("Sampler"),
    EQUALIZER("Equalizer"),
    DJ_EFFECTS("DJ Effects"),
    SETTINGS("Settings"),
    ABOUT("About BIJOY DJ")
}

data class WaveformPeak(
    val amplitude: Float, // 0..1
    val lowEnergy: Float, // bass intensity 0..1
    val highEnergy: Float // treble intensity 0..1
)

data class WaveformData(
    val peaks: List<WaveformPeak> = emptyList(),
    val beatPositionsRatio: List<Float> = emptyList()
)

data class HotCuePoint(
    val slotIndex: Int,
    val positionMs: Long? = null,
    val label: String = "CUE ${slotIndex + 1}"
)

data class DeckState(
    val deckId: DeckId,
    val songId: Long? = null,
    val title: String = if (deckId == DeckId.A) "DJ Remix - Party Mix 2026" else "Bangla DJ Song - Nonstop",
    val artist: String = if (deckId == DeckId.A) "DJ Bijoy Official" else "DJ Bijoy Mix",
    val artworkResName: String? = if (deckId == DeckId.A) "deck_a" else "deck_b",
    val isLoaded: Boolean = false,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val originalBpm: Float = if (deckId == DeckId.A) 128f else 132f,
    val effectiveBpm: Float = if (deckId == DeckId.A) 128f else 132f,
    val pitchPercent: Float = 0f, // -16% .. +16% (tempo)
    val keyLock: Boolean = true,
    val scratchMode: Boolean = true,
    val slipMode: Boolean = false,
    val syncActive: Boolean = false,
    val headphoneCue: Boolean = false,
    val beatFxAssigned: Boolean = true,
    val loopActive: Boolean = false,
    val loopInMs: Long? = null,
    val loopOutMs: Long? = null,
    val autoLoopBeats: Float = 4f,
    val hotCues: List<HotCuePoint> = (0..3).map { HotCuePoint(it) },
    val volume: Float = 0.85f, // 0..1
    val gainDb: Float = 0f, // -12..+12 dB
    val trebleDb: Float = 0f, // -12..+12 dB
    val midDb: Float = 0f, // -12..+12 dB
    val bassDb: Float = 0f, // -12..+12 dB
    val filterCutoff: Float = 0f, // -1 (LPF) .. 0 (Neutral) .. +1 (HPF) (Color knob)
    val eqPreset: EqPreset = EqPreset.FLAT,
    val activeEffect: DjEffectType = DjEffectType.NONE,
    val effectWet: Float = 0.45f,
    val effectParam: Float = 0.5f,
    val jogAngleDeg: Float = 0f,
    val levelLeft: Float = 0f,
    val levelRight: Float = 0f,
    val waveform: WaveformData = WaveformData(),
    val waveformZoom: Float = 1f,
    val beatCount: Int = 1 // 1..4
)

data class MixerState(
    val masterVolume: Float = 0.85f,
    val crossfader: Float = 0f, // -1f (Deck A) .. 0f (Center) .. +1f (Deck B)
    val crossfaderCurve: CrossfaderCurve = CrossfaderCurve.CONSTANT_POWER,
    val autoFadeActive: Boolean = false,
    val autoFadeTarget: Float = 1f,
    val activeColorFx: SoundColorFxType = SoundColorFxType.FILTER,
    val colorFxParam: Float = 0.5f,
    val beatFxType: DjEffectType = DjEffectType.ECHO,
    val beatFxOn: Boolean = false,
    val beatFxRatio: String = "1/2",
    val beatFxTarget: EffectTarget = EffectTarget.MASTER,
    val masterEffect: DjEffectType = DjEffectType.NONE,
    val masterEffectWet: Float = 0.4f,
    val masterEffectParam: Float = 0.5f,
    val masterLevelLeft: Float = 0f,
    val masterLevelRight: Float = 0f,
    val spectrumBands: List<Float> = List(16) { 0f }
)

data class SfxPadUi(
    val index: Int,
    val label: String,
    val colorHex: String,
    val synthType: String,
    val customAudioUri: String? = null,
    val volume: Float = 0.9f,
    val isPlaying: Boolean = false
)

data class RecordingState(
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val elapsedMs: Long = 0L,
    val currentTitle: String = "",
    val includeMic: Boolean = false
)
