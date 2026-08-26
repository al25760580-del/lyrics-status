package com.lyricsstatus.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.lyricsstatus.app.MainActivity
import com.lyricsstatus.app.R
import com.lyricsstatus.app.data.model.AppSettings
import com.lyricsstatus.app.data.model.PlaybackState

object NotificationHelper {

    const val CHANNEL_ID = "lyrics_playback_channel"
    const val NOTIFICATION_ID = 1001

    const val ACTION_PLAY_PAUSE = "com.lyricsstatus.app.ACTION_PLAY_PAUSE"
    const val ACTION_NEXT = "com.lyricsstatus.app.ACTION_NEXT"
    const val ACTION_PREV = "com.lyricsstatus.app.ACTION_PREV"
    const val ACTION_TOGGLE_TRANSLATION = "com.lyricsstatus.app.ACTION_TOGGLE_TRANSLATION"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.notification_channel_name)
            val descriptionText = context.getString(R.string.notification_channel_desc)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(false)
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun buildNotification(
        context: Context,
        state: PlaybackState,
        settings: AppSettings
    ): Notification {
        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context, 0, appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (state.hasTrack) {
            if (state.songAuthor.isNotBlank()) "${state.songName} - ${state.songAuthor}" else state.songName
        } else {
            context.getString(R.string.waiting_for_playback)
        }

        val activeLine = state.currentLine
        val originalLyric = activeLine?.text?.ifBlank { "..." } ?: "Waiting for lyrics..."
        val translatedLyric = activeLine?.textTranslated

        val mainDisplayText = if (settings.enableTranslation && !translatedLyric.isNullOrBlank()) {
            translatedLyric
        } else {
            originalLyric
        }

        // Subtitle / Secondary line
        val subLineText = if (settings.enableTranslation && !translatedLyric.isNullOrBlank()) {
            "Original: $originalLyric"
        } else {
            state.providerLabel.ifBlank { "Lyrics Live Sync" }
        }

        // Expandable BigTextStyle with current line + translated line + source badge
        val bigText = buildString {
            append(mainDisplayText)
            if (settings.enableTranslation && !translatedLyric.isNullOrBlank()) {
                append("\n\nOriginal: ")
                append(originalLyric)
            }
            if (state.lyricsSource.isNotBlank()) {
                append("\n\nSource: ")
                append(state.lyricsSource)
            }
            if (state.isTranslating) {
                append(" (Translating with ${settings.aiProvider.displayName}...)")
            }
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(mainDisplayText)
            .setSubText(subLineText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(bigText)
                    .setBigContentTitle(title)
                    .setSummaryText(if (state.isPlaying) "Playing" else "Paused")
            )
            .setContentIntent(contentPendingIntent)
            .setOngoing(state.isPlaying)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        // Action Buttons: Previous, Play/Pause, Next, Toggle Translation
        val prevPendingIntent = PendingIntent.getService(
            context, 1,
            Intent(context, LyricsForegroundService::class.java).apply { action = ACTION_PREV },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(android.R.drawable.ic_media_previous, "Previous", prevPendingIntent)

        val playPauseIcon = if (state.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseText = if (state.isPlaying) "Pause" else "Play"
        val playPausePendingIntent = PendingIntent.getService(
            context, 2,
            Intent(context, LyricsForegroundService::class.java).apply { action = ACTION_PLAY_PAUSE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(playPauseIcon, playPauseText, playPausePendingIntent)

        val nextPendingIntent = PendingIntent.getService(
            context, 3,
            Intent(context, LyricsForegroundService::class.java).apply { action = ACTION_NEXT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(android.R.drawable.ic_media_next, "Next", nextPendingIntent)

        val toggleTranslationIntent = PendingIntent.getService(
            context, 4,
            Intent(context, LyricsForegroundService::class.java).apply { action = ACTION_TOGGLE_TRANSLATION },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val transIcon = if (settings.enableTranslation) android.R.drawable.ic_menu_agenda else android.R.drawable.ic_menu_compass
        builder.addAction(transIcon, if (settings.enableTranslation) "Translate: ON" else "Translate: OFF", toggleTranslationIntent)

        return builder.build()
    }
}
