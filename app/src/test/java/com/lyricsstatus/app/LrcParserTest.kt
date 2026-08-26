package com.lyricsstatus.app

import com.lyricsstatus.app.data.parser.LrcParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

    @Test
    fun testParseStandardLrc() {
        val lrc = """
            [00:01.20]First lyric line
            [00:03.50]Second lyric line
            [01:05.100]Third lyric line after a minute
        """.trimIndent()

        val parsed = LrcParser.parse(lrc)
        assertEquals(3, parsed.lines.size)
        assertEquals(1200L, parsed.lines[0].time)
        assertEquals("First lyric line", parsed.lines[0].text)
        assertEquals(3500L, parsed.lines[1].time)
        assertEquals(65100L, parsed.lines[2].time)
    }

    @Test
    fun testParseMultipleTimestampsOnSingleLine() {
        val lrc = "[00:01.20][00:04.50]Repeated chorus line"
        val parsed = LrcParser.parse(lrc)

        assertEquals(2, parsed.lines.size)
        assertEquals(1200L, parsed.lines[0].time)
        assertEquals("Repeated chorus line", parsed.lines[0].text)
        assertEquals(4500L, parsed.lines[1].time)
        assertEquals("Repeated chorus line", parsed.lines[1].text)
    }

    @Test
    fun testParsePlainTextFallback() {
        val plain = """
            Line one
            Line two
            Line three
        """.trimIndent()

        val parsed = LrcParser.parse(plain, plainTextStepMs = 2500L)
        assertEquals(3, parsed.lines.size)
        assertEquals(0L, parsed.lines[0].time)
        assertEquals(2500L, parsed.lines[1].time)
        assertEquals(5000L, parsed.lines[2].time)
    }

    @Test
    fun testFindActiveLine() {
        val lrc = """
            [00:02.00]Line A
            [00:05.00]Line B
            [00:10.00]Line C
        """.trimIndent()

        val parsed = LrcParser.parse(lrc)
        val activeAt3Sec = parsed.findActiveLine(3000L)
        assertNotNull(activeAt3Sec)
        assertEquals("Line A", activeAt3Sec?.text)

        val activeAt6Sec = parsed.findActiveLine(6500L)
        assertEquals("Line B", activeAt6Sec?.text)

        val activeAt12Sec = parsed.findActiveLine(12000L)
        assertEquals("Line C", activeAt12Sec?.text)
    }
}
