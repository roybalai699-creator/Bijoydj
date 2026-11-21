package com.example.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.AppSection
import com.example.audio.LayoutMode
import com.example.audio.SfxPadUi
import com.example.database.LayoutPreferenceEntity
import com.example.ui.theme.*
import com.example.viewmodel.BijoyViewModel

/**
 * Collapsible & Hideable Left Vertical Sidebar matching the exact 12 sections from the reference image:
 * Library, Folders, Playlists, Favorites, History, Recordings, Music Sources, SFX, Sampler, FX, Equalizer, Settings.
 */
@Composable
fun LeftNavigationSidebar(
    layout: LayoutPreferenceEntity,
    activeSection: AppSection,
    collapsed: Boolean,
    onToggleCollapse: () -> Unit,
    onNavigate: (AppSection) -> Unit
) {
    if (!layout.showLeftSidebar) return

    if (collapsed) {
        Box(
            modifier = Modifier
                .width(20.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF0E1525))
                .border(1.dp, DeckACyan.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                .clickable { onToggleCollapse() }
                .testTag("left_sidebar_expand_tab"),
            contentAlignment = Alignment.Center
        ) {
            Icon(BijoyIcons.ChevronRight, contentDescription = "Expand Left Sidebar", tint = DeckACyan, modifier = Modifier.size(15.dp))
        }
        return
    }

    val widthDp = layout.sidebarWidthDp.coerceIn(72, 118).dp
    val compact = layout.layoutMode == "COMPACT"

    Column(
        modifier = Modifier
            .width(widthDp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF080C15))
            .border(0.8.dp, MetalBorder.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(3.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Slim Collapse Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF101726))
                .clickable { onToggleCollapse() }
                .testTag("left_sidebar_collapse_btn")
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(BijoyIcons.ChevronLeft, contentDescription = "Collapse Left Sidebar", tint = TextSecondary, modifier = Modifier.size(13.dp))
        }

        val aiItems = listOf(
            Triple(AppSection.AI_REMIX_STUDIO, BijoyIcons.AutoAwesome, layout.showAdditionalTools),
            Triple(AppSection.STEM_SEPARATOR, BijoyIcons.Layers, layout.showAdditionalTools),
            Triple(AppSection.AI_AUTO_DJ, BijoyIcons.SmartToy, layout.showAdditionalTools),
            Triple(AppSection.SAD_TO_DJ, BijoyIcons.Bolt, layout.showAdditionalTools),
            Triple(AppSection.AI_REMIX_GENERATOR, BijoyIcons.Pattern, layout.showAdditionalTools),
            Triple(AppSection.AI_ASSISTANT, BijoyIcons.Assistant, layout.showAdditionalTools)
        )

        val items = listOf(
            Triple(AppSection.MUSIC_LIBRARY, BijoyIcons.MusicNote, layout.showMusicLibrary),
            Triple(AppSection.FOLDERS, BijoyIcons.Folder, layout.showFolders),
            Triple(AppSection.PLAYLISTS, BijoyIcons.QueueMusic, layout.showPlaylists),
            Triple(AppSection.FAVORITES, Icons.Default.Favorite, layout.showFavorites),
            Triple(AppSection.HISTORY, BijoyIcons.Schedule, layout.showHistory),
            Triple(AppSection.RECORDINGS, BijoyIcons.Mic, layout.showRecordingPanel),
            Triple(AppSection.MUSIC_SOURCES, BijoyIcons.Cloud, layout.showAdditionalTools),
            Triple(AppSection.SOUND_EFFECTS, BijoyIcons.GraphicEq, layout.showSoundEffects),
            Triple(AppSection.SAMPLER, BijoyIcons.GridView, layout.showSamplerPanel),
            Triple(AppSection.DJ_EFFECTS, BijoyIcons.Tune, layout.showFxPanel),
            Triple(AppSection.EQUALIZER, BijoyIcons.Equalizer, true),
            Triple(AppSection.SETTINGS, Icons.Default.Settings, true)
        )

        aiItems.forEach { (section, icon, visible) ->
            if (visible) {
                val highlighted = activeSection == section
                SidebarListItem(
                    label = section.title,
                    icon = icon,
                    iconTint = if (highlighted) Color.White else CrownGold,
                    highlighted = highlighted,
                    compact = compact,
                    tag = "sidebar_${section.name.lowercase()}",
                    onClick = { onNavigate(section) }
                )
            }
        }

        HorizontalDivider(color = MetalBorder.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 2.dp))

        items.forEach { (section, icon, visible) ->
            if (visible) {
                val highlighted = activeSection == section || (activeSection == AppSection.DJ_MIXER && section == AppSection.MUSIC_LIBRARY)
                val iconTint = when {
                    section == AppSection.FAVORITES -> LedRed
                    highlighted -> Color.White
                    else -> TextSecondary
                }
                SidebarListItem(
                    label = section.title,
                    icon = icon,
                    iconTint = iconTint,
                    highlighted = highlighted,
                    compact = compact,
                    tag = "sidebar_${section.name.lowercase()}",
                    onClick = { onNavigate(section) }
                )
            }
        }
    }
}

