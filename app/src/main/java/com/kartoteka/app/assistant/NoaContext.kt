package com.kartoteka.app.assistant

import com.kartoteka.app.data.AppointmentFull
import com.kartoteka.app.data.AppointmentLogic
import com.kartoteka.app.data.AppointmentStatus
import com.kartoteka.app.data.PersonFull
import com.kartoteka.app.data.Repository
import com.kartoteka.app.data.ServiceTemplate
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Компактный сопоставитель имён: тот же приём, что у исполнителя в Noa (основы слов, русские и украинские
 * буквы как одни, нечёткое расстояние правки), но самостоятельный и без базы — работает по списку имён.
 * Им чиним имена, которые вернула модель, и разбираем ответы на уточняющие вопросы.
 */
object NoaMatch {
    /** Человек для сопоставления: [first] — имя, [rest] — остальные слова, [nick] — прозвище. */
    class Ref(val display: String, val first: String, val rest: List<String>, val nick: String) {
        val stems: List<String> = (listOf(first) + rest + nick).filter { it.isNotBlank() }.map(::stem)
        val firstStems: List<String> = listOf(first, nick).filter { it.isNotBlank() }.map(::stem)
    }

    enum class Kind { NONE, ONE, MANY }
    class Result(val kind: Kind, val hits: List<Ref>)

    /** «Анна Иванова (Аня)» → Ref; прозвище в скобках необязательно. */
    fun ref(name: String): Ref {
        val nick = Regex("\\(([^)]*)\\)").find(name)?.groupValues?.get(1)?.trim().orEmpty()
        val clean = name.replace(Regex("\\([^)]*\\)"), " ").trim().replace(Regex("\\s+"), " ")
        val w = clean.split(" ").filter { it.isNotBlank() }
        return Ref(clean, w.firstOrNull().orEmpty(), w.drop(1), nick)
    }

    fun refs(names: List<String>): List<Ref> = names.filter { it.isNotBlank() }.map(::ref)

    /** Сводим похожие буквы русского и украинского алфавитов: «Ілля Риков» = «Илья Рыков». */
    fun fold(w: String): String = buildString {
        for (c in w.lowercase()) when (c) {
            'і', 'ї', 'ы', 'й', 'и' -> append('и')
            'е', 'є', 'ё', 'э' -> append('е')
            'ґ' -> append('г')
            'ь', '\'', '’', '`', 'ʼ' -> {}
            else -> append(c)
        }
    }

    /** Основа: сводим алфавиты и отбрасываем 1–2 конечные гласные (падежи). */
    fun stem(w: String): String {
        var s = fold(w.trim())
        var cut = 0
        while (s.length > 2 && cut < 2 && s.last() in "ауюиеоя") { s = s.dropLast(1); cut++ }
        return s
    }

    /** Мягкое совпадение (как у исполнителя): равны или одно — начало другого, минимум 2 буквы. */
    fun stemMatch(a: String, b: String): Boolean {
        if (a.length < 2 || b.length < 2) return a == b
        return a == b || a.startsWith(b) || b.startsWith(a)
    }

    /** Строгое совпадение для слов из речи: равные основы, либо более короткая (≥3 букв) — начало длинной. */
    fun stemStrict(a: String, b: String): Boolean {
        if (a == b) return a.length >= 2
        val (s, l) = if (a.length <= b.length) a to b else b to a
        return s.length >= 3 && l.startsWith(s)
    }

    fun lev(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]; dp[0] = i
            for (j in 1..b.length) {
                val tmp = dp[j]
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return dp[b.length]
    }

    private fun squash(w: String) = w.replace(Regex("(.)\\1+"), "$1")

    fun words(s: String): List<String> = s.lowercase().split(Regex("[^\\p{L}]+")).filter { it.isNotBlank() }

