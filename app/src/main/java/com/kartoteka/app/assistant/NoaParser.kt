package com.kartoteka.app.assistant

import java.time.LocalDateTime

/** Что Ноа поняла из фразы. Человек и услуга — текстом, их сопоставляет исполнитель. */
sealed interface NoaIntent {
    data class CreateAppointment(
        val personQuery: String,
        val dateTime: LocalDateTime?,
        val hadTime: Boolean,
        val serviceQuery: String?,
        val confirm: Boolean,
    ) : NoaIntent
    data class Find(val query: String) : NoaIntent
    data class Open(val personQuery: String) : NoaIntent
    data class OpenScreen(val section: Section) : NoaIntent
    data class Call(val personQuery: String) : NoaIntent
    /** [aboutAppointment] — «отправь ему об этом / подтверждение»: текст берём из шаблона подтверждения записи. */
    data class Message(val personQuery: String, val channel: Channel, val text: String?, val aboutAppointment: Boolean = false) : NoaIntent
    /** «Возьми контакт Илья Рыков…» — просто выбрать человека для следующих шагов. */
    data class Select(val personQuery: String) : NoaIntent
    data class AddNote(val personQuery: String, val text: String) : NoaIntent
    /** Открыть контакт человека в приложении: Instagram, Facebook, Viber, почта, сайт… */
    data class OpenContact(val personQuery: String, val type: com.kartoteka.app.data.ContactType) : NoaIntent
    /** Проложить маршрут к адресу человека (дом / работа / любой). */
    /** Маршрут: к человеку ([personQuery]) или в любое место ([place] — «Киевская 5», «ближайшая заправка»). */
    data class Route(val personQuery: String, val kind: com.kartoteka.app.data.PlaceKind?, val app: String? = null, val place: String = "") : NoaIntent
    /** Что запланировано на день: записи и дни рождения. */
    data class Agenda(val date: java.time.LocalDate) : NoaIntent
    /** Вопрос о человеке: ответ из его карточки. */
    data class PersonInfo(val personQuery: String, val topic: Topic, val question: String) : NoaIntent
    data class Favorite(val personQuery: String, val on: Boolean) : NoaIntent
    /** Отменить/удалить запись человека (на день [date] или ближайшую). */
    data class CancelAppointment(val personQuery: String, val date: java.time.LocalDate?, val delete: Boolean) : NoaIntent
    /** Перенести ближайшую запись человека; [hadDate]/[hadTime] — что именно названо в новом времени. */
    data class MoveAppointment(val personQuery: String, val dateTime: LocalDateTime?, val hadDate: Boolean, val hadTime: Boolean) : NoaIntent
    /** Несколько действий подряд: «открой Аню и добавь заметку…». */
    data class Sequence(val steps: List<NoaIntent>) : NoaIntent
    /** Запустить приложение телефона по названию. */
    data class LaunchApp(val name: String) : NoaIntent
    /** Передать данные человека: в блокнот телефона, Google, буфер, любое приложение. [target]: notes|google|clipboard|share|app:<имя>. */
    data class ShareData(val personQuery: String, val data: Data, val target: String) : NoaIntent
    data class WebSearch(val query: String) : NoaIntent
    data class Alarm(val hour: Int, val minute: Int, val label: String? = null) : NoaIntent
    data class Timer(val seconds: Int) : NoaIntent
    data class Flashlight(val on: Boolean) : NoaIntent
    /** Включить музыку: [query] — что (пусто — что-нибудь/продолжить), [app] — в каком приложении. */
    data class Play(val query: String, val app: String?, val playlist: Boolean, val artist: Boolean = false,
                    val shuffle: Boolean = false, val video: Boolean = false, val channel: String = "") : NoaIntent
    /** Пауза, дальше, перемешать, громче… в плеере, который сейчас играет. */
    data class Media(val control: NoaMedia.Control) : NoaIntent
    /** Ответить человеку в мессенджере (кнопка «Ответить» уведомления), без открытия приложения. */
    data class Reply(val personQuery: String, val text: String) : NoaIntent
    /** Прочитать новые сообщения (от человека или все). [wait] — ждать ответа и прочитать, когда придёт. */
    data class ReadMessages(val personQuery: String, val wait: Boolean = false) : NoaIntent
    /** На главный экран («закрой приложение», «сверни»). */
    data object GoHome : NoaIntent
    data class PhoneSettings(val what: String?) : NoaIntent
    data object Lock : NoaIntent
    data object Backup : NoaIntent
    data class Unknown(val heard: String) : NoaIntent

    enum class Channel { WHATSAPP, SMS, TELEGRAM }
    enum class Data { NOTES, PHONE, ADDRESS, EMAIL, BIRTHDAY, CARD }
    enum class Topic { BIRTHDAY, PHONE, ADDRESS, SUMMARY }
    enum class Section { PEOPLE, CALENDAR, MAP, BROADCAST, SETTINGS, SERVICES }
}

/**
 * Разбор голосовой/текстовой команды по правилам (Этап 1, без нейросети).
 * Русский, украинский, английский. Сначала определяем намерение по ключевым словам,
 * затем вытаскиваем человека, дату/время и прочее.
 */
object NoaParser {
    private fun has(s: String, vararg keys: String) = keys.any { s.contains(" $it") }

    /** Союз + глагол команды: место, где одна команда заканчивается и начинается следующая. */
    private val CHAIN = Regex(
        "\\s*(?:,\\s*)?(?:\\s(?:и|і|й|та|а|потом|потім|затем|после|then|and)\\s+)+" +
            "(?:(?:потом|потім|затем|then|также|тоже|сразу|ещё|еще|також|теж|одразу|відразу|заодно|also|ну)\\s+)*" +
            "(?=(?:добав|додай|додати|запиш|позвон|подзвон|набер|напиш|отправ|відправ|надішл|скинь|відкрий|открой|покажи|проклад|пролож|построй|прокласти|маршрут|удал|видал|отмен|скасу|перенес|расскаж|розкаж|включ|увімкн|запуст|постав|play|закр|сверн|згорн|ответь|відповід|прочит|дождис|дочекай|пауз|громч|гучн|тише|тихіш|перемеш|перемі|скопир|скопію|перенес|найди|знайди|нажми|натисн|зайди|перейди|напомн|нагадай|add|call|write|send|open|show|route)\\S*)",
        RegexOption.IGNORE_CASE,
    )
    val PRONOUNS = setOf("ей", "ему", "её", "ее", "его", "неё", "нее", "него", "ним", "ней", "їй", "йому", "її", "його", "нього", "неї", "ним", "нею", "нему", "ньому", "him", "her", "them")

