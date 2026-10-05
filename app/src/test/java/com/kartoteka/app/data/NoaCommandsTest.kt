package com.kartoteka.app.data

import com.kartoteka.app.assistant.NoaIntent
import com.kartoteka.app.assistant.NoaParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** Новые команды Ноа: цепочки, маршрут, контакты, план на день, вопросы о человеке (рус./укр.). */
class NoaCommandsTest {
    private val now = LocalDateTime.of(2026, 10, 4, 10, 0)
    private fun p(s: String) = NoaParser.parse(s, now)

    @Test fun openProfileAndAddNoteIsAChain() {
        val seq = p("зайди в профиль Ани и добавь заметку купила новый телефон") as NoaIntent.Sequence
        assertEquals(2, seq.steps.size)
        val open = seq.steps[0] as NoaIntent.Open
        assertTrue(open.personQuery.contains("ани", true))
        val note = seq.steps[1] as NoaIntent.AddNote
        assertEquals("купила новый телефон", note.text)
        assertTrue("человек переходит во второй шаг", note.personQuery.contains("ани", true))
    }

    @Test fun ukrainianChainWithNote() {
        val seq = p("відкрий профіль Олега та додай нотатку повернув борг") as NoaIntent.Sequence
        val note = seq.steps.last() as NoaIntent.AddNote
        assertEquals("повернув борг", note.text)
        assertTrue(note.personQuery.contains("олега", true))
    }

    @Test fun noteWithColonAndWhat() {
        val a = p("добавь заметку Ане: любит латте") as NoaIntent.AddNote
        assertEquals("любит латте", a.text); assertTrue(a.personQuery.contains("ане", true))
        val b = p("запиши в хронику Олега что вернул долг") as NoaIntent.AddNote
        assertEquals("вернул долг", b.text); assertTrue(b.personQuery.contains("олега", true))
    }

    @Test fun openInstagramOfContact() {
        val a = p("нажми на инстаграм Ани в контактах") as NoaIntent.OpenContact
        assertEquals(ContactType.INSTAGRAM, a.type); assertEquals("ани", a.personQuery.lowercase())
        val b = p("відкрий інстаграм Олега") as NoaIntent.OpenContact
        assertEquals(ContactType.INSTAGRAM, b.type); assertEquals("олега", b.personQuery.lowercase())
    }

    @Test fun routeToPerson() {
        val a = p("проложи маршрут к Ане") as NoaIntent.Route
        assertEquals("ане", a.personQuery.lowercase()); assertEquals(null, a.kind)
        val b = p("проклади шлях до мами на роботу") as NoaIntent.Route
        assertEquals("мами", b.personQuery.lowercase()); assertEquals(PlaceKind.WORK, b.kind)
    }

    @Test fun agendaForToday() {
        assertEquals(now.toLocalDate(), (p("що в мене сьогодні") as NoaIntent.Agenda).date)
        assertEquals(now.toLocalDate().plusDays(1), (p("какие записи на завтра") as NoaIntent.Agenda).date)
    }

    @Test fun questionsAboutPerson() {
        val bd = p("коли день народження в Іллі") as NoaIntent.PersonInfo
        assertEquals(NoaIntent.Topic.BIRTHDAY, bd.topic); assertEquals("іллі", bd.personQuery.lowercase())
        assertEquals(NoaIntent.Topic.PHONE, (p("какой номер у Олега") as NoaIntent.PersonInfo).topic)
        assertEquals(NoaIntent.Topic.ADDRESS, (p("де живе мама") as NoaIntent.PersonInfo).topic)
        assertEquals(NoaIntent.Topic.SUMMARY, (p("що я знаю про Анну") as NoaIntent.PersonInfo).topic)
    }

    @Test fun pronounLeavesPersonForLastOne() {
        val seq = p("позвони маме и потом напиши ей в телеграм") as NoaIntent.Sequence
        assertTrue(seq.steps[1] is NoaIntent.Message)
        assertTrue(NoaParser.personOf(seq.steps[1]).isNotBlank())
    }

    @Test fun singleCommandsStillWork() {
        assertTrue(p("позвони маме") is NoaIntent.Call)
        assertTrue(p("запиши Анну на завтра в 12:00") is NoaIntent.CreateAppointment)
        assertTrue(p("добавь Олега в избранное") is NoaIntent.Favorite)
    }

    @Test fun userPhraseBookAndConfirmInWhatsApp() {
        val seq = p("а возьми контакт Илья рыков и запиши его на завтра на 12:00 и также сразу Отправь ему об этом в вотсап") as NoaIntent.Sequence
        assertEquals(3, seq.steps.size)
        assertTrue(seq.steps[0] is NoaIntent.Select)
        val appt = seq.steps[1] as NoaIntent.CreateAppointment
        assertEquals("илья рыков", appt.personQuery.lowercase())
        assertEquals(12, appt.dateTime!!.hour); assertEquals(now.toLocalDate().plusDays(1), appt.dateTime!!.toLocalDate())
        val msg = seq.steps[2] as NoaIntent.Message
        assertTrue(msg.aboutAppointment); assertEquals(NoaIntent.Channel.WHATSAPP, msg.channel)
        assertEquals("илья рыков", msg.personQuery.lowercase())
    }

