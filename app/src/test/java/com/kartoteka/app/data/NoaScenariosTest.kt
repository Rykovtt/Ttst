package com.kartoteka.app.data

import com.kartoteka.app.assistant.NoaIntent
import com.kartoteka.app.assistant.NoaMedia
import com.kartoteka.app.assistant.NoaParser
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Живые фразы пользователя (рус./укр./англ.): работа с клиентами, сообщения, музыка и навигация за рулём, телефон.
 * Ошибки в одном методе собираются все сразу — так видно весь список того, что Ноа поняла не так.
 */
class NoaScenariosTest {
    private val now = LocalDateTime.of(2026, 10, 4, 10, 0) // воскресенье
    private fun p(s: String) = NoaParser.parse(s, now)
    private fun day(d: Int): LocalDate = LocalDate.of(2026, 10, d)

    private val errors = mutableListOf<String>()

    /** Фраза должна разобраться в [T], а затем пройти проверки [check]. */
    private inline fun <reified T : NoaIntent> ok(phrase: String, check: T.() -> Unit = {}) {
        val r = p(phrase)
        if (r !is T) { errors += "«$phrase» → $r (ждали ${T::class.simpleName})"; return }
        try { r.check() } catch (e: AssertionError) { errors += "«$phrase» → $r: ${e.message}" }
    }

    /** Цепочка: шаги нужных типов по порядку, затем проверки. */
    private fun seq(phrase: String, vararg types: Class<out NoaIntent>, check: List<NoaIntent>.() -> Unit = {}) {
        val r = p(phrase)
        val steps = (r as? NoaIntent.Sequence)?.steps
        if (steps == null || steps.size != types.size || steps.zip(types).any { (s, t) -> !t.isInstance(s) }) {
            errors += "«$phrase» → $r (ждали ${types.joinToString { it.simpleName }})"; return
        }
        try { steps.check() } catch (e: AssertionError) { errors += "«$phrase» → $r: ${e.message}" }
    }

    private fun eq(phrase: String, expected: NoaIntent) {
        val r = p(phrase)
        if (r != expected) errors += "«$phrase» → $r (ждали $expected)"
    }

    private fun media(phrase: String, c: NoaMedia.Control) = ok<NoaIntent.Media>(phrase) { assertEquals(c, control) }

    @After fun report() {
        if (errors.isNotEmpty()) fail("Не поняты ${errors.size}:\n" + errors.joinToString("\n"))
    }

    private fun has(s: String, part: String) = assertTrue("«$s» должно содержать «$part»", s.lowercase().contains(part))

    // ---- записи ----

