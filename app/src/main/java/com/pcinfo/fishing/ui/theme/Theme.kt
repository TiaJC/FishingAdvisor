package com.pcinfo.fishing.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 主色：水蓝 */
val WaterBlue = Color(0xFF0B5FFF)
val DeepBlue = Color(0xFF08429E)
val GrassGreen = Color(0xFF2E7D32)
val WarnAmber = Color(0xFFEF6C00)
val DangerRed = Color(0xFFC62828)

private val LightColors = lightColorScheme(
    primary = WaterBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4FF),
    onPrimaryContainer = Color(0xFF001847),
    secondary = GrassGreen,
    onSecondary = Color.White,
    background = Color(0xFFF5F7FB),
    onBackground = Color(0xFF101828),
    surface = Color.White,
    onSurface = Color(0xFF101828),
    surfaceVariant = Color(0xFFEEF2F9),
    onSurfaceVariant = Color(0xFF475467),
    error = DangerRed
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AB4FF),
    onPrimary = Color(0xFF00204D),
    secondary = Color(0xFF81C784),
    background = Color(0xFF0E1420),
    onBackground = Color(0xFFE6EAF2),
    surface = Color(0xFF151C29),
    onSurface = Color(0xFFE6EAF2),
    surfaceVariant = Color(0xFF1E2634),
    onSurfaceVariant = Color(0xFFB6C0D0),
    error = Color(0xFFFF8A80)
)

@Composable
fun FishingAdvisorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content
    )
}
