package com.lyricsstatus.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lyricsstatus.app.data.parser.LrcParser
import com.lyricsstatus.app.ui.components.LyricsDisplay
import com.lyricsstatus.app.ui.theme.DarkBackground
import com.lyricsstatus.app.ui.theme.LyricsInactive
import com.lyricsstatus.app.ui.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    viewModel: PlayerViewModel,
    onNavigateToAiSettings: () -> Unit,
    onNavigateToCustomLyrics: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val playbackState by viewModel.playbackState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    var showDemoPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "LyricsStatus",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = if (playbackState.hasTrack) {
                                "${playbackState.providerLabel.ifBlank { "Media Session" }} • ${playbackState.lyricsSource}"
                            } else "Background Sync Ready",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    // AI Settings button
                    IconButton(onClick = onNavigateToAiSettings) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = "AI Settings",
                            tint = if (settings.enableTranslation) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    // Custom Lyrics button
                    IconButton(onClick = onNavigateToCustomLyrics) {
                        Icon(
                            imageVector = Icons.Rounded.EditNote,
                            contentDescription = "Custom Lyrics"
                        )
                    }
                    // General Settings button
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground.copy(alpha = 0.95f)
                )
            )
        },
        bottomBar = {
            BottomPlayerControls(
                playbackState = playbackState,
                settings = settings,
                onTogglePlay = { viewModel.togglePlayPause() },
                onSeek = { viewModel.seekTo(it) },
                onToggleTranslation = { viewModel.toggleTranslation() },
                onAdjustOffset = { viewModel.adjustOffset(it) },
                onToggleDemo = { showDemoPicker = !showDemoPicker },
                onResync = { viewModel.refreshLyrics() }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            DarkBackground,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                            DarkBackground
                        )
                    )
                )
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Playback Mode Switcher: Android Media / Discord RPC / Auto
                ModeSelectorBar(
                    currentMode = settings.musicDetectionMode,
                    onModeSelected = { viewModel.setMusicDetectionMode(it) }
                )

                // Header card with currently playing track details
                if (playbackState.hasTrack) {
                    TrackHeaderCard(
                        songName = playbackState.songName,
                        songAuthor = playbackState.songAuthor,
                        lyricsSource = playbackState.lyricsSource,
                        aiProvider = settings.aiProvider.displayName,
                        targetLanguage = settings.targetLanguage,
                        isTranslating = playbackState.isTranslating,
                        enableTranslation = settings.enableTranslation,
                        providerLabel = playbackState.providerLabel
                    )
                }

                // Demo track selector drawer
                AnimatedVisibility(visible = showDemoPicker) {
                    DemoTrackSelector(
                        onSelectDemo = { title, artist, sec ->
                            viewModel.loadDemoTrack(title, artist, sec)
                            showDemoPicker = false
                        }
                    )
                }

                // Main Synchronized Lyrics Stream
                LyricsDisplay(
                    lyrics = playbackState.lyrics,
                    activeLineIndex = playbackState.currentLineIndex,
                    isTranslating = playbackState.isTranslating,
                    enableTranslation = settings.enableTranslation,
                    onLineClick = { timestamp -> viewModel.seekTo(timestamp) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun ModeSelectorBar(
    currentMode: String,
    onModeSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val modes = listOf(
            Triple("auto", "Auto Sync", Icons.Rounded.Sync),
            Triple("android", "Android Player", Icons.Rounded.Headphones),
            Triple("discord", "Discord RPC", Icons.Rounded.GraphicEq)
        )

        modes.forEach { (modeId, label, icon) ->
            val isSelected = currentMode == modeId
            FilterChip(
                selected = isSelected,
                onClick = { onModeSelected(modeId) },
                label = { Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                leadingIcon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                shape = RoundedCornerShape(8.dp)
            )
        }
    }
}

@Composable
fun TrackHeaderCard(
    songName: String,
    songAuthor: String,
    lyricsSource: String,
    aiProvider: String,
    targetLanguage: String,
    isTranslating: Boolean,
    enableTranslation: Boolean,
    providerLabel: String = ""
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = songName,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = songAuthor,
                style = MaterialTheme.typography.bodyMedium,
                color = LyricsInactive,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Source badge
                Text(
                    text = "Source: $lyricsSource (${if (providerLabel.isNotBlank()) providerLabel else "Media"})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                if (enableTranslation) {
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = LyricsInactive
                    )
                    Text(
                        text = if (isTranslating) "AI: Translating…" else "AI: $aiProvider ($targetLanguage)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@Composable
fun DemoTrackSelector(
    onSelectDemo: (String, String, Int) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Demo Tracks (Instant Test):",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AssistChip(
                    onClick = { onSelectDemo("Blinding Lights", "The Weeknd", 200) },
                    label = { Text("Blinding Lights", fontSize = 11.sp) },
                    leadingIcon = {
                        Icon(Icons.Rounded.MusicNote, contentDescription = null, modifier = Modifier.size(14.dp))
                    }
                )
                AssistChip(
                    onClick = { onSelectDemo("Bohemian Rhapsody", "Queen", 354) },
                    label = { Text("Queen", fontSize = 11.sp) },
                    leadingIcon = {
                        Icon(Icons.Rounded.MusicNote, contentDescription = null, modifier = Modifier.size(14.dp))
                    }
                )
                AssistChip(
                    onClick = { onSelectDemo("As It Was", "Harry Styles", 167) },
                    label = { Text("Harry Styles", fontSize = 11.sp) },
                    leadingIcon = {
                        Icon(Icons.Rounded.MusicNote, contentDescription = null, modifier = Modifier.size(14.dp))
                    }
                )
            }
        }
    }
}

