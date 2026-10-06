package com.kartoteka.app.messaging

import com.kartoteka.app.i18n.t

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.kartoteka.app.MainActivity
import com.kartoteka.app.R
import com.kartoteka.app.data.ArchiveLogic
import com.kartoteka.app.data.NotifyChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

/** Одно сообщение для авто-отправки. */
data class SendJob(
    val personId: Long,
    val name: String,
    val channel: NotifyChannel,
    val target: String,
    val text: String,
)

enum class JobState { PENDING, SENDING, SENT, FAILED }

data class AutoSendProgress(
    val jobs: List<SendJob>,
    val states: List<JobState>,
    val running: Boolean,
) {
    val sent get() = states.count { it == JobState.SENT }
    val failed get() = states.count { it == JobState.FAILED }
    val done get() = states.count { it == JobState.SENT || it == JobState.FAILED }
}

/**
 * Очередь авто-отправки в WhatsApp/Telegram/Viber. Каждое сообщение передаётся
 * приложению «Автоотправка CRM» ([AutoSendLink]): его служба открывает чат с текстом
 * и сама нажимает «Отправить», а итог возвращает в [onResult].
 * После отправки — пауза и следующий человек.
 */
object AutoSend {
    /** Сколько служба ждёт кнопку «Отправить». */
    private const val TIMEOUT_MS = 25_000L
    /** Запас на случай, если ответ службы не пришёл (служба выключена посреди рассылки и т.п.). */
    private const val FALLBACK_MS = TIMEOUT_MS + 5_000L
    private const val NOTIFICATION_ID = 7001
    private const val CHANNEL = "autosend"

    private val handler = Handler(Looper.getMainLooper())
    private val _progress = MutableStateFlow<AutoSendProgress?>(null)
    val progress: StateFlow<AutoSendProgress?> = _progress.asStateFlow()

    private var jobs: List<SendJob> = emptyList()
    private var states: MutableList<JobState> = mutableListOf()
    private var index = -1
    private var run = 0L
    private var delayMs = 6_000L
    private var onResult: ((SendJob, Boolean) -> Unit)? = null
    private lateinit var appContext: Context

    /** Сообщение, которое сейчас ждёт нажатия «Отправить». */
    private val armedJob: SendJob?
        get() = jobs.getOrNull(index)?.takeIf { states.getOrNull(index) == JobState.SENDING }

    /** Идентификатор команды для текущего сообщения: ответы на старые команды отбрасываются. */
    private val currentRequestId: String get() = "rvault-$run-$index"

    /** «Автоотправка CRM» установлена и её служба включена. */
    fun isServiceEnabled(context: Context): Boolean = AutoSendLink.isServiceEnabled(context)

    fun openServiceSettings(context: Context) = AutoSendLink.openSetup(context)

    fun start(context: Context, list: List<SendJob>, delaySec: Int, onResult: (SendJob, Boolean) -> Unit) {
        if (list.isEmpty()) return
        stop(silent = true)
        appContext = context.applicationContext
        run++
        jobs = list
        states = MutableList(list.size) { JobState.PENDING }
        index = -1
        delayMs = delaySec.coerceAtLeast(2) * 1000L
        this.onResult = onResult
        publish(true)
        next(context)
    }

    fun stop(silent: Boolean = false) {
        handler.removeCallbacksAndMessages(null)
        if (index in states.indices && states[index] == JobState.SENDING) {
            states[index] = JobState.FAILED
            runCatching { AutoSendLink.cancel(appContext) }
        }
        for (i in states.indices) if (states[i] == JobState.PENDING) states[i] = JobState.FAILED
        if (!silent && jobs.isNotEmpty()) publish(false)
        if (::appContext.isInitialized) cancelNotification()
    }

    fun clear() {
        stop(silent = true)
        jobs = emptyList(); states = mutableListOf(); index = -1
        _progress.value = null
    }

