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
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.kartoteka.app.KartotekaApp
import com.kartoteka.app.i18n.t
import org.json.JSONArray
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

/**
 * «Ноа, ты тут?» — ассистент спит и слышит только своё имя. Услышала — просыпается, отвечает «Готова»,
 * слушает команду, выполняет и снова засыпает до следующего зова.
 *
 * Слушает офлайн-модель на самом телефоне (Vosk): интернет не нужен, звук не сохраняется и никуда не уходит,
 * системное распознавание речи не используется (оно пищит при каждом перезапуске и глушило бы музыку).
 * Пока работает — в шторке постоянное уведомление с кнопкой «Выключить» (так требует Android для микрофона).
 */
class WakeService : Service() {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var running = false
    private var worker: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android требует сразу сделать службу «передним планом» — иначе приложение падает. Делаем это первым делом.
        val n = notification()
        val started = runCatching {
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            else startForeground(NOTIF_ID, n)
        }.recoverCatching { startForeground(NOTIF_ID, n) }.isSuccess
        if (intent?.action == ACTION_STOP) {
            (application as KartotekaApp).settings.assistantWake.set(false)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!started) { stopSelf(); return START_NOT_STICKY }
        if (!running) {
            running = true
            worker = Thread({
                // Любой сбой в распознавании — только остановка службы, а не падение всего приложения.
                try { loop() } catch (t: Throwable) {
                    if (t !is InterruptedException) { CrashLog.record(this, "wake", t); main.post { stopSelf() } }
                }
            }, "wake").also { it.isDaemon = true; it.start() }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        instance = null
        worker?.interrupt()
        super.onDestroy()
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, t("Голосовой вызов ассистента"), NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            })
        }
        val name = (application as KartotekaApp).settings.assistantName.value.value.ifBlank { "Ноа" }
        val stop = PendingIntent.getService(this, 1, Intent(this, WakeService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(com.kartoteka.app.AppIcons.notificationIcon(this))
            .setContentTitle(t("Слушаю: «%1\$s»", name))
            .setContentText(t("Скажите имя — и я проснусь. Звук остаётся на телефоне."))
            .setOngoing(true).setSilent(true).setShowWhen(false)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, t("Выключить"), stop)
            .build()
    }

    /** Слова, на которые просыпаемся: имя ассистента (по умолчанию «Ноа»). */
    private fun wakeWords(): List<String> {
        val name = (application as KartotekaApp).settings.assistantName.value.value
        return name.lowercase().split(Regex("[^\\p{L}]+")).filter { it.length >= 2 }.ifEmpty { listOf("ноа") }
    }

    private fun loop() {
        val words = wakeWords()
        val model = runCatching { Model(WakeModel.dir(this).absolutePath) }.getOrNull()
        if (model == null) { main.post { stopSelf() }; return }
        // Грамматика: только имя и пара «зовущих» фраз; всё остальное — «[unk]», поэтому случайная речь не будит.
        val name = words.joinToString(" ")
        val phrases = listOf(name, "эй $name", "привет $name", "$name ты тут", "[unk]")
        val rec = runCatching { Recognizer(model, SAMPLE_RATE.toFloat(), JSONArray(phrases.distinct()).toString()) }.getOrNull()
        if (rec == null) { model.close(); main.post { stopSelf() }; return }
        rec.setWords(true)
        val audio = getSystemService(android.media.AudioManager::class.java)

        var record: AudioRecord? = null
        val buf = ShortArray(SAMPLE_RATE / 10)           // 100 мс
        var quiet = 0
        var fed = false
        var refractoryUntil = 0L
        try {
            while (running) {
                if (isBusy()) {
                    // Говорят с ассистентом или он сам говорит — микрофон отдаём ему.
                    record?.let { runCatching { it.stop(); it.release() } }; record = null
                    if (fed) { rec.reset(); fed = false }
                    Thread.sleep(150); continue
                }
                if (record == null) {
                    record = openRecord()
                    if (record == null) { Thread.sleep(1500); continue }
                }
                val n = record.read(buf, 0, buf.size)
                if (n <= 0) { Thread.sleep(40); continue }
                // Тишина не распознаётся — экономим заряд: решаем по громкости, держим «хвост» в 1.2 с.
                var sum = 0.0
                for (i in 0 until n) sum += buf[i].toDouble() * buf[i]
                val rms = Math.sqrt(sum / n)
                if (rms < QUIET_RMS) quiet++ else quiet = 0
                if (quiet > 12) { if (fed) { rec.reset(); fed = false }; continue }
                fed = true
                // Только законченные фразы: обрывки («ноа» внутри чужой речи) не считаются.
                if (!rec.acceptWaveForm(buf, n)) continue
                val obj = runCatching { JSONObject(rec.result) }.getOrNull() ?: continue
                val text = obj.optString("text")
                fed = false
                if (text.isBlank()) continue
                // Играет музыка/видео — в микрофон попадает чужая речь: просим более длинный зов («эй, Ноа»).
                val strict = runCatching { audio?.isMusicActive == true }.getOrDefault(false)
                if (accepts(text, words, strict, confidence(obj)) && System.currentTimeMillis() > refractoryUntil) {
                    refractoryUntil = System.currentTimeMillis() + 5_000
                    rec.reset(); quiet = 0
                    record.let { runCatching { it.stop(); it.release() } }; record = null
                    wake(ping = text.contains("тут"))
                }
            }
        } catch (_: InterruptedException) {
        } finally {
            record?.let { runCatching { it.stop(); it.release() } }
            runCatching { rec.close() }; runCatching { model.close() }
        }
    }

    private fun openRecord(): AudioRecord? {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return null
        return runCatching {
            val min = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(min, SAMPLE_RATE / 2) * 2).also {
                if (it.state != AudioRecord.STATE_INITIALIZED) { it.release(); return null }
                it.startRecording()
            }
        }.getOrNull()
    }

    /** Проснуться: выводим на экран сферу — она поздоровается, выслушает команду и сама закроется. */
    private fun wake(ping: Boolean) {
        hold(WAKE_GUARD_MS)
        main.post {
            val i = Intent(this, NoaWake::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                .putExtra(EXTRA_PING, ping)
            // Без права «поверх других окон» Android не даёт открыть окно из фона — тогда просто снова слушаем.
            if (runCatching { startActivity(i) }.isFailure) release()
        }
    }

    companion object {
        const val ACTION_STOP = "com.kartoteka.app.WAKE_STOP"
        const val EXTRA_PING = "wake_ping"
        private const val CHANNEL = "wake"
        private const val NOTIF_ID = 4107
        private const val SAMPLE_RATE = 16_000
        private const val QUIET_RMS = 250.0
        private const val WAKE_GUARD_MS = 12_000L
        private const val BUSY_MS = 90_000L

        /** В распознанной фразе прозвучало имя целиком (все его слова). */
        internal fun heard(text: String, words: List<String>): Boolean {
            val t = text.lowercase().split(' ').filter { it.isNotBlank() }
            return words.isNotEmpty() && words.all { w -> w in t }
        }

        /**
         * Будить только на ровно зов: «Ноа», «Эй, Ноа», «Привет, Ноа», «Ноа, ты тут». Любая другая речь вокруг
         * (в распознанном тексте появляется «[unk]») — не зов. Пока играет звук ([strict]) — только фразы подлиннее.
         */
        internal fun accepts(text: String, words: List<String>, strict: Boolean, confidence: Double = 1.0): Boolean {
            if (words.isEmpty() || confidence < MIN_CONFIDENCE) return false
            val t = text.lowercase().trim().replace(Regex("\\s+"), " ")
            if ("[unk]" in t) return false
            val name = words.joinToString(" ")
            val phrases = setOf("эй $name", "привет $name", "$name ты тут") + if (strict) emptySet() else setOf(name)
            return t in phrases
        }

        private const val MIN_CONFIDENCE = 0.7

        /** Средняя уверенность распознанных слов (0..1). */
        internal fun confidence(obj: JSONObject): Double {
            val arr = obj.optJSONArray("result") ?: return 1.0
            if (arr.length() == 0) return 0.0
            var sum = 0.0
            for (i in 0 until arr.length()) sum += arr.optJSONObject(i)?.optDouble("conf", 1.0) ?: 1.0
            return sum / arr.length()
        }

        @Volatile private var instance: WakeService? = null
        /** Микрофон занят ассистентом до этого момента (страховка: не залипнет, если окно не открылось). */
        @Volatile private var busyUntil = 0L
        @Volatile private var cooldownUntil = 0L

        private fun isBusy(): Boolean = System.currentTimeMillis().let { it < busyUntil || it < cooldownUntil }
        private fun hold(ms: Long) { busyUntil = System.currentTimeMillis() + ms }
        private fun release() { busyUntil = 0L; cooldownUntil = System.currentTimeMillis() + 1_200L }

        /** Ассистент слушает, думает или говорит — служба не подслушивает, чтобы не перебивать и не будить себя саму. */
        fun setBusy(on: Boolean) { if (on) hold(BUSY_MS) else release() }

        fun canRun(context: Context): Boolean {
            val app = context.applicationContext as KartotekaApp
            return app.settings.assistantWake.value.value && WakeModel.ready(context) &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        }

        /** Запуск из открытого приложения (так разрешает Android: микрофонную службу нельзя стартовать «из фона»). */
        fun start(context: Context): Boolean {
            if (!canRun(context) || instance != null) return instance != null
            return runCatching { ContextCompat.startForegroundService(context, Intent(context, WakeService::class.java)) }.isSuccess
        }

        fun stop(context: Context) { runCatching { context.stopService(Intent(context, WakeService::class.java)) } }

        /** Имя сменили — слушаем новое (служба перезапускается). */
        fun restart(context: Context) {
            stop(context)
            Handler(Looper.getMainLooper()).postDelayed({ start(context) }, 600)
        }

        fun running(): Boolean = instance != null
    }

    override fun onCreate() { super.onCreate(); instance = this }
}
