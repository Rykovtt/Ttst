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
        data class Do(val text: String, val quiet: Boolean = false, val effect: (android.content.Context) -> Unit) : Reply
    }

    /** Навигатор, названный последним: «перестрой маршрут на …» откроет тот же. */
    private var lastNavApp: String? = null
    /** Когда открыли чат с готовым текстом — «закрой приложение» сразу после этого не выбросит неотправленное. */
    private var chatOpenedAt = 0L

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
        val confirmOpen = awaitingConfirm != null && System.currentTimeMillis() - awaitingAt < 120_000
        val pending = pendingBooking ?: awaitingConfirm?.takeIf { confirmOpen } ?: return null
        val question = pendingBooking != null
        val own = NoaParser.parse(text, now)
        val clean = NoaParser.normalize(NoaParser.stripAddress(text)).lowercase().replace('ё', 'е')
        // «Отмена», «забудь», «стоп» — бросаем вопрос; на вопрос «кого записать?» ещё и «нет» значит отмену.
        val stop = clean in setOf("стоп", "стой", "хватит", "досить", "stop", "отмени", "отменить", "отмените", "скасуй", "скасувати", "відміни", "cancel") ||
            (question && clean in setOf("нет", "ні", "no", "не надо", "не нужно", "не треба", "неа", "нет не надо"))
        if (own is NoaIntent.Dismiss || stop) { pendingBooking = null; awaitingConfirm = null; return NoaIntent.Dismiss }
        // «Повтори» не сбрасывает ожидание: повторится вопрос.
        if (own is NoaIntent.Repeat) return own
        if (own !is NoaIntent.Unknown && own !is NoaIntent.CreateAppointment) { pendingBooking = null; awaitingConfirm = null; return null }
        val yn = NoaParser.yesNo(text)
        // «нет, на пятницу», «не в три, а в четыре», «лучше в четыре» — поправка к тому, что поняла
        val lead = Regex("^(?:лучше|краще|давай|давайте)\\s+").find(clean)
        val negated = yn == false
        if (!negated && lead == null && !question) return null              // «да» / не по делу — подтверждение обработает экран
        var body = clean.replace(Regex("^(?:(?:нет|не|ні|no|nope|неа|нее)(?:-нет|-ні)?(?:\\s+|$))+"), "")
        body = body.replace(Regex("^(?:лучше|краще|давай|давайте|а|ну)\\s+"), "")
        // «в пятницу а в субботу» → берём то, что после «а»
        val after = Regex("(?:^|\\s)(?:а|но|але)\\s+(?:лучше\\s+|краще\\s+)?(.+)$").find(body)?.groupValues?.get(1)
        val corr = (after ?: body).trim()
        if (corr.isBlank()) return null
        val parsed = NoaDateTime.parse(corr, now, workHours = true)
        val m = NoaParser.parse("запиши $corr", now) as? NoaIntent.CreateAppointment
        val newPerson = m?.personQuery.orEmpty()
        if (!question && parsed == null && newPerson.isBlank()) return null
        // Поправка заменяет названное; на вопрос «кого/когда?» — дополняет то, чего не хватало.
        val replace = negated || !question
        val dt: LocalDateTime? = when {
            parsed == null -> pending.dateTime
            pending.dateTime == null -> parsed.dateTime
            else -> {
                val old = pending.dateTime!!
                if (!replace) old
                else {
                    var time = if (parsed.hadTime) parsed.dateTime.toLocalTime() else old.toLocalTime()
                    // «не в три, а в четыре» к записи на 15:00 — это 16:00, а не 4 утра
                    if (parsed.hadTime && old.hour >= 12 && time.hour in 1..7 && !Regex("утр|ранк|ранок|\\bam\\b").containsMatchIn(corr)) time = time.plusHours(12)
                    LocalDateTime.of(if (parsed.hadDate) parsed.dateTime.toLocalDate() else old.toLocalDate(), time)
                }
            }
        }
        return NoaIntent.CreateAppointment(
            personQuery = if (replace && newPerson.isNotBlank()) newPerson else pending.personQuery.ifBlank { newPerson },
            dateTime = dt,
            hadTime = pending.hadTime || parsed?.hadTime == true,
            serviceQuery = pending.serviceQuery ?: m?.serviceQuery,
            confirm = false,
        )
    }

    /** Запись, по которой задан вопрос «Записать…?» и ответа ещё нет: «нет, на пятницу» её поправит. */
    private var awaitingConfirm: NoaIntent.CreateAppointment? = null
    private var awaitingAt = 0L

    /** Последний ответ: «повтори» говорит его снова (действие — выполняет снова). */
    var lastReply: Reply? = null
        private set

    /** Последняя созданная запись — для «отправь ему об этом». */
    var lastAppointmentId: Long? = null
        private set

    suspend fun handle(text: String, now: LocalDateTime = LocalDateTime.now()): Reply =
        handleIntent(NoaParser.parse(text, now), now)

    suspend fun handleIntent(intent: NoaIntent, now: LocalDateTime = LocalDateTime.now()): Reply {
        // «Повтори» — последний ответ (действие выполнится снова); сам он в память не попадает.
        if (intent is NoaIntent.Repeat) return lastReply ?: Reply.Say(t("Мне пока нечего повторять."))
        awaitingConfirm = null
        val r = execute(intent, now)
        if (intent !is NoaIntent.Select && intent !is NoaIntent.Dismiss && !(r is Reply.Say && r.text.isEmpty())) lastReply = r
        return r
    }

    private suspend fun execute(intent: NoaIntent, now: LocalDateTime): Reply =
        when (intent) {
            is NoaIntent.Remind -> remind(intent, now)
            is NoaIntent.Tool -> tool(intent, now)
            is NoaIntent.Calc -> calc(intent)
            is NoaIntent.Convert -> convert(intent)
            is NoaIntent.Crm -> crm(intent, now)
            is NoaIntent.Dismiss -> { pendingBooking = null; awaitingConfirm = null; Reply.Say(t("Хорошо, отменила.")) }
            is NoaIntent.Repeat -> lastReply ?: Reply.Say(t("Мне пока нечего повторять."))
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
                // Разрешены звонки — звоним сразу; нет — звонилка с номером и подсказка, где включить.
                val direct = com.kartoteka.app.messaging.Messaging.canCallDirect(app)
                val text = if (direct) t("Звоню %1\$s.", it.person.displayName)
                    else t("Набрала номер %1\$s — нажмите «Вызов». Чтобы я звонила сразу: Настройки → Авто-действия → «Звонить сразу».", it.person.displayName)
                Reply.Do(text) { ctx -> com.kartoteka.app.messaging.Messaging.call(ctx, phone) }
            }
            is NoaIntent.Message -> message(intent)
            is NoaIntent.AddNote -> addNote(intent)
            is NoaIntent.CreateAppointment -> createAppointment(intent, now)
            is NoaIntent.Select -> withPerson(intent.personQuery) { Reply.Say("") }
            is NoaIntent.OpenContact -> openContact(intent)
            is NoaIntent.Route -> route(intent)
            is NoaIntent.Agenda -> if (intent.days > 1) agendaRange(intent.date, intent.days, now.toLocalDate()) else agenda(intent.date, now.toLocalDate())
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
            is NoaIntent.Play -> play(intent)
            is NoaIntent.Media -> media(intent.control)
            is NoaIntent.Volume -> volume(intent)
            is NoaIntent.VoiceNote -> voiceNote(intent)
            is NoaIntent.Reply -> replyTo(intent)
            is NoaIntent.ReadMessages -> readMessages(intent)
            is NoaIntent.CloseApp -> {
                val a = PhoneActions.find(app, intent.name)
                if (a == null) Reply.Say(t("Не нашла на телефоне приложение «%1\$s».", intent.name))
                else Reply.Do(t("Закрываю %1\$s.", a.label)) { ctx -> PhoneActions.close(ctx, a) }
            }
            is NoaIntent.Wrong -> Reply.Say(t("Поняла, что ошиблась. Скажите, что нужно было сделать, — я запишу."))
            is NoaIntent.GoHome -> {
                if (System.currentTimeMillis() - chatOpenedAt < 15_000) Reply.Say(t("Чат открыт — нажмите «Отправить», потом закрою по команде."))
                else Reply.Do(t("Готово.")) { ctx ->
                    runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
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

    private suspend fun route(intent: NoaIntent.Route): Reply {
        intent.app?.let { lastNavApp = it }
        val navApp = intent.app ?: lastNavApp
        // «Поехали домой / на работу» без человека — моё место из сохранённых в навигаторе («Дом», «Работа»).
        val homeWords = setOf("домой", "додому", "дом", "дому", "home", "работу", "роботу", "work", "офис", "офіс")
        if (intent.personQuery.isBlank() && intent.kind != null && (intent.place.isBlank() || intent.place.split(" ").all { it in homeWords })) {
            val home = intent.kind == com.kartoteka.app.data.PlaceKind.HOME
            return Reply.Do((if (home) t("Едем домой.") else t("Едем на работу.")) + navName(navApp)) { ctx ->
                com.kartoteka.app.messaging.Messaging.navigateSaved(ctx, home, navApp)
            }
        }
        // Человек из книжки — к нему; иначе — любое место по словам («до Киевской 5», «ближайшая заправка»).
        val person = intent.personQuery.isNotBlank() && knows(intent.personQuery)
        if (!person && intent.place.isNotBlank() && !(intent.personQuery.isBlank() && intent.place.split(" ").all { it in NoaParser.PRONOUNS })) {
            val place = intent.place
            return Reply.Do(t("Прокладываю маршрут: %1\$s.", place) + navName(navApp)) { ctx ->
                com.kartoteka.app.messaging.Messaging.navigateTo(ctx, place, navApp)
            }
        }
        return personRoute(intent.copy(app = navApp))
    }

    private fun navName(app: String?) = when (app) {
        "waze" -> " (Waze)"; "google" -> " (Google Maps)"; "yandex" -> " (Яндекс)"; "organic" -> " (Organic Maps)"; else -> ""
    }

    private suspend fun personRoute(intent: NoaIntent.Route): Reply = withPerson(intent.personQuery) { pf ->
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
    /** План на несколько дней («на эту неделю»): по дням, только непустые. */
    private suspend fun agendaRange(from: java.time.LocalDate, days: Int, today: java.time.LocalDate): Reply {
        val to = from.plusDays(days.toLong())
        val appts = repo.appointmentsBetween(AppointmentLogic.millis(from.atStartOfDay()), AppointmentLogic.millis(to.atStartOfDay()))
            .filter { it.appointment.appointmentStatus != com.kartoteka.app.data.AppointmentStatus.CANCELLED }
            .sortedBy { it.appointment.start }
        val head = if (from == today) t("На этой неделе") else t("На следующей неделе")
        if (appts.isEmpty()) return Reply.Say(t("%1\$s записей нет.", head))
        val lines = appts.groupBy { AppointmentLogic.zoned(it.appointment.start).toLocalDate() }.map { (d, list) ->
            val day = when (d) { today -> t("Сегодня"); today.plusDays(1) -> t("Завтра"); else -> AppointmentLogic.weekday(d) + " " + AppointmentLogic.dateText(d.atStartOfDay()) }
            day + ": " + list.joinToString("; ") { af -> AppointmentLogic.timeText(AppointmentLogic.zoned(af.appointment.start)) + " " + af.person?.displayName.orEmpty() }
        }
        return Reply.Say(t("%1\$s записей: %2\$s.", head, appts.size) + "\n" + lines.joinToString("\n"))
    }

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
            NoaIntent.Channel.VIBER -> "Viber"
        }
        val kind = if (intent.reminder) com.kartoteka.app.data.TemplateKind.REMINDER else com.kartoteka.app.data.TemplateKind.CONFIRM
        val text = if (intent.aboutAppointment) appointmentText(pf, kind) ?: intent.text.orEmpty() else intent.text.orEmpty()
        if (intent.aboutAppointment && text.isBlank()) return@withPerson Reply.Say(t("У %1\$s нет предстоящих записей.", pf.person.displayName))
        val m = NoaActions.Message(pf.person.id, intent.channel, text)
        NoaActions.lastMessage = m
        val names = chatNames(pf)
        val watchReply = { NoaNotifications.watch = NoaNotifications.Companion.Watch(names, System.currentTimeMillis() + 60 * 60_000L, false) }
        // Есть открытая переписка в уведомлениях — отправляем прямо через «Ответить», без открытия мессенджера.
        if (text.isNotBlank() && intent.channel != NoaIntent.Channel.SMS && NoaNotifications.granted(app)) {
            val sent = NoaNotifications.reply(app, names, text)
            if (sent != null) { watchReply(); return@withPerson Reply.Say(t("Отправила %1\$s: «%2\$s». Прочитаю ответ, когда придёт.", pf.person.displayName, text)) }
        }
        if (NoaNotifications.granted(app)) watchReply()
        chatOpenedAt = System.currentTimeMillis()
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
            // Запоминаем вопрос: «нет, на пятницу» / «не в три, а в четыре» поправят эту запись (см. continueBooking).
            awaitingConfirm = intent; awaitingAt = System.currentTimeMillis()
            Reply.Confirm(t("Записать %1\$s%2\$s на %3\$s?", pf.person.displayName, serviceLabel, whenText)) {
                awaitingConfirm = null
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

    private suspend fun moveAppointment(intent: NoaIntent.MoveAppointment, now: LocalDateTime): Reply {
        val named = intent.personQuery.split(" ").any { it.isNotBlank() && it.lowercase() !in NoaParser.PRONOUNS }
        val from = intent.from?.takeIf { intent.fromHadDate || intent.fromHadTime }
        // «Завтра запись на 13:00 — измени время на 14:00»: имени нет, запись ищем по её времени.
        if (!named && from != null) {
            val found = appointmentsAt(from, intent.fromHadDate, intent.fromHadTime, now)
            val slot = slotText(from, intent.fromHadDate, intent.fromHadTime, now)
            return when {
                found.isEmpty() -> Reply.Say(t("Не нашла запись на %1\$s.", slot))
                found.size > 1 -> Reply.Say(t("На %1\$s несколько записей: %2\$s. Скажите, чью перенести.", slot,
                    found.mapNotNull { it.person?.displayName }.joinToString(", ")))
                else -> {
                    val pf = repo.getPerson(found[0].appointment.personId) ?: return Reply.Say(t("Не нашла запись на %1\$s.", slot))
                    lastPerson = pf
                    moveFound(pf, found[0], intent, now)
                }
            }
        }
        return withPerson(intent.personQuery) { pf ->
            val af = from?.takeIf { intent.fromHadDate }?.let { findAppointment(pf, it.toLocalDate(), now) } ?: findAppointment(pf, null, now)
                ?: return@withPerson Reply.Say(t("У %1\$s нет предстоящих записей.", pf.person.displayName))
            moveFound(pf, af, intent, now)
        }
    }

    /** Запланированные записи на названное время: день (или ближайшие две недели) и, если сказано, час и минуты. */
    private suspend fun appointmentsAt(from: LocalDateTime, hadDate: Boolean, hadTime: Boolean, now: LocalDateTime): List<com.kartoteka.app.data.AppointmentFull> {
        val start = if (hadDate) from.toLocalDate().atStartOfDay() else now.toLocalDate().atStartOfDay()
        val end = if (hadDate) start.plusDays(1) else start.plusDays(14)
        return repo.appointmentsBetween(AppointmentLogic.millis(start), AppointmentLogic.millis(end))
            .filter { it.appointment.appointmentStatus == com.kartoteka.app.data.AppointmentStatus.PLANNED }
            .filter { af ->
                val t0 = AppointmentLogic.zoned(af.appointment.start)
                !hadTime || (t0.hour == from.hour && t0.minute == from.minute)
            }
            .sortedBy { it.appointment.start }
            .let { list -> if (hadDate || list.size <= 1) list else list.filter { it.appointment.start >= AppointmentLogic.millis(now) }.take(1).ifEmpty { list.take(1) } }
    }

    private fun slotText(dt: LocalDateTime, hadDate: Boolean, hadTime: Boolean, now: LocalDateTime): String = when {
        hadDate && hadTime -> AppointmentLogic.whenText(dt, now, AppointmentLogic.uiLang()) + " " + t("в %1\$s", AppointmentLogic.timeText(dt))
        hadDate -> AppointmentLogic.whenText(dt, now, AppointmentLogic.uiLang())
        else -> AppointmentLogic.timeText(dt)
    }

    private fun moveFound(pf: PersonFull, af: com.kartoteka.app.data.AppointmentFull, intent: NoaIntent.MoveAppointment, now: LocalDateTime): Reply {
        val target = intent.dateTime
        if (target == null || (!intent.hadDate && !intent.hadTime))
            return Reply.Say(t("На когда перенести? Скажите, например: «перенеси %1\$s на пятницу в 15:00».", pf.person.displayName))
        val old = AppointmentLogic.zoned(af.appointment.start)
        // Назвали только день — время остаётся прежним; только время — день прежний.
        val dt = LocalDateTime.of(if (intent.hadDate) target.toLocalDate() else old.toLocalDate(), if (intent.hadTime) target.toLocalTime() else old.toLocalTime())
        val oldText = apptWhen(af.appointment.start, now)
        val newText = AppointmentLogic.whenText(dt, now, AppointmentLogic.uiLang()) + " " + t("в %1\$s", AppointmentLogic.timeText(dt))
        return Reply.Confirm(t("Перенести запись %1\$s с %2\$s на %3\$s?", pf.person.displayName, oldText, newText)) {
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

    // ---------- напоминания, инструменты, вопросы по картотеке ----------

    private fun dayLabel(date: java.time.LocalDate, today: java.time.LocalDate): String = when (date) {
        today -> t("Сегодня"); today.plusDays(1) -> t("Завтра")
        else -> AppointmentLogic.dateText(date.atStartOfDay()) + ", " + AppointmentLogic.weekday(date)
    }

    private fun speakNumber(v: Double, digits: Int = 4) =
        NoaTools.format(v, comma = com.kartoteka.app.i18n.I18n.lang != com.kartoteka.app.i18n.UiLang.EN, digits = digits)

    /**
     * «Напомни завтра в 10 позвонить Ане»: ближайшие сутки — будильник с подписью, дальше — форма нового события в календаре
     * (пользователь нажимает «Сохранить»). Только стандартные системные переходы.
     */
    private fun remind(intent: NoaIntent.Remind, now: LocalDateTime): Reply {
        if (intent.text.isBlank()) return Reply.Say(t("О чём напомнить? Скажите, например: «напомни завтра в 10 позвонить Ане»."))
        val dt = intent.dateTime ?: return Reply.Say(t("Когда напомнить? Скажите, например: «напомни завтра в 10 %1\$s».", intent.text))
        val whenText = AppointmentLogic.whenText(dt, now, AppointmentLogic.uiLang()) + " " + t("в %1\$s", AppointmentLogic.timeText(dt))
        val within = dt.isAfter(now) && java.time.Duration.between(now, dt).toMinutes() <= 24 * 60
        return if (within) Reply.Do(t("Напоминание %1\$s: «%2\$s». Поставила будильник с этой подписью.", whenText, intent.text)) {
            PhoneActions.alarm(it, dt.hour, dt.minute, intent.text)
        } else Reply.Do(t("Напоминание %1\$s: «%2\$s». Открываю календарь — осталось нажать «Сохранить».", whenText, intent.text)) {
            PhoneActions.calendarEvent(it, intent.text, AppointmentLogic.millis(dt))
        }
    }

    /** Время, число, день недели — по часам телефона, без сети. */
    private fun tool(intent: NoaIntent.Tool, now: LocalDateTime): Reply {
        val today = now.toLocalDate()
        val d = intent.date ?: today
        val label = when (d) { today -> t("Сегодня"); today.plusDays(1) -> t("Завтра"); else -> null }
        val dateText = AppointmentLogic.dateText(d.atStartOfDay())
        val weekday = AppointmentLogic.weekday(d)
        return Reply.Say(when (intent.kind) {
            NoaIntent.ToolKind.TIME -> t("Сейчас %1\$s.", AppointmentLogic.timeText(now))
            NoaIntent.ToolKind.DATE -> if (label != null) t("%1\$s %2\$s, %3\$s.", label, dateText, weekday) else t("%1\$s — %2\$s.", dateText, weekday)
            NoaIntent.ToolKind.WEEKDAY -> if (label != null) t("%1\$s %2\$s.", label, weekday) else t("%1\$s — %2\$s.", dateText, weekday)
        })
    }

    private fun calc(intent: NoaIntent.Calc): Reply {
        if (Regex("/\\s*0(?![0-9.])").containsMatchIn(intent.expression)) return Reply.Say(t("На ноль делить нельзя."))
        val v = NoaTools.evaluate(intent.expression)
            ?: return Reply.Say(t("Не получилось посчитать. Скажите пример ещё раз, например: «посчитай 15 процентов от 2400»."))
        return Reply.Say(t("Получится %1\$s.", speakNumber(v)))
    }

    private fun unitLabel(key: String): String = when (key) {
        "km" -> t("км"); "m" -> t("м"); "cm" -> t("см"); "mi" -> t("миль"); "ft" -> t("футов"); "in" -> t("дюймов")
        "kg" -> t("кг"); "g" -> t("г"); "lb" -> t("фунтов"); "oz" -> t("унций"); "l" -> t("л"); "gal" -> t("галлонов")
        "c" -> "°C"; "f" -> "°F"; else -> key.uppercase()
    }

    private fun convert(intent: NoaIntent.Convert): Reply {
        if (NoaTools.kindOf(intent.from) == NoaTools.Kind.MONEY || NoaTools.kindOf(intent.to) == NoaTools.Kind.MONEY)
            return Reply.Say(t("Курсы валют мне недоступны: без интернета я их не знаю."))
        val r = NoaTools.convert(NoaTools.Conversion(intent.value, intent.from, intent.to))
            ?: return Reply.Say(t("Не получилось перевести эти единицы."))
        return Reply.Say(t("%1\$s %2\$s — это %3\$s %4\$s.", speakNumber(intent.value, 2), unitLabel(intent.from), speakNumber(r, 2), unitLabel(intent.to)))
    }

    /** Предстоящие и прошедшие записи без отменённых — для вопросов по картотеке. */
    private suspend fun liveAppointments(fromMs: Long, toMs: Long, personId: Long?) =
        repo.appointmentsBetween(fromMs, toMs)
            .filter { it.appointment.appointmentStatus != com.kartoteka.app.data.AppointmentStatus.CANCELLED }
            .filter { personId == null || it.appointment.personId == personId }
            .sortedBy { it.appointment.start }

    private suspend fun crm(intent: NoaIntent.Crm, now: LocalDateTime): Reply {
        val today = now.toLocalDate()
        val nowMs = AppointmentLogic.millis(now)
        fun dayStartMs(d: java.time.LocalDate) = AppointmentLogic.millis(d.atStartOfDay())
        // Человек, если назван: без него вопрос про всю картотеку.
        val named = intent.personQuery.split(" ").filter { it.isNotBlank() && it.lowercase() !in NoaParser.PRONOUNS }.joinToString(" ")
        if (intent.kind == NoaIntent.CrmKind.LAST_CONTACT) return withPerson(intent.personQuery) { pf -> lastContact(pf, now) }
        val who: PersonFull? = if (named.isNotBlank() && intent.kind in setOf(NoaIntent.CrmKind.NEXT, NoaIntent.CrmKind.COUNT)) {
            matches(named).firstOrNull() ?: return Reply.Say(t("Не нашла человека по имени «%1\$s».", named))
        } else null
        return when (intent.kind) {
            NoaIntent.CrmKind.NEXT -> {
                val next = liveAppointments(nowMs, nowMs + 366L * 86_400_000, who?.person?.id)
                    .firstOrNull { it.appointment.appointmentStatus == com.kartoteka.app.data.AppointmentStatus.PLANNED }
                    ?: return Reply.Say(if (who != null) t("У %1\$s нет предстоящих записей.", who.person.displayName) else t("Предстоящих записей нет."))
                val title = next.appointment.title.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
                Reply.Say(t("Следующая запись: %1\$s — %2\$s.", next.person?.displayName.orEmpty() + title, apptWhen(next.appointment.start, now)))
            }
            NoaIntent.CrmKind.COUNT -> {
                val from: java.time.LocalDate; val to: java.time.LocalDate
                val label: String
                when (intent.period) {
                    NoaIntent.CrmPeriod.WEEK -> {
                        from = intent.date ?: NoaDateTime.weekStart(today); to = from.plusDays(7)
                        label = when (from) { NoaDateTime.weekStart(today) -> t("На этой неделе"); NoaDateTime.weekStart(today).plusDays(7) -> t("На следующей неделе"); else -> t("На неделе с %1\$s", AppointmentLogic.dateText(from.atStartOfDay())) }
                    }
                    NoaIntent.CrmPeriod.MONTH -> {
                        from = (intent.date ?: today).withDayOfMonth(1); to = from.plusMonths(1)
                        label = when (from) { today.withDayOfMonth(1) -> t("В этом месяце"); today.plusMonths(1).withDayOfMonth(1) -> t("В следующем месяце"); else -> t("В месяце с %1\$s", AppointmentLogic.dateText(from.atStartOfDay())) }
                    }
                    else -> { from = intent.date ?: today; to = from.plusDays(1); label = dayLabel(from, today) }
                }
                val n = liveAppointments(dayStartMs(from), dayStartMs(to), who?.person?.id).size
                Reply.Say(if (n == 0) t("%1\$s записей нет.", label) else t("%1\$s записей: %2\$s.", label, n))
            }
            NoaIntent.CrmKind.FREE -> {
                val day = intent.date ?: today
                val startH = app.settings.dayStartHour.value.value.toIntOrNull() ?: 8
                val endH = app.settings.dayEndHour.value.value.toIntOrNull() ?: 21
                val busy = liveAppointments(dayStartMs(day), dayStartMs(day.plusDays(1)), null)
                    .map { AppointmentLogic.zoned(it.appointment.start) to AppointmentLogic.zoned(it.appointment.end) }
                val slots = NoaTools.freeSlots(busy, day, startH, endH, notBefore = if (day == today) now else null)
                val label = dayLabel(day, today)
                if (slots.isEmpty()) Reply.Say(t("%1\$s свободных окон нет.", label))
                else Reply.Say(t("%1\$s свободно: %2\$s.", label, slots.take(4).joinToString(", ") { (a, b) -> "${a.format(HM)}–${b.format(HM)}" }))
            }
            NoaIntent.CrmKind.WHO_AT -> {
                val day = intent.date ?: today
                val time = intent.time ?: return Reply.Say(t("На какое время?"))
                val at = AppointmentLogic.millis(LocalDateTime.of(day, time))
                val hits = liveAppointments(dayStartMs(day), dayStartMs(day.plusDays(1)), null).filter { at >= it.appointment.start && at < it.appointment.end }
                val label = dayLabel(day, today) + " " + t("в %1\$s", time.format(HM))
                if (hits.isEmpty()) Reply.Say(t("%1\$s никого — свободно.", label))
                else Reply.Say(t("%1\$s: %2\$s.", label, hits.joinToString(", ") { (it.person?.displayName.orEmpty()) + it.appointment.title.takeIf { s -> s.isNotBlank() }?.let { s -> " ($s)" }.orEmpty() }))
            }
            NoaIntent.CrmKind.BIRTHDAYS -> birthdays(intent, today)
            NoaIntent.CrmKind.LAST_CONTACT -> Reply.Say("")
        }
    }

    private val HM = java.time.format.DateTimeFormatter.ofPattern("HH:mm")

    /** Когда в последний раз были на связи: звонок/сообщение (последний контакт), прошедшая запись или запись в хронике. */
    private suspend fun lastContact(pf: PersonFull, now: LocalDateTime): Reply {
        val nowMs = AppointmentLogic.millis(now)
        val lastAppt = liveAppointments(nowMs - 5L * 366 * 86_400_000, nowMs, pf.person.id).maxOfOrNull { it.appointment.start }
        val lastJournal = pf.journal.maxOfOrNull { it.date }
        val last = listOfNotNull(lastAppt, lastJournal, pf.person.lastContactAt).filter { it <= nowMs }.maxOrNull()
            ?: return Reply.Say(t("С %1\$s контактов пока нет.", pf.person.displayName))
        val date = AppointmentLogic.zoned(last).toLocalDate()
        val days = java.time.temporal.ChronoUnit.DAYS.between(date, now.toLocalDate())
        val ago = when (days) {
            0L -> t("сегодня"); 1L -> t("вчера")
            else -> "$days " + com.kartoteka.app.data.ArchiveLogic.plural(days, "день", "дня", "дней") + " " + t("назад")
        }
        return Reply.Say(t("Последний контакт с %1\$s: %2\$s (%3\$s).", pf.person.displayName, ago, AppointmentLogic.dateText(date.atStartOfDay())))
    }

    /** Дни рождения за день / неделю / месяц (или ближайшие 30 дней), не больше пяти. */
    private suspend fun birthdays(intent: NoaIntent.Crm, today: java.time.LocalDate): Reply {
        val (from, to, label) = when (intent.period) {
            NoaIntent.CrmPeriod.WEEK -> {
                val mon = intent.date ?: NoaDateTime.weekStart(today)
                Triple(maxOf(mon, today), mon.plusDays(6), if (mon == NoaDateTime.weekStart(today)) t("На этой неделе") else t("На следующей неделе"))
            }
            NoaIntent.CrmPeriod.MONTH -> {
                val first = intent.date
                if (first == null) Triple(today, today.plusDays(30), t("Скоро"))
                else Triple(maxOf(first, today), first.withDayOfMonth(first.lengthOfMonth()), if (first.month == today.month) t("В этом месяце") else t("В следующем месяце"))
            }
            else -> { val d = intent.date ?: today; Triple(d, d, dayLabel(d, today)) }
        }
        val hits = repo.getAll().mapNotNull { pf ->
            com.kartoteka.app.data.ArchiveLogic.nextBirthday(pf.person, from)?.takeIf { !it.isAfter(to) }?.let { pf to it }
        }.sortedBy { it.second }
        if (hits.isEmpty()) return Reply.Say(t("%1\$s дней рождения нет.", label))
        val items = hits.take(5).joinToString("; ") { (pf, d) -> pf.person.displayName + " — " + com.kartoteka.app.i18n.I18n.dayMonth(d.dayOfMonth, d.monthValue) }
        return Reply.Say(t("%1\$s день рождения: %2\$s.", label, items))
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

    // ---------- музыка и видео ----------

    private suspend fun play(intent: NoaIntent.Play): Reply {
        val named = intent.app?.let { PhoneActions.find(app, it) }
        val pkg = named?.pkg ?: if (intent.video) NoaMedia.YT else NoaMedia.defaultMusic(app)
        val label = named?.label ?: when (pkg) { NoaMedia.YT -> "YouTube"; NoaMedia.YT_MUSIC -> "YouTube Music"; NoaMedia.SPOTIFY -> "Spotify"; else -> "" }
        if (NoaMedia.isLiked(intent.query)) {
            val lp = if (pkg == NoaMedia.YT || pkg == NoaMedia.YT_MUSIC) pkg else if (NoaMedia.installed(app, NoaMedia.YT_MUSIC)) NoaMedia.YT_MUSIC else NoaMedia.YT
            val lbl = if (lp == NoaMedia.YT_MUSIC) "YouTube Music" else "YouTube"
            return Reply.Do(t("Включаю плейлист «Понравившиеся» в %1\$s.", lbl) + if (intent.shuffle) " " + t("В случайном порядке.") else "") { ctx ->
                NoaMedia.open(ctx, lp, NoaMedia.likedUrl(lp), "", true, false, intent.shuffle)
            }
        }
        val query = intent.query.ifBlank { if (intent.video) t("популярное видео") else "" }
        // YouTube / YouTube Music: находим конкретное видео или плейлист — тогда приложение сразу играет.
        var url: String? = null
        var title: String? = null
        // «любое видео с канала X»: находим канал и берём случайное из его последних видео.
        if (intent.channel.isNotBlank()) {
            val picked = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                kotlinx.coroutines.withTimeoutOrNull(12000) {
                    NoaMedia.searchChannel(intent.channel)?.let { ch ->
                        NoaMedia.channelVideos(ch.id).take(15).randomOrNull()?.let { ch to it }
                    }
                }
            }
            if (picked == null) return Reply.Say(t("Не нашла канал «%1\$s» или его видео. Проверьте интернет и название.", intent.channel))
            val (ch, video) = picked
            val ytPkg = if (pkg == NoaMedia.YT_MUSIC) NoaMedia.YT_MUSIC else NoaMedia.YT
            val link = NoaMedia.playUrl(ytPkg, video)
            val what = video.title ?: t("видео")
            return Reply.Do(t("Включаю «%1\$s» с канала %2\$s.", what, ch.name ?: intent.channel)) { ctx ->
                NoaMedia.open(ctx, ytPkg, link, intent.query, false, false, false)
            }
        }
        if ((pkg == NoaMedia.YT || pkg == NoaMedia.YT_MUSIC) && query.isNotBlank()) {
            val playlist = intent.playlist || (pkg == NoaMedia.YT_MUSIC && !intent.artist && intent.query.isBlank())
            val found = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                kotlinx.coroutines.withTimeoutOrNull(7000) { NoaMedia.searchYoutube(query, playlist) ?: if (playlist) NoaMedia.searchYoutube(query, false) else null }
            }
            if (found != null) { url = NoaMedia.playUrl(pkg, found); title = found.title }
        }
        val what = title ?: intent.query.ifBlank { if (intent.playlist) t("плейлист") else if (intent.video) t("видео") else t("музыку") }
        val shuffleNote = if (intent.shuffle) " " + t("в случайном порядке") else ""
        val text = (if (label.isNotBlank()) t("Включаю %1\$s в %2\$s.", what, label) else t("Включаю %1\$s.", what)).removeSuffix(".") + shuffleNote + "."
        val finalUrl = url
        return Reply.Do(text) { ctx -> NoaMedia.open(ctx, pkg, finalUrl, intent.query, intent.playlist, intent.artist, intent.shuffle) }
    }

    /** Громкость музыки в процентах от шкалы телефона (на Samsung шагов 15, поэтому реальные проценты округляются до шага). */
    private fun volume(i: NoaIntent.Volume): Reply {
        val am = app.getSystemService(android.media.AudioManager::class.java)
        val max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val cur = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        fun pct(idx: Int) = Math.round(idx * 100f / max)
        val p = i.percent ?: 0
        val atMax = cur >= max; val atMin = cur <= 0
        if ((i.kind == NoaIntent.VolumeKind.MAX || i.kind == NoaIntent.VolumeKind.UP) && atMax) return Reply.Say(t("Громкость уже на максимуме — громче не могу."))
        if ((i.kind == NoaIntent.VolumeKind.MIN || i.kind == NoaIntent.VolumeKind.DOWN) && atMin) return Reply.Say(t("Громкость уже на минимуме — тише не могу."))
        val want = when (i.kind) {
            NoaIntent.VolumeKind.MAX -> 100; NoaIntent.VolumeKind.MIN -> 0; NoaIntent.VolumeKind.SET -> p
            NoaIntent.VolumeKind.UP -> pct(cur) + p; NoaIntent.VolumeKind.DOWN -> pct(cur) - p
        }
        val idx = Math.round(want.coerceIn(0, 100) * max / 100f).coerceIn(0, max)
        // Просили «на 20 процентов громче», а шаг шкалы не сдвинулся — двигаем минимум на один шаг.
        val fixed = when {
            i.kind == NoaIntent.VolumeKind.UP && idx <= cur -> (cur + 1).coerceAtMost(max)
            i.kind == NoaIntent.VolumeKind.DOWN && idx >= cur -> (cur - 1).coerceAtLeast(0)
            else -> idx
        }
        runCatching { am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, fixed, android.media.AudioManager.FLAG_SHOW_UI) }
            .onFailure { return Reply.Say(t("Не получилось поменять громкость.")) }
        val now = pct(fixed)
        return Reply.Say(when {
            fixed >= max && i.kind == NoaIntent.VolumeKind.UP && want > 100 -> t("Громкость 100% — это максимум, выше не могу.")
            fixed >= max -> t("Громкость на максимуме.")
            fixed <= 0 -> t("Звук на минимуме.")
            else -> t("Громкость %1\$s%%.", now)
        })
    }

    /** «Открой голосовые заметки об Илье и начни запись»: запись в карточку человека, стоп — «стоп запись» или кнопка в шторке. */
    private suspend fun voiceNote(i: NoaIntent.VoiceNote): Reply = withPerson(i.personQuery) { pf ->
        if (!com.kartoteka.app.voice.VoiceRecorder.hasPermission(app)) return@withPerson Reply.Say(t("Нет доступа к микрофону — разрешите его в настройках телефона."))
        if (VoiceNoteService.active) return@withPerson Reply.Say(t("Запись уже идёт."))
        Reply.Do(t("Записываю заметку о %1\$s.", pf.person.displayName)) { ctx ->
            VoiceNoteService.start(ctx, pf.person.id, pf.person.displayName)
        }
    }

    private fun media(c: NoaMedia.Control): Reply {
        if (c == NoaMedia.Control.WHAT) {
            val now = NoaMedia.nowPlaying(app)
                ?: return Reply.Say(if (NoaNotifications.granted(app)) t("Сейчас ничего не играет.") else t("Чтобы я видела, что играет, включите мне доступ к уведомлениям в настройках ассистента."))
            return Reply.Say(t("Сейчас играет: %1\$s.", now))
        }
        if (c == NoaMedia.Control.LOUDER || c == NoaMedia.Control.QUIETER) {
            val am = app.getSystemService(android.media.AudioManager::class.java)
            val cur = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
            if (c == NoaMedia.Control.LOUDER && cur >= am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)) return Reply.Say(t("Громкость уже на максимуме — громче не могу."))
            if (c == NoaMedia.Control.QUIETER && cur <= 0) return Reply.Say(t("Громкость уже на минимуме — тише не могу."))
        }
        val needsAccess = c == NoaMedia.Control.SHUFFLE_ON || c == NoaMedia.Control.SHUFFLE_OFF || c == NoaMedia.Control.REPEAT
        if (needsAccess && !NoaNotifications.granted(app))
            return Reply.Say(t("Чтобы управлять перемешиванием, включите мне доступ к уведомлениям в настройках ассистента."))
        val text = when (c) {
            NoaMedia.Control.PAUSE -> t("Пауза."); NoaMedia.Control.RESUME -> t("Продолжаю.")
            NoaMedia.Control.NEXT -> t("Следующий."); NoaMedia.Control.PREV -> t("Предыдущий.")
            NoaMedia.Control.SHUFFLE_ON -> t("Перемешала."); NoaMedia.Control.SHUFFLE_OFF -> t("Играю по порядку.")
            NoaMedia.Control.REPEAT -> t("Повторяю."); NoaMedia.Control.STOP -> t("Остановила.")
            NoaMedia.Control.LOUDER -> t("Громче."); NoaMedia.Control.QUIETER -> t("Тише.")
            NoaMedia.Control.WHAT -> ""
        }
        // Короткий ответ без голоса — чтобы не перебивать музыку.
        return Reply.Do(text, quiet = true) { ctx -> NoaMedia.control(ctx, c) }
    }

    // ---------- сообщения ----------

    /** Как человек может быть подписан в мессенджере: полное имя, имя, прозвище, номер. */
    private fun chatNames(pf: PersonFull): List<String> = listOfNotNull(
        pf.person.displayName, pf.person.fullName, pf.person.firstName.takeIf { it.length > 2 }, pf.person.nickname.takeIf { it.length > 2 },
        pf.person.lastName.takeIf { it.length > 3 }, pf.phone?.filter { it.isDigit() }?.takeLast(9),
    ).filter { it.isNotBlank() }.distinct()

    private suspend fun replyTo(intent: NoaIntent.Reply): Reply {
        if (intent.text.isBlank()) return Reply.Say(t("Что ответить?"))
        if (!NoaNotifications.granted(app)) return Reply.Say(t("Чтобы отвечать в мессенджерах, включите мне доступ к уведомлениям в настройках ассистента."))
        // Кому: названный человек, тот, о ком говорили, или автор последнего сообщения.
        val q = intent.personQuery
        val names = if (q.isNotBlank() && knows(q)) matches(q).firstOrNull()?.let { chatNames(it) } ?: listOf(q)
            else if (q.isNotBlank()) listOf(q)
            else lastPerson?.let { chatNames(it) } ?: NoaNotifications.recentMessages().firstOrNull()?.let { listOf(it.sender) }
            ?: return Reply.Say(t("Кому ответить?"))
        val text = intent.text
        return Reply.Confirm(t("Ответить %1\$s: «%2\$s»?", names.first(), text)) {
            val sent = NoaNotifications.reply(app, names, text)
            if (sent != null) {
                NoaNotifications.watch = NoaNotifications.Companion.Watch(names, System.currentTimeMillis() + 30 * 60_000L, false)
                Reply.Say(t("Отправила. Прочитаю, когда ответит."))
            } else Reply.Say(t("Не нашла переписку с %1\$s в уведомлениях. Скажите «напиши %1\$s …» — открою чат.", names.first()))
        }
    }

    private suspend fun readMessages(intent: NoaIntent.ReadMessages): Reply {
        if (!NoaNotifications.granted(app)) return Reply.Say(t("Чтобы читать сообщения, включите мне доступ к уведомлениям в настройках ассистента."))
        val q = intent.personQuery
        val names = when {
            q.isNotBlank() -> matches(q).firstOrNull()?.let { chatNames(it) } ?: listOf(q)
            intent.wait -> lastPerson?.let { chatNames(it) }
            else -> null
        }
        if (intent.wait) {
            val n = names ?: return Reply.Say(t("Чей ответ ждать?"))
            NoaNotifications.watch = NoaNotifications.Companion.Watch(n, System.currentTimeMillis() + 60 * 60_000L, false)
            return Reply.Say(t("Хорошо, прочитаю ответ %1\$s, как только придёт.", n.first()))
        }
        val w = names?.let { NoaNotifications.Companion.Watch(it, Long.MAX_VALUE, false) }
        val list = NoaNotifications.recentMessages().filter { w == null || w.matches(it.sender) }.take(5)
        if (list.isEmpty()) return Reply.Say(if (names != null) t("Новых сообщений от %1\$s нет.", names.first()) else t("Новых сообщений нет."))
        return Reply.Say(list.joinToString("\n") { t("%1\$s: %2\$s", it.sender, it.text) })
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

    /** Текст подтверждения или напоминания о записи по шаблону приложения (своему для услуги или общему) — на языке человека. */
    private suspend fun appointmentText(pf: PersonFull, kind: com.kartoteka.app.data.TemplateKind): String? {
        val now = System.currentTimeMillis()
        val af = lastAppointmentId?.let { repo.getAppointment(it) }?.takeIf { it.appointment.personId == pf.person.id }
            ?: repo.appointmentsBetween(now, now + 365L * 86_400_000).filter { it.appointment.personId == pf.person.id }.minByOrNull { it.appointment.start }
            ?: return null
        val lang = app.settings.langFor(pf.person)
        val service = repo.getService(af.appointment.serviceId)
        val template = AppointmentLogic.messageTemplate(
            service, kind,
            app.settings.template(kind, lang).value.value, lang,
        )
        return AppointmentLogic.fill(template, af.appointment, pf.person, lang)
    }

    /**
     * Данные для модели по запросу: карточки упомянутых людей (или того, о ком говорили), план на сегодня и завтра.
     * Так модель отвечает по вашей картотеке, а не выдумывает.
     */
    suspend fun contextFor(text: String): String = NoaContext.build(repo, text, lastPerson)

    /** Имена из картотеки — подсказка модели, чтобы она называла людей так, как они записаны. */
    suspend fun knownNames(limit: Int = 60): List<String> = NoaContext.names(repo, limit)

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
