package com.kartoteka.app.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Cake
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
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
fun SettingsScreen(onImportContacts: () -> Unit) {
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
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) dialog = BackupDialog.Export(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) dialog = BackupDialog.Import(uri)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Column(Modifier.statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp)) {
            Text("Настройки", style = MaterialTheme.typography.headlineLarge)
        }

        SectionCard("Значок и название", Icons.Default.Palette) {
            AppearanceSettings(settings)
        }

        SectionCard("Приватность", Icons.Default.Shield) {
            ToggleRow(
                Icons.Default.Fingerprint, "Блокировка приложения",
                "Отпечаток, лицо или PIN-код телефона при входе", lock,
            ) { v ->
                if (!v) settings.setLockEnabled(false)
                else if (MainActivity.canUseLock(context as FragmentActivity)) settings.setLockEnabled(true)
                else message = "Сначала настройте блокировку экрана телефона (PIN, отпечаток или лицо)."
            }
            ToggleRow(
                Icons.Default.VisibilityOff, "Скрывать содержимое",
                "Запрет скриншотов и размытие в списке недавних приложений", secure, settings::setSecureScreen,
            )
            InfoLine(Icons.Default.EnhancedEncryption, "База данных зашифрована AES-256, ключ хранится в защищённом хранилище Android. Фото лежат во внутренней памяти приложения и не видны в галерее. Облачное резервирование Google отключено.")
        }

        SectionCard("Телефоны", Icons.Default.Phone) {
            val iso by settings.country.value.collectAsState()
            val country = com.kartoteka.app.data.PhoneFormat.byIso(iso)
            ActionRow(Icons.Default.Public, "Страна по умолчанию: ${country.flag} ${country.name} (+${country.code})",
                "Номер «${country.trunk}…» без кода сохранится как «+${country.code}…»") { countryPicker = true }
            ActionRow(Icons.Default.AutoFixHigh, "Привести все номера к международному виду", "Для уже сохранённых контактов") {
                scope.launch {
                    busy = true
                    val n = app.repository.normalizeAllPhones(country)
                    busy = false
                    message = if (n == 0) "Все номера уже в международном формате" else "Исправлено номеров: $n"
                }
            }
        }

        SectionCard("Язык сообщений", Icons.Default.Translate) {
            val lang by settings.messageLang.value.collectAsState()
            Text(
                "На этом языке будут шаблоны подтверждений и напоминаний, даты, дни недели и приветствие в рассылке. " +
                    "Для отдельного человека язык можно поменять в его карточке.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
            androidx.compose.foundation.layout.FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.kartoteka.app.data.MessageLang.entries.forEach { l ->
                    androidx.compose.material3.FilterChip(lang == l.name, { settings.messageLang.set(l.name) }, label = { Text(l.title) })
                }
            }
        }

        SectionCard("Записи и календарь", Icons.Default.CalendarMonth) {
            CalendarSettings(settings)
        }

        SectionCard("Авто-отправка в мессенджерах", Icons.Default.AutoMode) {
            val on = remember(resumeTick) { com.kartoteka.app.messaging.AutoSend.isServiceEnabled(context) }
            val delay by settings.autoSendDelaySec.value.collectAsState()
            ActionRow(
                Icons.Default.AutoMode,
                if (on) "Включена ✓" else "Выключена — нажмите, чтобы включить",
                "Картотека сама нажимает «Отправить» в WhatsApp и Telegram во время рассылок и напоминаний. " +
                    "Настройки → Спец. возможности → «Картотека: авто-отправка». Если переключатель неактивен: Приложения → Картотека → ⋮ → «Разрешить ограниченные настройки».",
            ) { com.kartoteka.app.messaging.AutoSend.openServiceSettings(context) }
            Text("Пауза между сообщениями: ${delay} с", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 18.dp))
            androidx.compose.material3.Slider(
                value = (delay.toIntOrNull() ?: 6).toFloat(),
                onValueChange = { settings.autoSendDelaySec.set(it.toInt().toString()) },
                valueRange = 3f..30f, steps = 26,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
            InfoLine(Icons.Default.Info, "Если телефон заблокирован в момент напоминания, придёт уведомление — одно нажатие, и сообщение уйдёт. SMS отправляются полностью в фоне.")
        }

        SectionCard("Напоминания", Icons.Default.Cake) {
            ToggleRow(Icons.Default.Cake, "Дни рождения", "Уведомление в день рождения и за 3 дня", birthdays) { v ->
                if (v && Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else {
                    settings.setBirthdayReminders(v)
                    if (v) BirthdayWorker.schedule(context) else BirthdayWorker.cancel(context)
                }
            }
        }

        SectionCard("Данные", Icons.Default.Backup) {
            ActionRow(Icons.Default.Contacts, "Импорт из контактов телефона", "Перенести людей из телефонной книги", onImportContacts)
            ActionRow(Icons.Default.Backup, "Создать резервную копию", "Зашифрованный файл с данными и фото") {
                val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                exportLauncher.launch("kartoteka-$stamp.krtk")
            }
            ActionRow(Icons.Default.Restore, "Восстановить из копии", "Загрузить файл .krtk") {
                importLauncher.launch(arrayOf("*/*"))
            }
        }

        Text(
            "Картотека 2.1 · все данные хранятся только на этом устройстве. Карта — © OpenStreetMap",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(20.dp),
        )
    }

    when (val d = dialog) {
        is BackupDialog.Export -> PasswordDialog(
            title = "Пароль для копии",
            hint = "Без пароля файл сможет прочитать любой. Запомните пароль — восстановить его нельзя.",
            confirm = "Сохранить",
            showReplace = false,
            onDismiss = { dialog = null },
        ) { pass, _ ->
            dialog = null; busy = true
            scope.launch {
                message = runCatching { withContext(Dispatchers.IO) { app.backup.export(d.uri, pass) } }
                    .fold({ "Сохранено людей: $it" }, { "Ошибка: ${it.message}" })
                busy = false
            }
        }
        is BackupDialog.Import -> PasswordDialog(
            title = "Восстановление",
            hint = "Введите пароль, указанный при создании копии (если был).",
            confirm = "Восстановить",
            showReplace = true,
            onDismiss = { dialog = null },
        ) { pass, replace ->
            dialog = null; busy = true
            scope.launch {
                message = runCatching { withContext(Dispatchers.IO) { app.backup.import(d.uri, pass, replace) } }
                    .fold({ "Восстановлено людей: $it" }, { if (it is BackupManager.WrongPasswordException) it.message else "Ошибка: ${it.message}" })
                busy = false
            }
        }
        null -> Unit
    }

    if (countryPicker) {
        com.kartoteka.app.ui.components.CountryPickerDialog(onDismiss = { countryPicker = false }) { settings.country.set(it.iso); countryPicker = false }
    }
    if (busy) {
        AlertDialog(onDismissRequest = {}, confirmButton = {}, title = { Text("Подождите…") }, text = {
            Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(28.dp)); Spacer(Modifier.width(16.dp)); Text("Работаем с данными") }
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
                    pass, { pass = it }, label = { Text("Пароль") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                if (showReplace) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(replace, { replace = it })
                        Text("Заменить текущую картотеку (иначе — добавить)")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(pass, replace) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun ToggleRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
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
private fun ActionRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
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
