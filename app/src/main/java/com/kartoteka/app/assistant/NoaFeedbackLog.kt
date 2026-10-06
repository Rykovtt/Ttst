package com.kartoteka.app.assistant

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Журнал непонятых фраз — по желанию человека, хранится только на телефоне. Нужен, чтобы по реальным фразам
 * улучшать ассистента: что он не понял совсем, что понял только модель (значит, нужно правило), и что человек
 * назвал неправильным («это не то»). Ничего не отправляется само: человек сам копирует журнал и пересылает.
 */
object NoaFeedbackLog {
    private const val MAX = 300
    private val lock = Any()

    data class Entry(val time: String, val kind: String, val phrase: String, val rules: String, val model: String, val reply: String)

    private fun file(context: Context) = File(context.filesDir, "assistant_log.tsv")

    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim().take(300)

    /** [kind]: unknown — не поняли ни правила, ни модель; model — поняла только модель; wrong — «это не то»; error — сбой. */
    fun add(context: Context, kind: String, phrase: String, rules: String = "", model: String = "", reply: String = "") {
        if (phrase.isBlank()) return
        runCatching {
            synchronized(lock) {
                val f = file(context)
                val line = listOf(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()), kind, clean(phrase), clean(rules), clean(model), clean(reply)).joinToString("\t")
                val lines = (if (f.exists()) f.readLines() else emptyList()) + line
                f.writeText(lines.takeLast(MAX).joinToString("\n") + "\n")
            }
        }
    }

    fun entries(context: Context): List<Entry> = runCatching {
        synchronized(lock) {
            file(context).takeIf { it.exists() }?.readLines().orEmpty().mapNotNull { l ->
                val p = l.split('\t'); if (p.size < 3) null else Entry(p[0], p[1], p[2], p.getOrElse(3) { "" }, p.getOrElse(4) { "" }, p.getOrElse(5) { "" })
            }
        }
    }.getOrDefault(emptyList())

    fun clear(context: Context) { runCatching { synchronized(lock) { file(context).delete() } } }

    /** Текст для пересылки. [names] — имена из книжки: при [hideNames] заменяются на «ИМЯ», длинные числа — на «№». */
    fun export(entries: List<Entry>, names: List<String>, hideNames: Boolean): String = buildString {
        val kinds = mapOf("unknown" to "не поняла", "model" to "поняла только модель", "wrong" to "это не то", "error" to "сбой")
        for (e in entries) {
            fun m(s: String) = if (hideNames) mask(s, names) else s
            append(e.time).append(" [").append(kinds[e.kind] ?: e.kind).append("] ").append(m(e.phrase))
            if (e.rules.isNotBlank()) append("  | правила: ").append(e.rules)
            if (e.model.isNotBlank()) append("  | модель: ").append(e.model)
            if (e.reply.isNotBlank()) append("  | ответ: ").append(m(e.reply))
            append('\n')
        }
    }

    /** Скрыть имена людей из книжки (с учётом падежей — по началу слова) и номера. */
    fun mask(text: String, names: List<String>): String {
        val stems = names.flatMap { it.lowercase().split(Regex("[^\\p{L}]+")) }.filter { it.length >= 3 }
            .map { it.take(maxOf(3, it.length - 2)) }.toSet()
        val words = Regex("\\p{L}+|[^\\p{L}]+").findAll(text).map { it.value }
        return words.joinToString("") { w ->
            val low = w.lowercase()
            if (w.firstOrNull()?.isLetter() == true && stems.any { low.startsWith(it) }) "ИМЯ" else w
        }.replace(Regex("\\+?\\d[\\d\\s\\-()]{4,}\\d"), "№")
    }
}
