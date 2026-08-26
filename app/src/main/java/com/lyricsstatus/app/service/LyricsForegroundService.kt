package com.lyricsstatus.app.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.lyricsstatus.app.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class LyricsForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observerJob: Job? = null
    private lateinit var notificationManager: NotificationManager
    private lateinit var settingsRepository: SettingsRepository

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        settingsRepository = SettingsRepository(applicationContext)

        NotificationHelper.createNotificationChannel(this)

        val initialNotification = NotificationHelper.buildNotification(
            this,
            PlaybackStateManager.playbackState.value,
            PlaybackStateManager.settings.value
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NotificationHelper.NOTIFICATION_ID,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NotificationHelper.NOTIFICATION_ID, initialNotification)
        }

        startStateObserver()
    }

    private fun startStateObserver() {
        observerJob?.cancel()
        observerJob = serviceScope.launch {
            combine(
                PlaybackStateManager.playbackState,
                PlaybackStateManager.settings
            ) { state, settings ->
                Pair(state, settings)
            }
                .distinctUntilChanged { old, new ->
                    val (oldState, oldSettings) = old
                    val (newState, newSettings) = new
                    oldState.currentLine?.time == newState.currentLine?.time &&
                            oldState.currentLine?.text == newState.currentLine?.text &&
                            oldState.currentLine?.textTranslated == newState.currentLine?.textTranslated &&
                            oldState.isPlaying == newState.isPlaying &&
                            oldState.songName == newState.songName &&
                            oldState.songAuthor == newState.songAuthor &&
                            oldState.isTranslating == newState.isTranslating &&
                            oldSettings.enableTranslation == newSettings.enableTranslation
                }
                .collect { (state, settings) ->
                    if (settings.showNotification) {
                        val notification = NotificationHelper.buildNotification(this@LyricsForegroundService, state, settings)
                        notificationManager.notify(NotificationHelper.NOTIFICATION_ID, notification)
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            NotificationHelper.ACTION_PLAY_PAUSE -> {
                val currentPlaying = PlaybackStateManager.playbackState.value.isPlaying
                PlaybackStateManager.setPlaying(!currentPlaying)
            }
            NotificationHelper.ACTION_NEXT -> {
                // Media controller skip next or simulated seek
                val state = PlaybackStateManager.playbackState.value
                val newPos = (state.songProgress + 15000).coerceAtMost(state.songDuration)
                PlaybackStateManager.updateProgress(newPos)
            }
            NotificationHelper.ACTION_PREV -> {
                val state = PlaybackStateManager.playbackState.value
                val newPos = (state.songProgress - 15000).coerceAtLeast(0)
                PlaybackStateManager.updateProgress(newPos)
            }
            NotificationHelper.ACTION_TOGGLE_TRANSLATION -> {
                serviceScope.launch {
                    settingsRepository.updateSettings {
                        it.copy(enableTranslation = !it.enableTranslation)
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        observerJob?.cancel()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, LyricsForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, LyricsForegroundService::class.java)
            context.stopService(intent)
        }
    }
}
