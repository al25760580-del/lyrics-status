package com.lyricsstatus.app.data.model

import kotlinx.serialization.Serializable

/**
 * Represents a single line in synchronized lyrics with its millisecond timestamp
 * and optional AI-generated translation.
 */
@Serializable
data class LyricsLine(
    val time: Long,
    val text: String,
    var textTranslated: String? = null
)

/**
 * Represents a full synchronized song lyrics object containing a list of timed lines.
 */
@Serializable
data class SongLyrics(
    val lines: List<LyricsLine> = emptyList()
) {
    val isEmpty: Boolean get() = lines.isEmpty()
    val isNotEmpty: Boolean get() = lines.isNotEmpty()

    /**
     * Finds the active line for a given playback progress in milliseconds.
     */
    fun findActiveLine(progressMs: Long, offsetMs: Long = 0): LyricsLine? {
        val target = progressMs + offsetMs
        return lines.lastOrNull { it.time <= target && it.text.isNotBlank() }
    }

    /**
     * Finds the index of the active line.
     */
    fun findActiveIndex(progressMs: Long, offsetMs: Long = 0): Int {
        val target = progressMs + offsetMs
        return lines.indexOfLast { it.time <= target && it.text.isNotBlank() }
    }
}
