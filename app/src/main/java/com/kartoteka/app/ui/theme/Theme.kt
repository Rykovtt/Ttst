package com.kartoteka.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Light = lightColorScheme(
    primary = Color(0xFF4F3FD1),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4DFFF),
    onPrimaryContainer = Color(0xFF16006E),
    secondary = Color(0xFF5E5A7D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4DEFF),
    onSecondaryContainer = Color(0xFF1B1736),
    tertiary = Color(0xFFB4436C),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD9E2),
    onTertiaryContainer = Color(0xFF3E0021),
    background = Color(0xFFF8F6FF),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFF8F6FF),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE5E1F0),
    onSurfaceVariant = Color(0xFF47464F),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF3F0FC),
    surfaceContainer = Color(0xFFEDEAF7),
    surfaceContainerHigh = Color(0xFFE7E4F2),
    surfaceContainerHighest = Color(0xFFE1DEEC),
    outline = Color(0xFF787680),
    outlineVariant = Color(0xFFC9C5D4),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFC6BFFF),
    onPrimary = Color(0xFF2A1A9F),
    primaryContainer = Color(0xFF3F2DB8),
    onPrimaryContainer = Color(0xFFE4DFFF),
    secondary = Color(0xFFC8C3EA),
    onSecondary = Color(0xFF302C4C),
    secondaryContainer = Color(0xFF474364),
    onSecondaryContainer = Color(0xFFE4DEFF),
    tertiary = Color(0xFFFFB0C8),
    onTertiary = Color(0xFF5E1138),
    tertiaryContainer = Color(0xFF7B2A4F),
    onTertiaryContainer = Color(0xFFFFD9E2),
    background = Color(0xFF121218),
    onBackground = Color(0xFFE5E1EA),
    surface = Color(0xFF121218),
    onSurface = Color(0xFFE5E1EA),
    surfaceVariant = Color(0xFF47464F),
    onSurfaceVariant = Color(0xFFC9C5D4),
    surfaceContainerLowest = Color(0xFF0D0D12),
    surfaceContainerLow = Color(0xFF1B1A22),
    surfaceContainer = Color(0xFF1F1E27),
    surfaceContainerHigh = Color(0xFF2A2931),
    surfaceContainerHighest = Color(0xFF35343C),
    outline = Color(0xFF928F9E),
    outlineVariant = Color(0xFF47464F),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun KartotekaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

/** Палитра для групп и аватаров без фото. */
val AccentPalette = listOf(
    0xFF5B4BD6, 0xFF0E8A7E, 0xFFD9544D, 0xFFE08E2B, 0xFF2F7ED8,
    0xFFB4436C, 0xFF6C9A2F, 0xFF8E44AD, 0xFF00897B, 0xFF546E7A,
)

val NameStyle = TextStyle(fontWeight = FontWeight.SemiBold)
