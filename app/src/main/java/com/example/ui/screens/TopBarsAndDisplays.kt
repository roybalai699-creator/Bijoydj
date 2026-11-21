package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.audio.*
import com.example.sources.SourceConnectionState
import com.example.ui.components.RealWaveformCanvas
import com.example.ui.theme.*

fun formatTimeMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return String.format("%02d:%02d", min, sec)
}

/**
 * Gold Crown Vector Branding matching BIJOY DJ Reference Header.
 */
@Composable
fun BijoyGoldCrownIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.08f, h * 0.80f)
            lineTo(w * 0.04f, h * 0.30f)
            lineTo(w * 0.30f, h * 0.54f)
            lineTo(w * 0.50f, h * 0.16f)
            lineTo(w * 0.70f, h * 0.54f)
            lineTo(w * 0.96f, h * 0.30f)
            lineTo(w * 0.92f, h * 0.80f)
            close()
        }
        drawPath(
            path = path,
            brush = Brush.verticalGradient(listOf(Color(0xFFFFE082), CrownGold, Color(0xFFFF8F00)))
        )
        drawRect(
            brush = Brush.horizontalGradient(listOf(Color(0xFFFF8F00), CrownGold, Color(0xFFFF8F00))),
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.08f, h * 0.83f),
            size = androidx.compose.ui.geometry.Size(w * 0.84f, h * 0.11f)
        )
    }
}

/**
 * Top Header matching BIJOY DJ Reference:
 * Left: Hamburger Menu + Gold Crown + BIJOY DJ + FEEL THE BEAT
 * Right: Spotify + SoundCloud + Settings + About (clean, uncrowded)
 */
@Composable
fun BijoyTopHeaderBar(
    spotifyState: SourceConnectionState,
    soundCloudState: SourceConnectionState,
    recordingState: RecordingState,
    isFullscreenMixer: Boolean,
    onMenuClick: () -> Unit,
    onSpotifyClick: () -> Unit,
    onSoundCloudClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAboutClick: () -> Unit,
    onLayoutClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF0A0E19), Color(0xFF05070D)))
            )
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Hamburger + Gold Crown + BIJOY DJ + FEEL THE BEAT
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color(0xFF101624))
                    .border(1.dp, MetalBorder, RoundedCornerShape(7.dp))
                    .clickable { onMenuClick() }
                    .testTag("nav_menu_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Menu, contentDescription = "Open Navigation Menu", tint = TextPrimary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(8.dp))
            BijoyGoldCrownIcon(modifier = Modifier.size(width = 30.dp, height = 24.dp))
            Spacer(Modifier.width(7.dp))
            Column {
                Text(
                    text = "BIJOY DJ",
                    color = CrownGold,
                    fontFamily = OrbitronFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = if (recordingState.isRecording) "● REC ${formatTimeMs(recordingState.elapsedMs)}" else "FEEL THE BEAT",
                    color = if (recordingState.isRecording) LedRed else TextPrimary.copy(alpha = 0.85f),
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    fontFamily = RajdhaniFontFamily
                )
            }
        }

        // Right: Spotify + SoundCloud + Settings + About (+ Layout quick icon when needed)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            HeaderSourcePill(
                label = "Spotify",
                icon = BijoyIcons.GraphicEq,
                accent = SpotifyGreen,
                connected = spotifyState.isConnected,
                tag = "header_spotify_button",
                onClick = onSpotifyClick
            )
            HeaderSourcePill(
                label = "SoundCloud",
                icon = BijoyIcons.CloudQueue,
                accent = SoundCloudOrange,
                connected = soundCloudState.isConnected,
                tag = "header_soundcloud_button",
                onClick = onSoundCloudClick
            )
            HeaderActionPill(
                label = "Settings",
                icon = Icons.Default.Settings,
                tag = "header_settings_button",
                onClick = onSettingsClick
            )
            HeaderActionPill(
                label = "About",
                icon = Icons.Default.Info,
                tag = "header_about_button",
                onClick = onAboutClick
            )
            if (isFullscreenMixer) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF101624))
                        .border(1.dp, DeckACyan.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                        .clickable { onLayoutClick() }
                        .testTag("header_layout_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(BijoyIcons.DashboardCustomize, contentDescription = "Customize Layout", tint = DeckACyan, modifier = Modifier.size(15.dp))
                }
            }
        }
    }
}

