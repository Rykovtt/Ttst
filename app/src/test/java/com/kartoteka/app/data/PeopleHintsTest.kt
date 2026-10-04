package com.kartoteka.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class PeopleHintsTest {
    private val now = LocalDateTime.of(2026, 10, 15, 10, 0)
    private fun pf(p: Person) = PersonFull(p, emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    private fun appt(at: LocalDateTime) = AppointmentFull(Appointment(id = 1, personId = 1, start = AppointmentLogic.millis(at)), null, emptyList())

    @Test
    fun todayAppointmentWins() {
        val p = pf(Person(id = 1, firstName = "А", birthDay = 16, birthMonth = 10))
        assertEquals(PersonHint.Today("16:30"), PeopleHints.hint(p, listOf(appt(now.withHour(16).withMinute(30))), now))
    }

    @Test
    fun birthdayBeforeUpcoming() {
        val p = pf(Person(id = 1, firstName = "А", birthDay = 20, birthMonth = 10))
        val h = PeopleHints.hint(p, listOf(appt(now.plusDays(2))), now)
        assertEquals(PersonHint.Birthday(5, 20, 10), h)
    }

    @Test
    fun lastMetFromPastAppointment() {
        val p = pf(Person(id = 1, firstName = "А"))
        assertEquals(PersonHint.LastMet(12), PeopleHints.hint(p, listOf(appt(now.minusDays(12))), now))
        assertNull(PeopleHints.hint(p, emptyList(), now))
    }
}
