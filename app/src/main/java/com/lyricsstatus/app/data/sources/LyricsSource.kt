package com.lyricsstatus.app.data.sources

import com.lyricsstatus.app.data.model.SongLyrics

/**
 * Common interface for all synchronized lyrics providers.
 */
interface LyricsSource {
    val name: String
    suspend fun getLyrics(track: String, artist: String): Result<SongLyrics>
}
