package com.kartoteka.app.assistant

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.kartoteka.app.KartotekaApp
import com.kartoteka.app.data.VoiceNote
import com.kartoteka.app.data.VoiceStorage
import com.kartoteka.app.i18n.t
import com.kartoteka.app.voice.VoiceNotes
import com.kartoteka.app.voice.VoiceRecorder
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

/**
 * «Открой голосовые заметки об Илье и начни запись»: пишем голос в зашифрованную заметку этого человека.
 * Пока идёт запись — в шторке уведомление с таймером и кнопкой «Остановить» (плюс системная точка микрофона),
 * два сигнала: начало и конец. Остановить можно и голосом: «стоп запись», «закончи запись», «останови запись».
 * Слушает стоп-фразу та же офлайн-модель, что и имя ассистента (если она скачана); иначе — только кнопка.
 */
class VoiceNoteService : Service() {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var running = false
    private var worker: Thread? = null
    private var personId = 0L
    private var personName = ""
    private var startedAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { running = false; return START_NOT_STICKY }
        if (running) return START_NOT_STICKY
        personId = intent?.getLongExtra(EXTRA_PERSON, 0L) ?: 0L
        personName = intent?.getStringExtra(EXTRA_NAME).orEmpty()
        startedAt = System.currentTimeMillis()
        val n = notification()
        val ok = runCatching {
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE) else startForeground(NOTIF_ID, n)
        }.isSuccess
        if (!ok || personId == 0L || ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            stopSelf(); return START_NOT_STICKY
        }
        running = true; active = true
        worker = Thread({
            try { record() } catch (t: Throwable) { if (t !is InterruptedException) CrashLog.record(this, "voicenote", t) }
            finally { active = false; main.post { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() } }
        }, "voicenote").also { it.isDaemon = true; it.start() }
        return START_NOT_STICKY
    }

    override fun onDestroy() { running = false; active = false; super.onDestroy() }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, t("Запись голосовой заметки"), NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 2, Intent(this, VoiceNoteService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(com.kartoteka.app.AppIcons.notificationIcon(this))
            .setContentTitle("● " + t("Идёт запись заметки: %1\$s", personName))
            .setContentText(t("Говорите. Чтобы закончить, скажите «стоп запись» или нажмите «Остановить»."))
            .setUsesChronometer(true).setWhen(startedAt).setShowWhen(true)
            .setOngoing(true).setSilent(true).setColor(0xFFE53935.toInt())
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, t("Остановить"), stop)
            .build()
    }

    private fun beep(tone: Int) = runCatching {
        ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 80).apply { startTone(tone, 180); main.postDelayed({ release() }, 400) }
    }

    private fun record() {
        val app = application as KartotekaApp
        val storage = app.repository.voices
        val rate = VoiceStorage.SAMPLE_RATE
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        // Пока ассистент договаривает «Записываю…», микрофон не трогаем — иначе его голос попадёт в заметку.
        Thread.sleep(1_400)
        if (!running) return
        val ar = runCatching { AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, rate) * 2) }
            .getOrNull()?.takeIf { it.state == AudioRecord.STATE_INITIALIZED } ?: return
        val model = if (WakeModel.ready(this)) runCatching { Model(WakeModel.dir(this).absolutePath) }.getOrNull() else null
        val rec = model?.let { m -> runCatching { Recognizer(m, rate.toFloat(), JSONArray(STOP_PHRASES + "[unk]").toString()) }.getOrNull() }
        val file = storage.newName()
        val buf = ShortArray(rate / 10)
        var total = 0L
        var stoppedByVoice = false
        main.post { beep(ToneGenerator.TONE_PROP_BEEP) }
        try {
            ar.startRecording()
            storage.open(file).use { out ->
                val bytes = ByteArray(buf.size * 2)
                while (running) {
                    val n = ar.read(buf, 0, buf.size)
                    if (n <= 0) continue
                    for (i in 0 until n) { bytes[i * 2] = (buf[i].toInt() and 0xFF).toByte(); bytes[i * 2 + 1] = (buf[i].toInt() shr 8).toByte() }
                    out.write(bytes, 0, n * 2)
                    total += n * 2
                    if (rec != null) {
                        val final = rec.acceptWaveForm(buf, n)
                        val text = JSONObject(if (final) rec.result else rec.partialResult).let { if (final) it.optString("text") else it.optString("partial") }
                        if (isStop(text)) { stoppedByVoice = true; running = false }
                    }
                    if (VoiceStorage.durationMs(total) >= VoiceRecorder.MAX_MS) running = false
                }
            }
        } finally {
            runCatching { ar.stop() }; runCatching { ar.release() }
            runCatching { rec?.close() }; runCatching { model?.close() }
        }
        main.post { beep(ToneGenerator.TONE_PROP_ACK) }
        // Стоп-фраза сказана в конце записи — последние ~1.5 с отрезаем, чтобы «стоп запись» не попало в заметку.
        var ms = VoiceStorage.durationMs(total)
        if (stoppedByVoice && ms > 2_500) {
            val cut = (1_300L * rate * 2 / 1000).toInt() and 1.inv()
            val kept = storage.pcm(file).let { it.copyOf((it.size - cut).coerceAtLeast(0)) }
            storage.save(file, kept); ms = VoiceStorage.durationMs(kept.size.toLong())
        }
        if (ms < 1_000) { storage.delete(file); return }
        val note = VoiceNote(personId = personId, file = file, durationMs = ms)
        app.appScope.launch {
            val id = app.repository.addVoiceNote(note)
            app.repository.touchContact(personId)
            VoiceNotes.transcribe(app, note.copy(id = id))
        }
        main.post {
            if (app.settings.assistantVoice.value.value) {
                NoaVoice.init(applicationContext)
                NoaVoice.speak(applicationContext, t("Заметка о %1\$s сохранена.", personName)) {}
            }
        }
    }

    companion object {
        const val ACTION_STOP = "com.kartoteka.app.VOICENOTE_STOP"
        private const val EXTRA_PERSON = "person"
        private const val EXTRA_NAME = "name"
        private const val CHANNEL = "voicenote"
        private const val NOTIF_ID = 4108
        /** Идёт запись — ассистент не трогает микрофон. */
        @Volatile var active = false
            private set

        val STOP_PHRASES = listOf("стоп запись", "останови запись", "заверши запись", "закончи запись", "конец записи", "хватит записывать",
            "стоп заметка", "стоп заметку", "заверши заметку", "закончи заметку", "зупини запис", "заверши запис", "закінчи запис", "стоп запис")

        internal fun isStop(text: String): Boolean {
            val s = text.lowercase().trim()
            return s.isNotEmpty() && STOP_PHRASES.any { s.contains(it) }
        }

        fun start(context: Context, personId: Long, name: String): Boolean {
            if (active) return false
            val i = Intent(context, VoiceNoteService::class.java).putExtra(EXTRA_PERSON, personId).putExtra(EXTRA_NAME, name)
            return runCatching { ContextCompat.startForegroundService(context, i) }.isSuccess
        }

        fun stop(context: Context) {
            if (active) runCatching { context.startService(Intent(context, VoiceNoteService::class.java).setAction(ACTION_STOP)) }
        }
    }
}
