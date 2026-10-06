package com.kartoteka.app.data

import com.kartoteka.app.assistant.NoaDateTime
import com.kartoteka.app.assistant.NoaIntent
import com.kartoteka.app.assistant.NoaMedia
import com.kartoteka.app.assistant.NoaParser
import com.kartoteka.app.assistant.NoaTools
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Раунд 2: обращение и «вода» в начале фразы, богатые даты и время, напоминания, локальные инструменты,
 * вопросы по картотеке, диалог (отмена/повтор/да-нет), шум распознавания речи (рус./укр./англ.).
 * Ошибки в одном методе собираются все сразу — виден весь список непонятых фраз.
 */
class NoaScenarios2Test {
    private val now = LocalDateTime.of(2026, 10, 4, 10, 0)      // воскресенье
    private val wed = LocalDateTime.of(2026, 10, 7, 10, 0)      // среда: для «на следующей неделе»
    private fun p(s: String) = NoaParser.parse(s, now)
    private fun d(day: Int, h: Int = 0, m: Int = 0) = LocalDateTime.of(2026, 10, day, h, m)
    private fun n(day: Int, h: Int = 0, m: Int = 0) = LocalDateTime.of(2026, 11, day, h, m)

    private val errors = mutableListOf<String>()

    @After fun report() {
        NoaParser.setAssistantName(null)
        if (errors.isNotEmpty()) fail("Не поняты ${errors.size}:\n" + errors.joinToString("\n"))
    }

    private inline fun <reified T : NoaIntent> ok(phrase: String, check: T.() -> Unit = {}) {
        val r = p(phrase)
        if (r !is T) { errors += "«$phrase» → $r (ждали ${T::class.simpleName})"; return }
        try { r.check() } catch (e: AssertionError) { errors += "«$phrase» → $r: ${e.message}" }
    }

    private fun eq(phrase: String, expected: NoaIntent) {
        val r = p(phrase)
        if (r != expected) errors += "«$phrase» → $r (ждали $expected)"
    }

    /** Дата/время фразы целиком (без команды). */
    private fun at(phrase: String, expected: LocalDateTime, hadTime: Boolean? = null, base: LocalDateTime = now) {
        val r = NoaDateTime.parse(phrase, base)
        when {
            r == null -> errors += "«$phrase» → не разобрано (ждали $expected)"
            r.dateTime != expected -> errors += "«$phrase» → ${r.dateTime} (ждали $expected)"
            hadTime != null && r.hadTime != hadTime -> errors += "«$phrase» → hadTime=${r.hadTime} (ждали $hadTime)"
        }
    }

    /** Запись с названным временем: «запиши Аню <фраза>» → дата и время. */
    private fun book(tail: String, expected: LocalDateTime, person: String = "аню") =
        ok<NoaIntent.CreateAppointment>("запиши Аню $tail") {
            assertEquals(person, personQuery); assertEquals(expected, dateTime)
        }

    private fun remind(phrase: String, text: String, expected: LocalDateTime?, hadTime: Boolean? = null) =
        ok<NoaIntent.Remind>(phrase) {
            assertEquals(text, this.text); assertEquals(expected, dateTime)
            if (hadTime != null) assertEquals(hadTime, this.hadTime)
        }

    private fun tool(phrase: String, kind: NoaIntent.ToolKind) = ok<NoaIntent.Tool>(phrase) { assertEquals(kind, this.kind) }

    private fun calc(phrase: String, result: Double) = ok<NoaIntent.Calc>(phrase) {
        val v = NoaTools.evaluate(expression)
        assertTrue("выражение «$expression» → $v, ждали $result", v != null && Math.abs(v - result) < 1e-6)
    }

    private fun convert(phrase: String, value: Double, from: String, to: String) = ok<NoaIntent.Convert>(phrase) {
        assertEquals(value, this.value, 1e-9); assertEquals(from, this.from); assertEquals(to, this.to)
    }

    private fun crm(phrase: String, kind: NoaIntent.CrmKind, person: String? = null, date: LocalDate? = null, time: LocalTime? = null,
                    period: NoaIntent.CrmPeriod? = null) = ok<NoaIntent.Crm>(phrase) {
        assertEquals(kind, this.kind)
        if (person != null) assertEquals(person, personQuery)
        if (date != null) assertEquals(date, this.date)
        if (time != null) assertEquals(time, this.time)
        if (period != null) assertEquals(period, this.period)
    }

    private fun day(dd: Int) = LocalDate.of(2026, 10, dd)

    // ---- 1. обращение по имени и слова-паразиты ----

