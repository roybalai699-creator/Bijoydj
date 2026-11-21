package com.example.ui.screens

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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.audio.*
import com.example.sources.SourceConnectionState
import com.example.ui.components.HardwareCrossfader
import com.example.ui.components.RealWaveformCanvas
import com.example.ui.components.RgbSpectrumVisualizer
import com.example.ui.theme.*

fun formatTimeMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return String.format("%02d:%02d", min, sec)
}

@Composable
fun BijoyTopHeaderBar(
    spectrumBands: List<Float>,
    recordingState: RecordingState,
    isFullscreenMixer: Boolean,
    showRecButton: Boolean,
    onMenuClick: () -> Unit,
    onRecClick: () -> Unit,
    onLayoutClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAboutClick: () -> Unit,
    onToggleExpandMixer: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF0A0F1D), Color(0xFF05070D)))
            )
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Menu + Brand Logo
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("nav_menu_button")
            ) {
                Icon(Icons.Default.Menu, contentDescription = "Open Navigation Menu", tint = TextPrimary)
            }
            Spacer(Modifier.width(4.dp))
            Image(
                painter = painterResource(R.drawable.img_bijoy_icon_1795192577742),
                contentDescription = "BIJOY DJ Logo",
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .border(1.dp, DeckACyan.copy(alpha = 0.7f), CircleShape),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(6.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "BIJOY ",
                        color = MetalSilverLight,
                        fontFamily = OrbitronFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "DJ",
                        color = DeckBRed,
                        fontFamily = OrbitronFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        letterSpacing = 1.sp
                    )
                }
                Text(
                    text = "Feel The Beat...",
                    color = TextSecondary,
                    fontSize = 9.sp,
                    fontStyle = FontStyle.Italic,
                    fontFamily = RajdhaniFontFamily
                )
            }
        }

        // Center: Live Real-Time RGB Audio Spectrum
        RgbSpectrumVisualizer(
            bands = spectrumBands,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
                .height(26.dp)
        )

        // Right: REC, Layout, Settings, About, Expand Mixer
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (showRecButton) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (recordingState.isRecording) LedRed.copy(alpha = 0.25f) else Color(0xFF101624)
                        )
                        .border(
                            1.dp,
                            if (recordingState.isRecording) LedRed else DeckACyan.copy(alpha = 0.55f),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { onRecClick() }
                        .testTag("header_rec_button")
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(LedRed)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = if (recordingState.isRecording) formatTimeMs(recordingState.elapsedMs) else "REC",
                        color = TextPrimary,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
            }

            HeaderIconBtn(
                icon = Icons.Default.DashboardCustomize,
                contentDesc = "Customize Layout",
                tag = "header_layout_button",
                tint = DeckACyan,
                onClick = onLayoutClick
            )
            HeaderIconBtn(
                icon = Icons.Default.Settings,
                contentDesc = "Settings",
                tag = "header_settings_button",
                tint = TextPrimary,
                onClick = onSettingsClick
            )
            HeaderIconBtn(
                icon = Icons.Default.Info,
                contentDesc = "About BIJOY DJ",
                tag = "header_about_button",
                tint = TextSecondary,
                onClick = onAboutClick
            )

            // Dedicated EXPAND MIXER / RESTORE LAYOUT button
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isFullscreenMixer) DeckBOrange.copy(alpha = 0.28f) else DeckACyan.copy(alpha = 0.18f))
                    .border(
                        1.2.dp,
                        if (isFullscreenMixer) DeckBOrange else DeckACyan,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { onToggleExpandMixer() }
                    .testTag("expand_mixer_button")
                    .padding(horizontal = 7.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isFullscreenMixer) Icons.Default.CloseFullscreen else Icons.Default.OpenInFull,
                    contentDescription = if (isFullscreenMixer) "Restore Layout" else "Expand Mixer",
                    tint = if (isFullscreenMixer) DeckBGold else DeckACyan,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = if (isFullscreenMixer) "RESTORE" else "EXPAND",
                    color = TextPrimary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = JetBrainsMonoFontFamily
                )
            }
        }
    }
}

@Composable
private fun HeaderIconBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDesc: String,
    tag: String,
    tint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(Color(0xFF101624))
            .border(1.dp, MetalBorder, RoundedCornerShape(7.dp))
            .clickable { onClick() }
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDesc, tint = tint, modifier = Modifier.size(16.dp))
    }
}

/**
 * Compact upper Song Display containing ONLY song info & real waveforms (+ optional side source pills).
 */
