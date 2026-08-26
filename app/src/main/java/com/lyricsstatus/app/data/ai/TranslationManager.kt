package com.lyricsstatus.app.data.ai

import android.content.Context
import com.lyricsstatus.app.data.model.AiProvider
import com.lyricsstatus.app.data.model.AppSettings
import com.lyricsstatus.app.data.model.SongLyrics
import com.lyricsstatus.app.data.model.TranslationCacheEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.File
import java.security.MessageDigest
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

    private val cacheDir: File
        get() = File(context.cacheDir, "lyrics_translations").apply { mkdirs() }

    /**
     * Translates the whole song lyrics and populates [LyricsLine.textTranslated]
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
        val fullLyricsText = lyrics.lines.joinToString("\n") { it.text }

        // 1. Check in-memory cache
        val cacheKey = buildCacheKey(trackName, artistName, targetLang, settings)
        inMemoryCache[cacheKey]?.let { cachedLines ->
            if (cachedLines.size == lyrics.lines.size) {
                applyTranslations(lyrics, cachedLines)
                return@withContext Result.success(lyrics)
            }
        }

        // 2. Check disk cache
        val diskFile = getDiskCacheFile(trackName, artistName, targetLang)
        if (diskFile.exists()) {
            try {
                val entry = json.decodeFromString<TranslationCacheEntry>(diskFile.readText())
                if (entry.lines.size == lyrics.lines.size) {
                    inMemoryCache[cacheKey] = entry.lines
                    applyTranslations(lyrics, entry.lines)
                    return@withContext Result.success(lyrics)
                }
            } catch (ignored: Exception) { }
        }

        // 3. Perform fresh AI call with mutex
        mutex.withLock {
            // Double check cache in mutex
            inMemoryCache[cacheKey]?.let { cachedLines ->
                if (cachedLines.size == lyrics.lines.size) {
                    applyTranslations(lyrics, cachedLines)
                    return@withContext Result.success(lyrics)
                }
            }

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

                    // Persist to disk cache
                    try {
                        val entry = TranslationCacheEntry(
                            language = targetLang,
                            provider = settings.aiProvider.name,
                            model = if (settings.aiProvider == AiProvider.CUSTOM) settings.customModelName else settings.aiModel,
                            lines = translatedLines
                        )
                        diskFile.writeText(json.encodeToString(entry))
                    } catch (ignored: Exception) { }

                    Result.success(lyrics)
                },
                onFailure = { error ->
                    Result.failure(error)
                }
            )
        }
    }

    private fun applyTranslations(lyrics: SongLyrics, translatedLines: List<String>) {
        lyrics.lines.zip(translatedLines).forEach { (line, translated) ->
            line.textTranslated = translated
        }
    }

    private fun buildCacheKey(
        track: String,
        artist: String,
        lang: String,
        settings: AppSettings
    ): String {
        val model = if (settings.aiProvider == AiProvider.CUSTOM) settings.customModelName else settings.aiModel
        return "${settings.aiProvider.name}_${model}_${lang}_${track.lowercase()}_${artist.lowercase()}"
    }

    private fun getDiskCacheFile(track: String, artist: String, lang: String): File {
        val sanitized = "${sanitize(track)}-${sanitize(artist)}-$lang"
        val hash = sha256("$track\u0000$artist\u0000$lang")
        val fileName = if (sanitized.length in 3..80) "$sanitized.json" else "$hash.json"
        return File(cacheDir, fileName)
    }

    private fun sanitize(input: String): String {
        return input.map { if (it.isLetterOrDigit() || it in "._+") it else '-' }
            .joinToString("")
            .trim('-')
            .take(40)
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun clearCache() {
        inMemoryCache.clear()
        cacheDir.deleteRecursively()
    }
}
