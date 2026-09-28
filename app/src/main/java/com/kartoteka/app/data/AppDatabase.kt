package com.kartoteka.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        Person::class, ContactItem::class, DetailField::class, Photo::class,
        Group::class, PersonGroup::class, JournalEntry::class,
        Place::class, Relation::class, Appointment::class, AppointmentReminder::class,
    ],
    version = 1,
    exportSchema = true,
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
