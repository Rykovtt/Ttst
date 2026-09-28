package com.kartoteka.app.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        Person::class, ContactItem::class, DetailField::class, Photo::class,
        Group::class, PersonGroup::class, JournalEntry::class,
        Place::class, Relation::class, Appointment::class, AppointmentReminder::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2, spec = AppDatabase.Migration1To2::class)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): ArchiveDao

    /** v2: адреса, связи, календарь. Старый адрес из карточки переносится в «Дом». */
    class Migration1To2 : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            db.execSQL("INSERT INTO places (personId, kind, label, address) SELECT id, 'HOME', '', address FROM persons WHERE address != ''")
        }
    }

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
