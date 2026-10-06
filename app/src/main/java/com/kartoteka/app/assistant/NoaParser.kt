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
    /** «Это не то», «ты неправильно поняла» — человек говорит, что прошлая команда понята неверно. */
    data object Wrong : NoaIntent
    /** «Закрой вейз»: свернуть и остановить приложение телефона по названию. */
    data class CloseApp(val name: String) : NoaIntent
    data class PhoneSettings(val what: String?) : NoaIntent
    data object Lock : NoaIntent
    data object Backup : NoaIntent
    /** Напоминание: [text] — о чём, [dateTime] — когда (null — время не названо); [hadTime] — названо ли точное время. */
    data class Remind(val text: String, val dateTime: LocalDateTime?, val hadTime: Boolean = true) : NoaIntent
    /** Время / число / день недели — отвечаем сами, без сети. [date] — о каком дне речь (null — сегодня). */
    data class Tool(val kind: ToolKind, val date: java.time.LocalDate? = null) : NoaIntent
    /** Калькулятор: [expression] — арифметическое выражение («2400*15/100», «sqrt(144)»), считает исполнитель. */
    data class Calc(val expression: String) : NoaIntent
    /** Перевод единиц: [from]/[to] — ключи (km, mi, kg, lb, c, f, l, gal, m, cm, ft, in, g, oz; валюты usd, eur, uah, rub, pln, gbp). */
    data class Convert(val value: Double, val from: String, val to: String) : NoaIntent
    /** Вопрос по картотеке: ближайшая запись, сколько записей, свободные окна, кто записан на время, последний контакт, дни рождения. */
    data class Crm(val kind: CrmKind, val personQuery: String = "", val date: java.time.LocalDate? = null,
                   val time: java.time.LocalTime? = null, val period: CrmPeriod = CrmPeriod.DAY) : NoaIntent
    /** «Отмена», «забудь», «не надо» — бросить ожидающий вопрос/подтверждение. */
    data object Dismiss : NoaIntent
    /** «Повтори», «ещё раз», «что ты сказала» — повторить последний ответ или действие. */
    data object Repeat : NoaIntent
    data class Unknown(val heard: String) : NoaIntent

    enum class Channel { WHATSAPP, SMS, TELEGRAM }
    enum class Data { NOTES, PHONE, ADDRESS, EMAIL, BIRTHDAY, CARD }
    enum class Topic { BIRTHDAY, PHONE, ADDRESS, SUMMARY }
    enum class Section { PEOPLE, CALENDAR, MAP, BROADCAST, SETTINGS, SERVICES }
    enum class ToolKind { TIME, DATE, WEEKDAY }
    enum class CrmKind { NEXT, COUNT, FREE, WHO_AT, LAST_CONTACT, BIRTHDAYS }
    /** Период вопроса: день ([Crm.date]), неделя (date — её понедельник), месяц (date — первое число; null — ближайшие 30 дней). */
    enum class CrmPeriod { DAY, WEEK, MONTH }
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
            "(?=(?:добав|додай|додати|запиш|позвон|подзвон|набер|напиш|отправ|відправ|надішл|скинь|відкрий|открой|покажи|проклад|пролож|построй|прокласти|маршрут|удал|видал|отмен|скасу|перенес|расскаж|розкаж|включ|увімкн|запуст|постав|play|закр|сверн|згорн|ответь|відповід|прочит|дождис|дочекай|пауз|громч|гучн|тише|тихіш|перемеш|перемі|скопир|скопію|перенес|найди|знайди|скаж|заблок|закин|зверн|нажми|натисн|зайди|перейди|напомн|нагадай|посчит|подсчит|порахуй|обчисл|вычисл|add|call|write|send|open|show|route)\\S*)",
        RegexOption.IGNORE_CASE,
    )
    val PRONOUNS = setOf("ей", "ему", "её", "ее", "его", "неё", "нее", "него", "ним", "ней", "їй", "йому", "її", "його", "нього", "неї", "ним", "нею", "нему", "ньому", "him", "her", "them")

    /**
     * Имена, на которые откликается ассистент (строчные): по умолчанию Ноа / Санта / Noa. Экран задаёт своё через [setAssistantName] —
     * имя в начале фразы («Санта, запиши Аню…») отбрасывается перед разбором.
     */
    private val DEFAULT_NAMES = setOf("ноа", "noa", "санта")
    @Volatile var assistantNames: Set<String> = DEFAULT_NAMES
        private set

    /** Имя, которое выбрал пользователь, добавляется к стандартным (слова имени — по отдельности). */
    fun setAssistantName(name: String?) {
        val own = name.orEmpty().lowercase().split(Regex("[^\\p{L}0-9]+")).filter { it.length >= 2 }
        assistantNames = DEFAULT_NAMES + own
    }

    /** Слова-обращения и «вода» в начале фразы: «слушай», «ну», «пожалуйста», «короче»… */
    private val LEAD_FILLERS = setOf("слушай", "послушай", "слушайте", "послушайте", "слухай", "послухай", "слухайте", "послухайте", "эй", "гей", "хей", "hey", "hi", "hello", "okay",
        "привет", "привіт", "здравствуй", "вітаю", "пожалуйста", "пожалуста", "будь", "ласка", "пліз", "плиз", "please", "ну", "короче", "значит", "вот", "ладно", "окей", "ок", "ok",
        "слышь", "типа", "блин", "эм", "эээ", "ээ", "ммм", "мм", "хм", "так", "давай", "а", "и", "і", "й", "тож", "то", "тоді", "добре", "ясно", "хорошо", "итак", "отже", "таким", "образом", "слушай-ка", "слухай-но")
    private val TAIL_FILLERS = setOf("пожалуйста", "пожалуста", "будь", "ласка", "пліз", "плиз", "please", "спасибо", "дякую", "благодарю")

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

    private fun bare(t: String) = t.trim(',', '.', '!', '?', ':', ';', '-', '—', '…', '«', '»', '"').lowercase()

    /** Обращение к ассистенту и слова-паразиты в начале и конце фразы отбрасываем: «Санта, ну позвони маме пожалуйста» → «позвони маме». */
    fun stripAddress(input: String, names: Collection<String> = assistantNames): String {
        val toks = input.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.toMutableList()
        fun isName(w: String) = names.any { n -> w == n || (n.length >= 4 && w.length >= 4 && lev(w, n) <= 1) }
        var changed = true
        while (changed && toks.size > 1) {
            changed = false
            val f = bare(toks[0])
            // «будь добр(а)» — вежливость в два слова
            if (f == "будь" && toks.size > 2 && bare(toks[1]).startsWith("добр")) { toks.removeAt(0); toks.removeAt(0); changed = true; continue }
            if (f.isEmpty() || isName(f) || f in LEAD_FILLERS) { toks.removeAt(0); changed = true }
        }
        // «пожалуйста», «спасибо» в конце — не слова команды
        while (toks.size > 1 && bare(toks.last()) in TAIL_FILLERS) toks.removeAt(toks.lastIndex)
        return toks.joinToString(" ")
    }

    /** Разговорные обороты → привычная форма: «напиши в ответ Ане …» = «ответь Ане …», «дай знать Маше что …» = «скажи Маше что …». */
    private val COLLOQUIAL = listOf(
        Regex("^(?:напиши|напишіть|напишите|скажи|отправь|відправ|кинь|скинь|сбрось|закинь)\\s+(?:(\\S+)\\s+)?(?:в ответ|у відповідь|в відповідь)\\s+", RegexOption.IGNORE_CASE) to "ответь $1 ",
        Regex("^(?:дай|дайте)\\s+(?:мне\\s+|мені\\s+)?(?:знать|знати)\\s+", RegexOption.IGNORE_CASE) to "скажи ",
    )

    /** Подготовка фразы: обращение и паразиты, слипшиеся слова («впятницу»), повторы подряд («запиши запиши Аню»). */
    fun prepare(input: String, names: Collection<String> = assistantNames): String {
        var s = stripAddress(input, names)
        s = NoaDateTime.fixMerged(s)
        s = COLLOQUIAL.fold(s) { acc, (re, to) -> re.replace(acc, to) }
        // Повторы подряд — заикание распознавания. В кавычках (текст сообщения) и числа словами не трогаем.
        if (!Regex("[«\"']").containsMatchIn(s)) {
            // Текст сообщения/заметки/напоминания диктуется как есть: «очень очень скучаю» не трогаем — чистим только начало фразы.
            val first = bare(s.substringBefore(' '))
            val freeText = first in MESSAGE_VERBS || first in REMIND_VERBS || first in setOf("ответь", "відповідай", "reply") ||
                Regex("заметк|нотатк|хроник|хронік").containsMatchIn(s.lowercase())
            val out = ArrayList<String>()
            for ((i, t) in s.split(" ").withIndex()) {
                val b = bare(t)
                if (!(freeText && i > 2) && out.isNotEmpty() && b.length >= 2 && b == bare(out.last()) && b.any { it.isLetter() } && !NoaDateTime.isDateWord(b)) continue
                out += t
            }
            s = out.joinToString(" ")
        }
        return s
    }

    /** Глаголы команд — для исправления ошибок распознавания в первом слове (запеши → запиши): сравниваем «скелеты» без гласных-близнецов. */
    private val KNOWN_VERBS = listOf("запиши", "позвони", "подзвони", "напиши", "отправь", "открой", "найди", "перенеси", "отмени", "включи", "поставь", "добавь", "покажи",
        "напомни", "посчитай", "прочитай", "проложи", "удали", "закрой", "запусти", "скажи", "передай", "ответь", "возьми", "выбери", "сыграй", "проиграй", "разбуди", "загугли",
        "відкрий", "знайди", "додай", "нагадай", "порахуй", "скасуй", "видали", "увімкни", "постав", "надішли", "зателефонуй", "запишіть", "розкажи", "проклади", "відправ", "обери", "вибери",
        "расскажи", "заблокируй", "сверни", "включить", "покажите", "переключи", "пропусти", "останови", "сними", "поищи")

    private fun skeleton(w: String): String = buildString {
        for (c in w.lowercase()) when (c) {
            'о' -> append('а'); 'е', 'ё', 'э', 'є' -> append('и'); 'ы', 'і', 'ї' -> append('и'); 'ь', 'ъ', '\'', 'ʼ', '’' -> {}
            else -> if (isEmpty() || last() != c) append(c)
        }
    }

    private val VERB_SKELETONS: Map<String, String> by lazy { KNOWN_VERBS.associateBy { skeleton(it) } }

    /** Первое слово — команда с «неправильной» гласной («запеши», «позвана», «открай») → верная форма. */
    private fun canonFirst(text: String): String {
        val i = text.indexOf(' ')
        val first = if (i < 0) text else text.substring(0, i)
        if (first.length < 5 || first.lowercase() in KNOWN_VERBS) return text
        val fixed = VERB_SKELETONS[skeleton(first)] ?: return text
        return fixed + (if (i < 0) "" else text.substring(i))
    }

    fun parse(inputRaw: String, now: LocalDateTime = LocalDateTime.now(), names: Collection<String> = assistantNames): NoaIntent {
        val input = prepare(inputRaw, names)
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
            val tail = device || cur is NoaIntent.GoHome || cur is NoaIntent.CloseApp || cur is NoaIntent.ReadMessages || cur is NoaIntent.Media ||
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
            // «выключи экран и закрой» — экран уже погашен, «закрой» после блокировки не нужно
            if (st is NoaIntent.GoHome && prev is NoaIntent.Lock) continue
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
        .replace(Regex("(?<=\\d),(?=\\d)"), "\u0001")   // «2,5» и «15,30» — запятая внутри числа остаётся
        .replace(Regex("[,!?;…]+"), " ")
        .replace(Regex("\\.(?!\\d)|(?<!\\d)\\."), " ")
        .replace("\u0001", ",")
        .replace(Regex("\\s+"), " ").trim()

    /** Разбор одной команды: правила + проверка «а точно ли фраза объяснена выбранным намерением» (иначе Unknown → модель). */
    fun parseOne(input: String, now: LocalDateTime = LocalDateTime.now()): NoaIntent {
        val r = parseOneCore(input, now)
        if (r is NoaIntent.Unknown) return r
        return if (suspicious(canonFirst(normalize(prepare(input))), r)) NoaIntent.Unknown(r.let { canonFirst(normalize(prepare(input))) }) else r
    }

    private fun parseOneCore(input: String, now: LocalDateTime): NoaIntent {
        val original = canonFirst(normalize(prepare(input)))
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

        // диалог («повтори», «отмена»), локальные инструменты, напоминания и вопросы по картотеке — раньше остальных правил
        dialogue(s)?.let { return it }
        tool(s, original, now)?.let { return it }
        remind(original, s, now)?.let { return it }
        crm(s, original, now)?.let { return it }

        // «вимкни телеграм», «закінчуй з інстаграмом закрий його», «більше не треба телеграм прибери» — закрыть приложение, а не сообщение
        closeBrandApp(s)?.let { return it }
        // «відкрий інстаграм», «ткни в вайбер», «хочу в телеграм» — только название приложения: запускаем его
        appOnly(s)?.let { return it }

        // блокировка / копия — без человека
        if (has(s, "заблокируй", "заблокуй", "закрой сейф", "закрий сейф", "lock") || LOCK_RE.containsMatchIn(s)) return NoaIntent.Lock
        // «закрой вейз», «закрий ютуб» — закрыть конкретное приложение (а не «закрой приложение» = свернуть текущее)
        Regex("^\\s*(?:закрой|закрий|close)\\s+(?:приложение\\s+|застосунок\\s+|додаток\\s+|app\\s+)?(.+?)\\s*$").find(s)?.groupValues?.get(1)?.let { rest ->
            if (rest !in setOf("приложение", "застосунок", "додаток", "app", "его", "її", "його", "её", "это", "це", "окно", "вікно", "сейф", "сейф.", "приложения", "всё", "все"))
                return NoaIntent.CloseApp(rest)
        }
        if (has(s, "закрой приложение", "закрий застосунок", "закрий додаток", "закрой его", "закрий його", "сверни", "згорни", "зверни", "на главный экран",
                "на головний екран", "домой экран", "выйди", "вийди", "go home", "close app", "закрой вотсап", "закрой телеграм", "закрий") ||
            s.trim() == "закрой") return NoaIntent.GoHome
        // «это не то», «ты не так поняла», «неправильно» — отметить прошлую команду как ошибочную
        if (has(s, "это не то", "це не те", "не то ты", "ты не так", "ти не так", "ты неправильно", "ти неправильно", "ты ошиб", "ти помил", "неправильно поняла", "неправильно зрозуміла",
                "не так поняла", "не так зрозуміла", "that's wrong", "wrong command") || s.trim() in setOf("неправильно", "не то", "не те", "ошибка", "помилка")) return NoaIntent.Wrong
        media(s)?.let { return it }
        messages(s, original)?.let { return it }
        if (has(s, "резервную копию", "бэкап", "бекап", "backup", "копію", "копию")) return NoaIntent.Backup

        // действия на телефоне: передать данные, будильник, таймер, фонарик, поиск, запуск приложений
        shareData(original)?.let { return it }
        phoneAction(s, original, now)?.let { return it }

        // «возьми контакт Илья Рыков» — выбрать человека для следующих команд
        if (has(s, "возьми", "візьми", "выбери", "обери", "вибери", "бери", "take", "select") && !has(s, "take me", "bring me") && !has(s, "запиш", "напиш", "позвон", "подзвон", "отправ", "надішл")) {
            return NoaIntent.Select(extractPerson(s))
        }

        // заметка в хронику — раньше записи на приём («запиши в хронику»)
        if (has(s, "заметк", "нотатк", "хроник", "хронік", "note")) return note(original, s)

        // план на день: «что у меня сегодня», «які записи на завтра»
        if (has(s, "что у меня", "что сегодня", "что завтра", "що в мене", "що у мене", "що сьогодні", "що завтра",
                "какие планы", "які плани", "план на", "расписан", "розклад", "кто записан", "хто записан", "кто сегодня", "хто сьогодні",
                "какие записи", "які записи", "записи на", "кто у меня", "хто в мене", "хто у мене", "agenda", "my schedule", "what do i have",
                "как у меня", "як у мене", "по записах", "по записям", "с записями", "з записами", "мой день", "мій день")
            && !has(s, "убери", "удали", "видали", "прибери", "сними", "зніми", "выкинь", "викинь", "перейди", "зайди", "отказыва", "відмовля", "scratch") && !SCHED_ADD.containsMatchIn(s)) {
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
        if ((has(s, "найди", "знайди", "find") && has(s, "ближайш", "найближч", "nearest", "closest") && has(s, "на карте", "на мапі", "on the map")) || has(s, "маршрут", "проклад", "пролож", "прокласти", "перестрой", "перебудуй", "поехали", "поїхали", "едем в", "едем до", "едем к", "едем на", "едем домой", "поедем", "поїдемо", "їдемо",
                "доехать до", "доїхати до", "навигатор", "навігатор", "построй путь", "построй дорогу", "дорогу до", "дорогу к", "как доехать", "как добраться",
                "як доїхати", "як дістатися", "take me to", "bring me to", "drive me to", "шлях", "навигац", "навігац", "отвези", "веди к", "route", "directions", "navigate")) {
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
        // «відкрий телеграм Дмитра», «ссылку Ильи на телеграм открой» — открыть контакт человека в мессенджере, а не писать
        if (has(s, "телеграм", "telegram") && has(s, "открой", "відкрий", "зайди", "перейди", "покажи", "open") &&
            !has(s, "напиш", "отправ", "відправ", "надішл", "скинь", "скаж")) {
            val who = extractPerson(s, extraStop = setOf("ссылку", "ссылка", "посилання", "посиланням", "страницу", "сторінку", "аккаунт", "акаунт"))
            if (who.isNotBlank()) return NoaIntent.OpenContact(who, com.kartoteka.app.data.ContactType.TELEGRAM)
        }
        contactType(s)?.let { type ->
            if (!has(s, "напиши", "напиш", "отправь", "надішли", "write", "send", "сообщение", "повідомлення")) {
                return NoaIntent.OpenContact(extractPerson(s), type)
            }
        }

        // перенести / отменить / удалить запись — раньше создания («удали запись Ильи» — не новая запись)
        val apptWord = has(s, "запис", "встреч", "зустріч", "сеанс", "прийом", "приём", "прием", "appointment", "booking", "meeting", "визит", "візит", "расписан", "розклад")
        if (has(s, "перенес", "перенест", "перенос", "передвин", "пересун", "зсунь", "посунь", "reschedule", "move")) {
            // «с пятницы на субботу» — новое время только то, что после «на»
            val to = Regex("\\s(?:с|со|з|із|from)\\s.+?\\s(?:на|to)\\s(.+)$").find(" " + original.lowercase())?.groupValues?.get(1)
            val dt = to?.let { NoaDateTime.parse(it, now, workHours = true) } ?: NoaDateTime.parse(original, now, workHours = true)
            return NoaIntent.MoveAppointment(extractPerson(s), dt?.dateTime, dt?.hadDate ?: false, dt?.hadTime ?: false)
        }
        // «scratch his booking», «відмовляється від запису», «запису вже нема» — отмена записи, если запись названа
        val cancelSoft = apptWord && has(s, "scratch", "drop", "відмовля", "отказыва", "нема", "немає", "нету", "не придет", "не придёт", "не прийде", "can't make", "cannot make")
        val cancelWord = has(s, "отмени", "отменить", "скасуй", "скасувати", "відміни", "cancel", "call off") || cancelSoft
        val deleteWord = has(s, "удали", "удалить", "видали", "видалити", "сотри", "зітри", "delete", "remove") ||
            (apptWord && has(s, "убери", "прибери", "сними", "зніми", "выкинь", "викинь"))
        // «отмена записи Ани» — существительное: тоже отмена записи
        val cancelNoun = has(s, "отмена", "отмену", "скасування", "відміна", "відміну")
        if ((apptWord && (cancelWord || deleteWord || cancelNoun)) || (cancelWord && !has(s, "избранн", "обран"))) {
            val dt = NoaDateTime.parse(original, now)
            return NoaIntent.CancelAppointment(extractPerson(s, extraStop = APPT_NOISE), dt?.takeIf { it.hadDate }?.dateTime?.toLocalDate(), delete = deleteWord && !cancelWord)
        }

        // запись на приём
        if (SCHED_ADD.containsMatchIn(s) || has(s, "запиши", "запиш", "запис", "записать", "назнач", "book", "appointment", "schedule")) {
            val service = extractService(s)
            val person = extractPerson(s, afterCreate = true, extraStop = APPT_NOISE)
            val dt = NoaDateTime.parse(original, now, workHours = true)
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

    /** «Поставь блокировку / постав замок / вимкни екран» — заблокировать телефон. */
    private val LOCK_RE = Regex("(?:^|\\s)(?:постав\\S*|включи\\S*|увімкни\\S*|ввімкни\\S*|set|enable|активуй\\S*)\\s+(?:\\S+\\s+)?(?:блокировк\\S*|блокуванн\\S*|блокіровк\\S*|замок|замк\\S*|сейф\\S*|защит\\S*|захист\\S*|пароль|lock)" +
        "|(?:^|\\s)(?:вимкни\\S*|выключи\\S*|погаси\\S*|гаси|выруби|turn off)\\s+(?:мій\\s+|мой\\s+|the\\s+)?(?:экран\\S*|екран\\S*|дисплей\\S*|screen)")

    /** Названия приложений-мессенджеров и соцсетей: к ним применяются «закрой / вимкни / прибери». */
    private val BRANDS = listOf("телеграм", "telegram", "вотсап", "ватсап", "вацап", "whatsapp", "вайбер", "viber", "инстаграм", "інстаграм", "instagram", "инста", "інста",
        "ютуб", "youtube", "ютюб", "вейз", "вэйз", "waze", "спотифай", "спотіфай", "spotify", "фейсбук", "facebook", "фб", "тг")

    private fun brandOf(tok: String): String? {
        val w = tok.trim('-', '«', '»', '"')
        return BRANDS.firstOrNull { w == it || (it.length >= 4 && w.startsWith(it) && w.length - it.length <= 3) }?.let { w }
    }

    private val OFF_VERB = Regex("(?:^|\\s)(?:вимкни\\S*|выключи\\S*|выруби\\S*|убери\\S*|прибери\\S*|закінчуй|закінчи|закончи|кончай|shut down|turn off|kill|вбий|заверши\\S*|завершуй)(?:\\s|$)")
    private val CLOSE_IT = Regex("(?:^|\\s)(?:закрий|закрой|close)\\s+(?:його|её|ее|его|її|it)(?:\\s|$)")
    private val OTHER_ACTION = listOf("напиш", "напис", "отправ", "відправ", "надішл", "скинь", "позвон", "подзвон", "набер", "запиш", "скаж", "передай", "открой", "відкрий", "заблок", "музык", "музик", "фонар", "ліхтар")

    private fun closeBrandApp(s: String): NoaIntent.CloseApp? {
        if (!OFF_VERB.containsMatchIn(s) && !CLOSE_IT.containsMatchIn(s)) return null
        if (OTHER_ACTION.any { has(s, it) }) return null
        val brands = s.trim().split(" ").mapNotNull { brandOf(it) }
        return brands.singleOrNull()?.let { NoaIntent.CloseApp(it) }
    }

    private val OPEN_APP_FILL = setOf("открой", "відкрий", "покажи", "ткни", "тапни", "нажми", "натисни", "клацни", "зайди", "перейди", "запусти", "хочу", "давай", "мне", "мені",
        "в", "у", "на", "до", "приложение", "застосунок", "додаток", "app", "open", "launch", "пожалуйста", "будь", "ласка", "ну", "а", "відкрити", "открыть")

    private fun appOnly(s: String): NoaIntent.LaunchApp? {
        val toks = s.trim().split(" ").filter { it.isNotBlank() }
        if (toks.size < 2 || toks.none { it in OPEN_APP_FILL && it.length > 2 }) return null
        val rest = toks.filter { it !in OPEN_APP_FILL }
        val name = rest.singleOrNull()?.let { brandOf(it) } ?: return null
        return NoaIntent.LaunchApp(name)
    }

    // ---- проверка результата: «объяснена ли фраза выбранным намерением» ----

    /** Слова, которыми действительно просят написать/передать (а не просто названо приложение или «повідомляє»). */
    private val MSG_STEM = Regex("(?:^|\\s)(?:напиш\\S*|напис\\S*|отправ\\S*|відправ\\S*|надішл\\S*|надісл\\S*|скинь\\S*|кинь|закинь|сбрось|сообщ(?:и|ите|ение)\\S*|повідом(?:и|те)|повідомленн\\S*|" +
        "скаж\\S*|переда\\S*|смс|sms|message|write|send|text|whatsapp|вотсап|ватсап|телеграм|telegram)(?:\\s|$)")
    /** Каналы, которых у «Написать» нет: уйти в WhatsApp вместо Viber/Instagram было бы тихой ошибкой. */
    private val UNSUPPORTED_CH = Regex("\\s(?:в|у|во|через|по|via|on)\\s+(?:вайбер\\S*|viber|инстаграм\\S*|інстаграм\\S*|instagram|фейсбук\\S*|фейсбуц\\S*|facebook|мессенджер\\S*|messenger|вк|вконтакте|фб|fb)(?:\\s|$)")
    private val SEND_VERBS = setOf("перешли", "перешлите", "перешли-ка", "перекинь", "отправь", "відправ", "надішли", "скинь", "кинь", "пошли", "відішли", "forward", "send")
    private val QUESTION_WORDS = setOf("какой", "какая", "какое", "какие", "який", "яка", "яке", "які", "где", "де", "сколько", "скільки", "кто", "хто", "как", "як", "what", "when", "where", "who", "how")

    /** Команды-глаголы: два разных в одной части без союза («знайди Дмитра відкрий картку») — цепочка, которую правила не разбирают. */
    private val CMD_VERBS = setOf("знайди", "найди", "відкрий", "открой", "подзвони", "позвони", "набери", "напиши", "запиши", "покажи", "зайди", "заблокуй", "заблокируй",
        "закрий", "закрой", "перейди", "додай", "добавь", "удали", "видали", "перенеси", "скасуй", "отмени", "включи", "увімкни", "запусти")

    /** «Поставь Оксану в расписание на среду» — добавить запись (а не показать расписание). */
    private val SCHED_ADD = Regex("(?:^|\\s)(?:постав\\S*|внеси|внесі|занеси|занесі|добавь|додай)\\s.*\\s(?:в|у)\\s+(?:расписан|розклад)")

    /** Служебные слова вокруг записи: «не придёт», «сними», «в расписание», «поставь» — не часть имени. */
    private val APPT_NOISE = setOf("не", "придёт", "придет", "прийде", "відмовляється", "отказывается", "scratch", "drop", "сними", "зніми", "выкинь", "викинь", "вже", "уже", "нема", "немає", "нету",
        "расписания", "расписание", "розкладу", "розклад", "поставь", "постав", "внеси", "занеси", "от", "від", "can't", "make", "so", "booking")

    private fun nameWords(q: String) = q.split(" ").count { it.isNotBlank() }

    /**
     * true — фраза содержит то, чего выбранное намерение не объясняет (лишние слова вместо имени, чужой глагол,
     * два намерения сразу): лучше отдать модели, чем выполнить не то.
     */
    private fun suspicious(original: String, r: NoaIntent): Boolean {
        val s = " " + original.lowercase() + " "
        val first = bare(original.substringBefore(' '))
        val freeText = r is NoaIntent.Message || r is NoaIntent.AddNote || r is NoaIntent.Remind || r is NoaIntent.Reply || r is NoaIntent.WebSearch || r is NoaIntent.Play
        if (!freeText && s.trim().split(" ").map { bare(it) }.filter { it in CMD_VERBS }.toSet().size >= 2) return true
        return when (r) {
            is NoaIntent.Message -> {
                val head = " " + (messageBody(original)?.first ?: original).lowercase() + " "
                !MSG_STEM.containsMatchIn(s) || (r.text == null && !r.aboutAppointment && nameWords(r.personQuery) >= 2 && Regex("^(?:отправ|відправ|надішл|скинь|напиш|напис)").containsMatchIn(bare(original.substringAfterLast(' ')))) ||
                    UNSUPPORTED_CH.containsMatchIn(head) || nameWords(r.personQuery) >= 3 ||
                    (r.text == null && !r.aboutAppointment && Regex("карточк|картк|заметк|нотатк|адрес|номер|контакт").containsMatchIn(head))
            }
            is NoaIntent.AddNote -> first in SEND_VERBS || nameWords(r.personQuery) >= 3
            is NoaIntent.Remind -> bare(r.text.substringBefore(' ')).lowercase() in QUESTION_WORDS
            is NoaIntent.Agenda -> Regex("\\s(?:про|об|о)\\s+\\S+").containsMatchIn(s) || has(s, "найди", "знайди", "шукай", "пошукай", "поищи")
            is NoaIntent.CreateAppointment, is NoaIntent.CancelAppointment, is NoaIntent.MoveAppointment, is NoaIntent.Call, is NoaIntent.Open,
            is NoaIntent.OpenContact, is NoaIntent.Select, is NoaIntent.Favorite -> nameWords(personOf(r)) >= 3
            else -> false
        }
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
        "скажіть", "передайте", "скинь", "кинь", "сбрось", "закинь", "скиньте", "write", "send", "text")

    private val OPEN_WORDS = setOf("открой", "відкрий", "open", "запусти", "запустить", "запустити", "launch", "start", "включи", "увімкни",
        "приложение", "приложения", "застосунок", "додаток", "app", "мне", "мені", "пожалуйста", "будь", "ласка", "ну", "а")

    private fun stripWords(s: String, words: Set<String>) =
        s.trim().split(" ").filter { it.isNotBlank() && it !in words }.joinToString(" ")

    private val NUMBERISH = Regex("\\d|\\s(?:one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|noon|midnight|o'clock|sharp|half|quarter)\\s")

    /** Будильник, таймер, фонарик, настройки, поиск в Google, запуск приложения. */
    private fun phoneAction(s: String, original: String, now: LocalDateTime): NoaIntent? {
        if (has(s, "будильник", "разбуди", "розбуди", "alarm", "wake me")) {
            // «будильник на 7», «на семь тридцать» — «на N» здесь час
            val dt = (NoaDateTime.parse(original, now)?.takeIf { it.hadTime }
                ?: NoaDateTime.parse(Regex(" на (?=\\d)").replace(" $original", " в "), now)?.takeIf { it.hadTime }
                ?: NoaDateTime.parse(original.replace(" на ", " в "), now)?.takeIf { it.hadTime })
                // время названо, но не понято («at seven sharp») — не открываем часы вместо будильника, пусть разберёт модель
                ?: return if (NUMBERISH.containsMatchIn(s)) null else NoaIntent.LaunchApp("будильник")
            return NoaIntent.Alarm(dt.dateTime.hour, dt.dateTime.minute)
        }
        if (has(s, "таймер", "засеки", "засічи", "timer")) {
            // «на пять минут», «на полчаса», «на час двадцать», «на полтора часа» — длительность словами
            NoaDateTime.durationSeconds(original)?.takeIf { it in 1..(24 * 3600L) }?.let { return NoaIntent.Timer(it.toInt()) }
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
        // «настройки батареи / звука / уведомлений / экрана» — раздел настроек телефона, а не самого приложения
        if (has(s, "настройк", "налаштуван", "settings") && has(s, "батаре", "battery", "звук", "sound", "уведомл", "сповіщ", "экран", "екран", "дисплей"))
            return NoaIntent.PhoneSettings(when {
                has(s, "батаре", "battery") -> "battery"; has(s, "звук", "sound") -> "sound"; has(s, "уведомл", "сповіщ") -> "notifications"; else -> "display"
            })
        if (has(s, "вайфай", "вай-фай", "wi-fi", "wifi", "вайфаю")) return NoaIntent.PhoneSettings("wifi")
        if (has(s, "блютуз", "блютус", "bluetooth")) return NoaIntent.PhoneSettings("bluetooth")
        if (has(s, "яркост", "яскрав", "brightness")) return NoaIntent.PhoneSettings("display")
        if (has(s, "настройки телефона", "налаштування телефону", "phone settings")) return NoaIntent.PhoneSettings(null)
        val search = Regex("(?:загугли|погугли|google|поищи в (?:гугле|гугл|интернете|сети)|найди в (?:гугле|гугл|интернете|сети)|" +
            "пошукай в (?:гуглі|гугл|інтернеті|мережі|сети)|знайди в (?:гуглі|гугл|інтернеті|мережі)|search for)\\s+(.+)").find(s.trim())
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
            "что написал", "що написав", "что написала", "що написала", "зачитай", "кто писал", "хто писав", "read messages", "read my messages", "есть сообщения", "є повідомлення")
        if (!read && !Regex("(?:что|що)\\s+(?:там\\s+|же\\s+)?(?:пишет|написал\\S*|написав\\S*|пише)").containsMatchIn(s)) return null
        return NoaIntent.ReadMessages(extractPerson(s), wait)
    }

    /** Куда ехать — слова фразы без команды, навигатора и служебных; цифры адреса сохраняются. */
    private fun destination(original: String): String {
        val words = original.lowercase().replace(Regex("[,.!?]"), " ").split(" ").filter { it.isNotBlank() }
        val stop = setOf("проложи", "проклади", "прокласти", "построй", "побудуй", "перестрой", "перебудуй", "маршрут", "маршрута", "мне", "мені", "пожалуйста",
            "будь", "ласка", "через", "в", "у", "во", "на", "до", "к", "ко", "по", "waze", "вейз", "вэйз", "вейс", "уэйз", "google", "гугл", "гугле", "карты", "карти",
            "картах", "карте", "мапи", "мапах", "maps", "навигатор", "навигаторе", "навігатор", "навігаторі", "яндекс", "yandex", "organic", "route", "to",
            "directions", "navigate", "take", "bring", "drive", "поехали", "поїхали", "едем", "їдемо", "доехать", "доїхати", "как", "як", "добраться", "дістатися", "путь", "шлях",
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

    // ---- диалог: «отмена», «повтори», «да/нет» ----

    private val DISMISS = setOf("отмена", "отбой", "забудь", "забудьте", "забей", "неважно", "не важно", "не надо", "не нужно", "ничего", "ничего не надо", "ничего не нужно",
        "не надо ничего", "не нужно ничего", "забудь это", "забудь про это", "забудь пожалуйста", "отмена команды", "отмени команду", "отмена отмена", "відміна", "відбій", "нічого",
        "не треба", "нічого не треба", "не потрібно", "не важливо", "забудь це", "забудь про це", "залиш", "залиште", "відміни команду", "скасуй команду", "cancel", "never mind", "forget it", "forget that")

    private val REPEAT = setOf("повтори", "повторите", "повторить", "повтори пожалуйста", "повтори еще раз", "еще раз", "ще раз", "повтори ще раз", "повтори ще", "скажи еще раз", "скажи ще раз",
        "что ты сказала", "что ты сказал", "що ти сказала", "що ти сказав", "что ты сейчас сказала", "що ти зараз сказала", "что что", "что-что", "що що", "що-що", "повтори последнее",
        "повтори останнє", "повтори ответ", "повтори відповідь", "повтори команду", "повтори последнюю команду", "повтори останню команду", "сделай еще раз", "зроби ще раз", "еще раз пожалуйста",
        "что", "що", "шо", "чего", "say that again", "say again", "repeat that", "one more time", "once more", "again", "come again", "pardon", "повтори это", "повтори це", "повторяй еще раз",
        "ну еще раз", "давай еще раз", "давай ще раз", "а ну повтори", "повтори-ка", "повтори ка", "скажи это еще раз", "скажи ще раз це")
    private val REPEAT_RE = Regex("(?:скажи|повтори|повторите)(?: (?:мне|еще|ще|раз|это|то|же|последнее|последнюю|команду|ответ|останнє|останню|відповідь|будь|ласка))+" +
        "|(?:повтори(?:те)? )?(?:что|що) (?:ты|ти)(?: (?:сейчас|зараз))? (?:сказал[аи]?|казал[аи]?)")
    private val NEGATIVE_RE = Regex("^(?:не (?:надо|нужно|треба|потрібно|звони|пиши|записывай|записуй|делай|роби|ищи|открывай|відкривай|включай|вмикай|отправляй|надсилай|телефонуй|дзвони)|давай не будем|давай не будемо|не будем|не будемо|не буду)(?: |$)")

    private fun dialogue(s: String): NoaIntent? {
        val t = s.trim().replace('ё', 'е')
        if (t in DISMISS) return NoaIntent.Dismiss
        // «не надо записывать», «не звони», «давай не будем» — запрет: ничего не делаем
        if (NEGATIVE_RE.containsMatchIn(t)) return NoaIntent.Dismiss
        if (t in REPEAT || REPEAT_RE.matches(t)) return NoaIntent.Repeat
        return null
    }

    private val YES_WORDS = setOf("да", "ага", "угу", "давай", "давайте", "конечно", "разумеется", "ладно", "хорошо", "окей", "ок", "оке", "ok", "okay", "yes", "yeah", "yep", "yup", "sure",
        "подтверждаю", "подтверди", "подтвердить", "верно", "правильно", "точно", "именно", "так", "звичайно", "авжеж", "гаразд", "добре", "підтверджую", "підтверди", "согласна", "согласен",
        "делай", "записывай", "оформляй", "go", "confirm", "естественно", "безусловно", "угу-угу", "да-да", "так-так", "ага-ага", "звісно", "обязательно", "обовязково")
    private val NO_WORDS = setOf("нет", "не", "неа", "не-а", "ни", "ні", "нее", "no", "nope", "nah", "отмена", "отмени", "отмените", "отменить", "cancel", "стоп", "stop", "скасуй", "скасувати",
        "відміна", "забудь", "відбій", "отбой", "хватит", "досить", "ненужно", "никак", "нехай", "нет-нет", "ні-ні", "no-no")

    /**
     * «Да»/«нет» в разных формулировках: true — согласие, false — отказ или поправка («нет, на пятницу», «не в три а в четыре»), null — ни то ни другое.
     * Решает первое смысловое слово (после обращения и «ну/так»); «не …» всегда отказ.
     */
    fun yesNo(text: String): Boolean? {
        val toks = normalize(stripAddress(text)).lowercase().replace('ё', 'е').split(" ").filter { it.isNotBlank() }
        if (toks.isEmpty()) return null
        // «ну да», «ой нет» — служебное слово перед ответом
        val first = toks.take(3).firstOrNull { it !in setOf("ну", "а", "ой", "эм", "ммм") } ?: toks[0]
        return when {
            first in NO_WORDS -> false
            first in YES_WORDS -> true
            first.startsWith("подтвер") || first.startsWith("підтвер") || first.startsWith("конечн") || first.startsWith("давай") -> true
            first.startsWith("отмен") || first.startsWith("скасу") -> false
            else -> null
        }
    }

    // ---- инструменты: время, дата, калькулятор, единицы ----

    private val TIME_Q = Regex("(?:котор\\S* (?:сейчас )?час|сколько (?:сейчас )?времени|сколько время|сколько (?:сейчас )?часов|скільки (?:зараз )?часу|скільки часу|котра (?:зараз )?година|яка (?:зараз )?година|який (?:зараз )?час|what time is it|what's the time|what is the time|current time|время сейчас|час сейчас|час зараз|time now)(?= |$)")
    private val DATE_Q = Regex("(?:какое|яке) (?:сегодня |сьогодні |завтра )?число|(?:какая|яка) (?:сегодня |сьогодні |завтра )?дата|what(?:'s| is) (?:the |today's )*date|today's date|сегодняшн\\S* (?:дата|число)|скажи дату|назови дату")
    private val WEEKDAY_Q = Regex("(?:какой|який) (?:сегодня |сьогодні |завтра |послезавтра )?день(?: недели| тижня| буде| будет)?(?= |$)|(?:какой|який) день недели|что за день|що за день|what day(?: of the week)?(?: is it| is)?")
    private val MATH_PATTERN = Regex("процент\\S*\\s+(?:от|від|з)|відсот\\S*\\s+(?:від|з)|корен\\S*\\s+(?:квадратн\\S*\\s+)?из|корін\\S*\\s+(?:квадратн\\S*\\s+)?(?:з|із)|квадратный корень|квадратний корінь|в квадрате|у квадраті|\\d\\s*(?:плюс|минус|мінус|умножить на|помножити на|разделить на|поділити на|\\+|\\*|×|÷)\\s*\\d")

    private fun tool(s: String, original: String, now: LocalDateTime): NoaIntent? {
        val calcTrig = has(s, "посчитай", "подсчитай", "сосчитай", "вычисли", "порахуй", "пораху", "обчисли", "calculate", "compute", "сколько будет", "скільки буде",
            "сколько получится", "скільки вийде", "сколько это", "скільки це", "чему равно", "чому дорівнює", "реши", "how much is", "what is", "what's")
        if (calcTrig || MATH_PATTERN.containsMatchIn(s)) NoaTools.toExpression(original)?.let { return NoaIntent.Calc(it) }
        NoaTools.parseConversion(original)?.let { return NoaIntent.Convert(it.value, it.from, it.to) }
        val bday = has(s, "рожд", "народж", "birth")
        val day = { NoaDateTime.parse(original, now)?.takeIf { it.hadDate }?.dateTime?.toLocalDate() }
        if (TIME_Q.containsMatchIn(s)) return NoaIntent.Tool(NoaIntent.ToolKind.TIME)
        if (DATE_Q.containsMatchIn(s)) return NoaIntent.Tool(NoaIntent.ToolKind.DATE, day())
        if (!bday && WEEKDAY_Q.containsMatchIn(s)) return NoaIntent.Tool(NoaIntent.ToolKind.WEEKDAY, day())
        return null
    }

    // ---- напоминания ----

    private val REMIND_VERBS = setOf("напомни", "напомнить", "напомните", "нагадай", "нагадайте", "нагадати", "remind")
    private val SET_VERBS = setOf("поставь", "создай", "сделай", "добавь", "запиши", "постав", "створи", "зроби", "додай", "set", "create", "make", "add", "поставить", "создать")
    private val REMIND_LEAD_FILL = setOf("мне", "мені", "мене", "me", "что", "що", "щоб", "чтобы", "чтоб", "про", "о", "об", "том", "that", "to", "about")

    /** «Напомни завтра в 10 позвонить Ане», «нагадай через годину купити хліб», «поставь напоминание на пятницу: оплатить аренду». */
    private fun remind(original: String, s: String, now: LocalDateTime): NoaIntent.Remind? {
        val toks = original.split(" ").filter { it.isNotBlank() }.toMutableList()
        if (toks.isEmpty()) return null
        val first = bare(toks[0])
        val start = when {
            first in REMIND_VERBS -> 1
            first in SET_VERBS -> {
                val i = toks.indexOfFirst { val b = bare(it); b.startsWith("напоминан") || b.startsWith("нагадуван") || b.startsWith("reminder") }
                if (i < 0) return null else i + 1
            }
            else -> {
                // «через два часа напомни что …» — время сказано до глагола
                val i = toks.indexOfFirst { bare(it) in REMIND_VERBS }
                if (i !in 1..4) return null
                toks.addAll(toks.subList(0, i)); i + 1
            }
        }
        var rest = toks.drop(start)
        if (rest.firstOrNull()?.lowercase() == "me") rest = rest.drop(1)
        val (text, dateStr) = splitReminder(rest)
        val dt = if (dateStr.isBlank()) null else NoaDateTime.parse(dateStr, now, workHours = true)
        return NoaIntent.Remind(text.replaceFirstChar { it.uppercase() }, dt?.dateTime, hadTime = dt?.hadTime ?: false)
    }

    /** Токены → (о чём, когда): время срезается с начала и с конца, середина — текст напоминания. */
    private fun splitReminder(tokens: List<String>): Pair<String, String> {
        var lo = 0; var hi = tokens.size
        val dateParts = mutableListOf<String>()
        fun dateAt(k: Int): Boolean { var j = k; while (j < hi && NoaDateTime.isDatePrep(tokens[j])) j++; return j < hi && NoaDateTime.isDateToken(tokens[j]) }
        fun bareNumbers(range: List<String>) = range.isNotEmpty() && range.all { Regex("\\d+").matches(it.trim(',', '.')) }
        var progress = true
        while (progress && lo < hi) {
            progress = false
            while (lo + 1 < hi && bare(tokens[lo]) in REMIND_LEAD_FILL) { lo++; progress = true }
            var j = lo; var took = false
            while (j < hi) {
                val t = tokens[j]
                if (NoaDateTime.isDateToken(t)) { j++; took = true }
                else if (NoaDateTime.isDatePrep(t) && dateAt(j + 1)) j++
                else break
            }
            if (took && j > lo && !bareNumbers(tokens.subList(lo, j))) { dateParts += tokens.subList(lo, j); lo = j; progress = true }
        }
        var k = hi; var tail = false
        while (k > lo) {
            val t = tokens[k - 1]
            if (NoaDateTime.isDateToken(t)) { k--; tail = true }
            else if (tail && NoaDateTime.isDatePrep(t)) k--
            else break
        }
        if (tail && !bareNumbers(tokens.subList(k, hi))) { dateParts += tokens.subList(k, hi); hi = k }
        return tokens.subList(lo, hi).joinToString(" ").trim() to dateParts.joinToString(" ")
    }

    // ---- вопросы по картотеке ----

    private val CRM_STOP = setOf("следующий", "следующая", "следующую", "следующее", "следующего", "наступний", "наступна", "наступну", "наступного", "ближайший", "ближайшая", "ближайшую", "ближайшие",
        "найближчий", "найближча", "найближчу", "последний", "последнего", "последнее", "останній", "останнього", "раз", "разу", "говорил", "говорила", "говорили", "разговаривал", "разговаривала",
        "звонил", "звонила", "писал", "писала", "общался", "общалась", "виделись", "виделся", "виделась", "видел", "видела", "встречались", "связывался", "связывалась", "говорив", "спілкувався",
        "спілкувалася", "дзвонив", "дзвонила", "писав", "бачились", "бачив", "зустрічались", "востаннє", "мы", "ми", "меня", "мене", "со", "окно", "окна", "окошко", "вікно", "вікна", "свободен",
        "свободна", "свободно", "свободное", "вільно", "вільний", "вільне", "время", "времени", "записей", "запись", "записи", "записан", "записаны", "записано", "записані", "запис", "клиент",
        "клиенты", "клиентов", "клієнт", "клієнти", "клієнтів", "сколько", "скільки", "есть", "є", "ли", "скоро", "кого", "кто", "хто", "дни", "дні", "рождения", "народження", "др", "дальше", "далі",
        "недели", "неделе", "тижні", "тижня", "месяце", "місяці", "месяца", "місяця", "этой", "цьому", "этом", "цієї", "покажи", "скажи", "назови", "назви", "list", "who", "next", "last", "time",
        "when", "what", "how", "many", "free", "slot", "appointments", "appointment", "client", "clients", "did", "do", "talk", "talked", "spoke", "speak", "call", "called", "with", "my", "is",
        "does", "have", "has", "i", "you", "me", "когда", "коли", "давно", "день", "дня", "какие", "які", "upcoming", "soon", "именинник", "іменинник", "именинники", "іменинники", "встреча", "зустріч",
        "встречи", "зустрічі", "занят", "зайнят", "будет", "буде", "что", "що", "сегодня", "завтра", "ближайших", "найближчих", "ближайшем", "какая", "какое", "яке", "после", "після", "перед", "обеда", "обіду", "днем", "днём", "вечером", "утром", "человек", "люди", "людей", "людини", "чоловік", "народився", "родился", "родилась", "народилась")
    private val APPT_WORDS = arrayOf("запис", "клиент", "клієнт", "встреч", "зустріч", "приём", "прием", "прийом", "пациент", "пацієнт", "appointment", "client", "booking", "сеанс")

    private fun weekOf(dt: NoaDateTime.Parsed?, now: LocalDateTime) = NoaDateTime.weekStart((dt?.takeIf { it.hadDate }?.dateTime ?: now).toLocalDate())

    /** Вопросы по картотеке: ближайшая запись, сколько записей, окна, кто записан на время, последний контакт, дни рождения. */
    private fun crm(s: String, original: String, now: LocalDateTime): NoaIntent.Crm? {
        if (has(s, "перенес", "перенос", "передвин", "пересун", "зсунь", "посунь", "отмен", "скасу", "відмін", "удал", "видал", "сотри", "зітри", "запиши", "запишіть", "запишите",
                "записать", "назнач", "создай", "book", "reschedule", "cancel", "delete", "закрой", "закрий", "сверни")) return null
        val person by lazy { extractPerson(s, extraStop = CRM_STOP) }
        val dt by lazy { NoaDateTime.parse(original, now, workHours = true) }
        val today = now.toLocalDate()
        // последний раз говорили: «когда я последний раз говорил с Аней»
        val lastWord = Regex("последн\\S*\\s+раз|останн\\S*\\s+раз|в последний|востаннє|last time|давно ли").containsMatchIn(s)
        val contactVerb = has(s, "говорил", "разговарив", "звонил", "писал", "общал", "виделс", "виделис", "видел", "встречал", "связыв", "контакт", "спілкув", "говорив", "дзвонив",
            "писав", "бачил", "зустрічал", "talked", "spoke", "called", "wrote", "saw", "met", "contacted", "созванивал", "переписывал")
        if ((lastWord || has(s, "когда мы", "коли ми", "когда я", "коли я", "when did i", "when did we")) && contactVerb)
            return NoaIntent.Crm(NoaIntent.CrmKind.LAST_CONTACT, person)
        // дни рождения вообще (без имени): «у кого скоро день рождения»
        if (has(s, "день рожд", "день народж", "дни рожд", "дні народж", "др ", "birthday", "именинник", "іменинник") && person.isBlank() &&
            (has(s, "кого", "кто", "хто", "who", "скоро", "ближайш", "найближч", "какие", "які", "upcoming", "soon", "список", "покажи", "когда", "коли", "есть", "є"))) {
            val month = has(s, "месяц", "місяц", "month")
            val week = has(s, "недел", "тижн", "тижд", "week")
            val nextWord = has(s, "следующ", "наступн", "next")
            return when {
                week -> NoaIntent.Crm(NoaIntent.CrmKind.BIRTHDAYS, period = NoaIntent.CrmPeriod.WEEK, date = weekOf(dt, now))
                month -> NoaIntent.Crm(NoaIntent.CrmKind.BIRTHDAYS, period = NoaIntent.CrmPeriod.MONTH,
                    date = if (nextWord) today.plusMonths(1).withDayOfMonth(1) else if (has(s, "этом", "цьому", "this")) today.withDayOfMonth(1) else null)
                dt?.hadDate == true -> NoaIntent.Crm(NoaIntent.CrmKind.BIRTHDAYS, date = dt!!.dateTime.toLocalDate())
                else -> NoaIntent.Crm(NoaIntent.CrmKind.BIRTHDAYS, period = NoaIntent.CrmPeriod.MONTH)
            }
        }
        // кто записан на конкретное время: «кто записан на 15:00», «что у меня в пятницу в три», «занято ли завтра в 12»
        val whoCue = has(s, "кто", "хто", "who", "что у", "що в", "що у", "что на", "що на", "что в", "занят", "зайнят", "свободно ли", "вільно чи", "free at", "busy")
        val d = dt
        if (whoCue && d != null && d.exactTime && !has(s, "окно", "окна", "вікно", "вікна")) {
            // «в три» без пометки — днём: записи не бывают в 3 часа ночи
            var time = d.dateTime.toLocalTime()
            if (time.minute == 0 && time.hour in 1..7 && !Regex("утр|ночи|ночью|ранк|ночі|\\bam\\b").containsMatchIn(s)) time = time.plusHours(12)
            return NoaIntent.Crm(NoaIntent.CrmKind.WHO_AT, person, date = if (d.hadDate) d.dateTime.toLocalDate() else today, time = time)
        }
        val apptWord = has(s, *APPT_WORDS)
        // сколько записей
        if (has(s, "сколько", "скільки", "how many") && apptWord && !has(s, "времени", "часу")) {
            return when {
                has(s, "недел", "тижн", "тижд", "week") -> NoaIntent.Crm(NoaIntent.CrmKind.COUNT, person, date = weekOf(dt, now), period = NoaIntent.CrmPeriod.WEEK)
                has(s, "месяц", "місяц", "month") -> NoaIntent.Crm(NoaIntent.CrmKind.COUNT, person,
                    date = (if (has(s, "следующ", "наступн", "next")) today.plusMonths(1) else today).withDayOfMonth(1), period = NoaIntent.CrmPeriod.MONTH)
                else -> NoaIntent.Crm(NoaIntent.CrmKind.COUNT, person, date = dt?.takeIf { it.hadDate }?.dateTime?.toLocalDate() ?: today)
            }
        }
        // свободные окна: «когда у меня окно завтра», «есть свободное время в пятницу»
        val freeWord = has(s, "окно", "окна", "окошк", "вікно", "вікна", "свободн", "вільн", "free slot", "free time", "available", "window", "free on", "when am i free")
        if (freeWord && has(s, "когда", "коли", "есть", "є", "покажи", "скажи", "when", "any", "дай", "назови", "назви", "найди", "знайди", "ли", "free"))
            return NoaIntent.Crm(NoaIntent.CrmKind.FREE, date = dt?.takeIf { it.hadDate }?.dateTime?.toLocalDate() ?: today)
        // ближайшая запись: «кто следующий», «когда следующая запись», «ближайший клиент»
        val nextWord = has(s, "следующ", "наступн", "ближайш", "найближч", "next", "дальше", "далі")
        if (nextWord && (apptWord || has(s, "кто", "хто", "who")))
            return NoaIntent.Crm(NoaIntent.CrmKind.NEXT, person)
        return null
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
        // шум распознавания и слова времени: «уже», «за два дня», «на два часа позже»
        "уже","вже","за","позже","пізніше","раньше","раніше","пол","след","отмена","отмену","скасування","відміна","відміну",
    )

    /** «Открой/зайди/покажи …» раздел приложения. */
    private fun screenSection(s: String): NoaIntent.Section? {
        if (!has(s, "открой", "зайди", "покажи", "перейди", "відкрий", "зайди в", "open", "go to")) return null
        return when {
            has(s, "рассылк", "розсилк", "broadcast") -> NoaIntent.Section.BROADCAST
            has(s, "календар", "calendar") -> NoaIntent.Section.CALENDAR
            (has(s, "карт") && !has(s, "карточ", "картк")) || has(s, "map") -> NoaIntent.Section.MAP
            has(s, "настройк", "налаштуванн", "settings") -> NoaIntent.Section.SETTINGS
            has(s, "услуг", "послуг", "service", "сервис", "сервіс") -> NoaIntent.Section.SERVICES
            has(s, "людей", "список", "контакты", "people", "home", "главн", "головн") -> NoaIntent.Section.PEOPLE
            else -> null
        }
    }
    private val MONTH_WORDS = listOf("январ","феврал","март","апрел","мая","июн","июл","август","сентябр","октябр","ноябр","декабр",
        "січн","лют","берез","квітн","травн","черв","лип","серп","вересн","жовтн","листопад","грудн",
        "janu","febr","march","april","june","july","august","septemb","octob","novemb","decemb")

    private fun extractPerson(sRaw: String, afterCreate: Boolean = false, extraStop: Set<String> = emptySet()): String {
        // Текст в кавычках (заметка/сообщение) — не имя.
        val s = sRaw.replace(Regex("[«\"'][^«»\"']*[»\"']"), " ")
        val tokens = s.trim().split(" ").filter { it.isNotBlank() }
        val words = tokens.filter { w ->
            val c = w.trim('-', '«', '»', '"').lowercase()
            c.isNotEmpty() && c !in STOP && c !in extraStop && !c.all { ch -> ch.isDigit() } &&
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
        has(s, "фейсбук", "фейсбуц", "facebook", "фб", "fb") -> com.kartoteka.app.data.ContactType.FACEBOOK
        has(s, "вайбер", "viber") -> com.kartoteka.app.data.ContactType.VIBER
        has(s, "вконтакт", "vk ") || Regex("(?:^|\\s)вк(?:\\s|$)").containsMatchIn(s) -> com.kartoteka.app.data.ContactType.VK
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
