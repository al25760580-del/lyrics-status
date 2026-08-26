package com.lyricsstatus.app.service

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState as AndroidPlaybackState
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class MediaPlaybackListenerService : NotificationListenerService() {

    private var mediaSessionManager: MediaSessionManager? = null
    private val activeControllers = mutableMapOf<String, MediaController>()
    private var activeController: MediaController? = null

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: AndroidPlaybackState?) {
            handlePlaybackUpdate()
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            handlePlaybackUpdate()
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
        } catch (e: Exception) {
            Log.e("MediaListener", "Error getting MediaSessionManager", e)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        registerActiveSessions()
    }

    private fun registerActiveSessions() {
        try {
            val component = ComponentName(this, MediaPlaybackListenerService::class.java)
            val controllers = mediaSessionManager?.getActiveSessions(component) ?: emptyList()
            for (controller in controllers) {
                val pkg = controller.packageName
                activeControllers[pkg] = controller
                controller.registerCallback(callback)
            }
            findBestActiveController()
        } catch (e: SecurityException) {
            Log.w("MediaListener", "Notification listener permission not granted", e)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn?.let {
            if (isMusicPlayerPackage(it.packageName)) {
                registerActiveSessions()
            }
        }
    }

    private fun isMusicPlayerPackage(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return lower.contains("spotify") || lower.contains("music") ||
                lower.contains("audio") || lower.contains("deezer") ||
                lower.contains("tidal") || lower.contains("apple") ||
                lower.contains("vlc") || lower.contains("player")
    }

    private fun findBestActiveController() {
        // Look for any playing media controller
        val playing = activeControllers.values.find {
            it.playbackState?.state == AndroidPlaybackState.STATE_PLAYING
        }
        val candidate = playing ?: activeControllers.values.firstOrNull()

        if (candidate != null && candidate != activeController) {
            activeController?.unregisterCallback(callback)
            activeController = candidate
            activeController?.registerCallback(callback)
        }

        handlePlaybackUpdate()
    }

    private fun handlePlaybackUpdate() {
        val controller = activeController ?: return
        val metadata = controller.metadata
        val pbState = controller.playbackState

        val songName = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: ""

        val songAuthor = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_AUTHOR)
            ?: ""

        val durationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val progressMs = pbState?.position ?: 0L
        val isPlaying = pbState?.state == AndroidPlaybackState.STATE_PLAYING

        val appLabel = getAppNameFromPackage(controller.packageName)

        if (songName.isNotBlank()) {
            PlaybackStateManager.updateMediaInfo(
                songName = songName,
                songAuthor = songAuthor,
                durationMs = durationMs,
                progressMs = progressMs,
                isPlaying = isPlaying,
                providerLabel = appLabel
            )
        }
    }

    private fun getAppNameFromPackage(packageName: String): String {
        return try {
            val pm = packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (ignored: Exception) {
            when {
                packageName.contains("spotify") -> "Spotify"
                packageName.contains("youtube") -> "YouTube Music"
                packageName.contains("apple") -> "Apple Music"
                else -> packageName
            }
        }
    }

    override fun onDestroy() {
        activeController?.unregisterCallback(callback)
        activeControllers.clear()
        super.onDestroy()
    }
}