    fun parse(input: String, now: LocalDateTime = LocalDateTime.now()): NoaIntent {
        // Кавычки сохраняем как есть (в них текст), остальное — без знаков препинания.
        val parts = input.split(CHAIN).map { it.trim() }.filter { it.isNotBlank() }
        if (parts.size < 2) return parseOne(input, now)
        // «Возьми заметки об Илье и перенеси их в блокнот» — одна передача данных, хоть и через «и».
        val whole = shareData(normalize(input))
        if (whole != null && parts.none { shareData(normalize(it)) != null }) return whole
        // Если какая-то часть — не команда («ну а открой…»), это не цепочка: разбираем фразу целиком.
        if (parts.any { parseOne(it, now) is NoaIntent.Unknown }) return parseOne(input, now)
        // Текст сообщения не режем на команды: «напиши Илье что куплю хлеб и позвоню вечером» — одно сообщение.
        // Отдельными остаются только «служебные» хвосты: закрой, прочитай ответ, пауза, подтверждение.
        // Действия с телефоном («…и включи музыку», «…и проложи маршрут домой») — тоже отдельные, если глагол
        // повелительный: «…и включу ей музыку» (1-е лицо) — это ещё текст сообщения.
        val seps = CHAIN.findAll(input).map { it.value }.toList()
        val joined = mutableListOf<NoaIntent>()
        for ((i, part) in parts.withIndex()) {
            val prev = joined.lastOrNull()
            val cur = parseOne(part, now)
            val first = part.trim().lowercase().substringBefore(' ')
            val device = (cur is NoaIntent.Play || cur is NoaIntent.Route || cur is NoaIntent.LaunchApp || cur is NoaIntent.Alarm ||
                cur is NoaIntent.Timer || cur is NoaIntent.Flashlight || cur is NoaIntent.Lock || cur is NoaIntent.PhoneSettings) &&
                !first.endsWith("у") && !first.endsWith("ю")
            val tail = device || cur is NoaIntent.GoHome || cur is NoaIntent.ReadMessages || cur is NoaIntent.Media ||
                (cur is NoaIntent.Message && (cur.aboutAppointment || cur.personQuery.isNotBlank()))
            if (prev is NoaIntent.Message && prev.text != null && !tail) {
                // Склеиваем текст обратно с тем союзом, что был сказан («і», «та», «потом»…).
                val sep = seps.getOrNull(i - 1)?.trim()?.trimStart(',')?.trim().orEmpty().ifBlank { "и" }
                joined[joined.lastIndex] = prev.copy(text = prev.text + " " + sep + " " + part)
            } else joined += cur
        }
        if (joined.size == 1) return joined[0]
        // Человек из предыдущего шага переходит в следующий, если там его нет («…и добавь ей заметку»).
        var person = ""
        val steps = joined.map { step ->
            val p = personOf(step).split(" ").filter { it.isNotBlank() && it.lowercase() !in PRONOUNS }.joinToString(" ")
            if (p.isNotBlank()) { person = p; withPerson(step, p) } else withPerson(step, person)
        }
        // «Открой ютуб мьюзик и включи плейлист» — включаем сразу в названном приложении.
        val merged = mutableListOf<NoaIntent>()
        for (st in steps) {
            val prev = merged.lastOrNull()
            if (st is NoaIntent.Play && st.app == null && prev is NoaIntent.LaunchApp) merged[merged.lastIndex] = st.copy(app = prev.name)
            // «зайди на ютуб и включи …» — «зайди» разобралось как открытие карточки, но «ютуб» — это приложение
            else if (st is NoaIntent.Play && st.app == null && prev is NoaIntent.Open && PhoneActions.isAppName(prev.personQuery)) merged[merged.lastIndex] = st.copy(app = prev.personQuery)
            // «найди плейлист … и включи в случайном порядке» — одно действие
            else if (prev is NoaIntent.Play && st is NoaIntent.Media && st.control in setOf(NoaMedia.Control.SHUFFLE_ON, NoaMedia.Control.RESUME))
                merged[merged.lastIndex] = prev.copy(shuffle = prev.shuffle || st.control == NoaMedia.Control.SHUFFLE_ON)
            else if (prev is NoaIntent.Play && st is NoaIntent.Play && st.query.isBlank())
                merged[merged.lastIndex] = prev.copy(shuffle = prev.shuffle || st.shuffle, app = prev.app ?: st.app)
            else merged += st
        }
        return if (merged.size == 1) merged[0] else NoaIntent.Sequence(merged)
    }

    fun personOf(i: NoaIntent): String = when (i) {
        is NoaIntent.CreateAppointment -> i.personQuery; is NoaIntent.Open -> i.personQuery
        is NoaIntent.Call -> i.personQuery; is NoaIntent.Message -> i.personQuery
        is NoaIntent.AddNote -> i.personQuery; is NoaIntent.OpenContact -> i.personQuery
        is NoaIntent.Route -> i.personQuery; is NoaIntent.PersonInfo -> i.personQuery
        is NoaIntent.Favorite -> i.personQuery; is NoaIntent.Select -> i.personQuery
        is NoaIntent.CancelAppointment -> i.personQuery; is NoaIntent.MoveAppointment -> i.personQuery
        is NoaIntent.ShareData -> i.personQuery
        is NoaIntent.Reply -> i.personQuery; is NoaIntent.ReadMessages -> i.personQuery
        else -> ""
    }

    fun withPerson(i: NoaIntent, p: String): NoaIntent = when (i) {
        is NoaIntent.CreateAppointment -> i.copy(personQuery = p); is NoaIntent.Open -> i.copy(personQuery = p)
        is NoaIntent.Call -> i.copy(personQuery = p); is NoaIntent.Message -> i.copy(personQuery = p)
        is NoaIntent.AddNote -> i.copy(personQuery = p); is NoaIntent.OpenContact -> i.copy(personQuery = p)
        is NoaIntent.Route -> i.copy(personQuery = p); is NoaIntent.PersonInfo -> i.copy(personQuery = p)
        is NoaIntent.Favorite -> i.copy(personQuery = p); is NoaIntent.Select -> i.copy(personQuery = p)
        is NoaIntent.CancelAppointment -> i.copy(personQuery = p); is NoaIntent.MoveAppointment -> i.copy(personQuery = p)
        is NoaIntent.ShareData -> i.copy(personQuery = p)
        is NoaIntent.Reply -> i.copy(personQuery = p); is NoaIntent.ReadMessages -> i.copy(personQuery = p)
        else -> i
    }

    /**
     * Распознаватель речи сам расставляет знаки («Запиши, Илья Рыков, на завтра.»). Убираем их,
     * кроме двоеточия/точки во времени (12:00, 12.30) и кавычек (в них — текст заметки/сообщения).
     */
    fun normalize(input: String): String = input
        .replace(Regex("[,!?;…]+"), " ")
        .replace(Regex("\\.(?!\\d)|(?<!\\d)\\."), " ")
        .replace(Regex("\\s+"), " ").trim()

