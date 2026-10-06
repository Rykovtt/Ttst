package com.kartoteka.app.assistant

/**
 * Команда Ноа → компактный JSON в формате интерпретатора: {"actions":[{"action":"call","person":"Аня"}]}.
 * Нужен для библиотеки примеров (assets/examples.tsv): модель видит похожие фразы вместе с верным ответом.
 * Пишем только то, что важно: пустые поля и значения по умолчанию опускаем, дату и время тоже — их берём из самой фразы.
 * Команды, которых нет в реестре интерпретатора (время/калькулятор/картотека/«повтори»…), в JSON не превращаются (null).
 */
object NoaIntentJson {
    /** Весь ответ: {"actions":[…]}; null — команду нельзя записать действиями. */
    fun toJson(i: NoaIntent): String? {
        val steps = if (i is NoaIntent.Sequence) i.steps else listOf(i)
        val acts = steps.map { action(it) ?: return null }
        return "{\"actions\":[" + acts.joinToString(",") + "]}"
    }

    /** Одно действие: {"action":"…",…}; null — нет такого действия. */
    fun action(i: NoaIntent): String? {
        val f = ArrayList<Pair<String, Any>>()
        fun p(person: String) { person.trim().takeIf { it.isNotBlank() }?.let { f += "person" to name(it) } }
        val act: String = when (i) {
            is NoaIntent.CreateAppointment -> { p(i.personQuery); i.serviceQuery?.takeIf { it.isNotBlank() }?.let { f += "service" to it }; "create_appointment" }
            is NoaIntent.CancelAppointment -> { p(i.personQuery); if (i.delete) "delete_appointment" else "cancel_appointment" }
            is NoaIntent.MoveAppointment -> { p(i.personQuery); "move_appointment" }
            is NoaIntent.Call -> { p(i.personQuery); "call" }
            is NoaIntent.Message -> {
                p(i.personQuery)
                if (i.channel != NoaIntent.Channel.WHATSAPP) f += "channel" to i.channel.name.lowercase()
                if (i.aboutAppointment) f += "about_appointment" to true else i.text?.takeIf { it.isNotBlank() }?.let { f += "text" to it }
                "message"
            }
            is NoaIntent.Reply -> { p(i.personQuery); f += "text" to i.text; "reply" }
            is NoaIntent.ReadMessages -> { p(i.personQuery); if (i.wait) f += "wait" to true; "read_messages" }
            is NoaIntent.AddNote -> { p(i.personQuery); f += "text" to i.text; "add_note" }
            is NoaIntent.Open -> { p(i.personQuery); "open_person" }
            is NoaIntent.Find -> { f += "query" to i.query; "find" }
            is NoaIntent.OpenContact -> { p(i.personQuery); f += "contact" to i.type.name.lowercase(); "open_contact" }
            is NoaIntent.Route -> {
                p(i.personQuery)
                i.kind?.let { f += "kind" to it.name.lowercase() }
                i.app?.let { f += "app" to it }
                // Адрес пишем только когда нет ни человека, ни «дом/работа»: иначе в place остаётся мусор из фразы («олегу работу»).
                if (i.personQuery.isBlank() && i.kind == null) i.place.takeIf { it.isNotBlank() }?.let { f += "place" to it }
                "route"
            }
            is NoaIntent.Agenda -> "agenda"
            is NoaIntent.PersonInfo -> {
                p(i.personQuery)
                if (i.topic != NoaIntent.Topic.SUMMARY) f += "topic" to i.topic.name.lowercase()
                "person_info"
            }
            is NoaIntent.Favorite -> { p(i.personQuery); if (!i.on) f += "on" to false; "favorite" }
            is NoaIntent.Select -> { p(i.personQuery); "select" }
            is NoaIntent.ShareData -> {
                p(i.personQuery)
                if (i.data != NoaIntent.Data.CARD) f += "data" to i.data.name.lowercase()
                if (i.target != "share") f += "to" to i.target.removePrefix("app:")
                "share_data"
            }
            is NoaIntent.LaunchApp -> { f += "app" to i.name; "launch_app" }
            is NoaIntent.CloseApp -> { f += "app" to i.name; "close_app" }
            is NoaIntent.WebSearch -> { f += "query" to i.query; "web_search" }
            is NoaIntent.Alarm -> { f += "time" to "%02d:%02d".format(i.hour, i.minute); i.label?.takeIf { it.isNotBlank() }?.let { f += "label" to it }; "alarm" }
            is NoaIntent.Timer -> {
                val h = i.seconds / 3600; val m = i.seconds % 3600 / 60; val s = i.seconds % 60
                if (h > 0) f += "hours" to h
                if (m > 0) f += "minutes" to m
                if (s > 0) f += "seconds" to s
                "timer"
            }
            is NoaIntent.Remind -> { f += "text" to i.text; "remind" }
            is NoaIntent.Flashlight -> { if (!i.on) f += "on" to false; "flashlight" }
            is NoaIntent.PhoneSettings -> { i.what?.takeIf { it.isNotBlank() }?.let { f += "what" to it }; "phone_settings" }
            is NoaIntent.Play -> {
                f += "query" to i.query
                i.app?.takeIf { it.isNotBlank() }?.let { f += "app" to it }
                if (i.playlist) f += "playlist" to true
                if (i.artist) f += "artist" to true
                if (i.shuffle) f += "shuffle" to true
                if (i.video) f += "video" to true
                i.channel.takeIf { it.isNotBlank() }?.let { f += "channel" to it }
                "play_music"
            }
            is NoaIntent.Media -> { f += "control" to i.control.name.lowercase(); "media" }
            is NoaIntent.OpenScreen -> { f += "section" to i.section.name.lowercase(); "open_screen" }
            NoaIntent.GoHome -> "go_home"
            NoaIntent.Lock -> "lock"
            NoaIntent.Backup -> "backup"
            else -> return null
        }
        return buildString {
            append("{\"action\":").append(quote(act))
            for ((k, v) in f) {
                append(',').append(quote(k)).append(':')
                if (v is String) append(quote(v)) else append(v.toString())
            }
            append('}')
        }
    }

    /** Имя из фразы пишем с заглавной: «анну» → «Анну»; местоимения («ему») оставляем как есть. */
    private fun name(s: String): String =
        if (s.lowercase() in NoaParser.PRONOUNS) s.lowercase() else s.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

    private fun quote(s: String): String = buildString {
        append('"')
        for (c in s) when {
            c == '"' -> append("\\\"")
            c == '\\' -> append("\\\\")
            c < ' ' -> append(' ')
            else -> append(c)
        }
        append('"')
    }
}
