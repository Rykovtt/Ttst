package com.kartoteka.app.ui.theme

import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kartoteka.app.R

/*
 * Дизайн-система RVAULT.
 * Тёплый молочный фон вместо белого, графит для акцентов, персиковый — фирменный акцент,
 * лаванда / лайм / коралл — категории и статусы. Тёмная тема — своя система поверхностей, не инверсия.
 */

// ---- Палитра ----
object Rv {
    val Ink = Color(0xFF151517)
    val Graphite = Color(0xFF1E1E22)
    val Milk = Color(0xFFF4F1EC)
    val Paper = Color(0xFFFCFAF7)
    val Sand = Color(0xFFEAE5DE)
    val Stone = Color(0xFF8C867E)
    val Peach = Color(0xFFF2A77E)
    val PeachDeep = Color(0xFFE8804C)
    val PeachSoft = Color(0xFFFBE4D6)
    val Lavender = Color(0xFF7D6CF2)
    val LavenderSoft = Color(0xFFE9E5FF)
    val Lime = Color(0xFF3FC46B)
    val LimeSoft = Color(0xFFDDF5E4)
    val Acid = Color(0xFFC8F25A)
    val Coral = Color(0xFFFF7A66)
    val CoralSoft = Color(0xFFFFE3DE)
    val Cobalt = Color(0xFF3B5BFF)

    /** Поверхности «тёмного героя» — шапки, Ноа, карта. Одинаковы в обеих темах. */
    val HeroBg = Color(0xFF0F0F11)
    val HeroSurface = Color(0xFF1C1C20)
    val HeroLine = Color(0x1FFFFFFF)
    val HeroText = Color(0xFFF6F3EE)
    val HeroMuted = Color(0xFF9C978F)
}

private val Light = lightColorScheme(
    primary = Rv.Ink,
    onPrimary = Color.White,
    primaryContainer = Rv.Sand,
    onPrimaryContainer = Rv.Ink,
    secondary = Rv.Lavender,
    onSecondary = Color.White,
    secondaryContainer = Rv.LavenderSoft,
    onSecondaryContainer = Color(0xFF2A1F7A),
    tertiary = Rv.PeachDeep,
    onTertiary = Color.White,
    tertiaryContainer = Rv.PeachSoft,
    onTertiaryContainer = Color(0xFF7A3512),
    background = Rv.Milk,
    onBackground = Rv.Ink,
    surface = Rv.Milk,
    onSurface = Rv.Ink,
    surfaceVariant = Color(0xFFE6E0D8),
    onSurfaceVariant = Rv.Stone,
    surfaceContainerLowest = Rv.Paper,
    surfaceContainerLow = Rv.Paper,
    surfaceContainer = Rv.Paper,
    surfaceContainerHigh = Color(0xFFF7F4F0),
    surfaceContainerHighest = Rv.Sand,
    outline = Color(0xFFA9A299),
    outlineVariant = Color(0xFFE7E1D9),
    error = Color(0xFFE5483B),
    errorContainer = Color(0xFFFDE5E1),
    inverseSurface = Rv.Ink,
    inverseOnSurface = Rv.Milk,
)

private val Dark = darkColorScheme(
    primary = Color(0xFFF6F3EE),
    onPrimary = Rv.Ink,
    primaryContainer = Color(0xFF2A2A2F),
    onPrimaryContainer = Color(0xFFF6F3EE),
    secondary = Color(0xFFA295FF),
    onSecondary = Color(0xFF1B1356),
    secondaryContainer = Color(0xFF2B2550),
    onSecondaryContainer = Color(0xFFE2DDFF),
    tertiary = Rv.Peach,
    onTertiary = Color(0xFF3A1704),
    tertiaryContainer = Color(0xFF4A2A18),
    onTertiaryContainer = Color(0xFFFFDCC8),
    background = Color(0xFF0C0C0E),
    onBackground = Color(0xFFF2EFEA),
    surface = Color(0xFF0C0C0E),
    onSurface = Color(0xFFF2EFEA),
    surfaceVariant = Color(0xFF26262B),
    onSurfaceVariant = Color(0xFF9A958E),
    surfaceContainerLowest = Color(0xFF141417),
    surfaceContainerLow = Color(0xFF17171A),
    surfaceContainer = Color(0xFF1B1B1F),
    surfaceContainerHigh = Color(0xFF212126),
    surfaceContainerHighest = Color(0xFF2A2A30),
    outline = Color(0xFF6E6A64),
    outlineVariant = Color(0xFF26262B),
    error = Color(0xFFFF7A6B),
    errorContainer = Color(0xFF4A1A15),
    inverseSurface = Color(0xFFF2EFEA),
    inverseOnSurface = Rv.Ink,
)

