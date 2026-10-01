package com.kartoteka.app.screens

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.kartoteka.app.data.AutoBackup
import com.kartoteka.app.data.ChatParser
import com.kartoteka.app.data.NamedFile
import com.kartoteka.app.data.Person
import com.kartoteka.app.data.Photo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class ChatImportTest {
    private val app get() = ApplicationProvider.getApplicationContext<TestApp>()
    private val utc = ZoneId.of("UTC")
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int = 0) =
        LocalDateTime.of(y, mo, d, h, mi, s).toInstant(ZoneOffset.UTC).toEpochMilli()

    private val waRu = """
        31.12.2023, 21:41 - Сообщения и звонки защищены сквозным шифрованием.
        31.12.2023, 21:41 - Анна: С наступающим!
        31.12.2023, 21:43 - Я: Спасибо 🎉
        Встречаемся у ёлки
        01.01.2024, 09:05 - Анна: <Без медиафайлов>
    """.trimIndent()

    @Test fun whatsAppAndroidRussian() {
        val c = ChatParser.parse(listOf(NamedFile("Чат WhatsApp с Анна.txt", waRu.toByteArray())), utc)!!
        assertEquals("WhatsApp", c.source)
        assertEquals("Анна", c.title)
        assertEquals(4, c.messages.size)
        assertEquals("", c.messages[0].author)
        assertEquals("Спасибо 🎉\nВстречаемся у ёлки", c.messages[2].text)
        assertEquals(at(2023, 12, 31, 21, 43), c.messages[2].time)
        assertEquals(listOf("Анна" to 2, "Я" to 1), c.authors)
    }

    @Test fun whatsAppUsAmPmAndIos() {
        val us = "12/31/23, 9:41 PM - Anna: Happy new year\n1/2/24, 12:05 AM - Max: Thanks"
        val c = ChatParser.parseWhatsApp(us, zone = utc)!!
        assertEquals(at(2023, 12, 31, 21, 41), c.messages[0].time)
        assertEquals(at(2024, 1, 2, 0, 5), c.messages[1].time)
        val ios = "[31.12.23, 21:41:05] Анна: Привет\n[01.01.24, 09:00:00] Макс: Утро"
        val c2 = ChatParser.parseWhatsApp(ios, zone = utc)!!
        assertEquals(at(2023, 12, 31, 21, 41, 5), c2.messages[0].time)
        assertEquals("Макс", c2.messages[1].author)
    }

    @Test fun whatsAppZipWithMedia() {
        val zip = ByteArrayOutputStream().also { bos ->
            ZipOutputStream(bos).use { z ->
                z.putNextEntry(ZipEntry("_chat.txt")); z.write(waRu.toByteArray()); z.closeEntry()
                z.putNextEntry(ZipEntry("IMG-001.jpg")); z.write(ByteArray(10)); z.closeEntry()
            }
        }.toByteArray()
        val c = ChatParser.parse(listOf(NamedFile("WhatsApp Chat with Anna.zip", zip)), utc)!!
        assertEquals("Anna", c.title)
        assertEquals(4, c.messages.size)
    }

    @Test fun telegramJson() {
        val json = """{"name":"Максим","type":"personal_chat","messages":[
            {"id":1,"type":"service","date":"2024-01-01T10:00:00","actor":"Максим","action":"phone_call"},
            {"id":2,"type":"message","date":"2024-01-01T10:00:05","date_unixtime":"1704103205","from":"Максим","text":"Привет!"},
            {"id":3,"type":"message","date":"2024-01-01T10:01:00","date_unixtime":"1704103260","from":"Я","text":["Смотри ",{"type":"link","text":"https://example.com"}]},
            {"id":4,"type":"message","date":"2024-01-01T10:02:00","date_unixtime":"1704103320","from":"Максим","photo":"photos/1.jpg","text":""}
        ]}"""
        val c = ChatParser.parse(listOf(NamedFile("result.json", json.toByteArray())), utc)!!
        assertEquals("Telegram", c.source)
        assertEquals("Максим", c.title)
        assertEquals(3, c.messages.size)
        assertEquals("Смотри https://example.com", c.messages[1].text)
        assertEquals("[фото]", c.messages[2].text)
        assertEquals(1704103205000L, c.messages[0].time)
    }

    @Test fun telegramHtmlWithJoinedMessages() {
        val html = """
            <div class="page_header"><div class="content"><div class="text bold">
              Максим
            </div></div></div>
            <div class="message service" id="message-1"><div class="body details">1 January 2024</div></div>
            <div class="message default clearfix" id="message2"><div class="body">
              <div class="pull_right date details" title="01.01.2024 10:00:05 UTC+03:00">10:00</div>
              <div class="from_name">Максим</div>
              <div class="text">Привет &amp; с Новым годом!<br>Как ты?</div>
            </div></div>
            <div class="message default clearfix joined" id="message3"><div class="body">
              <div class="pull_right date details" title="01.01.2024 10:00:30 UTC+03:00">10:00</div>
              <div class="text">Жду ответа</div>
            </div></div>
        """.trimIndent()
        val c = ChatParser.parse(listOf(NamedFile("messages.html", html.toByteArray())), utc)!!
        assertEquals("Максим", c.title)
        assertEquals(2, c.messages.size)
        assertEquals("Привет & с Новым годом!\nКак ты?", c.messages[0].text)
        assertEquals("Максим", c.messages[1].author)
        assertEquals(at(2024, 1, 1, 7, 0, 5), c.messages[0].time)
    }

    @Test fun garbageIsRejected() {
        assertEquals(null, ChatParser.parse(listOf(NamedFile("notes.txt", "просто текст".toByteArray()))))
    }

    @Test fun importReplacesSameChatAndSearchIgnoresCyrillicCase() = runBlocking {
        val repo = app.repository
        val id = repo.savePerson(Person(firstName = "Анна"), emptyList(), emptyList(), emptyList())
        val chat = ChatParser.parse(listOf(NamedFile("Чат WhatsApp с Анна.txt", waRu.toByteArray())), utc)!!
        repo.importChat(id, chat, "Я")
        repo.importChat(id, chat, "Я")
        assertEquals(1, repo.allChats().size)
        assertEquals(4, repo.chatMessages(repo.allChats()[0].id).size)
        assertEquals(mapOf(id to "Спасибо 🎉\nВстречаемся у ёлки"), repo.searchChats("ЁЛКИ встречаемся"))
        assertTrue(repo.searchChats("львов").isEmpty())
        repo.deletePerson(id)
        assertTrue(repo.allChats().isEmpty())
    }

    @Test fun photosAreEncryptedAndLegacyOnesMigrated() = runBlocking {
        val repo = app.repository
        val legacy = File(repo.photos.dir, "old.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val id = repo.savePerson(Person(firstName = "Старое", avatarPath = legacy.absolutePath), emptyList(), emptyList(), emptyList())
        repo.rawDao.insertPhoto(Photo(personId = id, path = legacy.absolutePath))
        assertEquals(1, repo.encryptLegacyPhotos())
        val pf = repo.getPerson(id)!!
        val newPath = pf.person.avatarPath!!
        assertTrue(newPath.endsWith(".enc"))
        assertEquals(newPath, pf.photos.single().path)
        assertFalse(legacy.exists())
        assertFalse(File(newPath).readBytes().contentEquals(byteArrayOf(1, 2, 3, 4)))
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), repo.photos.readBytes(newPath))
        assertEquals(0, repo.encryptLegacyPhotos())
    }

    @Test fun backupCarriesChatsAndEncryptedPhotos() = runBlocking {
        val repo = app.repository
        val id = repo.savePerson(Person(firstName = "Анна"), emptyList(), emptyList(), emptyList())
        val enc = repo.photos.saveEncrypted("pic.jpg", byteArrayOf(9, 8, 7))
        repo.addPhoto(id, enc)
        repo.importChat(id, ChatParser.parseWhatsApp(waRu, "Анна", utc)!!, "Я")
        val file = File(app.cacheDir, "chat.krtk")
        app.backup.export(Uri.fromFile(file), "secret")
        app.backup.import(Uri.fromFile(file), "secret", replace = true)
        val pf = repo.getAll().single()
        assertArrayEquals(byteArrayOf(9, 8, 7), repo.photos.readBytes(pf.person.avatarPath!!))
        val chat = repo.allChats().single()
        assertEquals("Я", chat.meAuthor)
        assertEquals(4, repo.chatMessages(chat.id).size)
    }

    @Test fun autoBackupKeepsNewestCopiesOnly() {
        val names = listOf("RVault_2026-09-01_0300.krtk", "RVault_2026-09-03_0300.krtk", "заметки.txt",
            "RVault_2026-09-02_0300.krtk", "RVault_2026-08-30_0300.krtk")
        assertEquals(listOf("RVault_2026-09-01_0300.krtk", "RVault_2026-08-30_0300.krtk"), AutoBackup.backupsToDelete(names, 2))
        assertTrue(AutoBackup.backupsToDelete(names, 10).isEmpty())
    }

    @Test fun backupPasswordIsStoredEncrypted() {
        AutoBackup.setPassword(app, "очень-секретно")
        assertEquals("очень-секретно", AutoBackup.password(app))
        val raw = File(app.filesDir, "backup_secret.enc").readBytes().decodeToString()
        assertFalse(raw.contains("секретно"))
        assertNotNull(AutoBackup.password(app))
    }
}
