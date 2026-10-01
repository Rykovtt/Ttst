package com.kartoteka.app.data

import com.kartoteka.app.i18n.t

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

object AppointmentLogic {
    /** Готовые варианты «за сколько напомнить», в минутах. */
    val presets: List<Int> = listOf(10, 30, 60, 120, 180, 24 * 60, 2 * 24 * 60, 3 * 24 * 60, 7 * 24 * 60)

    fun offsetTitle(min: Int): String = when {
        min == 0 -> t("в момент начала")
        min % (7 * 24 * 60) == 0 -> (min / (7 * 24 * 60)).let { t("за %1\$s %2\$s", it, ArchiveLogic.plural(it.toLong(), "неделю", "недели", "недель")) }
        min % (24 * 60) == 0 -> (min / (24 * 60)).let { if (it == 1) t("за сутки") else t("за %1\$s %2\$s", it, ArchiveLogic.plural(it.toLong(), "день", "дня", "дней")) }
        min % 60 == 0 -> (min / 60).let { if (it == 1) t("за час") else t("за %1\$s %2\$s", it, ArchiveLogic.plural(it.toLong(), "час", "часа", "часов")) }
        else -> t("за %1\$s мин", min)
    }

    /** Язык дат в интерфейсе (для сообщений язык передаётся явно). */
    fun uiLang(): MessageLang = when (com.kartoteka.app.i18n.I18n.lang) {
        com.kartoteka.app.i18n.UiLang.UK -> MessageLang.UK
        com.kartoteka.app.i18n.UiLang.EN -> MessageLang.EN
        else -> MessageLang.RU
    }

    fun offsetsToString(list: Collection<Int>) = list.sorted().joinToString(",")
    fun offsetsFromString(s: String?): List<Int> = s.orEmpty().split(",").mapNotNull { it.trim().toIntOrNull() }.distinct().sorted()

    fun zoned(millis: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())

    fun millis(dt: LocalDateTime): Long = dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun dateText(dt: LocalDateTime, lang: MessageLang = uiLang()) = lang.dateText(dt.dayOfMonth, dt.monthValue)

    fun timeText(dt: LocalDateTime): String = dt.format(DateTimeFormatter.ofPattern("HH:mm"))

    fun weekday(date: LocalDate, lang: MessageLang = uiLang()): String =
        date.dayOfWeek.getDisplayName(TextStyle.FULL_STANDALONE, lang.locale).let { if (lang == MessageLang.EN) it else it.lowercase(lang.locale) }

    /** «сегодня», «завтра», «послезавтра» или дата. */
    fun whenText(target: LocalDateTime, now: LocalDateTime, lang: MessageLang = MessageLang.RU): String {
        val days = java.time.temporal.ChronoUnit.DAYS.between(now.toLocalDate(), target.toLocalDate())
        return when (days) {
            0L -> lang.today
            1L -> lang.tomorrow
            2L -> lang.afterTomorrow
            else -> (if (lang == MessageLang.EN) "on " else "") + dateText(target, lang)
        }
    }

    /**
     * Подставляет данные записи в шаблон. Пустые строки (например, без места) убираются,
     * чтобы сообщение выглядело аккуратно.
     */
    fun fill(
        template: String,
        a: Appointment,
        p: Person,
        lang: MessageLang = MessageLang.RU,
        now: LocalDateTime = LocalDateTime.now(),
    ): String {
        val dt = zoned(a.start)
        val text = ArchiveLogic.fillTemplate(template, p, lang, now.toLocalTime())
            .replace("{дата}", dateText(dt, lang))
            .replace("{время}", timeText(dt))
            .replace("{день_недели}", weekday(dt.toLocalDate(), lang))
            .replace("{когда}", whenText(dt, now, lang))
            .replace("{услуга}", a.title)
            .replace("{место}", a.place)
            .replace("{длительность}", "${a.durationMin} ${lang.minutes}")
        return text.lines().map { it.trimEnd() }.filter { it.isNotBlank() }.joinToString("\n").trim()
    }

    /**
     * Текст сообщения: свой шаблон услуги, если он заполнен, иначе общий.
     * Так консультация и тату-сеанс получают каждый свои напоминания.
     */
    fun messageTemplate(service: ServiceTemplate?, kind: TemplateKind, general: String): String =
        service?.template(kind)?.takeIf { it.isNotBlank() } ?: general

    /** Список напоминаний для записи; прошедшие не создаются. */
    fun buildReminders(a: Appointment, clientOffsets: List<Int>, myOffsets: List<Int>, now: Long = System.currentTimeMillis()): List<AppointmentReminder> {
        val client = if (a.notifyChannel == NotifyChannel.NONE) emptyList() else clientOffsets
        return (client.map { ReminderTarget.CLIENT to it } + myOffsets.map { ReminderTarget.ME to it })
            .map { (t, off) -> AppointmentReminder(appointmentId = a.id, target = t.name, offsetMin = off, fireAt = a.start - off * 60_000L) }
            .filter { it.fireAt > now }
    }

    /** Пересечения по времени с другими записями (для предупреждения). */
    fun conflicts(a: Appointment, others: List<AppointmentFull>): List<AppointmentFull> =
        others.filter {
            val o = it.appointment
            o.id != a.id && o.appointmentStatus != AppointmentStatus.CANCELLED && o.start < a.end && a.start < o.end
        }
}
