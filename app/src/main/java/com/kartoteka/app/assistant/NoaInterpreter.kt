package com.kartoteka.app.assistant

import com.kartoteka.app.data.ContactType
import com.kartoteka.app.data.PlaceKind
import com.kartoteka.app.i18n.t
import java.time.LocalDate
import java.time.LocalDateTime

/** Чего не хватает действию, чтобы его можно было выполнить. */
enum class Need { PERSON, TEXT, QUERY, APP, TIME, DURATION }

/** Шаг цепочки после починки: каноническое имя действия и его поля (строки). Из таких шагов собирается команда. */
data class Step(val action: String, val args: Map<String, String>)

/**
 * Уточняющий вопрос Ноа. [need] — чего не хватает (null — свободный вопрос модели), [steps] — вся цепочка,
 * [index] — шаг, которому не хватает данных, [options] — варианты («какой именно Илья?»), [phrase] — исходная фраза,
 * [round] — номер попытки (после второй не переспрашиваем). Ответ человека подставляется в шаг и цепочка выполняется.
 */
data class Clarify(
    val question: String, val need: Need?, val steps: List<Step>, val index: Int,
    val options: List<String>, val phrase: String, val round: Int = 1,
)

/**
 * Результат понимания фразы мозгом: действие (или null = свободный разговор), реплика голосом
 * и, если не хватило данных, уточняющий вопрос [ask] (тогда ничего не выполняем, пока человек не ответит).
 */
data class Interpreted(val intent: NoaIntent?, val reply: String?, val ask: Clarify? = null)

/**
 * Превращает свободную речь в команду через небольшую модель на телефоне. Промпт просит модель вернуть
 * строго JSON {"actions":[…],"reply":"…","ask":"…"}; дату/время/канал всё равно берём из самой фразы
 * ([NoaDateTime]) — это надёжнее, чем доверять модели. Ответ читаем снисходительно (см. [Lenient]) и затем
 * «чиним»: синонимы действий и полей, имена по списку людей, обязательные поля (нет — спрашиваем или отбрасываем).
 * Любой сбой → null, и вызывающий переходит на быстрые команды.
 */
class NoaInterpreter(private val brain: LlmBrain?) {
    /** Подмена вызова модели (тесты). */
    internal var askOverride: (suspend (String) -> String?)? = null
    /** Последний «сырой» ответ модели — для проверки ИИ и журнала. */
    @Volatile var lastRaw: String? = null
        private set

    suspend fun interpret(
        userText: String, now: LocalDateTime = LocalDateTime.now(), names: List<String> = emptyList(), context: String = "",
        history: String = "", hasLast: Boolean = false, pending: Clarify? = null,
    ): Interpreted? {
        lastRaw = null
        // Запрос не влез в окно модели («too_long») — повторяем короче: без истории, затем без данных, затем без списка людей.
        val attempts = listOf(
            Triple(names, context, history),
            Triple(names.take(12), context.take(260), ""),
            Triple(names.take(6), "", ""),
        )
        var raw: String? = null
        for ((nm, ctx, hist) in attempts) {
            val p = prompt(userText, now, nm, ctx, hist, pending)
            raw = askOverride?.invoke(p) ?: brain?.ask(p)
            if (raw != null || brain?.lastError?.startsWith("too_long") != true) break
        }
        val text = raw ?: return null
        lastRaw = text
        val phrase = if (pending != null) pending.phrase + " " + userText else userText
        return fromJson(text, phrase, now, names, hasLast, context, (pending?.round ?: 0) + 1)
    }

    /**
     * Ответ на уточняющий вопрос. Сначала — без модели: имя/время/текст подставляем в ту же цепочку;
     * не получилось (или вопрос был свободный) — модель читает ответ вместе с прежней фразой и вопросом.
     */
    suspend fun answer(
        c: Clarify, answer: String, now: LocalDateTime = LocalDateTime.now(), names: List<String> = emptyList(),
        context: String = "", history: String = "", hasLast: Boolean = false,
    ): Interpreted? {
        if (isNo(answer)) return Interpreted(null, t("Хорошо, отменила."))
        if (c.need != null && c.steps.isNotEmpty()) {
            val ctx = Ctx(c.phrase + " " + answer, now, NoaMatch.refs(names), context, hasLast)
            fill(c, answer, ctx)?.let { return finish(it, null, ctx) }
        }
        return interpret(answer, now, names, context, history, hasLast, pending = c)
    }

    /** Разбор ответа модели в команду (чистая логика, тестируется отдельно). Никогда не бросает исключений. */
    fun fromJson(
        raw: String, userText: String, now: LocalDateTime = LocalDateTime.now(),
        names: List<String> = emptyList(), hasLast: Boolean = false, data: String = "", round: Int = 1,
    ): Interpreted? = runCatching { parse(raw, Ctx(userText, now, NoaMatch.refs(names), data, hasLast), round) }.getOrNull()

    // ---- контекст и разбор JSON ----

    /** Всё, что нужно починке: фраза пользователя, «сейчас», люди из книжки, данные, которые видела модель. */
    internal class Ctx(val phrase: String, val now: LocalDateTime, val people: List<NoaMatch.Ref>, val data: String, val hasLast: Boolean)

    private fun parse(raw: String, ctx: Ctx, round: Int): Interpreted? {
        val root = Lenient.extract(raw) ?: return null
        val obj = root as? Map<*, *>
        val reply = (obj?.get("reply") as? String)?.trim()?.ifBlank { null }
        val askRaw = listOf("ask", "clarify", "clarification", "question", "clarifying_question").firstNotNullOfOrNull { k -> (obj?.get(k) as? String)?.trim()?.ifBlank { null } }
        // Список действий: массив, один объект вместо массива, {"call":{…}}, голый массив без обёртки.
        val list: List<Map<*, *>> = when (val a = obj?.get("actions") ?: obj?.get("steps") ?: root.takeIf { it is List<*> }) {
            is List<*> -> a.mapNotNull(::asAction)
            is Map<*, *> -> if (nameOf(a) != null) listOf(a)
                else a.entries.mapNotNull { (k, v) -> (k as? String)?.let { named(v as? Map<*, *> ?: emptyMap<String, Any?>(), it) } }
            is String -> listOfNotNull(obj?.let { named(it, a) })
            else -> listOfNotNull(obj?.takeIf { nameOf(it) != null })
        }
        val chat = list.any { nameOf(it) in CHAT } || (obj != null && nameOf(obj) in CHAT)
        // Повтор одного и того же шага подряд — зацикливание модели: оставляем один; шагов не больше пяти.
        val steps = list.mapNotNull { m -> toStep(m) }
            .fold(ArrayList<Step>()) { acc, s -> if (acc.lastOrNull() != s) acc.add(s); acc }.take(MAX_ACTIONS)
        val out = process(steps, ctx, round)
        val cleanReply = cleanReply(reply, ctx)
        val ask = askRaw?.let { NoaText.speakable(it, 200) }?.takeIf { it.isNotBlank() && !NoaText.isPlaceholder(it) }
        return finish(out, cleanReply, ctx, ask, chat, steps.isNotEmpty())
    }

