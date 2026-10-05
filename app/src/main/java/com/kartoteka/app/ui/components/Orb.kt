package com.kartoteka.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import com.kartoteka.app.ui.theme.LocalReducedMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/** Состояние ассистента, которое показывает сфера. */
enum class OrbState { IDLE, LISTENING, THINKING, SUCCESS, ERROR }

private val Blue = Color(0xFF7EA2FF)
private val BlueDeep = Color(0xFF3B4FD8)
private val Gold = Color(0xFFFFCB8E)
private val GoldPale = Color(0xFFFFE6C4)
private val Night = Color(0xFF0B0E2A)

/**
 * Сфера Ноа: прозрачный стеклянный шар со светящейся кромкой (голубой ↔ золотой), внутри — закрученные
 * ленты «дыма» вокруг светящегося ядра, вокруг — орбиты из золотых частиц.
 * Время идёт непрерывно (накапливается по кадрам), поэтому анимация бесшовная и плавно меняет скорость:
 * покой — медленно; слушает — быстрее и «дышит» голосом; думает — ленты закручиваются сильнее.
 */
@Composable
fun NoaOrb(modifier: Modifier, state: OrbState = OrbState.IDLE, level: Float = 0f) {
    val reduced = LocalReducedMotion.current
    val speedTarget = when (state) {
        OrbState.LISTENING -> 1.7f
        OrbState.THINKING -> 2.8f
        OrbState.SUCCESS -> 1.4f
        else -> 1f
    }
    val lvl by animateFloatAsState(level.coerceIn(0f, 1f), tween(90), label = "orbLevel")
    val accent by animateColorAsState(
        when (state) { OrbState.SUCCESS -> Color(0xFF9BFFC9); OrbState.ERROR -> Color(0xFFFF8A80); else -> Gold },
        tween(300), label = "orbAccent",
    )
    // Часы в секундах (стандартная бесконечная анимация — тесты и система её понимают).
    val clock = rememberInfiniteTransition(label = "orbClock").animateFloat(
        0f, 100_000f, infiniteRepeatable(tween(100_000_000, easing = LinearEasing)), label = "orbTime",
    )
    // Фаза = часы × скорость + сдвиг. При смене скорости пересчитываем сдвиг — положение не прыгает.
    val speed = remember { mutableFloatStateOf(speedTarget) }
    val offset = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(speedTarget) {
        val now = clock.value
        offset.floatValue += now * (speed.floatValue - speedTarget)
        speed.floatValue = speedTarget
    }
    Canvas(modifier) {
        val t = if (reduced) 0f else clock.value * speed.floatValue + offset.floatValue
        drawOrb(t, lvl, accent)
    }
}

private fun polar(c: Offset, a: Float, r: Float) = Offset(c.x + cos(a) * r, c.y + sin(a) * r)

