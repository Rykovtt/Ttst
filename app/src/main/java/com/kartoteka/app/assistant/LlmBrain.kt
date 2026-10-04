package com.kartoteka.app.assistant

import android.content.Context
import android.net.Uri
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * «Мозг» Ноа: офлайн языковая модель (Gemma) через MediaPipe LLM. Модель (~1.3 ГБ) пользователь
 * один раз кладёт в приложение (выбирает файл или скачивает по ссылке), дальше всё работает без интернета.
 * Любая ошибка не роняет приложение — Ноа переключается на быстрые команды.
 */
class LlmBrain(context: Context) {
    enum class State { UNKNOWN, NEEDS_MODEL, PREPARING, READY, UNAVAILABLE }

    @Volatile var state: State = State.UNKNOWN
        private set
    @Volatile var detail: String = ""
        private set

    private val app = context.applicationContext
    private val dir = File(app.filesDir, "llm").apply { mkdirs() }
    val modelFile = File(dir, "model.task")
    private var llm: LlmInference? = null

    val supported: Boolean get() = true

    fun hasModel(): Boolean = modelFile.exists() && modelFile.length() > 100L * 1024 * 1024
    fun modelSizeMb(): Long = if (modelFile.exists()) modelFile.length() / (1024 * 1024) else 0

    /** Скопировать выбранный пользователем файл модели в приложение. onProgress: 0..100 (или -1). */
    suspend fun importModel(uri: Uri, onProgress: (Int) -> Unit): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val total = app.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
            val tmp = File(dir, "model.part")
            app.contentResolver.openInputStream(uri)!!.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(1 shl 20); var copied = 0L; var n: Int
                    while (input.read(buf).also { n = it } > 0) {
                        out.write(buf, 0, n); copied += n
                        if (total > 0) onProgress(((copied * 100) / total).toInt())
                    }
                }
            }
            if (tmp.length() < 100L * 1024 * 1024) { tmp.delete(); return@runCatching false }
            if (modelFile.exists()) modelFile.delete()
            tmp.renameTo(modelFile)
        }.getOrDefault(false)
    }

    /** Скачать модель по прямой ссылке. */
    suspend fun downloadModel(url: String, onProgress: (Int) -> Unit): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 30_000; readTimeout = 30_000; instanceFollowRedirects = true
            }
            conn.inputStream.use { input ->
                val total = conn.contentLengthLong
                val tmp = File(dir, "model.part")
                tmp.outputStream().use { out ->
                    val buf = ByteArray(1 shl 20); var copied = 0L; var n: Int
                    while (input.read(buf).also { n = it } > 0) {
                        out.write(buf, 0, n); copied += n
                        if (total > 0) onProgress(((copied * 100) / total).toInt())
                    }
                }
                if (tmp.length() < 100L * 1024 * 1024) { tmp.delete(); return@runCatching false }
                if (modelFile.exists()) modelFile.delete()
                tmp.renameTo(modelFile)
            }
        }.getOrDefault(false)
    }

    fun deleteModel() { runCatching { llm?.close() }; llm = null; modelFile.delete(); state = State.NEEDS_MODEL }

    /** Загрузить модель в движок. Долгая операция — вызывать в фоне. */
    suspend fun prepare(): State = withContext(Dispatchers.IO) {
        if (!hasModel()) { state = State.NEEDS_MODEL; return@withContext state }
        llm?.let { state = State.READY; return@withContext state }
        state = State.PREPARING
        val ok = runCatching {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(512)
                .setMaxTopK(40)
                .build()
            llm = LlmInference.createFromOptions(app, options)
        }.onFailure { detail = (it.message ?: it::class.java.simpleName).take(160) }.isSuccess
        state = if (ok) State.READY else State.UNAVAILABLE
        state
    }

    /** Спросить модель. Возвращает текст ответа или null при любой ошибке. */
    suspend fun ask(prompt: String): String? = withContext(Dispatchers.IO) {
        val engine = llm ?: return@withContext null
        withTimeoutOrNull(60_000) {
            runCatching { engine.generateResponse(gemma(prompt)) }.getOrNull()
        }
    }

    /** Формат инструкций Gemma. */
    private fun gemma(prompt: String) = "<start_of_turn>user\n$prompt<end_of_turn>\n<start_of_turn>model\n"

    fun close() { runCatching { llm?.close() }; llm = null }
}
