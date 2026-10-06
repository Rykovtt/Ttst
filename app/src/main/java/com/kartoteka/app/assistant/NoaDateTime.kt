package com.kartoteka.app.assistant

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Разбор даты и времени из речи на русском, украинском и английском.
 * Всё офлайн, без библиотек. Возвращает ближайшую подходящую дату относительно [now].
 *
 * Понимает: «завтра в 15:00», «через два часа», «через полчаса», «за годину», «через 3 недели»,
 * «на следующей неделе в среду», «в следующий вторник», «на выходных», «в конце месяца», «пятнадцатого числа»,
 * «послезавтра утром», «без пятнадцати три», «половина третьего», «о пів на третю», «двенадцать ноль ноль».
 */
object NoaDateTime {
    /** [exactTime] — время названо часами (а не частью суток вроде «вечером»). */
    data class Parsed(val dateTime: LocalDateTime, val hadTime: Boolean, val hadDate: Boolean, val exactTime: Boolean = hadTime)

    private val MONTHS = mapOf(
        1 to listOf("январ", "січн", "janu"), 2 to listOf("феврал", "лют", "febr"),
        3 to listOf("март", "берез", "march", "mar"), 4 to listOf("апрел", "квітн", "april", "apr"),
        5 to listOf("мая", "травн", "may"), 6 to listOf("июн", "черв", "june", "jun"),
        7 to listOf("июл", "лип", "july", "jul"), 8 to listOf("август", "серп", "august", "aug"),
        9 to listOf("сентябр", "вересн", "septemb", "sep"), 10 to listOf("октябр", "жовтн", "octob", "oct"),
        11 to listOf("ноябр", "листопад", "novemb", "nov"), 12 to listOf("декабр", "грудн", "decemb", "dec"),
    )

    /** Дни недели целыми словами (после того как убраны апострофы и «ё»). «четверть» сюда не попадает. */
    private val WEEKDAYS: List<Pair<DayOfWeek, Regex>> = listOf(
        DayOfWeek.MONDAY to Regex("понедельник\\S*|понеділ\\S*|monday"),
        DayOfWeek.TUESDAY to Regex("вторник\\S*|вівтор\\S*|вторк\\S*|tuesday"),
        DayOfWeek.WEDNESDAY to Regex("сред[аыуе]|средой|серед[аиіу]|середою|wednesday"),
        DayOfWeek.THURSDAY to Regex("четверг\\S*|четвер(?:а|у|ом|е|ові)?|thursday"),
        DayOfWeek.FRIDAY to Regex("пятниц\\S*|пятнич\\S*|friday"),
        DayOfWeek.SATURDAY to Regex("суббот\\S*|субот\\S*|saturday"),
        DayOfWeek.SUNDAY to Regex("воскресен\\S*|неділ[яюіь]|неділею|sunday"),
    )

    private fun weekdayOf(tok: String): DayOfWeek? = WEEKDAYS.firstOrNull { it.second.matches(tok) }?.first

    // ---------- числа словами ----------

    /** Количественные числительные (и «часовые» формы вроде «на третю»). Ключи — без апострофов. */
    private val CARD: Map<String, Int> = buildMap {
        fun put(v: Int, vararg w: String) = w.forEach { put(it.replace("'", ""), v) }
        put(0, "ноль", "нуль")
        put(1, "один", "одну", "одна", "одно", "першу", "первый")
        put(2, "два", "две", "дві", "двух", "двум", "двох", "другу")
        put(3, "три", "трех", "трем", "трьох", "трьом", "третю", "третий")
        put(4, "четыре", "четырех", "четырем", "чотири", "чотирьох", "четверту")
        put(5, "пять", "пяти", "пяту", "пятью")
        put(6, "шесть", "шести", "шість", "шосту")
        put(7, "семь", "семи", "сім", "сьому")
        put(8, "восемь", "восьми", "вісім", "восьму")
        put(9, "девять", "девяти", "девяту")
        put(10, "десять", "десяти", "десяту")
        put(11, "одиннадцать", "одиннадцати", "одинадцять", "одинадцяти", "одинадцяту")
        put(12, "двенадцать", "двенадцати", "дванадцять", "дванадцяти", "дванадцяту", "полдень", "полудень", "полудне", "опівдні")
        put(13, "тринадцать", "тринадцати", "тринадцять")
        put(14, "четырнадцать", "четырнадцати", "чотирнадцять")
        put(15, "пятнадцать", "пятнадцати", "пятнадцять")
        put(16, "шестнадцать", "шестнадцати", "шістнадцять")
        put(17, "семнадцать", "семнадцати", "сімнадцять")
        put(18, "восемнадцать", "восемнадцати", "вісімнадцять")
        put(19, "девятнадцать", "девятнадцати", "девятнадцять")
        put(20, "двадцать", "двадцати", "двадцять")
        put(30, "тридцать", "тридцати", "тридцять")
        put(40, "сорок")
        put(50, "пятьдесят", "пятидесяти", "пятдесят")
        put(60, "шестьдесят", "шестидесяти", "шістдесят")
        put(0, "полночь", "північ", "опівночі", "полуночи")
        // «о десятій», «о дванадцятій» (місцевий відмінок) и родительный «без п'ятнадцяти»
        put(1, "першій"); put(2, "другій"); put(3, "третій"); put(4, "четвертій"); put(5, "пятій"); put(6, "шостій"); put(7, "сьомій")
        put(8, "восьмій"); put(9, "девятій"); put(10, "десятій"); put(11, "одинадцятій"); put(12, "дванадцятій")
        put(13, "тринадцяти"); put(14, "чотирнадцяти"); put(15, "пятнадцяти"); put(16, "шістнадцяти"); put(17, "сімнадцяти")
        put(18, "вісімнадцяти"); put(19, "девятнадцяти"); put(20, "двадцяти"); put(30, "тридцяти"); put(50, "пятдесяти"); put(60, "шістдесяти")
    }

