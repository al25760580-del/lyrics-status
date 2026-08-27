package com.lyricsstatus.app.data.discord

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Spotify player poller, mirroring the reference Rust implementation
 * (aldair402/lyrics-status, src/spotify.rs):
 *
 *  1. Fetches the Spotify `access_token` from the user's Discord
 *     connections (`GET /users/@me/connections`).
 *  2. Polls `GET https://api.spotify.com/v1/me/player` every 2 seconds,
 *     refreshing the connection token on 401.
 *  3. Emits a [DiscordPresenceTrack] with the real `progress_ms`/track id,
 *     or null when nothing is playing (204 / no item).
 *
 * This gives instant, reliable song-change detection (by track id) that
 * depends on no gateway presence events at all.
 */
class SpotifyPlayerPoller(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    companion object {
        private const val TAG = "SpotifyPoller"
        private const val DISCORD_CONNECTIONS = "https://discord.com/api/v10/users/@me/connections"
        private const val SPOTIFY_PLAYER = "https://api.spotify.com/v1/me/player"
        private const val POLL_INTERVAL_MS = 2000L
        private const val NO_CONNECTION_RETRY_MS = 10_000L

        /** Strips "(Remaster/Live/…)" decorations from track names, as Rust does. */
        private val TITLE_CLEANUP = Regex(" \\(.+\\)")
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null

    @Volatile
    private var accessToken: String = ""

    private val _track = MutableStateFlow<DiscordPresenceTrack?>(null)
    val track: StateFlow<DiscordPresenceTrack?> = _track.asStateFlow()

    fun start(discordToken: String) {
        stop()
        val cleanToken = discordToken.trim().replace("\"", "")
        if (cleanToken.isEmpty()) return
        pollJob = scope.launch { pollLoop(cleanToken) }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
        accessToken = ""
        _track.value = null
    }

    private suspend fun fetchAccessToken(discordToken: String): String? {
        val request = Request.Builder()
            .url(DISCORD_CONNECTIONS)
            .header("Authorization", discordToken)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        return try {
            client.newCall(request).execute().use { res ->
                if (!res.isSuccessful) return null
                val body = res.body?.string() ?: return null
                json.parseToJsonElement(body).jsonArray
                    .firstOrNull { conn ->
                        (conn as? JsonObject)?.get("type")?.jsonPrimitive?.contentOrNull == "spotify"
                    }?.jsonObject
                    ?.get("access_token")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch Spotify connection token: ${e.message}")
            null
        }
    }

    private fun playerRequest(bearer: String): okhttp3.Response? =
        try {
            client.newCall(
                Request.Builder()
                    .url(SPOTIFY_PLAYER)
                    .header("Authorization", "Bearer $bearer")
                    .header("User-Agent", USER_AGENT)
                    .get()
                    .build()
            ).execute()
        } catch (e: Exception) {
            Log.w(TAG, "Spotify player request failed: ${e.message}")
            null
        }

    private suspend fun pollLoop(discordToken: String) {
        // Plain suspend fun (no CoroutineScope receiver): check the current
        // coroutine's context for cancellation.
        while (currentCoroutineContext().isActive) {
            try {
                if (accessToken.isBlank()) {
                    val token = fetchAccessToken(discordToken)
                    if (token == null) {
                        // No Spotify connection linked: retry gently (Rust keeps
                        // polling; we back off to avoid hammering the API).
                        _track.value = null
                        delay(NO_CONNECTION_RETRY_MS)
                        continue
                    }
                    accessToken = token
                }

                val started = System.currentTimeMillis()
                var response = playerRequest(accessToken)
                if (response != null && response.code == 401) {
                    response.close()
                    accessToken = fetchAccessToken(discordToken) ?: ""
                    if (accessToken.isBlank()) {
                        _track.value = null
                        delay(NO_CONNECTION_RETRY_MS)
                        continue
                    }
                    response = playerRequest(accessToken)
                }

                if (response == null) {
                    delay(POLL_INTERVAL_MS)
                    continue
                }

                response.use { res ->
                    when {
                        res.code == 204 -> _track.value = null // nothing playing
                        !res.isSuccessful -> _track.value = null
                        else -> {
                            val body = res.body?.string() ?: ""
                            _track.value = parsePlayer(body, started)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Poll error: ${e.message}")
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    private fun parsePlayer(body: String, startedAt: Long): DiscordPresenceTrack? {
        return try {
            val root = json.parseToJsonElement(body).jsonObject
            val isPlaying = root["is_playing"]?.jsonPrimitive?.booleanOrNull ?: false
            val item = root["item"]?.jsonObject ?: return null
            val trackId = item["id"]?.jsonPrimitive?.contentOrNull ?: return null
            val rawName = item["name"]?.jsonPrimitive?.contentOrNull ?: return null
            val durationMs = item["duration_ms"]?.jsonPrimitive?.longOrNull ?: 0L
            val artist = item["artists"]?.jsonArray
                ?.firstOrNull()
                ?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull ?: ""

            DiscordPresenceTrack(
                id = "spotify:$trackId",
                title = TITLE_CLEANUP.replace(rawName, "").trim(),
                artist = artist,
                // Rust parity: progress + request elapsed time
                progressMs = (root["progress_ms"]?.jsonPrimitive?.longOrNull ?: 0L) +
                    (System.currentTimeMillis() - startedAt),
                durationMs = durationMs,
                isPlaying = isPlaying,
                appName = "Spotify"
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse Spotify player: ${e.message}")
            null
        }
    }
}