    @Test fun createAppointments() {
        ok<NoaIntent.CreateAppointment>("запиши Анну на завтра в 15:00") {
            assertEquals("анну", personQuery); assertEquals(LocalDateTime.of(2026, 10, 5, 15, 0), dateTime); assertTrue(hadTime)
        }
        ok<NoaIntent.CreateAppointment>("запиши Олю на пятницу на маникюр в 10") {
            assertEquals("олю", personQuery); assertEquals("маникюр", serviceQuery); assertEquals(LocalDateTime.of(2026, 10, 9, 10, 0), dateTime)
        }
        ok<NoaIntent.CreateAppointment>("запиши Машу на стрижку послезавтра в 11:30") {
            assertEquals("машу", personQuery); assertEquals("стрижк", serviceQuery); assertEquals(LocalDateTime.of(2026, 10, 6, 11, 30), dateTime)
        }
        ok<NoaIntent.CreateAppointment>("запиши Олега на консультацию в среду в 14:00") {
            assertEquals("олега", personQuery); assertEquals("консультац", serviceQuery); assertEquals(LocalDateTime.of(2026, 10, 7, 14, 0), dateTime)
        }
        ok<NoaIntent.CreateAppointment>("запиши Ірину на завтра о 12") {
            assertEquals("ірину", personQuery); assertEquals(LocalDateTime.of(2026, 10, 5, 12, 0), dateTime)
        }
        ok<NoaIntent.CreateAppointment>("запишіть Марію на понеділок о 9:30") {
            assertEquals("марію", personQuery); assertEquals(LocalDateTime.of(2026, 10, 5, 9, 30), dateTime)
        }
        ok<NoaIntent.CreateAppointment>("назначь встречу с Ильей на четверг в 16:00") {
            assertEquals("ильей", personQuery); assertEquals(LocalDateTime.of(2026, 10, 8, 16, 0), dateTime)
        }
        ok<NoaIntent.CreateAppointment>("запиши маму на тату 12 октября в 18:00") {
            assertEquals("маму", personQuery); assertEquals("тату", serviceQuery); assertEquals(LocalDateTime.of(2026, 10, 12, 18, 0), dateTime)
        }
        ok<NoaIntent.CreateAppointment>("запиши Аню") { assertEquals("аню", personQuery); assertEquals(null, dateTime) }
        ok<NoaIntent.CreateAppointment>("запиши Диму на завтра") {
            assertEquals("диму", personQuery); assertEquals(day(5), dateTime!!.toLocalDate()); assertTrue(!hadTime)
        }
        ok<NoaIntent.CreateAppointment>("запиши пожалуйста Свету Петрову на маникюр в субботу в 12") {
            assertEquals("свету петрову", personQuery); assertEquals("маникюр", serviceQuery); assertEquals(LocalDateTime.of(2026, 10, 10, 12, 0), dateTime)
        }
        ok<NoaIntent.CreateAppointment>("запиши Олену на манікюр у вівторок о 17:00") {
            assertEquals("олену", personQuery); assertEquals("маникюр", serviceQuery); assertEquals(LocalDateTime.of(2026, 10, 6, 17, 0), dateTime)
        }
        ok<NoaIntent.CreateAppointment>("book Anna for tomorrow at 3 pm") {
            assertEquals("anna", personQuery); assertEquals(LocalDateTime.of(2026, 10, 5, 15, 0), dateTime)
        }
    }

    @Test fun moveAndCancelAppointments() {
        ok<NoaIntent.MoveAppointment>("перенеси Илью на понедельник") {
            assertEquals("илью", personQuery); assertTrue(hadDate); assertTrue(!hadTime); assertEquals(day(5), dateTime!!.toLocalDate())
        }
        ok<NoaIntent.MoveAppointment>("перенеси запись Ани на 17:00") {
            assertEquals("ани", personQuery); assertTrue(hadTime); assertTrue(!hadDate); assertEquals(17, dateTime!!.hour)
        }
        ok<NoaIntent.MoveAppointment>("перенеси встречу с Ильей на завтра в 12") {
            assertEquals("ильей", personQuery); assertEquals(LocalDateTime.of(2026, 10, 5, 12, 0), dateTime)
        }
        ok<NoaIntent.MoveAppointment>("перенеси Ірину на вівторок о 15:00") {
            assertEquals("ірину", personQuery); assertEquals(LocalDateTime.of(2026, 10, 6, 15, 0), dateTime)
        }
        // «с пятницы на субботу»: новое время — то, что после «на»
        ok<NoaIntent.MoveAppointment>("перенеси Олю с пятницы на субботу") {
            assertEquals("олю", personQuery); assertEquals(day(10), dateTime!!.toLocalDate())
        }
        ok<NoaIntent.MoveAppointment>("пересунь запис Марії на четвер") {
            assertEquals("марії", personQuery); assertEquals(day(8), dateTime!!.toLocalDate())
        }
        ok<NoaIntent.CancelAppointment>("отмени запись Ольги завтра") {
            assertEquals("ольги", personQuery); assertEquals(day(5), date); assertTrue(!delete)
        }
        ok<NoaIntent.CancelAppointment>("отмени Илью") { assertEquals("илью", personQuery); assertEquals(null, date) }
        ok<NoaIntent.CancelAppointment>("удали запись Ани на пятницу") {
            assertEquals("ани", personQuery); assertEquals(day(9), date); assertTrue(delete)
        }
        ok<NoaIntent.CancelAppointment>("скасуй запис Марії на завтра") { assertEquals("марії", personQuery); assertEquals(day(5), date) }
        ok<NoaIntent.CancelAppointment>("отмени встречу с Олегом в среду") { assertEquals("олегом", personQuery); assertEquals(day(7), date) }
    }

