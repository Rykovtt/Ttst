package com.kartoteka.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class PhoneFormatTest {
    private val ua = PhoneFormat.byIso("UA")
    private val ru = PhoneFormat.byIso("RU")

    @Test fun ukrainianLocalNumberGetsCountryCode() {
        assertEquals("+380930743829", PhoneFormat.normalize("0930743829", ua))
        assertEquals("+380930743829", PhoneFormat.normalize("093 074-38-29", ua))
        assertEquals("+380930743829", PhoneFormat.normalize("(093) 074 38 29", ua))
    }

    @Test fun alreadyInternationalStaysAsIs() {
        assertEquals("+380930743829", PhoneFormat.normalize("+380 93 074 38 29", ua))
        assertEquals("+380930743829", PhoneFormat.normalize("380930743829", ua))
        assertEquals("+380930743829", PhoneFormat.normalize("00380930743829", ua))
        // Номер другой страны с «+» не трогаем, даже если выбрана Украина
        assertEquals("+79001112233", PhoneFormat.normalize("+7 900 111-22-33", ua))
    }

    @Test fun russianTrunkEight() {
        assertEquals("+79001112233", PhoneFormat.normalize("8 900 111 22 33", ru))
        assertEquals("+79001112233", PhoneFormat.normalize("9001112233", ru))
    }

    @Test fun prettyPrint() {
        assertEquals("+380 93 074 38 29", PhoneFormat.pretty("+380930743829"))
        assertEquals("+7 900 111 22 33", PhoneFormat.pretty("+79001112233"))
        assertEquals("@username", PhoneFormat.pretty("@username"))
    }

    @Test fun flagEmoji() {
        assertEquals("🇺🇦", ua.flag)
    }
}

class RecipientFilterTest {
    private fun pf(p: Person, groups: List<Group> = emptyList()) = PersonFull(p, emptyList(), emptyList(), emptyList(), groups, emptyList())
    private val today = LocalDate.of(2026, 9, 28)
    private val now = System.currentTimeMillis()

    private val anna = pf(Person(id = 1, firstName = "Анна", gender = "Женский", city = "Киев", birthYear = 1990, birthMonth = 1, birthDay = 1, closeness = 5, relation = "Друг"), listOf(Group(id = 7, name = "Спорт")))
    private val ivan = pf(Person(id = 2, firstName = "Иван", gender = "Мужской", city = "Львов", birthYear = 2000, birthMonth = 10, birthDay = 3, closeness = 2, lastContactAt = now))
    private val olga = pf(Person(id = 3, firstName = "Ольга", gender = "Женский", city = "киев ", relation = "Коллега"))
    private val all = listOf(anna, ivan, olga)

    private fun ids(f: RecipientFilter) = all.filter { f.matches(it, today, now) }.map { it.person.id }

    @Test fun emptyFilterMatchesEveryone() = assertEquals(listOf(1L, 2L, 3L), ids(RecipientFilter()))
    @Test fun byGender() = assertEquals(listOf(1L, 3L), ids(RecipientFilter(genders = setOf("Женский"))))
    @Test fun byGroup() = assertEquals(listOf(1L), ids(RecipientFilter(groupIds = setOf(7))))
    @Test fun cityIgnoresCaseAndSpaces() = assertEquals(listOf(1L, 3L), ids(RecipientFilter(cities = setOf("Киев"))))
    @Test fun combinedConditionsAreAnd() = assertEquals(listOf(3L), ids(RecipientFilter(genders = setOf("Женский"), relations = setOf("Коллега"))))
    @Test fun ageRangeSkipsUnknownAge() = assertEquals(listOf(1L), ids(RecipientFilter(ageFrom = 30, ageTo = 40)))
    @Test fun closeness() = assertEquals(listOf(1L), ids(RecipientFilter(minCloseness = 4)))
    @Test fun birthdaySoon() = assertEquals(listOf(2L), ids(RecipientFilter(birthdayWithinDays = 7)))
    @Test fun longNoContactIncludesNeverContacted() = assertEquals(listOf(1L, 3L), ids(RecipientFilter(noContactDays = 30)))
}

class AppointmentLogicTest {
    private val anna = Person(id = 1, firstName = "Анна", middleName = "Викторовна")
    private val start = LocalDateTime.of(2026, 10, 2, 14, 30)
    private val appt = Appointment(id = 5, personId = 1, start = AppointmentLogic.millis(start), durationMin = 60,
        title = "Стрижка", place = "", channel = NotifyChannel.WHATSAPP.name)

    @Test fun fillTemplateAndDropEmptyLines() {
        val text = AppointmentLogic.fill("{имя}, запись {дата} ({день_недели}) в {время}.\n{услуга}\n{место}", appt, anna)
        assertEquals("Анна, запись 2 октября (пятница) в 14:30.\nСтрижка", text)
    }

    @Test fun whenTextIsRelative() {
        val now = LocalDateTime.of(2026, 10, 1, 9, 0)
        assertEquals("Анна, завтра в 14:30", AppointmentLogic.fill("{имя}, {когда} в {время}", appt, anna, now = now))
    }

