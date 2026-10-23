package com.example.personalmusicplayer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

private val ITunesColors = darkColorScheme(
    primary = Color(0xFF4A90D9),
    onPrimary = Color.White,
    secondary = Color(0xFFB0B0B0),
    onSecondary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF0E0E0E),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1C1C1C),
    onSurfaceVariant = Color(0xFFBBBBBB),
    primaryContainer = Color(0xFF2A2A2A),
    onPrimaryContainer = Color.White,
    secondaryContainer = Color(0xFF2A2A2A),
    onSecondaryContainer = Color.White,
    surfaceTint = Color(0xFF4A90D9),
    outline = Color(0xFF777777),
    outlineVariant = Color(0xFF333333),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0E0E0E),
    surfaceContainer = Color(0xFF151515),
    surfaceContainerHigh = Color(0xFF1C1C1C),
    surfaceContainerHighest = Color(0xFF242424)
)

class MainActivity : ComponentActivity() {
    private val vm: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        setContent { MaterialTheme(colorScheme = ITunesColors) { AppRoot(vm) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(i: Intent?) {
        if (i?.getBooleanExtra("open_player", false) == true) vm.requestPlayerTab.value = true
        if (i?.getBooleanExtra("shuffle_toggle", false) == true) vm.toggleShuffle()
    }
}