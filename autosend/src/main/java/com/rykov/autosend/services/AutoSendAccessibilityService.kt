package com.rykov.autosend.services

import android.accessibilityservice.AccessibilityService
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.rykov.autosend.core.AutoSendContract
import com.rykov.autosend.core.AutoSendController
import com.rykov.autosend.core.SendSession
import com.rykov.autosend.core.TargetApps

/**
 * Нажимает «Отправить» в мессенджере — только по команде CRM (ACTION_ARM_SEND)
 * и не более одного раза на команду.
 *
 * Поток: CRM подставляет текст и открывает чат → взводит службу → служба ждёт событий
 * окна целевого пакета (чат может открыть сама служба по ссылке CRM) → через [CLICK_DELAY_MS] (анимация открытия чата) ищет кнопку →
 * одна попытка клика → ответ CRM. Если кнопка не нашлась, служба продолжает ждать
 * изменений окна до таймаута команды.
 */
class AutoSendAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var pendingPackage: String? = null
    private val clickRunnable = Runnable { performPendingClick() }
    private val expiryRunnable = Runnable { expireIfDue() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        AutoSendController.attach(this)
        Log.i(TAG, "connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName !in TargetApps.packageNames) return
        if (!AutoSendController.session.isArmedFor(packageName)) return
        scheduleClick(packageName)
    }

    /**
     * Открывает чат по ссылке CRM. Служба специальных возможностей может открывать окна
     * из фона, а CRM — нет (ограничение Android 10+), поэтому это делаем мы.
     */
    fun openChat(packageName: String, uri: Uri): Boolean = try {
        startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage(packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: RuntimeException) {
        Log.w(TAG, "cannot open chat in $packageName", e)
        false
    }

    /** Вызывается контроллером сразу после новой команды. */
    fun onArmed(request: SendSession.Request<PendingIntent>) {
        handler.removeCallbacks(expiryRunnable)
        handler.postAtTime(expiryRunnable, request.expiresAt)
        // Чат мог открыться ещё до команды — тогда новых событий окна может не быть.
        activeWindowPackage()
            ?.takeIf { AutoSendController.session.isArmedFor(it) }
            ?.let { scheduleClick(it) }
    }

    fun onCancelled() {
        handler.removeCallbacks(clickRunnable)
        handler.removeCallbacks(expiryRunnable)
        pendingPackage = null
    }

    /** Не чаще одной отложенной проверки за раз: поток CONTENT_CHANGED не плодит задачи. */
    private fun scheduleClick(packageName: String) {
        if (pendingPackage != null) return
        pendingPackage = packageName
        handler.postDelayed(clickRunnable, CLICK_DELAY_MS)
    }

    private fun performPendingClick() {
        val packageName = pendingPackage ?: return
        pendingPackage = null
        val session = AutoSendController.session
        if (!session.isArmedFor(packageName)) return

        val root = activeRoot() ?: return
        try {
            // В фокусе другое окно (клавиатура, диалог, другое приложение) — ждём следующего события.
            if (root.packageName?.toString() != packageName) return
            val app = TargetApps.forPackage(packageName) ?: return
            val button = SendButtonFinder.find(root, app)
            if (button == null) {
                // Кнопки ещё нет: чат грузится или поле пустое (мессенджер не подставил текст по ссылке).
                // Один раз вписываем текст CRM; кнопка появится, и сработает следующее событие окна.
                session.takeTextToInsert(packageName)?.let { insertIntoEmptyInput(root, it) }
                return
            }
            try {
                // Единственная попытка: сессия снимается до клика, повтора не будет.
                val request = session.takeForClick(packageName) ?: return
                handler.removeCallbacks(expiryRunnable)
                val clicked = button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                val status = if (clicked) AutoSendContract.STATUS_SENT else AutoSendContract.STATUS_CLICK_FAILED
                AutoSendController.reply(this, request, packageName, status)
            } finally {
                button.recycleSafely()
            }
        } catch (e: RuntimeException) {
            // Макет целевого приложения мог измениться прямо во время обхода дерева.
            Log.w(TAG, "tree traversal failed", e)
        } finally {
            root.recycleSafely()
        }
    }

    private fun insertIntoEmptyInput(root: AccessibilityNodeInfo, text: String) {
        val input = SendButtonFinder.findInput(root) ?: return
        try {
            val empty = input.isShowingHintText || input.text.isNullOrBlank()
            if (empty) {
                val args = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                }
                input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            }
        } finally {
            input.recycleSafely()
        }
    }

    private fun expireIfDue() {
        val expired = AutoSendController.session.expireIfDue() ?: return
        handler.removeCallbacks(clickRunnable)
        pendingPackage = null
        AutoSendController.reply(this, expired, null, AutoSendContract.STATUS_TIMEOUT)
    }

    private fun activeRoot(): AccessibilityNodeInfo? = try {
        rootInActiveWindow
    } catch (e: RuntimeException) {
        Log.w(TAG, "rootInActiveWindow failed", e)
        null
    }

    private fun activeWindowPackage(): String? {
        val root = activeRoot() ?: return null
        return try {
            root.packageName?.toString()
        } finally {
            root.recycleSafely()
        }
    }

    override fun onInterrupt() {
        onCancelled()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        shutdown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private fun shutdown() {
        onCancelled()
        AutoSendController.detach(this, this)
    }

    companion object {
        private const val TAG = "AutoSend"

        /** Пауза перед поиском кнопки: даём завершиться анимации открытия чата (ТЗ: 300–500 мс). */
        const val CLICK_DELAY_MS = 400L
    }
}
