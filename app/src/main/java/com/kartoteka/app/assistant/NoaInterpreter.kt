package com.kartoteka.app.assistant

import com.kartoteka.app.data.ContactType
import com.kartoteka.app.data.PlaceKind
import java.time.LocalDate
import java.time.LocalDateTime

/** Результат понимания фразы мозгом: действие (или null = свободный разговор) и реплика голосом. */
data class Interpreted(val intent: NoaIntent?, val reply: String?)

/**
 * Превращает свободную речь в команду через небольшую модель на телефоне. Промпт просит модель вернуть
 * строго JSON {"actions":[…],"reply":"…"}; дату/время всё равно разбираем сами ([NoaDateTime]) —
 * это надёжнее, чем доверять счёт модели. Ответ модели читаем снисходительно (см. [Lenient]):
 * обёртки ```json, лишний текст, висячие запятые, «умные» кавычки, обрезанный конец.
 * Любой сбой → null, и вызывающий переходит на быстрые команды.
 */
class NoaInterpreter(private val brain: LlmBrain?) {

    suspend fun interpret(userText: String, now: LocalDateTime = LocalDateTime.now(), names: List<String> = emptyList(), context: String = "", history: String = ""): Interpreted? {
        val raw = brain?.ask(prompt(userText, now, names, context, history)) ?: return null
        return runCatching { fromJson(raw, userText, now) }.getOrNull()
    }

    /** Разбор ответа модели в команду (чистая логика, тестируется отдельно). Никогда не бросает исключений. */
    fun fromJson(raw: String, userText: String, now: LocalDateTime = LocalDateTime.now()): Interpreted? =
        runCatching { parse(raw, userText, now) }.getOrNull()

    private fun parse(raw: String, userText: String, now: LocalDateTime): Interpreted? {
        val root = Lenient.extract(raw) ?: return null
        val obj = root as? Map<*, *>
        val reply = (obj?.get("reply") as? String)?.trim()?.ifBlank { null }
        // Список действий: массив, один объект вместо массива, {"call":{…}}, голый массив без обёртки.
        val list: List<Map<*, *>> = when (val a = obj?.get("actions") ?: obj?.get("steps") ?: root.takeIf { it is List<*> }) {
            is List<*> -> a.mapNotNull(::asAction)
            is Map<*, *> -> if (nameOf(a) != null) listOf(a)
                else a.entries.mapNotNull { (k, v) -> (k as? String)?.let { named(v as? Map<*, *> ?: emptyMap<String, Any?>(), it) } }
            is String -> listOfNotNull(obj?.let { named(it, a) })
            else -> listOfNotNull(obj?.takeIf { nameOf(it) != null })
        }
        var person = ""
        val steps = list.mapNotNull { a ->
            val step = runCatching { action(a, userText, now) }.getOrNull() ?: return@mapNotNull null
            // «ей/йому» от модели — тот же человек, что в прошлом шаге.
            val p = NoaParser.personOf(step).split(" ").filter { it.isNotBlank() && it.lowercase() !in NoaParser.PRONOUNS }.joinToString(" ")
            if (p.isNotBlank()) { person = p; NoaParser.withPerson(step, p) } else NoaParser.withPerson(step, person)
        }
        return when {
            steps.size > 1 -> Interpreted(NoaIntent.Sequence(steps), reply)
            steps.size == 1 -> Interpreted(steps[0], reply)
            reply != null -> Interpreted(null, reply)
            // {"action":"chat"} без реплики — тоже разговор, просто молча.
            list.any { nameOf(it) in CHAT } || (obj != null && nameOf(obj) in CHAT) -> Interpreted(null, null)
            else -> null
        }
    }

    private fun named(m: Map<*, *>, name: String): Map<*, *> = HashMap<Any?, Any?>(m).apply { put("action", name) }

    private fun asAction(v: Any?): Map<*, *>? = when (v) {
        is Map<*, *> -> v
        is String -> mapOf("action" to v) // ["go_home"]
        else -> null
    }

    private fun nameOf(o: Map<*, *>): String? = (o["action"] ?: o["intent"] ?: o["type"])?.toString()
        ?.trim()?.lowercase()?.replace(Regex("[\\s-]+"), "_")?.ifBlank { null }

