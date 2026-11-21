package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.audio.*
import com.example.database.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun appName_isBijoyDj() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("BIJOY DJ", context.getString(R.string.app_name))
    }

    @Test
    fun crossfaderCurves_mixDeckAAndDeckBAccurately() {
        // Left extreme -> Deck A dominant
        val (leftA, leftB) = BijoyAudioEngine.computeCrossfaderGains(-1f, CrossfaderCurve.CONSTANT_POWER)
        assertTrue(leftA > 0.99f)
        assertTrue(leftB < 0.01f)

        // Right extreme -> Deck B dominant
        val (rightA, rightB) = BijoyAudioEngine.computeCrossfaderGains(1f, CrossfaderCurve.CONSTANT_POWER)
        assertTrue(rightA < 0.01f)
        assertTrue(rightB > 0.99f)

        // Center -> both audible (~0.707 constant power)
        val (midA, midB) = BijoyAudioEngine.computeCrossfaderGains(0f, CrossfaderCurve.CONSTANT_POWER)
        assertEquals(0.707f, midA, 0.02f)
        assertEquals(0.707f, midB, 0.02f)
    }

    @Test
    fun dspProcessor_appliesEqAndEffectsWithoutNaN() {
        val dsp = ChannelDspProcessor(44100f)
        dsp.updateEq(gainDb = 3f, bassDb = 6f, midDb = 0f, trebleDb = 4f, filter = -0.3f)
        dsp.updateEffect(DjEffectType.ECHO, wet = 0.5f, param = 0.5f)
        val out = FloatArray(2)
        for (i in 0 until 512) {
            val s = kotlin.math.sin(i * 0.05).toFloat() * 0.5f
            dsp.processStereo(s, s, out)
            assertFalse(out[0].isNaN())
            assertFalse(out[1].isNaN())
            assertTrue(out[0] in -1.01f..1.01f)
        }
    }

    @Test
    fun sfxSynthesizer_generatesAll12PadsAndMixesPolyphonically() {
        val sfx = SfxSamplerEngine(44100)
        sfx.triggerPad(0, 0.9f) // Airhorn
        sfx.triggerPad(9, 0.9f) // Bass Drop
        assertEquals(setOf(0, 9), sfx.activePadIndices())

        val mix = FloatArray(512 * 2)
        sfx.mixInto(mix, 512)
        val peak = mix.maxOf { kotlin.math.abs(it) }
        assertTrue("SFX mix should produce audible signal", peak > 0.05f)
    }

    @Test
    fun waveformAndBpmAnalyzer_producesRealPeaksFromSynthesizedTrack() = runTest {
        val decoded = AudioDecoder.generateBuiltInDjTrack(presetId = 0, bpm = 128f)
        assertTrue(decoded.pcmStereo.isNotEmpty())
        assertEquals(180, decoded.waveform.peaks.size)
        assertTrue(decoded.waveform.beatPositionsRatio.isNotEmpty())
        assertEquals(128f, decoded.detectedBpm, 0.1f)
    }

    @Test
    fun roomDatabase_persistsSongsPlaylistsLayoutsAndHotCues() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, BijoyDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val songId = db.songDao().insertSong(
                SongEntity(
                    title = "Club Test Mix",
                    artist = "DJ Bijoy",
                    uriString = "builtin://synth/0",
                    bpm = 130f
                )
            )
            val playlistId = db.playlistDao().insertPlaylist(PlaylistEntity(name = "Party Set"))
            db.playlistDao().addSongToPlaylist(PlaylistSongEntity(playlistId, songId))
            db.favoriteDao().addFavorite(FavoriteEntity(songId))
            db.cueLoopDao().upsertHotCue(HotCueEntity(songId = songId, slotIndex = 0, positionMs = 4500L))
            db.presetSettingsLayoutDao().upsertLayout(
                LayoutPreferenceEntity(profileName = "Fullscreen Mixer", isActive = true, layoutMode = "FULLSCREEN", showLeftSidebar = false, showRightSidebar = false)
            )

            assertEquals(1, db.playlistDao().getSongsInPlaylist(playlistId).first().size)
            assertEquals(listOf(songId), db.favoriteDao().getFavoriteSongIds().first())
            assertEquals(4500L, db.cueLoopDao().getHotCuesForSong(songId).first().positionMs)
            assertEquals("FULLSCREEN", db.presetSettingsLayoutDao().getActiveLayoutOnce()?.layoutMode)
        } finally {
            db.close()
        }
    }
}
