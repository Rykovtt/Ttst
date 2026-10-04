package com.kartoteka.app.data

import com.kartoteka.app.assistant.NoaDateTime
import com.kartoteka.app.assistant.NoaIntent
import com.kartoteka.app.assistant.NoaParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class NoaParserTest {
    private val now = LocalDateTime.of(2026, 10, 4, 10, 0) // воскресенье

    @Test fun createAppointmentFullCommand() {
        val i = NoaParser.parse("запиши Машу Фролову на тату-сеанс на 12-е в 12:00", now) as NoaIntent.CreateAppointment
        assertTrue(i.personQuery.contains("машу", true) && i.personQuery.contains("фролову", true))
        assertEquals("тату", i.serviceQuery)
        assertNotNull(i.dateTime)
        assertEquals(12, i.dateTime!!.dayOfMonth)
        assertEquals(12, i.dateTime!!.hour)
        assertTrue(i.hadTime)
    }

    @Test fun createTomorrowConsultation() {
        val i = NoaParser.parse("назначь Анну на консультацию завтра в 14:30", now) as NoaIntent.CreateAppointment
        assertEquals("консультац", i.serviceQuery)
        assertEquals(now.toLocalDate().plusDays(1), i.dateTime!!.toLocalDate())
        assertEquals(14, i.dateTime!!.hour); assertEquals(30, i.dateTime!!.minute)
        assertTrue(i.personQuery.contains("анну", true))
    }

    @Test fun callAndMessageIntents() {
        assertEquals("маме", (NoaParser.parse("позвони маме", now) as NoaIntent.Call).personQuery.lowercase())
        val m = NoaParser.parse("напиши Пете в вотсап «буду через час»", now) as NoaIntent.Message
        assertEquals(NoaIntent.Channel.WHATSAPP, m.channel)
        assertEquals("буду через час", m.text)
        assertTrue(m.personQuery.contains("пете", true))
        val tg = NoaParser.parse("отправь сообщение Олегу в телеграм", now) as NoaIntent.Message
        assertEquals(NoaIntent.Channel.TELEGRAM, tg.channel)
    }

    @Test fun noteOpenFindLockBackup() {
        val note = NoaParser.parse("добавь заметку Ане «переехала во Львов»", now) as NoaIntent.AddNote
        assertEquals("переехала во Львов", note.text)
        assertTrue(note.personQuery.contains("ане", true))
        assertTrue(NoaParser.parse("открой карточку Дмитрия", now) is NoaIntent.Open)
        assertEquals("клиентов киева", (NoaParser.parse("найди клиентов киева", now) as NoaIntent.Find).query.lowercase().replace("  ", " "))
        assertTrue(NoaParser.parse("заблокируй приложение", now) is NoaIntent.Lock)
        assertTrue(NoaParser.parse("сделай резервную копию", now) is NoaIntent.Backup)
        assertTrue(NoaParser.parse("бла бла бла", now) is NoaIntent.Unknown)
    }

    @Test fun deviceCommandsFromScreenshot() {
        assertTrue(NoaParser.parse("Открой контакт Илья рыков", now) is NoaIntent.Open)
        assertTrue(NoaParser.parse("набери Илья рыков", now) is NoaIntent.Call)
        assertTrue(NoaParser.parse("заблокируй приложение", now) is NoaIntent.Lock)
        // пустой ввод не падает
        assertTrue(NoaParser.parse("", now) is NoaIntent.Unknown)
    }

    @Test fun englishAndUkrainian() {
        val en = NoaParser.parse("call Anna", now) as NoaIntent.Call
        assertEquals("anna", en.personQuery.lowercase())
        val uk = NoaParser.parse("запиши Марію на завтра о 15:00", now) as NoaIntent.CreateAppointment
        assertEquals(now.toLocalDate().plusDays(1), uk.dateTime!!.toLocalDate())
        assertEquals(15, uk.dateTime!!.hour)
    }

    // ---- даты и время ----

    @Test fun dayWithoutMonthPicksNearestFuture() {
        // сегодня 4-е; «на 12» → 12-е этого месяца
        assertEquals(LocalDateTime.of(2026, 10, 12, 9, 0), NoaDateTime.parse("на 12-е", now)!!.dateTime)
        // «на 2» уже прошло → следующий месяц
        assertEquals(2, NoaDateTime.parse("на 2 число", now)!!.dateTime.dayOfMonth)
        assertEquals(11, NoaDateTime.parse("на 2 число", now)!!.dateTime.monthValue)
    }

    @Test fun weekdayResolvesToNext() {
        // воскресенье 4-е → «в субботу» = 10-е
        assertEquals(LocalDateTime.of(2026, 10, 10, 9, 0), NoaDateTime.parse("в субботу", now)!!.dateTime)
    }

    @Test fun timeOnlyTodayOrRolloverTomorrow() {
        assertEquals(LocalDateTime.of(2026, 10, 4, 18, 0), NoaDateTime.parse("в 18:00", now)!!.dateTime)
        // 8:00 уже прошло (сейчас 10:00) → завтра
        assertEquals(LocalDateTime.of(2026, 10, 5, 8, 0), NoaDateTime.parse("в 8 утра", now)!!.dateTime)
    }

    @Test fun monthNameAndPmAm() {
        assertEquals(LocalDateTime.of(2026, 12, 31, 23, 0), NoaDateTime.parse("31 декабря в 23:00", now)!!.dateTime)
        assertEquals(17, NoaDateTime.parse("at 5 pm", now)!!.dateTime.hour)
    }

    @Test fun noDateNoTimeIsNull() {
        assertNull(NoaDateTime.parse("просто текст без даты", now))
    }
}
