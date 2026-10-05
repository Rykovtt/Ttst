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
    data class Route(val personQuery: String, val kind: com.kartoteka.app.data.PlaceKind?) : NoaIntent
    /** Что запланировано на день: записи и дни рождения. */
    data class Agenda(val date: java.time.LocalDate) : NoaIntent
    /** Вопрос о человеке: ответ из его карточки. */
    data class PersonInfo(val personQuery: String, val topic: Topic, val question: String) : NoaIntent
    data class Favorite(val personQuery: String, val on: Boolean) : NoaIntent
    /** Несколько действий подряд: «открой Аню и добавь заметку…». */
    data class Sequence(val steps: List<NoaIntent>) : NoaIntent
    data object Lock : NoaIntent
    data object Backup : NoaIntent
    data class Unknown(val heard: String) : NoaIntent

    enum class Channel { WHATSAPP, SMS, TELEGRAM }
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
            "(?=(?:добав|додай|додати|запиш|позвон|подзвон|набер|напиш|отправ|відправ|надішл|скинь|відкрий|открой|покажи|проклад|построй|прокласти|найди|знайди|нажми|натисн|зайди|перейди|напомн|нагадай|add|call|write|send|open|show|route)\\S*)",
        RegexOption.IGNORE_CASE,
    )
    val PRONOUNS = setOf("ей", "ему", "её", "ее", "его", "неё", "нее", "него", "ним", "ней", "їй", "йому", "її", "його", "нього", "неї", "ним", "нею", "him", "her", "them")

    fun parse(input: String, now: LocalDateTime = LocalDateTime.now()): NoaIntent {
        val parts = input.split(CHAIN).map { it.trim() }.filter { it.isNotBlank() }
        if (parts.size < 2) return parseOne(input, now)
        // Если какая-то часть — не команда («ну а открой…»), это не цепочка: разбираем фразу целиком.
        if (parts.any { parseOne(it, now) is NoaIntent.Unknown }) return parseOne(input, now)
        // Человек из предыдущего шага переходит в следующий, если там его нет («…и добавь ей заметку»).
        var person = ""
        val steps = parts.map { part ->
            val step = parseOne(part, now)
            val p = personOf(step).split(" ").filter { it.isNotBlank() && it.lowercase() !in PRONOUNS }.joinToString(" ")
            if (p.isNotBlank()) { person = p; withPerson(step, p) } else withPerson(step, person)
        }
        return NoaIntent.Sequence(steps)
    }

    fun personOf(i: NoaIntent): String = when (i) {
        is NoaIntent.CreateAppointment -> i.personQuery; is NoaIntent.Open -> i.personQuery
        is NoaIntent.Call -> i.personQuery; is NoaIntent.Message -> i.personQuery
        is NoaIntent.AddNote -> i.personQuery; is NoaIntent.OpenContact -> i.personQuery
        is NoaIntent.Route -> i.personQuery; is NoaIntent.PersonInfo -> i.personQuery
        is NoaIntent.Favorite -> i.personQuery; is NoaIntent.Select -> i.personQuery
        else -> ""
    }

    fun withPerson(i: NoaIntent, p: String): NoaIntent = when (i) {
        is NoaIntent.CreateAppointment -> i.copy(personQuery = p); is NoaIntent.Open -> i.copy(personQuery = p)
        is NoaIntent.Call -> i.copy(personQuery = p); is NoaIntent.Message -> i.copy(personQuery = p)
        is NoaIntent.AddNote -> i.copy(personQuery = p); is NoaIntent.OpenContact -> i.copy(personQuery = p)
        is NoaIntent.Route -> i.copy(personQuery = p); is NoaIntent.PersonInfo -> i.copy(personQuery = p)
        is NoaIntent.Favorite -> i.copy(personQuery = p); is NoaIntent.Select -> i.copy(personQuery = p)
        else -> i
    }

    fun parseOne(input: String, now: LocalDateTime = LocalDateTime.now()): NoaIntent {
        val original = input.trim()
        val s = " " + original.lowercase().replace(Regex("\\s+"), " ") + " "
        if (original.isBlank()) return NoaIntent.Unknown(original)

        // блокировка / копия — без человека
        if (has(s, "заблокируй", "заблокуй", "закрой приложение", "lock")) return NoaIntent.Lock
        if (has(s, "резервную копию", "бэкап", "бекап", "backup", "копію", "копию")) return NoaIntent.Backup

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
        if (has(s, "маршрут", "проклад", "прокласти", "построй путь", "построй дорогу", "дорогу до", "дорогу к", "как доехать", "как добраться",
                "як доїхати", "як дістатися", "шлях", "навигац", "навігац", "отвези", "веди к", "route", "directions", "navigate")) {
            val kind = when {
                has(s, "работ", "робот", "офис", "офіс", "work", "office") -> com.kartoteka.app.data.PlaceKind.WORK
                has(s, "дом", "додому", "дому", "home") -> com.kartoteka.app.data.PlaceKind.HOME
                else -> null
            }
            return NoaIntent.Route(extractPerson(s), kind)
        }

        // открыть контакт в другом приложении: «нажми на инстаграм Ани», «відкрий фейсбук Олега»
        contactType(s)?.let { type ->
            if (!has(s, "напиши", "напиш", "отправь", "надішли", "write", "send", "сообщение", "повідомлення")) {
                return NoaIntent.OpenContact(extractPerson(s), type)
            }
        }

        // запись на приём
        if (has(s, "запиши", "запис", "записать", "назнач", "book", "appointment", "schedule")) {
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
            val channel = when {
                has(s, "whatsapp", "вотсап", "ватсап", "вацап", "вотс", "ватс") -> NoaIntent.Channel.WHATSAPP
                has(s, "телеграм", "telegram", "тг") -> NoaIntent.Channel.TELEGRAM
                has(s, "смс", "sms") -> NoaIntent.Channel.SMS
                else -> NoaIntent.Channel.WHATSAPP
            }
            val about = isAboutAppointment(s)
            val text = if (about) null else extractQuoted(original)
            return NoaIntent.Message(extractPerson(s), channel, text, aboutAppointment = about)
        }
        // открыть раздел приложения
        screenSection(s)?.let { return NoaIntent.OpenScreen(it) }
        // открыть карточку
        if (has(s, "открой", "покажи карточку", "покажи контакт", "відкрий", "open", "зайди", "перейди", "покажи профил", "покажи профіл", "покажи картку")) {
            return NoaIntent.Open(extractPerson(s))
        }
        // найти
        if (has(s, "найди", "найти", "поиск", "знайди", "пошук", "find", "search")) {
            return NoaIntent.Find(stripCommandWords(s).trim())
        }
        return NoaIntent.Unknown(original)
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
        "сделай","зроби","make","отправь","надішли","надішлі","send","тату-сеанс","о","от",
        "контакт","контакта","контакты","контакт","ну","а","же","ещё","еще","пожалуйста","будь","ласка",
        "рассылку","рассылка","рассылки","розсилку","календарь","календар","карту","карта","настройки","налаштування",
        "меню","раздел","вкладку","услуги","послуги","людей","людини",
        "зайди","перейди","профиль","профіль","профиле","профілі","профиля","профілю","карточку","картку","карточке","картці","страницу","сторінку",
        "нажми","натисни","тапни","клацни","контактах","контактів","контакті","і","и","й","та","потом","потім","затем","then","and",
        "маршрут","проложи","проклади","прокласти","построй","путь","шлях","до","дорогу","доехать","добраться","доїхати","дістатися","навигацию","навігацію",
        "route","directions","navigate","отвези","веди","дом","дому","додому","работу","роботу","работе","роботі","home","work","офис","офіс",
        "инстаграм","инсту","інстаграм","інсту","instagram","insta","фейсбук","facebook","вайбер","viber","сайт","website","почту","пошту","email","имейл","мейл",
        "вк","вконтакте","vk","в","у","его","её","ее","її","його","ей","їй","ему","йому","мне","мені","покажи","открой","відкрий",
        "избранное","избранного","обране","обраного","добавь","додай","убери","прибери","удали","видали",
        "когда","коли","день","рождения","народження","какой","який","яка","номер","телефон","телефона","адрес","адреса","где","де","живет","живе","живёт",
        "работает","працює","сколько","скільки","лет","років","что","що","я","знаю","расскажи","розкажи","о","об","про","кто","хто","такой","такая","такий","така",
        "у","него","неё","нього","неї","мой","мій","моя",
        "об","этом","это","этим","це","цим","подтверждение","подтверждения","підтвердження","также","тоже","сразу","еще","ещё","також","теж","одразу","відразу","заодно",
        "возьми","візьми","выбери","обери","вибери","бери","take","select","вацап","вотс","ватс","скинь","відправ","відправити","надішли","надіслати","отправить","ним","ему","йому",
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
                !isWeekdayWord(c) && c.length > 1
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
