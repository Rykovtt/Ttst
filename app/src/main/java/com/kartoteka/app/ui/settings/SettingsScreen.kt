package com.kartoteka.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.runtime.saveable.rememberSaveable
import com.kartoteka.app.ui.components.ScreenTitle

import com.kartoteka.app.i18n.t

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.kartoteka.app.ui.components.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.kartoteka.app.MainActivity
import com.kartoteka.app.data.BackupManager
import com.kartoteka.app.reminders.BirthdayWorker
import com.kartoteka.app.ui.app
import com.kartoteka.app.ui.components.SectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed interface BackupDialog {
    data class Export(val uri: android.net.Uri) : BackupDialog
    data class Import(val uri: android.net.Uri) : BackupDialog
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onImportContacts: () -> Unit, onServices: () -> Unit = {}, onNoa: () -> Unit = {}) {
    val app = app()
    val context = LocalContext.current
    val settings = app.settings
    val lock by settings.lockEnabled.collectAsState()
    val secure by settings.secureScreen.collectAsState()
    val birthdays by settings.birthdayReminders.collectAsState()
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<BackupDialog?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var countryPicker by remember { mutableStateOf(false) }
    var resumeTick by remember { mutableStateOf(0) }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { resumeTick++ }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        settings.setBirthdayReminders(ok)
        if (ok) BirthdayWorker.schedule(context)
    }
    // «Звонить сразу»: разрешение CALL_PHONE — после ответа перечитываем состояние
    val callPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { resumeTick++ }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) dialog = BackupDialog.Export(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) dialog = BackupDialog.Import(uri)
    }

    val iso by settings.country.value.collectAsState()
    val phoneSub = com.kartoteka.app.data.PhoneFormat.byIso(iso).let { "${it.flag} ${t(it.name)} (+${it.code})" }
    val uiLangCode by settings.uiLang.value.collectAsState()
    val uiScaleNow by app.settings.uiScale.value.collectAsState()
    val textScaleNow by app.settings.textScale.value.collectAsState()
    val scaleSub = t("Интерфейс %1\$s · текст %2\$s", pct(uiScaleNow), pct(textScaleNow))
    val uiLangSub = com.kartoteka.app.i18n.UiLang.entries.firstOrNull { it.code == uiLangCode }?.let { if (it == com.kartoteka.app.i18n.UiLang.AUTO) t(it.title) else it.title }.orEmpty()
    val msgLang by settings.messageLang.value.collectAsState()
    val msgLangSub = com.kartoteka.app.data.MessageLang.entries.firstOrNull { it.name == msgLang }?.title
        ?: t("Как в интерфейсе (%1\$s)", settings.defaultLang.title)
    val autoOn = remember(resumeTick) { com.kartoteka.app.messaging.AutoSend.isServiceEnabled(context) }
    val assistantOn by settings.assistant.value.collectAsState()
    val assistantName by settings.assistantName.value.collectAsState()
    val assistantSub = if (assistantOn) t("%1\$s — ваш ИИ-помощник", assistantName.ifBlank { "Ноа" }) else t("Выключен")
    val people by app.repository.observeAll().collectAsState(initial = emptyList())
    var search by rememberSaveable { mutableStateOf("") }

    val entries = buildList {

        add(SettingEntry(t("Значок и название"), Icons.Default.Palette, t("Маскировка значка и название"), "значок иконка маскировка название калькулятор") {
            AppearanceSettings(settings)
        })

        add(SettingEntry(t("Приватность"), Icons.Default.Shield, if (lock) t("Блокировка включена") else t("Блокировка выключена"), "pin пин блокировка отпечаток скриншот шифрование встряхнуть") {
            LockSettings()
            ToggleRow(
                Icons.Default.VisibilityOff, t("Скрывать содержимое"),
                t("Запрет скриншотов и размытие в списке недавних приложений"), secure, settings::setSecureScreen,
            )
            InfoLine(Icons.Default.EnhancedEncryption, t("База данных зашифрована AES-256, ключ хранится в защищённом хранилище Android. Фото лежат во внутренней памяти приложения и не видны в галерее. Облачное резервирование Google отключено."))
        })

        add(SettingEntry(t("Телефоны"), Icons.Default.Phone, phoneSub, "страна номер код телефон") {
            val iso by settings.country.value.collectAsState()
            val country = com.kartoteka.app.data.PhoneFormat.byIso(iso)
            ActionRow(Icons.Default.Public, t("Страна по умолчанию: %1\$s %2\$s (+%3\$s)", country.flag, t(country.name), country.code),
                t("Номер «%1\$s…» без кода сохранится как «+%2\$s…»", country.trunk, country.code)) { countryPicker = true }
            ActionRow(Icons.Default.AutoFixHigh, t("Привести все номера к международному виду"), t("Для уже сохранённых контактов")) {
                scope.launch {
                    busy = true
                    val n = app.repository.normalizeAllPhones(country)
                    busy = false
                    message = if (n == 0) t("Все номера уже в международном формате") else t("Исправлено номеров: %1\$s", n)
                }
            }
        })

        add(SettingEntry(t("Размер интерфейса"), Icons.Default.FormatSize, scaleSub, "масштаб размер текст шрифт крупнее мельче масштабування розмір") {
            ScaleSettings(app.settings)
        })
        add(SettingEntry(t("Язык приложения"), Icons.Default.Language, uiLangSub, "язык мова language") {
            val uiLang by settings.uiLang.value.collectAsState()
            androidx.compose.foundation.layout.FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.kartoteka.app.i18n.UiLang.entries.forEach { l ->
                    com.kartoteka.app.ui.components.FilterChip(
                        selected = uiLang == l.code,
                        onClick = {
                            if (uiLang != l.code) {
                                settings.uiLang.set(l.code)
                                com.kartoteka.app.i18n.I18n.init(context, l)
                                (context as? android.app.Activity)?.recreate()
                            }
                        },
                        label = { Text(if (l == com.kartoteka.app.i18n.UiLang.AUTO) t(l.title) else l.title) },
                    )
                }
            }
            Text(
                t("Язык меню и кнопок. Тексты сообщений людям настраиваются отдельно — ниже."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
        })

        add(SettingEntry(t("Язык сообщений"), Icons.Default.Translate, msgLangSub, "язык сообщений шаблоны") {
            val lang by settings.messageLang.value.collectAsState()
            Text(
                t("На этом языке будут шаблоны подтверждений и напоминаний, даты, дни недели и приветствие в рассылке. ") +
                    t("Для отдельного человека язык можно поменять в его карточке."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
            androidx.compose.foundation.layout.FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.kartoteka.app.ui.components.FilterChip(lang.isBlank(), { settings.messageLang.set("") }, label = { Text(t("Как в интерфейсе")) })
                com.kartoteka.app.data.MessageLang.entries.forEach { l ->
                    com.kartoteka.app.ui.components.FilterChip(lang == l.name, { settings.messageLang.set(l.name) }, label = { Text(l.title) })
                }
            }
        })

        add(SettingEntry(t("Записи и календарь"), Icons.Default.CalendarMonth, t("Длительность, напоминания, услуги"), "запись календарь услуги шаблоны напоминания длительность") {
            CalendarSettings(settings, onServices)
        })

        add(SettingEntry(t("Авто-действия"), Icons.Default.AutoMode, if (autoOn) t("Включена") else t("Выключена"), "whatsapp telegram viber авто отправка звонок позвонить вызов") {
            val on = remember(resumeTick) { com.kartoteka.app.messaging.AutoSend.isServiceEnabled(context) }
            val callOn = remember(resumeTick) { com.kartoteka.app.messaging.Messaging.canCallDirect(context) }
            ToggleRow(Icons.Default.Call, t("Звонить сразу"), t("Ноа начинает звонок сама, без нажатия «Вызов» в звонилке"), callOn) { v ->
                if (v) callPermission.launch(Manifest.permission.CALL_PHONE)
                // Отозвать разрешение можно только в настройках телефона
                else runCatching {
                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.fromParts("package", context.packageName, null)))
                }
            }
            val delay by settings.autoSendDelaySec.value.collectAsState()
            ActionRow(
                Icons.Default.AutoMode,
                if (on) t("Включена ✓") else t("Выключена — нажмите, чтобы включить"),
                t("Приложение «RVServices» само нажимает «Отправить» в WhatsApp, Telegram и Viber во время рассылок и напоминаний. ") +
                    t("Установите его, затем: Настройки → Спец. возможности → «RVServices». Если переключатель неактивен: Приложения → «RVServices» → ⋮ → «Разрешить ограниченные настройки»."),
            ) { com.kartoteka.app.messaging.AutoSend.openServiceSettings(context) }
            Text(t("Пауза между сообщениями: %1\$s с", delay), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 18.dp))
            androidx.compose.material3.Slider(
                value = (delay.toIntOrNull() ?: 6).toFloat(),
                onValueChange = { settings.autoSendDelaySec.set(it.toInt().toString()) },
                valueRange = 3f..30f, steps = 26,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
            InfoLine(Icons.Default.Info, t("Если телефон заблокирован в момент напоминания, придёт уведомление — одно нажатие, и сообщение уйдёт. SMS отправляются полностью в фоне."))
        })

        add(SettingEntry(t("Напоминания"), Icons.Default.Cake, if (birthdays) t("Дни рождения — включены") else t("Дни рождения — выключены"), "день рождения уведомления") {
            ToggleRow(Icons.Default.Cake, t("Дни рождения"), t("Уведомление в день рождения и за 3 дня"), birthdays) { v ->
                if (v && Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else {
                    settings.setBirthdayReminders(v)
                    if (v) BirthdayWorker.schedule(context) else BirthdayWorker.cancel(context)
                }
            }
        })

        add(SettingEntry(t("Ассистент Ноа"), Icons.Default.Mic, assistantSub, "ноа ассистент голос ии модель", accent = true) {
            AssistantSettings()
        })

        add(SettingEntry(t("Данные"), Icons.Default.Backup, t("Копии, импорт и восстановление"), "резервная копия импорт восстановление бэкап") {
            ActionRow(Icons.Default.Contacts, t("Импорт из контактов телефона"), t("Перенести людей из телефонной книги"), onImportContacts)
            ActionRow(Icons.Default.Backup, t("Создать резервную копию"), t("Зашифрованный файл с данными и фото")) {
                val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                exportLauncher.launch("rvault-${stamp}.krtk")
            }
            ActionRow(Icons.Default.Restore, t("Восстановить из копии"), t("Загрузить файл .krtk")) {
                importLauncher.launch(arrayOf("*/*"))
            }
            AutoBackupSettings()
        })

    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        SettingsHero(app.settings, people.size, search) { search = it }
        Spacer(Modifier.height(10.dp))
        val q = search.trim().lowercase()
        val shown = if (q.isEmpty()) entries else entries.filter { it.title.lowercase().contains(q) || it.keywords.contains(q) || it.subtitle.lowercase().contains(q) }
        if (shown.isEmpty()) {
            Text(t("Ничего не найдено"), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(22.dp))
        } else {
            SettingsGroup(shown, forceOpen = q.isNotEmpty() && shown.size == 1, onNoa = onNoa)
        }
        Text(
            t("Все данные хранятся только на этом устройстве. Разработчик Rykov."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(20.dp),
        )
    }

    when (val d = dialog) {
        is BackupDialog.Export -> PasswordDialog(
            title = t("Пароль для копии"),
            hint = t("Без пароля файл сможет прочитать любой. Запомните пароль — восстановить его нельзя."),
            confirm = t("Сохранить"),
            showReplace = false,
            onDismiss = { dialog = null },
        ) { pass, _ ->
            dialog = null; busy = true
            scope.launch {
                message = runCatching { withContext(Dispatchers.IO) { app.backup.export(d.uri, pass) } }
                    .fold({ t("Сохранено людей: %1\$s", it) }, { t("Ошибка: %1\$s", it.message) })
                busy = false
            }
        }
        is BackupDialog.Import -> PasswordDialog(
            title = t("Восстановление"),
            hint = t("Введите пароль, указанный при создании копии (если был)."),
            confirm = t("Восстановить"),
            showReplace = true,
            onDismiss = { dialog = null },
        ) { pass, replace ->
            dialog = null; busy = true
            scope.launch {
                message = runCatching { withContext(Dispatchers.IO) { app.backup.import(d.uri, pass, replace) } }
                    .fold({ t("Восстановлено людей: %1\$s", it) }, { if (it is BackupManager.WrongPasswordException) it.message else t("Ошибка: %1\$s", it.message) })
                busy = false
            }
        }
        null -> Unit
    }

    if (countryPicker) {
        com.kartoteka.app.ui.components.CountryPickerDialog(onDismiss = { countryPicker = false }) { settings.country.set(it.iso); countryPicker = false }
    }
    if (busy) {
        AlertDialog(onDismissRequest = {}, confirmButton = {}, title = { Text(t("Подождите…")) }, text = {
            Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(28.dp)); Spacer(Modifier.width(16.dp)); Text(t("Работаем с данными")) }
        })
    }
    message?.let {
        AlertDialog(onDismissRequest = { message = null }, confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } }, text = { Text(it) })
    }
}

