package com.kartoteka.app.voice

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.kartoteka.app.data.VoiceStorage
import com.kartoteka.app.i18n.I18n
import com.kartoteka.app.i18n.UiLang
import com.kartoteka.app.i18n.t
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Расшифровка голосовой заметки строго на устройстве (on-device распознавание Android 13+).
 * Звук никуда не отправляется: если офлайн-распознавания нет — честно говорим об этом.
 */
object VoiceTranscriber {
    sealed interface Result {
        data class Text(val text: String) : Result
        data class Unavailable(val reason: String) : Result
    }

    fun languageTag(lang: UiLang = I18n.lang): String = when (lang) {
        UiLang.UK -> "uk-UA"
        UiLang.EN -> "en-US"
        else -> "ru-RU"
    }

    suspend fun transcribe(context: Context, pcm: ByteArray, tag: String = languageTag()): Result {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return Result.Unavailable(t("Распознавание без интернета доступно на Android 13 и новее"))
        }
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            return Result.Unavailable(t("На телефоне нет распознавания речи без интернета"))
        }
        if (pcm.isEmpty()) return Result.Text("")
        val limit = VoiceStorage.durationMs(pcm.size.toLong()) * 3 + 60_000
        return withContext(Dispatchers.Main) {
            withTimeoutOrNull(limit) { run33(context, pcm, tag) }
                ?: Result.Unavailable(t("Распознавание не ответило вовремя"))
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun run33(context: Context, pcm: ByteArray, tag: String): Result = try {
        runOnDevice(context, pcm, tag)
    } catch (t: Throwable) {
        // Любой сбой распознавателя (включая устройства без поддержки файлового источника) — не роняем приложение.
        Result.Unavailable(t("На этом телефоне не удалось распознать речь"))
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun runOnDevice(context: Context, pcm: ByteArray, tag: String): Result {
        val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        try {
            val base = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            when (val s = support(context, recognizer, base)) {
                null -> Unit // не смогли проверить — пробуем
                else -> {
                    val lang = tag.substringBefore('-')
                    fun List<String>.has() = any { it.substringBefore('-').equals(lang, ignoreCase = true) }
                    if (!s.installedOnDeviceLanguages.has()) {
                        return if (s.supportedOnDeviceLanguages.has() || s.pendingOnDeviceLanguages.has()) {
                            recognizer.triggerModelDownload(base)
                            Result.Unavailable(t("Скачивается офлайн-модель языка. Повторите расшифровку чуть позже"))
                        } else {
                            Result.Unavailable(t("Офлайн-распознавание этого языка не поддерживается на телефоне"))
                        }
                    }
                }
            }
            return listen(recognizer, base, pcm)
        } finally {
            runCatching { recognizer.destroy() }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun support(context: Context, recognizer: SpeechRecognizer, intent: Intent): RecognitionSupport? =
        withTimeoutOrNull(10_000) {
            suspendCancellableCoroutine { cont ->
                recognizer.checkRecognitionSupport(intent, ContextCompat.getMainExecutor(context), object : RecognitionSupportCallback {
                    override fun onSupportResult(support: RecognitionSupport) { if (cont.isActive) cont.resume(support) }
                    override fun onError(error: Int) { if (cont.isActive) cont.resume(null) }
                })
            }
        }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun listen(recognizer: SpeechRecognizer, base: Intent, pcm: ByteArray): Result {
        val (read, write) = ParcelFileDescriptor.createPipe()
        val intent = Intent(base)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, read)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, VoiceStorage.SAMPLE_RATE)
            .putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
        val parts = mutableListOf<String>()
        try {
            return suspendCancellableCoroutine { cont ->
                fun finish(r: Result) { if (cont.isActive) cont.resume(r) }
                fun done() = finish(Result.Text(parts.joinToString(" ").trim()))
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onSegmentResults(segmentResults: Bundle) {
                        segmentResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                            ?.takeIf { it.isNotBlank() }?.let(parts::add)
                    }
                    override fun onEndOfSegmentedSession() = done()
                    override fun onResults(results: Bundle) {
                        results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                            ?.takeIf { it.isNotBlank() }?.let(parts::add)
                        done()
                    }
                    override fun onError(error: Int) {
                        if (parts.isNotEmpty() || error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) done()
                        else finish(Result.Unavailable(t("Не удалось распознать (код %1\$s)", error)))
                    }
                    override fun onReadyForSpeech(params: Bundle?) = Unit
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit
                    override fun onPartialResults(partialResults: Bundle?) = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })
                // На части устройств файловый источник не поддерживается — ловим и отдаём понятный ответ.
                try {
                    recognizer.startListening(intent)
                } catch (e: Throwable) {
                    finish(Result.Unavailable(t("На этом телефоне не удалось распознать речь")))
                    return@suspendCancellableCoroutine
                }
                // Подаём звук в канал; конец файла = конец сеанса.
                CoroutineScope(Dispatchers.IO).launch {
                    runCatching {
                        ParcelFileDescriptor.AutoCloseOutputStream(write).use { out ->
                            var i = 0
                            while (i < pcm.size) {
                                val n = minOf(CHUNK, pcm.size - i)
                                out.write(pcm, i, n)
                                i += n
                            }
                        }
                    }
                }
                // cancel() обязан вызываться на главном потоке — иначе SpeechRecognizer бросит исключение.
                cont.invokeOnCancellation {
                    android.os.Handler(android.os.Looper.getMainLooper()).post { runCatching { recognizer.cancel() } }
                }
            }
        } finally {
            runCatching { read.close() }
        }
    }

    private const val CHUNK = 8 * 1024
}