    fun parseOne(input: String, now: LocalDateTime = LocalDateTime.now()): NoaIntent {
        val original = normalize(input)
        val s = " " + original.lowercase().replace(Regex("\\s+"), " ") + " "
        if (original.isBlank()) return NoaIntent.Unknown(original)
        // «Напиши Илье, что запись переносится / поставил на паузу» — слова в тексте сообщения не команды.
        val verb = s.trim().substringBefore(' ')
        if (verb in MESSAGE_VERBS) {
            val head = messageBody(original)?.first?.let { " " + it.lowercase() + " " }
            // «скажи что у меня завтра» — не сообщение: для «скажи/передай» нужен адресат (имя или «ему/ей»).
            val addressee = head != null && (extractPerson(head).isNotBlank() || head.split(" ").any { it in PRONOUNS })
            if (head != null && !has(head, "заметк", "нотатк", "хроник", "хронік", "note") &&
                (addressee || verb !in setOf("скажи", "передай", "скажіть", "передайте"))) return message(s, original)
        }

        // блокировка / копия — без человека
        if (has(s, "заблокируй", "заблокуй", "закрой сейф", "закрий сейф", "lock")) return NoaIntent.Lock
        if (has(s, "закрой приложение", "закрий застосунок", "закрий додаток", "закрой его", "закрий його", "сверни", "згорни", "на главный экран",
                "на головний екран", "домой экран", "выйди", "вийди", "go home", "close app", "закрой вотсап", "закрой телеграм", "закрий") ||
            s.trim() == "закрой") return NoaIntent.GoHome
        media(s)?.let { return it }
        messages(s, original)?.let { return it }
        if (has(s, "резервную копию", "бэкап", "бекап", "backup", "копію", "копию")) return NoaIntent.Backup

        // действия на телефоне: передать данные, будильник, таймер, фонарик, поиск, запуск приложений
        shareData(original)?.let { return it }
        phoneAction(s, original, now)?.let { return it }

        // «возьми контакт Илья Рыков» — выбрать человека для следующих команд
        if (has(s, "возьми", "візьми", "выбери", "обери", "вибери", "бери", "take", "select") && !has(s, "запиш", "напиш", "позвон", "подзвон", "отправ", "надішл")) {
            return NoaIntent.Select(extractPerson(s))
        }

        // заметка в хронику — раньше записи на приём («запиши в хронику»)
        if (has(s, "заметк", "нотатк", "хроник", "хронік", "note")) return note(original, s)

        // план на день: «что у меня сегодня», «які записи на завтра»
        if (has(s, "что у меня", "что сегодня", "что завтра", "що в мене", "що у мене", "що сьогодні", "що завтра",
                "какие планы", "які плани", "план на", "расписан", "розклад", "кто записан", "хто записан", "кто сегодня", "хто сьогодні",
                "какие записи", "які записи", "записи на", "agenda", "my schedule", "what do i have")) {
            val d = NoaDateTime.parse(original, now)?.dateTime?.toLocalDate() ?: now.toLocalDate()
            return NoaIntent.Agenda(d)
        }

        // вопросы о человеке
        info(s, original)?.let { return it }

        // избранное
        if (has(s, "избранн", "обран", "favorite", "favourite")) {
            val off = has(s, "убери", "удали", "прибери", "видали", "remove", "из избранн", "з обран")
            return NoaIntent.Favorite(extractPerson(s), on = !off)
        }

        // маршрут к человеку
        if (has(s, "маршрут", "проклад", "пролож", "прокласти", "перестрой", "перебудуй", "поехали", "поїхали", "едем в", "едем до", "едем к", "едем на", "едем домой", "поедем", "поїдемо", "їдемо",
                "доехать до", "доїхати до", "навигатор", "навігатор", "построй путь", "построй дорогу", "дорогу до", "дорогу к", "как доехать", "как добраться",
                "як доїхати", "як дістатися", "шлях", "навигац", "навігац", "отвези", "веди к", "route", "directions", "navigate")) {
            val kind = when {
                has(s, "работ", "робот", "офис", "офіс", "work", "office") -> com.kartoteka.app.data.PlaceKind.WORK
                has(s, "дом", "додому", "дому", "home") -> com.kartoteka.app.data.PlaceKind.HOME
                else -> null
            }
            val app = when {
                has(s, "waze", "вейз", "вэйз", "вейс", "уэйз") -> "waze"
                has(s, "google", "гугл") -> "google"
                has(s, "яндекс", "yandex") -> "yandex"
                has(s, "organic", "органик") -> "organic"
                else -> null
            }
            return NoaIntent.Route(extractPerson(s), kind, app, destination(original))
        }

        // открыть контакт в другом приложении: «нажми на инстаграм Ани», «відкрий фейсбук Олега»
        contactType(s)?.let { type ->
            if (!has(s, "напиши", "напиш", "отправь", "надішли", "write", "send", "сообщение", "повідомлення")) {
                return NoaIntent.OpenContact(extractPerson(s), type)
            }
        }

        // перенести / отменить / удалить запись — раньше создания («удали запись Ильи» — не новая запись)
        val apptWord = has(s, "запис", "встреч", "зустріч", "сеанс", "прийом", "приём", "прием", "appointment", "booking", "meeting", "визит", "візит")
        if (has(s, "перенес", "перенест", "перенос", "передвин", "пересун", "зсунь", "посунь", "reschedule", "move")) {
            // «с пятницы на субботу» — новое время только то, что после «на»
            val to = Regex("\\s(?:с|со|з|із|from)\\s.+?\\s(?:на|to)\\s(.+)$").find(" " + original.lowercase())?.groupValues?.get(1)
            val dt = to?.let { NoaDateTime.parse(it, now) } ?: NoaDateTime.parse(original, now)
            return NoaIntent.MoveAppointment(extractPerson(s), dt?.dateTime, dt?.hadDate ?: false, dt?.hadTime ?: false)
        }
        val cancelWord = has(s, "отмени", "отменить", "скасуй", "скасувати", "відміни", "cancel", "call off")
        val deleteWord = has(s, "удали", "удалить", "видали", "видалити", "сотри", "зітри", "delete", "remove") ||
            (apptWord && has(s, "убери", "прибери"))
        if ((apptWord && (cancelWord || deleteWord)) || (cancelWord && !has(s, "избранн", "обран"))) {
            val dt = NoaDateTime.parse(original, now)
            return NoaIntent.CancelAppointment(extractPerson(s), dt?.takeIf { it.hadDate }?.dateTime?.toLocalDate(), delete = deleteWord && !cancelWord)
        }

        // запись на приём
        if (has(s, "запиши", "запиш", "запис", "записать", "назнач", "book", "appointment", "schedule")) {
            val service = extractService(s)
            val person = extractPerson(s, afterCreate = true)
            val dt = NoaDateTime.parse(original, now)
            return NoaIntent.CreateAppointment(person, dt?.dateTime, dt?.hadTime ?: false, service, confirm = false)
        }
        // позвонить
        if (has(s, "позвони", "набери", "подзвони", "зателефонуй", "call", "dial")) {
            return NoaIntent.Call(extractPerson(s))
        }
        // написать / отправить
        if (has(s, "напиши", "сообщение", "отправь", "відправ", "надішли", "скинь", "напиши смс", "смс", "sms", "message", "напис", "повідомл", "whatsapp", "вотсап", "телеграм", "telegram")) {
            return message(s, original)
        }
        // «скажи Илье, что я опаздываю», «передай маме, что задержусь» — сообщение, если назван кому и что.
        if (has(s, "скажи", "передай", "передайте", "скажіть") &&
            messageBody(original)?.let { extractPerson(" " + it.first.lowercase() + " ").isNotBlank() } == true) return message(s, original)
        // открыть раздел приложения
        screenSection(s)?.let { return NoaIntent.OpenScreen(it) }
        // «открой ютуб», «відкрий калькулятор» — приложение телефона по привычному названию
        if (has(s, "открой", "відкрий", "open")) {
            val rest = stripWords(s, OPEN_WORDS)
            if (PhoneActions.isAppName(rest)) return NoaIntent.LaunchApp(rest)
        }
        // открыть карточку
        if (has(s, "открой", "покажи карточку", "покажи контакт", "відкрий", "open", "зайди", "перейди", "покажи профил", "покажи профіл", "покажи картку")) {
            return NoaIntent.Open(extractPerson(s))
        }
        // «найди мне плейлист хиты 90-х» — это музыка, а не поиск в книжке
        if (has(s, "найди", "найти", "знайди", "find", "поищи", "пошукай") && has(s, "плейлист", "плейліст", "playlist", "песн", "пісн", "трек", "музык", "музик", "альбом", "видео", "відео", "клип", "кліп", "song", "music", "video")) {
            return playIntent(s, MUSIC_APP.find(s)?.groupValues?.get(1))
        }
        // найти
        if (has(s, "найди", "найти", "поиск", "знайди", "пошук", "find", "search")) {
            return NoaIntent.Find(stripCommandWords(s).trim())
        }
        // «Включи Rammstein», «постав Океан Ельзи» — ничего другого не подошло: значит, музыка.
        if (has(s, "включи", "поставь", "увімкни", "ввімкни", "постав", "play", "сыграй", "проиграй") && !has(s, "будильник", "таймер", "фонар", "ліхтар")) {
            val p = playIntent(s, null)
            if (p.query.isNotBlank()) return p.copy(artist = true)
        }
        return NoaIntent.Unknown(original)
    }

