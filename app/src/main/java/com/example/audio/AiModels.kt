package com.example.audio

/**
 * Domain & UI state models for BIJOY DJ AI Studio modules:
 * - AI Song Remix Studio (15 styles, 10-step workflow)
 * - AI Stem Separation & Vocal Remover
 * - AI Auto DJ (3 modes, transition styles, Camelot compatibility)
 * - Sad Song to DJ Converter (11 presets + 10 parameter controls)
 * - AI Remix Generator (Drum pattern + bassline + drop planner)
 * - BIJOY AI DJ Assistant (Gemini chat + explicit user approval action plan)
 */

enum class RemixStyle(val displayName: String, val targetBpm: Float, val bassGain: Float, val reverb: Float, val echo: Float) {
    SAD_DJ("Sad DJ Remix", 122f, 0.72f, 0.55f, 0.38f),
    BASS_BOOST("Bass Boost Remix", 128f, 0.92f, 0.25f, 0.20f),
    SLOWED_REVERB("Slowed + Reverb", 96f, 0.58f, 0.85f, 0.42f),
    NIGHTCORE("Nightcore", 152f, 0.50f, 0.22f, 0.18f),
    DEEP_HOUSE("Deep House", 124f, 0.78f, 0.45f, 0.35f),
    EDM_CLUB("EDM Remix", 128f, 0.85f, 0.40f, 0.42f),
    FESTIVAL_DJ("Festival DJ Remix", 132f, 0.90f, 0.50f, 0.45f),
    CLUB_REMIX("Club Remix", 128f, 0.80f, 0.35f, 0.30f),
    CINEMATIC("Cinematic Remix", 112f, 0.68f, 0.75f, 0.50f),
    LOFI_CHILL("Lofi Chill", 88f, 0.45f, 0.60f, 0.28f),
    AFRO_HOUSE("Afro House", 122f, 0.76f, 0.38f, 0.32f),
    TRAP_REMIX("Trap Remix", 140f, 0.95f, 0.35f, 0.35f),
    BENGALI_DJ("Bengali DJ Remix", 134f, 0.86f, 0.32f, 0.36f),
    BOLLYWOOD_DJ("Bollywood DJ Remix", 132f, 0.84f, 0.34f, 0.34f),
    CUSTOM("Custom Style", 128f, 0.70f, 0.40f, 0.30f)
}

enum class SadToDjStyle(val displayName: String, val presetBpm: Float, val pitchFactor: Float, val bassStrength: Float, val reverbAmount: Float, val beatIntensity: Float) {
    SAD_DJ("Sad DJ", 124f, 1.0f, 0.72f, 0.55f, 0.70f),
    EMOTIONAL_BASS("Emotional Bass Remix", 122f, 1.0f, 0.86f, 0.62f, 0.75f),
    HEARTBREAK_SLOWED("Heartbreak Slowed Remix", 94f, 0.88f, 0.64f, 0.82f, 0.35f),
    SLOWED_REVERB("Slowed + Reverb", 92f, 0.85f, 0.55f, 0.90f, 0.25f),
    DEEP_BASS("Deep Bass Remix", 126f, 1.0f, 0.94f, 0.42f, 0.82f),
    NIGHTCORE("Nightcore", 148f, 1.18f, 0.48f, 0.25f, 0.85f),
    CLUB_REMIX("Club Remix", 128f, 1.0f, 0.80f, 0.38f, 0.88f),
    EDM_DROP("EDM Drop Remix", 130f, 1.0f, 0.88f, 0.45f, 0.95f),
    MELODIC_HOUSE("Melodic House", 122f, 1.0f, 0.74f, 0.58f, 0.78f),
    CINEMATIC_REMIX("Cinematic Remix", 114f, 0.96f, 0.70f, 0.76f, 0.60f),
    BENGALI_DJ("Bengali DJ Remix", 134f, 1.02f, 0.86f, 0.36f, 0.90f)
}

data class SadToDjParams(
    val style: SadToDjStyle = SadToDjStyle.SAD_DJ,
    val bassStrength: Float = 0.72f,      // 0..1
    val reverbAmount: Float = 0.55f,      // 0..1
    val echoAmount: Float = 0.35f,        // 0..1
    val tempoBpm: Float = 124f,           // 70..175
    val pitchSemitones: Float = 0f,       // -6..+6
    val vocalLevel: Float = 0.95f,        // 0..1.4
    val instrumentalLevel: Float = 0.85f, // 0..1.4
    val beatIntensity: Float = 0.70f,     // 0..1
    val stereoWidth: Float = 1.20f,       // 0.5..1.8
    val masterVolume: Float = 0.90f,      // 0.3..1.2
    val dropPositionRatio: Float = 0.35f, // 0..0.9
    val addAiDjIntroOutro: Boolean = true
)

