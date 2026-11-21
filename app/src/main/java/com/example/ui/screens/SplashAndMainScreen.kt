package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.audio.AppSection
import com.example.audio.DeckId
import com.example.ui.theme.*
import com.example.viewmodel.BijoyViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun BijoyDjRootApp(viewModel: BijoyViewModel) {
    val isBootReady by viewModel.isBootReady.collectAsStateWithLifecycle()
    var showSplash by remember { mutableStateOf(true) }

    LaunchedEffect(isBootReady) {
        if (isBootReady) {
            delay(900)
            showSplash = false
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(ChassisVoid)) {
        if (showSplash) {
            BijoySplashScreen()
        } else {
            BijoyMainStudioScaffold(viewModel = viewModel)
        }
    }
}

@Composable
private fun BijoySplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF0E1A30), ChassisVoid)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.img_bijoy_icon_1795192577742),
                contentDescription = "BIJOY DJ Splash Logo",
                modifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
                    .border(2.dp, DeckACyan, CircleShape),
                contentScale = ContentScale.Crop
            )
            Row {
                Text("BIJOY ", color = MetalSilverLight, fontFamily = OrbitronFontFamily, fontWeight = FontWeight.Bold, fontSize = 30.sp)
                Text("DJ", color = DeckBRed, fontFamily = OrbitronFontFamily, fontWeight = FontWeight.Bold, fontSize = 30.sp)
            }
            Text(
                text = "FEEL THE BEAT",
                color = DeckACyan,
                fontFamily = OrbitronFontFamily,
                fontSize = 12.sp,
                letterSpacing = 4.sp
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                modifier = Modifier.width(180.dp).clip(RoundedCornerShape(4.dp)),
                color = DeckACyan,
                trackColor = Color(0xFF1A2233)
            )
            Text("Initializing Dual-Deck Audio Engine...", color = TextSecondary, fontSize = 11.sp, fontFamily = JetBrainsMonoFontFamily)
        }
    }
}

