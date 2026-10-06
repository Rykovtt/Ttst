package com.kartoteka.app.assistant

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Локальные «инструменты» ассистента: калькулятор (безопасный разбор выражения, без eval), перевод единиц
 * (без сети — валюты не считаем), свободные окна дня. Всё чистые функции — легко тестировать.
 */
object NoaTools {
    // ---------- числа словами для калькулятора ----------

    private val UNITS = mapOf(
        "ноль" to 0, "нуль" to 0, "один" to 1, "одна" to 1, "одну" to 1, "два" to 2, "две" to 2, "дві" to 2, "три" to 3, "четыре" to 4, "чотири" to 4,
        "пять" to 5, "шесть" to 6, "шість" to 6, "семь" to 7, "сім" to 7, "восемь" to 8, "вісім" to 8, "девять" to 9,
        "десять" to 10, "одиннадцать" to 11, "одинадцять" to 11, "двенадцать" to 12, "дванадцять" to 12, "тринадцать" to 13, "тринадцять" to 13,
        "четырнадцать" to 14, "чотирнадцять" to 14, "пятнадцать" to 15, "пятнадцять" to 15, "шестнадцать" to 16, "шістнадцять" to 16,
        "семнадцать" to 17, "сімнадцять" to 17, "восемнадцать" to 18, "вісімнадцять" to 18, "девятнадцать" to 19, "девятнадцять" to 19,
        "двадцать" to 20, "двадцять" to 20, "тридцать" to 30, "тридцять" to 30, "сорок" to 40, "пятьдесят" to 50, "пятдесят" to 50,
        "шестьдесят" to 60, "шістдесят" to 60, "семьдесят" to 70, "сімдесят" to 70, "восемьдесят" to 80, "вісімдесят" to 80, "девяносто" to 90, "дев'яносто" to 90, "девяносто" to 90,
        // родительный и другие падежи: «процентов от пятисот», «из ста сорока четырёх», «в одном километре»
        "одном" to 1, "одного" to 1, "одному" to 1, "одним" to 1, "одной" to 1, "двух" to 2, "трех" to 3, "четырех" to 4, "пяти" to 5, "шести" to 6, "семи" to 7,
        "восьми" to 8, "девяти" to 9, "десяти" to 10, "пятнадцати" to 15, "двадцати" to 20, "тридцати" to 30, "сорока" to 40, "пятидесяти" to 50,
        "ста" to 100, "двухсот" to 200, "трехсот" to 300, "четырехсот" to 400, "пятисот" to 500, "шестисот" to 600, "семисот" to 700, "восьмисот" to 800, "девятисот" to 900,
        "сто" to 100, "двести" to 200, "двісті" to 200, "триста" to 300, "четыреста" to 400, "чотириста" to 400, "пятьсот" to 500, "пятсот" to 500,
        "шестьсот" to 600, "шістсот" to 600, "семьсот" to 700, "сімсот" to 700, "восемьсот" to 800, "вісімсот" to 800, "девятьсот" to 900, "девятсот" to 900,
    )
    private val THOUSAND = setOf("тысяча", "тысячи", "тысяч", "тисяча", "тисячі", "тисяч", "thousand")
    private val MILLION = setOf("миллион", "миллиона", "миллионов", "мільйон", "мільйони", "мільйонів", "million")

    /** Число словами/цифрами с позиции [i]: («двадцать пять», 2) / («5 тысяч», 2); null — не число. */
    private fun numberAt(t: List<String>, i: Int): Pair<Double, Int>? {
        var total = 0.0; var cur = 0.0; var used = 0; var any = false
        var j = i
        while (j < t.size) {
            val w = t[j].replace("'", "")
            val d = w.replace(',', '.').toDoubleOrNull()
            when {
                d != null && !any && j == i -> { cur = d; any = true; used++ }
                UNITS.containsKey(w) && (d == null) -> { cur += UNITS.getValue(w); any = true; used++ }
                w in THOUSAND && any -> { total += (if (cur == 0.0) 1.0 else cur) * 1000; cur = 0.0; used++ }
                w in MILLION && any -> { total += (if (cur == 0.0) 1.0 else cur) * 1_000_000; cur = 0.0; used++ }
                else -> break
            }
            j++
        }
        return if (any) (total + cur) to used else null
    }