@Composable
private fun HeaderSourcePill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    connected: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0E1422))
            .border(1.dp, if (connected) accent else MetalBorder, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .testTag(tag)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(accent),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(12.dp))
        }
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            color = TextPrimary,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
        if (connected) {
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(LedGreen)
            )
        }
    }
}

@Composable
private fun HeaderActionPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0E1422))
            .border(1.dp, MetalBorder, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .testTag(tag)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = TextPrimary, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            color = TextPrimary,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

/**
 * Top Dual Music Deck Display & Waveform Row matching the reference image:
 * Left Deck (Cyan border & cyan waveform) | SWAP DECK button | Right Deck (Pink border & pink/magenta waveform)
 */
@Composable
fun TopSongDisplayRow(
    deckA: DeckState,
    deckB: DeckState,
    isFullscreenMixer: Boolean,
    onSeekA: (Float) -> Unit,
    onSeekB: (Float) -> Unit,
    onZoomA: (Float) -> Unit,
    onZoomB: (Float) -> Unit,
    onPlayPauseA: () -> Unit,
    onPlayPauseB: () -> Unit,
    onCueA: () -> Unit,
    onCueB: () -> Unit,
    onResetA: () -> Unit,
    onResetB: () -> Unit,
    onLoadA: () -> Unit,
    onLoadB: () -> Unit,
    onSwapDecks: () -> Unit,
    onEditBpm: (DeckId, Float) -> Unit
) {
    val rowHeight = if (isFullscreenMixer) 74.dp else 68.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        DeckSongInfoCard(
            deck = deckA,
            accent = DeckACyan,
            secondaryWave = DeckABlue,
            modifier = Modifier.weight(1f),
            onSeek = onSeekA,
            onZoom = onZoomA,
            onPlayPause = onPlayPauseA,
            onCue = onCueA,
            onReset = onResetA,
            onLoad = onLoadA,
            onBpmTap = { onEditBpm(DeckId.A, deckA.originalBpm) }
        )

        // Central SWAP DECK Button
        Box(
            modifier = Modifier
                .width(34.dp)
                .height(34.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Color(0xFF101727))
                .border(1.2.dp, MetalSilverDark, RoundedCornerShape(7.dp))
                .clickable { onSwapDecks() }
                .testTag("swap_decks_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = BijoyIcons.SwapHoriz,
                contentDescription = "Swap Decks A and B",
                tint = MetalSilverLight,
                modifier = Modifier.size(20.dp)
            )
        }

        DeckSongInfoCard(
            deck = deckB,
            accent = DeckBPink,
            secondaryWave = DeckBRed,
            modifier = Modifier.weight(1f),
            onSeek = onSeekB,
            onZoom = onZoomB,
            onPlayPause = onPlayPauseB,
            onCue = onCueB,
            onReset = onResetB,
            onLoad = onLoadB,
            onBpmTap = { onEditBpm(DeckId.B, deckB.originalBpm) }
        )
    }
}

