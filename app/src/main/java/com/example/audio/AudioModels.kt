package com.example.audio

enum class DeckId { A, B }

enum class JogMode(val displayName: String, val shortLabel: String) {
    VINYL("Vinyl Mode", "VINYL"),
    CDJ("CDJ Mode", "CDJ"),
    SEARCH("Search Mode", "SRCH")
}

enum class PerformancePadMode(val displayName: String) {
    HOT_CUE("HOT CUE"),
    SAMPLER("SAMPLER"),
    ROLL("ROLL"),
    SLICER("SLICER"),
    LOOP("LOOP"),
    CUE_LOOP("CUE LOOP")
}

enum class ChannelAssign(val label: String) {
    A("A"),
    THRU("THRU"),
    B("B")
}

enum class CrossfaderCurve(val displayName: String) {
    LINEAR("Linear"),
    CONSTANT_POWER("Constant Power"),
    SHARP_CUT("Scratch Cut")
}

enum class EqBandMode(val displayName: String, val bandCount: Int) {
    BAND_3("3-Band", 3),
    BAND_5("5-Band", 5),
    BAND_10("10-Band", 10),
    BAND_15("15-Band", 15)
}

enum class EqPreset(
    val displayName: String,
    val bassDb: Float,
    val midDb: Float,
    val trebleDb: Float,
    val tenBandDb: List<Float> = listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
    val fifteenBandDb: List<Float> = List(15) { 0f }
) {
    FLAT(
        displayName = "Normal",
        bassDb = 0f,
        midDb = 0f,
        trebleDb = 0f,
        tenBandDb = List(10) { 0f },
        fifteenBandDb = List(15) { 0f }
    ),
    BASS_BOOST(
        displayName = "Bass Boost",
        bassDb = 8f,
        midDb = -1f,
        trebleDb = 1f,
        tenBandDb = listOf(8f, 7f, 5f, 2f, 0f, -1f, 0f, 1f, 1f, 1f),
        fifteenBandDb = listOf(8f, 8f, 7f, 6f, 4f, 2f, 0f, -1f, -1f, 0f, 0f, 1f, 1f, 1f, 1f)
    ),
    DEEP_BASS(
        displayName = "Deep Bass",
        bassDb = 9f,
        midDb = -3f,
        trebleDb = -2f,
        tenBandDb = listOf(10f, 8f, 6f, 2f, -1f, -3f, -2f, -1f, -1f, -2f),
        fifteenBandDb = listOf(11f, 10f, 9f, 7f, 5f, 2f, 0f, -2f, -3f, -2f, -2f, -1f, -1f, -2f, -2f)
    ),
    VOCAL_BOOST(
        displayName = "Vocal Boost",
        bassDb = -2f,
        midDb = 6f,
        trebleDb = 3f,
        tenBandDb = listOf(-3f, -2f, 0f, 3f, 6f, 6f, 5f, 3f, 2f, 1f),
        fifteenBandDb = listOf(-3f, -3f, -2f, 0f, 1f, 3f, 5f, 6f, 6f, 6f, 5f, 4f, 3f, 2f, 1f)
    ),
    CLUB(
        displayName = "Club",
        bassDb = 6f,
        midDb = 1f,
        trebleDb = 5f,
        tenBandDb = listOf(5f, 6f, 4f, 1f, 0f, 1f, 2f, 4f, 5f, 5f),
        fifteenBandDb = listOf(6f, 6f, 5f, 4f, 2f, 1f, 0f, 1f, 2f, 3f, 4f, 5f, 5f, 5f, 4f)
    ),
    EDM(
        displayName = "EDM",
        bassDb = 6.5f,
        midDb = 0f,
        trebleDb = 6f,
        tenBandDb = listOf(7f, 6f, 3f, -1f, -2f, 0f, 2f, 5f, 6f, 7f),
        fifteenBandDb = listOf(7f, 7f, 6f, 4f, 2f, 0f, -1f, -2f, 0f, 2f, 4f, 5f, 6f, 7f, 7f)
    ),
    ROCK(
        displayName = "Rock",
        bassDb = 4f,
        midDb = -1f,
        trebleDb = 4f,
        tenBandDb = listOf(5f, 4f, 2f, -1f, -2f, 0f, 2f, 4f, 5f, 5f),
        fifteenBandDb = listOf(5f, 5f, 4f, 3f, 1f, -1f, -2f, -1f, 0f, 2f, 3f, 4f, 5f, 5f, 4f)
    ),
    POP(
        displayName = "Pop",
        bassDb = 3f,
        midDb = 3f,
        trebleDb = 4f,
        tenBandDb = listOf(2f, 3f, 3f, 2f, 3f, 4f, 3f, 3f, 4f, 3f),
        fifteenBandDb = listOf(2f, 3f, 3f, 3f, 2f, 2f, 3f, 4f, 4f, 3f, 3f, 3f, 4f, 4f, 3f)
    ),
    CLASSICAL(
        displayName = "Classical",
        bassDb = 2f,
        midDb = 1f,
        trebleDb = 4f,
        tenBandDb = listOf(3f, 2f, 1f, 0f, 0f, 1f, 2f, 3f, 4f, 5f),
        fifteenBandDb = listOf(3f, 3f, 2f, 1f, 0f, 0f, 0f, 1f, 1f, 2f, 3f, 3f, 4f, 5f, 5f)
    ),
    ACOUSTIC(
        displayName = "Acoustic",
        bassDb = 2f,
        midDb = 3f,
        trebleDb = 3f,
        tenBandDb = listOf(3f, 2f, 2f, 1f, 3f, 3f, 2f, 3f, 3f, 2f),
        fifteenBandDb = listOf(3f, 2f, 2f, 1f, 1f, 2f, 3f, 3f, 3f, 2f, 3f, 3f, 3f, 3f, 2f)
    ),
    CUSTOM(
        displayName = "Custom",
        bassDb = 0f,
        midDb = 0f,
        trebleDb = 0f,
        tenBandDb = List(10) { 0f },
        fifteenBandDb = List(15) { 0f }
    )
}

