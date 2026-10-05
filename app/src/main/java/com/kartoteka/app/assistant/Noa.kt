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
        /** Действие в другом приложении (маршрут, Instagram, почта…), выполняет экран — нужен Context. */
        data class Do(val text: String, val effect: (android.content.Context) -> Unit) : Reply
    }

    /** Последний человек, о котором шла речь: «…и добавь ей заметку», «а где она живёт?». */
    var lastPerson: PersonFull? = null
        private set

    suspend fun handle(text: String, now: LocalDateTime = LocalDateTime.now()): Reply =
        handleIntent(NoaParser.parse(text, now), now)

    suspend fun handleIntent(intent: NoaIntent, now: LocalDateTime = LocalDateTime.now()): Reply =
        when (intent) {
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
            is NoaIntent.OpenContact -> openContact(intent)
            is NoaIntent.Route -> route(intent)
            is NoaIntent.Agenda -> agenda(intent.date, now.toLocalDate())
            is NoaIntent.PersonInfo -> withPerson(intent.personQuery) { info(it, intent) }
            is NoaIntent.Favorite -> withPerson(intent.personQuery) { pf ->
                repo.setFavorite(pf.person.id, intent.on)
                Reply.Say(if (intent.on) t("Добавила %1\$s в избранное.", pf.person.displayName) else t("Убрала %1\$s из избранного.", pf.person.displayName))
            }
            // Цепочку разворачивает экран (шаг за шагом, с подтверждениями); здесь — на всякий случай первый шаг.
            is NoaIntent.Sequence -> intent.steps.firstOrNull()?.let { handleIntent(it, now) } ?: Reply.Say(t("Не поняла команду."))
            is NoaIntent.Unknown -> Reply.Say(t("Не поняла команду. Скажите, например: «запиши Анну на завтра в 12:00» или «позвони маме»."))
        }

    private suspend fun openContact(intent: NoaIntent.OpenContact): Reply = withPerson(intent.personQuery) { pf ->
        val item = pf.contacts.firstOrNull { it.contactType == intent.type && it.value.isNotBlank() }
            ?: return@withPerson Reply.Say(t("У %1\$s не указан %2\$s.", pf.person.displayName, intent.type.title))
        Reply.Do(t("Открываю %1\$s %2\$s.", intent.type.title, pf.person.displayName)) { ctx ->
            com.kartoteka.app.messaging.Messaging.openLink(ctx, item)
        }
    }

    private suspend fun route(intent: NoaIntent.Route): Reply = withPerson(intent.personQuery) { pf ->
        val places = pf.places.filter { it.address.isNotBlank() || it.hasCoords }
        val place = places.firstOrNull { intent.kind == null || it.placeKind == intent.kind }
            ?: places.firstOrNull()
            ?: return@withPerson Reply.Say(t("У %1\$s нет адреса. Добавьте его в карточке — и я проложу маршрут.", pf.person.displayName))
        val where = place.label.ifBlank { place.placeKind.title.lowercase() }
        Reply.Do(t("Прокладываю маршрут: %1\$s, %2\$s.", pf.person.displayName, where)) { ctx ->
            com.kartoteka.app.messaging.Messaging.navigate(ctx, place)
        }
    }

    /** Записи и дни рождения на день — коротко, голосом. */
    private suspend fun agenda(date: java.time.LocalDate, today: java.time.LocalDate): Reply {
        val from = AppointmentLogic.millis(date.atStartOfDay())
        val to = AppointmentLogic.millis(date.plusDays(1).atStartOfDay())
        val appts = repo.appointmentsBetween(from, to)
            .filter { it.appointment.appointmentStatus != com.kartoteka.app.data.AppointmentStatus.CANCELLED }
            .sortedBy { it.appointment.start }
        val bds = repo.getAll().filter { it.person.birthDay == date.dayOfMonth && it.person.birthMonth == date.monthValue }
        val day = when (date) {
            today -> t("Сегодня"); today.plusDays(1) -> t("Завтра")
            else -> AppointmentLogic.dateText(date.atStartOfDay()) + ", " + AppointmentLogic.weekday(date)
        }
        if (appts.isEmpty() && bds.isEmpty()) return Reply.Say(t("%1\$s ничего не запланировано.", day))
        val lines = buildList {
            appts.forEach { af ->
                val title = af.appointment.title.takeIf { it.isNotBlank() }?.let { " — $it" }.orEmpty()
                add("${AppointmentLogic.timeText(AppointmentLogic.zoned(af.appointment.start))} ${af.person?.displayName.orEmpty()}$title")
            }
            bds.forEach { add("🎂 " + t("день рождения: %1\$s", it.person.displayName)) }
        }
        val head = if (appts.isNotEmpty()) t("%1\$s записей: %2\$s.", day, appts.size) else "$day:"
        return Reply.Say(head + "\n" + lines.joinToString("\n"))
    }

    /** Ответ о человеке из его карточки; на свободный вопрос — через модель, если она готова. */
    private suspend fun info(pf: PersonFull, intent: NoaIntent.PersonInfo): Reply {
        val p = pf.person
        return when (intent.topic) {
            NoaIntent.Topic.BIRTHDAY -> {
                val bd = com.kartoteka.app.data.ArchiveLogic.formatBirthday(p)
                    ?: return Reply.Say(t("День рождения %1\$s не указан.", p.displayName))
                val days = com.kartoteka.app.data.ArchiveLogic.daysUntilBirthday(p)
                val turning = com.kartoteka.app.data.ArchiveLogic.turningAge(p)
                Reply.Say2Open(listOfNotNull(
                    t("День рождения %1\$s — %2\$s.", p.displayName, bd),
                    days?.let { com.kartoteka.app.data.ArchiveLogic.daysString(it).replaceFirstChar { c -> c.uppercase() } + "." },
                    turning?.let { t("Исполнится %1\$s.", com.kartoteka.app.data.ArchiveLogic.ageString(it)) },
                ).joinToString(" "))
            }
            NoaIntent.Topic.PHONE -> {
                val phone = pf.phone ?: return Reply.Say(t("У %1\$s нет номера телефона.", p.displayName))
                Reply.Say(t("Номер %1\$s: %2\$s.", p.displayName, com.kartoteka.app.data.PhoneFormat.pretty(phone)))
            }
            NoaIntent.Topic.ADDRESS -> {
                val places = pf.places.filter { it.address.isNotBlank() }
                if (places.isEmpty()) Reply.Say(t("У %1\$s нет адреса.", p.displayName))
                else Reply.Say(places.joinToString("\n") { "${it.label.ifBlank { it.placeKind.title }}: ${it.address}" })
            }
            NoaIntent.Topic.SUMMARY -> {
                val card = cardText(pf)
                val smart = if (app.brain.isReady) app.brain.ask(
                    t("Карточка человека из моей записной книжки:\n%1\$s\n\nВопрос: %2\$s\nОтветь коротко (2–4 предложения), только по карточке, на языке вопроса.", card, intent.question)
                )?.trim()?.takeIf { it.isNotBlank() } else null
                Reply.Say2Open(smart ?: card, personId = p.id)
            }
        }
    }

    /** Карточка человека текстом: кто, где, контакты, последние события, заметки. */
    private suspend fun cardText(pf: PersonFull): String {
        val p = pf.person
        val now = System.currentTimeMillis()
        val appts = repo.appointmentsBetween(now - 365L * 86_400_000, now + 365L * 86_400_000).filter { it.appointment.personId == p.id }
        val past = appts.filter { it.appointment.start < now }.maxByOrNull { it.appointment.start }
        val next = appts.filter { it.appointment.start >= now }.minByOrNull { it.appointment.start }
        fun whenOf(ms: Long) = AppointmentLogic.zoned(ms).let { AppointmentLogic.dateText(it) + " " + AppointmentLogic.timeText(it) }
        return listOfNotNull(
            p.fullName,
            listOf(t(p.relation), p.position, p.company, p.city).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { null },
            com.kartoteka.app.data.ArchiveLogic.formatBirthday(p)?.let { t("День рождения: %1\$s", it) },
            pf.phone?.let { t("Телефон: %1\$s", com.kartoteka.app.data.PhoneFormat.pretty(it)) },
            pf.details.filter { it.value.isNotBlank() }.take(6).joinToString("; ") { "${it.name}: ${it.value}" }.ifBlank { null },
            next?.let { t("Следующая запись: %1\$s", whenOf(it.appointment.start) + it.appointment.title.takeIf { s -> s.isNotBlank() }?.let { s -> " — $s" }.orEmpty()) },
            past?.let { t("Последняя встреча: %1\$s", whenOf(it.appointment.start)) },
            pf.journal.sortedByDescending { it.date }.take(3).joinToString("\n") { "• " + it.text }.ifBlank { null },
            p.notes.takeIf { it.isNotBlank() }?.take(300),
        ).joinToString("\n")
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
            repo.addJournal(JournalEntry(personId = pf.person.id, kind = "Заметка", text = intent.text))
            Reply.Say2Open(t("Добавила в хронику %1\$s: «%2\$s».", pf.person.displayName, intent.text), personId = pf.person.id)
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

    /**
     * Сводим похожие буквы русского и украинского алфавитов к одной форме, чтобы
     * «Илья Рыков» и «Ілля Риков» совпадали: и/і/ї/ы/й→и, е/є/ё/э→е, г/ґ→г, апостроф/ь убираем.
     */
    private fun fold(w: String): String = buildString {
        for (c in w.lowercase()) when (c) {
            'і', 'ї', 'ы', 'й', 'и' -> append('и')
            'е', 'є', 'ё', 'э' -> append('е')
            'ґ' -> append('г')
            'ь', '\'', '’', '`', 'ʼ' -> {}
            else -> append(c)
        }
    }

    /** Основа слова: сводим алфавиты и отбрасываем 1–2 конечные гласные (падежные окончания). */
    private fun stem(w: String): String {
        var s = fold(w.trim())
        var cut = 0
        while (s.length > 2 && cut < 2 && s.last() in "ауюиеоя") { s = s.dropLast(1); cut++ }
        return s
    }

    /** Слова совпадают, если их основы равны или одна — начало другой (минимум 2 буквы). */
    private fun stemMatch(a: String, b: String): Boolean {
        if (a.length < 2 || b.length < 2) return a == b
        return a == b || a.startsWith(b) || b.startsWith(a)
    }

    private suspend fun withPerson(query: String, block: suspend (PersonFull) -> Reply): Reply {
        val q = query.split(" ").filter { it.isNotBlank() && it.lowercase() !in NoaParser.PRONOUNS }.joinToString(" ")
        if (q.isBlank()) {
            val last = lastPerson?.let { repo.getPerson(it.person.id) } ?: return Reply.Say(t("Про кого именно?"))
            return block(last)
        }
        val hits = matches(q)
        suspend fun pick(pf: PersonFull): Reply { lastPerson = pf; return block(pf) }
        return when {
            hits.isEmpty() -> Reply.Say(t("Не нашла человека по имени «%1\$s».", q))
            hits.size == 1 -> pick(hits[0])
            // один явный лидер по совпадению имени
            hits.size > 1 && sameName(hits, q) -> pick(hits[0])
            else -> Reply.Choose(t("Кого именно: %1\$s?", hits.take(5).joinToString(", ") { it.person.displayName }), hits.take(5))
        }
    }

    /** Имена из картотеки — подсказка модели, чтобы она называла людей так, как они записаны. */
    suspend fun knownNames(limit: Int = 60): List<String> =
        repo.getAll().sortedByDescending { it.person.lastContactAt ?: 0L }.take(limit).map { it.person.displayName }

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
