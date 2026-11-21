package com.example.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// 1. Songs
@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val artist: String,
    val album: String = "Single",
    val folderName: String = "Local Music",
    val uriString: String,
    val localCopyPath: String? = null,
    val durationMs: Long = 0L,
    val bpm: Float = 128f,
    val musicalKey: String = "Am",
    val artworkResName: String? = null,
    val format: String = "WAV",
    val sourceType: String = "LOCAL", // LOCAL, BUILTIN, IMPORTED
    val isBuiltInSynth: Boolean = false,
    val synthPresetId: Int = 0,
    val dateAdded: Long = System.currentTimeMillis()
)

// 2. Artists
@Entity(tableName = "artists", indices = [Index(value = ["name"], unique = true)])
data class ArtistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val songCount: Int = 1
)

// 3. Albums
@Entity(tableName = "albums", indices = [Index(value = ["title", "artistName"], unique = true)])
data class AlbumEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val artistName: String,
    val artworkUri: String? = null
)

// 4. Playlists
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val artworkColorHex: String = "#00E5FF",
    val createdAt: Long = System.currentTimeMillis()
)

// 5. Playlist Songs
@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("songId")]
)
data class PlaylistSongEntity(
    val playlistId: Long,
    val songId: Long,
    val sortOrder: Int = 0,
    val addedAt: Long = System.currentTimeMillis()
)

// 6. Favorites
@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val songId: Long,
    val addedAt: Long = System.currentTimeMillis()
)

// 7. Playback History
@Entity(tableName = "playback_history")
data class PlaybackHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: Long,
    val songTitle: String,
    val artist: String,
    val deckName: String,
    val playedAt: Long = System.currentTimeMillis()
)

// 8. Recordings
@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val filePath: String,
    val format: String = "WAV",
    val durationMs: Long = 0L,
    val fileSizeBytes: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)

// 9. Hot Cues
@Entity(
    tableName = "hot_cues",
    indices = [Index(value = ["songId", "slotIndex"], unique = true)]
)
data class HotCueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: Long,
    val slotIndex: Int, // 0..3
    val positionMs: Long,
    val label: String = "CUE",
    val colorHex: String = "#00E5FF"
)

// 10. Saved Loops
@Entity(tableName = "saved_loops")
data class SavedLoopEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: Long,
    val startMs: Long,
    val endMs: Long,
    val beatLength: Float = 4f,
    val label: String = "4-Beat Loop"
)

// 11. DJ Presets
@Entity(tableName = "dj_presets")
data class DjPresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val bassDb: Float = 0f,
    val midDb: Float = 0f,
    val trebleDb: Float = 0f,
    val filterCutoff: Float = 0f, // -1 (LPF) .. 0 (OFF) .. +1 (HPF)
    val effectName: String = "NONE",
    val effectWet: Float = 0f,
    val isBuiltIn: Boolean = false
)

// 12. User Settings
@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val themeMode: String = "DARK_NEON",
    val language: String = "English",
    val defaultLayoutMode: String = "STANDARD",
    val preferLandscape: Boolean = true,
    val keepScreenAwake: Boolean = true,
    val animationsEnabled: Boolean = true,
    val jogSensitivity: Float = 1.0f,
    val crossfaderCurve: String = "CONSTANT_POWER",
    val defaultTempo: Float = 1.0f,
    val defaultBpm: Float = 128f,
    val autoSync: Boolean = false,
    val autoFadeDurationSec: Float = 4.0f,
    val defaultDeckVolume: Float = 0.85f,
    val audioLatencyMode: String = "LOW_LATENCY",
    val sampleRateHz: Int = 44100,
    val bufferSizeFrames: Int = 512,
    val outputDeviceName: String = "System Default",
    val audioQuality: String = "STUDIO_44K",
    val headphoneMonitoring: Boolean = false,
    val defaultMusicFolder: String = "Music",
    val copyImportedToPrivateStorage: Boolean = false,
    val autoScanMetadata: Boolean = true,
    val recordingFormat: String = "WAV",
    val recordingQuality: String = "16-bit 44.1kHz Stereo",
    val recordingFolder: String = "BIJOY_DJ/Recordings"
)

// 13. Layout Preferences
@Entity(tableName = "layout_preferences")
data class LayoutPreferenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileName: String,
    val isActive: Boolean = false,
    val layoutMode: String = "STANDARD", // STANDARD, COMPACT, FULLSCREEN
    val showLeftSidebar: Boolean = true,
    val showRightSidebar: Boolean = true,
    val showMusicLibrary: Boolean = true,
    val showFolders: Boolean = true,
    val showPlaylists: Boolean = true,
    val showFavorites: Boolean = true,
    val showHistory: Boolean = true,
    val showSoundEffects: Boolean = true,
    val showFxPanel: Boolean = true,
    val showSamplerPanel: Boolean = true,
    val showRecordingPanel: Boolean = true,
    val showAdditionalTools: Boolean = true,
    val sidebarWidthDp: Int = 78,
    val mixerScale: Float = 1.0f,
    val updatedAt: Long = System.currentTimeMillis()
)

// 14. Sampler Assignments
@Entity(tableName = "sampler_assignments")
data class SamplerAssignmentEntity(
    @PrimaryKey val padIndex: Int, // 0..11
    val label: String,
    val colorHex: String,
    val customAudioUri: String? = null,
    val volume: Float = 0.9f,
    val synthType: String
)

// 15. Imported Audio References
@Entity(tableName = "imported_audio_refs")
data class ImportedAudioRefEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: Long,
    val originalUri: String,
    val persistedPermission: Boolean = true,
    val copiedPrivatePath: String? = null,
    val fileAvailable: Boolean = true,
    val importedAt: Long = System.currentTimeMillis()
)
