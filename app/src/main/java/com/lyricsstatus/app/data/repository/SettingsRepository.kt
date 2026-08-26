package com.lyricsstatus.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lyricsstatus.app.data.model.AiProvider
import com.lyricsstatus.app.data.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "lyrics_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val ENABLE_TRANSLATION = booleanPreferencesKey("enable_translation")
        val TARGET_LANGUAGE = stringPreferencesKey("target_language")
        val AI_PROVIDER = stringPreferencesKey("ai_provider")
        val AI_API_KEY = stringPreferencesKey("ai_api_key")
        val AI_MODEL = stringPreferencesKey("ai_model")
        val CUSTOM_ENDPOINT_URL = stringPreferencesKey("custom_endpoint_url")
        val CUSTOM_MODEL_NAME = stringPreferencesKey("custom_model_name")
        val CUSTOM_AUTH_HEADER = stringPreferencesKey("custom_auth_header")
        val CUSTOM_HEADERS_JSON = stringPreferencesKey("custom_headers_json")
        val TEMPERATURE = floatPreferencesKey("temperature")

        val SEND_TIME_OFFSET_MS = longPreferencesKey("send_time_offset_ms")
        val ENABLE_AUTO_OFFSET = booleanPreferencesKey("enable_auto_offset")
        val AUTO_OFFSET_SAMPLE_SIZE = intPreferencesKey("auto_offset_sample_size")

        val SHOW_NOTIFICATION = booleanPreferencesKey("show_notification")
        val SHOW_TRANSLATED_IN_NOTIF = booleanPreferencesKey("show_translated_in_notif")
        val SHOW_NEXT_LINE_IN_NOTIF = booleanPreferencesKey("show_next_line_in_notif")
        val NOTIFICATION_HIGH_PRIORITY = booleanPreferencesKey("notification_high_priority")

        val MUSIC_DETECTION_MODE = stringPreferencesKey("music_detection_mode")
        val DISCORD_ENABLED = booleanPreferencesKey("discord_enabled")
        val DISCORD_TOKEN = stringPreferencesKey("discord_token")
        val DISCORD_EMOJI = stringPreferencesKey("discord_emoji")
        val DISCORD_STATUS_TEMPLATE = stringPreferencesKey("discord_status_template")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val providerStr = prefs[Keys.AI_PROVIDER] ?: AiProvider.GEMINI.name
        val provider = try {
            AiProvider.valueOf(providerStr)
        } catch (ignored: Exception) {
            AiProvider.GEMINI
        }

        AppSettings(
            enableTranslation = prefs[Keys.ENABLE_TRANSLATION] ?: false,
            targetLanguage = prefs[Keys.TARGET_LANGUAGE] ?: "es-MX",
            aiProvider = provider,
            aiApiKey = prefs[Keys.AI_API_KEY] ?: "",
            aiModel = prefs[Keys.AI_MODEL] ?: "gemini-3.5-flash-lite",
            customEndpointUrl = prefs[Keys.CUSTOM_ENDPOINT_URL] ?: "http://10.0.2.2:11434/v1/chat/completions",
            customModelName = prefs[Keys.CUSTOM_MODEL_NAME] ?: "llama3.2",
            customAuthHeader = prefs[Keys.CUSTOM_AUTH_HEADER] ?: "Bearer ",
            customHeadersJson = prefs[Keys.CUSTOM_HEADERS_JSON] ?: "{}",
            temperature = prefs[Keys.TEMPERATURE] ?: 0.2f,

            sendTimeOffsetMs = prefs[Keys.SEND_TIME_OFFSET_MS] ?: 500L,
            enableAutoOffset = prefs[Keys.ENABLE_AUTO_OFFSET] ?: true,
            autoOffsetSampleSize = prefs[Keys.AUTO_OFFSET_SAMPLE_SIZE] ?: 3,

            showNotification = prefs[Keys.SHOW_NOTIFICATION] ?: true,
            showTranslatedLineInNotification = prefs[Keys.SHOW_TRANSLATED_IN_NOTIF] ?: true,
            showNextLineInNotification = prefs[Keys.SHOW_NEXT_LINE_IN_NOTIF] ?: true,
            notificationHighPriority = prefs[Keys.NOTIFICATION_HIGH_PRIORITY] ?: true,

            musicDetectionMode = prefs[Keys.MUSIC_DETECTION_MODE] ?: "auto",
            discordEnabled = prefs[Keys.DISCORD_ENABLED] ?: false,
            discordToken = prefs[Keys.DISCORD_TOKEN] ?: "",
            discordCustomEmoji = prefs[Keys.DISCORD_EMOJI] ?: "🎶",
            discordStatusTemplate = prefs[Keys.DISCORD_STATUS_TEMPLATE] ?: "[{timestamp}] [{lyrics}]"
        )
    }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val currentProviderStr = prefs[Keys.AI_PROVIDER] ?: AiProvider.GEMINI.name
            val currentProvider = try { AiProvider.valueOf(currentProviderStr) } catch (ignored: Exception) { AiProvider.GEMINI }

            val current = AppSettings(
                enableTranslation = prefs[Keys.ENABLE_TRANSLATION] ?: false,
                targetLanguage = prefs[Keys.TARGET_LANGUAGE] ?: "es-MX",
                aiProvider = currentProvider,
                aiApiKey = prefs[Keys.AI_API_KEY] ?: "",
                aiModel = prefs[Keys.AI_MODEL] ?: "gemini-3.5-flash-lite",
                customEndpointUrl = prefs[Keys.CUSTOM_ENDPOINT_URL] ?: "http://10.0.2.2:11434/v1/chat/completions",
                customModelName = prefs[Keys.CUSTOM_MODEL_NAME] ?: "llama3.2",
                customAuthHeader = prefs[Keys.CUSTOM_AUTH_HEADER] ?: "Bearer ",
                customHeadersJson = prefs[Keys.CUSTOM_HEADERS_JSON] ?: "{}",
                temperature = prefs[Keys.TEMPERATURE] ?: 0.2f,

                sendTimeOffsetMs = prefs[Keys.SEND_TIME_OFFSET_MS] ?: 500L,
                enableAutoOffset = prefs[Keys.ENABLE_AUTO_OFFSET] ?: true,
                autoOffsetSampleSize = prefs[Keys.AUTO_OFFSET_SAMPLE_SIZE] ?: 3,

                showNotification = prefs[Keys.SHOW_NOTIFICATION] ?: true,
                showTranslatedLineInNotification = prefs[Keys.SHOW_TRANSLATED_IN_NOTIF] ?: true,
                showNextLineInNotification = prefs[Keys.SHOW_NEXT_LINE_IN_NOTIF] ?: true,
                notificationHighPriority = prefs[Keys.NOTIFICATION_HIGH_PRIORITY] ?: true,

                musicDetectionMode = prefs[Keys.MUSIC_DETECTION_MODE] ?: "auto",
                discordEnabled = prefs[Keys.DISCORD_ENABLED] ?: false,
                discordToken = prefs[Keys.DISCORD_TOKEN] ?: "",
                discordCustomEmoji = prefs[Keys.DISCORD_EMOJI] ?: "🎶",
                discordStatusTemplate = prefs[Keys.DISCORD_STATUS_TEMPLATE] ?: "[{timestamp}] [{lyrics}]"
            )

            val updated = transform(current)

            prefs[Keys.ENABLE_TRANSLATION] = updated.enableTranslation
            prefs[Keys.TARGET_LANGUAGE] = updated.targetLanguage
            prefs[Keys.AI_PROVIDER] = updated.aiProvider.name
            prefs[Keys.AI_API_KEY] = updated.aiApiKey
            prefs[Keys.AI_MODEL] = updated.aiModel
            prefs[Keys.CUSTOM_ENDPOINT_URL] = updated.customEndpointUrl
            prefs[Keys.CUSTOM_MODEL_NAME] = updated.customModelName
            prefs[Keys.CUSTOM_AUTH_HEADER] = updated.customAuthHeader
            prefs[Keys.CUSTOM_HEADERS_JSON] = updated.customHeadersJson
            prefs[Keys.TEMPERATURE] = updated.temperature

            prefs[Keys.SEND_TIME_OFFSET_MS] = updated.sendTimeOffsetMs
            prefs[Keys.ENABLE_AUTO_OFFSET] = updated.enableAutoOffset
            prefs[Keys.AUTO_OFFSET_SAMPLE_SIZE] = updated.autoOffsetSampleSize

            prefs[Keys.SHOW_NOTIFICATION] = updated.showNotification
            prefs[Keys.SHOW_TRANSLATED_IN_NOTIF] = updated.showTranslatedLineInNotification
            prefs[Keys.SHOW_NEXT_LINE_IN_NOTIF] = updated.showNextLineInNotification
            prefs[Keys.NOTIFICATION_HIGH_PRIORITY] = updated.notificationHighPriority

            prefs[Keys.MUSIC_DETECTION_MODE] = updated.musicDetectionMode
            prefs[Keys.DISCORD_ENABLED] = updated.discordEnabled
            prefs[Keys.DISCORD_TOKEN] = updated.discordToken
            prefs[Keys.DISCORD_EMOJI] = updated.discordCustomEmoji
            prefs[Keys.DISCORD_STATUS_TEMPLATE] = updated.discordStatusTemplate
        }
    }
}
