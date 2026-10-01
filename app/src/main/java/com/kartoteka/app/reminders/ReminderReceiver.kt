package com.kartoteka.app.reminders

import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.kartoteka.app.KartotekaApp
import com.kartoteka.app.MainActivity
import com.kartoteka.app.R
import com.kartoteka.app.data.AppointmentLogic
import com.kartoteka.app.data.AppointmentStatus
import com.kartoteka.app.data.JournalEntry
import com.kartoteka.app.data.NotifyChannel
import com.kartoteka.app.data.ReminderTarget
import com.kartoteka.app.data.TemplateKind
import com.kartoteka.app.messaging.AutoSend
import com.kartoteka.app.messaging.Sender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Срабатывает в момент напоминания: пишет человеку или показывает уведомление мне. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(ReminderScheduler.EXTRA_REMINDER_ID, 0)
        if (id == 0L) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Reminders.fire(context.applicationContext as KartotekaApp, id, interactive = false)
            } finally {
                pending.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as KartotekaApp
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { ReminderScheduler.rescheduleAll(app, app.repository) } finally { pending.finish() }
        }
    }
}

object Reminders {
    private const val CHANNEL = "appointments"

    /**
     * @param interactive true — вызвано из приложения (пользователь нажал уведомление), можно открывать чаты.
     */
    suspend fun fire(app: KartotekaApp, reminderId: Long, interactive: Boolean) {
        val repo = app.repository
        val r = repo.getReminder(reminderId) ?: return
        if (r.sentAt != null) return
        val full = repo.getAppointment(r.appointmentId) ?: return
        val a = full.appointment
        if (a.appointmentStatus != AppointmentStatus.PLANNED) return
        val pf = repo.getPerson(a.personId) ?: return
        val p = pf.person

        if (r.target == ReminderTarget.ME.name) {
            val dt = AppointmentLogic.zoned(a.start)
            notify(
                app, (reminderId % Int.MAX_VALUE).toInt(),
                "${AppointmentLogic.timeText(dt)} · ${p.displayName}",
                listOf(AppointmentLogic.offsetTitle(r.offsetMin).replaceFirst("за ", "Через "), a.title, a.place)
                    .filter { it.isNotBlank() }.joinToString(" · "),
                openIntent(app, MainActivity.EXTRA_APPOINTMENT_ID, a.id),
            )
            repo.markReminderSent(reminderId)
            return
        }

        // Напоминание человеку. Если запись уже началась — смысла нет.
        if (a.start < System.currentTimeMillis()) {
            repo.markReminderSent(reminderId); return
        }
        val lang = app.settings.langFor(p)
        val service = repo.getService(a.serviceId)
        val template = AppointmentLogic.messageTemplate(service, TemplateKind.REMINDER, app.settings.template(TemplateKind.REMINDER, lang).value.value)
        val text = AppointmentLogic.fill(template, a, p, lang)
        val channel = a.notifyChannel
        val canAutoMessenger = channel != NotifyChannel.SMS && AutoSend.isServiceEnabled(app) && deviceUnlocked(app)
        val automatic = interactive || channel == NotifyChannel.SMS && Sender.canSmsDirect(app) || canAutoMessenger

        if (!automatic) {
            // Телефон заблокирован или авто-отправка выключена — просим одно нажатие.
            notify(
                app, (reminderId % Int.MAX_VALUE).toInt(),
                "Напомнить ${p.displayName} о записи",
                "Нажмите, чтобы отправить в ${channel.title}: «${text.take(80)}…»",
                openIntent(app, MainActivity.EXTRA_SEND_REMINDER, reminderId),
            )
            return
        }
        val delay = app.settings.autoSendDelaySec.value.value.toIntOrNull() ?: 6
        withContext(Dispatchers.Main) {
            val result = Sender.send(app, pf, channel, text, delay, interactive) { ok ->
                if (ok) app.appScope.launch {
                    repo.markReminderSent(reminderId)
                    repo.addJournal(JournalEntry(personId = p.id, kind = "Переписка", text = "Напоминание о записи (${channel.title}): $text"))
                }
            }
            if (result == Sender.Result.SENT && !interactive) {
                notify(app, (reminderId % Int.MAX_VALUE).toInt(), "Напоминание отправлено", "${p.displayName} · ${channel.title}", openIntent(app, MainActivity.EXTRA_APPOINTMENT_ID, a.id), quiet = true)
            }
        }
    }

    private fun deviceUnlocked(context: Context): Boolean {
        val km = context.getSystemService(KeyguardManager::class.java)
        val pm = context.getSystemService(PowerManager::class.java)
        return pm.isInteractive && !km.isKeyguardLocked
    }

    private fun openIntent(context: Context, extra: String, id: Long): PendingIntent =
        PendingIntent.getActivity(
            context, (extra.hashCode() * 31 + id).toInt(),
            Intent(context, MainActivity::class.java).putExtra(extra, id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun notify(context: Context, id: Int, title: String, text: String, intent: PendingIntent, quiet: Boolean = false) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Записи и напоминания", NotificationManager.IMPORTANCE_HIGH))
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(com.kartoteka.app.AppIcons.notificationIcon(context))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(intent)
            .setAutoCancel(true)
            .setSilent(quiet)
            .build()
        runCatching { nm.notify(id, n) }
    }
}
