package com.kartoteka.app.screens

import com.kartoteka.app.assistant.NoaIntent
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.kartoteka.app.assistant.NoaInterpreter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** Проверяем «перевод ответа модели (JSON) → команда» без самой модели. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class NoaInterpreterTest {
    private val now = LocalDateTime.of(2026, 10, 4, 10, 0)
    private val ip = NoaInterpreter(null)

    private fun j(raw: String, user: String) = ip.fromJson(raw, user, now)

    @Test fun appointmentFromModelJsonUsesOurDateParser() {
        val r = j("""{"action":"create_appointment","person":"Маша Фролова","service":"тату","reply":"Записываю Машу"}""",
            "запиши Машу Фролову на тату на 12-е в 12:00")!!
        val a = r.intent as NoaIntent.CreateAppointment
        assertEquals("Маша Фролова", a.personQuery)
        assertEquals("тату", a.serviceQuery)
        assertEquals(12, a.dateTime!!.dayOfMonth); assertEquals(12, a.dateTime!!.hour)
        assertEquals("Записываю Машу", r.reply)
    }

    @Test fun callMessageNoteFindOpen() {
        assertTrue((j("""{"action":"call","person":"мама"}""", "позвони маме")!!.intent) is NoaIntent.Call)
        val m = j("""{"action":"message","person":"Петя","channel":"telegram","text":"привет"}""", "напиши Пете привет в телеграм")!!.intent as NoaIntent.Message
        assertEquals(NoaIntent.Channel.TELEGRAM, m.channel); assertEquals("привет", m.text)
        val note = j("""{"action":"add_note","person":"Аня","text":"любит кофе"}""", "добавь Ане заметку любит кофе")!!.intent as NoaIntent.AddNote
        assertEquals("любит кофе", note.text)
        assertTrue(j("""{"action":"find","query":"клиенты"}""", "x")!!.intent is NoaIntent.Find)
        assertTrue(j("""{"action":"open_person","person":"Олег"}""", "открой Олега")!!.intent is NoaIntent.Open)
        assertEquals(NoaIntent.Section.BROADCAST, (j("""{"action":"open_screen","section":"broadcast"}""", "x")!!.intent as NoaIntent.OpenScreen).section)
        assertTrue(j("""{"action":"lock"}""", "x")!!.intent is NoaIntent.Lock)
    }

    @Test fun chatReturnsReplyWithoutIntent() {
        val r = j("""{"action":"chat","reply":"Привет! Чем помочь?"}""", "привет")!!
        assertNull(r.intent)
        assertEquals("Привет! Чем помочь?", r.reply)
    }

    @Test fun modelAddsExtraTextAroundJson() {
        val r = j("Вот ответ: {\"action\":\"call\",\"person\":\"мама\"} — готово", "позвони маме")
        assertTrue(r!!.intent is NoaIntent.Call)
    }

    @Test fun garbageReturnsNull() {
        assertNull(j("это не json вообще", "x"))
    }
}
