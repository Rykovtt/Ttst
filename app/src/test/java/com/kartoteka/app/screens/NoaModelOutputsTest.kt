package com.kartoteka.app.screens

import androidx.test.core.app.ApplicationProvider
import com.kartoteka.app.assistant.NoaBenchmark
import com.kartoteka.app.assistant.NoaDescribe
import com.kartoteka.app.assistant.NoaIntent
import com.kartoteka.app.assistant.NoaInterpreter
import com.kartoteka.app.assistant.NoaMedia
import com.kartoteka.app.assistant.NoaTrust
import com.kartoteka.app.data.ContactType
import com.kartoteka.app.data.PlaceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Настоящие ответы маленькой модели на телефоне (фразы → что она выдала), прогнанные через починку.
 * «drop» — команда выброшена, дальше работают правила; «ask» — переспрашиваем; «chat» — только реплика.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class NoaModelOutputsTest {
    private val now = LocalDateTime.of(2026, 10, 4, 10, 0)
    private val ip = NoaInterpreter(null)
    private val app get() = ApplicationProvider.getApplicationContext<TestApp>()

    @Before fun ru() { com.kartoteka.app.i18n.I18n.init(app, com.kartoteka.app.i18n.UiLang.RU) }

    private val book = listOf("Ілля Риков", "Олексій Олексієнко", "Анна Іванова", "Ольга Сидорова")

    private fun run(phrase: String, raw: String, people: List<String> = book, data: String = "", hasLast: Boolean = false) =
        ip.fromJson(raw, phrase, now, people, hasLast, data)

    private fun outcome(r: com.kartoteka.app.assistant.Interpreted?): String = when {
        r == null -> "drop"
        r.ask != null -> "ask"
        r.intent != null -> NoaBenchmark.signature(r.intent)
        r.reply != null -> "chat"
        else -> "drop"
    }

    private class Case(val n: String, val phrase: String, val raw: String, val expected: String, val people: List<String> = emptyList(), val hasLast: Boolean = false)

    private val cases = listOf(
        Case("1 запись на завтра", "запиши Анну на завтра в 15:00",
            """{"actions":[{"action":"create_appointment","person":"Аня","service":"зареєстрована","time":"15:00"}],"reply":"Записала Аню."}""", "CreateAppointment"),
        Case("1b две Анны", "запиши Анну на завтра в 15:00",
            """{"actions":[{"action":"create_appointment","person":"Аня","service":"зареєстрована"}]}""", "ask",
            people = listOf("Анна Иванова", "Анна Петрова")),
        Case("2 день рождения", "когда день рождения у Ани",
            """{"actions":[{"action":"find","query":"день народження Ани"}],"reply":"<from Data>"}""", "PersonInfo"),
        Case("3 открой календарь", "открой календарь",
            """{"actions":[{"action":"reply","person":"Ілля Риков","text":"Календарь я не знаю"}],"reply":"Календарь я не знаю."}""", "drop"),
        Case("4 закрой приложение", "закрой приложение",
            """{"actions":[{"action":"phone_settings","person":"phone","what":"close_app"}],"reply":"Приложение закрыто."}""", "GoHome"),
        Case("5 который час", "который час",
            """{"actions":[{"action":"web_search","query":"What's the current time?"}], "reply":"Почекайте, шукаю."}""", "drop"),
        Case("6 закрой сейф", "закрой сейф",
            """{"actions":[{"action":"go_home", "text":"Закрий сейф"}],"reply":"Я не могу помочь с физическими предметами."}""", "Lock"),
        Case("7 что у меня сегодня", "что у меня сегодня",
            """{"actions": [], "reply": "У Ильи Рикова сегодня 04 жовтня, в суботу; у Олексія Олексієнка в 12:00."}""", "drop"),
        Case("8 карточка Ани", "открой карточку Ани",
            """{"actions":[{"action":"open_contact","person":"Аня","contact":"карточка"}],"reply":"Открываю."}""", "Open"),
        Case("9 пауза", "поставь на паузу",
            """{"actions":[{"action":"media","text":"pause"}],"reply":"Пауза наложена."}""", "Media"),
        Case("10 калькулятор", "открой калькулятор",
            """{"actions":[{"action":"open_screen","section":"Calculator"}],"reply":"Калькулятор открыт."}""", "LaunchApp"),
        Case("11 заметки в блокнот", "перенеси заметки Ильи в блокнот",
            """{"actions":[{"action":"add_note","person":"Илья","text":"Перенеси заметки в блокнот"}],"reply":"Заметка добавлена."}""", "ShareData"),
        Case("12 который час?", "Который час?",
            """{"actions":[{"action":"web_search","query":"What's the current time?"}], "reply":"Почекайте."}""", "drop"),
        Case("13 закрой waze", "закрой waze",
            """{"actions":[{"action":"launch_app","app":"waze"}],"reply":"Поставлено."}""", "CloseApp"),
        Case("14 планы на пятницу", "какие планы на пятницу",
            """{"actions": [{"action": "find", "query": "план на пятницу"}], "reply": "Я не могу найти ничего."}""", "Agenda"),
        Case("15 инстаграм Олега", "відкрий інстаграм Олега",
            """{"actions":[{"action":"reply","person":"Олег","text":"Я не знаю, як відкрити Instagram, але можу допомогти."}],"reply":"Не знаю."}""", "OpenContact",
            people = book + "Олег Петренко"),
        Case("16 камера", "открой камеру",
            """{"actions":[{"action":"phone_settings","what":"open_screen"}],"reply":"Проблемы с камерой нет."}""", "LaunchApp"),
        Case("17 адрес Оли в вотсап", "скинь адрес Оли в вотсап",
            """{"actions":[{"action":"reply","person":"Ольга","text":"Я не могу помочь с удалением адреса."}],"reply":"Не могу."}""", "ShareData",
            people = listOf("Ілля Риков", "Анна Іванова", "Ольга Сидорова")),
        Case("18 километры", "сколько километров в 20 милях",
            """{"actions":[{"action":"web_search", "query":"convert 20 miles to kilometers"}],"reply":"Тепер дізнаюся."}""", "drop"),
        Case("19 перестрой маршрут", "перестрой маршрут на Хрещатик 10",
            """{"actions":[{"action":"web_search","query":"Перестрой маршрут на Хрещатик 10"}],"reply":"Я не можу."}""", "drop"),
        Case("20 загугли рецепт", "загугли рецепт борща",
            """{"actions": [], "ask": "Хотели бы вы получить рецепт борща?"}""", "WebSearch"),
        Case("21 два плюс два", "сколько будет два плюс два",
            """{"actions":[{"action":"calculate","formula":"2+2"}],"reply":"Сума становить 4."}""", "drop"),
        Case("22 перенос Оли", "перенеси Олю с пятницы на субботу",
            """{"actions":[{"action":"cancel_appointment","person":"Оля"},{"action":"create_appointment","person":"Оля","time":"суббота"}],"reply":"Перенесла."}""", "MoveAppointment",
            people = listOf("Ілля Риков", "Анна Іванова", "Ольга Сидорова")),
        Case("23 позвони Ане Рыковой", "позвони Ане Рыковой",
            """{"actions":[{"action":"ask","text":"Кому вамneephone?"}], "reply":"Пожалуйста, скажіть мне номер телефона."}""", "drop"),
        // — дополнительные —
        Case("24 инстаграм верно", "открой инстаграм Ани",
            """{"actions":[{"action":"open_contact","person":"Аня","contact":"instagram"}]}""", "OpenContact", hasLast = false),
        Case("25 человек не назван", "позвони маме",
            """{"actions":[{"action":"call","person":"Ілля Риков"}]}""", "ask"),
        Case("26 местоимение", "позвони ей",
            """{"actions":[{"action":"call","person":"Ольга Сидорова"}]}""", "Call", hasLast = true),
        Case("27 громче в другом поле", "сделай погромче",
            """{"actions":[{"action":"media","command":"louder"}]}""", "Media"),
        Case("28 сообщение верное", "напиши Ане в телеграм что опоздаю",
            """{"actions":[{"action":"message","person":"Аня","channel":"telegram","text":"опоздаю"}],"reply":"Пишу"}""", "Message"),
        Case("29 услуга из фразы", "запиши Машу на тату завтра в 12:00",
            """{"actions":[{"action":"create_appointment","person":"Маша","service":"тату"}]}""", "CreateAppointment"),
        Case("30 чужой язык", "расскажи анекдот",
            """{"actions":[],"reply":"Я не можу розповісти анекдот, вибачте мені."}""", "drop"),
        Case("31 короткий чужой язык", "расскажи анекдот",
            """{"actions":[],"reply":"Добре, зараз."}""", "chat"),
        Case("32 выдуманные имена", "что нового",
            """{"actions":[],"reply":"Ольга Сидорова записана на пятницу."}""", "drop"),
        Case("33 болтовня", "как дела",
            """{"actions":[],"reply":"Отлично, спасибо."}""", "chat"),
        Case("34 закрыть приложение", "закрой waze",
            """{"actions":[{"action":"close_app","app":"waze"}]}""", "CloseApp"),
        Case("35 честный поиск", "кто такой Эйнштейн",
            """{"actions":[{"action":"web_search","query":"Эйнштейн"}]}""", "WebSearch"),
        Case("36 настройки вайфай", "открой настройки вайфай",
            """{"actions":[{"action":"phone_settings","what":"wifi"}]}""", "PhoneSettings"),
        Case("37 каша алфавитов", "как дела",
            """{"actions":[],"reply":"Всё хорошоneephone, спасибо"}""", "drop"),
        Case("38 удалить+создать", "перемісти Ілью на п'ятницю",
            """{"actions":[{"action":"delete_appointment","person":"Ілля"},{"action":"create_appointment","person":"Ілля"}]}""", "MoveAppointment"),
        Case("39 написать чужому", "позвони Олексію",
            """{"actions":[{"action":"call","person":"Ілля Риков"}]}""", "Call"),
    )

    @Test fun realOutputsResolve() {
        val bad = cases.mapNotNull { c ->
            val got = outcome(run(c.phrase, c.raw, c.people.ifEmpty { book }, hasLast = c.hasLast))
            if (got == c.expected) null else "${c.n}: ждали ${c.expected}, получили $got"
        }
        assertTrue("Не сошлось:\n" + bad.joinToString("\n"), bad.isEmpty())
        assertTrue(cases.size >= 30)
    }

    @Test fun personNeverCopiedFromPeopleList() {
        // «Ілля Риков» из списка — не названный во фразе, значит, не человек команды.
        val r = run("позвони маме", """{"actions":[{"action":"call","person":"Ілля Риков"}]}""")!!
        assertNotNull(r.ask)
        assertEquals(null, r.intent)
        // Названный во фразе человек из книжки побеждает выдуманного.
        val call = run("позвони Олексію", """{"actions":[{"action":"call","person":"Ілля Риков"}]}""")!!.intent as NoaIntent.Call
        assertEquals("Олексій Олексієнко", call.personQuery)
    }

    private fun assertNotNull(x: Any?) = assertTrue(x != null)

    @Test fun commandRepliesAreStripped() {
        for (n in listOf("4 закрой приложение", "9 пауза", "10 калькулятор", "13 закрой waze", "6 закрой сейф", "8 карточка Ани")) {
            val c = cases.first { it.n == n }
            assertNull(n, run(c.phrase, c.raw)!!.reply)
        }
    }

    @Test fun repairedFieldsAreRight() {
        run("запиши Анну на завтра в 15:00", cases[0].raw)!!.let {
            val a = it.intent as NoaIntent.CreateAppointment
            assertEquals("Анна Іванова", a.personQuery); assertNull(a.serviceQuery); assertEquals(15, a.dateTime!!.hour)
        }
        assertEquals(NoaMedia.Control.PAUSE, (run("поставь на паузу", cases.first { it.n == "9 пауза" }.raw)!!.intent as NoaIntent.Media).control)
        assertEquals("Calculator", (run("открой калькулятор", cases.first { it.n == "10 калькулятор" }.raw)!!.intent as NoaIntent.LaunchApp).name)
        assertEquals("waze", (run("закрой waze", cases.first { it.n == "13 закрой waze" }.raw)!!.intent as NoaIntent.CloseApp).name)
        val c = cases.first { it.n == "16 камера" }
        assertEquals("камеру", (run(c.phrase, c.raw)!!.intent as NoaIntent.LaunchApp).name)
        val p = run("когда день рождения у Ани", cases[2].raw)!!.intent as NoaIntent.PersonInfo
        assertEquals(NoaIntent.Topic.BIRTHDAY, p.topic)
        val oc = run("відкрий інстаграм Олега", cases.first { it.n == "15 инстаграм Олега" }.raw, book + "Олег Петренко")!!.intent as NoaIntent.OpenContact
        assertEquals(ContactType.INSTAGRAM, oc.type); assertEquals("Олег Петренко", oc.personQuery)
        val sd = run("перенеси заметки Ильи в блокнот", cases.first { it.n == "11 заметки в блокнот" }.raw)!!.intent as NoaIntent.ShareData
        assertEquals(NoaIntent.Data.NOTES, sd.data); assertEquals("notes", sd.target); assertEquals("Ілля Риков", sd.personQuery)
        assertEquals("рецепт борща", (run("загугли рецепт борща", cases.first { it.n == "20 загугли рецепт" }.raw)!!.intent as NoaIntent.WebSearch).query)
    }

    @Test fun agendaNamesAllowedWhenInData() {
        val raw = """{"actions": [], "reply": "У Ольги Сидоровой сегодня запись в 12:00."}"""
        assertEquals("drop", outcome(run("что у меня сегодня", raw)))
        assertEquals("chat", outcome(run("что у меня сегодня", raw, data = "Сегодня: Ольга Сидорова 12:00")))
    }

    // ---- описание команд ----

    private val samples: List<NoaIntent> = listOf(
        NoaIntent.CreateAppointment("Анна", LocalDateTime.of(2026, 10, 5, 15, 0), true, "тату", false),
        NoaIntent.Find("клиенты"), NoaIntent.Open("Аня"), NoaIntent.OpenScreen(NoaIntent.Section.CALENDAR), NoaIntent.Call("Аня"),
        NoaIntent.Message("Аня", NoaIntent.Channel.TELEGRAM, "опоздаю"), NoaIntent.Select("Аня"), NoaIntent.AddNote("Аня", "любит кофе"),
        NoaIntent.OpenContact("Олег", ContactType.INSTAGRAM), NoaIntent.Route("", PlaceKind.HOME, null, ""), NoaIntent.Agenda(LocalDate.of(2026, 10, 5)),
        NoaIntent.PersonInfo("Аня", NoaIntent.Topic.BIRTHDAY, "когда"), NoaIntent.Favorite("Аня", true),
        NoaIntent.CancelAppointment("Аня", LocalDate.of(2026, 10, 5), false), NoaIntent.MoveAppointment("Оля", LocalDateTime.of(2026, 10, 9, 12, 0), true, true),
        NoaIntent.Sequence(listOf(NoaIntent.Message("Аня", NoaIntent.Channel.SMS, null), NoaIntent.GoHome)),
        NoaIntent.LaunchApp("камера"), NoaIntent.ShareData("Илья", NoaIntent.Data.NOTES, "notes"), NoaIntent.WebSearch("рецепт борща"),
        NoaIntent.Alarm(7, 30), NoaIntent.Timer(300), NoaIntent.Flashlight(true), NoaIntent.Play("Скриптонит", null, false),
        NoaIntent.Media(NoaMedia.Control.PAUSE), NoaIntent.Reply("Олег", "буду в семь"), NoaIntent.ReadMessages(""), NoaIntent.GoHome,
        NoaIntent.Wrong, NoaIntent.CloseApp("waze"), NoaIntent.PhoneSettings(null), NoaIntent.Lock, NoaIntent.Backup,
        NoaIntent.Remind("позвонить маме", LocalDateTime.of(2026, 10, 5, 9, 0), true), NoaIntent.Tool(NoaIntent.ToolKind.TIME),
        NoaIntent.Volume(NoaIntent.VolumeKind.SET, 40), NoaIntent.VoiceNote("Илья"), NoaIntent.Calc("2+2"), NoaIntent.Convert(20.0, "mi", "km"), NoaIntent.Crm(NoaIntent.CrmKind.NEXT), NoaIntent.Dismiss, NoaIntent.Repeat, NoaIntent.Unknown("бла"),
    )

    /** Независимая от кода таблица: нужно ли подтверждение (when без else — новый класс не скомпилируется, пока его не отнесли). */
    private fun expectConfirm(i: NoaIntent): Boolean = when (i) {
        is NoaIntent.Agenda, is NoaIntent.PersonInfo, is NoaIntent.Find, is NoaIntent.Open, is NoaIntent.OpenScreen, is NoaIntent.Select, is NoaIntent.Tool,
        is NoaIntent.Calc, is NoaIntent.Convert, is NoaIntent.Crm, is NoaIntent.Media, is NoaIntent.Volume, is NoaIntent.Flashlight, is NoaIntent.GoHome, is NoaIntent.ReadMessages,
        is NoaIntent.Wrong, is NoaIntent.Dismiss, is NoaIntent.Repeat, is NoaIntent.Unknown -> false
        is NoaIntent.Message, is NoaIntent.Reply, is NoaIntent.Call, is NoaIntent.CreateAppointment, is NoaIntent.MoveAppointment, is NoaIntent.CancelAppointment,
        is NoaIntent.AddNote, is NoaIntent.Remind, is NoaIntent.Alarm, is NoaIntent.Timer, is NoaIntent.Route, is NoaIntent.Play, is NoaIntent.WebSearch,
        is NoaIntent.ShareData, is NoaIntent.Favorite, is NoaIntent.LaunchApp, is NoaIntent.CloseApp, is NoaIntent.OpenContact, is NoaIntent.PhoneSettings,
        is NoaIntent.Lock, is NoaIntent.Backup, is NoaIntent.VoiceNote -> true
        is NoaIntent.Sequence -> i.steps.any { expectConfirm(it) }
    }

    @Test fun describeCoversEveryIntent() {
        for (i in samples) {
            val d = NoaDescribe.describe(i)
            assertTrue("${NoaBenchmark.signature(i)}: пусто", d.isNotBlank())
            assertFalse("${NoaBenchmark.signature(i)}: необработанный шаблон «$d»", d.contains("%1") || d.contains("\$s"))
        }
    }

    @Test fun describeContent() {
        val ap = NoaDescribe.describe(NoaIntent.CreateAppointment("Анна", LocalDateTime.of(2026, 10, 5, 15, 0), true, "тату", false))
        assertTrue(ap, "Анна" in ap && "15:00" in ap && "5 октября" in ap && "тату" in ap)
        assertEquals("написать Аня в Telegram: «опоздаю»", NoaDescribe.describe(NoaIntent.Message("Аня", NoaIntent.Channel.TELEGRAM, "опоздаю")))
        assertEquals("написать Аня в SMS; затем свернуть на главный экран", NoaDescribe.describe(samples.first { it is NoaIntent.Sequence }))
        assertEquals("закрыть приложение «waze»", NoaDescribe.describe(NoaIntent.CloseApp("waze")))
        assertEquals("Поняла так: позвонить Аня. Выполнить?", NoaDescribe.confirmQuestion(NoaIntent.Call("Аня")))
        assertEquals("передать данные Илья: заметки — куда: блокнот", NoaDescribe.describe(NoaIntent.ShareData("Илья", NoaIntent.Data.NOTES, "notes")))
    }

    @Test fun trustClassification() {
        for (i in samples) assertEquals(NoaBenchmark.signature(i), expectConfirm(i), NoaTrust.needsConfirm(i))
        // Цепочка безопасна, только если безопасны все шаги.
        assertFalse(NoaTrust.needsConfirm(NoaIntent.Sequence(listOf(NoaIntent.Open("Аня"), NoaIntent.GoHome))))
        assertTrue(NoaTrust.needsConfirm(NoaIntent.Sequence(listOf(NoaIntent.Open("Аня"), NoaIntent.Call("Аня")))))
    }

    @Test fun repairedModelCommandsAreClassified() {
        // Что именно уйдёт на подтверждение из реальных ответов: опасное — да, чтение и плеер — нет.
        fun needs(n: String) = NoaTrust.needsConfirm(run(cases.first { it.n == n }.phrase, cases.first { it.n == n }.raw, cases.first { it.n == n }.people.ifEmpty { book })!!.intent!!)
        assertTrue(needs("1 запись на завтра")); assertTrue(needs("10 калькулятор")); assertTrue(needs("13 закрой waze")); assertTrue(needs("6 закрой сейф"))
        assertFalse(needs("2 день рождения")); assertFalse(needs("8 карточка Ани")); assertFalse(needs("9 пауза")); assertFalse(needs("14 планы на пятницу")); assertFalse(needs("4 закрой приложение"))
    }
}
