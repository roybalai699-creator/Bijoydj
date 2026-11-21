package com.example.ai

import android.util.Log
import com.example.audio.*
import com.example.database.SongEntity
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

data class AutoDjTransitionPlan(
    val compatibilityScore: Int,
    val suggestedStyle: AutoDjTransitionStyle,
    val suggestedBpm: Float,
    val explanation: String
)

/**
 * Cloud AI service backed by Firebase AI Logic (`gemini-3.8-flash`) with App Check.
 * Uses structured JSON schemas for remix plans, beat patterns, Auto DJ transitions, and
 * actionable assistant command plans that always require explicit user confirmation.
 */
class BijoyAiService {

    companion object {
        private const val TAG = "BijoyAiService"
        private const val MODEL_NAME = "gemini-3.8-flash"
        private const val TIMEOUT_MS = 60_000L
    }

    private val remixPlanModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                temperature = 0.45f
                responseMimeType = "application/json"
                responseSchema = Schema.obj(
                    properties = mapOf(
                        "recommendedBpm" to Schema.float(),
                        "recommendedPitchSemitones" to Schema.float(),
                        "bassBoostLevel" to Schema.float(),
                        "reverbAmount" to Schema.float(),
                        "echoAmount" to Schema.float(),
                        "beatIntensity" to Schema.float(),
                        "dropTimestampSec" to Schema.float(),
                        "kickPattern16" to Schema.array(Schema.integer()),
                        "snarePattern16" to Schema.array(Schema.integer()),
                        "hatPattern16" to Schema.array(Schema.integer()),
                        "techniqueSummary" to Schema.string()
                    )
                )
            },
            systemInstruction = content {
                text(
                    "You are a senior DJ music producer and audio mastering engineer inside BIJOY DJ Studio. " +
                        "Return exact remix DSP parameters and 16-step drum grids (16 integers of 0 or 1). " +
                        "Clamp bassBoostLevel/reverbAmount/echoAmount/beatIntensity to 0.0..1.0 and pitch to -6..+6 semitones."
                )
            }
        )
    }

    private val patternModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                temperature = 0.65f
                responseMimeType = "application/json"
                responseSchema = Schema.obj(
                    properties = mapOf(
                        "kickPattern16" to Schema.array(Schema.integer()),
                        "snarePattern16" to Schema.array(Schema.integer()),
                        "hatPattern16" to Schema.array(Schema.integer())
                    )
                )
            }
        )
    }

    private val autoDjModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                temperature = 0.35f
                responseMimeType = "application/json"
                responseSchema = Schema.obj(
                    properties = mapOf(
                        "compatibilityScore" to Schema.integer(),
                        "suggestedStyle" to Schema.string(),
                        "suggestedBpm" to Schema.float(),
                        "explanation" to Schema.string()
                    )
                )
            }
        )
    }

    private val assistantModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                temperature = 0.4f
                responseMimeType = "application/json"
                responseSchema = Schema.obj(
                    properties = mapOf(
                        "replyText" to Schema.string(),
                        "actions" to Schema.array(
                            Schema.obj(
                                properties = mapOf(
                                    "actionType" to Schema.string(),
                                    "targetDeck" to Schema.string(),
                                    "description" to Schema.string(),
                                    "paramValue" to Schema.float(),
                                    "paramLabel" to Schema.string()
                                )
                            )
                        )
                    )
                )
            },
            systemInstruction = content {
                text(
                    "You are BIJOY AI, the co-pilot inside BIJOY DJ Studio. Give concise, expert DJ advice and propose " +
                        "executable mixer action plans that the DJ can approve or decline. Allowed actionType values: " +
                        "MATCH_BPM, APPLY_EQ, APPLY_FX, REMOVE_VOCALS, OPEN_SAD_TO_DJ, SWAP_BASS, START_AUTO_FADE, START_RECORDING. " +
                        "targetDeck must be A, B, or MASTER. For APPLY_FX put effect enum name (ECHO, REVERB, DELAY, FLANGER, FILTER, " +
                        "CHORUS, VINYL_STOP, GATE, BIT_CRUSHER, STUTTER) in paramLabel and wet (0..1) in paramValue. " +
                        "For APPLY_EQ put preset name (Bass Boost, Deep Bass, Vocal Boost, Club, EDM, Normal) in paramLabel."
                )
            }
        )
    }

    suspend fun generateRemixPlan(
        title: String,
        analysis: TrackAnalysisResult,
        targetStyle: RemixStyle
    ): Result<AiRemixPlan> {
        return try {
            val response = withTimeout(TIMEOUT_MS) {
                remixPlanModel.generateContent(
                    content {
                        text(
                            "Create a DJ remix production plan for track '$title'.\n" +
                                "Detected BPM: ${analysis.detectedBpm}, Key: ${analysis.musicalKey}, " +
                                "Genre: ${analysis.genre}, Mood: ${analysis.mood}, Energy: ${analysis.energyScore}, " +
                                "Vocal ratio: ${analysis.vocalDominanceRatio}.\n" +
                                "Target Remix Style: ${targetStyle.displayName} (suggested BPM ~${targetStyle.targetBpm}).\n" +
                                "Return 16-element kickPattern16, snarePattern16, hatPattern16 arrays of 0 or 1."
                        )
                    }
                )
            }
            val raw = response.text?.trim().orEmpty()
            if (raw.isEmpty()) error("Gemini returned an empty remix plan.")
            val json = JSONObject(raw)
            val plan = AiRemixPlan(
                targetStyle = targetStyle.displayName,
                recommendedBpm = json.optDouble("recommendedBpm", targetStyle.targetBpm.toDouble()).toFloat().coerceIn(70f, 175f),
                recommendedPitchSemitones = json.optDouble("recommendedPitchSemitones", 0.0).toFloat().coerceIn(-6f, 6f),
                bassBoostLevel = json.optDouble("bassBoostLevel", targetStyle.bassGain.toDouble()).toFloat().coerceIn(0f, 1f),
                reverbAmount = json.optDouble("reverbAmount", targetStyle.reverb.toDouble()).toFloat().coerceIn(0f, 1f),
                echoAmount = json.optDouble("echoAmount", targetStyle.echo.toDouble()).toFloat().coerceIn(0f, 1f),
                beatIntensity = json.optDouble("beatIntensity", 0.8).toFloat().coerceIn(0f, 1f),
                dropTimestampSec = json.optDouble("dropTimestampSec", 10.0).toFloat().coerceIn(2f, 180f),
                kickPattern16 = parseGrid16(json.optJSONArray("kickPattern16"), listOf(1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0)),
                snarePattern16 = parseGrid16(json.optJSONArray("snarePattern16"), listOf(0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0)),
                hatPattern16 = parseGrid16(json.optJSONArray("hatPattern16"), listOf(0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 1)),
                techniqueSummary = json.optString("techniqueSummary", "${targetStyle.displayName} arrangement ready."),
                isCloudAiGenerated = true
            )
            Result.success(plan)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "generateRemixPlan failed", e)
            Result.failure(e)
        }
    }

    suspend fun generateBeatPatternFromPrompt(
        prompt: String,
        styleName: String
    ): Result<Triple<List<Int>, List<Int>, List<Int>>> {
        return try {
            val response = withTimeout(TIMEOUT_MS) {
                patternModel.generateContent(
                    content {
                        text(
                            "Generate a 16-step DJ drum pattern (values 0 or 1, exactly 16 entries per array) " +
                                "for style '$styleName'. Producer instruction: $prompt"
                        )
                    }
                )
            }
            val raw = response.text?.trim().orEmpty()
            if (raw.isEmpty()) error("Gemini returned an empty beat pattern.")
            val json = JSONObject(raw)
            val kicks = parseGrid16(json.optJSONArray("kickPattern16"), listOf(1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0))
            val snares = parseGrid16(json.optJSONArray("snarePattern16"), listOf(0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0))
            val hats = parseGrid16(json.optJSONArray("hatPattern16"), listOf(0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 1))
            Result.success(Triple(kicks, snares, hats))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "generateBeatPatternFromPrompt failed", e)
            Result.failure(e)
        }
    }

    suspend fun planAutoDjTransition(
        trackA: SongEntity,
        trackB: SongEntity,
        mode: AutoDjMode
    ): Result<AutoDjTransitionPlan> {
        return try {
            val response = withTimeout(TIMEOUT_MS) {
                autoDjModel.generateContent(
                    content {
                        text(
                            "Plan a DJ transition in ${mode.displayName} from Track 1 ('${trackA.title}' by ${trackA.artist}, " +
                                "${trackA.bpm} BPM, key ${trackA.musicalKey}) into Track 2 ('${trackB.title}' by ${trackB.artist}, " +
                                "${trackB.bpm} BPM, key ${trackB.musicalKey}). " +
                                "suggestedStyle must be one of: BEAT_MATCH, SMOOTH_FADE, BASS_SWAP, EQ_TRANSITION, ECHO_OUT, FILTER_TRANSITION, BEAT_DROP, INTRO_OUTRO."
                        )
                    }
                )
            }
            val raw = response.text?.trim().orEmpty()
            if (raw.isEmpty()) error("Gemini returned an empty Auto DJ plan.")
            val json = JSONObject(raw)
            val styleStr = json.optString("suggestedStyle", "BASS_SWAP")
            val style = AutoDjTransitionStyle.entries.find {
                it.name.equals(styleStr, ignoreCase = true) || it.displayName.equals(styleStr, ignoreCase = true)
            } ?: AutoDjTransitionStyle.BASS_SWAP
            Result.success(
                AutoDjTransitionPlan(
                    compatibilityScore = json.optInt("compatibilityScore", 82).coerceIn(10, 100),
                    suggestedStyle = style,
                    suggestedBpm = json.optDouble("suggestedBpm", trackB.bpm.toDouble()).toFloat().coerceIn(60f, 185f),
                    explanation = json.optString("explanation", "Harmonic beat-matched transition.")
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "planAutoDjTransition failed", e)
            Result.failure(e)
        }
    }

    suspend fun askDjAssistant(
        prompt: String,
        deckA: DeckState,
        deckB: DeckState,
        mixer: MixerState,
        librarySummary: String
    ): Result<Pair<String, List<AiProposedAction>>> {
        return try {
            val response = withTimeout(TIMEOUT_MS) {
                assistantModel.generateContent(
                    content {
                        text(
                            "Current Booth State:\n" +
                                "- Deck A: '${deckA.title}' (${deckA.effectiveBpm.toInt()} BPM, key ${deckA.musicalKey}, playing=${deckA.isPlaying}, vocalDb=${deckA.vocalDb}, fx=${deckA.activeEffect.displayName})\n" +
                                "- Deck B: '${deckB.title}' (${deckB.effectiveBpm.toInt()} BPM, key ${deckB.musicalKey}, playing=${deckB.isPlaying}, vocalDb=${deckB.vocalDb}, fx=${deckB.activeEffect.displayName})\n" +
                                "- Crossfader: ${String.format("%.2f", mixer.crossfader)}, Active Deck: ${mixer.activeDeck.name}, Master Vol: ${(mixer.masterVolume * 100).toInt()}%\n" +
                                "- Library sample: $librarySummary\n\n" +
                                "User request: $prompt"
                        )
                    }
                )
            }
            val raw = response.text?.trim().orEmpty()
            if (raw.isEmpty()) error("Gemini assistant returned an empty response.")
            val json = JSONObject(raw)
            val reply = json.optString("replyText", "Ready to help in the booth.")
            val arr = json.optJSONArray("actions") ?: JSONArray()
            val actions = mutableListOf<AiProposedAction>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val type = obj.optString("actionType", "").trim().uppercase()
                if (type.isBlank() || type == "NONE") continue
                actions.add(
                    AiProposedAction(
                        actionType = type,
                        targetDeck = obj.optString("targetDeck", "A").trim().uppercase(),
                        description = obj.optString("description", type),
                        paramValue = obj.optDouble("paramValue", 0.5).toFloat(),
                        paramLabel = obj.optString("paramLabel", "")
                    )
                )
            }
            Result.success(reply to actions)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "askDjAssistant failed", e)
            Result.failure(e)
        }
    }

    private fun parseGrid16(arr: JSONArray?, fallback: List<Int>): List<Int> {
        if (arr == null || arr.length() == 0) return fallback
        return (0 until 16).map { idx ->
            if (idx < arr.length() && arr.optInt(idx, 0) != 0) 1 else 0
        }
    }
}