    @Test fun agendaAndQuestions() {
        ok<NoaIntent.Agenda>("что у меня завтра") { assertEquals(day(5), date) }
        ok<NoaIntent.Agenda>("что у меня сегодня") { assertEquals(day(4), date) }
        ok<NoaIntent.Agenda>("какие планы на пятницу") { assertEquals(day(9), date) }
        ok<NoaIntent.Agenda>("що в мене в понеділок") { assertEquals(day(5), date) }
        ok<NoaIntent.Agenda>("кто записан на среду") { assertEquals(day(7), date) }
        ok<NoaIntent.Agenda>("розклад на завтра") { assertEquals(day(5), date) }
        ok<NoaIntent.Agenda>("что у меня на 12 октября") { assertEquals(day(12), date) }
        ok<NoaIntent.Agenda>("what do i have tomorrow") { assertEquals(day(5), date) }
        ok<NoaIntent.PersonInfo>("когда день рождения у Ани") { assertEquals(NoaIntent.Topic.BIRTHDAY, topic); assertEquals("ани", personQuery) }
        ok<NoaIntent.PersonInfo>("коли в Олі день народження") { assertEquals(NoaIntent.Topic.BIRTHDAY, topic); assertEquals("олі", personQuery) }
        ok<NoaIntent.PersonInfo>("сколько лет Ане") { assertEquals(NoaIntent.Topic.BIRTHDAY, topic); assertEquals("ане", personQuery) }
        ok<NoaIntent.PersonInfo>("какой телефон у мамы") { assertEquals(NoaIntent.Topic.PHONE, topic); assertEquals("мамы", personQuery) }
        ok<NoaIntent.PersonInfo>("який номер у Олега") { assertEquals(NoaIntent.Topic.PHONE, topic); assertEquals("олега", personQuery) }
        ok<NoaIntent.PersonInfo>("где живёт Илья") { assertEquals(NoaIntent.Topic.ADDRESS, topic); assertEquals("илья", personQuery) }
        ok<NoaIntent.PersonInfo>("расскажи про Илью") { assertEquals(NoaIntent.Topic.SUMMARY, topic); assertEquals("илью", personQuery) }
        ok<NoaIntent.PersonInfo>("що я знаю про Олену") { assertEquals(NoaIntent.Topic.SUMMARY, topic); assertEquals("олену", personQuery) }
        ok<NoaIntent.PersonInfo>("when is Anna's birthday") { assertEquals(NoaIntent.Topic.BIRTHDAY, topic); has(personQuery, "anna") }
    }

    @Test fun notesCallsOpenFavorites() {
        ok<NoaIntent.AddNote>("добавь заметку Ане: любит кофе") { assertEquals("ане", personQuery); assertEquals("любит кофе", text) }
        ok<NoaIntent.AddNote>("додай нотатку Олегу: повернув борг") { assertEquals("олегу", personQuery); assertEquals("повернув борг", text) }
        ok<NoaIntent.AddNote>("добавь заметку Илье что он предпочитает утро") { assertEquals("илье", personQuery); assertEquals("он предпочитает утро", text) }
        ok<NoaIntent.AddNote>("запиши в хронику Маши что купила абонемент") { assertEquals("маши", personQuery); assertEquals("купила абонемент", text) }
        ok<NoaIntent.AddNote>("добавь Ане заметку «аллергия на лак»") { assertEquals("ане", personQuery); assertEquals("аллергия на лак", text) }
        ok<NoaIntent.Call>("позвони маме") { assertEquals("маме", personQuery) }
        ok<NoaIntent.Call>("набери Илью") { assertEquals("илью", personQuery) }
        ok<NoaIntent.Call>("подзвони Олегу") { assertEquals("олегу", personQuery) }
        ok<NoaIntent.Call>("зателефонуй мамі") { assertEquals("мамі", personQuery) }
        ok<NoaIntent.Call>("позвони Ане Рыковой") { assertEquals("ане рыковой", personQuery) }
        ok<NoaIntent.Call>("позвони пожалуйста жене") { assertEquals("жене", personQuery) }
        ok<NoaIntent.Call>("call mom") { assertEquals("mom", personQuery) }
        ok<NoaIntent.Open>("відкрий профіль Олега") { assertEquals("олега", personQuery) }
        ok<NoaIntent.Open>("открой карточку Ани") { assertEquals("ани", personQuery) }
        ok<NoaIntent.Open>("покажи контакт Ильи") { assertEquals("ильи", personQuery) }
        ok<NoaIntent.Open>("открой Машу Фролову") { assertEquals("машу фролову", personQuery) }
        ok<NoaIntent.OpenContact>("открой инстаграм Ани") { assertEquals(ContactType.INSTAGRAM, type); assertEquals("ани", personQuery) }
        ok<NoaIntent.OpenScreen>("открой календарь") { assertEquals(NoaIntent.Section.CALENDAR, section) }
        ok<NoaIntent.Favorite>("добавь Олю в избранное") { assertEquals("олю", personQuery); assertTrue(on) }
        ok<NoaIntent.Favorite>("убери Олю из избранного") { assertEquals("олю", personQuery); assertTrue(!on) }
    }