    /** Из результата починки — ответ контроллеру. [hadSteps] — модель называла известные действия (если все отброшены, реплике верить нельзя). */
    private fun finish(out: Out, reply: String?, ctx: Ctx, ask: String? = null, chat: Boolean = false, hadSteps: Boolean = false): Interpreted? {
        when (out) {
            is Out.Ask -> return Interpreted(null, null, out.c)
            is Out.Ready -> {
                val intents = out.intents
                if (intents.size > 1) return Interpreted(NoaIntent.Sequence(intents), reply)
                if (intents.size == 1) return Interpreted(intents[0], reply)
                if (hadSteps) return null // действия были, но не собрались: «Пишу Ане» без дела — ложь
                if (ask != null) return Interpreted(null, null, free(ask, ctx))
                if (reply != null) return Interpreted(null, reply, if (reply.trimEnd().endsWith("?")) free(reply, ctx) else null)
                return if (chat) Interpreted(null, null) else null
            }
        }
    }

    private fun free(question: String, ctx: Ctx) = Clarify(question, null, emptyList(), 0, emptyList(), ctx.phrase)

    private fun cleanReply(reply: String?, ctx: Ctx): String? {
        val r = reply?.takeIf { it.isNotBlank() && !NoaText.isPlaceholder(it) } ?: return null
        return NoaText.speakable(NoaText.scrubNumbers(r, ctx.phrase + "\n" + ctx.data)).takeIf { it.isNotBlank() }
    }

    private fun named(m: Map<*, *>, name: String): Map<*, *> = HashMap<Any?, Any?>(m).apply { put("action", name) }

    private fun asAction(v: Any?): Map<*, *>? = when (v) {
        is Map<*, *> -> v
        is String -> mapOf("action" to v) // ["go_home"]
        else -> null
    }

    private fun nameOf(o: Map<*, *>): String? = (o["action"] ?: o["intent"] ?: o["type"] ?: o["name"].takeIf { o.containsKey("action").not() && it is String && specOf(normName(it)) != null })
        ?.toString()?.let(::normName)?.ifBlank { null }

    private fun normName(s: String) = s.trim().lowercase().replace(Regex("[\\s-]+"), "_")

    // ---- реестр действий ----

    /**
     * Одно действие: [name] и синонимы [aliases] (в т.ч. «whatsapp» → message), поля, которые оно читает ([fields],
     * синонимы полей сводятся к каноническим), обязательные данные [needs] (нет — спрашиваем), [own] — ключи, которые
     * здесь означают своё и не сводятся к человеку/тексту (например, «to» у share_data), [implies] — что значит
     * само имя-синоним (whatsapp → channel=whatsapp). Новое действие = одна строка [act] ниже.
     */
    internal class Spec(
        val name: String, val aliases: List<String>, val fields: Set<String>, val needs: List<Need>, val own: Set<String>,
        val implies: Map<String, Pair<String, String>>, val ask: Map<Need, () -> String>, val fillKey: Map<Need, String>,
        val inherit: (Map<String, String>) -> Boolean, val prefill: (Map<String, String>, Ctx) -> Map<String, String>,
        val missing: ((Map<String, String>, Ctx) -> Need?)?, val build: (Map<String, String>, Ctx) -> NoaIntent?,
    ) {
        val hasPerson get() = "person" in fields
    }

    private val NO_ASK = emptyMap<Need, () -> String>()
    private val NO_PRE: (Map<String, String>, Ctx) -> Map<String, String> = { _, _ -> emptyMap() }

    private fun act(
        name: String, aliases: List<String> = emptyList(), fields: Set<String> = emptySet(), needs: List<Need> = emptyList(),
        own: Set<String> = emptySet(), implies: Map<String, Pair<String, String>> = emptyMap(), ask: Map<Need, () -> String> = NO_ASK,
        fillKey: Map<Need, String> = emptyMap(), inherit: (Map<String, String>) -> Boolean = { true },
        prefill: (Map<String, String>, Ctx) -> Map<String, String> = NO_PRE, missing: ((Map<String, String>, Ctx) -> Need?)? = null,
        build: (Map<String, String>, Ctx) -> NoaIntent?,
    ) = Spec(name, aliases, fields, needs, own, implies, ask, fillKey, inherit, prefill, missing, build)

    private val P = setOf("person")
    private val chats = listOf("whatsapp", "telegram", "sms", "тг", "телеграм", "смс", "ватсап")

