package com.kartoteka.app.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import com.kartoteka.app.i18n.I18n
import com.kartoteka.app.i18n.UiLang
import java.util.Locale

/** Живое распознавание речи с микрофона (хорошо поддерживается, в отличие от файлового). */
class NoaListener(private val context: Context) {
    interface Callback {
        fun onPartial(text: String)
        fun onResult(text: String)
        fun onError(message: String?)
        fun onReady()
        fun onEnd()
        /** Уровень громкости 0..1 — для анимации сферы. */
        fun onLevel(level: Float) {}
        /** Человек начал говорить (в этом отрезке). */
        fun onSpeechStart() {}
    }

    private var recognizer: SpeechRecognizer? = null
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    @Volatile private var done = false

    // Глушим системный «бип» распознавания на время прослушивания.
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
    private fun muteBeep(mute: Boolean) = runCatching {
        val dir = if (mute) android.media.AudioManager.ADJUST_MUTE else android.media.AudioManager.ADJUST_UNMUTE
        audio?.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, dir, 0)
        audio?.adjustStreamVolume(android.media.AudioManager.STREAM_NOTIFICATION, dir, 0)
        audio?.adjustStreamVolume(android.media.AudioManager.STREAM_SYSTEM, dir, 0)
    }

    fun available() = runCatching { SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false)

    /** Запуск строго на главном потоке; все вызовы распознавателя защищены. */
    fun start(cb: Callback) {
        main.post { startOnMain(cb) }
    }

    private fun startOnMain(cb: Callback) {
        stop()
        done = false
        muteBeep(true)
        fun finish(body: () -> Unit) {
            if (done) return
            done = true
            muteBeep(false)
            body(); safe { cb.onEnd() }
        }
        // Любой сбой создания/запуска — сообщаем ошибкой, не роняя приложение.
        val r = runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
        if (r == null) { finish { safe { cb.onError(null) } }; return }
        recognizer = r
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = safe { cb.onReady() }
            override fun onResults(results: Bundle) {
                val best = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                finish { safe { cb.onResult(best) } }
            }
            override fun onPartialResults(partialResults: Bundle) {
                safe { partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(cb::onPartial) }
            }
            override fun onError(error: Int) { finish { safe { cb.onError(error.toString()) } } }
            override fun onBeginningOfSpeech() = safe { cb.onSpeechStart() }
            override fun onRmsChanged(rmsdB: Float) = safe { cb.onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f)) }
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        }
        runCatching { r.setRecognitionListener(listener) }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, NoaVoice.localeTag())
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L)
        runCatching { r.startListening(intent) }.onFailure { finish { safe { cb.onError(null) } } }
    }

    private inline fun safe(body: () -> Unit) { runCatching { body() } }

    fun stop() {
        val r = recognizer; recognizer = null
        muteBeep(false)
        main.post { r?.runCatching { cancel() }; r?.runCatching { destroy() } }
    }
}

/** Синтез речи женским голосом (офлайн-движок системы). */
object NoaVoice {
    private var tts: TextToSpeech? = null
    private var ready = false

    fun localeTag(lang: UiLang = I18n.lang): String = when (lang) {
        UiLang.UK -> "uk-UA"; UiLang.EN -> "en-US"; else -> "ru-RU"
    }

    private fun locale(): Locale = when (I18n.lang) {
        UiLang.UK -> Locale("uk", "UA"); UiLang.EN -> Locale.US; else -> Locale("ru", "RU")
    }

    fun init(context: Context) {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) applyVoice()
        }
    }

    private fun applyVoice() {
        val engine = tts ?: return
        runCatching {
            engine.language = locale()
            // Самый качественный установленный голос языка (без интернета); при равенстве — с меньшей задержкой.
            val best = engine.voices
                ?.filter { it.locale.language == locale().language && !it.isNetworkConnectionRequired &&
                    it.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true }
                ?.sortedWith(compareByDescending<Voice> { it.quality }.thenBy { it.latency })
                ?.firstOrNull()
            if (best != null) engine.voice = best
            engine.setPitch(1.0f)
            engine.setSpeechRate(1.03f)
        }
    }

    /** То, что читаем вслух: без эмодзи, кавычек-ёлочек и служебных символов. */
    fun spoken(text: String): String = text
        .replace(Regex("[\\p{So}\\p{Cn}\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{FE0F}]"), "")
        .replace(Regex("[«»\"“”•]"), "")
        .replace(Regex("\\s+"), " ").trim()

    @Volatile var speaking = false
        private set

    private var onDone: (() -> Unit)? = null
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    /** Сказать вслух; [done] — когда договорит (или сразу, если озвучка недоступна). */
    fun speak(context: Context, text: String, done: (() -> Unit)? = null) {
        val ok = runCatching {
            init(context)
            applyVoice()
            onDone = done
            tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) { speaking = true }
                override fun onDone(utteranceId: String?) { speaking = false; val d = onDone; onDone = null; if (d != null) main.post(d) }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { speaking = false; val d = onDone; onDone = null; if (d != null) main.post(d) }
            })
            tts?.speak(spoken(text), TextToSpeech.QUEUE_FLUSH, null, "noa") == TextToSpeech.SUCCESS
        }.getOrDefault(false)
        if (!ok && done != null) { onDone = null; main.post(done) }
    }

    fun stop() { speaking = false; onDone = null; tts?.runCatching { stop() } }

    fun shutdown() { tts?.runCatching { shutdown() }; tts = null; ready = false }
}


