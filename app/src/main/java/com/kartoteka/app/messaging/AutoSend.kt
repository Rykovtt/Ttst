package com.kartoteka.app.messaging

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
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
 * Очередь авто-отправки в WhatsApp/Telegram. Открывает чат с готовым текстом,
 * а [AutoSendService] (служба специальных возможностей) сам нажимает «Отправить».
 * После отправки — пауза и следующий человек.
 */
object AutoSend {
    private const val TIMEOUT_MS = 25_000L
    private const val TICK_MS = 700L
    private const val NOTIFICATION_ID = 7001
    private const val CHANNEL = "autosend"

    private val handler = Handler(Looper.getMainLooper())
    private val _progress = MutableStateFlow<AutoSendProgress?>(null)
    val progress: StateFlow<AutoSendProgress?> = _progress.asStateFlow()

    private var jobs: List<SendJob> = emptyList()
    private var states: MutableList<JobState> = mutableListOf()
    private var index = -1
    private var startedAt = 0L
    private var delayMs = 6_000L
    private var onResult: ((SendJob, Boolean) -> Unit)? = null
    private lateinit var appContext: Context

    /** Сообщение, которое сейчас ждёт нажатия «Отправить». */
    val armedJob: SendJob?
        get() = jobs.getOrNull(index)?.takeIf { states.getOrNull(index) == JobState.SENDING }

    fun isServiceEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(context, AutoSendService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    fun openServiceSettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun start(context: Context, list: List<SendJob>, delaySec: Int, onResult: (SendJob, Boolean) -> Unit) {
        if (list.isEmpty()) return
        stop(silent = true)
        appContext = context.applicationContext
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
        if (index in states.indices && states[index] == JobState.SENDING) states[index] = JobState.FAILED
        for (i in states.indices) if (states[i] == JobState.PENDING) states[i] = JobState.FAILED
        if (!silent && jobs.isNotEmpty()) publish(false)
        if (::appContext.isInitialized) cancelNotification()
    }

    fun clear() {
        stop(silent = true)
        jobs = emptyList(); states = mutableListOf(); index = -1
        _progress.value = null
    }

    /** Вызывается службой после нажатия «Отправить». */
    internal fun onSent() {
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
        startedAt = System.currentTimeMillis()
        publish(true)
        val job = jobs[index]
        val ok = runCatching { launch(launcher ?: AutoSendService.instance ?: appContext, job) }.isSuccess
        if (!ok) {
            fail()
            return
        }
        handler.postDelayed(::tick, 1500)
    }

    private fun tick() {
        if (armedJob == null) return
        if (System.currentTimeMillis() - startedAt > TIMEOUT_MS) {
            fail()
            return
        }
        AutoSendService.instance?.trySend()
        handler.postDelayed(::tick, TICK_MS)
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
        // Возвращаемся в картотеку показать итог.
        runCatching {
            (AutoSendService.instance ?: appContext).startActivity(
                Intent(appContext, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            )
        }
    }

    private fun launch(context: Context, job: SendJob) {
        val intent = when (job.channel) {
            NotifyChannel.WHATSAPP -> {
                val digits = ArchiveLogic.normalizePhone(job.target).removePrefix("+")
                val pkg = if (Messaging.isInstalled(context, Messaging.WHATSAPP)) Messaging.WHATSAPP else "com.whatsapp.w4b"
                Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$digits&text=" + Uri.encode(job.text)))
                    .setPackage(pkg)
            }
            NotifyChannel.TELEGRAM -> {
                Intent(Intent.ACTION_VIEW, Uri.parse(Messaging.telegramUrl(job.target) + "?text=" + Uri.encode(job.text)))
                    .apply { if (Messaging.isInstalled(context, Messaging.TELEGRAM)) setPackage(Messaging.TELEGRAM) }
            }
            else -> error("unsupported")
        }
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun publish(running: Boolean) {
        val p = AutoSendProgress(jobs.toList(), states.toList(), running)
        _progress.value = p
        if (running && ::appContext.isInitialized) notify(p)
    }

    private fun notify(p: AutoSendProgress) {
        val nm = appContext.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Авто-рассылка", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getActivity(
            appContext, 1,
            Intent(appContext, MainActivity::class.java).putExtra(MainActivity.EXTRA_STOP_AUTOSEND, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val current = p.jobs.getOrNull(index)?.name.orEmpty()
        val n = NotificationCompat.Builder(appContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Рассылка: ${p.done} из ${p.jobs.size}")
            .setContentText(if (current.isNotBlank()) "Сейчас: $current" else "")
            .setProgress(p.jobs.size, p.done, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Остановить", stop)
            .build()
        runCatching { nm.notify(NOTIFICATION_ID, n) }
    }

    private fun cancelNotification() {
        appContext.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }
}