    private val SPECS: List<Spec> = listOf(
        act("create_appointment", listOf("book", "appointment", "schedule_appointment", "add_appointment", "new_appointment", "make_appointment", "create_event", "book_appointment"), P, listOf(Need.PERSON),
            ask = mapOf(Need.PERSON to { t("Кого записать?") })) { a, c ->
            val dt = NoaDateTime.parse(c.phrase, c.now)
            NoaIntent.CreateAppointment(a["person"].orEmpty(), dt?.dateTime, dt?.hadTime ?: false, a["service"]?.lowercase(), false)
        },
        act("cancel_appointment", listOf("cancel", "cancel_booking"), P, listOf(Need.PERSON)) { a, c ->
            NoaIntent.CancelAppointment(a["person"].orEmpty(), NoaDateTime.parse(c.phrase, c.now)?.takeIf { it.hadDate }?.dateTime?.toLocalDate(), delete = false)
        },
        act("delete_appointment", listOf("remove_appointment"), P, listOf(Need.PERSON)) { a, c ->
            NoaIntent.CancelAppointment(a["person"].orEmpty(), NoaDateTime.parse(c.phrase, c.now)?.takeIf { it.hadDate }?.dateTime?.toLocalDate(), delete = true)
        },
        act("move_appointment", listOf("reschedule", "move", "postpone", "reschedule_appointment"), P, listOf(Need.PERSON)) { a, c ->
            NoaDateTime.parse(c.phrase, c.now).let { dt -> NoaIntent.MoveAppointment(a["person"].orEmpty(), dt?.dateTime, dt?.hadDate ?: false, dt?.hadTime ?: false) }
        },
        act("call", listOf("dial", "phone_call", "make_call", "ring", "phone", "call_person"), P, listOf(Need.PERSON),
            ask = mapOf(Need.PERSON to { t("Кому позвонить?") })) { a, _ -> NoaIntent.Call(a["person"].orEmpty()) },
        act("message", listOf("send_message", "text", "write", "send", "write_message", "send_sms", "tell", "send_text") + chats, setOf("person", "text"), listOf(Need.PERSON),
            implies = chats.associateWith { "channel" to it }, ask = mapOf(Need.PERSON to { t("Кому написать?") }),
            prefill = { a, c ->
                buildMap {
                    channelFrom(c.phrase)?.let { put("channel", it) }
                    if (a["text"].isNullOrBlank()) textFromPhrase(c.phrase)?.let { put("text", it) }
                    else cleanMessageText(a["text"]!!)?.let { put("text", it) }
                }
            }) { a, c ->
            // «Отправь ему об этом» — текст из шаблона подтверждения, а не выдумка модели.
            val about = NoaParser.isAboutAppointment(c.phrase) || bool(a["about_appointment"], false)
            NoaIntent.Message(a["person"].orEmpty(), channel(a["channel"]), if (about) null else a["text"], aboutAppointment = about)
        },
        act("reply", listOf("answer", "respond", "reply_message"), setOf("person", "text"), listOf(Need.TEXT), ask = mapOf(Need.TEXT to { t("Что ответить?") })) { a, _ ->
            NoaIntent.Reply(a["person"].orEmpty(), a["text"].orEmpty())
        },
        act("read_messages", listOf("read", "read_message", "check_messages", "read_notifications"), P) { a, _ -> NoaIntent.ReadMessages(a["person"].orEmpty(), bool(a["wait"], false)) },
        act("add_note", listOf("note", "add_journal", "write_note", "save_note", "remember", "add_comment"), setOf("person", "text"), listOf(Need.PERSON, Need.TEXT),
            ask = mapOf(Need.PERSON to { t("К кому добавить заметку?") }, Need.TEXT to { t("Что записать в заметку?") })) { a, _ ->
            NoaIntent.AddNote(a["person"].orEmpty(), a["text"].orEmpty())
        },
        act("open_person", listOf("open", "open_card", "show_person", "show_contact", "view_person", "open_contact_card"), P, listOf(Need.PERSON)) { a, _ -> NoaIntent.Open(a["person"].orEmpty()) },
        act("find", listOf("search", "search_person", "lookup", "search_contact", "find_person"), setOf("query"), listOf(Need.QUERY)) { a, _ -> NoaIntent.Find(a["query"].orEmpty()) },
        act("open_contact", listOf("open_social", "open_link"), P, listOf(Need.PERSON), own = setOf("contact")) { a, _ ->
            contactType(a["contact"])?.let { NoaIntent.OpenContact(a["person"].orEmpty(), it) }
        },
        act("route", listOf("navigate", "directions", "navigate_to", "go_to", "drive_to"), P, own = setOf("place", "app", "kind"),
            inherit = { it["place"].isNullOrBlank() },
            missing = { a, _ -> if (a["person"].isNullOrBlank() && a["place"].isNullOrBlank() && a["kind"].isNullOrBlank()) Need.QUERY else null },
            ask = mapOf(Need.QUERY to { t("Куда ехать?") }), fillKey = mapOf(Need.QUERY to "place")) { a, _ ->
            // Старый формат: place = home|work. Новый: place — любой адрес, kind — дом/работа человека.
            val place = a["place"].orEmpty()
            val kind = placeKind(a["kind"]) ?: placeKind(place)
            NoaIntent.Route(a["person"].orEmpty(), kind, navApp(a["app"]), if (placeKind(place) != null) "" else place)
        },
        act("agenda", listOf("plan", "show_agenda", "day_plan", "calendar_day", "today")) { a, c -> NoaIntent.Agenda(day(a["day"], c.phrase, c.now)) },
        act("person_info", listOf("info", "person_details", "get_info", "ask_person", "about_person"), P, listOf(Need.PERSON)) { a, c ->
            NoaIntent.PersonInfo(a["person"].orEmpty(), when (a["topic"]?.lowercase()) {
                "birthday" -> NoaIntent.Topic.BIRTHDAY
                "phone" -> NoaIntent.Topic.PHONE
                "address" -> NoaIntent.Topic.ADDRESS
                else -> NoaIntent.Topic.SUMMARY
            }, a["question"] ?: c.phrase)
        },
        act("favorite", listOf("add_favorite", "star"), P, listOf(Need.PERSON)) { a, _ -> NoaIntent.Favorite(a["person"].orEmpty(), bool(a["on"], true)) },
        act("select", listOf("choose", "pick", "select_person"), P, listOf(Need.PERSON)) { a, _ -> NoaIntent.Select(a["person"].orEmpty()) },
        act("share_data", listOf("transfer", "share", "export_data"), P, listOf(Need.PERSON), own = setOf("to", "data")) { a, _ ->
            NoaIntent.ShareData(a["person"].orEmpty(), when (a["data"]?.lowercase()) {
                "notes", "note" -> NoaIntent.Data.NOTES; "phone" -> NoaIntent.Data.PHONE; "address" -> NoaIntent.Data.ADDRESS
                "email" -> NoaIntent.Data.EMAIL; "birthday" -> NoaIntent.Data.BIRTHDAY; else -> NoaIntent.Data.CARD
            }, when (val to = a["to"]?.lowercase()) {
                null, "share" -> "share"; "notes", "notepad" -> "notes"; "google", "search" -> "google"; "clipboard" -> "clipboard"
                else -> "app:$to"
            })
        },
        act("launch_app", listOf("open_app", "start_app", "run_app", "open_application", "launch"), setOf("app"), listOf(Need.APP)) { a, _ -> NoaIntent.LaunchApp(a["app"].orEmpty()) },
        act("web_search", listOf("google", "search_web", "internet_search", "browse", "search_google"), setOf("query"), listOf(Need.QUERY)) { a, _ -> NoaIntent.WebSearch(a["query"].orEmpty()) },
        act("alarm", listOf("set_alarm", "wake_up", "alarm_clock"), setOf("time"), listOf(Need.TIME), ask = mapOf(Need.TIME to { t("На какое время поставить будильник?") }),
            prefill = { _, c -> NoaDateTime.parse(c.phrase, c.now)?.takeIf { it.hadTime }?.let { mapOf("time" to "%02d:%02d".format(it.dateTime.hour, it.dateTime.minute)) }.orEmpty() }) { a, c ->
            NoaDateTime.parse(a["time"].orEmpty(), c.now)?.takeIf { it.hadTime }?.let { NoaIntent.Alarm(it.dateTime.hour, it.dateTime.minute, a["label"]) }
        },
        act("timer", listOf("set_timer", "countdown", "start_timer"), emptySet(), listOf(Need.DURATION), ask = mapOf(Need.DURATION to { t("На сколько поставить таймер?") }),
            prefill = { _, c -> durationSecs(c.phrase)?.let { mapOf("hours" to "0", "minutes" to (it / 60).toString(), "seconds" to (it % 60).toString()) }.orEmpty() }) { a, _ ->
            seconds(a).takeIf { it > 0 }?.let { NoaIntent.Timer(it) }
        },
        act("remind", listOf("reminder", "set_reminder", "remind_me", "create_reminder"), setOf("text"), listOf(Need.TEXT), own = setOf("time"),
            ask = mapOf(Need.TEXT to { t("О чём напомнить?") })) { a, c ->
            // Когда напомнить — из самой фразы (правила дат понимают «через час», «завтра в 10»), а не со слов модели.
            val dt = NoaDateTime.parse(c.phrase, c.now, workHours = true) ?: a["time"]?.let { NoaDateTime.parse(it, c.now, workHours = true) }
            NoaIntent.Remind(a["text"].orEmpty(), dt?.dateTime, dt?.hadTime ?: false)
        },
        act("flashlight", listOf("torch", "light", "flash", "flashlight_on")) { a, _ -> NoaIntent.Flashlight(bool(a["on"], true)) },
        act("phone_settings", listOf("settings", "open_settings")) { a, _ -> NoaIntent.PhoneSettings(a["what"]) },
        act("play_music", listOf("play", "music", "play_song", "play_track", "play_video"), setOf("query"), own = setOf("channel")) { a, _ ->
            play(a["query"].orEmpty(), a["app"], bool(a["playlist"], false), bool(a["artist"], false), bool(a["shuffle"], false), bool(a["video"], false), a["channel"].orEmpty())
        },
        act("media", listOf("player", "media_control", "pause", "resume", "next", "prev", "stop", "louder", "quieter"),
            implies = listOf("pause", "resume", "next", "prev", "stop", "louder", "quieter").associateWith { "control" to it }) { a, _ ->
            control(a["control"] ?: a["command"])?.let { NoaIntent.Media(it) }
        },
        act("close_app", listOf("close", "quit_app"), setOf("app")) { a, _ -> a["app"]?.let { NoaIntent.CloseApp(it) } ?: NoaIntent.GoHome },
        act("go_home", listOf("home", "minimize")) { _, _ -> NoaIntent.GoHome },
        act("open_screen", listOf("open_section", "show_screen"), emptySet()) { a, _ -> section(a["section"])?.let { NoaIntent.OpenScreen(it) } },
        act("lock", listOf("lock_screen", "lock_app")) { _, _ -> NoaIntent.Lock },
        act("backup", listOf("make_backup")) { _, _ -> NoaIntent.Backup },
    )

