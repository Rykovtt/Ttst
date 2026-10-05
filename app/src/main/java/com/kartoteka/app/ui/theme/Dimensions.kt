package com.kartoteka.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Базовая композиция ТЗ — 720 по ширине. Все размеры главного экрана задаются в её единицах
 * и пересчитываются от фактической ширины экрана: u(96) на телефоне шириной 400 dp = 53 dp.
 */
const val BASE_WIDTH = 720f

val LocalLayoutScale = staticCompositionLocalOf { 400f / BASE_WIDTH }

/** Масштаб от ширины экрана; на планшетах композиция не растягивается шире 560 dp. */
@Composable
fun ProvideLayoutScale(content: @Composable () -> Unit) {
    val w = LocalConfiguration.current.screenWidthDp.coerceIn(320, 560)
    CompositionLocalProvider(LocalLayoutScale provides w / BASE_WIDTH, content = content)
}

/** Размер в единицах базовой композиции → dp текущего экрана. */
@Composable
@ReadOnlyComposable
fun u(v: Float): Dp = (v * LocalLayoutScale.current).dp

@Composable
@ReadOnlyComposable
fun u(v: Int): Dp = u(v.toFloat())

/** Размеры из ТЗ (единицы базовой композиции 720). */
object PeopleDims {
    // Шапка
    const val HeaderPad = 36f
    const val HeaderTopRadius = 42f
    const val HeaderBottomRadius = 46f
    const val ControlsTop = 24f
    const val LogoDot = 7f
    const val Avatar = 34f
    const val FilterButton = 40f
    const val FilterLine = 2f
    const val TitleTop = 32f
    const val CounterRadius = 18f
    const val CounterPadH = 12f
    const val CounterPadV = 5f
    const val CounterGap = 12f
    const val SubtitleTop = 6f

    // Поиск
    const val SearchTop = 20f
    const val SearchHeight = 70f
    const val SearchRadius = 26f
    const val SearchBlur = 18f
    const val SearchIcon = 25f
    const val SearchIconLeft = 20f
    const val SearchTextGap = 18f
    const val SearchDividerH = 32f
    const val SearchFilter = 48f

    // Категории
    const val ChipsTop = 20f
    const val ChipHeight = 50f
    const val ChipGap = 10f
    const val ChipPad = 18f
    const val ChipDot = 14f

    // Быстрые действия
    const val QuickTop = 26f
    const val QuickHeight = 160f
    const val QuickGap = 12f
    const val AddTileW = 102f
    const val AddTileH = 160f
    const val AddTileRadius = 34f
    const val AddPlus = 42f
    const val Circle = 116f
    const val CircleRing = 3f
    const val CircleDot = 16f
    const val NoaRadius = 32f
    const val NoaGlow = 18f

    // Список
    const val LetterLeft = 36f
    const val LetterTop = 22f
    const val LetterBottom = 12f
    const val CardHeight = 150f
    const val CardPad = 34f
    const val ContactAvatar = 96f
    const val AvatarDot = 18f
    const val Star = 24f
    const val PillHeight = 38f
    const val PillRadius = 20f
    const val PillIcon = 18f
    const val Action = 52f
    const val ActionIcon = 23f
    const val ActionGap = 12f
    const val Thumb = 52f
    const val ThumbRadius = 14f
    const val ThumbGap = 6f
    const val Chevron = 20f
    const val ChevronLeft = 14f

    // Навигация
    const val NavHeight = 112f
    const val NavRadius = 40f
    const val NavPad = 18f
    const val NavIcon = 26f
    const val NavIconGap = 8f
    const val NavPillW = 84f
    const val NavPillH = 90f
    const val NavPillRadius = 26f
    const val NavDot = 5f

    // Кнопка «+»
    const val Fab = 94f
    const val FabGlowLayer = 116f
    const val FabGlowRadius = 24f
    const val FabLift = 16f
    const val FabInner = 80f
    const val FabIcon = 40f
    const val FabIconStroke = 2f
    const val MenuItem = 54f
    const val MenuIcon = 24f
    const val MenuMinDistance = 72f
}
