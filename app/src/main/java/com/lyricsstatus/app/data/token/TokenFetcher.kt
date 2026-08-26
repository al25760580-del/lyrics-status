package com.lyricsstatus.app.data.token

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

@Serializable
data class DiscordUserInfo(
    val id: String,
    val username: String,
    val globalName: String? = null,
    val avatar: String? = null
)

@Serializable
data class SpotifyConnectionInfo(
    val name: String,
    val id: String,
    val accessToken: String? = null
)

@Serializable
data class TokenFetchResult(
    val user: DiscordUserInfo,
    val spotifyConnection: SpotifyConnectionInfo? = null
)

class TokenFetcher(
    private val client: OkHttpClient = OkHttpClient.Builder().build(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) {

    companion object {
        const val DISCORD_ME_URL = "https://discord.com/api/v10/users/@me"
        const val DISCORD_CONNECTIONS_URL = "https://discord.com/api/v10/users/@me/connections"

        const val DISCORD_WEB_EXTRACT_SCRIPT = """(webpackChunkdiscord_app.push([[''],{},e=>{m=[];for(let c in e.c)m.push(e.c[c])}]),m).find(m=>m?.exports?.default?.getToken!==void 0).exports.default.getToken()"""
    }

    /**
     * Validates Discord token and retrieves user profile information.
     */
    suspend fun fetchDiscordUser(token: String): Result<DiscordUserInfo> = withContext(Dispatchers.IO) {
        val cleanToken = token.trim().replace("\"", "")
        if (cleanToken.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Token cannot be empty"))
        }

        val request = Request.Builder()
            .url(DISCORD_ME_URL)
            .header("Authorization", cleanToken)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("Discord API error (HTTP ${response.code}): $body")
                    )
                }

                val userObj = json.parseToJsonElement(body).jsonObject
                val id = userObj["id"]?.jsonPrimitive?.content ?: ""
                val username = userObj["username"]?.jsonPrimitive?.content ?: "Unknown"
                val globalName = userObj["global_name"]?.jsonPrimitive?.content
                val avatar = userObj["avatar"]?.jsonPrimitive?.content

                Result.success(
                    DiscordUserInfo(
                        id = id,
                        username = username,
                        globalName = globalName,
                        avatar = avatar
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetches connected Spotify access token using the user's Discord token.
     */
    suspend fun fetchSpotifyConnection(token: String): Result<SpotifyConnectionInfo?> = withContext(Dispatchers.IO) {
        val cleanToken = token.trim().replace("\"", "")
        if (cleanToken.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Token cannot be empty"))
        }

        val request = Request.Builder()
            .url(DISCORD_CONNECTIONS_URL)
            .header("Authorization", cleanToken)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("Failed to fetch connections (HTTP ${response.code})")
                    )
                }

                val connectionsArray = json.parseToJsonElement(body).jsonArray
                val spotifyObj = connectionsArray.map { it.jsonObject }
                    .find { it["type"]?.jsonPrimitive?.content == "spotify" }

                if (spotifyObj == null) {
                    return@withContext Result.success(null)
                }

                val name = spotifyObj["name"]?.jsonPrimitive?.content ?: "Spotify Account"
                val id = spotifyObj["id"]?.jsonPrimitive?.content ?: ""
                val accessToken = spotifyObj["access_token"]?.jsonPrimitive?.content

                Result.success(
                    SpotifyConnectionInfo(
                        name = name,
                        id = id,
                        accessToken = accessToken
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Complete check: validates user and queries connected Spotify account.
     */
    suspend fun validateAndFetchAll(token: String): Result<TokenFetchResult> = withContext(Dispatchers.IO) {
        val userRes = fetchDiscordUser(token)
        if (userRes.isFailure) {
            return@withContext Result.failure(userRes.exceptionOrNull() ?: Exception("User check failed"))
        }

        val user = userRes.getOrThrow()
        val spotifyRes = fetchSpotifyConnection(token)
        val spotifyInfo = spotifyRes.getOrNull()

        Result.success(TokenFetchResult(user = user, spotifyConnection = spotifyInfo))
    }
}
