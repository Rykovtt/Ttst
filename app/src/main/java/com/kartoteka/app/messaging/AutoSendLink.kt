package com.kartoteka.app.messaging

import com.kartoteka.app.i18n.t

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast

/**
 * Связь с отдельным приложением «RVServices» (модуль :autosend, пакет com.rykov.autosend):
 * его служба специальных возможностей открывает чат, при необходимости вписывает текст
 * и один раз нажимает «Отправить». Протокол — com.rykov.autosend.core.AutoSendContract.
 */
object AutoSendLink {
    const val PACKAGE = "com.rykov.autosend"
    private const val SERVICE = "com.rykov.autosend.services.AutoSendAccessibilityService"

    private const val ACTION_ARM_SEND = "com.rykov.autosend.action.ARM_SEND"
    private const val ACTION_CANCEL = "com.rykov.autosend.action.CANCEL"
    const val ACTION_SEND_RESULT = "com.rykov.autosend.action.SEND_RESULT"

    private const val EXTRA_CALLBACK = "callback"
    private const val EXTRA_TARGET_PACKAGE = "target_package"
    private const val EXTRA_OPEN_URI = "open_uri"
    private const val EXTRA_TEXT = "text"
    const val EXTRA_REQUEST_ID = "request_id"
    private const val EXTRA_TIMEOUT_MS = "timeout_ms"
    const val EXTRA_STATUS = "status"

    const val STATUS_SENT = "sent"

    fun isInstalled(context: Context): Boolean = Messaging.isInstalled(context, PACKAGE)

    /** Приложение установлено и его служба включена в «Спец. возможностях». */
    fun isServiceEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.split(':').any { entry ->
            val parts = entry.trim().split('/')
            parts.size == 2 && parts[0] == PACKAGE &&
                (parts[1] == SERVICE || PACKAGE + parts[1] == SERVICE)
        }
    }

    /** Открывает «RVServices» (там инструкция и кнопка в настройки); нет её — подсказка. */
    fun openSetup(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(PACKAGE)
        if (launch == null) {
            Toast.makeText(context, t("Установите приложение «RVServices»"), Toast.LENGTH_LONG).show()
            return
        }
        runCatching { context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /**
     * Просит службу открыть [chatUri] в [messengerPackage] и нажать «Отправить» один раз.
     * [text] служба впишет сама, если мессенджер не подставил его по ссылке.
     * Ответ придёт в [AutoSendResultReceiver].
     */
    fun send(context: Context, requestId: String, messengerPackage: String, chatUri: String, text: String, timeoutMs: Long) {
        context.sendBroadcast(
            Intent(ACTION_ARM_SEND)
                .setPackage(PACKAGE)
                .putExtra(EXTRA_CALLBACK, callback(context))
                .putExtra(EXTRA_TARGET_PACKAGE, messengerPackage)
                .putExtra(EXTRA_OPEN_URI, chatUri)
                .putExtra(EXTRA_TEXT, text)
                .putExtra(EXTRA_REQUEST_ID, requestId)
                .putExtra(EXTRA_TIMEOUT_MS, timeoutMs),
        )
    }

    fun cancel(context: Context) {
        context.sendBroadcast(Intent(ACTION_CANCEL).setPackage(PACKAGE).putExtra(EXTRA_CALLBACK, callback(context)))
    }

    /**
     * По этому PendingIntent служба узнаёт RVault (создатель + сертификат подписи)
     * и возвращает итог. MUTABLE — служба дописывает статус в extras.
     */
    private fun callback(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, AutoSendResultReceiver::class.java).setAction(ACTION_SEND_RESULT),
        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/** Итог команды от «RVServices». Приходит только через наш PendingIntent. */
class AutoSendResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AutoSendLink.ACTION_SEND_RESULT) return
        AutoSend.onResult(
            intent.getStringExtra(AutoSendLink.EXTRA_REQUEST_ID),
            intent.getStringExtra(AutoSendLink.EXTRA_STATUS),
        )
    }
}
