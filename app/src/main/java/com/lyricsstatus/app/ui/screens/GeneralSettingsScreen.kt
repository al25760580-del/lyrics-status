package com.lyricsstatus.app.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lyricsstatus.app.data.discord.DiscordEmojiApi
import com.lyricsstatus.app.data.discord.DiscordGuildEmojis
import com.lyricsstatus.app.data.discord.DiscordStatusPusher
import com.lyricsstatus.app.data.model.AppSettings
import com.lyricsstatus.app.data.model.LyricLine
import com.lyricsstatus.app.data.model.TrackInfo
import com.lyricsstatus.app.data.repository.SettingsRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralSettingsScreen(
    settingsRepo: SettingsRepository,
    onNavigateToTokenFetcher: () -> Unit = {},
    onBack: () -> Unit
) {
    val settings by settingsRepo.settingsFlow.collectAsState(initial = com.lyricsstatus.app.data.model.AppSettings())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showResetDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & Sync", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Android Music Session Detection & Permissions
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Music Detection Permission",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Allows LyricsStatus to detect what's playing in Spotify, Apple Music, YT Music, etc. in real time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            context.startActivity(intent)
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Grant Notification Listener Access")
                    }
                }
            }

            // Notification Configuration
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Notification Settings",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Show Live Lyrics Notification", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = settings.showNotification,
                            onCheckedChange = { value ->
                                scope.launch {
                                    settingsRepo.updateSettings { it.copy(showNotification = value) }
                                }
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Include AI Translated Line", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = settings.showTranslatedLineInNotification,
                            onCheckedChange = { value ->
                                scope.launch {
                                    settingsRepo.updateSettings { it.copy(showTranslatedLineInNotification = value) }
                                }
                            }
                        )
                    }
                }
            }

            // Sync Timings & Offsets
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Synchronization Timing",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Offset: ${settings.sendTimeOffsetMs} ms",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Slider(
                        value = settings.sendTimeOffsetMs.toFloat(),
                        onValueChange = { value ->
                            scope.launch {
                                settingsRepo.updateSettings { it.copy(sendTimeOffsetMs = value.toLong()) }
                            }
                        },
                        valueRange = -2000f..3000f
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Automatic Latency Compensation", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Dynamically adjusts offset based on connection speed.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.enableAutoOffset,
                            onCheckedChange = { value ->
                                scope.launch {
                                    settingsRepo.updateSettings { it.copy(enableAutoOffset = value) }
                                }
                            }
                        )
                    }
                }
            }

            // Discord Status & Spotify Token Sync
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Discord & Spotify Token Integration",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Broadcast synchronized lyrics directly to your Discord profile status or fetch Spotify tokens.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Enable Discord Sync", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = settings.discordEnabled,
                            onCheckedChange = { value ->
                                scope.launch {
                                    settingsRepo.updateSettings { it.copy(discordEnabled = value) }
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onNavigateToTokenFetcher,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Rounded.Key, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Token Fetcher & Extractor Tool")
                    }

                    if (settings.discordEnabled) {
                        Spacer(modifier = Modifier.height(10.dp))
                        var tokenVisible by remember { mutableStateOf(false) }
                        OutlinedTextField(
                            value = settings.discordToken,
                            onValueChange = { value ->
                                scope.launch {
                                    settingsRepo.updateSettings { it.copy(discordToken = value) }
                                }
                            },
                            label = { Text("Discord User Token") },
                            singleLine = true,
                            visualTransformation = if (tokenVisible) {
                                androidx.compose.ui.text.input.VisualTransformation.None
                            } else {
                                PasswordVisualTransformation(mask = '•')
                            },
                            trailingIcon = {
                                IconButton(onClick = { tokenVisible = !tokenVisible }) {
                                    Icon(
                                        imageVector = if (tokenVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        contentDescription = if (tokenVisible) "Hide token" else "Show token"
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        DiscordStatusCustomizationSection(
                            settings = settings,
                            onUpdate = { transform ->
                                scope.launch { settingsRepo.updateSettings(transform) }
                            }
                        )
                    }
                }
            }

            // Reset to factory defaults
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Security, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Reset & Maintenance",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Restore every setting to its default value (offsets, translation, Discord config, templates and token will be cleared).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { showResetDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reset all settings to defaults")
                    }
                }
            }

            if (showResetDialog) {
                AlertDialog(
                    onDismissRequest = { showResetDialog = false },
                    title = { Text("Reset settings?", fontWeight = FontWeight.Bold) },
                    text = {
                        Text(
                            "All settings will return to their default values. This also clears your Discord token and custom template. Lyrics and translation caches are kept."
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    settingsRepo.updateSettings { com.lyricsstatus.app.data.model.AppSettings() }
                                }
                                showResetDialog = false
                            }
                        ) { Text("Reset", color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * Discord custom status personalization: custom emoji (unicode or Discord
 * custom/animated) and the status template editor with live preview,
 * quick-insert token chips and validation via [DiscordStatusPusher].
 */
@Composable
private fun DiscordStatusCustomizationSection(
    settings: AppSettings,
    onUpdate: (transform: (AppSettings) -> AppSettings) -> Unit
) {
    val emojiInput = settings.discordCustomEmoji
    // remember: evita re-parsear el emoji y la plantilla en cada recomposición
    val parsedEmoji = remember(emojiInput) { DiscordStatusPusher.parseEmoji(emojiInput) }
    val emojiLooksInvalid = emojiInput.trim().startsWith("<") && parsedEmoji?.id == null
    var showEmojiPicker by remember { mutableStateOf(false) }

    val previewLine = LyricLine(
        timestampMs = 65000L,
        text = "Is this the real life? Is this just fantasy?"
    )
    val previewTrack = TrackInfo(
        title = "Bohemian Rhapsody",
        artist = "Queen",
        album = "A Night at the Opera",
        durationMs = 354000L
    )
    val preview = remember(settings.discordStatusTemplate) {
        DiscordStatusPusher.formatStatusText(
            settings.discordStatusTemplate, previewLine, previewTrack
        )
    }

    val quickTokens = listOf(
        "{lyrics}", "{lyrics:uppercase}", "{lyrics:lowercase}",
        "{lyrics:cropped}", "{lyrics:letters_only}", "{timestamp}",
        "{song_name}", "{song_author}", "{song_album}"
    )

    // ── Custom emoji ────────────────────────────────────────────────────
    OutlinedTextField(
        value = emojiInput,
        onValueChange = { value ->
            onUpdate { s -> s.copy(discordCustomEmoji = value) }
        },
        label = { Text("Custom Status Emoji (optional)") },
        singleLine = true,
        isError = emojiLooksInvalid,
        trailingIcon = {
            IconButton(onClick = { showEmojiPicker = true }) {
                Icon(
                    Icons.Rounded.EmojiEmotions,
                    contentDescription = "Pick emoji",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        supportingText = {
            when {
                emojiLooksInvalid -> Text(
                    "Custom emoji must look like <:name:123456789> or <a:name:123456789>",
                    color = MaterialTheme.colorScheme.error
                )
                parsedEmoji?.id != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = "https://cdn.discordapp.com/emojis/${parsedEmoji.id}.png?size=64",
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("✓ Custom emoji \":${parsedEmoji.name}\" (ID ${parsedEmoji.id})")
                }
                parsedEmoji != null -> Text("✓ Unicode emoji ${parsedEmoji.name}")
                else -> Text("Unicode (🎶) or Discord custom emoji (<:name:id>, <a:name:id>)")
            }
        },
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(10.dp))

    // ── Template editor ─────────────────────────────────────────────────
    OutlinedTextField(
        value = settings.discordStatusTemplate,
        onValueChange = { value ->
            onUpdate { s -> s.copy(discordStatusTemplate = value) }
        },
        label = { Text("Status Template") },
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        supportingText = {
            Text(
                "Fields: {lyrics} {timestamp} {song_name} {song_author} {song_album} · " +
                    "transforms: :uppercase :lowercase :cropped :letters_only · " +
                    "legacy tokens ({lyrics_upper}, {song_name_cropped}, …) still supported"
            )
        },
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        quickTokens.forEachIndexed { index, token ->
            if (index > 0) Spacer(modifier = Modifier.width(6.dp))
            AssistChip(
                onClick = {
                    onUpdate { s ->
                        s.copy(
                            discordStatusTemplate = (s.discordStatusTemplate.trim() + " " + token).trim()
                        )
                    }
                },
                label = { Text(token, style = MaterialTheme.typography.labelSmall) }
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // ── Live preview ────────────────────────────────────────────────────
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (parsedEmoji?.id != null) {
                    AsyncImage(
                        model = "https://cdn.discordapp.com/emojis/${parsedEmoji.id}.png?size=64",
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                } else if (parsedEmoji?.name != null) {
                    Text("${parsedEmoji.name} ", style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    preview.ifBlank { "(empty status)" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (preview.isBlank()) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Live preview with sample line",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${preview.length}/${DiscordStatusPusher.MAX_STATUS_LENGTH}" +
                        if (preview.length >= DiscordStatusPusher.MAX_STATUS_LENGTH) " (truncated)" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (preview.length >= DiscordStatusPusher.MAX_STATUS_LENGTH) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(2.dp))
    TextButton(
        onClick = {
            onUpdate { s -> s.copy(discordStatusTemplate = DiscordStatusPusher.DEFAULT_STATUS_TEMPLATE) }
        }
    ) {
        Icon(
            Icons.Rounded.Refresh,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text("Reset to default template")
    }

    if (showEmojiPicker) {
        EmojiPickerDialog(
            token = settings.discordToken,
            onPick = { emoji ->
                onUpdate { s -> s.copy(discordCustomEmoji = emoji) }
                showEmojiPicker = false
            },
            onDismiss = { showEmojiPicker = false }
        )
    }
}

/**
 * Emoji picker dialog with two tabs:
 *  - Discord: the custom emojis of every mutual guild of the logged user,
 *    fetched live via [DiscordEmojiApi] (guilds -> emojis -> CDN images).
 *    Picking one stores it as `<:name:id>` / `<a:name:id>`.
 *  - Unicode: a curated grid of unicode emojis.
 */
@Composable
private fun EmojiPickerDialog(
    token: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val api = remember { DiscordEmojiApi() }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Discord, 1 = Unicode
    val guildGroups = remember { mutableStateListOf<DiscordGuildEmojis>() }
    var fetching by remember { mutableStateOf(false) }
    var fetchDone by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        // Instant open from the in-memory cache (5 min TTL): zero network,
        // zero re-render spike.
        DiscordEmojiApi.peekCachedGuildEmojis()?.let { cached ->
            guildGroups.addAll(cached)
            fetchDone = true
            return@LaunchedEffect
        }
        val cleanToken = token.trim().replace("\"", "")
        if (cleanToken.isBlank()) {
            loadError = "Add your Discord token first to browse your server emojis."
            fetchDone = true
        } else {
            fetching = true
            loadError = null
            // Progressive: each guild renders as soon as it arrives
            api.fetchGuildsWithEmojisProgressive(cleanToken) { group ->
                guildGroups.add(group)
            }.onFailure { e ->
                if (guildGroups.isEmpty()) {
                    loadError = e.message ?: "Failed to load server emojis"
                }
            }
            fetching = false
            fetchDone = true
            if (guildGroups.isNotEmpty()) {
                DiscordEmojiApi.storeGuildEmojisCache(guildGroups.toList())
            }
        }
    }

    val unicodeCategories = listOf(
        "Music" to listOf(
            "🎵", "🎶", "🎼", "🎤", "🎧", "🎷",
            "🎸", "🥁", "🎹", "🎺", "🎻", "🪩"
        ),
        "Vibes" to listOf(
            "✨", "💫", "⭐", "🌙", "🔥", "💜",
            "💙", "💚", "❤️", "🌈", "⚡", "🦋"
        ),
        "Fun" to listOf(
            "😎", "🥳", "😭", "🤯", "🫡", "🤠",
            "👀", "💯", "🌀", "💤", "🌸", "🫶"
        )
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick an emoji", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Discord") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Unicode") }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                when {
                    selectedTab == 1 -> Column(
                        modifier = Modifier
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        unicodeCategories.forEach { (label, emojis) ->
                            Text(
                                label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)
                            )
                            emojis.chunked(6).forEach { rowEmojis ->
                                Row {
                                    rowEmojis.forEach { emoji ->
                                        Text(
                                            emoji,
                                            fontSize = 24.sp,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { onPick(emoji) }
                                                .padding(6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    fetching && guildGroups.isEmpty() -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }

                    loadError != null && guildGroups.isEmpty() -> Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            loadError ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }

                    fetchDone && guildGroups.isEmpty() -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("No server emojis found") }

                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(6),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        guildGroups.forEach { group ->
                            item(
                                span = { GridItemSpan(maxLineSpan) },
                                key = "guild-${group.guild.id}"
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                ) {
                                    DiscordEmojiApi.guildIconUrl(group.guild)?.let { iconUrl ->
                                        AsyncImage(
                                            model = iconUrl,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Text(
                                        group.guild.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "(${group.emojis.size})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            items(
                                group.emojis,
                                key = { "e-${group.guild.id}-${it.id}" }
                            ) { emoji ->
                                AsyncImage(
                                    model = DiscordEmojiApi.emojiCdnUrl(emoji),
                                    contentDescription = ":${emoji.name}:",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .clickable { onPick(DiscordEmojiApi.toCustomEmojiFormat(emoji)) }
                                        .size(34.dp)
                                        .padding(2.dp)
                                )
                            }
                        }
                        if (fetching) {
                            item(
                                span = { GridItemSpan(maxLineSpan) },
                                key = "loading-more"
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Loading more servers…",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        dismissButton = {
            TextButton(onClick = { onPick("") }) { Text("Remove emoji") }
        }
    )
}