    // ---- сообщения ----

    @Test fun messaging() {
        ok<NoaIntent.Message>("напиши Илье что буду через 10 минут") {
            assertEquals("илье", personQuery); assertEquals("буду через 10 минут", text!!.lowercase())
        }
        ok<NoaIntent.Message>("напиши Илье что куплю хлеб и позвоню вечером") {
            assertEquals("илье", personQuery); assertEquals("куплю хлеб и позвоню вечером", text!!.lowercase())
        }
        ok<NoaIntent.Message>("напиши маме в телеграм что я доехал") {
            assertEquals("маме", personQuery); assertEquals(NoaIntent.Channel.TELEGRAM, channel); assertEquals("я доехал", text!!.lowercase())
        }
        ok<NoaIntent.Message>("отправь смс Олегу: перезвоню позже") {
            assertEquals("олегу", personQuery); assertEquals(NoaIntent.Channel.SMS, channel); assertEquals("перезвоню позже", text!!.lowercase())
        }
        ok<NoaIntent.Message>("напиши Олегу що буду о шостій") { assertEquals("олегу", personQuery); assertEquals("буду о шостій", text!!.lowercase()) }
        ok<NoaIntent.Message>("напиши в вотсап Ане что я на месте") {
            assertEquals("ане", personQuery); assertEquals(NoaIntent.Channel.WHATSAPP, channel); assertEquals("я на месте", text!!.lowercase())
        }
        ok<NoaIntent.Message>("скажи Илье что я опаздываю") { assertEquals("илье", personQuery); assertEquals("я опаздываю", text!!.lowercase()) }
        ok<NoaIntent.Message>("передай маме что я задержусь") { assertEquals("маме", personQuery); assertEquals("я задержусь", text!!.lowercase()) }
        ok<NoaIntent.Message>("напиши жене что люблю её") { assertEquals("жене", personQuery); assertEquals("люблю её", text!!.lowercase()) }
        ok<NoaIntent.Message>("send Anna a message that i'm late") { assertEquals("anna", personQuery); assertEquals("i'm late", text!!.lowercase()) }
        seq("напиши Илье в вотсап что опаздываю и закрой приложение", NoaIntent.Message::class.java, NoaIntent.GoHome::class.java) {
            val m = this[0] as NoaIntent.Message
            assertEquals("илье", m.personQuery); assertEquals("опаздываю", m.text!!.lowercase()); assertEquals(NoaIntent.Channel.WHATSAPP, m.channel)
        }
        seq("напиши Ане что я на месте и прочитай ответ", NoaIntent.Message::class.java, NoaIntent.ReadMessages::class.java) {
            assertEquals("я на месте", (this[0] as NoaIntent.Message).text!!.lowercase())
            assertTrue((this[1] as NoaIntent.ReadMessages).wait)
        }
        seq("напиши жене что выезжаю и проложи маршрут домой", NoaIntent.Message::class.java, NoaIntent.Route::class.java) {
            assertEquals("выезжаю", (this[0] as NoaIntent.Message).text!!.lowercase())
            assertEquals(PlaceKind.HOME, (this[1] as NoaIntent.Route).kind)
        }
        seq("напиши Илье что опаздываю и включи музыку", NoaIntent.Message::class.java, NoaIntent.Play::class.java) {
            assertEquals("опаздываю", (this[0] as NoaIntent.Message).text!!.lowercase())
        }
        // «…и поставлю чайник» — это тоже текст сообщения, а не команда Ноа
        ok<NoaIntent.Message>("напиши маме что скоро приеду и включу ей музыку") {
            assertEquals("скоро приеду и включу ей музыку", text!!.lowercase())
        }
    }

