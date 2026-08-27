package com.lyricsstatus.app.data.ai

object TranslationUtils {

    fun buildSystemPrompt(languageCode: String): String {
        val language = getLanguageDisplayName(languageCode)
        return "You are an expert lyrics translator. Translate the song lyrics provided by the user into $language, regardless of the source language or writing system.\n" +
                "STRICT RULES:\n" +
                "1. Return EXACTLY one translated output line for every input line, maintaining the same order and the same total line count. Never merge or split lines.\n" +
                "2. Automatically detect the source language, even when written in a non-Latin script (Japanese, Chinese, Korean, Cyrillic, Greek, Arabic, Hebrew, Hindi, Thai, etc.). Translate from ANY script.\n" +
                "3. NEVER romanize and never transliterate: always write the translation in the native script of $language.\n" +
                "4. Translate as sung: natural, rhythmic, singable phrasing suited for music rather than a formal literal translation.\n" +
                "5. Keep song titles, artist names, well-known brand names, and phonetic ad-libs (e.g., 'Oh', 'Yeah', 'Na na na', 'ラララ') as-is when appropriate.\n" +
                "6. If a line is already in $language, is an instrumental marker (♪, ♫, 🎶, ・, ---, ***), or contains only punctuation, keep it unchanged in the same position.\n" +
                "7. NEVER add line numbering, timestamps, markdown headers, explanations, or extra commentary.\n" +
                "8. Output ONLY the translated lyrics lines, with no blank lines in between."
    }

    fun getLanguageDisplayName(code: String): String {
        val lower = code.lowercase()
        return when {
            lower == "pt-br" -> "Brazilian Portuguese"
            lower == "en-gb" -> "British English"
            lower == "es-mx" -> "Mexican Spanish"
            lower == "es-es" -> "European Spanish"
            lower == "zh-tw" || lower == "zh-hk" -> "Traditional Chinese"
            lower == "zh-cn" || lower == "zh" -> "Simplified Chinese"
            lower.startsWith("es") -> "Spanish"
            lower.startsWith("en") -> "English"
            lower.startsWith("pt") -> "Portuguese"
            lower.startsWith("fr") -> "French"
            lower.startsWith("de") -> "German"
            lower.startsWith("it") -> "Italian"
            lower.startsWith("ru") -> "Russian"
            lower.startsWith("ja") -> "Japanese"
            lower.startsWith("ko") -> "Korean"
            lower.startsWith("nl") -> "Dutch"
            lower.startsWith("pl") -> "Polish"
            lower.startsWith("tr") -> "Turkish"
            lower.startsWith("hi") -> "Hindi"
            lower.startsWith("ar") -> "Arabic"
            else -> code
        }
    }

    fun stripCodeFences(text: String): String {
        val trimmed = text.trim()
        if (!trimmed.startsWith("```") || !trimmed.endsWith("```") || trimmed.length < 6) {
            return trimmed
        }
        val inner = trimmed.substring(3, trimmed.length - 3).trim()
        val lines = inner.lines().toMutableList()
        if (lines.isNotEmpty()) {
            val first = lines.first().trim()
            val looksLikeTag = first.isEmpty() ||
                    (first.length <= 15 && first.all { it.isLetterOrDigit() || it == '-' || it == '_' })
            if (looksLikeTag) {
                lines.removeAt(0)
            }
        }
        return lines.joinToString("\n").trim()
    }

    fun parseAndValidateLines(
        rawOutput: String,
        expectedCount: Int
    ): Result<List<String>> {
        val cleaned = stripCodeFences(rawOutput)
        // Models often add leading/trailing blank lines or filler around the
        // lyrics: drop them before counting.
        val content = cleaned.lines()
            .dropWhile { it.isBlank() }
            .dropLastWhile { it.isBlank() }
        val lines = content.map { it.trim() }

        if (lines.size == expectedCount) {
            return Result.success(lines)
        }

        // Inputs never contain blank lines, so any blank line inside the AI
        // output is noise (common with non-Latin scripts): drop and retry.
        val nonEmpty = lines.filter { it.isNotEmpty() }
        if (nonEmpty.size == expectedCount) {
            return Result.success(nonEmpty)
        }

        return Result.failure(
            IllegalStateException(
                "AI line count mismatch: expected $expectedCount lines, received ${lines.size}. " +
                    "Try re-syncing the lyrics."
            )
        )
    }
}
