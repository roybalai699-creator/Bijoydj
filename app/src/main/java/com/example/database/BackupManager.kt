package com.example.database

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class BackupManager(
    private val context: Context,
    private val db: BijoyDatabase
) {
    suspend fun exportBackupToUri(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val root = JSONObject()
            root.put("app", "BIJOY DJ")
            root.put("version", 1)
            root.put("exportedAt", System.currentTimeMillis())
            root.put(
                "notice",
                "Metadata backup only. Original local audio files are not embedded."
            )

            // Songs
            val songsArray = JSONArray()
            db.songDao().getAllSongsOnce().forEach { s ->
                songsArray.put(JSONObject().apply {
                    put("id", s.id)
                    put("title", s.title)
                    put("artist", s.artist)
                    put("album", s.album)
                    put("folderName", s.folderName)
                    put("uriString", s.uriString)
                    put("durationMs", s.durationMs)
                    put("bpm", s.bpm.toDouble())
                    put("musicalKey", s.musicalKey)
                    put("format", s.format)
                    put("sourceType", s.sourceType)
                    put("isBuiltInSynth", s.isBuiltInSynth)
                    put("synthPresetId", s.synthPresetId)
                })
            }
            root.put("songs", songsArray)

            // Playlists
            val playlistsArray = JSONArray()
            db.playlistDao().getAllPlaylistsOnce().forEach { p ->
                playlistsArray.put(JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("artworkColorHex", p.artworkColorHex)
                    put("createdAt", p.createdAt)
                })
            }
            root.put("playlists", playlistsArray)

            // Favorites
            val favArray = JSONArray()
            db.favoriteDao().getAllFavoritesOnce().forEach { f ->
                favArray.put(f.songId)
            }
            root.put("favorites", favArray)

            // Presets
            val presetsArray = JSONArray()
            db.presetSettingsLayoutDao().getAllPresetsOnce().forEach { pr ->
                presetsArray.put(JSONObject().apply {
                    put("name", pr.name)
                    put("bassDb", pr.bassDb.toDouble())
                    put("midDb", pr.midDb.toDouble())
                    put("trebleDb", pr.trebleDb.toDouble())
                    put("filterCutoff", pr.filterCutoff.toDouble())
                    put("effectName", pr.effectName)
                    put("effectWet", pr.effectWet.toDouble())
                    put("isBuiltIn", pr.isBuiltIn)
                })
            }
            root.put("presets", presetsArray)

            // Layouts
            val layoutsArray = JSONArray()
            db.presetSettingsLayoutDao().getAllLayoutsOnce().forEach { l ->
                layoutsArray.put(JSONObject().apply {
                    put("profileName", l.profileName)
                    put("isActive", l.isActive)
                    put("layoutMode", l.layoutMode)
                    put("showLeftSidebar", l.showLeftSidebar)
                    put("showRightSidebar", l.showRightSidebar)
                    put("showMusicLibrary", l.showMusicLibrary)
                    put("showFolders", l.showFolders)
                    put("showPlaylists", l.showPlaylists)
                    put("showFavorites", l.showFavorites)
                    put("showHistory", l.showHistory)
                    put("showSoundEffects", l.showSoundEffects)
                    put("showFxPanel", l.showFxPanel)
                    put("showSamplerPanel", l.showSamplerPanel)
                    put("showRecordingPanel", l.showRecordingPanel)
                    put("showAdditionalTools", l.showAdditionalTools)
                    put("sidebarWidthDp", l.sidebarWidthDp)
                    put("mixerScale", l.mixerScale.toDouble())
                })
            }
            root.put("layouts", layoutsArray)

            // Settings
            db.presetSettingsLayoutDao().getSettingsOnce()?.let { st ->
                root.put("settings", JSONObject().apply {
                    put("themeMode", st.themeMode)
                    put("language", st.language)
                    put("defaultLayoutMode", st.defaultLayoutMode)
                    put("preferLandscape", st.preferLandscape)
                    put("keepScreenAwake", st.keepScreenAwake)
                    put("animationsEnabled", st.animationsEnabled)
                    put("jogSensitivity", st.jogSensitivity.toDouble())
                    put("crossfaderCurve", st.crossfaderCurve)
                    put("autoSync", st.autoSync)
                    put("autoFadeDurationSec", st.autoFadeDurationSec.toDouble())
                    put("defaultDeckVolume", st.defaultDeckVolume.toDouble())
                    put("recordingFormat", st.recordingFormat)
                })
            }

            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(root.toString(2).toByteArray(Charsets.UTF_8))
            } ?: error("Unable to open backup destination")

            "Exported ${songsArray.length()} tracks, ${playlistsArray.length()} playlists, ${layoutsArray.length()} layouts"
        }
    }

    suspend fun restoreBackupFromUri(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val jsonText = context.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader().readText()
            } ?: error("Unable to read backup file")

            val root = JSONObject(jsonText)
            var restoredPlaylists = 0
            var restoredLayouts = 0
            var restoredPresets = 0

            root.optJSONArray("playlists")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    db.playlistDao().insertPlaylist(
                        PlaylistEntity(
                            name = o.optString("name", "Restored Playlist"),
                            artworkColorHex = o.optString("artworkColorHex", "#00E5FF"),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                    restoredPlaylists++
                }
            }

            root.optJSONArray("layouts")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    db.presetSettingsLayoutDao().upsertLayout(
                        LayoutPreferenceEntity(
                            profileName = o.optString("profileName", "Restored Layout"),
                            isActive = false,
                            layoutMode = o.optString("layoutMode", "STANDARD"),
                            showLeftSidebar = o.optBoolean("showLeftSidebar", true),
                            showRightSidebar = o.optBoolean("showRightSidebar", true),
                            showMusicLibrary = o.optBoolean("showMusicLibrary", true),
                            showFolders = o.optBoolean("showFolders", true),
                            showPlaylists = o.optBoolean("showPlaylists", true),
                            showFavorites = o.optBoolean("showFavorites", true),
                            showHistory = o.optBoolean("showHistory", true),
                            showSoundEffects = o.optBoolean("showSoundEffects", true),
                            showFxPanel = o.optBoolean("showFxPanel", true),
                            showSamplerPanel = o.optBoolean("showSamplerPanel", true),
                            showRecordingPanel = o.optBoolean("showRecordingPanel", true),
                            showAdditionalTools = o.optBoolean("showAdditionalTools", true),
                            sidebarWidthDp = o.optInt("sidebarWidthDp", 78),
                            mixerScale = o.optDouble("mixerScale", 1.0).toFloat()
                        )
                    )
                    restoredLayouts++
                }
            }

            root.optJSONArray("presets")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    if (!o.optBoolean("isBuiltIn", false)) {
                        db.presetSettingsLayoutDao().insertPreset(
                            DjPresetEntity(
                                name = o.optString("name", "Restored Preset"),
                                bassDb = o.optDouble("bassDb", 0.0).toFloat(),
                                midDb = o.optDouble("midDb", 0.0).toFloat(),
                                trebleDb = o.optDouble("trebleDb", 0.0).toFloat(),
                                filterCutoff = o.optDouble("filterCutoff", 0.0).toFloat(),
                                effectName = o.optString("effectName", "NONE"),
                                effectWet = o.optDouble("effectWet", 0.0).toFloat(),
                                isBuiltIn = false
                            )
                        )
                        restoredPresets++
                    }
                }
            }

            root.optJSONObject("settings")?.let { st ->
                val current = db.presetSettingsLayoutDao().getSettingsOnce() ?: UserSettingsEntity()
                db.presetSettingsLayoutDao().saveSettings(
                    current.copy(
                        themeMode = st.optString("themeMode", current.themeMode),
                        language = st.optString("language", current.language),
                        defaultLayoutMode = st.optString("defaultLayoutMode", current.defaultLayoutMode),
                        preferLandscape = st.optBoolean("preferLandscape", current.preferLandscape),
                        keepScreenAwake = st.optBoolean("keepScreenAwake", current.keepScreenAwake),
                        animationsEnabled = st.optBoolean("animationsEnabled", current.animationsEnabled),
                        jogSensitivity = st.optDouble("jogSensitivity", current.jogSensitivity.toDouble()).toFloat(),
                        crossfaderCurve = st.optString("crossfaderCurve", current.crossfaderCurve),
                        autoSync = st.optBoolean("autoSync", current.autoSync),
                        autoFadeDurationSec = st.optDouble("autoFadeDurationSec", current.autoFadeDurationSec.toDouble()).toFloat(),
                        defaultDeckVolume = st.optDouble("defaultDeckVolume", current.defaultDeckVolume.toDouble()).toFloat(),
                        recordingFormat = st.optString("recordingFormat", current.recordingFormat)
                    )
                )
            }

            "Restored $restoredPlaylists playlists, $restoredLayouts layouts, $restoredPresets presets & settings"
        }
    }
}
