package com.kartoteka.app.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Чистая логика без Android — покрыта unit-тестами. */
object ArchiveLogic {

    data class SearchHit(val person: PersonFull, val matchedIn: String?)

    /** Поиск по всем полям: имена, контакты, детали, заметки, хроника, группы. Все слова запроса должны найтись. */
    fun search(all: List<PersonFull>, query: String): List<SearchHit> {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return all.map { SearchHit(it, null) }
        return all.mapNotNull { pf ->
            val sources = searchSources(pf)
            val ok = words.all { w -> sources.any { it.second.lowercase().contains(w) } }
            if (!ok) return@mapNotNull null
            val nameHit = words.all { w -> pf.person.fullName.lowercase().contains(w) || pf.person.nickname.lowercase().contains(w) }
            val where = if (nameHit) null else sources.firstOrNull { s -> words.any { s.second.lowercase().contains(it) } }
                ?.let { "${it.first}: ${snippet(it.second, words)}" }
            SearchHit(pf, where)
        }
    }

    private fun searchSources(pf: PersonFull): List<Pair<String, String>> = buildList {
        val p = pf.person
        add("Имя" to "${p.lastName} ${p.firstName} ${p.middleName}")
        add("Прозвище" to p.nickname)
        add("Отношение" to p.relation)
        add("Работа" to "${p.company} ${p.position}")
        add("Город" to p.city)
        pf.places.forEach { add(it.placeKind.title to it.address) }
        add("Знакомство" to p.howMet)
        add("Заметки" to p.notes)
        pf.contacts.forEach { add(it.contactType.title to it.value) }
        pf.details.forEach { add(it.name.ifBlank { it.category } to it.value) }
        pf.groups.forEach { add("Группа" to it.name) }
        pf.journal.forEach { add(it.kind to it.text) }
        pf.photos.forEach { if (it.caption.isNotBlank()) add("Фото" to it.caption) }
    }.filter { it.second.isNotBlank() }

    private fun snippet(text: String, words: List<String>): String {
        val lower = text.lowercase()
        val idx = words.map { lower.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: 0
        val start = (idx - 20).coerceAtLeast(0)
        val end = (idx + 50).coerceAtMost(text.length)
        return (if (start > 0) "…" else "") + text.substring(start, end).replace('\n', ' ') + (if (end < text.length) "…" else "")
    }

    fun sort(list: List<SearchHit>, mode: SortMode): List<SearchHit> = when (mode) {
        SortMode.NAME -> list.sortedBy { it.person.person.sortKey }
        SortMode.RECENT -> list.sortedByDescending { it.person.person.createdAt }
        SortMode.CLOSENESS -> list.sortedWith(compareByDescending<SearchHit> { it.person.person.closeness }.thenBy { it.person.person.sortKey })
        SortMode.LONG_AGO -> list.sortedBy { it.person.person.lastContactAt ?: 0L }
    }

    /** Ближайший день рождения (сегодня или позже). null — если дата не заполнена. */
    fun nextBirthday(p: Person, today: LocalDate = LocalDate.now()): LocalDate? {
        val d = p.birthDay ?: return null
        val m = p.birthMonth ?: return null
        if (m !in 1..12 || d !in 1..31) return null
        fun at(year: Int): LocalDate {
            val len = java.time.YearMonth.of(year, m).lengthOfMonth()
            return LocalDate.of(year, m, d.coerceAtMost(len))
        }
        val thisYear = at(today.year)
        return if (thisYear.isBefore(today)) at(today.year + 1) else thisYear
    }

    fun daysUntilBirthday(p: Person, today: LocalDate = LocalDate.now()): Long? =
        nextBirthday(p, today)?.let { ChronoUnit.DAYS.between(today, it) }

    fun age(p: Person, today: LocalDate = LocalDate.now()): Int? {
        val y = p.birthYear ?: return null
        val m = p.birthMonth ?: return today.year - y
        val d = p.birthDay ?: 1
        var age = today.year - y
        if (today.monthValue < m || (today.monthValue == m && today.dayOfMonth < d)) age--
        return age.takeIf { it in 0..150 }
    }

    /** Возраст, который исполнится в ближайший день рождения. */
    fun turningAge(p: Person, today: LocalDate = LocalDate.now()): Int? {
        val y = p.birthYear ?: return null
        val next = nextBirthday(p, today) ?: return null
        return next.year - y
    }

    fun upcomingBirthdays(all: List<PersonFull>, withinDays: Int, today: LocalDate = LocalDate.now()) =
        all.mapNotNull { pf -> daysUntilBirthday(pf.person, today)?.let { pf to it } }
            .filter { it.second <= withinDays }
            .sortedBy { it.second }

    /** Подстановка в шаблон рассылки. */
    fun fillTemplate(template: String, p: Person): String =
        MessageLang.canonicalize(template)
            .replace("{имя}", p.firstName.ifBlank { p.displayName })
            .replace("{отчество}", p.middleName)
            .replace("{фамилия}", p.lastName)
            .replace("{имя_отчество}", listOf(p.firstName, p.middleName).filter { it.isNotBlank() }.joinToString(" ").ifBlank { p.displayName })
            .replace("{прозвище}", p.nickname.ifBlank { p.firstName })

    fun formatBirthday(p: Person): String? {
        val d = p.birthDay ?: return null
        val m = p.birthMonth ?: return null
        val month = MONTHS_GEN.getOrNull(m - 1) ?: return null
        return if (p.birthYear != null) "$d $month ${p.birthYear}" else "$d $month"
    }

    fun plural(n: Long, one: String, few: String, many: String): String {
        val n10 = n % 10
        val n100 = n % 100
        return when {
            n10 == 1L && n100 != 11L -> one
            n10 in 2..4 && n100 !in 12..14 -> few
            else -> many
        }
    }

    fun ageString(age: Int): String = "$age ${plural(age.toLong(), "год", "года", "лет")}"

    fun daysString(days: Long): String = when (days) {
        0L -> "сегодня"
        1L -> "завтра"
        else -> "через $days ${plural(days, "день", "дня", "дней")}"
    }

    fun normalizePhone(raw: String): String {
        val digits = raw.filter { it.isDigit() || it == '+' }
        // Российские номера 8XXXXXXXXXX → +7XXXXXXXXXX
        if (digits.length == 11 && digits.startsWith("8")) return "+7" + digits.substring(1)
        return digits
    }

    val MONTHS_GEN = listOf(
        "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря",
    )
}