    @Test fun addressPrefixAndFillers() {
        ok<NoaIntent.CreateAppointment>("Санта, запиши Аню на завтра") { assertEquals("аню", personQuery); assertEquals(d(5), dateTime!!.withHour(0).withMinute(0)) }
        ok<NoaIntent.Call>("Ноа ну позвони маме пожалуйста") { assertEquals("маме", personQuery) }
        ok<NoaIntent.Call>("Ноа, позвони Ане") { assertEquals("ане", personQuery) }
        ok<NoaIntent.Call>("Noa call Anna") { assertEquals("anna", personQuery) }
        ok<NoaIntent.Call>("Hey Noa call Anna please") { assertEquals("anna", personQuery) }
        ok<NoaIntent.Call>("слушай позвони Диме") { assertEquals("диме", personQuery) }
        ok<NoaIntent.Call>("слухай подзвони мамі") { assertEquals("мамі", personQuery) }
        ok<NoaIntent.Call>("Ноа, слухай, зателефонуй Олегу") { assertEquals("олегу", personQuery) }
        ok<NoaIntent.Call>("эй Ноа привет позвони Ане") { assertEquals("ане", personQuery) }
        ok<NoaIntent.Call>("Санта привіт зателефонуй Ані") { assertEquals("ані", personQuery) }
        ok<NoaIntent.Call>("ну короче позвони Диме") { assertEquals("диме", personQuery) }
        ok<NoaIntent.Call>("так значит позвони маме") { assertEquals("маме", personQuery) }
        ok<NoaIntent.Call>("Ноа Ноа позвони маме") { assertEquals("маме", personQuery) }
        ok<NoaIntent.Call>("Санта позвони маме пожалуйста спасибо") { assertEquals("маме", personQuery) }
        ok<NoaIntent.Call>("позвони маме будь ласка") { assertEquals("маме", personQuery) }
        ok<NoaIntent.Call>("будь добр позвони маме") { assertEquals("маме", personQuery) }
        ok<NoaIntent.Call>("Ноа давай позвони Ане") { assertEquals("ане", personQuery) }
        ok<NoaIntent.Call>("слушай Санта ну позвони уже Оле") { assertEquals("оле", personQuery) }
        ok<NoaIntent.OpenScreen>("пожалуйста открой календарь") { assertEquals(NoaIntent.Section.CALENDAR, section) }
        ok<NoaIntent.OpenScreen>("Ноа відкрий налаштування") { assertEquals(NoaIntent.Section.SETTINGS, section) }
        ok<NoaIntent.CreateAppointment>("слушай запиши Олега на пятницу в 15:00") { assertEquals("олега", personQuery); assertEquals(d(9, 15), dateTime) }
        ok<NoaIntent.CreateAppointment>("так запиши Свету на завтра в 12") { assertEquals("свету", personQuery); assertEquals(d(5, 12), dateTime) }
        ok<NoaIntent.Remind>("привіт Санта нагадай завтра о 10 зателефонувати Ані") { assertEquals("Зателефонувати Ані", text); assertEquals(d(5, 10), dateTime) }
        ok<NoaIntent.Message>("Ноа ну напиши Илье что опаздываю") { assertEquals("илье", personQuery); assertEquals("Опаздываю", text) }
        ok<NoaIntent.Find>("Санта найди Петрова") { assertEquals("петрова", query) }
        ok<NoaIntent.Media>("Ноа, пауза") { assertEquals(NoaMedia.Control.PAUSE, control) }
        ok<NoaIntent.Media>("Ноа давай дальше") { assertEquals(NoaMedia.Control.NEXT, control) }
        ok<NoaIntent.Flashlight>("Санта, включи фонарик") { assertTrue(on) }
        // имя, которое выбрал пользователь
        val own = setOf("джарвис")
        eq2("Джарвис открой календарь", own) { it is NoaIntent.OpenScreen }
        eq2("Джарвис, позвони маме", own) { it is NoaIntent.Call && it.personQuery == "маме" }
        NoaParser.setAssistantName("Мисс Пеппер")
        ok<NoaIntent.Call>("мисс Пеппер позвони маме") { assertEquals("маме", personQuery) }
        ok<NoaIntent.Call>("Санта позвони маме") { assertEquals("маме", personQuery) }      // стандартные имена остаются
        // слегка исказившееся имя («Санти») тоже отбрасывается
        ok<NoaIntent.Call>("Санти позвони маме") { assertEquals("маме", personQuery) }
        // одно имя без команды — не ломается
        eq("Ноа", NoaIntent.Unknown("Ноа"))
    }

    private fun eq2(phrase: String, names: Set<String>, check: (NoaIntent) -> Boolean) {
        val r = NoaParser.parse(phrase, now, names)
        if (!check(r)) errors += "«$phrase» (имена $names) → $r"
    }

    // ---- 2. даты и время ----

    @Test fun relativeTimeOffsets() {
        at("через 2 часа", d(4, 12), hadTime = true)
        at("через два часа", d(4, 12))
        at("через час", d(4, 11))
        at("через полчаса", d(4, 10, 30))
        at("через 45 минут", d(4, 10, 45))
        at("через сорок пять минут", d(4, 10, 45))
        at("через пятнадцать минут", d(4, 10, 15))
        at("через 5 минут", d(4, 10, 5))
        at("через 1 час 30 минут", d(4, 11, 30))
        at("через час тридцать минут", d(4, 11, 30))
        at("через полтора часа", d(4, 11, 30))
        at("через пару часов", d(4, 12))
        at("через несколько минут", d(4, 10, 3))
        at("через три часа", d(4, 13))
        at("через двадцать минут", d(4, 10, 20))
        at("через минуту", d(4, 10, 1))
        at("через годину", d(4, 11))
        at("за годину", d(4, 11))
        at("за пів години", d(4, 10, 30))
        at("за півгодини", d(4, 10, 30))
        at("через дві години", d(4, 12))
        at("через п'ять хвилин", d(4, 10, 5))
        at("через півтори години", d(4, 11, 30))
        at("за дві години", d(4, 12))
        at("in an hour", d(4, 11))
        at("in half an hour", d(4, 10, 30))
        at("in 30 minutes", d(4, 10, 30))
        at("in 2 hours", d(4, 12))
        at("after 15 minutes", d(4, 10, 15))
    }

    @Test fun relativeDays() {
        at("через три дня", d(7, 9), hadTime = false)
        at("через 3 дня в 15:00", d(7, 15), hadTime = true)
        at("через неделю", d(11, 9), hadTime = false)
        at("через две недели", d(18, 9))
        at("через месяц", n(4, 9))
        at("через пару дней", d(6, 9))
        at("через день", d(5, 9))
        at("через 10 дней", d(14, 9))
        at("через 2 дня в 10 утра", d(6, 10))
        at("через тиждень", d(11, 9))
        at("за два дні", d(6, 9))
        at("через місяць", n(4, 9))
        at("через три дні о 12", d(7, 12))
        at("in 2 days", d(6, 9))
        at("in a week", d(11, 9))
        at("через неделю в среду вечером", d(11, 19))
    }

    @Test fun weekdaysAndWeeks() {
        at("в среду", d(7, 9), hadTime = false)
        at("на следующей неделе в среду", d(7, 9))
        at("на следующей неделе", d(5, 9))
        at("в следующий вторник", d(6, 9))
        at("в следующую пятницу в 18:00", d(9, 18))
        at("наступного вівторка", d(6, 9))
        at("у наступну суботу", d(10, 9))
        at("на наступному тижні у середу о 10", d(7, 10))
        at("на этой неделе в пятницу", d(9, 9))
        at("в этот четверг", d(8, 9))
        // среда: календарная неделя уже началась, «следующий» — это неделя после этой
        at("в следующий вторник", d(13, 9), base = wed)
        at("на следующей неделе в среду", d(14, 9), base = wed)
        at("на следующей неделе", d(12, 9), base = wed)
        at("на этой неделе в пятницу", d(9, 9), base = wed)
        at("в среду", d(14, 9), base = wed)
        at("в пятницу", d(9, 9), base = wed)
        at("на выходных", d(10, 9), base = wed)
        at("на выходных", d(10, 9))
        at("на вихідних", d(10, 9))
        at("в конце недели", d(9, 9), base = wed)
        at("в конце месяца", d(31, 9))
        at("наприкінці місяця", d(31, 9))
        at("в начале месяца", n(1, 9))
        at("в следующем месяце", n(4, 9))
    }

