package com.lyricsstatus.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lyricsstatus.app.data.model.CustomLyricsMeta
import com.lyricsstatus.app.data.sources.CustomLyricsSource
import com.lyricsstatus.app.service.PlaybackStateManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CustomLyricsViewModel(application: Application) : AndroidViewModel(application) {

    private val customSource = CustomLyricsSource(application)

    private val _lyricsList = MutableStateFlow<List<CustomLyricsMeta>>(emptyList())
    val lyricsList: StateFlow<List<CustomLyricsMeta>> = _lyricsList.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        loadList()
    }

    fun loadList() {
        viewModelScope.launch {
            _lyricsList.value = customSource.listAll()
        }
    }

    fun saveLyrics(track: String, artist: String, rawText: String) {
        viewModelScope.launch {
            val result = customSource.save(track, artist, rawText)
            result.fold(
                onSuccess = { meta ->
                    _statusMessage.value = "Saved ${meta.track} (${meta.linesCount} lines)"
                    loadList()

                    // If it's currently playing, apply right away
                    val currentTrack = PlaybackStateManager.playbackState.value
                    if (currentTrack.songName.equals(track, ignoreCase = true)) {
                        val loaded = customSource.load(track, artist)
                        if (loaded != null) {
                            PlaybackStateManager.applyCustomLyricsManually(loaded)
                        }
                    }
                },
                onFailure = { error ->
                    _statusMessage.value = "Error: ${error.message}"
                }
            )
        }
    }

    fun deleteLyrics(track: String, artist: String) {
        viewModelScope.launch {
            val deleted = customSource.remove(track, artist)
            if (deleted) {
                _statusMessage.value = "Deleted lyrics for $track"
                loadList()
            }
        }
    }

    suspend fun getRawLyrics(track: String, artist: String): String? {
        return customSource.loadRaw(track, artist)
    }

    fun clearStatus() {
        _statusMessage.value = null
    }
}
