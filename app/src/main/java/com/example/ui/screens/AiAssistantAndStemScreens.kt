package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
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
import com.example.ui.theme.*
import com.example.viewmodel.BijoyViewModel

// ==================== 4-STEM SEPARATOR & VOCAL REMOVER ====================

@Composable
fun StemSeparatorScreen(
    viewModel: BijoyViewModel,
    onBack: () -> Unit
) {
    val allSongs by viewModel.allSongs.collectAsStateWithLifecycle()
    val selectedSong by viewModel.aiStudio.selectedSourceSong.collectAsStateWithLifecycle()
    val stems by viewModel.aiStudio.separatedStems.collectAsStateWithLifecycle()
    val levels by viewModel.aiStudio.stemMixLevels.collectAsStateWithLifecycle()
    val stemTask by viewModel.aiStudio.stemTaskState.collectAsStateWithLifecycle()

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
        SubScreenHeader("AI 4-Stem Separator & Vocal Remover", onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "On-Device DSP Spectral & Mid-Side 4-Stem Separator (Vocals • Drums • Bass • Other)",
                color = DeckACyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )

            AiTaskStatusBanner(
                state = stemTask,
                onCancel = { viewModel.aiStudio.cancelActiveAiJob() }
            )

            SongSelectorStrip(
                songs = allSongs,
                selectedSong = selectedSong,
                onSelect = { viewModel.aiStudio.selectSourceSong(it) }
            )

            Button(
                onClick = { selectedSong?.let { viewModel.aiStudio.separateTrackStems(it) } },
                enabled = selectedSong != null && stemTask !is AiTaskUiState.Working,
                colors = ButtonDefaults.buttonColors(containerColor = DeckACyan, contentColor = ChassisVoid),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("separate_stems_btn")
            ) {
                Icon(BijoyIcons.MultilineChart, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("SEPARATE INTO 4 STEMS (VOCALS • DRUMS • BASS • OTHER)", fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }

            stems?.let { sep ->
                // Quick Modes
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    FilterChip(
                        selected = levels.soloStem == "VOCALS",
                        onClick = { viewModel.aiStudio.applyVocalsOnlyQuickMode() },
                        label = { Text("Vocals Only", fontSize = 10.sp) },
                        modifier = Modifier.testTag("stem_vocals_only_btn")
                    )
                    FilterChip(
                        selected = levels.vocalsMuted && levels.soloStem == null,
                        onClick = { viewModel.aiStudio.applyInstrumentalOnlyQuickMode() },
                        label = { Text("Instrumental (Remove Vocals)", fontSize = 10.sp) },
                        modifier = Modifier.testTag("stem_instrumental_only_btn")
                    )
                    FilterChip(
                        selected = false,
                        onClick = { viewModel.aiStudio.updateStemLevels { StemMixLevels() } },
                        label = { Text("Reset", fontSize = 10.sp) }
                    )
                }

                StemTrackControlCard(
                    title = "VOCALS STEM (Center 300Hz–4.3kHz)",
                    accent = LedPurple,
                    energy = sep.vocalsEnergy,
                    gain = levels.vocalsGain,
                    muted = levels.vocalsMuted,
                    solo = levels.soloStem == "VOCALS",
                    onGainChange = { g -> viewModel.aiStudio.updateStemLevels { it.copy(vocalsGain = g) } },
                    onToggleMute = { viewModel.aiStudio.updateStemLevels { it.copy(vocalsMuted = !it.vocalsMuted) } },
                    onToggleSolo = { viewModel.aiStudio.updateStemLevels { it.copy(soloStem = if (it.soloStem == "VOCALS") null else "VOCALS") } },
                    onExportStem = { viewModel.aiStudio.exportSingleStemWav("VOCALS") }
                )

                StemTrackControlCard(
                    title = "DRUMS STEM (Transients & Hats)",
                    accent = DeckBGold,
                    energy = sep.drumsEnergy,
                    gain = levels.drumsGain,
                    muted = levels.drumsMuted,
                    solo = levels.soloStem == "DRUMS",
                    onGainChange = { g -> viewModel.aiStudio.updateStemLevels { it.copy(drumsGain = g) } },
                    onToggleMute = { viewModel.aiStudio.updateStemLevels { it.copy(drumsMuted = !it.drumsMuted) } },
                    onToggleSolo = { viewModel.aiStudio.updateStemLevels { it.copy(soloStem = if (it.soloStem == "DRUMS") null else "DRUMS") } },
                    onExportStem = { viewModel.aiStudio.exportSingleStemWav("DRUMS") }
                )

                StemTrackControlCard(
                    title = "BASS STEM (<165Hz Sub & Bassline)",
                    accent = DeckBRed,
                    energy = sep.bassEnergy,
                    gain = levels.bassGain,
                    muted = levels.bassMuted,
                    solo = levels.soloStem == "BASS",
                    onGainChange = { g -> viewModel.aiStudio.updateStemLevels { it.copy(bassGain = g) } },
                    onToggleMute = { viewModel.aiStudio.updateStemLevels { it.copy(bassMuted = !it.bassMuted) } },
                    onToggleSolo = { viewModel.aiStudio.updateStemLevels { it.copy(soloStem = if (it.soloStem == "BASS") null else "BASS") } },
                    onExportStem = { viewModel.aiStudio.exportSingleStemWav("BASS") }
                )

                StemTrackControlCard(
                    title = "OTHER INSTRUMENTS STEM (Synths & Sides)",
                    accent = DeckACyan,
                    energy = sep.otherEnergy,
                    gain = levels.otherGain,
                    muted = levels.otherMuted,
                    solo = levels.soloStem == "OTHER",
                    onGainChange = { g -> viewModel.aiStudio.updateStemLevels { it.copy(otherGain = g) } },
                    onToggleMute = { viewModel.aiStudio.updateStemLevels { it.copy(otherMuted = !it.otherMuted) } },
                    onToggleSolo = { viewModel.aiStudio.updateStemLevels { it.copy(soloStem = if (it.soloStem == "OTHER") null else "OTHER") } },
                    onExportStem = { viewModel.aiStudio.exportSingleStemWav("OTHER") }
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { viewModel.aiStudio.previewStemsOnDeck(DeckId.A) },
                        colors = ButtonDefaults.buttonColors(containerColor = DeckACyan, contentColor = ChassisVoid),
                        modifier = Modifier.weight(1f)
                    ) { Text("Play Stems Deck A", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    Button(
                        onClick = { viewModel.aiStudio.previewStemsOnDeck(DeckId.B) },
                        colors = ButtonDefaults.buttonColors(containerColor = DeckBRed, contentColor = Color.White),
                        modifier = Modifier.weight(1f)
                    ) { Text("Deck B", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    Button(
                        onClick = { viewModel.aiStudio.remixSeparatedStems(RemixStyle.CLUB_REMIX) },
                        colors = ButtonDefaults.buttonColors(containerColor = LedPurple, contentColor = Color.White),
                        modifier = Modifier.weight(1f)
                    ) { Text("Remix Stems", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun StemTrackControlCard(
    title: String,
    accent: Color,
    energy: Float,
    gain: Float,
    muted: Boolean,
    solo: Boolean,
    onGainChange: (Float) -> Unit,
    onToggleMute: () -> Unit,
    onToggleSolo: () -> Unit,
    onExportStem: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(ChassisCard)
            .border(1.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = accent, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1f))
                Text("${(gain * 100).toInt()}%", color = TextPrimary, fontFamily = JetBrainsMonoFontFamily, fontSize = 11.sp)
            }
            LinearProgressIndicator(
                progress = { energy.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = accent,
                trackColor = ChassisSurface
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Slider(
                    value = gain,
                    onValueChange = onGainChange,
                    valueRange = 0f..1.5f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                )
                FilterChip(
                    selected = muted,
                    onClick = onToggleMute,
                    label = { Text("MUTE", fontSize = 9.sp, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = LedRed.copy(alpha = 0.3f), selectedLabelColor = LedRed)
                )
                FilterChip(
                    selected = solo,
                    onClick = onToggleSolo,
                    label = { Text("SOLO", fontSize = 9.sp, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = DeckBGold.copy(alpha = 0.3f), selectedLabelColor = DeckBGold)
                )
                OutlinedButton(onClick = onExportStem, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                    Text("WAV", fontSize = 10.sp, color = TextPrimary)
                }
            }
        }
    }
}

// ==================== AI AUTO DJ STUDIO ====================

@Composable
fun AiAutoDjScreen(
    viewModel: BijoyViewModel,
    onBack: () -> Unit
) {
    val allSongs by viewModel.allSongs.collectAsStateWithLifecycle()
    val state by viewModel.aiStudio.autoDjState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ChassisVoid)
            .navigationBarsPadding()
    ) {
        SubScreenHeader("AI Auto DJ Mixing Engine", onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Active Auto DJ Live Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(ChassisCard)
                    .border(1.dp, if (state.isActive) LedGreen else DeckACyan.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (state.isActive) "LIVE AUTO DJ • DECK ${state.currentDeck.name} ACTIVE" else "AI AUTO DJ READY",
                        color = if (state.isActive) LedGreen else DeckACyan,
                        fontFamily = OrbitronFontFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(state.compatibilitySummary, color = TextPrimary, fontSize = 12.sp)
                    if (state.activePlanExplanation.isNotBlank()) {
                        Text(state.activePlanExplanation, color = TextSecondary, fontSize = 11.sp)
                    }
                    if (state.isTransitioning) {
                        LinearProgressIndicator(
                            progress = { state.transitionProgress },
                            modifier = Modifier.fillMaxWidth(),
                            color = DeckBGold
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        if (!state.isActive) {
                            Button(
                                onClick = { viewModel.aiStudio.startAutoDj(allSongs) },
                                colors = ButtonDefaults.buttonColors(containerColor = LedGreen, contentColor = ChassisVoid),
                                modifier = Modifier.weight(1f).testTag("start_auto_dj_btn")
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Start Auto DJ", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = { viewModel.aiStudio.toggleAutoDjPause() },
                                colors = ButtonDefaults.buttonColors(containerColor = DeckBGold, contentColor = ChassisVoid),
                                modifier = Modifier.weight(1f)
                            ) { Text(if (state.isPaused) "Resume" else "Pause", fontWeight = FontWeight.Bold) }
                            Button(
                                onClick = { viewModel.aiStudio.triggerAutoDjNextNow() },
                                colors = ButtonDefaults.buttonColors(containerColor = DeckACyan, contentColor = ChassisVoid),
                                modifier = Modifier.weight(1f)
                            ) { Text("Mix Next", fontWeight = FontWeight.Bold) }
                            Button(
                                onClick = { viewModel.aiStudio.stopAutoDj() },
                                colors = ButtonDefaults.buttonColors(containerColor = LedRed, contentColor = Color.White),
                                modifier = Modifier.weight(1f)
                            ) { Text("Stop", fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }

            // 3 Modes
            Text("AUTO DJ MODE", color = DeckBGold, fontFamily = OrbitronFontFamily, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                AutoDjMode.entries.forEach { mode ->
                    val selected = state.mode == mode
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.aiStudio.setAutoDjMode(mode) },
                        label = { Text(mode.displayName, fontSize = 10.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 8 Transition Styles
            Text("TRANSITION STYLE (8 STYLES)", color = DeckACyan, fontFamily = OrbitronFontFamily, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(AutoDjTransitionStyle.entries) { style ->
                    FilterChip(
                        selected = state.transitionStyle == style,
                        onClick = { viewModel.aiStudio.setAutoDjTransitionStyle(style) },
                        label = { Text(style.displayName, fontSize = 10.sp) }
                    )
                }
            }

            // Track Queue Builder
            Text("MIX QUEUE (${state.queueSongIds.size} SELECTED — LEAVE EMPTY TO AUTO-QUEUE ALL)", color = TextSecondary, fontFamily = JetBrainsMonoFontFamily, fontSize = 10.sp)
            allSongs.forEach { song ->
                val checked = song.id in state.queueSongIds
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(ChassisCard)
                        .clickable { viewModel.aiStudio.toggleAutoDjSong(song.id) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = checked, onCheckedChange = { viewModel.aiStudio.toggleAutoDjSong(song.id) })
                    Column(modifier = Modifier.weight(1f)) {
                        Text(song.title, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${song.artist} • ${song.bpm.toInt()} BPM • Key ${song.musicalKey}", color = TextSecondary, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

// ==================== AI REMIX GENERATOR (16-STEP BEAT PATTERN) ====================

@Composable
fun AiRemixGeneratorScreen(
    viewModel: BijoyViewModel,
    onBack: () -> Unit
) {
    val allSongs by viewModel.allSongs.collectAsStateWithLifecycle()
    val selectedSong by viewModel.aiStudio.selectedSourceSong.collectAsStateWithLifecycle()
    val kick by viewModel.aiStudio.kickPattern.collectAsStateWithLifecycle()
    val snare by viewModel.aiStudio.snarePattern.collectAsStateWithLifecycle()
    val hat by viewModel.aiStudio.hatPattern.collectAsStateWithLifecycle()
    val taskState by viewModel.aiStudio.aiTaskState.collectAsStateWithLifecycle()
    val rendered by viewModel.aiStudio.renderedRemix.collectAsStateWithLifecycle()

    var prompt by remember { mutableStateOf("Bengali Folk EDM punchy club drop") }
    var styleChoice by remember { mutableStateOf(RemixStyle.BENGALI_DJ) }

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
        SubScreenHeader("AI Beat & Remix Pattern Generator", onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AiTaskStatusBanner(state = taskState, onCancel = { viewModel.aiStudio.cancelActiveAiJob() })

            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                label = { Text("Describe your Remix Beat & Drop Prompt", fontSize = 11.sp) },
                modifier = Modifier.fillMaxWidth().testTag("pattern_prompt_input"),
                singleLine = true
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(
                    listOf(
                        RemixStyle.BENGALI_DJ, RemixStyle.EDM_CLUB, RemixStyle.TRAP_REMIX,
                        RemixStyle.DEEP_HOUSE, RemixStyle.SAD_DJ, RemixStyle.AFRO_HOUSE
                    )
                ) { st ->
                    FilterChip(
                        selected = styleChoice == st,
                        onClick = { styleChoice = st },
                        label = { Text(st.displayName, fontSize = 10.sp) }
                    )
                }
            }

            Button(
                onClick = { viewModel.aiStudio.generateAiBeatPattern(prompt, styleChoice.displayName) },
                enabled = taskState !is AiTaskUiState.Working,
                colors = ButtonDefaults.buttonColors(containerColor = LedPurple),
                modifier = Modifier.fillMaxWidth().height(46.dp).testTag("generate_pattern_btn")
            ) {
                Icon(BijoyIcons.AutoAwesome, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("GENERATE 16-STEP PATTERN (GEMINI 3.8 FLASH)", fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }

            // 16-Step Pattern Grid
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(ChassisCard)
                    .border(1.dp, DeckACyan.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                    .padding(10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("16-STEP DRUM & DROP SEQUENCER", color = DeckACyan, fontFamily = OrbitronFontFamily, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    PatternSequencerRow("KICK", kick, DeckBRed) { step -> viewModel.aiStudio.togglePatternStep(0, step) }
                    PatternSequencerRow("SNARE", snare, DeckBGold) { step -> viewModel.aiStudio.togglePatternStep(1, step) }
                    PatternSequencerRow("HI-HAT", hat, DeckACyan) { step -> viewModel.aiStudio.togglePatternStep(2, step) }
                }
            }

            SongSelectorStrip(
                songs = allSongs,
                selectedSong = selectedSong,
                onSelect = { viewModel.aiStudio.selectSourceSong(it) }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { selectedSong?.let { viewModel.aiStudio.convertSadSongToDj(it) } },
                    enabled = selectedSong != null && taskState !is AiTaskUiState.Working,
                    colors = ButtonDefaults.buttonColors(containerColor = DeckACyan, contentColor = ChassisVoid),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("Render Pattern Remix WAV", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
                rendered?.let { res ->
                    Button(
                        onClick = { viewModel.aiStudio.loadRemixToDeck(res, DeckId.A) },
                        colors = ButtonDefaults.buttonColors(containerColor = LedGreen, contentColor = ChassisVoid),
                        modifier = Modifier.height(48.dp)
                    ) { Text("Load Deck A", fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                }
            }
        }
    }
}

@Composable
private fun PatternSequencerRow(
    label: String,
    steps: List<Int>,
    activeColor: Color,
    onToggle: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = activeColor, fontFamily = JetBrainsMonoFontFamily, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxWidth()) {
            for (i in 0 until 16) {
                val on = steps.getOrElse(i) { 0 } == 1
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(30.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (on) activeColor else if (i % 4 == 0) Color(0xFF202A3E) else ChassisSurface)
                        .border(1.dp, if (on) Color.White else MetalBorder, RoundedCornerShape(4.dp))
                        .clickable { onToggle(i) },
                    contentAlignment = Alignment.Center
                ) {
                    Text("${i + 1}", color = if (on) ChassisVoid else TextMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ==================== BIJOY AI DJ ASSISTANT (CONFIRM BEFORE APPLYING) ====================

@Composable
fun BijoyAiAssistantScreen(
    viewModel: BijoyViewModel,
    onVoiceCommandClick: () -> Unit,
    onBack: () -> Unit
) {
    val allSongs by viewModel.allSongs.collectAsStateWithLifecycle()
    val messages by viewModel.aiStudio.assistantMessages.collectAsStateWithLifecycle()
    val loading by viewModel.aiStudio.assistantLoading.collectAsStateWithLifecycle()
    var inputText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ChassisVoid)
            .navigationBarsPadding()
    ) {
        SubScreenHeader("BIJOY AI DJ Assistant (Gemini 3.8 Flash)", onBack = onBack)

        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val quickPrompts = listOf(
                "Match BPM on Deck B",
                "Apply Bass Boost EQ",
                "Add Echo FX on Deck A",
                "Remove vocals on Deck A",
                "Swap bass Deck A to B",
                "Open Sad to DJ converter"
            )
            items(quickPrompts) { qp ->
                AssistChip(
                    onClick = { viewModel.aiStudio.sendAssistantPrompt(qp, allSongs) },
                    label = { Text(qp, fontSize = 10.sp) }
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                val bg = when {
                    msg.isUser -> DeckACyan.copy(alpha = 0.2f)
                    msg.isError -> LedRed.copy(alpha = 0.16f)
                    else -> ChassisCard
                }
                val border = when {
                    msg.isUser -> DeckACyan
                    msg.isError -> LedRed
                    else -> LedPurple.copy(alpha = 0.55f)
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(bg)
                        .border(1.dp, border, RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        if (msg.isUser) "DJ YOU" else "BIJOY AI ASSISTANT",
                        color = if (msg.isUser) DeckACyan else LedPurple,
                        fontFamily = OrbitronFontFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(msg.text, color = TextPrimary, fontSize = 12.sp)

                    if (msg.proposedActions.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(ChassisSurface)
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("PROPOSED MIXER ACTION PLAN:", color = DeckBGold, fontFamily = JetBrainsMonoFontFamily, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            msg.proposedActions.forEach { act ->
                                Text("• [${act.targetDeck}] ${act.description} (${act.actionType})", color = TextPrimary, fontSize = 11.sp)
                            }
                            when (msg.actionsApproved) {
                                null -> {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                                        Button(
                                            onClick = { viewModel.aiStudio.approveAssistantActions(msg.id) },
                                            colors = ButtonDefaults.buttonColors(containerColor = LedGreen, contentColor = ChassisVoid),
                                            modifier = Modifier.testTag("approve_ai_plan_${msg.id}")
                                        ) { Text("Approve & Apply", fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                                        OutlinedButton(
                                            onClick = { viewModel.aiStudio.declineAssistantActions(msg.id) },
                                            modifier = Modifier.testTag("decline_ai_plan_${msg.id}")
                                        ) { Text("Decline", color = LedRed, fontSize = 11.sp) }
                                    }
                                }
                                true -> Text("✓ Approved & applied to mixer", color = LedGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                false -> Text("✕ Declined by DJ — mixer unchanged", color = LedRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        if (loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = LedPurple)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0A0F1D))
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(
                onClick = onVoiceCommandClick,
                modifier = Modifier.testTag("ai_voice_cmd_btn")
            ) {
                Icon(BijoyIcons.Mic, contentDescription = "Voice DJ Command", tint = DeckBGold)
            }
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Ask AI DJ (e.g. 'Match BPM and boost bass')...", fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("ai_prompt_input")
            )
            IconButton(
                onClick = {
                    val text = inputText.trim()
                    if (text.isNotEmpty()) {
                        viewModel.aiStudio.sendAssistantPrompt(text, allSongs)
                        inputText = ""
                    }
                },
                enabled = !loading && inputText.isNotBlank(),
                modifier = Modifier.testTag("ai_send_btn")
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send AI Prompt", tint = DeckACyan)
            }
        }
    }
}
