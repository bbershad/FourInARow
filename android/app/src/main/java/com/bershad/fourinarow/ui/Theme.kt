package com.bershad.fourinarow.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The handful of colours the game actually needs. Material's scheme covers the buttons and
 * cards; the board, the discs and their rims are their own thing and live here so the light
 * and dark versions sit side by side and stay in step.
 */
data class Palette(
    val background: Color,
    val surface: Color,
    val board: Color,
    val boardEdge: Color,
    val hole: Color,
    val red: Color,
    val redRim: Color,
    val yellow: Color,
    val yellowRim: Color,
    val text: Color,
    val textDim: Color,
    val accent: Color,
)

private val LightPalette = Palette(
    background = Color(0xFFEDF1F7),
    surface = Color(0xFFFFFFFF),
    board = Color(0xFF1E56C7),
    boardEdge = Color(0xFF17439C),
    hole = Color(0xFFE3E9F2),
    red = Color(0xFFE4443B),
    redRim = Color(0xFFB02D25),
    yellow = Color(0xFFF5C023),
    yellowRim = Color(0xFFC1901A),
    text = Color(0xFF11161F),
    textDim = Color(0xFF5C6675),
    accent = Color(0xFF1E56C7),
)

private val DarkPalette = Palette(
    background = Color(0xFF0E1117),
    surface = Color(0xFF181D26),
    board = Color(0xFF1B4497),
    boardEdge = Color(0xFF12326F),
    hole = Color(0xFF0E1117),
    red = Color(0xFFE9534A),
    redRim = Color(0xFF9C2A23),
    yellow = Color(0xFFF7C93C),
    yellowRim = Color(0xFFB0821A),
    text = Color(0xFFEDF1F7),
    textDim = Color(0xFF98A3B3),
    accent = Color(0xFF6E9BFF),
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

private val AppTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 15.sp),
    bodySmall = TextStyle(fontSize = 13.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun FourInARowTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = Color(0xFF06121F),
            background = palette.background,
            onBackground = palette.text,
            surface = palette.surface,
            onSurface = palette.text,
            surfaceVariant = Color(0xFF232A36),
            onSurfaceVariant = palette.textDim,
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = Color.White,
            background = palette.background,
            onBackground = palette.text,
            surface = palette.surface,
            onSurface = palette.text,
            surfaceVariant = Color(0xFFDDE4EE),
            onSurfaceVariant = palette.textDim,
        )
    }
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
    }
}
