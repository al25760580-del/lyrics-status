package com.lyricsstatus.app.data.model

/**
 * Represents the current media playback snapshot across any detected player
 * (Spotify, Apple Music, YouTube Music, local players, or manual test simulator).
 */
data class PlaybackState(
    val songName: String = "",
    val songAuthor: String = "",
    val songId: String = "",
    val songDuration: Long = 0L,
    val songProgress: Long = 0L,
    val isPlaying: Boolean = false,
    val lyrics: SongLyrics? = null,
    val currentLine: LyricsLine? = null,
    val currentLineIndex: Int = -1,
    val lyricsSource: String = "",
    val activeProvider: String = "",
    val providerLabel: String = "",
    val albumArtUrl: String? = null,
    val lyricsEpoch: Long = 0L,
    val isTranslating: Boolean = false,
    val translationError: String? = null
) {
    val isEnded: Boolean get() = songDuration > 0 && songProgress >= songDuration
    val hasTrack: Boolean get() = songName.isNotBlank() || songAuthor.isNotBlank()

    fun progressFraction(): Float {
        if (songDuration <= 0) return 0f
        return (songProgress.toFloat() / songDuration.toFloat()).coerceIn(0f, 1f)
    }
}