    @Test fun dayOfMonthWords() {
        at("15-го", d(15, 9), hadTime = false)
        at("пятнадцатого числа", d(15, 9))
        at("пятнадцатого", d(15, 9))
        at("на пятнадцатое", d(15, 9))
        at("двадцать пятого", d(25, 9))
        at("двадцатого числа в 14:00", d(20, 14))
        at("тридцатого", d(30, 9))
        at("тридцать первого октября", d(31, 9))
        at("первого ноября", n(1, 9))
        at("второго числа", n(2, 9))
        at("п'ятнадцятого", d(15, 9))
        at("двадцять п'ятого", d(25, 9))
        at("першого листопада", n(1, 9))
        at("десятого жовтня о 16:30", d(10, 16, 30))
        at("5 ноября в 11 утра", n(5, 11))
        at("15-го в 10", d(15, 10))
        at("15-го следующего месяца", n(15, 9))
    }

    @Test fun partsOfDay() {
        at("послезавтра утром", d(6, 9), hadTime = true)
        at("завтра вечером", d(5, 19))
        at("сегодня вечером", d(4, 19))
        at("завтра днём", d(5, 14))
        at("завтра днем", d(5, 14))
        at("завтра ночью", d(5, 23))
        at("завтра в обед", d(5, 13))
        at("завтра после обеда", d(5, 15))
        at("послезавтра вечером в 8", d(6, 20))
        at("завтра в 8 вечера", d(5, 20))
        at("завтра в три дня", d(5, 15))
        at("завтра в три ночи", d(5, 3))
        at("завтра в 11 ночи", d(5, 23))
        at("завтра в 12 ночи", d(5, 0))
        at("завтра в 12 дня", d(5, 12))
        at("завтра в 7 утра", d(5, 7))
        at("завтра вранці", d(5, 9))
        at("післязавтра ввечері", d(6, 19))
        at("завтра о 3 дня", d(5, 15))
        at("завтра вдень", d(5, 14))
        at("в пятницу утром", d(9, 9))
        at("в субботу вечером", d(10, 19))
        at("вечером", d(4, 19))                       // 19:00 ещё впереди
        at("утром", d(5, 9))                           // 9:00 сегодня уже прошло
    }

    @Test fun clockPhrases() {
        at("завтра без пятнадцати три", d(5, 14, 45), hadTime = true)
        at("завтра половина третьего", d(5, 14, 30))
        at("завтра в половине третьего", d(5, 14, 30))
        at("завтра четверть третьего", d(5, 14, 15))
        at("завтра без десяти пять", d(5, 16, 50))
        at("завтра без четверти час", d(5, 12, 45))
        at("завтра половина девятого", d(5, 8, 30))
        at("завтра половина девятого вечера", d(5, 20, 30))
        at("завтра половина первого", d(5, 12, 30))
        at("завтра полтретьего", d(5, 14, 30))
        at("завтра о пів на третю", d(5, 14, 30))
        at("завтра пів до третьої", d(5, 14, 30))
        at("завтра чверть на третю", d(5, 14, 15))
        at("завтра без п'ятнадцяти три", d(5, 14, 45))
        at("завтра без чверті три", d(5, 14, 45))
        at("завтра о пів на дванадцяту", d(5, 11, 30))
        at("завтра о пів на першу", d(5, 12, 30))
        at("завтра в полдень", d(5, 12, 0))
        at("завтра в полночь", d(5, 0, 0))
        at("в полдень", d(4, 12))
        at("в полночь", d(5, 0))
        at("завтра в час дня", d(5, 13))
        at("завтра в час ночи", d(5, 1))
    }

    @Test fun numbersAsWords() {
        at("завтра в двенадцать ноль ноль", d(5, 12, 0), hadTime = true)
        at("завтра в десять ноль пять", d(5, 10, 5))
        at("завтра в пятнадцать тридцать", d(5, 15, 30))
        at("завтра в четырнадцать сорок пять", d(5, 14, 45))
        at("в двадцать три часа", d(4, 23))
        at("в двадцать один час", d(4, 21))
        at("завтра в семь тридцать утра", d(5, 7, 30))
        at("завтра в девять", d(5, 9))
        at("завтра в 12 00", d(5, 12, 0))
        at("завтра в 12.00", d(5, 12, 0))
        at("завтра в 15,30", d(5, 15, 30))
        at("завтра на пятнадцять тридцять", d(5, 15, 30))
        at("завтра о дванадцятій нуль нуль", d(5, 12, 0))
        at("завтра о дев'ятій ноль п'ять", d(5, 9, 5))
        at("завтра в двенадцать", d(5, 12))
        at("завтра в одиннадцать утра", d(5, 11))
    }

    @Test fun timersAndAlarmsWithWords() {
        eq("поставь таймер на пять минут", NoaIntent.Timer(300))
        eq("таймер на полчаса", NoaIntent.Timer(1800))
        eq("таймер на час", NoaIntent.Timer(3600))
        eq("таймер на полтора часа", NoaIntent.Timer(5400))
        eq("таймер на 1 час 30 минут", NoaIntent.Timer(5400))
        eq("засеки двадцать секунд", NoaIntent.Timer(20))
        eq("поставь таймер на две минуты", NoaIntent.Timer(120))
        eq("таймер на час двадцать минут", NoaIntent.Timer(4800))
        eq("постав таймер на десять хвилин", NoaIntent.Timer(600))
        eq("таймер на півгодини", NoaIntent.Timer(1800))
        eq("будильник на семь тридцать", NoaIntent.Alarm(7, 30))
        eq("разбуди меня через полчаса", NoaIntent.Alarm(10, 30))
        eq("разбуди меня в шесть утра", NoaIntent.Alarm(6, 0))
        eq("постав будильник на восьму ноль п'ять", NoaIntent.Alarm(8, 5))
    }