    private fun message(s: String, original: String): NoaIntent.Message {
        val head = messageBody(original)?.first?.let { " " + it.lowercase() + " " } ?: s
        val channel = when {
            has(head, "whatsapp", "вотсап", "ватсап", "вацап", "вотс", "ватс") -> NoaIntent.Channel.WHATSAPP
            has(head, "телеграм", "telegram", "тг") -> NoaIntent.Channel.TELEGRAM
            has(head, "смс", "sms") -> NoaIntent.Channel.SMS
            else -> null
        }
        // Мессенджер бывает назван в самом конце: «напиши Илье что опаздываю в телеграм».
        val end = if (channel == null) CHANNEL_END.find(original.lowercase()) else null
        val endChannel = when (end?.groupValues?.get(1)) {
            null -> null
            "телеграм", "telegram", "тг" -> NoaIntent.Channel.TELEGRAM
            "смс", "sms", "эсэмэс" -> NoaIntent.Channel.SMS
            else -> NoaIntent.Channel.WHATSAPP
        }
        val about = isAboutAppointment(s)
        val quoted = extractQuoted(original)
        // «напиши Илье, что буду через 10 минут» / «напиши Илье: опаздываю» — после «что/:» идёт текст сообщения.
        val body = if (about || quoted != null) null else messageBody(if (end != null) original.substring(0, end.range.first) else original)
        val text = if (about) null else quoted ?: body?.second
        val who = body?.let { extractPerson(" " + it.first.lowercase() + " ") } ?: extractPerson(s)
        return NoaIntent.Message(who, channel ?: endChannel ?: NoaIntent.Channel.WHATSAPP, text, aboutAppointment = about)
    }

    private val CHANNEL_END = Regex("\\s(?:в|у|по|через)\\s+(вотсап|ватсап|вацап|whatsapp|телеграм|telegram|тг|смс|sms|эсэмэс)\\s*$")

    /** Глаголы, с которых начинается сообщение: дальше после «что/:» — его текст, а не команды. */
    private val MESSAGE_VERBS = setOf("напиши", "напишите", "отправь", "отправьте", "відправ", "надішли", "напиши-ка", "скажи", "передай",
        "скажіть", "передайте", "write", "send", "text")

    private val OPEN_WORDS = setOf("открой", "відкрий", "open", "запусти", "запустить", "запустити", "launch", "start", "включи", "увімкни",
        "приложение", "приложения", "застосунок", "додаток", "app", "мне", "мені", "пожалуйста", "будь", "ласка", "ну", "а")

    private fun stripWords(s: String, words: Set<String>) =
        s.trim().split(" ").filter { it.isNotBlank() && it !in words }.joinToString(" ")

    /** Будильник, таймер, фонарик, настройки, поиск в Google, запуск приложения. */
    private fun phoneAction(s: String, original: String, now: LocalDateTime): NoaIntent? {
        if (has(s, "будильник", "разбуди", "розбуди", "alarm", "wake me")) {
            val dt = NoaDateTime.parse(original, now)?.takeIf { it.hadTime } ?: return NoaIntent.LaunchApp("будильник")
            return NoaIntent.Alarm(dt.dateTime.hour, dt.dateTime.minute)
        }
        if (has(s, "таймер", "засеки", "засічи", "timer")) {
            val n = Regex("(\\d+)").find(s)?.groupValues?.get(1)?.toIntOrNull() ?: return NoaIntent.LaunchApp("часы")
            val sec = when {
                has(s, "сек", "sec") -> n
                has(s, "час", "годин", "hour") && !has(s, "минут", "хвилин", "min") -> n * 3600
                else -> n * 60
            }
            return NoaIntent.Timer(sec)
        }
        if (has(s, "фонарик", "фонарь", "ліхтарик", "ліхтар", "flashlight", "torch", "вспышк", "спалах")) {
            return NoaIntent.Flashlight(!has(s, "выключ", "вимкн", "погаси", "отключ", "off", "turn off"))
        }
        play(s)?.let { return it }
        if (has(s, "вайфай", "вай-фай", "wi-fi", "wifi", "вайфаю")) return NoaIntent.PhoneSettings("wifi")
        if (has(s, "блютуз", "блютус", "bluetooth")) return NoaIntent.PhoneSettings("bluetooth")
        if (has(s, "яркост", "яскрав", "brightness")) return NoaIntent.PhoneSettings("display")
        if (has(s, "настройки телефона", "налаштування телефону", "phone settings")) return NoaIntent.PhoneSettings(null)
        val search = Regex("(?:загугли|погугли|google|поищи в (?:гугле|гугл|интернете|сети)|найди в (?:гугле|гугл|интернете|сети)|" +
            "пошукай в (?:гуглі|гугл|інтернеті)|знайди в (?:гуглі|гугл|інтернеті)|search for)\\s+(.+)").find(s.trim())
        if (search != null) return NoaIntent.WebSearch(search.groupValues[1].trim())
        if (has(s, "запусти", "запустить", "запустити", "launch", "открой приложение", "відкрий застосунок", "відкрий додаток", "open app")) {
            val name = stripWords(s, OPEN_WORDS)
            if (name.isNotBlank()) return NoaIntent.LaunchApp(name)
        }
        return null
    }

