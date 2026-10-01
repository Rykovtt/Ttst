package com.kartoteka.app.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.DeleteTable
import androidx.room.migration.AutoMigrationSpec
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        Person::class, ContactItem::class, DetailField::class, Photo::class,
        Group::class, PersonGroup::class, JournalEntry::class,
        Place::class, Relation::class, Appointment::class, AppointmentReminder::class,
        ServiceTemplate::class, VoiceNote::class,
    ],
    version = 5,
    exportSchema = true,
    // 1 → 2: услуги и привязка записи к услуге. Данные версии 1.x сохраняются.
    // 2 → 3: голосовые заметки. 3 → 4: импорт переписки (1.7). 4 → 5: импорт переписки убран, таблицы удаляются.
    autoMigrations = [
        AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5, spec = AppDatabase.DropChats::class),
    ],
)
abstract class AppDatabase : RoomDatabase() {
    @DeleteTable(tableName = "chat_messages")
    @DeleteTable(tableName = "chats")
    class DropChats : AutoMigrationSpec

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
