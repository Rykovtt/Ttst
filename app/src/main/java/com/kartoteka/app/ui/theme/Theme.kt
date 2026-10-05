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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
    val Ink = Color(0xFF111113)
    val Graphite = Color(0xFF1E1E22)
    val Milk = Color(0xFFF5F2EC)
    val Paper = Color(0xFFFBF9F5)
    val Sand = Color(0xFFEDEAE6)
    val Stone = Color(0xFF77777C)
    val Peach = Color(0xFFF3CFA7)
    val PeachDeep = Color(0xFFF18B35)
    val PeachSoft = Color(0xFFF5E9DC)
    val Lavender = Color(0xFF8056ED)
    val LavenderSoft = Color(0xFFE9E5FF)
    val Lime = Color(0xFF35D88C)
    val LimeSoft = Color(0xFFDDF5E4)
    val Acid = Color(0xFFC8F25A)
    val Coral = Color(0xFFFF7A66)
    val CoralSoft = Color(0xFFFFE3DE)
    val Cobalt = Color(0xFF3B5BFF)

    /** Поверхности «тёмного героя» — шапки, Ноа, карта. Одинаковы в обеих темах. */
    val HeroBg = Color(0xFF101012)
    val HeroSurface = Color(0xFF242326)
    val HeroLine = Color(0x1FFFFFFF)
    val HeroText = Color(0xFFF8F7F4)
    val HeroMuted = Color(0xFFA5A3A9)
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
    tertiary = Color(0xFFD9893F),
    onTertiary = Color.White,
    tertiaryContainer = Rv.PeachSoft,
    onTertiaryContainer = Color(0xFF181818),
    background = Color(0xFFF5F2EC),
    onBackground = Rv.Ink,
    surface = Color(0xFFF5F2EC),
    onSurface = Rv.Ink,
    surfaceVariant = Color(0xFFEAE7E3),
    onSurfaceVariant = Color(0xFF77777C),
    surfaceContainerLowest = Rv.Paper,
    surfaceContainerLow = Rv.Paper,
    surfaceContainer = Rv.Paper,
    surfaceContainerHigh = Color(0xFFF1EEE9),
    surfaceContainerHighest = Rv.Sand,
    outline = Color(0xFFA9A299),
    outlineVariant = Color(0xFFE5E1DA),
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

// ---- Типографика: Manrope (локальные файлы начертаний, см. Typography.kt) ----
/** Совместимость со старыми именами: всё приложение набрано Manrope. */
val Inter = ManropeFamily
val Wordmark = ManropeFamily
val Manrope = ManropeFamily

private val AppTypography = Typography().let { t ->
    fun TextStyle.m(w: FontWeight, size: Number? = null, ls: Float? = null, lh: Number? = null) = copy(
        fontFamily = ManropeFamily, fontWeight = w,
        fontSize = size?.toFloat()?.sp ?: fontSize,
        letterSpacing = ls?.sp ?: letterSpacing,
        lineHeight = lh?.toFloat()?.sp ?: lineHeight,
    )
    // Шкала — та же, что на главном экране (PeopleType): плотный Manrope, крупные заголовки ExtraBold.
    t.copy(
        displayLarge = t.displayLarge.m(FontWeight.ExtraBold, ls = -2f),
        displayMedium = t.displayMedium.m(FontWeight.ExtraBold, ls = -1.6f),
        displaySmall = t.displaySmall.m(FontWeight.ExtraBold, 36, -1.2f, 40),
        headlineLarge = t.headlineLarge.m(FontWeight.ExtraBold, 34, -1f, 38),
        headlineMedium = t.headlineMedium.m(FontWeight.ExtraBold, 26, -0.6f, 30),
        headlineSmall = t.headlineSmall.m(FontWeight.Bold, 20, -0.3f, 25),
        titleLarge = t.titleLarge.m(FontWeight.Bold, 17, -0.2f, 22),
        titleMedium = t.titleMedium.m(FontWeight.Bold, 15, -0.1f, 20),
        titleSmall = t.titleSmall.m(FontWeight.SemiBold, 13, 0f, 17),
        bodyLarge = t.bodyLarge.m(FontWeight.Normal, 14, 0f, 19),
        bodyMedium = t.bodyMedium.m(FontWeight.Normal, 13, 0f, 17),
        bodySmall = t.bodySmall.m(FontWeight.Normal, 11.5f, 0f, 15),
        labelLarge = t.labelLarge.m(FontWeight.Medium, 13, 0f, 16),
        labelMedium = t.labelMedium.m(FontWeight.Medium, 11.5f, 0f, 14),
        labelSmall = t.labelSmall.m(FontWeight.Medium, 10.5f, 0.1f, 13),
    )
}

/** Цифры для времени, дат и статистики — крупные и плотные. */
val NumberStyle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp)

// ---- Скругления ----
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
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
        // Масштаб из настроек: «интерфейс» увеличивает всё (плотность), «текст» — только шрифт.
        val settings = (context.applicationContext as? com.kartoteka.app.KartotekaApp)?.settings
        val ui = settings?.uiScale?.value?.collectAsState()?.value?.toFloatOrNull()?.coerceIn(0.8f, 1.4f) ?: 1f
        val text = settings?.textScale?.value?.collectAsState()?.value?.toFloatOrNull()?.coerceIn(0.8f, 1.5f) ?: 1f
        val base = androidx.compose.ui.platform.LocalDensity.current
        CompositionLocalProvider(
            androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(base.density * ui, base.fontScale * text),
        ) {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) Dark else Light,
                typography = AppTypography,
                shapes = AppShapes,
            ) { ProvideLayoutScale { com.kartoteka.app.ui.components.ProvideSheen(content) } }
        }
    }
}

/** Палитра для групп и аватаров без фото — приглушённая, в тон системе. */
val AccentPalette = listOf(
    0xFF7D6CF2, 0xFF3FA978, 0xFFE8804C, 0xFFD9A441, 0xFF4A74E8,
    0xFFC0587E, 0xFF7FA34A, 0xFF9A6BD1, 0xFF2E9C90, 0xFF6E7B85,
)

val NameStyle = TextStyle(fontWeight = FontWeight.Bold)