    private val MUSIC_APP = Regex("\\s(?:в|у|на|in|on|через)\\s+((?:ютуб|ютюб|ютубе|youtube|yt)(?:\\s+(?:мьюзик|мюзик|музик|music|мьюзік|мюзік|музыке|музиці|м\\S+))?|спотифа\\S*|спотіфа\\S*|spotify|музык\\S*|музик\\S*|deezer|дизер|apple music)\\s*$")

    /** «Включи плейлист», «включи музыку в спотифай», «поставь Imagine Dragons», «увімкни мій плейлист Ранок». */
    private fun play(s: String): NoaIntent.Play? {
        if (!has(s, "включи", "включить", "увімкни", "ввімкни", "постав", "запусти", "play", "проиграй", "сыграй", "грай")) return null
        val music = has(s, "музык", "музик", "песн", "пісн", "трек", "плейлист", "плейліст", "playlist", "song", "music", "альбом", "album", "радио", "радіо",
            "видео", "відео", "клип", "кліп", "ролик", "video", "канал", "channel")
        val app = MUSIC_APP.find(s)?.groupValues?.get(1)?.takeIf { PhoneActions.isAppName(it) || it.startsWith("спотиф") || it.startsWith("спотіф") || it == "spotify" }
        if (!music && app == null) return null
        if (has(s, "будильник", "таймер", "фонар", "ліхтар")) return null
        return playIntent(s, app)
    }

    private fun playIntent(sIn: String, app: String?): NoaIntent.Play {
        val s0 = sIn
        val playlist = has(s0, "плейлист", "плейліст", "playlist", "альбом", "album")
        // «любую песню Rammstein», «что-нибудь группы Би-2» — это исполнитель, а не название песни.
        val artist = !playlist && has(s0, "любую", "любой", "любу", "будь-яку", "якусь", "какую-нибудь", "что-нибудь", "щось", "any", "something",
            "групп", "гурт", "исполнител", "виконав", "band", "artist", "песни", "пісні", "songs", "треки")
        // «включи любое видео с канала ределион» — видео этого канала.
        val ch = CHANNEL.find(s0)
        val channel = ch?.groupValues?.get(1)?.trim().orEmpty()
        val s = if (ch != null) s0.replace(ch.value, " ") else s0
        var q = (if (app != null) MUSIC_APP.replace(s, " ") else s).trim()
        q = q.split(" ").filter { it.isNotBlank() && it !in PLAY_WORDS }.joinToString(" ")
        val shuffle = has(s0, "в случайном порядке", "случайн", "випадков", "перемешай", "перемішай", "вперемешку", "shuffle", "рандом")
        val video = channel.isNotBlank() || has(s0, "видео", "відео", "клип", "кліп", "ролик", "video", "clip") ||
            (app != null && Regex("^(ютуб|ютюб|youtube)$").matches(app.trim()))
        q = q.split(" ").filter { it !in SHUFFLE_WORDS }.joinToString(" ")
        return NoaIntent.Play(q, app, playlist, artist && q.isNotBlank(), shuffle, video, channel)
    }

    /** «с канала X», «на канале X», «від каналу X», «from the X channel» — название канала до конца фразы. */
    private val CHANNEL = Regex("(?:\\s)(?:с|со|из|от|на|від|з|із|from)?\\s*(?:канала|каналу|каналі|канале|канал|channel)\\s+(.+?)\\s*$")

    private val SHUFFLE_WORDS = setOf("в", "у", "случайном", "випадковому", "порядке", "порядку", "перемешай", "перемішай", "вперемешку", "shuffle",
        "рандом", "рандомно", "случайно", "випадково", "видео", "відео", "клип", "кліп", "ролик", "видос", "video", "clip", "найди", "найти", "знайди",
        "поищи", "пошукай", "find", "воспроизведение", "відтворення", "плей", "на", "with", "and", "it")

    /** Пауза, дальше, перемешать, громче, что играет. Короткие фразы — чтобы «дальше» в другом смысле не путать. */
    private fun media(s: String): NoaIntent.Media? {
        val words = s.trim().split(" ").size
        val music = has(s, "музык", "музик", "трек", "песн", "пісн", "плеер", "плеєр", "видео", "відео", "воспроизвед", "відтворен", "music", "song", "track")
        val c = when {
            has(s, "сними с паузы", "зніми з паузи", "сними паузу", "зніми паузу") -> NoaMedia.Control.RESUME
            has(s, "что играет", "что сейчас играет", "що грає", "що зараз грає", "что за песня", "що за пісня", "what's playing", "what is playing", "какая песня", "яка пісня") -> NoaMedia.Control.WHAT
            has(s, "пауз", "pause", "останови", "зупини", "призупини", "стоп музык", "выключи музык", "вимкни музик", "stop music", "замолчи") && !has(s, "будильник", "таймер") -> NoaMedia.Control.PAUSE
            words <= 2 && has(s, "стоп", "stop", "хватит", "досить") -> NoaMedia.Control.PAUSE
            has(s, "по порядку", "без перемешив", "без перемішув", "shuffle off", "выключи перемеш", "вимкни перемі") -> NoaMedia.Control.SHUFFLE_OFF
            (has(s, "в случайном порядке", "перемешай", "перемішай", "вперемешку", "shuffle", "випадковому порядку", "рандом") && (words <= 7 || music)) -> NoaMedia.Control.SHUFFLE_ON
            has(s, "на повтор", "повторяй", "repeat") -> NoaMedia.Control.REPEAT
            has(s, "следующ", "наступн", "next", "пропусти", "skip", "переключи") && (words <= 4 || music) -> NoaMedia.Control.NEXT
            // «дальше», «давай дальше» — только коротко: «поехали дальше» не про музыку
            words <= 2 && has(s, "дальше", "далі", "далее") -> NoaMedia.Control.NEXT
            has(s, "предыдущ", "попередн", "previous", "прошл трек", "верни трек", "предыдущую") && (words <= 4 || music) -> NoaMedia.Control.PREV
            has(s, "продолжи", "продолж", "продовж", "возобнови", "сними с паузы", "зніми з паузи", "resume", "включи воспроизвед", "увімкни відтвор", "відтвори") && (words <= 5 || music) -> NoaMedia.Control.RESUME
            has(s, "громче", "гучніше", "louder", "volume up", "погромче", "прибавь звук", "додай звук") -> NoaMedia.Control.LOUDER
            has(s, "тише", "тихіше", "quieter", "volume down", "потише", "убавь звук", "зменш звук") -> NoaMedia.Control.QUIETER
            else -> null
        } ?: return null
        // «Найди плейлист … в случайном порядке» — это включение, а не управление.
        if (c == NoaMedia.Control.SHUFFLE_ON && has(s, "найди", "знайди", "включи плейлист", "плейлист", "плейліст") && words > 3) return null
        // «Включи музыку / Rammstein в случайном порядке» — тоже включение (с перемешиванием), а не просто управление.
        if (c == NoaMedia.Control.SHUFFLE_ON && has(s, "включи", "увімкни", "ввімкни", "поставь", "постав", "запусти", "play") &&
            (has(s, "музык", "музик", "песн", "пісн", "трек", "альбом", "радио", "радіо", "music", "song") || playIntent(s, null).query.isNotBlank())) return null
        return NoaIntent.Media(c)
    }