@Composable
private fun DeckSongInfoCard(
    deck: DeckState,
    accent: Color,
    secondaryWave: Color,
    modifier: Modifier = Modifier,
    onSeek: (Float) -> Unit,
    onZoom: (Float) -> Unit,
    onPlayPause: () -> Unit,
    onCue: () -> Unit,
    onReset: () -> Unit,
    onLoad: () -> Unit,
    onBpmTap: () -> Unit
) {
    val artRes = if (deck.artworkResName == "deck_b") {
        R.drawable.img_deck_b_art_1795192576972
    } else {
        R.drawable.img_deck_a_art_1795192577075
    }
    val deckPrefix = if (deck.deckId == DeckId.A) "deck_a" else "deck_b"

    Row(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0A0F1B))
            .border(1.2.dp, accent.copy(alpha = 0.80f), RoundedCornerShape(8.dp))
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Album Art (left side in both cards matching reference image)
        Image(
            painter = painterResource(artRes),
            contentDescription = "Deck ${deck.deckId.name} Artwork",
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(6.dp))
                .border(1.dp, accent.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                .clickable { onLoad() }
                .testTag("${deckPrefix}_load_track_art"),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.width(6.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = deck.title,
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = deck.artist,
                            color = TextSecondary,
                            fontSize = 8.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = "${formatTimeMs(deck.positionMs)}/${formatTimeMs(deck.durationMs)}",
                            color = accent,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = JetBrainsMonoFontFamily
                        )
                    }
                }

                // Micro Deck Controls + Key + BPM + Zoom
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    MiniDeckActionBadge("LD", DeckACyan, "${deckPrefix}_load_btn", onLoad)
                    MiniDeckActionBadge("RST", accent, "${deckPrefix}_disp_reset", onReset)
                    MiniDeckActionBadge("C", DeckBOrange, "${deckPrefix}_disp_cue", onCue)
                    MiniDeckActionBadge(if (deck.isPlaying) "II" else "▶", LedGreen, "${deckPrefix}_disp_play", onPlayPause)

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF141C2E))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(deck.musicalKey, color = DeckBGold, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = JetBrainsMonoFontFamily)
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF151D2F))
                            .border(0.8.dp, accent.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                            .clickable { onBpmTap() }
                            .testTag("bpm_badge_${deck.deckId.name.lowercase()}")
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "BPM:${String.format("%.0f", deck.effectiveBpm)}",
                            color = TextPrimary,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = JetBrainsMonoFontFamily
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF151D2E))
                            .clickable {
                                val nextZoom = if (deck.waveformZoom >= 3f) 1f else deck.waveformZoom + 1f
                                onZoom(nextZoom)
                            }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${deck.waveformZoom.toInt()}x",
                            color = TextSecondary,
                            fontSize = 7.5.sp,
                            fontFamily = JetBrainsMonoFontFamily
                        )
                    }
                }
            }

            // Real Synchronized Audio Waveform (Cyan on Deck A, Pink/Magenta on Deck B)
            RealWaveformCanvas(
                deck = deck,
                primaryColor = accent,
                secondaryColor = secondaryWave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp),
                onSeekRatio = onSeek
            )
        }
    }
}

@Composable
private fun MiniDeckActionBadge(
    label: String,
    accent: Color,
    tag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(accent.copy(alpha = 0.16f))
            .border(0.7.dp, accent.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .testTag(tag)
            .padding(horizontal = 4.dp, vertical = 1.5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 7.5.sp, fontWeight = FontWeight.Bold, fontFamily = JetBrainsMonoFontFamily)
    }
}

/**
 * Independent Dual-Side Bottom DJ Control Bar:
 * - LEFT SIDE (Deck A Player): Round PLAY/PAUSE A | Round CUE A | Round RESET A | SYNC A | LOOP A
 * - CENTER MIXER TOOLS: AUTO FADE | FADE TIME | FX | SAMPLER | EXPAND
 * - RIGHT SIDE (Deck B Player): LOOP B | SYNC B | Round RESET B | Round CUE B | Round PLAY/PAUSE B
 */
