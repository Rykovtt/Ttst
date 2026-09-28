package com.kartoteka.app.messaging

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Служба специальных возможностей: когда картотека открыла чат с готовым текстом,
 * находит кнопку «Отправить» и нажимает её. Работает только во время рассылки,
 * запущенной из картотеки, и только в WhatsApp и Telegram.
 */
class AutoSendService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onInterrupt() = Unit

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (AutoSend.armedJob == null) return
        if (event?.packageName?.toString() !in PACKAGES) return
        trySend()
    }

    /** Пытается отправить текущее сообщение. true — кнопка нажата. */
    fun trySend(): Boolean {
        val job = AutoSend.armedJob ?: return false
        val root = rootInActiveWindow ?: return false
        val pkg = root.packageName?.toString() ?: return false
        if (pkg !in PACKAGES) return false

        var send = findSend(root, pkg)
        if (send == null) {
            // Текст не подставился (например, в Telegram по номеру) — впишем его сами.
            val input = findInput(root) ?: return false
            if (input.text.isNullOrBlank()) {
                input.performAction(
                    AccessibilityNodeInfo.ACTION_SET_TEXT,
                    Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, job.text) },
                )
            }
            return false // кнопка появится при следующей проверке
        }
        while (send != null && !send.isClickable) send = send.parent
        if (send != null && send.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            AutoSend.onSent()
            return true
        }
        return false
    }

    private fun findSend(root: AccessibilityNodeInfo, pkg: String): AccessibilityNodeInfo? {
        if (pkg.startsWith("com.whatsapp")) {
            root.findAccessibilityNodeInfosByViewId("$pkg:id/send").firstOrNull { it.isVisibleToUser }?.let { return it }
        }
        return find(root) { n ->
            val d = n.contentDescription?.toString()?.trim()?.lowercase() ?: return@find false
            n.isVisibleToUser && d in SEND_LABELS
        }
    }

    private fun findInput(root: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        find(root) { it.isEditable && it.isVisibleToUser }

    private fun find(node: AccessibilityNodeInfo, depth: Int = 0, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (depth > 40) return null
        if (predicate(node)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            find(child, depth + 1, predicate)?.let { return it }
        }
        return null
    }

    companion object {
        @Volatile
        var instance: AutoSendService? = null
            private set

        val PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram")

        private val SEND_LABELS = setOf("send", "отправить", "надіслати", "wyślij", "senden", "send message", "отправить сообщение")
    }
}