    private val BY_NAME: Map<String, Spec> = SPECS.flatMap { s -> (listOf(s.name) + s.aliases).map { it to s } }.toMap()

    private fun specOf(name: String?): Spec? = name?.let { BY_NAME[it] }

    /** Синонимы полей: что модель могла назвать иначе. */
    private val FIELD_ALIASES = mapOf(
        "person" to listOf("person", "name", "contact", "recipient", "receiver", "to", "client", "customer", "who", "target", "user", "кому", "человек", "имя", "контакт"),
        "text" to listOf("text", "body", "content", "message", "msg", "note", "comment", "description", "title", "текст"),
        "query" to listOf("query", "q", "search", "keyword", "song", "track", "what", "text", "title", "name", "person", "запрос"),
        "app" to listOf("app", "application", "package", "name", "query", "title"),
        "time" to listOf("time", "at", "when", "datetime", "date_time", "время"),
    )

    private fun strOf(v: Any?): String? = when (v) {
        null, is Map<*, *>, is List<*> -> null
        is Double -> if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
        else -> v.toString().trim().ifBlank { null }
    }

    /** Действие модели → шаг: каноническое имя, поля со сведёнными синонимами. Незнакомое действие → null. */
    private fun toStep(m: Map<*, *>): Step? {
        val raw = nameOf(m) ?: return null
        val spec = specOf(raw) ?: return null
        val args = LinkedHashMap<String, String>()
        for ((k, v) in m) { val key = (k as? String)?.trim()?.lowercase() ?: continue; strOf(v)?.let { args[key] = it } }
        for (f in spec.fields) {
            val keys = (FIELD_ALIASES[f] ?: listOf(f)).filter { it !in spec.own }
            keys.firstNotNullOfOrNull { k -> args[k]?.takeIf { it.isNotBlank() } }?.let { args[f] = it } ?: args.remove(f)
        }
        spec.implies[raw]?.let { (k, v) -> args.putIfAbsent(k, v) }
        return Step(spec.name, args)
    }

    // ---- починка ----

    private sealed interface Out {
        class Ready(val intents: List<NoaIntent>) : Out
        class Ask(val c: Clarify) : Out
    }

    private sealed interface Fix {
        data object Keep : Fix
        data object Drop : Fix
        data class To(val name: String) : Fix
        data class Many(val options: List<String>) : Fix
    }

    private fun digits(s: String) = s.filter { it.isDigit() }

    /** Имя от модели → имя из книжки. Выдуманное (нет ни в книжке, ни во фразе, ни в данных) отбрасываем. */
    private fun fixPerson(raw: String, ctx: Ctx): Fix {
        if (NoaText.looksLikePhone(raw)) return if (digits(ctx.phrase).contains(digits(raw))) Fix.Keep else Fix.Drop
        if (ctx.people.isEmpty()) return Fix.Keep
        val own = NoaMatch.resolve(raw, ctx.people)
        val ph = NoaMatch.fromPhrase(ctx.phrase, ctx.people)
        val inPhrase = NoaMatch.mentions(ctx.phrase, raw)
        return when (own.kind) {
            NoaMatch.Kind.ONE -> if (ph != null && ph !== own.hits[0] && !inPhrase) Fix.To(ph.display) else Fix.To(own.hits[0].display)
            NoaMatch.Kind.MANY -> if (ph != null) Fix.To(ph.display) else Fix.Many(own.hits.take(4).map { it.display })
            NoaMatch.Kind.NONE -> when {
                ph != null -> Fix.To(ph.display)
                inPhrase || ctx.data.contains(raw, ignoreCase = true) -> Fix.Keep
                else -> Fix.Drop
            }
        }
    }

    private fun isPronoun(s: String) = s.isNotBlank() && s.split(" ").all { it.isBlank() || it.lowercase() in NoaParser.PRONOUNS }

    private fun hasPronoun(s: String) = NoaMatch.words(s).any { it in NoaParser.PRONOUNS }

    private fun askText(spec: Spec, need: Need): String = (spec.ask[need] ?: DEFAULT_ASK[need])!!.invoke()

    private val DEFAULT_ASK: Map<Need, () -> String> = mapOf(
        Need.PERSON to { t("Про кого именно?") }, Need.TEXT to { t("Что написать?") }, Need.QUERY to { t("Что искать?") },
        Need.APP to { t("Какое приложение?") }, Need.TIME to { t("На какое время?") }, Need.DURATION to { t("На сколько поставить таймер?") },
    )

    private fun missingOf(spec: Spec, a: Map<String, String>, ctx: Ctx): Need? {
        spec.missing?.invoke(a, ctx)?.let { return it }
        for (n in spec.needs) {
            val ok = when (n) {
                Need.PERSON -> a["person"].orEmpty().let { p -> if (p.isBlank()) hasPronoun(ctx.phrase) && ctx.hasLast else !isPronoun(p) || ctx.hasLast }
                Need.TEXT -> !a["text"].isNullOrBlank()
                Need.QUERY -> !a["query"].isNullOrBlank()
                Need.APP -> !a["app"].isNullOrBlank()
                Need.TIME -> NoaDateTime.parse(a["time"].orEmpty(), ctx.now)?.hadTime == true
                Need.DURATION -> seconds(a) > 0
            }
            if (!ok) return n
        }
        return null
    }

