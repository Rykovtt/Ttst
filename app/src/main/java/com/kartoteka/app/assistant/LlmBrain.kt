package com.kartoteka.app.assistant

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * «Мозг» Ноа: офлайн языковая модель через MediaPipe LLM. Модель один раз скачивается в приложение
 * одной кнопкой (системный загрузчик — продолжает качать в фоне и после закрытия приложения),
 * дальше всё работает без интернета. Любая ошибка не роняет приложение — Ноа переключается на быстрые команды.
 */
class LlmBrain(context: Context) {
    enum class State { UNKNOWN, NEEDS_MODEL, PREPARING, READY, UNAVAILABLE }

    /** Состояние фоновой загрузки модели. */
    sealed interface Download {
        data object None : Download
        data class Running(val percent: Int, val doneMb: Long, val totalMb: Long, val waiting: Boolean) : Download
        data object Done : Download
        data class Failed(val reason: Int) : Download
    }

    @Volatile var state: State = State.UNKNOWN
        private set
    @Volatile var detail: String = ""
        private set

    private val app = context.applicationContext
    private val dir = File(app.filesDir, "llm").apply { mkdirs() }
    // Скачанная модель лежит в личной папке приложения на общем хранилище (туда пишет системный загрузчик).
    private val extDir: File? get() = app.getExternalFilesDir("llm")?.apply { mkdirs() }
    private val importedFile = File(dir, "model.task")
    private val downloadedFile: File? get() = extDir?.let { File(it, "model.task") }
    private val partFile: File? get() = extDir?.let { File(it, "model.part") }
    private val kindFile = File(dir, "model.kind")
    private val prefs = app.getSharedPreferences("llm", Context.MODE_PRIVATE)
    private var llm: LlmInference? = null

    val supported: Boolean get() = true

    /** Текущий файл модели (свой выбранный файл в приоритете). */
    val modelFile: File?
        get() = importedFile.takeIf { it.big() } ?: downloadedFile?.takeIf { it.big() }

    private fun File.big() = exists() && length() > 100L * 1024 * 1024

    fun hasModel(): Boolean { syncDownload(); return modelFile != null }
    fun modelSizeMb(): Long = (modelFile?.length() ?: 0) / (1024 * 1024)

    // ---- скачивание одной кнопкой ----

    /** Начать загрузку модели. false — если места мало или загрузчик недоступен. */
    fun startDownload(): Boolean = runCatching {
        val dm = app.getSystemService(DownloadManager::class.java) ?: return false
        cancelDownload()
        val part = partFile ?: return false
        part.delete()
        val req = DownloadManager.Request(Uri.parse(MODEL_URL))
            .setTitle("RVault — " + MODEL_NAME)
            .setDescription(MODEL_NAME)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(part))
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
        prefs.edit().putLong(KEY_ID, dm.enqueue(req)).apply()
        true
    }.getOrDefault(false)

    /** Хватает ли места под модель (с запасом). */
    fun enoughSpace(): Boolean = (extDir?.usableSpace ?: 0L) > MODEL_BYTES + 200L * 1024 * 1024

    fun cancelDownload() {
        val id = prefs.getLong(KEY_ID, -1L)
        if (id >= 0) runCatching { app.getSystemService(DownloadManager::class.java)?.remove(id) }
        prefs.edit().remove(KEY_ID).apply()
        partFile?.delete()
    }

    /** Опрос загрузки; по завершении сам переносит файл на место модели. */
    fun syncDownload(): Download {
        val id = prefs.getLong(KEY_ID, -1L)
        if (id < 0) return Download.None
        val dm = app.getSystemService(DownloadManager::class.java) ?: return Download.None
        val row = runCatching {
            dm.query(DownloadManager.Query().setFilterById(id))?.use { c ->
                if (!c.moveToFirst()) null
                else Triple(
                    c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                    c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                    c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)) to
                        c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)),
                )
            }
        }.getOrNull()
        if (row == null) { prefs.edit().remove(KEY_ID).apply(); return Download.None }
        val (status, done, totalReason) = row
        val (total, reason) = totalReason
        return when (status) {
            DownloadManager.STATUS_SUCCESSFUL -> {
                val part = partFile; val target = downloadedFile
                val ok = part != null && target != null && part.big() &&
                    run { target.delete(); part.renameTo(target) }
                prefs.edit().remove(KEY_ID).apply()
                if (ok) { kindFile.writeText(KIND_QWEN); state = State.UNKNOWN; Download.Done } else Download.Failed(-1)
            }
            DownloadManager.STATUS_FAILED -> {
                prefs.edit().remove(KEY_ID).apply(); partFile?.delete()
                Download.Failed(reason)
            }
            else -> {
                val t = if (total > 0) total else MODEL_BYTES
                Download.Running(
                    ((done * 100) / t).toInt().coerceIn(0, 100), done / MB, t / MB,
                    waiting = status == DownloadManager.STATUS_PAUSED || status == DownloadManager.STATUS_PENDING,
                )
            }
        }
    }

    /** Скопировать выбранный пользователем файл модели в приложение. onProgress: 0..100 (или -1). */
    suspend fun importModel(uri: Uri, onProgress: (Int) -> Unit): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val total = app.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
            val name = runCatching {
                app.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { if (it.moveToFirst()) it.getString(0) else null }
            }.getOrNull().orEmpty()
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
            if (!tmp.big()) { tmp.delete(); return@runCatching false }
            close()
            importedFile.delete()
            tmp.renameTo(importedFile).also { if (it) kindFile.writeText(kindOf(name)); state = State.UNKNOWN }
        }.getOrDefault(false)
    }

    fun deleteModel() {
        close()
        importedFile.delete(); downloadedFile?.delete(); kindFile.delete()
        state = State.NEEDS_MODEL
    }

    /** Загрузить модель в движок. Долгая операция — вызывать в фоне. */
    suspend fun prepare(): State = withContext(Dispatchers.IO) {
        val file = if (hasModel()) modelFile else null
        if (file == null) { state = State.NEEDS_MODEL; return@withContext state }
        llm?.let { state = State.READY; return@withContext state }
        state = State.PREPARING
        val ok = runCatching {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(file.absolutePath)
                .setMaxTokens(1024)
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
            runCatching { engine.generateResponse(wrap(prompt)) }.getOrNull()
        }
    }

    /** Оборачиваем запрос в формат диалога, который понимает конкретная модель. */
    private fun wrap(prompt: String): String {
        // Файл без пометки — модель, выбранная в прошлых версиях (там была только Gemma).
        val kind = runCatching { kindFile.readText().trim() }
            .getOrDefault(if (importedFile.big()) KIND_GEMMA else KIND_QWEN)
        return if (kind == KIND_GEMMA) "<start_of_turn>user\n$prompt<end_of_turn>\n<start_of_turn>model\n"
        else "<|im_start|>user\n$prompt<|im_end|>\n<|im_start|>assistant\n"
    }

    fun close() { runCatching { llm?.close() }; llm = null }

    companion object {
        /** Открытая (без регистрации) многоязычная модель, хорошо понимает русский и украинский. */
        const val MODEL_NAME = "Qwen2.5 1.5B"
        const val MODEL_URL =
            "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task"
        const val MODEL_BYTES = 1_597_913_616L
        private const val MB = 1024L * 1024
        private const val KEY_ID = "download_id"
        private const val KIND_QWEN = "qwen"
        private const val KIND_GEMMA = "gemma"

        fun kindOf(fileName: String): String = if (fileName.contains("gemma", true)) KIND_GEMMA else KIND_QWEN
    }
}