    /** Порядковые: «пятнадцатого», «двадцать пятого» (число месяца) и часы («половина третьего»). */
    private val ORD: Map<String, Int> = buildMap {
        val ru = listOf(1 to "перв", 2 to "втор", 3 to "трет", 4 to "четверт", 5 to "пят", 6 to "шест", 7 to "седьм", 8 to "восьм", 9 to "девят",
            10 to "десят", 11 to "одиннадцат", 12 to "двенадцат", 13 to "тринадцат", 14 to "четырнадцат", 15 to "пятнадцат", 16 to "шестнадцат",
            17 to "семнадцат", 18 to "восемнадцат", 19 to "девятнадцат", 20 to "двадцат", 30 to "тридцат")
        val uk = listOf(1 to "перш", 2 to "друг", 3 to "трет", 4 to "четверт", 5 to "пят", 6 to "шост", 7 to "сьом", 8 to "восьм", 9 to "девят",
            10 to "десят", 11 to "одинадцят", 12 to "дванадцят", 13 to "тринадцят", 14 to "чотирнадцят", 15 to "пятнадцят", 16 to "шістнадцят",
            17 to "сімнадцят", 18 to "вісімнадцят", 19 to "девятнадцят", 20 to "двадцят", 30 to "тридцят")
        val endings = listOf("ого", "ое", "ому", "ым", "ом", "ої", "ій", "е", "є", "ое")
        for ((v, stem) in ru + uk) {
            for (e in endings) put(stem + e, v)
            if (stem == "трет") for (e in listOf("ьего", "ье", "ьему", "ьем", "ього", "ьої", "ьому", "ьою")) put(stem + e, v)
        }
        // «первый/второй/…» без хвоста не трогаем: они не отличимы от обычных слов.
    }

    private fun isMonthWord(w: String?) = w != null && (w == "числа" || w in setOf("мая", "июня", "июля", "лютого", "червня", "липня", "серпня") ||
        MONTHS.values.any { ms -> ms.any { w.startsWith(it) && it.length >= 4 } })

    private fun key(w: String) = w.trim(',', '.', '!', '?', ';', ':').replace("'", "")

    /** Слова числами: «двадцать пять» → «25», «пятнадцатого» → «15-го», «ноль ноль» → «00», «полтретьего» → «половина 3-го». */
    private fun numberize(tokens: List<String>): List<String> {
        val out = ArrayList<String>(tokens.size)
        var i = 0
        while (i < tokens.size) {
            val raw = tokens[i]; val k = key(raw)
            val next = tokens.getOrNull(i + 1)?.let(::key)
            val v = CARD[k]
            when {
                k == "ноль" || k == "нуль" -> {
                    val nv = next?.let { CARD[it] }
                    when {
                        nv == 0 && (next == "ноль" || next == "нуль") -> { out += "00"; i += 2 }
                        nv != null && nv in 1..9 -> { out += "0$nv"; i += 2 }
                        else -> { out += "0"; i++ }
                    }
                }
                v != null && v >= 20 && v % 10 == 0 -> {
                    val u = next?.let { CARD[it] }?.takeIf { it in 1..9 && (next in CARD) }
                    val o = next?.let { ORD[it] }?.takeIf { it in 1..9 }
                    when {
                        u != null -> { out += (v + u).toString(); i += 2 }
                        o != null -> { out += "${v + o}-го"; i += 2 }
                        else -> { out += v.toString(); i++ }
                    }
                }
                v != null -> { out += v.toString(); i++ }
                // «другого» без месяца — это «другого человека», а не 2-е число
                ORD[k] != null && !(k.startsWith("друг") && !isMonthWord(next)) -> { out += "${ORD[k]}-го"; i++ }
                k.startsWith("пол") && ORD[k.removePrefix("пол")] != null -> { out += "половина"; out += "${ORD[k.removePrefix("пол")]}-го"; i++ }
                else -> { out += raw; i++ }
            }
        }
        return out
    }

