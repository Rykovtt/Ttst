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

    @Test fun addsNoteRightAway() = runBlocking {
        seed()
        // Заметка добавляется сразу (её можно удалить в хронике) и открывается карточка человека.
        val reply = Noa(app).handle("добавь заметку Ане «переехала во Львов»", now) as Noa.Reply.Say2Open
        assertTrue(reply.personId != null)
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

    @Test fun instagramRouteInfoAndLastPerson() = runBlocking {
        app.repository.savePerson(
            Person(firstName = "Олег", birthDay = 7, birthMonth = 10, birthYear = 1990),
            listOf(ContactItem(type = "INSTAGRAM", value = "oleg.ig"), ContactItem(type = "PHONE", value = "+380671234567")),
            emptyList(), emptyList(),
        )
        val noa = Noa(app)
        assertTrue(noa.handle("відкрий інстаграм Олега", now) is Noa.Reply.Do)
        // Адреса нет — честно говорим об этом, а не открываем пустую карту.
        assertTrue(noa.handle("проклади маршрут до Олега", now) is Noa.Reply.Say)
        val bd = noa.handle("коли день народження в Олега", now)
        assertTrue("reply=$bd", bd is Noa.Reply.Say2Open && bd.text.contains("7"))
        // «ей/йому» — тот же человек, что в прошлой команде.
        val phone = noa.handle("який номер у нього", now) as Noa.Reply.Say
        assertTrue(phone.text.contains("67"))
        assertTrue(noa.handle("що в мене завтра", now) is Noa.Reply.Say)
    }

    @Test fun bookThenSendConfirmationFromTemplate() = runBlocking {
        app.repository.savePerson(Person(firstName = "Илья", lastName = "Рыков"),
            listOf(ContactItem(type = "PHONE", value = "+380731018582")), emptyList(), emptyList())
        val noa = Noa(app)
        val seq = com.kartoteka.app.assistant.NoaParser.parse(
            "возьми контакт Илья Рыков и запиши его на завтра на 12:00 и сразу отправь ему об этом в вотсап", now,
        ) as com.kartoteka.app.assistant.NoaIntent.Sequence
        noa.handleIntent(seq.steps[0], now)
        val confirm = noa.handleIntent(seq.steps[1], now) as Noa.Reply.Confirm
        confirm.onYes()
        noa.handleIntent(seq.steps[2], now)
        val m = com.kartoteka.app.assistant.NoaActions.pendingMessage!!
        assertTrue("text=${m.text}", m.text.contains("12:00") && m.text.contains("Илья"))
        com.kartoteka.app.assistant.NoaActions.pendingMessage = null
    }
}