@Composable
fun BottomPlayerControls(
    playbackState: com.lyricsstatus.app.data.model.PlaybackState,
    settings: com.lyricsstatus.app.data.model.AppSettings,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleTranslation: () -> Unit,
    onAdjustOffset: (Long) -> Unit,
    onToggleDemo: () -> Unit,
    onResync: () -> Unit = {}
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = DarkBackground.copy(alpha = 0.95f),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Timeline progress bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = LrcParser.formatSeconds(playbackState.songProgress / 1000L),
                    style = MaterialTheme.typography.labelSmall,
                    color = LyricsInactive
                )
                Slider(
                    value = playbackState.progressFraction(),
                    onValueChange = { fraction ->
                        val targetMs = (fraction * playbackState.songDuration).toLong()
                        onSeek(targetMs)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )
                Text(
                    text = LrcParser.formatSeconds(playbackState.songDuration / 1000L),
                    style = MaterialTheme.typography.labelSmall,
                    color = LyricsInactive
                )
            }

            // Controls row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Offset adjust buttons
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onAdjustOffset(-100L) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Remove, contentDescription = "-100ms", modifier = Modifier.size(16.dp))
                    }
                    Text(
                        text = "${settings.sendTimeOffsetMs}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = { onAdjustOffset(100L) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Add, contentDescription = "+100ms", modifier = Modifier.size(16.dp))
                    }
                }

                // Media Action Buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onSeek((playbackState.songProgress - 10000L).coerceAtLeast(0L)) }) {
                        Icon(Icons.Rounded.FastRewind, contentDescription = "Rewind 10s")
                    }

                    FilledIconButton(
                        onClick = onTogglePlay,
                        modifier = Modifier.size(52.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(28.dp),
                            tint = Color.White
                        )
                    }

                    IconButton(onClick = { onSeek((playbackState.songProgress + 10000L).coerceAtMost(playbackState.songDuration)) }) {
                        Icon(Icons.Rounded.FastForward, contentDescription = "Forward 10s")
                    }
                }

                // AI translate, resync & Demo toggles
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onToggleTranslation) {
                        Icon(
                            imageVector = Icons.Rounded.Translate,
                            contentDescription = "Toggle Translation",
                            tint = if (settings.enableTranslation) MaterialTheme.colorScheme.secondary else LyricsInactive
                        )
                    }
                    IconButton(onClick = onResync) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Resync lyrics",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onToggleDemo) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = "Demo tracks"
                        )
                    }
                }
            }
        }
    }
}
