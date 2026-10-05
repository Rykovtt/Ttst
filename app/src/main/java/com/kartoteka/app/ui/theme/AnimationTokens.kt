package com.kartoteka.app.ui.theme

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing

/** Единые параметры анимаций — таблица ТЗ. */
object AnimationTokens {
    const val Press = 100
    const val Release = 180
    const val TabSwitch = 220
    const val MenuOpen = 260
    const val MenuClose = 180
    const val CardAppear = 280
    const val CardStagger = 35
    const val Scrim = 180
    const val SearchFocus = 180
    const val Category = 220
    const val MenuStagger = 45
    const val SearchDebounce = 150L
    const val FabIdleHalf = 1600
    const val FabIconTurn = 220
    const val MenuSpringDamping = 0.72f

    /** Стандартное перемещение. */
    val Move = FastOutSlowInEasing
    /** Появление. */
    val Enter = LinearOutSlowInEasing
    /** Исчезновение. */
    val Exit = FastOutLinearInEasing
}
