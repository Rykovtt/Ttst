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
    // Модели .litertlm (Gemma 4) обязаны называться «*.litertlm»: движок выбирает формат по расширению (под именем .task он ищет zip и падает).
    private val importedLite = File(dir, "model.litertlm")
    private val downloadedLite: File? get() = extDir?.let { File(it, "model.litertlm") }
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
        if (service != null || connecting?.isCompleted == false) lastDeath = System.currentTimeMillis()
        service = null; prepared = false; verified = false
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

    /** Что случилось при последнем сбое — для настроек. */
    val crashDetail: String get() = prefs.getString(KEY_CRASH_DETAIL, null)
        ?: t("Модель не запустилась на этом телефоне. Нажмите «Попробовать снова» в настройках ассистента.")

    /** Попробовать запустить модель ещё раз (после смены модели или по кнопке). */
    fun clearCrash() { prefs.edit().remove(KEY_CRASHED).remove(KEY_CRASH_DETAIL).apply() }

    private fun currentKind() = KIND_GEMMA4

    @Volatile private var lastDeath = 0L
    /** На чём работает модель: «gpu» / «cpu». */
    @Volatile var backend: String = ""
        private set

    init {
        // Модель работает в отдельном процессе, так что смерть приложения во время загрузки — не её вина
        // (свернули, закрыли). Старую пометку «не хватило памяти» из версий, где модель жила в приложении, снимаем.
        prefs.edit().remove(KEY_LOADING).apply()
        if (!prefs.getBoolean(KEY_CRASH_RESET, false)) prefs.edit().remove(KEY_CRASHED).putBoolean(KEY_CRASH_RESET, true).apply()
        // 3.4.2: движок падал на видеокарте — прежние пометки о сбое сбрасываем, модель получает чистую попытку на процессоре.
        // 3.5.1: «ранний стоп» валил движок; пометки о сбое сбрасываем ещё раз.
        if (!prefs.getBoolean("crash_reset_351", false)) prefs.edit().remove(KEY_CRASHED).remove(KEY_CRASH_DETAIL).putBoolean("crash_reset_351", true).apply()
        if (!prefs.getBoolean("crash_reset_342", false)) prefs.edit().remove(KEY_CRASHED).remove(KEY_CRASH_DETAIL).putBoolean("crash_reset_342", true).apply()
    }

    /** Почему система закрыла процесс модели — по журналу выходов (Android 11+). */
    private fun brainExitReason(since: Long): String? {
        if (android.os.Build.VERSION.SDK_INT < 30) return null
        val am = app.getSystemService(android.app.ActivityManager::class.java) ?: return null
        val info = runCatching { am.getHistoricalProcessExitReasons(app.packageName, 0, 10) }.getOrNull()
            ?.firstOrNull { it.processName.endsWith(":brain") && it.timestamp >= since - 5_000 } ?: return null
        return when (info.reason) {
            android.app.ApplicationExitInfo.REASON_LOW_MEMORY ->
                t("Телефону не хватило памяти для этой модели. Закройте тяжёлые приложения и попробуйте снова.")
            android.app.ApplicationExitInfo.REASON_CRASH_NATIVE, android.app.ApplicationExitInfo.REASON_CRASH ->
                t("Движок ИИ не смог открыть эту модель (сбой при загрузке, не память). Перезапустите приложение и попробуйте снова.")
            android.app.ApplicationExitInfo.REASON_SIGNALED ->
                if (info.status == 9) t("Система остановила модель, чтобы освободить память. Попробуйте снова, когда закроете другие приложения.")
                else t("Движок ИИ не смог открыть эту модель (сбой при загрузке, не память). Перезапустите приложение и попробуйте снова.")
            else -> null
        }
    }

    private fun t(s: String) = com.kartoteka.app.i18n.t(s)

    val supported: Boolean get() = true

    /** Модель загружена в движок и отвечает. */
    val isReady: Boolean get() = prepared && service != null

    /** Текущий файл модели (свой выбранный файл в приоритете). */
    val modelFile: File?
        get() = importedLite.takeIf { it.big() } ?: downloadedLite?.takeIf { it.big() }

    private fun File.big() = exists() && length() > 100L * 1024 * 1024

    fun hasModel(): Boolean { syncDownload(); dropLegacy(); return modelFile != null }

    /**
     * Прежние модели (Qwen, Phi-4, свой .task) больше не поддерживаются: файлы удаляем (это несколько ГБ), нужна Gemma 4.
     * Файл Gemma 4, скачанный под старым именем «model.task», просто переименовываем.
     */
    private fun dropLegacy() {
        val old = listOfNotNull(importedFile, downloadedFile).filter { it.exists() }
        if (old.isEmpty()) return
        val kind = runCatching { kindFile.readText().trim() }.getOrDefault("")
        for (f in old) {
            val target = File(f.parentFile, "model.litertlm")
            if (kind == KIND_GEMMA4 && f.big() && !target.exists() && f.renameTo(target)) continue
            f.delete()
        }
        if (kind != KIND_GEMMA4) kindFile.delete()
    }
    fun modelSizeMb(): Long = (modelFile?.length() ?: 0) / (1024 * 1024)

    // ---- скачивание одной кнопкой ----

    /** Единственная поддерживаемая модель: Gemma 4 E2B на движке LiteRT-LM (быстрее и точнее прежних Phi-4 и Qwen). */
    enum class Model(val id: String, val title: String, val url: String, val bytes: Long, val kind: String, val minRamGb: Int) {
        GEMMA4(
            "gemma4", "Gemma 4 E2B",
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
            2_588_147_712L, KIND_GEMMA4, 6,
        ),
    }

    /** Какая модель установлена (по пометке рядом с файлом). */
    fun installed(): Model? {
        if (!hasModel()) return null
        return Model.GEMMA4
    }

    /** Оперативная память телефона, ГБ — чтобы подсказать, потянет ли умная модель. */
    fun ramGb(): Double = runCatching {
        val mi = android.app.ActivityManager.MemoryInfo()
        app.getSystemService(android.app.ActivityManager::class.java).getMemoryInfo(mi)
        mi.totalMem / 1024.0 / 1024.0 / 1024.0
    }.getOrDefault(0.0)

    /** Начать загрузку модели. false — если места мало или загрузчик недоступен. */
    fun startDownload(model: Model = Model.GEMMA4): Boolean = runCatching {
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
    fun enoughSpace(model: Model = Model.GEMMA4): Boolean = (extDir?.usableSpace ?: 0L) > model.bytes + 200L * 1024 * 1024

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
                val part = partFile
                val lite = prefs.getString(KEY_PENDING_KIND, "") == KIND_GEMMA4
                val target = if (lite) downloadedLite else downloadedFile
                val ok = part != null && target != null && part.big() &&
                    run { target.delete(); part.renameTo(target) }
                prefs.edit().remove(KEY_ID).apply()
                if (ok) {
                    close()
                    importedFile.delete(); importedLite.delete()
                    (if (lite) downloadedFile else downloadedLite)?.delete()
                    kindFile.writeText(KIND_GEMMA4)
                    clearCrash()
                    state = State.UNKNOWN; Download.Done
                } else Download.Failed(-1)
            }
            DownloadManager.STATUS_FAILED -> {
                prefs.edit().remove(KEY_ID).apply(); partFile?.delete()
                Download.Failed(reason)
            }
            else -> {
                val t = if (total > 0) total else Model.entries.firstOrNull { it.kind == prefs.getString(KEY_PENDING_KIND, "") }?.bytes ?: Model.GEMMA4.bytes
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
            if (!tmp.big() || !name.endsWith(".litertlm", true)) { tmp.delete(); return@runCatching false }
            close()
            importedLite.delete(); downloadedLite?.delete()
            tmp.renameTo(importedLite).also { if (it) kindFile.writeText(KIND_GEMMA4); state = State.UNKNOWN }
        }.getOrDefault(false)
    }

    fun deleteModel() {
        close()
        importedFile.delete(); importedLite.delete(); downloadedFile?.delete(); downloadedLite?.delete(); kindFile.delete()
        clearCrash()
        state = State.NEEDS_MODEL
    }

    /** Загрузить модель (в отдельном процессе). Долгая операция — вызывать в фоне. */
    suspend fun prepare(): State = withContext(Dispatchers.IO) {
        val found = if (hasModel()) modelFile else null
        if (found == null) { state = State.NEEDS_MODEL; return@withContext state }
        // Gemma 4 скачана под старым именем «model.task» — переименовываем (мгновенно, в той же папке).
        val file: File = if (currentKind() == KIND_GEMMA4 && found.extension != "litertlm")
            File(found.parentFile, "model.litertlm").takeIf { found.renameTo(it) } ?: found else found
        if (isReady) { state = State.READY; return@withContext state }
        if (crashed) {
            detail = prefs.getString(KEY_CRASH_DETAIL, null) ?: t("Модель не запустилась на этом телефоне. Нажмите «Попробовать снова» в настройках ассистента.")
            state = State.UNAVAILABLE; return@withContext state
        }
        state = State.PREPARING
        val kind = currentKind()
        val gpu = false   // процессор: надёжно и достаточно быстро (видеокарту не используем)
        val started = System.currentTimeMillis()
        val reply = request(BrainService.MSG_PREPARE, 0, android.os.Bundle().apply {
            putString(BrainService.KEY_PATH, file.absolutePath); putInt(BrainService.KEY_MAX, 2048); putBoolean(BrainService.KEY_GPU, gpu)
            putBoolean(BrainService.KEY_LITERT, true)
        }, 240_000)
        // Процесс умер на видеокарте — запоминаем и сразу пробуем на процессоре.
        if (reply == null && gpu && lastDeath >= started) {
            prefs.edit().putBoolean(KEY_GPU_BAD + kind, true).apply()
            return@withContext prepare()
        }
        reply?.getString(BrainService.KEY_BACKEND)?.let { backend = it }
        when {
            reply == null && lastDeath >= started -> {
                // Процесс модели умер — узнаём у системы почему, и сами больше не пробуем (кнопка «Попробовать снова»).
                kotlinx.coroutines.delay(1500)
                detail = brainExitReason(started)
                    ?: t("Процесс модели закрылся при загрузке. Попробуйте снова.")
                prefs.edit().putString(KEY_CRASHED, currentKind()).putString(KEY_CRASH_DETAIL, detail).apply()
                state = State.UNAVAILABLE
            }
            reply == null -> {
                // Не успела загрузиться за отведённое время — это не поломка, в следующий раз попробуем снова.
                detail = t("Модель загружается слишком долго. Попробую ещё раз при следующем открытии ассистента.")
                close()
                state = State.UNAVAILABLE
            }
            reply.getBoolean(BrainService.KEY_OK) -> { prepared = true; state = State.READY }
            else -> { detail = reply.getString(BrainService.KEY_DETAIL).orEmpty(); state = State.UNAVAILABLE }
        }
        state
    }

    /** Спросить модель. Возвращает текст ответа или null при любой ошибке. */
    /** Почему последний запрос не дал ответа (для проверки ИИ и подсказок): «too_long:N», «no_model», текст ошибки движка. */
    @Volatile var lastError: String? = null
        private set
    /** Размер последнего запроса в токенах (−1 — неизвестно). */
    @Volatile var lastTokens: Int = -1
        private set

    /** Модель реально ответила хотя бы раз с момента загрузки (а не просто «загрузилась»). */
    @Volatile var verified = false
        private set

    /**
     * Спросить модель. Если процесс движка умер во время ответа (аварийное завершение — его в самой службе не поймать),
     * считаем видеокарту негодной, перезагружаем модель на процессоре и повторяем запрос один раз.
     */
    suspend fun ask(prompt: String): String? {
        askOnce(prompt)?.let { verified = true; return it }
        var attempts = 0
        while (lastError == "no_reply" && attempts < 2) {
            attempts++
            val kind = currentKind()
            when {
                // Сначала подозреваем «жадную» сессию, затем (только для своей модели Gemma) видеокарту.
                !prefs.getBoolean(KEY_NO_SESSION + kind, false) -> prefs.edit().putBoolean(KEY_NO_SESSION + kind, true).apply()
                else -> break
            }
            if (prepare() != State.READY) return null
            askOnce(prompt)?.let { verified = true; return it }
        }
        if (lastError != "no_reply") return null
        // Падает при любых настройках — сами больше не пробуем, объясняем и предлагаем быструю модель.
        val kind = currentKind()
        val why = com.kartoteka.app.i18n.t("Движок ИИ закрывается при ответе на этом телефоне. Перезапустите приложение и попробуйте снова.")
        prefs.edit().putString(KEY_CRASHED, kind).putString(KEY_CRASH_DETAIL, why).apply()
        detail = why; state = State.UNAVAILABLE
        lastError = "engine_crash"
        return null
    }

    /** Короткий настоящий ответ после загрузки: проверяем, что модель не только загружена, но и отвечает. */
    suspend fun selfTest(): Boolean {
        if (verified && isReady) return true
        val t0 = System.currentTimeMillis()
        val ok = ask("Ответь одним словом: ОК") != null
        selfTestMillis = System.currentTimeMillis() - t0
        return ok
    }
    @Volatile var selfTestMillis = 0L
        private set

    private suspend fun askOnce(prompt: String): String? = withContext(Dispatchers.IO) {
        lastError = null; lastTokens = -1
        if (!isReady) { lastError = "not_ready"; return@withContext null }
        val id = ids.incrementAndGet()
        val r = request(BrainService.MSG_ASK, id, android.os.Bundle().apply {
            putInt(BrainService.KEY_ID, id); putString(BrainService.KEY_PROMPT, wrap(prompt))
            putBoolean(BrainService.KEY_GREEDY, !prefs.getBoolean(KEY_NO_SESSION + currentKind(), false))
        }, 90_000)
        if (r == null) { lastError = "no_reply"; return@withContext null }
        lastError = r.getString(BrainService.KEY_ERR)
        lastTokens = r.getInt(BrainService.KEY_TOKENS, -1)
        // Служба перешла с видеокарты на процессор — запоминаем, чтобы не просить видеокарту снова.
        r.getString(BrainService.KEY_BACKEND)?.takeIf { it.isNotBlank() && it != backend }?.let {
            backend = it
            if (it == "cpu") prefs.edit().putBoolean(KEY_GPU_BAD + currentKind(), true).apply()
        }
        r.getString(BrainService.KEY_TEXT)
    }

    /** Gemma 4 сама применяет шаблон диалога (движок LiteRT-LM) — запрос передаём как есть. */
    private fun wrap(prompt: String): String = prompt

    fun close() {
        prepared = false; verified = false
        service?.let { to -> runCatching { to.send(android.os.Message.obtain(null, BrainService.MSG_CLOSE)) } }
    }

    companion object {
        private const val MB = 1024L * 1024
        private const val KEY_ID = "download_id"
        private const val KIND_GEMMA4 = "gemma4"
        private const val KEY_PENDING_KIND = "pending_kind"
        private const val KEY_CRASHED = "crashed_kind"
        private const val KEY_LOADING = "loading_kind"
        private const val KEY_CRASH_DETAIL = "crashed_detail"
        private const val KEY_GPU_BAD = "gpu_bad_"
        private const val KEY_NO_SESSION = "no_session_"
        private const val KEY_CRASH_RESET = "crash_reset_294"

        fun kindOf(fileName: String): String = KIND_GEMMA4
    }
}
