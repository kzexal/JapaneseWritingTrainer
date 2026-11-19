package com.example.japanesewritingtrainer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BrandPrimary = Color(0xFF6667AB)

private val LightColorScheme = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE4E3FF),
    onPrimaryContainer = Color(0xFF1E1E5A),
    secondary = Color(0xFF5F6088),
    secondaryContainer = Color(0xFFE3E1F4),
    tertiary = Color(0xFF3E8E6B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD5F0E1),
    onTertiaryContainer = Color(0xFF102F22),
    error = Color(0xFFBA1A1A),
    background = Color(0xFFFBF9FF),
    surface = Color(0xFFFBF9FF),
    surfaceContainer = Color(0xFFF1EFF8),
    surfaceContainerHigh = Color(0xFFEAE8F3),
    onBackground = Color(0xFF1B1B21),
    onSurface = Color(0xFF1B1B21),
    onSurfaceVariant = Color(0xFF5C5C6B),
    outline = Color(0xFF7A7A8C)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFBFC0FF),
    onPrimary = Color(0xFF2E2F72),
    primaryContainer = Color(0xFF4B4C91),
    onPrimaryContainer = Color(0xFFE2E0FF),
    secondary = Color(0xFFC5C4E0),
    secondaryContainer = Color(0xFF444560),
    tertiary = Color(0xFF8FD5B0),
    onTertiary = Color(0xFF153727),
    tertiaryContainer = Color(0xFF28513D),
    onTertiaryContainer = Color(0xFFB0F0CA),
    error = Color(0xFFFFB4AB),
    background = Color(0xFF131318),
    surface = Color(0xFF131318),
    surfaceContainer = Color(0xFF1F1F25),
    surfaceContainerHigh = Color(0xFF2A2A31),
    onBackground = Color(0xFFE4E1E9),
    onSurface = Color(0xFFE4E1E9),
    onSurfaceVariant = Color(0xFFC7C5D0),
    outline = Color(0xFF918F9A)
)

@Composable
fun JapaneseWritingTrainerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