    /** Слова чисел → цифры, остальное без изменений. */
    private fun numberize(tokens: List<String>): List<String> {
        val out = ArrayList<String>()
        var i = 0
        while (i < tokens.size) {
            val r = numberAt(tokens, i)
            if (r != null && (r.second > 1 || tokens[i].replace(',', '.').toDoubleOrNull() == null)) { out += plain(r.first); i += r.second }
            else { out += tokens[i]; i++ }
        }
        return out
    }

    private fun plain(v: Double): String = BigDecimal(v).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

    // ---------- калькулятор ----------

    private const val N = "(\\d+(?:\\.\\d+)?)"

    /**
     * Фраза → арифметическое выражение: «15 процентов от 2400» → «(2400*15/100)», «корень из 144» → «sqrt(144)»,
     * «двенадцать умножить на семь» → «12*7». Всё, что не число и не знак действия, отбрасывается; null — не выражение.
     */
    fun toExpression(raw: String): String? {
        val low = raw.lowercase().replace('ё', 'е').replace('’', '\'').replace("'", "")
        var s = " " + numberize(low.split(Regex("\\s+")).filter { it.isNotEmpty() }).joinToString(" ") + " "
        s = s.replace(Regex("(?<=\\d),(?=\\d)"), ".")
        fun sub(re: String, to: String) { s = s.replace(Regex(re), to) }
        // «X плюс/минус N процентов»
        sub("$N\\s*(?:плюс|\\+|додати|plus)\\s*$N\\s*(?:процент\\S*|%|відсот\\S*|percent)", "$1*(1+$2/100)")
        sub("$N\\s*(?:минус|мінус|-|minus)\\s*$N\\s*(?:процент\\S*|%|відсот\\S*|percent)", "$1*(1-$2/100)")
        // «15 процентов от 2400»
        sub("$N\\s*(?:процент\\S*|%|відсот\\S*|percent)\\s*(?:от|від|з|із|of)\\s*$N", " ($2*$1/100) ")
        // «корень из 144»
        sub("(?:квадратн\\S*\\s+)?(?:корень|корінь|корня|sqrt|square root)(?:\\s+квадратн\\S*)?\\s*(?:из|з|із|of)?\\s*$N", " sqrt($1) ")
        sub("$N\\s*(?:в квадрате|у квадраті|squared)", " ($1^2) ")
        sub("$N\\s*(?:в кубе|у кубі|cubed)", " ($1^3) ")
        sub("$N\\s*(?:в степени|у степені|в ступені|to the power of|power)\\s*$N", " $1^$2 ")
        sub("(?:умножь|умножить|помнож\\S*|multiply)\\s+$N\\s+(?:на|by)\\s+$N", " $1*$2 ")
        sub("(?:раздели|разделить|подели|поделить|поділи|поділити|подели|divide)\\s+$N\\s+(?:на|by)\\s+$N", " $1/$2 ")
        sub("\\s(?:умножить на|умножь на|помножити на|помнож на|multiplied by|times|умножить|помножити)\\s", " * ")
        sub("(?<=\\d)\\s*[хx×✕]\\s*(?=\\d)", " * ")
        sub("\\s(?:разделить на|поделить на|поділити на|divided by|делить на|ділити на|разделить|поделить|поділити)\\s", " / ")
        sub("(?<=\\d)\\s*÷\\s*(?=\\d)", " / ")
        sub("\\s(?:плюс|plus|додати|прибавить|добавить)\\s", " + ")
        sub("\\s(?:минус|мінус|minus|отнять|відняти|вычесть)\\s", " - ")
        val kept = s.trim().split(Regex("\\s+")).filter { Regex("[0-9+\\-*/^().%]+|sqrt\\([0-9+\\-*/^(). ]*\\)").matches(it) }
        val expr = kept.joinToString("")
        if (!expr.any { it.isDigit() }) return null
        if (!(expr.any { it in "+-*/^%" } || expr.contains("sqrt("))) return null
        // одинокий минус впереди — не действие
        if (expr.drop(1).none { it in "+-*/^%" } && !expr.contains("sqrt(") && !expr.contains("(")) return null
        return expr
    }

