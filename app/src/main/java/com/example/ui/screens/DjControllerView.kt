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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audio.*
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.viewmodel.BijoyViewModel
import kotlin.math.abs

/**
 * BIJOY DJ — Exact Reference-Matched Dual-Deck + 4-Channel Central Mixer Console:
 * - Left Deck A & Right Deck B:
 *   Top Loop Bar (IN, OUT, 1/2, 1, 2, 4, 8, 16)
 *   Second Row (RELOOP/EXIT, Jog Mode Circle, PITCH BEND -/+, KEY LOCK)
 *   Center: Left Utility Knobs | Large Brushed Metallic Jog Wheel (Blue RGB ring + DJ emblem) | Right Pitch/Tempo Slider
 *   Bottom: Mode Selector (HOT CUE | SAMPLER | ROLL | SLICER | LOOP | CUE LP) + 2x4 Large Colored Performance Pads
 * - Central 4-Channel Mixer ("BIJOY DJ"):
 *   Left Utility Column | Channels 1, 2, 3, 4 (Meter, GAIN, TREBLE, MID, BASS, FILTER, CUE, Volume Fader) | Right Master/Meter Column
 *   Bottom: Horizontal Crossfader (< A | B >)
 */
@Composable
fun DjControllerConsole(
    deckA: DeckState,
    deckB: DeckState,
    mixer: MixerState,
    mixerScale: Float,
    isFullscreenMixer: Boolean,
    viewModel: BijoyViewModel,
    modifier: Modifier = Modifier
) {
    val sfxPads by viewModel.sfxPads.collectAsStateWithLifecycle()

    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF141A27), Color(0xFF070A12), Color(0xFF0E131E))
                )
            )
            .border(1.4.dp, Color(0xFF2B364F), RoundedCornerShape(10.dp))
            .padding(if (isFullscreenMixer) 4.dp else 3.dp)
    ) {
        val isWide = maxWidth >= 560.dp
        val clampedMixerScale = mixerScale.coerceIn(0.85f, 1.45f)

        if (isWide) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // LEFT DECK A
                BijoyDeckConsole(
                    deck = deckA,
                    accent = DeckACyan,
                    isActiveDeck = mixer.activeDeck == DeckId.A,
                    isFullscreen = isFullscreenMixer,
                    sfxPads = sfxPads,
                    viewModel = viewModel,
                    modifier = Modifier
                        .weight(1.0f)
                        .fillMaxHeight()
                )

                // CENTRAL 4-CHANNEL MIXER
                FourChannelCentralMixer(
                    deckA = deckA,
                    deckB = deckB,
                    mixer = mixer,
                    isFullscreen = isFullscreenMixer,
                    viewModel = viewModel,
                    modifier = Modifier
                        .weight(1.30f * clampedMixerScale)
                        .fillMaxHeight()
                )

                // RIGHT DECK B
                BijoyDeckConsole(
                    deck = deckB,
                    accent = DeckBPink,
                    isActiveDeck = mixer.activeDeck == DeckId.B,
                    isFullscreen = isFullscreenMixer,
                    sfxPads = sfxPads,
                    viewModel = viewModel,
                    modifier = Modifier
                        .weight(1.0f)
                        .fillMaxHeight()
                )
            }
        } else {
            // Responsive Portrait Stack without losing any controls
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BijoyDeckConsole(
                    deck = deckA,
                    accent = DeckACyan,
                    isActiveDeck = mixer.activeDeck == DeckId.A,
                    isFullscreen = isFullscreenMixer,
                    sfxPads = sfxPads,
                    viewModel = viewModel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                )
                FourChannelCentralMixer(
                    deckA = deckA,
                    deckB = deckB,
                    mixer = mixer,
                    isFullscreen = isFullscreenMixer,
                    viewModel = viewModel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                )
                BijoyDeckConsole(
                    deck = deckB,
                    accent = DeckBPink,
                    isActiveDeck = mixer.activeDeck == DeckId.B,
                    isFullscreen = isFullscreenMixer,
                    sfxPads = sfxPads,
                    viewModel = viewModel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BijoyDeckConsole(
    deck: DeckState,
    accent: Color,
    isActiveDeck: Boolean,
    isFullscreen: Boolean,
    sfxPads: List<SfxPadUi>,
    viewModel: BijoyViewModel,
    modifier: Modifier = Modifier
) {
    val engine = viewModel.audioEngine
    val tagPrefix = if (deck.deckId == DeckId.A) "deck_a" else "deck_b"

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0A0D16))
            .border(
                width = if (isActiveDeck) 1.3.dp else 0.8.dp,
                color = if (isActiveDeck) accent.copy(alpha = 0.65f) else MetalBorder,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(3.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 1. TOP LOOP CONTROL BAR ABOVE JOG WHEEL: Yellow IN | OUT | 1/2 | 1 | 2 | 4 | 8 | 16
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            YellowHardwareBtn("IN", deck.loopInMs != null, "${tagPrefix}_loop_in", Modifier.weight(1f)) {
                engine.setLoopIn(deck.deckId)
            }
            YellowHardwareBtn("OUT", deck.loopActive, "${tagPrefix}_loop_out", Modifier.weight(1f)) {
                engine.setLoopOut(deck.deckId)
            }
            val loopBeats = listOf(
                "1/2" to 0.5f,
                "1" to 1f,
                "2" to 2f,
                "4" to 4f,
                "8" to 8f,
                "16" to 16f
            )
            loopBeats.forEach { (label, beats) ->
                val active = deck.loopActive && abs(deck.autoLoopBeats - beats) < 0.01f
                Box(
                    modifier = Modifier
                        .weight(0.85f)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (active) LedAmber.copy(alpha = 0.35f) else Color(0xFF151C2B))
                        .border(0.8.dp, if (active) LedAmber else MetalBorder, RoundedCornerShape(3.dp))
                        .clickable { engine.toggleAutoLoop(deck.deckId, beats) }
                        .testTag("${tagPrefix}_loop_len_${label.replace("/", "_")}")
                        .padding(vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (active) Color.White else TextPrimary,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = JetBrainsMonoFontFamily,
                        maxLines = 1
                    )
                }
            }
        }

        // 2. SECOND ROW ABOVE JOG WHEEL: RELOOP/EXIT | Orange Jog Mode Circle | PITCH BEND (- / +) | KEY LOCK
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 1.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (deck.loopActive) LedGreen.copy(alpha = 0.22f) else Color(0xFF141B29))
                    .border(0.8.dp, if (deck.loopActive) LedGreen else MetalBorder, RoundedCornerShape(4.dp))
                    .clickable { engine.toggleReloop(deck.deckId) }
                    .testTag("${tagPrefix}_reloop_exit")
                    .padding(horizontal = 5.dp, vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (deck.loopActive) "EXIT" else "RELOOP",
                    color = if (deck.loopActive) LedGreen else TextPrimary,
                    fontSize = 6.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = JetBrainsMonoFontFamily
                )
                Text("/ EXIT", color = TextMuted, fontSize = 5.5.sp, fontFamily = JetBrainsMonoFontFamily)
            }

            // Center Orange Circular Jog Mode Switch (Vinyl / CDJ / Search)
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF28190A))
                    .border(1.5.dp, DeckBOrange, CircleShape)
                    .clickable { engine.cycleJogMode(deck.deckId) }
                    .testTag("${tagPrefix}_jog_mode_btn"),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Jog Mode: ${deck.jogMode.displayName}", tint = DeckBOrange, modifier = Modifier.size(14.dp))
            }

            // Pitch Bend (- / +) + Key Lock
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("PITCH BEND", color = TextSecondary, fontSize = 5.5.sp, fontWeight = FontWeight.Bold, fontFamily = JetBrainsMonoFontFamily)
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        PitchBendPill("-", "${tagPrefix}_bend_minus") {
                            engine.setPitchPercent(deck.deckId, deck.pitchPercent - 0.5f)
                        }
                        PitchBendPill("+", "${tagPrefix}_bend_plus") {
                            engine.setPitchPercent(deck.deckId, deck.pitchPercent + 0.5f)
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(if (deck.keyLock) DeckBOrange.copy(alpha = 0.25f) else Color(0xFF141B29))
                        .border(1.3.dp, if (deck.keyLock) DeckBOrange else MetalBorder, CircleShape)
                        .clickable { engine.toggleKeyLock(deck.deckId) }
                        .testTag("${tagPrefix}_key_lock"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Lock, contentDescription = "Key Lock", tint = if (deck.keyLock) DeckBGold else TextSecondary, modifier = Modifier.size(12.dp))
                }
            }
        }

        // 3. MIDDLE SECTION: Left Mini Knobs/Cue | LARGE METALLIC JOG WHEEL | Right Pitch/Tempo Slider
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // Left Transport & Utility Strip beside Jog Wheel: Round PLAY, Round CUE, Round RESET + SLIP + FILT
            Column(
                modifier = Modifier
                    .width(if (isFullscreen) 42.dp else 37.dp)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                RoundHardwareTransportBtn(
                    isCircleIcon = true,
                    isPlaying = deck.isPlaying,
                    label = if (deck.isPlaying) "PAUSE" else "PLAY",
                    accentColor = LedGreen,
                    active = deck.isPlaying,
                    size = 28.dp,
                    tag = "${tagPrefix}_deck_play_btn"
                ) { engine.togglePlayPause(deck.deckId) }

                RoundHardwareTransportBtn(
                    isCircleIcon = false,
                    isPlaying = false,
                    label = "CUE",
                    accentColor = DeckBOrange,
                    active = deck.headphoneCue,
                    size = 24.dp,
                    tag = "${tagPrefix}_deck_cue_btn"
                ) { engine.triggerCue(deck.deckId) }

                RoundHardwareTransportBtn(
                    isCircleIcon = false,
                    isPlaying = false,
                    label = "RST",
                    accentColor = accent,
                    active = deck.positionMs == 0L && !deck.isPlaying,
                    size = 22.dp,
                    icon = BijoyIcons.RestartAlt,
                    tag = "${tagPrefix}_deck_reset_btn"
                ) { engine.resetDeckPlayback(deck.deckId) }

                MiniDeckBadge(
                    text = "SLIP",
                    active = deck.slipMode,
                    color = LedPurple,
                    tag = "${tagPrefix}_slip_btn"
                ) { engine.toggleSlipMode(deck.deckId) }

                RotaryHardwareKnob(
                    label = "FILT",
                    value = deck.filterCutoff,
                    accentColor = accent,
                    knobSize = 26.dp,
                    testTagName = "${tagPrefix}_mini_filter_knob",
                    onValueChange = { engine.updateDeckEq(deck.deckId, filterCutoff = it) }
                )
            }

            // Center LARGE Metallic Jog Wheel
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
                    jogMode = deck.jogMode,
                    onTouchStart = { engine.onJogTouchStart(deck.deckId) },
                    onDragAngle = { delta -> engine.onJogDrag(deck.deckId, delta) },
                    onTouchEnd = { engine.onJogTouchEnd(deck.deckId) },
                    modifier = Modifier.fillMaxHeight(0.98f)
                )
            }

            // Right Pitch & Tempo Slider Column (±6/10/16/50/100%, Slider, TEMPO Reset)
            Column(
                modifier = Modifier
                    .width(if (isFullscreen) 40.dp else 35.dp)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Pitch Range Tap Badge (-50% / ±50% etc.)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF0E1523))
                        .clickable { engine.cyclePitchRange(deck.deckId) }
                        .testTag("${tagPrefix}_pitch_range_btn")
                        .padding(vertical = 1.dp)
                ) {
                    Text(
                        text = if (abs(deck.pitchPercent) > 0.2f) String.format("%+.0f%%", deck.pitchPercent) else "-${deck.pitchRangePercent.toInt()}%",
                        color = accent,
                        fontSize = 6.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = JetBrainsMonoFontFamily,
                        maxLines = 1
                    )
                    Text(
                        text = "±${deck.pitchRangePercent.toInt()}%",
                        color = TextMuted,
                        fontSize = 5.5.sp,
                        fontFamily = JetBrainsMonoFontFamily
                    )
                }

                val range = deck.pitchRangePercent.coerceAtLeast(6f)
                VerticalHardwareFader(
                    value = ((deck.pitchPercent + range) / (range * 2f)).coerceIn(0f, 1f),
                    accentColor = accent,
                    testTagName = "${tagPrefix}_pitch_fader",
                    centerDetent = true,
                    modifier = Modifier.weight(1f)
                ) { norm ->
                    engine.setPitchPercent(deck.deckId, (norm * (range * 2f)) - range)
                }

                // Tempo Reset + Micro +/- Pitch buttons
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFF181014))
                            .border(0.7.dp, LedRed.copy(alpha = 0.7f), RoundedCornerShape(3.dp))
                            .clickable { engine.resetTempo(deck.deckId) }
                            .testTag("${tagPrefix}_tempo_reset")
                            .padding(vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("TEMPO", color = Color.White, fontSize = 5.5.sp, fontWeight = FontWeight.Bold, fontFamily = JetBrainsMonoFontFamily)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        MiniPitchStepBtn("-", "${tagPrefix}_tempo_minus") {
                            engine.setPitchPercent(deck.deckId, deck.pitchPercent - 1f)
                        }
                        MiniPitchStepBtn("+", "${tagPrefix}_tempo_plus") {
                            engine.setPitchPercent(deck.deckId, deck.pitchPercent + 1f)
                        }
                    }
                }
            }
        }

        // 4. BOTTOM PERFORMANCE PAD SECTION (Mode Selector + 2x4 Large Colored Pads)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF070A11))
                .border(0.8.dp, MetalBorder.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                .padding(3.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // Mode Selector Row: HOT CUE | SAMPLER | ROLL | SLICER (+ LOOP / CUE LOOP toggle)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val primaryModes = listOf(
                    PerformancePadMode.HOT_CUE,
                    PerformancePadMode.SAMPLER,
                    PerformancePadMode.ROLL,
                    PerformancePadMode.SLICER
                )
                primaryModes.forEach { mode ->
                    val selected = deck.padMode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (selected) accent.copy(alpha = 0.30f) else Color(0xFF131926))
                            .border(0.8.dp, if (selected) accent else MetalBorder, RoundedCornerShape(3.dp))
                            .clickable { engine.setPadMode(deck.deckId, mode) }
                            .testTag("${tagPrefix}_mode_${mode.name.lowercase()}")
                            .padding(vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mode.displayName,
                            color = if (selected) Color.White else TextSecondary,
                            fontSize = 5.8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = JetBrainsMonoFontFamily,
                            maxLines = 1
                        )
                    }
                }
                // Cycle between LOOP & CUE LOOP
                val loopSelected = deck.padMode == PerformancePadMode.LOOP || deck.padMode == PerformancePadMode.CUE_LOOP
                Box(
                    modifier = Modifier
                        .weight(0.85f)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (loopSelected) LedAmber.copy(alpha = 0.30f) else Color(0xFF131926))
                        .border(0.8.dp, if (loopSelected) LedAmber else MetalBorder, RoundedCornerShape(3.dp))
                        .clickable {
                            val next = if (deck.padMode == PerformancePadMode.LOOP) PerformancePadMode.CUE_LOOP else PerformancePadMode.LOOP
                            engine.setPadMode(deck.deckId, next)
                        }
                        .testTag("${tagPrefix}_mode_loop_cueloop")
                        .padding(vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (deck.padMode == PerformancePadMode.CUE_LOOP) "CUE LP" else "LOOP",
                        color = if (loopSelected) Color.White else TextSecondary,
                        fontSize = 5.8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = JetBrainsMonoFontFamily,
                        maxLines = 1
                    )
                }
            }

            // 2 ROWS x 4 COLUMNS PERFORMANCE PADS (Top: Red, Orange, Blue, Yellow | Bottom: Green, Purple, Cyan, Violet)
            val padColors = listOf(
                LedRed,              // Pad 1 - Red
                DeckBOrange,         // Pad 2 - Orange
                DeckABlue,           // Pad 3 - Blue
                DeckBGold,           // Pad 4 - Yellow
                LedGreen,            // Pad 5 - Green
                LedPurple,           // Pad 6 - Purple
                DeckACyan,           // Pad 7 - Cyan
                Color(0xFF9C27B0)    // Pad 8 - Violet
            )

            val padH = if (isFullscreen) 26.dp else 22.dp
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                for (row in 0..1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        for (col in 0..3) {
                            val idx = row * 4 + col
                            val pCol = padColors[idx]
                            val (label, isActive) = padLabelAndState(deck, idx, sfxPads)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(padH)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isActive) pCol else pCol.copy(alpha = 0.72f))
                                    .border(
                                        width = if (isActive) 1.5.dp else 0.8.dp,
                                        color = if (isActive) Color.White else pCol,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .combinedClickable(
                                        onClick = { viewModel.onPerformancePadPress(deck.deckId, idx) },
                                        onLongClick = { viewModel.onPerformancePadLongPress(deck.deckId, idx) }
                                    )
                                    .testTag("${tagPrefix}_perf_pad_${idx + 1}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (idx == 3 || idx == 4 || idx == 6) Color(0xFF070A12) else Color.White,
                                    fontSize = 6.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun padLabelAndState(deck: DeckState, idx: Int, sfxPads: List<SfxPadUi>): Pair<String, Boolean> {
    return when (deck.padMode) {
        PerformancePadMode.HOT_CUE -> {
            val cue = deck.hotCues.getOrNull(idx)
            val set = cue?.positionMs != null
            (if (set) "HC ${idx + 1}●" else "HC ${idx + 1}") to set
        }
        PerformancePadMode.SAMPLER -> {
            val s = sfxPads.getOrNull(idx)
            (s?.label?.take(5) ?: "S${idx + 1}") to (s?.isPlaying == true)
        }
        PerformancePadMode.ROLL -> {
            val labels = listOf("1/2", "1", "2", "4", "8", "16", "1/4", "1/8")
            val beats = listOf(0.5f, 1f, 2f, 4f, 8f, 16f, 0.25f, 0.125f)
            val active = deck.loopActive && deck.activeRollBeats == beats[idx]
            labels[idx] to active
        }
        PerformancePadMode.SLICER -> {
            "SL ${idx + 1}" to (deck.activeSlicerIndex == idx)
        }
        PerformancePadMode.LOOP -> {
            val labels = listOf("L1/2", "L1", "L2", "L4", "L8", "L16", "L1/4", "L32")
            val beats = listOf(0.5f, 1f, 2f, 4f, 8f, 16f, 0.25f, 32f)
            labels[idx] to (deck.loopActive && abs(deck.autoLoopBeats - beats[idx]) < 0.01f)
        }
        PerformancePadMode.CUE_LOOP -> {
            val set = deck.hotCues.getOrNull(idx)?.positionMs != null
            "CL ${idx + 1}" to (set && deck.loopActive)
        }
    }
}

@Composable
private fun YellowHardwareBtn(
    text: String,
    active: Boolean,
    tag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(if (active) CrownGold else Color(0xFFD4A017))
            .border(0.8.dp, Color(0xFFFFEE58), RoundedCornerShape(3.dp))
            .clickable { onClick() }
            .testTag(tag)
            .padding(vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color(0xFF0A0D14),
            fontSize = 7.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = JetBrainsMonoFontFamily,
            maxLines = 1
        )
    }
}

@Composable
private fun PitchBendPill(symbol: String, tag: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(20.dp)
            .height(14.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(if (symbol == "+") LedRed.copy(alpha = 0.85f) else Color(0xFF1F293D))
            .border(0.7.dp, MetalBorder, RoundedCornerShape(3.dp))
            .clickable { onClick() }
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun MiniDeckBadge(
    text: String,
    active: Boolean,
    color: Color,
    tag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(3.dp))
            .background(if (active) color.copy(alpha = 0.28f) else Color(0xFF131A29))
            .border(0.7.dp, if (active) color else MetalBorder, RoundedCornerShape(3.dp))
            .clickable { onClick() }
            .testTag(tag)
            .padding(vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontSize = 6.sp, fontWeight = FontWeight.Bold, fontFamily = JetBrainsMonoFontFamily, maxLines = 1)
    }
}

@Composable
private fun MiniPitchStepBtn(symbol: String, tag: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(15.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0xFF1A2234))
            .border(0.6.dp, MetalBorder, RoundedCornerShape(3.dp))
            .clickable { onClick() }
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, color = TextPrimary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * Exact Reference-Matched 4-Channel Central Mixer ("BIJOY DJ"):
 * - Left & Right Flanking Utility Strips (Headphone Vol/Mix, Master Vol, CUE, Master LED Meter, FX Depth, Assign)
 * - 4 Independent Vertical Channel Strips (Channels 1, 2, 3, 4):
 *   Each has: Channel Meter + GAIN + TREBLE + MID + BASS + glowing FILTER knob + orange CUE button + Channel Volume Fader
 * - Bottom Horizontal Crossfader (< A | B >)
 */
@Composable
private fun FourChannelCentralMixer(
    deckA: DeckState,
    deckB: DeckState,
    mixer: MixerState,
    isFullscreen: Boolean,
    viewModel: BijoyViewModel,
    modifier: Modifier = Modifier
) {
    val engine = viewModel.audioEngine

    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF070A11))
            .border(1.2.dp, MetalBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 4.dp, vertical = 3.dp)
    ) {
        val compactHeight = maxHeight < 320.dp
        val knobSize: Dp = when {
            maxHeight >= 390.dp -> 28.dp
            maxHeight >= 325.dp || isFullscreen -> 24.5.dp
            else -> 20.dp
        }
        val showSideUtilityColumns = maxWidth >= 250.dp

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Mixer Top Branding Header: CH CUE | BIJOY DJ | CH CUE
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    MiniTopTag("CH", LedGreen)
                    MiniTopTag("CUE", LedGreen)
                }
                Text(
                    text = "BIJOY DJ",
                    color = CrownGold,
                    fontFamily = OrbitronFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 10.sp,
                    letterSpacing = 1.2.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    MiniTopTag("CH", LedGreen)
                    MiniTopTag("CUE", LedGreen)
                }
            }

            // Middle Mixer Body: [Left HP/Utility] + [4 Channel Strips 1..4] + [Right Master/Meter/FX]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Flanking Column (Headphone Vol, HP Mix, Beat Sync, Assign)
                if (showSideUtilityColumns) {
                    Column(
                        modifier = Modifier
                            .width(40.dp)
                            .fillMaxHeight()
                            .padding(vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        RotaryHardwareKnob(
                            label = "H.VOL",
                            value = (mixer.headphoneVolume * 2f) - 1f,
                            accentColor = DeckACyan,
                            knobSize = knobSize,
                            testTagName = "mixer_hp_vol_knob",
                            onValueChange = { engine.setHeadphoneVolume((it + 1f) * 0.5f) },
                            onReset = { engine.setHeadphoneVolume(0.85f) }
                        )
                        RotaryHardwareKnob(
                            label = "H.MIX",
                            value = (mixer.headphoneMix * 2f) - 1f,
                            accentColor = DeckACyan,
                            knobSize = knobSize,
                            testTagName = "mixer_hp_mix_knob",
                            onValueChange = { engine.setHeadphoneMix((it + 1f) * 0.5f) },
                            onReset = { engine.setHeadphoneMix(0.5f) }
                        )
                        RotaryHardwareKnob(
                            label = "BOOTH",
                            value = (mixer.boothVolume * 2f) - 1f,
                            accentColor = DeckBGold,
                            knobSize = knobSize,
                            testTagName = "mixer_booth_knob",
                            onValueChange = { engine.setBoothVolume((it + 1f) * 0.5f) },
                            onReset = { engine.setBoothVolume(0.75f) }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (deckA.syncActive) DeckACyan.copy(alpha = 0.35f) else Color(0xFF131A29))
                                    .border(0.7.dp, DeckACyan, RoundedCornerShape(3.dp))
                                    .clickable { engine.toggleSync(DeckId.A) }
                                    .testTag("mixer_beat_sync_a_btn")
                                    .padding(vertical = 3.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("SA", color = Color.White, fontSize = 5.5.sp, fontWeight = FontWeight.Bold, fontFamily = JetBrainsMonoFontFamily)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (deckB.syncActive) DeckBPink.copy(alpha = 0.35f) else Color(0xFF131A29))
                                    .border(0.7.dp, DeckBPink, RoundedCornerShape(3.dp))
                                    .clickable { engine.toggleSync(DeckId.B) }
                                    .testTag("mixer_beat_sync_b_btn")
                                    .padding(vertical = 3.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("SB", color = Color.White, fontSize = 5.5.sp, fontWeight = FontWeight.Bold, fontFamily = JetBrainsMonoFontFamily)
                            }
                        }
                    }
                }

                // 4 INDEPENDENT CHANNEL STRIPS (CHANNEL 1, 2, 3, 4)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    mixer.channels.forEach { ch ->
                        val chColor = when (ch.channelIndex) {
                            1 -> DeckACyan
                            2 -> DeckBPink
                            3 -> LedGreen
                            else -> DeckBOrange
                        }
                        ChannelStripColumn(
                            channel = ch,
                            accent = chColor,
                            knobSize = knobSize,
                            compactHeight = compactHeight,
                            onGainChange = { engine.updateChannelEqByIndex(ch.channelIndex, gainDb = it * 18f) },
                            onTrebleChange = { engine.updateChannelEqByIndex(ch.channelIndex, trebleDb = it * 12f) },
                            onMidChange = { engine.updateChannelEqByIndex(ch.channelIndex, midDb = it * 12f) },
                            onBassChange = { engine.updateChannelEqByIndex(ch.channelIndex, bassDb = it * 12f) },
                            onVocalChange = { engine.updateChannelEqByIndex(ch.channelIndex, vocalDb = it * 12f) },
                            onFilterChange = { engine.updateChannelEqByIndex(ch.channelIndex, filterCutoff = it) },
                            onCueClick = { engine.toggleChannelCue(ch.channelIndex) },
                            onToggleEnabled = { engine.toggleChannelEnabled(ch.channelIndex) },
                            onAssignClick = { engine.cycleChannelAssign(ch.channelIndex) },
                            onVolumeChange = { engine.setChannelVolumeByIndex(ch.channelIndex, it) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                }

                // Right Flanking Column (MASTER Knob, CUE LED, Master Stereo Meter, FX Wet/Depth, LIM)
                if (showSideUtilityColumns) {
                    Column(
                        modifier = Modifier
                            .width(40.dp)
                            .fillMaxHeight()
                            .padding(vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        RotaryHardwareKnob(
                            label = "MSTR",
                            value = (mixer.masterVolume * 2f) - 1f,
                            accentColor = CrownGold,
                            knobSize = knobSize,
                            testTagName = "mixer_master_knob",
                            onValueChange = { engine.setMasterVolume((it + 1f) * 0.5f) },
                            onReset = { engine.setMasterVolume(0.85f) }
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (mixer.beatFxOn) DeckBOrange else Color(0xFF281709))
                                .border(0.8.dp, DeckBOrange, RoundedCornerShape(3.dp))
                                .clickable { engine.toggleBeatFxOn() }
                                .testTag("mixer_master_cue_fx_btn")
                                .padding(vertical = 2.5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(if (mixer.beatFxOn) "FX ON" else "CUE", color = Color.White, fontSize = 6.sp, fontWeight = FontWeight.Bold, fontFamily = JetBrainsMonoFontFamily)
                        }

                        // Tall Master Stereo Level Meter
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            VerticalVuMeter(level = mixer.masterLevelLeft, segments = 14, modifier = Modifier.fillMaxHeight())
                            VerticalVuMeter(level = mixer.masterLevelRight, segments = 14, modifier = Modifier.fillMaxHeight())
                        }

                        RotaryHardwareKnob(
                            label = "FX",
                            value = (mixer.masterEffectWet * 2f) - 1f,
                            accentColor = LedPurple,
                            knobSize = knobSize,
                            testTagName = "mixer_fx_wet_knob",
                            onValueChange = {
                                val wet = ((it + 1f) * 0.5f).coerceIn(0f, 1f)
                                val fx = if (mixer.masterEffect == DjEffectType.NONE) DjEffectType.ECHO else mixer.masterEffect
                                engine.setMasterEffect(fx, wet = wet)
                            },
                            onReset = { engine.setMasterEffect(DjEffectType.NONE) }
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (mixer.masterLimiterEnabled) LedGreen.copy(alpha = 0.25f) else Color(0xFF1A1E29))
                                .border(0.7.dp, if (mixer.masterLimiterEnabled) LedGreen else MetalBorder, RoundedCornerShape(3.dp))
                                .clickable { engine.toggleMasterLimiter() }
                                .testTag("mixer_limiter_btn")
                                .padding(vertical = 1.5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("LIM", color = if (mixer.masterLimiterEnabled) LedGreen else TextMuted, fontSize = 5.5.sp, fontWeight = FontWeight.Bold, fontFamily = JetBrainsMonoFontFamily)
                        }
                    }
                }
            }

            // Bottom Horizontal Crossfader (< A | B >)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("< A", color = DeckACyan, fontSize = 7.sp, fontWeight = FontWeight.Bold, fontFamily = JetBrainsMonoFontFamily)
                HardwareCrossfader(
                    value = mixer.crossfader,
                    modifier = Modifier
                        .weight(1f)
                        .height(26.dp),
                    onValueChange = { engine.setCrossfader(it) }
                )
                Text("B >", color = DeckBPink, fontSize = 7.sp, fontWeight = FontWeight.Bold, fontFamily = JetBrainsMonoFontFamily)
            }
        }
    }
}