    // ---------- слова, которые относятся ко времени (их нет в именах и в тексте напоминаний) ----------

    private val DAYWORDS = setOf("завтра", "послезавтра", "сегодня", "сьогодні", "післязавтра", "tomorrow", "today", "позавтра", "вчера", "учора", "позавчера", "позавчора", "yesterday")
    private val VOCAB = setOf(
        "половина", "половине", "половину", "пів", "четверть", "четверти", "чверть", "чверті", "без",
        "утром", "утра", "утро", "днем", "дня", "вечером", "вечера", "вечор", "вечора", "вечір", "вечер", "след", "вечеру", "ввечері", "увечері", "ночью", "ночи", "ночі", "вночі", "уночі",
        "вранці", "зранку", "уранці", "ранку", "вдень", "удень", "обед", "обеда", "обід", "обіду", "выходные", "выходных", "выходные", "вихідні", "вихідних", "weekend",
        "месяца", "месяце", "месяц", "місяця", "місяці", "місяць", "недели", "неделе", "неделю", "неделя", "тижня", "тижні", "тиждень", "тижню",
        "следующей", "следующий", "следующую", "следующее", "следующем", "следующего", "наступного", "наступний", "наступну", "наступне", "наступному", "наступної", "наступній",
        "ближайший", "ближайшую", "ближайшее", "ближайшего", "найближчий", "найближчу", "найближче", "этой", "этот", "эту", "цього", "цей", "цю", "цієї", "будущей", "будущий",
        "мая", "июня", "июля", "лютого", "червня", "липня", "серпня",
        "часов", "часа", "час", "годин", "годину", "години", "годині", "минут", "минуты", "минуту", "минуті", "хвилин", "хвилини", "хвилину", "секунд", "секунды", "секунду", "секунди",
        "дней", "дні", "днів", "сутки", "добу", "полчаса", "півгодини", "пару", "несколько", "кілька", "полтора", "півтори", "півтора", "полторы",
        "конце", "кінці", "начале", "початку", "числа", "число", "числі", "числу", "чисел", "через", "спустя", "later", "next", "this", "in", "after", "hour", "hours", "minute", "minutes", "days", "day", "week", "weeks",
    )

    /** Слово относится ко времени: число словами, порядковое, часть суток, единица времени, день недели, месяц. */
    fun isDateWord(w0: String): Boolean {
        val w = key(w0.lowercase().replace('ё', 'е'))
        // «другу/другому» — это друг (человек), а не «на другу годину» и не 2-е число
        if (w.isEmpty() || w.startsWith("друг")) return false
        return CARD.containsKey(w) || ORD.containsKey(w) || w in VOCAB || w in DAYWORDS || weekdayOf(w) != null ||
            (w.startsWith("пол") && ORD.containsKey(w.removePrefix("пол"))) || MONTHS.values.any { ms -> ms.any { w.startsWith(it) && it.length >= 4 } }
    }

    /** Совместимость: раньше — только «часовые» слова. */
    fun isHourWord(w: String) = isDateWord(w) || (CARD.containsKey(key(w)) && !w.lowercase().startsWith("друг"))

    /** Токен целиком «про время»: цифры, «15:30», «15-го», слово из словаря. Для вырезания времени из текста напоминания. */
    fun isDateToken(tok: String): Boolean {
        val t = tok.lowercase().trim(',', '.', '!', '?', ';', ':')
        if (t.isEmpty()) return false
        if (Regex("\\d{1,2}([:.]\\d{2})?(-?(го|е|й|м|х|ого|числа|am|pm))?").matches(t)) return true
        // «другу» (другу позвонить) — это человек, а не «на другу годину»
        return isDateWord(t) && t != "другу"
    }

