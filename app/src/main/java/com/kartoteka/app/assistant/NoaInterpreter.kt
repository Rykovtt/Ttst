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

    suspend fun interpret(userText: String, now: LocalDateTime = LocalDateTime.now()): Interpreted? {
        val raw = brain?.ask(prompt(userText, now)) ?: return null
        return fromJson(raw, userText, now)
    }

    /** Разбор ответа модели в команду (чистая логика, тестируется отдельно). */
    fun fromJson(raw: String, userText: String, now: LocalDateTime = LocalDateTime.now()): Interpreted? {
        val json = extractJson(raw) ?: return null
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val action = o.optString("action").lowercase().trim()
        val person = o.optString("person").trim()
        val reply = o.optString("reply").trim().ifBlank { null }
        fun str(key: String) = o.optString(key).trim().ifBlank { null }

        val intent: NoaIntent? = when (action) {
            "create_appointment" -> {
                val dt = NoaDateTime.parse(userText, now)
                NoaIntent.CreateAppointment(person, dt?.dateTime, dt?.hadTime ?: false, str("service")?.lowercase(), false)
            }
            "call" -> NoaIntent.Call(person)
            "message" -> NoaIntent.Message(person, channel(str("channel")), str("text"))
            "add_note" -> NoaIntent.AddNote(person, str("text") ?: str("note") ?: "")
            "find" -> NoaIntent.Find(str("query") ?: person)
            "open_person" -> NoaIntent.Open(person)
            "open_screen" -> section(str("section"))?.let { NoaIntent.OpenScreen(it) }
            "lock" -> NoaIntent.Lock
            "backup" -> NoaIntent.Backup
            "chat" -> null
            else -> null
        }
        // Разговор без действия: отвечаем репликой модели.
        if (intent == null && action != "chat" && reply == null) return null
        return Interpreted(intent, reply)
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

    private fun prompt(user: String, now: LocalDateTime): String {
        val date = "%04d-%02d-%02d".format(now.year, now.monthValue, now.dayOfMonth)
        return """
            Ты — Ноа, ассистент в приложении-архиве контактов. Преобразуй запрос пользователя в ОДНУ строку JSON без пояснений.
            Поля:
            action: одно из [create_appointment, call, message, add_note, find, open_person, open_screen, lock, backup, chat]
            person: имя человека как в запросе (или "")
            service: тату | консультация | маникюр | стрижка | ""
            channel: whatsapp | sms | telegram
            section: people | calendar | map | broadcast | settings | services
            text: текст заметки или сообщения
            query: что искать
            reply: короткий дружелюбный ответ по-русски
            Если это обычный разговор, а не команда — action="chat" и ответь в reply.
            Сегодня: $date.
            Запрос: "${user.replace("\"", "'")}"
            JSON:
        """.trimIndent()
    }
}