    /** Кто подходит под запрос: точные совпадения по основам; иначе нечёткие («ильерикову», «Рикову»). */
    fun resolve(query: String, people: List<Ref>, fuzzy: Boolean = true): Result {
        val q = words(query).filter { it.length > 1 }.map(::stem)
        if (q.isEmpty() || people.isEmpty()) return Result(Kind.NONE, emptyList())
        val scored = people.mapNotNull { p ->
            if (!q.all { qw -> p.stems.any { nw -> stemMatch(qw, nw) } }) return@mapNotNull null
            p to if (q.any { qw -> p.firstStems.any { stemMatch(qw, it) } }) 2 else 1
        }
        // «ильерикову» совпало с «иль» лишь первыми буквами — слабое совпадение: сначала пробуем нечёткий поиск.
        val weak = scored.isNotEmpty() && scored.all { (p, _) -> q.any { qw -> p.stems.none { nw -> nw.length >= 2 && minOf(qw.length, nw.length).toFloat() / maxOf(qw.length, nw.length) >= 0.5f && stemMatch(qw, nw) } } }
        if (scored.isNotEmpty() && !(weak && fuzzy)) {
            val top = scored.maxOf { it.second }
            return group(scored.filter { it.second == top }.map { it.first })
        }
        if (weak) fuzzyOf(query, people).let { if (it.kind == Kind.ONE) return it }
        if (weak) return group(scored.let { l -> val top = l.maxOf { it.second }; l.filter { it.second == top }.map { it.first } })
        if (!fuzzy) return Result(Kind.NONE, emptyList())
        return fuzzyOf(query, people)
    }

    private fun fuzzyOf(query: String, people: List<Ref>): Result {
        val qj = words(query).filter { it.length > 1 }.joinToString("") { squash(stem(it)) }
        if (qj.length < 3) return Result(Kind.NONE, emptyList())
        val best = people.mapNotNull { p ->
            val f = squash(stem(p.first)); val l = squash(stem(p.rest.lastOrNull().orEmpty())); val n = squash(stem(p.nick))
            val d = listOf(f + l, l + f, f, l, n, n + l).filter { it.length >= 2 }.distinct().minOfOrNull { v ->
                val e = lev(qj, v)
                if (e <= maxOf(1, (v.length * 0.3f).toInt())) e else Int.MAX_VALUE
            } ?: Int.MAX_VALUE
            if (d == Int.MAX_VALUE) null else p to d
        }.sortedBy { it.second }
        if (best.isEmpty()) return Result(Kind.NONE, emptyList())
        return group(best.filter { it.second == best[0].second }.map { it.first })
    }

    private fun group(l: List<Ref>) = Result(if (l.size == 1) Kind.ONE else Kind.MANY, l)

    /** Единственный человек, названный во фразе (окна из 3, 2, 1 слов; первое однозначное совпадение). */
    fun fromPhrase(phrase: String, people: List<Ref>): Ref? {
        val toks = words(phrase).flatMap { listOf(it, translit(it)) }.distinct().filter { it.length >= 3 }
        for (n in 3 downTo 1) for (i in 0..(toks.size - n)) {
            val win = toks.subList(i, i + n)
            val r = if (n == 1) {
                // одно слово: строго по основам; нечёткий поиск — только для длинных слов (искажённое распознавание)
                val st = stem(win[0])
                val strict = people.filter { p -> p.stems.any { stemStrict(st, it) } }
                if (strict.size == 1) Result(Kind.ONE, strict) else if (strict.isEmpty() && win[0].length >= 6) resolve(win[0], people) else Result(Kind.NONE, emptyList())
            } else resolve(win.joinToString(" "), people, fuzzy = false)
            if (r.kind == Kind.ONE) return r.hits[0]
        }
        return null
    }

    /** Названо ли [name] во фразе (по основам слов). */
    /** Латиница → кириллица («Oleg» → «олег»): английская фраза называет того же человека, что записан по-русски. */
    internal fun translit(w: String): String {
        var s = w.lowercase()
        if (s.isEmpty() || s.any { it !in 'a'..'z' }) return w
        for ((a, b) in listOf("shch" to "щ", "sh" to "ш", "ch" to "ч", "zh" to "ж", "kh" to "х", "ts" to "ц", "ya" to "я", "yu" to "ю", "yo" to "ё", "ye" to "е", "ii" to "ий")) s = s.replace(a, b)
        val m = mapOf('a' to 'а', 'b' to 'б', 'c' to 'к', 'd' to 'д', 'e' to 'е', 'f' to 'ф', 'g' to 'г', 'h' to 'х', 'i' to 'и', 'j' to 'й', 'k' to 'к', 'l' to 'л', 'm' to 'м',
            'n' to 'н', 'o' to 'о', 'p' to 'п', 'q' to 'к', 'r' to 'р', 's' to 'с', 't' to 'т', 'u' to 'у', 'v' to 'в', 'w' to 'в', 'x' to 'х', 'y' to 'и', 'z' to 'з')
        return s.map { m[it] ?: it }.joinToString("")
    }

