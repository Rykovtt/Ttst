package com.kartoteka.app.assistant

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession

/**
 * Языковая модель в ОТДЕЛЬНОМ процессе («:brain»). Крупной модели может не хватить памяти —
 * тогда система убивает только этот процесс, а приложение остаётся открытым и сообщает об ошибке.
 * Протокол — сообщения: PREPARE(path, maxTokens) → STATE(ok, detail); ASK(id, prompt) → ANSWER(id, text?).
 */
class BrainService : Service() {
    private var llm: LlmInference? = null
    private var loadedPath: String? = null
    private var backend = ""
    private var loadedMax = 1280
    private lateinit var worker: Handler

    override fun onCreate() {
        super.onCreate()
        val thread = HandlerThread("brain").apply { start() }
        worker = Handler(thread.looper) { msg -> handle(msg); true }
    }

    private val messenger by lazy { Messenger(Handler(mainLooper) { msg -> worker.sendMessage(Message.obtain(msg)); true }) }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    private fun handle(msg: Message) {
        val reply = msg.replyTo ?: return
        when (msg.what) {
            MSG_PREPARE -> {
                val path = msg.data.getString(KEY_PATH).orEmpty()
                val max = msg.data.getInt(KEY_MAX, 1280)
                loadedMax = max
                val gpu = msg.data.getBoolean(KEY_GPU, false)
                val result = runCatching {
                    if (llm == null || loadedPath != path || (gpu && backend != "gpu")) {
                        runCatching { llm?.close() }
                        llm = null
                        fun create(b: LlmInference.Backend) = LlmInference.createFromOptions(applicationContext,
                            LlmInference.LlmInferenceOptions.builder()
                                .setModelPath(path).setMaxTokens(max).setMaxTopK(40).setPreferredBackend(b).build())
                        // Видеокарта в разы быстрее; не поддерживает модель/телефон — процессор.
                        llm = if (gpu) runCatching { create(LlmInference.Backend.GPU).also { backend = "gpu" } }.getOrNull() else null
                        if (llm == null) { llm = create(LlmInference.Backend.CPU); backend = "cpu" }
                        loadedPath = path
                    }
                }
                send(reply, MSG_STATE, Bundle().apply {
                    putString(KEY_BACKEND, backend)
                    putBoolean(KEY_OK, result.isSuccess)
                    putString(KEY_DETAIL, result.exceptionOrNull()?.let { (it.message ?: it::class.java.simpleName).take(160) })
                })
            }
            MSG_ASK -> {
                val id = msg.data.getInt(KEY_ID)
                val prompt = msg.data.getString(KEY_PROMPT).orEmpty()
                val greedy = msg.data.getBoolean(KEY_GREEDY, true)
                var text: String? = null
                var err: String? = null
                var tokens = -1
                val model = llm
                if (model == null) err = "no_model"
                else {
                    tokens = runCatching { model.sizeInTokens(prompt) }.getOrDefault(-1)
                    // Запрос не влезает в окно модели (оно общее для запроса и ответа) — не гоним впустую, пусть сократят.
                    if (tokens > 0 && tokens > loadedMax - OUT_RESERVE) err = "too_long:$tokens"
                    else {
                        try { text = generate(model, prompt, greedy) }
                        catch (t: Throwable) {
                            err = (t.message ?: t::class.java.simpleName).take(160)
                            // Видеокарта не потянула этот запрос — переходим на процессор и пробуем ещё раз.
                            if (backend == "gpu" && reloadOnCpu()) {
                                try { text = llm?.let { generate(it, prompt, greedy) }; err = null } catch (t2: Throwable) { err = (t2.message ?: t2::class.java.simpleName).take(160) }
                            }
                        }
                    }
                }
                send(reply, MSG_ANSWER, Bundle().apply {
                    putInt(KEY_ID, id); putString(KEY_TEXT, text); putString(KEY_ERR, err); putInt(KEY_TOKENS, tokens); putString(KEY_BACKEND, backend)
                })
            }
            MSG_CLOSE -> { runCatching { llm?.close() }; llm = null; loadedPath = null }
        }
    }

    /**
     * Ответ модели: «жадная» генерация (без случайности — для разбора команд нужен один и тот же ответ на одну фразу,
     * а не шум вроде «вамneephone»). Генерацию не обрываем: отмена на лету с немедленным закрытием сессии аварийно
     * закрывала движок (проверено на телефоне). Модель сама заканчивает ответ, когда JSON готов.
     */
    private fun generate(model: LlmInference, prompt: String, greedy: Boolean = true): String? {
        // Запасной путь: если сессия валит движок на этом телефоне, приложение просит обычную генерацию.
        if (!greedy) return model.generateResponse(prompt)
        val options = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTopK(1).setTopP(1f).setTemperature(0.1f).setRandomSeed(1).build()
        val session = LlmInferenceSession.createFromOptions(model, options)
        try {
            session.addQueryChunk(prompt)
            return session.generateResponse()
        } finally {
            runCatching { session.close() }
        }
    }

    /** Пересоздать модель на процессоре (после сбоя видеокарты). */
    private fun reloadOnCpu(): Boolean {
        val path = loadedPath ?: return false
        return runCatching {
            runCatching { llm?.close() }
            llm = LlmInference.createFromOptions(applicationContext, LlmInference.LlmInferenceOptions.builder()
                .setModelPath(path).setMaxTokens(loadedMax).setMaxTopK(40).setPreferredBackend(LlmInference.Backend.CPU).build())
            backend = "cpu"
        }.isSuccess
    }

    private fun send(to: Messenger, what: Int, data: Bundle) {
        runCatching { to.send(Message.obtain(null, what).apply { this.data = data }) }
    }

    override fun onDestroy() {
        runCatching { llm?.close() }
        super.onDestroy()
    }

    companion object {
        const val MSG_PREPARE = 1
        const val MSG_STATE = 2
        const val MSG_ASK = 3
        const val MSG_ANSWER = 4
        const val MSG_CLOSE = 5
        const val KEY_PATH = "path"
        const val KEY_MAX = "max"
        const val KEY_OK = "ok"
        const val KEY_DETAIL = "detail"
        const val KEY_ID = "id"
        const val KEY_PROMPT = "prompt"
        const val KEY_TEXT = "text"
        const val KEY_ERR = "err"
        const val KEY_GREEDY = "greedy"
        const val KEY_TOKENS = "tokens"
        /** Сколько токенов окна оставляем под ответ модели. */
        const val OUT_RESERVE = 220
        const val KEY_GPU = "gpu"
        const val KEY_BACKEND = "backend"
    }
}
