package com.kartoteka.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class StatsLogicTest {
    private val today = LocalDate.of(2026, 10, 15)
    private fun at(d: LocalDate, h: Int = 12) = AppointmentLogic.millis(d.atTime(h, 0))
    private fun appt(id: Long, person: Person, d: LocalDate, status: AppointmentStatus = AppointmentStatus.PLANNED, reminders: Int = 0) =
        AppointmentFull(
            Appointment(id = id, personId = person.id, start = at(d), status = status.name), person,
            List(reminders) { AppointmentReminder(id = id * 10 + it, appointmentId = id, target = "ME", offsetMin = 60, fireAt = at(d)) },
        )

    @Test
    fun countsCurrentMonthAgainstPrevious() {
        val anna = Person(id = 1, firstName = "Анна")
        val ivan = Person(id = 2, firstName = "Иван")
        val list = listOf(
            appt(1, anna, today.withDayOfMonth(3), reminders = 2),
            appt(2, anna, today.withDayOfMonth(10)),
            appt(3, ivan, today.withDayOfMonth(10)),
            appt(4, ivan, today.withDayOfMonth(11), AppointmentStatus.CANCELLED),
            appt(5, ivan, today.minusMonths(1).withDayOfMonth(5)),
            appt(6, ivan, today.minusMonths(1).withDayOfMonth(6)),
        )
        val r = StatsLogic.compute(StatsLogic.Period.MONTH, list, emptyList(), today)
        assertEquals(3, r.appointments)
        assertEquals(50, r.appointmentsDelta)
        assertEquals(2, r.reminders)
        assertNull(r.remindersDelta)
        assertEquals(31, r.bars.size)
        assertEquals(2, r.bars[9])
        assertEquals(anna.id, r.topPerson?.first?.id)
    }

    @Test
    fun yearHasTwelveBars() {
        val p = Person(id = 1, firstName = "А")
        val r = StatsLogic.compute(StatsLogic.Period.YEAR, listOf(appt(1, p, today)), emptyList(), today)
        assertEquals(12, r.bars.size)
        assertEquals(1, r.bars[9])
    }

    @Test
    fun deltaNeedsBase() {
        assertNull(StatsLogic.delta(5, 0))
        assertEquals(-50, StatsLogic.delta(1, 2))
    }
}
