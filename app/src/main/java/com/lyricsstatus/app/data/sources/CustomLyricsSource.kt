package com.lyricsstatus.app.data.sources

import android.content.Context
import com.lyricsstatus.app.data.model.CustomLyricsMeta
import com.lyricsstatus.app.data.model.SongLyrics
import com.lyricsstatus.app.data.model.StoredCustomLyrics
import com.lyricsstatus.app.data.parser.LrcParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

class CustomLyricsSource(
    private val context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true; prettyPrint = true }
) : LyricsSource {

    override val name: String = "Custom"

    private val customDir: File
        get() = File(context.filesDir, "custom_lyrics").apply { mkdirs() }

    override suspend fun getLyrics(track: String, artist: String): Result<SongLyrics> =
        withContext(Dispatchers.IO) {
            val loaded = load(track, artist)
            if (loaded != null && loaded.isNotEmpty) {
                Result.success(loaded)
            } else {
                Result.failure(NoSuchElementException("No custom lyrics for $track - $artist"))
            }
        }

    suspend fun save(track: String, artist: String, raw: String): Result<CustomLyricsMeta> =
        withContext(Dispatchers.IO) {
            val cleanTrack = track.trim()
            val cleanArtist = artist.trim()
            if (cleanTrack.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("Track name cannot be empty"))
            }

            val lyrics = LrcParser.parse(raw, plainTextStepMs = 3000L)
            if (lyrics.isEmpty) {
                return@withContext Result.failure(IllegalArgumentException("No valid lyric lines found"))
            }

            val file = getEntryFile(cleanTrack, cleanArtist)
            val stored = StoredCustomLyrics(
                track = cleanTrack,
                artist = cleanArtist,
                raw = raw,
                lines = lyrics.lines
            )

            try {
                file.writeText(json.encodeToString(stored))
                Result.success(
                    CustomLyricsMeta(
                        track = cleanTrack,
                        artist = cleanArtist,
                        linesCount = lyrics.lines.size,
                        path = file.absolutePath
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun load(track: String, artist: String): SongLyrics? =
        withContext(Dispatchers.IO) {
            val file = getEntryFile(track, artist)
            if (file.exists()) {
                try {
                    val content = file.readText()
                    val stored = json.decodeFromString<StoredCustomLyrics>(content)
                    if (stored.lines.isNotEmpty()) {
                        return@withContext SongLyrics(stored.lines)
                    }
                } catch (ignored: Exception) { }
            }

            // Fuzzy lookup across stored custom lyrics
            val wantTrack = normalizeKeyPart(track)
            val wantArtist = normalizeKeyPart(artist)
            if (wantTrack.isEmpty()) return@withContext null

            val files = customDir.listFiles { f -> f.extension == "json" } ?: return@withContext null
            for (f in files) {
                try {
                    val content = f.readText()
                    val stored = json.decodeFromString<StoredCustomLyrics>(content)
                    val storedTrack = normalizeKeyPart(stored.track)
                    val storedArtist = normalizeKeyPart(stored.artist)

                    val trackMatches = storedTrack == wantTrack ||
                            storedTrack.contains(wantTrack) || wantTrack.contains(storedTrack)
                    val artistMatches = wantArtist.isEmpty() || storedArtist.isEmpty() ||
                            storedArtist == wantArtist || storedArtist.contains(wantArtist) || wantArtist.contains(storedArtist)

                    if (trackMatches && artistMatches && stored.lines.isNotEmpty()) {
                        return@withContext SongLyrics(stored.lines)
                    }
                } catch (ignored: Exception) { }
            }
            null
        }

    suspend fun loadRaw(track: String, artist: String): String? =
        withContext(Dispatchers.IO) {
            val file = getEntryFile(track, artist)
            if (file.exists()) {
                try {
                    val content = file.readText()
                    return@withContext json.decodeFromString<StoredCustomLyrics>(content).raw
                } catch (ignored: Exception) { }
            }
            null
        }

    suspend fun remove(track: String, artist: String): Boolean =
        withContext(Dispatchers.IO) {
            val file = getEntryFile(track, artist)
            if (file.exists()) file.delete() else false
        }

    suspend fun listAll(): List<CustomLyricsMeta> =
        withContext(Dispatchers.IO) {
            val list = mutableListOf<CustomLyricsMeta>()
            val files = customDir.listFiles { f -> f.extension == "json" } ?: return@withContext emptyList()
            for (f in files) {
                try {
                    val content = f.readText()
                    val stored = json.decodeFromString<StoredCustomLyrics>(content)
                    list.add(
                        CustomLyricsMeta(
                            track = stored.track,
                            artist = stored.artist,
                            linesCount = stored.lines.size,
                            path = f.absolutePath,
                            lastModified = f.lastModified()
                        )
                    )
                } catch (ignored: Exception) { }
            }
            list.sortedWith(
                compareBy<CustomLyricsMeta> { it.artist.lowercase() }
                    .thenBy { it.track.lowercase() }
            )
        }

    private fun getEntryFile(track: String, artist: String): File {
        val hash = sha256("$track\u0000$artist")
        return File(customDir, "$hash.json")
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun normalizeKeyPart(value: String): String {
        return value.map { if (it.isLetterOrDigit()) it.lowercaseChar() else ' ' }
            .joinToString("")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }
}
