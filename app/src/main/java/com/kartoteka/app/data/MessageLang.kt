package com.kartoteka.app.data

import java.util.Locale

/**
 * Язык исходящих сообщений (рассылки, подтверждения, напоминания).
 * Даты, дни недели и «завтра/сегодня» подставляются на выбранном языке.
 * Плейсхолдеры можно писать на любом из языков — {імʼя}, {name} и {имя} равнозначны.
 */
enum class MessageLang(
    val title: String,
    val locale: Locale,
    val monthsGen: List<String>,
    val today: String,
    val tomorrow: String,
    val afterTomorrow: String,
    val minutes: String,
    val greeting: String,
    /** Плейсхолдеры в порядке [TOKENS]. */
    val tokens: List<String>,
) {
    RU(
        "Русский", Locale("ru"),
        ArchiveLogic.MONTHS_GEN,
        "сегодня", "завтра", "послезавтра", "мин", "Привет, {имя}! ",
        listOf("имя", "имя_отчество", "фамилия", "прозвище", "дата", "время", "день_недели", "когда", "услуга", "место", "длительность"),
    ),
    UK(
        "Українська", Locale("uk"),
        listOf("січня", "лютого", "березня", "квітня", "травня", "червня", "липня", "серпня", "вересня", "жовтня", "листопада", "грудня"),
        "сьогодні", "завтра", "післязавтра", "хв", "Привіт, {імʼя}! ",
        listOf("імʼя", "імʼя_по_батькові", "прізвище", "прізвисько", "дата", "час", "день_тижня", "коли", "послуга", "місце", "тривалість"),
    ),
    EN(
        "English", Locale.ENGLISH,
        listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"),
        "today", "tomorrow", "the day after tomorrow", "min", "Hi {name}! ",
        listOf("name", "full_name", "surname", "nickname", "date", "time", "weekday", "when", "service", "place", "duration"),
    );

    /** Токены, доступные в рассылке (без полей записи). */
    val personTokens: List<String> get() = tokens.take(4).map { "{$it}" }
    val allTokens: List<String> get() = tokens.map { "{$it}" }

    fun dateText(day: Int, month: Int): String =
        if (this == EN) "${monthsGen[month - 1]} $day" else "$day ${monthsGen[month - 1]}"

    fun template(kind: TemplateKind): String = when (this) {
        RU -> when (kind) {
            TemplateKind.CONFIRM -> "{имя}, здравствуйте! Подтверждаю вашу запись: {дата} ({день_недели}) в {время}.\n{услуга}\n{место}"
            TemplateKind.REMINDER -> "{имя}, напоминаю о записи: {когда} в {время}.\n{услуга}\n{место}\nЕсли планы изменились — пожалуйста, сообщите."
            TemplateKind.CANCEL -> "{имя}, здравствуйте! К сожалению, запись на {дата} в {время} отменяется. Давайте подберём другое время."
            TemplateKind.RESCHEDULE -> "{имя}, здравствуйте! Ваша запись перенесена: теперь {дата} ({день_недели}) в {время}.\n{место}"
        }
        UK -> when (kind) {
            TemplateKind.CONFIRM -> "{імʼя}, добрий день! Підтверджую ваш запис: {дата} ({день_тижня}) о {час}.\n{послуга}\n{місце}"
            TemplateKind.REMINDER -> "{імʼя}, нагадую про запис: {коли} о {час}.\n{послуга}\n{місце}\nЯкщо плани змінилися — будь ласка, повідомте."
            TemplateKind.CANCEL -> "{імʼя}, добрий день! На жаль, запис на {дата} о {час} скасовується. Давайте підберемо інший час."
            TemplateKind.RESCHEDULE -> "{імʼя}, добрий день! Ваш запис перенесено: тепер {дата} ({день_тижня}) о {час}.\n{місце}"
        }
        EN -> when (kind) {
            TemplateKind.CONFIRM -> "Hi {name}! Your appointment is confirmed: {weekday}, {date} at {time}.\n{service}\n{place}"
            TemplateKind.REMINDER -> "Hi {name}, a reminder about your appointment {when} at {time}.\n{service}\n{place}\nIf your plans have changed, please let me know."
            TemplateKind.CANCEL -> "Hi {name}, unfortunately the appointment on {date} at {time} has to be cancelled. Let's find another time."
            TemplateKind.RESCHEDULE -> "Hi {name}! Your appointment has been moved to {weekday}, {date} at {time}.\n{place}"
        }
    }

    companion object {
        /** Канонические имена (как в [RU]). */
        val TOKENS = RU.tokens

        fun of(code: String?): MessageLang? = entries.firstOrNull { it.name == code }

        /** Приводит плейсхолдеры любого языка к каноническим {имя}, {дата}… */
        fun canonicalize(text: String): String {
            var s = text.replace("{ім'я", "{імʼя").replace("{ім’я", "{імʼя")
            for (lang in entries) {
                if (lang == RU) continue
                // Длинные токены первыми: {імʼя_по_батькові} раньше {імʼя}
                lang.tokens.withIndex().sortedByDescending { it.value.length }.forEach { (i, t) ->
                    s = s.replace("{$t}", "{${TOKENS[i]}}")
                }
            }
            return s
        }
    }
}

enum class TemplateKind(val title: String) {
    CONFIRM("Подтверждение"),
    REMINDER("Напоминание"),
    RESCHEDULE("Перенос"),
    CANCEL("Отмена"),
}
