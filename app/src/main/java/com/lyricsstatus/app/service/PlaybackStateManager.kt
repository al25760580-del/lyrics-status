package com.lyricsstatus.app.service

import com.lyricsstatus.app.data.discord.DiscordGatewayPresence
import com.lyricsstatus.app.data.discord.DiscordPresenceTrack
import com.lyricsstatus.app.data.discord.DiscordStatusPusher
import com.lyricsstatus.app.data.model.AppSettings
import com.lyricsstatus.app.data.model.LyricsLine
import com.lyricsstatus.app.data.model.PlaybackState
import com.lyricsstatus.app.data.model.SongLyrics
import com.lyricsstatus.app.data.repository.LyricsRepository
import com.lyricsstatus.app.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

object PlaybackStateManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    val discordGateway = DiscordGatewayPresence()
    val discordStatusPusher = DiscordStatusPusher()

    private var lyricsRepository: LyricsRepository? = null
    private var settingsRepository: SettingsRepository? = null

    private var fetchJob: Job? = null
    private var tickerJob: Job? = null
    private var discordObserverJob: Job? = null
    private var lastTickTime = System.currentTimeMillis()

    private var isAndroidPlayingLocally = false
    private var lastPushedLineTime = -1L

    fun initialize(lyricsRepo: LyricsRepository, settingsRepo: SettingsRepository) {
        this.lyricsRepository = lyricsRepo
        this.settingsRepository = settingsRepo

        scope.launch {
            settingsRepo.settingsFlow.collectLatest { newSettings ->
                val oldSettings = _settings.value
                _settings.value = newSettings

                // Manage Discord Gateway lifecycle
                val shouldRunDiscord = (newSettings.musicDetectionMode == "discord" || newSettings.musicDetectionMode == "auto") &&
                        newSettings.discordEnabled && newSettings.discordToken.isNotBlank()

                if (shouldRunDiscord) {
                    if (oldSettings.discordToken != newSettings.discordToken || oldSettings.musicDetectionMode != newSettings.musicDetectionMode) {
                        discordGateway.connect(newSettings.discordToken)
                    }
                } else {
                    discordGateway.disconnect()
                }

                // If translation settings changed mid-song, re-fetch/translate
                if (oldSettings.enableTranslation != newSettings.enableTranslation ||
                    oldSettings.targetLanguage != newSettings.targetLanguage ||
                    oldSettings.aiProvider != newSettings.aiProvider ||
                    oldSettings.aiModel != newSettings.aiModel
                ) {
                    val current = _playbackState.value
                    if (current.hasTrack) {
                        triggerLyricsFetch(current.songName, current.songAuthor, forceRefresh = true)
                    }
                }
            }
        }

        observeDiscordPresence()
        startTicker()
    }

    private fun observeDiscordPresence() {
        discordObserverJob?.cancel()
        discordObserverJob = scope.launch {
            discordGateway.currentTrack.collectLatest { discordTrack ->
                val mode = _settings.value.musicDetectionMode
                if (discordTrack != null) {
                    val allowDiscord = when (mode) {
                        "discord" -> true
                        "auto" -> !isAndroidPlayingLocally
                        else -> false // "android" mode ignores Discord
                    }

                    if (allowDiscord) {
                        applyDiscordTrack(discordTrack)
                    }
                } else if (mode == "discord") {
                    _playbackState.update { it.copy(isPlaying = false) }
                    discordStatusPusher.clearStatus(_settings.value)
                }
            }
        }
    }

    private fun applyDiscordTrack(track: DiscordPresenceTrack) {
        val current = _playbackState.value
        val songChanged = current.songName != track.title || current.songAuthor != track.artist

        _playbackState.update {
            it.copy(
                songName = track.title,
                songAuthor = track.artist,
                songId = "${track.title} - ${track.artist}",
                songDuration = track.durationMs,
                songProgress = track.progressMs,
                isPlaying = track.isPlaying,
                providerLabel = "Discord RPC (${track.appName})",
                lyricsEpoch = if (songChanged) it.lyricsEpoch + 1 else it.lyricsEpoch
            )
        }

        lastTickTime = System.currentTimeMillis()

        if (songChanged && track.title.isNotBlank()) {
            lastPushedLineTime = -1L
            discordStatusPusher.reset()
            triggerLyricsFetch(track.title, track.artist)
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            lastTickTime = System.currentTimeMillis()
            while (true) {
                val now = System.currentTimeMillis()
                val delta = now - lastTickTime
                lastTickTime = now

                val current = _playbackState.value
                if (current.isPlaying && !current.isEnded) {
                    val newProgress = current.songProgress + delta
                    val lyrics = current.lyrics
                    val config = _settings.value

                    val dynamicOffset = if (config.enableAutoOffset) {
                        discordStatusPusher.getAverageLatency() + 100L
                    } else {
                        config.sendTimeOffsetMs
                    }

                    var activeLine: LyricsLine? = null
                    var activeIndex = -1

                    if (lyrics != null && lyrics.isNotEmpty) {
                        activeLine = lyrics.findActiveLine(newProgress, dynamicOffset)
                        activeIndex = lyrics.findActiveIndex(newProgress, dynamicOffset)

                        // Push active lyric line to Discord custom status
                        if (activeLine != null && activeLine.time != lastPushedLineTime && config.discordEnabled) {
                            lastPushedLineTime = activeLine.time
                            launch(Dispatchers.IO) {
                                discordStatusPusher.pushLyricsStatus(current, activeLine, config)
                            }
                        }
                    }

                    _playbackState.update {
                        it.copy(
                            songProgress = newProgress,
                            currentLine = activeLine,
                            currentLineIndex = activeIndex
                        )
                    }
                } else if (current.isEnded && lastPushedLineTime != -1L) {
                    lastPushedLineTime = -1L
                    launch(Dispatchers.IO) {
                        discordStatusPusher.clearStatus(_settings.value)
                    }
                }

                delay(25) // ~40 FPS synchronization precision
            }
        }
    }

    fun updateMediaInfo(
        songName: String,
        songAuthor: String,
        durationMs: Long,
        progressMs: Long,
        isPlaying: Boolean,
        providerLabel: String,
        albumArtUrl: String? = null
    ) {
        isAndroidPlayingLocally = isPlaying && songName.isNotBlank()

        val mode = _settings.value.musicDetectionMode
        // In "discord" mode, ignore Android media
        if (mode == "discord") {
            return
        }

        val current = _playbackState.value
        val songChanged = current.songName != songName || current.songAuthor != songAuthor

        _playbackState.update {
            it.copy(
                songName = songName,
                songAuthor = songAuthor,
                songId = "$songName - $songAuthor",
                songDuration = durationMs,
                songProgress = progressMs,
                isPlaying = isPlaying,
                providerLabel = providerLabel,
                albumArtUrl = albumArtUrl ?: it.albumArtUrl,
                lyricsEpoch = if (songChanged) it.lyricsEpoch + 1 else it.lyricsEpoch
            )
        }

        lastTickTime = System.currentTimeMillis()

        if (songChanged && songName.isNotBlank()) {
            lastPushedLineTime = -1L
            discordStatusPusher.reset()
            triggerLyricsFetch(songName, songAuthor)
        }
    }

    fun updateProgress(progressMs: Long) {
        lastTickTime = System.currentTimeMillis()
        val current = _playbackState.value
        val lyrics = current.lyrics
        val offset = _settings.value.sendTimeOffsetMs

        var activeLine: LyricsLine? = null
        var activeIndex = -1

        if (lyrics != null && lyrics.isNotEmpty) {
            activeLine = lyrics.findActiveLine(progressMs, offset)
            activeIndex = lyrics.findActiveIndex(progressMs, offset)
        }

        _playbackState.update {
            it.copy(
                songProgress = progressMs,
                currentLine = activeLine,
                currentLineIndex = activeIndex
            )
        }
    }

    fun setPlaying(isPlaying: Boolean) {
        lastTickTime = System.currentTimeMillis()
        _playbackState.update { it.copy(isPlaying = isPlaying) }

        if (!isPlaying) {
            scope.launch(Dispatchers.IO) {
                discordStatusPusher.clearStatus(_settings.value)
            }
        }
    }

    fun setMusicDetectionMode(mode: String) {
        scope.launch {
            settingsRepository?.updateSettings {
                it.copy(musicDetectionMode = mode)
            }
        }
    }

    fun triggerLyricsFetch(songName: String, songAuthor: String, forceRefresh: Boolean = false) {
        fetchJob?.cancel()
        fetchJob = scope.launch {
            _playbackState.update {
                it.copy(
                    isTranslating = _settings.value.enableTranslation,
                    translationError = null,
                    lyrics = if (forceRefresh) null else it.lyrics
                )
            }

            val repo = lyricsRepository ?: return@launch
            val result = repo.fetchLyrics(songName, songAuthor, _settings.value)

            if (result != null) {
                val (lyrics, sourceLabel) = result
                val currentProgress = _playbackState.value.songProgress
                val offset = _settings.value.sendTimeOffsetMs
                val activeLine = lyrics.findActiveLine(currentProgress, offset)
                val activeIndex = lyrics.findActiveIndex(currentProgress, offset)

                _playbackState.update {
                    it.copy(
                        lyrics = lyrics,
                        lyricsSource = sourceLabel,
                        currentLine = activeLine,
                        currentLineIndex = activeIndex,
                        isTranslating = false,
                        lyricsEpoch = it.lyricsEpoch + 1
                    )
                }
            } else {
                _playbackState.update {
                    it.copy(
                        lyrics = null,
                        lyricsSource = "None",
                        currentLine = null,
                        currentLineIndex = -1,
                        isTranslating = false
                    )
                }
            }
        }
    }

    fun applyCustomLyricsManually(lyrics: SongLyrics) {
        _playbackState.update {
            val offset = _settings.value.sendTimeOffsetMs
            val activeLine = lyrics.findActiveLine(it.songProgress, offset)
            val activeIndex = lyrics.findActiveIndex(it.songProgress, offset)
            it.copy(
                lyrics = lyrics,
                lyricsSource = "Custom",
                currentLine = activeLine,
                currentLineIndex = activeIndex,
                lyricsEpoch = it.lyricsEpoch + 1
            )
        }
    }
}
