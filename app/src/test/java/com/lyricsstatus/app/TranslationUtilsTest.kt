package com.lyricsstatus.app

import com.lyricsstatus.app.data.ai.TranslationUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationUtilsTest {

    @Test
    fun testStripCodeFences() {
        val markdown = "```json\nline 1\nline 2\n```"
        val stripped = TranslationUtils.stripCodeFences(markdown)
        assertEquals("line 1\nline 2", stripped)

        val plain = "line 1\nline 2"
        assertEquals("line 1\nline 2", TranslationUtils.stripCodeFences(plain))
    }

    @Test
    fun testLanguageNames() {
        assertEquals("Mexican Spanish", TranslationUtils.getLanguageDisplayName("es-MX"))
        assertEquals("Brazilian Portuguese", TranslationUtils.getLanguageDisplayName("pt-BR"))
        assertEquals("Japanese", TranslationUtils.getLanguageDisplayName("ja-JP"))
        assertEquals("French", TranslationUtils.getLanguageDisplayName("fr-FR"))
    }

    @Test
    fun testParseAndValidateLines() {
        val raw = "Hola mundo\nEsta es una canción\nLínea final"
        val result = TranslationUtils.parseAndValidateLines(raw, 3)
        assertTrue(result.isSuccess)
        assertEquals(3, result.getOrNull()?.size)
        assertEquals("Hola mundo", result.getOrNull()?.get(0))
    }

    @Test
    fun testSystemPromptIsScriptAware() {
        val prompt = TranslationUtils.buildSystemPrompt("es-MX")
        assertTrue(prompt.contains("non-Latin script"))
        assertTrue(prompt.contains("Japanese"))
        assertTrue(prompt.contains("romanize"))
        assertTrue(prompt.contains("Never merge or split lines"))
    }

    @Test
    fun testParseAndValidateLinesTrimsOuterBlanks() {
        val raw = "\n\n\nこんにちは\nさようなら\n\n"
        val result = TranslationUtils.parseAndValidateLines(raw, 2)
        assertTrue(result.isSuccess)
        assertEquals(listOf("こんにちは", "さようなら"), result.getOrNull())
    }

    @Test
    fun testParseAndValidateLinesDropsInnerBlanks() {
        val raw = "Привет мир\n   \nДо свидания"
        val result = TranslationUtils.parseAndValidateLines(raw, 2)
        assertTrue(result.isSuccess)
        assertEquals(listOf("Привет мир", "До свидания"), result.getOrNull())
    }

    @Test
    fun testParseAndValidateLinesCyrillicExactCount() {
        val raw = "Первый стих\nВторой стих\nТретий стих"
        val result = TranslationUtils.parseAndValidateLines(raw, 3)
        assertTrue(result.isSuccess)
        assertEquals(3, result.getOrNull()?.size)
    }
}
