package com.kartoteka.app.screens

import androidx.test.core.app.ApplicationProvider
import com.kartoteka.app.assistant.Noa
import com.kartoteka.app.assistant.NoaContext
import com.kartoteka.app.assistant.NoaContext.Topic
import com.kartoteka.app.data.Appointment
import com.kartoteka.app.data.AppointmentLogic
import com.kartoteka.app.data.AppointmentStatus
import com.kartoteka.app.data.ContactItem
import com.kartoteka.app.data.Group
import com.kartoteka.app.data.JournalEntry
import com.kartoteka.app.data.Person
import com.kartoteka.app.data.ServiceTemplate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/** Данные для модели по теме фразы: люди, расписание, дни рождения, «давно не общались», счётчики, услуги — на настоящей базе в памяти. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class NoaContextTest {
    private val app get() = ApplicationProvider.getApplicationContext<TestApp>()
    private val repo get() = app.repository
    private val now = LocalDateTime.of(2026, 10, 4, 10, 0)
    private val day = 86_400_000L
    private fun ms(daysFromNow: Long, hour: Int = 12) = AppointmentLogic.millis(now.toLocalDate().plusDays(daysFromNow).atTime(hour, 0))
    private fun ago(days: Long) = AppointmentLogic.millis(now) - days * day

    @org.junit.Before fun ru() { com.kartoteka.app.i18n.I18n.init(app, com.kartoteka.app.i18n.UiLang.RU) }

    private data class Seed(val anna: Long, val oleg: Long, val maria: Long, val clients: Long)

    private fun seed(): Seed = runBlocking {
        val clients = repo.saveGroup(Group(name = "Клиенты"))
        val friends = repo.saveGroup(Group(name = "Друзья"))
        val anna = repo.savePerson(
            Person(firstName = "Анна", lastName = "Иванова", nickname = "Аня", company = "Студия", city = "Киев", relation = "клиент",
                birthDay = 8, birthMonth = 10, birthYear = 1990, lastContactAt = ago(8), notes = "аллергия на латекс"),
            listOf(ContactItem(type = "PHONE", value = "+380671112233")), emptyList(), listOf(clients))
        val oleg = repo.savePerson(Person(firstName = "Олег", lastName = "Петренко", city = "Львов", lastContactAt = ago(60)),
            emptyList(), emptyList(), listOf(friends))
        val maria = repo.savePerson(Person(firstName = "Мария", lastName = "Фролова", birthDay = 30, birthMonth = 12), emptyList(), emptyList(), listOf(clients))
        repo.savePerson(Person(firstName = "Илья", lastName = "Рыков", lastContactAt = ago(3)), emptyList(), emptyList(), emptyList())
        repo.addJournal(JournalEntry(personId = anna, date = ago(8), kind = "Звонок", text = "просила перенести сеанс"))
        repo.addJournal(JournalEntry(personId = anna, date = ago(20), kind = "Заметка", text = "любит кофе"))
        val tattoo = repo.saveService(ServiceTemplate(name = "Тату-сеанс", durationMin = 180, place = "Студия"))
        repo.saveService(ServiceTemplate(name = "Консультация", durationMin = 30))
        repo.saveAppointment(Appointment(personId = anna, start = ms(-30), title = "Тату-сеанс", serviceId = tattoo, status = AppointmentStatus.DONE.name), emptyList(), emptyList())
        repo.saveAppointment(Appointment(personId = anna, start = ms(1, 15), title = "Тату-сеанс", durationMin = 180, serviceId = tattoo), emptyList(), emptyList())
        repo.saveAppointment(Appointment(personId = oleg, start = ms(0, 18), title = "Консультация", durationMin = 30), emptyList(), emptyList())
        repo.saveAppointment(Appointment(personId = maria, start = ms(0, 16), status = AppointmentStatus.CANCELLED.name), emptyList(), emptyList())
        repo.saveAppointment(Appointment(personId = maria, start = ms(3, 11)), emptyList(), emptyList())
        Seed(anna, oleg, maria, clients)
    }

    private fun ctx(text: String, last: com.kartoteka.app.data.PersonFull? = null, budget: Int = NoaContext.DATA_BUDGET) =
        runBlocking { NoaContext.build(repo, text, last, now, budget) }

    @Test fun personCardHasHistoryJournalAndNextVisit() {
        seed()
        val c = ctx("когда Аня была у меня в последний раз")
        println(c)
        assertTrue(c, c.contains("Анна Иванова (Аня)"))
        assertTrue(c, c.contains("tel +380671112233"))
        assertTrue(c, c.contains("last contact 26.09 (8d ago)"))
        assertTrue(c, c.contains("appts 2, last 04.09"))
        assertTrue(c, c.contains("next 05.10 15:00 Тату-сеанс"))
        assertTrue(c, c.contains("звонок: просила перенести сеанс"))
        assertTrue(c, c.contains("groups: Клиенты"))
        assertTrue(c, c.contains("b-day 08.10"))
    }

    @Test fun cardIsFirstAndTrimmedToBudget() {
        seed()
        val c = ctx("расскажи про Аню", budget = 220)
        assertTrue(c.length <= 220)
        assertTrue("карточка важнее фона: $c", c.startsWith("Анна Иванова"))
    }

    @Test fun pronounUsesLastPersonCard() = runBlocking {
        val s = seed()
        val last = repo.getPerson(s.oleg)
        val c = ctx("а где он живёт?", last)
        assertTrue(c, c.contains("Олег Петренко") && c.contains("Львов"))
        // без последнего собеседника карточки нет
        assertFalse(ctx("а где он живёт?").contains("Львов"))
        // «о ком говорили» тоже возвращает последнего
        assertTrue(ctx("о ком мы говорили", last).contains("Олег Петренко"))
    }

    @Test fun twoNamedPeopleBothGetCards() {
        seed()
        val c = ctx("сравни Аню и Олега")
        assertTrue(c, c.contains("Анна Иванова") && c.contains("Олег Петренко"))
    }

    @Test fun agendaTodayTomorrowSkipsCancelled() {
        seed()
        val c = ctx("что у меня сегодня и завтра")
        assertTrue(c, c.contains("Today 04.10: 18:00 Олег Петренко (Консультация) 30m"))
        assertTrue(c, c.contains("Tomorrow 05.10: 15:00 Анна Иванова (Тату-сеанс) 180m"))
        assertFalse("отменённая запись не показывается: $c", c.contains("16:00"))
    }

    @Test fun agendaSaysNothingWhenAskedAboutEmptyDay() {
        seed()
        val c = ctx("что у меня послезавтра")
        assertTrue(c, c.contains("Mon 06.10: nothing") || c.contains("06.10: nothing"))
    }

    @Test fun weekAgendaOnWeekWord() {
        seed()
        val c = ctx("что у меня на этой неделе")
        assertTrue(c, c.contains("04.10") && c.contains("05.10") && c.contains("07.10: 11:00 Мария Фролова"))
    }

    @Test fun birthdaysWithinTwoWeeks() {
        seed()
        val c = ctx("у кого скоро день рождения")
        assertTrue(c, c.contains("Birthdays 14d: Анна Иванова 08.10 in 4d, turns 36"))
        assertFalse("дальше 14 дней не показываем: $c", c.contains("Мария Фролова 30.12"))
    }

    @Test fun staleListsOldestFirstWithDays() {
        seed()
        val c = ctx("кому я давно не писал")
        assertTrue(c, c.contains("Not contacted 30d+: Олег Петренко 60d"))
        assertFalse("недавние контакты не в списке: $c", c.contains("Илья Рыков 3d"))
        val c14 = ctx("с кем не общался 7 дней")
        assertTrue(c14, c14.contains("Not contacted 7d+") && c14.contains("Анна Иванова 8d"))
    }

    @Test fun recentContacts() {
        seed()
        val c = ctx("с кем я последним общался")
        assertTrue(c, c.contains("Recently contacted: Илья Рыков 01.10; Анна Иванова 26.09"))
    }

    @Test fun countsAndGroups() {
        seed()
        val c = ctx("сколько у меня клиентов и друзей")
        assertTrue(c, c.contains("People total: 4; Клиенты 2, Друзья 1"))
        // без вопроса о количестве групповая разбивка не нужна
        assertFalse(ctx("позвони маме").contains("Клиенты 2"))
        assertTrue(ctx("позвони маме").contains("People total: 4"))
    }

    @Test fun groupMembersByName() {
        seed()
        val c = ctx("кто в группе клиенты")
        assertTrue(c, c.contains("Group Клиенты (2): Анна Иванова, Мария Фролова"))
    }

    @Test fun servicesWithDurations() {
        seed()
        val c = ctx("сколько длится тату сеанс")
        assertTrue(c, c.contains("Services: Консультация 30min; Тату-сеанс 180min, Студия"))
    }

    @Test fun whoAttributeMatchesCity() {
        seed()
        val c = ctx("кто у меня живёт во Львове")
        assertTrue(c, c.contains("Matches: Олег Петренко — Львов"))
    }

    @Test fun lastTalkedAboutIsBackgroundLine() = runBlocking {
        val s = seed()
        val c = ctx("как дела", repo.getPerson(s.maria))
        assertTrue(c, c.contains("Last talked about: Мария Фролова"))
    }

    @Test fun nothingAskedKeepsContextTiny() {
        seed()
        val c = ctx("как дела")
        assertTrue(c, c.length < 200)
        assertTrue(c, c.contains("People total: 4"))
    }

    @Test fun contextRespectsBudgetWithManyPeopleAndLongJournals() = runBlocking {
        val g = repo.saveGroup(Group(name = "Клиенты"))
        repeat(60) { i ->
            val id = repo.savePerson(Person(firstName = "Клиент$i", lastName = "Тестов", city = "Киев", birthDay = 8, birthMonth = 10, lastContactAt = ago(40L + i)),
                emptyList(), emptyList(), listOf(g))
            repeat(5) { j -> repo.addJournal(JournalEntry(personId = id, date = ago(j * 5L), text = "длинная запись хроники ".repeat(10))) }
            repo.saveAppointment(Appointment(personId = id, start = ms(i % 3L, 9 + i % 9), title = "Сеанс"), emptyList(), emptyList())
        }
        for (q in listOf("что у меня на неделе и у кого день рождения", "кому давно не писал", "расскажи про Клиент5", "сколько клиентов в группе клиенты", "что у меня сегодня")) {
            val c = ctx(q)
            assertTrue("«$q» → ${c.length} символов", c.length <= NoaContext.DATA_BUDGET)
            assertTrue(c.isNotBlank())
        }
        // и при урезанном бюджете — тоже
        assertTrue(ctx("кому давно не писал", budget = 150).length <= 150)
    }

    @Test fun nothingWrongWithEmptyBook() {
        val c = ctx("что у меня завтра")
        assertTrue(c, c.contains("People total: 0"))
    }

    @Test fun namesCarryNicknamesAndRecencyOrder() = runBlocking {
        seed()
        val n = Noa(app).knownNames()
        assertEquals("Илья Рыков", n[0])
        assertTrue(n.toString(), "Анна Иванова (Аня)" in n)
        assertEquals(4, n.size)
        assertEquals(2, Noa(app).knownNames(2).size)
    }

    @Test fun noaContextForDelegates() = runBlocking {
        seed()
        val c = Noa(app).contextFor("расскажи про Олега")
        assertTrue(c, c.contains("Олег Петренко"))
    }

    // --- маршрутизация по темам (без базы) ---

    @Test fun topicRouting() {
        assertTrue(Topic.AGENDA in NoaContext.topicsOf("Что у меня завтра?"))
        assertTrue(Topic.AGENDA in NoaContext.topicsOf("що в мене на п'ятницю"))
        assertTrue(Topic.WEEK in NoaContext.topicsOf("что на этой неделе"))
        assertTrue(Topic.BIRTHDAYS in NoaContext.topicsOf("у кого день рождения"))
        assertTrue(Topic.BIRTHDAYS in NoaContext.topicsOf("в кого день народження"))
        assertTrue(Topic.COUNTS in NoaContext.topicsOf("скільки в мене клієнтів"))
        assertTrue(Topic.SERVICES in NoaContext.topicsOf("сколько стоит и сколько длится сеанс"))
        assertTrue(Topic.STALE in NoaContext.topicsOf("кому я давно не звонил"))
        assertTrue(Topic.STALE in NoaContext.topicsOf("з ким я не спілкувався місяць"))
        assertTrue(Topic.RECENT in NoaContext.topicsOf("кому я последним звонил"))
        assertTrue(Topic.LASTP in NoaContext.topicsOf("о ком мы говорили"))
        assertTrue(Topic.WHO in NoaContext.topicsOf("кто живёт в Киеве"))
        assertTrue(NoaContext.topicsOf("как дела").isEmpty())
        assertTrue(NoaContext.topicsOf("расскажи анекдот").isEmpty())
    }

    @Test fun staleDaysParsing() {
        assertEquals(30, NoaContext.staleDays("кому давно не писал"))
        assertEquals(14, NoaContext.staleDays("не общались 2 недели"))
        assertEquals(45, NoaContext.staleDays("не звонил 45 дней"))
        assertEquals(30, NoaContext.staleDays("не писал месяц"))
        assertEquals(60, NoaContext.staleDays("не бачилися 2 місяці"))
        assertEquals(180, NoaContext.staleDays("не виделись полгода"))
    }
}
