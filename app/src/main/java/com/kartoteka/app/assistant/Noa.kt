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
    /** Запись, для которой ещё не хватает человека или времени (ждём ответ на уточняющий вопрос). */
    var pendingBooking: NoaIntent.CreateAppointment? = null

    /**
     * Ответ на «Кого записать?» / «На какое время?»: дополняем ожидающую запись.
     * Если фраза — самостоятельная новая команда, возвращаем null (её выполнят как обычно).
     */
    fun continueBooking(text: String, now: LocalDateTime = LocalDateTime.now()): NoaIntent? {
        val pending = pendingBooking ?: return null
        val own = NoaParser.parse(text, now)
        if (own !is NoaIntent.Unknown && own !is NoaIntent.CreateAppointment) { pendingBooking = null; return null }
        val m = NoaParser.parse("запиши " + text, now) as? NoaIntent.CreateAppointment ?: return null
        return NoaIntent.CreateAppointment(
            personQuery = pending.personQuery.ifBlank { m.personQuery },
            dateTime = pending.dateTime ?: m.dateTime,
            hadTime = pending.hadTime || m.hadTime,
            serviceQuery = pending.serviceQuery ?: m.serviceQuery,
            confirm = false,
        )
    }

    /** Последняя созданная запись — для «отправь ему об этом». */
    var lastAppointmentId: Long? = null
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
            is NoaIntent.Open -> {
                // «Открой Телеграм»: человека с таким именем нет, а приложение есть — запускаем приложение.
                val app = if (!knows(intent.personQuery)) PhoneActions.find(this.app, intent.personQuery) else null
                if (app != null) launchApp(intent.personQuery)
                else withPerson(intent.personQuery) { Reply.Say2Open(t("Открываю %1\$s.", it.person.displayName), personId = it.person.id) }
            }
            is NoaIntent.OpenScreen -> Reply.Navigate(t("Открываю %1\$s.", sectionName(intent.section)), intent.section)
            is NoaIntent.Call -> withPerson(intent.personQuery) {
                val phone = it.phone ?: return@withPerson Reply.Say(t("У %1\$s нет номера телефона.", it.person.displayName))
                Reply.Do(t("Звоню %1\$s.", it.person.displayName)) { ctx -> com.kartoteka.app.messaging.Messaging.dial(ctx, phone) }
            }
            is NoaIntent.Message -> message(intent)
            is NoaIntent.AddNote -> addNote(intent)
            is NoaIntent.CreateAppointment -> createAppointment(intent, now)
            is NoaIntent.Select -> withPerson(intent.personQuery) { Reply.Say("") }
            is NoaIntent.OpenContact -> openContact(intent)
            is NoaIntent.Route -> route(intent)
            is NoaIntent.Agenda -> agenda(intent.date, now.toLocalDate())
            is NoaIntent.PersonInfo -> withPerson(intent.personQuery) { info(it, intent) }
            is NoaIntent.Favorite -> withPerson(intent.personQuery) { pf ->
                repo.setFavorite(pf.person.id, intent.on)
                Reply.Say(if (intent.on) t("Добавила %1\$s в избранное.", pf.person.displayName) else t("Убрала %1\$s из избранного.", pf.person.displayName))
            }
            is NoaIntent.CancelAppointment -> cancelAppointment(intent, now)
            is NoaIntent.ShareData -> shareData(intent)
            is NoaIntent.LaunchApp -> launchApp(intent.name)
            is NoaIntent.WebSearch -> Reply.Do(t("Ищу в Google: %1\$s", intent.query)) { PhoneActions.webSearch(it, intent.query) }
            is NoaIntent.Alarm -> "%02d:%02d".format(intent.hour, intent.minute).let { time ->
                Reply.Do(t("Ставлю будильник на %1\$s.", time)) { PhoneActions.alarm(it, intent.hour, intent.minute, intent.label) }
            }
            is NoaIntent.Timer -> Reply.Do(t("Запускаю таймер: %1\$s.", durationText(intent.seconds))) { PhoneActions.timer(it, intent.seconds) }
            is NoaIntent.Play -> {
                val a = intent.app?.let { name -> PhoneActions.find(app, name) }
                val what = intent.query.ifBlank { if (intent.playlist) t("плейлист") else t("музыку") }
                Reply.Do(a?.let { t("Включаю %1\$s в %2\$s.", what, it.label) } ?: t("Включаю %1\$s.", what)) {
                    PhoneActions.play(it, intent.query, a, intent.playlist)
                }
            }
            is NoaIntent.Flashlight -> Reply.Do(if (intent.on) t("Включаю фонарик.") else t("Выключаю фонарик.")) { PhoneActions.flashlight(it, intent.on) }
            is NoaIntent.PhoneSettings -> Reply.Do(t("Открываю настройки телефона.")) { PhoneActions.settings(it, intent.what) }
            is NoaIntent.MoveAppointment -> moveAppointment(intent, now)
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
            com.kartoteka.app.messaging.Messaging.navigate(ctx, place, intent.app)
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
        val text = if (intent.aboutAppointment) confirmationText(pf) ?: intent.text.orEmpty() else intent.text.orEmpty()
        val m = NoaActions.Message(pf.person.id, intent.channel, text)
        NoaActions.lastMessage = m
        Reply.Do(t("Открываю %1\$s для %2\$s.", channelName, pf.person.displayName)) { ctx -> sendMessage(ctx, pf, m) }
    }

    private suspend fun addNote(intent: NoaIntent.AddNote): Reply {
        if (intent.text.isBlank()) return Reply.Say(t("Что записать в заметку?"))
        return withPerson(intent.personQuery) { pf ->
            repo.addJournal(JournalEntry(personId = pf.person.id, kind = "Заметка", text = intent.text))
            Reply.Say(t("Добавила в хронику %1\$s: «%2\$s».", pf.person.displayName, intent.text))
        }
    }

    private suspend fun createAppointment(intent: NoaIntent.CreateAppointment, now: LocalDateTime): Reply {
        // Не хватает человека или времени — спрашиваем и ждём ответ; следующая фраза дополнит эту же запись.
        if (intent.personQuery.isBlank()) { pendingBooking = intent; return Reply.Choose(t("Кого записать?"), emptyList()) }
        if (intent.dateTime == null) { pendingBooking = intent; return Reply.Choose(t("На какое число и время записать %1\$s?", intent.personQuery), emptyList()) }
        pendingBooking = null
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
                lastAppointmentId = id
                com.kartoteka.app.reminders.ReminderScheduler.schedule(app, reminders)
                Reply.Say(t("Готово, записала %1\$s.", pf.person.displayName))
            }
        }
    }

    /** Запись человека: на названный день или ближайшая предстоящая (сегодняшние — тоже). */
    private suspend fun findAppointment(pf: PersonFull, date: java.time.LocalDate?, now: LocalDateTime): com.kartoteka.app.data.AppointmentFull? {
        val from = AppointmentLogic.millis(date?.atStartOfDay() ?: now.toLocalDate().atStartOfDay())
        val to = AppointmentLogic.millis(date?.plusDays(1)?.atStartOfDay() ?: now.plusYears(1))
        val list = repo.appointmentsBetween(from, to)
            .filter { it.appointment.personId == pf.person.id && it.appointment.appointmentStatus == com.kartoteka.app.data.AppointmentStatus.PLANNED }
            .sortedBy { it.appointment.start }
        return list.firstOrNull { it.appointment.start >= AppointmentLogic.millis(now) } ?: list.firstOrNull()
    }

    private fun apptWhen(ms: Long, now: LocalDateTime): String {
        val dt = AppointmentLogic.zoned(ms)
        return AppointmentLogic.whenText(dt, now, AppointmentLogic.uiLang()) + " " + t("в %1\$s", AppointmentLogic.timeText(dt))
    }

    private suspend fun cancelAppointment(intent: NoaIntent.CancelAppointment, now: LocalDateTime): Reply = withPerson(intent.personQuery) { pf ->
        val af = findAppointment(pf, intent.date, now)
            ?: return@withPerson Reply.Say(
                if (intent.date != null) t("У %1\$s нет записи на этот день.", pf.person.displayName)
                else t("У %1\$s нет предстоящих записей.", pf.person.displayName)
            )
        val id = af.appointment.id
        val whenText = apptWhen(af.appointment.start, now)
        val question = if (intent.delete) t("Удалить запись %1\$s на %2\$s?", pf.person.displayName, whenText)
            else t("Отменить запись %1\$s на %2\$s?", pf.person.displayName, whenText)
        Reply.Confirm(question) {
            com.kartoteka.app.reminders.ReminderScheduler.cancel(app, repo.pendingRemindersFor(id).map { it.id })
            if (intent.delete) repo.deleteAppointment(id) else repo.setAppointmentStatus(id, com.kartoteka.app.data.AppointmentStatus.CANCELLED)
            if (lastAppointmentId == id) lastAppointmentId = null
            Reply.Say(if (intent.delete) t("Удалила запись %1\$s.", pf.person.displayName) else t("Отменила запись %1\$s.", pf.person.displayName))
        }
    }

    private suspend fun moveAppointment(intent: NoaIntent.MoveAppointment, now: LocalDateTime): Reply = withPerson(intent.personQuery) { pf ->
        val af = findAppointment(pf, null, now)
            ?: return@withPerson Reply.Say(t("У %1\$s нет предстоящих записей.", pf.person.displayName))
        val target = intent.dateTime
        if (target == null || (!intent.hadDate && !intent.hadTime))
            return@withPerson Reply.Say(t("На когда перенести? Скажите, например: «перенеси %1\$s на пятницу в 15:00».", pf.person.displayName))
        val old = AppointmentLogic.zoned(af.appointment.start)
        // Назвали только день — время остаётся прежним; только время — день прежний.
        val dt = LocalDateTime.of(if (intent.hadDate) target.toLocalDate() else old.toLocalDate(), if (intent.hadTime) target.toLocalTime() else old.toLocalTime())
        val oldText = apptWhen(af.appointment.start, now)
        val newText = AppointmentLogic.whenText(dt, now, AppointmentLogic.uiLang()) + " " + t("в %1\$s", AppointmentLogic.timeText(dt))
        Reply.Confirm(t("Перенести запись %1\$s с %2\$s на %3\$s?", pf.person.displayName, oldText, newText)) {
            val a = af.appointment
            com.kartoteka.app.reminders.ReminderScheduler.cancel(app, repo.pendingRemindersFor(a.id).map { it.id })
            val service = repo.getService(a.serviceId)
            val clientOffsets = AppointmentLogic.offsetsFromString(service?.clientOffsets?.ifBlank { null } ?: app.settings.apptClientOffsets.value.value)
            val myOffsets = AppointmentLogic.offsetsFromString(service?.myOffsets?.ifBlank { null } ?: app.settings.apptMyOffsets.value.value)
            val (id, reminders) = repo.saveAppointment(a.copy(start = AppointmentLogic.millis(dt)), clientOffsets, myOffsets)
            lastAppointmentId = id
            com.kartoteka.app.reminders.ReminderScheduler.schedule(app, reminders)
            Reply.Say(t("Перенесла запись %1\$s на %2\$s.", pf.person.displayName, newText))
        }
    }

    private fun durationText(sec: Int): String = when {
        sec % 3600 == 0 -> t("%1\$s ч", sec / 3600)
        sec % 60 == 0 -> t("%1\$s мин", sec / 60)
        else -> t("%1\$s сек", sec)
    }

    private fun launchApp(name: String): Reply {
        val a = PhoneActions.find(app, name) ?: return Reply.Say(t("Не нашла на телефоне приложение «%1\$s».", name))
        return Reply.Do(t("Открываю %1\$s.", a.label)) { PhoneActions.launch(it, a) }
    }

    /** Текст данных человека для передачи в другое приложение. */
    private suspend fun dataText(pf: PersonFull, data: NoaIntent.Data): String? {
        val p = pf.person
        val df = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")
        return when (data) {
            NoaIntent.Data.NOTES -> {
                val lines = pf.journal.sortedByDescending { it.date }.map { AppointmentLogic.zoned(it.date).format(df) + " — " + it.text } +
                    listOfNotNull(p.notes.takeIf { it.isNotBlank() })
                if (lines.isEmpty()) null else (listOf(t("Заметки: %1\$s", p.displayName), "") + lines).joinToString("\n")
            }
            NoaIntent.Data.PHONE -> pf.phone?.let { com.kartoteka.app.data.PhoneFormat.pretty(it) }
            NoaIntent.Data.ADDRESS -> pf.places.filter { it.address.isNotBlank() }.joinToString("\n") { it.address }.ifBlank { null }
            NoaIntent.Data.EMAIL -> pf.firstOf(com.kartoteka.app.data.ContactType.EMAIL)
            NoaIntent.Data.BIRTHDAY -> com.kartoteka.app.data.ArchiveLogic.formatBirthday(p)
            NoaIntent.Data.CARD -> (listOf(cardText(pf)) +
                pf.contacts.filter { it.value.isNotBlank() && it.contactType != com.kartoteka.app.data.ContactType.PHONE }
                    .map { it.contactType.title + ": " + it.value }).joinToString("\n")
        }
    }

    private fun dataName(d: NoaIntent.Data): String = when (d) {
        NoaIntent.Data.NOTES -> t("заметки"); NoaIntent.Data.PHONE -> t("номер"); NoaIntent.Data.ADDRESS -> t("адрес")
        NoaIntent.Data.EMAIL -> t("почту"); NoaIntent.Data.BIRTHDAY -> t("день рождения"); NoaIntent.Data.CARD -> t("данные")
    }

    private suspend fun shareData(intent: NoaIntent.ShareData): Reply = withPerson(intent.personQuery) { pf ->
        val name = pf.person.displayName
        val what = dataName(intent.data)
        val text = dataText(pf, intent.data)
            ?: return@withPerson Reply.Say(t("У %1\$s нет данных: %2\$s.", name, what))
        // Для поиска — само значение (номер без пробелов), а не карточка целиком.
        val plain = if (intent.data == NoaIntent.Data.PHONE) pf.phone.orEmpty() else text
        when {
            intent.target == "clipboard" -> Reply.Do(t("Скопировала %1\$s (%2\$s). Буфер очистится через 30 секунд.", what, name)) {
                com.kartoteka.app.security.SecureClipboard.copy(it, plain)
            }
            intent.target == "google" -> Reply.Do(t("Ищу в Google %1\$s (%2\$s).", what, name)) { PhoneActions.webSearch(it, plain) }
            intent.target == "notes" -> {
                val notes = PhoneActions.notesApp(app)
                Reply.Do(notes?.let { t("Переношу %1\$s (%2\$s) в %3\$s.", what, name, it.label) } ?: t("Выберите блокнот, куда сохранить %1\$s (%2\$s).", what, name)) {
                    PhoneActions.shareTo(it, text, notes, subject = name)
                }
            }
            intent.target.startsWith("app:") -> {
                val target = PhoneActions.find(app, intent.target.removePrefix("app:"))
                Reply.Do(target?.let { t("Передаю %1\$s (%2\$s) в %3\$s.", what, name, it.label) } ?: t("Выберите, куда отправить %1\$s (%2\$s).", what, name)) {
                    PhoneActions.shareTo(it, text, target, subject = name)
                }
            }
            else -> Reply.Do(t("Выберите, куда отправить %1\$s (%2\$s).", what, name)) { PhoneActions.shareTo(it, text, null, subject = name) }
        }
    }

    /** Есть ли в книжке человек по этому запросу (пусто/местоимение — «тот же человек», считается найденным). */
    suspend fun knows(query: String): Boolean {
        val q = query.split(" ").filter { it.isNotBlank() && it.lowercase() !in NoaParser.PRONOUNS }.joinToString(" ")
        return q.isBlank() || matches(q).isNotEmpty()
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
        val exact = all.map { it to score(it.person) }.filter { it.second > 0 }
            .sortedByDescending { it.second }.map { it.first }
        return exact.ifEmpty { fuzzy(query, all) }
    }

    /**
     * Нечёткий поиск — когда распознавание речи исказило имя: «ильерикову» (имя и фамилия слитно),
     * «Рыкову/Рикову», пропущенная буква. Сравниваем склеенные основы слов с вариантами имени по расстоянию правки.
     */
    private fun fuzzy(query: String, all: List<PersonFull>): List<PersonFull> {
        val qj = query.lowercase().split(" ").filter { it.length > 1 }.joinToString("") { squash(stem(it)) }
        if (qj.length < 3) return emptyList()
        fun variants(p: Person): List<String> {
            val f = squash(stem(p.firstName.lowercase())); val l = squash(stem(p.lastName.lowercase()))
            val m = squash(stem(p.middleName.lowercase())); val n = squash(stem(p.nickname.lowercase()))
            return listOf(f + l, l + f, f, l, n, n + l, f + m).filter { it.length >= 2 }.distinct()
        }
        return all.mapNotNull { pf ->
            val best = variants(pf.person).minOfOrNull { v ->
                val d = lev(qj, v)
                if (d <= maxOf(1, (v.length * 0.3f).toInt())) d else Int.MAX_VALUE
            } ?: Int.MAX_VALUE
            if (best == Int.MAX_VALUE) null else pf to best
        }.sortedBy { it.second }.let { list ->
            // Один явный лучший — только он; иначе — несколько кандидатов на выбор.
            val top = list.firstOrNull()?.second ?: return emptyList()
            list.filter { it.second == top }.map { it.first }
        }
    }

    /** Схлопываем двойные буквы («Ілля» → «иля»). */
    private fun squash(w: String) = w.replace(Regex("(.)\\1+"), "$1")

    private fun lev(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]; dp[0] = i
            for (j in 1..b.length) {
                val tmp = dp[j]
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return dp[b.length]
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

    /** Текст подтверждения записи по шаблону приложения (своему для услуги или общему) — на языке человека. */
    private suspend fun confirmationText(pf: PersonFull): String? {
        val now = System.currentTimeMillis()
        val af = lastAppointmentId?.let { repo.getAppointment(it) }?.takeIf { it.appointment.personId == pf.person.id }
            ?: repo.appointmentsBetween(now, now + 365L * 86_400_000).filter { it.appointment.personId == pf.person.id }.minByOrNull { it.appointment.start }
            ?: return null
        val lang = app.settings.langFor(pf.person)
        val service = repo.getService(af.appointment.serviceId)
        val template = AppointmentLogic.messageTemplate(
            service, com.kartoteka.app.data.TemplateKind.CONFIRM,
            app.settings.template(com.kartoteka.app.data.TemplateKind.CONFIRM, lang).value.value,
        )
        return AppointmentLogic.fill(template, af.appointment, pf.person, lang)
    }

    /**
     * Данные для модели по запросу: карточки упомянутых людей (или того, о ком говорили), план на сегодня и завтра.
     * Так модель отвечает по вашей картотеке, а не выдумывает.
     */
    suspend fun contextFor(text: String): String {
        val words = text.lowercase().split(Regex("[^\\p{L}]+")).filter { it.length > 2 }.map(::stem)
        val all = repo.getAll()
        val named = all.filter { pf ->
            val p = pf.person
            listOf(p.firstName, p.lastName, p.nickname).filter { it.length > 1 }.map { stem(it.lowercase()) }
                .any { nw -> words.any { stemMatch(it, nw) && it.length >= 3 } }
        }.take(2)
        val people = named.ifEmpty { if (words.any { it in NoaParser.PRONOUNS }) listOfNotNull(lastPerson) else emptyList() }
        val today = java.time.LocalDate.now()
        suspend fun dayLine(d: java.time.LocalDate, label: String): String? {
            val from = AppointmentLogic.millis(d.atStartOfDay()); val to = AppointmentLogic.millis(d.plusDays(1).atStartOfDay())
            val list = repo.appointmentsBetween(from, to)
                .filter { it.appointment.appointmentStatus != com.kartoteka.app.data.AppointmentStatus.CANCELLED }
                .sortedBy { it.appointment.start }
            if (list.isEmpty()) return null
            return label + ": " + list.joinToString("; ") {
                AppointmentLogic.timeText(AppointmentLogic.zoned(it.appointment.start)) + " " + it.person?.displayName.orEmpty() +
                    it.appointment.title.takeIf { t -> t.isNotBlank() }?.let { t -> " ($t)" }.orEmpty()
            }
        }
        return buildList {
            people.forEach { add(cardText(it).take(500)) }
            dayLine(today, t("Сегодня"))?.let(::add)
            dayLine(today.plusDays(1), t("Завтра"))?.let(::add)
            add(t("Людей в книжке: %1\$s", all.size))
        }.joinToString("\n---\n")
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
    /** Последнее подготовленное сообщение (для проверки и повтора). */
    var lastMessage: Message? = null
}