    /**
     * Чиним цепочку по шагам: данные из фразы (канал, время, длительность, текст), человек (местоимение — прошлый шаг,
     * имя — по книжке), обязательные поля. Чего-то нет — вопрос; шаг не собрался — отбрасываем.
     */
    private fun process(steps: List<Step>, ctx: Ctx, round: Int = 1): Out {
        val fixed = ArrayList<Step>()
        var prior = ""
        for ((i, st) in steps.withIndex()) {
            val spec = specOf(st.action) ?: continue
            val a = LinkedHashMap(st.args)
            a.putAll(spec.prefill(a, ctx))
            if (spec.hasPerson) {
                val raw = a["person"].orEmpty().trim()
                when {
                    raw.isBlank() || isPronoun(raw) -> if (prior.isNotBlank() && spec.inherit(a)) a["person"] = prior
                    else -> when (val f = fixPerson(raw, ctx)) {
                        Fix.Keep -> {}
                        Fix.Drop -> a["person"] = ""
                        is Fix.To -> a["person"] = f.name
                        is Fix.Many -> {
                            val all = fixed + Step(st.action, a) + steps.drop(i + 1)
                            return Out.Ask(Clarify(t("Кого именно: %1\$s?", f.options.joinToString(", ")), Need.PERSON, all, i, f.options, ctx.phrase, round))
                        }
                    }
                }
                a["person"]?.takeIf { it.isNotBlank() && !isPronoun(it) }?.let { prior = it }
            }
            val need = missingOf(spec, a, ctx)
            if (need != null) {
                val all = fixed + Step(st.action, a) + steps.drop(i + 1)
                return Out.Ask(Clarify(askText(spec, need), need, all, i, emptyList(), ctx.phrase, round))
            }
            fixed += Step(st.action, a)
        }
        return Out.Ready(fixed.mapNotNull { st -> specOf(st.action)?.let { runCatching { it.build(st.args, ctx) }.getOrNull() } })
    }

    /** Подставляем ответ человека в шаг, которому чего-то не хватало, и чиним цепочку заново. null — ответ не годится. */
    private fun fill(c: Clarify, answer: String, ctx: Ctx): Out? {
        val step = c.steps.getOrNull(c.index) ?: return null
        val spec = specOf(step.action) ?: return null
        val need = c.need ?: return null
        val a = LinkedHashMap(step.args)
        when (need) {
            Need.PERSON -> {
                val pool = if (c.options.isNotEmpty()) NoaMatch.refs(c.options) else ctx.people
                val cleaned = cleanName(answer)
                val ord = ordinal(answer)
                val byOrdinal = if (c.options.isNotEmpty() && ord != null) c.options.getOrNull(ord)?.let { o -> NoaMatch.Result(NoaMatch.Kind.ONE, listOf(NoaMatch.ref(o))) } else null
                val r = byOrdinal ?: NoaMatch.fromPhrase(answer, pool)?.let { NoaMatch.Result(NoaMatch.Kind.ONE, listOf(it)) } ?: NoaMatch.resolve(cleaned, pool)
                when (r.kind) {
                    NoaMatch.Kind.ONE -> a["person"] = r.hits[0].display
                    NoaMatch.Kind.MANY -> return if (c.round >= 2) null else Out.Ask(Clarify(t("Кого именно: %1\$s?", r.hits.take(4).joinToString(", ") { it.display }), Need.PERSON,
                        c.steps.toMutableList().also { it[c.index] = Step(step.action, a) }, c.index, r.hits.take(4).map { it.display }, c.phrase, c.round + 1))
                    NoaMatch.Kind.NONE -> if (c.options.isEmpty() && looksLikeName(cleaned)) a["person"] = cleaned else return null
                }
            }
            Need.TEXT -> a["text"] = answer.trim().trim('"', '«', '»').ifBlank { return null }
            Need.QUERY, Need.APP -> a[spec.fillKey[need] ?: need.name.lowercase()] = answer.trim().trim('"', '«', '»').ifBlank { return null }
            Need.TIME -> {
                // «на 6 утра», «семь тридцать»: даём разборщику времени шанс и с «в» впереди.
                val bare = answer.trim().replace(Regex("^(?:на|к|в|о|об|at|by)\\s+", RegexOption.IGNORE_CASE), "")
                a["time"] = listOf(answer, "в $bare").firstOrNull { NoaDateTime.parse(it, ctx.now)?.hadTime == true } ?: answer
            }
            Need.DURATION -> {
                val s = durationSecs(answer) ?: Regex("^\\s*(\\d{1,3})\\s*$").find(answer)?.let { it.groupValues[1].toInt() * 60 } ?: return null
                a["hours"] = "0"; a["minutes"] = (s / 60).toString(); a["seconds"] = (s % 60).toString()
            }
        }
        val steps = c.steps.toMutableList().also { it[c.index] = Step(step.action, a) }
        // Шаги до вопроса уже починены; повторная починка идемпотентна. Фраза = прежняя + ответ: оттуда берутся время и канал.
        val out = process(steps, ctx, c.round + 1)
        return if (out is Out.Ask && c.round >= 2) null else out
    }

    private val NAME_FILLERS = setOf("для", "кому", "это", "пусть", "нужно", "надо", "ну", "да", "давай", "к", "по", "про", "о", "об", "про", "ей", "ему", "пожалуйста", "будь", "ласка", "будь-ласка", "напиши", "позвони", "звони", "позвонить", "написать", "скажи", "мне", "для")

    private fun cleanName(answer: String) = NoaMatch.words(answer).filter { it !in NAME_FILLERS }.joinToString(" ")

    private fun looksLikeName(s: String) = s.isNotBlank() && s.split(" ").size <= 3 && s.all { it.isLetter() || it == ' ' }

    private fun ordinal(s: String): Int? {
        val w = NoaMatch.words(s)
        return when {
            w.any { it in setOf("первый", "первого", "первую", "перший", "першого", "first") } || s.trim() == "1" -> 0
            w.any { it in setOf("второй", "второго", "второе", "другой", "другого", "другий", "другу", "другого", "другую", "другому", "другая", "другое", "другий", "другогo", "second", "другой") } || s.trim() == "2" -> 1
            w.any { it in setOf("третий", "третьего", "третій", "третього", "third") } || s.trim() == "3" -> 2
            else -> null
        }
    }

    // ---- мелкие разборы ----

    private fun bool(v: String?, def: Boolean): Boolean = when (v?.trim()?.lowercase()) {
        "true", "yes", "on", "1", "да", "так" -> true
        "false", "no", "off", "0", "нет", "ні" -> false
        else -> def
    }

