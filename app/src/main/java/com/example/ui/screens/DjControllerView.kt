package com.example.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.*
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.viewmodel.BijoyViewModel

/**
 * Main Pioneer-style Dual-Deck DJ Controller optimized for 16:9 Landscape Studio on launch,
 * with responsive support for compact/portrait widths and Fullscreen Mixer Mode.
 */
@Composable
fun DjControllerConsole(
    deckA: DeckState,
    deckB: DeckState,
    mixer: MixerState,
    isFullscreenMixer: Boolean,
    viewModel: BijoyViewModel,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF151B28), Color(0xFF0A0D15), Color(0xFF101520))
                )
            )
            .border(1.5.dp, Color(0xFF2A354D), RoundedCornerShape(10.dp))
            .padding(if (isFullscreenMixer) 6.dp else 4.dp)
    ) {
        val isWide = maxWidth >= 520.dp

        if (isWide) {
            // 16:9 Widescreen Pioneer Console: [Deck A | DJM-A9 BIJOY MIXER | Deck B]
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DeckHardwarePanel(
                    deck = deckA,
                    accent = DeckACyan,
                    isFullscreen = isFullscreenMixer,
                    viewModel = viewModel,
                    modifier = Modifier
                        .weight(1.02f)
                        .fillMaxHeight()
                )

                CentralMixerPanel(
                    deckA = deckA,
                    deckB = deckB,
                    mixer = mixer,
                    isFullscreen = isFullscreenMixer,
                    viewModel = viewModel,
                    modifier = Modifier
                        .weight(1.26f)
                        .fillMaxHeight()
                )

                DeckHardwarePanel(
                    deck = deckB,
                    accent = DeckBRed,
                    isFullscreen = isFullscreenMixer,
                    viewModel = viewModel,
                    modifier = Modifier
                        .weight(1.02f)
                        .fillMaxHeight()
                )
            }
        } else {
            // Portrait Fallback: Scrollable Deck A -> DJM-A9 Mixer -> Deck B
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DeckHardwarePanel(
                    deck = deckA,
                    accent = DeckACyan,
                    isFullscreen = isFullscreenMixer,
                    viewModel = viewModel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                )
                CentralMixerPanel(
                    deckA = deckA,
                    deckB = deckB,
                    mixer = mixer,
                    isFullscreen = isFullscreenMixer,
                    viewModel = viewModel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(270.dp)
                )
                DeckHardwarePanel(
                    deck = deckB,
                    accent = DeckBRed,
                    isFullscreen = isFullscreenMixer,
                    viewModel = viewModel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeckHardwarePanel(
    deck: DeckState,
    accent: Color,
    isFullscreen: Boolean,
    viewModel: BijoyViewModel,
    modifier: Modifier = Modifier
) {
    val engine = viewModel.audioEngine
    val tagPrefix = if (deck.deckId == DeckId.A) "deck_a" else "deck_b"

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0D111B))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(4.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Row 1: Deck Mode Toggles
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HardwareLedButton("KEY", deck.keyLock, accent, "${tagPrefix}_key_lock", Modifier.weight(1f)) {
                engine.toggleKeyLock(deck.deckId)
            }
            HardwareLedButton("SCR", deck.scratchMode, DeckBOrange, "${tagPrefix}_scratch", Modifier.weight(1f)) {
                engine.toggleScratchMode(deck.deckId)
            }
            HardwareLedButton("SLIP", deck.slipMode, LedPurple, "${tagPrefix}_slip", Modifier.weight(1f)) {
                engine.toggleSlipMode(deck.deckId)
            }
            HardwareLedButton(
                text = if (deck.activeEffect == DjEffectType.NONE) "FX" else deck.activeEffect.displayName.take(4).uppercase(),
                active = deck.activeEffect != DjEffectType.NONE,
                activeColor = LedPurple,
                testTagName = "${tagPrefix}_fx_cycle",
                modifier = Modifier.weight(1f)
            ) {
                val all = DjEffectType.entries
                val next = all[(all.indexOf(deck.activeEffect) + 1) % all.size]
                engine.setDeckEffect(deck.deckId, next)
            }
        }

        // Row 2: Loop Controls Strip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            HardwareLedButton("IN", deck.loopInMs != null, LedAmber, "${tagPrefix}_loop_in", Modifier.weight(1f)) {
                engine.setLoopIn(deck.deckId)
            }
            HardwareLedButton("OUT", deck.loopActive, LedAmber, "${tagPrefix}_loop_out", Modifier.weight(1f)) {
                engine.setLoopOut(deck.deckId)
            }
            HardwareLedButton("EXIT", false, DeckBRed, "${tagPrefix}_loop_exit", Modifier.weight(1f)) {
                engine.exitLoop(deck.deckId)
            }
            HardwareLedButton(
                "${deck.autoLoopBeats.toInt()}B LP",
                deck.loopActive,
                LedGreen,
                "${tagPrefix}_auto_loop",
                Modifier.weight(1.25f)
            ) {
                engine.toggleAutoLoop(deck.deckId, deck.autoLoopBeats)
            }
        }

        // Row 3: Large Metallic Jog Wheel + Pitch/Tempo Slider
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                MetallicJogWheel(
                    angleDeg = deck.jogAngleDeg,
                    isPlaying = deck.isPlaying,
                    accentColor = accent,
                    artworkResName = deck.artworkResName,
                    bpm = deck.effectiveBpm,
                    deckTag = tagPrefix,
                    onTouchStart = { engine.onJogTouchStart(deck.deckId) },
                    onDragAngle = { delta -> engine.onJogDrag(deck.deckId, delta) },
                    onTouchEnd = { engine.onJogTouchEnd(deck.deckId) },
                    modifier = Modifier.fillMaxHeight(if (isFullscreen) 0.96f else 0.92f)
                )
            }

            Column(
                modifier = Modifier
                    .width(if (isFullscreen) 38.dp else 33.dp)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = String.format("%+.1f%%", deck.pitchPercent),
                    color = accent,
                    fontSize = 7.sp,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold
                )
                VerticalHardwareFader(
                    value = ((deck.pitchPercent + 16f) / 32f).coerceIn(0f, 1f),
                    accentColor = accent,
                    testTagName = "${tagPrefix}_pitch_fader",
                    centerDetent = true,
                    modifier = Modifier.weight(1f)
                ) { norm ->
                    val pct = (norm * 32f) - 16f
                    engine.setPitchPercent(deck.deckId, pct)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    MiniPitchBendBtn("-", "${tagPrefix}_bend_minus") {
                        engine.setPitchPercent(deck.deckId, deck.pitchPercent - 0.5f)
                    }
                    MiniPitchBendBtn("+", "${tagPrefix}_bend_plus") {
                        engine.setPitchPercent(deck.deckId, deck.pitchPercent + 0.5f)
                    }
                }
            }
        }

        // Row 4: 4 Colorful RGB Hot Cue Performance Pads
        val padColors = listOf(LedRed, LedAmber, DeckACyan, LedPurple)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            deck.hotCues.forEachIndexed { idx, cue ->
                val pColor = padColors.getOrElse(idx) { accent }
                val isSet = cue.positionMs != null
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(if (isFullscreen) 30.dp else 24.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (isSet) pColor.copy(alpha = 0.38f) else Color(0xFF141B2B))
                        .border(
                            width = if (isSet) 1.5.dp else 1.dp,
                            color = if (isSet) pColor else pColor.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(5.dp)
                        )
                        .testTag("${tagPrefix}_hot_cue_${idx + 1}")
                        .combinedClickable(
                            onClick = { viewModel.onHotCuePress(deck.deckId, idx) },
                            onLongClick = { viewModel.onHotCueClear(deck.deckId, idx) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isSet) "HC ${idx + 1}" else "SET ${idx + 1}",
                        color = if (isSet) Color.White else pColor.copy(alpha = 0.85f),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = JetBrainsMonoFontFamily
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniPitchBendBtn(symbol: String, tag: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(15.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0xFF1B2436))
            .border(0.8.dp, MetalBorder, RoundedCornerShape(3.dp))
            .clickable { onClick() }
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, color = TextPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * Pioneer DJM-A9 Inspired Central BIJOY MIXER:
 * - Left Column: SOUND COLOR FX (SPACE, ECHO, SWEEP, NOISE, CRUSH, FILTER + PARAMETER knob)
 * - Center Columns: CH 1 (Deck A) & CH 2 (Deck B) with TRIM/GAIN, HI, MID, LOW, Metallic COLOR knob,
 *   Per-Channel LED VU Meter, Orange CUE button, Blue BEAT FX assign button, and Channel Volume Fader
 * - Right Column: MASTER LEVEL knob, Dual Stereo MASTER VU Meters, BEAT FX OLED Display,
 *   Beat Ratio buttons (1/2, 1, 2), LEVEL/DEPTH knob, and glowing blue Beat FX ON/OFF button.
 */
@Composable
private fun CentralMixerPanel(
    deckA: DeckState,
    deckB: DeckState,
    mixer: MixerState,
    isFullscreen: Boolean,
    viewModel: BijoyViewModel,
    modifier: Modifier = Modifier
) {
    val engine = viewModel.audioEngine
    val knobSize = if (isFullscreen) 26.dp else 22.dp
    val colorKnobSize = if (isFullscreen) 32.dp else 27.dp

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF080B12))
            .border(1.2.dp, MetalBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 4.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // DJM-A9 Style Header Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("COLOR FX", color = TextMuted, fontSize = 7.sp, fontFamily = JetBrainsMonoFontFamily)
            Text(
                text = "BIJOY MIXER",
                color = MetalSilverLight,
                fontFamily = OrbitronFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                letterSpacing = 1.1.sp
            )
            Text("BEAT FX", color = DeckACyan, fontSize = 7.sp, fontFamily = JetBrainsMonoFontFamily)
        }

        Spacer(Modifier.height(2.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. LEFT COLUMN: SOUND COLOR FX RACK (matches DJM-A9 left bank)
            Column(
                modifier = Modifier
                    .weight(0.76f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(0xFF0D121D))
                    .border(0.8.dp, Color(0xFF1F2A3F), RoundedCornerShape(5.dp))
                    .padding(3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text("SND COLOR", color = TextSecondary, fontSize = 6.5.sp, fontFamily = JetBrainsMonoFontFamily, fontWeight = FontWeight.Bold)
                SoundColorFxType.entries.forEach { fx ->
                    val active = mixer.activeColorFx == fx
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (active) DeckACyan.copy(alpha = 0.28f) else Color(0xFF141B29))
                            .border(
                                width = if (active) 1.dp else 0.6.dp,
                                color = if (active) DeckACyan else MetalBorder,
                                shape = RoundedCornerShape(3.dp)
                            )
                            .clickable { engine.setSoundColorFx(fx) }
                            .testTag("color_fx_${fx.name.lowercase()}")
                            .padding(vertical = 2.5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = fx.shortLabel,
                            color = if (active) Color.White else TextSecondary,
                            fontSize = 6.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = JetBrainsMonoFontFamily,
                            maxLines = 1
                        )
                    }
                }
                RotaryHardwareKnob(
                    label = "PARAM",
                    value = (mixer.colorFxParam * 2f) - 1f,
                    accentColor = DeckACyan,
                    knobSize = knobSize,
                    testTagName = "color_fx_param",
                    onValueChange = { engine.setColorFxParam((it + 1f) * 0.5f) },
                    onReset = { engine.setColorFxParam(0.5f) }
                )
            }

            // 2. CHANNEL 1 STRIP (Deck A)
            MixerChannelStrip(
                channelNum = "1",
                deck = deckA,
                accent = DeckACyan,
                knobSize = knobSize,
                colorKnobSize = colorKnobSize,
                onGain = { engine.updateDeckEq(DeckId.A, gainDb = it * 12f) },
                onHi = { engine.updateDeckEq(DeckId.A, trebleDb = it * 12f) },
                onMid = { engine.updateDeckEq(DeckId.A, midDb = it * 12f) },
                onLow = { engine.updateDeckEq(DeckId.A, bassDb = it * 12f) },
                onColor = { engine.updateDeckEq(DeckId.A, filterCutoff = it) },
                onCue = { engine.toggleHeadphoneCue(DeckId.A) },
                onBeatFx = { engine.toggleBeatFxAssign(DeckId.A) },
                onFader = { engine.setChannelVolume(DeckId.A, it) },
                modifier = Modifier
                    .weight(1.0f)
                    .fillMaxHeight()
            )

            // 3. CHANNEL 2 STRIP (Deck B)
            MixerChannelStrip(
                channelNum = "2",
                deck = deckB,
                accent = DeckBRed,
                knobSize = knobSize,
                colorKnobSize = colorKnobSize,
                onGain = { engine.updateDeckEq(DeckId.B, gainDb = it * 12f) },
                onHi = { engine.updateDeckEq(DeckId.B, trebleDb = it * 12f) },
                onMid = { engine.updateDeckEq(DeckId.B, midDb = it * 12f) },
                onLow = { engine.updateDeckEq(DeckId.B, bassDb = it * 12f) },
                onColor = { engine.updateDeckEq(DeckId.B, filterCutoff = it) },
                onCue = { engine.toggleHeadphoneCue(DeckId.B) },
                onBeatFx = { engine.toggleBeatFxAssign(DeckId.B) },
                onFader = { engine.setChannelVolume(DeckId.B, it) },
                modifier = Modifier
                    .weight(1.0f)
                    .fillMaxHeight()
            )

            // 4. RIGHT COLUMN: MASTER LEVEL + VU METERS + BEAT FX OLED + ON/OFF
            Column(
                modifier = Modifier
                    .weight(1.04f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(0xFF0D121D))
                    .border(0.8.dp, Color(0xFF1F2A3F), RoundedCornerShape(5.dp))
                    .padding(3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top: Master Level Knob + Dual Master Stereo VU Meters
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.46f),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        RotaryHardwareKnob(
                            label = "MASTER",
                            value = (mixer.masterVolume * 2f) - 1f,
                            accentColor = DeckBGold,
                            knobSize = knobSize,
                            testTagName = "mixer_master_knob",
                            onValueChange = { engine.setMasterVolume((it + 1f) * 0.5f) },
                            onReset = { engine.setMasterVolume(0.85f) }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxHeight(0.9f),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        VerticalVuMeter(level = mixer.masterLevelLeft, segments = 14, modifier = Modifier.fillMaxHeight())
                        VerticalVuMeter(level = mixer.masterLevelRight, segments = 14, modifier = Modifier.fillMaxHeight())
                    }
                }

                // Middle: DJM-A9 Beat FX OLED Screen (tap to cycle FX)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF030509))
                        .border(1.dp, DeckABlue.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                        .clickable { engine.cycleBeatFxType() }
                        .testTag("beat_fx_oled")
                        .padding(horizontal = 4.dp, vertical = 3.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = mixer.beatFxType.displayName.uppercase(),
                            color = Color.White,
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = JetBrainsMonoFontFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${String.format("%.0f", deckA.effectiveBpm)}",
                            color = DeckACyan,
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = JetBrainsMonoFontFamily
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        listOf("1/2", "1", "2").forEach { ratio ->
                            val sel = mixer.beatFxRatio == ratio
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(if (sel) DeckABlue else Color(0xFF141B2B))
                                    .clickable { engine.setBeatFxRatio(ratio) }
                                    .padding(vertical = 1.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(ratio, color = Color.White, fontSize = 6.5.sp, fontFamily = JetBrainsMonoFontFamily)
                            }
                        }
                    }
                }

                // Level / Depth Knob
                RotaryHardwareKnob(
                    label = "DEPTH",
                    value = (mixer.masterEffectWet * 2f) - 1f,
                    accentColor = DeckABlue,
                    knobSize = knobSize,
                    testTagName = "beat_fx_depth_knob",
                    onValueChange = { engine.setBeatFxLevel((it + 1f) * 0.5f) },
                    onReset = { engine.setBeatFxLevel(0.4f) }
                )

                // Signature DJM-A9 Glowing Blue Beat FX ON/OFF Button
                Box(
                    modifier = Modifier
                        .size(if (isFullscreen) 32.dp else 26.dp)
                        .clip(CircleShape)
                        .background(
                            if (mixer.beatFxOn) DeckABlue else Color(0xFF13223D)
                        )
                        .border(
                            width = 1.5.dp,
                            color = if (mixer.beatFxOn) DeckACyan else DeckABlue.copy(alpha = 0.6f),
                            shape = CircleShape
                        )
                        .clickable { engine.toggleBeatFxOn() }
                        .testTag("beat_fx_on_off_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (mixer.beatFxOn) "ON" else "FX",
                        color = Color.White,
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = JetBrainsMonoFontFamily
                    )
                }
            }
        }
    }
}