    /** Ответ «Автоотправки CRM» на команду [requestId]. */
    internal fun onResult(requestId: String?, status: String?) {
        if (armedJob == null || requestId != currentRequestId) return
        if (status == AutoSendLink.STATUS_SENT) onSent() else fail()
    }

    private fun onSent() {
        val job = armedJob ?: return
        states[index] = JobState.SENT
        onResult?.invoke(job, true)
        publish(true)
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ next(null) }, delayMs + Random.nextLong(0, 2500))
    }

    private fun next(launcher: Context?) {
        index++
        if (index >= jobs.size) {
            finish()
            return
        }
        states[index] = JobState.SENDING
        publish(true)
        val job = jobs[index]
        // Чат открывает служба «Автоотправки CRM»: RVault в фоне окна открывать не может.
        val ok = runCatching {
            val (pkg, uri) = chatFor(launcher ?: appContext, job) ?: error("no messenger")
            AutoSendLink.send(appContext, currentRequestId, pkg, uri, job.text, TIMEOUT_MS)
        }.isSuccess
        if (!ok) {
            fail()
            return
        }
        val requestId = currentRequestId
        handler.postDelayed({ if (requestId == currentRequestId && armedJob != null) fail() }, FALLBACK_MS)
    }

    private fun fail() {
        val job = jobs.getOrNull(index) ?: return
        states[index] = JobState.FAILED
        onResult?.invoke(job, false)
        publish(true)
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ next(null) }, 1500)
    }

    private fun finish() {
        publish(false)
        cancelNotification()
        // Возвращаемся в картотеку показать итог (из фона Android 10+ может не пустить — тогда итог в приложении).
        runCatching {
            appContext.startActivity(
                Intent(appContext, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            )
        }
    }

    /** Пакет мессенджера и ссылка на чат с текстом; null — нужного мессенджера нет. */
    private fun chatFor(context: Context, job: SendJob): Pair<String, String>? = when (job.channel) {
        NotifyChannel.WHATSAPP -> {
            val digits = ArchiveLogic.normalizePhone(job.target).removePrefix("+")
            val pkg = listOf(Messaging.WHATSAPP, Messaging.WHATSAPP_BUSINESS).firstOrNull { Messaging.isInstalled(context, it) }
            pkg?.let { it to "https://api.whatsapp.com/send?phone=${digits}&text=" + Uri.encode(job.text) }
        }
        NotifyChannel.TELEGRAM -> {
            val pkg = Messaging.TELEGRAM_APPS.firstOrNull { Messaging.isInstalled(context, it) }
            pkg?.let { it to Messaging.telegramUrl(job.target) + "?text=" + Uri.encode(job.text) }
        }
        NotifyChannel.VIBER -> {
            if (Messaging.isInstalled(context, Messaging.VIBER)) Messaging.VIBER to Messaging.viberUrl(job.target, job.text) else null
        }
        else -> null
    }

    private fun publish(running: Boolean) {
        val p = AutoSendProgress(jobs.toList(), states.toList(), running)
        _progress.value = p
        if (running && ::appContext.isInitialized) notify(p)
    }

    private fun notify(p: AutoSendProgress) {
        val nm = appContext.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, t("Авто-рассылка"), NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getActivity(
            appContext, 1,
            Intent(appContext, MainActivity::class.java).putExtra(MainActivity.EXTRA_STOP_AUTOSEND, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val current = p.jobs.getOrNull(index)?.name.orEmpty()
        val n = NotificationCompat.Builder(appContext, CHANNEL)
            .setSmallIcon(com.kartoteka.app.AppIcons.notificationIcon(appContext))
            .setContentTitle(t("Рассылка: %1\$s из %2\$s", p.done, p.jobs.size))
            .setContentText(if (current.isNotBlank()) t("Сейчас: %1\$s", current) else "")
            .setProgress(p.jobs.size, p.done, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, t("Остановить"), stop)
            .build()
        runCatching { nm.notify(NOTIFICATION_ID, n) }
    }

    private fun cancelNotification() {
        appContext.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }
}