    @Test fun remindersSkipPastAndNoneChannel() {
        val now = AppointmentLogic.millis(start.minusHours(5))
        val r = AppointmentLogic.buildReminders(appt, listOf(24 * 60, 120), listOf(30), now)
        assertEquals(listOf(120 to "CLIENT", 30 to "ME"), r.map { it.offsetMin to it.target })
        assertEquals(AppointmentLogic.millis(start.minusHours(2)), r[0].fireAt)

        val silent = AppointmentLogic.buildReminders(appt.copy(channel = NotifyChannel.NONE.name), listOf(120), listOf(30), now)
        assertEquals(listOf("ME"), silent.map { it.target })
    }

    @Test fun ukrainianTemplate() {
        val now = LocalDateTime.of(2026, 10, 1, 9, 0)
        val tpl = MessageLang.UK.template(TemplateKind.REMINDER)
        assertEquals(
            "Доброго ранку, Анна! Нагадую про запис: завтра о 14:30.\nСтрижка\nЯкщо плани змінилися — будь ласка, повідомте.",
            AppointmentLogic.fill(tpl, appt, anna, MessageLang.UK, now),
        )
        val confirm = AppointmentLogic.fill(MessageLang.UK.template(TemplateKind.CONFIRM), appt, anna, MessageLang.UK, now)
        assertEquals("Доброго ранку, Анна! Підтверджую ваш запис: 2 жовтня (пʼятниця) о 14:30.\nСтрижка", confirm)
    }

    @Test fun englishDates() {
        val evening = LocalDateTime.of(2026, 10, 1, 19, 0)
        val text = AppointmentLogic.fill(MessageLang.EN.template(TemplateKind.CONFIRM), appt, anna, MessageLang.EN, evening)
        assertEquals("Good evening, Anna! Your appointment is confirmed: Friday, October 2 at 14:30.\nСтрижка", text)
    }

    @Test fun greetingDependsOnTimeOfSending() {
        fun at(h: Int) = ArchiveLogic.fillTemplate("{приветствие}, {имя}!", anna, MessageLang.RU, java.time.LocalTime.of(h, 0))
        assertEquals("Доброе утро, Анна!", at(8))
        assertEquals("Добрый день, Анна!", at(13))
        assertEquals("Добрый вечер, Анна!", at(19))
        assertEquals("Доброй ночи, Анна!", at(2))
        // Токен на другом языке тоже понимается
        assertEquals("Добрий вечір, Анна!", ArchiveLogic.fillTemplate("{привітання}, {імʼя}!", anna, MessageLang.UK, java.time.LocalTime.of(18, 30)))
    }

    @Test fun placeholdersInAnyLanguage() {
        // Можно смешивать: {ім'я} с обычным апострофом, {name}, {імʼя_по_батькові}
        assertEquals("Анна / Анна / Анна Викторовна", ArchiveLogic.fillTemplate("{ім'я} / {name} / {імʼя_по_батькові}", anna))
    }

    @Test fun offsetTitles() {
        assertEquals("за сутки", AppointmentLogic.offsetTitle(24 * 60))
        assertEquals("за 3 дня", AppointmentLogic.offsetTitle(3 * 24 * 60))
        assertEquals("за 2 часа", AppointmentLogic.offsetTitle(120))
        assertEquals("за 1 неделю", AppointmentLogic.offsetTitle(7 * 24 * 60))
        assertEquals("за 30 мин", AppointmentLogic.offsetTitle(30))
    }

    @Test fun conflictsIgnoreCancelledAndSelf() {
        fun other(id: Long, h: Int, m: Int, status: AppointmentStatus = AppointmentStatus.PLANNED) = AppointmentFull(
            Appointment(id = id, personId = 2, start = AppointmentLogic.millis(start.withHour(h).withMinute(m)), durationMin = 30, status = status.name), null, emptyList(),
        )
        val list = listOf(other(5, 14, 30), other(6, 15, 0), other(7, 15, 30), other(8, 14, 0, AppointmentStatus.CANCELLED))
        assertEquals(listOf(6L), AppointmentLogic.conflicts(appt, list).map { it.appointment.id })
    }
}

class RelationsTest {
    private val maria = Person(id = 1, firstName = "Мария", gender = "Женский")
    private val ivan = Person(id = 2, firstName = "Иван", gender = "Мужской")
    private val people = mapOf(1L to maria, 2L to ivan)

    @Test fun inverseWithGender() {
        // Мария — мать Ивана
        val rel = Relation(id = 1, personId = 2, relatedId = 1, type = RelationType.PARENT.name)
        assertEquals("Мать", Relations.viewFor(2, listOf(rel), people).single().label)
        assertEquals("Сын", Relations.viewFor(1, listOf(rel), people).single().label)
    }

    @Test fun symmetricTypes() {
        val rel = Relation(id = 1, personId = 1, relatedId = 2, type = RelationType.SPOUSE.name)
        assertEquals("Муж", Relations.viewFor(1, listOf(rel), people).single().label)
        assertEquals("Жена", Relations.viewFor(2, listOf(rel), people).single().label)
    }

    @Test fun everyTypeHasConsistentInverse() {
        RelationType.entries.forEach { assertEquals(it, it.inverse.inverse) }
        assertTrue(RelationType.PARENT.family)
        assertFalse(RelationType.COLLEAGUE.family)
    }
}
