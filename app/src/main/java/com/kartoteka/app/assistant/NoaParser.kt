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
    data class Call(val personQuery: String) : NoaIntent
    data class Message(val personQuery: String, val channel: Channel, val text: String?) : NoaIntent
    data class AddNote(val personQuery: String, val text: String) : NoaIntent
    data object Lock : NoaIntent
    data object Backup : NoaIntent
    data class Unknown(val heard: String) : NoaIntent

    enum class Channel { WHATSAPP, SMS, TELEGRAM }
}

/**
 * Разбор голосовой/текстовой команды по правилам (Этап 1, без нейросети).
 * Русский, украинский, английский. Сначала определяем намерение по ключевым словам,
 * затем вытаскиваем человека, дату/время и прочее.
 */
object NoaParser {
    private fun has(s: String, vararg keys: String) = keys.any { s.contains(" $it") }

    fun parse(input: String, now: LocalDateTime = LocalDateTime.now()): NoaIntent {
        val original = input.trim()
        val s = " " + original.lowercase().replace(Regex("\\s+"), " ") + " "
        if (original.isBlank()) return NoaIntent.Unknown(original)

        // блокировка / копия — без человека
        if (has(s, "заблокируй", "заблокуй", "закрой приложение", "lock")) return NoaIntent.Lock
        if (has(s, "резервную копию", "бэкап", "бекап", "backup", "копію", "копию")) return NoaIntent.Backup

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
        if (has(s, "напиши", "сообщение", "отправь", "напиши смс", "смс", "sms", "message", "напис", "повідомл", "whatsapp", "вотсап", "телеграм", "telegram")) {
            val channel = when {
                has(s, "whatsapp", "вотсап", "ватсап") -> NoaIntent.Channel.WHATSAPP
                has(s, "телеграм", "telegram", "тг") -> NoaIntent.Channel.TELEGRAM
                has(s, "смс", "sms") -> NoaIntent.Channel.SMS
                else -> NoaIntent.Channel.WHATSAPP
            }
            val text = extractQuoted(original)
            return NoaIntent.Message(extractPerson(s), channel, text)
        }
        // заметка в хронику
        if (has(s, "заметк", "запиши в хронику", "добавь заметк", "нотат", "note")) {
            val text = extractQuoted(original) ?: afterWord(original, "что", "про", "о том", "about")
            return NoaIntent.AddNote(extractPerson(s), text.orEmpty())
        }
        // открыть карточку
        if (has(s, "открой", "покажи карточку", "відкрий", "open")) {
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
    )
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
