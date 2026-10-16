package com.kartoteka.app.assistant

/**
 * Приглушение музыки, пока человек договаривает команду: услышали «Санта…» — музыка тише,
 * сама команда слышна обычным голосом (так делают умные колонки). Без команды за [HOLD_MS] — громкость возвращается.
 *
 * Громкость читается и пишется через [io] (на устройстве — STREAM_MUSIC), время — [clock]; вызывается из потока
 * службы и из главного, поэтому методы синхронизированы.
 */
internal class MusicDuck(private val io: VolumeIO, private val clock: () -> Long = System::currentTimeMillis) {
    interface VolumeIO {
        fun get(): Int
        fun set(index: Int)
    }

    private var saved = -1
    private var ducked = -1
    private var until = 0L

    val active: Boolean @Synchronized get() = saved >= 0

    /** Приглушить (или продлить уже начатое) на [holdMs]. false — нечего приглушать (и так почти тихо). */
    @Synchronized fun duck(holdMs: Long = HOLD_MS): Boolean {
        if (saved >= 0) { until = maxOf(until, clock() + holdMs); return true }
        val cur = io.get()
        if (cur <= 1) return false
        saved = cur
        ducked = Math.round(cur * LEVEL).coerceIn(1, cur - 1)
        io.set(ducked)
        until = clock() + holdMs
        return true
    }

    /** Продлить, если приглушено (ассистент слушает или говорит). */
    @Synchronized fun extend(holdMs: Long) { if (saved >= 0) until = maxOf(until, clock() + holdMs) }

    /** Вернуть громкость, если время вышло. */
    @Synchronized fun restoreIfDue(): Boolean = if (saved >= 0 && clock() >= until) { restore(); true } else false

    /** Вернуть громкость сейчас. Если человек сам поменял её, пока было тихо, — оставляем как есть. */
    @Synchronized fun restore() {
        if (saved < 0) return
        if (io.get() == ducked) io.set(saved)
        saved = -1; ducked = -1
    }

    /** Забыть приглушение без возврата: громкость сейчас выставят на нужный уровень («громкость на 50»). */
    @Synchronized fun forget() { saved = -1; ducked = -1 }

    companion object {
        /** До какой доли текущей громкости приглушать. */
        const val LEVEL = 0.3f
        const val HOLD_MS = 4_000L
    }
}