    private fun action(o: Map<*, *>, userText: String, now: LocalDateTime): NoaIntent? {
        val action = nameOf(o) ?: return null
        fun str(key: String): String? = when (val v = o[key]) {
            null -> null
            is Map<*, *>, is List<*> -> null
            is Double -> (if (v % 1.0 == 0.0) v.toLong().toString() else v.toString())
            else -> v.toString().trim().ifBlank { null }
        }
        fun bool(key: String, def: Boolean): Boolean = when (val v = o[key]) {
            is Boolean -> v
            is Double -> v != 0.0
            is String -> when (v.trim().lowercase()) { "true", "yes", "on", "1", "да", "так" -> true; "false", "no", "off", "0", "нет", "ні" -> false; else -> def }
            else -> def
        }
        fun num(key: String): Int = when (val v = o[key]) {
            is Double -> v.toInt()
            is String -> Regex("\\d+").find(v)?.value?.toIntOrNull() ?: 0
            else -> 0
        }
        val person = str("person") ?: str("name") ?: ""
        return when (action) {
            "create_appointment", "book", "appointment" -> {
                val dt = NoaDateTime.parse(userText, now)
                NoaIntent.CreateAppointment(person, dt?.dateTime, dt?.hadTime ?: false, str("service")?.lowercase(), false)
            }
            "cancel_appointment", "delete_appointment" -> NoaIntent.CancelAppointment(
                person, NoaDateTime.parse(userText, now)?.takeIf { it.hadDate }?.dateTime?.toLocalDate(),
                delete = action == "delete_appointment",
            )
            "move_appointment" -> NoaDateTime.parse(userText, now).let { dt ->
                NoaIntent.MoveAppointment(person, dt?.dateTime, dt?.hadDate ?: false, dt?.hadTime ?: false)
            }
            "call" -> NoaIntent.Call(person)
            "message", "send_message", "write" -> {
                // «Отправь ему об этом» — текст из шаблона подтверждения, а не выдумка модели.
                val about = NoaParser.isAboutAppointment(userText) || bool("about_appointment", false)
                NoaIntent.Message(person, channel(str("channel")), if (about) null else str("text"), aboutAppointment = about)
            }
            "reply", "answer" -> NoaIntent.Reply(person, str("text").orEmpty())
            "read_messages", "read" -> NoaIntent.ReadMessages(person, bool("wait", false))
            "add_note", "note" -> NoaIntent.AddNote(person, str("text") ?: str("note") ?: "")
            "open_person", "open" -> NoaIntent.Open(person)
            "find", "search" -> (str("query") ?: person.ifBlank { null })?.let { NoaIntent.Find(it) }
            "open_contact" -> contactType(str("contact"))?.let { NoaIntent.OpenContact(person, it) }
            "route", "navigate" -> {
                // Старый формат: place = home|work. Новый: place — любой адрес, kind — дом/работа человека.
                val place = str("place").orEmpty()
                val kind = placeKind(str("kind")) ?: placeKind(place)
                NoaIntent.Route(person, kind, navApp(str("app")), if (placeKind(place) != null) "" else place)
            }
            "agenda", "plan" -> NoaIntent.Agenda(day(str("day"), userText, now))
            "person_info", "info" -> NoaIntent.PersonInfo(person, when (str("topic")?.lowercase()) {
                "birthday" -> NoaIntent.Topic.BIRTHDAY
                "phone" -> NoaIntent.Topic.PHONE
                "address" -> NoaIntent.Topic.ADDRESS
                else -> NoaIntent.Topic.SUMMARY
            }, str("question") ?: userText)
            "favorite" -> NoaIntent.Favorite(person, bool("on", true))
            "select" -> NoaIntent.Select(person)
            "share_data", "transfer" -> NoaIntent.ShareData(person, when (str("data")?.lowercase()) {
                "notes", "note" -> NoaIntent.Data.NOTES; "phone" -> NoaIntent.Data.PHONE; "address" -> NoaIntent.Data.ADDRESS
                "email" -> NoaIntent.Data.EMAIL; "birthday" -> NoaIntent.Data.BIRTHDAY; else -> NoaIntent.Data.CARD
            }, when (val to = str("to")?.lowercase()) {
                null, "share" -> "share"; "notes", "notepad" -> "notes"; "google", "search" -> "google"; "clipboard" -> "clipboard"
                else -> "app:$to"
            })
            "launch_app", "open_app" -> str("app")?.let { NoaIntent.LaunchApp(it) }
            "web_search", "google" -> str("query")?.let { NoaIntent.WebSearch(it) }
            "alarm" -> NoaDateTime.parse(str("time") ?: userText, now)?.takeIf { it.hadTime }
                ?.let { NoaIntent.Alarm(it.dateTime.hour, it.dateTime.minute, str("label")) }
            "timer" -> (num("hours") * 3600 + num("minutes") * 60 + num("seconds")).takeIf { it > 0 }?.let { NoaIntent.Timer(it) }
            "flashlight" -> NoaIntent.Flashlight(bool("on", true))
            "phone_settings", "settings" -> NoaIntent.PhoneSettings(str("what"))
            "play_music", "play", "music" -> play(str("query").orEmpty(), str("app"), bool("playlist", false), bool("artist", false),
                bool("shuffle", false), bool("video", false))
            "media", "player" -> control(str("control") ?: str("command"))?.let { NoaIntent.Media(it) }
            "pause", "resume", "next", "prev", "stop", "louder", "quieter" -> control(action)?.let { NoaIntent.Media(it) }
            "go_home", "home", "close_app", "minimize" -> NoaIntent.GoHome
            "open_screen" -> section(str("section"))?.let { NoaIntent.OpenScreen(it) }
            "lock" -> NoaIntent.Lock
            "backup" -> NoaIntent.Backup
            // chat и всё незнакомое — не действие: пропускаем.
            else -> null
        }
    }

