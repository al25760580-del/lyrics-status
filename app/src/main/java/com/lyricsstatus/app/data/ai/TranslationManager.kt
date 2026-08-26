package com.lyricsstatus.app.data.ai

import android.content.Context
import com.lyricsstatus.app.data.model.AiProvider
import com.lyricsstatus.app.data.model.AppSettings
import com.lyricsstatus.app.data.model.LyricsLine
import com.lyricsstatus.app.data.model.SongLyrics
import com.lyricsstatus.app.data.parser.LrcParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class TranslationManager(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true; prettyPrint = true }
) {
    private val inMemoryCache = ConcurrentHashMap<String, List<String>>()
    private val mutex = Mutex()

    private val geminiTranslator = GeminiTranslator(client, json)
    private val openAiTranslator = OpenAiTranslator(client, null, json)
    private val claudeTranslator = ClaudeTranslator(client, json)
    private val grokTranslator = GrokTranslator(client)
    private val customTranslator = CustomEndpointTranslator(client, json)

    /**
     * Persistent cache for translated lyrics. Each translation is stored as
     * an LRC sidecar named after the original lyrics file plus the language:
     *
     *   `{FileNameOriginal}-{lang}.lrc`  (e.g. `Song-Artist-es-MX.lrc`)
     *
     * The base name matches the original raw/custom lyrics cache file
     * (`{track}-{artist}.json` -> `{track}-{artist}-{lang}.lrc`), so a
     * translated song is only ever requested to the AI once per language.
     */
    private val cacheDir: File
        get() = File(context.filesDir, "lyrics_translations").apply { mkdirs() }

    /**
     * Translates the whole song lyrics and populates [LyricsLine.textTranslated].
     */
    suspend fun translateSongLyrics(
        lyrics: SongLyrics,
        trackName: String,
        artistName: String,
        settings: AppSettings
    ): Result<SongLyrics> = withContext(Dispatchers.IO) {
        if (lyrics.isEmpty) {
            return@withContext Result.success(lyrics)
        }

        if (!settings.enableTranslation) {
            return@withContext Result.success(lyrics)
        }

        val targetLang = settings.targetLanguage.ifBlank { "es-MX" }

        val originalBase = buildOriginalBaseName(trackName, artistName)
        val cacheKey = "${originalBase.lowercase(Locale.US)}_$targetLang"
        val cacheFile = getLrcCacheFile(originalBase, targetLang)

        // 1. Check in-memory cache
        inMemoryCache[cacheKey]?.let { cachedLines ->
            if (cachedLines.size == lyrics.lines.size) {
                applyTranslations(lyrics, cachedLines)
                return@withContext Result.success(lyrics)
            }
        }

        // 2. Check LRC sidecar cache ({FileNameOriginal}-{lang}.lrc)
        readCachedTranslations(cacheFile, lyrics)?.let { cachedLines ->
            inMemoryCache[cacheKey] = cachedLines
            applyTranslations(lyrics, cachedLines)
            return@withContext Result.success(lyrics)
        }

        // 3. Perform fresh AI call with mutex
        mutex.withLock {
            // Double check both caches inside the mutex
            inMemoryCache[cacheKey]?.let { cachedLines ->
                if (cachedLines.size == lyrics.lines.size) {
                    applyTranslations(lyrics, cachedLines)
                    return@withContext Result.success(lyrics)
                }
            }
            readCachedTranslations(cacheFile, lyrics)?.let { cachedLines ->
                inMemoryCache[cacheKey] = cachedLines
                applyTranslations(lyrics, cachedLines)
                return@withContext Result.success(lyrics)
            }

            val fullLyricsText = lyrics.lines.joinToString("\n") { it.text }
            val translator = when (settings.aiProvider) {
                AiProvider.CHATGPT -> openAiTranslator
                AiProvider.GEMINI -> geminiTranslator
                AiProvider.CLAUDE -> claudeTranslator
                AiProvider.GROK -> grokTranslator
                AiProvider.CUSTOM -> customTranslator
            }

            val result = translator.translate(
                lyricsText = fullLyricsText,
                targetLanguage = targetLang,
                settings = settings
            )

            result.fold(
                onSuccess = { translatedLines ->
                    inMemoryCache[cacheKey] = translatedLines
                    applyTranslations(lyrics, translatedLines)

                    // Persist as {FileNameOriginal}-{lang}.lrc sidecar
                    try {
                        cacheFile.writeText(
                            buildTranslationLrc(lyrics, translatedLines, targetLang, settings)
                        )
                    } catch (ignored: Exception) { }

                    Result.success(lyrics)
                },
                onFailure = { error ->
                    Result.failure(error)
                }
            )
        }
    }

    /**
     * Reads the `{FileNameOriginal}-{lang}.lrc` sidecar and matches it back
     * to the current lyrics: by line count first, then by exact timestamps.
     */
    private fun readCachedTranslations(file: File, lyrics: SongLyrics): List<String>? {
        if (!file.exists()) return null
        return try {
            val cached = LrcParser.parse(file.readText())
            when {
                cached.lines.size == lyrics.lines.size -> cached.lines.map { it.text }
                cached.lines.isNotEmpty() -> {
                    val byTime = cached.lines.associateBy { it.time }
                    val mapped = lyrics.lines.map { byTime[it.time]?.text }
                    if (mapped.all { it != null }) mapped.map { it!! } else null
                }
                else -> null
            }
        } catch (ignored: Exception) {
            null
        }
    }

    /**
     * Serializes a translation as a standalone synchronized LRC file with
     * `#` metadata headers (ignored by [LrcParser]).
     */
    private fun buildTranslationLrc(
        lyrics: SongLyrics,
        translated: List<String>,
        lang: String,
        settings: AppSettings
    ): String {
        val model = if (settings.aiProvider == AiProvider.CUSTOM) {
            settings.customModelName
        } else {
            settings.aiModel
        }
        val sb = StringBuilder()
        sb.append("# LyricsStatus translated lyrics cache\n")
        sb.append("# lang=").append(lang).append('\n')
        sb.append("# provider=").append(settings.aiProvider.name).append('\n')
        sb.append("# model=").append(model).append('\n')
        lyrics.lines.zip(translated).forEach { (line, text) ->
            sb.append('[')
                .append(formatLrcTimestamp(line.time))
                .append(']')
                .append(text.replace("\n", " "))
                .append('\n')
        }
        return sb.toString()
    }

    /** `[mm:ss.cc]` (centiseconds, matching what [LrcParser] reads back). */
    private fun formatLrcTimestamp(ms: Long): String {
        val centis = ms / 10
        return String.format(
            Locale.US,
            "%02d:%02d.%02d",
            centis / 6000,
            (centis % 6000) / 100,
            centis % 100
        )
    }

    /** `Song-Artist`: readable base matching the original lyrics cache file. */
    private fun buildOriginalBaseName(track: String, artist: String): String {
        val base = "${sanitize(track)}-${sanitize(artist)}".trim('-')
        return base.ifBlank { "lyrics" }.take(80)
    }

    private fun getLrcCacheFile(base: String, lang: String): File =
        File(cacheDir, "$base-$lang.lrc")

    private fun applyTranslations(lyrics: SongLyrics, translatedLines: List<String>) {
        lyrics.lines.zip(translatedLines).forEach { (line, translated) ->
            line.textTranslated = translated
        }
    }

    private fun sanitize(input: String): String {
        return input.map { if (it.isLetterOrDigit() || it in "._+") it else '-' }
            .joinToString("")
            .trim('-')
            .take(40)
    }

    fun clearCache() {
        inMemoryCache.clear()
        cacheDir.deleteRecursively()
    }
}
