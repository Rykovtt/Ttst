package com.kartoteka.app.assistant

import org.json.JSONObject
import java.time.LocalDateTime

/** Результат понимания фразы мозгом: действие (или null = свободный разговор) и реплика голосом. */
data class Interpreted(val intent: NoaIntent?, val reply: String?)

/**
 * Превращает свободную речь в команду через Gemini Nano. Промпт просит модель вернуть строго JSON;
 * дату/время всё равно разбираем сами ([NoaDateTime]) — это надёжнее, чем доверять счёт модели.
 * Любой сбой → null, и вызывающий переходит на быстрые команды.
 */
class NoaInterpreter(private val brain: LlmBrain?) {

    suspend fun interpret(userText: String, now: LocalDateTime = LocalDateTime.now(), names: List<String> = emptyList(), context: String = ""): Interpreted? {
        val raw = brain?.ask(prompt(userText, now, names, context)) ?: return null
        return fromJson(raw, userText, now)
    }

    /** Разбор ответа модели в команду (чистая логика, тестируется отдельно). Понимает и список действий. */
    fun fromJson(raw: String, userText: String, now: LocalDateTime = LocalDateTime.now()): Interpreted? {
        val json = extractJson(raw) ?: return null
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val reply = o.optString("reply").trim().ifBlank { null }
        val arr = o.optJSONArray("actions")
        if (arr != null && arr.length() > 0) {
            var person = ""
            val steps = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.mapNotNull { a ->
                val step = action(a, userText, now) ?: return@mapNotNull null
                val p = NoaParser.personOf(step)
                if (p.isNotBlank()) { person = p; step } else NoaParser.withPerson(step, person)
            }
            return when {
                steps.size > 1 -> Interpreted(NoaIntent.Sequence(steps), reply)
                steps.size == 1 -> Interpreted(steps[0], reply)
                reply != null -> Interpreted(null, reply)
                else -> null
            }
        }
        val action = o.optString("action").lowercase().trim()
        val intent = action(o, userText, now)
        // Разговор без действия: отвечаем репликой модели.
        if (intent == null && action != "chat" && reply == null) return null
        return Interpreted(intent, reply)
    }

