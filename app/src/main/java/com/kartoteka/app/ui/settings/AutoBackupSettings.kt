package com.kartoteka.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kartoteka.app.data.AutoBackup
import com.kartoteka.app.i18n.t
import com.kartoteka.app.ui.app
import com.kartoteka.app.ui.components.FilterChip
import com.kartoteka.app.ui.components.OutlinedTextField
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date

/** Автоматическая резервная копия в выбранную папку или на флешку. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AutoBackupSettings() {
    val app = app()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = app.settings
    val enabled by s.autoBackup.value.collectAsState()
    val folder by s.autoBackupFolder.value.collectAsState()
    val days by s.autoBackupDays.value.collectAsState()
    val keep by s.autoBackupKeep.value.collectAsState()
    val lastAt by s.autoBackupLastAt.value.collectAsState()
    val error by s.autoBackupError.value.collectAsState()
    var askPassword by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    // Включение идёт по шагам: папка → пароль → первая копия.
    var enabling by remember { mutableStateOf(false) }

    fun runNow() {
        running = true
        scope.launch {
            val err = AutoBackup.run(app)
            running = false
            Toast.makeText(context, err ?: t("Резервная копия сохранена"), Toast.LENGTH_SHORT).show()
        }
    }

    fun finishEnable() {
        enabling = false
        s.autoBackup.set(true)
        AutoBackup.schedule(context, days.toIntOrNull() ?: 1)
        runNow()
    }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) { enabling = false; return@rememberLauncherForActivityResult }
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        s.autoBackupFolder.set(uri.toString())
        if (enabling) { if (AutoBackup.hasPassword(app)) finishEnable() else askPassword = true }
    }

    ToggleRow(Icons.Default.Schedule, t("Автоматическая копия"), t("Зашифрованная копия в выбранную папку или на флешку, без облака"), enabled) { v ->
        if (v) {
            enabling = true
            when {
                folder.isBlank() -> pickFolder.launch(null)
                !AutoBackup.hasPassword(app) -> askPassword = true
                else -> finishEnable()
            }
        } else {
            s.autoBackup.set(false)
            AutoBackup.cancel(context)
        }
    }
    if (enabled) {
        ActionRow(
            Icons.Default.Folder, t("Папка для копий"),
            if (folder.isBlank()) t("Не выбрана") else AutoBackup.folderName(context, Uri.parse(folder)),
        ) { pickFolder.launch(null) }
        FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("1" to t("Каждый день"), "7" to t("Раз в неделю")).forEach { (v, label) ->
                FilterChip(selected = days == v, onClick = {
                    s.autoBackupDays.set(v); AutoBackup.schedule(context, v.toInt())
                }, label = { Text(label) })
            }
        }
        FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("3", "5", "10").forEach { v ->
                FilterChip(selected = keep == v, onClick = { s.autoBackupKeep.set(v) }, label = { Text(t("Хранить %1\$s", v)) })
            }
        }
        val last = lastAt.toLongOrNull()?.takeIf { it > 0 }
        val fmt = remember { SimpleDateFormat("d MMMM yyyy, HH:mm", com.kartoteka.app.i18n.I18n.locale) }
        ActionRow(
            Icons.Default.Save,
            if (running) t("Сохраняю копию…") else t("Сделать копию сейчас"),
            when {
                error.isNotBlank() -> error
                last != null -> t("Последняя копия: %1\$s", fmt.format(Date(last)))
                else -> t("Копий ещё не было")
            },
        ) { if (!running) runNow() }
        ActionRow(Icons.Default.Key, t("Пароль копий"), t("Нужен, чтобы восстановить копию на любом телефоне")) { askPassword = true }
    }

    if (askPassword) {
        BackupPasswordDialog(
            onDismiss = { askPassword = false; enabling = false },
            onSave = { pw ->
                AutoBackup.setPassword(app, pw)
                askPassword = false
                if (enabling) finishEnable()
            },
        )
    }
}

@Composable
private fun BackupPasswordDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    val ok = first.length >= 6 && first == second
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Пароль для копий")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    t("Каждая копия шифруется этим паролем. Без него копию не открыть — запишите его в надёжном месте. Минимум 6 символов."),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(first, { first = it }, label = { Text(t("Пароль")) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                OutlinedTextField(second, { second = it }, label = { Text(t("Повторите пароль")) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                if (second.isNotEmpty() && first != second) {
                    Text(t("Пароли не совпадают"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(enabled = ok, onClick = { onSave(first) }) { Text(t("Сохранить")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Отмена")) } },
    )
}