enum class DjEffectType(val displayName: String) {
    NONE("Off"),
    ECHO("Echo"),
    REVERB("Reverb"),
    DELAY("Delay"),
    FLANGER("Flanger"),
    PHASER("Phaser"),
    CHORUS("Chorus"),
    DISTORTION("Distortion"),
    FILTER("Filter"),
    LOW_PASS_FILTER("Low-Pass"),
    HIGH_PASS_FILTER("High-Pass"),
    BEAT_REPEAT("Beat Repeat"),
    ROLL("Roll"),
    BACKSPIN("Backspin"),
    BRAKE("Brake"),
    VINYL_STOP("Vinyl Stop"),
    NOISE("Noise"),
    GATE("Gate"),
    COMPRESSOR("Compressor"),
    LIMITER("Limiter"),
    TREMOLO("Tremolo"),
    STUTTER("Stutter"),
    BIT_CRUSHER("Bit Crusher"),
    TRANS("Trans")
}

enum class EffectTarget(val displayName: String) {
    DECK_A("Channel 1 (Deck A)"),
    DECK_B("Channel 2 (Deck B)"),
    CHANNEL_3("Channel 3 (Sampler)"),
    CHANNEL_4("Channel 4 (Aux/FX)"),
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

enum class RecordSource(val displayName: String) {
    MASTER("Master Output"),
    DECK_A("Deck A"),
    DECK_B("Deck B"),
    LIVE_MIC("Live Mix + Mic")
}

enum class ExportAudioFormat(val displayName: String, val ext: String, val mimeType: String) {
    WAV("WAV (Lossless PCM)", "wav", "audio/wav"),
    AAC_M4A("M4A / AAC (Compressed)", "m4a", "audio/mp4")
}

enum class AppSection(val title: String) {
    DJ_MIXER("DJ Mixer"),
    AI_REMIX_STUDIO("AI Remix"),
    STEM_SEPARATOR("AI Stems"),
    AI_AUTO_DJ("AI Auto DJ"),
    SAD_TO_DJ("Sad → DJ"),
    AI_REMIX_GENERATOR("Remix Gen"),
    AI_ASSISTANT("AI DJ"),
    MUSIC_LIBRARY("Library"),
    FOLDERS("Folders"),
    PLAYLISTS("Playlists"),
    FAVORITES("Favorites"),
    HISTORY("History"),
    RECORDINGS("Recordings"),
    MUSIC_SOURCES("Music Sources"),
    SOUND_EFFECTS("SFX"),
    SAMPLER("Sampler"),
    DJ_EFFECTS("FX"),
    EQUALIZER("Equalizer"),
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
    val label: String = "HC ${slotIndex + 1}"
)

data class MixerChannelState(
    val channelIndex: Int, // 1, 2, 3, 4
    val label: String,
    val channelEnabled: Boolean = true, // Channel ON / OFF mute switch
    val gainDb: Float = 0f,
    val trebleDb: Float = 0f,
    val midDb: Float = 0f,
    val bassDb: Float = 0f,
    val vocalDb: Float = 0f, // Dedicated Vocal presence/cut control
    val filterCutoff: Float = 0f,
    val volume: Float = 0.85f,
    val cueActive: Boolean = false,
    val meterLevel: Float = 0f,
    val assign: ChannelAssign = ChannelAssign.THRU
)

data class DeckState(
    val deckId: DeckId,
    val songId: Long? = null,
    val title: String = if (deckId == DeckId.A) "Tumi Chole Gele" else "Na Dekhle - Dj (RemiX)",
    val artist: String = if (deckId == DeckId.A) "DJ Remix" else "DJ Bijoy Mix",
    val musicalKey: String = if (deckId == DeckId.A) "Am" else "Bm",
    val artworkResName: String? = if (deckId == DeckId.A) "deck_a" else "deck_b",
    val isLoaded: Boolean = false,
    val isPlaying: Boolean = false,
    val channelEnabled: Boolean = true,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val originalBpm: Float = if (deckId == DeckId.A) 128f else 135f,
    val effectiveBpm: Float = if (deckId == DeckId.A) 128f else 135f,
    val pitchPercent: Float = 0f,
    val pitchRangePercent: Float = 50f, // 6, 10, 16, 50, 100
    val keyLock: Boolean = true,
    val jogMode: JogMode = JogMode.VINYL,
    val scratchMode: Boolean = true,
    val slipMode: Boolean = false,
    val syncActive: Boolean = false,
    val headphoneCue: Boolean = false,
    val repeatEnabled: Boolean = false,
    val beatFxAssigned: Boolean = true,
    val loopActive: Boolean = false,
    val loopInMs: Long? = null,
    val loopOutMs: Long? = null,
    val autoLoopBeats: Float = 4f,
    val padMode: PerformancePadMode = PerformancePadMode.HOT_CUE,
    val activeSlicerIndex: Int = -1,
    val activeRollBeats: Float? = null,
    val hotCues: List<HotCuePoint> = (0..7).map { HotCuePoint(it) },
    val volume: Float = 0.85f, // 0..1
    val gainDb: Float = 0f, // -12..+12 dB
    val trebleDb: Float = 0f, // -12..+12 dB
    val midDb: Float = 0f, // -12..+12 dB
    val bassDb: Float = 0f, // -12..+12 dB
    val vocalDb: Float = 0f, // -12..+12 dB
    val filterCutoff: Float = 0f, // -1 (LPF) .. 0 (Neutral) .. +1 (HPF)
    val eqPreset: EqPreset = EqPreset.FLAT,
    val eqBandMode: EqBandMode = EqBandMode.BAND_10,
    val tenBandEqDb: List<Float> = List(10) { 0f },
    val fifteenBandEqDb: List<Float> = List(15) { 0f },
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
    val activeDeck: DeckId = DeckId.A,
    val masterVolume: Float = 0.85f,
    val boothVolume: Float = 0.75f,
    val headphoneVolume: Float = 0.85f,
    val headphoneMix: Float = 0.5f,
    val masterLimiterEnabled: Boolean = true,
    val crossfader: Float = 0f, // -1f (Deck A) .. 0f (Center) .. +1f (Deck B)
    val crossfaderCurve: CrossfaderCurve = CrossfaderCurve.CONSTANT_POWER,
    val autoFadeActive: Boolean = false,
    val autoFadeTarget: Float = 1f,
    val fadeDurationSec: Float = 5f, // 1, 3, 5, 10, 15
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
    val spectrumBands: List<Float> = List(16) { 0f },
    val channels: List<MixerChannelState> = listOf(
        MixerChannelState(1, "CH 1", assign = ChannelAssign.A),
        MixerChannelState(2, "CH 2", assign = ChannelAssign.B),
        MixerChannelState(3, "CH 3", assign = ChannelAssign.THRU),
        MixerChannelState(4, "CH 4", assign = ChannelAssign.THRU)
    ),
    val samplerRollBeats: Float = 1f,
    val samplerRollActive: Boolean = false,
    val samplerBankIndex: Int = 0
)

data class SfxPadUi(
    val index: Int,
    val label: String,
    val colorHex: String,
    val synthType: String,
    val category: String = "FX",
    val customAudioUri: String? = null,
    val volume: Float = 0.9f,
    val pitchRatio: Float = 1.0f,
    val isPlaying: Boolean = false,
    val isLooping: Boolean = false
)

data class RecordingState(
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val elapsedMs: Long = 0L,
    val currentTitle: String = "",
    val includeMic: Boolean = false,
    val source: RecordSource = RecordSource.MASTER
)
