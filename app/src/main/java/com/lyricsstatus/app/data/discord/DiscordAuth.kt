package com.lyricsstatus.app.data.discord

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

@Serializable
data class DiscordLoginResponse(
    @SerialName("user_id") val userId: String? = null,
    val token: String? = null,
    @SerialName("login_instance_id") val loginInstanceId: String? = null,
    val ticket: String? = null,
    val mfa: Boolean = false,
    val totp: Boolean = false,
    val sms: Boolean = false,
    val backup: Boolean = false
)

@Serializable
data class DiscordMfaResponse(
    val token: String? = null
)

@Serializable
data class DiscordExperimentsResponse(
    val fingerprint: String? = null
)

sealed interface DiscordAuthResult {
    data class Success(val token: String, val userId: String? = null) : DiscordAuthResult
    data class MfaRequired(val ticket: String, val userId: String? = null, val totp: Boolean = true) : DiscordAuthResult
    data class Error(val message: String) : DiscordAuthResult
}

class DiscordAuth(
    private val client: OkHttpClient = OkHttpClient.Builder().build(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    companion object {
        const val DISCORD_API = "https://discord.com/api/v10"
    }

    private var cachedFingerprint: String? = null

    suspend fun fetchFingerprint(): String? = withContext(Dispatchers.IO) {
        if (cachedFingerprint != null) return@withContext cachedFingerprint
        val req = Request.Builder()
            .url("$DISCORD_API/experiments")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
            .build()
        try {
            client.newCall(req).execute().use { res ->
                val body = res.body?.string() ?: ""
                val exp = json.decodeFromString<DiscordExperimentsResponse>(body)
                cachedFingerprint = exp.fingerprint
                cachedFingerprint
            }
        } catch (ignored: Exception) {
            null
        }
    }

    /**
     * Performs standard Discord Login with email/phone and password.
     */
    suspend fun login(login: String, password: String): DiscordAuthResult = withContext(Dispatchers.IO) {
        val fingerprint = fetchFingerprint()
        val payload = buildJsonObject {
            put("login", login.trim())
            put("password", password)
            put("undelete", false)
        }

        val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val reqBuilder = Request.Builder()
            .url("$DISCORD_API/auth/login")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
            .header("Content-Type", "application/json")
            .post(body)

        if (!fingerprint.isNullOrBlank()) {
            reqBuilder.header("X-Fingerprint", fingerprint)
        }

        try {
            client.newCall(reqBuilder.build()).execute().use { res ->
                val resStr = res.body?.string() ?: ""
                if (!res.isSuccessful && res.code != 400 && res.code != 401) {
                    return@withContext DiscordAuthResult.Error("Discord login failed (HTTP ${res.code}): $resStr")
                }

                val loginResp = try {
                    json.decodeFromString<DiscordLoginResponse>(resStr)
                } catch (e: Exception) {
                    return@withContext DiscordAuthResult.Error("Failed to parse Discord response: $resStr")
                }

                when {
                    !loginResp.token.isNullOrBlank() -> DiscordAuthResult.Success(
                        token = loginResp.token,
                        userId = loginResp.userId
                    )
                    loginResp.mfa && !loginResp.ticket.isNullOrBlank() -> DiscordAuthResult.MfaRequired(
                        ticket = loginResp.ticket,
                        userId = loginResp.userId,
                        totp = loginResp.totp
                    )
                    else -> DiscordAuthResult.Error(
                        if (resStr.contains("INVALID_LOGIN")) "Invalid email or password." else "Login failed: $resStr"
                    )
                }
            }
        } catch (e: Exception) {
            DiscordAuthResult.Error(e.message ?: "Network error during Discord login")
        }
    }

    /**
     * Verifies 2FA / MFA code (TOTP, backup code, SMS).
     */
    suspend fun verifyMfa(ticket: String, code: String, mfaType: String = "totp"): DiscordAuthResult = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("ticket", ticket.trim())
            put("code", code.trim())
        }

        val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val req = Request.Builder()
            .url("$DISCORD_API/auth/mfa/$mfaType")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
            .header("Content-Type", "application/json")
            .post(body)
            .build()

        try {
            client.newCall(req).execute().use { res ->
                val resStr = res.body?.string() ?: ""
                if (!res.isSuccessful) {
                    return@withContext DiscordAuthResult.Error("MFA Verification Failed (HTTP ${res.code}): $resStr")
                }

                val mfaResp = json.decodeFromString<DiscordMfaResponse>(resStr)
                if (!mfaResp.token.isNullOrBlank()) {
                    DiscordAuthResult.Success(token = mfaResp.token)
                } else {
                    DiscordAuthResult.Error("MFA verification did not return token")
                }
            }
        } catch (e: Exception) {
            DiscordAuthResult.Error(e.message ?: "MFA verification failed")
        }
    }
}
