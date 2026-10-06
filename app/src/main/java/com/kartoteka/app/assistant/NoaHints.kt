package com.kartoteka.app.assistant

import com.kartoteka.app.KartotekaApp

/**
 * Подсказки для распознавания речи: имена людей, услуги и группы из книжки. Android 13+ позволяет передать
 * распознавателю список слов, которые стоит ждать, — и «ильерикову» чаще превращается в «Илье Рыкову».
 * Имена даём и в падежах («Илья, Ильи, Илье, Илью…»): так их и говорят в командах.
 */
object NoaHints {
    @Volatile var list: List<String> = emptyList()
        private set
    @Volatile private var builtAt = 0L

    /** Пересобрать список (не чаще раза в 5 минут). Вызывать с фонового потока. */
    suspend fun refresh(app: KartotekaApp, force: Boolean = false) {
        if (!force && System.currentTimeMillis() - builtAt < 5 * 60_000L && list.isNotEmpty()) return
        runCatching {
            val people = app.repository.getAll()
            val out = LinkedHashSet<String>()
            // Недавние контакты — первыми: лимит распознавателя небольшой.
            for (pf in people.sortedByDescending { it.person.lastContactAt ?: 0L }) {
                val p = pf.person
                for (w in listOf(p.firstName, p.lastName, p.nickname, p.middleName)) forms(w).forEach { out += it }
                if (p.firstName.isNotBlank() && p.lastName.isNotBlank()) out += "${p.firstName} ${p.lastName}"
                if (out.size > 180) break
            }
            app.repository.getServices().forEach { if (it.name.isNotBlank()) out += it.name.take(40) }
            app.repository.getGroups().forEach { if (it.name.isNotBlank()) out += it.name.take(40) }
            val name = app.settings.assistantName.value.value
            if (name.isNotBlank()) out += name
            list = out.filter { it.length in 2..40 }.take(240)
            builtAt = System.currentTimeMillis()
        }
    }

    /** Слово и его основные падежные формы (русские/украинские окончания; грубо, но для подсказки достаточно). */
    internal fun forms(word: String): List<String> {
        val w = word.trim()
        if (w.length < 2) return emptyList()
        if (!w.any { it in 'а'..'я' || it in 'А'..'Я' || it in "іїєґІЇЄҐёЁ" }) return listOf(w)
        val low = w.lowercase()
        val cap = { s: String -> s.replaceFirstChar { it.uppercase() } }
        val endings: List<String> = when {
            low.endsWith("а") -> listOf("ы", "и", "е", "і", "у", "ой", "ою")
            low.endsWith("я") -> listOf("и", "е", "ю", "ей", "і")
            low.endsWith("й") -> listOf("я", "ю", "ем", "е")
            low.endsWith("ь") -> listOf("я", "ю", "ем", "е")
            low.endsWith("о") || low.endsWith("е") || low.endsWith("и") || low.endsWith("ы") || low.endsWith("у") || low.endsWith("ю") -> emptyList()
            else -> listOf("а", "у", "ом", "е")                      // Рыков → Рыкова, Рыкову, Рыковым(~ом), Рыкове
        }
        val base = if (low.last() in "аяйь") low.dropLast(1) else low
        // Илья → Ильи/Илье/Илью: основа «иль» + окончание; для «я» основа сохраняет мягкий знак — «ь» добавим вручную
        val stem = if (low.endsWith("я") && base.length >= 2 && base.last() !in "аеёиоуыэюяь") base + "ь" else base
        val extra = if (low.endsWith("ов") || low.endsWith("ев") || low.endsWith("ин")) listOf("ым", "ом") else emptyList()
        return (listOf(w) + endings.map { cap(stem + it) } + extra.map { cap(base + it) }).distinct().take(9)
    }
}