internal fun DrawScope.drawOrb(time: Float, level: Float, accent: Color) {
    val s = size.minDimension
    val c = center
    val r = s * 0.34f * (1f + level * 0.06f)
    val tau = (2 * PI).toFloat()

    // 1. Мягкое свечение вокруг.
    drawCircle(
        Brush.radialGradient(
            0f to BlueDeep.copy(alpha = 0.45f), 0.55f to BlueDeep.copy(alpha = 0.16f), 1f to Color.Transparent,
            center = c, radius = r * 1.55f,
        ),
        radius = r * 1.55f, center = c,
    )

    // 2. Задние половины орбит (за шаром — тусклее).
    rings(time, c, r, front = false, accent)

    // 3. Тело шара.
    val ball = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r)) }
    clipPath(ball) {
        drawCircle(
            Brush.radialGradient(
                0f to Color(0xFF141A44), 0.6f to Color(0xFF19205A), 0.88f to Color(0xFF3A47B4), 1f to Color(0xFFD5DCFF),
                center = Offset(c.x - r * 0.06f, c.y - r * 0.08f), radius = r * 1.02f,
            ),
            radius = r, center = c,
        )
        // Ленты «дыма»: широкие шёлковые полотна, закрученные вокруг ядра; слева голубые, справа золотые.
        val n = 6
        val spin = time * 0.28f
        for (i in 0 until n) {
            val base = tau * i / n + spin
            val wob = sin(time * 0.7f + i * 1.9f)
            val curl = 1.15f + 0.3f * sin(time * 0.45f + i * 1.3f)
            // Цвет по положению: правая сторона — золото, левая — голубой (как в референсе).
            val side = ((cos(base + 0.5f) + 1f) / 2f).let { it * it * (3 - 2 * it) }
            val color = androidx.compose.ui.graphics.lerp(Color(0xFF6E95FF), Color(0xFFFFB45E), side)
            val start = polar(c, base, r * 0.04f)
            val c1 = polar(c, base + 0.95f * curl, r * (0.5f + 0.08f * wob))
            val c2 = polar(c, base - 0.45f + 0.3f * wob, r * 0.98f)
            val end = polar(c, base + 0.3f * curl, r * (0.95f + 0.04f * wob))
            fun sheet(w: Float): Path {
                val n1 = polar(Offset.Zero, base + 0.95f * curl + PI.toFloat() / 2, w)
                val n2 = polar(Offset.Zero, base - 0.45f + 0.3f * wob + PI.toFloat() / 2, w * 1.4f)
                return Path().apply {
                    moveTo(start.x, start.y)
                    cubicTo(c1.x + n1.x, c1.y + n1.y, c2.x + n2.x, c2.y + n2.y, end.x, end.y)
                    cubicTo(c2.x - n2.x, c2.y - n2.y, c1.x - n1.x, c1.y - n1.y, start.x, start.y)
                    close()
                }
            }
            drawPath(sheet(r * 0.30f), color.copy(alpha = 0.12f), blendMode = BlendMode.Plus)
            drawPath(sheet(r * 0.17f), color.copy(alpha = 0.18f), blendMode = BlendMode.Plus)
            drawPath(sheet(r * 0.07f), color.copy(alpha = 0.26f), blendMode = BlendMode.Plus)
            val spine = Path().apply { moveTo(start.x, start.y); cubicTo(c1.x, c1.y, c2.x, c2.y, end.x, end.y) }
            drawPath(spine, Color.White.copy(alpha = 0.22f), style = Stroke(r * 0.012f, cap = StrokeCap.Round), blendMode = BlendMode.Plus)
        }
        // Тёплый свет у правого нижнего края, холодный — у левого верхнего.
        drawCircle(
            Brush.radialGradient(0f to Color(0xFFFFB866).copy(alpha = 0.35f), 1f to Color.Transparent, center = Offset(c.x + r * 0.75f, c.y + r * 0.45f), radius = r * 0.9f),
            radius = r * 0.9f, center = Offset(c.x + r * 0.75f, c.y + r * 0.45f), blendMode = BlendMode.Plus,
        )
        drawCircle(
            Brush.radialGradient(0f to Color(0xFF8FB4FF).copy(alpha = 0.28f), 1f to Color.Transparent, center = Offset(c.x - r * 0.7f, c.y - r * 0.5f), radius = r * 0.85f),
            radius = r * 0.85f, center = Offset(c.x - r * 0.7f, c.y - r * 0.5f), blendMode = BlendMode.Plus,
        )
        // Ядро — небольшая яркая точка света.
        val core = r * (0.11f + 0.02f * sin(time * 1.3f) + level * 0.12f)
        drawCircle(
            Brush.radialGradient(0f to Color.White, 0.3f to Color.White.copy(alpha = 0.7f), 0.65f to GoldPale.copy(alpha = 0.18f), 1f to Color.Transparent, center = c, radius = core * 2.4f),
            radius = core * 2.4f, center = c, blendMode = BlendMode.Plus,
        )
        // Искры внутри.
        for (i in 0 until 16) {
            val a = tau * i / 16 + time * (0.18f + 0.05f * (i % 3))
            val d = r * (0.25f + 0.6f * ((i * 37 % 16) / 16f))
            val tw = 0.4f + 0.6f * (0.5f + 0.5f * sin(time * 2.1f + i * 2.3f))
            drawCircle(GoldPale.copy(alpha = 0.7f * tw), radius = s * 0.004f * (1 + i % 2), center = polar(c, a, d), blendMode = BlendMode.Plus)
        }
        // Блик стекла сверху слева.
        rotate(-35f, c) {
            drawArc(
                Brush.linearGradient(listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0f)),
                    start = Offset(c.x - r, c.y), end = Offset(c.x + r, c.y)),
                startAngle = 200f, sweepAngle = 110f, useCenter = false,
                topLeft = Offset(c.x - r * 0.82f, c.y - r * 0.82f), size = androidx.compose.ui.geometry.Size(r * 1.64f, r * 1.64f),
                style = Stroke(r * 0.045f, cap = StrokeCap.Round),
            )
        }
    }

    // 4. Кромка: голубая с одной стороны, золотая с другой, медленно вращается.
    rotate(12f * sin(time * 0.3f), c) {
        // sweep: 0° — справа; справа и снизу золото, слева и сверху голубой.
        val rim = Brush.sweepGradient(
            0f to accent, 0.22f to GoldPale, 0.42f to Blue, 0.62f to Color(0xFFB9C8FF), 0.8f to Blue, 0.93f to Color.White, 1f to accent,
            center = c,
        )
        drawCircle(rim, radius = r, center = c, style = Stroke(r * 0.16f), alpha = 0.10f, blendMode = BlendMode.Plus)
        drawCircle(rim, radius = r * 0.985f, center = c, style = Stroke(r * 0.07f), alpha = 0.32f, blendMode = BlendMode.Plus)
        drawCircle(rim, radius = r, center = c, style = Stroke(r * 0.022f), alpha = 1f)
    }

    // 5. Передние половины орбит.
    rings(time, c, r, front = true, accent)
}

