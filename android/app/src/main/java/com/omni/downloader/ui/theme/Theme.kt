package com.omni.downloader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = AccentCobalt,
    onPrimary = Color.White,
    primaryContainer = AccentCobaltContainer,
    onPrimaryContainer = AccentCobaltLight,
    secondary = TextSecondary,
    onSecondary = BgDark,
    tertiary = AccentCobaltLight,
    onTertiary = BgDark,
    background = BgDark,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceCard,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle,
    outlineVariant = BorderMedium,
    error = StatusError,
    onError = Color.White
)

@Composable
fun OmniDownloaderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
