package com.kartoteka.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import com.kartoteka.app.ui.theme.LocalReducedMotion
import com.kartoteka.app.ui.theme.Rv
import kotlin.math.cos
import kotlin.math.sin

/** Состояние ассистента, которое показывает сфера. */
enum class OrbState { IDLE, LISTENING, THINKING, SUCCESS, ERROR }

/**
 * Живая сфера Ноа: перламутровый шар из нескольких цветовых пятен.
 * Ожидание — едва заметное дыхание; слушает — пульсирует от уровня звука;
 * думает — пятна вращаются быстрее; успех/ошибка — короткая вспышка цвета.
 */
@Composable
fun NoaOrb(modifier: Modifier, state: OrbState = OrbState.IDLE, level: Float = 0f) {
    val reduced = LocalReducedMotion.current
    val inf = rememberInfiniteTransition(label = "orb")
    val spin by inf.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(if (state == OrbState.THINKING) 2400 else 14000, easing = LinearEasing)), label = "spin",
    )
    val breath by inf.animateFloat(0.97f, 1.03f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "breath")
    val voice by animateFloatAsState(if (state == OrbState.LISTENING) 1f + level.coerceIn(0f, 1f) * 0.12f else 1f, tween(90), label = "voice")
    val flash by animateFloatAsState(if (state == OrbState.SUCCESS || state == OrbState.ERROR) 1f else 0f, tween(260), label = "flash")
    val scale = if (reduced) 1f else breath * voice
    val angle = if (reduced) 30f else spin

    Canvas(modifier) {
        val r = size.minDimension / 2 * 0.86f * scale
        val c = center
        // Мягкий ореол.
        val halo = when (state) {
            OrbState.ERROR -> Rv.Coral
            OrbState.SUCCESS -> Rv.Lime
            OrbState.LISTENING -> Color(0xFF8E7CFF)
            else -> Color(0xFF7FA8FF)
        }
        drawCircle(Brush.radialGradient(listOf(halo.copy(alpha = 0.35f + flash * 0.25f), Color.Transparent), c, r * 1.35f), r * 1.35f, c)
        // Основа шара.
        drawCircle(Brush.radialGradient(listOf(Color(0xFFDCE6FF), Color(0xFF6E7BFF), Color(0xFF1B1840)), c + Offset(-r * 0.25f, -r * 0.3f), r * 1.4f), r, c)
        // Цветные пятна, вращающиеся внутри.
        rotate(angle, c) {
            val spots = listOf(
                Triple(Color(0xFFFF8FD8), 0.0, 0.55f),
                Triple(Color(0xFF6CE3FF), 2.1, 0.5f),
                Triple(Rv.Peach, 4.2, 0.45f),
            )
            spots.forEach { (col, a, s) ->
                val p = c + Offset((cos(a) * r * 0.42f).toFloat(), (sin(a) * r * 0.42f).toFloat())
                drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.75f), Color.Transparent), p, r * s), r * s, p)
            }
        }
        // Блик.
        val hl = c + Offset(-r * 0.35f, -r * 0.42f)
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.85f), Color.Transparent), hl, r * 0.38f), r * 0.38f, hl)
        if (flash > 0f) drawCircle(halo.copy(alpha = 0.25f * flash), r, c)
    }
}
