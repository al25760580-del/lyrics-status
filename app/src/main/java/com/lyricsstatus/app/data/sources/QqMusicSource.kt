package com.lyricsstatus.app.data.sources

import android.util.Base64
import com.lyricsstatus.app.data.model.SongLyrics
import com.lyricsstatus.app.data.parser.LrcParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.nio.charset.StandardCharsets

class QqMusicSource(
    private val client: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : LyricsSource {

    override val name: String = "QQ Music"

    override suspend fun getLyrics(track: String, artist: String): Result<SongLyrics> =
        withContext(Dispatchers.IO) {
            try {
                val key = "$track-$artist"
                val searchUrl = "https://c.y.qq.com/splcloud/fcgi-bin/smartbox_new.fcg".toHttpUrl().newBuilder()
                    .addQueryParameter("inCharset", "utf-8")
                    .addQueryParameter("outCharset", "utf-8")
                    .addQueryParameter("key", key)
                    .build()

                val searchRequest = Request.Builder()
                    .url(searchUrl)
                    .header("Referer", "https://y.qq.com/portal/player.html")
                    .get()
                    .build()

                val songMid = client.newCall(searchRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(IOException("QQ search HTTP ${response.code}"))
                    }
                    val body = response.body?.string() ?: return@withContext Result.failure(IOException("Empty search response"))
                    val jsonTree = json.parseToJsonElement(body).jsonObject
                    val count = jsonTree["count"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                    if (count == 0L) {
                        return@withContext Result.failure(IllegalStateException("Song not found in QQMusic"))
                    }
                    val dataObj = jsonTree["data"]?.jsonObject
                    val songObj = dataObj?.get("song")?.jsonObject
                    val itemList = songObj?.get("itemlist")?.jsonArray
                    if (itemList.isNullOrEmpty()) {
                        return@withContext Result.failure(IllegalStateException("Empty item list in QQMusic"))
                    }
                    val firstItem = itemList[0].jsonObject
                    firstItem["mid"]?.jsonPrimitive?.content
                        ?: return@withContext Result.failure(IllegalStateException("Missing mid in QQMusic"))
                }

                val lyricUrl = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg".toHttpUrl().newBuilder()
                    .addQueryParameter("g_tk", "5381")
                    .addQueryParameter("format", "json")
                    .addQueryParameter("inCharset", "utf-8")
                    .addQueryParameter("outCharset", "utf-8")
                    .addQueryParameter("songmid", songMid)
                    .build()

                val lyricRequest = Request.Builder()
                    .url(lyricUrl)
                    .header("Referer", "https://y.qq.com/portal/player.html")
                    .get()
                    .build()

                client.newCall(lyricRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(IOException("QQ lyric HTTP ${response.code}"))
                    }
                    val body = response.body?.string() ?: return@withContext Result.failure(IOException("Empty lyric response"))
                    val jsonTree = json.parseToJsonElement(body).jsonObject
                    val base64Lyric = jsonTree["lyric"]?.jsonPrimitive?.content

                    if (base64Lyric.isNullOrBlank()) {
                        return@withContext Result.failure(IllegalStateException("Empty lyric payload in QQMusic"))
                    }

                    val decodedBytes = try {
                        Base64.decode(base64Lyric, Base64.DEFAULT)
                    } catch (e: Exception) {
                        java.util.Base64.getDecoder().decode(base64Lyric)
                    }
                    val decodedString = String(decodedBytes, StandardCharsets.UTF_8)
                    val unescaped = unescapeHtml(decodedString)

                    val lyrics = LrcParser.parse(unescaped)
                    if (lyrics.isEmpty) {
                        return@withContext Result.failure(IllegalStateException("Parsed QQ lyrics are empty"))
                    }

                    Result.success(lyrics)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun unescapeHtml(text: String): String {
        return text
            .replace("&apos;", "'")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#32;", " ")
            .replace("&#58;", ":")
    }
}