@Composable
private fun PasswordDialog(
    title: String,
    hint: String,
    confirm: String,
    showReplace: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, Boolean) -> Unit,
) {
    var pass by remember { mutableStateOf("") }
    var replace by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(hint, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    pass, { pass = it }, label = { Text(t("Пароль")) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                if (showReplace) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(replace, { replace = it })
                        Text(t("Заменить текущую картотеку (иначе — добавить)"))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(pass, replace) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Отмена")) } },
    )
}

@Composable
internal fun ToggleRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
internal fun ActionRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InfoLine(icon: ImageVector, text: String) {
    Row(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}


private fun pct(v: String) = "${((v.toFloatOrNull() ?: 1f) * 100).toInt()}%"

/** Размер интерфейса (всё вместе) и отдельно текста — сразу применяется во всём приложении. */
@Composable
private fun ScaleSettings(settings: com.kartoteka.app.data.Settings) {
    val ui by settings.uiScale.value.collectAsState()
    val text by settings.textScale.value.collectAsState()
    val uiSteps = listOf("0.9", "1.0", "1.1", "1.25")
    val textSteps = listOf("0.9", "1.0", "1.15", "1.3")
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(t("Элементы и текст"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 6.dp))
        com.kartoteka.app.ui.components.Segmented(
            uiSteps.map(::pct), uiSteps.indexOfFirst { it.toFloat() == ui.toFloatOrNull() }.coerceAtLeast(0),
            { settings.uiScale.set(uiSteps[it]) }, Modifier.fillMaxWidth(),
        )
        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 14.dp))
        Text(t("Только текст"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 6.dp))
        com.kartoteka.app.ui.components.Segmented(
            textSteps.map(::pct), textSteps.indexOfFirst { it.toFloat() == text.toFloatOrNull() }.coerceAtLeast(0),
            { settings.textScale.set(textSteps[it]) }, Modifier.fillMaxWidth(),
        )
        Text(
            t("Применяется сразу во всём приложении."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
