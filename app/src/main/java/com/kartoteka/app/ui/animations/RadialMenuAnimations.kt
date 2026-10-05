package com.kartoteka.app.ui.animations

import androidx.compose.ui.geometry.Offset
import kotlin.math.cos
import kotlin.math.sin

/**
 * Позиция пункта радиального меню при прогрессе [p] 0..1: пункт выходит из центра кнопки
 * и движется по дуге — угол плавно уходит от вертикали к своему, радиус растёт.
 */
fun arcPosition(p: Float, targetAngleDeg: Double, radiusPx: Float, liftPx: Float): Offset {
    val angle = Math.toRadians(90.0 + (targetAngleDeg - 90.0) * p.coerceIn(0f, 1.2f))
    val r = radiusPx * p
    return Offset((cos(angle) * r).toFloat(), (-sin(angle) * r).toFloat() - liftPx * p)
}

/** Углы для N пунктов дугой над кнопкой (0° — вправо, 90° — вверх). */
fun arcAngles(n: Int): List<Double> = when (n) {
    1 -> listOf(90.0)
    2 -> listOf(125.0, 55.0)
    3 -> listOf(150.0, 90.0, 30.0)
    else -> listOf(158.0, 116.0, 64.0, 22.0)
}