    @Test fun bookingsWithRichTimes() {
        book("через два дня в 15:00", d(6, 15))
        book("на следующей неделе в среду в 14:00", d(7, 14))
        book("в следующий вторник в 11:30", d(6, 11, 30))
        book("на пятнадцатое в 10", d(15, 10))
        book("послезавтра утром", d(6, 9))
        book("в пятницу без пятнадцати три", d(9, 14, 45))
        book("в среду в половине четвёртого", d(7, 15, 30))
        book("завтра в двенадцать ноль ноль", d(5, 12))
        book("на следующей неделе во вторник вечером", d(6, 19))
        book("на выходных в 12", d(10, 12))
        book("в конце месяца в 16:00", d(31, 16))
        book("на субботу в полдень", d(10, 12))
        book("завтра в три часа дня", d(5, 15))
        book("через неделю в 18:00", d(11, 18))
        book("наступного вівторка о 17:00", d(6, 17))
        book("за два дні о 10", d(6, 10))
        ok<NoaIntent.MoveAppointment>("перенеси Олега на следующую пятницу") { assertEquals("олега", personQuery); assertEquals(day(9), dateTime!!.toLocalDate()); assertTrue(hadDate) }
        ok<NoaIntent.MoveAppointment>("перенеси Олега на два часа позже") { assertEquals("олега", personQuery) }
        ok<NoaIntent.CancelAppointment>("отмени запись Ани на следующий вторник") { assertEquals("ани", personQuery); assertEquals(day(6), date) }
    }

    // ---- 3. напоминания ----

    @Test fun reminders() {
        remind("напомни мне завтра в 10 позвонить Ане", "Позвонить Ане", d(5, 10), true)
        remind("нагадай через годину купити хліб", "Купити хліб", d(4, 11), true)
        remind("напомни через час выпить таблетку", "Выпить таблетку", d(4, 11))
        remind("напомни в 18:00 забрать детей", "Забрать детей", d(4, 18))
        remind("напомни завтра утром позвонить в банк", "Позвонить в банк", d(5, 9), true)
        remind("напомни в пятницу оплатить аренду", "Оплатить аренду", d(9, 9), false)
        remind("напомни позвонить маме в 5 вечера", "Позвонить маме", d(4, 17))
        remind("напомни мне через 20 минут проверить духовку", "Проверить духовку", d(4, 10, 20))
        remind("поставь напоминание на завтра в 9 утра: отправить отчёт", "Отправить отчёт", d(5, 9))
        remind("создай напоминание на 15 октября в 12:00 оплатить налоги", "Оплатить налоги", d(15, 12))
        remind("remind me tomorrow at 9 to call John", "Call John", d(5, 9))
        remind("напомни мне позвонить Ане", "Позвонить Ане", null, false)
        remind("напомни что завтра в 10 встреча с Олегом", "Встреча с Олегом", d(5, 10))
        remind("нагадай у п'ятницю о 15:00 забрати посилку", "Забрати посилку", d(9, 15))
        remind("нагадай мені завтра зранку полити квіти", "Полити квіти", d(5, 9))
        remind("напомни через неделю про день рождения Лены", "День рождения Лены", d(11, 9))
        remind("напомни послезавтра в 8 утра взять документы", "Взять документы", d(6, 8))
        remind("напомни мне в полдень позвонить", "Позвонить", d(4, 12))
        remind("напомни в половине третьего позвонить Ане", "Позвонить Ане", d(4, 14, 30))
        remind("напомни через 2 часа", "", d(4, 12))
        remind("напомни завтра", "", d(5, 9), false)
        remind("напомни", "", null)
        remind("поставь напоминание позвонить врачу завтра в 11", "Позвонить врачу", d(5, 11))
        remind("додай нагадування на понеділок: купити квіти", "Купити квіти", d(5, 9))
        remind("Санта напомни мне завтра в 7 вечера купить подарок", "Купить подарок", d(5, 19))
        remind("напомни мне купить 2 литра молока", "Купить 2 литра молока", null)
        remind("напомни в субботу в 12 дня встретиться с Олей", "Встретиться с Олей", d(10, 12))
        remind("нагадай мені післязавтра ввечері подзвонити тітці", "Подзвонити тітці", d(6, 19))
        remind("напомни через пятнадцать минут выключить плиту", "Выключить плиту", d(4, 10, 15))
        remind("напомни в двенадцать ноль ноль позвонить маме", "Позвонить маме", d(4, 12))
    }

    // ---- 4. инструменты ----

    @Test fun timeDateWeekday() {
        for (ph in listOf("который час", "Который час?", "сколько времени", "скажи сколько времени", "сколько сейчас времени", "котра година",
            "скільки зараз часу", "який зараз час", "what time is it", "Ноа который час", "подскажи который час"))
            tool(ph, NoaIntent.ToolKind.TIME)
        for (ph in listOf("какое сегодня число", "какое число", "яке сьогодні число", "какая сегодня дата", "what's the date", "Санта, какое сегодня число?"))
            tool(ph, NoaIntent.ToolKind.DATE)
        for (ph in listOf("какой сегодня день недели", "какой день недели", "який сьогодні день", "что за день сегодня", "what day is it", "какой сегодня день"))
            tool(ph, NoaIntent.ToolKind.WEEKDAY)
        ok<NoaIntent.Tool>("какое число будет в пятницу") { assertEquals(NoaIntent.ToolKind.DATE, kind); assertEquals(day(9), date) }
        ok<NoaIntent.Tool>("какой день недели будет 15 октября") { assertEquals(NoaIntent.ToolKind.WEEKDAY, kind); assertEquals(day(15), date) }
        ok<NoaIntent.Tool>("какой день недели завтра") { assertEquals(day(5), date) }
        // это вопрос о человеке, а не инструмент
        ok<NoaIntent.PersonInfo>("какой день рождения у Ани") { assertEquals(NoaIntent.Topic.BIRTHDAY, topic) }
    }

