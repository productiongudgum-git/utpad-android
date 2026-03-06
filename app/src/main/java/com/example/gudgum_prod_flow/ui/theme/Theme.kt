package com.example.gudgum_prod_flow.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = md_theme_light_primary,
    onPrimary = md_theme_light_onPrimary,
    primaryContainer = md_theme_light_primaryContainer,
    tertiary = WarningAmber,
    onTertiary = Color.White,
    tertiaryContainer = ManuFlowWarningContainer,
    background = md_theme_light_background,
    surface = md_theme_light_surface,
    onSurface = md_theme_light_onSurface,
    onSurfaceVariant = md_theme_light_onSurfaceVariant,
    surfaceVariant = md_theme_light_surfaceVariant,
    error = md_theme_light_error,
    errorContainer = md_theme_light_errorContainer,
    outline = md_theme_light_outline
)

private val DarkColorScheme = darkColorScheme(
    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    tertiary = WarningAmber,
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF3A2F00),
    background = md_theme_dark_background,
    surface = md_theme_dark_surface,
    onSurface = md_theme_dark_onSurface,
    onSurfaceVariant = md_theme_dark_onSurfaceVariant,
    surfaceVariant = md_theme_dark_surfaceVariant,
    error = md_theme_dark_error,
    errorContainer = md_theme_dark_errorContainer,
    outline = md_theme_dark_outline
)

@Composable
fun GudGumProdFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
