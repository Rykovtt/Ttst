package com.kartoteka.app.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** Подсчёты для экрана «Статистика» — чистые функции, без UI. */
object StatsLogic {
    enum class Period { MONTH, PREV_MONTH, YEAR }

    data class Range(val from: LocalDate, val toExclusive: LocalDate, val prevFrom: LocalDate, val prevTo: LocalDate)

    data class Result(
        val appointments: Int,
        val appointmentsDelta: Int?,
        val newPeople: Int,
        val newPeopleDelta: Int?,
        val reminders: Int,
        val remindersDelta: Int?,
        /** Столбики графика: по дням месяца или по месяцам года. */
        val bars: List<Int>,
        val busiestWeekday: DayOfWeek?,
        val topPerson: Pair<Person, Int>?,
    )

    fun range(p: Period, today: LocalDate = LocalDate.now()): Range = when (p) {
        Period.MONTH -> YearMonth.from(today).let { Range(it.atDay(1), it.plusMonths(1).atDay(1), it.minusMonths(1).atDay(1), it.atDay(1)) }
        Period.PREV_MONTH -> YearMonth.from(today).minusMonths(1).let { Range(it.atDay(1), it.plusMonths(1).atDay(1), it.minusMonths(1).atDay(1), it.atDay(1)) }
        Period.YEAR -> LocalDate.of(today.year, 1, 1).let { Range(it, it.plusYears(1), it.minusYears(1), it) }
    }

    /** Изменение в процентах; null — не с чем сравнить. */
    fun delta(now: Int, before: Int): Int? = if (before == 0) null else ((now - before) * 100.0 / before).toInt()

    fun compute(
        period: Period,
        appts: List<AppointmentFull>,
        people: List<PersonFull>,
        today: LocalDate = LocalDate.now(),
    ): Result {
        val r = range(period, today)
        fun dateOf(ms: Long) = AppointmentLogic.zoned(ms).toLocalDate()
        fun inRange(d: LocalDate, a: LocalDate, b: LocalDate) = !d.isBefore(a) && d.isBefore(b)
        val live = appts.filter { it.appointment.appointmentStatus != AppointmentStatus.CANCELLED }
        val cur = live.filter { inRange(dateOf(it.appointment.start), r.from, r.toExclusive) }
        val prev = live.filter { inRange(dateOf(it.appointment.start), r.prevFrom, r.prevTo) }
        val newCur = people.count { inRange(dateOf(it.person.createdAt), r.from, r.toExclusive) }
        val newPrev = people.count { inRange(dateOf(it.person.createdAt), r.prevFrom, r.prevTo) }
        val remCur = cur.sumOf { it.reminders.size }
        val remPrev = prev.sumOf { it.reminders.size }
        val bars = if (period == Period.YEAR) {
            (1..12).map { m -> cur.count { dateOf(it.appointment.start).monthValue == m } }
        } else {
            val days = YearMonth.from(r.from).lengthOfMonth()
            (1..days).map { d -> cur.count { dateOf(it.appointment.start).dayOfMonth == d } }
        }
        val weekday = cur.groupingBy { dateOf(it.appointment.start).dayOfWeek }.eachCount().maxByOrNull { it.value }?.key
        val top = cur.mapNotNull { it.person }.groupingBy { it.id }.eachCount().maxByOrNull { it.value }
            ?.let { (id, n) -> cur.first { it.person?.id == id }.person!! to n }
        return Result(
            cur.size, delta(cur.size, prev.size),
            newCur, delta(newCur, newPrev),
            remCur, delta(remCur, remPrev),
            bars, weekday, top,
        )
    }
}
