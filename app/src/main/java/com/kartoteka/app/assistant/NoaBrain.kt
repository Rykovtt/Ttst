package com.kartoteka.app.assistant

import android.content.Context
import android.os.Build
import com.google.ai.edge.aicore.DownloadCallback
import com.google.ai.edge.aicore.DownloadConfig
import com.google.ai.edge.aicore.GenerativeModel
import com.google.ai.edge.aicore.generationConfig
import kotlinx.coroutines.withTimeoutOrNull

/**
 * «Мозг» Ноа — языковая модель Gemini Nano на самом устройстве (офлайн, через Google AICore).
 * Доступна только на Android 12+ и телефонах с AICore (флагманы Samsung/Pixel).
 * Любая ошибка не роняет приложение — Ноа переключается на быстрые команды.
 */
class GeminiNanoBrain(context: Context) {
    enum class State { UNKNOWN, PREPARING, DOWNLOADING, READY, UNAVAILABLE }

    @Volatile var state: State = State.UNKNOWN
        private set
    @Volatile var detail: String = ""
        private set
    @Volatile var downloadPercent: Int = -1
        private set

    private val app = context.applicationContext
    private var model: GenerativeModel? = null

    val supported: Boolean get() = Build.VERSION.SDK_INT >= 31

    private fun build(): GenerativeModel? {
        if (!supported) return null
        model?.let { return it }
        return runCatching {
            val cfg = generationConfig {
                this.context = app
                temperature = 0.2f
                topK = 16
                maxOutputTokens = 256
            }
            val download = DownloadConfig(object : DownloadCallback {
                override fun onDownloadStarted(bytesToDownload: Long) { state = State.DOWNLOADING; downloadPercent = 0 }
                override fun onDownloadProgress(totalBytesDownloaded: Long) {}
                override fun onDownloadCompleted() { downloadPercent = 100 }
                override fun onDownloadFailed(failureStatus: String, e: com.google.ai.edge.aicore.GenerativeAIException) {
                    state = State.UNAVAILABLE; detail = failureStatus
                }
            })
            GenerativeModel(cfg, download).also { model = it }
        }.getOrNull()
    }

    /** Подготовить движок (скачает модель при первом запуске). Возвращает готовность. */
    suspend fun prepare(): State {
        if (!supported) { state = State.UNAVAILABLE; detail = "Android 12+"; return state }
        val m = build() ?: run { state = State.UNAVAILABLE; return state }
        if (state != State.DOWNLOADING) state = State.PREPARING
        val ok = withTimeoutOrNull(120_000) {
            runCatching { m.prepareInferenceEngine() }
                .onFailure { detail = (it::class.java.simpleName + ": " + (it.message ?: "")).take(140) }
                .isSuccess
        } ?: run { detail = "timeout"; false }
        state = if (ok) State.READY else if (state == State.DOWNLOADING) State.DOWNLOADING else State.UNAVAILABLE
        return state
    }

    /** Спросить модель. Возвращает текст ответа или null при любой ошибке. */
    suspend fun ask(prompt: String): String? {
        val m = model ?: build() ?: return null
        return withTimeoutOrNull(30_000) {
            runCatching { m.generateContent(prompt).text }.getOrNull()
        }
    }

    fun close() { runCatching { model?.close() }; model = null }
}
