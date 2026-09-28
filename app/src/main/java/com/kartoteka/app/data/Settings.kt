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

    // --- телефоны ---
    val country = StringPref("country", "UA")
    val defaultCountry: Country get() = PhoneFormat.byIso(country.value.value)

    // --- календарь ---
    val apptChannel = StringPref("appt_channel", NotifyChannel.WHATSAPP.name)
    val apptDuration = StringPref("appt_duration", "60")
    val apptClientOffsets = StringPref("appt_client_offsets", "${24 * 60},120")
    val apptMyOffsets = StringPref("appt_my_offsets", "60")
    val apptSendConfirm = StringPref("appt_send_confirm", "true")
    val tplConfirm = StringPref("tpl_confirm", AppointmentLogic.DEFAULT_CONFIRM)
    val tplReminder = StringPref("tpl_reminder", AppointmentLogic.DEFAULT_REMINDER)
    val tplCancel = StringPref("tpl_cancel", AppointmentLogic.DEFAULT_CANCEL)
    val tplReschedule = StringPref("tpl_reschedule", AppointmentLogic.DEFAULT_RESCHEDULE)
    val dayStartHour = StringPref("day_start", "8")
    val dayEndHour = StringPref("day_end", "21")

    // --- авто-отправка в мессенджерах ---
    val autoSendDelaySec = StringPref("autosend_delay", "6")

    inner class StringPref(private val key: String, val default: String) {
        private val state = MutableStateFlow(prefs.getString(key, null) ?: default)
        val value: StateFlow<String> = state.asStateFlow()
        fun set(v: String) { prefs.edit().putString(key, v).apply(); state.value = v }
        fun reset() = set(default)
    }

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
