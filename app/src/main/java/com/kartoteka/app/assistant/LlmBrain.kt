package com.kartoteka.app.assistant

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
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
    // Модель живёт в отдельном процессе (BrainService): если ей не хватит памяти, падает только он.
    private var service: android.os.Messenger? = null
    private var connecting: kotlinx.coroutines.CompletableDeferred<android.os.Messenger?>? = null
    private val replies = java.util.concurrent.ConcurrentHashMap<Int, kotlinx.coroutines.CompletableDeferred<android.os.Bundle?>>()
    private val ids = java.util.concurrent.atomic.AtomicInteger(1)
    @Volatile private var prepared = false
    private val replyTo = android.os.Messenger(android.os.Handler(android.os.Looper.getMainLooper()) { msg ->
        when (msg.what) {
            BrainService.MSG_STATE -> replies.remove(0)?.complete(msg.data)
            BrainService.MSG_ANSWER -> replies.remove(msg.data.getInt(BrainService.KEY_ID))?.complete(msg.data)
        }
        true
    })
    private val connection = object : android.content.ServiceConnection {
        override fun onServiceConnected(name: android.content.ComponentName?, binder: android.os.IBinder?) {
            service = binder?.let { android.os.Messenger(it) }; connecting?.complete(service)
        }
        override fun onServiceDisconnected(name: android.content.ComponentName?) = died()
        override fun onBindingDied(name: android.content.ComponentName?) = died()
    }

    /** Процесс модели умер (обычно — не хватило памяти): все ожидающие получают null. */
    private fun died() {
        service = null; prepared = false
        connecting?.complete(null)
        replies.values.forEach { it.complete(null) }; replies.clear()
        runCatching { app.unbindService(connection) }
    }

    private suspend fun connect(): android.os.Messenger? {
        service?.let { return it }
        val wait = kotlinx.coroutines.CompletableDeferred<android.os.Messenger?>()
        connecting = wait
        val ok = withContext(Dispatchers.Main) {
            runCatching { app.bindService(android.content.Intent(app, BrainService::class.java), connection, Context.BIND_AUTO_CREATE) }.getOrDefault(false)
        }
        if (!ok) return null
        return withTimeoutOrNull(20_000) { wait.await() }
    }

    private suspend fun request(what: Int, id: Int, data: android.os.Bundle, timeoutMs: Long): android.os.Bundle? {
        val to = connect() ?: return null
        val wait = kotlinx.coroutines.CompletableDeferred<android.os.Bundle?>()
        replies[id] = wait
        val sent = runCatching { to.send(android.os.Message.obtain(null, what).apply { this.data = data; replyTo = this@LlmBrain.replyTo }) }.isSuccess
        if (!sent) { replies.remove(id); died(); return null }
        return withTimeoutOrNull(timeoutMs) { wait.await() }.also { replies.remove(id) }
    }

    /** Модель уже падала на этом телефоне (не хватило памяти) — не пытаемся снова сами. */
    val crashed: Boolean get() = prefs.getString(KEY_CRASHED, null)?.let { it == currentKind() } ?: false

    /** Попробовать запустить модель ещё раз (после смены модели или по кнопке). */
    fun clearCrash() { prefs.edit().remove(KEY_CRASHED).apply() }

    private fun currentKind() = runCatching { kindFile.readText().trim() }.getOrDefault(KIND_QWEN)

    init {
        // Прошлый запуск умер во время загрузки модели — считаем, что ей не хватает памяти.
        prefs.getString(KEY_LOADING, null)?.let { prefs.edit().putString(KEY_CRASHED, it).remove(KEY_LOADING).apply() }
    }

    val supported: Boolean get() = true

    /** Модель загружена в движок и отвечает. */
    val isReady: Boolean get() = prepared && service != null

    /** Текущий файл модели (свой выбранный файл в приоритете). */
    val modelFile: File?
        get() = importedFile.takeIf { it.big() } ?: downloadedFile?.takeIf { it.big() }

    private fun File.big() = exists() && length() > 100L * 1024 * 1024

    fun hasModel(): Boolean { syncDownload(); return modelFile != null }
    fun modelSizeMb(): Long = (modelFile?.length() ?: 0) / (1024 * 1024)

    // ---- скачивание одной кнопкой ----

    /** Модели на выбор: быстрая (лёгкая) и умная (крупнее, нужен телефон помощнее). */
    enum class Model(val id: String, val title: String, val url: String, val bytes: Long, val kind: String, val minRamGb: Int) {
        FAST("qwen", "Qwen2.5 1.5B", MODEL_URL, MODEL_BYTES, KIND_QWEN, 4),
        SMART(
            "phi4", "Phi-4 mini 3.8B",
            "https://huggingface.co/litert-community/Phi-4-mini-instruct/resolve/main/Phi-4-mini-instruct_multi-prefill-seq_q8_ekv1280.task",
            3_944_275_882L, KIND_PHI, 8,
        ),
    }

    /** Какая модель установлена (по пометке рядом с файлом). */
    fun installed(): Model? {
        if (!hasModel()) return null
        val kind = runCatching { kindFile.readText().trim() }.getOrDefault(KIND_QWEN)
        return Model.entries.firstOrNull { it.kind == kind }
    }

    /** Оперативная память телефона, ГБ — чтобы подсказать, потянет ли умная модель. */
    fun ramGb(): Double = runCatching {
        val mi = android.app.ActivityManager.MemoryInfo()
        app.getSystemService(android.app.ActivityManager::class.java).getMemoryInfo(mi)
        mi.totalMem / 1024.0 / 1024.0 / 1024.0
    }.getOrDefault(0.0)

    /** Начать загрузку модели. false — если места мало или загрузчик недоступен. */
    fun startDownload(model: Model = Model.FAST): Boolean = runCatching {
        val dm = app.getSystemService(DownloadManager::class.java) ?: return false
        cancelDownload()
        val part = partFile ?: return false
        part.delete()
        prefs.edit().putString(KEY_PENDING_KIND, model.kind).apply()
        val req = DownloadManager.Request(Uri.parse(model.url))
            .setTitle("RVault — " + model.title)
            .setDescription(model.title)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(part))
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
        prefs.edit().putLong(KEY_ID, dm.enqueue(req)).apply()
        true
    }.getOrDefault(false)

    /** Хватает ли места под модель (с запасом). */
    fun enoughSpace(model: Model = Model.FAST): Boolean = (extDir?.usableSpace ?: 0L) > model.bytes + 200L * 1024 * 1024

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
                if (ok) {
                    close()
                    importedFile.delete()
                    kindFile.writeText(prefs.getString(KEY_PENDING_KIND, KIND_QWEN) ?: KIND_QWEN)
                    clearCrash()
                    state = State.UNKNOWN; Download.Done
                } else Download.Failed(-1)
            }
            DownloadManager.STATUS_FAILED -> {
                prefs.edit().remove(KEY_ID).apply(); partFile?.delete()
                Download.Failed(reason)
            }
            else -> {
                val t = if (total > 0) total else Model.entries.firstOrNull { it.kind == prefs.getString(KEY_PENDING_KIND, "") }?.bytes ?: MODEL_BYTES
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
        clearCrash()
        state = State.NEEDS_MODEL
    }

    /** Загрузить модель (в отдельном процессе). Долгая операция — вызывать в фоне. */
    suspend fun prepare(): State = withContext(Dispatchers.IO) {
        val file = if (hasModel()) modelFile else null
        if (file == null) { state = State.NEEDS_MODEL; return@withContext state }
        if (isReady) { state = State.READY; return@withContext state }
        if (crashed) {
            detail = com.kartoteka.app.i18n.t("Телефону не хватило памяти для этой модели. Выберите быструю модель в настройках ассистента.")
            state = State.UNAVAILABLE; return@withContext state
        }
        state = State.PREPARING
        // Пометка «загружаем»: если процесс модели умрёт, в следующий раз сами пробовать не будем.
        prefs.edit().putString(KEY_LOADING, currentKind()).commit()
        val reply = request(BrainService.MSG_PREPARE, 0, android.os.Bundle().apply {
            putString(BrainService.KEY_PATH, file.absolutePath); putInt(BrainService.KEY_MAX, 1280)
        }, 240_000)
        prefs.edit().remove(KEY_LOADING).apply()
        when {
            reply == null -> {
                prefs.edit().putString(KEY_CRASHED, currentKind()).apply()
                detail = com.kartoteka.app.i18n.t("Телефону не хватило памяти для этой модели. Выберите быструю модель в настройках ассистента.")
                state = State.UNAVAILABLE
            }
            reply.getBoolean(BrainService.KEY_OK) -> { prepared = true; state = State.READY }
            else -> { detail = reply.getString(BrainService.KEY_DETAIL).orEmpty(); state = State.UNAVAILABLE }
        }
        state
    }

    /** Спросить модель. Возвращает текст ответа или null при любой ошибке. */
    suspend fun ask(prompt: String): String? = withContext(Dispatchers.IO) {
        if (!isReady) return@withContext null
        val id = ids.incrementAndGet()
        request(BrainService.MSG_ASK, id, android.os.Bundle().apply {
            putInt(BrainService.KEY_ID, id); putString(BrainService.KEY_PROMPT, wrap(prompt))
        }, 90_000)?.getString(BrainService.KEY_TEXT)
    }

    /** Оборачиваем запрос в формат диалога, который понимает конкретная модель. */
    private fun wrap(prompt: String): String {
        // Файл без пометки — модель, выбранная в прошлых версиях (там была только Gemma).
        val kind = runCatching { kindFile.readText().trim() }
            .getOrDefault(if (importedFile.big()) KIND_GEMMA else KIND_QWEN)
        return when (kind) {
            KIND_GEMMA -> "<start_of_turn>user\n$prompt<end_of_turn>\n<start_of_turn>model\n"
            KIND_PHI -> "<|user|>\n$prompt<|end|>\n<|assistant|>\n"
            else -> "<|im_start|>user\n$prompt<|im_end|>\n<|im_start|>assistant\n"
        }
    }

    fun close() {
        prepared = false
        service?.let { to -> runCatching { to.send(android.os.Message.obtain(null, BrainService.MSG_CLOSE)) } }
    }

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
        private const val KIND_PHI = "phi4"
        private const val KEY_PENDING_KIND = "pending_kind"
        private const val KEY_CRASHED = "crashed_kind"
        private const val KEY_LOADING = "loading_kind"

        fun kindOf(fileName: String): String = when {
            fileName.contains("gemma", true) -> KIND_GEMMA
            fileName.contains("phi", true) -> KIND_PHI
            else -> KIND_QWEN
        }
    }
}