    /** Вычисление выражения из цифр, + - * / ^ % и sqrt(...). null — ошибка (в т.ч. деление на ноль). */
    fun evaluate(expr: String): Double? = runCatching {
        val p = Evaluator(expr.replace(" ", ""))
        val v = p.expr()
        if (!p.done()) null else v.takeIf { it.isFinite() }
    }.getOrNull()

    private class Evaluator(val s: String) {
        var i = 0
        fun done() = i == s.length
        fun expr(): Double {
            var v = term()
            while (i < s.length && (s[i] == '+' || s[i] == '-')) { val op = s[i++]; val r = term(); v = if (op == '+') v + r else v - r }
            return v
        }
        fun term(): Double {
            var v = power()
            while (i < s.length && (s[i] == '*' || s[i] == '/')) {
                val op = s[i++]; val r = power()
                v = if (op == '*') v * r else { if (r == 0.0) throw ArithmeticException("div0"); v / r }
            }
            return v
        }
        fun power(): Double {
            val b = unary()
            if (i < s.length && s[i] == '^') { i++; return Math.pow(b, power()) }
            return b
        }
        fun unary(): Double {
            if (i < s.length && s[i] == '-') { i++; return -unary() }
            if (i < s.length && s[i] == '+') { i++; return unary() }
            var v = primary()
            while (i < s.length && s[i] == '%') { i++; v /= 100.0 }
            return v
        }
        fun primary(): Double {
            if (s.startsWith("sqrt(", i)) {
                i += 5; val v = expr(); expect(')')
                if (v < 0) throw ArithmeticException("neg")
                return Math.sqrt(v)
            }
            if (i < s.length && s[i] == '(') { i++; val v = expr(); expect(')'); return v }
            val st = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            if (st == i) throw IllegalArgumentException("num")
            return s.substring(st, i).toDouble()
        }
        fun expect(c: Char) { if (i >= s.length || s[i] != c) throw IllegalArgumentException("expected $c"); i++ }
    }

    /** Число для голоса: целое без «.0», дробное — до 4 знаков, десятичная запятая (по-английски — точка). */
    fun format(v: Double, comma: Boolean = true, digits: Int = 4): String {
        val bd = BigDecimal(v).setScale(digits, RoundingMode.HALF_UP).stripTrailingZeros()
        val str = bd.toPlainString()
        return if (comma) str.replace('.', ',') else str
    }

    // ---------- единицы ----------

    enum class Kind { LENGTH, MASS, VOLUME, TEMP, MONEY }

    private data class UnitDef(val key: String, val kind: Kind, val toBase: Double, val prefixes: List<String>, val exact: Set<String>)