@Composable
fun TopSongDisplayRow(
    deckA: DeckState,
    deckB: DeckState,
    spotifyState: SourceConnectionState,
    soundCloudState: SourceConnectionState,
    showSourceCards: Boolean,
    isFullscreenMixer: Boolean,
    onSeekA: (Float) -> Unit,
    onSeekB: (Float) -> Unit,
    onZoomA: (Float) -> Unit,
    onZoomB: (Float) -> Unit,
    onSwapDecks: () -> Unit,
    onEditBpm: (DeckId, Float) -> Unit,
    onOpenSources: () -> Unit
) {
    val rowHeight = if (isFullscreenMixer) 76.dp else 72.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // Optional Compact Spotify Quick Card (hidden in Fullscreen Mixer Mode)
        if (showSourceCards && !isFullscreenMixer) {
            SourceMiniCard(
                label = "Spotify",
                connected = spotifyState.isConnected,
                accent = SpotifyGreen,
                tag = "top_spotify_card",
                onClick = onOpenSources
            )
        }

        // Deck A Song Display
        DeckSongInfoCard(
            deck = deckA,
            accent = DeckACyan,
            secondaryWave = DeckABlue,
            alignRight = false,
            modifier = Modifier.weight(1f),
            onSeek = onSeekA,
            onZoom = onZoomA,
            onBpmTap = { onEditBpm(DeckId.A, deckA.originalBpm) }
        )

        // Central Deck Swap Button
        Box(
            modifier = Modifier
                .width(32.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF0E1525))
                .border(1.dp, DeckACyan.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .clickable { onSwapDecks() }
                .testTag("swap_decks_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.SwapHoriz,
                contentDescription = "Swap Decks A and B",
                tint = DeckACyan,
                modifier = Modifier.size(20.dp)
            )
        }

        // Deck B Song Display
        DeckSongInfoCard(
            deck = deckB,
            accent = DeckBRed,
            secondaryWave = DeckBOrange,
            alignRight = true,
            modifier = Modifier.weight(1f),
            onSeek = onSeekB,
            onZoom = onZoomB,
            onBpmTap = { onEditBpm(DeckId.B, deckB.originalBpm) }
        )

        // Optional Compact SoundCloud Quick Card (hidden in Fullscreen Mixer Mode)
        if (showSourceCards && !isFullscreenMixer) {
            SourceMiniCard(
                label = "SoundCloud",
                connected = soundCloudState.isConnected,
                accent = SoundCloudOrange,
                tag = "top_soundcloud_card",
                onClick = onOpenSources
            )
        }
    }
}

@Composable
private fun SourceMiniCard(
    label: String,
    connected: Boolean,
    accent: Color,
    tag: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(66.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0B1120))
            .border(1.dp, accent.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .testTag(tag)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (label == "Spotify") Icons.Default.GraphicEq else Icons.Default.CloudQueue,
            contentDescription = label,
            tint = accent,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            color = TextPrimary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = if (connected) "Live" else "Connect ›",
            color = if (connected) LedGreen else accent,
            fontSize = 8.sp,
            fontFamily = JetBrainsMonoFontFamily
        )
    }
}

@Composable
private fun DeckSongInfoCard(
    deck: DeckState,
    accent: Color,
    secondaryWave: Color,
    alignRight: Boolean,
    modifier: Modifier = Modifier,
    onSeek: (Float) -> Unit,
    onZoom: (Float) -> Unit,
    onBpmTap: () -> Unit
) {
    val artRes = if (deck.artworkResName == "deck_b") {
        R.drawable.img_deck_b_art_1795192576972
    } else {
        R.drawable.img_deck_a_art_1795192577075
    }

    Row(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0B101D))
            .border(1.2.dp, accent.copy(alpha = 0.75f), RoundedCornerShape(8.dp))
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!alignRight) {
            Image(
                painter = painterResource(artRes),
                contentDescription = "Deck A Artwork",
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(6.dp))
        }

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
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "${formatTimeMs(deck.positionMs)} / ${formatTimeMs(deck.durationMs)}",
                            color = accent,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = JetBrainsMonoFontFamily
                        )
                    }
                }

                // BPM Pill + Zoom toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(accent.copy(alpha = 0.2f))
                            .border(1.dp, accent, RoundedCornerShape(5.dp))
                            .clickable { onBpmTap() }
                            .testTag("bpm_badge_${deck.deckId.name.lowercase()}")
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = String.format("%.1f BPM", deck.effectiveBpm),
                            color = TextPrimary,
                            fontSize = 9.sp,
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
                            fontSize = 8.sp,
                            fontFamily = JetBrainsMonoFontFamily
                        )
                    }
                }
            }

            // Real Decoded Waveform
            RealWaveformCanvas(
                deck = deck,
                primaryColor = accent,
                secondaryColor = secondaryWave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp),
                onSeekRatio = onSeek
            )
        }

        if (alignRight) {
            Spacer(Modifier.width(6.dp))
            Image(
                painter = painterResource(artRes),
                contentDescription = "Deck B Artwork",
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )
        }
    }
}

