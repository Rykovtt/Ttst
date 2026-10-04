package com.kartoteka.app.i18n

import android.content.Context
import java.util.Locale

/** Язык интерфейса. AUTO — как в системе телефона. */
enum class UiLang(val code: String, val title: String) {
    AUTO("auto", "Авто (как в системе)"),
    RU("ru", "Русский"),
    UK("uk", "Українська"),
    EN("en", "English");

    companion object {
        fun of(code: String?) = entries.firstOrNull { it.code == code } ?: AUTO
    }
}

/**
 * Переводы интерфейса. Исходный текст — русский (он же ключ); переводы на украинский
 * и английский лежат в assets/i18n.tsv. Нет перевода — показываем русский.
 */
object I18n {
    /** Действующий язык (никогда не AUTO). */
    @Volatile
    var lang: UiLang = UiLang.RU
        private set

    private var uk: Map<String, String> = emptyMap()
    private var en: Map<String, String> = emptyMap()

    val locale: Locale get() = Locale(lang.code)

    fun init(context: Context, chosen: UiLang) {
        if (uk.isEmpty() && en.isEmpty()) load(context)
        lang = resolve(chosen)
    }

    fun resolve(chosen: UiLang): UiLang = if (chosen != UiLang.AUTO) chosen else when (Locale.getDefault().language) {
        "uk" -> UiLang.UK
        "ru", "be", "kk" -> UiLang.RU
        else -> UiLang.EN
    }

    private fun load(context: Context) {
        val u = HashMap<String, String>()
        val e = HashMap<String, String>()
        runCatching {
            context.assets.open("i18n.tsv").bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (line.isBlank() || line.startsWith("#")) return@forEach
                    val parts = line.split('\t')
                    if (parts.size < 3) return@forEach
                    val key = unescape(parts[0])
                    if (parts[1].isNotEmpty()) u[key] = unescape(parts[1])
                    if (parts[2].isNotEmpty()) e[key] = unescape(parts[2])
                }
            }
        }
        uk = u
        en = e
    }

    private fun unescape(s: String) = s.replace("\\n", "\n").replace("\\t", "\t")

    fun tr(ru: String): String = when (lang) {
        UiLang.UK -> uk[ru] ?: ru
        UiLang.EN -> en[ru] ?: ru
        else -> ru
    }

    /** Склонение по числу: русский и украинский — три формы, английский — две. */
    fun plural(n: Long, one: String, few: String, many: String): String {
        // Форма «один» может совпадать с «много» по-русски (1 человек / 5 человек) — для неё есть ключ «…#one».
        fun trOne(): String = when (lang) {
            UiLang.UK -> uk["$one#one"] ?: tr(one)
            UiLang.EN -> en["$one#one"] ?: tr(one)
            else -> one
        }
        if (lang == UiLang.EN) return if (n == 1L) trOne() else tr(many)
        val n10 = n % 10
        val n100 = n % 100
        return when {
            n10 == 1L && n100 != 11L -> trOne()
            n10 in 2..4 && n100 !in 12..14 -> tr(few)
            else -> tr(many)
        }
    }

    /** Месяц в родительном падеже («1 октября»), на английском — «October 1». */
    fun dayMonth(day: Int, month: Int): String = when (lang) {
        UiLang.UK -> "$day ${UK_MONTHS[month - 1]}"
        UiLang.EN -> "${EN_MONTHS[month - 1]} $day"
        else -> "$day ${RU_MONTHS[month - 1]}"
    }

    /** Короткая дата для плашек: «3 лис», «7 окт», «Oct 7». */
    fun dayMonthShort(day: Int, month: Int): String = when (lang) {
        UiLang.UK -> "$day ${UK_SHORT[month - 1]}"
        UiLang.EN -> "${EN_MONTHS[month - 1].take(3)} $day"
        else -> "$day ${RU_SHORT[month - 1]}"
    }
    private val RU_SHORT = listOf("янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")
    private val UK_SHORT = listOf("січ", "лют", "бер", "кві", "тра", "чер", "лип", "сер", "вер", "жов", "лис", "гру")

    val RU_MONTHS = listOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря")
    val UK_MONTHS = listOf("січня", "лютого", "березня", "квітня", "травня", "червня", "липня", "серпня", "вересня", "жовтня", "листопада", "грудня")
    val EN_MONTHS = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
}

/** Перевести строку интерфейса. */
fun t(ru: String): String = I18n.tr(ru)

/** Перевести строку с подстановками %1$s, %2$s… */
fun t(ru: String, vararg args: Any?): String = String.format(I18n.locale, I18n.tr(ru), *args)
