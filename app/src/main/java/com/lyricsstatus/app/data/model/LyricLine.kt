package com.lyricsstatus.app.data.model

/**
 * Lightweight lyric line consumed by the Discord status template engine.
 *
 * This is intentionally decoupled from the full-blown [LyricsLine] playback
 * model so the template engine can also be unit-tested and reused without
 * pulling in the whole lyrics pipeline.
 */
data class LyricLine(
    val timestampMs: Long,
    val text: String,
    val translation: String? = null
)
