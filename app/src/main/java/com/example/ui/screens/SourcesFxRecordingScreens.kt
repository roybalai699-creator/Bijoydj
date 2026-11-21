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
                    Icon(BijoyIcons.LibraryMusic, contentDescription = null, tint = DeckACyan, modifier = Modifier.size(28.dp))
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
                    Icon(BijoyIcons.GraphicEq, contentDescription = "Spotify", tint = SpotifyGreen, modifier = Modifier.size(28.dp))
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
                    Icon(BijoyIcons.CloudQueue, contentDescription = "SoundCloud", tint = SoundCloudOrange, modifier = Modifier.size(28.dp))
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
                    Icon(BijoyIcons.VpnKey, contentDescription = "API Key Manager", tint = LedPurple, modifier = Modifier.size(26.dp))
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
    val savedPresets by viewModel.allPresets.collectAsStateWithLifecycle()
    var target by remember { mutableStateOf(EffectTarget.DECK_A) }
    var ch3Fx by remember { mutableStateOf(DjEffectType.NONE) }
    var ch3Wet by remember { mutableFloatStateOf(0.45f) }
    var ch3Param by remember { mutableFloatStateOf(0.5f) }
    var ch4Fx by remember { mutableStateOf(DjEffectType.NONE) }
    var ch4Wet by remember { mutableFloatStateOf(0.45f) }
    var ch4Param by remember { mutableFloatStateOf(0.5f) }
    var showSavePresetDialog by remember { mutableStateOf(false) }

    val activeDeck = if (target == EffectTarget.DECK_B) deckB else deckA
    val engine = viewModel.audioEngine
    val tenBandLabels = listOf("31Hz", "62Hz", "125Hz", "250Hz", "500Hz", "1k", "2k", "4k", "8k", "16k")
    val fifteenBandLabels = listOf("25Hz", "40Hz", "63Hz", "100", "160", "250", "400", "630", "1k", "1.6k", "2.5k", "4k", "6.3k", "10k", "16k")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ChassisVoid)
            .navigationBarsPadding()
    ) {
        SubScreenHeader(
            title = if (initialMode == AppSection.EQUALIZER) "Multi-Band Equalizer Studio" else "24 DJ Effects & EQ",
            onBack = onBack,
            actions = {
                if (target == EffectTarget.DECK_A || target == EffectTarget.DECK_B) {
                    TextButton(onClick = { showSavePresetDialog = true }) {
                        Text("Save Preset", color = DeckACyan, fontSize = 11.sp)
                    }
                }
                TextButton(onClick = {
                    when (target) {
                        EffectTarget.DECK_A -> engine.applyEqPreset(DeckId.A, EqPreset.FLAT)
                        EffectTarget.DECK_B -> engine.applyEqPreset(DeckId.B, EqPreset.FLAT)
                        EffectTarget.CHANNEL_3 -> engine.updateChannelEqByIndex(3, 0f, 0f, 0f, 0f, 0f, 0f)
                        EffectTarget.CHANNEL_4 -> engine.updateChannelEqByIndex(4, 0f, 0f, 0f, 0f, 0f, 0f)
                        EffectTarget.MASTER -> engine.setMasterEffect(DjEffectType.NONE)
                    }
                }) {
                    Text("Reset", color = DeckBOrange)
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
            // 5 Target Channels: Deck A (CH1), Deck B (CH2), CH3 Sampler, CH4 Aux, Master
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(EffectTarget.entries) { t ->
                    FilterChip(
                        selected = target == t,
                        onClick = { target = t },
                        label = { Text(t.displayName, fontFamily = JetBrainsMonoFontFamily, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DeckACyan.copy(alpha = 0.25f),
                            selectedLabelColor = DeckACyan
                        )
                    )
                }
            }

            if (target == EffectTarget.DECK_A || target == EffectTarget.DECK_B) {
                val deckId = if (target == EffectTarget.DECK_B) DeckId.B else DeckId.A
                Text("EQ BAND MODE & PRESETS (${target.displayName})", color = TextSecondary, fontSize = 10.sp, fontFamily = JetBrainsMonoFontFamily)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(EqBandMode.entries) { bm ->
                        FilterChip(
                            selected = activeDeck.eqBandMode == bm,
                            onClick = { engine.setEqBandMode(deckId, bm) },
                            label = { Text(bm.displayName, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = LedPurple.copy(alpha = 0.28f),
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(EqPreset.entries) { preset ->
                        FilterChip(
                            selected = activeDeck.eqPreset == preset,
                            onClick = { engine.applyEqPreset(deckId, preset) },
                            label = { Text(preset.displayName, fontSize = 11.sp) }
                        )
                    }
                }

                val customUserPresets = savedPresets.filter { !it.isBuiltIn }
                if (customUserPresets.isNotEmpty()) {
                    Text("SAVED USER PRESETS", color = CrownGold, fontSize = 10.sp, fontFamily = JetBrainsMonoFontFamily)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(customUserPresets, key = { it.id }) { up ->
                            FilterChip(
                                selected = false,
                                onClick = { viewModel.applyCustomDjPreset(deckId, up) },
                                label = { Text("${up.name} (×)", fontSize = 11.sp) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Delete preset",
                                        modifier = Modifier.size(14.dp).clickable { viewModel.deleteCustomDjPreset(up) }
                                    )
                                }
                            )
                        }
                    }
                }

                when (activeDeck.eqBandMode) {
                    EqBandMode.BAND_15 -> {
                        Text("15-BAND GRAPHIC EQUALIZER (-12dB to +12dB)", color = DeckACyan, fontSize = 10.sp, fontFamily = JetBrainsMonoFontFamily, fontWeight = FontWeight.Bold)
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(ChassisCard)
                                .border(1.dp, MetalBorder, RoundedCornerShape(10.dp))
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(15) { idx ->
                                val bandDb = activeDeck.fifteenBandEqDb.getOrElse(idx) { 0f }
                                Column(
                                    modifier = Modifier.width(48.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = String.format("%+.0f", bandDb),
                                        color = if (bandDb > 0.5f) LedGreen else if (bandDb < -0.5f) DeckBOrange else TextSecondary,
                                        fontSize = 9.sp,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Slider(
                                        value = bandDb,
                                        onValueChange = { engine.updateFifteenBandEq(deckId, idx, it) },
                                        valueRange = -12f..12f,
                                        modifier = Modifier.fillMaxWidth().testTag("eq_15band_$idx"),
                                        colors = SliderDefaults.colors(
                                            thumbColor = if (idx < 5) DeckBRed else if (idx < 10) DeckBGold else DeckACyan,
                                            activeTrackColor = DeckACyan
                                        )
                                    )
                                    Text(fifteenBandLabels[idx], color = TextMuted, fontSize = 8.sp, fontFamily = JetBrainsMonoFontFamily)
                                }
                            }
                        }
                    }
                    EqBandMode.BAND_10, EqBandMode.BAND_5 -> {
                        val indices = if (activeDeck.eqBandMode == EqBandMode.BAND_5) listOf(1, 3, 5, 7, 9) else (0..9).toList()
                        Text("${activeDeck.eqBandMode.bandCount}-BAND GRAPHIC EQUALIZER (-12dB to +12dB)", color = DeckACyan, fontSize = 10.sp, fontFamily = JetBrainsMonoFontFamily, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(ChassisCard)
                                .border(1.dp, MetalBorder, RoundedCornerShape(10.dp))
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            indices.forEach { idx ->
                                val bandDb = activeDeck.tenBandEqDb.getOrElse(idx) { 0f }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = String.format("%+.0f", bandDb),
                                        color = if (bandDb > 0.5f) LedGreen else if (bandDb < -0.5f) DeckBOrange else TextSecondary,
                                        fontSize = 9.sp,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Slider(
                                        value = bandDb,
                                        onValueChange = { engine.updateTenBandEq(deckId, idx, it) },
                                        valueRange = -12f..12f,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("eq_10band_$idx"),
                                        colors = SliderDefaults.colors(
                                            thumbColor = if (idx < 3) DeckBRed else if (idx < 7) DeckBGold else DeckACyan,
                                            activeTrackColor = DeckACyan
                                        )
                                    )
                                    Text(tenBandLabels[idx], color = TextMuted, fontSize = 8.sp, fontFamily = JetBrainsMonoFontFamily)
                                }
                            }
                        }
                    }
                    EqBandMode.BAND_3 -> {
                        Text("3-BAND ISOLATOR MODE ACTIVE", color = DeckACyan, fontSize = 10.sp, fontFamily = JetBrainsMonoFontFamily, fontWeight = FontWeight.Bold)
                    }
                }

                // 3-Band Isolator EQ + Dedicated Vocal Level + Filter Sliders
                EqSliderRow("Treble (High)", activeDeck.trebleDb, -12f..12f, "${String.format("%+.1f", activeDeck.trebleDb)} dB", DeckACyan) {
                    engine.updateDeckEq(deckId, trebleDb = it)
                }
                EqSliderRow("Mid", activeDeck.midDb, -12f..12f, "${String.format("%+.1f", activeDeck.midDb)} dB", DeckBGold) {
                    engine.updateDeckEq(deckId, midDb = it)
                }
                EqSliderRow("Bass (Low)", activeDeck.bassDb, -12f..12f, "${String.format("%+.1f", activeDeck.bassDb)} dB", DeckBRed) {
                    engine.updateDeckEq(deckId, bassDb = it)
                }
                EqSliderRow("Vocal Presence / Remover", activeDeck.vocalDb, -12f..12f, "${String.format("%+.1f", activeDeck.vocalDb)} dB", LedPurple) {
                    engine.updateDeckEq(deckId, vocalDb = it)
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
            } else if (target == EffectTarget.CHANNEL_3 || target == EffectTarget.CHANNEL_4) {
                val chIdx = if (target == EffectTarget.CHANNEL_3) 3 else 4
                val ch = mixer.channels.find { it.channelIndex == chIdx }
                if (ch != null) {
                    EqSliderRow("${ch.label} Treble", ch.trebleDb, -12f..12f, "${String.format("%+.1f", ch.trebleDb)} dB", DeckACyan) {
                        engine.updateChannelEqByIndex(chIdx, trebleDb = it)
                    }
                    EqSliderRow("${ch.label} Mid", ch.midDb, -12f..12f, "${String.format("%+.1f", ch.midDb)} dB", DeckBGold) {
                        engine.updateChannelEqByIndex(chIdx, midDb = it)
                    }
                    EqSliderRow("${ch.label} Bass", ch.bassDb, -12f..12f, "${String.format("%+.1f", ch.bassDb)} dB", DeckBRed) {
                        engine.updateChannelEqByIndex(chIdx, bassDb = it)
                    }
                    EqSliderRow("${ch.label} Vocal", ch.vocalDb, -12f..12f, "${String.format("%+.1f", ch.vocalDb)} dB", LedPurple) {
                        engine.updateChannelEqByIndex(chIdx, vocalDb = it)
                    }
                    EqSliderRow("${ch.label} Filter", ch.filterCutoff, -1f..1f, String.format("%+.2f", ch.filterCutoff), LedPurple) {
                        engine.updateChannelEqByIndex(chIdx, filterCutoff = it)
                    }
                }
            }

            HorizontalDivider(color = MetalBorder)

            // All 16 Professional DJ Effects
            val currentFx = when (target) {
                EffectTarget.DECK_A -> deckA.activeEffect
                EffectTarget.DECK_B -> deckB.activeEffect
                EffectTarget.CHANNEL_3 -> ch3Fx
                EffectTarget.CHANNEL_4 -> ch4Fx
                EffectTarget.MASTER -> mixer.masterEffect
            }
            val currentWet = when (target) {
                EffectTarget.DECK_A -> deckA.effectWet
                EffectTarget.DECK_B -> deckB.effectWet
                EffectTarget.CHANNEL_3 -> ch3Wet
                EffectTarget.CHANNEL_4 -> ch4Wet
                EffectTarget.MASTER -> mixer.masterEffectWet
            }
            val currentParam = when (target) {
                EffectTarget.DECK_A -> deckA.effectParam
                EffectTarget.DECK_B -> deckB.effectParam
                EffectTarget.CHANNEL_3 -> ch3Param
                EffectTarget.CHANNEL_4 -> ch4Param
                EffectTarget.MASTER -> mixer.masterEffectParam
            }

            Text("${DjEffectType.entries.size} DJ EFFECTS (${target.displayName})", color = TextSecondary, fontSize = 10.sp, fontFamily = JetBrainsMonoFontFamily)
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
                                    EffectTarget.CHANNEL_3 -> { ch3Fx = fx; engine.setChannelEffect(3, fx, ch3Wet, ch3Param) }
                                    EffectTarget.CHANNEL_4 -> { ch4Fx = fx; engine.setChannelEffect(4, fx, ch4Wet, ch4Param) }
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
                    EffectTarget.CHANNEL_3 -> { ch3Wet = w; engine.setChannelEffect(3, ch3Fx, w, ch3Param) }
                    EffectTarget.CHANNEL_4 -> { ch4Wet = w; engine.setChannelEffect(4, ch4Fx, w, ch4Param) }
                    EffectTarget.MASTER -> engine.setMasterEffect(currentFx, wet = w)
                }
            }
            EqSliderRow("Effect Depth / Rate", currentParam, 0f..1f, "${(currentParam * 100).toInt()}%", DeckACyan) { p ->
                when (target) {
                    EffectTarget.DECK_A -> engine.setDeckEffect(DeckId.A, currentFx, param = p)
                    EffectTarget.DECK_B -> engine.setDeckEffect(DeckId.B, currentFx, param = p)
                    EffectTarget.CHANNEL_3 -> { ch3Param = p; engine.setChannelEffect(3, ch3Fx, ch3Wet, p) }
                    EffectTarget.CHANNEL_4 -> { ch4Param = p; engine.setChannelEffect(4, ch4Fx, ch4Wet, p) }
                    EffectTarget.MASTER -> engine.setMasterEffect(currentFx, param = p)
                }
            }
        }
    }

    if (showSavePresetDialog) {
        var presetName by remember { mutableStateOf("") }
        val deckId = if (target == EffectTarget.DECK_B) DeckId.B else DeckId.A
        AlertDialog(
            onDismissRequest = { showSavePresetDialog = false },
            containerColor = ChassisDark,
            title = { Text("Save Custom EQ Preset", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = presetName,
                    onValueChange = { presetName = it },
                    placeholder = { Text("e.g., Bangla Bass Peak") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (presetName.isNotBlank()) {
                        viewModel.saveCurrentEqPreset(presetName, deckId)
                        showSavePresetDialog = false
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showSavePresetDialog = false }) { Text("Cancel") } }
        )
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
    val mixer by viewModel.mixer.collectAsStateWithLifecycle()
    val sampleVol by viewModel.sampleMasterVolume.collectAsStateWithLifecycle()
    var editingPad by remember { mutableStateOf<SfxPadUi?>(null) }
    var selectedCategory by remember { mutableStateOf("All") }

    val visiblePads = remember(pads, selectedCategory) {
        if (selectedCategory == "All") pads else pads.filter { it.category.equals(selectedCategory, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ChassisVoid)
            .navigationBarsPadding()
    ) {
        SubScreenHeader(
            title = "16-Pad SFX & Sampler Studio",
            onBack = onBack,
            actions = {
                TextButton(onClick = { viewModel.stopAllSfx() }) {
                    Text("STOP ALL", color = LedRed, fontFamily = JetBrainsMonoFontFamily)
                }
            }
        )

        // Sampler Banks A/B/C/D + Sampler Roll Controls + Master Sample Volume + Category Filter
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(ChassisCard)
                .border(1.dp, LedPurple.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("BANK:", color = TextSecondary, fontSize = 10.sp, fontFamily = JetBrainsMonoFontFamily)
                    listOf(0 to "Bank A", 1 to "Bank B", 2 to "Bank C", 3 to "Bank D").forEach { (idx, name) ->
                        FilterChip(
                            selected = mixer.samplerBankIndex == idx,
                            onClick = { viewModel.switchSamplerBank(idx) },
                            label = { Text(name, fontSize = 10.sp) }
                        )
                    }
                }
                Button(
                    onClick = { viewModel.audioEngine.toggleSamplerRoll(0, mixer.samplerRollBeats) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (mixer.samplerRollActive) DeckACyan else LedPurple
                    ),
                    modifier = Modifier.testTag("studio_sampler_roll_btn")
                ) {
                    Text(if (mixer.samplerRollActive) "ROLL ON" else "SAMPLER ROLL", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(SfxSamplerEngine.PAD_CATEGORIES) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat, fontSize = 10.sp) }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("ROLL BEAT:", color = TextSecondary, fontSize = 10.sp, fontFamily = JetBrainsMonoFontFamily)
                listOf("1/2" to 0.5f, "1" to 1f, "2" to 2f, "4" to 4f, "8" to 8f, "16" to 16f).forEach { (label, beats) ->
                    FilterChip(
                        selected = kotlin.math.abs(mixer.samplerRollBeats - beats) < 0.01f,
                        onClick = { viewModel.audioEngine.setSamplerRollBeats(beats) },
                        label = { Text(label, fontSize = 10.sp, fontFamily = JetBrainsMonoFontFamily) }
                    )
                }
            }

            EqSliderRow(
                label = "Sampler Bus Master Volume (CH 3)",
                value = sampleVol,
                range = 0f..1.5f,
                readout = "${(sampleVol * 100).toInt()}%",
                color = LedGreen
            ) { viewModel.setSampleMasterVolume(it) }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(visiblePads, key = { it.index }) { pad ->
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
                        Text("${pad.index + 1}. ${pad.label}", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            text = "${pad.category} • Vol ${(pad.volume * 100).toInt()}% • Pitch ${String.format("%.2fx", pad.pitchRatio)}" +
                                (if (pad.isLooping) " • LOOPING" else ""),
                            color = if (pad.isLooping) DeckACyan else TextSecondary,
                            fontSize = 10.sp
                        )
                    }
                    IconButton(onClick = { viewModel.toggleSfxPadLoop(pad.index) }) {
                        Icon(
                            BijoyIcons.Repeat,
                            contentDescription = "Loop Sample",
                            tint = if (pad.isLooping) DeckACyan else TextMuted
                        )
                    }
                    IconButton(onClick = { viewModel.audioEngine.toggleSamplerRoll(pad.index, mixer.samplerRollBeats) }) {
                        Icon(BijoyIcons.FastForward, contentDescription = "Roll Sample", tint = LedPurple)
                    }
                    IconButton(onClick = { onPickSampleForPad(pad.index) }) {
                        Icon(BijoyIcons.AudioFile, contentDescription = "Assign Sample", tint = DeckACyan)
                    }
                    if (pad.customAudioUri != null) {
                        IconButton(onClick = { viewModel.removeCustomSampleFromPad(pad.index) }) {
                            Icon(BijoyIcons.DeleteSweep, contentDescription = "Remove Custom Sample", tint = LedRed)
                        }
                    }
                    IconButton(onClick = { editingPad = pad }) {
                        Icon(BijoyIcons.Tune, contentDescription = "Edit Pad", tint = TextSecondary)
                    }
                }
            }
        }
    }

    editingPad?.let { pad ->
        var label by remember(pad) { mutableStateOf(pad.label) }
        var vol by remember(pad) { mutableFloatStateOf(pad.volume) }
        var pitch by remember(pad) { mutableFloatStateOf(pad.pitchRatio) }
        AlertDialog(
            onDismissRequest = { editingPad = null },
            containerColor = ChassisDark,
            title = { Text("Configure Pad ${pad.index + 1}", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Pad Name") }, singleLine = true)
                    Text("Volume: ${(vol * 100).toInt()}%", color = TextSecondary, fontSize = 11.sp)
                    Slider(value = vol, onValueChange = { vol = it }, valueRange = 0.1f..1.4f)
                    Text("Pitch Tuning: ${String.format("%.2fx", pitch)}", color = DeckACyan, fontSize = 11.sp)
                    Slider(value = pitch, onValueChange = { pitch = it }, valueRange = 0.5f..2.0f)
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.updateSfxPadConfig(pad.index, label, vol, pitch)
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
    var renameTarget by remember { mutableStateOf<com.example.database.RecordingEntity?>(null) }

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
                        text = if (recState.isRecording) (if (recState.isPaused) "PAUSED" else "RECORDING ${recState.source.displayName.uppercase()}") else "READY TO RECORD",
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

            if (!recState.isRecording) {
                Text("RECORDING SOURCE", color = TextSecondary, fontSize = 10.sp, fontFamily = JetBrainsMonoFontFamily)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(RecordSource.entries) { src ->
                        FilterChip(
                            selected = recState.source == src,
                            onClick = {
                                viewModel.setRecordSource(src)
                                if (src == RecordSource.LIVE_MIC) {
                                    includeMic = true
                                    onRequestMicPermission()
                                }
                            },
                            label = { Text(src.displayName, fontSize = 10.sp) }
                        )
                    }
                }
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
                        onClick = { viewModel.startRecording(customTitle, includeMic, recState.source) },
                        colors = ButtonDefaults.buttonColors(containerColor = LedRed),
                        modifier = Modifier.testTag("start_recording_btn")
                    ) {
                        Icon(BijoyIcons.FiberManualRecord, contentDescription = null)
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
                        Icon(BijoyIcons.Stop, contentDescription = null)
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
                    Icon(BijoyIcons.Mic, contentDescription = null, tint = LedRed)
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
                    if (rec.format.equals("WAV", ignoreCase = true)) {
                        TextButton(onClick = { viewModel.exportRecordingAsM4a(rec) }) {
                            Text("M4A", color = CrownGold, fontSize = 10.sp)
                        }
                    }
                    IconButton(onClick = { renameTarget = rec }) {
                        Icon(Icons.Default.Edit, contentDescription = "Rename Recording", tint = TextSecondary, modifier = Modifier.size(17.dp))
                    }
                    IconButton(onClick = { onExportRecording(rec.filePath) }) {
                        Icon(BijoyIcons.SaveAlt, contentDescription = "Save to Folder", tint = LedGreen, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = {
                        val file = File(rec.filePath)
                        if (file.exists()) {
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = if (rec.format == "M4A") "audio/mp4" else "audio/wav"
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

    renameTarget?.let { rec ->
        var newName by remember(rec) { mutableStateOf(rec.title) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = ChassisDark,
            title = { Text("Rename Recording", color = TextPrimary) },
            text = {
                OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Recording Title") }, singleLine = true)
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.renameRecording(rec, newName)
                    renameTarget = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } }
        )
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
    val deckA by viewModel.deckA.collectAsStateWithLifecycle()
    val allSongs by viewModel.allSongs.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ChassisVoid)
            .navigationBarsPadding()
    ) {
        SubScreenHeader(
            title = "BIJOY DJ Settings",
            onBack = onBack,
            actions = {
                TextButton(onClick = { viewModel.openLayoutCustomizer(true) }) {
                    Text("Layout Studio", color = DeckACyan, fontSize = 11.sp)
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
            SettingsGroup("GENERAL & INTERFACE SETTINGS") {
                SettingToggle("Keep Screen Awake", settings.keepScreenAwake) { v -> viewModel.updateSettings { it.copy(keepScreenAwake = v) } }
                SettingToggle("Prefer Landscape Mode", settings.preferLandscape) { v -> viewModel.updateSettings { it.copy(preferLandscape = v) } }
                SettingToggle("Hardware LED Animations", settings.animationsEnabled) { v -> viewModel.updateSettings { it.copy(animationsEnabled = v) } }
                Text("Interface Language", color = TextPrimary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("English", "বাংলা", "Hindi").forEach { lang ->
                        FilterChip(
                            selected = settings.language == lang,
                            onClick = { viewModel.updateSettings { it.copy(language = lang) } },
                            label = { Text(lang, fontSize = 11.sp) }
                        )
                    }
                }
            }

            SettingsGroup("MIXER & PITCH SETTINGS") {
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
                Text("Default Pitch Slider Range", color = TextPrimary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(6f, 10f, 16f, 50f, 100f).forEach { range ->
                        FilterChip(
                            selected = kotlin.math.abs(deckA.pitchRangePercent - range) < 0.5f,
                            onClick = {
                                viewModel.audioEngine.setPitchRange(DeckId.A, range)
                                viewModel.audioEngine.setPitchRange(DeckId.B, range)
                            },
                            label = { Text("±${range.toInt()}%", fontSize = 10.sp) }
                        )
                    }
                }
                SettingToggle("Auto-Sync on Deck Load", settings.autoSync) { v -> viewModel.updateSettings { it.copy(autoSync = v) } }
                Text("Auto-Fade Duration: ${String.format("%.0fs", settings.autoFadeDurationSec)}", color = TextPrimary, fontSize = 12.sp)
                Slider(
                    value = settings.autoFadeDurationSec,
                    onValueChange = { d ->
                        viewModel.updateSettings { it.copy(autoFadeDurationSec = d) }
                        viewModel.audioEngine.setFadeDuration(d)
                    },
                    valueRange = 1f..15f
                )
            }

            SettingsGroup("AUDIO ENGINE, OUTPUT & LATENCY") {
                Text(
                    "Engine: 4-Channel AudioTrack • ${settings.sampleRateHz} Hz Stereo • ${settings.bufferSizeFrames}-frame buffer",
                    color = DeckACyan,
                    fontSize = 11.sp,
                    fontFamily = JetBrainsMonoFontFamily
                )
                Text("Audio Output Device", color = TextPrimary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("System Default", "Speaker / Headphones", "USB Audio Interface").forEach { dev ->
                        FilterChip(
                            selected = settings.outputDeviceName == dev,
                            onClick = { viewModel.updateSettings { it.copy(outputDeviceName = dev) } },
                            label = { Text(dev, fontSize = 10.sp) }
                        )
                    }
                }
                Text("Buffer Size (Latency vs Stability)", color = TextPrimary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(256, 512, 1024).forEach { frames ->
                        FilterChip(
                            selected = settings.bufferSizeFrames == frames,
                            onClick = { viewModel.updateSettings { it.copy(bufferSizeFrames = frames) } },
                            label = { Text("$frames frames", fontSize = 10.sp) }
                        )
                    }
                }
                Text("Latency Mode", color = TextPrimary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("LOW_LATENCY", "BALANCED", "SAFE_BUFFER").forEach { mode ->
                        FilterChip(
                            selected = settings.audioLatencyMode == mode,
                            onClick = { viewModel.updateSettings { it.copy(audioLatencyMode = mode) } },
                            label = { Text(mode.replace("_", " "), fontSize = 10.sp) }
                        )
                    }
                }
                Text("Recording Quality", color = TextPrimary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("16-bit 44.1kHz Stereo", "16-bit 48kHz Stereo").forEach { q ->
                        FilterChip(
                            selected = settings.recordingQuality == q,
                            onClick = { viewModel.updateSettings { it.copy(recordingQuality = q) } },
                            label = { Text(q, fontSize = 10.sp) }
                        )
                    }
                }
                SettingToggle("Headphone Cue Monitoring (Pre-Fader Split)", settings.headphoneMonitoring) { v ->
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
                    "Metadata backups include library metadata, playlists, favorites, hot cues, saved loops, settings, custom layouts, and DJ presets.",
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
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { viewModel.restoreDefaultSettings() },
                    modifier = Modifier.fillMaxWidth().testTag("restore_defaults_btn")
                ) {
                    Icon(BijoyIcons.RestartAlt, contentDescription = null, tint = DeckBOrange)
                    Spacer(Modifier.width(6.dp))
                    Text("Restore Default BIJOY DJ Settings", color = DeckBOrange, fontWeight = FontWeight.Bold)
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
            BijoyGoldCrownIcon(modifier = Modifier.size(width = 64.dp, height = 50.dp))
            Text("BIJOY DJ", color = CrownGold, fontFamily = OrbitronFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp)
            Text("FEEL THE BEAT", color = TextPrimary, fontFamily = OrbitronFontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 3.sp)
            Text("Version 2.0 • AI-Powered Studio 4-Channel Edition", color = DeckACyan, fontSize = 12.sp, fontFamily = JetBrainsMonoFontFamily)
            HorizontalDivider(color = MetalBorder)
            Text(
                text = "Developer: BIJOY DJ Audio Engineering Studio\nArchitecture: Kotlin + Jetpack Compose + MVVM + Room SQLite (15 Tables) + Low-Latency 4-Channel AudioTrack PCM DSP + Firebase AI Logic (Gemini 3.8 Flash)",
                color = TextPrimary,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
            Text(
                text = "Features: Dual-deck vinyl/CDJ/search jog wheels, 4-channel central hardware mixer (Gain, Treble, Mid, Bass, Vocal, Filter, Cue, ON/OFF, Channel Faders, Booth Vol, Limiter, Crossfader), 3/5/10/15-Band Graphic EQ with 11 presets + custom saves, 24 DJ audio effects, 8 Hot Cues, Auto-Loop & Cue Loop, 16-Pad Banked Sampler with Pitch & Roll, AI Song Remix Studio (15 styles), On-Device 4-Stem Separator & Vocal Remover, AI Auto DJ, Sad-to-DJ Converter, 16-step AI Beat Pattern Generator, BIJOY AI Assistant, WAV & M4A Recording Studio, Official Spotify & SoundCloud OAuth + Encrypted API Manager.",
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
            HorizontalDivider(color = MetalBorder)
            Text(
                text = "Privacy & Permissions: BIJOY DJ uses zero-permission Android Storage Access Framework (SAF) for music & sample import and requests Microphone access only when live voiceover mixing is enabled. OAuth client credentials and API keys are encrypted locally using Android Keystore AES-256-GCM.",
                color = TextMuted,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
            Text(
                text = "Licenses: Built with AndroidX Jetpack Compose, Material 3, Room, Kotlin Coroutines, OkHttp, and Ktor/Serialization open-source components.",
                color = TextMuted,
                fontSize = 10.sp
            )
        }
    }
}
