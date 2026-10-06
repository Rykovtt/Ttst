package com.rykov.autosend.core

/** Разбор Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES без зависимостей от Android. */
object EnabledServices {
    /**
     * [setting] — строка вида "pkg/.Cls:pkg2/pkg2.Cls2".
     * Имя класса в записи может быть как полным, так и сокращённым (начинается с точки).
     */
    fun contains(setting: String?, packageName: String, className: String): Boolean {
        if (setting.isNullOrBlank()) return false
        return setting.split(':').any { entry ->
            val parts = entry.trim().split('/')
            if (parts.size != 2 || !parts[0].equals(packageName, ignoreCase = true)) return@any false
            val cls = parts[1].let { if (it.startsWith('.')) packageName + it else it }
            cls.equals(className, ignoreCase = true)
        }
    }
}
