package com.lyricsstatus.app.data.sources

import com.lyricsstatus.app.data.model.SongLyrics
import com.lyricsstatus.app.data.parser.LrcParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class LrcLibSource(
    private val client: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : LyricsSource {

    override val name: String = "LrcLib"

    override suspend fun getLyrics(track: String, artist: String): Result<SongLyrics> =
        withContext(Dispatchers.IO) {
            try {
                val url = "https://lrclib.net/api/get".toHttpUrl().newBuilder()
                    .addQueryParameter("track_name", track)
                    .addQueryParameter("artist_name", artist)
                    .build()

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "LyricsStatusApp/2.0 (Android; Kotlin)")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            IOException("LrcLib HTTP error: ${response.code}")
                        )
                    }

                    val bodyString = response.body?.string()
                        ?: return@withContext Result.failure(IOException("Empty response body"))

                    val jsonElement = json.parseToJsonElement(bodyString)
                    val syncedLyrics = jsonElement.jsonObject["syncedLyrics"]?.jsonPrimitive?.content

                    if (syncedLyrics.isNullOrBlank()) {
                        return@withContext Result.failure(
                            IllegalStateException("No synchronized lyrics found in LrcLib")
                        )
                    }

                    val lyrics = LrcParser.parse(syncedLyrics)
                    if (lyrics.isEmpty) {
                        return@withContext Result.failure(
                            IllegalStateException("Parsed lyrics contain 0 lines")
                        )
                    }

                    Result.success(lyrics)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
