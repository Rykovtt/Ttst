package com.kartoteka.app

import android.app.Application
import com.kartoteka.app.data.AppDatabase
import com.kartoteka.app.data.BackupManager
import com.kartoteka.app.data.PhoneContacts
import com.kartoteka.app.data.PhotoStorage
import com.kartoteka.app.data.Repository
import com.kartoteka.app.data.Settings
import com.kartoteka.app.reminders.BirthdayWorker

open class KartotekaApp : Application() {
    val settings by lazy { Settings(this) }
    val repository by lazy { Repository(createDatabase(), PhotoStorage(this)) }
    val backup by lazy { BackupManager(this, repository) }
    val phoneContacts by lazy { PhoneContacts(this) }

    override fun onCreate() {
        super.onCreate()
        if (settings.birthdayReminders.value) scheduleReminders()
    }

    protected open fun createDatabase(): AppDatabase = AppDatabase.create(this)
    protected open fun scheduleReminders() = BirthdayWorker.schedule(this)
}