@Composable
private fun SidebarListItem(
    label: String,
    icon: ImageVector,
    iconTint: Color,
    highlighted: Boolean,
    compact: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (highlighted) Brush.horizontalGradient(listOf(DeckABlue, DeckACyan))
                else Brush.horizontalGradient(listOf(Color(0xFF0C111E), Color(0xFF0C111E)))
            )
            .clickable { onClick() }
            .testTag(tag)
            .padding(horizontal = 5.dp, vertical = if (compact) 4.dp else 5.5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = iconTint, modifier = Modifier.size(14.dp))
        if (!compact) {
            Spacer(Modifier.width(5.dp))
            Text(
                text = label,
                color = if (highlighted) Color.White else TextPrimary.copy(alpha = 0.9f),
                fontSize = 8.5.sp,
                fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Right Collapsible Panel matching the exact reference image:
 * - Top Section: "SFX" with X collapse button + 3x4 Grid of 12 glowing SFX trigger pads
 * - Bottom Section: "Sampler" with X collapse button + 3x2 Grid of colorful numbered Sampler Pads (1..6) + Roll quick toggle
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RightSfxSidebar(
    layout: LayoutPreferenceEntity,
    pads: List<SfxPadUi>,
    collapsed: Boolean,
    samplerRollActive: Boolean,
    onToggleCollapse: () -> Unit,
    onTriggerPad: (SfxPadUi) -> Unit,
    onTogglePadLoop: (Int) -> Unit,
    onToggleSamplerRoll: (Int) -> Unit,
    onStopAllSfx: () -> Unit
) {
    if (!layout.showRightSidebar || (!layout.showSoundEffects && !layout.showSamplerPanel)) {
        return
    }

    if (collapsed) {
        Box(
            modifier = Modifier
                .width(20.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF160E24))
                .border(1.dp, LedPurple.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                .clickable { onToggleCollapse() }
                .testTag("right_sidebar_expand_tab"),
            contentAlignment = Alignment.Center
        ) {
            Icon(BijoyIcons.ChevronLeft, contentDescription = "Expand SFX & Sampler Panel", tint = LedPurple, modifier = Modifier.size(15.dp))
        }
        return
    }

    val widthDp = (layout.sidebarWidthDp + 44).coerceIn(110, 152).dp

    Column(
        modifier = Modifier
            .width(widthDp)
            .fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        var showExtraPads by remember { mutableStateOf(false) }

        // 1. TOP SFX BOX (12 visible per page + 1-12 / 5-16 bank toggle for all 16 SFX buttons)
        if (layout.showSoundEffects) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.45f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0A0E18))
                    .border(1.dp, MetalBorder, RoundedCornerShape(8.dp))
                    .padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (showExtraPads) "SFX+" else "SFX", color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = OrbitronFontFamily)
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (showExtraPads) "1-12" else "13-16",
                            color = DeckACyan,
                            fontSize = 6.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = JetBrainsMonoFontFamily,
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(DeckACyan.copy(alpha = 0.16f))
                                .clickable { showExtraPads = !showExtraPads }
                                .testTag("sfx_bank_page_btn")
                                .padding(horizontal = 3.dp, vertical = 1.dp)
                        )
                        Text(
                            text = "STOP",
                            color = LedRed,
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = JetBrainsMonoFontFamily,
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(LedRed.copy(alpha = 0.16f))
                                .clickable { onStopAllSfx() }
                                .testTag("sfx_stop_all_btn")
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Collapse SFX",
                            tint = TextSecondary,
                            modifier = Modifier
                                .size(14.dp)
                                .clickable { onToggleCollapse() }
                                .testTag("right_sidebar_collapse_btn")
                        )
                    }
                }

                // Exact reference 3x4 color & icon scheme for the 16 SFX buttons
                val sfxPalette = listOf(
                    Color(0xFF00E676) to BijoyIcons.Campaign,      // 1. Airhorn
                    Color(0xFF76FF03) to BijoyIcons.GpsFixed,      // 2. Gunshot
                    Color(0xFF00C853) to BijoyIcons.FlashOn,       // 3. Laser
                    Color(0xFFFFD600) to BijoyIcons.NotificationsActive, // 4. Police Siren
                    Color(0xFF00E676) to BijoyIcons.SportsMma,     // 5. Punch
                    Color(0xFF00B0FF) to BijoyIcons.Alarm,         // 6. Siren
                    Color(0xFFD500F9) to BijoyIcons.Album,         // 7. Scratch
                    Color(0xFFFF9100) to BijoyIcons.Groups,        // 8. Crowd
                    Color(0xFF0091EA) to BijoyIcons.MusicNote,     // 9. Drum
                    Color(0xFF64DD17) to BijoyIcons.GraphicEq,     // 10. Bass Drop
                    Color(0xFFFF1744) to BijoyIcons.PanTool,       // 11. Clap
                    Color(0xFFFFB300) to BijoyIcons.Mic,           // 12. DJ Voice
                    Color(0xFFFFEA00) to BijoyIcons.MusicNote,     // 13. Snare Snap
                    Color(0xFF18FFFF) to BijoyIcons.TrendingUp,    // 14. Riser Sweep
                    Color(0xFFFF5252) to BijoyIcons.GraphicEq,     // 15. Impact Boom
                    Color(0xFFB388FF) to BijoyIcons.RecordVoiceOver // 16. DJ Drop
                )

                val visiblePads = if (showExtraPads && pads.size > 12) pads.drop(4).take(12) else pads.take(12)

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    items(visiblePads, key = { it.index }) { pad ->
                        val (bgCol, icon) = sfxPalette.getOrElse(pad.index) { DeckACyan to BijoyIcons.Audiotrack }
                        Column(
                            modifier = Modifier
                                .height(38.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(if (pad.isPlaying) bgCol else bgCol.copy(alpha = 0.86f))
                                .border(
                                    width = if (pad.isPlaying || pad.isLooping) 1.5.dp else 0.7.dp,
                                    color = if (pad.isLooping) Color.White else bgCol.copy(alpha = 0.9f),
                                    shape = RoundedCornerShape(5.dp)
                                )
                                .combinedClickable(
                                    onClick = { onTriggerPad(pad) },
                                    onLongClick = { onTogglePadLoop(pad.index) }
                                )
                                .testTag("sfx_pad_${pad.index}")
                                .padding(2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(icon, contentDescription = pad.label, tint = Color(0xFF090D16), modifier = Modifier.size(14.dp))
                            Text(
                                text = pad.label,
                                color = Color(0xFF090D16),
                                fontSize = 6.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // 2. BOTTOM SAMPLER BOX (Numbered 1..6 colorful pads + ROLL trigger)
        if (layout.showSamplerPanel) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.55f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0A0E18))
                    .border(1.dp, MetalBorder, RoundedCornerShape(8.dp))
                    .padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Sampler", color = TextPrimary, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, fontFamily = OrbitronFontFamily)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (samplerRollActive) "ROLL ON" else "ROLL",
                            color = if (samplerRollActive) ChassisVoid else DeckACyan,
                            fontSize = 6.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = JetBrainsMonoFontFamily,
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (samplerRollActive) DeckACyan else DeckACyan.copy(alpha = 0.16f))
                                .clickable { onToggleSamplerRoll(0) }
                                .testTag("sampler_roll_toggle")
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Collapse Sampler",
                            tint = TextSecondary,
                            modifier = Modifier
                                .size(13.dp)
                                .clickable { onToggleCollapse() }
                        )
                    }
                }

                val samplerColors = listOf(
                    LedRed,            // 1 Red
                    DeckBOrange,       // 2 Orange
                    DeckACyan,         // 3 Cyan
                    LedGreen,          // 4 Green
                    Color(0xFFB388FF), // 5 Lavender/Purple
                    DeckBGold          // 6 Gold
                )
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    for (row in 0..1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            for (col in 0..2) {
                                val idx = row * 3 + col
                                val pad = pads.getOrNull(idx)
                                val pCol = samplerColors[idx]
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(if (pad?.isPlaying == true) pCol else pCol.copy(alpha = 0.85f))
                                        .clickable { if (pad != null) onTriggerPad(pad) }
                                        .testTag("right_sampler_num_${idx + 1}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${idx + 1}",
                                        color = Color(0xFF090D16),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontFamily = OrbitronFontFamily
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun parseHexColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        DeckACyan
    }
}

/**
 * Full Layout Customizer Modal Dialog:
 * - 3 Modes: Standard, Compact, Fullscreen Mixer
 * - Individual Panel Visibility Toggles
 * - Sidebar Width & Main Mixer Resize scale
 * - Save, Apply, Rename, Delete custom layouts + Restore Default
 */
@Composable
fun LayoutCustomizerDialog(
    activeLayout: LayoutPreferenceEntity,
    savedLayouts: List<LayoutPreferenceEntity>,
    viewModel: BijoyViewModel,
    onDismiss: () -> Unit
) {
    var newProfileName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ChassisDark,
        titleContentColor = TextPrimary,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LAYOUT STUDIO",
                    fontFamily = OrbitronFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = DeckACyan
                )
                TextButton(onClick = { viewModel.setLayoutMode(LayoutMode.STANDARD) }) {
                    Text("Restore Default", color = DeckBOrange, fontSize = 11.sp)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("LAYOUT MODES", color = TextSecondary, fontSize = 10.sp, fontFamily = JetBrainsMonoFontFamily)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LayoutMode.entries.forEach { mode ->
                        val selected = activeLayout.layoutMode == mode.name
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) DeckACyan.copy(alpha = 0.25f) else ChassisSurface)
                                .border(1.dp, if (selected) DeckACyan else MetalBorder, RoundedCornerShape(8.dp))
                                .clickable { viewModel.setLayoutMode(mode) }
                                .testTag("layout_mode_${mode.name.lowercase()}")
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = mode.displayName,
                                color = if (selected) Color.White else TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Text(
                    text = "SIDEBAR WIDTH: ${activeLayout.sidebarWidthDp} dp",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontFamily = JetBrainsMonoFontFamily
                )
                Slider(
                    value = activeLayout.sidebarWidthDp.toFloat().coerceIn(60f, 118f),
                    onValueChange = { w ->
                        viewModel.updateActiveLayout { it.copy(sidebarWidthDp = w.toInt()) }
                    },
                    valueRange = 60f..118f,
                    colors = SliderDefaults.colors(thumbColor = DeckACyan, activeTrackColor = DeckACyan)
                )

                Text(
                    text = "CENTRAL MIXER SIZE RATIO: ${String.format("%.2fx", activeLayout.mixerScale)}",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontFamily = JetBrainsMonoFontFamily
                )
                Slider(
                    value = activeLayout.mixerScale.coerceIn(0.8f, 1.35f),
                    onValueChange = { s ->
                        viewModel.updateActiveLayout { it.copy(mixerScale = s) }
                    },
                    valueRange = 0.8f..1.35f,
                    colors = SliderDefaults.colors(thumbColor = CrownGold, activeTrackColor = CrownGold)
                )

                Text("PANEL VISIBILITY CONTROLS", color = TextSecondary, fontSize = 10.sp, fontFamily = JetBrainsMonoFontFamily)
                LayoutToggleRow("Left Sidebar", activeLayout.showLeftSidebar) { v ->
                    viewModel.updateActiveLayout { it.copy(showLeftSidebar = v) }
                }
                LayoutToggleRow("Right SFX & Sampler Panel", activeLayout.showRightSidebar) { v ->
                    viewModel.updateActiveLayout { it.copy(showRightSidebar = v) }
                }
                LayoutToggleRow("Music Library", activeLayout.showMusicLibrary) { v ->
                    viewModel.updateActiveLayout { it.copy(showMusicLibrary = v) }
                }
                LayoutToggleRow("Folders", activeLayout.showFolders) { v ->
                    viewModel.updateActiveLayout { it.copy(showFolders = v) }
                }
                LayoutToggleRow("Playlists", activeLayout.showPlaylists) { v ->
                    viewModel.updateActiveLayout { it.copy(showPlaylists = v) }
                }
                LayoutToggleRow("Favorites", activeLayout.showFavorites) { v ->
                    viewModel.updateActiveLayout { it.copy(showFavorites = v) }
                }
                LayoutToggleRow("History", activeLayout.showHistory) { v ->
                    viewModel.updateActiveLayout { it.copy(showHistory = v) }
                }
                LayoutToggleRow("SFX Panel", activeLayout.showSoundEffects) { v ->
                    viewModel.updateActiveLayout { it.copy(showSoundEffects = v) }
                }
                LayoutToggleRow("Sampler Panel", activeLayout.showSamplerPanel) { v ->
                    viewModel.updateActiveLayout { it.copy(showSamplerPanel = v) }
                }
                LayoutToggleRow("FX Shortcut", activeLayout.showFxPanel) { v ->
                    viewModel.updateActiveLayout { it.copy(showFxPanel = v) }
                }
                LayoutToggleRow("Recording Access", activeLayout.showRecordingPanel) { v ->
                    viewModel.updateActiveLayout { it.copy(showRecordingPanel = v) }
                }

                HorizontalDivider(color = MetalBorder)

                Text("SAVE CUSTOM LAYOUT", color = TextSecondary, fontSize = 10.sp, fontFamily = JetBrainsMonoFontFamily)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = newProfileName,
                        onValueChange = { newProfileName = it },
                        placeholder = { Text("Custom profile name...", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            if (newProfileName.isNotBlank()) {
                                viewModel.saveCustomLayoutProfile(newProfileName)
                                newProfileName = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DeckACyan, contentColor = ChassisVoid)
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                }

                if (savedLayouts.isNotEmpty()) {
                    Text("SAVED PROFILES", color = TextSecondary, fontSize = 10.sp, fontFamily = JetBrainsMonoFontFamily)
                    savedLayouts.forEach { profile ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (profile.isActive) DeckACyan.copy(alpha = 0.16f) else ChassisSurface)
                                .clickable { viewModel.applySavedLayout(profile) }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(profile.profileName, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(profile.layoutMode, color = TextSecondary, fontSize = 9.sp, fontFamily = JetBrainsMonoFontFamily)
                            }
                            if (savedLayouts.size > 1) {
                                IconButton(
                                    onClick = { viewModel.deleteSavedLayout(profile.id) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Layout", tint = LedRed, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = DeckACyan, contentColor = ChassisVoid)
            ) {
                Text("Done", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun LayoutToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextPrimary, fontSize = 12.sp)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = ChassisVoid, checkedTrackColor = DeckACyan)
        )
    }
}
