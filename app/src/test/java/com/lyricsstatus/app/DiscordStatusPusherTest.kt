package com.lyricsstatus.app

import com.lyricsstatus.app.data.discord.DiscordStatusPusher
import com.lyricsstatus.app.data.discord.DiscordStatusPusher.CaseTransformation
import com.lyricsstatus.app.data.discord.DiscordStatusPusher.StatusField
import com.lyricsstatus.app.data.discord.DiscordStatusPusher.StatusTemplateToken
import com.lyricsstatus.app.data.model.LyricLine
import com.lyricsstatus.app.data.model.TrackInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscordStatusPusherTest {

    private val sampleTrack = TrackInfo(
        title = "Bohemian Rhapsody",
        artist = "Queen",
        album = "A Night at the Opera",
        durationMs = 354000L
    )

    private val sampleLine = LyricLine(
        timestampMs = 65000L,
        text = "Is this the real life? Is this just fantasy?",
        translation = "¿Es esta la vida real? ¿Es solo fantasía?"
    )

    @Test
    fun testParseTemplateTokens() {
        val template = "🎶 {lyrics} - {song_author} [{timestamp}]"
        val tokens = DiscordStatusPusher.parseTemplate(template)

        assertEquals(5, tokens.size)
        assertTrue(tokens[0] is StatusTemplateToken.Literal)
        assertEquals("🎶 ", (tokens[0] as StatusTemplateToken.Literal).text)

        assertTrue(tokens[1] is StatusTemplateToken.Field)
        val lyricsField = tokens[1] as StatusTemplateToken.Field
        assertEquals(StatusField.LYRICS, lyricsField.field)
        assertEquals(CaseTransformation.ORIGINAL, lyricsField.casing)

        assertTrue(tokens[2] is StatusTemplateToken.Literal)
        assertEquals(" - ", (tokens[2] as StatusTemplateToken.Literal).text)

        assertTrue(tokens[3] is StatusTemplateToken.Field)
        val authorField = tokens[3] as StatusTemplateToken.Field
        assertEquals(StatusField.SONG_AUTHOR, authorField.field)

        assertTrue(tokens[4] is StatusTemplateToken.Field)
        val tsField = tokens[4] as StatusTemplateToken.Field
        assertEquals(StatusField.TIMESTAMP, tsField.field)
    }

    @Test
    fun testFormatStatusTextDefault() {
        val template = "{lyrics}"
        val result = DiscordStatusPusher.formatStatusText(template, sampleLine, sampleTrack)
        assertEquals("Is this the real life? Is this just fantasy?", result)
    }

    @Test
    fun testFormatStatusTextWithArtistAndTimestamp() {
        val template = "{song_author} | {lyrics} ({timestamp})"
        val result = DiscordStatusPusher.formatStatusText(template, sampleLine, sampleTrack)
        assertEquals("Queen | Is this the real life? Is this just fantasy? (01:05)", result)
    }

    @Test
    fun testFormatStatusTextCasingUppercase() {
        val template = "{lyrics:uppercase} - {song_name:uppercase}"
        val result = DiscordStatusPusher.formatStatusText(template, sampleLine, sampleTrack)
        assertEquals("IS THIS THE REAL LIFE? IS THIS JUST FANTASY? - BOHEMIAN RHAPSODY", result)
    }

    @Test
    fun testFormatStatusTextCasingLowercase() {
        val template = "{song_name:lowercase}"
        val result = DiscordStatusPusher.formatStatusText(template, sampleLine, sampleTrack)
        assertEquals("bohemian rhapsody", result)
    }

    @Test
    fun testFormatStatusTextCropped() {
        val template = "{lyrics:cropped}"
        val longLine = LyricLine(
            timestampMs = 10000L,
            text = "This is a very very very very very very very long lyric line exceeding forty characters easily"
        )
        val result = DiscordStatusPusher.formatStatusText(template, longLine, sampleTrack)
        assertTrue(result.length <= 40)
        assertTrue(result.endsWith("..."))
    }

    @Test
    fun testParseUnicodeEmoji() {
        val emoji = DiscordStatusPusher.parseEmoji("🎵")
        assertNotNull(emoji)
        assertEquals("🎵", emoji?.name)
        assertNull(emoji?.id)
    }

    @Test
    fun testParseCustomDiscordEmoji() {
        val emoji = DiscordStatusPusher.parseEmoji("<:catJam:852951753951>")
        assertNotNull(emoji)
        assertEquals("catJam", emoji?.name)
        assertEquals("852951753951", emoji?.id)
    }

    @Test
    fun testParseCustomAnimatedDiscordEmoji() {
        val emoji = DiscordStatusPusher.parseEmoji("<a:vibing:9988776655>")
        assertNotNull(emoji)
        assertEquals("vibing", emoji?.name)
        assertEquals("9988776655", emoji?.id)
    }

    @Test
    fun testAutoOffsetLatencyAdjustment() {
        DiscordStatusPusher.autoOffsetMs = 0L
        DiscordStatusPusher.updateAutoOffset(latencyMs = 250L)
        // EWMA: 0.7 * 0 + 0.3 * 250 = 75
        assertEquals(75L, DiscordStatusPusher.autoOffsetMs)

        DiscordStatusPusher.updateAutoOffset(latencyMs = 250L)
        // EWMA: 0.7 * 75 + 0.3 * 250 = 52.5 + 75 = 127.5 -> 127
        assertEquals(127L, DiscordStatusPusher.autoOffsetMs)
    }
}