    private val DEFS = listOf(
        UnitDef("km", Kind.LENGTH, 1000.0, listOf("километр", "кілометр", "kilomet"), setOf("км", "km")),
        UnitDef("m", Kind.LENGTH, 1.0, listOf("метр", "metre", "meter"), setOf("м", "m")),
        UnitDef("cm", Kind.LENGTH, 0.01, listOf("сантиметр", "centimet"), setOf("см", "cm")),
        UnitDef("mi", Kind.LENGTH, 1609.344, emptyList(), setOf("миля", "мили", "миль", "милю", "милях", "милями", "милей", "мілі", "міль", "мілю", "мілях", "мілями", "mile", "miles", "mi")),
        UnitDef("ft", Kind.LENGTH, 0.3048, emptyList(), setOf("фут", "фута", "футы", "футов", "футах", "футам", "футів", "фути", "ft", "feet", "foot")),
        UnitDef("in", Kind.LENGTH, 0.0254, listOf("дюйм"), setOf("inch", "inches")),
        UnitDef("kg", Kind.MASS, 1.0, listOf("килограмм", "кілограм", "kilogram"), setOf("кг", "kg", "кило", "кіло")),
        UnitDef("g", Kind.MASS, 0.001, emptyList(), setOf("г", "гр", "грамм", "грамма", "граммов", "граммах", "грам", "грама", "грамів", "грамах", "g", "gram", "grams")),
        UnitDef("lb", Kind.MASS, 0.45359237, listOf("фунт"), setOf("lb", "lbs", "pound", "pounds")),
        UnitDef("oz", Kind.MASS, 0.028349523, emptyList(), setOf("унция", "унции", "унций", "унцій", "унції", "унцію", "унциях", "унціях", "унціями", "oz", "ounce", "ounces")),
        UnitDef("l", Kind.VOLUME, 1.0, emptyList(), setOf("литр", "литра", "литров", "литрах", "литры", "літр", "літра", "літрів", "літрах", "л", "l", "liter", "liters", "litre", "litres")),
        UnitDef("gal", Kind.VOLUME, 3.785411784, listOf("галлон", "галон", "gallon"), emptySet()),
        UnitDef("c", Kind.TEMP, 1.0, listOf("цельси", "цельс", "celsius"), setOf("°c", "c")),
        UnitDef("f", Kind.TEMP, 1.0, listOf("фаренгейт", "fahrenheit", "фарингейт"), setOf("°f", "f")),
        UnitDef("usd", Kind.MONEY, 1.0, listOf("доллар", "долар", "dollar", "бакс"), setOf("usd", "$")),
        UnitDef("eur", Kind.MONEY, 1.0, listOf("евро", "євро", "euro"), setOf("eur", "€")),
        UnitDef("uah", Kind.MONEY, 1.0, listOf("гривн", "гривен", "гривень", "hryvn"), setOf("uah", "грн")),
        UnitDef("rub", Kind.MONEY, 1.0, listOf("рубл", "ruble"), setOf("rub")),
        UnitDef("pln", Kind.MONEY, 1.0, listOf("злот", "zlot"), setOf("pln")),
        UnitDef("gbp", Kind.MONEY, 1.0, listOf("стерлинг", "стерлінг"), setOf("gbp")),
    )

    private fun unitKey(tok: String, next: String?): String? {
        val w = tok.trim(',', '.', '?', '!')
        if (w == "метро") return null
        if (w.startsWith("фунт") && next != null && (next.startsWith("стерл"))) return "gbp"
        return DEFS.firstOrNull { d -> w in d.exact || d.prefixes.any { w.startsWith(it) } }?.key
    }

    fun kindOf(key: String): Kind? = DEFS.firstOrNull { it.key == key }?.kind

    /** Распознанный перевод: значение, из чего, во что. */
    data class Conversion(val value: Double, val from: String, val to: String)

    private val CONVERT_TRIGGERS = listOf("перевед", "перевод", "переведи", "конверт", "convert", "сколько", "скільки", "how many", "how much", "чему равн", "чому дорівн", "equal")

