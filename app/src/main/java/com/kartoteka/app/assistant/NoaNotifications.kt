package com.kartoteka.app.assistant

import android.app.Notification
import android.app.RemoteInput
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.kartoteka.app.i18n.t

/**
 * Доступ к уведомлениям (включает сам человек в настройках Android) даёт ассистенту то, что умеют
 * Android Auto и Google Ассистент:
 *  • управлять музыкой/видео в любом плеере (пауза, дальше, перемешать, «включи …» прямо в плеере);
 *  • прочитать вслух ответ человека, которому только что написали, — только по просьбе и только от него;
 *  • ответить в мессенджере через кнопку «Ответить» самого уведомления.
 * Ничего не сохраняется и никуда не отправляется: сообщения читаются вслух и забываются.
 */
class NoaNotifications : NotificationListenerService() {

    override fun onListenerConnected() { instance = this }
    override fun onListenerDisconnected() { if (instance === this) instance = null }
    override fun onDestroy() { if (instance === this) instance = null; super.onDestroy() }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val w = watch ?: return
        if (System.currentTimeMillis() > w.until) { watch = null; return }
        val msg = message(sbn) ?: return
        if (msg.pkg !in MESSENGERS || !w.matches(msg.sender)) return
        if (msg.text == w.lastHeard) return
        w.lastHeard = msg.text
        if (!w.repeat) watch = null
        NoaVoice.speak(applicationContext, t("%1\$s отвечает: %2\$s", msg.sender, msg.text)) {}
    }

    data class Msg(val pkg: String, val sender: String, val text: String, val key: String, val posted: Long)

    companion object {
        @Volatile var instance: NoaNotifications? = null
            private set

        /** Ждём ответ от человека: [names] — как он может быть подписан в мессенджере. */
        class Watch(val names: List<String>, val until: Long, val repeat: Boolean) {
            @Volatile var lastHeard: String? = null
            fun matches(sender: String): Boolean {
                val s = norm(sender)
                return names.any { n -> norm(n).let { it.isNotBlank() && (s.contains(it) || it.contains(s) && s.length >= 3) } }
            }
        }
        @Volatile var watch: Watch? = null

        val MESSENGERS = setOf(
            "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "org.thunderdog.challegram", "com.viber.voip",
            "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.facebook.orca", "com.instagram.android",
            "com.discord", "com.skype.raider", "com.microsoft.teams", "com.slack",
        )

        fun granted(context: Context): Boolean =
            androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

        /** Список приложений с доступом к уведомлениям; не нашёлся — настройки уведомлений приложения. */
        fun openSettings(context: Context) {
            val tries = listOf(
                Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
                Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"),
                Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName),
                Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + context.packageName)),
            )
            for (i in tries) {
                if (context !is android.app.Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (runCatching { context.startActivity(i) }.isSuccess) return
            }
        }

        /** «О приложении»: там в меню ⋮ включается «Разрешить ограниченные настройки» (для приложений не из Google Play). */
        fun openAppDetails(context: Context) {
            val i = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + context.packageName))
            if (context !is android.app.Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(i) }
        }

        private fun norm(s: String) = s.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "")
            .map { c -> when (c) { 'і', 'ї', 'ы', 'й' -> 'и'; 'є', 'ё', 'э' -> 'е'; 'ґ' -> 'г'; else -> c } }.joinToString("")

        fun message(sbn: StatusBarNotification): Msg? {
            val n = sbn.notification ?: return null
            if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
            val ex = n.extras ?: return null
            // Переписка: последнее сообщение из MessagingStyle; иначе заголовок/текст.
            val style = ex.getParcelableArray(Notification.EXTRA_MESSAGES)?.mapNotNull { it as? Bundle }?.lastOrNull()
            val sender = (style?.getCharSequence("sender") ?: ex.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
                ?: ex.getCharSequence(Notification.EXTRA_TITLE))?.toString()?.trim().orEmpty()
            val text = (style?.getCharSequence("text") ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString()?.trim().orEmpty()
            if (sender.isBlank() || text.isBlank()) return null
            return Msg(sbn.packageName, sender, text, sbn.key, sbn.postTime)
        }

        /** Свежие сообщения из мессенджеров (те, что сейчас в шторке). */
        fun recentMessages(): List<Msg> = runCatching {
            instance?.activeNotifications.orEmpty().filter { it.packageName in MESSENGERS }.mapNotNull { message(it) }
                .sortedByDescending { it.posted }
        }.getOrDefault(emptyList())

        /** Ответить в переписке через кнопку «Ответить» уведомления (как в Android Auto). */
        fun reply(context: Context, names: List<String>, text: String): Msg? {
            val svc = instance ?: return null
            val w = Watch(names, Long.MAX_VALUE, false)
            val sbn = runCatching { svc.activeNotifications.orEmpty() }.getOrDefault(emptyArray())
                .filter { it.packageName in MESSENGERS }
                .sortedByDescending { it.postTime }
                .firstOrNull { s -> message(s)?.let { w.matches(it.sender) } == true && replyAction(s.notification) != null } ?: return null
            val action = replyAction(sbn.notification) ?: return null
            val inputs = action.remoteInputs ?: return null
            val results = Bundle().apply { inputs.forEach { putCharSequence(it.resultKey, text) } }
            val intent = Intent().addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            RemoteInput.addResultsToIntent(inputs, intent, results)
            return runCatching { action.actionIntent.send(context, 0, intent); message(sbn) }.getOrNull()
        }

        private fun replyAction(n: Notification): Notification.Action? =
            n.actions?.firstOrNull { a -> a.remoteInputs?.any { it.allowFreeFormInput } == true }
                ?: runCatching {
                    Notification.WearableExtender(n).actions.firstOrNull { a -> a.remoteInputs?.any { it.allowFreeFormInput } == true }
                }.getOrNull()

        // ---- музыка и видео ----

        fun controllers(context: Context): List<MediaController> = runCatching {
            val msm = context.getSystemService(MediaSessionManager::class.java)
            msm.getActiveSessions(ComponentName(context, NoaNotifications::class.java))
        }.getOrDefault(emptyList())

        /** Плеер, который сейчас играет (или последний активный); можно сузить до приложения. */
        fun player(context: Context, pkg: String? = null): MediaController? {
            val list = controllers(context).filter { pkg == null || it.packageName == pkg }
            return list.firstOrNull { it.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING } ?: list.firstOrNull()
        }
    }
}
