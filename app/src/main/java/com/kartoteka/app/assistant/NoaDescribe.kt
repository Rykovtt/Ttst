package com.kartoteka.app.assistant

import com.kartoteka.app.i18n.I18n
import com.kartoteka.app.i18n.t
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Доверие к командам от модели. Маленькая модель на телефоне ошибается, поэтому всё, что она придумала
 * и что что-то меняет или отправляет, сначала проговариваем («Поняла так: … Выполнить?») и ждём «да».
 * Безопасное (только читает, показывает, открывает экран или управляет плеером) выполняем сразу.
 */
object NoaTrust {
    /**
     * Нужно ли подтверждение для команды, пришедшей от модели. Список исчерпывающий (when без else):
     * новая команда не соберётся, пока для неё не решено, безопасна она или нет. Цепочка безопасна, только если безопасны все шаги.
     */
    fun needsConfirm(i: NoaIntent): Boolean = when (i) {
        is NoaIntent.Sequence -> i.steps.any { needsConfirm(it) }
        // Только читаем и показываем.
        is NoaIntent.Agenda, is NoaIntent.PersonInfo, is NoaIntent.Find, is NoaIntent.Open, is NoaIntent.OpenScreen,
        is NoaIntent.Select, is NoaIntent.Tool, is NoaIntent.Calc, is NoaIntent.Convert, is NoaIntent.Crm,
        is NoaIntent.ReadMessages -> false
        // Управление телефоном без последствий: плеер, фонарик, свернуть.
        is NoaIntent.Media, is NoaIntent.Volume, is NoaIntent.Flashlight, is NoaIntent.GoHome -> false
        // Служебные: ничего не исполняют.
        is NoaIntent.Wrong, is NoaIntent.Dismiss, is NoaIntent.Repeat, is NoaIntent.Unknown -> false
        // Всё остальное — пишет людям, меняет данные, звонит, уходит в сеть или в другие приложения.
        is NoaIntent.CreateAppointment, is NoaIntent.CancelAppointment, is NoaIntent.MoveAppointment,
        is NoaIntent.Call, is NoaIntent.Message, is NoaIntent.Reply, is NoaIntent.AddNote, is NoaIntent.Remind,
        is NoaIntent.Alarm, is NoaIntent.Timer, is NoaIntent.Route, is NoaIntent.Play, is NoaIntent.WebSearch,
        is NoaIntent.ShareData, is NoaIntent.Favorite, is NoaIntent.LaunchApp, is NoaIntent.CloseApp,
        is NoaIntent.OpenContact, is NoaIntent.PhoneSettings, is NoaIntent.Lock, is NoaIntent.Backup, is NoaIntent.VoiceNote -> true
    }
}

/** Короткое проговариваемое описание команды — для вопроса «Поняла так: … Выполнить?». */
object NoaDescribe {
    private fun who(p: String) = p.trim().ifBlank { t("того же человека") }

    private fun dm(d: LocalDate) = I18n.dayMonth(d.dayOfMonth, d.monthValue)

    private fun hm(h: Int, m: Int) = "%02d:%02d".format(h, m)

    /** «5 октября в 15:00» / «5 октября» / пусто. */
    private fun whenText(dt: LocalDateTime?, hadDate: Boolean, hadTime: Boolean): String = when {
        dt == null -> ""
        hadTime && hadDate -> t("%1\$s в %2\$s", dm(dt.toLocalDate()), hm(dt.hour, dt.minute))
        hadTime -> t("в %1\$s", hm(dt.hour, dt.minute))
        else -> dm(dt.toLocalDate())
    }

    private fun channel(c: NoaIntent.Channel) = when (c) {
        NoaIntent.Channel.WHATSAPP -> "WhatsApp"
        NoaIntent.Channel.TELEGRAM -> "Telegram"
        NoaIntent.Channel.SMS -> "SMS"
        NoaIntent.Channel.VIBER -> "Viber"
    }

    private fun section(s: NoaIntent.Section) = when (s) {
        NoaIntent.Section.PEOPLE -> t("Люди")
        NoaIntent.Section.CALENDAR -> t("Календарь")
        NoaIntent.Section.MAP -> t("Карта")
        NoaIntent.Section.BROADCAST -> t("Рассылка")
        NoaIntent.Section.SETTINGS -> t("Настройки")
        NoaIntent.Section.SERVICES -> t("Услуги")
    }

    private fun data(d: NoaIntent.Data) = when (d) {
        NoaIntent.Data.NOTES -> t("заметки")
        NoaIntent.Data.PHONE -> t("телефон")
        NoaIntent.Data.ADDRESS -> t("адрес")
        NoaIntent.Data.EMAIL -> t("почту")
        NoaIntent.Data.BIRTHDAY -> t("день рождения")
        NoaIntent.Data.CARD -> t("карточку")
    }

