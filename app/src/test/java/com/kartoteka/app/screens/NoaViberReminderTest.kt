package com.kartoteka.app.screens

import androidx.test.core.app.ApplicationProvider
import com.kartoteka.app.assistant.Noa
import com.kartoteka.app.assistant.NoaIntent
import com.kartoteka.app.assistant.NoaParser
import com.kartoteka.app.data.Appointment
import com.kartoteka.app.data.AppointmentLogic
import com.kartoteka.app.data.ContactItem
import com.kartoteka.app.data.Person
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/** Голосовые команды из отчёта 3.7.0: перенос записи по её времени, напоминание в Viber/WhatsApp. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class NoaViberReminderTest {
    private val app get() = ApplicationProvider.getApplicationContext<TestApp>()
    private val now = LocalDateTime.of(2026, 10, 6, 23, 40)

    @org.junit.Before fun ru() { com.kartoteka.app.i18n.I18n.init(app, com.kartoteka.app.i18n.UiLang.RU) }

    @Test fun parsesMoveBySlotThenReminderInViber() {
        val r = NoaParser.parse("завтра запись на 13:00 измени время на 14:00 и отправив напоминание в Viber", now) as NoaIntent.Sequence
        val move = r.steps[0] as NoaIntent.MoveAppointment
        assertEquals("", move.personQuery)
        assertEquals(LocalDateTime.of(2026, 10, 7, 13, 0), move.from)
        assertTrue(move.fromHadDate && move.fromHadTime)
        assertEquals(14, move.dateTime!!.hour)
        assertTrue(move.hadTime && !move.hadDate)
        val msg = r.steps[1] as NoaIntent.Message
        assertEquals(NoaIntent.Channel.VIBER, msg.channel)
        assertTrue(msg.aboutAppointment && msg.reminder)
    }

    @Test fun parsesReminderToNamedPerson() {
        val m = NoaParser.parse("Отправь напоминание илье рыкову в WhatsApp", now) as NoaIntent.Message
        assertEquals("илье рыкову", m.personQuery)
        assertEquals(NoaIntent.Channel.WHATSAPP, m.channel)
        assertTrue(m.aboutAppointment && m.reminder)
        val c = NoaParser.parse("отправь Илье подтверждение записи", now) as NoaIntent.Message
        assertTrue(c.aboutAppointment && !c.reminder)
        val uk = NoaParser.parse("надішли Олі нагадування у вайбер", now) as NoaIntent.Message
        assertEquals(NoaIntent.Channel.VIBER, uk.channel)
        assertTrue(uk.reminder)
    }

    @Test fun viberIsAMessageChannel() {
        val m = NoaParser.parse("напиши Илье в вайбер что опаздываю", now) as NoaIntent.Message
        assertEquals(NoaIntent.Channel.VIBER, m.channel)
        assertEquals("Опаздываю", m.text)
    }

    @Test fun reminderToMeStaysAReminder() {
        assertTrue(NoaParser.parse("напомни мне завтра отправить напоминание Ане", now) is NoaIntent.Remind)
    }

    @Test fun movesAppointmentFoundByTimeAndRemindsItsPerson() = runBlocking {
        val id = app.repository.savePerson(Person(firstName = "Илья", lastName = "Рыков"),
            listOf(ContactItem(type = "PHONE", value = "+380731018582")), emptyList(), emptyList())
        app.repository.saveAppointment(
            Appointment(personId = id, start = AppointmentLogic.millis(LocalDateTime.of(2026, 10, 7, 13, 0)), durationMin = 60),
            emptyList(), emptyList(),
        )
        val noa = Noa(app)
        val confirm = noa.handle("завтра запись на 13:00 измени время на 14:00 и отправь напоминание в Viber", now)
        assertTrue("reply=$confirm", confirm is Noa.Reply.Confirm)
        (confirm as Noa.Reply.Confirm).onYes()
        val start = AppointmentLogic.zoned(app.repository.appointmentsBetween(0, Long.MAX_VALUE).single().appointment.start)
        assertEquals(LocalDateTime.of(2026, 10, 7, 14, 0), start)
        // Второй шаг цепочки: человек — из найденной записи, текст — шаблон напоминания.
        val send = noa.handle("отправь ему напоминание в вайбер", now)
        assertTrue("reply=$send", send is Noa.Reply.Do)
        assertTrue((send as Noa.Reply.Do).text.contains("Viber"))
    }

    @Test fun slotWithoutAppointmentIsReported() = runBlocking {
        val r = Noa(app).handle("завтра запись на 13:00 измени время на 14:00", now)
        assertTrue("reply=$r", r is Noa.Reply.Say && r.text.contains("Не нашла запись"))
    }
}
