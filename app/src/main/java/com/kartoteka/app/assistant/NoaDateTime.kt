package com.kartoteka.app.assistant

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Разбор даты и времени из речи на русском, украинском и английском.
 * Всё офлайн, без библиотек. Возвращает ближайшую подходящую дату относительно [now].
 */
object NoaDateTime {
    data class Parsed(val dateTime: LocalDateTime, val hadTime: Boolean, val hadDate: Boolean)

    private val MONTHS = mapOf(
        1 to listOf("январ", "січн", "janu"), 2 to listOf("феврал", "лют", "febr"),
        3 to listOf("март", "берез", "march", "mar"), 4 to listOf("апрел", "квітн", "april", "apr"),
        5 to listOf("мая", "травн", "may"), 6 to listOf("июн", "черв", "june", "jun"),
        7 to listOf("июл", "лип", "july", "jul"), 8 to listOf("август", "серп", "august", "aug"),
        9 to listOf("сентябр", "вересн", "septemb", "sep"), 10 to listOf("октябр", "жовтн", "octob", "oct"),
        11 to listOf("ноябр", "листопад", "novemb", "nov"), 12 to listOf("декабр", "грудн", "decemb", "dec"),
    )
    private val WEEKDAYS = mapOf(
        DayOfWeek.MONDAY to listOf("понедельник", "понеділок", "monday"),
        DayOfWeek.TUESDAY to listOf("вторник", "вівторок", "tuesday"),
        DayOfWeek.WEDNESDAY to listOf("сред", "серед", "wednesday"),
        DayOfWeek.THURSDAY to listOf("четверг", "четвер", "thursday"),
        DayOfWeek.FRIDAY to listOf("пятниц", "п'ятниц", "пʼятниц", "friday"),
        DayOfWeek.SATURDAY to listOf("суббот", "субот", "saturday"),
        DayOfWeek.SUNDAY to listOf("воскресень", "неділ", "sunday"),
    )

    fun parse(raw: String, now: LocalDateTime = LocalDateTime.now()): Parsed? {
        val s = " " + raw.lowercase().replace(Regex("[,.]"), " ").replace(Regex("\\s+"), " ") + " "
        val time = parseTime(s)
        // Число, которым оказалось время («в 8 утра»), не должно потом стать днём месяца — вырезаем его.
        val sForDate = timeRange(s)?.let { s.replaceRange(it, " ".repeat(it.last - it.first + 1)) } ?: s
        var date: LocalDate? = null
        var hadDate = false

        // относительные дни
        when {
            Regex("(?U)\\b(послезавтра|післязавтра|day after tomorrow)\\b").containsMatchIn(s) -> { date = now.toLocalDate().plusDays(2); hadDate = true }
            Regex("(?U)\\b(завтра|tomorrow)\\b").containsMatchIn(s) -> { date = now.toLocalDate().plusDays(1); hadDate = true }
            Regex("(?U)\\b(сегодня|сьогодні|today)\\b").containsMatchIn(s) -> { date = now.toLocalDate(); hadDate = true }
        }
        // день недели → ближайший будущий
        if (date == null) {
            for ((dow, words) in WEEKDAYS) if (words.any { s.contains(it) }) {
                var d = now.toLocalDate()
                do { d = d.plusDays(1) } while (d.dayOfWeek != dow)
                date = d; hadDate = true; break
            }
        }
        // число месяца: «12», «12-е», «12 числа», «12 октября», «12th»
        if (date == null) {
            val dayMatch = Regex("(?U)\\b(\\d{1,2})(?:\\s*(?:-?е|-?го|числа|th|st|nd|rd))?\\b").find(sForDate)
            val day = dayMatch?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..31 }
            if (day != null && !isTimeToken(sForDate, dayMatch.range, time)) {
                val month = MONTHS.entries.firstOrNull { (_, w) -> w.any { s.contains(" $it") } }?.key
                date = resolveDay(day, month, now.toLocalDate()); hadDate = true
            }
        }

        val finalDate = date ?: now.toLocalDate()
        var dt = LocalDateTime.of(finalDate, time ?: LocalTime.of(9, 0))
        // «сегодня» без даты, но время уже прошло → перенос на завтра
        if (!hadDate && time != null && dt.isBefore(now)) dt = dt.plusDays(1)
        if (time == null && !hadDate) return null
        return Parsed(dt, hadTime = time != null, hadDate = hadDate)
    }

    /** Диапазон строки, занятый временем (чтобы не спутать с числом дня). */
    private fun timeRange(s: String): IntRange? {
        Regex("(?U)\\b(?:в|о|at)?\\s*(\\d{1,2})[:.\\s](\\d{2})\\b").find(s)?.let { return it.range }
        Regex("(?U)\\b(?:в|о|at)\\s*(\\d{1,2})(?:\\s*(?:час\\w*|год\\w*|o'?clock|pm|am|рм|ам))?\\b").find(s)?.let { return it.range }
        return null
    }

    private fun parseTime(s: String): LocalTime? {
        // «в 14:30», «о 9:00», «at 12:00», «14 30»
        Regex("(?U)\\b(?:в|о|at)?\\s*(\\d{1,2})[:.\\s](\\d{2})\\b").find(s)?.let { m ->
            val h = m.groupValues[1].toInt(); val mi = m.groupValues[2].toInt()
            if (h in 0..23 && mi in 0..59) return withAmPm(s, m.range, LocalTime.of(h, mi))
        }
        // «в 12», «в 12 часов», «at 5 pm» — целый час
        Regex("(?U)\\b(?:в|о|at)\\s*(\\d{1,2})(?:\\s*(?:час\\w*|год\\w*|o'?clock|pm|am|рм|ам))?\\b").find(s)?.let { m ->
            val h = m.groupValues[1].toInt()
            if (h in 0..23) return withAmPm(s, m.range, LocalTime.of(h, 0))
        }
        return null
    }

    private fun withAmPm(s: String, range: IntRange, t: LocalTime): LocalTime {
        val from = (range.first).coerceIn(0, s.length)
        val after = s.substring(from).take(14)
        return when {
            (after.contains("pm") || after.contains("рм") || after.contains("вечер") || after.contains("вечор")) && t.hour < 12 -> t.plusHours(12)
            (after.contains("am") || after.contains("ам") || after.contains("утр") || after.contains("ранк")) && t.hour == 12 -> t.minusHours(12)
            else -> t
        }
    }

    /** Число оказалось частью времени (например «12» из «12:00»)? */
    private fun isTimeToken(s: String, range: IntRange, time: LocalTime?): Boolean {
        if (time == null) return false
        val before = if (range.first > 0) s[range.first - 1] else ' '
        val after = if (range.last + 1 < s.length) s[range.last + 1] else ' '
        return before == ':' || after == ':' || before == '.' || after == '.'
    }

    /** День с месяцем или без: без месяца берём ближайший будущий такой день. */
    private fun resolveDay(day: Int, month: Int?, today: LocalDate): LocalDate {
        if (month != null) {
            var d = runCatching { LocalDate.of(today.year, month, day) }.getOrNull() ?: today
            if (d.isBefore(today)) d = runCatching { LocalDate.of(today.year + 1, month, day) }.getOrNull() ?: d
            return d
        }
        var d = runCatching { today.withDayOfMonth(day) }.getOrNull()
        if (d == null || !d.isAfter(today)) {
            val next = today.plusMonths(1)
            d = runCatching { next.withDayOfMonth(day) }.getOrNull() ?: today
        }
        return d
    }
}