@Composable
fun BottomDjControlBar(
    deckA: DeckState,
    deckB: DeckState,
    mixer: MixerState,
    isFullscreenMixer: Boolean,
    onPlayPauseA: () -> Unit,
    onCueA: () -> Unit,
    onResetA: () -> Unit,
    onSyncA: () -> Unit,
    onRepeatA: () -> Unit,
    onPlayPauseB: () -> Unit,
    onCueB: () -> Unit,
    onResetB: () -> Unit,
    onSyncB: () -> Unit,
    onRepeatB: () -> Unit,
    onToggleAutoFade: () -> Unit,
    onCycleFadeTime: () -> Unit,
    onOpenFx: () -> Unit,
    onOpenSampler: () -> Unit,
    onToggleExpand: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF060911))
            .border(width = 0.8.dp, color = MetalBorder.copy(alpha = 0.6f))
            .navigationBarsPadding()
            .padding(horizontal = 5.dp, vertical = 4.dp)
    ) {
        val isWide = maxWidth >= 560.dp

        if (isWide) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // LEFT SIDE: DECK A CONTROLS (100% SEPARATE)
                DeckSideTransportStrip(
                    deck = deckA,
                    accent = DeckACyan,
                    tagPrefix = "deck_a",
                    mirrored = false,
                    onPlayPause = onPlayPauseA,
                    onCue = onCueA,
                    onReset = onResetA,
                    onSync = onSyncA,
                    onRepeat = onRepeatA,
                    modifier = Modifier.weight(1.15f)
                )

                // CENTER MIXER TOOLS
                CenterMixerToolsStrip(
                    mixer = mixer,
                    isFullscreenMixer = isFullscreenMixer,
                    onToggleAutoFade = onToggleAutoFade,
                    onCycleFadeTime = onCycleFadeTime,
                    onOpenFx = onOpenFx,
                    onOpenSampler = onOpenSampler,
                    onToggleExpand = onToggleExpand,
                    modifier = Modifier.weight(1.25f)
                )

                // RIGHT SIDE: DECK B CONTROLS (100% SEPARATE)
                DeckSideTransportStrip(
                    deck = deckB,
                    accent = DeckBPink,
                    tagPrefix = "deck_b",
                    mirrored = true,
                    onPlayPause = onPlayPauseB,
                    onCue = onCueB,
                    onReset = onResetB,
                    onSync = onSyncB,
                    onRepeat = onRepeatB,
                    modifier = Modifier.weight(1.15f)
                )
            }
        } else {
            // Portrait Dual-Row Layout: Row 1 = Left Player A | Right Player B, Row 2 = Center Mixer Tools
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DeckSideTransportStrip(
                        deck = deckA,
                        accent = DeckACyan,
                        tagPrefix = "deck_a",
                        mirrored = false,
                        onPlayPause = onPlayPauseA,
                        onCue = onCueA,
                        onReset = onResetA,
                        onSync = onSyncA,
                        onRepeat = onRepeatA,
                        modifier = Modifier.weight(1f)
                    )
                    DeckSideTransportStrip(
                        deck = deckB,
                        accent = DeckBPink,
                        tagPrefix = "deck_b",
                        mirrored = true,
                        onPlayPause = onPlayPauseB,
                        onCue = onCueB,
                        onReset = onResetB,
                        onSync = onSyncB,
                        onRepeat = onRepeatB,
                        modifier = Modifier.weight(1f)
                    )
                }
                CenterMixerToolsStrip(
                    mixer = mixer,
                    isFullscreenMixer = isFullscreenMixer,
                    onToggleAutoFade = onToggleAutoFade,
                    onCycleFadeTime = onCycleFadeTime,
                    onOpenFx = onOpenFx,
                    onOpenSampler = onOpenSampler,
                    onToggleExpand = onToggleExpand,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                )
            }
        }
    }
}

