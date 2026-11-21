package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.example.audio.AppSection
import com.example.audio.DeckId
import com.example.database.PlaylistEntity
import com.example.database.SongEntity
import com.example.ui.theme.*
import com.example.viewmodel.BijoyViewModel
import kotlinx.coroutines.launch

@Composable
fun SubScreenHeader(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0A0F1D))
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.testTag("subscreen_back_btn")) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Mixer", tint = DeckACyan)
        }
        Text(
            text = title.uppercase(),
            color = TextPrimary,
            fontFamily = OrbitronFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f)
        )
        actions()
    }
}

@Composable
fun MusicLibraryScreen(
    section: AppSection,
    viewModel: BijoyViewModel,
    onImportClick: () -> Unit,
    onBack: () -> Unit
) {
    val allSongs by viewModel.allSongs.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val favoriteSongs by viewModel.favoriteSongs.collectAsStateWithLifecycle()
    val folders by viewModel.allFolders.collectAsStateWithLifecycle()
    val artists by viewModel.allArtists.collectAsStateWithLifecycle()
    val albums by viewModel.allAlbums.collectAsStateWithLifecycle()
    val playlists by viewModel.allPlaylists.collectAsStateWithLifecycle()
    val history by viewModel.recentHistory.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf("RECENT") } // RECENT, TITLE, ARTIST, BPM
    var libraryTab by remember(section) {
        mutableStateOf(
            when (section) {
                AppSection.FAVORITES -> "FAVORITES"
                AppSection.FOLDERS -> "FOLDERS"
                AppSection.PLAYLISTS -> "PLAYLISTS"
                AppSection.HISTORY -> "HISTORY"
                else -> "ALL"
            }
        )
    }
    val customFolders by viewModel.customFolders.collectAsStateWithLifecycle()
    val mergedFolders = remember(folders, customFolders) { (folders + customFolders).distinct().sorted() }
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var selectedPlaylist by remember { mutableStateOf<PlaylistEntity?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var folderToRename by remember { mutableStateOf<String?>(null) }
    var songToAddToPlaylist by remember { mutableStateOf<SongEntity?>(null) }
    var songToDelete by remember { mutableStateOf<SongEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ChassisVoid)
            .navigationBarsPadding()
    ) {
        SubScreenHeader(
            title = section.title,
            onBack = onBack,
            actions = {
                Button(
                    onClick = onImportClick,
                    colors = ButtonDefaults.buttonColors(containerColor = DeckACyan, contentColor = ChassisVoid),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("import_music_btn")
                ) {
                    Icon(BijoyIcons.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Import Audio", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        )

        // Category Tabs
        val tabs = listOf("ALL", "REMIXES", "FAVORITES", "PLAYLISTS", "FOLDERS", "ARTISTS", "ALBUMS", "HISTORY")
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(tabs) { tab ->
                val active = libraryTab == tab
                FilterChip(
                    selected = active,
                    onClick = {
                        libraryTab = tab
                        selectedFolder = null
                        selectedPlaylist = null
                    },
                    label = { Text(tab, fontSize = 10.sp, fontFamily = JetBrainsMonoFontFamily) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = DeckACyan.copy(alpha = 0.25f),
                        selectedLabelColor = DeckACyan
                    )
                )
            }
        }

        // Search + Sort Row
        if (libraryTab == "ALL" || libraryTab == "FAVORITES" || libraryTab == "REMIXES") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search title, artist, album...", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = DeckACyan) },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("library_search_input")
                )
                FilterChip(
                    selected = true,
                    onClick = {
                        sortMode = when (sortMode) {
                            "RECENT" -> "TITLE"
                            "TITLE" -> "BPM"
                            "BPM" -> "ARTIST"
                            else -> "RECENT"
                        }
                    },
                    label = { Text("Sort: $sortMode", fontSize = 10.sp) }
                )
            }
        }

        when (libraryTab) {
            "HISTORY" -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${history.size} recent plays", color = TextSecondary, fontSize = 11.sp)
                    TextButton(onClick = { scope.launch { viewModel.repository.clearHistory() } }) {
                        Text("Clear History", color = LedRed, fontSize = 11.sp)
                    }
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(history, key = { it.id }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(ChassisCard)
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(BijoyIcons.History, contentDescription = null, tint = DeckACyan)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.songTitle, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("${item.artist} • ${item.deckName}", color = TextSecondary, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            "PLAYLISTS" -> {
                if (selectedPlaylist == null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Custom DJ Playlists (${playlists.size})", color = TextSecondary, fontSize = 12.sp)
                        Button(
                            onClick = { showCreatePlaylistDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = LedPurple),
                            modifier = Modifier.testTag("create_playlist_btn")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("New Playlist", fontSize = 11.sp)
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(playlists, key = { it.id }) { pl ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ChassisCard)
                                    .border(1.dp, LedPurple.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                                    .clickable { selectedPlaylist = pl }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(BijoyIcons.QueueMusic, contentDescription = null, tint = LedPurple)
                                Spacer(Modifier.width(10.dp))
                                Text(pl.name, color = TextPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                IconButton(onClick = { scope.launch { viewModel.repository.deletePlaylist(pl.id) } }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Playlist", tint = LedRed)
                                }
                            }
                        }
                    }
                } else {
                    val pl = selectedPlaylist!!
                    val plSongs by viewModel.repository.getSongsInPlaylist(pl.id)
                        .collectAsStateWithLifecycle(initialValue = emptyList())
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { selectedPlaylist = null }) {
                            Text("‹ All Playlists", color = DeckACyan)
                        }
                        Text(pl.name, color = TextPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (plSongs.isNotEmpty()) {
                            TextButton(onClick = {
                                viewModel.loadSongToDeck(DeckId.A, plSongs.first(), autoPlay = true)
                                if (plSongs.size > 1) viewModel.loadSongToDeck(DeckId.B, plSongs[1], autoPlay = false)
                                onBack()
                            }) {
                                Text("Load Set", color = LedGreen)
                            }
                        }
                    }
                    SongListSection(
                        songs = plSongs,
                        favoriteIds = favoriteIds,
                        onLoadA = { viewModel.loadSongToDeck(DeckId.A, it, autoPlay = false); onBack() },
                        onLoadB = { viewModel.loadSongToDeck(DeckId.B, it, autoPlay = false); onBack() },
                        onToggleFav = { s -> scope.launch { viewModel.repository.toggleFavorite(s.id, s.id in favoriteIds) } },
                        onAddToPlaylist = { songToAddToPlaylist = it },
                        onDelete = { s -> scope.launch { viewModel.repository.removeSongFromPlaylist(pl.id, s.id) } },
                        onMoveUp = { idx ->
                            if (idx > 0) scope.launch { viewModel.repository.swapPlaylistSongs(pl.id, plSongs, idx, idx - 1) }
                        },
                        onMoveDown = { idx ->
                            if (idx < plSongs.lastIndex) scope.launch { viewModel.repository.swapPlaylistSongs(pl.id, plSongs, idx, idx + 1) }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            "FOLDERS" -> {
                if (selectedFolder == null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("DJ Music Folders (${mergedFolders.size})", color = TextSecondary, fontSize = 12.sp)
                        Button(
                            onClick = { showCreateFolderDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = DeckBGold, contentColor = ChassisVoid),
                            modifier = Modifier.testTag("create_folder_btn")
                        ) {
                            Icon(BijoyIcons.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("New Folder", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(mergedFolders) { folder ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ChassisCard)
                                    .clickable { selectedFolder = folder }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(BijoyIcons.Folder, contentDescription = null, tint = DeckBGold)
                                Spacer(Modifier.width(10.dp))
                                Text(folder, color = TextPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                IconButton(onClick = { folderToRename = folder }, modifier = Modifier.size(30.dp)) {
                                    Icon(Icons.Default.Edit, contentDescription = "Rename Folder", tint = DeckACyan, modifier = Modifier.size(17.dp))
                                }
                                if (folder != "Local Music" && folder != "BIJOY Studio Tracks") {
                                    IconButton(onClick = { viewModel.deleteFolder(folder) }, modifier = Modifier.size(30.dp)) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete Folder", tint = LedRed, modifier = Modifier.size(17.dp))
                                    }
                                }
                            }
                        }
                    }
                } else {
                    TextButton(onClick = { selectedFolder = null }) {
                        Text("‹ Back to Folders ($selectedFolder)", color = DeckACyan)
                    }
                    val folderSongs = allSongs.filter { it.folderName == selectedFolder }
                    SongListSection(
                        songs = folderSongs,
                        favoriteIds = favoriteIds,
                        onLoadA = { viewModel.loadSongToDeck(DeckId.A, it); onBack() },
                        onLoadB = { viewModel.loadSongToDeck(DeckId.B, it); onBack() },
                        onToggleFav = { s -> scope.launch { viewModel.repository.toggleFavorite(s.id, s.id in favoriteIds) } },
                        onAddToPlaylist = { songToAddToPlaylist = it },
                        onDelete = { songToDelete = it },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            "ARTISTS" -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(artists, key = { it.id }) { a ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(ChassisCard)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = DeckACyan)
                            Spacer(Modifier.width(10.dp))
                            Text(a.name, color = TextPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            "ALBUMS" -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(albums, key = { it.id }) { al ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(ChassisCard)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(BijoyIcons.Album, contentDescription = null, tint = DeckBOrange)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(al.title, color = TextPrimary, fontWeight = FontWeight.Bold)
                                Text(al.artistName, color = TextSecondary, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            else -> {
                val baseList = when (libraryTab) {
                    "FAVORITES" -> favoriteSongs
                    "REMIXES" -> allSongs.filter { it.sourceType == "REMIX" || it.folderName == "AI Remixes" }
                    else -> allSongs
                }
                val filtered = baseList.filter {
                    searchQuery.isBlank() ||
                        it.title.contains(searchQuery, ignoreCase = true) ||
                        it.artist.contains(searchQuery, ignoreCase = true) ||
                        it.album.contains(searchQuery, ignoreCase = true)
                }.let { list ->
                    when (sortMode) {
                        "TITLE" -> list.sortedBy { it.title.lowercase() }
                        "ARTIST" -> list.sortedBy { it.artist.lowercase() }
                        "BPM" -> list.sortedBy { it.bpm }
                        else -> list
                    }
                }
                SongListSection(
                    songs = filtered,
                    favoriteIds = favoriteIds,
                    onLoadA = { viewModel.loadSongToDeck(DeckId.A, it); onBack() },
                    onLoadB = { viewModel.loadSongToDeck(DeckId.B, it); onBack() },
                    onOpenRemix = { s ->
                        viewModel.aiStudio.selectSourceSong(s)
                        viewModel.navigateTo(AppSection.AI_REMIX_STUDIO)
                    },
                    onOpenStems = { s ->
                        viewModel.aiStudio.selectSourceSong(s)
                        viewModel.aiStudio.separateTrackStems(s)
                        viewModel.navigateTo(AppSection.STEM_SEPARATOR)
                    },
                    onToggleFav = { s -> scope.launch { viewModel.repository.toggleFavorite(s.id, s.id in favoriteIds) } },
                    onAddToPlaylist = { songToAddToPlaylist = it },
                    onDelete = { songToDelete = it },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    // Create Playlist Dialog
    if (showCreatePlaylistDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            containerColor = ChassisDark,
            title = { Text("Create DJ Playlist", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("Playlist name...") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (name.isNotBlank()) {
                        scope.launch { viewModel.repository.createPlaylist(name) }
                        showCreatePlaylistDialog = false
                    }
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreatePlaylistDialog = false }) { Text("Cancel") } }
        )
    }

    // Add Song to Playlist Dialog
    songToAddToPlaylist?.let { song ->
        AlertDialog(
            onDismissRequest = { songToAddToPlaylist = null },
            containerColor = ChassisDark,
            title = { Text("Add '${song.title}' to Playlist", color = TextPrimary, fontSize = 15.sp) },
            text = {
                if (playlists.isEmpty()) {
                    Text("No playlists yet. Create one in the Playlists tab first.", color = TextSecondary)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        playlists.forEach { pl ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(ChassisSurface)
                                    .clickable {
                                        scope.launch { viewModel.repository.addSongToPlaylist(pl.id, song.id) }
                                        viewModel.postToast("Added to ${pl.name}")
                                        songToAddToPlaylist = null
                                    }
                                    .padding(10.dp)
                            ) {
                                Text(pl.name, color = TextPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { songToAddToPlaylist = null }) { Text("Close") } }
        )
    }

    // Confirm Delete Library Entry (never deletes original device audio file)
    songToDelete?.let { song ->
        AlertDialog(
            onDismissRequest = { songToDelete = null },
            containerColor = ChassisDark,
            title = { Text("Remove from Library?", color = TextPrimary) },
            text = {
                Text(
                    "Remove '${song.title}' from BIJOY DJ library? Your original audio file on device storage will NOT be deleted.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch { viewModel.repository.deleteSongFromLibrary(song.id) }
                        songToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LedRed)
                ) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { songToDelete = null }) { Text("Cancel") } }
        )
    }
    // Create Folder Dialog
    if (showCreateFolderDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            containerColor = ChassisDark,
            title = { Text("Create DJ Folder", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("Folder name...") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (name.isNotBlank()) {
                        viewModel.createFolder(name)
                        showCreateFolderDialog = false
                    }
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreateFolderDialog = false }) { Text("Cancel") } }
        )
    }

    // Rename Folder Dialog
    folderToRename?.let { oldName ->
        var newName by remember(oldName) { mutableStateOf(oldName) }
        AlertDialog(
            onDismissRequest = { folderToRename = null },
            containerColor = ChassisDark,
            title = { Text("Rename Folder", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Folder Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newName.isNotBlank()) {
                        viewModel.renameFolder(oldName, newName)
                        folderToRename = null
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { folderToRename = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SongListSection(
    songs: List<SongEntity>,
    favoriteIds: Set<Long>,
    onLoadA: (SongEntity) -> Unit,
    onLoadB: (SongEntity) -> Unit,
    onToggleFav: (SongEntity) -> Unit,
    onAddToPlaylist: (SongEntity) -> Unit,
    onDelete: (SongEntity) -> Unit,
    onOpenRemix: ((SongEntity) -> Unit)? = null,
    onOpenStems: ((SongEntity) -> Unit)? = null,
    onMoveUp: ((Int) -> Unit)? = null,
    onMoveDown: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (songs.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("No tracks found. Tap 'Import Audio' to add local MP3/WAV/FLAC/AAC/OGG files.", color = TextSecondary, fontSize = 12.sp)
        }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(songs.size, key = { idx -> songs[idx].id }) { index ->
            val song = songs[index]
            val isFav = song.id in favoriteIds
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(ChassisCard)
                    .border(1.dp, MetalBorder.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${song.artist} • ${String.format("%.0f", song.bpm)} BPM • ${song.musicalKey} • ${formatTimeMs(song.durationMs)} • ${song.format}",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontFamily = JetBrainsMonoFontFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Load to Deck A / Deck B buttons + AI Remix / Stems + optional Reorder arrows
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (onMoveUp != null && index > 0) {
                        IconButton(onClick = { onMoveUp(index) }, modifier = Modifier.size(28.dp)) {
                            Icon(BijoyIcons.ArrowUpward, contentDescription = "Move Up", tint = DeckACyan, modifier = Modifier.size(16.dp))
                        }
                    }
                    if (onMoveDown != null && index < songs.lastIndex) {
                        IconButton(onClick = { onMoveDown(index) }, modifier = Modifier.size(28.dp)) {
                            Icon(BijoyIcons.ArrowDownward, contentDescription = "Move Down", tint = DeckACyan, modifier = Modifier.size(16.dp))
                        }
                    }
                    if (onOpenRemix != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(LedPurple.copy(alpha = 0.22f))
                                .border(1.dp, LedPurple, RoundedCornerShape(6.dp))
                                .clickable { onOpenRemix(song) }
                                .padding(horizontal = 7.dp, vertical = 5.dp)
                        ) {
                            Text("REMIX", color = LedPurple, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = JetBrainsMonoFontFamily)
                        }
                    }
                    if (onOpenStems != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(DeckBGold.copy(alpha = 0.18f))
                                .border(1.dp, DeckBGold, RoundedCornerShape(6.dp))
                                .clickable { onOpenStems(song) }
                                .padding(horizontal = 7.dp, vertical = 5.dp)
                        ) {
                            Text("STEMS", color = DeckBGold, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = JetBrainsMonoFontFamily)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(DeckACyan.copy(alpha = 0.22f))
                            .border(1.dp, DeckACyan, RoundedCornerShape(6.dp))
                            .clickable { onLoadA(song) }
                            .testTag("load_deck_a_${song.id}")
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Text("DECK A", color = DeckACyan, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = JetBrainsMonoFontFamily)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(DeckBRed.copy(alpha = 0.22f))
                            .border(1.dp, DeckBRed, RoundedCornerShape(6.dp))
                            .clickable { onLoadB(song) }
                            .testTag("load_deck_b_${song.id}")
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Text("DECK B", color = DeckBRed, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = JetBrainsMonoFontFamily)
                    }
                    IconButton(onClick = { onToggleFav(song) }, modifier = Modifier.size(30.dp)) {
                        Icon(
                            imageVector = if (isFav) Icons.Default.Star else BijoyIcons.StarBorder,
                            contentDescription = "Favorite",
                            tint = if (isFav) DeckBGold else TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = { onAddToPlaylist(song) }, modifier = Modifier.size(30.dp)) {
                        Icon(BijoyIcons.PlaylistAdd, contentDescription = "Add to Playlist", tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { onDelete(song) }, modifier = Modifier.size(30.dp)) {
                        Icon(BijoyIcons.DeleteOutline, contentDescription = "Remove", tint = TextMuted, modifier = Modifier.size(17.dp))
                    }
                }
            }
        }
    }
}
