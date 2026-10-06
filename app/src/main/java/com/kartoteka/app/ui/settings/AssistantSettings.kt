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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
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
        // Доступ к уведомлениям: плеер (пауза, перемешать), чтение и ответ в мессенджерах по команде.
        var access by remember { mutableStateOf(com.kartoteka.app.assistant.NoaNotifications.granted(context)) }
        var accessHelp by remember { mutableStateOf(false) }
        androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
            access = com.kartoteka.app.assistant.NoaNotifications.granted(context)
            onPauseOrDispose { }
        }
        ActionRow(
            Icons.Default.Apps, t("Музыка и сообщения"),
            if (access) t("Включено: пауза, «дальше», перемешать, читаю ответы вслух и отвечаю в мессенджерах по команде")
            else t("Выключено. Нажмите и разрешите доступ к уведомлениям — тогда я смогу управлять музыкой, читать ответы и отвечать в WhatsApp/Telegram"),
        ) { if (access) com.kartoteka.app.assistant.NoaNotifications.openSettings(context) else accessHelp = true }
        if (accessHelp) {
            AlertDialog(
                onDismissRequest = { accessHelp = false },
                title = { Text(t("Доступ к уведомлениям")) },
                text = { Text(t("1. Нажмите «Открыть» и включите RVault в списке.\n\nЕсли переключатель серый и пишет «Ограниченная настройка»: откройте «О приложении» → меню ⋮ вверху справа → «Разрешить ограниченные настройки», потом вернитесь и включите доступ. Так Android защищает приложения, установленные не из Google Play.")) },
                confirmButton = { TextButton(onClick = { accessHelp = false; com.kartoteka.app.assistant.NoaNotifications.openSettings(context) }) { Text(t("Открыть")) } },
                dismissButton = { TextButton(onClick = { accessHelp = false; com.kartoteka.app.assistant.NoaNotifications.openAppDetails(context) }) { Text(t("О приложении")) } },
            )
        }
        WakeRow()
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

    // Журнал последнего сбоя: можно посмотреть, скопировать и прислать разработчику.
    var crash by remember { mutableStateOf(com.kartoteka.app.assistant.CrashLog.read(context)) }
    var showCrash by remember { mutableStateOf(false) }
    crash?.let { text ->
        ActionRow(Icons.Default.Close, t("Журнал последнего сбоя"), text.lineSequence().first().take(60)) { showCrash = true }
        if (showCrash) AlertDialog(
            onDismissRequest = { showCrash = false },
            title = { Text(t("Журнал последнего сбоя")) },
            text = { androidx.compose.foundation.text.selection.SelectionContainer {
                Text(text.take(2500), style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    modifier = Modifier.heightIn(max = 360.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())) } },
            confirmButton = { TextButton(onClick = {
                val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                cm?.setPrimaryClip(android.content.ClipData.newPlainText("crash", text))
                showCrash = false
            }) { Text(t("Скопировать")) } },
            dismissButton = { TextButton(onClick = {
                com.kartoteka.app.assistant.CrashLog.clear(context); crash = null; showCrash = false
            }) { Text(t("Очистить")) } },
        )
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
            confirmButton = { TextButton(onClick = {
                s.assistantName.set(value.trim().ifBlank { "Ноа" }); rename = false
                if (s.assistantWake.value.value) com.kartoteka.app.assistant.WakeService.restart(context)
            }) { Text(t("Сохранить")) } },
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
            var crashed by remember { mutableStateOf(brain.crashed) }
            if (crashed) {
                Text(
                    brain.crashDetail,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                ActionRow(Icons.Default.AutoAwesome, t("Попробовать снова"), t("Закройте другие приложения и запустите модель ещё раз")) {
                    brain.clearCrash(); crashed = false
                }
            }
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

/**
 * «Ноа, ты тут?» — звать ассистента голосом, не касаясь телефона. Нужны: микрофон, право «поверх других окон»
 * (иначе Android не даст показать сферу из фона) и небольшая офлайн-модель (~45 МБ, скачивается один раз).
 */
@Composable
private fun WakeRow() {
    val context = LocalContext.current
    val s = app().settings
    val on by s.assistantWake.value.collectAsState()
    var progress by remember { mutableStateOf<Int?>(null) }
    var failed by remember { mutableStateOf(false) }
    var waitOverlay by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    fun enable() {
        s.assistantOverlay.set(true)            // без сферы поверх экрана зов не имеет смысла
        s.assistantWake.set(true)
        com.kartoteka.app.assistant.WakeService.start(context)
    }
    fun download() {
        if (com.kartoteka.app.assistant.WakeModel.ready(context)) { enable(); return }
        failed = false; progress = 0
        scope.launch {
            val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.kartoteka.app.assistant.WakeModel.download(context) { progress = it }
            }
            progress = null
            if (ok) enable() else failed = true
        }
    }
    fun overlay() {
        if (android.provider.Settings.canDrawOverlays(context)) download()
        else {
            waitOverlay = true
            runCatching {
                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:" + context.packageName)))
            }
        }
    }
    val askPermissions = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted -> if (granted[android.Manifest.permission.RECORD_AUDIO] == true) overlay() }

    // Вернулись из системных настроек с выданным правом — продолжаем.
    androidx.lifecycle.compose.LifecycleResumeEffect(waitOverlay) {
        if (waitOverlay && android.provider.Settings.canDrawOverlays(context)) { waitOverlay = false; download() }
        onPauseOrDispose { }
    }

    val name = s.assistantName.value.collectAsState().value.ifBlank { "Ноа" }
    ToggleRow(
        Icons.Default.RecordVoiceOver, t("Звать голосом: «%1\$s»", name),
        when {
            progress != null -> t("Загружаю модель распознавания… %1\$s%%", progress)
            failed -> t("Не удалось скачать модель. Проверьте интернет и включите ещё раз.")
            on -> t("Слушаю имя «%1\$s». Скажите «%1\$s, ты тут?» — я проснусь, выслушаю команду и снова усну. При музыке и видео работают «%1\$s, пауза / дальше / назад / громче / тише». Звук остаётся на телефоне.", name)
            else -> t("Работает без рук: скажите имя — и ассистент проснётся. Слушает только имя, офлайн, звук не сохраняется. Сфера открывается без PIN, телефон должен быть разблокирован.")
        },
        on || progress != null,
    ) { v ->
        if (v) {
            val need = buildList {
                add(android.Manifest.permission.RECORD_AUDIO)
                if (android.os.Build.VERSION.SDK_INT >= 33) add(android.Manifest.permission.POST_NOTIFICATIONS)
            }.toTypedArray()
            askPermissions.launch(need)
        } else {
            s.assistantWake.set(false)
            com.kartoteka.app.assistant.WakeService.stop(context)
        }
    }
    if (on) {
        val locked by s.assistantWakeLocked.value.collectAsState()
        ToggleRow(
            Icons.Default.RecordVoiceOver, t("Сфера на заблокированном экране"),
            t("Включено: на зов сфера появляется и над экраном блокировки — без PIN, ею сможет пользоваться любой, кто рядом. Выключено: при блокировке работают только «%1\$s, пауза / дальше / громче…», а на зов ассистент попросит разблокировать телефон.", name),
            locked, s.assistantWakeLocked::set,
        )
    }
}
