package com.rykov.autosend.core

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.rykov.autosend.services.AutoSendAccessibilityService

/**
 * Связывает команды CRM (CommandReceiver) с работающей службой.
 * Приёмник и служба живут в одном процессе и вызываются на главном потоке.
 */
object AutoSendController {
    private const val TAG = "AutoSend"

    /** uptimeMillis — тот же отсчёт, что у Handler.postAtTime. */
    val session = SendSession(clock = SystemClock::uptimeMillis)

    private var service: AutoSendAccessibilityService? = null

    fun attach(service: AutoSendAccessibilityService) {
        this.service = service
    }

    fun detach(service: AutoSendAccessibilityService) {
        if (this.service !== service) return
        this.service = null
        session.cancel()
    }

    fun arm(context: Context, intent: Intent) {
        val target = intent.getStringExtra(AutoSendContract.EXTRA_TARGET_PACKAGE)?.takeIf { it.isNotBlank() }
        val requestId = intent.getStringExtra(AutoSendContract.EXTRA_REQUEST_ID)
        val replyPackage = intent.getStringExtra(AutoSendContract.EXTRA_REPLY_PACKAGE)
        val ttl = intent.getLongExtra(AutoSendContract.EXTRA_TIMEOUT_MS, SendSession.DEFAULT_TTL_MS)

        if (target != null && target !in TargetApps.packageNames) {
            reply(context, requestId, replyPackage, target, AutoSendContract.STATUS_UNSUPPORTED_PACKAGE)
            return
        }
        val running = service
        if (running == null) {
            reply(context, requestId, replyPackage, target, AutoSendContract.STATUS_SERVICE_DISABLED)
            return
        }
        // Новая команда вытесняет старую: та получает ответ «отменено».
        session.cancel()?.let { reply(context, it, null, AutoSendContract.STATUS_CANCELLED) }
        val request = session.arm(target, requestId, replyPackage, ttl)
        Log.i(TAG, "armed for ${target ?: "any target"}")
        running.onArmed(request)
    }

    fun cancel(context: Context) {
        session.cancel()?.let { reply(context, it, null, AutoSendContract.STATUS_CANCELLED) }
        service?.onCancelled()
    }

    fun reply(context: Context, request: SendSession.Request, clickedPackage: String?, status: String) {
        reply(context, request.requestId, request.replyPackage, clickedPackage ?: request.packageName, status)
    }

    /**
     * Ответ адресуется только явно указанному пакету CRM и только при наличии у него
     * разрешения CONTROL. В ответе нет ни текста сообщения, ни данных контакта.
     */
    private fun reply(context: Context, requestId: String?, replyPackage: String?, target: String?, status: String) {
        Log.i(TAG, "result: $status")
        if (replyPackage.isNullOrBlank()) return
        val intent = Intent(AutoSendContract.ACTION_SEND_RESULT)
            .setPackage(replyPackage)
            .putExtra(AutoSendContract.EXTRA_STATUS, status)
            .putExtra(AutoSendContract.EXTRA_REQUEST_ID, requestId)
            .putExtra(AutoSendContract.EXTRA_TARGET_PACKAGE, target)
        try {
            context.sendBroadcast(intent, AutoSendContract.PERMISSION_CONTROL)
        } catch (e: RuntimeException) {
            Log.w(TAG, "reply failed", e)
        }
    }
}