    /** Служебные слова, которые стоят перед датой («в пятницу», «через час», «к трём»). */
    fun isDatePrep(tok: String) = tok.lowercase().trim(',', '.') in setOf("в", "во", "на", "к", "ко", "о", "об", "у", "через", "за", "до", "по", "с", "со", "at", "in", "on", "by", "after", "before", "около", "близько", "приблизно", "примерно", "и", "і")

    /** «впятницу», «вчетыре», «назавтра» → «в пятницу», «в четыре», «на завтра»: распознаватель часто склеивает предлог со словом. */
    fun fixMerged(input: String): String = input.split(" ").joinToString(" ") { tok ->
        val lower = tok.lowercase().trim(',', '.', '!', '?', ';')
        if (lower.length < 4 || isKnownDateWord(lower)) tok
        else MERGE_PREPS.firstNotNullOfOrNull { p ->
            if (lower.startsWith(p) && lower.length >= p.length + 3 && isKnownDateWord(lower.removePrefix(p))) tok.substring(0, p.length) + " " + tok.substring(p.length) else null
        } ?: tok
    }

    private val MERGE_PREPS = listOf("на", "во", "в", "к")
    private fun isKnownDateWord(w: String): Boolean = isDateWord(w) || w in DAYWORDS

    // ---------- главный разбор ----------

    /** Текст → нормальный вид: строчные, без «ё», апострофов и знаков, числа словами → цифры. */
    private fun prepare(raw: String): String {
        val low = raw.lowercase().replace('ё', 'е').replace('’', '\'').replace('ʼ', '\'').replace('`', '\'')
        val toks = fixMerged(low).split(Regex("\\s+")).filter { it.isNotEmpty() }
        return numberize(toks).joinToString(" ")
            .replace(Regex("(?<=\\d)[.,](?=\\d)"), ":").replace(Regex("[,.!?;]"), " ").replace("'", "")
            .replace(Regex("\\s+"), " ").trim()
    }