    /** «Прочитай сообщения», «что пишет Илья», «ответь Илье: буду в пять», «дождись ответа и прочитай». */
    private fun messages(s: String, original: String): NoaIntent? {
        val reply = Regex("(?:^|\\s)(?:ответь|відповідай|відповісти|ответить|reply(?: to)?)\\s+(.+)$").find(original.lowercase().trim())
        if (reply != null) {
            val rest = reply.groupValues[1]
            // Текст — после двоеточия / «что» / «що»; до — кому.
            val split = Regex("^(.*?)(?::|\\sчто\\s|\\sщо\\s|\\sthat\\s)(.+)$").find(rest)
            val (who, text) = if (split != null) split.groupValues[1].trim() to split.groupValues[2].trim()
                else rest.split(" ").let { w ->
                    // «ответь ему ок» / «ответь Илье буду через 5 минут»: первое слово — кому, если это местоимение/имя.
                    if (w.size > 1) w[0] to w.drop(1).joinToString(" ") else "" to rest
                }
            return NoaIntent.Reply(extractPerson(" $who "), text.trim('"', '«', '»', ' '))
        }
        val wait = has(s, "дождись ответ", "дочекайся відповід", "когда ответит", "коли відповість", "прочитай ответ", "прочитай відповідь",
            "читай ответ", "when he replies", "when she replies", "read the reply", "как ответит", "як відповість")
        val read = wait || has(s, "прочитай сообщ", "прочитай повідомл", "прочитай смс", "новые сообщения", "нові повідомлення", "что пишет", "що пише",
            "что написал", "що написав", "что написала", "що написала", "кто писал", "хто писав", "read messages", "read my messages", "есть сообщения", "є повідомлення")
        if (!read) return null
        return NoaIntent.ReadMessages(extractPerson(s), wait)
    }

    /** Куда ехать — слова фразы без команды, навигатора и служебных; цифры адреса сохраняются. */
    private fun destination(original: String): String {
        val words = original.lowercase().replace(Regex("[,.!?]"), " ").split(" ").filter { it.isNotBlank() }
        val stop = setOf("проложи", "проклади", "прокласти", "построй", "побудуй", "перестрой", "перебудуй", "маршрут", "маршрута", "мне", "мені", "пожалуйста",
            "будь", "ласка", "через", "в", "у", "во", "на", "до", "к", "ко", "по", "waze", "вейз", "вэйз", "вейс", "уэйз", "google", "гугл", "гугле", "карты", "карти",
            "картах", "карте", "мапи", "мапах", "maps", "навигатор", "навигаторе", "навігатор", "навігаторі", "яндекс", "yandex", "organic", "route", "to",
            "directions", "navigate", "поехали", "поїхали", "едем", "їдемо", "доехать", "доїхати", "как", "як", "добраться", "дістатися", "путь", "шлях",
            "дорогу", "веди", "отвези", "перепроверь", "перевір", "другую", "другу", "іншу", "точку", "новый", "новий", "новую", "нову", "поменяй", "зміни",
            "измени", "теперь", "тепер", "лучше", "краще", "давай", "и", "і", "а", "запусти", "включи", "увімкни", "навигацию", "навігацію", "the", "me", "please", "with")
        return words.filter { it !in stop }.joinToString(" ").trim()
    }

    private val PLAY_WORDS = setOf("включи", "включить", "увімкни", "ввімкни", "поставь", "постав", "запусти", "play", "проиграй", "сыграй", "грай",
        "музыку", "музика", "музику", "музыка", "песню", "пісню", "песни", "пісні", "трек", "треки", "плейлист", "плейліст", "playlist", "some", "music", "song",
        "мой", "мій", "мою", "мої", "мои", "my", "the", "a", "какую-нибудь", "якусь", "что-нибудь", "щось", "мне", "мені", "пожалуйста", "будь", "ласка",
        "альбом", "album", "радио", "радіо", "любимый", "улюблений", "там", "і", "и", "а",
        "любую", "любой", "любые", "любое", "любі", "будь-яке", "якесь", "любу", "будь-яку", "будь-яку", "якусь", "якийсь", "какую-нибудь", "какой-нибудь", "any", "something", "some",
        "группы", "группу", "группа", "гурту", "гурт", "групи", "исполнителя", "виконавця", "band", "artist", "by", "of", "от", "від",
        "песен", "пісень", "songs", "треков", "треків", "композицию", "композицію", "из", "з", "мне", "нам", "немного", "трохи", "кстати", "чтонибудь")

    private val NOTES_TARGET = Regex("\\s(?:в|у|во|to|into)\\s+(?:блокнот|заметки|нотатки|нотатник|notes|notepad|keep|кип|samsung notes)(?:\\s+(?:телефона|телефону|phone|на телефоне|на телефоні))?")

    /**
     * «Перенеси заметки об Илье в блокнот», «скопируй номер Анны и вставь в гугл», «скинь адрес Оли в телеграм».
     * Нужны: что передать (данные человека), глагол передачи и куда.
     */
    fun shareData(original: String): NoaIntent.ShareData? {
        val s0 = " " + original.lowercase().replace(Regex("\\s+"), " ") + " "
        if (Regex("[«\"]").containsMatchIn(s0) || has(s0, "добавь", "додай", "add note", "добавить")) return null
        val verb = has(s0, "перенес", "скопир", "скопію", "копир", "копію", "вставь", "встав", "всей", "скинь", "поделись", "поділись",
            "передай", "сохрани", "збережи", "запиши в блокнот", "запиши в нотатки", "copy", "share", "paste", "save to",
            "загугли", "погугли", "поищи", "пошукай", "отправь в", "надішли в", "відправ в", "кинь", "закинь", "export", "экспорт", "експорт")
        if (!verb) return null
        val target = when {
            NOTES_TARGET.containsMatchIn(s0) || has(s0, "блокнот", "нотатник", "notepad", "samsung notes", "google keep") -> "notes"
            has(s0, "гугл", "google", "интернет", "інтернет", "загугли", "погугли", "поищи", "пошукай") -> "google"
            has(s0, "буфер", "clipboard") -> "clipboard"
            else -> Regex("\\s(?:в|у|to|into)\\s+(\\S+(?:\\s\\S+)?)\\s").findAll(s0).map { it.groupValues[1] }
                .mapNotNull { w -> listOf(w, w.substringBefore(' ')).firstOrNull { it in PhoneActions.ALIAS_NAMES } }
                .firstOrNull()?.let { "app:$it" }
                ?: when {
                    has(s0, "скопир", "скопію", "копир", "копію", "copy") -> "clipboard"
                    has(s0, "поделись", "поділись", "share", "скинь", "кинь", "передай", "export", "экспорт", "експорт") -> "share"
                    else -> null
                }
        } ?: return null
        // Слова «куда» убираем, чтобы «заметки/телефон» в них не спутать с данными и не принять за имя.
        val s = NOTES_TARGET.replace(s0, " ")
        val data = when {
            has(s, "замет", "нотат", "хроник", "хронік", "notes", "записи о", "записи про", "записки") -> NoaIntent.Data.NOTES
            has(s, "номер", "телефон", "phone", "number") -> NoaIntent.Data.PHONE
            has(s, "адрес", "address") -> NoaIntent.Data.ADDRESS
            has(s, "почт", "пошт", "email", "имейл", "мейл", "e-mail") -> NoaIntent.Data.EMAIL
            has(s, "день рожд", "день народж", "birthday", "др ") -> NoaIntent.Data.BIRTHDAY
            has(s, "данн", "дані", "даних", "карточк", "картк", "информац", "інформац", "контакт", "все о", "всё о", "все про", "info", "профил", "профіл") -> NoaIntent.Data.CARD
            else -> return null
        }
        return NoaIntent.ShareData(extractPerson(s), data, target)
    }

