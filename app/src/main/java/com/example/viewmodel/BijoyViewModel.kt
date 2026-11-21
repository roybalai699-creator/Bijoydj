package com.example.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.BijoyAiService
import com.example.audio.*
import com.example.database.*
import com.example.sources.MusicSourceManager
import com.example.sources.SourceConnectionState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class BijoyViewModel(application: Application) : AndroidViewModel(application) {

    private val db = BijoyDatabase.getInstance(application)
    val repository = BijoyRepository(application, db)
    val recordingEngine = RecordingEngine(application)
    val audioEngine = BijoyAudioEngine(application, recordingEngine)
    val sourceManager = MusicSourceManager(application)
    val backupManager = BackupManager(application, db)
    val aiService = BijoyAiService()

    val aiStudio by lazy {
        AiStudioDelegate(
            app = getApplication(),
            scope = viewModelScope,
            repository = repository,
            audioEngine = audioEngine,
            aiService = aiService,
            onToast = { postToast(it) },
            onNavigate = { navigateTo(it) },
            onLoadSong = { deckId, song, autoPlay -> loadSongToDeck(deckId, song, autoPlay) },
            onStartRecording = { title, mic, src -> startRecording(title, mic, src) }
        )
    }

    // Audio & Engine States
    val deckA: StateFlow<DeckState> = audioEngine.deckA
    val deckB: StateFlow<DeckState> = audioEngine.deckB
    val mixer: StateFlow<MixerState> = audioEngine.mixer
    val recordingState: StateFlow<RecordingState> = recordingEngine.state

    // Music Sources
    val spotifyState: StateFlow<SourceConnectionState> = sourceManager.spotifyState
    val soundCloudState: StateFlow<SourceConnectionState> = sourceManager.soundCloudState

    // Room-backed Reactive Flows
    val allSongs: StateFlow<List<SongEntity>> = repository.allSongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allFolders: StateFlow<List<String>> = repository.allFolders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allArtists: StateFlow<List<ArtistEntity>> = repository.allArtists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allAlbums: StateFlow<List<AlbumEntity>> = repository.allAlbums
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allPlaylists: StateFlow<List<PlaylistEntity>> = repository.allPlaylists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val favoriteIds: StateFlow<Set<Long>> = repository.favoriteIds
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    val favoriteSongs: StateFlow<List<SongEntity>> = repository.favoriteSongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recentHistory: StateFlow<List<PlaybackHistoryEntity>> = repository.recentHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allRecordings: StateFlow<List<RecordingEntity>> = repository.allRecordings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allPresets: StateFlow<List<DjPresetEntity>> = repository.allPresets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val userSettings: StateFlow<UserSettingsEntity> = repository.settings
        .map { it ?: UserSettingsEntity() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserSettingsEntity())
    val savedLayouts: StateFlow<List<LayoutPreferenceEntity>> = repository.allLayouts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val activeLayout: StateFlow<LayoutPreferenceEntity> = repository.activeLayout
        .map { it ?: LayoutPreferenceEntity(id = 1, profileName = "Standard Studio", isActive = true) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            LayoutPreferenceEntity(id = 1, profileName = "Standard Studio", isActive = true)
        )

    val savedLoops: StateFlow<List<SavedLoopEntity>> = repository.savedLoops
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeSfxPads = MutableStateFlow<Set<Int>>(emptySet())
    private val _loopingSfxPads = MutableStateFlow<Set<Int>>(emptySet())
    private val _padPitches = MutableStateFlow<Map<Int, Float>>(emptyMap())
    val sfxPads: StateFlow<List<SfxPadUi>> = combine(
        repository.samplerAssignments,
        _activeSfxPads,
        _loopingSfxPads,
        _padPitches
    ) { assignments, active, looping, pitches ->
        assignments.map { a ->
            SfxPadUi(
                index = a.padIndex,
                label = a.label,
                colorHex = a.colorHex,
                synthType = a.synthType,
                category = SfxSamplerEngine.categoryForType(a.synthType),
                customAudioUri = a.customAudioUri,
                volume = a.volume,
                pitchRatio = pitches[a.padIndex] ?: 1.0f,
                isPlaying = a.padIndex in active,
                isLooping = a.padIndex in looping
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Navigation & UI Overlays
    private val _currentSection = MutableStateFlow(AppSection.DJ_MIXER)
    val currentSection: StateFlow<AppSection> = _currentSection.asStateFlow()

    private val _showLayoutCustomizer = MutableStateFlow(false)
    val showLayoutCustomizer: StateFlow<Boolean> = _showLayoutCustomizer.asStateFlow()

    private val _statusToast = MutableStateFlow<String?>(null)
    val statusToast: StateFlow<String?> = _statusToast.asStateFlow()

    private val _isBootReady = MutableStateFlow(false)
    val isBootReady: StateFlow<Boolean> = _isBootReady.asStateFlow()

    private var previousLayoutBeforeFullscreen: LayoutPreferenceEntity? = null

    init {
        viewModelScope.launch {
            repository.ensureSeedData()
            audioEngine.startEngine()
            // Auto-load first two studio tracks into Deck A and Deck B so user can mix right away
            val songs = db.songDao().getAllSongsOnce()
            if (songs.isNotEmpty()) {
                loadSongToDeck(DeckId.A, songs[0], autoPlay = false)
            }
            if (songs.size >= 2) {
                loadSongToDeck(DeckId.B, songs[1], autoPlay = false)
            }
            _isBootReady.value = true
        }
    }

    fun navigateTo(section: AppSection) {
        _currentSection.value = section
    }

    fun openLayoutCustomizer(open: Boolean) {
        _showLayoutCustomizer.value = open
    }

    fun clearToast() {
        _statusToast.value = null
    }

    fun postToast(msg: String) {
        _statusToast.value = msg
    }

    // ==================== DECK LOADING & PLAYBACK ====================

    fun loadSongToDeck(deckId: DeckId, song: SongEntity, autoPlay: Boolean = false) {
        viewModelScope.launch {
            val savedCues = repository.getHotCues(song.id)
            val cuePoints = (0..7).map { slot ->
                val found = savedCues.find { it.slotIndex == slot }
                HotCuePoint(slot, found?.positionMs, found?.label ?: "HC ${slot + 1}")
            }

            val decodedResult = if (song.isBuiltInSynth) {
                Result.success(AudioDecoder.generateBuiltInDjTrack(song.synthPresetId, song.bpm))
            } else {
                AudioDecoder.decodeUriToPcm(
                    getApplication(),
                    Uri.parse(song.uriString),
                    song.localCopyPath
                )
            }

            decodedResult.onSuccess { decoded ->
                audioEngine.loadDecodedTrack(
                    deckId = deckId,
                    songId = song.id,
                    title = song.title,
                    artist = song.artist,
                    musicalKey = song.musicalKey,
                    artworkResName = song.artworkResName,
                    decoded = decoded,
                    savedCues = cuePoints
                )
                if (!song.isBuiltInSynth && decoded.detectedBpm > 60f) {
                    repository.updateSongBpm(song.id, decoded.detectedBpm)
                }
                repository.recordPlaybackHistory(song, if (deckId == DeckId.A) "Deck A" else "Deck B")
                if (autoPlay) {
                    audioEngine.setPlaying(deckId, true)
                }
                if (userSettings.value.autoSync) {
                    audioEngine.toggleSync(deckId)
                }
            }.onFailure { err ->
                postToast("Unable to decode '${song.title}': ${err.localizedMessage ?: "file unavailable"}")
            }
        }
    }

    fun onHotCuePress(deckId: DeckId, slotIndex: Int, loopAfterJump: Boolean = false) {
        val deck = if (deckId == DeckId.A) deckA.value else deckB.value
        val posMs = audioEngine.triggerHotCue(deckId, slotIndex, loopAfterJump)
        val songId = deck.songId
        if (songId != null && posMs != null) {
            viewModelScope.launch {
                repository.saveHotCue(songId, slotIndex, posMs)
            }
        }
    }

    fun onHotCueClear(deckId: DeckId, slotIndex: Int) {
        val deck = if (deckId == DeckId.A) deckA.value else deckB.value
        audioEngine.clearHotCue(deckId, slotIndex)
        deck.songId?.let { id ->
            viewModelScope.launch { repository.deleteHotCue(id, slotIndex) }
        }
    }

    fun onPerformancePadPress(deckId: DeckId, padIndex: Int) {
        val deck = if (deckId == DeckId.A) deckA.value else deckB.value
        when (deck.padMode) {
            PerformancePadMode.HOT_CUE -> onHotCuePress(deckId, padIndex, loopAfterJump = false)
            PerformancePadMode.SAMPLER -> {
                val totalPads = sfxPads.value.size.coerceAtLeast(16)
                val offset = (mixer.value.samplerBankIndex * 4 + padIndex) % totalPads
                val pad = sfxPads.value.getOrNull(offset)
                if (pad != null) triggerSfxPad(pad)
            }
            PerformancePadMode.ROLL -> {
                val rollBeats = listOf(0.5f, 1f, 2f, 4f, 8f, 16f, 0.25f, 0.125f)
                audioEngine.triggerRollPad(deckId, rollBeats.getOrElse(padIndex) { 1f })
            }
            PerformancePadMode.SLICER -> audioEngine.triggerSlicerPad(deckId, padIndex)
            PerformancePadMode.LOOP -> {
                val loopBeats = listOf(0.5f, 1f, 2f, 4f, 8f, 16f, 0.25f, 32f)
                audioEngine.toggleAutoLoop(deckId, loopBeats.getOrElse(padIndex) { 4f })
            }
            PerformancePadMode.CUE_LOOP -> onHotCuePress(deckId, padIndex, loopAfterJump = true)
        }
    }

    fun onPerformancePadLongPress(deckId: DeckId, padIndex: Int) {
        val deck = if (deckId == DeckId.A) deckA.value else deckB.value
        when (deck.padMode) {
            PerformancePadMode.HOT_CUE, PerformancePadMode.CUE_LOOP -> {
                onHotCueClear(deckId, padIndex)
                postToast("Cleared Cue ${padIndex + 1} on Deck ${deckId.name}")
            }
            PerformancePadMode.SAMPLER -> {
                val totalPads = sfxPads.value.size.coerceAtLeast(16)
                val offset = (mixer.value.samplerBankIndex * 4 + padIndex) % totalPads
                audioEngine.sfxEngine.stopPad(offset)
                _activeSfxPads.value = audioEngine.sfxEngine.activePadIndices()
            }
            PerformancePadMode.LOOP -> {
                val songId = deck.songId ?: return
                val inMs = deck.loopInMs ?: return
                val outMs = deck.loopOutMs ?: return
                viewModelScope.launch {
                    repository.saveLoop(songId, inMs, outMs, deck.autoLoopBeats)
                    postToast("Saved loop to track memory")
                }
            }
            else -> audioEngine.exitLoop(deckId)
        }
    }

    fun saveActiveLoop(deckId: DeckId) {
        val deck = if (deckId == DeckId.A) deckA.value else deckB.value
        val songId = deck.songId ?: return
        val inMs = deck.loopInMs ?: deck.positionMs
        val outMs = deck.loopOutMs ?: (inMs + ((60000f / deck.effectiveBpm.coerceAtLeast(60f)) * deck.autoLoopBeats).toLong())
        viewModelScope.launch {
            repository.saveLoop(songId, inMs, outMs, deck.autoLoopBeats)
            postToast("Saved ${deck.autoLoopBeats}-beat loop for ${deck.title}")
        }
    }

    fun recallSavedLoop(deckId: DeckId, loop: SavedLoopEntity) {
        val deck = if (deckId == DeckId.A) deckA.value else deckB.value
        val dur = deck.durationMs.coerceAtLeast(1L)
        audioEngine.seekToRatio(deckId, (loop.startMs.toFloat() / dur).coerceIn(0f, 0.99f))
        audioEngine.toggleAutoLoop(deckId, loop.beatLength)
    }

    fun updateManualBpm(deckId: DeckId, newBpm: Float) {
        audioEngine.setManualBpm(deckId, newBpm)
        val songId = (if (deckId == DeckId.A) deckA.value else deckB.value).songId
        if (songId != null) {
            viewModelScope.launch { repository.updateSongBpm(songId, newBpm) }
        }
    }

    fun loadAdjacentTrack(deckId: DeckId, delta: Int) {
        val songs = allSongs.value
        if (songs.isEmpty()) return
        val currentId = (if (deckId == DeckId.A) deckA.value else deckB.value).songId
        val curIdx = songs.indexOfFirst { it.id == currentId }.let { if (it < 0) 0 else it }
        val nextIdx = (curIdx + delta + songs.size) % songs.size
        val target = songs[nextIdx]
        val wasPlaying = (if (deckId == DeckId.A) deckA.value else deckB.value).isPlaying
        loadSongToDeck(deckId, target, autoPlay = wasPlaying)
    }

    private val _sampleMasterVolume = MutableStateFlow(1.0f)
    val sampleMasterVolume: StateFlow<Float> = _sampleMasterVolume.asStateFlow()

    fun setSampleMasterVolume(vol: Float) {
        val clamped = vol.coerceIn(0f, 1.5f)
        _sampleMasterVolume.value = clamped
        audioEngine.sfxEngine.masterSampleVolume = clamped
    }

    // ==================== SFX SAMPLER ====================

    fun triggerSfxPad(pad: SfxPadUi) {
        audioEngine.sfxEngine.triggerPad(pad.index, pad.volume, pad.isLooping, pad.pitchRatio)
        _activeSfxPads.value = audioEngine.sfxEngine.activePadIndices()
    }

    fun setSfxPadPitch(padIndex: Int, pitchRatio: Float) {
        val clamped = pitchRatio.coerceIn(0.5f, 2.0f)
        audioEngine.sfxEngine.setPadPitch(padIndex, clamped)
        _padPitches.update { it + (padIndex to clamped) }
    }

    fun stopSfxPad(padIndex: Int) {
        audioEngine.sfxEngine.stopPad(padIndex)
        _activeSfxPads.value = audioEngine.sfxEngine.activePadIndices()
    }

    fun toggleSfxPadLoop(padIndex: Int) {
        val isLoop = audioEngine.sfxEngine.togglePadLoop(padIndex)
        _loopingSfxPads.value = audioEngine.sfxEngine.loopingPads
        postToast(if (isLoop) "Pad ${padIndex + 1} loop enabled" else "Pad ${padIndex + 1} loop disabled")
    }

    fun stopAllSfx() {
        audioEngine.sfxEngine.stopAll()
        _activeSfxPads.value = emptySet()
    }

    fun switchSamplerBank(bankIdx: Int) {
        val clamped = bankIdx.coerceIn(0, 3)
        audioEngine.setSamplerBank(clamped)
        postToast("Sampler Bank ${('A' + clamped)} selected")
    }

    fun updateSfxPadConfig(padIndex: Int, newLabel: String, newVolume: Float, pitchRatio: Float = 1.0f) {
        setSfxPadPitch(padIndex, pitchRatio)
        viewModelScope.launch {
            val current = repository.db.presetSettingsLayoutDao().getSamplerAssignmentsOnce()
                .find { it.padIndex == padIndex } ?: return@launch
            repository.updateSamplerPad(current.copy(label = newLabel.trim(), volume = newVolume))
        }
    }

    fun saveCurrentEqPreset(name: String, deckId: DeckId) {
        val deck = if (deckId == DeckId.A) deckA.value else deckB.value
        viewModelScope.launch {
            repository.saveUserEqPreset(name, deck.bassDb, deck.midDb, deck.trebleDb, deck.filterCutoff)
            postToast("Saved custom EQ preset: $name")
        }
    }

    fun applyCustomDjPreset(deckId: DeckId, preset: DjPresetEntity) {
        audioEngine.applyCustomEqValues(deckId, preset.bassDb, preset.midDb, preset.trebleDb)
        postToast("Applied preset: ${preset.name}")
    }

    fun deleteCustomDjPreset(preset: DjPresetEntity) {
        viewModelScope.launch {
            repository.deleteUserPreset(preset)
            postToast("Deleted preset: ${preset.name}")
        }
    }

    fun assignCustomSampleToPad(padIndex: Int, uri: Uri) {
        viewModelScope.launch {
            AudioDecoder.decodeUriToPcm(getApplication(), uri).onSuccess { decoded ->
                audioEngine.sfxEngine.setCustomSample(padIndex, decoded.pcmStereo)
                val current = repository.db.presetSettingsLayoutDao().getSamplerAssignmentsOnce()
                    .find { it.padIndex == padIndex }
                if (current != null) {
                    repository.updateSamplerPad(current.copy(customAudioUri = uri.toString()))
                }
                postToast("Custom sample assigned to Pad ${padIndex + 1}")
            }.onFailure {
                postToast("Could not decode custom sample")
            }
        }
    }

    fun removeCustomSampleFromPad(padIndex: Int) {
        viewModelScope.launch {
            audioEngine.sfxEngine.clearCustomSample(padIndex)
            val current = repository.db.presetSettingsLayoutDao().getSamplerAssignmentsOnce()
                .find { it.padIndex == padIndex }
            if (current != null) {
                repository.updateSamplerPad(current.copy(customAudioUri = null))
            }
            postToast("Restored default studio sound for Pad ${padIndex + 1}")
        }
    }

    // ==================== RECORDING STUDIO ====================

    fun setRecordSource(source: RecordSource) {
        recordingEngine.setRecordSource(source)
    }

    fun startRecording(
        customTitle: String? = null,
        includeMic: Boolean = false,
        source: RecordSource = recordingState.value.source
    ) {
        val title = recordingEngine.startRecording(customTitle, includeMic, source)
        postToast("Recording (${source.displayName}): $title")
    }

    fun pauseRecording() = recordingEngine.pauseRecording()
    fun resumeRecording() = recordingEngine.resumeRecording()

    fun stopAndSaveRecording() {
        val completed = recordingEngine.stopRecording() ?: return
        viewModelScope.launch {
            repository.saveRecording(
                RecordingEntity(
                    title = completed.title,
                    filePath = completed.filePath,
                    format = completed.format,
                    durationMs = completed.durationMs,
                    fileSizeBytes = completed.fileSizeBytes
                )
            )
            postToast("Saved WAV recording: ${completed.title}")
        }
    }

    fun renameRecording(recording: RecordingEntity, newTitle: String) {
        if (newTitle.isBlank()) return
        viewModelScope.launch {
            val clean = newTitle.trim()
            val renamedFile = recordingEngine.renameRecordingFile(recording.filePath, clean).getOrNull()
            if (renamedFile != null) {
                repository.db.recordingDao().insertRecording(
                    recording.copy(title = clean, filePath = renamedFile.absolutePath)
                )
            } else {
                repository.renameRecording(recording.id, clean)
            }
            postToast("Renamed recording to $clean")
        }
    }

    fun exportRecordingAsM4a(recording: RecordingEntity) {
        viewModelScope.launch {
            postToast("Encoding AAC/M4A...")
            recordingEngine.exportWavAsM4a(recording.filePath)
                .onSuccess { m4aFile ->
                    repository.saveRecording(
                        RecordingEntity(
                            title = "${recording.title} (AAC)",
                            filePath = m4aFile.absolutePath,
                            format = "M4A",
                            durationMs = recording.durationMs,
                            fileSizeBytes = m4aFile.length()
                        )
                    )
                    postToast("Exported M4A: ${m4aFile.name}")
                }
                .onFailure { postToast("M4A encode unavailable: ${it.localizedMessage}") }
        }
    }

    fun loadRecordingToDeck(deckId: DeckId, recording: RecordingEntity) {
        val song = SongEntity(
            title = recording.title,
            artist = "BIJOY DJ Recording",
            album = "Recorded Mixes",
            folderName = "Recordings",
            uriString = Uri.fromFile(java.io.File(recording.filePath)).toString(),
            localCopyPath = recording.filePath,
            durationMs = recording.durationMs,
            bpm = 128f,
            format = recording.format,
            sourceType = "RECORDING",
            isBuiltInSynth = false
        )
        loadSongToDeck(deckId, song, autoPlay = true)
        navigateTo(AppSection.DJ_MIXER)
    }

    fun exportRecordingToUri(sourcePath: String, destUri: Uri) {
        viewModelScope.launch {
            recordingEngine.exportRecordingToUri(sourcePath, destUri)
                .onSuccess { postToast("Recording exported successfully") }
                .onFailure { postToast("Export failed: ${it.localizedMessage}") }
        }
    }

    // ==================== LAYOUT CUSTOMIZATION ====================

    fun setLayoutMode(mode: LayoutMode) {
        viewModelScope.launch {
            val current = activeLayout.value
            val updated = when (mode) {
                LayoutMode.STANDARD -> current.copy(
                    layoutMode = "STANDARD",
                    showLeftSidebar = true,
                    showRightSidebar = true,
                    showMusicLibrary = true,
                    showFolders = true,
                    showPlaylists = true,
                    showFavorites = true,
                    showHistory = true,
                    showSoundEffects = true,
                    showFxPanel = true,
                    showSamplerPanel = true,
                    showRecordingPanel = true,
                    showAdditionalTools = true,
                    sidebarWidthDp = 76
                )
                LayoutMode.COMPACT -> current.copy(
                    layoutMode = "COMPACT",
                    showLeftSidebar = true,
                    showRightSidebar = true,
                    sidebarWidthDp = 56
                )
                LayoutMode.FULLSCREEN -> {
                    if (current.layoutMode != "FULLSCREEN") {
                        previousLayoutBeforeFullscreen = current
                    }
                    current.copy(
                        layoutMode = "FULLSCREEN",
                        showLeftSidebar = false,
                        showRightSidebar = false,
                        showMusicLibrary = false,
                        showFolders = false,
                        showPlaylists = false,
                        showFavorites = false,
                        showHistory = false,
                        showSoundEffects = false,
                        showFxPanel = false,
                        showSamplerPanel = false,
                        showRecordingPanel = false,
                        showAdditionalTools = false
                    )
                }
            }
            repository.saveActiveLayout(updated)
        }
    }

    fun toggleExpandMixer() {
        val current = activeLayout.value
        if (current.layoutMode == "FULLSCREEN") {
            restorePreviousLayout()
        } else {
            setLayoutMode(LayoutMode.FULLSCREEN)
        }
    }

    fun restorePreviousLayout() {
        viewModelScope.launch {
            val prev = previousLayoutBeforeFullscreen ?: activeLayout.value.copy(
                layoutMode = "STANDARD",
                showLeftSidebar = true,
                showRightSidebar = true,
                showMusicLibrary = true,
                showFolders = true,
                showPlaylists = true,
                showFavorites = true,
                showHistory = true,
                showSoundEffects = true,
                showFxPanel = true,
                showSamplerPanel = true,
                showRecordingPanel = true,
                showAdditionalTools = true,
                sidebarWidthDp = 76
            )
            repository.saveActiveLayout(prev)
        }
    }

    fun updateActiveLayout(transform: (LayoutPreferenceEntity) -> LayoutPreferenceEntity) {
        viewModelScope.launch {
            repository.saveActiveLayout(transform(activeLayout.value))
        }
    }

    fun saveCustomLayoutProfile(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.saveNewCustomLayout(name, activeLayout.value)
            postToast("Saved layout profile: $name")
        }
    }

    fun applySavedLayout(layout: LayoutPreferenceEntity) {
        viewModelScope.launch {
            repository.saveActiveLayout(layout)
            postToast("Activated layout: ${layout.profileName}")
        }
    }

    fun renameSavedLayout(id: Long, newName: String) {
        viewModelScope.launch { repository.renameLayout(id, newName) }
    }

    fun deleteSavedLayout(id: Long) {
        viewModelScope.launch { repository.deleteLayout(id) }
    }

    // ==================== LIBRARY, PLAYLISTS, SETTINGS, BACKUP ====================

    fun importAudioFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val copyPrivate = userSettings.value.copyImportedToPrivateStorage
            val imported = repository.importLocalAudioUris(uris, copyPrivate)
            postToast("Imported ${imported.size} audio file(s) to BIJOY DJ Library")
        }
    }

    private val _customFolders = MutableStateFlow<List<String>>(emptyList())
    val customFolders: StateFlow<List<String>> = _customFolders.asStateFlow()

    fun createFolder(name: String) {
        val clean = name.trim()
        if (clean.isNotBlank() && clean !in _customFolders.value) {
            _customFolders.value = _customFolders.value + clean
            postToast("Created folder: $clean")
        }
    }

    fun renameFolder(oldName: String, newName: String) {
        val clean = newName.trim()
        if (clean.isBlank()) return
        _customFolders.value = _customFolders.value.map { if (it == oldName) clean else it }
        viewModelScope.launch {
            repository.renameFolder(oldName, clean)
            postToast("Renamed folder to $clean")
        }
    }

    fun deleteFolder(name: String) {
        _customFolders.value = _customFolders.value.filterNot { it == name }
        viewModelScope.launch {
            repository.renameFolder(name, "Local Music")
            postToast("Folder removed; tracks moved to Local Music")
        }
    }

    fun updateSettings(transform: (UserSettingsEntity) -> UserSettingsEntity) {
        viewModelScope.launch {
            val next = transform(userSettings.value)
            repository.saveSettings(next)
            audioEngine.setJogSensitivity(next.jogSensitivity)
            audioEngine.setBufferFrames(next.bufferSizeFrames)
            val curve = runCatching { CrossfaderCurve.valueOf(next.crossfaderCurve) }
                .getOrDefault(CrossfaderCurve.CONSTANT_POWER)
            audioEngine.setCrossfaderCurve(curve)
        }
    }

    fun restoreDefaultSettings() {
        viewModelScope.launch {
            val defaults = UserSettingsEntity()
            repository.saveSettings(defaults)
            audioEngine.setJogSensitivity(defaults.jogSensitivity)
            audioEngine.setBufferFrames(defaults.bufferSizeFrames)
            audioEngine.setCrossfaderCurve(CrossfaderCurve.CONSTANT_POWER)
            postToast("Restored default BIJOY DJ settings")
        }
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            backupManager.exportBackupToUri(uri)
                .onSuccess { postToast("Backup complete: $it") }
                .onFailure { postToast("Backup failed: ${it.localizedMessage}") }
        }
    }

    fun restoreBackup(uri: Uri) {
        viewModelScope.launch {
            backupManager.restoreBackupFromUri(uri)
                .onSuccess { postToast(it) }
                .onFailure { postToast("Restore failed: ${it.localizedMessage}") }
        }
    }

    fun handleOAuthIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "bijoydj" && data.host == "callback") {
            viewModelScope.launch {
                sourceManager.handleOAuthRedirect(data)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        aiStudio.release()
        audioEngine.stopEngine()
    }
}
