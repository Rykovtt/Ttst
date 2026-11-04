package com.kartoteka.app.assistant

import kotlin.math.log10
import kotlin.math.sqrt

/**
 * «Говорит ли человек поверх музыки» — как у умных колонок, которые знают, что играют сами.
 *
 * Сравниваем громкость микрофона с громкостью того, что сейчас играет телефон ([PlaybackLevel]).
 * Пока звучит только песня, разница «микрофон − плеер» держится около своего обычного уровня (базы):
 * микрофон слышит ровно эту песню. Человек говорит — к песне добавляется голос, и микрофон становится
 * громче, чем объясняется самой песней. Имя, «услышанное» в словах песни, такой прибавки не даёт.
 *
 * Чистая логика (без Android): время и уровни передаются снаружи — проверяется тестами.
 */
class VoiceOverMusic {
    private val diffs = ArrayDeque<Pair<Long, Float>>()      // время → (микрофон − плеер), дБ

    /** Очередное чтение (каждые ~100 мс). [micRms] — RMS отсчётов микрофона (16 бит), [playDb] — уровень плеера, дБFS. */
    fun add(now: Long, micRms: Double, playDb: Float?) {
        if (playDb == null || playDb < MIN_PLAY_DB || micRms <= 1.0) return
        val micDb = (20 * log10(micRms / 32768.0)).toFloat()
        diffs.addLast(now to (micDb - playDb))
        while (diffs.isNotEmpty() && now - diffs.first().first > HISTORY_MS) diffs.removeFirst()
    }

    fun reset() = diffs.clear()

    /** База: медиана разницы за последние 20 с, кроме последних [RECENT_MS] (там может быть сам голос). */
    fun baseline(now: Long): Float? {
        val old = diffs.filter { now - it.first > RECENT_MS }.map { it.second }.sorted()
        if (old.size < MIN_BASE_POINTS) return null
        return old[old.size / 2]
    }

    /** Насколько микрофон в последние [RECENT_MS] громче, чем объясняется песней (дБ); null — данных мало. */
    fun excess(now: Long): Float? {
        val base = baseline(now) ?: return null
        val recent = diffs.filter { now - it.first <= RECENT_MS }.map { it.second }
        if (recent.isEmpty()) return null
        // не одиночный всплеск, а устойчивая прибавка: вторая по величине
        val top = recent.sortedDescending()
        return (if (top.size >= 2) top[1] else top[0]) - base
    }

    /** Голос поверх музыки есть. Данных о плеере нет (недоступен) — не мешаем: true. */
    fun voice(now: Long): Boolean = excess(now)?.let { it >= THRESHOLD_DB } ?: true

    companion object {
        /** Сколько дБ сверх песни считать голосом. Подобрать по журналу: строки «голос над музыкой». */
        const val THRESHOLD_DB = 4f
        private const val HISTORY_MS = 20_000L
        private const val RECENT_MS = 1_500L
        private const val MIN_BASE_POINTS = 30          // ~3 с истории
        private const val MIN_PLAY_DB = -60f

        fun rms(buf: ShortArray, n: Int): Double {
            var s = 0.0
            for (k in 0 until n) s += buf[k].toDouble() * buf[k]
            return if (n > 0) sqrt(s / n) else 0.0
        }
    }
}

/** Уровень того, что сейчас играет телефон (общий выход, Visualizer). Недоступен — [db] вернёт null. */
class PlaybackLevel {
    private val vis: android.media.audiofx.Visualizer? = runCatching {
        android.media.audiofx.Visualizer(0).apply {
            captureSize = android.media.audiofx.Visualizer.getCaptureSizeRange()[0]
            scalingMode = android.media.audiofx.Visualizer.SCALING_MODE_AS_PLAYED
            measurementMode = android.media.audiofx.Visualizer.MEASUREMENT_MODE_PEAK_RMS
            enabled = true
        }
    }.getOrNull()
    private val m = android.media.audiofx.Visualizer.MeasurementPeakRms()

    val available: Boolean get() = vis != null

    /** RMS выхода в дБFS (−96 — тишина); null — недоступно. */
    fun db(): Float? {
        val v = vis ?: return null
        return runCatching {
            if (v.getMeasurementPeakRms(m) != android.media.audiofx.Visualizer.SUCCESS) null else m.mRms / 100f
        }.getOrNull()
    }

    fun release() { runCatching { vis?.enabled = false; vis?.release() } }
}