@Composable
private fun DeckSideTransportStrip(
    deck: DeckState,
    accent: Color,
    tagPrefix: String,
    mirrored: Boolean,
    onPlayPause: () -> Unit,
    onCue: () -> Unit,
    onReset: () -> Unit,
    onSync: () -> Unit,
    onRepeat: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0A0F1B))
            .border(1.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        val playCircle: @Composable () -> Unit = {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(if (deck.isPlaying) LedGreen.copy(alpha = 0.28f) else Color(0xFF0C1A13))
                    .border(1.6.dp, LedGreen, CircleShape)
                    .clickable { onPlayPause() }
                    .testTag("${tagPrefix}_bottom_play_btn"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (deck.isPlaying) BijoyIcons.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play or Pause Deck ${deck.deckId.name}",
                    tint = LedGreen,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        val cueCircle: @Composable () -> Unit = {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (deck.headphoneCue) DeckBOrange.copy(alpha = 0.35f) else Color(0xFF19120A))
                    .border(1.3.dp, DeckBOrange, CircleShape)
                    .clickable { onCue() }
                    .testTag("${tagPrefix}_bottom_cue_btn"),
                contentAlignment = Alignment.Center
            ) {
                Text("CUE", color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.ExtraBold, fontFamily = OrbitronFontFamily)
            }
        }

        val resetCircle: @Composable () -> Unit = {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF101828))
                    .border(1.3.dp, accent, CircleShape)
                    .clickable { onReset() }
                    .testTag("${tagPrefix}_bottom_reset_btn"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = BijoyIcons.RestartAlt,
                    contentDescription = "Reset Deck ${deck.deckId.name}",
                    tint = accent,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        val syncPill: @Composable RowScope.() -> Unit = {
            BottomGlowButton(
                label = "SYNC",
                accent = DeckABlue,
                active = deck.syncActive,
                tag = "${tagPrefix}_bottom_sync_btn",
                modifier = Modifier.weight(1f),
                onClick = onSync
            )
        }

        val loopPill: @Composable RowScope.() -> Unit = {
            Box(
                modifier = Modifier
                    .weight(0.7f)
                    .fillMaxHeight(0.86f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (deck.repeatEnabled || deck.loopActive) accent.copy(alpha = 0.28f) else Color(0xFF101624))
                    .border(1.dp, if (deck.repeatEnabled || deck.loopActive) accent else MetalBorder, RoundedCornerShape(6.dp))
                    .clickable { onRepeat() }
                    .testTag("${tagPrefix}_bottom_repeat_btn"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = BijoyIcons.Repeat,
                    contentDescription = "Repeat / Loop Deck ${deck.deckId.name}",
                    tint = if (deck.repeatEnabled || deck.loopActive) accent else TextPrimary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        if (!mirrored) {
            playCircle()
            cueCircle()
            resetCircle()
            syncPill()
            loopPill()
        } else {
            loopPill()
            syncPill()
            resetCircle()
            cueCircle()
            playCircle()
        }
    }
}

@Composable
private fun CenterMixerToolsStrip(
    mixer: MixerState,
    isFullscreenMixer: Boolean,
    onToggleAutoFade: () -> Unit,
    onCycleFadeTime: () -> Unit,
    onOpenFx: () -> Unit,
    onOpenSampler: () -> Unit,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxHeight(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        // AUTO FADE
        Row(
            modifier = Modifier
                .weight(1.35f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(7.dp))
                .background(if (mixer.autoFadeActive) DeckACyan.copy(alpha = 0.22f) else Color(0xFF0D1422))
                .border(1.2.dp, if (mixer.autoFadeActive) DeckACyan else DeckABlue, RoundedCornerShape(7.dp))
                .clickable { onToggleAutoFade() }
                .testTag("auto_fade_button")
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (mixer.autoFadeActive) Icons.Default.CheckCircle else BijoyIcons.CheckCircleOutline,
                contentDescription = "Auto Fade",
                tint = CrownGold,
                modifier = Modifier.size(12.dp)
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text = "FADE",
                color = TextPrimary,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = JetBrainsMonoFontFamily,
                maxLines = 1
            )
        }

        // FADE TIME (1s, 3s, 5s, 10s, 15s)
        Box(
            modifier = Modifier
                .weight(0.65f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(7.dp))
                .background(Color(0xFF101624))
                .border(1.dp, MetalBorder, RoundedCornerShape(7.dp))
                .clickable { onCycleFadeTime() }
                .testTag("bottom_fade_time_btn"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${mixer.fadeDurationSec.toInt()}s",
                color = TextPrimary,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = JetBrainsMonoFontFamily
            )
        }

        // FX
        BottomGlowButton(
            label = "FX",
            accent = DeckBOrange,
            active = mixer.beatFxOn,
            tag = "bottom_fx_btn",
            modifier = Modifier.weight(0.75f),
            onClick = onOpenFx
        )

        // SAMPLER
        BottomGlowButton(
            label = "SMPL",
            accent = LedPurple,
            active = mixer.samplerRollActive,
            tag = "bottom_sampler_btn",
            modifier = Modifier.weight(0.9f),
            onClick = onOpenSampler
        )

        // EXPAND
        Row(
            modifier = Modifier
                .weight(1.05f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(7.dp))
                .background(if (isFullscreenMixer) DeckBOrange.copy(alpha = 0.25f) else Color(0xFF0E1626))
                .border(1.2.dp, if (isFullscreenMixer) DeckBOrange else DeckACyan, RoundedCornerShape(7.dp))
                .clickable { onToggleExpand() }
                .testTag("expand_mixer_button")
                .padding(horizontal = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isFullscreenMixer) BijoyIcons.CloseFullscreen else BijoyIcons.OpenInFull,
                contentDescription = if (isFullscreenMixer) "Restore Layout" else "Expand Mixer",
                tint = if (isFullscreenMixer) DeckBGold else DeckACyan,
                modifier = Modifier.size(12.dp)
            )
            Spacer(Modifier.width(2.dp))
            Text(
                text = if (isFullscreenMixer) "RST" else "FULL",
                color = TextPrimary,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = JetBrainsMonoFontFamily,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun BottomGlowButton(
    label: String,
    accent: Color,
    active: Boolean,
    tag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxHeight(0.86f)
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) accent.copy(alpha = 0.30f) else Color(0xFF0E131F))
            .border(1.1.dp, accent, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = OrbitronFontFamily,
            maxLines = 1
        )
    }
}