    private fun num(v: String?): Int = v?.let { Regex("\\d+").find(it)?.value?.toIntOrNull() } ?: 0

    private fun seconds(a: Map<String, String>) = num(a["hours"]) * 3600 + num(a["minutes"]) * 60 + num(a["seconds"])

    /** «любую песню X» — модель иногда оставляет эти слова в запросе: это исполнитель. */
    private fun play(query: String, app: String?, playlist: Boolean, artist: Boolean, shuffle: Boolean, video: Boolean, channel: String = ""): NoaIntent.Play {
        val any = Regex("^(?:любую|любой|какую-нибудь|будь-яку|будь-який|якусь)\\s+(?:песню|трек|музыку|пісню|музику)\\s+", RegexOption.IGNORE_CASE)
        val q = query.trim()
        val m = any.find(q)
        return if (m != null) NoaIntent.Play(q.substring(m.range.last + 1).trim(), app, playlist, true, shuffle, video, channel)
        else NoaIntent.Play(q, app, playlist, artist, shuffle, video, channel)
    }

    private fun day(day: String?, userText: String, now: LocalDateTime): LocalDate {
        NoaDateTime.parse(userText, now)?.takeIf { it.hadDate }?.let { return it.dateTime.toLocalDate() }
        val today = now.toLocalDate()
        val d = day?.lowercase()?.trim() ?: return today
        runCatching { return LocalDate.parse(d) }
        return when (d) {
            "tomorrow", "завтра" -> today.plusDays(1)
            "after_tomorrow", "day_after_tomorrow", "послезавтра", "післязавтра" -> today.plusDays(2)
            "yesterday", "вчера", "вчора" -> today.minusDays(1)
            else -> NoaDateTime.parse(d, now)?.takeIf { it.hadDate }?.dateTime?.toLocalDate() ?: today
        }
    }

    private fun placeKind(s: String?): PlaceKind? = when (s?.lowercase()?.trim()) {
        "home", "дом", "домой", "додому", "дім" -> PlaceKind.HOME
        "work", "работа", "на работу", "робота", "на роботу" -> PlaceKind.WORK
        else -> null
    }

    private fun navApp(s: String?): String? {
        val a = s?.lowercase() ?: return null
        return when {
            "waze" in a || "вейз" in a -> "waze"
            "google" in a || "гугл" in a -> "google"
            "yandex" in a || "яндекс" in a -> "yandex"
            "organic" in a || "органик" in a -> "organic"
            else -> null
        }
    }

    private fun control(s: String?): NoaMedia.Control? = when (s?.lowercase()?.trim()?.replace(Regex("[\\s-]+"), "_")) {
        "pause" -> NoaMedia.Control.PAUSE
        "resume", "play", "continue", "unpause" -> NoaMedia.Control.RESUME
        "next", "skip" -> NoaMedia.Control.NEXT
        "prev", "previous", "back" -> NoaMedia.Control.PREV
        "shuffle_on", "shuffle" -> NoaMedia.Control.SHUFFLE_ON
        "shuffle_off" -> NoaMedia.Control.SHUFFLE_OFF
        "repeat" -> NoaMedia.Control.REPEAT
        "stop" -> NoaMedia.Control.STOP
        "louder", "volume_up" -> NoaMedia.Control.LOUDER
        "quieter", "volume_down" -> NoaMedia.Control.QUIETER
        "what", "now_playing" -> NoaMedia.Control.WHAT
        else -> null
    }

    private fun contactType(s: String?): ContactType? = when (s?.lowercase()) {
        "instagram" -> ContactType.INSTAGRAM
        "facebook" -> ContactType.FACEBOOK
        "viber" -> ContactType.VIBER
        "email", "mail" -> ContactType.EMAIL
        "website", "site" -> ContactType.WEBSITE
        "vk" -> ContactType.VK
        "telegram" -> ContactType.TELEGRAM
        "whatsapp" -> ContactType.WHATSAPP
        "phone" -> ContactType.PHONE
        else -> null
    }

    private fun channel(s: String?): NoaIntent.Channel = when (s?.lowercase()?.trim()) {
        "telegram", "тг", "телеграм", "tg", "телеграмм" -> NoaIntent.Channel.TELEGRAM
        "sms", "смс" -> NoaIntent.Channel.SMS
        else -> NoaIntent.Channel.WHATSAPP
    }

    /** Канал, названный в самой фразе («в телеграм», «смской»): он важнее догадки модели. */
    internal fun channelFrom(phrase: String): String? {
        val w = NoaMatch.words(phrase)
        return when {
            w.any { it.startsWith("telegram") || it.startsWith("телеграм") || it.startsWith("телег") || it == "тг" || it == "tg" } -> "telegram"
            w.any { it == "sms" || it.startsWith("смс") || it.startsWith("эсэмэс") } -> "sms"
            w.any { it.startsWith("whatsapp") || it.startsWith("ватсап") || it.startsWith("вотсап") || it.startsWith("вацап") || it.startsWith("вотс") || it.startsWith("ватс") } -> "whatsapp"
            else -> null
        }
    }

    private val WRITE_VERBS = Regex("^(?:напиши|напишіть|отправь|відправ\\S*|надішли|надішліть|скажи|скажіть|передай|передайте|пошли|send|tell|write)(?:\\s|$)", RegexOption.IGNORE_CASE)

    /** Текст сообщения из фразы: «напиши Ане, что опоздаю» → «опоздаю». */
    internal fun textFromPhrase(phrase: String): String? {
        if (!WRITE_VERBS.containsMatchIn(phrase.trim())) return null
        return Regex("(?:^|\\s)(?:что|що|that|:)\\s*(.+)$", RegexOption.IGNORE_CASE).find(phrase)?.groupValues?.get(1)?.trim()
            ?.takeIf { it.length >= 2 }
    }

    /** Модель иногда кладёт в text всю команду («напиши Ане что опоздаю»): оставляем сказанное после «что». */
    internal fun cleanMessageText(text: String): String? {
        val s = text.trim()
        if (!WRITE_VERBS.containsMatchIn(s)) return s
        return Regex("(?:^|\\s)(?:что|що|that)\\s+(.+)$", RegexOption.IGNORE_CASE).find(s)?.groupValues?.get(1)?.trim() ?: s
    }

    private fun section(s: String?): NoaIntent.Section? = when (s?.lowercase()) {
        "calendar", "календарь" -> NoaIntent.Section.CALENDAR
        "map", "карта" -> NoaIntent.Section.MAP
        "broadcast", "рассылка" -> NoaIntent.Section.BROADCAST
        "settings", "настройки" -> NoaIntent.Section.SETTINGS
        "services", "услуги" -> NoaIntent.Section.SERVICES
        "people", "люди" -> NoaIntent.Section.PEOPLE
        else -> null
    }

