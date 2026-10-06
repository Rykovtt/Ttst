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
    /** Пока слушаем имя, процессор не должен засыпать при выключенном экране — иначе «не слышит» с заблокированным телефоном. */
    private var wakeLock: android.os.PowerManager.WakeLock? = null
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
        if (wakeLock == null) {
            wakeLock = runCatching {
                getSystemService(android.os.PowerManager::class.java)
                    .newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "rvault:wake").apply { setReferenceCounted(false); acquire() }
            }.getOrNull()
        }
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
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }; wakeLock = null
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
        val phrases = listOf(name, "эй $name", "привет $name", "$name ты тут", "[unk]") + COMMANDS.keys.map { "$name $it" }
        val rec = runCatching { Recognizer(model, SAMPLE_RATE.toFloat(), JSONArray(phrases.distinct()).toString()) }.getOrNull()
        if (rec == null) { model.close(); main.post { stopSelf() }; return }
        rec.setWords(true)
        val audio = getSystemService(android.media.AudioManager::class.java)

        var record: AudioRecord? = null
        val buf = ShortArray(SAMPLE_RATE / 10)           // 100 мс
        var quiet = 0
        var fed = false
        var refractoryUntil = 0L
        var lastKey: String? = null
        var stable = 0
        // Последние ~2.5 с звука — для повторной проверки кандидата обычным (не «только имя») распознаванием.
        val ring = ShortArray(SAMPLE_RATE * 5 / 2)
        var ringPos = 0
        var ringFilled = 0
        fun drop() {
            record?.let { runCatching { it.stop(); it.release() } }; record = null
            effects.forEach { runCatching { it.release() } }; effects.clear()
        }
        try {
            while (running) {
                if (isBusy()) {
                    // Говорят с ассистентом или он сам говорит — микрофон отдаём ему.
                    drop()
                    if (fed) { rec.reset(); fed = false }
                    Thread.sleep(150); continue
                }
                val ar = record ?: openRecord()?.also { record = it }
                if (ar == null) { Thread.sleep(1500); continue }
                val n = ar.read(buf, 0, buf.size)
                if (n <= 0) { Thread.sleep(40); continue }
                for (k in 0 until n) { ring[ringPos] = buf[k]; ringPos = (ringPos + 1) % ring.size }
                ringFilled = minOf(ring.size, ringFilled + n)
                // Тишина не распознаётся — экономим заряд: решаем по громкости, держим «хвост» в 1.2 с.
                var sum = 0.0
                for (k in 0 until n) sum += buf[k].toDouble() * buf[k]
                val rms = Math.sqrt(sum / n)
                if (rms < QUIET_RMS) quiet++ else quiet = 0
                if (quiet > 12) { if (fed) { rec.reset(); fed = false }; continue }
                fed = true
                // Законченная фраза — как раньше; при музыке пауз в речи нет, поэтому смотрим и на промежуточный
                // результат, но принимаем его, только если он не меняется два чтения подряд.
                val final = rec.acceptWaveForm(buf, n)
                val obj = runCatching { JSONObject(if (final) rec.result else rec.partialResult) }.getOrNull() ?: continue
                val text = if (final) obj.optString("text") else obj.optString("partial")
                if (final) fed = false
                val playing = runCatching { audio?.isMusicActive == true }.getOrDefault(false)
                val d = if (text.isBlank()) null else decide(text, words, playing, if (final) confidence(obj) else 1.0, final)
                val mode = (application as KartotekaApp).settings.assistantMusicWake.value.value
                if (text.isNotBlank() && (playing || d != null)) WakeDiag.add(playing, final, text, d?.toString() ?: "нет")
                if (!final) {
                    val key = d?.toString()
                    stable = if (key != null && key == lastKey) stable + 1 else if (key != null) 1 else 0
                    lastKey = key
                }
                val ready = d != null && (final || stable >= (if (playing && mode != "keen") 3 else 2))
                if (!ready) continue
                val now = System.currentTimeMillis()
                // Вторая проверка: настоящая речь с именем, а не похожий звук из видео/рилсов, которые «подпали» под узкую грамматику.
                val snapshot = ShortArray(ringFilled) { ring[(ringPos - ringFilled + it + ring.size) % ring.size] }
                // При музыке свободное распознавание тонет в звуке и «не пускает» настоящий зов — по умолчанию его не требуем (режим «строго» требует).
                val genuine = if (playing && mode != "strict") true else verify(model, snapshot, words, d is Decision.Command)
                WakeDiag.add(playing, final, text, if (genuine) "ПРИНЯТО $d" else "отклонено проверкой")
                rec.reset(); fed = false; quiet = 0; stable = 0; lastKey = null
                if (!genuine) continue
                when (d) {
                    is Decision.Command -> if (now > cmdRefractoryUntil) {
                        cmdRefractoryUntil = now + 1_500
                        main.post { NoaMedia.control(applicationContext, d.control); buzz() }
                    }
                    is Decision.Wake -> if (now > refractoryUntil) {
                        refractoryUntil = now + 5_000
                        drop()
                        wake(ping = d.ping)
                    }
                    null -> Unit
                }
            }
        } catch (_: InterruptedException) {
        } finally {
            drop()
            runCatching { rec.close() }; runCatching { model.close() }
        }
    }

    /** Свободное распознавание последних секунд: в услышанном тексте должно быть имя (и для команды — слово команды). */
    private fun verify(model: Model, pcm: ShortArray, words: List<String>, command: Boolean): Boolean {
        if (pcm.size < SAMPLE_RATE / 2) return true
        var free: Recognizer? = null
        return try {
            free = Recognizer(model, SAMPLE_RATE.toFloat())
            free.acceptWaveForm(pcm, pcm.size)
            val text = JSONObject(free.finalResult).optString("text")
            verifies(text, words, command)
        } catch (_: Throwable) {
            true        // сбой самой проверки не должен глушить зов
        } finally {
            runCatching { free?.close() }
        }
    }

    private val effects = mutableListOf<android.media.audiofx.AudioEffect>()
    private var cmdRefractoryUntil = 0L

    /** Короткая вибрация: команда плеера принята (голосом отвечать нельзя — играет музыка). */
    private fun buzz() = runCatching {
        val v = getSystemService(android.os.Vibrator::class.java)
        if (Build.VERSION.SDK_INT >= 26) v?.vibrate(android.os.VibrationEffect.createOneShot(40, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        else @Suppress("DEPRECATION") v?.vibrate(40)
    }

    private fun openRecord(): AudioRecord? {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return null
        val min = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        // Источник «голосовая связь» умеет вычитать из записи то, что играет динамик (музыка, видео) — так слышно голос поверх звука.
        for (source in intArrayOf(MediaRecorder.AudioSource.VOICE_COMMUNICATION, MediaRecorder.AudioSource.VOICE_RECOGNITION)) {
            val r = runCatching {
                AudioRecord(source, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, SAMPLE_RATE / 2) * 2)
            }.getOrNull() ?: continue
            if (r.state != AudioRecord.STATE_INITIALIZED) { r.release(); continue }
            runCatching {
                if (android.media.audiofx.AcousticEchoCanceler.isAvailable())
                    android.media.audiofx.AcousticEchoCanceler.create(r.audioSessionId)?.also { it.enabled = true; effects += it }
                if (android.media.audiofx.NoiseSuppressor.isAvailable())
                    android.media.audiofx.NoiseSuppressor.create(r.audioSessionId)?.also { it.enabled = true; effects += it }
            }
            if (runCatching { r.startRecording() }.isFailure) { r.release(); continue }
            return r
        }
        return null
    }

    /** Проснуться: выводим на экран сферу — она поздоровается, выслушает команду и сама закроется. */
    private fun wake(ping: Boolean) {
        val app = application as KartotekaApp
        val locked = getSystemService(android.app.KeyguardManager::class.java)?.isKeyguardLocked == true
        if (locked && !app.settings.assistantWakeLocked.value.value) {
            // Заблокированный экран: сферу без PIN не показываем (так решает человек в настройках) — только подсказка.
            hold(6_000)
            main.post {
                buzz()
                if (app.settings.assistantVoice.value.value) {
                    NoaVoice.init(applicationContext)
                    NoaVoice.speak(applicationContext, t("Телефон заблокирован. Разблокируйте его.")) { release() }
                } else release()
                main.postDelayed({ release() }, 6_000)
            }
            return
        }
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

        sealed interface Decision {
            data class Wake(val ping: Boolean) : Decision
            data class Command(val control: NoaMedia.Control) : Decision
        }

        /** Команды плеера, которые можно сказать сразу после имени: «Санта, пауза». */
        internal val COMMANDS: Map<String, NoaMedia.Control> = mapOf(
            "пауза" to NoaMedia.Control.PAUSE, "паузу" to NoaMedia.Control.PAUSE, "стоп" to NoaMedia.Control.PAUSE,
            "хватит" to NoaMedia.Control.PAUSE, "замолчи" to NoaMedia.Control.PAUSE, "останови" to NoaMedia.Control.PAUSE,
            "дальше" to NoaMedia.Control.NEXT, "далее" to NoaMedia.Control.NEXT, "следующий" to NoaMedia.Control.NEXT,
            "следующая" to NoaMedia.Control.NEXT, "переключи" to NoaMedia.Control.NEXT,
            "назад" to NoaMedia.Control.PREV, "предыдущий" to NoaMedia.Control.PREV,
            "громче" to NoaMedia.Control.LOUDER, "тише" to NoaMedia.Control.QUIETER,
            "продолжи" to NoaMedia.Control.RESUME, "продолжай" to NoaMedia.Control.RESUME, "играй" to NoaMedia.Control.RESUME,
        )

        /**
         * Что значит услышанное: зов («Ноа», «Эй, Ноа», «Привет, Ноа», «Ноа, ты тут»), команда плеера («Ноа, пауза») или ничего.
         * Тихо вокруг ([playing] = false): законченная фраза должна быть ровно зовом, любая посторонняя речь ([unk]) — отказ.
         * Играет звук: вокруг всегда есть «[unk]» (чужая речь/музыка), поэтому его по краям срезаем, а внутри фразы — нет;
         * одиночное имя при этом принимается только с высокой уверенностью.
         */
        internal fun decide(text: String, words: List<String>, playing: Boolean, confidence: Double = 1.0, final: Boolean = true): Decision? {
            if (words.isEmpty() || confidence < MIN_CONFIDENCE) return null
            var t = text.lowercase().trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            val noisy = "[unk]" in t
            if (noisy) {
                if (!playing && final) return null
                t = t.dropWhile { it == "[unk]" }.dropLastWhile { it == "[unk]" }
                if ("[unk]" in t) return null
            }
            if (t.isEmpty()) return null
            if (t.size == words.size + 1 && t.take(words.size) == words) COMMANDS[t.last()]?.let { return Decision.Command(it) }
            val name = words.joinToString(" ")
            val phrase = t.joinToString(" ")
            if (phrase in setOf("эй $name", "привет $name", "$name ты тут")) return Decision.Wake(ping = phrase.endsWith("тут"))
            // Одно имя — только законченной фразой без постороннего звука (при музыке — ещё и с высокой уверенностью).
            if (phrase == name && final && !noisy && (!playing || confidence >= 0.9)) return Decision.Wake(ping = false)
            return null
        }

        private const val MIN_CONFIDENCE = 0.7

        /**
         * Подтверждение свободным распознаванием: среди слов есть имя (допускаем одну неточность — «санти», «сонта»),
         * а для команды — ещё и слово команды. Короткие имена («Ноа») — только со знакомыми вариантами записи.
         */
        internal fun verifies(freeText: String, words: List<String>, command: Boolean): Boolean {
            val tokens = freeText.lowercase().split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }
            if (tokens.isEmpty() || words.isEmpty()) return false
            fun nameOk(w: String) = tokens.any { t ->
                t == w || (w.length >= 4 && editDistance(t, w) <= 1) || (w.length <= 3 && editDistance(t, w) <= 1 && t.firstOrNull() == w.firstOrNull() && t.length <= 3)
            }
            if (!words.all { nameOk(it) }) return false
            return !command || tokens.any { it in COMMANDS }
        }

        internal fun editDistance(a: String, b: String): Int {
            val dp = IntArray(b.length + 1) { it }
            for (i in 1..a.length) {
                var prev = dp[0]; dp[0] = i
                for (j in 1..b.length) {
                    val tmp = dp[j]
                    dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                    prev = tmp
                }
            }
            return dp[b.length]
        }

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

        private fun isBusy(): Boolean = VoiceNoteService.active || System.currentTimeMillis().let { it < busyUntil || it < cooldownUntil }
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
