package com.lyricsstatus.app.data.model

/**
 * Minimal track metadata consumed by the Discord status template engine.
 */
data class TrackInfo(
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long = 0L
)
