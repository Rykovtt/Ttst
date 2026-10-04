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
    }

    private var recognizer: SpeechRecognizer? = null

    fun available() = SpeechRecognizer.isRecognitionAvailable(context)

    /** Вызывать с главного потока. */
    fun start(cb: Callback) {
        stop()
        val r = runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull() ?: return cb.onError(null)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = cb.onReady()
            override fun onResults(results: Bundle) {
                val best = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                cb.onResult(best); cb.onEnd()
            }
            override fun onPartialResults(partialResults: Bundle) {
                partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(cb::onPartial)
            }
            override fun onError(error: Int) { cb.onError(error.toString()); cb.onEnd() }
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        val tag = NoaVoice.localeTag()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        runCatching { r.startListening(intent) }.onFailure { cb.onError(null); cb.onEnd() }
    }

    fun stop() {
        recognizer?.runCatching { stopListening() }
        recognizer?.runCatching { destroy() }
        recognizer = null
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
        init(context)
        applyVoice()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "noa")
    }

    fun stop() { tts?.runCatching { stop() } }

    fun shutdown() { tts?.runCatching { shutdown() }; tts = null; ready = false }
}