    @Test fun calculator() {
        calc("посчитай 15 процентов от 2400", 360.0)
        calc("сколько будет 12 умножить на 7", 84.0)
        calc("корень из 144", 12.0)
        calc("сколько будет два плюс два", 4.0)
        calc("посчитай сто двадцать разделить на четыре", 30.0)
        calc("скільки буде 15 відсотків від 200", 30.0)
        calc("порахуй 7 помножити на 8", 56.0)
        calc("сколько будет 100 минус 15 процентов", 85.0)
        calc("10 процентов от 500", 50.0)
        calc("пять в квадрате", 25.0)
        calc("посчитай 2 в степени 10", 1024.0)
        calc("сколько будет 3 плюс 4 умножить на 2", 11.0)
        calc("сколько будет 2,5 умножить на 4", 10.0)
        calc("сколько будет 1000 разделить на 8", 125.0)
        calc("посчитай 1500 плюс 20 процентов", 1800.0)
        calc("calculate 12 times 5", 60.0)
        calc("what is 20 plus 22", 42.0)
        calc("сколько будет пять тысяч умножить на три", 15000.0)
        calc("квадратный корень из 81", 9.0)
        calc("вычисли двадцать пять умножить на четыре", 100.0)
        calc("сколько будет 7 умножить на 7 минус 9", 40.0)
        calc("посчитай 8 процентов от 1250", 100.0)
        ok<NoaIntent.Calc>("сколько будет 5 разделить на 0") { assertTrue(NoaTools.evaluate(expression) == null) }
    }

    @Test fun calculatorSafety() {
        // выражение — только цифры и знаки действий; ничего исполняемого
        assertEquals(null, NoaTools.evaluate("2+"))
        assertEquals(null, NoaTools.evaluate("System.exit(0)"))
        assertEquals(null, NoaTools.evaluate("1/0"))
        assertEquals(null, NoaTools.evaluate("sqrt(-4)"))
        assertEquals(14.0, NoaTools.evaluate("2+3*4")!!, 1e-9)
        assertEquals(512.0, NoaTools.evaluate("2^3^2")!!, 1e-9)
        assertEquals(-5.0, NoaTools.evaluate("-5")!!, 1e-9)
        assertEquals(50.0, NoaTools.evaluate("(100*50/100)")!!, 1e-9)
        assertEquals(null, NoaTools.toExpression("посчитай привет мир"))
        assertEquals("sqrt(144)", NoaTools.toExpression("корень из 144"))
        assertEquals("(2400*15/100)", NoaTools.toExpression("15 процентов от 2400"))
        assertEquals("1,5", NoaTools.format(1.5)); assertEquals("1.5", NoaTools.format(1.5, comma = false)); assertEquals("84", NoaTools.format(84.0))
    }

    @Test fun unitConversion() {
        convert("сколько миль в 10 километрах", 10.0, "km", "mi")
        convert("переведи 5 фунтов в килограммы", 5.0, "lb", "kg")
        convert("сколько километров в 20 милях", 20.0, "mi", "km")
        convert("сколько будет 100 фаренгейт в цельсиях", 100.0, "f", "c")
        convert("переведи 30 градусов цельсия в фаренгейты", 30.0, "c", "f")
        convert("сколько кг в 10 фунтах", 10.0, "lb", "kg")
        convert("convert 5 km to miles", 5.0, "km", "mi")
        convert("скільки кілометрів у 10 милях", 10.0, "mi", "km")
        convert("сколько сантиметров в 5 дюймах", 5.0, "in", "cm")
        convert("сколько литров в 3 галлонах", 3.0, "gal", "l")
        convert("сколько долларов в 100 гривнах", 100.0, "uah", "usd")
        convert("переведи 50 евро в гривны", 50.0, "eur", "uah")
        convert("скільки грамів у 2 унціях", 2.0, "oz", "g")
        convert("сколько футов в 10 метрах", 10.0, "m", "ft")
        convert("переведи десять миль в километры", 10.0, "mi", "km")
        convert("сколько градусов по цельсию 100 по фаренгейту", 100.0, "f", "c")
        assertEquals(6.2137, NoaTools.convert(NoaTools.Conversion(10.0, "km", "mi"))!!, 1e-3)
        assertEquals(2.2046, NoaTools.convert(NoaTools.Conversion(1.0, "kg", "lb"))!!, 1e-3)
        assertEquals(37.7778, NoaTools.convert(NoaTools.Conversion(100.0, "f", "c"))!!, 1e-3)
        assertEquals(212.0, NoaTools.convert(NoaTools.Conversion(100.0, "c", "f"))!!, 1e-9)
        assertEquals(null, NoaTools.convert(NoaTools.Conversion(100.0, "usd", "uah")))     // курсов без сети нет
        assertEquals(null, NoaTools.convert(NoaTools.Conversion(1.0, "km", "kg")))
    }

    // ---- 5. вопросы по картотеке ----

    @Test fun crmNextAppointment() {
        for (ph in listOf("кто следующий", "кто у меня следующий", "когда у меня следующая запись", "ближайшая запись", "хто наступний",
            "коли наступний запис", "когда следующий клиент", "кто дальше", "покажи следующую запись", "next appointment", "who is next",
            "Санта, кто следующий?", "какая у меня следующая запись", "а кто следующий клиент"))
            crm(ph, NoaIntent.CrmKind.NEXT, person = "")
        crm("когда у меня следующая запись с Аней", NoaIntent.CrmKind.NEXT, person = "аней")
        // «ближайшую запись» в переносе/отмене — не вопрос
        ok<NoaIntent.MoveAppointment>("перенеси ближайшую запись Ани на пятницу") { assertEquals("ани", personQuery) }
        ok<NoaIntent.CancelAppointment>("отмени ближайшую запись Олега") { assertEquals("олега", personQuery) }
    }

