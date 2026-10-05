package com.kartoteka.app.ui.settings

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import com.kartoteka.app.assistant.LlmBrain
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Close
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
            t("ИИ прямо на телефоне понимает свободную речь. Нужно один раз скачать модель (~1.6 ГБ)."),
            brain,
        ) { s.assistantBrain.set(it) }
        if (brain) BrainModelRow()
        ActionRow(Icons.Default.RecordVoiceOver, t("Имя ассистента"), name.ifBlank { "Ноа" }) { rename = true }
        ToggleRow(Icons.Default.RecordVoiceOver, t("Отвечать голосом"), t("Женский голос; читает ответы вслух"), voice, s.assistantVoice::set)
        ToggleRow(Icons.Default.Apps, t("Иконка на рабочем столе"), t("Отдельный значок для быстрого запуска ассистента"), launcher) { v ->
            s.assistantLauncher.set(v)
            setLauncher(context, v)
        }
        if (launcher) {
            val overlay by s.assistantOverlay.value.collectAsState()
            ToggleRow(
                Icons.Default.AutoAwesome, t("Сфера поверх экрана"),
                t("Иконка вызывает ассистента прямо поверх рабочего стола, без входа в приложение. Он может зачитывать данные из картотеки без PIN — выключите, если телефоном пользуются другие."),
                overlay, s.assistantOverlay::set,
            )
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

/** Модель для умного режима: одна кнопка «Скачать», загрузка идёт в фоне, приложение само её подхватывает. */
@Composable
private fun BrainModelRow() {
    val app = app()
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val brain = app.brain
    var has by remember { mutableStateOf(brain.hasModel()) }
    var dl by remember { mutableStateOf(brain.syncDownload()) }
    var importing by remember { mutableStateOf(-1) }
    var confirm by remember { mutableStateOf<LlmBrain.Model?>(null) }
    val ram = remember { brain.ramGb() }
    fun sizeGb(m: LlmBrain.Model) = "%.1f".format(m.bytes / 1e9)

    // Следим за загрузкой, пока открыт экран; по окончании модель подключается сама.
    LaunchedEffect(dl is LlmBrain.Download.Running) {
        while (dl is LlmBrain.Download.Running) {
            kotlinx.coroutines.delay(1000)
            dl = brain.syncDownload()
            if (dl == LlmBrain.Download.Done) {
                has = brain.hasModel()
                android.widget.Toast.makeText(context, t("Модель скачана — умный режим готов"), android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    val pick = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importing = 0
        scope.launch {
            val ok = brain.importModel(uri) { importing = it }
            importing = -1; has = brain.hasModel()
            android.widget.Toast.makeText(context, if (ok) t("Модель загружена") else t("Не удалось загрузить модель"), android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val running = dl as? LlmBrain.Download.Running
    when {
        importing >= 0 -> ActionRow(Icons.Default.Downloading, t("Загрузка модели…"), "$importing%") {}
        running != null -> {
            ActionRow(
                Icons.Default.Downloading,
                t("Скачивание модели…") + " ${running.percent}%",
                if (running.waiting) t("Ожидание сети. Загрузка продолжится сама.")
                else t("%1\$s из %2\$s МБ. Можно закрыть приложение — загрузка идёт в фоне.", running.doneMb, running.totalMb),
            ) {}
            androidx.compose.material3.LinearProgressIndicator(
                progress = { running.percent / 100f },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            ActionRow(Icons.Default.Close, t("Отменить загрузку"), "") {
                brain.cancelDownload(); dl = LlmBrain.Download.None
            }
        }
        has -> {
            val m = brain.installed()
            ActionRow(
                Icons.Default.AutoAwesome, t("Модель установлена: %1\$s", m?.title ?: t("своя")),
                t("%1\$s МБ. Умный режим готов к работе.", brain.modelSizeMb()),
            ) {}
            if (m == LlmBrain.Model.FAST) {
                ActionRow(
                    Icons.Default.Download, t("Перейти на умную модель"),
                    t("%1\$s — заметно умнее, ~%2\$s ГБ. В телефоне %3\$s ГБ памяти (нужно от %4\$s).",
                        LlmBrain.Model.SMART.title, sizeGb(LlmBrain.Model.SMART), "%.0f".format(ram), LlmBrain.Model.SMART.minRamGb),
                ) { confirm = LlmBrain.Model.SMART }
            }
            if (m == LlmBrain.Model.SMART) {
                ActionRow(Icons.Default.Download, t("Перейти на быструю модель"), t("%1\$s — легче и быстрее, ~%2\$s ГБ.", LlmBrain.Model.FAST.title, sizeGb(LlmBrain.Model.FAST))) {
                    confirm = LlmBrain.Model.FAST
                }
            }
            ActionRow(Icons.Default.Delete, t("Удалить модель"), t("Освободить место")) {
                brain.deleteModel(); has = false
            }
        }
        else -> {
            if (dl is LlmBrain.Download.Failed) {
                Text(
                    t("Загрузка прервалась. Проверьте интернет и нажмите «Скачать» ещё раз."),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            val smartOk = ram >= LlmBrain.Model.SMART.minRamGb - 0.6
            ActionRow(
                Icons.Default.AutoAwesome, t("Скачать умную модель") + if (smartOk) " · " + t("рекомендуется") else "",
                t("%1\$s, ~%2\$s ГБ. Лучше понимает речь и отвечает по вашим данным. В телефоне %3\$s ГБ памяти (нужно от %4\$s).",
                    LlmBrain.Model.SMART.title, sizeGb(LlmBrain.Model.SMART), "%.0f".format(ram), LlmBrain.Model.SMART.minRamGb),
            ) { confirm = LlmBrain.Model.SMART }
            ActionRow(
                Icons.Default.Download, t("Скачать быструю модель") + if (!smartOk) " · " + t("рекомендуется") else "",
                t("%1\$s, ~%2\$s ГБ. Легче и быстрее, для любого телефона.", LlmBrain.Model.FAST.title, sizeGb(LlmBrain.Model.FAST)),
            ) { confirm = LlmBrain.Model.FAST }
            ActionRow(Icons.Default.FileOpen, t("Свой файл модели"), t("Для опытных: файл .task с телефона")) {
                pick.launch(arrayOf("*/*"))
            }
        }
    }

    confirm?.let { model ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(t("Скачать %1\$s", model.title)) },
            text = { Text(t("Будет скачано около %1\$s ГБ. Загрузка идёт в фоне — можно пользоваться телефоном и закрыть приложение. Когда закончится, умный режим включится сам.", sizeGb(model))) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    when {
                        !brain.enoughSpace(model) -> android.widget.Toast.makeText(context, t("Не хватает места: нужно около %1\$s ГБ свободной памяти", "%.1f".format(model.bytes / 1e9 + 0.2)), android.widget.Toast.LENGTH_LONG).show()
                        brain.startDownload(model) -> dl = brain.syncDownload()
                        else -> android.widget.Toast.makeText(context, t("Не удалось скачать модель"), android.widget.Toast.LENGTH_SHORT).show()
                    }
                }) { Text(t("Скачать")) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(t("Отмена")) } },
        )
    }
}
