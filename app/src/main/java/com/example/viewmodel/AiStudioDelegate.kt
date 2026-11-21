package com.example.viewmodel

import android.app.Application
import android.net.Uri
import com.example.ai.BijoyAiService
import com.example.audio.*
import com.example.database.SongEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Modular delegate encapsulating all AI Studio, Stem Separation, Sad-to-DJ, Auto DJ,
 * AI Beat Pattern Generator, and BIJOY AI DJ Assistant workflows.
 */
class AiStudioDelegate(
    private val app: Application,
    private val scope: CoroutineScope,
    private val repository: BijoyRepository,
    private val audioEngine: BijoyAudioEngine,
    private val aiService: BijoyAiService,
    private val onToast: (String) -> Unit,
    private val onNavigate: (AppSection) -> Unit,
    private val onLoadSong: (DeckId, SongEntity, Boolean) -> Unit,
    private val onStartRecording: (String?, Boolean, RecordSource) -> Unit
) {
    private var activeAiJob: Job? = null
    private var autoDjLoopJob: Job? = null

    private val _selectedSourceSong = MutableStateFlow<SongEntity?>(null)
    val selectedSourceSong: StateFlow<SongEntity?> = _selectedSourceSong.asStateFlow()

    private val _trackAnalysis = MutableStateFlow<TrackAnalysisResult?>(null)
    val trackAnalysis: StateFlow<TrackAnalysisResult?> = _trackAnalysis.asStateFlow()

    private val _selectedRemixStyle = MutableStateFlow(RemixStyle.SAD_DJ)
    val selectedRemixStyle: StateFlow<RemixStyle> = _selectedRemixStyle.asStateFlow()

    private val _aiRemixPlan = MutableStateFlow<AiRemixPlan?>(null)
    val aiRemixPlan: StateFlow<AiRemixPlan?> = _aiRemixPlan.asStateFlow()

    private val _renderedRemix = MutableStateFlow<RenderedRemixResult?>(null)
    val renderedRemix: StateFlow<RenderedRemixResult?> = _renderedRemix.asStateFlow()

    private val _isPreviewingOriginal = MutableStateFlow(false)
    val isPreviewingOriginal: StateFlow<Boolean> = _isPreviewingOriginal.asStateFlow()

    private val _aiTaskState = MutableStateFlow<AiTaskUiState>(AiTaskUiState.Idle)
    val aiTaskState: StateFlow<AiTaskUiState> = _aiTaskState.asStateFlow()

    private val _sadToDjParams = MutableStateFlow(SadToDjParams())
    val sadToDjParams: StateFlow<SadToDjParams> = _sadToDjParams.asStateFlow()

    // Stem Separation
    private val _separatedStems = MutableStateFlow<SeparatedStems?>(null)
    val separatedStems: StateFlow<SeparatedStems?> = _separatedStems.asStateFlow()

    private val _stemMixLevels = MutableStateFlow(StemMixLevels())
    val stemMixLevels: StateFlow<StemMixLevels> = _stemMixLevels.asStateFlow()

    private val _stemTaskState = MutableStateFlow<AiTaskUiState>(AiTaskUiState.Idle)
    val stemTaskState: StateFlow<AiTaskUiState> = _stemTaskState.asStateFlow()

    // Pattern Generator (16-step grid)
    private val _kickPattern = MutableStateFlow(listOf(1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0))
    val kickPattern: StateFlow<List<Int>> = _kickPattern.asStateFlow()
    private val _snarePattern = MutableStateFlow(listOf(0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0))
    val snarePattern: StateFlow<List<Int>> = _snarePattern.asStateFlow()
    private val _hatPattern = MutableStateFlow(listOf(0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 1))
    val hatPattern: StateFlow<List<Int>> = _hatPattern.asStateFlow()

    // Auto DJ
    private val _autoDjState = MutableStateFlow(AutoDjState())
    val autoDjState: StateFlow<AutoDjState> = _autoDjState.asStateFlow()

    // AI DJ Assistant
    private val _assistantMessages = MutableStateFlow(
        listOf(
            AiAssistantMessage(
                id = 1L,
                isUser = false,
                text = "Welcome to BIJOY AI DJ Studio! Ask me to match BPM, suggest Club/Bass EQ, apply Echo/Reverb/Filter FX, remove vocals, or prep a Sad-to-DJ remix. I will ask for your approval before modifying any deck controls."
            )
        )
    )
    val assistantMessages: StateFlow<List<AiAssistantMessage>> = _assistantMessages.asStateFlow()

    private val _assistantLoading = MutableStateFlow(false)
    val assistantLoading: StateFlow<Boolean> = _assistantLoading.asStateFlow()

    suspend fun decodeSong(song: SongEntity): Result<DecodedAudio> {
        return if (song.isBuiltInSynth) {
            Result.success(AudioDecoder.generateBuiltInDjTrack(song.synthPresetId, song.bpm))
        } else {
            AudioDecoder.decodeUriToPcm(app, Uri.parse(song.uriString), song.localCopyPath)
        }
    }

    fun selectSourceSong(song: SongEntity) {
        _selectedSourceSong.value = song
        analyzeSong(song)
    }

    fun selectRemixStyle(style: RemixStyle) {
        _selectedRemixStyle.value = style
    }

    fun analyzeSong(song: SongEntity) {
        activeAiJob?.cancel()
        activeAiJob = scope.launch {
            _aiTaskState.value = AiTaskUiState.Working("Analyzing BPM, key & sections...", 0.3f)
            decodeSong(song).onSuccess { decoded ->
                val metrics = OfflineRemixRenderer.analyzeTrackMetrics(decoded)
                _trackAnalysis.value = metrics
                _aiTaskState.value = AiTaskUiState.Success("Analyzed: ${metrics.detectedBpm.toInt()} BPM • Key ${metrics.musicalKey}")
            }.onFailure { err ->
                _aiTaskState.value = AiTaskUiState.Error("Decode failed: ${err.localizedMessage ?: "Audio file unreadable"}")
            }
        }
    }

    fun generateAiRemixPlan(song: SongEntity, style: RemixStyle) {
        activeAiJob?.cancel()
        activeAiJob = scope.launch {
            _selectedSourceSong.value = song
            _selectedRemixStyle.value = style
            _aiTaskState.value = AiTaskUiState.Working("Running audio analysis...", 0.25f)
            val decoded = decodeSong(song).getOrElse { err ->
                _aiTaskState.value = AiTaskUiState.Error("Decode error: ${err.localizedMessage}")
                return@launch
            }
            val analysis = OfflineRemixRenderer.analyzeTrackMetrics(decoded)
            _trackAnalysis.value = analysis

            _aiTaskState.value = AiTaskUiState.Working("Gemini 3.8 Flash planning ${style.displayName}...", 0.6f)
            aiService.generateRemixPlan(song.title, analysis, style)
                .onSuccess { plan ->
                    _aiRemixPlan.value = plan
                    _kickPattern.value = plan.kickPattern16
                    _snarePattern.value = plan.snarePattern16
                    _hatPattern.value = plan.hatPattern16
                    _aiTaskState.value = AiTaskUiState.Success("Gemini AI Remix Plan ready (${plan.recommendedBpm.toInt()} BPM)")
                }
                .onFailure { err ->
                    _aiTaskState.value = AiTaskUiState.Error("Gemini AI Error: ${err.localizedMessage ?: "Request failed — verify network/App Check and retry."}")
                }
        }
    }

    fun renderAiSongRemix(song: SongEntity, style: RemixStyle, saveToLibrary: Boolean = true) {
        activeAiJob?.cancel()
        activeAiJob = scope.launch {
            _selectedSourceSong.value = song
            _selectedRemixStyle.value = style
            _aiTaskState.value = AiTaskUiState.Working("Decoding source audio...", 0.12f)
            val decoded = decodeSong(song).getOrElse { err ->
                _aiTaskState.value = AiTaskUiState.Error("Unable to decode '${song.title}': ${err.localizedMessage}")
                return@launch
            }
            val analysis = _trackAnalysis.value ?: OfflineRemixRenderer.analyzeTrackMetrics(decoded).also {
                _trackAnalysis.value = it
            }

            _aiTaskState.value = AiTaskUiState.Working("Consulting Gemini AI Studio...", 0.28f)
            val planResult = aiService.generateRemixPlan(song.title, analysis, style)
            val plan = planResult.getOrNull() ?: _aiRemixPlan.value
            if (plan != null) {
                _aiRemixPlan.value = plan
            }

            OfflineRemixRenderer.renderSongRemix(
                context = app,
                sourceDecoded = decoded,
                sourceTitle = song.title,
                style = style,
                plan = plan,
                analysis = analysis,
                onProgress = { p ->
                    _aiTaskState.value = AiTaskUiState.Working("Rendering separate DSP WAV remix...", 0.35f + p * 0.65f)
                }
            ).onSuccess { result ->
                _renderedRemix.value = result
                _isPreviewingOriginal.value = false
                if (saveToLibrary) {
                    repository.registerRenderedRemix(
                        title = result.title,
                        wavFilePath = result.wavFilePath,
                        durationMs = result.durationMs,
                        bpm = result.decoded.detectedBpm,
                        musicalKey = analysis.musicalKey,
                        styleName = style.displayName
                    )
                }
                val suffix = if (planResult.isFailure) " (DSP render used cached/style settings: ${planResult.exceptionOrNull()?.localizedMessage})" else ""
                _aiTaskState.value = AiTaskUiState.Success("Remix saved as new WAV: ${result.title}$suffix")
                onToast("Remix generated & saved separately!")
            }.onFailure { err ->
                _aiTaskState.value = AiTaskUiState.Error("Render error: ${err.localizedMessage}")
            }
        }
    }

    // ==================== SAD SONG -> DJ CONVERTER ====================

    fun applySadToDjPreset(style: SadToDjStyle) {
        _sadToDjParams.update {
            it.copy(
                style = style,
                tempoBpm = style.presetBpm,
                pitchSemitones = ((style.pitchFactor - 1f) * 12f).coerceIn(-6f, 6f),
                bassStrength = style.bassStrength,
                reverbAmount = style.reverbAmount,
                beatIntensity = style.beatIntensity
            )
        }
    }

    fun updateSadToDjParams(transform: (SadToDjParams) -> SadToDjParams) {
        _sadToDjParams.update(transform)
    }

    fun convertSadSongToDj(song: SongEntity) {
        activeAiJob?.cancel()
        activeAiJob = scope.launch {
            _selectedSourceSong.value = song
            _aiTaskState.value = AiTaskUiState.Working("Decoding emotional track...", 0.15f)
            val decoded = decodeSong(song).getOrElse { err ->
                _aiTaskState.value = AiTaskUiState.Error("Decode failed: ${err.localizedMessage}")
                return@launch
            }
            val p = _sadToDjParams.value
            OfflineRemixRenderer.renderSadToDjRemix(
                context = app,
                sourceDecoded = decoded,
                sourceTitle = song.title,
                params = p,
                kickPattern16 = _kickPattern.value,
                snarePattern16 = _snarePattern.value,
                hatPattern16 = _hatPattern.value,
                onProgress = { prog ->
                    _aiTaskState.value = AiTaskUiState.Working("Converting to ${p.style.displayName}...", 0.18f + prog * 0.80f)
                }
            ).onSuccess { result ->
                _renderedRemix.value = result
                _isPreviewingOriginal.value = false
                repository.registerRenderedRemix(
                    title = result.title,
                    wavFilePath = result.wavFilePath,
                    durationMs = result.durationMs,
                    bpm = p.tempoBpm,
                    musicalKey = song.musicalKey,
                    styleName = p.style.displayName
                )
                _aiTaskState.value = AiTaskUiState.Success("Converted to ${p.style.displayName} WAV & added to AI Remixes!")
                onToast("Saved ${result.title}")
            }.onFailure { err ->
                _aiTaskState.value = AiTaskUiState.Error("Conversion failed: ${err.localizedMessage}")
            }
        }
    }

    fun toggleAbPreview(sourceSong: SongEntity?, rendered: RenderedRemixResult) {
        val nextShowOriginal = !_isPreviewingOriginal.value
        _isPreviewingOriginal.value = nextShowOriginal
        if (nextShowOriginal && sourceSong != null) {
            scope.launch {
                decodeSong(sourceSong).onSuccess { dec ->
                    audioEngine.loadDecodedTrack(DeckId.A, sourceSong.id, "[ORIG] ${sourceSong.title}", sourceSong.artist, sourceSong.musicalKey, sourceSong.artworkResName, dec, (0..7).map { HotCuePoint(it) })
                    audioEngine.setPlaying(DeckId.A, true)
                }
            }
        } else {
            audioEngine.loadDecodedTrack(DeckId.A, 0L, "[REMIX] ${rendered.title}", "BIJOY AI Remix", "Am", "deck_b", rendered.decoded, (0..7).map { HotCuePoint(it) })
            audioEngine.setPlaying(DeckId.A, true)
        }
    }

    fun loadRemixToDeck(rendered: RenderedRemixResult, deckId: DeckId) {
        audioEngine.loadDecodedTrack(deckId, 0L, rendered.title, "BIJOY AI Studio", "Am", "deck_b", rendered.decoded, (0..7).map { HotCuePoint(it) })
        audioEngine.setPlaying(deckId, true)
        onNavigate(AppSection.DJ_MIXER)
        onToast("Loaded AI Remix to Deck ${deckId.name}")
    }

    // ==================== 4-STEM SEPARATOR & VOCAL REMOVER ====================

    fun separateTrackStems(song: SongEntity) {
        activeAiJob?.cancel()
        activeAiJob = scope.launch {
            _selectedSourceSong.value = song
            _stemTaskState.value = AiTaskUiState.Working("Decoding PCM audio...", 0.12f)
            val decoded = decodeSong(song).getOrElse { err ->
                _stemTaskState.value = AiTaskUiState.Error("Decode failed: ${err.localizedMessage}")
                return@launch
            }
            val stems = StemSeparatorEngine.separateIntoStems(song.id, song.title, decoded) { prog ->
                _stemTaskState.value = AiTaskUiState.Working("Isolating Vocals, Drums, Bass & Other stems...", 0.15f + prog * 0.85f)
            }
            _separatedStems.value = stems
            _stemMixLevels.value = StemMixLevels()
            _stemTaskState.value = AiTaskUiState.Success("4 Stems separated (Vocals / Drums / Bass / Other)")
        }
    }

    fun updateStemLevels(transform: (StemMixLevels) -> StemMixLevels) {
        _stemMixLevels.update(transform)
    }

    fun applyVocalsOnlyQuickMode() {
        _stemMixLevels.value = StemMixLevels(
            vocalsGain = 1.15f, drumsGain = 0f, bassGain = 0f, otherGain = 0f,
            drumsMuted = true, bassMuted = true, otherMuted = true, soloStem = "VOCALS"
        )
    }

    fun applyInstrumentalOnlyQuickMode() {
        _stemMixLevels.value = StemMixLevels(
            vocalsGain = 0f, drumsGain = 1f, bassGain = 1f, otherGain = 1f,
            vocalsMuted = true, soloStem = null
        )
    }

    fun previewStemsOnDeck(deckId: DeckId = DeckId.A) {
        val stems = _separatedStems.value ?: return
        scope.launch {
            val mixed = StemSeparatorEngine.mixStemsToDecoded(stems, _stemMixLevels.value)
            audioEngine.loadDecodedTrack(deckId, stems.sourceSongId, "[STEM MIX] ${stems.sourceTitle}", "4-Stem DSP Mix", "Am", "deck_a", mixed, (0..7).map { HotCuePoint(it) })
            audioEngine.setPlaying(deckId, true)
            onToast("Playing 4-Stem Mix on Deck ${deckId.name}")
        }
    }

    fun exportSingleStemWav(stemKey: String) {
        val stems = _separatedStems.value ?: return
        scope.launch {
            StemSeparatorEngine.exportStemWav(app, stems, stemKey)
                .onSuccess { f ->
                    val decoded = AudioDecoder.decodeUriToPcm(app, Uri.fromFile(f), f.absolutePath).getOrNull()
                    if (decoded != null) {
                        repository.registerRenderedRemix("${stems.sourceTitle} [$stemKey]", f.absolutePath, decoded.durationMs, decoded.detectedBpm, "Am", "$stemKey Stem")
                    }
                    onToast("Saved $stemKey WAV: ${f.name}")
                }
                .onFailure { onToast("Stem export failed: ${it.localizedMessage}") }
        }
    }

    fun remixSeparatedStems(style: RemixStyle) {
        val stems = _separatedStems.value ?: return
        activeAiJob?.cancel()
        activeAiJob = scope.launch {
            _aiTaskState.value = AiTaskUiState.Working("Mixing active stems into remix...", 0.25f)
            val mixed = StemSeparatorEngine.mixStemsToDecoded(stems, _stemMixLevels.value)
            val analysis = OfflineRemixRenderer.analyzeTrackMetrics(mixed)
            OfflineRemixRenderer.renderSongRemix(app, mixed, "${stems.sourceTitle} Stems", style, null, analysis) { p ->
                _aiTaskState.value = AiTaskUiState.Working("Rendering stem remix...", 0.3f + p * 0.7f)
            }.onSuccess { result ->
                _renderedRemix.value = result
                repository.registerRenderedRemix(result.title, result.wavFilePath, result.durationMs, result.decoded.detectedBpm, analysis.musicalKey, style.displayName)
                onNavigate(AppSection.AI_REMIX_STUDIO)
                _aiTaskState.value = AiTaskUiState.Success("Stem Remix created: ${result.title}")
            }
        }
    }

    // ==================== 16-STEP BEAT PATTERN GENERATOR ====================

    fun togglePatternStep(row: Int, step: Int) {
        if (step !in 0..15) return
        val flow = when (row) { 0 -> _kickPattern; 1 -> _snarePattern; else -> _hatPattern }
        flow.update { list -> list.mapIndexed { i, v -> if (i == step) (if (v == 1) 0 else 1) else v } }
    }

    fun generateAiBeatPattern(prompt: String, styleName: String) {
        activeAiJob?.cancel()
        activeAiJob = scope.launch {
            _aiTaskState.value = AiTaskUiState.Working("Gemini generating 16-step drum pattern...", 0.45f)
            aiService.generateBeatPatternFromPrompt(prompt, styleName)
                .onSuccess { (k, s, h) ->
                    _kickPattern.value = k; _snarePattern.value = s; _hatPattern.value = h
                    _aiTaskState.value = AiTaskUiState.Success("Generated Gemini beat pattern for $styleName")
                }
                .onFailure { err ->
                    _aiTaskState.value = AiTaskUiState.Error("Pattern AI Error: ${err.localizedMessage}")
                }
        }
    }

    // ==================== AI AUTO DJ SCHEDULER ====================

    fun toggleAutoDjSong(songId: Long) {
        _autoDjState.update { st ->
            val q = if (songId in st.queueSongIds) st.queueSongIds - songId else st.queueSongIds + songId
            st.copy(queueSongIds = q)
        }
    }

    fun setAutoDjMode(mode: AutoDjMode) = _autoDjState.update { it.copy(mode = mode) }
    fun setAutoDjTransitionStyle(style: AutoDjTransitionStyle) = _autoDjState.update { it.copy(transitionStyle = style) }

    fun startAutoDj(songs: List<SongEntity>) {
        val queueIds = _autoDjState.value.queueSongIds.ifEmpty { songs.map { it.id } }
        if (queueIds.size < 2) {
            onToast("Add at least 2 tracks to the AI Auto DJ queue")
            return
        }
        val ordered = queueIds.mapNotNull { id -> songs.find { it.id == id } }
        if (ordered.size < 2) return

        autoDjLoopJob?.cancel()
        _autoDjState.update {
            it.copy(
                isActive = true, isPaused = false, queueSongIds = ordered.map { s -> s.id },
                currentQueueIndex = 0, currentDeck = DeckId.A, isTransitioning = false,
                compatibilitySummary = "Active: ${ordered[0].title} -> Next: ${ordered[1].title}"
            )
        }
        onLoadSong(DeckId.A, ordered[0], true)
        onLoadSong(DeckId.B, ordered[1], false)
        audioEngine.setCrossfader(-1f)

        autoDjLoopJob = scope.launch {
            var qIdx = 0
            var activeDeck = DeckId.A
            while (isActive && _autoDjState.value.isActive) {
                delay(400)
                if (_autoDjState.value.isPaused) continue
                val curDeck = if (activeDeck == DeckId.A) audioEngine.deckA.value else audioEngine.deckB.value
                val ratio = if (curDeck.durationMs > 2000L) curDeck.positionMs.toFloat() / curDeck.durationMs else 0f
                val manualSkip = _autoDjState.value.isTransitioning && _autoDjState.value.transitionProgress == 0f

                if (ratio >= 0.78f || manualSkip) {
                    val nextIdx = (qIdx + 1) % ordered.size
                    val nextDeck = if (activeDeck == DeckId.A) DeckId.B else DeckId.A
                    val curTrack = ordered[qIdx]
                    val nextTrack = ordered[nextIdx]

                    val plan = if (_autoDjState.value.mode == AutoDjMode.AI_AUTO) {
                        aiService.planAutoDjTransition(curTrack, nextTrack, _autoDjState.value.mode).getOrNull()
                    } else null

                    val styleToRun = plan?.suggestedStyle ?: _autoDjState.value.transitionStyle
                    _autoDjState.update {
                        it.copy(
                            isTransitioning = true,
                            compatibilitySummary = "${curTrack.title} -> ${nextTrack.title} (Compat ${plan?.compatibilityScore ?: 88}%)",
                            activePlanExplanation = plan?.explanation ?: "Executing ${styleToRun.displayName}"
                        )
                    }

                    audioEngine.setPlaying(nextDeck, true)
                    if (styleToRun == AutoDjTransitionStyle.BEAT_MATCH || styleToRun == AutoDjTransitionStyle.BASS_SWAP) {
                        audioEngine.toggleSync(nextDeck)
                    }

                    val startCf = audioEngine.mixer.value.crossfader
                    val targetCf = if (nextDeck == DeckId.B) 1f else -1f
                    for (step in 1..20) {
                        if (!_autoDjState.value.isActive || _autoDjState.value.isPaused) break
                        val t = step / 20f
                        audioEngine.setCrossfader(startCf + (targetCf - startCf) * t)
                        if (styleToRun == AutoDjTransitionStyle.BASS_SWAP || styleToRun == AutoDjTransitionStyle.EQ_TRANSITION) {
                            audioEngine.updateDeckEq(activeDeck, bassDb = -10f * t)
                            audioEngine.updateDeckEq(nextDeck, bassDb = -10f * (1f - t))
                        } else if (styleToRun == AutoDjTransitionStyle.FILTER_TRANSITION) {
                            audioEngine.updateDeckEq(activeDeck, filterCutoff = 0.75f * t)
                        } else if (styleToRun == AutoDjTransitionStyle.ECHO_OUT && step == 10) {
                            audioEngine.setDeckEffect(activeDeck, DjEffectType.ECHO, wet = 0.65f, param = 0.5f)
                        }
                        _autoDjState.update { it.copy(transitionProgress = t) }
                        delay(180)
                    }
                    audioEngine.updateDeckEq(activeDeck, bassDb = 0f, filterCutoff = 0f)
                    audioEngine.updateDeckEq(nextDeck, bassDb = 0f, filterCutoff = 0f)
                    audioEngine.setDeckEffect(activeDeck, DjEffectType.NONE)
                    audioEngine.setPlaying(activeDeck, false)

                    qIdx = nextIdx
                    activeDeck = nextDeck
                    val upcomingIdx = (qIdx + 1) % ordered.size
                    val oldFreedDeck = if (activeDeck == DeckId.A) DeckId.B else DeckId.A
                    onLoadSong(oldFreedDeck, ordered[upcomingIdx], false)

                    _autoDjState.update {
                        it.copy(currentQueueIndex = qIdx, currentDeck = activeDeck, isTransitioning = false, transitionProgress = 0f)
                    }
                }
            }
        }
    }

    fun triggerAutoDjNextNow() {
        if (_autoDjState.value.isActive && !_autoDjState.value.isTransitioning) {
            _autoDjState.update { it.copy(isTransitioning = true, transitionProgress = 0f) }
        }
    }

    fun toggleAutoDjPause() {
        _autoDjState.update { it.copy(isPaused = !it.isPaused) }
    }

    fun stopAutoDj() {
        autoDjLoopJob?.cancel()
        _autoDjState.update { it.copy(isActive = false, isPaused = false, isTransitioning = false, transitionProgress = 0f) }
    }

    // ==================== BIJOY AI DJ ASSISTANT (CONFIRM BEFORE APPLYING) ====================

    fun sendAssistantPrompt(promptText: String, librarySongs: List<SongEntity>) {
        if (promptText.isBlank()) return
        val userMsg = AiAssistantMessage(id = System.currentTimeMillis(), isUser = true, text = promptText.trim())
        _assistantMessages.update { it + userMsg }
        _assistantLoading.value = true

        activeAiJob?.cancel()
        activeAiJob = scope.launch {
            val summary = librarySongs.take(8).joinToString("; ") { "${it.title} (${it.bpm.toInt()} BPM)" }
            aiService.askDjAssistant(
                prompt = promptText.trim(),
                deckA = audioEngine.deckA.value,
                deckB = audioEngine.deckB.value,
                mixer = audioEngine.mixer.value,
                librarySummary = summary
            ).onSuccess { (reply, actions) ->
                _assistantMessages.update {
                    it + AiAssistantMessage(
                        id = System.currentTimeMillis() + 1,
                        isUser = false,
                        text = reply,
                        proposedActions = actions,
                        actionsApproved = if (actions.isEmpty()) true else null
                    )
                }
            }.onFailure { err ->
                _assistantMessages.update {
                    it + AiAssistantMessage(
                        id = System.currentTimeMillis() + 2,
                        isUser = false,
                        text = "AI Assistant Error: ${err.localizedMessage ?: "Gemini request failed. Check connection/App Check and retry."}",
                        isError = true
                    )
                }
            }
            _assistantLoading.value = false
        }
    }

    fun approveAssistantActions(messageId: Long) {
        val msg = _assistantMessages.value.find { it.id == messageId } ?: return
        msg.proposedActions.forEach { act ->
            val deck = if (act.targetDeck == "B") DeckId.B else DeckId.A
            when (act.actionType) {
                "MATCH_BPM" -> audioEngine.toggleSync(deck)
                "APPLY_EQ" -> {
                    val preset = EqPreset.entries.find {
                        it.displayName.equals(act.paramLabel, true) || it.name.equals(act.paramLabel, true)
                    } ?: EqPreset.BASS_BOOST
                    audioEngine.applyEqPreset(deck, preset)
                }
                "APPLY_FX" -> {
                    val fx = DjEffectType.entries.find {
                        it.name.equals(act.paramLabel, true) || it.displayName.equals(act.paramLabel, true)
                    } ?: DjEffectType.ECHO
                    val wet = act.paramValue.takeIf { it > 0.05f }?.coerceIn(0.1f, 1f) ?: 0.55f
                    if (act.targetDeck == "MASTER") audioEngine.setMasterEffect(fx, wet = wet)
                    else audioEngine.setDeckEffect(deck, fx, wet = wet)
                }
                "REMOVE_VOCALS" -> audioEngine.updateDeckEq(deck, vocalDb = -12f)
                "SWAP_BASS" -> {
                    audioEngine.updateDeckEq(DeckId.A, bassDb = -10f)
                    audioEngine.updateDeckEq(DeckId.B, bassDb = 4f)
                }
                "OPEN_SAD_TO_DJ" -> onNavigate(AppSection.SAD_TO_DJ)
                "START_AUTO_FADE" -> audioEngine.triggerAutoFade()
                "START_RECORDING" -> onStartRecording("BIJOY_AI_Mix", false, RecordSource.MASTER)
            }
        }
        _assistantMessages.update { list -> list.map { if (it.id == messageId) it.copy(actionsApproved = true) else it } }
        onToast("Applied ${msg.proposedActions.size} approved AI action(s)")
    }

    fun declineAssistantActions(messageId: Long) {
        _assistantMessages.update { list -> list.map { if (it.id == messageId) it.copy(actionsApproved = false) else it } }
        onToast("AI action plan declined")
    }

    fun cancelActiveAiJob() {
        activeAiJob?.cancel()
        _aiTaskState.value = AiTaskUiState.Idle
        _stemTaskState.value = AiTaskUiState.Idle
        _assistantLoading.value = false
    }

    fun release() {
        activeAiJob?.cancel()
        autoDjLoopJob?.cancel()
    }
}
