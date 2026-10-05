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
        var s = " " + words(raw.lowercase()).replace(Regex("(?<=\\d)\\.(?=\\d)"), ":").replace(Regex("[,.]"), " ").replace(Regex("\\s+"), " ") + " "
        // «12 00» — так распознаватель иногда пишет «12:00».
        s = s.replace(Regex(" (\\d{1,2}) ([0-5]\\d) "), " $1:$2 ")
        var time = parseTime(s)
        // «на завтра на 12», «в пятницу на 3» — когда день назван словом, «на N» означает час.
        val dayWord = listOf("завтра", "сегодня", "сьогодні", "tomorrow", "today").any { s.contains(it) } ||
            WEEKDAYS.any { (_, w) -> w.any { s.contains(it) } }
        if (time == null && dayWord) {
            Regex(" (?:на|к|до|by) (\\d{1,2})(?: |$)").find(s)?.let { m ->
                val h = m.groupValues[1].toInt()
                if (h in 0..23) {
                    time = withAmPm(s, m.groups[1]!!.range, LocalTime.of(if (h in 1..7) h + 12 else h, 0))
                    s = s.replaceRange(m.groups[1]!!.range, " ".repeat(m.groupValues[1].length))
                }
            }
        }
        // Число, которым оказалось время («в 8 утра»), не должно потом стать днём месяца — вырезаем его.
        val sForDate = timeRange(s)?.let { s.replaceRange(it, " ".repeat(it.last - it.first + 1)) } ?: s
        val timeFound = time
        var date: LocalDate? = null
        var hadDate = false

        // относительные дни
        when {
            listOf("послезавтра", "післязавтра", "day after tomorrow").any { s.contains(it) } -> { date = now.toLocalDate().plusDays(2); hadDate = true }
            listOf("завтра", "tomorrow").any { s.contains(it) } -> { date = now.toLocalDate().plusDays(1); hadDate = true }
            listOf("сегодня", "сьогодні", "today").any { s.contains(it) } -> { date = now.toLocalDate(); hadDate = true }
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
            // Время уже вырезано из sForDate, поэтому любое оставшееся число 1–31 — это день.
            val dayMatch = Regex("\\d{1,2}").find(sForDate)
            val day = dayMatch?.value?.toIntOrNull()?.takeIf { it in 1..31 }
            if (day != null) {
                val month = MONTHS.entries.firstOrNull { (_, w) -> w.any { s.contains(" $it") } }?.key
                date = resolveDay(day, month, now.toLocalDate()); hadDate = true
            }
        }

        val finalDate = date ?: now.toLocalDate()
        var dt = LocalDateTime.of(finalDate, timeFound ?: LocalTime.of(9, 0))
        // «сегодня» без даты, но время уже прошло → перенос на завтра
        if (!hadDate && timeFound != null && dt.isBefore(now)) dt = dt.plusDays(1)
        if (timeFound == null && !hadDate) return null
        return Parsed(dt, hadTime = timeFound != null, hadDate = hadDate)
    }

    /** Часы словами → цифрами: «на дванадцяту», «в двенадцать». Только точные формы — «семья», «пятница» не трогаем. */
    private val HOUR_WORDS: Map<String, Int> = buildMap {
        fun put(h: Int, vararg w: String) = w.forEach { put(it, h) }
        put(1, "один", "одну", "одна", "першу", "первый")
        put(2, "два", "две", "дві", "другу")
        put(3, "три", "третю", "третий")
        put(4, "четыре", "чотири", "четверту")
        put(5, "пять", "п'ять", "пʼять", "п'яту", "пʼяту")
        put(6, "шесть", "шість", "шосту")
        put(7, "семь", "сім", "сьому")
        put(8, "восемь", "вісім", "восьму")
        put(9, "девять", "дев'ять", "девʼять", "дев'яту", "девʼяту")
        put(10, "десять", "десяту")
        put(11, "одиннадцать", "одинадцять", "одинадцяту")
        put(12, "двенадцать", "дванадцять", "дванадцяту", "полдень", "полудень")
    }

    fun isHourWord(w: String) = HOUR_WORDS.containsKey(w)

    private fun words(s: String): String = s.split(" ").joinToString(" ") { w ->
        HOUR_WORDS[w.trim(',', '.', '!', '?')]?.toString() ?: w
    }

    // Границы вокруг предлога «в/о/at» делаем по пробелам, а не \b (Cyrillic + Android regex).
    private val TIME_HM = Regex("(\\d{1,2})[:.](\\d{2})")
    private val TIME_H = Regex("(?:^| )(?:в|о|у|at)\\s+(\\d{1,2})(?: |$)")

    /** Диапазон строки, занятый временем (чтобы не спутать с числом дня). */
    private fun timeRange(s: String): IntRange? {
        TIME_HM.find(s)?.let { return it.range }
        TIME_H.find(s)?.let { return it.groups[1]!!.range }
        return null
    }

    private fun parseTime(s: String): LocalTime? {
        // «в 14:30», «о 9:00», «at 12:00»
        TIME_HM.find(s)?.let { m ->
            val h = m.groupValues[1].toInt(); val mi = m.groupValues[2].toInt()
            if (h in 0..23 && mi in 0..59) return withAmPm(s, m.range, LocalTime.of(h, mi))
        }
        // «в 12», «о 9», «at 5» (+ pm/утра обрабатываем ниже)
        TIME_H.find(s)?.let { m ->
            val h = m.groupValues[1].toInt()
            if (h in 0..23) return withAmPm(s, m.groups[1]!!.range, LocalTime.of(h, 0))
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
