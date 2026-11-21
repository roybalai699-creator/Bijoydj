package com.example.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY dateAdded DESC")
    fun getAllSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs ORDER BY dateAdded DESC")
    suspend fun getAllSongsOnce(): List<SongEntity>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getSongById(id: Long): SongEntity?

    @Query("SELECT * FROM songs WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%'")
    fun searchSongs(query: String): Flow<List<SongEntity>>

    @Query("SELECT DISTINCT folderName FROM songs ORDER BY folderName ASC")
    fun getAllFolders(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: SongEntity): Long

    @Update
    suspend fun updateSong(song: SongEntity)

    @Query("UPDATE songs SET bpm = :bpm WHERE id = :songId")
    suspend fun updateBpm(songId: Long, bpm: Float)

    @Query("DELETE FROM songs WHERE id = :id")
    suspend fun deleteSongById(id: Long)

    @Query("UPDATE songs SET folderName = :newName WHERE folderName = :oldName")
    suspend fun renameFolder(oldName: String, newName: String)

    @Query("SELECT COUNT(*) FROM songs")
    suspend fun getSongCount(): Int
}

@Dao
interface ArtistAlbumDao {
    @Query("SELECT * FROM artists ORDER BY name ASC")
    fun getAllArtists(): Flow<List<ArtistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertArtist(artist: ArtistEntity)

    @Query("SELECT * FROM albums ORDER BY title ASC")
    fun getAllAlbums(): Flow<List<AlbumEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAlbum(album: AlbumEntity)
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    suspend fun getAllPlaylistsOnce(): List<PlaylistEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :newName WHERE id = :playlistId")
    suspend fun renamePlaylist(playlistId: Long, newName: String)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addSongToPlaylist(entry: PlaylistSongEntity)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long)

    @Query("UPDATE playlist_songs SET sortOrder = :order WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun updateSongOrder(playlistId: Long, songId: Long, order: Int)

    @Query(
        "SELECT s.* FROM songs s INNER JOIN playlist_songs ps ON s.id = ps.songId " +
            "WHERE ps.playlistId = :playlistId ORDER BY ps.sortOrder ASC, ps.addedAt ASC"
    )
    fun getSongsInPlaylist(playlistId: Long): Flow<List<SongEntity>>

    @Query("SELECT * FROM playlist_songs")
    suspend fun getAllPlaylistSongsOnce(): List<PlaylistSongEntity>
}

@Dao
interface FavoriteDao {
    @Query("SELECT songId FROM favorites")
    fun getFavoriteSongIds(): Flow<List<Long>>

    @Query("SELECT * FROM favorites")
    suspend fun getAllFavoritesOnce(): List<FavoriteEntity>

    @Query(
        "SELECT s.* FROM songs s INNER JOIN favorites f ON s.id = f.songId ORDER BY f.addedAt DESC"
    )
    fun getFavoriteSongs(): Flow<List<SongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE songId = :songId")
    suspend fun removeFavorite(songId: Long)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM playback_history ORDER BY playedAt DESC LIMIT 100")
    fun getRecentHistory(): Flow<List<PlaybackHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(entry: PlaybackHistoryEntity)

    @Query("DELETE FROM playback_history")
    suspend fun clearHistory()
}

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recordings ORDER BY createdAt DESC")
    fun getAllRecordings(): Flow<List<RecordingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecording(recording: RecordingEntity): Long

    @Query("UPDATE recordings SET title = :title WHERE id = :id")
    suspend fun renameRecording(id: Long, title: String)

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun deleteRecording(id: Long)
}

@Dao
interface CueLoopDao {
    @Query("SELECT * FROM hot_cues WHERE songId = :songId ORDER BY slotIndex ASC")
    suspend fun getHotCuesForSong(songId: Long): List<HotCueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHotCue(cue: HotCueEntity)

    @Query("DELETE FROM hot_cues WHERE songId = :songId AND slotIndex = :slotIndex")
    suspend fun deleteHotCue(songId: Long, slotIndex: Int)

    @Query("SELECT * FROM saved_loops WHERE songId = :songId")
    suspend fun getLoopsForSong(songId: Long): List<SavedLoopEntity>

    @Query("SELECT * FROM saved_loops ORDER BY id DESC")
    fun observeAllLoops(): Flow<List<SavedLoopEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLoop(loop: SavedLoopEntity)

    @Query("DELETE FROM saved_loops WHERE id = :id")
    suspend fun deleteLoop(id: Long)
}

@Dao
interface PresetSettingsLayoutDao {
    // Presets
    @Query("SELECT * FROM dj_presets ORDER BY isBuiltIn DESC, id ASC")
    fun getAllPresets(): Flow<List<DjPresetEntity>>

    @Query("SELECT * FROM dj_presets")
    suspend fun getAllPresetsOnce(): List<DjPresetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: DjPresetEntity): Long

    @Delete
    suspend fun deletePreset(preset: DjPresetEntity)

    // Settings
    @Query("SELECT * FROM user_settings WHERE id = 1")
    fun observeSettings(): Flow<UserSettingsEntity?>

    @Query("SELECT * FROM user_settings WHERE id = 1")
    suspend fun getSettingsOnce(): UserSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: UserSettingsEntity)

    // Layouts
    @Query("SELECT * FROM layout_preferences ORDER BY updatedAt DESC")
    fun getAllLayouts(): Flow<List<LayoutPreferenceEntity>>

    @Query("SELECT * FROM layout_preferences")
    suspend fun getAllLayoutsOnce(): List<LayoutPreferenceEntity>

    @Query("SELECT * FROM layout_preferences WHERE isActive = 1 LIMIT 1")
    fun observeActiveLayout(): Flow<LayoutPreferenceEntity?>

    @Query("SELECT * FROM layout_preferences WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveLayoutOnce(): LayoutPreferenceEntity?

    @Query("UPDATE layout_preferences SET isActive = 0")
    suspend fun deactivateAllLayouts()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLayout(layout: LayoutPreferenceEntity): Long

    @Query("UPDATE layout_preferences SET profileName = :name WHERE id = :id")
    suspend fun renameLayout(id: Long, name: String)

    @Query("DELETE FROM layout_preferences WHERE id = :id")
    suspend fun deleteLayout(id: Long)

    // Sampler Assignments
    @Query("SELECT * FROM sampler_assignments ORDER BY padIndex ASC")
    fun observeSamplerAssignments(): Flow<List<SamplerAssignmentEntity>>

    @Query("SELECT * FROM sampler_assignments ORDER BY padIndex ASC")
    suspend fun getSamplerAssignmentsOnce(): List<SamplerAssignmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSamplerAssignment(assignment: SamplerAssignmentEntity)

    // Imported Audio Refs
    @Query("SELECT * FROM imported_audio_refs ORDER BY importedAt DESC")
    fun observeImportedRefs(): Flow<List<ImportedAudioRefEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImportedRef(ref: ImportedAudioRefEntity): Long

    @Query("UPDATE imported_audio_refs SET fileAvailable = :available WHERE songId = :songId")
    suspend fun updateFileAvailability(songId: Long, available: Boolean)
}
