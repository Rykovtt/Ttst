package com.kartoteka.app.screens

import androidx.test.core.app.ApplicationProvider
import com.kartoteka.app.assistant.Noa
import com.kartoteka.app.data.ContactItem
import com.kartoteka.app.data.Person
import com.kartoteka.app.data.ServiceTemplate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class NoaExecutorTest {
    private val app get() = ApplicationProvider.getApplicationContext<TestApp>()
    private val now = LocalDateTime.of(2026, 10, 4, 10, 0)

    @org.junit.Before fun ru() { com.kartoteka.app.i18n.I18n.init(app, com.kartoteka.app.i18n.UiLang.RU) }

    private fun seed() = runBlocking {
        app.repository.savePerson(Person(firstName = "Мария", lastName = "Фролова"),
            listOf(ContactItem(type = "PHONE", value = "+380671112233")), emptyList(), emptyList())
        app.repository.savePerson(Person(firstName = "Анна", nickname = "Аня"), emptyList(), emptyList(), emptyList())
        app.repository.saveService(ServiceTemplate(name = "Тату-сеанс", durationMin = 180, place = "Студия"))
    }

    @Test fun booksAppointmentAfterConfirmation() = runBlocking {
        seed()
        val reply = Noa(app).handle("запиши Марию Фролову на тату на 12-е в 12:00", now)
        assertTrue(reply is Noa.Reply.Confirm)
        val done = (reply as Noa.Reply.Confirm).onYes()
        assertTrue(done is Noa.Reply.Say2Open)
        val appts = app.repository.appointmentsBetween(0, Long.MAX_VALUE)
        assertEquals(1, appts.size)
        val a = appts[0].appointment
        assertEquals("Тату-сеанс", a.title)
        assertEquals(180, a.durationMin)
        val dt = com.kartoteka.app.data.AppointmentLogic.zoned(a.start)
        assertEquals(12, dt.dayOfMonth); assertEquals(12, dt.hour)
    }

    @Test fun findsPersonAndReportsMissingPhone() = runBlocking {
        seed()
        val found = Noa(app).handle("найди Марию", now)
        assertTrue(found is Noa.Reply.Say2Open)
        // звонок человеку без телефона
        val call = Noa(app).handle("позвони Ане", now)
        assertTrue((call as Noa.Reply.Say).text.contains("номера"))
    }

    @Test fun addsNoteAfterConfirmation() = runBlocking {
        seed()
        val reply = Noa(app).handle("добавь заметку Ане «переехала во Львов»", now) as Noa.Reply.Confirm
        reply.onYes()
        val anya = app.repository.getAll().first { it.person.nickname == "Аня" }
        assertEquals(1, anya.journal.size)
        assertEquals("переехала во Львов", anya.journal[0].text)
    }

    @Test fun matchesAcrossRussianUkrainianLetters() = runBlocking {
        app.repository.savePerson(Person(firstName = "Ілля", lastName = "Риков"),
            listOf(ContactItem(type = "PHONE", value = "+380731018582")), emptyList(), emptyList())
        // запрос русскими буквами находит украинскую карточку
        val r = Noa(app).handle("открой илья рыков", now)
        assertTrue("reply=$r", r is Noa.Reply.Say2Open)
    }

    @Test fun unknownCommandIsHandledGracefully() = runBlocking {
        val r = Noa(app).handle("расскажи анекдот", now)
        assertTrue(r is Noa.Reply.Say)
    }
}
