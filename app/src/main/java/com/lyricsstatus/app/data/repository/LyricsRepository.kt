package com.lyricsstatus.app.data.repository

import android.content.Context
import com.lyricsstatus.app.data.ai.TranslationManager
import com.lyricsstatus.app.data.model.AppSettings
import com.lyricsstatus.app.data.model.LyricsLine
import com.lyricsstatus.app.data.model.SongLyrics
import com.lyricsstatus.app.data.sources.CustomLyricsSource
import com.lyricsstatus.app.data.sources.LrcLibSource
import com.lyricsstatus.app.data.sources.LyricsSource
import com.lyricsstatus.app.data.sources.NetEaseSource
import com.lyricsstatus.app.data.sources.QqMusicSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

@Serializable
private data class RawLyricsCacheEntry(
    val appName: String,
    val lines: List<LyricsLine>
)

class LyricsRepository(
    private val context: Context,
    val customLyricsSource: CustomLyricsSource = CustomLyricsSource(context),
    val translationManager: TranslationManager = TranslationManager(context),
    private val client: OkHttpClient = OkHttpClient.Builder().build(),
    private val json: Json = Json { ignoreUnknownKeys = true; prettyPrint = true }
) {
    private val onlineSources: List<LyricsSource> = listOf(
        LrcLibSource(client, json),
        NetEaseSource(client, json),
        QqMusicSource(client, json)
    )

    private val rawLyricsCacheDir: File
        get() = File(context.cacheDir, "raw_lyrics").apply { mkdirs() }

    private val memoryLyricsCache = ConcurrentHashMap<String, Pair<SongLyrics, String>>()

    suspend fun fetchLyrics(
        track: String,
        artist: String,
        settings: AppSettings
    ): Pair<SongLyrics, String>? = withContext(Dispatchers.IO) {
        val cleanTrack = track.trim()
        val cleanArtist = artist.trim()
        if (cleanTrack.isEmpty()) return@withContext null

        val memoryKey = "${cleanTrack.lowercase()}_${cleanArtist.lowercase()}"

        // 1. Custom User Overrides ALWAYS Win
        val custom = customLyricsSource.load(cleanTrack, cleanArtist)
        if (custom != null && custom.isNotEmpty) {
            val processed = custom.copy(lines = custom.lines.map { it.copy() })
            if (settings.enableTranslation) {
                translationManager.translateSongLyrics(processed, cleanTrack, cleanArtist, settings)
            }
            return@withContext Pair(processed, "Custom")
        }

        // 2. Check Memory Cache
        memoryLyricsCache[memoryKey]?.let { (cachedLyrics, sourceLabel) ->
            val processed = cachedLyrics.copy(lines = cachedLyrics.lines.map { it.copy() })
            if (settings.enableTranslation) {
                translationManager.translateSongLyrics(processed, cleanTrack, cleanArtist, settings)
            }
            return@withContext Pair(processed, sourceLabel)
        }

        // 3. Check Disk Cache
        val cacheFile = getDiskCacheFile(cleanTrack, cleanArtist)
        if (cacheFile.exists()) {
            try {
                val cached = json.decodeFromString<RawLyricsCacheEntry>(cacheFile.readText())
                if (cached.lines.isNotEmpty()) {
                    val songLyrics = SongLyrics(cached.lines)
                    val label = "Cache (${cached.appName})"
                    memoryLyricsCache[memoryKey] = Pair(songLyrics, label)

                    val processed = songLyrics.copy(lines = songLyrics.lines.map { it.copy() })
                    if (settings.enableTranslation) {
                        translationManager.translateSongLyrics(processed, cleanTrack, cleanArtist, settings)
                    }
                    return@withContext Pair(processed, label)
                }
            } catch (ignored: Exception) { }
        }

        // 4. Query Online Sources Sequentially
        for (source in onlineSources) {
            val result = source.getLyrics(cleanTrack, cleanArtist)
            if (result.isSuccess) {
                val lyrics = result.getOrNull()
                if (lyrics != null && lyrics.isNotEmpty) {
                    // Write to disk cache
                    try {
                        val entry = RawLyricsCacheEntry(
                            appName = source.name,
                            lines = lyrics.lines
                        )
                        cacheFile.writeText(json.encodeToString(entry))
                    } catch (ignored: Exception) { }

                    val label = source.name
                    memoryLyricsCache[memoryKey] = Pair(lyrics, label)

                    val processed = lyrics.copy(lines = lyrics.lines.map { it.copy() })
                    if (settings.enableTranslation) {
                        translationManager.translateSongLyrics(processed, cleanTrack, cleanArtist, settings)
                    }
                    return@withContext Pair(processed, label)
                }
            }
        }

        null
    }

    suspend fun invalidateCache(track: String, artist: String) = withContext(Dispatchers.IO) {
        val memoryKey = "${track.trim().lowercase()}_${artist.trim().lowercase()}"
        memoryLyricsCache.remove(memoryKey)
        val file = getDiskCacheFile(track, artist)
        if (file.exists()) file.delete()
    }

    private fun getDiskCacheFile(track: String, artist: String): File {
        val sanitized = "${sanitize(track)}-${sanitize(artist)}"
        val hash = sha256("$track\u0000$artist")
        val fileName = if (sanitized.length in 3..80) "$sanitized.json" else "$hash.json"
        return File(rawLyricsCacheDir, fileName)
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
}
