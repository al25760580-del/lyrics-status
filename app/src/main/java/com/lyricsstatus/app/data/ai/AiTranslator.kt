package com.lyricsstatus.app.data.ai

import com.lyricsstatus.app.data.model.AppSettings

/**
 * Common interface for LLM translation services.
 */
interface AiTranslator {
    suspend fun translate(
        lyricsText: String,
        targetLanguage: String,
        settings: AppSettings
    ): Result<List<String>>
}
