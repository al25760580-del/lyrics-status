package com.lyricsstatus.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lyricsstatus.app.data.ai.TranslationManager
import com.lyricsstatus.app.data.model.AiModelOption
import com.lyricsstatus.app.data.model.AiProvider
import com.lyricsstatus.app.data.model.AppSettings
import com.lyricsstatus.app.data.model.LyricsLine
import com.lyricsstatus.app.data.model.PredefinedAiModels
import com.lyricsstatus.app.data.model.SongLyrics
import com.lyricsstatus.app.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AiTestResult(
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val output: List<String> = emptyList(),
    val errorMessage: String? = null,
    val latencyMs: Long = 0L
)

class AiSettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository(application)
    private val translationManager = TranslationManager(application)

    val settings: StateFlow<AppSettings> = settingsRepo.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppSettings()
    )

    private val _testResult = MutableStateFlow(AiTestResult())
    val testResult: StateFlow<AiTestResult> = _testResult.asStateFlow()

    fun setProvider(provider: AiProvider) {
        viewModelScope.launch {
            val defaultModel = PredefinedAiModels.getDefaultModel(provider)
            settingsRepo.updateSettings {
                it.copy(
                    aiProvider = provider,
                    aiModel = defaultModel
                )
            }
        }
    }

    fun setModel(modelId: String) {
        viewModelScope.launch {
            settingsRepo.updateSettings {
                it.copy(aiModel = modelId)
            }
        }
    }

    fun setApiKey(apiKey: String) {
        viewModelScope.launch {
            settingsRepo.updateSettings {
                it.copy(aiApiKey = apiKey)
            }
        }
    }

    fun setTargetLanguage(langCode: String) {
        viewModelScope.launch {
            settingsRepo.updateSettings {
                it.copy(targetLanguage = langCode)
            }
        }
    }

    fun setCustomEndpoint(url: String, model: String, authHeader: String, customHeadersJson: String) {
        viewModelScope.launch {
            settingsRepo.updateSettings {
                it.copy(
                    customEndpointUrl = url,
                    customModelName = model,
                    customAuthHeader = authHeader,
                    customHeadersJson = customHeadersJson
                )
            }
        }
    }

    fun setTemperature(temp: Float) {
        viewModelScope.launch {
            settingsRepo.updateSettings {
                it.copy(temperature = temp.coerceIn(0f, 1f))
            }
        }
    }

    fun toggleTranslationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.updateSettings {
                it.copy(enableTranslation = enabled)
            }
        }
    }

    fun runTestTranslation() {
        val sampleLyrics = listOf(
            "I've been on my own for long enough",
            "Maybe you can show me how to love, maybe",
            "I'm going through withdrawals",
            "You don't even have to do too much",
            "You can turn me on with just a touch, baby"
        )

        viewModelScope.launch {
            _testResult.value = AiTestResult(isLoading = true)
            val startTime = System.currentTimeMillis()
            val currentSettings = settings.value.copy(enableTranslation = true)

            val testSong = SongLyrics(sampleLyrics.mapIndexed { idx, txt -> LyricsLine(time = idx * 3000L, text = txt) })
            val result = translationManager.translateSongLyrics(
                lyrics = testSong,
                trackName = "Test Sample",
                artistName = "Demo Artist",
                settings = currentSettings
            )

            val elapsed = System.currentTimeMillis() - startTime

            result.fold(
                onSuccess = { translatedSong ->
                    val lines = translatedSong.lines.mapNotNull { it.textTranslated }
                    _testResult.value = AiTestResult(
                        isLoading = false,
                        isSuccess = true,
                        output = lines,
                        latencyMs = elapsed
                    )
                },
                onFailure = { err ->
                    _testResult.value = AiTestResult(
                        isLoading = false,
                        isSuccess = false,
                        errorMessage = err.message ?: "Unknown AI error",
                        latencyMs = elapsed
                    )
                }
            )
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            translationManager.clearCache()
        }
    }
}
