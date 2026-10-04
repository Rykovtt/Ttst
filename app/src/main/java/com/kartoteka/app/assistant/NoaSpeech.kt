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
            override fun onBeginningOfSpeech() = Unit
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
            // Выбираем женский голос, если система его помечает.
            val female = engine.voices?.filter { it.locale.language == locale().language && !it.isNetworkConnectionRequired }
                ?.firstOrNull { v -> v.name.contains("female", true) || v.features?.any { it.contains("female", true) } == true }
                ?: engine.voices?.firstOrNull { it.locale.language == locale().language && it.quality >= Voice.QUALITY_NORMAL }
            if (female != null) engine.voice = female
            engine.setPitch(1.05f)
            engine.setSpeechRate(1.0f)
        }
    }

    fun speak(context: Context, text: String) {
        runCatching {
            init(context)
            applyVoice()
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "noa")
        }
    }

    fun stop() { tts?.runCatching { stop() } }

    fun shutdown() { tts?.runCatching { shutdown() }; tts = null; ready = false }
}
