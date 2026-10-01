package com.kartoteka.app.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.kartoteka.app.data.VoiceStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Запись с микрофона сразу в зашифрованный файл. Максимум — [MAX_MS]. */
class VoiceRecorder(private val storage: VoiceStorage, private val scope: CoroutineScope) {
    /** Громкость 0..1 для индикатора. */
    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()
    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()

    private var job: Job? = null
    private var name: String? = null
    @Volatile private var running = false

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        val minBuf = AudioRecord.getMinBufferSize(VoiceStorage.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) return false
        val record = runCatching {
            AudioRecord(MediaRecorder.AudioSource.MIC, VoiceStorage.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, minBuf * 4)
        }.getOrNull()?.takeIf { it.state == AudioRecord.STATE_INITIALIZED } ?: return false
        val file = storage.newName().also { name = it }
        running = true
        record.startRecording()
        job = scope.launch(Dispatchers.IO) {
            val buf = ByteArray(minBuf)
            var total = 0L
            storage.open(file).use { out ->
                while (running && isActive) {
                    val n = record.read(buf, 0, buf.size)
                    if (n <= 0) continue
                    out.write(buf, 0, n)
                    total += n
                    _elapsedMs.value = VoiceStorage.durationMs(total)
                    _level.value = peak(buf, n)
                    if (_elapsedMs.value >= MAX_MS) running = false
                }
            }
            record.stop()
            record.release()
        }
        return true
    }

    /** Остановить и дождаться записи файла. Возвращает имя файла и длительность. */
    suspend fun stop(): Pair<String, Long>? {
        running = false
        job?.join()
        val file = name ?: return null
        return file to _elapsedMs.value
    }

    /** Отменить запись и удалить файл. */
    suspend fun cancel() {
        stop()?.let { storage.delete(it.first) }
    }

    val isFinishedByLimit: Boolean get() = !running && job?.isCompleted == true

    private fun peak(buf: ByteArray, n: Int): Float {
        var max = 0
        var i = 0
        while (i + 1 < n) {
            val s = (buf[i].toInt() and 0xFF) or (buf[i + 1].toInt() shl 8)
            max = maxOf(max, abs(s.toShort().toInt()))
            i += 2
        }
        return (max / 32768f).coerceIn(0f, 1f)
    }

    companion object {
        const val MAX_MS = 15 * 60_000L

        fun hasPermission(context: Context) =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
}
