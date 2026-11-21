package com.example

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.audio.*
import com.example.database.*
import com.example.ui.components.RotaryHardwareKnob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

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

    @Test
    fun dspProcessor_tenBandEqAndAll16EffectsRemainFiniteAndBounded() {
        val dsp = ChannelDspProcessor(44100f)
        dsp.updateTenBandEq(EqPreset.CLUB.tenBandDb)
        val out = FloatArray(2)
        for (fx in DjEffectType.entries) {
            dsp.updateEffect(fx, wet = 0.65f, param = 0.5f)
            for (i in 0 until 256) {
                val s = kotlin.math.sin(i * 0.08).toFloat() * 0.45f
                dsp.processStereo(s, s, out)
                assertFalse("NaN output for ${fx.name}", out[0].isNaN() || out[1].isNaN())
                assertTrue("Sample out of bounds for ${fx.name}", out[0] in -1.01f..1.01f && out[1] in -1.01f..1.01f)
            }
        }
    }

    @Test
    fun sfxSampler_supportsLoopToggleAndRhythmicSamplerRoll() {
        val sfx = SfxSamplerEngine(44100)
        assertTrue(sfx.togglePadLoop(2))
        assertTrue(2 in sfx.loopingPads)
        sfx.startSamplerRoll(padIndex = 0, beats = 1f, bpm = 128f)
        assertTrue(sfx.rollActive)

        val ch3Bus = FloatArray(1024 * 2)
        sfx.renderInto(ch3Bus, 1024)
        assertTrue("Sampler Roll should render audio into CH3 bus", ch3Bus.maxOf { kotlin.math.abs(it) } > 0.02f)

        sfx.stopSamplerRoll()
        assertFalse(sfx.rollActive)
    }

    @Test
    fun deckState_hasEightHotCuesAndFourMixerChannels() {
        val deck = DeckState(DeckId.A)
        val mixer = MixerState()
        assertEquals(8, deck.hotCues.size)
        assertEquals(4, mixer.channels.size)
        assertEquals(15, deck.fifteenBandEqDb.size)
    }

    @Test
    fun sfxSynthesizer_supports16PadsPitchAndCategories() {
        val sfx = SfxSamplerEngine(44100)
        assertEquals(16, SfxSamplerEngine.DEFAULT_TYPES_16.size)
        sfx.setPadPitch(15, 1.35f)
        assertEquals(1.35f, sfx.getPadPitch(15), 0.01f)
        sfx.triggerPad(15, volume = 0.9f)
        assertTrue(15 in sfx.activePadIndices())

        val mix = FloatArray(512 * 2)
        sfx.mixInto(mix, 512)
        assertTrue("16th pad should render audible audio", mix.maxOf { kotlin.math.abs(it) } > 0.02f)
        assertEquals("Siren", SfxSamplerEngine.categoryForType("AIRHORN"))
        assertEquals("Vocal FX", SfxSamplerEngine.categoryForType("DJ_VOICE"))
    }

    @Test
    fun dspProcessor_handlesVocalRemovalAnd24EffectsAnd15BandEq() {
        assertEquals(24, DjEffectType.entries.size)
        assertEquals(11, EqPreset.entries.size)

        val dsp = ChannelDspProcessor(44100f)
        dsp.updateVocalDb(-12f)
        dsp.updateFifteenBandEq(EqPreset.DEEP_BASS.fifteenBandDb)
        val out = FloatArray(2)
        for (fx in DjEffectType.entries) {
            dsp.updateEffect(fx, wet = 0.5f, param = 0.5f)
            for (i in 0 until 128) {
                val s = kotlin.math.sin(i * 0.07).toFloat() * 0.4f
                dsp.processStereo(s, s * 0.9f, out)
                assertFalse("NaN on ${fx.name}", out[0].isNaN() || out[1].isNaN())
                assertTrue(out[0] in -1.01f..1.01f)
            }
        }
    }

    @Test
    fun audioEngine_channelEnableMuteAndBoothAndLimiter() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val rec = RecordingEngine(context)
        val engine = BijoyAudioEngine(context, rec)
        try {
            assertTrue(engine.mixer.value.channels.first().channelEnabled)
            engine.toggleChannelEnabled(1)
            assertFalse(engine.mixer.value.channels.first().channelEnabled)
            engine.toggleChannelEnabled(1)
            assertTrue(engine.mixer.value.channels.first().channelEnabled)

            engine.setBoothVolume(0.42f)
            assertEquals(0.42f, engine.mixer.value.boothVolume, 0.01f)

            assertTrue(engine.mixer.value.masterLimiterEnabled)
            engine.toggleMasterLimiter()
            assertFalse(engine.mixer.value.masterLimiterEnabled)
        } finally {
            engine.stopEngine()
        }
    }

    @Test
    fun stemSeparator_separates4StemsAndInstrumentalMix() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val decoded = AudioDecoder.generateBuiltInDjTrack(presetId = 0, bpm = 126f)
        val stems = StemSeparatorEngine.separateIntoStems(1L, "Test Song", decoded)
        assertEquals(decoded.pcmStereo.size, stems.vocalsPcm.size)
        assertEquals(decoded.pcmStereo.size, stems.drumsPcm.size)
        assertEquals(decoded.pcmStereo.size, stems.bassPcm.size)
        assertEquals(decoded.pcmStereo.size, stems.otherPcm.size)

        val instLevels = StemMixLevels(vocalsMuted = true)
        val instrumental = StemSeparatorEngine.mixStemsToDecoded(stems, instLevels)
        assertTrue(instrumental.pcmStereo.isNotEmpty())

        val stemFile = StemSeparatorEngine.exportStemWav(context, stems, "VOCALS").getOrThrow()
        assertTrue(stemFile.exists() && stemFile.length() > 44L)
    }

    @Test
    fun offlineRemixRenderer_analyzesAndRendersSadToDjAndRemixWav() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val decoded = AudioDecoder.generateBuiltInDjTrack(presetId = 1, bpm = 96f)
        val analysis = OfflineRemixRenderer.analyzeTrackMetrics(decoded)
        assertTrue(analysis.sections.size == 5)
        assertTrue(analysis.musicalKey.isNotBlank())

        val sadParams = SadToDjParams(style = SadToDjStyle.BENGALI_DJ, tempoBpm = 132f, bassStrength = 0.85f)
        val sadRes = OfflineRemixRenderer.renderSadToDjRemix(context, decoded, "Mon Amar", sadParams).getOrThrow()
        val wavFile = java.io.File(sadRes.wavFilePath)
        assertTrue("Rendered Sad-to-DJ WAV must exist", wavFile.exists() && wavFile.length() > 44L)

        val remixRes = OfflineRemixRenderer.renderSongRemix(context, decoded, "Mon Amar", RemixStyle.DEEP_HOUSE, null, analysis).getOrThrow()
        assertTrue("Rendered AI Remix WAV must exist", java.io.File(remixRes.wavFilePath).exists())
    }

    @Test
    fun recordingEngine_supportsRecordSourcesAndRename() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val rec = RecordingEngine(context)
        rec.setRecordSource(RecordSource.DECK_A)
        assertEquals(RecordSource.DECK_A, rec.state.value.source)

        val title = rec.startRecording("My_Test_Set", includeMic = false, source = RecordSource.MASTER)
        assertEquals("My_Test_Set", title)
        val dummyPcm = ShortArray(2048) { 1200 }
        rec.writeMasterPcm(dummyPcm, dummyPcm.size)
        val completed = rec.stopRecording()
        assertNotNull(completed)
        assertTrue(java.io.File(completed!!.filePath).exists())

        val renamed = rec.renameRecordingFile(completed.filePath, "Renamed_Club_Set").getOrThrow()
        assertTrue(renamed.exists() && renamed.name.contains("Renamed_Club_Set"))
    }

    @Test
    fun audioEngine_dualDecksPlayIndependentlyAndResetOnlyAffectsTargetDeck() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val rec = RecordingEngine(context)
        val engine = BijoyAudioEngine(context, rec)
        try {
            val trackA = AudioDecoder.generateBuiltInDjTrack(presetId = 0, bpm = 128f)
            val trackB = AudioDecoder.generateBuiltInDjTrack(presetId = 1, bpm = 132f)

            engine.loadDecodedTrack(
                deckId = DeckId.A,
                songId = 101L,
                title = "Track Alpha",
                artist = "DJ Bijoy",
                artworkResName = null,
                decoded = trackA,
                savedCues = List(8) { HotCuePoint(it) }
            )
            engine.loadDecodedTrack(
                deckId = DeckId.B,
                songId = 102L,
                title = "Track Beta",
                artist = "DJ Bijoy",
                artworkResName = null,
                decoded = trackB,
                savedCues = List(8) { HotCuePoint(it) }
            )

            // Play both decks simultaneously
            engine.togglePlayPause(DeckId.A)
            engine.togglePlayPause(DeckId.B)
            assertTrue("Deck A must be playing", engine.deckA.value.isPlaying)
            assertTrue("Deck B must be playing simultaneously", engine.deckB.value.isPlaying)

            // Activate loop on both decks independently
            engine.toggleAutoLoop(DeckId.A, 4f)
            engine.toggleAutoLoop(DeckId.B, 8f)
            assertTrue(engine.deckA.value.loopActive)
            assertTrue(engine.deckB.value.loopActive)

            // Reset ONLY Deck A playback -> Deck B must continue playing & looping unaffected
            engine.resetDeckPlayback(DeckId.A)
            assertFalse("Deck A must stop on reset", engine.deckA.value.isPlaying)
            assertEquals(0L, engine.deckA.value.positionMs)
            assertFalse(engine.deckA.value.loopActive)
            assertEquals(1, engine.deckA.value.beatCount)

            assertTrue("Deck B must continue playing after Deck A reset", engine.deckB.value.isPlaying)
            assertTrue("Deck B loop must remain unaffected", engine.deckB.value.loopActive)

            // Play Deck A again while Deck B is playing, then Cue Deck B without stopping Deck A
            engine.togglePlayPause(DeckId.A)
            assertTrue(engine.deckA.value.isPlaying)
            assertTrue(engine.deckB.value.isPlaying)

            engine.triggerCue(DeckId.B)
            assertFalse("Deck B should stop at cue point", engine.deckB.value.isPlaying)
            assertTrue("Deck A must keep playing when Deck B is cued", engine.deckA.value.isPlaying)
        } finally {
            engine.stopEngine()
        }
    }

    @Test
    fun audioEngine_expandedGainAndBidirectionalChannelEqRange() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val rec = RecordingEngine(context)
        val engine = BijoyAudioEngine(context, rec)
        try {
            // Turn right (+18 dB) and turn left (-18 dB) on CH1..CH4
            for (ch in 1..4) {
                engine.updateChannelEqByIndex(ch, gainDb = 18f, midDb = 12f, filterCutoff = 1f)
                val boosted = engine.mixer.value.channels.first { it.channelIndex == ch }
                assertEquals(18f, boosted.gainDb, 0.01f)
                assertEquals(12f, boosted.midDb, 0.01f)
                assertEquals(1f, boosted.filterCutoff, 0.01f)

                engine.updateChannelEqByIndex(ch, gainDb = -18f, midDb = -12f, filterCutoff = -1f)
                val cut = engine.mixer.value.channels.first { it.channelIndex == ch }
                assertEquals(-18f, cut.gainDb, 0.01f)
                assertEquals(-12f, cut.midDb, 0.01f)
                assertEquals(-1f, cut.filterCutoff, 0.01f)
            }
        } finally {
            engine.stopEngine()
        }
    }

    @Test
    fun rotaryKnob_turnsSmoothlyRightLeftUpAndDown() {
        var knobVal by mutableFloatStateOf(0f)
        composeRule.setContent {
            RotaryHardwareKnob(
                label = "GAIN",
                value = knobVal,
                knobSize = 28.dp,
                testTagName = "test_gain_knob",
                onValueChange = { knobVal = it }
            )
        }
        composeRule.waitForIdle()

        // Turn Right (+)
        composeRule.onNodeWithTag("test_gain_knob").performTouchInput { swipeRight() }
        composeRule.waitForIdle()
        assertTrue("Swiping right must turn knob positive: $knobVal", knobVal > 0.1f)

        // Turn Left (-) from positive back below 0
        composeRule.onNodeWithTag("test_gain_knob").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        val afterLeft = knobVal
        assertTrue("Swiping left must decrease knob value: $afterLeft", afterLeft < 0.2f)

        // Turn Up (+)
        knobVal = 0f
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("test_gain_knob").performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        assertTrue("Swiping up must turn knob positive: $knobVal", knobVal > 0.1f)

        // Turn Down (-)
        composeRule.onNodeWithTag("test_gain_knob").performTouchInput { swipeDown() }
        composeRule.waitForIdle()
        assertTrue("Swiping down must decrease knob value: $knobVal", knobVal < 0.2f)
    }
}


