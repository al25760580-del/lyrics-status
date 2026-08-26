package com.lyricsstatus.app.data.ai

import com.lyricsstatus.app.data.model.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class ClaudeTranslator(
    private val client: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : AiTranslator {

    override suspend fun translate(
        lyricsText: String,
        targetLanguage: String,
        settings: AppSettings
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val apiKey = settings.aiApiKey.trim()
            if (apiKey.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("Anthropic API Key is missing. Please set it in AI Settings."))
            }

            val model = settings.aiModel.ifBlank { "claude-3-5-haiku-20241022" }
            val systemPrompt = TranslationUtils.buildSystemPrompt(targetLanguage)

            val payload = buildJsonObject {
                put("model", model)
                put("max_tokens", 4096)
                put("system", systemPrompt)
                putJsonArray("messages") {
                    addJsonObject {
                        put("role", "user")
                        put("content", lyricsText)
                    }
                }
            }

            val url = "https://api.anthropic.com/v1/messages"
            val requestBody = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(url)
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("Claude API error (HTTP ${response.code}): $body")
                    )
                }

                val jsonRoot = json.parseToJsonElement(body).jsonObject
                val content = jsonRoot["content"]?.jsonArray
                if (content.isNullOrEmpty()) {
                    return@withContext Result.failure(IllegalStateException("Empty content returned from Claude"))
                }

                val rawText = content.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.content }.joinToString("")
                TranslationUtils.parseAndValidateLines(rawText, lyricsText.lines().size)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