@Composable
private fun MiniTopTag(label: String, bg: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(bg.copy(alpha = 0.85f))
            .padding(horizontal = 3.dp, vertical = 1.dp)
    ) {
        Text(label, color = Color(0xFF070A12), fontSize = 5.5.sp, fontWeight = FontWeight.ExtraBold, fontFamily = JetBrainsMonoFontFamily)
    }
}

/**
 * Single Independent Mixer Channel Strip (CH 1, 2, 3, or 4):
 * Channel Meter + GAIN + TREBLE + MID + BASS + FILTER + CUE + Volume Fader + Crossfader Assign
 */
@Composable
private fun ChannelStripColumn(
    channel: MixerChannelState,
    accent: Color,
    knobSize: Dp,
    compactHeight: Boolean,
    onGainChange: (Float) -> Unit,
    onTrebleChange: (Float) -> Unit,
    onMidChange: (Float) -> Unit,
    onBassChange: (Float) -> Unit,
    onVocalChange: (Float) -> Unit,
    onFilterChange: (Float) -> Unit,
    onCueClick: () -> Unit,
    onToggleEnabled: () -> Unit,
    onAssignClick: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val chIdx = channel.channelIndex
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(Color(0xFF0B101C))
            .border(0.6.dp, MetalBorder.copy(alpha = 0.55f), RoundedCornerShape(5.dp))
            .padding(horizontal = 1.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Upper EQ + Gain + Vocal + Filter + Individual Channel Meter
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(if (compactHeight) 1.62f else 1.72f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                StripKnob((channel.gainDb / 18f).coerceIn(-1f, 1f), "GAIN", accent, knobSize, "ch_${chIdx}_gain", onGainChange)
                StripKnob(channel.trebleDb / 12f, "TREB", accent, knobSize, "ch_${chIdx}_treble", onTrebleChange)
                StripKnob(channel.midDb / 12f, "MID", accent, knobSize, "ch_${chIdx}_mid", onMidChange)
                StripKnob(channel.bassDb / 12f, "BASS", accent, knobSize, "ch_${chIdx}_bass", onBassChange)
                StripKnob(channel.vocalDb / 12f, "VOC", LedPurple, knobSize, "ch_${chIdx}_vocal", onVocalChange)
                StripKnob(channel.filterCutoff, "FILT", DeckACyan, knobSize, "ch_${chIdx}_filter", onFilterChange)
            }

            // Individual Channel Level Meter beside knobs
            VerticalVuMeter(
                level = channel.meterLevel,
                segments = 12,
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight(0.88f)
            )
        }

        // Orange CUE Button + ON/OFF Mute Toggle
        Row(
            modifier = Modifier.fillMaxWidth(0.94f),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (channel.cueActive) DeckBOrange else Color(0xFF8C4508))
                    .border(0.7.dp, DeckBGold.copy(alpha = 0.85f), RoundedCornerShape(3.dp))
                    .clickable { onCueClick() }
                    .testTag("ch_${chIdx}_cue_btn")
                    .padding(vertical = 1.5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("CUE", color = Color.White, fontSize = 5.2.sp, fontWeight = FontWeight.ExtraBold, fontFamily = JetBrainsMonoFontFamily)
            }
            Box(
                modifier = Modifier
                    .weight(0.85f)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (channel.channelEnabled) LedGreen.copy(alpha = 0.25f) else LedRed.copy(alpha = 0.35f))
                    .border(0.7.dp, if (channel.channelEnabled) LedGreen else LedRed, RoundedCornerShape(3.dp))
                    .clickable { onToggleEnabled() }
                    .testTag("ch_${chIdx}_enable_btn")
                    .padding(vertical = 1.5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (channel.channelEnabled) "ON" else "MUTE",
                    color = if (channel.channelEnabled) LedGreen else Color.White,
                    fontSize = 5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = JetBrainsMonoFontFamily
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text("$chIdx", color = TextPrimary, fontSize = 7.sp, fontWeight = FontWeight.Bold, fontFamily = OrbitronFontFamily)
            Text(
                text = channel.assign.label,
                color = accent,
                fontSize = 5.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = JetBrainsMonoFontFamily,
                modifier = Modifier
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent.copy(alpha = 0.16f))
                    .clickable { onAssignClick() }
                    .testTag("ch_${chIdx}_assign_btn")
                    .padding(horizontal = 2.dp)
            )
        }

        // Channel Volume Fader
        VerticalHardwareFader(
            value = channel.volume,
            accentColor = accent,
            testTagName = "ch_${chIdx}_volume_fader",
            modifier = Modifier
                .fillMaxWidth()
                .weight(if (compactHeight) 0.50f else 0.65f)
        ) { onVolumeChange(it) }
    }
}