    fun mentions(phrase: String, name: String): Boolean {
        val ps = words(phrase).flatMap { listOf(it, translit(it)) }.map(::stem)
        return words(name).filter { it.length > 1 }.map(::stem).let { ns -> ns.isNotEmpty() && ns.all { n -> ps.any { stemStrict(it, n) } } }
    }
}

/** Приведение текста к виду для озвучки и защита от выдумок модели. */
object NoaText {
    private val EMOJI_RANGES = listOf(0x1F000..0x1FAFF, 0x2190..0x21FF, 0x2300..0x23FF, 0x2460..0x24FF, 0x25A0..0x27BF, 0x2900..0x297F, 0x2B00..0x2BFF, 0xFE00..0xFE0F, 0x200B..0x200F, 0x20E3..0x20E3, 0xE0020..0xE007F)

    private fun isEmoji(cp: Int) = EMOJI_RANGES.any { cp in it }

    /** Без markdown, эмодзи и служебных меток; одной строкой; не длиннее [max] — обрезаем по границе предложения. */
    fun speakable(raw: String, max: Int = 280): String {
        var s = raw.replace("\r", "")
            .replace(Regex("```[a-zA-Z]*"), " ")
            .replace(Regex("!?\\[([^\\]]*)]\\([^)]*\\)"), "$1")
            .replace(Regex("(?m)^\\s{0,3}#{1,6}\\s*"), "")
            .replace(Regex("(?m)^\\s*(?:[-*•–]|\\d{1,2}[.)])\\s+"), "")
            .replace(Regex("(\\*\\*|__|~~|`)"), "")
            .replace(Regex("(?<![\\p{L}\\d])[*_](?=\\S)|(?<=\\S)[*_](?![\\p{L}\\d])"), "")
        s = buildString { s.codePoints().forEach { cp -> if (!isEmoji(cp)) appendCodePoint(cp) } }
        // Переводы строк → паузы между фразами.
        val lines = s.lines().map { it.trim() }.filter { it.isNotEmpty() }
        s = lines.withIndex().joinToString(" ") { (i, l) -> if (i == lines.lastIndex || l.last() in ".!?…:;,") l else "$l." }
        s = s.replace(Regex("^(?:Ноа|Noa|Assistant|Ассистент|Асистент|Reply|Answer)\\s*:\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+"), " ").replace(Regex("\\s+([.,!?;:])"), "$1").trim().trim('"', '«', '»').trim()
        return capSentence(s, max)
    }

    /** Обрезка по концу предложения (иначе по слову с многоточием). */
    fun capSentence(s: String, max: Int): String {
        if (s.length <= max) return s
        val cut = s.substring(0, max)
        val end = cut.indexOfLast { it in ".!?…" }
        if (end >= max * 2 / 5) return cut.substring(0, end + 1)
        val sp = cut.lastIndexOf(' ')
        return (if (sp > max / 2) cut.substring(0, sp) else cut).trimEnd(',', ';', ':', '-', '—', ' ') + "…"
    }

    /** Шаблон-заглушка из примеров промпта («<from Data>») — это не ответ. */
    fun isPlaceholder(s: String) = Regex("<[^>]{1,24}>").containsMatchIn(s) || s.contains("from Data", true)

    private val NUMBER = Regex("[+(]?\\d[\\d\\s()\\-]{5,}\\d")

    /**
     * Номера телефонов и другие длинные числа в ответе — только если они есть в данных или во фразе.
     * Предложение с выдуманным номером выбрасываем целиком.
     */
    fun scrubNumbers(reply: String, allowed: String): String {
        val ok = allowed.filter { it.isDigit() }
        val sentences = reply.split(Regex("(?<=[.!?…])\\s+"))
        return sentences.filter { sent ->
            NUMBER.findAll(sent).all { m ->
                val d = m.value.filter { it.isDigit() }
                d.length < 7 || ok.contains(d)
            }
        }.joinToString(" ").trim()
    }

