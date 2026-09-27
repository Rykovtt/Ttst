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
            "Картотека 1.0 · все данные хранятся только на этом устройстве",
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