    @Test fun messageTextIsNotACommand() {
        ok<NoaIntent.Message>("напиши Илье что запись переносится на завтра") {
            assertEquals("илье", personQuery); assertEquals("запись переносится на завтра", text!!.lowercase())
        }
        ok<NoaIntent.Message>("напиши Ане что я поставил машину на паузу") { assertEquals("я поставил машину на паузу", text!!.lowercase()) }
        ok<NoaIntent.Message>("напиши маме что позвоню ей из дома") { assertEquals("маме", personQuery); assertEquals("позвоню ей из дома", text!!.lowercase()) }
        ok<NoaIntent.Message>("напиши Олегу що куплю хліб та подзвоню ввечері") { assertEquals("куплю хліб та подзвоню ввечері", text!!.lowercase()) }
        ok<NoaIntent.Message>("напиши Илье что опаздываю в телеграм") {
            assertEquals("илье", personQuery); assertEquals(NoaIntent.Channel.TELEGRAM, channel); assertEquals("опаздываю", text!!.lowercase())
        }
        ok<NoaIntent.Message>("напиши Илье что скинул файл в телеграм и в почту") { assertEquals(NoaIntent.Channel.WHATSAPP, channel) }
        ok<NoaIntent.Message>("відправ Олені у телеграм що я вже їду") {
            assertEquals("олені", personQuery); assertEquals(NoaIntent.Channel.TELEGRAM, channel); assertEquals("я вже їду", text!!.lowercase())
        }
        // «скажи, что у меня завтра» — вопрос Ноа, а не сообщение
        ok<NoaIntent.Agenda>("скажи что у меня завтра") { assertEquals(day(5), date) }
        ok<NoaIntent.PersonInfo>("скажи какой номер у Олега") { assertEquals(NoaIntent.Topic.PHONE, topic) }
        seq("отмени запись Ольги на завтра и напиши ей что приём переносится", NoaIntent.CancelAppointment::class.java, NoaIntent.Message::class.java) {
            assertEquals("ольги", NoaParser.personOf(this[1])); assertEquals("приём переносится", (this[1] as NoaIntent.Message).text!!.lowercase())
        }
        seq("запиши Аню на пятницу в 12 и напиши ей что жду её", NoaIntent.CreateAppointment::class.java, NoaIntent.Message::class.java) {
            assertEquals("жду её", (this[1] as NoaIntent.Message).text!!.lowercase())
        }
    }

    @Test fun readAndReply() {
        ok<NoaIntent.Reply>("ответь ему ок") { assertEquals("", personQuery); assertEquals("ок", text.lowercase()) }
        ok<NoaIntent.Reply>("ответь Илье: буду в пять") { assertEquals("илье", personQuery); assertEquals("буду в пять", text.lowercase()) }
        ok<NoaIntent.Reply>("ответь Илье что буду через 5 минут") { assertEquals("илье", personQuery); assertEquals("буду через 5 минут", text.lowercase()) }
        ok<NoaIntent.Reply>("відповідай йому: добре") { assertEquals("", personQuery); assertEquals("добре", text.lowercase()) }
        ok<NoaIntent.Reply>("ответь ей что я согласна") { assertEquals("", personQuery); assertEquals("я согласна", text.lowercase()) }
        ok<NoaIntent.ReadMessages>("прочитай новые сообщения") { assertEquals("", personQuery); assertTrue(!wait) }
        ok<NoaIntent.ReadMessages>("что пишет Илья") { assertEquals("илья", personQuery) }
        ok<NoaIntent.ReadMessages>("что написала мама") { assertEquals("мама", personQuery) }
        ok<NoaIntent.ReadMessages>("прочитай сообщения от Ильи") { assertEquals("ильи", personQuery) }
        ok<NoaIntent.ReadMessages>("прочитай повідомлення від Олега") { assertEquals("олега", personQuery) }
        ok<NoaIntent.ReadMessages>("есть новые сообщения") { assertEquals("", personQuery) }
        ok<NoaIntent.ReadMessages>("що пише Оля") { assertEquals("оля", personQuery) }
        ok<NoaIntent.ReadMessages>("дождись ответа и прочитай") { assertTrue(wait) }
        ok<NoaIntent.ReadMessages>("дочекайся відповіді і прочитай") { assertTrue(wait) }
        ok<NoaIntent.ReadMessages>("read my messages") { assertEquals("", personQuery) }
    }

