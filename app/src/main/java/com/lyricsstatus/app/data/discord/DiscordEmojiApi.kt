package com.lyricsstatus.app.data.discord

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

@Serializable
data class DiscordGuildSummary(
    val id: String,
    val name: String,
    val icon: String? = null
)

@Serializable
data class DiscordGuildEmoji(
    val id: String,
    val name: String,
    val animated: Boolean = false,
    val available: Boolean = true,
    val managed: Boolean = false
)

/** A mutual guild and its usable custom emojis. */
data class DiscordGuildEmojis(
    val guild: DiscordGuildSummary,
    val emojis: List<DiscordGuildEmoji>
)

/**
 * Fetches the user's mutual guilds and their custom emojis for the emoji
 * picker, following the Discord API reference (docs.discord.food):
 *
 *  - `GET /users/@me/guilds`    -> partial guild objects (snowflake pagination)
 *  - `GET /guilds/{id}/emojis`  -> guild emoji objects
 *  - Emoji images: `https://cdn.discordapp.com/emojis/{id}.(png|gif)`
 *
 * Authentication is performed with the plain user token in the
 * `Authorization` header, as documented for user accounts.
 */
class DiscordEmojiApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    companion object {
        private const val API = "https://discord.com/api/v10"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

        /** Concurrent guild-emoji requests (keeps first paint fast without flooding). */
        private const val PARALLEL_GUILD_FETCHES = 6

        /** Safety cap for guild pagination. */
        const val MAX_GUILDS = 200

        /** In-memory picker cache so reopening the dialog is instant. */
        const val PICKER_CACHE_TTL_MS = 5 * 60 * 1000L
        private var cachedGuildEmojis: List<DiscordGuildEmojis>? = null
        private var cachedAtMs: Long = 0L

        fun peekCachedGuildEmojis(): List<DiscordGuildEmojis>? =
            cachedGuildEmojis?.takeIf { System.currentTimeMillis() - cachedAtMs < PICKER_CACHE_TTL_MS }

        fun storeGuildEmojisCache(list: List<DiscordGuildEmojis>) {
            cachedGuildEmojis = list
            cachedAtMs = System.currentTimeMillis()
        }

        /** CDN url for an emoji image (gif for animated ones). */
        fun emojiCdnUrl(emoji: DiscordGuildEmoji): String =
            "https://cdn.discordapp.com/emojis/${emoji.id}." +
                (if (emoji.animated) "gif" else "png") + "?size=64&quality=lossless"

        /** CDN url for a guild icon, if any. */
        fun guildIconUrl(guild: DiscordGuildSummary): String? =
            guild.icon?.let { "https://cdn.discordapp.com/icons/${guild.id}/$it.png?size=64" }

        /** Formats an emoji as the `<:name:id>` / `<a:name:id>` status representation. */
        fun toCustomEmojiFormat(emoji: DiscordGuildEmoji): String =
            if (emoji.animated) "<a:${emoji.name}:${emoji.id}>" else "<:${emoji.name}:${emoji.id}>"
    }

    private fun buildGet(url: String, token: String): Request =
        Request.Builder()
            .url(url)
            .header("Authorization", token)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

    /**
     * Lists the mutual guilds of the logged user, paginated by snowflake
     * (`after` + `limit`, up to [MAX_GUILDS]).
     */
    suspend fun fetchGuilds(token: String): Result<List<DiscordGuildSummary>> = withContext(Dispatchers.IO) {
        try {
            val all = mutableListOf<DiscordGuildSummary>()
            var after: String? = null
            while (all.size < MAX_GUILDS) {
                val url = "$API/users/@me/guilds?limit=100" +
                    (after?.let { "&after=$it" } ?: "")
                val body = client.newCall(buildGet(url, token)).execute().use { res ->
                    if (!res.isSuccessful) {
                        return@withContext Result.failure(Exception("HTTP ${res.code} listing guilds"))
                    }
                    res.body?.string() ?: "[]"
                }
                val page = json.decodeFromString<List<DiscordGuildSummary>>(body)
                if (page.isEmpty()) break
                all += page
                after = page.last().id
                if (page.size < 100) break
            }
            Result.success(all)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Lists the custom emojis of a guild.
     */
    suspend fun fetchGuildEmojis(token: String, guildId: String): Result<List<DiscordGuildEmoji>> =
        withContext(Dispatchers.IO) {
            try {
                val body = client.newCall(buildGet("$API/guilds/$guildId/emojis", token)).execute().use { res ->
                    if (!res.isSuccessful) {
                        return@withContext Result.failure(Exception("HTTP ${res.code} loading emojis"))
                    }
                    res.body?.string() ?: "[]"
                }
                Result.success(json.decodeFromString(body))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Progressive picker loading: invokes [onGuildEmojis] as each guild with
     * emojis resolves (fetched in parallel, bounded by a semaphore), so the
     * UI can render results incrementally instead of blocking until every
     * guild is done. Composing images lazily + progressively avoids the
     * first-open render spike.
     */
    suspend fun fetchGuildsWithEmojisProgressive(
        token: String,
        onGuildEmojis: (DiscordGuildEmojis) -> Unit
    ): Result<Unit> {
        val guilds = fetchGuilds(token).getOrElse { return Result.failure(it) }
        coroutineScope {
            val semaphore = Semaphore(PARALLEL_GUILD_FETCHES)
            guilds.map { guild ->
                launch {
                    semaphore.withPermit {
                        val emojis = fetchGuildEmojis(token, guild.id).getOrNull()
                            ?.filter { it.available && !it.managed && it.name.isNotBlank() }
                            .orEmpty()
                        if (emojis.isNotEmpty()) {
                            onGuildEmojis(
                                DiscordGuildEmojis(
                                    guild = guild,
                                    emojis = emojis.sortedBy { it.name.lowercase() }
                                )
                            )
                        }
                    }
                }
            }.joinAll()
        }
        return Result.success(Unit)
    }
}
