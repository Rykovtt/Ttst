package com.kartoteka.app.reminders

import com.kartoteka.app.i18n.t

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.kartoteka.app.data.AppointmentReminder
import com.kartoteka.app.data.Repository

/** Точные будильники для напоминаний о записях. */
object ReminderScheduler {
    const val EXTRA_REMINDER_ID = "reminder_id"

    fun schedule(context: Context, reminders: List<AppointmentReminder>) {
        val am = context.getSystemService(AlarmManager::class.java)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        reminders.filter { it.sentAt == null && it.fireAt > System.currentTimeMillis() }.forEach { r ->
            val pi = pending(context, r.id)
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.fireAt, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.fireAt, pi)
        }
    }

    fun cancel(context: Context, reminderIds: List<Long>) {
        val am = context.getSystemService(AlarmManager::class.java)
        reminderIds.forEach { am.cancel(pending(context, it)) }
    }

    /** После перезагрузки или обновления приложения. */
    suspend fun rescheduleAll(context: Context, repo: Repository) {
        schedule(context, repo.upcomingReminders(System.currentTimeMillis()))
    }

    private fun pending(context: Context, id: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context, id.toInt(),
            Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_REMINDER_ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
