package com.rykov.autosend.core

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
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
    val session = SendSession<PendingIntent>(clock = SystemClock::uptimeMillis)

    private var service: AutoSendAccessibilityService? = null

    fun attach(service: AutoSendAccessibilityService) {
        this.service = service
    }

    fun detach(context: Context, service: AutoSendAccessibilityService) {
        if (this.service !== service) return
        this.service = null
        session.cancel()?.let { reply(context, it, null, AutoSendContract.STATUS_SERVICE_DISABLED) }
    }

    fun arm(context: Context, intent: Intent) {
        val callback = callbackOf(intent)
        val target = intent.getStringExtra(AutoSendContract.EXTRA_TARGET_PACKAGE)?.takeIf { it.isNotBlank() }
        val requestId = intent.getStringExtra(AutoSendContract.EXTRA_REQUEST_ID)
        val openUri = intent.getStringExtra(AutoSendContract.EXTRA_OPEN_URI)?.takeIf { it.isNotBlank() }
        val text = intent.getStringExtra(AutoSendContract.EXTRA_TEXT)
        val ttl = intent.getLongExtra(AutoSendContract.EXTRA_TIMEOUT_MS, SendSession.DEFAULT_TTL_MS)

        if (!CallerVerifier.isTrusted(context, callback)) {
            send(context, callback, requestId, target, AutoSendContract.STATUS_UNTRUSTED_CALLER)
            return
        }
        // Открыть чат можно только в конкретном поддерживаемом мессенджере.
        if (target != null && target !in TargetApps.packageNames || openUri != null && target == null) {
            send(context, callback, requestId, target, AutoSendContract.STATUS_UNSUPPORTED_PACKAGE)
            return
        }
        val running = service
        if (running == null) {
            send(context, callback, requestId, target, AutoSendContract.STATUS_SERVICE_DISABLED)
            return
        }
        // Новая команда вытесняет старую: та получает ответ «отменено».
        session.cancel()?.let { reply(context, it, null, AutoSendContract.STATUS_CANCELLED) }
        val request = session.arm(target, requestId, callback, ttl, text)
        Log.i(TAG, "armed for ${target ?: "any target"}")
        if (openUri != null && target != null && !running.openChat(target, Uri.parse(openUri))) {
            session.cancel()
            reply(context, request, target, AutoSendContract.STATUS_OPEN_FAILED)
            return
        }
        running.onArmed(request)
    }

    fun cancel(context: Context, intent: Intent) {
        if (!CallerVerifier.isTrusted(context, callbackOf(intent))) return
        session.cancel()?.let { reply(context, it, null, AutoSendContract.STATUS_CANCELLED) }
        service?.onCancelled()
    }

    fun reply(context: Context, request: SendSession.Request<PendingIntent>, clickedPackage: String?, status: String) {
        send(context, request.replyTo, request.requestId, clickedPackage ?: request.packageName, status)
    }

    /** Ответ уходит только в PendingIntent вызывающего. В нём нет ни текста, ни данных контакта. */
    private fun send(context: Context, callback: PendingIntent?, requestId: String?, target: String?, status: String) {
        Log.i(TAG, "result: $status")
        if (callback == null) return
        val fillIn = Intent()
            .putExtra(AutoSendContract.EXTRA_STATUS, status)
            .putExtra(AutoSendContract.EXTRA_REQUEST_ID, requestId)
            .putExtra(AutoSendContract.EXTRA_TARGET_PACKAGE, target)
        try {
            callback.send(context, 0, fillIn)
        } catch (e: PendingIntent.CanceledException) {
            Log.w(TAG, "reply target is gone")
        }
    }

    @Suppress("DEPRECATION")
    private fun callbackOf(intent: Intent): PendingIntent? =
        try {
            intent.getParcelableExtra(AutoSendContract.EXTRA_CALLBACK) as? PendingIntent
        } catch (e: RuntimeException) {
            null
        }
}
