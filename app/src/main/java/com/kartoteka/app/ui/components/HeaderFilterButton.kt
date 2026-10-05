package com.kartoteka.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kartoteka.app.ui.animations.pressScale
import com.kartoteka.app.ui.theme.AnimationTokens
import com.kartoteka.app.ui.theme.PeopleDims
import com.kartoteka.app.ui.theme.u

/**
 * Кнопка фильтра в шапке: три белые линии разной длины (кастомная отрисовка), без фона.
 * Пока открыта панель фильтров, линии складываются в «×».
 */
@Composable
fun HeaderFilterButton(open: Boolean, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val src = remember { MutableInteractionSource() }
    val p by animateFloatAsState(if (open) 1f else 0f, tween(AnimationTokens.TabSwitch, easing = AnimationTokens.Move), label = "filterMorph")
    val box = maxOf(u(PeopleDims.FilterButton), 40.dp)
    val stroke = maxOf(u(PeopleDims.FilterLine), 1.5.dp)
    Box(
        modifier.size(box).pressScale(src, 0.9f)
            .clickable(src, indication = null, onClick = onClick)
            .semantics { contentDescription = description; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(u(PeopleDims.FilterButton) * 0.62f)) {
            val w = size.width; val cy = size.height / 2; val gap = size.height * 0.30f
            val st = stroke.toPx(); val c = Color.White
            rotate(45f * p, Offset(w / 2, cy)) {
                val y = cy - gap * (1 - p)
                drawLine(c, Offset(w * (0.02f + 0.1f * p), y), Offset(w * (0.98f - 0.1f * p), y), st, StrokeCap.Round)
            }
            drawLine(c.copy(alpha = 1f - p), Offset(w * 0.28f, cy), Offset(w * 0.98f, cy), st, StrokeCap.Round)
            rotate(-45f * p, Offset(w / 2, cy)) {
                val y = cy + gap * (1 - p)
                drawLine(c, Offset(w * (0.14f - 0.02f * p), y), Offset(w * (0.98f - 0.1f * p), y), st, StrokeCap.Round)
            }
        }
    }
}
