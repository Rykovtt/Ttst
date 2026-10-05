package com.kartoteka.app.ui.animations

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.kartoteka.app.ui.theme.AnimationTokens
import com.kartoteka.app.ui.theme.LocalReducedMotion
import kotlinx.coroutines.delay

/**
 * Появление строки: fadeIn + сдвиг снизу за 280 мс, задержка 35 мс на каждую следующую.
 * Уже показанные элементы ([seen]) при прокрутке повторно не анимируются.
 */
@Composable
fun Modifier.appearOnce(key: Any, order: Int, seen: MutableSet<Any>): Modifier {
    val reduced = LocalReducedMotion.current
    val first = remember(key) { !reduced && seen.add(key) }
    val a = remember(key) { Animatable(if (first) 0f else 1f) }
    LaunchedEffect(key) {
        if (first) {
            delay(order.coerceIn(0, 12) * AnimationTokens.CardStagger.toLong())
            a.animateTo(1f, tween(AnimationTokens.CardAppear, easing = AnimationTokens.Enter))
        }
    }
    val shift = with(LocalDensity.current) { 16.dp.toPx() }
    return graphicsLayer { alpha = a.value; translationY = (1f - a.value) * shift }
}