    @Test fun crmCounts() {
        crm("сколько записей на этой неделе", NoaIntent.CrmKind.COUNT, date = LocalDate.of(2026, 9, 28), period = NoaIntent.CrmPeriod.WEEK)
        crm("сколько записей на следующей неделе", NoaIntent.CrmKind.COUNT, date = day(5), period = NoaIntent.CrmPeriod.WEEK)
        crm("сколько записей сегодня", NoaIntent.CrmKind.COUNT, date = day(4), period = NoaIntent.CrmPeriod.DAY)
        crm("сколько клиентов завтра", NoaIntent.CrmKind.COUNT, date = day(5), period = NoaIntent.CrmPeriod.DAY)
        crm("сколько записей в этом месяце", NoaIntent.CrmKind.COUNT, date = day(1), period = NoaIntent.CrmPeriod.MONTH)
        crm("скільки записів на тиждень", NoaIntent.CrmKind.COUNT, period = NoaIntent.CrmPeriod.WEEK)
        crm("скільки у мене записів на п'ятницю", NoaIntent.CrmKind.COUNT, date = day(9), period = NoaIntent.CrmPeriod.DAY)
        crm("how many appointments tomorrow", NoaIntent.CrmKind.COUNT, date = day(5))
        crm("сколько у меня записей в субботу", NoaIntent.CrmKind.COUNT, date = day(10))
        crm("сколько встреч на этой неделе", NoaIntent.CrmKind.COUNT, period = NoaIntent.CrmPeriod.WEEK)
        crm("Ноа сколько записей на послезавтра", NoaIntent.CrmKind.COUNT, date = day(6))
        crm("скільки клієнтів сьогодні", NoaIntent.CrmKind.COUNT, date = day(4))
    }

    @Test fun crmFreeSlots() {
        crm("когда у меня окно завтра", NoaIntent.CrmKind.FREE, date = day(5))
        crm("есть ли окно в пятницу", NoaIntent.CrmKind.FREE, date = day(9))
        crm("когда у меня свободное время", NoaIntent.CrmKind.FREE, date = day(4))
        crm("коли в мене вільне вікно", NoaIntent.CrmKind.FREE, date = day(4))
        crm("є вікно на завтра", NoaIntent.CrmKind.FREE, date = day(5))
        crm("покажи свободное время на пятницу", NoaIntent.CrmKind.FREE, date = day(9))
        crm("when am I free tomorrow", NoaIntent.CrmKind.FREE, date = day(5))
        crm("когда у меня окошко в субботу", NoaIntent.CrmKind.FREE, date = day(10))
        crm("Санта когда я свободна завтра", NoaIntent.CrmKind.FREE, date = day(5))
    }

    @Test fun crmWhoAt() {
        crm("кто записан на 15:00", NoaIntent.CrmKind.WHO_AT, date = day(4), time = LocalTime.of(15, 0))
        crm("кто записан завтра в 15:00", NoaIntent.CrmKind.WHO_AT, date = day(5), time = LocalTime.of(15, 0))
        crm("что у меня в пятницу в три", NoaIntent.CrmKind.WHO_AT, date = day(9), time = LocalTime.of(15, 0))
        crm("хто в мене завтра о 12", NoaIntent.CrmKind.WHO_AT, date = day(5), time = LocalTime.of(12, 0))
        crm("занято ли завтра в 14:00", NoaIntent.CrmKind.WHO_AT, date = day(5), time = LocalTime.of(14, 0))
        crm("кто у меня в пятницу в 17:00", NoaIntent.CrmKind.WHO_AT, date = day(9), time = LocalTime.of(17, 0))
        crm("кто на субботу в 11", NoaIntent.CrmKind.WHO_AT, date = day(10), time = LocalTime.of(11, 0))
        // без времени — обычный план дня
        ok<NoaIntent.Agenda>("кто записан завтра") { assertEquals(day(5), date) }
        ok<NoaIntent.Agenda>("что у меня завтра") { assertEquals(day(5), date) }
    }

    @Test fun crmLastContact() {
        crm("когда я последний раз говорил с Аней", NoaIntent.CrmKind.LAST_CONTACT, person = "аней")
        crm("когда я в последний раз звонил Олегу", NoaIntent.CrmKind.LAST_CONTACT, person = "олегу")
        crm("коли я востаннє говорив з Анею", NoaIntent.CrmKind.LAST_CONTACT, person = "анею")
        crm("когда мы виделись с Ильёй", NoaIntent.CrmKind.LAST_CONTACT, person = "ильёй")
        crm("когда я в последний раз писал Маше", NoaIntent.CrmKind.LAST_CONTACT, person = "маше")
        crm("last time I talked to Anna", NoaIntent.CrmKind.LAST_CONTACT, person = "anna")
        crm("Санта когда я последний раз общался с Димой", NoaIntent.CrmKind.LAST_CONTACT, person = "димой")
        crm("коли ми бачились з Оленою востаннє", NoaIntent.CrmKind.LAST_CONTACT, person = "оленою")
    }

    @Test fun crmBirthdays() {
        crm("у кого скоро день рождения", NoaIntent.CrmKind.BIRTHDAYS, person = "", period = NoaIntent.CrmPeriod.MONTH)
        crm("у кого день рождения на этой неделе", NoaIntent.CrmKind.BIRTHDAYS, date = LocalDate.of(2026, 9, 28), period = NoaIntent.CrmPeriod.WEEK)
        crm("у кого день рождения сегодня", NoaIntent.CrmKind.BIRTHDAYS, date = day(4), period = NoaIntent.CrmPeriod.DAY)
        crm("хто іменинник завтра", NoaIntent.CrmKind.BIRTHDAYS, date = day(5))
        crm("ближайшие дни рождения", NoaIntent.CrmKind.BIRTHDAYS, period = NoaIntent.CrmPeriod.MONTH)
        crm("які дні народження в цьому місяці", NoaIntent.CrmKind.BIRTHDAYS, date = day(1), period = NoaIntent.CrmPeriod.MONTH)
        crm("у кого в следующем месяце день рождения", NoaIntent.CrmKind.BIRTHDAYS, date = LocalDate.of(2026, 11, 1), period = NoaIntent.CrmPeriod.MONTH)
        crm("у кого др на следующей неделе", NoaIntent.CrmKind.BIRTHDAYS, date = day(5), period = NoaIntent.CrmPeriod.WEEK)
        // вопрос про конкретного человека остаётся прежним
        ok<NoaIntent.PersonInfo>("когда день рождения у Ани") { assertEquals(NoaIntent.Topic.BIRTHDAY, topic); assertEquals("ани", personQuery) }
        ok<NoaIntent.PersonInfo>("коли день народження в Іллі") { assertEquals(NoaIntent.Topic.BIRTHDAY, topic) }
        // обычные команды не превращаются в вопросы
        ok<NoaIntent.CreateAppointment>("запиши Аню на завтра") { assertEquals("аню", personQuery) }
        ok<NoaIntent.Open>("открой Аню") { assertEquals("аню", personQuery) }
    }

