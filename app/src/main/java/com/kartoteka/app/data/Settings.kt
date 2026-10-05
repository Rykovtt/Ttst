package com.kartoteka.app.data

import com.kartoteka.app.i18n.t

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("kartoteka_settings", Context.MODE_PRIVATE)

    private val _lockEnabled = MutableStateFlow(prefs.getBoolean(LOCK, false))
    val lockEnabled: StateFlow<Boolean> = _lockEnabled.asStateFlow()

    private val _biometric = MutableStateFlow(prefs.getBoolean(BIOMETRIC, false))
    /** Вход по отпечатку вместо PIN-кода. */
    val biometric: StateFlow<Boolean> = _biometric.asStateFlow()
    fun setBiometric(v: Boolean) { prefs.edit().putBoolean(BIOMETRIC, v).apply(); _biometric.value = v }

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

    /** Фото владельца для аватара на главном экране (путь в зашифрованном хранилище). */
    val ownerPhoto = StringPref("owner_photo", "")

    /** Показывать число людей в фильтрах на главном экране. */
    val homeCounts = BoolPref("home_counts", true)

    // --- ассистент «Ноа» ---
    val assistant = BoolPref("assistant", true)
    val assistantName = StringPref("assistant_name", "Ноа")
    val assistantVoice = BoolPref("assistant_voice", true)
    /** Показывать отдельную иконку ассистента на рабочем столе. */
    val assistantLauncher = BoolPref("assistant_launcher", false)
    /** Умный режим: Gemini Nano на устройстве. */
    val assistantBrain = BoolPref("assistant_brain", true)

    /** Тихий снимок фронтальной камерой при неверном PIN-коде. */
    val intruderPhoto = BoolPref("intruder_photo", false)

    /** Встряхнуть телефон — архив закрывается и пропадает из недавних. */
    val shakeToClose = BoolPref("shake_close", true)

    // --- автоматическая резервная копия ---
    val autoBackup = BoolPref("auto_backup", false)
    /** Папка (SAF tree URI): память телефона, SD-карта или флешка. */
    val autoBackupFolder = StringPref("auto_backup_folder", "")
    /** Раз в сколько дней: 1 или 7. */
    val autoBackupDays = StringPref("auto_backup_days", "1")
    /** Сколько последних копий хранить в папке. */
    val autoBackupKeep = StringPref("auto_backup_keep", "5")
    val autoBackupLastAt = StringPref("auto_backup_last_at", "0")
    /** Текст ошибки последней попытки; пусто — всё хорошо. */
    val autoBackupError = StringPref("auto_backup_error", "")

    /** Язык интерфейса: auto (как в системе), ru, uk, en. */
    val uiLang = StringPref("ui_lang", "auto")

    /** Своё название внутри приложения; пусто — как у значка. */
    val appTitle = StringPref("app_title", "")

    // --- телефоны ---
    val country = StringPref("country", "UA")
    val defaultCountry: Country get() = PhoneFormat.byIso(country.value.value)

    // --- календарь ---
    val apptChannel = StringPref("appt_channel", NotifyChannel.WHATSAPP.name)
    val apptDuration = StringPref("appt_duration", "60")
    val apptClientOffsets = StringPref("appt_client_offsets", "${24 * 60},120")
    val apptMyOffsets = StringPref("appt_my_offsets", "60")
    val apptSendConfirm = StringPref("appt_send_confirm", "true")
    /** Язык сообщений по умолчанию. */
    val messageLang = StringPref("msg_lang", MessageLang.RU.name)
    val defaultLang: MessageLang get() = MessageLang.of(messageLang.value.value) ?: MessageLang.RU

    private val templates = HashMap<String, StringPref>()

    /** Шаблон на конкретном языке. Для русского сохранены ключи версии 2.0. */
    fun template(kind: TemplateKind, lang: MessageLang): StringPref {
        val key = "tpl_" + kind.name.lowercase() + if (lang == MessageLang.RU) "" else "_" + lang.name.lowercase()
        return templates.getOrPut(key) { StringPref(key, lang.template(kind)) }
    }

    /** Язык для человека: его личный или общий. */
    fun langFor(p: Person): MessageLang = MessageLang.of(p.language) ?: defaultLang

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

    inner class BoolPref(private val key: String, default: Boolean) {
        private val state = MutableStateFlow(prefs.getBoolean(key, default))
        val value: StateFlow<Boolean> = state.asStateFlow()
        fun set(v: Boolean) { prefs.edit().putBoolean(key, v).apply(); state.value = v }
    }

    private companion object {
        const val LOCK = "lock"
        const val BIOMETRIC = "biometric"
        const val SECURE = "secure_screen"
        const val BIRTHDAYS = "birthdays"
        const val SORT = "sort"
    }
}

enum class SortMode(private val titleRu: String) {
    NAME("По имени"),
    RECENT("Недавно добавленные"),
    CLOSENESS("По близости"),
    LONG_AGO("Давно не общались");

    /** Название на языке интерфейса. */
    val title: String get() = t(titleRu)

    companion object {
        fun of(name: String?) = entries.firstOrNull { it.name == name } ?: NAME
    }
}
