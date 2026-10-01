package com.kartoteka.app.screens

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.kartoteka.app.data.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Обновление с RVault 1.x: люди и записи сохраняются, появляются услуги. */
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
    fun v1ToV2KeepsAppointmentsAndAddsServices() {
        helper.createDatabase("migration.db", 1).apply {
            execSQL(
                """INSERT INTO persons (id, lastName, firstName, middleName, nickname, gender, relation, closeness, company,
                   position, city, howMet, notes, favorite, createdAt, updatedAt, language)
                   VALUES (1, '', 'Максим', '', '', '', '', 0, '', '', '', '', '', 0, 0, 0, '')"""
            )
            execSQL(
                """INSERT INTO appointments (id, personId, start, durationMin, title, place, notes, channel, status, createdAt)
                   VALUES (1, 1, 1000, 60, 'Тату-сеанс', '', '', 'WHATSAPP', 'PLANNED', 0)"""
            )
            close()
        }
        val db = helper.runMigrationsAndValidate("migration.db", 2, true)
        db.query("SELECT title, serviceId FROM appointments").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Тату-сеанс", c.getString(0))
            assertTrue(c.isNull(1))
        }
        db.query("SELECT COUNT(*) FROM services").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
    }

    @Test
    fun v2ToV3AddsVoiceNotes() {
        helper.createDatabase("migration3.db", 2).apply {
            execSQL(
                """INSERT INTO persons (id, lastName, firstName, middleName, nickname, gender, relation, closeness, company,
                   position, city, howMet, notes, favorite, createdAt, updatedAt, language)
                   VALUES (1, '', 'Максим', '', '', '', '', 0, '', '', '', '', '', 0, 0, 0, '')"""
            )
            close()
        }
        val db = helper.runMigrationsAndValidate("migration3.db", 3, true)
        db.query("SELECT firstName FROM persons").use { it.moveToFirst(); assertEquals("Максим", it.getString(0)) }
        db.query("SELECT COUNT(*) FROM voice_notes").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
    }


    @Test
    fun v4ToV5DropsChatsAndKeepsPeople() {
        helper.createDatabase("migration5.db", 4).apply {
            execSQL(
                """INSERT INTO persons (id, lastName, firstName, middleName, nickname, gender, relation, closeness, company,
                   position, city, howMet, notes, favorite, createdAt, updatedAt, language)
                   VALUES (1, '', 'Максим', '', '', '', '', 0, '', '', '', '', '', 0, 0, 0, '')"""
            )
            execSQL("""INSERT INTO chats (id, personId, source, title, meAuthor, importedAt, messageCount, firstAt, lastAt)
                       VALUES (1, 1, 'WhatsApp', 'Максим', '', 0, 1, 0, 0)""")
            execSQL("""INSERT INTO chat_messages (id, chatId, time, author, text, textLower)
                       VALUES (1, 1, 0, 'Максим', 'привет', 'привет')""")
            close()
        }
        val db = helper.runMigrationsAndValidate("migration5.db", 5, true)
        db.query("SELECT firstName FROM persons").use { it.moveToFirst(); assertEquals("Максим", it.getString(0)) }
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name IN ('chats','chat_messages')").use {
            assertEquals(0, it.count)
        }
    }
}
