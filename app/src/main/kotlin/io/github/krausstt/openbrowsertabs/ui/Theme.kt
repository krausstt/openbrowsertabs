package io.github.krausstt.openbrowsertabs.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Brand blue, matching the launcher icon and the web demo accent.
private val Blue = Color(0xFF2A78D6)
private val BlueDark = Color(0xFF9EC4F5)

private val Light = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8FA),
    onPrimaryContainer = Color(0xFF102C4F),
    secondary = Color(0xFF4A3AA7),
    secondaryContainer = Color(0xFFE4E0F7),
    onSecondaryContainer = Color(0xFF1F1747),
    background = Color(0xFFFBFBFA),
    surface = Color(0xFFFBFBFA),
    surfaceVariant = Color(0xFFEFEFEB),
    onSurfaceVariant = Color(0xFF54544E),
    outlineVariant = Color(0xFFDEDED8),
)

private val Dark = darkColorScheme(
    primary = BlueDark,
    onPrimary = Color(0xFF0C2745),
    primaryContainer = Color(0xFF1F3E63),
    onPrimaryContainer = Color(0xFFD6E4F7),
    secondary = Color(0xFFB9AFF0),
    secondaryContainer = Color(0xFF2E2660),
    onSecondaryContainer = Color(0xFFE4E0F7),
    background = Color(0xFF131316),
    surface = Color(0xFF131316),
    surfaceVariant = Color(0xFF25252A),
    onSurfaceVariant = Color(0xFFB6B6BD),
    outlineVariant = Color(0xFF34343B),
)

/**
 * Material You where the platform offers it (Android 12+), a fixed brand
 * scheme otherwise — the app should feel native on the device it runs on.
 */
@Composable
fun OpenTabsTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