/** Три наклонные орбиты из частиц; по каждой бежит яркая «комета». */
private fun DrawScope.rings(time: Float, c: Offset, r: Float, front: Boolean, accent: Color) {
    val tau = (2 * PI).toFloat()
    val tilts = floatArrayOf(-24f, 18f, 72f)
    val flat = floatArrayOf(0.30f, 0.22f, 0.26f)
    val speeds = floatArrayOf(0.22f, -0.16f, 0.12f)
    val dot = size.minDimension * 0.0038f
    for (k in 0..2) {
        val tilt = (tilts[k] + 6f * sin(time * 0.15f + k)) * (PI / 180f).toFloat()
        val head = (time * speeds[k] * tau) % tau
        val rx = r * (1.28f + 0.06f * k)
        val ry = rx * flat[k]
        val count = 150
        for (j in 0 until count) {
            val th = tau * j / count
            val z = sin(th)                       // >0 — перед шаром, <0 — за ним
            if ((z >= 0f) != front) continue
            val ex = cos(th) * rx
            val ey = sin(th) * ry
            val p = Offset(c.x + ex * cos(tilt) - ey * sin(tilt), c.y + ex * sin(tilt) + ey * cos(tilt))
            // Хвост кометы тянется позади головы.
            var dth = (head - th) % tau; if (dth < 0) dth += tau
            val comet = exp(-dth / 0.55f)
            val a = (0.32f + 0.68f * comet) * (if (front) 1f else 0.4f)
            val col = if (comet > 0.5f) GoldPale else accent
            drawCircle(col.copy(alpha = a), radius = dot * (1f + 1.4f * comet), center = p, blendMode = BlendMode.Plus)
        }
    }
}
