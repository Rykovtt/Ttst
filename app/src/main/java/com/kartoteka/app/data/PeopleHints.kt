package com.kartoteka.app.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** Подсказка-«чип» под именем в списке людей: ближайшее важное о человеке. */
sealed interface PersonHint {
    data class Today(val time: String) : PersonHint
    data class Upcoming(val date: LocalDate, val time: String) : PersonHint
    data class Birthday(val days: Long, val day: Int, val month: Int) : PersonHint
    data class LastMet(val days: Long) : PersonHint
}

object PeopleHints {
    /**
     * Приоритет: запись сегодня → день рождения в ближайшие 2 недели → запись на неделе → когда виделись последний раз.
     * [appts] — записи этого человека (любой период).
     */
    fun hint(pf: PersonFull, appts: List<AppointmentFull>, now: LocalDateTime = LocalDateTime.now()): PersonHint? {
        val today = now.toLocalDate()
        val nowMs = AppointmentLogic.millis(now)
        val live = appts.filter { it.appointment.appointmentStatus != AppointmentStatus.CANCELLED }
        val next = live.filter { it.appointment.end >= nowMs }.minByOrNull { it.appointment.start }
        val nextAt = next?.let { AppointmentLogic.zoned(it.appointment.start) }
        if (nextAt != null && nextAt.toLocalDate() == today) return PersonHint.Today(AppointmentLogic.timeText(nextAt))

        val bd = ArchiveLogic.daysUntilBirthday(pf.person, today)
        if (bd != null && bd <= 14) return PersonHint.Birthday(bd, pf.person.birthDay ?: 1, pf.person.birthMonth ?: 1)

        if (nextAt != null && ChronoUnit.DAYS.between(today, nextAt.toLocalDate()) <= 7) {
            return PersonHint.Upcoming(nextAt.toLocalDate(), AppointmentLogic.timeText(nextAt))
        }

        val lastAppt = live.filter { it.appointment.start < nowMs }.maxOfOrNull { it.appointment.start }
        val lastJournal = pf.journal.maxOfOrNull { it.date }
        val last = listOfNotNull(lastAppt, lastJournal, pf.person.lastContactAt).filter { it <= nowMs }.maxOrNull() ?: return null
        val days = ChronoUnit.DAYS.between(AppointmentLogic.zoned(last).toLocalDate(), today)
        return PersonHint.LastMet(days)
    }
}
