package com.kartoteka.app.assistant

import com.kartoteka.app.KartotekaApp
import com.kartoteka.app.data.Appointment
import com.kartoteka.app.data.AppointmentLogic
import com.kartoteka.app.data.JournalEntry
import com.kartoteka.app.data.Person
import com.kartoteka.app.data.PersonFull
import com.kartoteka.app.data.ServiceTemplate
import com.kartoteka.app.i18n.t
import java.time.LocalDateTime

/**
 * Исполнитель команд Ноа (Этап 1): берёт разобранное намерение, находит человека/услугу,
 * выполняет действие и возвращает, что сказать вслух. Опасные действия (запись, копия) —
 * только после подтверждения.
 */
class Noa(private val app: KartotekaApp) {
    private val repo = app.repository

    sealed interface Reply {
        /** Просто ответ голосом/текстом. */
        data class Say(val text: String) : Reply
        /** Нужно подтверждение; [onYes] выполняется по «да». */
        data class Confirm(val text: String, val onYes: suspend () -> Reply) : Reply
        /** Открыть человека/запись на экране. */
        data class Say2Open(val text: String, val personId: Long? = null, val appointmentId: Long? = null) : Reply
        /** Уточнить, кого из нескольких имелось в виду. */
        data class Choose(val text: String, val options: List<PersonFull>) : Reply
        /** Перейти в раздел приложения. */
        data class Navigate(val text: String, val section: NoaIntent.Section) : Reply
    }

    suspend fun handle(text: String, now: LocalDateTime = LocalDateTime.now()): Reply =
        when (val intent = NoaParser.parse(text, now)) {
            is NoaIntent.Lock -> Reply.Confirm(t("Заблокировать приложение?")) {
                app.settings.setLockEnabled(true); Reply.Say(t("Готово, заблокировала."))
            }
            is NoaIntent.Backup -> Reply.Say(t("Резервную копию удобнее сделать в настройках, в разделе «Данные»."))
            is NoaIntent.Find -> find(intent.query)
            is NoaIntent.Open -> withPerson(intent.personQuery) { Reply.Say2Open(t("Открываю %1\$s.", it.person.displayName), personId = it.person.id) }
            is NoaIntent.OpenScreen -> Reply.Navigate(t("Открываю %1\$s.", sectionName(intent.section)), intent.section)
            is NoaIntent.Call -> withPerson(intent.personQuery) {
                val phone = it.phone ?: return@withPerson Reply.Say(t("У %1\$s нет номера телефона.", it.person.displayName))
                Reply.Say2Open(t("Звоню %1\$s.", it.person.displayName), personId = it.person.id).also { NoaActions.pendingCall = phone }
            }
            is NoaIntent.Message -> message(intent)
            is NoaIntent.AddNote -> addNote(intent)
            is NoaIntent.CreateAppointment -> createAppointment(intent, now)
            is NoaIntent.Unknown -> Reply.Say(t("Не поняла команду. Скажите, например: «запиши Анну на завтра в 12:00» или «позвони маме»."))
        }

    private fun sectionName(s: NoaIntent.Section): String = when (s) {
        NoaIntent.Section.PEOPLE -> t("людей"); NoaIntent.Section.CALENDAR -> t("календарь")
        NoaIntent.Section.MAP -> t("карту"); NoaIntent.Section.BROADCAST -> t("рассылку")
        NoaIntent.Section.SETTINGS -> t("настройки"); NoaIntent.Section.SERVICES -> t("услуги")
    }

    private suspend fun find(query: String): Reply {
        if (query.isBlank()) return Reply.Say(t("Кого найти?"))
        val hits = matches(query)
        return when {
            hits.isEmpty() -> Reply.Say(t("Никого не нашла по запросу «%1\$s».", query))
            hits.size == 1 -> Reply.Say2Open(t("Нашла: %1\$s.", hits[0].person.displayName), personId = hits[0].person.id)
            else -> Reply.Say(t("Нашла %1\$s: %2\$s.", hits.size, hits.take(5).joinToString(", ") { it.person.displayName }))
        }
    }

    private suspend fun message(intent: NoaIntent.Message): Reply = withPerson(intent.personQuery) { pf ->
        val channelName = when (intent.channel) {
            NoaIntent.Channel.WHATSAPP -> "WhatsApp"; NoaIntent.Channel.TELEGRAM -> "Telegram"; NoaIntent.Channel.SMS -> "SMS"
        }
        NoaActions.pendingMessage = NoaActions.Message(pf.person.id, intent.channel, intent.text.orEmpty())
        Reply.Say2Open(t("Открываю %1\$s для %2\$s.", channelName, pf.person.displayName), personId = pf.person.id)
    }

    private suspend fun addNote(intent: NoaIntent.AddNote): Reply {
        if (intent.text.isBlank()) return Reply.Say(t("Что записать в заметку?"))
        return withPerson(intent.personQuery) { pf ->
            Reply.Confirm(t("Записать в хронику %1\$s: «%2\$s»?", pf.person.displayName, intent.text)) {
                repo.addJournal(JournalEntry(personId = pf.person.id, kind = "Заметка", text = intent.text))
                Reply.Say2Open(t("Записала."), personId = pf.person.id)
            }
        }
    }