    // ---- 6. диалог ----

    @Test fun dismissAndRepeat() {
        for (ph in listOf("отмена", "забудь", "не надо", "отбой", "нічого", "never mind", "неважно", "відміна", "Санта отмена", "ничего не нужно", "забудь это", "forget it"))
            ok<NoaIntent.Dismiss>(ph)
        for (ph in listOf("повтори", "ещё раз", "повтори пожалуйста", "что ты сказала", "ще раз", "повтори ещё раз", "скажи ещё раз", "повтори ще раз",
            "Ноа повтори", "что-что", "say that again", "повтори последнее", "повтори, пожалуйста"))
            ok<NoaIntent.Repeat>(ph)
        // старое поведение не сломано
        ok<NoaIntent.Media>("стоп") { assertEquals(NoaMedia.Control.PAUSE, control) }
        ok<NoaIntent.Media>("поставь на повтор") { assertEquals(NoaMedia.Control.REPEAT, control) }
        ok<NoaIntent.CancelAppointment>("отмени Илью") { assertEquals("илью", personQuery) }
    }

    @Test fun yesNoWordings() {
        val yes = listOf("да", "Да, конечно", "ага", "давай", "угу", "подтверждаю", "так", "звичайно", "добре", "yes", "ok", "окей", "ну да", "Санта да",
            "конечно", "ладно", "хорошо", "yeah", "авжеж", "гаразд", "підтверджую", "да давай", "да, записывай", "подтверди", "sure")
        val no = listOf("нет", "не надо", "ні", "отмена", "неа", "нет, на пятницу", "не в три а в четыре", "no", "cancel", "стоп", "не нужно", "ні не треба",
            "отмени", "nope", "скасуй", "ой нет", "нет нет")
        val neither = listOf("завтра", "в пятницу", "Олю", "что-то", "")
        for (s in yes) if (NoaParser.yesNo(s) != true) errors += "«$s» должно быть «да», а получилось ${NoaParser.yesNo(s)}"
        for (s in no) if (NoaParser.yesNo(s) != false) errors += "«$s» должно быть «нет», а получилось ${NoaParser.yesNo(s)}"
        for (s in neither) if (NoaParser.yesNo(s) != null) errors += "«$s» не ответ, а получилось ${NoaParser.yesNo(s)}"
    }

    @Test fun bareHoursOfAppointmentsAreAfternoon() {
        // запись «в 3» — это 15:00; «в 3 утра» — всё-таки ночь; сам разбор даты (для будильника) час не трогает
        book("на завтра в 3", d(5, 15))
        book("в пятницу в 7", d(9, 19))
        book("завтра в 8", d(5, 8))
        book("завтра в 3 утра", d(5, 3))
        book("завтра в 3:30", d(5, 15, 30))
        book("завтра в 12", d(5, 12))
        book("завтра в 11", d(5, 11))
        at("завтра в 3", d(5, 3))
        eq("будильник на 7", NoaIntent.Alarm(7, 0))
        eq("будильник на 7 вечера", NoaIntent.Alarm(19, 0))
        eq("разбуди меня в 3", NoaIntent.Alarm(3, 0))
        eq("будильник на 6:45", NoaIntent.Alarm(6, 45))
        ok<NoaIntent.Call>("позвони другу") { assertEquals("другу", personQuery) }
        ok<NoaIntent.Message>("напиши другому что опаздываю") { assertEquals("Опаздываю", text) }
        ok<NoaIntent.Call>("позвони маме через час") { assertEquals("маме", personQuery) }
        ok<NoaIntent.Message>("напиши Ане что буду через 10 минут") { assertEquals("Буду через 10 минут", text) }
        // цепочка с напоминанием и калькулятором
        seqOf("позвони Ане и напомни мне завтра в 10 купить хлеб", NoaIntent.Call::class.java, NoaIntent.Remind::class.java)
        seqOf("сколько будет 2 плюс 2 и позвони Ане", NoaIntent.Calc::class.java, NoaIntent.Call::class.java)
        seqOf("позвони Ане и посчитай 2 плюс 2", NoaIntent.Call::class.java, NoaIntent.Calc::class.java)
        // текст сообщения диктуется как есть — повторы в нём не чистим
        ok<NoaIntent.Message>("напиши Ане что очень очень скучаю") { assertEquals("Очень очень скучаю", text) }
        ok<NoaIntent.AddNote>("добавь заметку Ане: ха ха ха смешной") { assertEquals("ха ха ха смешной", text) }
    }

    @Test fun spokenVariantsFoundInReview() {
        ok<NoaIntent.CreateAppointment>("запиши Олега на пятницу на пол третьего") { assertEquals("олега", personQuery); assertEquals(d(9, 14, 30), dateTime) }
        ok<NoaIntent.CreateAppointment>("запиши Машу на след неделю в среду") { assertEquals("машу", personQuery); assertEquals(d(7, 9), dateTime) }
        ok<NoaIntent.CreateAppointment>("запиши Машу в след. пятницу") { assertEquals("машу", personQuery); assertEquals(d(9, 9), dateTime) }
        remind("нагадай мені сьогодні о шостій вечора купити молоко", "Купити молоко", d(4, 18))
        remind("напомни мне завтра в 10:30 утра позвонить врачу", "Позвонить врачу", d(5, 10, 30))
        remind("напомни сегодня вечером купить цветы", "Купить цветы", d(4, 19))
        remind("напомни пятнадцатого числа заплатить за интернет", "Заплатить за интернет", d(15, 9))
        remind("напомни на выходных позвонить бабушке", "Позвонить бабушке", d(10, 9))
        calc("сколько будет двадцать процентов от пятисот", 100.0)
        calc("корень квадратный из ста сорока четырёх", 12.0)
        calc("посчитай 45 умножить на 12 плюс 8", 548.0)
        calc("сколько будет 150 разделить на 6", 25.0)
        convert("сколько в одном километре миль", 1.0, "km", "mi")
        convert("переведи 70 кг в фунты", 70.0, "kg", "lb")
        convert("сколько градусов по фаренгейту будет 25 цельсия", 25.0, "c", "f")
        convert("сколько будет 1000 гривен в долларах", 1000.0, "uah", "usd")
        ok<NoaIntent.Agenda>("кто у меня сегодня после обеда") { assertEquals(day(4), date) }
        crm("сколько человек записано на сегодня", NoaIntent.CrmKind.COUNT, person = "", date = day(4))
        crm("когда я последний раз звонила маме", NoaIntent.CrmKind.LAST_CONTACT, person = "маме")
        crm("у кого в октябре день рождения", NoaIntent.CrmKind.BIRTHDAYS, period = NoaIntent.CrmPeriod.MONTH)
        ok<NoaIntent.CancelAppointment>("отмена записи") { assertEquals("", personQuery) }
        ok<NoaIntent.CancelAppointment>("отмена записи Ани на завтра") { assertEquals("ани", personQuery); assertEquals(day(5), date) }
        ok<NoaIntent.Dismiss>("не надо записывать")
        ok<NoaIntent.Dismiss>("давай не будем")
        ok<NoaIntent.Dismiss>("не звони ей")
        ok<NoaIntent.Repeat>("повтори что ты сказала")
        ok<NoaIntent.Repeat>("что ты сейчас сказала")
        ok<NoaIntent.Tool>("какое число было вчера") { assertEquals(day(3), date) }
        ok<NoaIntent.Tool>("какой день недели был позавчера") { assertEquals(day(2), date); assertEquals(NoaIntent.ToolKind.WEEKDAY, kind) }
        at("вчера", d(3, 9)); at("позавчера в 15:00", d(2, 15))
        // «вечером» — не точное время: «кто записан вечером» остаётся планом дня
        ok<NoaIntent.Agenda>("кто у меня завтра вечером") { assertEquals(day(5), date) }
    }

