package com.kartoteka.app.screens

import androidx.room.Room
import com.kartoteka.app.KartotekaApp
import com.kartoteka.app.data.AppDatabase

/** Приложение для тестов: база в памяти без SQLCipher (нативная библиотека недоступна на JVM). */
class TestApp : KartotekaApp() {
    override fun createDatabase(): AppDatabase =
        Room.inMemoryDatabaseBuilder(this, AppDatabase::class.java).allowMainThreadQueries().build()

    /** На JVM нет Android Keystore — тот же AES-GCM, но с постоянным тестовым ключом. */
    override fun createFileVault() = object : com.kartoteka.app.data.FileVault() {
        override fun key() = javax.crypto.spec.SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
    }

    override fun scheduleReminders() = Unit
    override suspend fun rescheduleAppointmentReminders() = Unit
}
