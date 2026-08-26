package com.lyricsstatus.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.lyricsstatus.app.data.repository.SettingsRepository
import com.lyricsstatus.app.service.LyricsForegroundService
import com.lyricsstatus.app.ui.screens.AiSettingsScreen
import com.lyricsstatus.app.ui.screens.CustomLyricsScreen
import com.lyricsstatus.app.ui.screens.GeneralSettingsScreen
import com.lyricsstatus.app.ui.screens.NowPlayingScreen
import com.lyricsstatus.app.ui.screens.TokenFetcherScreen
import com.lyricsstatus.app.ui.theme.LyricsStatusTheme
import com.lyricsstatus.app.ui.viewmodel.AiSettingsViewModel
import com.lyricsstatus.app.ui.viewmodel.CustomLyricsViewModel
import com.lyricsstatus.app.ui.viewmodel.PlayerViewModel

class MainActivity : ComponentActivity() {

    private val playerViewModel: PlayerViewModel by viewModels()
    private val aiSettingsViewModel: AiSettingsViewModel by viewModels()
    private val customLyricsViewModel: CustomLyricsViewModel by viewModels()
    private lateinit var settingsRepository: SettingsRepository

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            LyricsForegroundService.start(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsRepository = SettingsRepository(applicationContext)

        checkAndRequestPermissions()
        LyricsForegroundService.start(this)

        setContent {
            LyricsStatusTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        playerViewModel = playerViewModel,
                        aiSettingsViewModel = aiSettingsViewModel,
                        customLyricsViewModel = customLyricsViewModel,
                        settingsRepo = settingsRepository
                    )
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@Composable
fun AppNavigation(
    playerViewModel: PlayerViewModel,
    aiSettingsViewModel: AiSettingsViewModel,
    customLyricsViewModel: CustomLyricsViewModel,
    settingsRepo: SettingsRepository
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "now_playing"
    ) {
        composable("now_playing") {
            NowPlayingScreen(
                viewModel = playerViewModel,
                onNavigateToAiSettings = { navController.navigate("ai_settings") },
                onNavigateToCustomLyrics = { navController.navigate("custom_lyrics") },
                onNavigateToSettings = { navController.navigate("settings") }
            )
        }
        composable("ai_settings") {
            AiSettingsScreen(
                viewModel = aiSettingsViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable("custom_lyrics") {
            CustomLyricsScreen(
                viewModel = customLyricsViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable("settings") {
            GeneralSettingsScreen(
                settingsRepo = settingsRepo,
                onNavigateToTokenFetcher = { navController.navigate("token_fetcher") },
                onBack = { navController.popBackStack() }
            )
        }
        composable("token_fetcher") {
            TokenFetcherScreen(
                settingsRepo = settingsRepo,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