// ---- Типографика: Inter (как системный шрифт макета) + Montserrat для словесного знака ----
@OptIn(ExperimentalTextApi::class)
private fun inter(w: Int) = Font(
    R.font.inter, FontWeight(w),
    variationSettings = FontVariation.Settings(FontVariation.weight(w), FontVariation.Setting("opsz", 28f)),
)

@OptIn(ExperimentalTextApi::class)
private fun montserrat(w: Int) = Font(
    R.font.montserrat, FontWeight(w),
    variationSettings = FontVariation.Settings(FontVariation.weight(w)),
)

val Inter = FontFamily(inter(300), inter(400), inter(500), inter(600), inter(700), inter(800))

/** Шрифт тонкого логотипа «RVAULT». */
val Wordmark = FontFamily(montserrat(200), montserrat(300), montserrat(400))

/** Совместимость: старое имя основного шрифта. */
val Manrope = Inter

private val AppTypography = Typography().let { t ->
    fun TextStyle.m(w: FontWeight, size: Int? = null, ls: Float? = null, lh: Int? = null) = copy(
        fontFamily = Inter, fontWeight = w,
        fontSize = size?.sp ?: fontSize,
        letterSpacing = ls?.sp ?: letterSpacing,
        lineHeight = lh?.sp ?: lineHeight,
    )
    t.copy(
        displayLarge = t.displayLarge.m(FontWeight.Bold, ls = -2f),
        displayMedium = t.displayMedium.m(FontWeight.Bold, ls = -1.6f),
        displaySmall = t.displaySmall.m(FontWeight.Bold, 36, -1.2f, 40),
        headlineLarge = t.headlineLarge.m(FontWeight.Bold, 34, -1.1f, 38),
        headlineMedium = t.headlineMedium.m(FontWeight.Bold, 28, -0.8f, 32),
        headlineSmall = t.headlineSmall.m(FontWeight.SemiBold, 22, -0.4f),
        titleLarge = t.titleLarge.m(FontWeight.SemiBold, 20, -0.4f),
        titleMedium = t.titleMedium.m(FontWeight.SemiBold, 16, -0.2f),
        titleSmall = t.titleSmall.m(FontWeight.SemiBold, 14, -0.1f),
        bodyLarge = t.bodyLarge.m(FontWeight.Normal, 16, -0.2f),
        bodyMedium = t.bodyMedium.m(FontWeight.Normal, 14, -0.1f),
        bodySmall = t.bodySmall.m(FontWeight.Normal, 12, 0f),
        labelLarge = t.labelLarge.m(FontWeight.Medium, 14, -0.1f),
        labelMedium = t.labelMedium.m(FontWeight.Medium, 12, 0f),
        labelSmall = t.labelSmall.m(FontWeight.Medium, 11, 0.1f),
    )
}

/** Цифры для времени, дат и статистики — крупные и плотные. */
val NumberStyle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp)

// ---- Скругления ----
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

object Radius {
    val chip = 999.dp
    val field = 18.dp
    val card = 26.dp
    val hero = 34.dp
}

// ---- Движение ----
object Motion {
    const val MICRO = 150
    const val STANDARD = 280
    const val EMPHASIZED = 460
    /** Плавный старт и мягкая посадка — фирменная кривая. */
    val Ease = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EaseOut = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
}

/** Системная настройка «Убрать анимацию». */
val LocalReducedMotion = staticCompositionLocalOf { false }

@Composable
@ReadOnlyComposable
fun <T> motion(ms: Int = Motion.STANDARD, delay: Int = 0): FiniteAnimationSpec<T> =
    if (LocalReducedMotion.current) snap() else tween(ms, delay, Motion.Ease)

// ---- Тактильный отклик ----
@Immutable
class Haptics(private val view: android.view.View) {
    /** Лёгкий щелчок — выбор фильтра, переключатель. */
    fun tick() { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
    /** Подтверждение — сохранение, выполненное действие. */
    fun confirm() {
        view.performHapticFeedback(
            if (android.os.Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
        )
    }
    /** Длинное нажатие, начало жеста. */
    fun heavy() { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}

@Composable
fun KartotekaTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val reduced = remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
            .getOrDefault(false)
    }
    CompositionLocalProvider(LocalReducedMotion provides reduced) {
        MaterialTheme(
            colorScheme = if (isSystemInDarkTheme()) Dark else Light,
            typography = AppTypography,
            shapes = AppShapes,
        ) { com.kartoteka.app.ui.components.ProvideSheen(content) }
    }
}

/** Палитра для групп и аватаров без фото — приглушённая, в тон системе. */
val AccentPalette = listOf(
    0xFF7D6CF2, 0xFF3FA978, 0xFFE8804C, 0xFFD9A441, 0xFF4A74E8,
    0xFFC0587E, 0xFF7FA34A, 0xFF9A6BD1, 0xFF2E9C90, 0xFF6E7B85,
)

val NameStyle = TextStyle(fontWeight = FontWeight.Bold)
