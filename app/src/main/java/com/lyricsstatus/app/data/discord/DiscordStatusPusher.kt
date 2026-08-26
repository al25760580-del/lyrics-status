package com.lyricsstatus.app.data.discord

import android.util.Log
import com.lyricsstatus.app.data.model.AppSettings
import com.lyricsstatus.app.data.model.LyricLine
import com.lyricsstatus.app.data.model.LyricsLine
import com.lyricsstatus.app.data.model.PlaybackState
import com.lyricsstatus.app.data.model.TrackInfo
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
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Pushes the currently active lyric line to the user's Discord custom status
 * by PATCHing `https://discord.com/api/v9/users/@me/settings` (same mechanism
 * used by the reference implementation aldair402/lyrics-status).
 *
 * The status text is rendered from a user-editable template supporting
 * `{field}` tokens with optional case/crop transformations
 * (e.g. `{lyrics:uppercase}`) as well as the legacy placeholder names kept
 * for parity with the reference project (`{lyrics_upper}`, `{song_name}`,
 * `{timestamp}`, ...).
 */
class DiscordStatusPusher(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()
) {

    // ─────────────────────────────────────────────────────────────────────┐
    // Template engine types                                                │
    // ─────────────────────────────────────────────────────────────────────┘

    /** Data sources available to the status template. */
    enum class StatusField {
        LYRICS,
        TIMESTAMP,
        SONG_NAME,
        SONG_AUTHOR,
        SONG_ALBUM
    }

    /** Text transformations applicable to a template field. */
    enum class CaseTransformation {
        ORIGINAL,
        UPPERCASE,
        LOWERCASE,
        CROPPED,
        LETTERS_ONLY,
        UPPERCASE_LETTERS_ONLY,
        LOWERCASE_LETTERS_ONLY,
        UPPERCASE_CROPPED,
        LOWERCASE_CROPPED
    }

    /** A parsed template is a flat list of literal and field tokens. */
    sealed class StatusTemplateToken {
        data class Literal(val text: String) : StatusTemplateToken()

        /**
         * A `{field:casing}` token. Square brackets hugging the token are
         * absorbed as decoration (e.g. `[{timestamp}]` keeps its brackets but
         * still counts as a single field token).
         */
        data class Field(
            val field: StatusField,
            val casing: CaseTransformation = CaseTransformation.ORIGINAL,
            val prefix: String = "",
            val suffix: String = ""
        ) : StatusTemplateToken()
    }

    /** Parsed emoji ready to be attached to a Discord custom status. */
    data class DiscordEmoji(
        val id: String? = null,
        val name: String? = null
    )

    private var lastSentKey: Pair<String, Long>? = null

    // ─────────────────────────────────────────────────────────────────────┐
    // Push                                                                 │
    // ─────────────────────────────────────────────────────────────────────┘

    /**
     * Pushes the active lyric line to the Discord custom status.
     * Returns the request latency in milliseconds on success.
     */
    suspend fun pushLyricsStatus(
        state: PlaybackState,
        line: LyricsLine,
        settings: AppSettings
    ): Result<Long> = withContext(Dispatchers.IO) {
        val token = settings.discordToken.trim().replace("\"", "")
        if (!settings.discordEnabled || token.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Discord sync is disabled or token is empty")
            )
        }

        val key = Pair(state.songId, line.time)
        if (lastSentKey == key) {
            return@withContext Result.success(0L) // Already sent this line
        }

        val formattedText = formatStatusText(state, line, settings)
        val emoji = parseEmoji(settings.discordCustomEmoji)

        val payload = buildJsonObject {
            putJsonObject("custom_status") {
                put("text", formattedText)
                if (emoji?.id != null) {
                    put("emoji_id", emoji.id)
                }
                if (emoji?.name != null) {
                    put("emoji_name", emoji.name)
                }
                put("expires_at", getExpirationIso8601())
            }
        }

        val requestBody = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(DISCORD_SETTINGS_API)
            .header("Authorization", token)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
            )
            .patch(requestBody)
            .build()

        val startTime = System.currentTimeMillis()
        try {
            client.newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - startTime
                if (response.isSuccessful) {
                    updateAutoOffset(latency)
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
     * Clears the user's custom status on Discord when playback stops.
     */
    suspend fun clearStatus(settings: AppSettings): Result<Unit> = withContext(Dispatchers.IO) {
        val token = settings.discordToken.trim().replace("\"", "")
        if (!settings.discordEnabled || token.isBlank()) return@withContext Result.success(Unit)

        val payload = "{\"custom_status\":null}".toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(DISCORD_SETTINGS_API)
            .header("Authorization", token)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
            )
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

    // ─────────────────────────────────────────────────────────────────────┐
    // Status text rendering                                                │
    // ─────────────────────────────────────────────────────────────────────┘

    /**
     * Renders the status text for the running app pipeline, resolving the
     * translation preference and mapping playback models into the template
     * engine models.
     */
    fun formatStatusText(
        state: PlaybackState,
        line: LyricsLine,
        settings: AppSettings
    ): String {
        val displayText = if (settings.enableTranslation && !line.textTranslated.isNullOrBlank()) {
            line.textTranslated!!
        } else {
            line.text
        }.replace('♪', '🎶')

        val engineLine = LyricLine(
            timestampMs = line.time,
            text = displayText,
            translation = line.textTranslated
        )
        val track = TrackInfo(
            title = state.songName,
            artist = state.songAuthor,
            album = null,
            durationMs = state.songDuration
        )
        return formatStatusText(settings.discordStatusTemplate, engineLine, track)
    }

    fun reset() {
        lastSentKey = null
    }

    /**
     * Learned latency (EWMA) used by [com.lyricsstatus.app.service.PlaybackStateManager]
     * to compensate the send offset (`enableAutoOffset`).
     */
    fun getAverageLatency(): Long = autoOffsetMs

    private fun getExpirationIso8601(): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date(System.currentTimeMillis() + 60_000L))
    }

    companion object {
        const val DISCORD_SETTINGS_API = "https://discord.com/api/v9/users/@me/settings"
        private const val MAX_STATUS_LENGTH = 128
        private const val MAX_CROPPED_LENGTH = 40
        private const val TAG = "DiscordStatusPusher"

        /** Fallback template mirroring the reference project defaults. */
        const val DEFAULT_STATUS_TEMPLATE = "[{timestamp}] [{lyrics}]"

        /** EWMA smoothing factor: 70% history + 30% new latency sample. */
        private const val AUTO_OFFSET_SMOOTHING = 0.7

        private val CUSTOM_EMOJI_PATTERN = Regex("^<a?:([^:]+):(\\d+)>$")

        /**
         * Matches `{field}` / `{field:transform[:transform...]}` tokens and
         * absorbs square-bracket decoration hugging the token
         * (`[{timestamp}]` → one field token carrying the brackets).
         */
        private val TEMPLATE_TOKEN_PATTERN =
            Regex("(\\s*\\[\\s*)?\\{([A-Za-z_][A-Za-z0-9_]*(?::[A-Za-z_][A-Za-z0-9_]*)*)\\}(\\s*\\])?")

        /**
         * Legacy placeholder names kept for parity with the reference
         * project's advanced templates.
         */
        private val LEGACY_TOKENS: Map<String, Pair<StatusField, CaseTransformation>> = mapOf(
            "lyrics" to Pair(StatusField.LYRICS, CaseTransformation.ORIGINAL),
            "lyrics_upper" to Pair(StatusField.LYRICS, CaseTransformation.UPPERCASE),
            "lyrics_lower" to Pair(StatusField.LYRICS, CaseTransformation.LOWERCASE),
            "lyrics_letters_only" to Pair(StatusField.LYRICS, CaseTransformation.LETTERS_ONLY),
            "lyrics_upper_letters_only" to Pair(StatusField.LYRICS, CaseTransformation.UPPERCASE_LETTERS_ONLY),
            "lyrics_lower_letters_only" to Pair(StatusField.LYRICS, CaseTransformation.LOWERCASE_LETTERS_ONLY),
            "timestamp" to Pair(StatusField.TIMESTAMP, CaseTransformation.ORIGINAL),
            "song_name" to Pair(StatusField.SONG_NAME, CaseTransformation.ORIGINAL),
            "song_name_upper" to Pair(StatusField.SONG_NAME, CaseTransformation.UPPERCASE),
            "song_name_lower" to Pair(StatusField.SONG_NAME, CaseTransformation.LOWERCASE),
            "song_name_cropped" to Pair(StatusField.SONG_NAME, CaseTransformation.CROPPED),
            "song_name_upper_cropped" to Pair(StatusField.SONG_NAME, CaseTransformation.UPPERCASE_CROPPED),
            "song_name_lower_cropped" to Pair(StatusField.SONG_NAME, CaseTransformation.LOWERCASE_CROPPED),
            "song_author" to Pair(StatusField.SONG_AUTHOR, CaseTransformation.ORIGINAL),
            "song_author_upper" to Pair(StatusField.SONG_AUTHOR, CaseTransformation.UPPERCASE),
            "song_author_lower" to Pair(StatusField.SONG_AUTHOR, CaseTransformation.LOWERCASE)
        )

        // ── Auto-offset (EWMA) ────────────────────────────────────────────

        /**
         * Exponentially-weighted moving average of successful PATCH
         * latencies, in milliseconds. Used to push lines slightly early so
         * they appear on Discord right on beat.
         */
        var autoOffsetMs: Long = 0L

        fun updateAutoOffset(latencyMs: Long) {
            autoOffsetMs = (
                (AUTO_OFFSET_SMOOTHING * autoOffsetMs) +
                    ((1 - AUTO_OFFSET_SMOOTHING) * latencyMs)
                ).toLong()
        }

        // ── Template engine ───────────────────────────────────────────────

        /**
         * Splits a template into literal and field tokens.
         */
        fun parseTemplate(template: String): List<StatusTemplateToken> {
            val tokens = mutableListOf<StatusTemplateToken>()
            var cursor = 0

            for (match in TEMPLATE_TOKEN_PATTERN.findAll(template)) {
                if (match.range.first > cursor) {
                    tokens.add(StatusTemplateToken.Literal(template.substring(cursor, match.range.first)))
                }
                val parsed = resolveFieldToken(match.groupValues[2])
                if (parsed != null) {
                    tokens.add(parsed.copy(prefix = match.groupValues[1], suffix = match.groupValues[3]))
                } else {
                    // Unknown field: keep the raw text so users can spot the typo.
                    tokens.add(StatusTemplateToken.Literal(match.value))
                }
                cursor = match.range.last + 1
            }

            if (cursor < template.length) {
                tokens.add(StatusTemplateToken.Literal(template.substring(cursor)))
            }
            return tokens
        }

        /**
         * Renders a template against a lyric line and its track metadata,
         * truncated to Discord's 128-character custom status limit.
         */
        fun formatStatusText(template: String, line: LyricLine, track: TrackInfo): String {
            val effective = template.ifBlank { DEFAULT_STATUS_TEMPLATE }
            val rendered = parseTemplate(effective).joinToString("") { token ->
                when (token) {
                    is StatusTemplateToken.Literal -> token.text
                    is StatusTemplateToken.Field ->
                        token.prefix +
                            applyCasing(rawValue(token.field, line, track), token.casing) +
                            token.suffix
                }
            }
            return rendered.take(MAX_STATUS_LENGTH).trim()
        }

        /**
         * Parses an emoji spec: unicode emoji (`🎶`) or Discord custom emoji
         * (`<:name:id>` / `<a:name:id>`). Returns null for blank input.
         */
        fun parseEmoji(rawEmoji: String): DiscordEmoji? {
            val trimmed = rawEmoji.trim()
            if (trimmed.isEmpty()) return null
            val match = CUSTOM_EMOJI_PATTERN.find(trimmed) ?: return DiscordEmoji(name = trimmed)
            return DiscordEmoji(id = match.groupValues[2], name = match.groupValues[1])
        }

        /**
         * Formats a millisecond position as `mm:ss` (or `h:mm:ss` past the
         * hour mark), zero-padded.
         */
        fun formatTimestamp(millis: Long): String {
            val totalSeconds = millis / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return if (hours > 0) {
                String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.US, "%02d:%02d", minutes, seconds)
            }
        }

        private fun resolveFieldToken(raw: String): StatusTemplateToken.Field? {
            LEGACY_TOKENS[raw]?.let { (field, casing) ->
                return StatusTemplateToken.Field(field, casing)
            }

            val parts = raw.split(":")
            val field = when (parts.first()) {
                "lyrics" -> StatusField.LYRICS
                "timestamp" -> StatusField.TIMESTAMP
                "song_name" -> StatusField.SONG_NAME
                "song_author" -> StatusField.SONG_AUTHOR
                "song_album" -> StatusField.SONG_ALBUM
                else -> return null
            }

            var casing = CaseTransformation.ORIGINAL
            for (part in parts.drop(1)) {
                val next = when (part.lowercase(Locale.US)) {
                    "original" -> CaseTransformation.ORIGINAL
                    "uppercase", "upper" -> CaseTransformation.UPPERCASE
                    "lowercase", "lower" -> CaseTransformation.LOWERCASE
                    "cropped", "crop" -> CaseTransformation.CROPPED
                    "letters_only", "letters" -> CaseTransformation.LETTERS_ONLY
                    else -> return null
                }
                casing = combineCasing(casing, next)
            }
            return StatusTemplateToken.Field(field, casing)
        }

        private fun combineCasing(a: CaseTransformation, b: CaseTransformation): CaseTransformation {
            val upper = hasUpper(a) || hasUpper(b)
            val lower = hasLower(a) || hasLower(b)
            val cropped = a == CaseTransformation.CROPPED ||
                a == CaseTransformation.UPPERCASE_CROPPED ||
                a == CaseTransformation.LOWERCASE_CROPPED ||
                b == CaseTransformation.CROPPED ||
                b == CaseTransformation.UPPERCASE_CROPPED ||
                b == CaseTransformation.LOWERCASE_CROPPED
            val letters = a == CaseTransformation.LETTERS_ONLY ||
                a == CaseTransformation.UPPERCASE_LETTERS_ONLY ||
                a == CaseTransformation.LOWERCASE_LETTERS_ONLY ||
                b == CaseTransformation.LETTERS_ONLY ||
                b == CaseTransformation.UPPERCASE_LETTERS_ONLY ||
                b == CaseTransformation.LOWERCASE_LETTERS_ONLY

            return when {
                cropped && upper -> CaseTransformation.UPPERCASE_CROPPED
                cropped && lower -> CaseTransformation.LOWERCASE_CROPPED
                letters && upper -> CaseTransformation.UPPERCASE_LETTERS_ONLY
                letters && lower -> CaseTransformation.LOWERCASE_LETTERS_ONLY
                cropped -> CaseTransformation.CROPPED
                letters -> CaseTransformation.LETTERS_ONLY
                upper -> CaseTransformation.UPPERCASE
                lower -> CaseTransformation.LOWERCASE
                else -> CaseTransformation.ORIGINAL
            }
        }

        private fun hasUpper(c: CaseTransformation) =
            c == CaseTransformation.UPPERCASE ||
                c == CaseTransformation.UPPERCASE_LETTERS_ONLY ||
                c == CaseTransformation.UPPERCASE_CROPPED

        private fun hasLower(c: CaseTransformation) =
            c == CaseTransformation.LOWERCASE ||
                c == CaseTransformation.LOWERCASE_LETTERS_ONLY ||
                c == CaseTransformation.LOWERCASE_CROPPED

        private fun rawValue(field: StatusField, line: LyricLine, track: TrackInfo): String =
            when (field) {
                StatusField.LYRICS -> line.text
                StatusField.TIMESTAMP -> formatTimestamp(line.timestampMs)
                StatusField.SONG_NAME -> track.title
                StatusField.SONG_AUTHOR -> track.artist
                StatusField.SONG_ALBUM -> track.album.orEmpty()
            }

        private fun applyCasing(value: String, casing: CaseTransformation): String = when (casing) {
            CaseTransformation.ORIGINAL -> value
            CaseTransformation.UPPERCASE -> value.uppercase(Locale.US)
            CaseTransformation.LOWERCASE -> value.lowercase(Locale.US)
            CaseTransformation.CROPPED -> cropped(value)
            CaseTransformation.LETTERS_ONLY -> lettersOnly(value)
            CaseTransformation.UPPERCASE_LETTERS_ONLY -> lettersOnly(value.uppercase(Locale.US))
            CaseTransformation.LOWERCASE_LETTERS_ONLY -> lettersOnly(value.lowercase(Locale.US))
            CaseTransformation.UPPERCASE_CROPPED -> cropped(value.uppercase(Locale.US))
            CaseTransformation.LOWERCASE_CROPPED -> cropped(value.lowercase(Locale.US))
        }

        private fun cropped(value: String): String =
            if (value.length > MAX_CROPPED_LENGTH) {
                value.take(MAX_CROPPED_LENGTH - 3) + "..."
            } else {
                value
            }

        private fun lettersOnly(value: String): String =
            value.filter { it.isLetter() || it.isWhitespace() }
    }
}