    /** Длительность из фразы в секундах: «5 минут», «1 час 30 минут», «полчаса»; смежные части суммируются. */
    internal fun durationSecs(text: String): Int? {
        val s = text.lowercase().replace(',', '.')
        if (Regex("пол\\s?часа|півгодини|half an hour").containsMatchIn(s)) return 1800
        val rx = Regex("(\\d+(?:\\.\\d+)?|[\\p{L}'’ʼ]+)\\s*(час\\p{L}*|годин\\p{L}*|hour\\p{L}*|мин\\p{L}*|хв\\p{L}*|min\\p{L}*|сек\\p{L}*|sec\\p{L}*)")
        var total = 0.0
        var end = -1
        for (m in rx.findAll(s)) {
            if (end >= 0 && m.range.first - end > 4) break
            val n = m.groupValues[1].toDoubleOrNull() ?: NUM_WORDS[m.groupValues[1]] ?: continue
            val u = m.groupValues[2]
            total += n * when { u.startsWith("час") || u.startsWith("годин") || u.startsWith("hour") -> 3600; u.startsWith("сек") || u.startsWith("sec") -> 1 else -> 60 }
            end = m.range.last + 1
        }
        if (total <= 0) {
            if (Regex("(?:на|через|за|in|for)\\s+(?:час|годину|hour)(?![\\p{L}])").containsMatchIn(s)) return 3600
            return null
        }
        return total.toInt()
    }

    private val NUM_WORDS = mapOf(
        "один" to 1.0, "одну" to 1.0, "одна" to 1.0, "две" to 2.0, "два" to 2.0, "дві" to 2.0, "три" to 3.0, "четыре" to 4.0, "чотири" to 4.0,
        "пять" to 5.0, "п'ять" to 5.0, "пʼять" to 5.0, "шесть" to 6.0, "шість" to 6.0, "семь" to 7.0, "сім" to 7.0, "восемь" to 8.0, "вісім" to 8.0,
        "девять" to 9.0, "дев'ять" to 9.0, "десять" to 10.0, "пятнадцать" to 15.0, "п'ятнадцять" to 15.0, "двадцать" to 20.0, "двадцять" to 20.0,
        "тридцать" to 30.0, "тридцять" to 30.0, "сорок" to 40.0, "пятьдесят" to 50.0, "п'ятдесят" to 50.0,
    )

    // ---- промпт ----

    internal fun prompt(user: String, now: LocalDateTime, names: List<String>, context: String, history: String, pending: Clarify?): String {
        val phrase = user.replace("\"", "'").replace(Regex("\\s+"), " ").trim().take(NoaContext.COMMAND_MAX)
        val date = "%04d-%02d-%02d %s".format(now.year, now.monthValue, now.dayOfMonth, now.dayOfWeek.name.lowercase())
        val said0 = if (pending != null) pending.phrase + " " + user else user
        // Три самых похожих проверенных примера вместо статичных: модель повторяет форму ответа (поля, действие, краткость).
        val examples = NoaExamples.pick(said0, EXAMPLES_K, names).map { it.line }.filter { it.length <= EXAMPLE_MAX }
        val stat = staticPrompt(langFor(said0)) + if (examples.isEmpty()) "" else "\nExamples:\n" + examples.joinToString("\n")
        val tail = "\nToday: $date\nCommand: \"$phrase\"\nJSON:"
        val room = NoaContext.TOTAL_CHARS - stat.length - tail.length - 1
        // Названные во фразе люди — первыми: если список режется, их имена останутся.
        val said = NoaMatch.words(user).map(NoaMatch::stem)
        val ordered = names.sortedBy { n -> if (NoaMatch.words(n).any { w -> said.any { NoaMatch.stemStrict(it, NoaMatch.stem(w)) } }) 0 else 1 }
        val earlier = pending?.let { "Earlier:\nUser: ${it.phrase.take(160)}\nNoa asked: ${it.question.take(100)}" }
            ?: history.trim().takeIf { it.isNotBlank() }?.let { "Earlier:\n" + it.take(220) }
        val people = "People: " + ordered.take(12).joinToString(", ").ifBlank { "—" }
        // Данные не должны съесть весь остаток: имена людей нужны, чтобы модель писала их так, как в книжке.
        val body = NoaContext.fit(listOfNotNull(
            NoaContext.Block(people, 3, max = 200),
            context.takeIf { it.isNotBlank() }?.let { NoaContext.Block("Data:\n$it", 2, max = maxOf(150, room - minOf(people.length, 130))) },
            earlier?.let { NoaContext.Block(it, if (pending != null) 1 else 4, max = 280) },
        ), room)
        return stat + "\n" + body + tail
    }

    companion object {
        private val CHAT = setOf("chat", "none", "talk", "answer_only")
        private const val MAX_ACTIONS = 5
        /** Украинские слова без «і/ї/є/ґ»: «що в мене завтра». */
        private val UK_WORDS = setOf("що", "як", "чи", "де", "коли", "мене", "мені", "ще", "яка", "який", "дякую")

        /** Язык ответа — по самой фразе (украинские буквы), иначе язык интерфейса. */
        internal fun langFor(user: String): String = when {
            user.any { it in "іїєґІЇЄҐ" } ||
                user.lowercase().split(Regex("[^\\p{L}]+")).any { it in UK_WORDS } -> "Ukrainian"
            user.any { it in 'а'..'я' || it in 'А'..'Я' } -> "Russian"
            else -> runCatching {
                when (com.kartoteka.app.i18n.I18n.lang) {
                    com.kartoteka.app.i18n.UiLang.UK -> "Ukrainian"
                    com.kartoteka.app.i18n.UiLang.EN -> "English"
                    else -> "Russian"
                }
            }.getOrDefault("Russian")
        }

        /** Сколько похожих примеров кладём в промпт и сколько символов максимум в одном. */
        internal const val EXAMPLES_K = 3
        internal const val EXAMPLE_MAX = 170

        /**
         * Неизменная часть промпта (без примеров, списка людей, данных и самой фразы). Окно у модели маленькое, поэтому
         * правила — по-английски и очень коротко; примеры добавляет [prompt] из библиотеки по сходству с командой.
         * Модель просим отвечать только компактным JSON: чем короче ответ, тем быстрее он на процессоре.
         */
        internal fun staticPrompt(lang: String = "Russian"): String = """
JSON only, compact: {"actions":[{"action":"…"}]}. Add "reply" only for chat/answers, "ask" only to clarify.
Actions: call message(channel,text) reply read_messages create_appointment(service) cancel_appointment delete_appointment move_appointment add_note open_person select favorite find(query) person_info open_contact share_data route agenda play_music(query) media(control) launch_app(app) close_app(app) web_search(query) remind alarm timer flashlight phone_settings open_screen(section) go_home lock backup
open_screen=section of this app; launch_app=phone app; close_app=named app; go_home=close, no name; lock=lock this app. find=search the book; person_info=question about a person. move_appointment is ONE action. Time, math, apps are not web_search.
Rules: person only if the user said a name or pronoun; never copy names from People. Date/time are read from the command. Several commands → actions in order. About people or appointments answer ONLY from Data; not in Data → say it is not in the book; never invent. Unclear → actions [] + ask. Chat → actions [] + reply. reply, ask: $lang, max 2 sentences.
""".trim()
    }
}

