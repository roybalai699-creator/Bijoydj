package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
 * Collapsible & Hideable Left Sidebar (Library, Favorites, History, Folders, Playlists).
 * Individual panel visibility toggles are respected; when collapsed displays a slim expand tab.
 */
@Composable
fun LeftNavigationSidebar(
    layout: LayoutPreferenceEntity,
    collapsed: Boolean,
    onToggleCollapse: () -> Unit,
    onNavigate: (AppSection) -> Unit
) {
    if (!layout.showLeftSidebar) return

    if (collapsed) {
        Box(
            modifier = Modifier
                .width(22.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF0E1525))
                .border(1.dp, DeckACyan.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                .clickable { onToggleCollapse() }
                .testTag("left_sidebar_expand_tab"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Expand Left Sidebar",
                tint = DeckACyan,
                modifier = Modifier.size(16.dp)
            )
        }
        return
    }

    val widthDp = layout.sidebarWidthDp.coerceIn(54, 110).dp
    val compact = layout.layoutMode == "COMPACT" || layout.sidebarWidthDp <= 60

    Column(
        modifier = Modifier
            .width(widthDp)
            .fillMaxHeight()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Collapse button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(5.dp))
                .background(Color(0xFF101726))
                .clickable { onToggleCollapse() }
                .testTag("left_sidebar_collapse_btn")
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "Collapse Left Sidebar", tint = TextSecondary, modifier = Modifier.size(14.dp))
        }

        if (layout.showMusicLibrary) {
            SidebarNavTile("Library", Icons.Default.MusicNote, DeckACyan, compact, "sidebar_library") {
                onNavigate(AppSection.MUSIC_LIBRARY)
            }
        }
        if (layout.showFavorites) {
            SidebarNavTile("Favorites", Icons.Default.Star, DeckBGold, compact, "sidebar_favorites") {
                onNavigate(AppSection.FAVORITES)
            }
        }
        if (layout.showHistory) {
            SidebarNavTile("History", Icons.Default.History, DeckABlue, compact, "sidebar_history") {
                onNavigate(AppSection.HISTORY)
            }
        }
        if (layout.showFolders) {
            SidebarNavTile("Folders", Icons.Default.Folder, MetalSilverLight, compact, "sidebar_folders") {
                onNavigate(AppSection.FOLDERS)
            }
        }
        if (layout.showPlaylists) {
            SidebarNavTile("Playlists", Icons.Default.QueueMusic, LedPurple, compact, "sidebar_playlists") {
                onNavigate(AppSection.PLAYLISTS)
            }
        }
    }
}