    /** «любую песню Скриптонита» — модель иногда оставляет эти слова в запросе: это исполнитель. */
    private fun play(query: String, app: String?, playlist: Boolean, artist: Boolean, shuffle: Boolean, video: Boolean): NoaIntent.Play {
        val any = Regex("^(?:любую|любой|какую-нибудь|будь-яку|будь-який|якусь)\\s+(?:песню|трек|музыку|пісню|музику)\\s+", RegexOption.IGNORE_CASE)
        val q = query.trim()
        val m = any.find(q)
        return if (m != null) NoaIntent.Play(q.substring(m.range.last + 1).trim(), app, playlist, true, shuffle, video)
        else NoaIntent.Play(q, app, playlist, artist, shuffle, video)
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

    private fun channel(s: String?): NoaIntent.Channel = when (s?.lowercase()) {
        "telegram", "тг", "телеграм" -> NoaIntent.Channel.TELEGRAM
        "sms", "смс" -> NoaIntent.Channel.SMS
        else -> NoaIntent.Channel.WHATSAPP
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

    private fun prompt(user: String, now: LocalDateTime, names: List<String>, context: String = "", history: String = ""): String {
        val people = names.take(if (context.isBlank()) 30 else 15).joinToString(", ").ifBlank { "—" }
        val data = if (context.isBlank()) "" else "\nData:\n$context"
        // Пара последних реплик — чтобы модель поняла «а ему то же», «а когда?»; обрезаем, чтобы не раздувать окно.
        val hist = history.trim().takeIf { it.isNotBlank() }?.let { "\nEarlier:\n${it.take(240)}" }.orEmpty()
        val date = "%04d-%02d-%02d %s".format(now.year, now.monthValue, now.dayOfMonth, now.dayOfWeek.name.lowercase())
        return staticPrompt(langFor(user)) +
            "\nPeople: $people$data$hist\nToday: $date\nCommand: \"${user.replace("\"", "'")}\"\nJSON:"
    }

    companion object {
        private val CHAT = setOf("chat", "none", "talk", "answer_only")
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

        /**
         * Неизменная часть промпта (без списка людей, данных и самой фразы). Окно у модели маленькое,
         * поэтому правила — по-английски и коротко (так дешевле в токенах), примеры — на русском и украинском.
         */
        internal fun staticPrompt(lang: String = "Russian"): String = """
Reply with ONE JSON object only: {"actions":[...],"reply":"..."}
Actions(fields):
call(person) message(person,channel:whatsapp|telegram|sms,text) reply(person,text) read_messages(person,wait)
create_appointment(person,service) cancel_appointment(person) delete_appointment(person) move_appointment(person)
add_note(person,text) open_person(person) select(person) favorite(person,on) find(query) person_info(person,topic)
open_contact(person,contact) share_data(person,data,to) route(person or place,kind:home|work,app:waze|google|yandex|organic) agenda(day)
play_music(query,app,playlist,artist,shuffle,video) media(control:pause|resume|next|prev|shuffle_on|shuffle_off|repeat|stop|louder|quieter|what)
launch_app(app) web_search(query) alarm(time) timer(minutes) flashlight(on) phone_settings(what) open_screen(section) go_home lock
Rules: several commands → actions in order. Message text = the user's own words to send, not to you. "любую песню X" → query X, artist true. Question → actions [] + answer only from Data. Chat → actions [] + friendly reply. reply: short, in $lang.
напиши Ане что опоздаю и сверни → {"actions":[{"action":"message","person":"Аня","text":"опоздаю"},{"action":"go_home"}],"reply":"Пишу"}
увімкни плейлист для бігу вперемішку → {"actions":[{"action":"play_music","query":"для бігу","playlist":true,"shuffle":true}],"reply":"Вмикаю"}
пауза → {"actions":[{"action":"media","control":"pause"}],"reply":""}
веди на Крещатик 22 в вейзе → {"actions":[{"action":"route","place":"Крещатик 22","app":"waze"}],"reply":"Еду"}
відповідай Олегу буду о сьомій → {"actions":[{"action":"reply","person":"Олег","text":"буду о сьомій"}],"reply":"Відповідаю"}
когда Аня была у меня? → {"actions":[],"reply":"<from Data>"}
как дела? → {"actions":[],"reply":"Отлично! Чем помочь?"}
""".trim()
    }
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
