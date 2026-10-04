package com.kartoteka.app.ui.settings

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Info
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.kartoteka.app.i18n.t
import com.kartoteka.app.ui.app
import com.kartoteka.app.ui.components.OutlinedTextField

/** Настройки голосового ассистента «Ноа». */
@Composable
fun AssistantSettings() {
    val app = app()
    val context = LocalContext.current
    val s = app.settings
    val on by s.assistant.value.collectAsState()
    val voice by s.assistantVoice.value.collectAsState()
    val launcher by s.assistantLauncher.value.collectAsState()
    val brain by s.assistantBrain.value.collectAsState()
    val name by s.assistantName.value.collectAsState()
    var rename by remember { mutableStateOf(false) }

    ToggleRow(Icons.Default.RecordVoiceOver, t("Ассистент"), t("Кнопка-микрофон на главном экране"), on, s.assistant::set)
    if (on) {
        ToggleRow(
            Icons.Default.AutoAwesome, t("Умный режим (офлайн ИИ)"),
            t("Модель Gemma на телефоне понимает свободную речь. Нужна разовая загрузка модели (~1.3 ГБ)."),
            brain,
        ) { s.assistantBrain.set(it) }
        if (brain) BrainModelRow()
        ActionRow(Icons.Default.RecordVoiceOver, t("Имя ассистента"), name.ifBlank { "Ноа" }) { rename = true }
        ToggleRow(Icons.Default.RecordVoiceOver, t("Отвечать голосом"), t("Женский голос; читает ответы вслух"), voice, s.assistantVoice::set)
        ToggleRow(Icons.Default.Apps, t("Иконка на рабочем столе"), t("Отдельный значок для быстрого запуска ассистента"), launcher) { v ->
            s.assistantLauncher.set(v)
            setLauncher(context, v)
        }
    }

    if (rename) {
        var value by remember { mutableStateOf(name) }
        AlertDialog(
            onDismissRequest = { rename = false },
            title = { Text(t("Имя ассистента")) },
            text = {
                OutlinedTextField(
                    value, { value = it.take(20) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text(t("Как обращаться")) },
                )
            },
            confirmButton = { TextButton(onClick = { s.assistantName.set(value.trim().ifBlank { "Ноа" }); rename = false }) { Text(t("Сохранить")) } },
            dismissButton = { TextButton(onClick = { rename = false }) { Text(t("Отмена")) } },
        )
    }
}


private fun setLauncher(context: android.content.Context, on: Boolean) = runCatching {
    context.packageManager.setComponentEnabledSetting(
        ComponentName(context.packageName, "com.kartoteka.app.assistant.NoaLauncher"),
        if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        PackageManager.DONT_KILL_APP,
    )
}

/** Управление файлом модели для умного режима: выбрать файл, скачать по ссылке, удалить. */
@Composable
private fun BrainModelRow() {
    val app = app()
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val brain = app.brain
    var has by remember { mutableStateOf(brain.hasModel()) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(-1) }
    var urlDialog by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }

    val pick = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true; progress = 0
        scope.launch {
            val ok = brain.importModel(uri) { progress = it }
            busy = false; progress = -1; has = brain.hasModel()
            android.widget.Toast.makeText(context, if (ok) t("Модель загружена") else t("Не удалось загрузить модель"), android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    when {
        busy -> ActionRow(Icons.Default.Downloading, t("Загрузка модели…"), if (progress in 0..100) "$progress%" else t("Подождите")) {}
        has -> {
            ActionRow(Icons.Default.AutoAwesome, t("Модель загружена"), t("%1\$s МБ. Умный режим готов к работе.", brain.modelSizeMb())) {}
            ActionRow(Icons.Default.Delete, t("Удалить модель"), t("Освободить место")) {
                brain.deleteModel(); has = false
            }
        }
        else -> {
            ActionRow(Icons.Default.FileOpen, t("Выбрать файл модели"), t("Если вы уже скачали файл Gemma (.task)")) {
                pick.launch(arrayOf("*/*"))
            }
            ActionRow(Icons.Default.Download, t("Скачать по ссылке"), t("Прямая ссылка на модель .task")) { urlDialog = true }
            ActionRow(Icons.Default.Info, t("Где взять модель"), t("Короткая инструкция")) { info = true }
        }
    }

    if (urlDialog) {
        var url by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { urlDialog = false },
            title = { Text(t("Скачать модель")) },
            text = {
                Column(androidx.compose.ui.Modifier.fillMaxWidth()) {
                    Text(t("Вставьте прямую ссылку на файл модели Gemma (.task). Загрузка ~1.3 ГБ."), style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(url, { url = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text("URL") })
                }
            },
            confirmButton = {
                TextButton(enabled = url.startsWith("http"), onClick = {
                    urlDialog = false; busy = true; progress = 0
                    scope.launch {
                        val ok = brain.downloadModel(url.trim()) { progress = it }
                        busy = false; progress = -1; has = brain.hasModel()
                        android.widget.Toast.makeText(context, if (ok) t("Модель загружена") else t("Не удалось скачать модель"), android.widget.Toast.LENGTH_SHORT).show()
                    }
                }) { Text(t("Скачать")) }
            },
            dismissButton = { TextButton(onClick = { urlDialog = false }) { Text(t("Отмена")) } },
        )
    }
    if (info) {
        AlertDialog(
            onDismissRequest = { info = false },
            title = { Text(t("Где взять модель")) },
            text = {
                Text(t("Нужна модель Gemma для MediaPipe в формате .task (около 1.3 ГБ).\n\n1. На сайте Hugging Face найдите litert-community (например, модель Gemma 2B в формате .task).\n2. Примите лицензию Gemma и скачайте файл .task на телефон или компьютер.\n3. Здесь нажмите «Выбрать файл модели» и укажите его, либо «Скачать по ссылке» с прямым адресом файла.\n\nМодель хранится только на вашем телефоне."))
            },
            confirmButton = { TextButton(onClick = { info = false }) { Text(t("Понятно")) } },
        )
    }
}