data class SongSectionMarker(
    val name: String,      // Intro, Verse, Chorus, Bridge, Drop, Outro
    val startSec: Float,
    val endSec: Float
)

data class TrackAnalysisResult(
    val detectedBpm: Float,
    val musicalKey: String,
    val genre: String,
    val mood: String,
    val rhythmProfile: String,
    val energyScore: Float,
    val vocalDominanceRatio: Float,
    val sections: List<SongSectionMarker>,
    val aiNotes: String
)

data class AiRemixPlan(
    val targetStyle: String,
    val recommendedBpm: Float,
    val recommendedPitchSemitones: Float,
    val bassBoostLevel: Float,
    val reverbAmount: Float,
    val echoAmount: Float,
    val beatIntensity: Float,
    val dropTimestampSec: Float,
    val kickPattern16: List<Int>,
    val snarePattern16: List<Int>,
    val hatPattern16: List<Int>,
    val techniqueSummary: String,
    val isCloudAiGenerated: Boolean
)

data class RenderedRemixResult(
    val title: String,
    val wavFilePath: String,
    val durationMs: Long,
    val styleName: String,
    val decoded: DecodedAudio,
    val analysis: TrackAnalysisResult?,
    val plan: AiRemixPlan?
)

data class StemMixLevels(
    val vocalsGain: Float = 1f,
    val drumsGain: Float = 1f,
    val bassGain: Float = 1f,
    val otherGain: Float = 1f,
    val vocalsMuted: Boolean = false,
    val drumsMuted: Boolean = false,
    val bassMuted: Boolean = false,
    val otherMuted: Boolean = false,
    val soloStem: String? = null // "VOCALS", "DRUMS", "BASS", "OTHER", or null
)

class SeparatedStems(
    val sourceSongId: Long,
    val sourceTitle: String,
    val sampleRate: Int,
    val vocalsPcm: ShortArray,
    val drumsPcm: ShortArray,
    val bassPcm: ShortArray,
    val otherPcm: ShortArray,
    val vocalsEnergy: Float,
    val drumsEnergy: Float,
    val bassEnergy: Float,
    val otherEnergy: Float
)

enum class AutoDjMode(val displayName: String, val subtitle: String) {
    BEGINNER("Beginner Mode", "Smooth 16-beat fade + BPM auto-sync"),
    PRO_DJ("Pro DJ Mode", "Harmonic phrasing + bass swap transitions"),
    AI_AUTO("AI Auto Mode", "Gemini key/energy ordering + dynamic drops")
}

enum class AutoDjTransitionStyle(val displayName: String) {
    BEAT_MATCH("Beat Match"),
    SMOOTH_FADE("Smooth Fade"),
    BASS_SWAP("Bass Swap"),
    EQ_TRANSITION("EQ Transition"),
    ECHO_OUT("Echo Out"),
    FILTER_TRANSITION("Filter Sweep"),
    BEAT_DROP("Beat Drop"),
    INTRO_OUTRO("Intro / Outro Alignment")
}

data class AutoDjState(
    val isActive: Boolean = false,
    val isPaused: Boolean = false,
    val mode: AutoDjMode = AutoDjMode.AI_AUTO,
    val transitionStyle: AutoDjTransitionStyle = AutoDjTransitionStyle.BASS_SWAP,
    val queueSongIds: List<Long> = emptyList(),
    val currentQueueIndex: Int = 0,
    val currentDeck: DeckId = DeckId.A,
    val isTransitioning: Boolean = false,
    val transitionProgress: Float = 0f,
    val compatibilitySummary: String = "Select 2+ songs to build an AI mix queue",
    val activePlanExplanation: String = ""
)

data class AiProposedAction(
    val actionType: String, // MATCH_BPM, APPLY_EQ, APPLY_FX, REMOVE_VOCALS, OPEN_SAD_TO_DJ, SWAP_BASS, START_AUTO_FADE, START_RECORDING
    val targetDeck: String = "A", // A, B, MASTER
    val description: String,
    val paramValue: Float = 0f,
    val paramLabel: String = ""
)

data class AiAssistantMessage(
    val id: Long,
    val isUser: Boolean,
    val text: String,
    val proposedActions: List<AiProposedAction> = emptyList(),
    val actionsApproved: Boolean? = null, // null = pending confirmation, true = applied, false = declined
    val isError: Boolean = false
)

sealed interface AiTaskUiState {
    data object Idle : AiTaskUiState
    data class Working(val stepLabel: String, val progress: Float) : AiTaskUiState
    data class Success(val message: String) : AiTaskUiState
    data class Error(val message: String) : AiTaskUiState
}