/**
 * Разговорное прослушивание: не обрывает фразу на первой паузе. Куски речи склеиваются, а фраза
 * считается законченной после [silenceMs] тишины (или по [finishNow] — нажатию на сферу).
 */
class VoiceSession(context: Context, private val silenceMs: Long = 1500L) {
    interface Events {
        fun onText(live: String)
        fun onLevel(level: Float)
        fun onListening(on: Boolean)
        /** Фраза закончена; пустая строка — ничего не услышали. */
        fun onPhrase(text: String)
    }

    private val listener = NoaListener(context)
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private var events: Events? = null
    private val buffer = StringBuilder()
    private var partial = ""
    private var active = false
    private var retries = 0
    private val finishTask = Runnable { finish() }

    fun available() = listener.available()

    fun start(e: Events) {
        stop()
        events = e; active = true
        buffer.clear(); partial = ""; retries = 0
        e.onListening(true)
        segment()
    }

    private fun current() = (buffer.toString() + " " + partial).trim()

    private val DANGLING = setOf("и", "і", "й", "та", "а", "потом", "потім", "затем", "на", "в", "у", "во", "к", "до", "с", "з", "со", "для", "про",
        "and", "then", "to", "the", "on", "in", "включи", "увімкни", "поставь", "постав", "запиши", "запиш", "отправь", "надішли", "напиши",
        "открой", "відкрий", "скажи", "любую", "якусь", "через", "его", "її", "его", "мне", "мені")

    /** Последнее слово — союз/предлог/команда без продолжения. */
    private fun dangling(text: String) = text.lowercase().split(Regex("[^\\p{L}]+")).lastOrNull { it.isNotBlank() } in DANGLING

    private fun segment() {
        if (!active) return
        listener.start(object : NoaListener.Callback {
            override fun onPartial(text: String) {
                partial = text
                main.removeCallbacks(finishTask)
                events?.onText(current())
            }
            override fun onResult(text: String) {
                if (text.isNotBlank()) { buffer.append(' ').append(text.trim()) }
                partial = ""
                events?.onText(current())
                // Ждём продолжения: если человек заговорит снова — допишем, если нет — фраза готова.
                main.removeCallbacks(finishTask)
                // Фраза оборвалась на «и», «на», «включи»… — человек явно не договорил: ждём вдвое дольше.
                main.postDelayed(finishTask, if (dangling(current())) silenceMs * 2 else silenceMs)
                main.post { segment() }
            }
            override fun onError(message: String?) {
                val code = message?.toIntOrNull()
                val silence = code == android.speech.SpeechRecognizer.ERROR_NO_MATCH || code == android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                // Сбой перезапуска (занят, клиент) посреди фразы — не обрываем её, а пробуем слушать дальше.
                if ((!silence || dangling(current())) && buffer.isNotEmpty() && retries < 3) {
                    retries++
                    main.postDelayed({ segment() }, 300)
                    return
                }
                // Тишина/нет совпадения: если что-то уже сказано — заканчиваем фразу, иначе — «ничего не услышали».
                main.removeCallbacks(finishTask)
                finish()
            }
            override fun onSpeechStart() {
                // Снова заговорили — фраза продолжается.
                main.removeCallbacks(finishTask)
            }
            override fun onReady() = Unit
            override fun onEnd() = Unit
            override fun onLevel(level: Float) { events?.onLevel(level) }
        })
    }

    /** Закончить фразу сейчас (нажали на сферу). */
    fun finishNow() { main.removeCallbacks(finishTask); finish() }

    private fun finish() {
        if (!active) return
        active = false
        listener.stop()
        val text = current()
        val e = events
        events = null
        e?.onListening(false)
        e?.onLevel(0f)
        e?.onPhrase(text)
    }

    fun stop() {
        main.removeCallbacks(finishTask)
        val wasActive = active
        active = false
        listener.stop()
        if (wasActive) { events?.onListening(false); events?.onLevel(0f) }
        events = null
    }
}
