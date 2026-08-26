package com.lyricsstatus.app.data.discord

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

@Serializable
data class DiscordPresenceTrack(
    val id: String,
    val title: String,
    val artist: String,
    val progressMs: Long,
    val durationMs: Long,
    val isPlaying: Boolean,
    val appName: String,
    val timestamp: Long = System.currentTimeMillis()
)

class DiscordGatewayPresence(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    companion object {
        const val GATEWAY_URL = "wss://gateway.discord.gg/?v=10&encoding=json"
        private const val TAG = "DiscordGateway"
    }

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var isConnected = false
    private var selfUserId: String? = null
    private var lastSeq: Int? = null

    private val _currentTrack = MutableStateFlow<DiscordPresenceTrack?>(null)
    val currentTrack: StateFlow<DiscordPresenceTrack?> = _currentTrack.asStateFlow()

    private val _connectionStatus = MutableStateFlow("Disconnected")
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    fun connect(token: String) {
        val cleanToken = token.trim().replace("\"", "")
        if (cleanToken.isEmpty()) {
            _connectionStatus.value = "Token is empty"
            return
        }

        disconnect()
        _connectionStatus.value = "Connecting to Discord Gateway…"

        val request = Request.Builder()
            .url(GATEWAY_URL)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
                _connectionStatus.value = "Gateway Connected"
                Log.d(TAG, "Gateway socket opened")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleGatewayMessage(webSocket, text, cleanToken)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.w(TAG, "Gateway closing: $code $reason")
                _connectionStatus.value = "Gateway Closing: $reason"
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
                _connectionStatus.value = "Disconnected"
                stopHeartbeat()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
                _connectionStatus.value = "Connection Error: ${t.message}"
                Log.e(TAG, "Gateway error", t)
                stopHeartbeat()
            }
        })
    }

    private fun handleGatewayMessage(ws: WebSocket, text: String, token: String) {
        try {
            val root = json.parseToJsonElement(text).jsonObject
            val op = root["op"]?.jsonPrimitive?.intOrNull ?: return
            val s = root["s"]?.jsonPrimitive?.intOrNull
            if (s != null) lastSeq = s

            when (op) {
                10 -> { // HELLO
                    val d = root["d"]?.jsonObject
                    val heartbeatInterval = d?.get("heartbeat_interval")?.jsonPrimitive?.longOrNull ?: 41250L
                    startHeartbeat(ws, heartbeatInterval)
                    sendIdentify(ws, token)
                }
                11 -> { // HEARTBEAT ACK
                    // Heartbeat acknowledged
                }
                0 -> { // DISPATCH
                    val t = root["t"]?.jsonPrimitive?.content ?: ""
                    val d = root["d"]
                    handleDispatch(t, d)
                }
                7, 9 -> { // RECONNECT or INVALID SESSION
                    Log.w(TAG, "Gateway requested reconnect / invalid session: op $op")
                    sendIdentify(ws, token)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling message", e)
        }
    }

    private fun startHeartbeat(ws: WebSocket, intervalMs: Long) {
        stopHeartbeat()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(intervalMs)
                val payload = buildJsonObject {
                    put("op", 1)
                    put("d", lastSeq)
                }
                ws.send(payload.toString())
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun sendIdentify(ws: WebSocket, token: String) {
        val payload = buildJsonObject {
            put("op", 2)
            putJsonObject("d") {
                put("token", token)
                put("capabilities", 16381)
                putJsonObject("properties") {
                    put("os", "Android")
                    put("browser", "LyricsStatus")
                    put("device", "LyricsStatus Android")
                    put("system_locale", "en-US")
                }
                putJsonObject("presence") {
                    put("status", "unknown")
                    put("since", 0)
                    put("afk", false)
                }
                put("compress", false)
            }
        }
        ws.send(payload.toString())
        _connectionStatus.value = "Identified & Listening for Rich Presence"
    }

    private fun handleDispatch(event: String, data: JsonElement?) {
        if (data !is JsonObject) return
        when (event) {
            "READY" -> {
                selfUserId = data["user"]?.jsonObject?.get("id")?.jsonPrimitive?.content
                _connectionStatus.value = "Active (Monitoring Rich Presence)"
                val sessions = data["sessions"]?.jsonArray
                sessions?.forEach { sessionElem ->
                    val activities = sessionElem.jsonObject["activities"]?.jsonArray
                    if (activities != null) {
                        parseAndEmitActivities(activities)
                    }
                }
            }
            "PRESENCE_UPDATE" -> {
                val userId = data["user"]?.jsonObject?.get("id")?.jsonPrimitive?.content
                if (selfUserId == null || userId == selfUserId) {
                    val activities = data["activities"]?.jsonArray
                    if (activities != null && activities.isNotEmpty()) {
                        parseAndEmitActivities(activities)
                    } else {
                        _currentTrack.value = null
                    }
                }
            }
            "SESSIONS_REPLACE" -> {
                val array = data["sessions"]?.jsonArray ?: JsonArray(listOf(data))
                array.forEach { sessionElem ->
                    val activities = sessionElem.jsonObject["activities"]?.jsonArray
                    if (activities != null) {
                        parseAndEmitActivities(activities)
                    }
                }
            }
        }
    }

    private fun parseAndEmitActivities(activities: JsonArray) {
        var bestTrack: DiscordPresenceTrack? = null
        var highestScore = -1

        for (elem in activities) {
            val act = elem.jsonObject
            val type = act["type"]?.jsonPrimitive?.intOrNull ?: 0
            val name = act["name"]?.jsonPrimitive?.content ?: ""
            val details = act["details"]?.jsonPrimitive?.content
            val state = act["state"]?.jsonPrimitive?.content
            val syncId = act["sync_id"]?.jsonPrimitive?.content

            // Music activities: type 2 (Listening), type 0 (Playing), type 3 (Watching)
            if (type != 2 && type != 0 && type != 3) continue

            val rawTitle = details?.trim() ?: ""
            if (rawTitle.isBlank()) continue

            var cleanTitle = cleanParentheses(rawTitle)
            var cleanArtist = state?.let { cleanArtistName(it) } ?: ""

            // If state is empty or generic, check if details has "Artist - Title" format
            if (cleanArtist.isBlank() || cleanArtist.equals("listening to music", ignoreCase = true)) {
                if (rawTitle.contains(" - ")) {
                    val parts = rawTitle.split(" - ", limit = 2)
                    cleanArtist = cleanArtistName(parts[0])
                    cleanTitle = cleanParentheses(parts[1])
                }
            }

            val timestamps = act["timestamps"]?.jsonObject
            val start = timestamps?.get("start")?.jsonPrimitive?.longOrNull ?: 0L
            val end = timestamps?.get("end")?.jsonPrimitive?.longOrNull ?: 0L
            val now = System.currentTimeMillis()

            val (progressMs, durationMs, isPlaying) = when {
                start > 0 && end > start -> {
                    val dur = end - start
                    val prog = (now - start).coerceIn(0L, dur)
                    val playing = now <= (end + 5000L)
                    Triple(prog, dur, playing)
                }
                start > 0 -> {
                    val prog = (now - start).coerceAtLeast(0L)
                    Triple(prog, 0L, true)
                }
                else -> Triple(0L, 0L, true)
            }

            if (!isPlaying) continue

            // Score: Listening (type 2) > Playing (type 0)
            var score = if (type == 2) 100 else 20
            if (end > start) score += 10
            if (!syncId.isNullOrBlank()) score += 5

            if (score > highestScore) {
                highestScore = score
                bestTrack = DiscordPresenceTrack(
                    id = syncId ?: "$name:$cleanTitle:$cleanArtist",
                    title = cleanTitle,
                    artist = cleanArtist,
                    progressMs = progressMs,
                    durationMs = durationMs,
                    isPlaying = true,
                    appName = if (name.isNotBlank()) name else "Discord RPC"
                )
            }
        }

        if (bestTrack != null) {
            _currentTrack.value = bestTrack
        }
    }

    private fun cleanArtistName(raw: String): String {
        val withoutBy = raw.replace(Regex("(?i)^by\\s+"), "").trim()
        val parts = withoutBy.split(Regex("[·|;]"))
        return parts.firstOrNull()?.trim() ?: withoutBy
    }

    private fun cleanParentheses(raw: String): String {
        return raw.replace(Regex("\\s*\\([^)]*\\)"), "").trim()
    }

    fun disconnect() {
        stopHeartbeat()
        webSocket?.close(1000, "Normal Closure")
        webSocket = null
        isConnected = false
        _currentTrack.value = null
        _connectionStatus.value = "Disconnected"
    }
}