    /** Похоже ли на телефонный номер (≥7 цифр). */
    fun looksLikePhone(s: String) = s.count { it.isDigit() } >= 7
}

/**
 * Данные для модели по теме фразы (маршрутизация по ключевым словам, а не по решению модели) и бюджет окна.
 * Окно у модели ≈1280 токенов вместе с ответом: считаем ≈3 буквы кириллицы на токен, весь промпт держим
 * в [TOTAL_CHARS] символов, остальное остаётся на ответ (≈500 токенов). Что не влезло — отбрасываем по приоритету.
 */
object NoaContext {
    /** Весь промпт (правила + люди + данные + диалог + фраза), символов. */
    const val TOTAL_CHARS = 2600
    /** Фраза пользователя в промпте не длиннее этого. */
    const val COMMAND_MAX = 300
    /** Бюджет блока данных по умолчанию (интерпретатор ещё раз подрезает под остаток окна). */
    const val DATA_BUDGET = 560
    private const val DAY_MS = 86_400_000L
    /** «Ему/её» и «он/она»: во фразе речь о том, кого обсуждали последним. */
    private val PRONOUNS = NoaParser.PRONOUNS + setOf("он", "она", "они", "вона", "він", "вони")

    /** Кусок промпта: чем меньше [priority], тем он важнее; [max] — потолок длины этого куска. */
    class Block(val text: String, val priority: Int, val max: Int = Int.MAX_VALUE)

    /**
     * Складывает куски в [budget] символов: сначала самые важные, не влезший кусок обрезается по границе строки
     * или перечисления, а слишком мелкий остаток отбрасывается. Порядок вывода — как во входном списке.
     */
    fun fit(blocks: List<Block>, budget: Int, sep: String = "\n"): String {
        var left = budget
        val kept = HashMap<Int, String>()
        for ((i, b) in blocks.withIndex().filter { it.value.text.isNotBlank() }.sortedBy { it.value.priority }) {
            val cost = if (kept.isEmpty()) 0 else sep.length
            val cap = minOf(b.max, left - cost)
            if (cap <= 0) continue
            val s = if (b.text.length <= cap) b.text else trimAt(b.text, cap) ?: continue
            kept[i] = s; left -= s.length + cost
        }
        return blocks.indices.mapNotNull { kept[it] }.joinToString(sep)
    }

    /** Обрезка по последней границе (перенос строки, «; », «, », «. ») в пределах [cap]; слишком короткий результат — отказ. */
    fun trimAt(text: String, cap: Int): String? {
        if (cap < 25) return null
        val cut = text.take(cap)
        val b = maxOf(cut.lastIndexOf('\n'), cut.lastIndexOf("; "), cut.lastIndexOf(", "), cut.lastIndexOf(". "))
        return (if (b >= cap / 3) cut.substring(0, b) else cut).trimEnd(',', ';', ' ', '\n').takeIf { it.length >= 20 }
    }

    // ---- темы ----

    enum class Topic { AGENDA, WEEK, BIRTHDAYS, COUNTS, SERVICES, STALE, RECENT, WHO, LASTP }

    private fun hasAny(s: String, vararg keys: String) = keys.any { s.contains(it) }

