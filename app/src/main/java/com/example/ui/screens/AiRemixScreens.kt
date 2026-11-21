package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audio.*
import com.example.database.SongEntity
import com.example.ui.theme.*
import com.example.viewmodel.BijoyViewModel

// ==================== AI SONG REMIX STUDIO (15 STYLES, 10-STEP WORKFLOW) ====================

@Composable
fun AiSongRemixStudioScreen(
    viewModel: BijoyViewModel,
    onExportWav: (wavPath: String, suggestedName: String) -> Unit,
    onBack: () -> Unit
) {
    val allSongs by viewModel.allSongs.collectAsStateWithLifecycle()
    val selectedSong by viewModel.aiStudio.selectedSourceSong.collectAsStateWithLifecycle()
    val analysis by viewModel.aiStudio.trackAnalysis.collectAsStateWithLifecycle()
    val selectedStyle by viewModel.aiStudio.selectedRemixStyle.collectAsStateWithLifecycle()
    val aiPlan by viewModel.aiStudio.aiRemixPlan.collectAsStateWithLifecycle()
    val rendered by viewModel.aiStudio.renderedRemix.collectAsStateWithLifecycle()
    val isPreviewOriginal by viewModel.aiStudio.isPreviewingOriginal.collectAsStateWithLifecycle()
    val taskState by viewModel.aiStudio.aiTaskState.collectAsStateWithLifecycle()

    LaunchedEffect(allSongs) {
        if (selectedSong == null && allSongs.isNotEmpty()) {
            viewModel.aiStudio.selectSourceSong(allSongs.first())
        }
    }

    val currentStep = when {
        rendered != null && isPreviewOriginal -> 8
        rendered != null -> 9
        taskState is AiTaskUiState.Working -> 6
        aiPlan != null -> 4
        analysis != null -> 3
        selectedSong != null -> 2
        else -> 1
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ChassisVoid)
            .navigationBarsPadding()
    ) {
        SubScreenHeader(
            title = "AI Song Remix Studio (15 Styles)",
            onBack = onBack,
            actions = {
                TextButton(onClick = { viewModel.navigateTo(AppSection.STEM_SEPARATOR) }) {
                    Text("4-Stems", color = DeckACyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = { viewModel.navigateTo(AppSection.SAD_TO_DJ) }) {
                    Text("Sad→DJ", color = LedPurple, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 10-Step Workflow Progress Stepper
            RemixTenStepBar(currentStep = currentStep)

            AiTaskStatusBanner(
                state = taskState,
                onCancel = { viewModel.aiStudio.cancelActiveAiJob() },
                onRetry = {
                    selectedSong?.let { viewModel.aiStudio.generateAiRemixPlan(it, selectedStyle) }
                }
            )

            // Step 1: Song Picker
            SongSelectorStrip(
                songs = allSongs,
                selectedSong = selectedSong,
                onSelect = { viewModel.aiStudio.selectSourceSong(it) }
            )

            // Step 2: Analyze & Gemini Plan Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { selectedSong?.let { viewModel.aiStudio.analyzeSong(it) } },
                    enabled = selectedSong != null && taskState !is AiTaskUiState.Working,
                    colors = ButtonDefaults.buttonColors(containerColor = ChassisSurface),
                    modifier = Modifier.weight(1f).testTag("analyze_song_btn")
                ) {
                    Icon(BijoyIcons.Analytics, contentDescription = null, tint = DeckACyan, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("2. Analyze Audio", color = DeckACyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { selectedSong?.let { viewModel.aiStudio.generateAiRemixPlan(it, selectedStyle) } },
                    enabled = selectedSong != null && taskState !is AiTaskUiState.Working,
                    colors = ButtonDefaults.buttonColors(containerColor = LedPurple),
                    modifier = Modifier.weight(1f).testTag("gemini_remix_plan_btn")
                ) {
                    Icon(BijoyIcons.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("4. Gemini AI Plan", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Analysis Card
            analysis?.let { a ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(ChassisCard)
                        .border(1.dp, DeckACyan.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "TRACK ANALYSIS • ${a.detectedBpm.toInt()} BPM • KEY ${a.musicalKey} • ENERGY ${(a.energyScore * 100).toInt()}%",
                            color = DeckACyan,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text("${a.genre} | ${a.mood} | ${a.rhythmProfile}", color = TextPrimary, fontSize = 12.sp)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(a.sections) { sec ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(ChassisSurface)
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        "${sec.name} ${sec.startSec.toInt()}s-${sec.endSec.toInt()}s",
                                        color = TextSecondary,
                                        fontSize = 9.sp,
                                        fontFamily = JetBrainsMonoFontFamily
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Step 3: 15 Remix Style Presets
            Text("3. SELECT REMIX STYLE (15 STYLES)", color = DeckBGold, fontFamily = OrbitronFontFamily, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(RemixStyle.entries) { style ->
                    val active = selectedStyle == style
                    FilterChip(
                        selected = active,
                        onClick = { viewModel.aiStudio.selectRemixStyle(style) },
                        label = { Text("${style.displayName} (${style.targetBpm.toInt()})", fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DeckBGold.copy(alpha = 0.25f),
                            selectedLabelColor = DeckBGold
                        ),
                        modifier = Modifier.testTag("remix_style_${style.name}")
                    )
                }
            }

            // AI Plan Result Card
            aiPlan?.let { plan ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(ChassisCard)
                        .border(1.dp, LedPurple.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(BijoyIcons.AutoAwesome, contentDescription = null, tint = LedPurple, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "GEMINI 3.8 FLASH REMIX PLAN • ${plan.targetStyle}",
                                color = LedPurple,
                                fontFamily = OrbitronFontFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            "Target ${plan.recommendedBpm.toInt()} BPM • Pitch ${String.format("%+.1f", plan.recommendedPitchSemitones)} st • Bass ${(plan.bassBoostLevel * 100).toInt()}% • Reverb ${(plan.reverbAmount * 100).toInt()}% • Drop @ ${plan.dropTimestampSec.toInt()}s",
                            color = DeckACyan,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 10.sp
                        )
                        Text(plan.techniqueSummary, color = TextPrimary, fontSize = 12.sp)
                    }
                }
            }

            // Step 5/6: Render Separate Non-Destructive WAV Remix
            Button(
                onClick = { selectedSong?.let { viewModel.aiStudio.renderAiSongRemix(it, selectedStyle, saveToLibrary = true) } },
                enabled = selectedSong != null && taskState !is AiTaskUiState.Working,
                colors = ButtonDefaults.buttonColors(containerColor = DeckACyan, contentColor = ChassisVoid),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("render_remix_btn")
            ) {
                Icon(BijoyIcons.GraphicEq, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("6. RENDER SEPARATE WAV REMIX (SAVES TO AI REMIXES)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            // Step 8-10: A/B Preview, Load to Deck, Export WAV
            rendered?.let { res ->
                RenderedRemixResultCard(
                    result = res,
                    isPreviewOriginal = isPreviewOriginal,
                    onToggleAb = { viewModel.aiStudio.toggleAbPreview(selectedSong, res) },
                    onLoadDeckA = { viewModel.aiStudio.loadRemixToDeck(res, DeckId.A) },
                    onLoadDeckB = { viewModel.aiStudio.loadRemixToDeck(res, DeckId.B) },
                    onExportWav = { onExportWav(res.wavFilePath, "${res.title.replace(" ", "_")}.wav") }
                )
            }
        }
    }
}

// ==================== SAD SONG -> DJ CONVERTER (11 PRESETS + 10 CONTROLS) ====================

@Composable
fun SadToDjConverterScreen(
    viewModel: BijoyViewModel,
    onExportWav: (wavPath: String, suggestedName: String) -> Unit,
    onBack: () -> Unit
) {
    val allSongs by viewModel.allSongs.collectAsStateWithLifecycle()
    val selectedSong by viewModel.aiStudio.selectedSourceSong.collectAsStateWithLifecycle()
    val params by viewModel.aiStudio.sadToDjParams.collectAsStateWithLifecycle()
    val rendered by viewModel.aiStudio.renderedRemix.collectAsStateWithLifecycle()
    val isPreviewOriginal by viewModel.aiStudio.isPreviewingOriginal.collectAsStateWithLifecycle()
    val taskState by viewModel.aiStudio.aiTaskState.collectAsStateWithLifecycle()

    LaunchedEffect(allSongs) {
        if (selectedSong == null && allSongs.isNotEmpty()) {
            viewModel.aiStudio.selectSourceSong(allSongs.first())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ChassisVoid)
            .navigationBarsPadding()
    ) {
        SubScreenHeader("Sad Song → DJ Remix Converter", onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AiTaskStatusBanner(
                state = taskState,
                onCancel = { viewModel.aiStudio.cancelActiveAiJob() },
                onRetry = { selectedSong?.let { viewModel.aiStudio.convertSadSongToDj(it) } }
            )

            SongSelectorStrip(
                songs = allSongs,
                selectedSong = selectedSong,
                onSelect = { viewModel.aiStudio.selectSourceSong(it) }
            )

            Text("11 SAD → DJ PRESETS", color = LedPurple, fontFamily = OrbitronFontFamily, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(SadToDjStyle.entries) { style ->
                    val active = params.style == style
                    FilterChip(
                        selected = active,
                        onClick = { viewModel.aiStudio.applySadToDjPreset(style) },
                        label = { Text("${style.displayName} (${style.presetBpm.toInt()})", fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = LedPurple.copy(alpha = 0.28f),
                            selectedLabelColor = LedPurple
                        ),
                        modifier = Modifier.testTag("sad_style_${style.name}")
                    )
                }
            }

            // 10 Studio Sliders + Drop Position + Intro/Outro Switch
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(ChassisCard)
                    .border(1.dp, MetalBorder, RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("10-CHANNEL SAD → DJ STUDIO CONTROLS", color = DeckACyan, fontFamily = OrbitronFontFamily, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                    StudioParamSlider("1. Bass Strength", params.bassStrength, 0f..1f, "${(params.bassStrength * 100).toInt()}%") { v ->
                        viewModel.aiStudio.updateSadToDjParams { it.copy(bassStrength = v) }
                    }
                    StudioParamSlider("2. Reverb Depth", params.reverbAmount, 0f..1f, "${(params.reverbAmount * 100).toInt()}%") { v ->
                        viewModel.aiStudio.updateSadToDjParams { it.copy(reverbAmount = v) }
                    }
                    StudioParamSlider("3. Echo Delay", params.echoAmount, 0f..1f, "${(params.echoAmount * 100).toInt()}%") { v ->
                        viewModel.aiStudio.updateSadToDjParams { it.copy(echoAmount = v) }
                    }
                    StudioParamSlider("4. Tempo (BPM)", params.tempoBpm, 70f..175f, "${params.tempoBpm.toInt()} BPM") { v ->
                        viewModel.aiStudio.updateSadToDjParams { it.copy(tempoBpm = v) }
                    }
                    StudioParamSlider("5. Pitch Shift", params.pitchSemitones, -6f..6f, "${String.format("%+.1f", params.pitchSemitones)} st") { v ->
                        viewModel.aiStudio.updateSadToDjParams { it.copy(pitchSemitones = v) }
                    }
                    StudioParamSlider("6. Vocal Level", params.vocalLevel, 0f..1.4f, "${(params.vocalLevel * 100).toInt()}%") { v ->
                        viewModel.aiStudio.updateSadToDjParams { it.copy(vocalLevel = v) }
                    }
                    StudioParamSlider("7. Instrumental Level", params.instrumentalLevel, 0f..1.4f, "${(params.instrumentalLevel * 100).toInt()}%") { v ->
                        viewModel.aiStudio.updateSadToDjParams { it.copy(instrumentalLevel = v) }
                    }
                    StudioParamSlider("8. Beat Drop Intensity", params.beatIntensity, 0f..1f, "${(params.beatIntensity * 100).toInt()}%") { v ->
                        viewModel.aiStudio.updateSadToDjParams { it.copy(beatIntensity = v) }
                    }
                    StudioParamSlider("9. Stereo Width", params.stereoWidth, 0.5f..1.8f, "${(params.stereoWidth * 100).toInt()}%") { v ->
                        viewModel.aiStudio.updateSadToDjParams { it.copy(stereoWidth = v) }
                    }
                    StudioParamSlider("10. Master Volume", params.masterVolume, 0.3f..1.2f, "${(params.masterVolume * 100).toInt()}%") { v ->
                        viewModel.aiStudio.updateSadToDjParams { it.copy(masterVolume = v) }
                    }
                    StudioParamSlider("Beat Drop Trigger Point", params.dropPositionRatio, 0f..0.85f, "${(params.dropPositionRatio * 100).toInt()}%") { v ->
                        viewModel.aiStudio.updateSadToDjParams { it.copy(dropPositionRatio = v) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("AI DJ Drop Riser & Transition", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("Adds sweep riser before beat drop without overwriting original file", color = TextMuted, fontSize = 10.sp)
                        }
                        Switch(
                            checked = params.addAiDjIntroOutro,
                            onCheckedChange = { checked -> viewModel.aiStudio.updateSadToDjParams { it.copy(addAiDjIntroOutro = checked) } }
                        )
                    }
                }
            }

            Button(
                onClick = { selectedSong?.let { viewModel.aiStudio.convertSadSongToDj(it) } },
                enabled = selectedSong != null && taskState !is AiTaskUiState.Working,
                colors = ButtonDefaults.buttonColors(containerColor = LedPurple, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("convert_sad_to_dj_btn")
            ) {
                Icon(BijoyIcons.Bolt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("CONVERT SAD SONG → DJ REMIX WAV", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            rendered?.let { res ->
                RenderedRemixResultCard(
                    result = res,
                    isPreviewOriginal = isPreviewOriginal,
                    onToggleAb = { viewModel.aiStudio.toggleAbPreview(selectedSong, res) },
                    onLoadDeckA = { viewModel.aiStudio.loadRemixToDeck(res, DeckId.A) },
                    onLoadDeckB = { viewModel.aiStudio.loadRemixToDeck(res, DeckId.B) },
                    onExportWav = { onExportWav(res.wavFilePath, "${res.title.replace(" ", "_")}.wav") }
                )
            }
        }
    }
}

// ==================== SHARED AI STUDIO UI WIDGETS ====================

@Composable
internal fun SongSelectorStrip(
    songs: List<SongEntity>,
    selectedSong: SongEntity?,
    onSelect: (SongEntity) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("SOURCE TRACK (${songs.size} IN LIBRARY)", color = TextSecondary, fontFamily = JetBrainsMonoFontFamily, fontSize = 10.sp)
        if (songs.isEmpty()) {
            Text("Import audio in Library to begin.", color = TextMuted, fontSize = 12.sp)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(songs, key = { it.id }) { song ->
                    val active = selectedSong?.id == song.id
                    FilterChip(
                        selected = active,
                        onClick = { onSelect(song) },
                        label = {
                            Text(
                                "${song.title} (${song.bpm.toInt()} BPM)",
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DeckACyan.copy(alpha = 0.25f),
                            selectedLabelColor = DeckACyan
                        )
                    )
                }
            }
        }
    }
}

@Composable
internal fun AiTaskStatusBanner(
    state: AiTaskUiState,
    onCancel: () -> Unit,
    onRetry: (() -> Unit)? = null
) {
    when (state) {
        AiTaskUiState.Idle -> Unit
        is AiTaskUiState.Working -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(ChassisCard)
                    .border(1.dp, DeckACyan, RoundedCornerShape(8.dp))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(state.stepLabel, color = DeckACyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    LinearProgressIndicator(
                        progress = { state.progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = DeckACyan,
                        trackColor = ChassisSurface
                    )
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onCancel) { Text("Cancel", color = LedRed, fontSize = 11.sp) }
            }
        }
        is AiTaskUiState.Success -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(LedGreen.copy(alpha = 0.14f))
                    .border(1.dp, LedGreen, RoundedCornerShape(8.dp))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = LedGreen, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(state.message, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        is AiTaskUiState.Error -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(LedRed.copy(alpha = 0.15f))
                    .border(1.dp, LedRed, RoundedCornerShape(8.dp))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(BijoyIcons.ErrorOutline, contentDescription = null, tint = LedRed, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(state.message, color = TextPrimary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                if (onRetry != null) {
                    TextButton(onClick = onRetry) { Text("Retry", color = DeckACyan, fontSize = 11.sp) }
                }
            }
        }
    }
}

@Composable
internal fun RenderedRemixResultCard(
    result: RenderedRemixResult,
    isPreviewOriginal: Boolean,
    onToggleAb: () -> Unit,
    onLoadDeckA: () -> Unit,
    onLoadDeckB: () -> Unit,
    onExportWav: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(ChassisCard)
            .border(1.dp, LedGreen.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(BijoyIcons.LibraryMusic, contentDescription = null, tint = LedGreen)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(result.title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(
                        "${result.styleName} • ${formatTimeMs(result.durationMs)} • ${result.decoded.detectedBpm.toInt()} BPM • Separate WAV",
                        color = LedGreen,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 10.sp
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onToggleAb,
                    modifier = Modifier.weight(1f).testTag("ab_preview_btn")
                ) {
                    Text(
                        if (isPreviewOriginal) "A/B: ORIGINAL" else "A/B: REMIX",
                        color = if (isPreviewOriginal) DeckBGold else DeckACyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Button(
                    onClick = onLoadDeckA,
                    colors = ButtonDefaults.buttonColors(containerColor = DeckACyan, contentColor = ChassisVoid),
                    modifier = Modifier.weight(1f).testTag("remix_load_deck_a")
                ) { Text("Deck A", fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                Button(
                    onClick = onLoadDeckB,
                    colors = ButtonDefaults.buttonColors(containerColor = DeckBRed, contentColor = Color.White),
                    modifier = Modifier.weight(1f).testTag("remix_load_deck_b")
                ) { Text("Deck B", fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                OutlinedButton(
                    onClick = onExportWav,
                    modifier = Modifier.testTag("export_remix_wav_btn")
                ) {
                    Icon(BijoyIcons.SaveAlt, contentDescription = "Export WAV", tint = DeckBGold, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun RemixTenStepBar(currentStep: Int) {
    val steps = listOf(
        "1.Song", "2.Analyze", "3.Style", "4.Plan", "5.Stems",
        "6.Render", "7.Tune", "8.A/B", "9.Export", "10.Deck"
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items(steps.size) { idx ->
            val stepNum = idx + 1
            val active = stepNum == currentStep
            val done = stepNum < currentStep
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        when {
                            active -> DeckACyan.copy(alpha = 0.25f)
                            done -> LedGreen.copy(alpha = 0.18f)
                            else -> ChassisSurface
                        }
                    )
                    .border(
                        1.dp,
                        when {
                            active -> DeckACyan
                            done -> LedGreen
                            else -> MetalBorder
                        },
                        RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 7.dp, vertical = 4.dp)
            ) {
                Text(
                    steps[idx],
                    color = if (active) DeckACyan else if (done) LedGreen else TextSecondary,
                    fontSize = 9.sp,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun StudioParamSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    formatted: String,
    onChange: (Float) -> Unit
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = TextPrimary, fontSize = 11.sp)
            Text(formatted, color = DeckACyan, fontFamily = JetBrainsMonoFontFamily, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = DeckACyan, activeTrackColor = DeckACyan)
        )
    }
}
