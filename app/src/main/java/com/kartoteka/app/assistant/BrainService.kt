package com.kartoteka.app.assistant

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig

/**
 * Языковая модель в ОТДЕЛЬНОМ процессе («:brain»). Крупной модели может не хватить памяти —
 * тогда система убивает только этот процесс, а приложение остаётся открытым и сообщает об ошибке.
 * Протокол — сообщения: PREPARE(path, maxTokens) → STATE(ok, detail); ASK(id, prompt) → ANSWER(id, text?).
 */
class BrainService : Service() {
    /** Модели .litertlm (Gemma 4) идут через новый движок LiteRT-LM; обычные .task — через MediaPipe. */
    private var lite: Engine? = null
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
                val result = runCatching {
                    if (lite == null || loadedPath != path) {
                        runCatching { lite?.close() }; lite = null
                        val e = Engine(EngineConfig(modelPath = path, backend = Backend.CPU(), maxNumTokens = max, cacheDir = cacheDir.absolutePath))
                        e.initialize()
                        lite = e; backend = "cpu"; loadedPath = path
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
                val engine = lite
                if (engine == null) err = "no_model"
                else try { text = generateLite(engine, prompt) } catch (t: Throwable) { err = (t.message ?: t::class.java.simpleName).take(160) }
                send(reply, MSG_ANSWER, Bundle().apply {
                    putInt(KEY_ID, id); putString(KEY_TEXT, text); putString(KEY_ERR, err); putInt(KEY_TOKENS, -1); putString(KEY_BACKEND, backend)
                })
            }
            MSG_CLOSE -> { runCatching { lite?.close() }; lite = null; loadedPath = null }
        }
    }

    private fun generateLite(engine: Engine, prompt: String): String? {
        val cfg = ConversationConfig(samplerConfig = SamplerConfig(1, 1.0, 0.1, 1))
        engine.createConversation(cfg).use { c ->
            val msg = c.sendMessage(prompt)
            return msg.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }.trim().ifBlank { null }
        }
    }

    private fun send(to: Messenger, what: Int, data: Bundle) {
        runCatching { to.send(Message.obtain(null, what).apply { this.data = data }) }
    }

    override fun onDestroy() {
        runCatching { lite?.close() }
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
        const val KEY_LITERT = "litert"
        const val KEY_BACKEND = "backend"
    }
}
