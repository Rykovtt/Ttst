package com.kartoteka.app

import android.app.Application
import com.kartoteka.app.data.AppDatabase
import com.kartoteka.app.data.BackupManager
import com.kartoteka.app.data.PhoneContacts
import com.kartoteka.app.data.PhotoStorage
import com.kartoteka.app.data.Repository
import com.kartoteka.app.data.Settings
import com.kartoteka.app.reminders.BirthdayWorker
import com.kartoteka.app.reminders.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import java.io.File

open class KartotekaApp : Application() {
    val settings by lazy { Settings(this) }
    val repository by lazy { Repository(createDatabase(), PhotoStorage(this)) }
    val backup by lazy { BackupManager(this, repository) }
    val phoneContacts by lazy { PhoneContacts(this) }
    val pinLock by lazy { com.kartoteka.app.data.PinLock(this) }

    /** Для фоновой работы, которая должна пережить экран (журнал после отправки и т.п.). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        com.kartoteka.app.i18n.I18n.init(this, com.kartoteka.app.i18n.UiLang.of(settings.uiLang.value.value))
        // Карта OpenStreetMap: кэш плиток во внутренней памяти приложения.
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = File(filesDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid-tiles")
        }
        if (settings.birthdayReminders.value) scheduleReminders()
        appScope.launch(Dispatchers.IO) { runCatching { rescheduleAppointmentReminders() } }
    }

    protected open fun createDatabase(): AppDatabase = AppDatabase.create(this)
    protected open fun scheduleReminders() = BirthdayWorker.schedule(this)
    protected open suspend fun rescheduleAppointmentReminders() = ReminderScheduler.rescheduleAll(this, repository)
}
