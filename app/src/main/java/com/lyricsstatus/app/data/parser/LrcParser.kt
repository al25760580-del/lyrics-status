package com.lyricsstatus.app.data.parser

import com.lyricsstatus.app.data.model.LyricsLine
import com.lyricsstatus.app.data.model.SongLyrics
import java.util.regex.Pattern

object LrcParser {

    // Tolerates ASCII and full-width separators (CJK sources often use
    // "：" instead of ":" and "．" instead of ".").
    private val TIMESTAMP_PATTERN = Pattern.compile("\\[(\\d{1,3})[:：](\\d{2})(?:[.．:：](\\d{1,3}))?]")
    private val OFFSET_PATTERN = Pattern.compile("^\\[offset:\\s*([+-]?\\d+)\\]", Pattern.CASE_INSENSITIVE)

    /**
     * Parses an LRC formatted string or plain text lyrics.
     * Supports multi-timestamp lines, millisecond fractions, offset tags,
     * and fallback step calculation for raw text.
     */
    fun parse(input: String, plainTextStepMs: Long = 3000L): SongLyrics {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return SongLyrics(emptyList())
        }

        // Check if input contains LRC timestamps
        val hasLrcTimestamps = trimmed.lines().any { line ->
            TIMESTAMP_PATTERN.matcher(line).find()
        }

        return if (hasLrcTimestamps) {
            parseLrc(trimmed)
        } else {
            parsePlainText(trimmed, plainTextStepMs)
        }
    }

    /**
     * Parses standard synchronized LRC format.
     */
    fun parseLrc(raw: String): SongLyrics {
        val lines = mutableListOf<LyricsLine>()
        var globalOffsetMs = 0L

        for (line in raw.lines()) {
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) continue

            // Check for [offset:+/-ms] tag
            val offsetMatcher = OFFSET_PATTERN.matcher(trimmedLine)
            if (offsetMatcher.find()) {
                globalOffsetMs = offsetMatcher.group(1)?.toLongOrNull() ?: 0L
                continue
            }

            // Extract timestamp captures
            val matcher = TIMESTAMP_PATTERN.matcher(trimmedLine)
            val timestamps = mutableListOf<Long>()

            while (matcher.find()) {
                val minutes = matcher.group(1)?.toLongOrNull() ?: 0L
                val seconds = matcher.group(2)?.toLongOrNull() ?: 0L
                val fractionStr = matcher.group(3) ?: "0"

                val millis = when (fractionStr.length) {
                    1 -> (fractionStr.toLongOrNull() ?: 0L) * 100L
                    2 -> (fractionStr.toLongOrNull() ?: 0L) * 10L
                    else -> fractionStr.take(3).toLongOrNull() ?: 0L
                }

                val totalMs = (minutes * 60L + seconds) * 1000L + millis
                timestamps.add(totalMs)
            }

            if (timestamps.isNotEmpty()) {
                // Strip all timestamp tags from line to get lyric text
                val text = TIMESTAMP_PATTERN.matcher(trimmedLine).replaceAll("").trim()
                if (text.isNotEmpty()) {
                    for (time in timestamps) {
                        val adjustedTime = (time + globalOffsetMs).coerceAtLeast(0L)
                        lines.add(LyricsLine(time = adjustedTime, text = text))
                    }
                }
            }
        }

        // Sort lines chronologically
        lines.sortBy { it.time }
        return SongLyrics(lines)
    }

    /**
     * Fallback for plain text lyrics: distributes lines evenly starting at 0ms.
     */
    fun parsePlainText(raw: String, stepMs: Long = 3000L): SongLyrics {
        val step = stepMs.coerceAtLeast(500L)
        val lines = mutableListOf<LyricsLine>()
        var index = 0

        for (line in raw.lines()) {
            val text = line.trim()
            if (text.isNotEmpty()) {
                lines.add(
                    LyricsLine(
                        time = index * step,
                        text = text
                    )
                )
                index++
            }
        }

        return SongLyrics(lines)
    }

    /**
     * Formats milliseconds into [mm:ss.xx] standard timestamp string.
     */
    fun formatTimestamp(millis: Long): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val hundredths = (millis % 1000) / 10
        return String.format("%02d:%02d.%02d", minutes, seconds, hundredths)
    }

    /**
     * Formats seconds into mm:ss display string.
     */
    fun formatSeconds(totalSeconds: Long): String {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }
}
