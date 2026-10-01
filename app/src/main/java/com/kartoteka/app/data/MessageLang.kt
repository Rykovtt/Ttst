package com.kartoteka.app.data

import com.kartoteka.app.i18n.t

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
        "сегодня", "завтра", "послезавтра", "мин", "{приветствие}, {имя}! ",
        listOf("имя", "имя_отчество", "фамилия", "прозвище", "дата", "время", "день_недели", "когда", "услуга", "место", "длительность", "приветствие"),
    ),
    UK(
        "Українська", Locale("uk"),
        listOf("січня", "лютого", "березня", "квітня", "травня", "червня", "липня", "серпня", "вересня", "жовтня", "листопада", "грудня"),
        "сьогодні", "завтра", "післязавтра", "хв", "{привітання}, {імʼя}! ",
        listOf("імʼя", "імʼя_по_батькові", "прізвище", "прізвисько", "дата", "час", "день_тижня", "коли", "послуга", "місце", "тривалість", "привітання"),
    ),
    EN(
        "English", Locale.ENGLISH,
        listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"),
        "today", "tomorrow", "the day after tomorrow", "min", "{greeting}, {name}! ",
        listOf("name", "full_name", "surname", "nickname", "date", "time", "weekday", "when", "service", "place", "duration", "greeting"),
    );

    /** Токены, доступные в рассылке (без полей записи). */
    val personTokens: List<String> get() = (listOf(tokens.last()) + tokens.take(4)).map { "{$it}" }

    /**
     * Приветствие по времени отправки: утро 5–12, день 12–17, вечер 17–24, ночь 0–5.
     * Считается в момент отправки, поэтому подходит и для автоматических напоминаний.
     */
    fun timeGreeting(time: java.time.LocalTime): String {
        val h = time.hour
        val i = when (h) {
            in 5..11 -> 0
            in 12..16 -> 1
            in 17..23 -> 2
            else -> 3
        }
        return when (this) {
            RU -> listOf("Доброе утро", "Добрый день", "Добрый вечер", "Доброй ночи")
            UK -> listOf("Доброго ранку", "Добрий день", "Добрий вечір", "Доброї ночі")
            EN -> listOf("Good morning", "Good afternoon", "Good evening", "Hello")
        }[i]
    }
    val allTokens: List<String> get() = tokens.map { "{$it}" }

    fun dateText(day: Int, month: Int): String =
        if (this == EN) "${monthsGen[month - 1]} $day" else "$day ${monthsGen[month - 1]}"

    fun template(kind: TemplateKind): String = when (this) {
        RU -> when (kind) {
            TemplateKind.CONFIRM -> "{приветствие}, {имя}! Подтверждаю вашу запись: {дата} ({день_недели}) в {время}.\n{услуга}\n{место}"
            TemplateKind.REMINDER -> "{приветствие}, {имя}! Напоминаю о записи: {когда} в {время}.\n{услуга}\n{место}\nЕсли планы изменились — пожалуйста, сообщите."
            TemplateKind.CANCEL -> "{приветствие}, {имя}! К сожалению, запись на {дата} в {время} отменяется. Давайте подберём другое время."
            TemplateKind.RESCHEDULE -> "{приветствие}, {имя}! Ваша запись перенесена: теперь {дата} ({день_недели}) в {время}.\n{место}"
        }
        UK -> when (kind) {
            TemplateKind.CONFIRM -> "{привітання}, {імʼя}! Підтверджую ваш запис: {дата} ({день_тижня}) о {час}.\n{послуга}\n{місце}"
            TemplateKind.REMINDER -> "{привітання}, {імʼя}! Нагадую про запис: {коли} о {час}.\n{послуга}\n{місце}\nЯкщо плани змінилися — будь ласка, повідомте."
            TemplateKind.CANCEL -> "{привітання}, {імʼя}! На жаль, запис на {дата} о {час} скасовується. Давайте підберемо інший час."
            TemplateKind.RESCHEDULE -> "{привітання}, {імʼя}! Ваш запис перенесено: тепер {дата} ({день_тижня}) о {час}.\n{місце}"
        }
        EN -> when (kind) {
            TemplateKind.CONFIRM -> "{greeting}, {name}! Your appointment is confirmed: {weekday}, {date} at {time}.\n{service}\n{place}"
            TemplateKind.REMINDER -> "{greeting}, {name}! A reminder about your appointment {when} at {time}.\n{service}\n{place}\nIf your plans have changed, please let me know."
            TemplateKind.CANCEL -> "{greeting}, {name}! Unfortunately the appointment on {date} at {time} has to be cancelled. Let's find another time."
            TemplateKind.RESCHEDULE -> "{greeting}, {name}! Your appointment has been moved to {weekday}, {date} at {time}.\n{place}"
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

enum class TemplateKind(private val titleRu: String) {
    CONFIRM("Подтверждение"),
    REMINDER("Напоминание"),
    RESCHEDULE("Перенос"),
    CANCEL("Отмена"),
    ;

    /** Название на языке интерфейса. */
    val title: String get() = t(titleRu)
}