    // ---- музыка и видео за рулём ----

    @Test fun playMusicAndVideo() {
        ok<NoaIntent.Play>("найди мне плейлист хиты 90х и включи воспроизведение в случайном порядке") {
            assertEquals("хиты 90х", query); assertTrue(playlist); assertTrue(shuffle)
        }
        ok<NoaIntent.Play>("включи Rammstein") { assertEquals("rammstein", query); assertTrue(artist) }
        ok<NoaIntent.Play>("открой ютуб мьющик и включи плейлист") { assertEquals("ютуб мьющик", app); assertTrue(playlist) }
        ok<NoaIntent.Play>("включи любое видео на YouTube") { assertTrue(video); assertEquals("", query); assertTrue(app != null) }
        ok<NoaIntent.Play>("включи видео про рыбалку на ютубе") { assertTrue(video); has(query, "рыбалк") }
        ok<NoaIntent.Play>("включи музыку") { assertEquals("", query); assertEquals(null, app) }
        ok<NoaIntent.Play>("увімкни музику в спотіфай") { assertEquals("", query); assertEquals("спотіфай", app) }
        ok<NoaIntent.Play>("включи песню Bohemian Rhapsody") { assertEquals("bohemian rhapsody", query); assertTrue(!playlist) }
        ok<NoaIntent.Play>("включи что-нибудь Queen") { assertEquals("queen", query); assertTrue(artist) }
        ok<NoaIntent.Play>("включи мой плейлист для бега") { assertEquals("для бега", query); assertTrue(playlist) }
        ok<NoaIntent.Play>("увімкни плейліст ранок") { assertEquals("ранок", query); assertTrue(playlist) }
        ok<NoaIntent.Play>("включи музыку в случайном порядке") { assertEquals("", query); assertTrue(shuffle) }
        ok<NoaIntent.Play>("поставь Океан Эльзы") { assertEquals("океан эльзы", query) }
        ok<NoaIntent.Play>("увімкни Океан Ельзи") { assertEquals("океан ельзи", query) }
        ok<NoaIntent.Play>("включи радио") { assertEquals("", query) }
        ok<NoaIntent.Play>("включи Imagine Dragons в спотифай") { assertEquals("imagine dragons", query); assertEquals("спотифай", app) }
        ok<NoaIntent.Play>("поставь мой плейлист в случайном порядке") { assertTrue(playlist); assertTrue(shuffle); assertEquals("", query) }
        ok<NoaIntent.Play>("включи на ютубе клип Шакиры") { assertTrue(video); has(query, "шакир") }
        ok<NoaIntent.Play>("найди песню Despacito") { assertEquals("despacito", query) }
        ok<NoaIntent.Play>("play some jazz") { assertEquals("jazz", query) }
    }

    @Test fun playerControl() {
        media("поставь на паузу", NoaMedia.Control.PAUSE)
        media("пауза", NoaMedia.Control.PAUSE)
        media("постав на паузу", NoaMedia.Control.PAUSE)
        media("на паузу", NoaMedia.Control.PAUSE)
        media("стоп", NoaMedia.Control.PAUSE)
        media("выключи музыку", NoaMedia.Control.PAUSE)
        media("pause", NoaMedia.Control.PAUSE)
        media("продолжи", NoaMedia.Control.RESUME)
        media("продовжуй", NoaMedia.Control.RESUME)
        media("сними с паузы", NoaMedia.Control.RESUME)
        media("дальше", NoaMedia.Control.NEXT)
        media("далі", NoaMedia.Control.NEXT)
        media("следующий трек", NoaMedia.Control.NEXT)
        media("следующая песня", NoaMedia.Control.NEXT)
        media("наступний трек", NoaMedia.Control.NEXT)
        media("переключи трек", NoaMedia.Control.NEXT)
        media("next song", NoaMedia.Control.NEXT)
        media("предыдущий трек", NoaMedia.Control.PREV)
        media("попередня пісня", NoaMedia.Control.PREV)
        media("громче", NoaMedia.Control.LOUDER)
        media("сделай погромче", NoaMedia.Control.LOUDER)
        media("тише", NoaMedia.Control.QUIETER)
        media("сделай потише", NoaMedia.Control.QUIETER)
        media("зроби тихіше", NoaMedia.Control.QUIETER)
        media("перемешай", NoaMedia.Control.SHUFFLE_ON)
        media("что сейчас играет", NoaMedia.Control.WHAT)
        media("что за песня", NoaMedia.Control.WHAT)
        media("поставь на повтор", NoaMedia.Control.REPEAT)
        media("останови музыку", NoaMedia.Control.PAUSE)
        media("давай дальше", NoaMedia.Control.NEXT)
        media("включи следующую песню", NoaMedia.Control.NEXT)
        media("сделай музыку громче", NoaMedia.Control.LOUDER)
        media("вимкни музику", NoaMedia.Control.PAUSE)
        media("перемішай треки", NoaMedia.Control.SHUFFLE_ON)
        media("що зараз грає", NoaMedia.Control.WHAT)
    }

