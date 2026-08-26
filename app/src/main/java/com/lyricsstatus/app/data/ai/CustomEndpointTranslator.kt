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

class CustomEndpointTranslator(
    private val client: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : AiTranslator {

    override suspend fun translate(
        lyricsText: String,
        targetLanguage: String,
        settings: AppSettings
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val endpointUrl = settings.customEndpointUrl.trim()
            if (endpointUrl.isEmpty()) {
                return@withContext Result.failure(
                    IllegalArgumentException("Custom endpoint URL is empty. Please configure it in AI Settings.")
                )
            }

            val model = settings.customModelName.ifBlank { "llama3.2" }
            val systemPrompt = TranslationUtils.buildSystemPrompt(targetLanguage)

            // OpenAI compatible payload format
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

            val requestBody = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val reqBuilder = Request.Builder()
                .url(endpointUrl)
                .post(requestBody)

            if (settings.aiApiKey.isNotBlank()) {
                val headerPrefix = settings.customAuthHeader.ifBlank { "Bearer " }
                reqBuilder.header("Authorization", "$headerPrefix${settings.aiApiKey.trim()}")
            }

            // Custom extra headers support
            if (settings.customHeadersJson.isNotBlank() && settings.customHeadersJson != "{}") {
                try {
                    val customHeaders = json.parseToJsonElement(settings.customHeadersJson).jsonObject
                    for ((k, v) in customHeaders) {
                        reqBuilder.header(k, v.jsonPrimitive.content)
                    }
                } catch (ignored: Exception) { }
            }

            client.newCall(reqBuilder.build()).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("Custom API error (HTTP ${response.code}): $body")
                    )
                }

                val jsonRoot = json.parseToJsonElement(body).jsonObject
                val choices = jsonRoot["choices"]?.jsonArray
                if (choices.isNullOrEmpty()) {
                    // Try parsing Ollama / vLLM alternate format
                    val responseText = jsonRoot["response"]?.jsonPrimitive?.content
                    if (!responseText.isNullOrBlank()) {
                        return@withContext TranslationUtils.parseAndValidateLines(responseText, lyricsText.lines().size)
                    }
                    return@withContext Result.failure(IllegalStateException("No valid output choices returned from Custom endpoint"))
                }

                val firstChoice = choices[0].jsonObject
                val message = firstChoice["message"]?.jsonObject
                val rawContent = message?.get("content")?.jsonPrimitive?.content
                    ?: return@withContext Result.failure(IllegalStateException("Empty message content from Custom endpoint"))

                TranslationUtils.parseAndValidateLines(rawContent, lyricsText.lines().size)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