    // ---- извлечение человека ----

    /** Слова-команды и служебные, которые не являются именем. */
    private val STOP = setOf(
        "запиши","запис","записать","назначь","назначить","book","appointment","schedule","на","в","во","о","к",
        "позвони","набери","подзвони","зателефонуй","call","dial","напиши","напис","отправь","сообщение","повідомлення",
        "смс","sms","message","whatsapp","вотсап","ватсап","телеграм","telegram","тг","заметку","заметка","нотатку",
        "note","открой","відкрий","open","найди","найти","поиск","знайди","пошук","find","search","сеанс","сеансе",
        "консультацию","консультацію","консультац","консультації","сегодня","завтра","послезавтра","сьогодні","післязавтра","today","tomorrow",
        "клиента","клієнта","client","числа","час","часов","года","the","to","at","for","про","что","о","том","about",
        "добавь","добав","додай","запиши","хронику","хроніку","тату","tattoo","маникюр","манікюр","стрижк","стрижку","маникюра",
        "сделай","зроби","make","запишіть","запишите","записати","запиши","запишіть-но","отправь","надішли","надішлі","send","тату-сеанс","о","от",
        "контакт","контакта","контакты","контакт","ну","а","же","ещё","еще","пожалуйста","будь","ласка",
        "рассылку","рассылка","рассылки","розсилку","календарь","календар","карту","карта","настройки","налаштування",
        "меню","раздел","вкладку","услуги","послуги","людей","людини",
        "зайди","перейди","профиль","профіль","профиле","профілі","профиля","профілю","карточку","картку","карточке","картці","страницу","сторінку",
        "нажми","натисни","тапни","клацни","контактах","контактів","контакті","і","и","й","та","потом","потім","затем","then","and",
        "маршрут","проложи","проклади","прокласти","построй","путь","шлях","до","дорогу","доехать","добраться","доїхати","дістатися","навигацию","навігацію",
        "route","directions","navigate","отвези","веди","дом","дому","додому","домой","работу","роботу","работе","роботі","home","work","офис","офіс",
        "инстаграм","инсту","інстаграм","інсту","instagram","insta","фейсбук","facebook","вайбер","viber","сайт","website","почту","пошту","email","имейл","мейл",
        "вк","вконтакте","vk","в","у","его","её","ее","її","його","ей","їй","ему","йому","мне","мені","покажи","открой","відкрий",
        "избранное","избранного","обране","обраного","добавь","додай","убери","прибери","удали","видали",
        "когда","коли","день","рождения","народження","какой","який","яка","номер","телефон","телефона","адрес","адреса","где","де","живет","живе","живёт",
        "работает","працює","сколько","скільки","лет","років","что","що","я","знаю","расскажи","розкажи","о","об","про","кто","хто","такой","такая","такий","така",
        "у","него","неё","нього","неї","мой","мій","моя",
        "об","этом","это","этим","це","цим","подтверждение","подтверждения","підтвердження","также","тоже","сразу","еще","ещё","також","теж","одразу","відразу","заодно",
        "запись","записи","запису","записів","записью","встречу","встречи","зустріч","зустрічі","прийом","прийому","приём","прием","приёма","приема",
        "сеанса","визит","візит","appointment","booking","meeting","удалить","видалити","отмени","отменить","скасуй","скасувати","відміни",
        "сотри","зітри","delete","remove","cancel","перенеси","перенести","перенос","передвинь","пересунь","зсунь","посунь","reschedule","move",
        "с","з","со","із","from","ближайшую","найближчу","next",
        "карте","карті","картах","мапі","мапу","мапа","мапах","через","waze","вейз","вэйз","вейс","уэйз","google","гугл","гугле","maps","мапс",
        "мне","мені","нему","ньому","маршрутом","маршрута","на","к","по",
        "данные","данных","дані","даних","заметок","заметки","заметку","нотатки","нотаток","записки","хронику","хроніку","их","їх","номер","номера",
        "телефона","телефону","блокнот","нотатник","notes","notepad","keep","кип","гугл","гугле","гуглі","google","интернет","интернете","інтернет",
        "вставь","встав","вставити","вставить","всей","скопируй","скопіюй","скопировать","скопіювати","copy","paste","перенеси","перенести",
        "буфер","буфера","обмена","обміну","clipboard","поделись","поділись","share","передай","скинь","кинь","закинь","сохрани","збережи","save",
        "адрес","адреса","адресу","почту","пошту","email","информацию","інформацію","карточку","картку","всё","все","данными","export","экспорт","експорт",
        "загугли","погугли","поищи","пошукай","samsung","ее","её","his","her","its",
        "возьми","візьми","выбери","обери","вибери","бери","take","select","вацап","вотс","ватс","скинь","відправ","відправити","надішли","надіслати","отправить","ним","ему","йому",
        // навигация и сообщения: глаголы и служебные слова, которые иначе попадают в имя
        "поехали","поїхали","едем","поедем","їдемо","поїдемо","как","як","меня","мене","яндекс","yandex","навигатор","навигаторе","навігатор","навігаторі",
        "мапи","мапах","карты","карти","картам","из","від","для","прочитай","прочитати","прочти","сообщения","сообщений","повідомлень","новые","новых","нові",
        "есть","є","пишет","пише","написал","написала","написав","написали","ответ","ответа","відповідь","відповіді","дождись","дочекайся",
        "read","my","messages","pm","am","скажи","скажіть","передайте",
    )

    /** «Открой/зайди/покажи …» раздел приложения. */
    private fun screenSection(s: String): NoaIntent.Section? {
        if (!has(s, "открой", "зайди", "покажи", "перейди", "відкрий", "зайди в", "open", "go to")) return null
        return when {
            has(s, "рассылк", "розсилк", "broadcast") -> NoaIntent.Section.BROADCAST
            has(s, "календар", "calendar") -> NoaIntent.Section.CALENDAR
            (has(s, "карт") && !has(s, "карточ")) || has(s, "map") -> NoaIntent.Section.MAP
            has(s, "настройк", "налаштуванн", "settings") -> NoaIntent.Section.SETTINGS
            has(s, "услуг", "послуг", "service") -> NoaIntent.Section.SERVICES
            has(s, "людей", "список", "контакты", "people", "home", "главн", "головн") -> NoaIntent.Section.PEOPLE
            else -> null
        }
    }
    private val MONTH_WORDS = listOf("январ","феврал","март","апрел","мая","июн","июл","август","сентябр","октябр","ноябр","декабр",
        "січн","лют","берез","квітн","травн","черв","лип","серп","вересн","жовтн","листопад","грудн",
        "janu","febr","march","april","june","july","august","septemb","octob","novemb","decemb")

