package com.kartoteka.app.ui.animations

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.kartoteka.app.ui.theme.AnimationTokens

/** Нажатие: сжатие до [pressed] за 100 мс, возврат за 180 мс. Значение читается при рисовании. */
@Composable
fun Modifier.pressScale(source: MutableInteractionSource, pressed: Float = 0.94f): Modifier {
    val isPressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(
        if (isPressed) pressed else 1f,
        tween(if (isPressed) AnimationTokens.Press else AnimationTokens.Release, easing = AnimationTokens.Move),
        label = "pressScale",
    )
    return graphicsLayer { scaleX = s; scaleY = s }
}