    /** Какие данные нужны фразе — по ключевым словам (русский, украинский, английский). */
    fun topicsOf(text: String): Set<Topic> {
        val s = " " + text.lowercase().replace('ё', 'е') + " "
        val out = HashSet<Topic>()
        if (hasAny(s, "сегодня", "сьогодні", "завтра", "послезавтра", "післязавтра", "запис", "встреч", "зустрі", "расписан", "розклад", "план ",
                "приём", "приема", "прием", "прийом", "календар", "today", "tomorrow", "agenda", "schedule", "свобод", "занят", "вільн", "понедельник", "вторник",
                "сред", "четверг", "пятниц", "суббот", "воскресен", "понеділ", "вівтор", "четвер", "п'ятн", "субот", "неділ", "клиент", "клієнт")) out += Topic.AGENDA
        if (hasAny(s, "недел", "тижн", "week", "ближайш", "найближч")) out += Topic.WEEK
        if (hasAny(s, "рожден", "рождень", "народж", "birthday", "именин", "іменин", "родил", "родила", " др ")) out += Topic.BIRTHDAYS
        if (hasAny(s, "сколько", "скільки", "how many", "количеств", "кількіст", "всего", "всього", "статистик", "групп", "груп")) out += Topic.COUNTS
        if (hasAny(s, "услуг", "послуг", "сеанс", "процедур", "длительн", "тривал", "прайс", "цен", "стоим", "вартіст", "ціна", "service", "price", "сколько длится")) out += Topic.SERVICES
        if (hasAny(s, "давно", "не писал", "не звонил", "не общал", "не связ", "не видел", "не виделись", "не дзвон", "не писав", "не спілк", "не бачи", "не зв'яз", "не зв’яз", "забыл", "забула", "стар")) out += Topic.STALE
        if (hasAny(s, "последн", "останн", "недавн", "нещодавн", "last", "recent") &&
            hasAny(s, "пис", "звон", "общ", "контакт", "дзвон", "спілк", "говор", "розмов", "разговор", "связ", "виделись", "бачились")) out += Topic.RECENT
        if (hasAny(s, " кто ", " хто ", " who ", "у кого", "кого ", "список", "перечисли", "покажи всех")) out += Topic.WHO
        if (hasAny(s, "о ком", "про кого", "кого мы", "кого ми", "говорили", "говорили", "розмовляли", "обсуждали")) out += Topic.LASTP
        return out
    }

    // ---- формат ----

