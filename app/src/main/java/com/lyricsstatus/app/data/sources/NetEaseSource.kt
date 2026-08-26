package com.lyricsstatus.app.data.sources

import com.lyricsstatus.app.data.model.SongLyrics
import com.lyricsstatus.app.data.parser.LrcParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class NetEaseSource(
    private val client: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : LyricsSource {

    override val name: String = "NetEase Music"

    override suspend fun getLyrics(track: String, artist: String): Result<SongLyrics> =
        withContext(Dispatchers.IO) {
            try {
                val query = "$track-$artist"
                val searchUrl = "https://music.163.com/api/search/get".toHttpUrl().newBuilder()
                    .addQueryParameter("s", query)
                    .addQueryParameter("type", "1")
                    .addQueryParameter("offset", "0")
                    .addQueryParameter("sub", "false")
                    .addQueryParameter("limit", "5")
                    .build()

                val searchRequest = Request.Builder()
                    .url(searchUrl)
                    .post(FormBody.Builder().build())
                    .header("Referer", "https://music.163.com")
                    .header("Cookie", "appver=2.0.2")
                    .build()

                val songId: Long = client.newCall(searchRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(IOException("Search HTTP ${response.code}"))
                    }
                    val body = response.body?.string() ?: return@withContext Result.failure(IOException("Empty search response"))
                    val jsonTree = json.parseToJsonElement(body).jsonObject
                    val resultObj = jsonTree["result"]?.jsonObject
                        ?: return@withContext Result.failure(IllegalStateException("No result object"))
                    val songs = resultObj["songs"]?.jsonArray
                    if (songs.isNullOrEmpty()) {
                        return@withContext Result.failure(IllegalStateException("Song not found in NetEase"))
                    }
                    val firstSong = songs[0].jsonObject
                    firstSong["id"]?.jsonPrimitive?.long
                        ?: return@withContext Result.failure(IllegalStateException("Missing song id"))
                }

                val lyricUrl = "https://music.163.com/api/song/lyric?tv=-1&kv=-1&lv=-1&os=pc&id=$songId"
                val lyricRequest = Request.Builder()
                    .url(lyricUrl)
                    .post(FormBody.Builder().build())
                    .header("Referer", "https://music.163.com")
                    .header("Cookie", "appver=2.0.2")
                    .header("X-Real-IP", "202.96.0.0")
                    .build()

                client.newCall(lyricRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(IOException("Lyric HTTP ${response.code}"))
                    }
                    val body = response.body?.string() ?: return@withContext Result.failure(IOException("Empty lyric response"))
                    val jsonTree = json.parseToJsonElement(body).jsonObject
                    val lrcObj = jsonTree["lrc"]?.jsonObject
                    val rawLyric = lrcObj?.get("lyric")?.jsonPrimitive?.content

                    if (rawLyric.isNullOrBlank()) {
                        return@withContext Result.failure(IllegalStateException("Lyrics not found in NetEase"))
                    }

                    val lyrics = LrcParser.parse(rawLyric)
                    if (lyrics.isEmpty) {
                        return@withContext Result.failure(IllegalStateException("Empty lyrics content"))
                    }

                    Result.success(lyrics)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