    private fun seqOf(phrase: String, vararg types: Class<out NoaIntent>) {
        val steps = (p(phrase) as? NoaIntent.Sequence)?.steps
        if (steps == null || steps.size != types.size || steps.zip(types).any { (s, t) -> !t.isInstance(s) }) errors += "«$phrase» → ${p(phrase)} (ждали цепочку ${types.joinToString { it.simpleName }})"
    }

    // ---- 7. шум распознавания речи ----

    @Test fun asrNoise() {
        // перепутанные гласные в команде
        ok<NoaIntent.CreateAppointment>("запеши Аню на завтра") { assertEquals("аню", personQuery); assertEquals(day(5), dateTime!!.toLocalDate()) }
        ok<NoaIntent.Message>("напеши Ане что опаздываю") { assertEquals("ане", personQuery); assertEquals("Опаздываю", text) }
        ok<NoaIntent.CancelAppointment>("атмени запись Ани") { assertEquals("ани", personQuery) }
        ok<NoaIntent.Call>("пазвони маме") { assertEquals("маме", personQuery) }
        // слипшиеся слова
        book("впятницу в 15:00", d(9, 15))
        book("назавтра в 15:00", d(5, 15))
        book("навторник в 11:00", d(6, 11))
        book("на пятницу вдвенадцать ноль ноль", d(9, 12))
        book("на завтра вчетырнадцать тридцать", d(5, 14, 30))
        // повторы подряд
        ok<NoaIntent.CreateAppointment>("запиши запиши Аню на завтра") { assertEquals("аню", personQuery) }
        ok<NoaIntent.Call>("позвони позвони маме") { assertEquals("маме", personQuery) }
        ok<NoaIntent.CreateAppointment>("запиши Аню на завтра завтра в 12") { assertEquals("аню", personQuery); assertEquals(d(5, 12), dateTime) }
        // склонения имён
        ok<NoaIntent.Call>("позвони Анне") { assertEquals("анне", personQuery) }
        ok<NoaIntent.Call>("позвони Илье Рыкову") { assertEquals("илье рыкову", personQuery) }
        ok<NoaIntent.Call>("подзвони Олені Петренко") { assertEquals("олені петренко", personQuery) }
        ok<NoaIntent.CreateAppointment>("запиши Ольгу Петровну на завтра в 12") { assertEquals("ольгу петровну", personQuery) }
        ok<NoaIntent.Message>("напиши Михайлу что задержусь") { assertEquals("михайлу", personQuery) }
        // знаки, регистр, лишние пробелы
        ok<NoaIntent.CreateAppointment>("Запиши, Аню, на завтра, в три часа дня.") { assertEquals("аню", personQuery); assertEquals(d(5, 15), dateTime) }
        ok<NoaIntent.CreateAppointment>("ЗАПИШИ АНЮ НА ЗАВТРА В 15:00") { assertEquals("аню", personQuery); assertEquals(d(5, 15), dateTime) }
        ok<NoaIntent.CreateAppointment>("запиши   Аню    на   завтра   в   15:00") { assertEquals(d(5, 15), dateTime) }
        ok<NoaIntent.CreateAppointment>("запиши Аню на завтра в 15 ноль ноль") { assertEquals(d(5, 15), dateTime) }
        ok<NoaIntent.CreateAppointment>("Запиши Аню на завтра на пятнадцять тридцять") { assertEquals(d(5, 15, 30), dateTime) }
        ok<NoaIntent.CreateAppointment>("запиши Аню на завтра о пів на третю") { assertEquals(d(5, 14, 30), dateTime) }
        // числа словами не мешают имени
        ok<NoaIntent.CreateAppointment>("запиши Свету Петрову на двадцатое в пятнадцать ноль ноль") { assertEquals("свету петрову", personQuery); assertEquals(d(20, 15), dateTime) }
        ok<NoaIntent.CreateAppointment>("запиши Диму через две недели в десять") { assertEquals("диму", personQuery); assertEquals(d(18, 10), dateTime) }
        // обычные команды после чистки не меняются
        ok<NoaIntent.Call>("позвони маме") { assertEquals("маме", personQuery) }
        ok<NoaIntent.Media>("дальше") { assertEquals(NoaMedia.Control.NEXT, control) }
        ok<NoaIntent.Media>("давай дальше") { assertEquals(NoaMedia.Control.NEXT, control) }
        ok<NoaIntent.Message>("напиши Илье в телеграм что опаздываю") { assertEquals("илье", personQuery); assertEquals(NoaIntent.Channel.TELEGRAM, channel) }
        ok<NoaIntent.Play>("включи Rammstein") { assertEquals("Rammstein".lowercase(), query.lowercase()) }
    }
}