    private fun dm(d: LocalDate) = "%02d.%02d".format(d.dayOfMonth, d.monthValue)
    private fun dm(ms: Long) = dm(AppointmentLogic.zoned(ms).toLocalDate())
    private fun hm(ms: Long) = AppointmentLogic.timeText(AppointmentLogic.zoned(ms))
    private fun dow(d: LocalDate) = d.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }

    private fun live(a: AppointmentFull) = a.appointment.appointmentStatus != AppointmentStatus.CANCELLED

    /** Люди, названные во фразе (по имени, фамилии, прозвищу), не больше [max]. */
    fun mentioned(text: String, people: List<PersonFull>, max: Int = 2): List<PersonFull> {
        val ws = NoaMatch.words(text).filter { it.length >= 3 }.map(NoaMatch::stem)
        if (ws.isEmpty()) return emptyList()
        return people.mapNotNull { pf ->
            val p = pf.person
            val ns = listOf(p.firstName, p.lastName, p.nickname).filter { it.length > 1 }.map(NoaMatch::stem)
            val n = ws.count { w -> ns.any { NoaMatch.stemStrict(w, it) } }
            if (n > 0) pf to n else null
        }.sortedWith(compareByDescending<Pair<PersonFull, Int>> { it.second }.thenByDescending { it.first.person.lastContactAt ?: 0L }).take(max).map { it.first }
    }

    /** Карточка человека: самое нужное — в начале, чтобы обрезка по строкам съедала хвост. */
    fun card(pf: PersonFull, appts: List<AppointmentFull>, now: LocalDateTime): String {
        val p = pf.person
        val nowMs = AppointmentLogic.millis(now)
        val head = listOfNotNull(
            p.displayName + p.nickname.takeIf { it.isNotBlank() && !p.displayName.contains(it, true) }?.let { " ($it)" }.orEmpty(),
            listOf(p.relation, p.position, p.company, p.city).filter { it.isNotBlank() }.joinToString(", ").ifBlank { null },
            if (p.birthDay != null && p.birthMonth != null) "b-day %02d.%02d%s".format(p.birthDay, p.birthMonth, p.birthYear?.let { " ($it)" }.orEmpty()) else null,
            pf.phone?.let { "tel $it" },
            pf.contacts.filter { it.value.isNotBlank() && it.type != "PHONE" }.take(3).joinToString(", ") { "${it.type.lowercase()} ${it.value}" }.ifBlank { null },
        ).joinToString(" · ")
        val mine = appts.filter { it.appointment.personId == p.id && live(it) }
        val past = mine.filter { it.appointment.start < nowMs }.maxByOrNull { it.appointment.start }
        val next = mine.filter { it.appointment.start >= nowMs }.minByOrNull { it.appointment.start }
        val ago = p.lastContactAt?.let { "last contact ${dm(it)} (${((nowMs - it) / DAY_MS).coerceAtLeast(0)}d ago)" } ?: "never contacted"
        val visits = buildString {
            append(ago)
            if (mine.isNotEmpty()) append("; appts ${mine.size}")
            past?.let { append(", last ${dm(it.appointment.start)}" + it.appointment.title.takeIf { t -> t.isNotBlank() }?.let { t -> " $t" }.orEmpty()) }
            next?.let { append("; next ${dm(it.appointment.start)} ${hm(it.appointment.start)}" + it.appointment.title.takeIf { t -> t.isNotBlank() }?.let { t -> " $t" }.orEmpty()) }
        }
        val groups = pf.groups.takeIf { it.isNotEmpty() }?.joinToString(", ", "groups: ") { it.name }
        val journal = pf.journal.sortedByDescending { it.date }.take(3).joinToString("\n") { "${dm(it.date)} ${it.kind.lowercase()}: ${it.text.take(90)}" }
        val notes = p.notes.takeIf { it.isNotBlank() }?.let { "notes: " + it.take(120) }
        val details = pf.details.filter { it.value.isNotBlank() }.take(4).joinToString("; ") { "${it.name}: ${it.value.take(40)}" }.ifBlank { null }
        val places = pf.places.filter { it.address.isNotBlank() }.take(2).joinToString("; ") { "${it.placeKind.name.lowercase()}: ${it.address.take(60)}" }.ifBlank { null }
        return listOfNotNull(head, visits, groups, journal.ifBlank { null }, notes, details, places).joinToString("\n")
    }

    private fun dayLine(label: String, d: LocalDate, appts: List<AppointmentFull>, people: List<PersonFull>, withBirthdays: Boolean, askedEmpty: Boolean): String? {
        val from = AppointmentLogic.millis(d.atStartOfDay()); val to = AppointmentLogic.millis(d.plusDays(1).atStartOfDay())
        val list = appts.filter { live(it) && it.appointment.start in from until to }.sortedBy { it.appointment.start }
        val bd = if (withBirthdays) people.filter { it.person.birthDay == d.dayOfMonth && it.person.birthMonth == d.monthValue }.map { it.person.displayName } else emptyList()
        if (list.isEmpty() && bd.isEmpty()) return if (askedEmpty) "$label ${dm(d)}: nothing" else null
        val items = list.map {
            hm(it.appointment.start) + " " + it.person?.displayName.orEmpty() + it.appointment.title.takeIf { t -> t.isNotBlank() }?.let { t -> " ($t)" }.orEmpty() +
                it.appointment.durationMin.takeIf { m -> m != 60 }?.let { m -> " ${m}m" }.orEmpty()
        } + bd.map { "b-day $it" }
        return "$label ${dm(d)}: " + items.joinToString("; ")
    }

    /** Ближайшие дни рождения (в пределах [days] дней от [today]). */
    fun birthdays(people: List<PersonFull>, today: LocalDate, days: Int = 14): List<String> = people.mapNotNull { pf ->
        val p = pf.person
        val d = p.birthDay ?: return@mapNotNull null
        val m = p.birthMonth ?: return@mapNotNull null
        val next = runCatching { LocalDate.of(today.year, m, d) }.getOrNull()?.let { if (it.isBefore(today)) it.plusYears(1) else it } ?: return@mapNotNull null
        val left = java.time.temporal.ChronoUnit.DAYS.between(today, next).toInt()
        if (left > days) null else Triple(left, p, next)
    }.sortedBy { it.first }.map { (left, p, next) ->
        p.displayName + " " + dm(next) + (if (left == 0) " today" else " in ${left}d") + p.birthYear?.let { ", turns ${next.year - it}" }.orEmpty()
    }

    /** Сколько дней «давно»: «30 дней», «2 недели», «месяц», по умолчанию 30. */
    fun staleDays(text: String): Int {
        val s = text.lowercase()
        Regex("(\\d{1,3})\\s*(дн|день|днів|нед|тиж|месяц|міс|week|day|month)").find(s)?.let { m ->
            val n = m.groupValues[1].toInt()
            return n * when { m.groupValues[2].startsWith("нед") || m.groupValues[2].startsWith("тиж") || m.groupValues[2] == "week" -> 7
                m.groupValues[2].startsWith("мес") || m.groupValues[2].startsWith("міс") || m.groupValues[2] == "month" -> 30; else -> 1 }
        }
        return when {
            hasAny(s, "полгода", "півроку") -> 180
            hasAny(s, "год", "рік", "року") && !hasAny(s, "сегодня", "сьогодні") -> 365
            hasAny(s, "месяц", "місяц") -> 30
            hasAny(s, "недел", "тижн", "тиждень") -> 7
            else -> 30
        }
    }

    /** Кому давно не писали/не звонили: самые «забытые» первыми. */
    fun stale(people: List<PersonFull>, now: LocalDateTime, days: Int, limit: Int = 8): List<String> {
        val nowMs = AppointmentLogic.millis(now)
        val dated = people.filter { it.person.lastContactAt != null && (nowMs - it.person.lastContactAt!!) / DAY_MS >= days }
            .sortedBy { it.person.lastContactAt }.map { it.person.displayName + " " + (nowMs - it.person.lastContactAt!!) / DAY_MS + "d" }
        val never = people.filter { it.person.lastContactAt == null }.map { it.person.displayName + " never" }
        return (dated + never).take(limit)
    }

    /** Все данные для фразы одним текстом (чистая функция: удобно проверять без базы). */
    fun build(
        text: String, now: LocalDateTime, people: List<PersonFull>, appts: List<AppointmentFull>, services: List<ServiceTemplate>,
        last: PersonFull? = null, budget: Int = DATA_BUDGET,
    ): String {
        val topics = topicsOf(text)
        val today = now.toLocalDate()
        val tokens = NoaMatch.words(text)
        val blocks = ArrayList<Block>()
        // 1. Карточки названных людей (или того, о ком говорили, если во фразе «ему/она»).
        val named = mentioned(text, people)
        val pronoun = tokens.any { it in PRONOUNS }
        val cardPeople = named.ifEmpty { if (pronoun || Topic.LASTP in topics) listOfNotNull(last?.let { l -> people.firstOrNull { it.person.id == l.person.id } ?: l }) else emptyList() }
        cardPeople.forEachIndexed { i, pf -> blocks += Block(card(pf, appts, now), if (named.isEmpty()) 2 else 1, max = if (cardPeople.size > 1) 300 else 430) }
        // 2. Расписание: названный день — первым; «неделя» — на 7 дней; иначе сегодня и завтра.
        val asked = Topic.AGENDA in topics || Topic.WEEK in topics
        val wanted = NoaDateTime.parse(text, now)?.takeIf { it.hadDate }?.dateTime?.toLocalDate()
        if (Topic.WEEK in topics) {
            val lines = (0..6).mapNotNull { dayLine(dow(today.plusDays(it.toLong())), today.plusDays(it.toLong()), appts, people, true, false) }
            blocks += Block(if (lines.isEmpty()) "Week: nothing" else lines.joinToString("\n"), 2, max = 420)
        } else {
            val days = linkedSetOf(today, today.plusDays(1)).also { s -> wanted?.let { s += it } }
            days.forEach { d ->
                val label = when (d) { today -> "Today"; today.plusDays(1) -> "Tomorrow"; else -> dow(d) }
                dayLine(label, d, appts, people, asked, asked && (d == wanted || d == today || d == today.plusDays(1)))?.let {
                    blocks += Block(it, if (d == wanted || asked) 2 else 4, max = 320)
                }
            }
        }
        // 3. Темы по ключевым словам.
        if (Topic.BIRTHDAYS in topics) {
            val b = birthdays(people, today)
            blocks += Block("Birthdays 14d: " + b.joinToString("; ").ifBlank { "none" }, 2, max = 300)
        }
        if (Topic.STALE in topics) {
            val n = staleDays(text)
            blocks += Block("Not contacted ${n}d+: " + stale(people, now, n).joinToString("; ").ifBlank { "none" }, 2, max = 300)
        }
        if (Topic.RECENT in topics) {
            val r = people.filter { it.person.lastContactAt != null }.sortedByDescending { it.person.lastContactAt }.take(5)
                .joinToString("; ") { it.person.displayName + " " + dm(it.person.lastContactAt!!) }
            blocks += Block("Recently contacted: " + r.ifBlank { "none" }, 2, max = 250)
        }
        if (Topic.SERVICES in topics || tokens.any { w -> services.any { s -> NoaMatch.words(s.name).any { NoaMatch.stemStrict(NoaMatch.stem(w), NoaMatch.stem(it)) } } }) {
            blocks += Block("Services: " + services.take(8).joinToString("; ") { it.name + " " + it.durationMin + "min" + it.place.takeIf { p -> p.isNotBlank() }?.let { p -> ", $p" }.orEmpty() }.ifBlank { "none" }, 3, max = 300)
        }
        // Люди группы: «кто в группе клиенты».
        val groupNames = people.flatMap { it.groups }.distinctBy { it.id }
        val gs = tokens.filter { it.length >= 4 }.map(NoaMatch::stem)
        groupNames.filter { g -> NoaMatch.words(g.name).any { gw -> gs.any { NoaMatch.stemStrict(it, NoaMatch.stem(gw)) } } }.take(2).forEach { g ->
            val m = people.filter { pf -> pf.groups.any { it.id == g.id } }
            blocks += Block("Group ${g.name} (${m.size}): " + m.take(10).joinToString(", ") { it.person.displayName }, 2, max = 300)
        }
        // Атрибуты: «кто живёт в Киеве / работает в банке».
        if (Topic.WHO in topics && gs.isNotEmpty()) {
            val hits = people.filter { pf ->
                val p = pf.person
                listOf(p.city, p.company, p.position, p.relation).filter { it.isNotBlank() }.flatMap(NoaMatch::words).map(NoaMatch::stem)
                    .any { a -> gs.any { NoaMatch.stemStrict(it, a) } }
            }.filter { it !in named }
            if (hits.isNotEmpty()) blocks += Block("Matches: " + hits.take(8).joinToString("; ") { it.person.displayName + " — " + listOf(it.person.city, it.person.company, it.person.position).filter { s -> s.isNotBlank() }.joinToString(", ") }, 3, max = 300)
        }
        // 4. Фон: счётчики и последний собеседник.
        val byGroup = groupNames.map { g -> g.name to people.count { pf -> pf.groups.any { it.id == g.id } } }.sortedByDescending { it.second }
        blocks += Block("People total: ${people.size}" + if (byGroup.isNotEmpty() && (Topic.COUNTS in topics)) "; " + byGroup.take(6).joinToString(", ") { "${it.first} ${it.second}" } else "",
            if (Topic.COUNTS in topics) 2 else 5)
        if (last != null && cardPeople.none { it.person.id == last.person.id }) blocks += Block("Last talked about: ${last.person.displayName}", 5)
        return fit(blocks, budget)
    }

    /** Данные из базы: люди, записи за год назад и месяц вперёд, услуги. Тяжёлую часть делаем на фоновом потоке вызывающего. */
    suspend fun build(repo: Repository, text: String, last: PersonFull?, now: LocalDateTime = LocalDateTime.now(), budget: Int = DATA_BUDGET): String {
        val nowMs = AppointmentLogic.millis(now)
        return build(text, now, repo.getAll(), repo.appointmentsBetween(nowMs - 400 * DAY_MS, nowMs + 31 * DAY_MS), repo.getServices(), last, budget)
    }

    /** Имена для подсказки модели и для проверки её ответа: недавние контакты первыми, прозвище — в скобках. */
    suspend fun names(repo: Repository, limit: Int = 60): List<String> = names(repo.getAll(), limit)

    fun names(people: List<PersonFull>, limit: Int = 60): List<String> =
        people.sortedByDescending { it.person.lastContactAt ?: 0L }.take(limit).map {
            val p = it.person
            p.displayName + p.nickname.takeIf { n -> n.isNotBlank() && !p.displayName.contains(n, true) }?.let { n -> " ($n)" }.orEmpty()
        }
}
