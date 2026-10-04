package com.kartoteka.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kartoteka.app.ui.theme.LocalReducedMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/*
 * «Переливание» рамок RVAULT: свет медленно обходит контур (цикл ~11 с).
 * Одна анимация на всё приложение; фаза читается только при рисовании —
 * экран не пересчитывается, перерисовываются лишь сами рамки.
 */

/** Фаза 0..1 общего «блика». */
val LocalSheenPhase = staticCompositionLocalOf<State<Float>> { mutableFloatStateOf(0.12f) }

@Composable
fun ProvideSheen(content: @Composable () -> Unit) {
    if (LocalReducedMotion.current) {
        CompositionLocalProvider(LocalSheenPhase provides mutableFloatStateOf(0.12f), content = content)
        return
    }
    val phase = rememberInfiniteTransition(label = "sheen")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(11_000, easing = LinearEasing)), label = "sheenPhase")
    CompositionLocalProvider(LocalSheenPhase provides phase, content = content)
}

/** Палитры рамок из макета. */
object SheenColors {
    /** Кольца фото и плитки в тёмной шапке: шампань → бронза → тень. */
    val Champagne = listOf(Color(0xFFF7E1CB), Color(0xFFB98F6F), Color(0xFF3A2E26), Color(0xFF8E7058))
    /** Стеклянные элементы: светлая кромка, гаснущая к низу. */
    val Glass = listOf(Color.White.copy(alpha = 0.34f), Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.04f), Color.White.copy(alpha = 0.16f))
    /** Мягкая кромка для светлого фона. */
    val Soft = listOf(Color.White.copy(alpha = 0.9f), Color(0x22000000), Color(0x11000000), Color.White.copy(alpha = 0.6f))
}

/** Рамка с медленно «переливающимся» градиентом. */
fun Modifier.sheenBorder(width: Dp = 1.dp, shape: Shape, colors: List<Color> = SheenColors.Glass): Modifier = composed {
    val phase = LocalSheenPhase.current
    drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val stroke = Stroke(width.toPx())
        val r = hypot(size.width, size.height) / 2
        onDrawWithContent {
            drawContent()
            val a = (phase.value * 2 * PI - PI * 0.75).toFloat()
            val d = Offset(cos(a) * r, sin(a) * r)
            val brush = Brush.linearGradient(colors, start = center - d, end = center + d)
            drawOutline(outline, brush, style = stroke)
        }
    }
}
