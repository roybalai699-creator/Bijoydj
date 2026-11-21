package com.example.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.example.audio.AudioDecoder
import com.example.audio.EqPreset
import com.example.database.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class BijoyRepository(
    private val context: Context,
    val db: BijoyDatabase
) {
    val allSongs: Flow<List<SongEntity>> = db.songDao().getAllSongs()
    val allFolders: Flow<List<String>> = db.songDao().getAllFolders()
    val allArtists: Flow<List<ArtistEntity>> = db.artistAlbumDao().getAllArtists()
    val allAlbums: Flow<List<AlbumEntity>> = db.artistAlbumDao().getAllAlbums()
    val allPlaylists: Flow<List<PlaylistEntity>> = db.playlistDao().getAllPlaylists()
    val favoriteIds: Flow<List<Long>> = db.favoriteDao().getFavoriteSongIds()
    val favoriteSongs: Flow<List<SongEntity>> = db.favoriteDao().getFavoriteSongs()
    val recentHistory: Flow<List<PlaybackHistoryEntity>> = db.historyDao().getRecentHistory()
    val allRecordings: Flow<List<RecordingEntity>> = db.recordingDao().getAllRecordings()
    val allPresets: Flow<List<DjPresetEntity>> = db.presetSettingsLayoutDao().getAllPresets()
    val settings: Flow<UserSettingsEntity?> = db.presetSettingsLayoutDao().observeSettings()
    val allLayouts: Flow<List<LayoutPreferenceEntity>> = db.presetSettingsLayoutDao().getAllLayouts()
    val activeLayout: Flow<LayoutPreferenceEntity?> = db.presetSettingsLayoutDao().observeActiveLayout()
    val samplerAssignments: Flow<List<SamplerAssignmentEntity>> = db.presetSettingsLayoutDao().observeSamplerAssignments()
    val importedRefs: Flow<List<ImportedAudioRefEntity>> = db.presetSettingsLayoutDao().observeImportedRefs()

    suspend fun ensureSeedData() = withContext(Dispatchers.IO) {
        if (db.songDao().getSongCount() == 0) {
            val builtInTracks = listOf(
                SongEntity(
                    title = "DJ Remix - Party Mix 2026",
                    artist = "DJ Bijoy Official",
                    album = "BIJOY Club Sessions",
                    folderName = "Studio Grooves",
                    uriString = "builtin://synth/0",
                    durationMs = 30000L,
                    bpm = 128f,
                    musicalKey = "Am",
                    artworkResName = "deck_a",
                    format = "PCM",
                    sourceType = "BUILTIN",
                    isBuiltInSynth = true,
                    synthPresetId = 0
                ),
                SongEntity(
                    title = "Bangla DJ Song - Nonstop",
                    artist = "DJ Bijoy Mix",
                    album = "Dhaka Bass Nights",
                    folderName = "Studio Grooves",
                    uriString = "builtin://synth/1",
                    durationMs = 28444L,
                    bpm = 135f,
                    musicalKey = "Bm",
                    artworkResName = "deck_b",
                    format = "PCM",
                    sourceType = "BUILTIN",
                    isBuiltInSynth = true,
                    synthPresetId = 1
                ),
                SongEntity(
                    title = "Midnight Electro Pulse",
                    artist = "BIJOY Sound Lab",
                    album = "Neon Underground",
                    folderName = "Studio Grooves",
                    uriString = "builtin://synth/2",
                    durationMs = 30967L,
                    bpm = 124f,
                    musicalKey = "Fm",
                    artworkResName = "deck_a",
                    format = "PCM",
                    sourceType = "BUILTIN",
                    isBuiltInSynth = true,
                    synthPresetId = 2
                ),
                SongEntity(
                    title = "Festival Drop Anthem",
                    artist = "DJ Bijoy Official",
                    album = "Mainstage 2026",
                    folderName = "Studio Grooves",
                    uriString = "builtin://synth/3",
                    durationMs = 29538L,
                    bpm = 130f,
                    musicalKey = "C",
                    artworkResName = "deck_b",
                    format = "PCM",
                    sourceType = "BUILTIN",
                    isBuiltInSynth = true,
                    synthPresetId = 3
                )
            )
            builtInTracks.forEach { song ->
                db.songDao().insertSong(song)
                db.artistAlbumDao().upsertArtist(ArtistEntity(name = song.artist))
                db.artistAlbumDao().upsertAlbum(AlbumEntity(title = song.album, artistName = song.artist))
            }
        }

        if (db.presetSettingsLayoutDao().getSettingsOnce() == null) {
            db.presetSettingsLayoutDao().saveSettings(UserSettingsEntity())
        }

        if (db.presetSettingsLayoutDao().getAllLayoutsOnce().isEmpty()) {
            db.presetSettingsLayoutDao().upsertLayout(
                LayoutPreferenceEntity(
                    profileName = "Standard Studio",
                    isActive = true,
                    layoutMode = "STANDARD",
                    showLeftSidebar = true,
                    showRightSidebar = true,
                    sidebarWidthDp = 76
                )
            )
            db.presetSettingsLayoutDao().upsertLayout(
                LayoutPreferenceEntity(
                    profileName = "Compact Booth",
                    isActive = false,
                    layoutMode = "COMPACT",
                    showLeftSidebar = true,
                    showRightSidebar = true,
                    sidebarWidthDp = 56
                )
            )
            db.presetSettingsLayoutDao().upsertLayout(
                LayoutPreferenceEntity(
                    profileName = "Fullscreen Mixer",
                    isActive = false,
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
                    showAdditionalTools = false,
                    sidebarWidthDp = 0
                )
            )
        }

        val existingPads = db.presetSettingsLayoutDao().getSamplerAssignmentsOnce()
        val existingIndices = existingPads.map { it.padIndex }.toSet()
        val defaults16 = listOf(
            SamplerAssignmentEntity(0, "Airhorn", "#00E676", null, 0.92f, "AIRHORN"),
            SamplerAssignmentEntity(1, "Gunshot", "#FF6D00", null, 0.90f, "GUNSHOT"),
            SamplerAssignmentEntity(2, "Laser", "#00E5FF", null, 0.88f, "LASER"),
            SamplerAssignmentEntity(3, "Police Siren", "#D500F9", null, 0.85f, "POLICE_SIREN"),
            SamplerAssignmentEntity(4, "Punch", "#76FF03", null, 0.92f, "PUNCH"),
            SamplerAssignmentEntity(5, "Siren", "#FF9100", null, 0.86f, "SIREN"),
            SamplerAssignmentEntity(6, "Scratch", "#00B0FF", null, 0.90f, "SCRATCH"),
            SamplerAssignmentEntity(7, "Crowd", "#FF4081", null, 0.85f, "CROWD"),
            SamplerAssignmentEntity(8, "Drum", "#2979FF", null, 0.95f, "DRUM"),
            SamplerAssignmentEntity(9, "Bass Drop", "#E040FB", null, 0.95f, "BASS_DROP"),
            SamplerAssignmentEntity(10, "Clap", "#FFC400", null, 0.88f, "CLAP"),
            SamplerAssignmentEntity(11, "DJ Voice", "#FF1744", null, 0.90f, "DJ_VOICE"),
            SamplerAssignmentEntity(12, "Snare Snap", "#FFEA00", null, 0.90f, "SNARE_SNAP"),
            SamplerAssignmentEntity(13, "Riser Sweep", "#18FFFF", null, 0.88f, "RISER_SWEEP"),
            SamplerAssignmentEntity(14, "Impact Boom", "#FF5252", null, 0.95f, "IMPACT_BOOM"),
            SamplerAssignmentEntity(15, "DJ Drop", "#B388FF", null, 0.92f, "DJ_DROP_TAG")
        )
        defaults16.filter { it.padIndex !in existingIndices }.forEach {
            db.presetSettingsLayoutDao().upsertSamplerAssignment(it)
        }

        val existingPresets = db.presetSettingsLayoutDao().getAllPresetsOnce().map { it.name }.toSet()
        EqPreset.entries.filter { it != EqPreset.CUSTOM && it.displayName !in existingPresets }.forEach { preset ->
            db.presetSettingsLayoutDao().insertPreset(
                DjPresetEntity(
                    name = preset.displayName,
                    bassDb = preset.bassDb,
                    midDb = preset.midDb,
                    trebleDb = preset.trebleDb,
                    isBuiltIn = true
                )
            )
        }
    }

    suspend fun importLocalAudioUris(
        uris: List<Uri>,
        copyToPrivateStorage: Boolean
    ): List<SongEntity> = withContext(Dispatchers.IO) {
        val imported = mutableListOf<SongEntity>()
        for (uri in uris) {
            var persisted = false
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                persisted = true
            } catch (_: Exception) {
            }

            val displayName = queryDisplayName(uri) ?: "Imported_Track_${System.currentTimeMillis()}.mp3"
            val meta = AudioDecoder.extractMetadata(context, uri, displayName)

            var privatePath: String? = null
            if (copyToPrivateStorage) {
                try {
                    val dir = File(context.filesDir, "imported_audio").apply { mkdirs() }
                    val safeName = displayName.replace(Regex("[^a-zA-Z0-9._\\-]"), "_")
                    val dst = File(dir, "${System.currentTimeMillis()}_$safeName")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        dst.outputStream().use { output -> input.copyTo(output) }
                    }
                    privatePath = dst.absolutePath
                } catch (_: Exception) {
                }
            }

            val entity = SongEntity(
                title = meta.title,
                artist = meta.artist,
                album = meta.album,
                folderName = "Imported Music",
                uriString = uri.toString(),
                localCopyPath = privatePath,
                durationMs = meta.durationMs,
                bpm = 128f,
                format = meta.format,
                sourceType = "IMPORTED",
                isBuiltInSynth = false
            )
            val newId = db.songDao().insertSong(entity)
            db.artistAlbumDao().upsertArtist(ArtistEntity(name = entity.artist))
            db.artistAlbumDao().upsertAlbum(AlbumEntity(title = entity.album, artistName = entity.artist))
            db.presetSettingsLayoutDao().insertImportedRef(
                ImportedAudioRefEntity(
                    songId = newId,
                    originalUri = uri.toString(),
                    persistedPermission = persisted,
                    copiedPrivatePath = privatePath,
                    fileAvailable = true
                )
            )
            imported.add(entity.copy(id = newId))
        }
        imported
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        } catch (_: Exception) {
            uri.lastPathSegment
        }
    }

    fun getSongsInPlaylist(playlistId: Long): Flow<List<SongEntity>> =
        db.playlistDao().getSongsInPlaylist(playlistId)

    suspend fun toggleFavorite(songId: Long, currentlyFavorite: Boolean) {
        if (currentlyFavorite) db.favoriteDao().removeFavorite(songId)
        else db.favoriteDao().addFavorite(FavoriteEntity(songId))
    }

    suspend fun createPlaylist(name: String): Long =
        db.playlistDao().insertPlaylist(PlaylistEntity(name = name.trim()))

    suspend fun renamePlaylist(id: Long, newName: String) =
        db.playlistDao().renamePlaylist(id, newName.trim())

    suspend fun deletePlaylist(id: Long) = db.playlistDao().deletePlaylist(id)

    suspend fun addSongToPlaylist(playlistId: Long, songId: Long) =
        db.playlistDao().addSongToPlaylist(PlaylistSongEntity(playlistId, songId))

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) =
        db.playlistDao().removeSongFromPlaylist(playlistId, songId)

    suspend fun deleteSongFromLibrary(songId: Long) = db.songDao().deleteSongById(songId)

    suspend fun recordPlaybackHistory(song: SongEntity, deckName: String) {
        db.historyDao().insertHistory(
            PlaybackHistoryEntity(
                songId = song.id,
                songTitle = song.title,
                artist = song.artist,
                deckName = deckName
            )
        )
    }

    suspend fun clearHistory() = db.historyDao().clearHistory()

    suspend fun saveRecording(recording: RecordingEntity) =
        db.recordingDao().insertRecording(recording)

    suspend fun renameRecording(id: Long, title: String) =
        db.recordingDao().renameRecording(id, title)

    suspend fun deleteRecording(id: Long, filePath: String) {
        db.recordingDao().deleteRecording(id)
        try {
            File(filePath).delete()
        } catch (_: Exception) {
        }
    }

    suspend fun saveSettings(settings: UserSettingsEntity) =
        db.presetSettingsLayoutDao().saveSettings(settings)

    suspend fun saveActiveLayout(layout: LayoutPreferenceEntity) {
        db.presetSettingsLayoutDao().deactivateAllLayouts()
        db.presetSettingsLayoutDao().upsertLayout(layout.copy(isActive = true, updatedAt = System.currentTimeMillis()))
    }

    suspend fun saveNewCustomLayout(profileName: String, base: LayoutPreferenceEntity) {
        db.presetSettingsLayoutDao().deactivateAllLayouts()
        db.presetSettingsLayoutDao().upsertLayout(
            base.copy(id = 0, profileName = profileName.trim(), isActive = true, updatedAt = System.currentTimeMillis())
        )
    }

    suspend fun renameLayout(id: Long, name: String) =
        db.presetSettingsLayoutDao().renameLayout(id, name.trim())

    suspend fun deleteLayout(id: Long) = db.presetSettingsLayoutDao().deleteLayout(id)

    suspend fun updateSamplerPad(assignment: SamplerAssignmentEntity) =
        db.presetSettingsLayoutDao().upsertSamplerAssignment(assignment)

    val savedLoops: Flow<List<SavedLoopEntity>> = db.cueLoopDao().observeAllLoops()

    suspend fun saveHotCue(songId: Long, slotIndex: Int, positionMs: Long) {
        db.cueLoopDao().upsertHotCue(
            HotCueEntity(songId = songId, slotIndex = slotIndex, positionMs = positionMs)
        )
    }

    suspend fun deleteHotCue(songId: Long, slotIndex: Int) {
        db.cueLoopDao().deleteHotCue(songId, slotIndex)
    }

    suspend fun getHotCues(songId: Long): List<HotCueEntity> =
        db.cueLoopDao().getHotCuesForSong(songId)

    suspend fun saveLoop(songId: Long, startMs: Long, endMs: Long, beats: Float) {
        db.cueLoopDao().insertLoop(
            SavedLoopEntity(
                songId = songId,
                startMs = startMs,
                endMs = endMs,
                beatLength = beats,
                label = "${if (beats < 1f) "1/${(1f / beats).toInt()}" else beats.toInt()} Beat Loop"
            )
        )
    }

    suspend fun deleteLoop(id: Long) = db.cueLoopDao().deleteLoop(id)

    suspend fun updateSongBpm(songId: Long, bpm: Float) =
        db.songDao().updateBpm(songId, bpm)

    suspend fun renameFolder(oldName: String, newName: String) {
        if (newName.isNotBlank()) {
            db.songDao().renameFolder(oldName, newName.trim())
        }
    }

    suspend fun moveSongToFolder(song: SongEntity, folderName: String) {
        db.songDao().updateSong(song.copy(folderName = folderName.trim()))
    }

    suspend fun reorderPlaylistSong(playlistId: Long, songId: Long, newOrder: Int) {
        db.playlistDao().updateSongOrder(playlistId, songId, newOrder)
    }

    suspend fun swapPlaylistSongs(playlistId: Long, songs: List<SongEntity>, fromIndex: Int, toIndex: Int) {
        if (fromIndex !in songs.indices || toIndex !in songs.indices || fromIndex == toIndex) return
        val mutable = songs.toMutableList()
        val moved = mutable.removeAt(fromIndex)
        mutable.add(toIndex, moved)
        mutable.forEachIndexed { order, song ->
            db.playlistDao().updateSongOrder(playlistId, song.id, order)
        }
    }

    suspend fun registerRenderedRemix(
        title: String,
        wavFilePath: String,
        durationMs: Long,
        bpm: Float,
        musicalKey: String,
        styleName: String
    ): SongEntity = withContext(Dispatchers.IO) {
        val file = File(wavFilePath)
        val uri = Uri.fromFile(file).toString()
        val entity = SongEntity(
            title = title,
            artist = "BIJOY AI Remix ($styleName)",
            album = "AI Remix Studio",
            folderName = "AI Remixes",
            uriString = uri,
            localCopyPath = wavFilePath,
            durationMs = durationMs,
            bpm = bpm,
            musicalKey = musicalKey,
            artworkResName = "deck_b",
            format = "WAV",
            sourceType = "REMIX",
            isBuiltInSynth = false
        )
        val newId = db.songDao().insertSong(entity)
        db.artistAlbumDao().upsertArtist(ArtistEntity(name = entity.artist))
        db.artistAlbumDao().upsertAlbum(AlbumEntity(title = entity.album, artistName = entity.artist))
        entity.copy(id = newId)
    }

    suspend fun saveUserEqPreset(name: String, bassDb: Float, midDb: Float, trebleDb: Float, filterCutoff: Float = 0f) {
        if (name.isBlank()) return
        db.presetSettingsLayoutDao().insertPreset(
            DjPresetEntity(
                name = name.trim(),
                bassDb = bassDb,
                midDb = midDb,
                trebleDb = trebleDb,
                filterCutoff = filterCutoff,
                isBuiltIn = false
            )
        )
    }

    suspend fun deleteUserPreset(preset: DjPresetEntity) {
        if (!preset.isBuiltIn) {
            db.presetSettingsLayoutDao().deletePreset(preset)
        }
    }
}