    fun parse(raw: String, now: LocalDateTime = LocalDateTime.now(), workHours: Boolean = false): Parsed? {
        var s = " " + prepare(raw) + " "
        // «12 00» — так распознаватель иногда пишет «12:00» (а «двенадцать ноль ноль» мы уже превратили в «12 00»).
        s = s.replace(Regex(" (\\d{1,2}) ([0-5]\\d) "), " $1:$2 ")
        val today = now.toLocalDate()
        val consumed = mutableListOf<IntRange>()
        val toks = Regex("\\S+").findAll(s).toList()

        // «через два часа», «за годину», «через неделю», «in 3 days» — смещение от сейчас.
        var date: LocalDate? = null
        var hadDate = false
        relative(toks.map { it.value })?.let { r ->
            val range = toks[r.first].range.first..toks[r.last].range.last
            if (r.months == 0 && r.days == 0) return Parsed(now.plusSeconds(r.seconds).withSecond(0).withNano(0), hadTime = true, hadDate = true)
            if (r.seconds > 0) return Parsed(now.plusMonths(r.months.toLong()).plusDays(r.days.toLong()).plusSeconds(r.seconds).withSecond(0).withNano(0), true, true)
            date = today.plusMonths(r.months.toLong()).plusDays(r.days.toLong()); hadDate = true; consumed += range
        }

        val hit = findTime(s, workHours)
        var time = hit?.time
        hit?.let { consumed += it.range }
        val hasWeekday = toks.any { weekdayOf(it.value) != null }
        // «на завтра на 12», «в пятницу на 3» — когда день назван словом, «на N» означает час.
        val dayWord = toks.any { it.value in DAYWORDS } || hasWeekday || date != null
        if (time == null && dayWord) {
            Regex(" (?:на|к|до|by) (\\d{1,2})(?: |$)").find(s)?.let { m ->
                val h = m.groupValues[1].toInt()
                if (h in 0..23) {
                    val g = m.groups[1]!!
                    time = adjustHour(s, g.range.last + 1, h, 0, bias = true)
                    consumed += g.range
                }
            }
        }
        // Число, которым оказалось время («в 8 утра»), не должно потом стать днём месяца — вырезаем его.
        var sForDate = s
        for (r in consumed) sForDate = sForDate.replaceRange(r, " ".repeat(r.last - r.first + 1))

        // относительные дни
        if (date == null) {
            when {
                toks.any { it.value == "послезавтра" || it.value == "післязавтра" || it.value == "позавтра" } || s.contains("day after tomorrow") -> { date = today.plusDays(2); hadDate = true }
                toks.any { it.value == "завтра" || it.value == "tomorrow" } -> { date = today.plusDays(1); hadDate = true }
                toks.any { it.value == "сегодня" || it.value == "сьогодні" || it.value == "today" } -> { date = today; hadDate = true }
                // прошедшие дни нужны только вопросам «какое число было вчера»
                toks.any { it.value == "позавчера" || it.value == "позавчора" } -> { date = today.minusDays(2); hadDate = true }
                toks.any { it.value == "вчера" || it.value == "учора" || it.value == "yesterday" } -> { date = today.minusDays(1); hadDate = true }
            }
        }
        // «на следующей неделе», «на этой неделе», «в следующем месяце»
        val nextWeek = Regex(" (?:следующ\\S*|след|наступн\\S*|next|будущ\\S*) (?:недел\\S*|тижн\\S*|тиждень|week)").containsMatchIn(s)
        val thisWeek = Regex(" (?:этой|эту|цього|цієї|this) (?:недел\\S*|тижн\\S*|тиждень|week)").containsMatchIn(s)
        val nextMonth = Regex(" (?:следующ\\S*|след|наступн\\S*|next|будущ\\S*) (?:месяц\\S*|місяц\\S*|month)").containsMatchIn(s)
        val thisMon = today.minusDays((today.dayOfWeek.value - 1).toLong())
        // день недели → ближайший будущий; «следующий вторник» и «на следующей неделе в среду» — на следующей неделе
        if (date == null) {
            for ((i, t) in toks.withIndex()) {
                val dow = weekdayOf(t.value) ?: continue
                val before = toks.subList(maxOf(0, i - 3), i).map { it.value }
                val qNext = before.any { Regex("следующ\\S*|след|наступн\\S*|next|будущ\\S*").matches(it) }
                val qThis = before.any { Regex("этот|эту|этой|эта|цей|цю|цього|цієї|this|ближайш\\S*|найближч\\S*").matches(it) }
                date = when {
                    qNext || nextWeek -> thisMon.plusDays(7L + dow.value - 1)
                    thisWeek || qThis -> thisMon.plusDays(dow.value - 1L).let { if (it.isBefore(today)) it.plusDays(7) else it }
                    else -> { var d = today; do { d = d.plusDays(1) } while (d.dayOfWeek != dow); d }
                }
                hadDate = true; consumed += t.range; break
            }
        }
        if (date == null && nextWeek) { date = thisMon.plusDays(7); hadDate = true }
        // «на выходных» → ближайшая суббота; «в конце недели» → пятница
        if (date == null && toks.any { it.value in setOf("выходных", "выходные", "вихідних", "вихідні", "weekend") }) {
            var d = today; do { d = d.plusDays(1) } while (d.dayOfWeek != DayOfWeek.SATURDAY)
            date = d; hadDate = true
        }
        if (date == null && Regex("(?:конц\\S*|кінц\\S*|наприкінці|end) (?:недел\\S*|тижн\\S*|тиждень|of the week|week)").containsMatchIn(s)) {
            var d = today; do { d = d.plusDays(1) } while (d.dayOfWeek != DayOfWeek.FRIDAY)
            date = d; hadDate = true
        }
        // «в конце месяца», «в начале месяца»
        if (date == null && Regex("(?:конц\\S*|кінц\\S*|наприкінці|end) (?:of )?(?:the )?(?:месяц\\S*|місяц\\S*|month)").containsMatchIn(s)) {
            var d = today.withDayOfMonth(today.lengthOfMonth())
            if (!d.isAfter(today)) d = today.plusMonths(1).let { it.withDayOfMonth(it.lengthOfMonth()) }
            date = d; hadDate = true
        }
        if (date == null && Regex("(?:начал\\S*|початк\\S*|початку|beginning) (?:of )?(?:the )?(?:месяц\\S*|місяц\\S*|month)").containsMatchIn(s)) {
            date = today.plusMonths(1).withDayOfMonth(1); hadDate = true
        }
        // число месяца: «12», «15-го», «пятнадцатого числа», «12 октября», «12th»; «15-го следующего месяца»
        if (date == null) {
            // Время уже вырезано из sForDate, поэтому любое оставшееся число 1–31 — это день.
            val dayMatch = Regex("(?<!\\d)\\d{1,2}(?!\\d)(?!:)").find(sForDate)
            val day = dayMatch?.value?.toIntOrNull()?.takeIf { it in 1..31 }
            if (day != null) {
                val month = MONTHS.entries.firstOrNull { (_, w) -> w.any { s.contains(" $it") } }?.key
                date = if (nextMonth && month == null) {
                    val nm = today.plusMonths(1)
                    runCatching { nm.withDayOfMonth(day) }.getOrDefault(nm.withDayOfMonth(nm.lengthOfMonth()))
                } else resolveDay(day, month, today)
                hadDate = true
            } else if (nextMonth) { date = today.plusMonths(1); hadDate = true }
        }

        // часть суток без часа: «послезавтра утром», «сегодня вечером»
        var exact = true
        if (time == null) { time = partOfDayDefault(toks.map { it.value }, s); exact = false }

        val finalDate = date ?: today
        var dt = LocalDateTime.of(finalDate, time ?: LocalTime.of(9, 0))
        // «сегодня» без даты, но время уже прошло → перенос на завтра
        if (!hadDate && time != null && dt.isBefore(now)) dt = dt.plusDays(1)
        if (time == null && !hadDate) return null
        return Parsed(dt, hadTime = time != null, hadDate = hadDate, exactTime = time != null && exact)
    }

