package com.lyricsstatus.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lyricsstatus.app.data.model.AppSettings
import com.lyricsstatus.app.data.model.PlaybackState
import com.lyricsstatus.app.data.model.SongLyrics
import com.lyricsstatus.app.data.repository.LyricsRepository
import com.lyricsstatus.app.data.repository.SettingsRepository
import com.lyricsstatus.app.service.PlaybackStateManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository(application)
    private val lyricsRepo = LyricsRepository(application)

    val playbackState: StateFlow<PlaybackState> = PlaybackStateManager.playbackState
    val settings: StateFlow<AppSettings> = settingsRepo.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppSettings()
    )

    init {
        PlaybackStateManager.initialize(lyricsRepo, settingsRepo)
    }

    fun togglePlayPause() {
        val isCurrentlyPlaying = playbackState.value.isPlaying
        PlaybackStateManager.setPlaying(!isCurrentlyPlaying)
    }

    fun seekTo(progressMs: Long) {
        PlaybackStateManager.updateProgress(progressMs)
    }

    fun toggleTranslation() {
        viewModelScope.launch {
            settingsRepo.updateSettings {
                it.copy(enableTranslation = !it.enableTranslation)
            }
        }
    }

    fun setMusicDetectionMode(mode: String) {
        viewModelScope.launch {
            settingsRepo.updateSettings {
                it.copy(musicDetectionMode = mode)
            }
        }
    }

    fun refreshLyrics() {
        val state = playbackState.value
        if (state.hasTrack) {
            viewModelScope.launch {
                lyricsRepo.invalidateCache(state.songName, state.songAuthor)
                PlaybackStateManager.triggerLyricsFetch(state.songName, state.songAuthor, forceRefresh = true)
            }
        }
    }

    fun adjustOffset(deltaMs: Long) {
        viewModelScope.launch {
            settingsRepo.updateSettings {
                it.copy(sendTimeOffsetMs = (it.sendTimeOffsetMs + deltaMs).coerceIn(-5000L, 5000L))
            }
        }
    }

    // Demo Player simulator for instant testing
    fun loadDemoTrack(title: String, artist: String, durationSec: Int = 210) {
        PlaybackStateManager.updateMediaInfo(
            songName = title,
            songAuthor = artist,
            durationMs = durationSec * 1000L,
            progressMs = 0L,
            isPlaying = true,
            providerLabel = "Demo Player"
        )
    }
}
