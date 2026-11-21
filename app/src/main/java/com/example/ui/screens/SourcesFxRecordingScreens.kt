package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audio.*
import com.example.ui.theme.*
import com.example.viewmodel.BijoyViewModel
import kotlinx.coroutines.launch
import java.io.File

// ==================== MUSIC SOURCES (SPOTIFY, SOUNDCLOUD, LOCAL) ====================

@Composable
fun MusicSourcesScreen(
    viewModel: BijoyViewModel,
    onImportLocal: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val spotify by viewModel.spotifyState.collectAsStateWithLifecycle()
    val soundCloud by viewModel.soundCloudState.collectAsStateWithLifecycle()
    val apiConfig by viewModel.sourceManager.apiKeyConfig.collectAsStateWithLifecycle()
    val allSongs by viewModel.allSongs.collectAsStateWithLifecycle()
    var scQuery by remember { mutableStateOf("") }

    var spClientIdInput by remember { mutableStateOf("") }
    var spApiKeyInput by remember { mutableStateOf("") }
    var scClientIdInput by remember { mutableStateOf("") }
    var customBaseUrl by remember(apiConfig.customApiUrl) { mutableStateOf(apiConfig.customApiUrl) }
    var customHeaderName by remember(apiConfig.customApiHeaderName) { mutableStateOf(apiConfig.customApiHeaderName) }
    var customApiKeyInput by remember { mutableStateOf("") }
    var customEndpointPath by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ChassisVoid)
            .navigationBarsPadding()
    ) {
        SubScreenHeader("Music Sources & API Setup", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Primary Local Device & Imported Music Card
            SourceCardContainer(accent = DeckACyan) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LibraryMusic, contentDescription = null, tint = DeckACyan, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Local & Imported Music (Primary Mixing Source)", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("${allSongs.size} tracks available for full dual-deck PCM mixing, scratching, EQ, loops & recording.", color = TextSecondary, fontSize = 11.sp)
                    }
                    Button(
                        onClick = onImportLocal,
                        colors = ButtonDefaults.buttonColors(containerColor = DeckACyan, contentColor = ChassisVoid)
                    ) { Text("Import", fontWeight = FontWeight.Bold) }
                }
            }

            // Official Spotify Connection + Client ID & API Key Card
            SourceCardContainer(accent = SpotifyGreen) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.GraphicEq, contentDescription = "Spotify", tint = SpotifyGreen, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Spotify Account (Official OAuth PKCE / App Remote)", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(spotify.statusMessage, color = if (spotify.isConnected) LedGreen else TextSecondary, fontSize = 11.sp)
                        Text("Redirect URI: bijoydj://callback/spotify • Client ID: ${if (apiConfig.spotifyClientIdSet) "Configured (AES Encrypted)" else "Not Set"}", color = TextMuted, fontSize = 10.sp)
                    }
                    if (spotify.isConnected) {
                        OutlinedButton(onClick = { viewModel.sourceManager.disconnectSpotify() }) {
                            Text("Disconnect", color = LedRed, fontSize = 11.sp)
                        }
                    } else {
                        Button(
                            onClick = {
                                val intent = viewModel.sourceManager.createSpotifyAuthIntent()
                                if (intent != null) context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = ChassisVoid),
                            modifier = Modifier.testTag("connect_spotify_btn")
                        ) { Text("Connect Spotify", fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = spClientIdInput,
                        onValueChange = { spClientIdInput = it },
                        label = { Text("Spotify Client ID", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("spotify_client_id_input")
                    )
                    OutlinedTextField(
                        value = spApiKeyInput,
                        onValueChange = { spApiKeyInput = it },
                        label = { Text("Spotify API Key", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("spotify_api_key_input")
                    )
                    Button(
                        onClick = {
                            viewModel.sourceManager.saveSpotifyCredentials(spClientIdInput, spApiKeyInput)
                            spClientIdInput = ""
                            spApiKeyInput = ""
                            viewModel.postToast("Spotify Client ID & API Key encrypted and saved")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = ChassisVoid),
                        modifier = Modifier.testTag("save_spotify_keys_btn")
                    ) { Text("Save", fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                }
                Spacer(Modifier.height(6.dp))
                Text(spotify.limitationNotice, color = TextMuted, fontSize = 10.sp, lineHeight = 14.sp)
                if (spotify.tracks.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    spotify.tracks.take(6).forEach { pl ->
                        Text("• ${pl.title} (${pl.albumOrPlaylist})", color = TextPrimary, fontSize = 11.sp)
                    }
                }
            }

            // Official SoundCloud Connection Card
            SourceCardContainer(accent = SoundCloudOrange) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudQueue, contentDescription = "SoundCloud", tint = SoundCloudOrange, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("SoundCloud (Official OAuth 2.0 PKCE)", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(soundCloud.statusMessage, color = if (soundCloud.isConnected) LedGreen else TextSecondary, fontSize = 11.sp)
                    }
                    if (soundCloud.isConnected) {
                        OutlinedButton(onClick = { viewModel.sourceManager.disconnectSoundCloud() }) {
                            Text("Disconnect", color = LedRed, fontSize = 11.sp)
                        }
                    } else {
                        Button(
                            onClick = {
                                val intent = viewModel.sourceManager.createSoundCloudAuthIntent()
                                if (intent != null) context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SoundCloudOrange, contentColor = Color.White),
                            modifier = Modifier.testTag("connect_soundcloud_btn")
                        ) { Text("Connect SoundCloud", fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = scClientIdInput,
                        onValueChange = { scClientIdInput = it },
                        label = { Text("SoundCloud Client ID", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            viewModel.sourceManager.saveSoundCloudCredentials(scClientIdInput)
                            scClientIdInput = ""
                            viewModel.postToast("SoundCloud Client ID saved")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SoundCloudOrange, contentColor = Color.White)
                    ) { Text("Save", fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                }
                Spacer(Modifier.height(6.dp))
                Text(soundCloud.limitationNotice, color = TextMuted, fontSize = 10.sp, lineHeight = 14.sp)
                if (soundCloud.isConnected) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = scQuery,
                            onValueChange = { scQuery = it },
                            placeholder = { Text("Search SoundCloud tracks...", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = { scope.launch { viewModel.sourceManager.searchSoundCloudTracks(scQuery) } }) {
                            Text("Search")
                        }
                    }
                    soundCloud.tracks.take(6).forEach { tr ->
                        Text("• ${tr.title} — ${tr.artist}", color = TextPrimary, fontSize = 11.sp)
                    }
                }
            }

            // Universal Custom API Key & Endpoint Connector Card
            SourceCardContainer(accent = LedPurple) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VpnKey, contentDescription = "API Key Manager", tint = LedPurple, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Custom API Key System (Modular Provider Adapter)", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            text = if (apiConfig.customApiKeySet) "Encrypted API Key stored in Android Keystore" else "Configure any external REST API base URL, header & API Key",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customBaseUrl,
                        onValueChange = { customBaseUrl = it },
                        label = { Text("API Base URL (https://...)", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1.4f)
                    )
                    OutlinedTextField(
                        value = customHeaderName,
                        onValueChange = { customHeaderName = it },
                        label = { Text("Header (e.g. X-API-Key)", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = customApiKeyInput,
                        onValueChange = { customApiKeyInput = it },
                        label = { Text("API Key / Bearer Token", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("custom_api_key_input")
                    )
                    Button(
                        onClick = {
                            viewModel.sourceManager.saveCustomApiConfig(customBaseUrl, customHeaderName, customApiKeyInput)
                            customApiKeyInput = ""
                            viewModel.postToast("Custom API configuration encrypted & saved")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = LedPurple),
                        modifier = Modifier.testTag("save_custom_api_btn")
                    ) { Text("Save Key", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = customEndpointPath,
                        onValueChange = { customEndpointPath = it },
                        label = { Text("Test Endpoint Path (e.g. /tracks)", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = {
                        viewModel.sourceManager.saveCustomApiConfig(customBaseUrl, customHeaderName, customApiKeyInput)
                        scope.launch { viewModel.sourceManager.callCustomApiEndpoint(customEndpointPath) }
                    }) {
                        Text("Call API", color = DeckACyan, fontSize = 11.sp)
                    }
                }
                if (apiConfig.lastCustomApiResponse.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = apiConfig.lastCustomApiResponse,
                        color = DeckACyan,
                        fontSize = 10.sp,
                        fontFamily = JetBrainsMonoFontFamily
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceCardContainer(accent: Color, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(ChassisCard)
            .border(1.2.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .padding(12.dp),
        content = content
    )
}

// ==================== EQUALIZER & DJ EFFECTS STUDIO ====================

@Composable
fun EqualizerAndEffectsScreen(
    initialMode: AppSection,
    viewModel: BijoyViewModel,
    onBack: () -> Unit
) {
    val deckA by viewModel.deckA.collectAsStateWithLifecycle()
    val deckB by viewModel.deckB.collectAsStateWithLifecycle()
    val mixer by viewModel.mixer.collectAsStateWithLifecycle()
    var target by remember { mutableStateOf(EffectTarget.DECK_A) }
    val activeDeck = if (target == EffectTarget.DECK_B) deckB else deckA
    val engine = viewModel.audioEngine

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ChassisVoid)
            .navigationBarsPadding()
    ) {
        SubScreenHeader(
            title = if (initialMode == AppSection.EQUALIZER) "Equalizer Studio" else "DJ Effects & EQ",
            onBack = onBack,
            actions = {
                TextButton(onClick = {
                    val id = if (target == EffectTarget.DECK_B) DeckId.B else DeckId.A
                    engine.updateDeckEq(id, gainDb = 0f, trebleDb = 0f, midDb = 0f, bassDb = 0f, filterCutoff = 0f, preset = EqPreset.FLAT)
                }) {
                    Text("Reset EQ", color = DeckBOrange)
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Target Selector (Deck A / Deck B / Master)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EffectTarget.entries.forEach { t ->
                    FilterChip(
                        selected = target == t,
                        onClick = { target = t },
                        label = { Text(t.displayName, fontFamily = JetBrainsMonoFontFamily) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DeckACyan.copy(alpha = 0.25f),
                            selectedLabelColor = DeckACyan
                        )
                    )
                }
            }

            // EQ Presets
            if (target != EffectTarget.MASTER) {
                val deckId = if (target == EffectTarget.DECK_B) DeckId.B else DeckId.A
                Text("EQ PRESETS (${target.displayName})", color = TextSecondary, fontSize = 10.sp, fontFamily = JetBrainsMonoFontFamily)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(EqPreset.entries) { preset ->
                        FilterChip(
                            selected = activeDeck.eqPreset == preset,
                            onClick = { engine.applyEqPreset(deckId, preset) },
                            label = { Text(preset.displayName, fontSize = 11.sp) }
                        )
                    }
                }

                // 3-Band EQ + Filter Sliders
                EqSliderRow("Treble (High)", activeDeck.trebleDb, -12f..12f, "${String.format("%+.1f", activeDeck.trebleDb)} dB", DeckACyan) {
                    engine.updateDeckEq(deckId, trebleDb = it)
                }
                EqSliderRow("Mid", activeDeck.midDb, -12f..12f, "${String.format("%+.1f", activeDeck.midDb)} dB", DeckBGold) {
                    engine.updateDeckEq(deckId, midDb = it)
                }
                EqSliderRow("Bass (Low)", activeDeck.bassDb, -12f..12f, "${String.format("%+.1f", activeDeck.bassDb)} dB", DeckBRed) {
                    engine.updateDeckEq(deckId, bassDb = it)
                }
                EqSliderRow(
                    "Filter (LPF ◄ 0 ► HPF)",
                    activeDeck.filterCutoff,
                    -1f..1f,
                    when {
                        activeDeck.filterCutoff < -0.05f -> "LPF"
                        activeDeck.filterCutoff > 0.05f -> "HPF"
                        else -> "OFF"
                    },
                    LedPurple
                ) {
                    engine.updateDeckEq(deckId, filterCutoff = it)
                }
            }

            HorizontalDivider(color = MetalBorder)

            // 11 Professional DJ Effects
            val currentFx = when (target) {
                EffectTarget.DECK_A -> deckA.activeEffect
                EffectTarget.DECK_B -> deckB.activeEffect
                EffectTarget.MASTER -> mixer.masterEffect
            }
            val currentWet = when (target) {
                EffectTarget.DECK_A -> deckA.effectWet
                EffectTarget.DECK_B -> deckB.effectWet
                EffectTarget.MASTER -> mixer.masterEffectWet
            }
            val currentParam = when (target) {
                EffectTarget.DECK_A -> deckA.effectParam
                EffectTarget.DECK_B -> deckB.effectParam
                EffectTarget.MASTER -> mixer.masterEffectParam
            }

            Text("DJ EFFECTS (${target.displayName})", color = TextSecondary, fontSize = 10.sp, fontFamily = JetBrainsMonoFontFamily)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(DjEffectType.entries) { fx ->
                    val selected = currentFx == fx
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) LedPurple.copy(alpha = 0.35f) else ChassisCard)
                            .border(1.2.dp, if (selected) LedPurple else MetalBorder, RoundedCornerShape(8.dp))
                            .clickable {
                                when (target) {
                                    EffectTarget.DECK_A -> engine.setDeckEffect(DeckId.A, fx)
                                    EffectTarget.DECK_B -> engine.setDeckEffect(DeckId.B, fx)
                                    EffectTarget.MASTER -> engine.setMasterEffect(fx)
                                }
                            }
                            .testTag("fx_chip_${fx.name.lowercase()}")
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(fx.displayName, color = if (selected) Color.White else TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            EqSliderRow("Effect Wet / Dry Mix", currentWet, 0f..1f, "${(currentWet * 100).toInt()}%", LedPurple) { w ->
                when (target) {
                    EffectTarget.DECK_A -> engine.setDeckEffect(DeckId.A, currentFx, wet = w)
                    EffectTarget.DECK_B -> engine.setDeckEffect(DeckId.B, currentFx, wet = w)
                    EffectTarget.MASTER -> engine.setMasterEffect(currentFx, wet = w)
                }
            }
            EqSliderRow("Effect Depth / Rate", currentParam, 0f..1f, "${(currentParam * 100).toInt()}%", DeckACyan) { p ->
                when (target) {
                    EffectTarget.DECK_A -> engine.setDeckEffect(DeckId.A, currentFx, param = p)
                    EffectTarget.DECK_B -> engine.setDeckEffect(DeckId.B, currentFx, param = p)
                    EffectTarget.MASTER -> engine.setMasterEffect(currentFx, param = p)
                }
            }
        }
    }
}

@Composable
private fun EqSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    readout: String,
    color: Color,
    onChange: (Float) -> Unit
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(readout, color = color, fontSize = 11.sp, fontFamily = JetBrainsMonoFontFamily, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color)
        )
    }
}

// ==================== SAMPLER & SFX STUDIO ====================

@Composable
fun SamplerStudioScreen(
    viewModel: BijoyViewModel,
    onPickSampleForPad: (Int) -> Unit,
    onBack: () -> Unit
) {
    val pads by viewModel.sfxPads.collectAsStateWithLifecycle()
    var editingPad by remember { mutableStateOf<SfxPadUi?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ChassisVoid)
            .navigationBarsPadding()
    ) {
        SubScreenHeader(
            title = "SFX & Sampler Studio",
            onBack = onBack,
            actions = {
                TextButton(onClick = { viewModel.stopAllSfx() }) {
                    Text("STOP ALL", color = LedRed, fontFamily = JetBrainsMonoFontFamily)
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(pads, key = { it.index }) { pad ->
                val color = parseHexColor(pad.colorHex)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(ChassisCard)
                        .border(1.dp, color.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(color.copy(alpha = 0.28f))
                            .border(1.5.dp, color, RoundedCornerShape(8.dp))
                            .clickable { viewModel.triggerSfxPad(pad) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Trigger ${pad.label}", tint = Color.White)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(pad.label, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            text = if (pad.customAudioUri != null) "Custom Audio Sample • Vol ${(pad.volume * 100).toInt()}%" else "Built-in Synth (${pad.synthType}) • Vol ${(pad.volume * 100).toInt()}%",
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                    }
                    IconButton(onClick = { onPickSampleForPad(pad.index) }) {
                        Icon(Icons.Default.AudioFile, contentDescription = "Assign Sample", tint = DeckACyan)
                    }
                    IconButton(onClick = { editingPad = pad }) {
                        Icon(Icons.Default.Tune, contentDescription = "Edit Pad", tint = TextSecondary)
                    }
                }
            }
        }
    }

    editingPad?.let { pad ->
        var label by remember(pad) { mutableStateOf(pad.label) }
        var vol by remember(pad) { mutableFloatStateOf(pad.volume) }
        AlertDialog(
            onDismissRequest = { editingPad = null },
            containerColor = ChassisDark,
            title = { Text("Configure Pad ${pad.index + 1}", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Pad Name") }, singleLine = true)
                    Text("Volume: ${(vol * 100).toInt()}%", color = TextSecondary, fontSize = 11.sp)
                    Slider(value = vol, onValueChange = { vol = it }, valueRange = 0.1f..1.4f)
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.updateSfxPadConfig(pad.index, label, vol)
                    editingPad = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editingPad = null }) { Text("Cancel") } }
        )
    }
}

// ==================== RECORDING STUDIO ====================

@Composable
fun RecordingStudioScreen(
    viewModel: BijoyViewModel,
    onRequestMicPermission: () -> Unit,
    onExportRecording: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recState by viewModel.recordingState.collectAsStateWithLifecycle()
    val recordings by viewModel.allRecordings.collectAsStateWithLifecycle()
    var customTitle by remember { mutableStateOf("") }
    var includeMic by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ChassisVoid)
            .navigationBarsPadding()
    ) {
        SubScreenHeader("DJ Recording Studio", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ChassisCard)
                .border(1.2.dp, if (recState.isRecording) LedRed else DeckACyan.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(if (recState.isRecording && !recState.isPaused) LedRed else TextMuted)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (recState.isRecording) (if (recState.isPaused) "PAUSED" else "RECORDING MASTER MIX") else "READY TO RECORD",
                        color = if (recState.isRecording) LedRed else TextPrimary,
                        fontFamily = OrbitronFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
                Text(
                    text = formatTimeMs(recState.elapsedMs),
                    color = DeckACyan,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }

            Text(
                text = "Captures the real mixed master output (Deck A + Deck B + Crossfader + EQ + FX + SFX) as 16-bit 44.1kHz Stereo WAV.",
                color = TextSecondary,
                fontSize = 11.sp
            )

            if (!recState.isRecording) {
                OutlinedTextField(
                    value = customTitle,
                    onValueChange = { customTitle = it },
                    placeholder = { Text("Optional mix title (e.g., Party_Set_Live)...", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Request Microphone Input Permission", color = TextSecondary, fontSize = 11.sp)
                    Switch(
                        checked = includeMic,
                        onCheckedChange = {
                            includeMic = it
                            if (it) onRequestMicPermission()
                        }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!recState.isRecording) {
                    Button(
                        onClick = { viewModel.startRecording(customTitle, includeMic) },
                        colors = ButtonDefaults.buttonColors(containerColor = LedRed),
                        modifier = Modifier.testTag("start_recording_btn")
                    ) {
                        Icon(Icons.Default.FiberManualRecord, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Start Recording", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = { if (recState.isPaused) viewModel.resumeRecording() else viewModel.pauseRecording() },
                        colors = ButtonDefaults.buttonColors(containerColor = DeckBOrange)
                    ) {
                        Text(if (recState.isPaused) "Resume" else "Pause", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { viewModel.stopAndSaveRecording() },
                        colors = ButtonDefaults.buttonColors(containerColor = DeckACyan, contentColor = ChassisVoid),
                        modifier = Modifier.testTag("stop_recording_btn")
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Stop & Save WAV", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Text(
            text = "RECORDING HISTORY (${recordings.size})",
            color = TextSecondary,
            fontSize = 10.sp,
            fontFamily = JetBrainsMonoFontFamily,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(recordings, key = { it.id }) { rec ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(ChassisCard)
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null, tint = LedRed)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(rec.title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            "${rec.format} • ${formatTimeMs(rec.durationMs)} • ${(rec.fileSizeBytes / 1024)} KB",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontFamily = JetBrainsMonoFontFamily
                        )
                    }
                    TextButton(onClick = { viewModel.loadRecordingToDeck(DeckId.A, rec) }) {
                        Text("Deck A", color = DeckACyan, fontSize = 10.sp)
                    }
                    IconButton(onClick = { onExportRecording(rec.filePath) }) {
                        Icon(Icons.Default.SaveAlt, contentDescription = "Save to Folder", tint = LedGreen, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = {
                        val file = File(rec.filePath)
                        if (file.exists()) {
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "audio/wav"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(share, "Share BIJOY DJ Mix"))
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share Recording", tint = DeckACyan, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { scope.launch { viewModel.repository.deleteRecording(rec.id, rec.filePath) } }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Recording", tint = LedRed, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

// ==================== SETTINGS & BACKUP/RESTORE ====================

@Composable
fun SettingsScreen(
    viewModel: BijoyViewModel,
    onExportBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    onBack: () -> Unit
) {
    val settings by viewModel.userSettings.collectAsStateWithLifecycle()
    val allSongs by viewModel.allSongs.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ChassisVoid)
            .navigationBarsPadding()
    ) {
        SubScreenHeader("BIJOY DJ Settings", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsGroup("GENERAL SETTINGS") {
                SettingToggle("Keep Screen Awake", settings.keepScreenAwake) { v -> viewModel.updateSettings { it.copy(keepScreenAwake = v) } }
                SettingToggle("Prefer Landscape Mode", settings.preferLandscape) { v -> viewModel.updateSettings { it.copy(preferLandscape = v) } }
                SettingToggle("Hardware LED Animations", settings.animationsEnabled) { v -> viewModel.updateSettings { it.copy(animationsEnabled = v) } }
            }

            SettingsGroup("MIXER SETTINGS") {
                Text("Jog Wheel Sensitivity: ${String.format("%.1fx", settings.jogSensitivity)}", color = TextPrimary, fontSize = 12.sp)
                Slider(
                    value = settings.jogSensitivity,
                    onValueChange = { s -> viewModel.updateSettings { it.copy(jogSensitivity = s) } },
                    valueRange = 0.5f..2.0f
                )
                Text("Crossfader Curve", color = TextPrimary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CrossfaderCurve.entries.forEach { curve ->
                        FilterChip(
                            selected = settings.crossfaderCurve == curve.name,
                            onClick = { viewModel.updateSettings { it.copy(crossfaderCurve = curve.name) } },
                            label = { Text(curve.displayName, fontSize = 10.sp) }
                        )
                    }
                }
                SettingToggle("Auto-Sync on Deck Load", settings.autoSync) { v -> viewModel.updateSettings { it.copy(autoSync = v) } }
                Text("Auto-Fade Duration: ${String.format("%.1fs", settings.autoFadeDurationSec)}", color = TextPrimary, fontSize = 12.sp)
                Slider(
                    value = settings.autoFadeDurationSec,
                    onValueChange = { d -> viewModel.updateSettings { it.copy(autoFadeDurationSec = d) } },
                    valueRange = 1f..12f
                )
            }

            SettingsGroup("AUDIO & LIBRARY SETTINGS") {
                Text("Engine: Low-Latency AudioTrack • 44.1 kHz Stereo • 512-frame buffer", color = DeckACyan, fontSize = 11.sp, fontFamily = JetBrainsMonoFontFamily)
                SettingToggle("Headphone Cue Monitoring (where supported)", settings.headphoneMonitoring) { v ->
                    viewModel.updateSettings { it.copy(headphoneMonitoring = v) }
                }
                SettingToggle("Copy Imported Songs to Private App Storage", settings.copyImportedToPrivateStorage) { v ->
                    viewModel.updateSettings { it.copy(copyImportedToPrivateStorage = v) }
                }
                SettingToggle("Automatic Metadata & BPM Scanning", settings.autoScanMetadata) { v ->
                    viewModel.updateSettings { it.copy(autoScanMetadata = v) }
                }
            }

            SettingsGroup("DATA, BACKUP & RESTORE (ROOM SQLITE)") {
                Text(
                    "Database: bijoy_dj_database.db (15 Tables, Schema v2) • ${allSongs.size} Tracks indexed",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = JetBrainsMonoFontFamily
                )
                Text(
                    "Note: Metadata backups include library metadata, playlists, favorites, settings, custom layouts, and DJ presets. Original local music files are not embedded automatically.",
                    color = TextMuted,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onExportBackup,
                        colors = ButtonDefaults.buttonColors(containerColor = DeckACyan, contentColor = ChassisVoid),
                        modifier = Modifier.testTag("export_backup_btn")
                    ) { Text("Backup JSON", fontWeight = FontWeight.Bold, fontSize = 11.sp) }

                    OutlinedButton(
                        onClick = onRestoreBackup,
                        modifier = Modifier.testTag("restore_backup_btn")
                    ) { Text("Restore Backup", color = DeckACyan, fontSize = 11.sp) }

                    OutlinedButton(onClick = { scope.launch { viewModel.repository.clearHistory(); viewModel.postToast("Playback history cleared") } }) {
                        Text("Clear History", color = LedRed, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(ChassisCard)
            .border(1.dp, MetalBorder, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(title, color = DeckACyan, fontSize = 11.sp, fontFamily = OrbitronFontFamily, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun SettingToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextPrimary, fontSize = 12.sp)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// ==================== ABOUT BIJOY DJ ====================

@Composable
fun AboutBijoyScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ChassisVoid)
            .navigationBarsPadding()
    ) {
        SubScreenHeader("About BIJOY DJ", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("BIJOY DJ", color = TextPrimary, fontFamily = OrbitronFontFamily, fontWeight = FontWeight.Bold, fontSize = 26.sp)
            Text("FEEL THE BEAT", color = DeckBRed, fontFamily = OrbitronFontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 3.sp)
            Text("Version 1.0 • Professional Android DJ Mixing Studio", color = TextSecondary, fontSize = 12.sp)
            HorizontalDivider(color = MetalBorder)
            Text(
                text = "Engineered with a real-time low-latency dual-deck PCM mixing engine, Pioneer-inspired metallic jog wheels, 3-band isolator EQ, resonant LPF/HPF filters, 11 DJ effects, 12-pad polyphonic SFX sampler, automatic BPM detection & beat sync, real decoded multi-band waveforms, WAV master recording studio, 15-table Room SQLite persistence, and full layout customization.",
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
        }
    }
}