    /** Сколько прошло/осталось: часы-минуты-секунды, дни, месяцы. [first]/[last] — индексы слов во фразе. */
    private data class Rel(val months: Int, val days: Int, val seconds: Long, val first: Int, val last: Int)

    private enum class U { SEC, MIN, HOUR, DAY, WEEK, MONTH, YEAR }

    private fun unitOf(w: String): U? = when {
        w.startsWith("секунд") || w == "сек" || w.startsWith("second") || w == "sec" || w == "secs" -> U.SEC
        w.startsWith("минут") || w.startsWith("хвилин") || w == "мин" || w == "хв" || w.startsWith("minute") || w == "min" || w == "mins" -> U.MIN
        (w.startsWith("годин") && w != "годинник") || w == "час" || w == "часа" || w == "часов" || w == "часу" || w == "часы" || w == "ч" ||
            w.startsWith("hour") || w == "hr" || w == "hrs" -> U.HOUR
        w == "день" || w.startsWith("дня") || w.startsWith("дней") || w.startsWith("дні") || w == "днів" || w == "сутки" || w == "суток" ||
            w == "добу" || w == "доби" || w == "діб" || w.startsWith("day") -> U.DAY
        w.startsWith("недел") || w.startsWith("тижн") || w == "тиждень" || w.startsWith("week") -> U.WEEK
        w.startsWith("месяц") || w.startsWith("місяц") || w.startsWith("month") -> U.MONTH
        w == "год" || w == "года" || w == "лет" || w == "рік" || w == "року" || w == "роки" || w == "років" || w.startsWith("year") -> U.YEAR
        else -> null
    }

    /** Цепочка «число + единица» с индекса [from]: «2 часа 30 минут», «час», «полчаса», «пару минут», «an hour». */
    private fun components(t: List<String>, from: Int): Rel? {
        var j = from; var months = 0; var days = 0; var secs = 0L; var any = false
        fun add(u: U, n: Int) {
            when (u) {
                U.SEC -> secs += n; U.MIN -> secs += n * 60L; U.HOUR -> secs += n * 3600L
                U.DAY -> days += n; U.WEEK -> days += n * 7; U.MONTH -> months += n; U.YEAR -> months += n * 12
            }
        }
        while (j < t.size) {
            val w = t[j]
            val n = w.toIntOrNull()
            val nextU = t.getOrNull(j + 1)?.let(::unitOf)
            when {
                n != null && nextU != null -> { add(nextU, n); j += 2 }
                w == "полчаса" || w == "півгодини" || w == "півгодину" -> { secs += 1800; j++ }
                (w == "пів" || w == "половина" || w == "half") && t.getOrNull(j + 1).let { it == "часа" || it == "години" || it == "an" || it == "hour" } -> {
                    secs += 1800; j += if (t.getOrNull(j + 1) == "an") 3 else 2
                }
                w in setOf("полтора", "півтора", "півтори", "полторы") && nextU != null -> {
                    when (nextU) {
                        U.HOUR -> secs += 5400; U.MIN -> secs += 90; U.DAY -> { days += 1; secs += 43200 }
                        U.WEEK -> { days += 10; secs += 43200 }; U.MONTH -> { months += 1; days += 15 }; else -> { add(nextU, 1) }
                    }
                    j += 2
                }
                w in setOf("пару", "пары", "пара", "парочку", "couple") && (nextU != null || t.getOrNull(j + 1) == "of") -> {
                    val k = if (nextU != null) j + 1 else j + 2
                    val u = t.getOrNull(k)?.let(::unitOf) ?: break
                    add(u, 2); j = k + 1
                }
                w in setOf("несколько", "кілька", "декілька", "several", "few") && nextU != null -> { add(nextU, 3); j += 2 }
                (w == "a" || w == "an" || w == "one") && nextU != null -> { add(nextU, 1); j += 2 }
                unitOf(w) != null && (j == from || t[j - 1] in setOf("и", "і", "and", "та", "й")) && n == null -> { add(unitOf(w)!!, 1); j++ }
                w in setOf("и", "і", "and", "та", "й") && any -> { j++; continue }
                else -> break
            }
            any = true
        }
        // «и» в конце — не часть длительности
        var last = j - 1
        while (last >= from && t[last] in setOf("и", "і", "and", "та", "й")) last--
        return if (any && last >= from) Rel(months, days, secs, from, last) else null
    }