@Composable
private fun BijoyMainStudioScaffold(viewModel: BijoyViewModel) {
    val deckA by viewModel.deckA.collectAsStateWithLifecycle()
    val deckB by viewModel.deckB.collectAsStateWithLifecycle()
    val mixer by viewModel.mixer.collectAsStateWithLifecycle()
    val recState by viewModel.recordingState.collectAsStateWithLifecycle()
    val layout by viewModel.activeLayout.collectAsStateWithLifecycle()
    val savedLayouts by viewModel.savedLayouts.collectAsStateWithLifecycle()
    val sfxPads by viewModel.sfxPads.collectAsStateWithLifecycle()
    val spotifyState by viewModel.spotifyState.collectAsStateWithLifecycle()
    val soundCloudState by viewModel.soundCloudState.collectAsStateWithLifecycle()
    val currentSection by viewModel.currentSection.collectAsStateWithLifecycle()
    val showLayoutDialog by viewModel.showLayoutCustomizer.collectAsStateWithLifecycle()
    val statusToast by viewModel.statusToast.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var leftCollapsed by remember { mutableStateOf(false) }
    var rightCollapsed by remember { mutableStateOf(false) }
    var bpmEditTarget by remember { mutableStateOf<Pair<DeckId, Float>?>(null) }
    var pendingSamplePadIndex by remember { mutableStateOf<Int?>(null) }
    var pendingExportRecordingPath by remember { mutableStateOf<String?>(null) }

    // SAF Launchers (Zero-permission compliant Storage Access Framework)
    val importAudioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> viewModel.importAudioFiles(uris) }

    val customSampleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val padIdx = pendingSamplePadIndex
        if (uri != null && padIdx != null) {
            viewModel.assignCustomSampleToPad(padIdx, uri)
        }
        pendingSamplePadIndex = null
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> if (uri != null) viewModel.exportBackup(uri) }

    val restoreBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.restoreBackup(uri) }

    val exportRecordingLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/wav")
    ) { uri ->
        val path = pendingExportRecordingPath
        if (uri != null && path != null) {
            viewModel.exportRecordingToUri(path, uri)
        }
        pendingExportRecordingPath = null
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.postToast(
            if (granted) "Microphone permission granted for live voiceover recording"
            else "Microphone permission denied — recording master deck audio only"
        )
    }

    LaunchedEffect(statusToast) {
        val msg = statusToast ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
        viewModel.clearToast()
    }

    // BackHandler for all secondary screens to return to primary DJ Mixer
    BackHandler(enabled = currentSection != AppSection.DJ_MIXER) {
        viewModel.navigateTo(AppSection.DJ_MIXER)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = ChassisDark,
                modifier = Modifier.width(260.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF070B14))
                        .statusBarsPadding()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(R.drawable.img_bijoy_icon_1795192577742),
                        contentDescription = null,
                        modifier = Modifier.size(38.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("BIJOY DJ", color = TextPrimary, fontFamily = OrbitronFontFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("FEEL THE BEAT", color = DeckACyan, fontSize = 10.sp, fontFamily = JetBrainsMonoFontFamily)
                    }
                }
                HorizontalDivider(color = MetalBorder)
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(AppSection.entries) { section ->
                        val selected = currentSection == section
                        NavigationDrawerItem(
                            label = { Text(section.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
                            selected = selected,
                            onClick = {
                                viewModel.navigateTo(section)
                                scope.launch { drawerState.close() }
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = DeckACyan.copy(alpha = 0.2f),
                                selectedTextColor = DeckACyan,
                                unselectedTextColor = TextPrimary
                            )
                        )
                    }
                }
            }
        }
    ) {
        Scaffold(
            containerColor = ChassisVoid,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (currentSection) {
                    AppSection.DJ_MIXER -> {
                        val isFullscreen = layout.layoutMode == "FULLSCREEN"
                        Column(modifier = Modifier.fillMaxSize()) {
                            BijoyTopHeaderBar(
                                spectrumBands = mixer.spectrumBands,
                                recordingState = recState,
                                isFullscreenMixer = isFullscreen,
                                showRecButton = layout.showRecordingPanel || isFullscreen,
                                onMenuClick = { scope.launch { drawerState.open() } },
                                onRecClick = { viewModel.navigateTo(AppSection.RECORDINGS) },
                                onLayoutClick = { viewModel.openLayoutCustomizer(true) },
                                onSettingsClick = { viewModel.navigateTo(AppSection.SETTINGS) },
                                onAboutClick = { viewModel.navigateTo(AppSection.ABOUT) },
                                onToggleExpandMixer = { viewModel.toggleExpandMixer() }
                            )

                            TopSongDisplayRow(
                                deckA = deckA,
                                deckB = deckB,
                                spotifyState = spotifyState,
                                soundCloudState = soundCloudState,
                                showSourceCards = layout.showAdditionalTools,
                                isFullscreenMixer = isFullscreen,
                                onSeekA = { viewModel.audioEngine.seekToRatio(DeckId.A, it) },
                                onSeekB = { viewModel.audioEngine.seekToRatio(DeckId.B, it) },
                                onZoomA = { viewModel.audioEngine.setWaveformZoom(DeckId.A, it) },
                                onZoomB = { viewModel.audioEngine.setWaveformZoom(DeckId.B, it) },
                                onSwapDecks = { viewModel.audioEngine.swapDecks() },
                                onEditBpm = { id, cur -> bpmEditTarget = id to cur },
                                onOpenSources = { viewModel.navigateTo(AppSection.MUSIC_SOURCES) }
                            )

                            // Main Middle Area: Left Sidebar + Expandable Dual-Deck Controller + Right SFX Sidebar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(horizontal = if (isFullscreen) 4.dp else 6.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                AnimatedVisibility(visible = !isFullscreen && layout.showLeftSidebar, enter = fadeIn(), exit = fadeOut()) {
                                    LeftNavigationSidebar(
                                        layout = layout,
                                        collapsed = leftCollapsed,
                                        onToggleCollapse = { leftCollapsed = !leftCollapsed },
                                        onNavigate = { viewModel.navigateTo(it) }
                                    )
                                }

                                DjControllerConsole(
                                    deckA = deckA,
                                    deckB = deckB,
                                    mixer = mixer,
                                    isFullscreenMixer = isFullscreen,
                                    viewModel = viewModel,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                )

                                AnimatedVisibility(visible = !isFullscreen && layout.showRightSidebar, enter = fadeIn(), exit = fadeOut()) {
                                    RightSfxSidebar(
                                        layout = layout,
                                        pads = sfxPads,
                                        collapsed = rightCollapsed,
                                        onToggleCollapse = { rightCollapsed = !rightCollapsed },
                                        onOpenFx = { viewModel.navigateTo(AppSection.DJ_EFFECTS) },
                                        onTriggerPad = { viewModel.triggerSfxPad(it) },
                                        onStopAllSfx = { viewModel.stopAllSfx() }
                                    )
                                }
                            }

                            BottomTransportBar(
                                deckA = deckA,
                                deckB = deckB,
                                mixer = mixer,
                                onCueA = { viewModel.audioEngine.triggerCue(DeckId.A) },
                                onPlayPauseA = { viewModel.audioEngine.togglePlayPause(DeckId.A) },
                                onSyncA = { viewModel.audioEngine.toggleSync(DeckId.A) },
                                onCueB = { viewModel.audioEngine.triggerCue(DeckId.B) },
                                onPlayPauseB = { viewModel.audioEngine.togglePlayPause(DeckId.B) },
                                onSyncB = { viewModel.audioEngine.toggleSync(DeckId.B) },
                                onToggleAutoFade = {
                                    viewModel.audioEngine.triggerAutoFade(viewModel.userSettings.value.autoFadeDurationSec)
                                },
                                onCrossfaderChange = { viewModel.audioEngine.setCrossfader(it) },
                                onMasterVolumeChange = { viewModel.audioEngine.setMasterVolume(it) }
                            )
                        }
                    }

                    AppSection.MUSIC_LIBRARY,
                    AppSection.PLAYLISTS,
                    AppSection.FOLDERS,
                    AppSection.FAVORITES,
                    AppSection.HISTORY -> MusicLibraryScreen(
                        section = currentSection,
                        viewModel = viewModel,
                        onImportClick = { importAudioLauncher.launch(arrayOf("audio/*")) },
                        onBack = { viewModel.navigateTo(AppSection.DJ_MIXER) }
                    )

                    AppSection.MUSIC_SOURCES -> MusicSourcesScreen(
                        viewModel = viewModel,
                        onImportLocal = { importAudioLauncher.launch(arrayOf("audio/*")) },
                        onBack = { viewModel.navigateTo(AppSection.DJ_MIXER) }
                    )

                    AppSection.EQUALIZER,
                    AppSection.DJ_EFFECTS -> EqualizerAndEffectsScreen(
                        initialMode = currentSection,
                        viewModel = viewModel,
                        onBack = { viewModel.navigateTo(AppSection.DJ_MIXER) }
                    )

                    AppSection.SOUND_EFFECTS,
                    AppSection.SAMPLER -> SamplerStudioScreen(
                        viewModel = viewModel,
                        onPickSampleForPad = { padIdx ->
                            pendingSamplePadIndex = padIdx
                            customSampleLauncher.launch(arrayOf("audio/*"))
                        },
                        onBack = { viewModel.navigateTo(AppSection.DJ_MIXER) }
                    )

                    AppSection.RECORDINGS -> RecordingStudioScreen(
                        viewModel = viewModel,
                        onRequestMicPermission = { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                        onExportRecording = { path ->
                            pendingExportRecordingPath = path
                            exportRecordingLauncher.launch("BIJOY_DJ_Mix.wav")
                        },
                        onBack = { viewModel.navigateTo(AppSection.DJ_MIXER) }
                    )

                    AppSection.SETTINGS -> SettingsScreen(
                        viewModel = viewModel,
                        onExportBackup = { exportBackupLauncher.launch("bijoy_dj_backup.json") },
                        onRestoreBackup = { restoreBackupLauncher.launch(arrayOf("application/json", "*/*")) },
                        onBack = { viewModel.navigateTo(AppSection.DJ_MIXER) }
                    )

                    AppSection.ABOUT -> AboutBijoyScreen(
                        onBack = { viewModel.navigateTo(AppSection.DJ_MIXER) }
                    )
                }
            }
        }
    }

    // Layout Customizer Modal
    if (showLayoutDialog) {
        LayoutCustomizerDialog(
            activeLayout = layout,
            savedLayouts = savedLayouts,
            viewModel = viewModel,
            onDismiss = { viewModel.openLayoutCustomizer(false) }
        )
    }

    // Manual BPM Override Dialog
    bpmEditTarget?.let { (deckId, currentBpm) ->
        var text by remember(deckId, currentBpm) { mutableStateOf(String.format("%.1f", currentBpm)) }
        AlertDialog(
            onDismissRequest = { bpmEditTarget = null },
            containerColor = ChassisDark,
            title = { Text("Manual BPM — Deck ${deckId.name}", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("BPM (60 - 200)") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    text.toFloatOrNull()?.let { viewModel.updateManualBpm(deckId, it) }
                    bpmEditTarget = null
                }) { Text("Apply BPM") }
            },
            dismissButton = { TextButton(onClick = { bpmEditTarget = null }) { Text("Cancel") } }
        )
    }
}