    @Test fun recognizerPunctuationAndUkrainianForms() {
        fun appt(t: String) = p(t) as NoaIntent.CreateAppointment
        listOf(
            "Запиши, Илья Рыков, на завтра на 12:00.", "Запиши. Илья Рыков на завтра на 12:00",
            "запиши Илья Рыков на завтра на 12", "запиши Илью Рыкова на завтра на 12 00",
            "Запиши Ілля Риков на завтра на дванадцяту",
        ).forEach { t ->
            val a = appt(t)
            assertTrue("$t -> ${a.personQuery}", a.personQuery.lowercase().replace(Regex("[^а-яіїєё ]"), "").trim().split(" ").size == 2)
            assertEquals(t, 12, a.dateTime!!.hour); assertEquals(t, now.toLocalDate().plusDays(1), a.dateTime!!.toLocalDate())
        }
        assertEquals("іллю рикова", appt("Запишіть Іллю Рикова на завтра на 12:00").personQuery)
        assertEquals(15, appt("запиши Аню в пятницу на 3").dateTime!!.hour)
        assertEquals(12, appt("запиши Машу на 12 октября в 15:00").dateTime!!.dayOfMonth)
    }

    @Test fun deleteCancelAndMoveAreNotNewBookings() {
        val del = p("удали запись ильи рыкова завтра") as NoaIntent.CancelAppointment
        assertEquals("ильи рыкова", del.personQuery); assertEquals(now.toLocalDate().plusDays(1), del.date); assertTrue(del.delete)
        val cancel = p("скасуй запис Ольги") as NoaIntent.CancelAppointment
        assertEquals("ольги", cancel.personQuery); assertEquals(false, cancel.delete)
        val move = p("перенеси Аню на пятницу в 15:00") as NoaIntent.MoveAppointment
        assertEquals("аню", move.personQuery); assertEquals(15, move.dateTime!!.hour); assertTrue(move.hadDate && move.hadTime)
    }

    @Test fun openProfileThenRouteViaWaze() {
        val seq = p("Открой профиль Илья рыков и проложи мне на карте через Waze к нему маршрут") as NoaIntent.Sequence
        assertEquals("илья рыков", (seq.steps[0] as NoaIntent.Open).personQuery)
        val route = seq.steps[1] as NoaIntent.Route
        assertEquals("илья рыков", route.personQuery); assertEquals("waze", route.app)
    }

    @Test fun phoneActionsAndDataTransfer() {
        val notes = p("Возьми данные заметок о Илье рыкове и перенеси их в блокнот телефона") as NoaIntent.ShareData
        assertEquals("илье рыкове", notes.personQuery); assertEquals(NoaIntent.Data.NOTES, notes.data); assertEquals("notes", notes.target)
        val phone = p("Скопируй номер Анны и вставь его в гугл") as NoaIntent.ShareData
        assertEquals("анны", phone.personQuery); assertEquals(NoaIntent.Data.PHONE, phone.data); assertEquals("google", phone.target)
        val misheard = p("Скопируйте номер Анны и всей его в гугл") as NoaIntent.ShareData
        assertEquals("google", misheard.target)
        val clip = p("скопируй номер Анны") as NoaIntent.ShareData
        assertEquals("clipboard", clip.target)
        val tg = p("скинь адрес Оли в телеграм") as NoaIntent.ShareData
        assertEquals("app:телеграм", tg.target); assertEquals(NoaIntent.Data.ADDRESS, tg.data)
        assertEquals("ютуб", (p("открой ютуб") as NoaIntent.LaunchApp).name)
        assertEquals("spotify", (p("запусти spotify") as NoaIntent.LaunchApp).name)
        assertEquals(NoaIntent.Alarm(7, 30), p("поставь будильник на 7:30"))
        assertEquals(NoaIntent.Timer(300), p("таймер на 5 минут"))
        assertEquals(NoaIntent.Flashlight(false), p("выключи фонарик"))
        assertEquals("рецепт борща", (p("загугли рецепт борща") as NoaIntent.WebSearch).query)
        // старые команды не задеты
        assertTrue(p("перенеси Аню на пятницу в 15:00") is NoaIntent.MoveAppointment)
        assertTrue(p("добавь заметку Ане: скопировать фото") !is NoaIntent.ShareData)
        assertTrue(p("открой Аню") is NoaIntent.Open)
    }
}