    private val REL_TRIGGERS = setOf("через", "за", "in", "after", "спустя")

    private fun relative(t: List<String>): Rel? {
        for (i in t.indices) if (t[i] in REL_TRIGGERS) {
            val r = components(t, i + 1) ?: continue
            return r.copy(first = i)
        }
        return null
    }

    /** Длительность из речи в секундах («5 минут», «полчаса», «час двадцать минут», «полтора часа»); null — нет. */
    fun durationSeconds(raw: String): Long? {
        val t = prepare(raw).split(" ").filter { it.isNotEmpty() }
        for (i in t.indices) {
            val r = components(t, i) ?: continue
            return r.seconds + r.days * 86_400L + r.months * 30L * 86_400L
        }
        return null
    }

    // ---------- время суток ----------

    private data class TimeHit(val time: LocalTime, val range: IntRange)

    private val PM_W = setOf("pm", "p.m", "пм", "рм", "вечера", "вечор", "вечером", "ввечері", "увечері", "вечір")
    private val AM_W = setOf("am", "a.m", "ам", "утра", "утром", "ранку", "вранці", "зранку", "уранці", "ранок")
    private val DAY_W = setOf("дня", "днем", "вдень", "удень")
    private val NIGHT_W = setOf("ночи", "ночью", "ночі", "вночі", "уночі")
    private val SKIP_W = setOf("мая", "июня", "июля", "лютого", "червня", "липня", "серпня",
        "часов", "часа", "час", "годин", "години", "годині", "hours", "hour", "oclock", "ровно", "рівно", "приблизно", "примерно", "около", "біля")

    private fun kindOf(w: String): Char? = when (w) {
        in PM_W -> 'P'; in AM_W -> 'A'; in DAY_W -> 'D'; in NIGHT_W -> 'N'; else -> null
    }

    /** Какая «часть суток» относится к часу, стоящему перед [end]: слово сразу после числа или, иначе, любое во фразе («завтра вечером в 8»). */
    private fun kindAfter(s: String, end: Int): Char? {
        val after = s.substring(minOf(end, s.length)).trim().split(" ").filter { it.isNotEmpty() }.take(4)
        for (w in after) { if (w in SKIP_W) continue; kindOf(w)?.let { return it }; break }
        // «через 2 дня в 5» — «дня» здесь не время суток
        val all = s.trim().split(" ")
        return all.withIndex().firstNotNullOfOrNull { (i, w) ->
            if (w == "дня" && all.getOrNull(i - 1)?.let { p -> p.toIntOrNull() != null || p in setOf("пару", "несколько", "кілька", "пары") } == true) null else kindOf(w)
        }
    }

    private fun applyKind(kind: Char?, h: Int): Int = when (kind) {
        'P' -> if (h in 1..11) h + 12 else h
        'A' -> if (h == 12) 0 else h
        'D' -> if (h in 1..6) h + 12 else h
        'N' -> if (h == 12) 0 else if (h in 6..11) h + 12 else h
        else -> h
    }

    /** Час [h]:[m], стоящий перед позицией [end]: поправка на «утра/вечера/дня/ночи». [bias] — «на 3» без пометки считаем за 15:00. */
    private fun adjustHour(s: String, end: Int, h: Int, m: Int, bias: Boolean = false, biasMax: Int = 7): LocalTime {
        val kind = kindAfter(s, end)
        val hh = if (kind == null && bias && h in 1..biasMax) h + 12 else applyKind(kind, h)
        return LocalTime.of(hh.coerceIn(0, 23), m)
    }