/**
 * Что сказать, когда модель промолчала или вернула мусор: не «не поняла», а две подсказки по теме фразы
 * («запиши Анну на завтра…» — если речь о записях, «позвони маме» — если о звонках).
 */
object NoaFallback {
    private fun has(s: String, vararg keys: String) = keys.any { s.contains(it) }

    /** Две подсказки (уже на языке интерфейса) под тему фразы. */
    fun suggestions(phrase: String): Pair<String, String> {
        val s = " " + phrase.lowercase().replace('ё', 'е') + " "
        return when {
            has(s, "замет", "нотат", "запомн", "хроник") -> t("Добавь Ане заметку: любит кофе") to t("Что в заметках про Аню?")
            has(s, "запис", "запиш", "встреч", "зустрі", "приём", "прием", "приема", "прийом", "календар", "расписан", "розклад", "перенес", "отмен", "скасу") ->
                t("Запиши Анну на завтра в 12:00") to t("Что у меня завтра?")
            has(s, "напиш", "сообщ", "отправ", "ответ", "повідом", "надішл", "відпов", "скаж") -> t("Напиши Ане в телеграм, что опоздаю") to t("Прочитай новые сообщения")
            has(s, "позвон", "звон", "набер", "телефон", "дозвон", "дзвін", "дзвон", "номер") -> t("Позвони маме") to t("Какой телефон у Ани?")
            has(s, "рожден", "рождень", "народж", "birthday") -> t("У кого скоро день рождения?") to t("Когда день рождения у Олега?")
            has(s, "музык", "песн", "включи", "играй", "плейлист", "трек", "пісн", "увімкни", "музик") -> t("Включи музыку Скриптонит") to t("Поставь на паузу")
            has(s, "маршрут", "дорог", "поеха", "навигац", "веди", "проложи", "проклад") -> t("Проложи маршрут домой") to t("Веди на Крещатик 22")
            has(s, "будильн", "таймер", "разбуд", "напомн", "нагада", "розбуд") -> t("Поставь таймер на 5 минут") to t("Разбуди меня в 7:30")
            has(s, "найд", "найти", "покаж", "карточ", "контакт", "знайд", "інформ", "информ", "расскаж", "розкаж", "кто ", "хто ") -> t("Найди Марию") to t("Расскажи про Олега")
            else -> t("Что у меня сегодня?") to t("Позвони маме")
        }
    }

    fun message(phrase: String): String = suggestions(phrase).let { (a, b) -> t("Не поняла, скажите иначе — например: «%1\$s» или «%2\$s».", a, b) }
}



/**
 * Снисходительный разбор JSON от маленькой модели: висячие и пропущенные запятые, ключи без кавычек,
 * строки в одинарных кавычках, «умные» кавычки, кавычки внутри текста, обрезанный конец ответа.
 * Объект → Map, массив → List, число → Double. Ничего не бросает наружу.
 */
internal object Lenient {
    private class Fail : RuntimeException()
    private object Cut // строка оборвалась на конце ответа

    /** Первый разбираемый объект или массив в тексте (игнорируя ```json и пояснения вокруг). */
    fun extract(text: String): Any? {
        val s = text.replace('“', '"').replace('”', '"').replace('„', '"').replace('‟', '"').replace('″', '"')
        var from = 0
        repeat(12) {
            val start = s.indexOfAny(charArrayOf('{', '['), from)
            if (start < 0) return null
            val v = runCatching { Reader(s, start).value() }.getOrNull()
            if (v is Map<*, *> && v.isNotEmpty()) return v
            if (v is List<*> && v.any { it is Map<*, *> }) return v
            from = start + 1
        }
        return null
    }

    private class Reader(val s: String, var i: Int) {
        private fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
        private fun ws2() { while (i < s.length && (s[i].isWhitespace() || s[i] == ',')) i++ }

        fun value(): Any? {
            ws()
            if (i >= s.length) throw Fail()
            return when (s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"', '\'' -> str(s[i])
                else -> bare()
            }
        }

        private fun obj(): Map<String, Any?> {
            i++
            val m = LinkedHashMap<String, Any?>()
            while (true) {
                ws2()
                if (i >= s.length) return m // обрезанный ответ: закрываем сами
                if (s[i] == '}') { i++; return m }
                if (s[i] == ']') throw Fail()
                val key = when (s[i]) {
                    '"', '\'' -> str(s[i]) as? String ?: return m
                    else -> ident()
                }
                ws()
                if (i < s.length && (s[i] == ':' || s[i] == '=')) i++ else throw Fail()
                ws()
                if (i >= s.length) return m
                val v = value()
                if (v === Cut) return m
                m[key] = v
            }
        }

        private fun arr(): List<Any?> {
            i++
            val l = ArrayList<Any?>()
            while (true) {
                ws2()
                if (i >= s.length) return l
                if (s[i] == ']') { i++; return l }
                if (s[i] == '}') throw Fail()
                val v = value()
                if (v === Cut) return l
                l += v
            }
        }

        private fun ident(): String {
            val b = i
            while (i < s.length && (s[i].isLetterOrDigit() || s[i] == '_' || s[i] == '-')) i++
            if (i == b) throw Fail()
            return s.substring(b, i)
        }

        /** Кавычка закрывает строку, только если за ней идёт разделитель: так «"скажи "привет""» не ломает разбор. */
        private fun str(q: Char): Any {
            i++
            val b = StringBuilder()
            while (i < s.length) {
                val c = s[i]
                if (c == '\\' && i + 1 < s.length) {
                    val e = s[i + 1]
                    i += 2
                    when (e) {
                        'n' -> b.append('\n'); 't' -> b.append('\t'); 'r' -> {}
                        'u' -> { s.substring(i, minOf(i + 4, s.length)).toIntOrNull(16)?.let { b.append(it.toChar()); i += 4 } ?: b.append("\\u") }
                        else -> b.append(e)
                    }
                    continue
                }
                if (c == q) {
                    var j = i + 1
                    while (j < s.length && s[j].isWhitespace()) j++
                    if (j >= s.length || s[j] in ",:}]") { i++; return b.toString() }
                }
                b.append(c); i++
            }
            return Cut
        }

        private fun bare(): Any? {
            val b = i
            while (i < s.length && s[i] !in ",}]\n") i++
            val t = s.substring(b, i).trim()
            if (t.isEmpty()) throw Fail()
            return when (t.lowercase()) {
                "true" -> true; "false" -> false; "null", "none" -> null
                else -> t.toDoubleOrNull() ?: t
            }
        }
    }
}