/**
 * Bottom Transport & Master Control Strip:
 * Deck A (CUE, PLAY/PAUSE, SYNC) | AUTO FADE + Crossfader | Deck B (SYNC, PLAY/PAUSE, CUE)
 * Plus Master Volume slider and BIJOY DJ v1.0 label.
 */
@Composable
fun BottomTransportBar(
    deckA: DeckState,
    deckB: DeckState,
    mixer: MixerState,
    onCueA: () -> Unit,
    onPlayPauseA: () -> Unit,
    onSyncA: () -> Unit,
    onCueB: () -> Unit,
    onPlayPauseB: () -> Unit,
    onSyncB: () -> Unit,
    onToggleAutoFade: () -> Unit,
    onCrossfaderChange: (Float) -> Unit,
    onMasterVolumeChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF070A12))
            .border(width = 1.dp, color = MetalBorder.copy(alpha = 0.5f))
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // Deck A Transport
            TransportPillButton("CUE", false, DeckACyan, "deck_a_cue_btn", onCueA)
            PlayPauseHardwareButton(
                isPlaying = deckA.isPlaying,
                tag = "deck_a_play_btn",
                onClick = onPlayPauseA
            )
            TransportPillButton("SYNC", deckA.syncActive, DeckACyan, "deck_a_sync_btn", onSyncA)

            // Auto Fade toggle
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (mixer.autoFadeActive) DeckBOrange.copy(alpha = 0.28f) else Color(0xFF121826))
                    .border(
                        1.2.dp,
                        if (mixer.autoFadeActive) DeckBOrange else DeckBOrange.copy(alpha = 0.5f),
                        RoundedCornerShape(7.dp)
                    )
                    .clickable { onToggleAutoFade() }
                    .testTag("auto_fade_button")
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (mixer.autoFadeActive) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                    contentDescription = "Auto Fade",
                    tint = DeckBOrange,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "AUTO FADE",
                    color = TextPrimary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = JetBrainsMonoFontFamily
                )
            }

            // Hardware Crossfader
            HardwareCrossfader(
                value = mixer.crossfader,
                modifier = Modifier.weight(1f),
                onValueChange = onCrossfaderChange
            )

            // Deck B Transport
            TransportPillButton("SYNC", deckB.syncActive, DeckBOrange, "deck_b_sync_btn", onSyncB)
            PlayPauseHardwareButton(
                isPlaying = deckB.isPlaying,
                tag = "deck_b_play_btn",
                onClick = onPlayPauseB
            )
            TransportPillButton("CUE", false, DeckBRed, "deck_b_cue_btn", onCueB)
        }

        // Master Volume Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.VolumeUp,
                contentDescription = "Master Volume",
                tint = DeckACyan,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "MASTER",
                color = TextSecondary,
                fontSize = 9.sp,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold
            )
            Slider(
                value = mixer.masterVolume,
                onValueChange = onMasterVolumeChange,
                modifier = Modifier
                    .width(170.dp)
                    .testTag("master_volume_slider"),
                colors = SliderDefaults.colors(
                    thumbColor = TextPrimary,
                    activeTrackColor = DeckACyan,
                    inactiveTrackColor = Color(0xFF1E293B)
                )
            )
            Text(
                text = "${(mixer.masterVolume * 100).toInt()}%",
                color = DeckACyan,
                fontSize = 9.sp,
                fontFamily = JetBrainsMonoFontFamily
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "CURVE: ${mixer.crossfaderCurve.displayName.uppercase()}",
                color = TextMuted,
                fontSize = 8.sp,
                fontFamily = JetBrainsMonoFontFamily
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "BIJOY DJ",
                color = TextPrimary,
                fontFamily = OrbitronFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            )
            Text(
                text = " v1.0",
                color = TextSecondary,
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 9.sp
            )
        }
    }
}

@Composable
private fun TransportPillButton(
    label: String,
    active: Boolean,
    accent: Color,
    tag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (active) accent.copy(alpha = 0.30f) else Color(0xFF121826))
            .border(1.2.dp, if (active) accent else MetalBorder, RoundedCornerShape(7.dp))
            .clickable { onClick() }
            .testTag(tag)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (active) Color.White else TextPrimary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = JetBrainsMonoFontFamily
        )
    }
}

@Composable
private fun PlayPauseHardwareButton(
    isPlaying: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (isPlaying) LedGreen.copy(alpha = 0.28f) else Color(0xFF101820))
            .border(1.5.dp, if (isPlaying) LedGreen else LedGreen.copy(alpha = 0.65f), RoundedCornerShape(7.dp))
            .clickable { onClick() }
            .testTag(tag)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            tint = LedGreen,
            modifier = Modifier.size(18.dp)
        )
    }
}