    // ---- навигация ----

    @Test fun navigation() {
        ok<NoaIntent.Route>("проложи маршрут до Киевской 5 в вейз") { has(place, "киевской 5"); assertEquals("waze", app) }
        ok<NoaIntent.Route>("поехали на работу к Илье") { assertEquals("илье", personQuery); assertEquals(PlaceKind.WORK, kind) }
        ok<NoaIntent.Route>("маршрут до ближайшей заправки") { has(place, "ближайшей заправки") }
        ok<NoaIntent.Route>("перестрой маршрут на Хрещатик 10") { assertEquals("хрещатик 10", place); assertEquals(null, app) }
        ok<NoaIntent.Route>("проклади маршрут до мами через гугл мапи") { assertEquals("мами", personQuery); assertEquals("google", app) }
        ok<NoaIntent.Route>("поехали домой") { assertEquals(PlaceKind.HOME, kind); assertEquals("", personQuery) }
        ok<NoaIntent.Route>("отвези меня к маме") { assertEquals("маме", personQuery) }
        ok<NoaIntent.Route>("как доехать до Ани") { assertEquals("ани", personQuery) }
        ok<NoaIntent.Route>("проложи маршрут к Олегу на работу через waze") {
            assertEquals("олегу", personQuery); assertEquals(PlaceKind.WORK, kind); assertEquals("waze", app)
        }
        ok<NoaIntent.Route>("навигатор до аэропорта") { has(place, "аэропорта") }
        ok<NoaIntent.Route>("поехали в аэропорт Борисполь") { has(place, "аэропорт борисполь") }
        ok<NoaIntent.Route>("проклади маршрут додому") { assertEquals(PlaceKind.HOME, kind) }
        ok<NoaIntent.Route>("построй маршрут до ТЦ Ocean Plaza в гугл картах") { has(place, "тц ocean plaza"); assertEquals("google", app) }
        ok<NoaIntent.Route>("проложи маршрут в яндекс навигаторе до Ани") { assertEquals("ани", personQuery); assertEquals("yandex", app) }
        ok<NoaIntent.Route>("едем к Илье домой") { assertEquals("илье", personQuery); assertEquals(PlaceKind.HOME, kind) }
        ok<NoaIntent.Route>("їдемо до Олега на роботу") { assertEquals("олега", personQuery); assertEquals(PlaceKind.WORK, kind) }
        ok<NoaIntent.Route>("проложи маршрут на Шевченко 12 через waze") { has(place, "шевченко 12"); assertEquals("waze", app) }
        ok<NoaIntent.Route>("navigate to central station") { has(place, "central station") }
        ok<NoaIntent.Route>("поїхали додому через вейз") { assertEquals(PlaceKind.HOME, kind); assertEquals("waze", app); assertEquals("", personQuery) }
        ok<NoaIntent.Route>("как добраться до вокзала") { has(place, "вокзала") }
        ok<NoaIntent.Route>("проложи маршрут к маме домой") { assertEquals("маме", personQuery); assertEquals(PlaceKind.HOME, kind) }
    }

    // ---- телефон ----

