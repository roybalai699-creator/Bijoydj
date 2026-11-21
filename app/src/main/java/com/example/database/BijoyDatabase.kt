package com.example.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SongEntity::class,
        ArtistEntity::class,
        AlbumEntity::class,
        PlaylistEntity::class,
        PlaylistSongEntity::class,
        FavoriteEntity::class,
        PlaybackHistoryEntity::class,
        RecordingEntity::class,
        HotCueEntity::class,
        SavedLoopEntity::class,
        DjPresetEntity::class,
        UserSettingsEntity::class,
        LayoutPreferenceEntity::class,
        SamplerAssignmentEntity::class,
        ImportedAudioRefEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class BijoyDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun artistAlbumDao(): ArtistAlbumDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun historyDao(): HistoryDao
    abstract fun recordingDao(): RecordingDao
    abstract fun cueLoopDao(): CueLoopDao
    abstract fun presetSettingsLayoutDao(): PresetSettingsLayoutDao

    companion object {
        @Volatile
        private var INSTANCE: BijoyDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Schema migration v1 -> v2: ensure performance indices & availability column exist
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_hot_cues_songId_slotIndex ON hot_cues(songId, slotIndex)"
                )
            }
        }

        fun getInstance(context: Context): BijoyDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BijoyDatabase::class.java,
                    "bijoy_dj_database.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