@Composable
private fun MixerChannelStrip(
    channelNum: String,
    deck: DeckState,
    accent: Color,
    knobSize: Dp,
    colorKnobSize: Dp,
    onGain: (Float) -> Unit,
    onHi: (Float) -> Unit,
    onMid: (Float) -> Unit,
    onLow: (Float) -> Unit,
    onColor: (Float) -> Unit,
    onCue: () -> Unit,
    onBeatFx: () -> Unit,
    onFader: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val chTag = if (channelNum == "1") "ch_a" else "ch_b"
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(Color(0xFF0C101B))
            .border(0.8.dp, Color(0xFF1F2A3F), RoundedCornerShape(5.dp))
            .padding(horizontal = 2.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Channel Header + Knobs & Per-Channel LED Meter side-by-side
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.15f),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxHeight()
            ) {
                RotaryHardwareKnob("TRIM", deck.gainDb / 12f, accent, knobSize, "${chTag}_gain", onGain)
                RotaryHardwareKnob("HI", deck.trebleDb / 12f, accent, knobSize, "${chTag}_hi", onHi)
                RotaryHardwareKnob("MID", deck.midDb / 12f, accent, knobSize, "${chTag}_mid", onMid)
                RotaryHardwareKnob("LOW", deck.bassDb / 12f, accent, knobSize, "${chTag}_low", onLow)
            }
            VerticalVuMeter(
                level = (deck.levelLeft + deck.levelRight) * 0.5f,
                segments = 12,
                modifier = Modifier.fillMaxHeight(0.88f)
            )
        }

        // Large Metallic Sound Color FX Knob
        RotaryHardwareKnob("COLOR", deck.filterCutoff, DeckACyan, colorKnobSize, "${chTag}_color", onColor)

        // Orange CUE + Blue BEAT FX Assign Buttons (exact DJM-A9 colors)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (deck.headphoneCue) DeckBOrange else Color(0xFF2B1C10))
                    .border(0.8.dp, DeckBOrange, RoundedCornerShape(3.dp))
                    .clickable { onCue() }
                    .testTag("hp_cue_${if (channelNum == "1") "a" else "b"}")
                    .padding(vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("CUE", color = Color.White, fontSize = 6.5.sp, fontWeight = FontWeight.Bold, fontFamily = JetBrainsMonoFontFamily)
            }
            Box(
                modifier = Modifier
                    .weight(0.8f)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (deck.beatFxAssigned) DeckABlue else Color(0xFF101C2E))
                    .border(0.8.dp, DeckABlue, RoundedCornerShape(3.dp))
                    .clickable { onBeatFx() }
                    .padding(vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("FX", color = Color.White, fontSize = 6.5.sp, fontWeight = FontWeight.Bold, fontFamily = JetBrainsMonoFontFamily)
            }
        }

        // Channel Fader + Channel Number
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(0.75f)
        ) {
            VerticalHardwareFader(
                value = deck.volume,
                accentColor = accent,
                testTagName = "channel_${if (channelNum == "1") "a" else "b"}_volume_fader",
                modifier = Modifier.weight(1f),
                onValueChange = onFader
            )
            Text("CH $channelNum", color = accent, fontSize = 7.sp, fontWeight = FontWeight.Bold, fontFamily = JetBrainsMonoFontFamily)
        }
    }
}