    private fun action(o: JSONObject, userText: String, now: LocalDateTime): NoaIntent? {
        val action = o.optString("action").lowercase().trim()
        val person = o.optString("person").trim()
        fun str(key: String) = o.optString(key).trim().ifBlank { null }
        return when (action) {
            "create_appointment" -> {
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
            "launch_app", "open_app" -> str("app")?.let { NoaIntent.LaunchApp(it) }
            "share_data", "transfer" -> NoaIntent.ShareData(person, when (str("data")?.lowercase()) {
                "notes", "note" -> NoaIntent.Data.NOTES; "phone" -> NoaIntent.Data.PHONE; "address" -> NoaIntent.Data.ADDRESS
                "email" -> NoaIntent.Data.EMAIL; "birthday" -> NoaIntent.Data.BIRTHDAY; else -> NoaIntent.Data.CARD
            }, when (val to = str("to")?.lowercase()) {
                null, "", "share" -> "share"; "notes", "notepad" -> "notes"; "google", "search" -> "google"; "clipboard" -> "clipboard"
                else -> "app:$to"
            })
            "web_search" -> str("query")?.let { NoaIntent.WebSearch(it) }
            "alarm" -> NoaDateTime.parse(str("time") ?: userText, now)?.takeIf { it.hadTime }?.let { NoaIntent.Alarm(it.dateTime.hour, it.dateTime.minute, str("label")) }
            "timer" -> (o.optInt("minutes", 0) * 60 + o.optInt("seconds", 0)).takeIf { it > 0 }?.let { NoaIntent.Timer(it) }
            "play_music", "play" -> NoaIntent.Play(str("query").orEmpty(), str("app"), o.optBoolean("playlist", false), o.optBoolean("artist", false))
            "flashlight" -> NoaIntent.Flashlight(o.optBoolean("on", true))
            "phone_settings" -> NoaIntent.PhoneSettings(str("what"))
            "call" -> NoaIntent.Call(person)
            "message" -> {
                // «Отправь ему об этом» — текст из шаблона подтверждения, а не выдумка модели.
                val about = NoaParser.isAboutAppointment(userText) || o.optBoolean("about_appointment", false)
                NoaIntent.Message(person, channel(str("channel")), if (about) null else str("text"), aboutAppointment = about)
            }
            "add_note" -> NoaIntent.AddNote(person, str("text") ?: str("note") ?: "")
            "find" -> NoaIntent.Find(str("query") ?: person)
            "open_person" -> NoaIntent.Open(person)
            "open_screen" -> section(str("section"))?.let { NoaIntent.OpenScreen(it) }
            "open_contact" -> contactType(str("contact"))?.let { NoaIntent.OpenContact(person, it) }
            "route" -> NoaIntent.Route(person, when (str("place")?.lowercase()) {
                "home" -> com.kartoteka.app.data.PlaceKind.HOME
                "work" -> com.kartoteka.app.data.PlaceKind.WORK
                else -> null
            }, str("app")?.lowercase()?.let { if ("waze" in it) "waze" else if ("google" in it) "google" else null })
            "agenda" -> NoaIntent.Agenda(
                NoaDateTime.parse(userText, now)?.dateTime?.toLocalDate()
                    ?: if (str("day") == "tomorrow") now.toLocalDate().plusDays(1) else now.toLocalDate()
            )
            "person_info" -> NoaIntent.PersonInfo(person, when (str("topic")?.lowercase()) {
                "birthday" -> NoaIntent.Topic.BIRTHDAY
                "phone" -> NoaIntent.Topic.PHONE
                "address" -> NoaIntent.Topic.ADDRESS
                else -> NoaIntent.Topic.SUMMARY
            }, str("question") ?: userText)
            "favorite" -> NoaIntent.Favorite(person, o.optBoolean("on", true))
            "select" -> NoaIntent.Select(person)
            "lock" -> NoaIntent.Lock
            "backup" -> NoaIntent.Backup
            else -> null
        }
    }

    private fun contactType(s: String?): com.kartoteka.app.data.ContactType? = when (s?.lowercase()) {
        "instagram" -> com.kartoteka.app.data.ContactType.INSTAGRAM
        "facebook" -> com.kartoteka.app.data.ContactType.FACEBOOK
        "viber" -> com.kartoteka.app.data.ContactType.VIBER
        "email", "mail" -> com.kartoteka.app.data.ContactType.EMAIL
        "website", "site" -> com.kartoteka.app.data.ContactType.WEBSITE
        "vk" -> com.kartoteka.app.data.ContactType.VK
        "telegram" -> com.kartoteka.app.data.ContactType.TELEGRAM
        "whatsapp" -> com.kartoteka.app.data.ContactType.WHATSAPP
        "phone" -> com.kartoteka.app.data.ContactType.PHONE
        else -> null
    }

    private fun channel(s: String?): NoaIntent.Channel = when (s?.lowercase()) {
        "telegram", "тг" -> NoaIntent.Channel.TELEGRAM
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

    /** Берём первый сбалансированный {…} из ответа модели. */
    private fun extractJson(text: String): String? {
        val start = text.indexOf('{')
        if (start < 0) return null
        var depth = 0
        for (i in start until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return text.substring(start, i + 1) }
            }
        }
        return null
    }