@Composable
private fun StripKnob(
    value: Float,
    label: String,
    accent: Color,
    knobSize: Dp,
    tag: String,
    onChange: (Float) -> Unit
) {
    RotaryHardwareKnob(
        label = label,
        value = value,
        accentColor = accent,
        knobSize = knobSize,
        testTagName = tag,
        onValueChange = onChange
    )
}

@Composable
private fun RoundHardwareTransportBtn(
    isCircleIcon: Boolean,
    isPlaying: Boolean,
    label: String,
    accentColor: Color,
    active: Boolean,
    size: Dp,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    tag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = if (active) listOf(accentColor.copy(alpha = 0.45f), Color(0xFF0A0F1A))
                    else listOf(Color(0xFF1D2434), Color(0xFF090D16))
                )
            )
            .border(1.4.dp, if (active) accentColor else accentColor.copy(alpha = 0.72f), CircleShape)
            .clickable { onClick() }
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        when {
            isCircleIcon -> Icon(
                imageVector = if (isPlaying) BijoyIcons.Pause else Icons.Default.PlayArrow,
                contentDescription = label,
                tint = if (isPlaying) LedGreen else Color.White,
                modifier = Modifier.size(size * 0.62f)
            )
            icon != null -> Icon(
                imageVector = icon,
                contentDescription = label,
                tint = accentColor,
                modifier = Modifier.size(size * 0.62f)
            )
            else -> Text(
                text = label,
                color = if (active) Color.White else accentColor,
                fontSize = 6.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = OrbitronFontFamily,
                maxLines = 1
            )
        }
    }
}
