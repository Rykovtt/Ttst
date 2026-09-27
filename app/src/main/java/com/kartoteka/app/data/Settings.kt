package com.kartoteka.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("kartoteka_settings", Context.MODE_PRIVATE)

    private val _lockEnabled = MutableStateFlow(prefs.getBoolean(LOCK, false))
    val lockEnabled: StateFlow<Boolean> = _lockEnabled.asStateFlow()

    private val _secureScreen = MutableStateFlow(prefs.getBoolean(SECURE, true))
    val secureScreen: StateFlow<Boolean> = _secureScreen.asStateFlow()

    private val _birthdayReminders = MutableStateFlow(prefs.getBoolean(BIRTHDAYS, true))
    val birthdayReminders: StateFlow<Boolean> = _birthdayReminders.asStateFlow()

    private val _sortMode = MutableStateFlow(SortMode.of(prefs.getString(SORT, null)))
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()

    fun setLockEnabled(v: Boolean) { prefs.edit().putBoolean(LOCK, v).apply(); _lockEnabled.value = v }
    fun setSecureScreen(v: Boolean) { prefs.edit().putBoolean(SECURE, v).apply(); _secureScreen.value = v }
    fun setBirthdayReminders(v: Boolean) { prefs.edit().putBoolean(BIRTHDAYS, v).apply(); _birthdayReminders.value = v }
    fun setSortMode(v: SortMode) { prefs.edit().putString(SORT, v.name).apply(); _sortMode.value = v }

    private companion object {
        const val LOCK = "lock"
        const val SECURE = "secure_screen"
        const val BIRTHDAYS = "birthdays"
        const val SORT = "sort"
    }
}

enum class SortMode(val title: String) {
    NAME("По имени"),
    RECENT("Недавно добавленные"),
    CLOSENESS("По близости"),
    LONG_AGO("Давно не общались");

    companion object {
        fun of(name: String?) = entries.firstOrNull { it.name == name } ?: NAME
    }
}