    private fun target(s: String) = when {
        s == "notes" -> t("блокнот")
        s == "google" -> t("поиск Google")
        s == "clipboard" -> t("буфер обмена")
        s.startsWith("app:") -> s.removePrefix("app:")
        else -> t("любое приложение на выбор")
    }

    private fun control(c: NoaMedia.Control) = when (c) {
        NoaMedia.Control.PAUSE -> t("пауза")
        NoaMedia.Control.RESUME -> t("продолжить")
        NoaMedia.Control.NEXT -> t("следующий трек")
        NoaMedia.Control.PREV -> t("предыдущий трек")
        NoaMedia.Control.SHUFFLE_ON -> t("перемешать")
        NoaMedia.Control.SHUFFLE_OFF -> t("не перемешивать")
        NoaMedia.Control.REPEAT -> t("повтор")
        NoaMedia.Control.STOP -> t("стоп")
        NoaMedia.Control.LOUDER -> t("громче")
        NoaMedia.Control.QUIETER -> t("тише")
        NoaMedia.Control.WHAT -> t("что сейчас играет")
    }

    private fun duration(sec: Int): String {
        val h = sec / 3600; val m = sec % 3600 / 60; val s = sec % 60
        return listOfNotNull(
            if (h > 0) t("%1\$s ч", h) else null, if (m > 0) t("%1\$s мин", m) else null, if (s > 0) t("%1\$s сек", s) else null,
        ).joinToString(" ").ifBlank { t("%1\$s сек", 0) }
    }