    @Test fun phone() {
        ok<NoaIntent.LaunchApp>("запусти телеграм") { assertEquals("телеграм", name) }
        ok<NoaIntent.LaunchApp>("открой калькулятор") { assertEquals("калькулятор", name) }
        ok<NoaIntent.LaunchApp>("открой камеру") { assertEquals("камеру", name) }
        ok<NoaIntent.LaunchApp>("open calculator") { assertEquals("calculator", name) }
        ok<NoaIntent.LaunchApp>("відкрий ютуб") { assertEquals("ютуб", name) }
        eq("будильник на 7:30", NoaIntent.Alarm(7, 30))
        eq("разбуди меня в 6 утра", NoaIntent.Alarm(6, 0))
        eq("постав будильник на 8:15", NoaIntent.Alarm(8, 15))
        eq("таймер на 10 минут", NoaIntent.Timer(600))
        eq("засеки 30 секунд", NoaIntent.Timer(30))
        eq("постав таймер на 2 години", NoaIntent.Timer(7200))
        eq("поставь таймер на 15 хвилин", NoaIntent.Timer(900))
        eq("включи фонарик", NoaIntent.Flashlight(true))
        eq("вимкни ліхтарик", NoaIntent.Flashlight(false))
        ok<NoaIntent.WebSearch>("загугли погоду в Киеве") { assertEquals("погоду в киеве", query) }
        ok<NoaIntent.WebSearch>("найди в гугле рецепт плова") { assertEquals("рецепт плова", query) }
        ok<NoaIntent.WebSearch>("пошукай в гуглі ціни на квитки") { assertEquals("ціни на квитки", query) }
        ok<NoaIntent.ShareData>("скопируй номер Анны") { assertEquals("анны", personQuery); assertEquals(NoaIntent.Data.PHONE, data); assertEquals("clipboard", target) }
        ok<NoaIntent.ShareData>("перенеси заметки Ильи в блокнот") { assertEquals("ильи", personQuery); assertEquals(NoaIntent.Data.NOTES, data); assertEquals("notes", target) }
        ok<NoaIntent.ShareData>("скинь адрес Оли в вотсап") { assertEquals(NoaIntent.Data.ADDRESS, data); assertEquals("app:вотсап", target) }
        ok<NoaIntent.ShareData>("сохрани номер Ильи в блокнот") { assertEquals(NoaIntent.Data.PHONE, data); assertEquals("notes", target) }
        eq("закрой приложение", NoaIntent.GoHome)
        eq("сверни", NoaIntent.GoHome)
        eq("закрий застосунок", NoaIntent.GoHome)
        eq("заблокируй", NoaIntent.Lock)
        eq("сделай резервную копию", NoaIntent.Backup)
        ok<NoaIntent.PhoneSettings>("включи вайфай") { assertEquals("wifi", what) }
        ok<NoaIntent.PhoneSettings>("увімкни блютуз") { assertEquals("bluetooth", what) }
    }

    // ---- цепочки ----

    @Test fun chains() {
        seq("позвони маме и включи музыку", NoaIntent.Call::class.java, NoaIntent.Play::class.java)
        seq("открой профиль Ани и позвони ей", NoaIntent.Open::class.java, NoaIntent.Call::class.java) {
            assertEquals("ани", (this[1] as NoaIntent.Call).personQuery)
        }
        seq("запиши Анну на завтра в 15:00 и отправь ей подтверждение", NoaIntent.CreateAppointment::class.java, NoaIntent.Message::class.java) {
            assertTrue((this[1] as NoaIntent.Message).aboutAppointment); assertEquals("анну", NoaParser.personOf(this[1]))
        }
        seq("поставь на паузу и позвони Илье", NoaIntent.Media::class.java, NoaIntent.Call::class.java)
        seq("включи фонарик и поставь таймер на 5 минут", NoaIntent.Flashlight::class.java, NoaIntent.Timer::class.java)
        seq("проложи маршрут до Ани и позвони ей", NoaIntent.Route::class.java, NoaIntent.Call::class.java)
        // «открой … и включи …» — одна команда в названном приложении
        ok<NoaIntent.Play>("открой ютуб мьюзик и включи Rammstein в случайном порядке") {
            assertEquals("rammstein", query); assertTrue(shuffle); assertTrue(app != null)
        }
    }
}
