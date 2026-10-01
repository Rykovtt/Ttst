package com.kartoteka.app.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        Person::class, ContactItem::class, DetailField::class, Photo::class,
        Group::class, PersonGroup::class, JournalEntry::class,
        Place::class, Relation::class, Appointment::class, AppointmentReminder::class,
        ServiceTemplate::class,
    ],
    version = 2,
    exportSchema = true,
    // 1 → 2: услуги и привязка записи к услуге. Данные версии 1.x сохраняются.
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): ArchiveDao

    companion object {
        /** База зашифрована SQLCipher (AES-256); ключ хранится в Android Keystore. */
        fun create(context: Context): AppDatabase {
            System.loadLibrary("sqlcipher")
            val passphrase = KeyManager.databasePassphrase(context)
            return Room.databaseBuilder(context, AppDatabase::class.java, "kartoteka.db")
                .openHelperFactory(SupportOpenHelperFactory(passphrase))
                .build()
        }
    }
}
