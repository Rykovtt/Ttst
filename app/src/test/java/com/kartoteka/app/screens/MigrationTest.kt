package com.kartoteka.app.screens

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.kartoteka.app.data.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Обновление с версии 1.0: люди сохраняются, адрес из карточки переезжает в «Дом». */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun v1ToV2KeepsPeopleAndMovesAddress() {
        helper.createDatabase("migration.db", 1).apply {
            execSQL(
                """INSERT INTO persons (id, lastName, firstName, middleName, nickname, gender, relation, closeness, company,
                   position, city, address, howMet, notes, favorite, createdAt, updatedAt)
                   VALUES (1, 'Петров', 'Иван', '', '', '', '', 0, '', '', '', 'Киев, Крещатик 1', '', '', 0, 0, 0),
                          (2, '', 'Анна', '', '', '', '', 0, '', '', '', '', '', '', 0, 0, 0)"""
            )
            close()
        }
        val db = helper.runMigrationsAndValidate("migration.db", 2, true)
        db.query("SELECT COUNT(*) FROM persons").use { it.moveToFirst(); assertEquals(2, it.getInt(0)) }
        db.query("SELECT personId, kind, address FROM places").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals(1L, c.getLong(0))
            assertEquals("HOME", c.getString(1))
            assertEquals("Киев, Крещатик 1", c.getString(2))
        }
    }
}