@Composable
private fun SidebarNavTile(
    label: String,
    icon: ImageVector,
    accent: Color,
    compact: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0E1422))
            .border(1.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .testTag(tag)
            .padding(vertical = if (compact) 6.dp else 8.dp, horizontal = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = accent, modifier = Modifier.size(if (compact) 18.dp else 22.dp))
        if (!compact) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                color = TextPrimary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Collapsible & Hideable Right Sidebar (FX Panel Trigger + Colorful SFX Sampler Pads).
 */
@Composable
fun RightSfxSidebar(
    layout: LayoutPreferenceEntity,
    pads: List<SfxPadUi>,
    collapsed: Boolean,
    onToggleCollapse: () -> Unit,
    onOpenFx: () -> Unit,
    onTriggerPad: (SfxPadUi) -> Unit,
    onStopAllSfx: () -> Unit
) {
    if (!layout.showRightSidebar || (!layout.showSoundEffects && !layout.showFxPanel && !layout.showSamplerPanel)) {
        return
    }

    if (collapsed) {
        Box(
            modifier = Modifier
                .width(22.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF160E24))
                .border(1.dp, LedPurple.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                .clickable { onToggleCollapse() }
                .testTag("right_sidebar_expand_tab"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ChevronLeft,
                contentDescription = "Expand SFX Sidebar",
                tint = LedPurple,
                modifier = Modifier.size(16.dp)
            )
        }
        return
    }

    val widthDp = (layout.sidebarWidthDp + 24).coerceIn(78, 130).dp

    Column(
        modifier = Modifier
            .width(widthDp)
            .fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(0xFF141B2B))
                    .clickable { onToggleCollapse() }
                    .testTag("right_sidebar_collapse_btn")
                    .padding(vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Collapse SFX", tint = TextSecondary, modifier = Modifier.size(14.dp))
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(5.dp))
                    .background(LedRed.copy(alpha = 0.22f))
                    .clickable { onStopAllSfx() }
                    .testTag("sfx_stop_all_btn")
                    .padding(vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("STOP", color = LedRed, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = JetBrainsMonoFontFamily)
            }
        }

        if (layout.showFxPanel) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(LedPurple.copy(alpha = 0.22f))
                    .border(1.2.dp, LedPurple, RoundedCornerShape(8.dp))
                    .clickable { onOpenFx() }
                    .testTag("sidebar_fx_button")
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = "DJ FX & EQ", tint = LedPurple, modifier = Modifier.size(18.dp))
                Text("FX / EQ", color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = OrbitronFontFamily)
            }
        }

        if (layout.showSoundEffects || layout.showSamplerPanel) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(pads, key = { it.index }) { pad ->
                    val padColor = parseHexColor(pad.colorHex)
                    Box(
                        modifier = Modifier
                            .height(44.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(
                                if (pad.isPlaying) padColor.copy(alpha = 0.65f) else padColor.copy(alpha = 0.24f)
                            )
                            .border(
                                width = if (pad.isPlaying) 2.dp else 1.2.dp,
                                color = padColor,
                                shape = RoundedCornerShape(7.dp)
                            )
                            .clickable { onTriggerPad(pad) }
                            .testTag("sfx_pad_${pad.index}")
                            .padding(3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = pad.label,
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            lineHeight = 9.sp,
                            overflow = TextOverflow.Ellipsis
                        )
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
 * - Individual Panel Visibility Toggles (all 10 optional panels)
 * - Sidebar Width adjustment
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
                // 3 Primary Modes
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

                // Sidebar Width Slider
                Text(
                    text = "SIDEBAR WIDTH: ${activeLayout.sidebarWidthDp} dp",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontFamily = JetBrainsMonoFontFamily
                )
                Slider(
                    value = activeLayout.sidebarWidthDp.toFloat().coerceIn(54f, 110f),
                    onValueChange = { w ->
                        viewModel.updateActiveLayout { it.copy(sidebarWidthDp = w.toInt()) }
                    },
                    valueRange = 54f..110f,
                    colors = SliderDefaults.colors(thumbColor = DeckACyan, activeTrackColor = DeckACyan)
                )

                // Individual Panel Visibility Toggles
                Text("PANEL VISIBILITY CONTROLS", color = TextSecondary, fontSize = 10.sp, fontFamily = JetBrainsMonoFontFamily)
                LayoutToggleRow("Left Sidebar", activeLayout.showLeftSidebar) { v ->
                    viewModel.updateActiveLayout { it.copy(showLeftSidebar = v) }
                }
                LayoutToggleRow("Right SFX Sidebar", activeLayout.showRightSidebar) { v ->
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
                LayoutToggleRow("Sound Effects", activeLayout.showSoundEffects) { v ->
                    viewModel.updateActiveLayout { it.copy(showSoundEffects = v) }
                }
                LayoutToggleRow("FX Panel", activeLayout.showFxPanel) { v ->
                    viewModel.updateActiveLayout { it.copy(showFxPanel = v) }
                }
                LayoutToggleRow("Sampler Panel", activeLayout.showSamplerPanel) { v ->
                    viewModel.updateActiveLayout { it.copy(showSamplerPanel = v) }
                }
                LayoutToggleRow("Recording Button", activeLayout.showRecordingPanel) { v ->
                    viewModel.updateActiveLayout { it.copy(showRecordingPanel = v) }
                }
                LayoutToggleRow("Source Quick Cards", activeLayout.showAdditionalTools) { v ->
                    viewModel.updateActiveLayout { it.copy(showAdditionalTools = v) }
                }

                HorizontalDivider(color = MetalBorder)

                // Save Custom Layout
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

                // Saved Profiles List
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
