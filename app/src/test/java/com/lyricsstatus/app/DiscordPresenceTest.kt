package com.lyricsstatus.app

import com.lyricsstatus.app.data.discord.DiscordAuth
import com.lyricsstatus.app.data.discord.DiscordGatewayPresence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscordPresenceTest {

    @Test
    fun testDiscordApiConstants() {
        assertEquals("https://discord.com/api/v10", DiscordAuth.DISCORD_API)
        assertTrue(DiscordGatewayPresence.GATEWAY_URL.contains("gateway.discord.gg"))
    }
}
