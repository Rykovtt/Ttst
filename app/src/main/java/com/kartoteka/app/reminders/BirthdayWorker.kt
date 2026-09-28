package com.kartoteka.app.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kartoteka.app.KartotekaApp
import com.kartoteka.app.MainActivity
import com.kartoteka.app.R
import com.kartoteka.app.data.ArchiveLogic
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** Раз в день (около 9:00) напоминает о днях рождения сегодня и через 3 дня. */
class BirthdayWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as KartotekaApp
        if (!app.settings.birthdayReminders.value) return Result.success()
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= 33
        ) return Result.success()

        val all = app.repository.getAll()
        val today = ArchiveLogic.upcomingBirthdays(all, 0)
        val soon = ArchiveLogic.upcomingBirthdays(all, 3).filter { it.second == 3L }
        ensureChannel(app)
        val nm = NotificationManagerCompat.from(app)
        (today + soon).forEach { (pf, days) ->
            val p = pf.person
            val age = ArchiveLogic.turningAge(p)?.let { " — исполняется ${ArchiveLogic.ageString(it)}" }.orEmpty()
            val title = if (days == 0L) "🎂 Сегодня день рождения: ${p.displayName}" else "Через 3 дня день рождения: ${p.displayName}"
            val intent = Intent(app, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_PERSON_ID, p.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pi = PendingIntent.getActivity(app, p.id.toInt(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val n = NotificationCompat.Builder(app, CHANNEL)
                .setSmallIcon(com.kartoteka.app.AppIcons.notificationIcon(app))
                .setContentTitle(title)
                .setContentText("Не забудьте поздравить$age")
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            @Suppress("MissingPermission")
            nm.notify((p.id * 10 + days).toInt(), n)
        }
        return Result.success()
    }

    companion object {
        private const val CHANNEL = "birthdays"
        private const val WORK = "birthday_reminders"

        fun schedule(context: Context) {
            val now = LocalDateTime.now()
            var next = now.with(LocalTime.of(9, 0))
            if (!next.isAfter(now)) next = next.plusDays(1)
            val delay = Duration.between(now, next).toMinutes()
            val req = PeriodicWorkRequestBuilder<BirthdayWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delay, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
        }

        fun cancel(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(WORK)

        private fun ensureChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Дни рождения", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }
}
