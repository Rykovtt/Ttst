package com.kartoteka.app.data

import java.time.LocalDate

/** Критерии выбора людей (для рассылки). Пустой критерий не ограничивает выборку. */
data class RecipientFilter(
    val groupIds: Set<Long> = emptySet(),
    val genders: Set<String> = emptySet(),
    val relations: Set<String> = emptySet(),
    val cities: Set<String> = emptySet(),
    val minCloseness: Int = 0,
    val favoritesOnly: Boolean = false,
    val ageFrom: Int? = null,
    val ageTo: Int? = null,
    val birthdayWithinDays: Int? = null,
    val noContactDays: Int? = null,
) {
    val isEmpty: Boolean get() = this == RecipientFilter()

    fun matches(pf: PersonFull, today: LocalDate = LocalDate.now(), now: Long = System.currentTimeMillis()): Boolean {
        val p = pf.person
        if (groupIds.isNotEmpty() && pf.groups.none { it.id in groupIds }) return false
        if (genders.isNotEmpty() && p.gender !in genders) return false
        if (relations.isNotEmpty() && p.relation !in relations) return false
        if (cities.isNotEmpty() && cities.none { it.equals(p.city.trim(), ignoreCase = true) }) return false
        if (p.closeness < minCloseness) return false
        if (favoritesOnly && !p.favorite) return false
        if (ageFrom != null || ageTo != null) {
            val age = ArchiveLogic.age(p, today) ?: return false
            if (ageFrom != null && age < ageFrom) return false
            if (ageTo != null && age > ageTo) return false
        }
        if (birthdayWithinDays != null) {
            val d = ArchiveLogic.daysUntilBirthday(p, today) ?: return false
            if (d > birthdayWithinDays) return false
        }
        if (noContactDays != null) {
            val last = p.lastContactAt
            if (last != null && now - last < noContactDays * 86_400_000L) return false
        }
        return true
    }

    fun apply(all: List<PersonFull>) = all.filter { matches(it) }
}
