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
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class GeminiTranslator(
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
                return@withContext Result.failure(IllegalArgumentException("Gemini API Key is missing. Please set it in AI Settings."))
            }

            val model = settings.aiModel.ifBlank { "gemini-2.0-flash" }
            val systemPrompt = TranslationUtils.buildSystemPrompt(targetLanguage)

            val payload = buildJsonObject {
                putJsonObject("systemInstruction") {
                    putJsonArray("parts") {
                        addJsonObject { put("text", systemPrompt) }
                    }
                }
                putJsonArray("contents") {
                    addJsonObject {
                        put("role", "user")
                        putJsonArray("parts") {
                            addJsonObject { put("text", lyricsText) }
                        }
                    }
                }
                putJsonObject("generationConfig") {
                    put("temperature", settings.temperature.toDouble())
                    put("maxOutputTokens", 8192)
                }
            }

            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
            val requestBody = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(url)
                .header("x-goog-api-key", apiKey)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("Gemini API error (HTTP ${response.code}): $body")
                    )
                }

                val jsonRoot = json.parseToJsonElement(body).jsonObject
                val candidates = jsonRoot["candidates"]?.jsonArray
                if (candidates.isNullOrEmpty()) {
                    return@withContext Result.failure(IllegalStateException("No candidates returned from Gemini"))
                }

                val firstCandidate = candidates[0].jsonObject
                val content = firstCandidate["content"]?.jsonObject
                val parts = content?.get("parts")?.jsonArray
                if (parts.isNullOrEmpty()) {
                    return@withContext Result.failure(IllegalStateException("No content parts in Gemini response"))
                }

                val rawText = parts.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.content }.joinToString("")
                TranslationUtils.parseAndValidateLines(rawText, lyricsText.lines().size)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
