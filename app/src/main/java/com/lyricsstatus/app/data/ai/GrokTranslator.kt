package com.lyricsstatus.app.data.ai

import com.lyricsstatus.app.data.model.AppSettings
import okhttp3.OkHttpClient

class GrokTranslator(
    client: OkHttpClient
) : AiTranslator {

    private val openAiDelegate = OpenAiTranslator(
        client = client,
        customEndpoint = "https://api.x.ai/v1/chat/completions"
    )

    override suspend fun translate(
        lyricsText: String,
        targetLanguage: String,
        settings: AppSettings
    ): Result<List<String>> {
        val grokSettings = if (settings.aiModel.isBlank()) {
            settings.copy(aiModel = "grok-2-latest")
        } else {
            settings
        }
        return openAiDelegate.translate(lyricsText, targetLanguage, grokSettings)
    }
}
