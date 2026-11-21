package com.example.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

    private val _activeSfxPads = MutableStateFlow<Set<Int>>(emptySet())
    val sfxPads: StateFlow<List<SfxPadUi>> = combine(
        repository.samplerAssignments,
        _activeSfxPads
    ) { assignments, active ->
        assignments.map { a ->
            SfxPadUi(
                index = a.padIndex,
                label = a.label,
                colorHex = a.colorHex,
                synthType = a.synthType,
                customAudioUri = a.customAudioUri,
                volume = a.volume,
                isPlaying = a.padIndex in active
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
            val cuePoints = (0..3).map { slot ->
                val found = savedCues.find { it.slotIndex == slot }
                HotCuePoint(slot, found?.positionMs, found?.label ?: "CUE ${slot + 1}")
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

    fun onHotCuePress(deckId: DeckId, slotIndex: Int) {
        val deck = if (deckId == DeckId.A) deckA.value else deckB.value
        val posMs = audioEngine.triggerHotCue(deckId, slotIndex)
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

    fun updateManualBpm(deckId: DeckId, newBpm: Float) {
        audioEngine.setManualBpm(deckId, newBpm)
        val songId = (if (deckId == DeckId.A) deckA.value else deckB.value).songId
        if (songId != null) {
            viewModelScope.launch { repository.updateSongBpm(songId, newBpm) }
        }
    }

    // ==================== SFX SAMPLER ====================

    fun triggerSfxPad(pad: SfxPadUi) {
        audioEngine.sfxEngine.triggerPad(pad.index, pad.volume)
        _activeSfxPads.value = audioEngine.sfxEngine.activePadIndices()
    }

    fun stopAllSfx() {
        audioEngine.sfxEngine.stopAll()
        _activeSfxPads.value = emptySet()
    }

    fun updateSfxPadConfig(padIndex: Int, newLabel: String, newVolume: Float) {
        viewModelScope.launch {
            val current = repository.db.presetSettingsLayoutDao().getSamplerAssignmentsOnce()
                .find { it.padIndex == padIndex } ?: return@launch
            repository.updateSamplerPad(current.copy(label = newLabel.trim(), volume = newVolume))
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

    // ==================== RECORDING STUDIO ====================

    fun startRecording(customTitle: String? = null, includeMic: Boolean = false) {
        val title = recordingEngine.startRecording(customTitle, includeMic)
        postToast("Recording master mix: $title")
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

    fun updateSettings(transform: (UserSettingsEntity) -> UserSettingsEntity) {
        viewModelScope.launch {
            val next = transform(userSettings.value)
            repository.saveSettings(next)
            audioEngine.setJogSensitivity(next.jogSensitivity)
            val curve = runCatching { CrossfaderCurve.valueOf(next.crossfaderCurve) }
                .getOrDefault(CrossfaderCurve.CONSTANT_POWER)
            audioEngine.setCrossfaderCurve(curve)
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
        audioEngine.stopEngine()
    }
}
