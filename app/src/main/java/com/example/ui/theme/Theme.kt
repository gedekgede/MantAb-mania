package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = OnEmeraldContainer,
    secondary = TealSecondary,
    onSecondary = Color.White,
    secondaryContainer = TealContainer,
    onSecondaryContainer = OnTealContainer,
    tertiary = AmberAccent,
    onTertiary = Color.White,
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = OnAmberContainer,
    background = SurfaceLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceCardLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceMutedLight,
    outline = OutlineLight,
    error = StatusError,
    onError = Color.White,
    errorContainer = StatusErrorBg,
    onErrorContainer = Color(0xFF7F1D1D)
)

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldPrimaryLight,
    onPrimary = Color(0xFF00382E),
    primaryContainer = EmeraldContainerDark,
    onPrimaryContainer = Color(0xFFA7F3D0),
    secondary = TealSecondaryLight,
    onSecondary = Color(0xFF05332C),
    secondaryContainer = Color(0xFF134E45),
    onSecondaryContainer = Color(0xFFCCFBF1),
    tertiary = AmberAccentLight,
    onTertiary = Color(0xFF451A03),
    tertiaryContainer = AmberContainerDark,
    onTertiaryContainer = Color(0xFFFEF3C7),
    background = SurfaceDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceCardDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceMutedDark,
    outline = OutlineDark,
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2)
)

val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun KawalSalesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