    private suspend fun createAppointment(intent: NoaIntent.CreateAppointment, now: LocalDateTime): Reply {
        if (intent.personQuery.isBlank()) return Reply.Say(t("Кого записать?"))
        if (intent.dateTime == null) return Reply.Say(t("На какое число и время записать %1\$s?", intent.personQuery))
        return withPerson(intent.personQuery) { pf ->
            val service = intent.serviceQuery?.let { q -> repo.getServices().firstOrNull { it.name.lowercase().contains(q) } }
            val dt = intent.dateTime
            val durationMin = service?.durationMin ?: app.settings.apptDuration.value.value.toIntOrNull() ?: 60
            val whenText = AppointmentLogic.whenText(dt, now, AppointmentLogic.uiLang()) + " " + t("в %1\$s", AppointmentLogic.timeText(dt))
            val serviceLabel = service?.name?.let { " ($it)" }.orEmpty()
            Reply.Confirm(t("Записать %1\$s%2\$s на %3\$s?", pf.person.displayName, serviceLabel, whenText)) {
                val appt = Appointment(
                    personId = pf.person.id, start = AppointmentLogic.millis(dt), durationMin = durationMin,
                    title = service?.name.orEmpty(), place = service?.place.orEmpty(),
                    channel = app.settings.apptChannel.value.value, serviceId = service?.id,
                )
                val clientOffsets = AppointmentLogic.offsetsFromString(service?.clientOffsets?.ifBlank { null } ?: app.settings.apptClientOffsets.value.value)
                val myOffsets = AppointmentLogic.offsetsFromString(service?.myOffsets?.ifBlank { null } ?: app.settings.apptMyOffsets.value.value)
                val (id, reminders) = repo.saveAppointment(appt, clientOffsets, myOffsets)
                com.kartoteka.app.reminders.ReminderScheduler.schedule(app, reminders)
                Reply.Say2Open(t("Готово, записала %1\$s.", pf.person.displayName), appointmentId = id)
            }
        }
    }

    // ---- поиск человека ----

    private suspend fun matches(query: String): List<PersonFull> {
        val q = query.lowercase().split(" ").map { it.trim() }.filter { it.length > 1 }.map(::stem)
        if (q.isEmpty()) return emptyList()
        val all = repo.getAll()
        fun score(p: Person): Int {
            val nameWords = listOf(p.firstName, p.lastName, p.middleName, p.nickname)
                .filter { it.isNotBlank() }.map { stem(it.lowercase()) }
            // Каждое слово запроса должно совпасть с каким-то словом имени (с учётом падежей).
            if (!q.all { qw -> nameWords.any { nw -> stemMatch(qw, nw) } }) return 0
            val first = stem(p.firstName.lowercase()); val nick = stem(p.nickname.lowercase())
            return if (q.any { stemMatch(it, first) || (nick.isNotEmpty() && stemMatch(it, nick)) }) 2 else 1
        }
        return all.map { it to score(it.person) }.filter { it.second > 0 }
            .sortedByDescending { it.second }.map { it.first }
    }

    /** Основа слова: отбрасываем 1–2 конечные гласные и мягкий знак (падежные окончания). */
    private fun stem(w: String): String {
        var s = w.trim()
        var cut = 0
        while (s.length > 2 && cut < 2 && s.last() in "ауюыиеояэёїієь") { s = s.dropLast(1); cut++ }
        return s
    }

    /** Слова совпадают, если их основы равны или одна — начало другой (минимум 2 буквы). */
    private fun stemMatch(a: String, b: String): Boolean {
        if (a.length < 2 || b.length < 2) return a == b
        return a == b || a.startsWith(b) || b.startsWith(a)
    }

    private suspend fun withPerson(query: String, block: suspend (PersonFull) -> Reply): Reply {
        if (query.isBlank()) return Reply.Say(t("Про кого именно?"))
        val hits = matches(query)
        return when {
            hits.isEmpty() -> Reply.Say(t("Не нашла человека по имени «%1\$s».", query))
            hits.size == 1 -> block(hits[0])
            // один явный лидер по совпадению имени
            hits.size > 1 && sameName(hits, query) -> block(hits[0])
            else -> Reply.Choose(t("Кого именно: %1\$s?", hits.take(5).joinToString(", ") { it.person.displayName }), hits.take(5))
        }
    }

    private fun sameName(hits: List<PersonFull>, query: String): Boolean {
        val q = query.lowercase().split(" ").map { stem(it.trim()) }.filter { it.length > 1 }
        // Ровно один человек, чьё имя/прозвище совпадает со словами запроса по основе.
        return hits.count { pf ->
            val names = listOf(pf.person.firstName, pf.person.nickname).filter { it.isNotBlank() }.map { stem(it.lowercase()) }
            q.any { qw -> names.any { stemMatch(qw, it) } }
        } == 1
    }
}

/** Побочные действия, которые выполняет уже UI (нужен Context/запуск интента). */
object NoaActions {
    data class Message(val personId: Long, val channel: NoaIntent.Channel, val text: String)
    var pendingCall: String? = null
    var pendingMessage: Message? = null
}
