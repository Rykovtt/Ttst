package com.kartoteka.app.ui.settings

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
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
    val name by s.assistantName.value.collectAsState()
    var rename by remember { mutableStateOf(false) }

    ToggleRow(Icons.Default.RecordVoiceOver, t("Ассистент"), t("Кнопка-микрофон на главном экране"), on, s.assistant::set)
    if (on) {
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
