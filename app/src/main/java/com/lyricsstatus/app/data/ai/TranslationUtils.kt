package com.lyricsstatus.app.data.ai

object TranslationUtils {

    fun buildSystemPrompt(languageCode: String): String {
        val language = getLanguageDisplayName(languageCode)
        return "You are an expert lyrics translator. Translate the song lyrics provided by the user into $language.\n" +
                "STRICT RULES:\n" +
                "1. Return EXACTLY one translated output line for every input line, maintaining the same order.\n" +
                "2. Translate as sung: natural, rhythmic, singable phrasing suited for music rather than a formal literal translation.\n" +
                "3. Keep song titles, artist names, well-known brand names, and phonetic ad-libs (e.g., 'Oh', 'Yeah', 'Na na na') as-is when appropriate.\n" +
                "4. NEVER add line numbering, timestamps, markdown headers, explanations, or extra commentary.\n" +
                "5. Output ONLY the translated lyrics lines."
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
        val lines = cleaned.lines().map { it.trim() }

        if (lines.size != expectedCount) {
            // If line count mismatch, try filtering empty trailing lines or aligning
            if (lines.filter { it.isNotEmpty() }.size == expectedCount) {
                return Result.success(lines.filter { it.isNotEmpty() })
            }
            return Result.failure(
                IllegalStateException("AI line count mismatch: expected $expectedCount lines, received ${lines.size} lines")
            )
        }

        return Result.success(lines)
    }
}
