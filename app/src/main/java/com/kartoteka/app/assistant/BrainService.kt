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

/**
 * Языковая модель в ОТДЕЛЬНОМ процессе («:brain»). Крупной модели может не хватить памяти —
 * тогда система убивает только этот процесс, а приложение остаётся открытым и сообщает об ошибке.
 * Протокол — сообщения: PREPARE(path, maxTokens) → STATE(ok, detail); ASK(id, prompt) → ANSWER(id, text?).
 */
class BrainService : Service() {
    private var llm: LlmInference? = null
    private var loadedPath: String? = null
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
                val result = runCatching {
                    if (llm == null || loadedPath != path) {
                        runCatching { llm?.close() }
                        llm = null
                        val options = LlmInference.LlmInferenceOptions.builder()
                            .setModelPath(path).setMaxTokens(max).setMaxTopK(40).build()
                        llm = LlmInference.createFromOptions(applicationContext, options)
                        loadedPath = path
                    }
                }
                send(reply, MSG_STATE, Bundle().apply {
                    putBoolean(KEY_OK, result.isSuccess)
                    putString(KEY_DETAIL, result.exceptionOrNull()?.let { (it.message ?: it::class.java.simpleName).take(160) })
                })
            }
            MSG_ASK -> {
                val id = msg.data.getInt(KEY_ID)
                val text = runCatching { llm?.generateResponse(msg.data.getString(KEY_PROMPT).orEmpty()) }.getOrNull()
                send(reply, MSG_ANSWER, Bundle().apply { putInt(KEY_ID, id); putString(KEY_TEXT, text) })
            }
            MSG_CLOSE -> { runCatching { llm?.close() }; llm = null; loadedPath = null }
        }
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
    }
}
