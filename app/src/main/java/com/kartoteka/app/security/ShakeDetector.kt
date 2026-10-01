package com.kartoteka.app.security

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * Встряхивание: несколько резких рывков (> [THRESHOLD_G] g) за короткое время.
 * Обычная ходьба, бег или телефон, брошенный на стол, не срабатывают.
 */
class ShakeDetector(private val onShake: () -> Unit) : SensorEventListener {
    private val peaks = ArrayDeque<Long>()
    private var lastShake: Long? = null

    /** Чистая логика (для тестов): ускорение в g и время в мс. Возвращает true, если это встряхивание. */
    fun accept(gForce: Float, timeMs: Long): Boolean {
        if (gForce < THRESHOLD_G) return false
        // Один рывок длится несколько показаний датчика — считаем его один раз.
        if (peaks.isNotEmpty() && timeMs - peaks.last() < MIN_GAP_MS) return false
        peaks.addLast(timeMs)
        while (peaks.isNotEmpty() && timeMs - peaks.first() > WINDOW_MS) peaks.removeFirst()
        if (peaks.size >= PEAKS && (lastShake?.let { timeMs - it > COOLDOWN_MS } != false)) {
            peaks.clear()
            lastShake = timeMs
            return true
        }
        return false
    }

    override fun onSensorChanged(event: SensorEvent) {
        val (x, y, z) = event.values
        val g = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
        if (accept(g, event.timestamp / 1_000_000)) onShake()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    fun start(sm: SensorManager) {
        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop(sm: SensorManager) = sm.unregisterListener(this)

    companion object {
        const val THRESHOLD_G = 2.3f
        const val PEAKS = 3
        const val WINDOW_MS = 1_200L
        const val MIN_GAP_MS = 120L
        const val COOLDOWN_MS = 2_000L
    }
}
