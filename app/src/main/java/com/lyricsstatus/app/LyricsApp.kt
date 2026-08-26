package com.lyricsstatus.app

import android.app.Application
import com.lyricsstatus.app.data.repository.LyricsRepository
import com.lyricsstatus.app.data.repository.SettingsRepository
import com.lyricsstatus.app.service.LyricsForegroundService
import com.lyricsstatus.app.service.NotificationHelper
import com.lyricsstatus.app.service.PlaybackStateManager

class LyricsApp : Application() {

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannel(this)

        val lyricsRepo = LyricsRepository(this)
        val settingsRepo = SettingsRepository(this)
        PlaybackStateManager.initialize(lyricsRepo, settingsRepo)

        LyricsForegroundService.start(this)
    }
}
