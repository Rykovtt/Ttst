package com.kartoteka.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ArchiveLogicTest {

    private fun full(
        p: Person,
        contacts: List<ContactItem> = emptyList(),
        details: List<DetailField> = emptyList(),
        groups: List<Group> = emptyList(),
    ) = PersonFull(p, contacts, details, emptyList(), groups, emptyList())

    private val ivan = full(
        Person(id = 1, firstName = "Иван", lastName = "Петров", city = "Казань"),
        contacts = listOf(ContactItem(type = "PHONE", value = "+7 900 111-22-33")),
        details = listOf(DetailField(category = "Интересы", name = "Хобби", value = "Рыбалка и шахматы")),
        groups = listOf(Group(id = 5, name = "Работа")),
    )
    private val anna = full(Person(id = 2, firstName = "Анна", lastName = "Смирнова", notes = "Любит пионы"))

    @Test fun searchIsCaseInsensitiveForCyrillic() {
        val hits = ArchiveLogic.search(listOf(ivan, anna), "иВАН")
        assertEquals(listOf(1L), hits.map { it.person.person.id })
        assertNull("совпадение по имени не требует пояснения", hits[0].matchedIn)
    }

    @Test fun searchFindsDetailsAndExplainsWhere() {
        val hits = ArchiveLogic.search(listOf(ivan, anna), "шахмат")
        assertEquals(1, hits.size)
        assertTrue(hits[0].matchedIn!!.startsWith("Хобби:"))
    }

    @Test fun searchRequiresAllWords() {
        assertEquals(1, ArchiveLogic.search(listOf(ivan, anna), "петров казань").size)
        assertEquals(0, ArchiveLogic.search(listOf(ivan, anna), "петров москва").size)
    }

    @Test fun searchByNotesGroupsAndPhone() {
        assertEquals(2L, ArchiveLogic.search(listOf(ivan, anna), "пион").single().person.person.id)
        assertEquals(1L, ArchiveLogic.search(listOf(ivan, anna), "работа").single().person.person.id)
        assertEquals(1L, ArchiveLogic.search(listOf(ivan, anna), "111-22").single().person.person.id)
    }

    @Test fun emptyQueryReturnsEveryone() {
        assertEquals(2, ArchiveLogic.search(listOf(ivan, anna), "  ").size)
    }

    @Test fun nextBirthdayRollsOverToNextYear() {
        val p = Person(birthDay = 5, birthMonth = 1, birthYear = 1990)
        val today = LocalDate.of(2026, 9, 27)
        assertEquals(LocalDate.of(2027, 1, 5), ArchiveLogic.nextBirthday(p, today))
        assertEquals(37, ArchiveLogic.turningAge(p, today))
        assertEquals(36, ArchiveLogic.age(p, today))
    }

    @Test fun birthdayTodayIsZeroDays() {
        val p = Person(birthDay = 27, birthMonth = 9)
        assertEquals(0L, ArchiveLogic.daysUntilBirthday(p, LocalDate.of(2026, 9, 27)))
        assertNull(ArchiveLogic.age(p, LocalDate.of(2026, 9, 27)))
    }

    @Test fun feb29InNonLeapYearFallsOnFeb28() {
        val p = Person(birthDay = 29, birthMonth = 2)
        assertEquals(LocalDate.of(2027, 2, 28), ArchiveLogic.nextBirthday(p, LocalDate.of(2027, 1, 1)))
    }

    @Test fun fillTemplateReplacesPlaceholders() {
        val p = Person(firstName = "Иван", middleName = "Сергеевич", lastName = "Петров", nickname = "Ваня")
        assertEquals(
            "Иван Сергеевич, Ваня, Петров — Иван",
            ArchiveLogic.fillTemplate("{имя_отчество}, {прозвище}, {фамилия} — {имя}", p),
        )
    }

    @Test fun russianPlurals() {
        assertEquals("1 год", ArchiveLogic.ageString(1))
        assertEquals("22 года", ArchiveLogic.ageString(22))
        assertEquals("11 лет", ArchiveLogic.ageString(11))
        assertEquals("через 5 дней", ArchiveLogic.daysString(5))
        assertEquals("через 21 день", ArchiveLogic.daysString(21))
    }

    @Test fun normalizesRussianPhone() {
        assertEquals("+79001112233", ArchiveLogic.normalizePhone("8 (900) 111-22-33"))
        assertEquals("+79001112233", ArchiveLogic.normalizePhone("+7 900 111 22 33"))
    }

    @Test fun sortByCloseness() {
        val a = full(Person(id = 1, firstName = "Б", closeness = 2))
        val b = full(Person(id = 2, firstName = "А", closeness = 5))
        val sorted = ArchiveLogic.sort(ArchiveLogic.search(listOf(a, b), ""), SortMode.CLOSENESS)
        assertEquals(listOf(2L, 1L), sorted.map { it.person.person.id })
    }
}
