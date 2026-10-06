package com.kartoteka.app.screens

import android.provider.AlarmClock
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import com.kartoteka.app.assistant.Noa
import com.kartoteka.app.assistant.NoaIntent
import com.kartoteka.app.data.Appointment
import com.kartoteka.app.data.AppointmentLogic
import com.kartoteka.app.data.Person
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/** Раунд 2: напоминания, локальные инструменты, вопросы по картотеке, поправки в диалоге записи. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class NoaExecutor2Test {
    private val app get() = ApplicationProvider.getApplicationContext<TestApp>()
    private val now = LocalDateTime.of(2026, 10, 4, 10, 0)      // воскресенье
    private fun d(day: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 10, day, h, m)

    @org.junit.Before fun ru() { com.kartoteka.app.i18n.I18n.init(app, com.kartoteka.app.i18n.UiLang.RU) }

    private suspend fun person(first: String, last: String = "", nick: String = "", day: Int? = null, month: Int? = null, lastContact: Long? = null) =
        app.repository.savePerson(Person(firstName = first, lastName = last, nickname = nick, birthDay = day, birthMonth = month, lastContactAt = lastContact),
            emptyList(), emptyList(), emptyList())

    private suspend fun booked(personId: Long, at: LocalDateTime, minutes: Int = 60) {
        app.repository.saveAppointment(Appointment(personId = personId, start = AppointmentLogic.millis(at), durationMin = minutes), emptyList(), emptyList())
    }

    /** Картотека: Анна и Олег; сегодня 14:00 и 16:00, завтра 10:00 и 11:00–13:00, в пятницу 15:00. */
    private suspend fun seedDay() {
        val anna = person("Анна", nick = "Аня")
        val oleg = person("Олег", day = 7, month = 10)
        booked(anna, d(4, 14)); booked(oleg, d(4, 16))
        booked(anna, d(5, 10)); booked(oleg, d(5, 11), 120)
        booked(anna, d(9, 15))
    }

    private fun say(r: Noa.Reply): String = when (r) {
        is Noa.Reply.Say -> r.text; is Noa.Reply.Say2Open -> r.text; is Noa.Reply.Do -> r.text; is Noa.Reply.Confirm -> r.text
        is Noa.Reply.Choose -> r.text; is Noa.Reply.Navigate -> r.text
    }

    // ---- напоминания ----

    @Test fun reminderWithinADayIsAnAlarmWithLabel() = runBlocking {
        val r = Noa(app).handle("напомни мне завтра в 9 позвонить Ане", now)
        assertTrue("reply=$r", r is Noa.Reply.Do)
        assertTrue(say(r), say(r).contains("будильник") && say(r).contains("Позвонить Ане") && say(r).contains("09:00"))
        (r as Noa.Reply.Do).effect(app)
        val i = shadowOf(app).nextStartedActivity
        assertEquals(AlarmClock.ACTION_SET_ALARM, i.action)
        assertEquals("Позвонить Ане", i.getStringExtra(AlarmClock.EXTRA_MESSAGE))
        assertEquals(9, i.getIntExtra(AlarmClock.EXTRA_HOUR, -1)); assertEquals(0, i.getIntExtra(AlarmClock.EXTRA_MINUTES, -1))
    }

    @Test fun reminderInAnHourIsAnAlarm() = runBlocking {
        val r = Noa(app).handle("нагадай через годину купити хліб", now) as Noa.Reply.Do
        r.effect(app)
        val i = shadowOf(app).nextStartedActivity
        assertEquals(AlarmClock.ACTION_SET_ALARM, i.action)
        assertEquals("Купити хліб", i.getStringExtra(AlarmClock.EXTRA_MESSAGE)); assertEquals(11, i.getIntExtra(AlarmClock.EXTRA_HOUR, -1))
    }

    @Test fun farReminderOpensCalendarForm() = runBlocking {
        val r = Noa(app).handle("напомни в пятницу в 15:00 забрать посылку", now)
        assertTrue("reply=$r", r is Noa.Reply.Do && r.text.contains("календар") && r.text.contains("Забрать посылку"))
        (r as Noa.Reply.Do).effect(app)
        val i = shadowOf(app).nextStartedActivity
        assertEquals(android.content.Intent.ACTION_INSERT, i.action)
        assertEquals(CalendarContract.Events.CONTENT_URI, i.data)
        assertEquals("Забрать посылку", i.getStringExtra(CalendarContract.Events.TITLE))
        assertEquals(AppointmentLogic.millis(d(9, 15)), i.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, 0))
    }

    @Test fun reminderWithoutTimeOrTextAsksBack() = runBlocking {
        val noa = Noa(app)
        val noTime = noa.handle("напомни мне позвонить Ане", now)
        assertTrue("reply=$noTime", noTime is Noa.Reply.Say && noTime.text.contains("Когда напомнить"))
        val noText = noa.handle("напомни завтра в 10", now)
        assertTrue("reply=$noText", noText is Noa.Reply.Say && noText.text.contains("О чём напомнить"))
    }

    // ---- инструменты ----

    @Test fun timeDateWeekdayAnsweredLocally() = runBlocking {
        val noa = Noa(app)
        assertEquals("Сейчас 10:00.", say(noa.handle("который час", now)))
        val date = say(noa.handle("какое сегодня число", now))
        assertTrue(date, date.contains("4 октября") && date.contains("воскресенье"))
        assertEquals("Сегодня воскресенье.", say(noa.handle("какой сегодня день недели", now)))
        val fri = say(noa.handle("какой день недели будет 15 октября", now))
        assertTrue(fri, fri.contains("15 октября") && fri.contains("четверг"))
        val tomorrow = say(noa.handle("какое число завтра", now))
        assertTrue(tomorrow, tomorrow.startsWith("Завтра") && tomorrow.contains("5 октября") && tomorrow.contains("понедельник"))
    }

    @Test fun calculatorAndUnits() = runBlocking {
        val noa = Noa(app)
        assertEquals("Получится 360.", say(noa.handle("посчитай 15 процентов от 2400", now)))
        assertEquals("Получится 84.", say(noa.handle("сколько будет 12 умножить на 7", now)))
        assertEquals("Получится 12.", say(noa.handle("корень из 144", now)))
        assertEquals("Получится 2,5.", say(noa.handle("сколько будет пять разделить на два", now)))
        assertTrue(say(noa.handle("сколько будет 5 разделить на 0", now)).contains("ноль"))
        val miles = say(noa.handle("сколько миль в 10 километрах", now))
        assertTrue(miles, miles.contains("6,21") && miles.contains("км") && miles.contains("миль"))
        val fahr = say(noa.handle("переведи 100 градусов цельсия в фаренгейты", now))
        assertTrue(fahr, fahr.contains("212") && fahr.contains("°F"))
        val money = say(noa.handle("сколько долларов в 100 гривнах", now))
        assertTrue(money, money.contains("Курсы валют"))
    }

    // ---- картотека ----

    @Test fun nextAppointmentAndCounts() = runBlocking {
        seedDay()
        val noa = Noa(app)
        val next = say(noa.handle("кто следующий", now))
        assertTrue(next, next.contains("Анна") && next.contains("14:00") && next.contains("сегодня"))
        val oleg = say(noa.handle("когда у меня следующая запись с Олегом", now))
        assertTrue(oleg, oleg.contains("Олег") && oleg.contains("16:00"))
        assertTrue(say(noa.handle("сколько записей сегодня", now)).contains("2"))
        assertTrue(say(noa.handle("сколько записей завтра", now)).contains("2"))
        val week = say(noa.handle("сколько записей на следующей неделе", now))
        assertTrue(week, week.contains("3") && week.contains("следующей неделе"))
        val thisWeek = say(noa.handle("сколько записей на этой неделе", now))
        assertTrue(thisWeek, thisWeek.contains("2") && thisWeek.contains("этой неделе"))
        assertTrue(say(noa.handle("сколько записей у Анны на следующей неделе", now)).contains("2"))
    }

    @Test fun noAppointmentsAreSaidPlainly() = runBlocking {
        person("Анна")
        val noa = Noa(app)
        assertEquals("Предстоящих записей нет.", say(noa.handle("кто следующий", now)))
        assertTrue(say(noa.handle("сколько записей сегодня", now)).contains("нет"))
        val free = say(noa.handle("когда у меня окно завтра", now))
        assertTrue(free, free.contains("08:00–21:00"))
    }

    @Test fun freeSlotsFromTheDaysAppointments() = runBlocking {
        seedDay()
        val noa = Noa(app)
        val tomorrow = say(noa.handle("когда у меня окно завтра", now))
        assertTrue(tomorrow, tomorrow.contains("08:00–10:00") && tomorrow.contains("13:00–21:00") && !tomorrow.contains("10:00–11:00"))
        // сегодня — от текущего момента: 10:00–14:00, 15:00–16:00, 17:00–21:00
        val today = say(noa.handle("есть ли окно сегодня", now))
        assertTrue(today, today.contains("10:00–14:00") && today.contains("15:00–16:00") && today.contains("17:00–21:00"))
    }

    @Test fun whoIsBookedAtATime() = runBlocking {
        seedDay()
        val noa = Noa(app)
        val at14 = say(noa.handle("кто записан на 14:00", now))
        assertTrue(at14, at14.contains("Анна"))
        val tom = say(noa.handle("кто записан завтра в 12:00", now))
        assertTrue(tom, tom.contains("Олег"))
        val free = say(noa.handle("кто на завтра в 15:00", now))
        assertTrue(free, free.contains("никого"))
    }

    @Test fun lastContactWithAPerson() = runBlocking {
        person("Мария", "Фролова", lastContact = AppointmentLogic.millis(now.minusDays(3)))
        person("Анна")
        val noa = Noa(app)
        val r = say(noa.handle("когда я последний раз говорил с Марией", now))
        assertTrue(r, r.contains("3 дня назад") && r.contains("1 октября"))
        val none = say(noa.handle("когда я последний раз говорил с Анной", now))
        assertTrue(none, none.contains("контактов пока нет"))
    }

    @Test fun birthdaysSoonAndThisWeek() = runBlocking {
        seedDay()
        val noa = Noa(app)
        val soon = say(noa.handle("у кого скоро день рождения", now))
        assertTrue(soon, soon.contains("Олег") && soon.contains("7 октября"))
        assertTrue(say(noa.handle("у кого день рождения на этой неделе", now)).contains("нет"))
        val next = say(noa.handle("у кого день рождения на следующей неделе", now))
        assertTrue(next, next.contains("Олег"))
        assertTrue(say(noa.handle("у кого день рождения завтра", now)).contains("нет"))
    }

    // ---- диалог записи ----

    @Test fun correctionToPendingConfirmation() = runBlocking {
        person("Мария", "Фролова")
        val noa = Noa(app)
        val first = noa.handle("запиши Марию Фролову на завтра в 15:00", now)
        assertTrue("reply=$first", first is Noa.Reply.Confirm && first.text.contains("15:00"))
        // «нет, на пятницу» — меняется день, время остаётся
        val fri = noa.continueBooking("нет, на пятницу", now) as NoaIntent.CreateAppointment
        assertEquals(d(9, 15), fri.dateTime)
        val second = noa.handleIntent(fri, now)
        assertTrue("reply=$second", second is Noa.Reply.Confirm && second.text.contains("15:00") && second.text.contains("9 октября"))
        // «не в три, а в четыре» — меняется время (16:00, а не 4 утра)
        val four = noa.continueBooking("не в три а в четыре", now) as NoaIntent.CreateAppointment
        assertEquals(d(9, 16), four.dateTime)
        val third = noa.handleIntent(four, now)
        assertTrue("reply=$third", third is Noa.Reply.Confirm && third.text.contains("16:00"))
        // «да» — это подтверждение: поправкой не считается
        assertNull(noa.continueBooking("да", now))
        // «нет» без поправки тоже оставляем экрану (он отменит)
        assertNull(noa.continueBooking("нет", now))
        // «отмена» сбрасывает вопрос
        assertEquals(NoaIntent.Dismiss, noa.continueBooking("отмена", now))
        assertNull(noa.continueBooking("не в пять а в шесть", now))
    }

    @Test fun correctionCanChangePersonAndService() = runBlocking {
        person("Мария", "Фролова"); person("Анна", nick = "Аня")
        val noa = Noa(app)
        noa.handle("запиши Марию на завтра в 12", now)
        val fixed = noa.continueBooking("нет, Аню", now) as NoaIntent.CreateAppointment
        assertEquals("аню", fixed.personQuery); assertEquals(d(5, 12), fixed.dateTime)
        val reply = noa.handleIntent(fixed, now)
        assertTrue("reply=$reply", reply is Noa.Reply.Confirm && reply.text.contains("Анна"))
        // «лучше в четыре» тоже поправка
        val better = noa.continueBooking("лучше в четыре", now) as NoaIntent.CreateAppointment
        assertEquals(d(5, 16), better.dateTime)
    }

    @Test fun cancelWordsDropThePendingQuestion() = runBlocking {
        person("Анна")
        for (word in listOf("отмена", "забудь", "стоп", "не надо", "нет", "хватит", "отмени")) {
            val noa = Noa(app)
            assertTrue(noa.handle("запиши", now) is Noa.Reply.Choose)
            assertNotNull(noa.pendingBooking)
            assertEquals("«$word»", NoaIntent.Dismiss, noa.continueBooking(word, now))
            assertNull("«$word»", noa.pendingBooking)
            assertNull("после «$word» продолжать нечего", noa.continueBooking("Анну на завтра", now))
        }
        val noa = Noa(app)
        noa.handle("запиши", now)
        val say = noa.handleIntent(NoaIntent.Dismiss, now)
        assertTrue(say is Noa.Reply.Say && say.text.contains("отменила"))
        assertNull(noa.pendingBooking)
    }

    @Test fun answersToQuestionsStillWork() = runBlocking {
        person("Мария", "Фролова")
        val noa = Noa(app)
        assertTrue(noa.handle("запиши Марию", now) is Noa.Reply.Choose)       // «На какое число и время?»
        val merged = noa.continueBooking("завтра в 3", now) as NoaIntent.CreateAppointment
        assertEquals(d(5, 15), merged.dateTime)
        // «повтори» не сбрасывает вопрос
        val noa2 = Noa(app)
        noa2.handle("запиши Марию", now)
        assertEquals(NoaIntent.Repeat, noa2.continueBooking("повтори", now))
        assertNotNull(noa2.pendingBooking)
    }

    @Test fun repeatSaysTheLastReplyAgain() = runBlocking {
        val noa = Noa(app)
        val nothing = noa.handle("повтори", now)
        assertTrue("reply=$nothing", nothing is Noa.Reply.Say && nothing.text.contains("нечего"))
        val time = noa.handle("который час", now)
        assertEquals(say(time), say(noa.handle("повтори", now)))
        assertEquals(say(time), say(noa.handle("ещё раз пожалуйста", now)))
        // повтор действия — то же действие
        val timer = noa.handle("таймер на пять минут", now)
        assertTrue(noa.handle("повтори", now) is Noa.Reply.Do)
        assertEquals(say(timer), say(noa.lastReply!!))
    }
}
