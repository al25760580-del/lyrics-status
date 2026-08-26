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

class OpenAiTranslator(
    private val client: OkHttpClient,
    private val customEndpoint: String? = null,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : AiTranslator {

    override suspend fun translate(
        lyricsText: String,
        targetLanguage: String,
        settings: AppSettings
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val apiKey = settings.aiApiKey.trim()
            if (apiKey.isEmpty() && customEndpoint == null) {
                return@withContext Result.failure(IllegalArgumentException("OpenAI API Key is missing. Please set it in AI Settings."))
            }

            val model = settings.aiModel.ifBlank { "gpt-4o-mini" }
            val systemPrompt = TranslationUtils.buildSystemPrompt(targetLanguage)

            val payload = buildJsonObject {
                put("model", model)
                put("temperature", settings.temperature.toDouble())
                putJsonArray("messages") {
                    addJsonObject {
                        put("role", "system")
                        put("content", systemPrompt)
                    }
                    addJsonObject {
                        put("role", "user")
                        put("content", lyricsText)
                    }
                }
            }

            val url = customEndpoint ?: "https://api.openai.com/v1/chat/completions"
            val requestBody = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

            val reqBuilder = Request.Builder()
                .url(url)
                .post(requestBody)

            if (apiKey.isNotEmpty()) {
                reqBuilder.header("Authorization", "Bearer $apiKey")
            }

            client.newCall(reqBuilder.build()).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("OpenAI API error (HTTP ${response.code}): $body")
                    )
                }

                val jsonRoot = json.parseToJsonElement(body).jsonObject
                val choices = jsonRoot["choices"]?.jsonArray
                if (choices.isNullOrEmpty()) {
                    return@withContext Result.failure(IllegalStateException("No choices returned from OpenAI"))
                }

                val firstChoice = choices[0].jsonObject
                val message = firstChoice["message"]?.jsonObject
                val rawContent = message?.get("content")?.jsonPrimitive?.content
                    ?: return@withContext Result.failure(IllegalStateException("Empty message content"))

                TranslationUtils.parseAndValidateLines(rawContent, lyricsText.lines().size)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
