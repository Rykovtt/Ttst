package com.kartoteka.app.data

/**
 * Страна для ввода телефонов: код, национальный префикс (который отбрасывается)
 * и длина национального номера — для надёжного распознавания.
 */
data class Country(
    val iso: String,
    val name: String,
    val code: String,
    val trunk: String,
    val nationalLength: Int?,
    /** Группировка для красивого показа, например [2,3,2,2] → 93 074 38 29. */
    val groups: List<Int>,
) {
    val flag: String
        get() = iso.uppercase().map { Character.toChars(0x1F1E6 + (it - 'A')).concatToString() }.joinToString("")
}

object PhoneFormat {
    val countries = listOf(
        Country("UA", "Украина", "380", "0", 9, listOf(2, 3, 2, 2)),
        Country("RU", "Россия", "7", "8", 10, listOf(3, 3, 2, 2)),
        Country("BY", "Беларусь", "375", "80", 9, listOf(2, 3, 2, 2)),
        Country("KZ", "Казахстан", "7", "8", 10, listOf(3, 3, 2, 2)),
        Country("MD", "Молдова", "373", "0", 8, listOf(2, 3, 3)),
        Country("PL", "Польша", "48", "", 9, listOf(3, 3, 3)),
        Country("DE", "Германия", "49", "0", null, listOf(3, 4, 4)),
        Country("CZ", "Чехия", "420", "", 9, listOf(3, 3, 3)),
        Country("LT", "Литва", "370", "8", 8, listOf(3, 5)),
        Country("LV", "Латвия", "371", "", 8, listOf(2, 3, 3)),
        Country("EE", "Эстония", "372", "", null, listOf(4, 4)),
        Country("GE", "Грузия", "995", "0", 9, listOf(3, 2, 2, 2)),
        Country("AM", "Армения", "374", "0", 8, listOf(2, 3, 3)),
        Country("AZ", "Азербайджан", "994", "0", 9, listOf(2, 3, 2, 2)),
        Country("UZ", "Узбекистан", "998", "", 9, listOf(2, 3, 2, 2)),
        Country("KG", "Кыргызстан", "996", "0", 9, listOf(3, 3, 3)),
        Country("IL", "Израиль", "972", "0", 9, listOf(2, 3, 4)),
        Country("TR", "Турция", "90", "0", 10, listOf(3, 3, 2, 2)),
        Country("GB", "Великобритания", "44", "0", 10, listOf(4, 6)),
        Country("US", "США / Канада", "1", "1", 10, listOf(3, 3, 4)),
        Country("IT", "Италия", "39", "", null, listOf(3, 3, 4)),
        Country("ES", "Испания", "34", "", 9, listOf(3, 3, 3)),
        Country("FR", "Франция", "33", "0", 9, listOf(1, 2, 2, 2, 2)),
        Country("PT", "Португалия", "351", "", 9, listOf(3, 3, 3)),
        Country("NL", "Нидерланды", "31", "0", 9, listOf(1, 4, 4)),
        Country("AT", "Австрия", "43", "0", null, listOf(3, 3, 4)),
        Country("CH", "Швейцария", "41", "0", 9, listOf(2, 3, 2, 2)),
        Country("RO", "Румыния", "40", "0", 9, listOf(3, 3, 3)),
        Country("BG", "Болгария", "359", "0", 9, listOf(2, 3, 4)),
        Country("AE", "ОАЭ", "971", "0", 9, listOf(2, 3, 4)),
        Country("TH", "Таиланд", "66", "0", 9, listOf(2, 3, 4)),
    )

    fun byIso(iso: String?): Country = countries.firstOrNull { it.iso == iso } ?: countries.first()

    /**
     * Приводит ввод к международному виду «+380930743829».
     * Понимает: «093 074 38 29», «+380 93…», «380930743829», «00380…», «8 900…» для России.
     */
    fun normalize(raw: String, country: Country): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        var digits = trimmed.filter { it.isDigit() }
        if (digits.isEmpty()) return trimmed
        if (trimmed.startsWith("+")) return "+$digits"
        if (digits.startsWith("00")) return "+" + digits.drop(2)

        val len = country.nationalLength
        // Уже с кодом страны, но без «+»: 380930743829
        if (digits.startsWith(country.code) && len != null && digits.length == country.code.length + len) {
            return "+$digits"
        }
        if (country.trunk.isNotEmpty() && digits.startsWith(country.trunk) &&
            (len == null || digits.length == country.trunk.length + len)
        ) {
            digits = digits.drop(country.trunk.length)
        }
        return "+" + country.code + digits
    }

    /** Определяет страну по международному номеру (самый длинный подходящий код). */
    fun countryOf(e164: String, preferred: Country? = null): Country? {
        if (!e164.startsWith("+")) return null
        val digits = e164.drop(1)
        if (preferred != null && digits.startsWith(preferred.code)) return preferred
        return countries.filter { digits.startsWith(it.code) }.maxByOrNull { it.code.length }
    }

    /** Красивый показ: «+380 93 074 38 29». Непонятные номера возвращаются как есть. */
    fun pretty(value: String, preferred: Country? = null): String {
        if (!value.startsWith("+") || value.drop(1).any { !it.isDigit() }) return value
        val c = countryOf(value, preferred) ?: return value
        val national = value.drop(1 + c.code.length)
        val parts = mutableListOf<String>()
        var i = 0
        for (g in c.groups) {
            if (i >= national.length) break
            parts += national.substring(i, minOf(national.length, i + g))
            i += g
        }
        if (i < national.length) parts += national.substring(i)
        return "+${c.code} " + parts.joinToString(" ")
    }

    fun isPhoneType(type: ContactType) = type == ContactType.PHONE || type == ContactType.WHATSAPP || type == ContactType.VIBER
}