    private fun clock(h: Int, m: Int): LocalTime? = if (h in 0..23 && m in 0..59) LocalTime.of(h, m) else null

    private fun findTime(s: String, workHours: Boolean = false): TimeHit? {
        // «без пятнадцати три», «без четверти три», «за п'ять три» — минуты до часа
        Regex(" (?:без|за) (четверти|чверті|четверть|чверть|\\d{1,2}) (\\d{1,2}|час)(?:-го)?(?= |$)").find(s)?.let { m ->
            val min = m.groupValues[1].toIntOrNull() ?: 15
            val h = m.groupValues[2].toIntOrNull() ?: 1
            if (min in 1..30 && h in 1..24) {
                val base = if (h == 1) 12 else h - 1
                val g = m.groups[2]!!
                return TimeHit(adjustHour(s, g.range.last + 1, base, 60 - min, bias = true, biasMax = 5), m.range.first + 1..m.range.last)
            }
        }
        // «половина третьего», «пів на третю», «о пів до третьої» — полчаса до названного часа
        Regex(" (?:половин\\S*|пів|пол) (?:(?:на|до) )?(\\d{1,2})(?:-го)?(?= |$)").find(s)?.let { m ->
            val h = m.groupValues[1].toInt()
            if (h in 1..12) {
                val base = if (h == 1) 12 else h - 1
                return TimeHit(adjustHour(s, m.groups[1]!!.range.last + 1, base, 30, bias = true, biasMax = 5), m.range.first + 1..m.range.last)
            }
        }
        // «четверть третьего», «чверть на третю» — 15 минут третьего (2:15)
        Regex(" (?:четверть|чверть) (?:на )?(\\d{1,2})(?:-го)?(?= |$)").find(s)?.let { m ->
            val h = m.groupValues[1].toInt()
            if (h in 1..12) {
                val base = if (h == 1) 12 else h - 1
                return TimeHit(adjustHour(s, m.groups[1]!!.range.last + 1, base, 15, bias = true, biasMax = 5), m.range.first + 1..m.range.last)
            }
        }
        // «в 14:30», «о 9:00», «at 12:00»
        Regex("(?<!\\d)(\\d{1,2}):(\\d{2})(?!\\d)").find(s)?.let { m ->
            val h = m.groupValues[1].toInt(); val mi = m.groupValues[2].toInt()
            clock(h, mi)?.let { return TimeHit(adjustHour(s, m.range.last + 1, h, mi, bias = workHours), m.range) }
        }
        // «в час дня» — час без числа
        Regex(" (?:в|о|у|к) час(?= |$)").find(s)?.let { m ->
            return TimeHit(adjustHour(s, m.range.last + 1, 1, 0), m.range.first + 1..m.range.last)
        }
        // «в 12», «о 9», «at 5», «к 3» (+ pm/утра обрабатываем отдельно); «в 15-го» и «у 12 числа» — это день, а не час
        Regex("(?:^| )(?:в|о|у|к|at) +(\\d{1,2})(?!\\d)(?!-)(?=\\s|$|am|pm)(?! (?:числа|число|числі|числу))").find(s)?.let { m ->
            val h = m.groupValues[1].toInt()
            if (h in 0..23) return TimeHit(adjustHour(s, m.groups[1]!!.range.last + 1, h, 0, bias = workHours), m.groups[1]!!.range)
        }
        return null
    }

    /** «утром» → 9:00, «днём» → 14:00, «в обед» → 13:00, «вечером» → 19:00, «ночью» → 23:00. */
    private fun partOfDayDefault(t: List<String>, s: String): LocalTime? {
        if (s.contains(" после обеда") || s.contains(" після обіду")) return LocalTime.of(15, 0)
        for (w in t) when (w) {
            "утром", "утро", "вранці", "зранку", "уранці", "morning" -> return LocalTime.of(9, 0)
            "обед", "обід", "обеда", "обіду" -> return LocalTime.of(13, 0)
            "днем", "вдень", "удень", "afternoon" -> return LocalTime.of(14, 0)
            "вечеру" -> return LocalTime.of(18, 0)
            "вечером", "вечер", "ввечері", "увечері", "вечір", "evening", "tonight" -> return LocalTime.of(19, 0)
            "ночью", "вночі", "уночі", "night" -> return LocalTime.of(23, 0)
        }
        return null
    }

    /** Понедельник недели, в которой лежит [d]. */
    fun weekStart(d: LocalDate): LocalDate = d.minusDays((d.dayOfWeek.value - 1).toLong())

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
