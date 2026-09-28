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

// Монохромная палитра: серый фон, белые карточки, чёрные акценты.
private val Light = lightColorScheme(
    primary = Color(0xFF111114),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFECECF0),
    onPrimaryContainer = Color(0xFF111114),
    secondary = Color(0xFF6B6B72),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFECECF0),
    onSecondaryContainer = Color(0xFF111114),
    tertiary = Color(0xFFE0406E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFDE6EE),
    onTertiaryContainer = Color(0xFF7A1033),
    background = Color(0xFFF2F2F5),
    onBackground = Color(0xFF111114),
    surface = Color(0xFFF2F2F5),
    onSurface = Color(0xFF111114),
    surfaceVariant = Color(0xFFE6E6EA),
    onSurfaceVariant = Color(0xFF6B6B72),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFEEEEF2),
    outline = Color(0xFF8E8E94),
    outlineVariant = Color(0xFFE2E2E7),
    error = Color(0xFFE5383B),
    errorContainer = Color(0xFFFDE8E8),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFF4F4F6),
    onPrimary = Color(0xFF111114),
    primaryContainer = Color(0xFF2C2C30),
    onPrimaryContainer = Color(0xFFF4F4F6),
    secondary = Color(0xFFA1A1A8),
    onSecondary = Color(0xFF111114),
    secondaryContainer = Color(0xFF2C2C30),
    onSecondaryContainer = Color(0xFFF4F4F6),
    tertiary = Color(0xFFFF6F97),
    onTertiary = Color(0xFF3A0716),
    tertiaryContainer = Color(0xFF4A1426),
    onTertiaryContainer = Color(0xFFFFD9E3),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF4F4F6),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFF4F4F6),
    surfaceVariant = Color(0xFF2C2C30),
    onSurfaceVariant = Color(0xFFA1A1A8),
    surfaceContainerLowest = Color(0xFF0B0B0D),
    surfaceContainerLow = Color(0xFF1C1C1F),
    surfaceContainer = Color(0xFF1C1C1F),
    surfaceContainerHigh = Color(0xFF2C2C30),
    surfaceContainerHighest = Color(0xFF3A3A3F),
    outline = Color(0xFF7C7C84),
    outlineVariant = Color(0xFF2C2C30),
    error = Color(0xFFFF6B6B),
    errorContainer = Color(0xFF4A1515),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, letterSpacing = (-0.5).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
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
