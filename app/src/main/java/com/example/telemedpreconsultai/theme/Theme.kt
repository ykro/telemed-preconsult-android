package com.example.telemedpreconsultai.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ClinicalLightColorScheme = lightColorScheme(
    primary = Sky600,
    onPrimary = Color.White,
    primaryContainer = Sky50,
    onPrimaryContainer = Sky600,
    secondary = Teal600,
    onSecondary = Color.White,
    secondaryContainer = Teal50,
    onSecondaryContainer = Teal600,
    tertiary = Sky600,
    error = Rose600,
    onError = Color.White,
    errorContainer = Rose50,
    onErrorContainer = Rose600,
    background = ClinicalBackground,
    onBackground = Slate900,
    surface = ClinicalSurface,
    onSurface = Slate900,
    surfaceVariant = ClinicalSubtleBg,
    onSurfaceVariant = Slate700,
    outline = ClinicalBorder
)

@Composable
fun TelemedPreconsultAITheme(
    content: @Composable () -> Unit
) {
    // Strictly enforced Light Mode (never uses darkColorScheme or dynamic dark colors)
    MaterialTheme(
        colorScheme = ClinicalLightColorScheme,
        typography = Typography,
        content = content
    )
}
