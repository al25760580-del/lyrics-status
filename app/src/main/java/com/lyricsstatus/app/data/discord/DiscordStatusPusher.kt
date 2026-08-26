package com.lyricsstatus.app.data.discord

import android.util.Log
import com.lyricsstatus.app.data.model.AppSettings
import com.lyricsstatus.app.data.model.LyricsLine
import com.lyricsstatus.app.data.model.PlaybackState
import com.lyricsstatus.app.data.parser.LrcParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class DiscordStatusPusher(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        const val DISCORD_SETTINGS_API = "https://discord.com/api/v9/users/@me/settings"
        private const val MAX_STATUS_LENGTH = 128
        private const val TAG = "DiscordStatusPusher"
        private val CUSTOM_EMOJI_PATTERN = Pattern.compile("^<a?:([^:]+):(\\d+)>$")
        private val CROPPED_REGEX = Regex("(?i)( ?- ?.+)|(\\([^)]*\\))")
    }

    private val latencySamples = ArrayDeque<Long>()
    private var lastSentKey: Pair<String, Long>? = null

    /**
     * Pushes the active lyric line to Discord custom status.
     */
    suspend fun pushLyricsStatus(
        state: PlaybackState,
        line: LyricsLine,
        settings: AppSettings
    ): Result<Long> = withContext(Dispatchers.IO) {
        val token = settings.discordToken.trim().replace("\"", "")
        if (!settings.discordEnabled || token.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Discord sync is disabled or token is empty"))
        }

        val key = Pair(state.songId, line.time)
        if (lastSentKey == key) {
            return@withContext Result.success(0L) // Already sent this line
        }

        val formattedText = formatStatusText(state, line, settings)
        val emojiPair = parseEmoji(settings.discordCustomEmoji)
        val expiresAt = getExpirationIso8601()

        val payload = buildJsonObject {
            putJsonObject("custom_status") {
                put("text", formattedText)
                if (emojiPair.first != null) {
                    put("emoji_id", emojiPair.first)
                }
                if (emojiPair.second != null) {
                    put("emoji_name", emojiPair.second)
                }
                put("expires_at", expiresAt)
            }
        }

        val requestBody = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(DISCORD_SETTINGS_API)
            .header("Authorization", token)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
            .patch(requestBody)
            .build()

        val startTime = System.currentTimeMillis()
        try {
            client.newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - startTime
                if (response.isSuccessful) {
                    recordLatency(latency, settings.autoOffsetSampleSize)
                    lastSentKey = key
                    Log.d(TAG, "Pushed Discord Status (${latency}ms): $formattedText")
                    Result.success(latency)
                } else {
                    val body = response.body?.string() ?: ""
                    Log.w(TAG, "Failed to push status (HTTP ${response.code}): $body")
                    Result.failure(Exception("HTTP ${response.code}: $body"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error updating Discord status", e)
            Result.failure(e)
        }
    }

    /**
     * Clears user's custom status on Discord when playback stops.
     */
    suspend fun clearStatus(settings: AppSettings): Result<Unit> = withContext(Dispatchers.IO) {
        val token = settings.discordToken.trim().replace("\"", "")
        if (!settings.discordEnabled || token.isBlank()) return@withContext Result.success(Unit)

        val payload = "{\"custom_status\":null}".toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(DISCORD_SETTINGS_API)
            .header("Authorization", token)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
            .patch(payload)
            .build()

        try {
            client.newCall(request).execute().use { res ->
                lastSentKey = null
                if (res.isSuccessful) {
                    Log.d(TAG, "Cleared Discord custom status")
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("HTTP ${res.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun formatStatusText(
        state: PlaybackState,
        line: LyricsLine,
        settings: AppSettings
    ): String {
        val timestamp = LrcParser.formatSeconds(line.time / 1000L)
        val displayText = if (settings.enableTranslation && !line.textTranslated.isNullOrBlank()) {
            line.textTranslated!!
        } else {
            line.text
        }.replace('♪', '🎶')

        val template = settings.discordStatusTemplate.ifBlank { "[{timestamp}] [{lyrics}]" }

        val replaced = template
            .replace("{lyrics}", displayText)
            .replace("{lyrics_upper}", displayText.uppercase())
            .replace("{lyrics_lower}", displayText.lowercase())
            .replace("{lyrics_letters_only}", lettersOnly(displayText))
            .replace("{lyrics_upper_letters_only}", lettersOnly(displayText.uppercase()))
            .replace("{lyrics_lower_letters_only}", lettersOnly(displayText.lowercase()))
            .replace("{timestamp}", timestamp)
            .replace("{song_name}", state.songName)
            .replace("{song_name_upper}", state.songName.uppercase())
            .replace("{song_name_lower}", state.songName.lowercase())
            .replace("{song_name_cropped}", cropped(state.songName))
            .replace("{song_name_upper_cropped}", cropped(state.songName.uppercase()))
            .replace("{song_name_lower_cropped}", cropped(state.songName.lowercase()))
            .replace("{song_author}", state.songAuthor)
            .replace("{song_author_upper}", state.songAuthor.uppercase())
            .replace("{song_author_lower}", state.songAuthor.lowercase())

        return replaced.take(MAX_STATUS_LENGTH).trim()
    }

    private fun lettersOnly(input: String): String {
        return input.filter { it.isLetter() || it.isWhitespace() }
    }

    private fun cropped(input: String): String {
        return CROPPED_REGEX.replace(input, "").trim()
    }

    private fun parseEmoji(rawEmoji: String): Pair<String?, String?> {
        val trimmed = rawEmoji.trim()
        if (trimmed.isEmpty()) return Pair(null, null)

        val matcher = CUSTOM_EMOJI_PATTERN.matcher(trimmed)
        if (matcher.find()) {
            val emojiName = matcher.group(1)
            val emojiId = matcher.group(2)
            return Pair(emojiId, emojiName)
        }

        return Pair(null, trimmed)
    }

    private fun recordLatency(latency: Long, sampleLimit: Int) {
        val limit = sampleLimit.coerceAtLeast(1)
        synchronized(latencySamples) {
            latencySamples.addFirst(latency)
            while (latencySamples.size > limit) {
                latencySamples.removeLast()
            }
        }
    }

    fun getAverageLatency(): Long {
        synchronized(latencySamples) {
            if (latencySamples.isEmpty()) return 0L
            return latencySamples.sum() / latencySamples.size
        }
    }

    private fun getExpirationIso8601(): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date(System.currentTimeMillis() + 60_000L))
    }

    fun reset() {
        lastSentKey = null
    }
}
