package com.kartoteka.app.data

import com.kartoteka.app.assistant.NoaIntent
import com.kartoteka.app.assistant.NoaInterpreter
import com.kartoteka.app.assistant.NoaMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** Ответы модели (хорошие, в обёртке ```json, кривые, одиночные, просто разговор) → команды Ноа. Без Android. */
class NoaInterpreterTest {
    private val now = LocalDateTime.of(2026, 10, 4, 10, 0)
    private val ip = NoaInterpreter(null)
    private fun j(raw: String, user: String = "x") = ip.fromJson(raw, user, now)

    @Test fun chainMessageThenGoHome() {
        val r = j("""{"actions":[{"action":"message","person":"Аня","channel":"telegram","text":"опоздаю на 10 минут"},{"action":"go_home"}],"reply":"Пишу Ане"}""",
            "напиши Ане в телеграм что опоздаю на 10 минут и сверни")!!
        val seq = r.intent as NoaIntent.Sequence
        val m = seq.steps[0] as NoaIntent.Message
        assertEquals("Аня", m.personQuery); assertEquals(NoaIntent.Channel.TELEGRAM, m.channel); assertEquals("опоздаю на 10 минут", m.text)
        assertEquals(NoaIntent.GoHome, seq.steps[1])
        assertEquals("Пишу Ане", r.reply)
    }

    @Test fun fencedPlaylistShuffled() {
        val raw = "```json\n{\"actions\":[{\"action\":\"play_music\",\"query\":\"для бігу\",\"playlist\":true,\"shuffle\":true}],\"reply\":\"Вмикаю\"}\n```"
        val p = j(raw)!!.intent as NoaIntent.Play
        assertEquals("для бігу", p.query); assertTrue(p.playlist); assertTrue(p.shuffle); assertFalse(p.video)
    }

    @Test fun mediaPauseAsBareSingleObject() {
        assertEquals(NoaIntent.Media(NoaMedia.Control.PAUSE), j("""{"action":"media","control":"pause"}""")!!.intent)
        assertEquals(NoaIntent.Media(NoaMedia.Control.QUIETER), j("""{"actions":[{"action":"media","control":"volume_down"}]}""")!!.intent)
    }

    @Test fun routeToFreeAddressInWaze() {
        val r = j("""{"actions":[{"action":"route","place":"Крещатик 22","app":"Waze"}],"reply":"Прокладываю"}""")!!.intent as NoaIntent.Route
        assertEquals("Крещатик 22", r.place); assertEquals("waze", r.app); assertNull(r.kind); assertEquals("", r.personQuery)
    }

    @Test fun routeToPersonWorkOldAndNewFormat() {
        val old = j("""{"actions":[{"action":"route","person":"мама","place":"work"}]}""")!!.intent as NoaIntent.Route
        assertEquals(PlaceKind.WORK, old.kind); assertEquals("", old.place)
        val new = j("""{"actions":[{"action":"route","person":"мама","kind":"home","app":"yandex"}]}""")!!.intent as NoaIntent.Route
        assertEquals(PlaceKind.HOME, new.kind); assertEquals("yandex", new.app)
    }

    @Test fun replyAndReadMessages() {
        val r = j("""{"actions":[{"action":"reply","person":"Олег","text":"буду о сьомій"},{"action":"read_messages","wait":true}],"reply":"Відповідаю"}""", "відповідай Олегу буду о сьомій і прочитай")!!
        val seq = r.intent as NoaIntent.Sequence
        assertEquals(NoaIntent.Reply("Олег", "буду о сьомій"), seq.steps[0])
        // Человек переходит в следующий шаг.
        assertEquals(NoaIntent.ReadMessages("Олег", true), seq.steps[1])
    }

    @Test fun questionAnsweredFromDataHasNoIntent() {
        val r = j("""{"actions":[],"reply":"В этом месяце Аня была у вас дважды."}""")!!
        assertNull(r.intent); assertEquals("В этом месяце Аня была у вас дважды.", r.reply)
    }

    @Test fun chitChatWithTextAround() {
        val r = j("Конечно! Вот JSON:\n{\"actions\": [], \"reply\": \"Отлично! Чем помочь?\"}\nНадеюсь, это поможет.")!!
        assertNull(r.intent); assertEquals("Отлично! Чем помочь?", r.reply)
    }

    @Test fun trailingCommasAndSmartQuotes() {
        val raw = "{\u201Cactions\u201D:[{\u201Caction\u201D:\u201Ccall\u201D,\u201Cperson\u201D:\u201Cмама\u201D,},],\u201Creply\u201D:\u201CЗвоню\u201D,}"
        val r = j(raw, "позвони маме")!!
        assertEquals(NoaIntent.Call("мама"), r.intent); assertEquals("Звоню", r.reply)
    }

    @Test fun actionsAsObjectInsteadOfArray() {
        val a = j("""{"actions":{"action":"flashlight","on":false},"reply":""}""")!!.intent
        assertEquals(NoaIntent.Flashlight(false), a)
        val b = j("""{"actions":{"timer":{"minutes":"5"}}}""")!!.intent
        assertEquals(NoaIntent.Timer(300), b)
    }

    @Test fun unknownActionsAreSkipped() {
        val r = j("""{"actions":[{"action":"teleport","person":"Аня"},{"action":"launch_app","app":"youtube"}],"reply":"Запускаю"}""", "запусти ютуб")!!
        assertEquals(NoaIntent.LaunchApp("youtube"), r.intent)
        // Только незнакомое, но есть реплика — это разговор.
        val c = j("""{"actions":[{"action":"fly"}],"reply":"Не умею летать"}""")!!
        assertNull(c.intent); assertEquals("Не умею летать", c.reply)
    }

    @Test fun anySongOfArtistBecomesArtist() {
        val p = j("""{"actions":[{"action":"play_music","query":"любую песню Земфиры"}]}""")!!.intent as NoaIntent.Play
        assertEquals("Земфиры", p.query); assertTrue(p.artist)
        val v = j("""{"actions":[{"action":"play_music","query":"котики","video":"true","app":"youtube"}]}""")!!.intent as NoaIntent.Play
        assertTrue(v.video); assertEquals("youtube", v.app)
    }

    @Test fun truncatedOutputKeepsFinishedActions() {
        val r = j("""{"actions":[{"action":"call","person":"мама"}],"reply":"Звоню ма""", "позвони маме")!!
        assertEquals(NoaIntent.Call("мама"), r.intent); assertNull(r.reply)
    }

    @Test fun unquotedKeysSingleQuotesAndInnerQuotes() {
        val a = j("{actions:[{action:'add_note',person:'Аня',text:'любит м'ятний чай'}]}", "добавь Ане заметку любит м'ятний чай")!!.intent as NoaIntent.AddNote
        assertEquals("любит м'ятний чай", a.text)
        val m = j("""{"actions":[{"action":"message","person":"Петя","text":"скажи "привет" маме"}]}""", "напиши Пете скажи привет маме")!!.intent as NoaIntent.Message
        assertEquals("скажи \"привет\" маме", m.text); assertEquals(NoaIntent.Channel.WHATSAPP, m.channel)
    }

    @Test fun bareArrayAndStringActions() {
        val r = j("""[{"action":"media","control":"next"},"go_home"]""")!!.intent as NoaIntent.Sequence
        assertEquals(listOf(NoaIntent.Media(NoaMedia.Control.NEXT), NoaIntent.GoHome), r.steps)
    }

    @Test fun datesComeFromUserText() {
        val a = j("""{"actions":[{"action":"create_appointment","person":"Маша","service":"Тату"}]}""", "запиши Машу на тату на 12-е в 12:00")!!.intent as NoaIntent.CreateAppointment
        assertEquals(12, a.dateTime!!.dayOfMonth); assertEquals(12, a.dateTime!!.hour); assertEquals("тату", a.serviceQuery)
        val ag = j("""{"actions":[{"action":"agenda","day":"tomorrow"}]}""", "що в мене на потім")!!.intent as NoaIntent.Agenda
        assertEquals(LocalDate.of(2026, 10, 5), ag.date)
        val al = j("""{"actions":[{"action":"alarm","time":"07:30"}]}""")!!.intent as NoaIntent.Alarm
        assertEquals(7, al.hour); assertEquals(30, al.minute)
    }

    @Test fun pronounPersonInheritsFromPreviousStep() {
        val seq = j("""{"actions":[{"action":"open_person","person":"Илья"},{"action":"add_note","person":"ему","text":"вернул долг"},{"action":"share_data","data":"phone","to":"telegram"}]}""", "открой Илью и добавь ему заметку вернул долг и скинь телефон в телеграм")!!.intent as NoaIntent.Sequence
        assertEquals(NoaIntent.AddNote("Илья", "вернул долг"), seq.steps[1])
        assertEquals(NoaIntent.ShareData("Илья", NoaIntent.Data.PHONE, "app:telegram"), seq.steps[2])
    }

    @Test fun garbageNeverThrows() {
        for (raw in listOf("", "это не json", "{", "}{", "{\"actions\":[{\"action\":", "[[[", "{\"a\" \"b\"}", "```json\n```", "{\"actions\":null}"))
            assertNull(raw, j(raw)?.intent)
        assertNull(j("это не json вообще"))
    }

    @Test fun staticPromptIsCompactAndComplete() {
        val p = NoaInterpreter.staticPrompt("Russian")
        assertTrue("промпт ${p.length} символов — слишком длинный для маленького окна", p.length < 1250)
        for (a in listOf("create_appointment", "cancel_appointment", "delete_appointment", "move_appointment", "call", "message", "reply",
            "read_messages", "add_note", "open_person", "find", "open_contact", "route", "agenda", "person_info", "favorite", "select",
            "share_data", "launch_app", "web_search", "alarm", "timer", "flashlight", "phone_settings", "play_music", "media", "go_home",
            "open_screen", "lock"))
            assertTrue(a, Regex("\\b$a\\b").containsMatchIn(p))
        assertEquals("Ukrainian", NoaInterpreter.langFor("що в мене завтра"))
        assertEquals("Russian", NoaInterpreter.langFor("что у меня завтра"))
    }
}
