package com.kartoteka.app.screens

import androidx.room.Room
import com.kartoteka.app.KartotekaApp
import com.kartoteka.app.data.AppDatabase

/** Приложение для тестов: база в памяти без SQLCipher (нативная библиотека недоступна на JVM). */
class TestApp : KartotekaApp() {
    override fun createDatabase(): AppDatabase =
        Room.inMemoryDatabaseBuilder(this, AppDatabase::class.java).allowMainThreadQueries().build()

    override fun scheduleReminders() = Unit
    override suspend fun rescheduleAppointmentReminders() = Unit
}