    /** Описание одной команды или цепочки («…; затем …»). Каждая разновидность [NoaIntent] описана отдельно (when без else). */
    fun describe(i: NoaIntent): String = when (i) {
        is NoaIntent.Sequence -> i.steps.joinToString(t("; затем ")) { describe(it) }
        is NoaIntent.Volume -> when (i.kind) {
            NoaIntent.VolumeKind.MAX -> t("громкость на максимум"); NoaIntent.VolumeKind.MIN -> t("громкость на минимум")
            NoaIntent.VolumeKind.SET -> t("громкость %1\$s%%", i.percent ?: 0)
            NoaIntent.VolumeKind.UP -> t("прибавить громкость на %1\$s%%", i.percent ?: 0)
            NoaIntent.VolumeKind.DOWN -> t("убавить громкость на %1\$s%%", i.percent ?: 0)
        }
        is NoaIntent.VoiceNote -> t("записать голосовую заметку о %1\$s", who(i.personQuery))
        is NoaIntent.CreateAppointment -> {
            val w = whenText(i.dateTime, i.dateTime != null, i.hadTime)
            val base = if (w.isBlank()) t("записать %1\$s", who(i.personQuery)) else t("записать %1\$s на %2\$s", who(i.personQuery), w)
            if (i.serviceQuery.isNullOrBlank()) base else base + ", " + t("услуга: %1\$s", i.serviceQuery)
        }
        is NoaIntent.Find -> t("найти «%1\$s»", i.query)
        is NoaIntent.Open -> t("открыть карточку %1\$s", who(i.personQuery))
        is NoaIntent.OpenScreen -> t("открыть раздел «%1\$s»", section(i.section))
        is NoaIntent.Call -> t("позвонить %1\$s", who(i.personQuery))
        is NoaIntent.Message -> when {
            i.aboutAppointment && i.reminder -> t("отправить %1\$s напоминание о записи в %2\$s", who(i.personQuery), channel(i.channel))
            i.aboutAppointment -> t("отправить %1\$s подтверждение записи в %2\$s", who(i.personQuery), channel(i.channel))
            i.text.isNullOrBlank() -> t("написать %1\$s в %2\$s", who(i.personQuery), channel(i.channel))
            else -> t("написать %1\$s в %2\$s: «%3\$s»", who(i.personQuery), channel(i.channel), i.text)
        }
        is NoaIntent.Select -> t("выбрать %1\$s", who(i.personQuery))
        is NoaIntent.AddNote -> t("добавить заметку для %1\$s: «%2\$s»", who(i.personQuery), i.text)
        is NoaIntent.OpenContact -> t("открыть %1\$s у %2\$s", i.type.title, who(i.personQuery))
        is NoaIntent.Route -> {
            val to = when {
                i.place.isNotBlank() -> i.place
                i.personQuery.isNotBlank() -> when (i.kind) {
                    com.kartoteka.app.data.PlaceKind.HOME -> t("домой к %1\$s", i.personQuery)
                    com.kartoteka.app.data.PlaceKind.WORK -> t("на работу к %1\$s", i.personQuery)
                    else -> t("к %1\$s", i.personQuery)
                }
                i.kind == com.kartoteka.app.data.PlaceKind.HOME -> t("домой")
                i.kind == com.kartoteka.app.data.PlaceKind.WORK -> t("на работу")
                else -> t("в нужное место")
            }
            t("проложить маршрут: %1\$s", to) + (i.app?.takeIf { it.isNotBlank() }?.let { " (" + it + ")" } ?: "")
        }
        is NoaIntent.Agenda -> t("показать планы на %1\$s", dm(i.date))
        is NoaIntent.PersonInfo -> t("рассказать про %1\$s", who(i.personQuery))
        is NoaIntent.Favorite -> if (i.on) t("добавить %1\$s в избранное", who(i.personQuery)) else t("убрать %1\$s из избранного", who(i.personQuery))
        is NoaIntent.CancelAppointment -> {
            val base = if (i.delete) t("удалить запись %1\$s", who(i.personQuery)) else t("отменить запись %1\$s", who(i.personQuery))
            if (i.date == null) base else base + " " + t("на %1\$s", dm(i.date))
        }
        is NoaIntent.MoveAppointment -> {
            val w = whenText(i.dateTime, i.hadDate, i.hadTime)
            val old = whenText(i.from, i.fromHadDate, i.fromHadTime)
            when {
                i.personQuery.isBlank() && old.isNotBlank() && w.isNotBlank() -> t("перенести запись с %1\$s на %2\$s", old, w)
                w.isBlank() -> t("перенести запись %1\$s", who(i.personQuery))
                else -> t("перенести запись %1\$s на %2\$s", who(i.personQuery), w)
            }
        }
        is NoaIntent.LaunchApp -> t("запустить приложение «%1\$s»", i.name)
        is NoaIntent.CloseApp -> t("закрыть приложение «%1\$s»", i.name)
        is NoaIntent.ShareData -> t("передать данные %1\$s: %2\$s — куда: %3\$s", who(i.personQuery), data(i.data), target(i.target))
        is NoaIntent.WebSearch -> t("поискать в интернете «%1\$s»", i.query)
        is NoaIntent.Alarm -> t("поставить будильник на %1\$s", hm(i.hour, i.minute)) + (i.label?.takeIf { it.isNotBlank() }?.let { " (" + it + ")" } ?: "")
        is NoaIntent.Timer -> t("поставить таймер на %1\$s", duration(i.seconds))
        is NoaIntent.Flashlight -> if (i.on) t("включить фонарик") else t("выключить фонарик")
        is NoaIntent.Play -> {
            val what = if (i.query.isBlank()) t("музыку") else "«" + i.query + "»"
            t("включить %1\$s", what) + (i.app?.takeIf { it.isNotBlank() }?.let { " " + t("в %1\$s", it) } ?: "")
        }
        is NoaIntent.Media -> t("управление плеером: %1\$s", control(i.control))
        is NoaIntent.Reply -> t("ответить %1\$s: «%2\$s»", who(i.personQuery), i.text)
        is NoaIntent.ReadMessages -> if (i.personQuery.isBlank()) t("прочитать новые сообщения") else t("прочитать сообщения от %1\$s", i.personQuery)
        is NoaIntent.GoHome -> t("свернуть на главный экран")
        is NoaIntent.Wrong -> t("отметить, что я поняла неверно")
        is NoaIntent.PhoneSettings -> if (i.what.isNullOrBlank()) t("открыть настройки телефона") else t("открыть настройки телефона: %1\$s", i.what)
        is NoaIntent.Lock -> t("заблокировать приложение")
        is NoaIntent.Backup -> t("сделать резервную копию")
        is NoaIntent.Remind -> {
            val w = whenText(i.dateTime, i.dateTime != null, i.hadTime)
            if (w.isBlank()) t("напомнить: «%1\$s»", i.text) else t("напомнить: «%1\$s» — %2\$s", i.text, w)
        }
        is NoaIntent.Tool -> when (i.kind) {
            NoaIntent.ToolKind.TIME -> t("сказать, который час")
            NoaIntent.ToolKind.DATE -> t("сказать дату")
            NoaIntent.ToolKind.WEEKDAY -> t("сказать день недели")
        }
        is NoaIntent.Calc -> t("посчитать %1\$s", i.expression)
        is NoaIntent.Convert -> t("перевести %1\$s %2\$s в %3\$s", i.value.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }, i.from, i.to)
        is NoaIntent.Crm -> t("ответить на вопрос по картотеке")
        is NoaIntent.Dismiss -> t("отменить")
        is NoaIntent.Repeat -> t("повторить")
        is NoaIntent.Unknown -> t("ничего не делать")
    }

    /** Вопрос перед выполнением команды модели. */
    fun confirmQuestion(i: NoaIntent): String = t("Поняла так: %1\$s. Выполнить?", describe(i))
}
