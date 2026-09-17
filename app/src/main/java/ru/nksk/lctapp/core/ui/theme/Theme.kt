package ru.nksk.lctapp.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AdventureColorScheme = darkColorScheme(
    primary = AdventureLime,
    onPrimary = AdventureNight,
    secondary = AdventureLavender,
    background = AdventureNight,
    surface = AdventurePanel,
    surfaceContainer = AdventurePanel,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = AdventureMuted,
)

@Composable
fun LCTAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AdventureColorScheme,
        typography = Typography,
        content = content,
    )
}