    /** «сколько миль в 10 километрах», «переведи 5 фунтов в килограммы», «100 по Фаренгейту в Цельсиях»; null — не перевод. */
    fun parseConversion(raw: String): Conversion? {
        val low = " " + raw.lowercase().replace('ё', 'е').replace('’', '\'').replace("'", "") + " "
        if (CONVERT_TRIGGERS.none { low.contains(it) }) return null
        val tokens = numberize(low.trim().split(Regex("\\s+")).filter { it.isNotEmpty() })
        var numIdx = -1; var value = 0.0
        for ((i, t) in tokens.withIndex()) {
            val d = t.replace(',', '.').toDoubleOrNull()
            if (d != null) { numIdx = i; value = d; break }
        }
        if (numIdx < 0) return null
        val units = tokens.withIndex().mapNotNull { (i, t) -> unitKey(t, tokens.getOrNull(i + 1))?.let { i to it } }
        if (units.isEmpty()) return null
        // единица справа от числа (в пределах трёх слов: «градусов по цельсию») — исходная; другая — целевая
        val from = units.firstOrNull { it.first > numIdx && it.first - numIdx <= 3 }?.second ?: units.firstOrNull { it.first > numIdx }?.second ?: return null
        var to = units.firstOrNull { it.second != from }?.second
        if (to == null && kindOf(from) == Kind.TEMP && low.contains("градус")) to = if (from == "c") "f" else "c"
        if (to == null) return null
        if (kindOf(from) != kindOf(to)) return null
        return Conversion(value, from, to)
    }

    /** Результат перевода; null — для валют (курсов без сети нет) и несовместимых единиц. */
    fun convert(c: Conversion): Double? {
        val a = DEFS.firstOrNull { it.key == c.from } ?: return null
        val b = DEFS.firstOrNull { it.key == c.to } ?: return null
        if (a.kind != b.kind) return null
        return when (a.kind) {
            Kind.MONEY -> null
            Kind.TEMP -> when {
                a.key == b.key -> c.value
                a.key == "c" -> c.value * 9 / 5 + 32
                else -> (c.value - 32) * 5 / 9
            }
            else -> c.value * a.toBase / b.toBase
        }
    }

    // ---------- свободные окна ----------

    /**
     * Свободные промежутки дня [day] между [dayStartHour] и [dayEndHour], не короче [minMinutes].
     * [busy] — занятые отрезки (начало, конец) записей; [notBefore] — «с этого момента» (для сегодняшнего дня).
     */
    fun freeSlots(
        busy: List<Pair<LocalDateTime, LocalDateTime>>, day: LocalDate, dayStartHour: Int, dayEndHour: Int,
        notBefore: LocalDateTime? = null, minMinutes: Int = 30,
    ): List<Pair<LocalTime, LocalTime>> {
        val startH = dayStartHour.coerceIn(0, 23)
        val endH = if (dayEndHour <= startH) 24 else dayEndHour.coerceIn(1, 24)
        val winStart = day.atStartOfDay().plusHours(startH.toLong())
        val winEnd = day.atStartOfDay().plusHours(endH.toLong())
        var cursor = winStart
        if (notBefore != null && notBefore.isAfter(cursor)) {
            // с ближайших 15 минут вперёд
            val m = notBefore.minute
            val up = if (m % 15 == 0 && notBefore.second == 0) notBefore else notBefore.withSecond(0).withNano(0).plusMinutes((15 - m % 15).toLong())
            cursor = up
        }
        val out = ArrayList<Pair<LocalTime, LocalTime>>()
        fun gap(from: LocalDateTime, to: LocalDateTime) {
            val end = if (to.isAfter(winEnd)) winEnd else to
            if (java.time.Duration.between(from, end).toMinutes() >= minMinutes) out += from.toLocalTime() to (if (end == day.plusDays(1).atStartOfDay()) LocalTime.of(23, 59) else end.toLocalTime())
        }
        for ((a, b) in busy.sortedBy { it.first }) {
            if (!b.isAfter(cursor)) continue
            if (a.isAfter(cursor)) gap(cursor, a)
            if (b.isAfter(cursor)) cursor = b
            if (!cursor.isBefore(winEnd)) break
        }
        if (cursor.isBefore(winEnd)) gap(cursor, winEnd)
        return out
    }
}
