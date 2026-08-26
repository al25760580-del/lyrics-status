package com.lyricsstatus.app

import com.lyricsstatus.app.data.token.TokenFetcher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenFetcherTest {

    @Test
    fun testTokenExtractorScriptIsNotEmpty() {
        assertTrue(TokenFetcher.DISCORD_WEB_EXTRACT_SCRIPT.contains("getToken"))
        assertFalse(TokenFetcher.DISCORD_WEB_EXTRACT_SCRIPT.isBlank())
    }

    @Test
    fun testUrlsAreValid() {
        assertTrue(TokenFetcher.DISCORD_ME_URL.startsWith("https://discord.com"))
        assertTrue(TokenFetcher.DISCORD_CONNECTIONS_URL.startsWith("https://discord.com"))
    }
}