    private fun extractPerson(sRaw: String, afterCreate: Boolean = false): String {
        // Текст в кавычках (заметка/сообщение) — не имя.
        val s = sRaw.replace(Regex("[«\"'][^«»\"']*[»\"']"), " ")
        val tokens = s.trim().split(" ").filter { it.isNotBlank() }
        val words = tokens.filter { w ->
            val c = w.trim('-', '«', '»', '"').lowercase()
            c.isNotEmpty() && c !in STOP && !c.all { ch -> ch.isDigit() } &&
                !Regex("\\d").containsMatchIn(c) && MONTH_WORDS.none { c.startsWith(it) } &&
                !isWeekdayWord(c) && !NoaDateTime.isHourWord(c) && c.length > 1
        }
        return words.joinToString(" ").trim()
    }

    private fun isWeekdayWord(c: String) = listOf("понедельник","вторник","сред","четверг","пятниц","суббот","воскресень",
        "понеділок","вівторок","серед","четвер","п'ятниц","пʼятниц","субот","неділ",
        "monday","tuesday","wednesday","thursday","friday","saturday","sunday").any { c.startsWith(it) }

    /** Нормализованный корень услуги для поиска среди шаблонов («консультац», «тату»…). */
    private fun extractService(s: String): String? = when {
        s.contains("консультац") -> "консультац"
        has(s, "тату", "tattoo") -> "тату"
        has(s, "маникюр", "манікюр", "manicure") -> "маникюр"
        has(s, "стрижк", "haircut") -> "стрижк"
        else -> null
    }

    /** «Об этом», «подтверждение», «про запись» — сообщение о только что созданной записи. */
    fun isAboutAppointment(text: String): Boolean {
        val s = " " + text.lowercase() + " "
        return has(s, "об этом", "о этом", "про это", "про неё", "подтвержд", "про запис", "о записи", "про це", "про нього", "підтвердж",
            "нагадуван", "напоминан", "о встрече", "про зустріч", "about it", "confirmation")
    }

    /** Тип контакта, если он назван во фразе. Telegram/WhatsApp — это «написать», их здесь нет. */
    private fun contactType(s: String): com.kartoteka.app.data.ContactType? = when {
        has(s, "инстаграм", "инсту", "інстаграм", "інсту", "instagram", "insta") -> com.kartoteka.app.data.ContactType.INSTAGRAM
        has(s, "фейсбук", "facebook") -> com.kartoteka.app.data.ContactType.FACEBOOK
        has(s, "вайбер", "viber") -> com.kartoteka.app.data.ContactType.VIBER
        has(s, "вконтакт", "vk ") -> com.kartoteka.app.data.ContactType.VK
        has(s, "сайт", "website") -> com.kartoteka.app.data.ContactType.WEBSITE
        has(s, "почт", "пошт", "email", "имейл", "мейл") -> com.kartoteka.app.data.ContactType.EMAIL
        else -> null
    }

    /** «Когда день рождения у Ани», «який номер Олега», «де живе мама», «що я знаю про Ілля». */
    private fun info(s: String, original: String): NoaIntent? {
        val q = has(s, "когда", "коли", "какой", "який", "яка", "какая", "сколько", "скільки", "где", "де ", "что я знаю", "що я знаю",
            "расскажи", "розкажи", "кто так", "хто так", "дай", "скажи", "what", "when", "where", "tell me")
        if (!q) return null
        val topic = when {
            has(s, "день рождения", "день народження", "др ", "лет", "років", "возраст", "вік", "birthday", "how old") -> NoaIntent.Topic.BIRTHDAY
            has(s, "номер", "телефон", "phone", "number") -> NoaIntent.Topic.PHONE
            has(s, "адрес", "где жив", "де жив", "где работ", "де працю", "address", "where") -> NoaIntent.Topic.ADDRESS
            has(s, "знаю", "расскажи", "розкажи", "кто так", "хто так", "tell me", "about") -> NoaIntent.Topic.SUMMARY
            else -> return null
        }
        return NoaIntent.PersonInfo(extractPerson(s), topic, original)
    }

    /**
     * Заметка: «добавь заметку Ане: купила телефон», «запиши в хронику Олега что вернул долг»,
     * «…и додай нотатку купила новий телефон» (человек — из прошлого шага).
     */
    private fun note(original: String, s: String): NoaIntent.AddNote {
        quoted(original)?.let { text ->
            return NoaIntent.AddNote(extractPerson(s), text)
        }
        val low = original.lowercase()
        val marker = Regex("(заметк|нотатк|хроник|хронік|note)\\S*").find(low) ?: return NoaIntent.AddNote(extractPerson(s), "")
        val head = original.substring(0, marker.range.first)
        var tail = original.substring(marker.range.last + 1).trim()
        var who = extractPerson(" " + head.lowercase() + " ")
        // «…Ане: текст» / «…Олега что текст» — имя до двоеточия или «что/що/про».
        val colon = tail.indexOf(':')
        val split = Regex("\\s(что|що|про|о том|about|that)\\s", RegexOption.IGNORE_CASE).find(" $tail ")
        when {
            colon >= 0 -> { who = extractPerson(" " + tail.substring(0, colon).lowercase() + " ").ifBlank { who }; tail = tail.substring(colon + 1).trim() }
            split != null -> {
                val cut = (split.range.first).coerceAtMost(tail.length)
                who = extractPerson(" " + tail.substring(0, cut).lowercase() + " ").ifBlank { who }
                tail = " $tail ".substring(split.range.last + 1).trim()
            }
        }
        return NoaIntent.AddNote(who, tail)
    }

    private fun quoted(text: String): String? = extractQuoted(text)

    /** «…Илье что буду через 10 минут» → («…Илье», «буду через 10 минут»). */
    private fun messageBody(original: String): Pair<String, String>? {
        val m = Regex("^(.*?)(?:\\s*:\\s*|\\s(?:что|що|текст|that|saying)\\s+)(.+)$", RegexOption.IGNORE_CASE).find(original.trim()) ?: return null
        val body = m.groupValues[2].trim()
        if (body.isBlank() || isAboutAppointment(" " + body.lowercase() + " ")) return null
        return m.groupValues[1] to body.replaceFirstChar { it.uppercase() }
    }

    private fun extractQuoted(text: String): String? =
        Regex("[«\"']([^«»\"']+)[»\"']").find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }

    private fun afterWord(text: String, vararg markers: String): String? {
        val low = text.lowercase()
        for (m in markers) {
            val i = low.indexOf(" $m ")
            if (i >= 0) return text.substring(i + m.length + 2).trim().takeIf { it.isNotEmpty() }
        }
        return null
    }

    private fun stripCommandWords(s: String): String =
        s.trim().split(" ").filter { it.lowercase() !in STOP && it.isNotBlank() }.joinToString(" ")
}