    private fun prompt(user: String, now: LocalDateTime, names: List<String>, context: String = ""): String {
        val date = "%04d-%02d-%02d".format(now.year, now.monthValue, now.dayOfMonth)
        val lang = when (com.kartoteka.app.i18n.I18n.lang) {
            com.kartoteka.app.i18n.UiLang.UK -> "українською"
            com.kartoteka.app.i18n.UiLang.EN -> "in English"
            else -> "по-русски"
        }
        val people = names.take(if (context.isBlank()) 30 else 15).joinToString(", ").ifBlank { "—" }
        val data = if (context.isBlank()) "" else "\nДанные из записной книжки (отвечай на вопросы ТОЛЬКО по ним, не выдумывай):\n$context\n"
        return """
            Ты — голосовой ассистент в личной записной книжке людей. Переведи запрос в JSON. Только JSON, без пояснений.
            Формат: {"actions":[{...},{...}],"reply":"короткий ответ $lang"}
            Действия (поле action):
            create_appointment {person, service}; cancel_appointment {person}; delete_appointment {person}; move_appointment {person};
            call {person}; message {person, channel: whatsapp|telegram|sms, text};
            add_note {person, text}; open_person {person}; find {query};
            open_contact {person, contact: instagram|facebook|viber|email|website|telegram|whatsapp};
            route {person, place: home|work|"", app: waze|google|""}; agenda {day: today|tomorrow};
            person_info {person, topic: birthday|phone|address|summary, question};
            favorite {person, on: true|false};
            launch_app {app} — запустить приложение телефона; share_data {person, data: notes|phone|address|email|birthday|card, to: notes|google|clipboard|share|<название приложения>} — передать данные человека в блокнот, Google, буфер или приложение;
            web_search {query}; alarm {time: "HH:MM"}; timer {minutes}; flashlight {on}; play_music {query, app, playlist: true|false, artist: true|false} — включить музыку; query — только название/исполнитель, без слов «любую песню»; phone_settings {what: wifi|bluetooth|display|sound|""}; open_screen {section: people|calendar|map|broadcast|settings|services}; lock
            Несколько команд — несколько действий по порядку. «ей/її/him» — тот же человек.
            Вопрос о людях, встречах, планах — "actions":[] и подробный полезный ответ в reply по данным ниже. Обычный разговор — "actions":[] и живой ответ в reply.
            Люди в книжке: $people$data
            Примеры:
            «зайди в профиль Ани и добавь заметку купила новый телефон» → {"actions":[{"action":"open_person","person":"Аня"},{"action":"add_note","person":"Аня","text":"купила новый телефон"}],"reply":"Готово"}
            «відкрий інстаграм Олега» → {"actions":[{"action":"open_contact","person":"Олег","contact":"instagram"}],"reply":"Відкриваю"}
            «проклади маршрут до мами на роботу» → {"actions":[{"action":"route","person":"мама","place":"work"}],"reply":"Прокладаю"}
            «удали запись Ильи Рыкова на завтра» → {"actions":[{"action":"delete_appointment","person":"Илья Рыков"}],"reply":""}
            «перенеси Аню на пятницу в 15» → {"actions":[{"action":"move_appointment","person":"Аня"}],"reply":""}
            «возьми заметки об Илье и перенеси в блокнот» → {"actions":[{"action":"share_data","person":"Илья","data":"notes","to":"notes"}],"reply":""}
            «скопируй номер Анны и вставь в гугл» → {"actions":[{"action":"share_data","person":"Анна","data":"phone","to":"google"}],"reply":""}
            «открой ютуб мьюзик и включи плейлист» → {"actions":[{"action":"play_music","query":"","app":"youtube music","playlist":true}],"reply":""}
            «запусти ютуб» → {"actions":[{"action":"launch_app","app":"youtube"}],"reply":""}
            «що в мене завтра» → {"actions":[{"action":"agenda","day":"tomorrow"}],"reply":""}
            «коли день народження в Ілля» → {"actions":[{"action":"person_info","person":"Ілля","topic":"birthday","question":"коли день народження"}],"reply":""}
            Сегодня: $date.
            Запрос: "${user.replace("\"", "'")}"
            JSON:
        """.trimIndent()
    }
}
