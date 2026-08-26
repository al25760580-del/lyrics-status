package com.lyricsstatus.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class CustomLyricsMeta(
    val track: String,
    val artist: String,
    val linesCount: Int,
    val path: String,
    val lastModified: Long = System.currentTimeMillis()
)

@Serializable
data class StoredCustomLyrics(
    val track: String,
    val artist: String,
    val raw: String,
    val lines: List<LyricsLine>
)

@Serializable
data class TranslationCacheEntry(
    val language: String,
    val provider: String,
    val model: String,
    val lines: List<String>,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class AppSettings(
    // AI Translation Configuration
    val enableTranslation: Boolean = false,
    val targetLanguage: String = "es-MX",
    val aiProvider: AiProvider = AiProvider.GEMINI,
    val aiApiKey: String = "",
    val aiModel: String = "gemini-3.5-flash-lite",
    val customEndpointUrl: String = "http://10.0.2.2:11434/v1/chat/completions",
    val customModelName: String = "llama3.2",
    val customAuthHeader: String = "Bearer ",
    val customHeadersJson: String = "{}",
    val temperature: Float = 0.2f,

    // Timing & Offsets
    val sendTimeOffsetMs: Long = 500L,
    val enableAutoOffset: Boolean = true,
    val autoOffsetSampleSize: Int = 3,

    // Notification Settings
    val showNotification: Boolean = true,
    val showTranslatedLineInNotification: Boolean = true,
    val showNextLineInNotification: Boolean = true,
    val notificationHighPriority: Boolean = true,

    // Sources and detection
    val musicDetectionMode: String = "auto", // "auto", "notification", "spotify_api", "simulator"
    val preferredSources: List<String> = listOf("custom", "cache", "lrclib", "netease", "qqmusic"),

    // Discord Status Integration (Optional)
    val discordEnabled: Boolean = false,
    val discordToken: String = "",
    val discordCustomEmoji: String = "",
    val discordStatusTemplate: String = "[{timestamp}] [{lyrics}]"
)
