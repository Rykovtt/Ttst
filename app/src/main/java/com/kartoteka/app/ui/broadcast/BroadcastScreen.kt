package com.kartoteka.app.ui.broadcast

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.kartoteka.app.messaging.AutoSend
import com.kartoteka.app.messaging.JobState
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kartoteka.app.KartotekaApp
import com.kartoteka.app.data.ArchiveLogic
import com.kartoteka.app.data.JournalEntry
import com.kartoteka.app.data.PersonFull
import com.kartoteka.app.messaging.Messaging
import com.kartoteka.app.ui.app
import com.kartoteka.app.ui.components.Avatar
import com.kartoteka.app.ui.components.ColorDot
import com.kartoteka.app.ui.groups.PeoplePickerDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Channel(val title: String, val description: String, val icon: ImageVector, val personal: Boolean) {
    WHATSAPP("WhatsApp", "Каждому своё сообщение. С авто-отправкой уходит само, по очереди, с паузами", Icons.AutoMirrored.Filled.Chat, true),
    TELEGRAM("Telegram", "Каждому своё сообщение. С авто-отправкой уходит само, по очереди, с паузами", Icons.AutoMirrored.Filled.Send, true),
    SMS_AUTO("SMS автоматически", "Персональные SMS уходят сами, без открытия приложений. Оплачивается по тарифу оператора", Icons.Default.Sms, true),
    SMS_APP("SMS одним сообщением", "Открыть SMS-приложение сразу со всеми номерами — один текст всем", Icons.Default.Sms, false),
    SHARE("В группу / чат мессенджера", "Отправить текст в существующий групповой чат WhatsApp, Telegram, Viber или любой другой", Icons.Default.Share, false),
}

class BroadcastViewModel(private val app: KartotekaApp, initialGroupId: Long, initialPersonIds: List<Long>) : ViewModel() {
    val all = app.repository.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val groups = app.repository.observeGroups().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val recipients = mutableStateListOf<Long>()
    val lang = app.settings.defaultLang
    var text by mutableStateOf(TextFieldValue(lang.greeting, TextRange(lang.greeting.length)))
    var channel by mutableStateOf(Channel.WHATSAPP)
    var logToJournal by mutableStateOf(true)
    var sending by mutableStateOf(false)
    val sent = mutableStateMapOf<Long, Boolean>()
    var autoProgress by mutableStateOf<Pair<Int, Int>?>(null)
    var autoMode by mutableStateOf(true)
    val delaySec = app.settings.autoSendDelaySec

    /** Полностью автоматическая рассылка в мессенджер через службу авто-отправки. */
    fun startAuto(context: android.content.Context) {
        val targets = selected().filter { canReceive(it) }
        val byId = targets.associateBy { it.person.id }
        val ch = if (channel == Channel.WHATSAPP) com.kartoteka.app.data.NotifyChannel.WHATSAPP else com.kartoteka.app.data.NotifyChannel.TELEGRAM
        val jobs = targets.map { pf ->
            com.kartoteka.app.messaging.SendJob(pf.person.id, pf.person.displayName, ch, (if (ch == com.kartoteka.app.data.NotifyChannel.WHATSAPP) pf.whatsapp else pf.telegram)!!, messageFor(pf))
        }
        sending = true
        com.kartoteka.app.messaging.AutoSend.start(context, jobs, delaySec.value.value.toIntOrNull() ?: 6) { job, ok ->
            if (ok) byId[job.personId]?.let(::markSent)
        }
    }

    init {
        viewModelScope.launch {
            val list = app.repository.getAll()
            recipients.addAll(initialPersonIds)
            if (initialGroupId != 0L) addGroup(initialGroupId, list)
        }
    }

    fun addGroup(groupId: Long, list: List<PersonFull> = all.value) {
        list.filter { pf -> pf.groups.any { it.id == groupId } }.forEach { if (it.person.id !in recipients) recipients.add(it.person.id) }
    }

    fun selected(): List<PersonFull> = all.value.filter { it.person.id in recipients }.sortedBy { it.person.sortKey }

    fun canReceive(pf: PersonFull, ch: Channel = channel): Boolean = when (ch) {
        Channel.WHATSAPP -> pf.whatsapp != null
        Channel.TELEGRAM -> pf.telegram != null
        Channel.SMS_AUTO, Channel.SMS_APP -> pf.phone != null
        Channel.SHARE -> true
    }

    fun messageFor(pf: PersonFull) = ArchiveLogic.fillTemplate(text.text, pf.person).trim()

    fun markSent(pf: PersonFull) {
        sent[pf.person.id] = true
        viewModelScope.launch {
            app.repository.touchContact(pf.person.id)
            if (logToJournal) {
                app.repository.addJournal(
                    JournalEntry(personId = pf.person.id, kind = "Переписка", text = "Рассылка (${channel.title}): ${messageFor(pf)}")
                )
            }
        }
    }

    fun sendAutoSms(context: android.content.Context) {
        val targets = selected().filter { canReceive(it, Channel.SMS_AUTO) && sent[it.person.id] != true }
        viewModelScope.launch {
            autoProgress = 0 to targets.size
            targets.forEachIndexed { i, pf ->
                runCatching { Messaging.sendSmsDirect(context, pf.phone!!, messageFor(pf)) }
                    .onSuccess { markSent(pf) }
                autoProgress = (i + 1) to targets.size
                delay(1200) // не перегружаем оператора
            }
        }
    }

    fun reset() {
        sending = false; sent.clear(); autoProgress = null
        com.kartoteka.app.messaging.AutoSend.clear()
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BroadcastScreen(initialGroupId: Long, initialPersonIds: List<Long>, onBack: (() -> Unit)?) {
    val app = app()
    val vm: BroadcastViewModel = viewModel(key = "bc_${initialGroupId}_${initialPersonIds.joinToString()}") {
        BroadcastViewModel(app, initialGroupId, initialPersonIds)
    }
    val context = LocalContext.current
    val all by vm.all.collectAsState()
    val groups by vm.groups.collectAsState()
    val selected = all.filter { it.person.id in vm.recipients }.sortedBy { it.person.sortKey }
    var picker by remember { mutableStateOf(false) }
    var criteria by remember { mutableStateOf(false) }
    var confirmAuto by remember { mutableStateOf(false) }
    var confirmMessenger by remember { mutableStateOf(false) }
    var serviceOn by remember { mutableStateOf(AutoSend.isServiceEnabled(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { serviceOn = AutoSend.isServiceEnabled(context) }
    val autoProgressState by AutoSend.progress.collectAsState()
    val delay by vm.delaySec.value.collectAsState()

    val smsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) confirmAuto = true
    }

    LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Row(Modifier.statusBarsPadding().padding(start = if (onBack != null) 4.dp else 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                Column {
                    Text("Рассылка", style = MaterialTheme.typography.headlineLarge)
                    Text("SMS и мессенджеры — персонально каждому или в группу", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (!vm.sending) {
            // --- 1. Получатели ---
            item { StepTitle("1", "Кому") }
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    if (groups.isNotEmpty()) {
                        Text("Добавить группу целиком:", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(4.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            groups.forEach { gc ->
                                AssistChip(
                                    onClick = { vm.addGroup(gc.group.id) },
                                    label = { Text("${gc.group.emoji} ${gc.group.name} · ${gc.count}".trim()) },
                                    leadingIcon = { ColorDot(gc.group.color) },
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { picker = true }) {
                            Icon(Icons.Default.PersonAdd, null); Spacer(Modifier.width(6.dp)); Text("Люди")
                        }
                        FilledTonalButton(onClick = { criteria = true }) {
                            Icon(Icons.Default.FilterAlt, null); Spacer(Modifier.width(6.dp)); Text("По критериям")
                        }
                        if (vm.recipients.isNotEmpty()) TextButton(onClick = { vm.recipients.clear() }) { Text("Очистить") }
                    }
                    if (selected.isNotEmpty()) {
                        Text("Выбрано: ${selected.size}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(4.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            selected.forEach { pf ->
                                InputChip(
                                    selected = false,
                                    onClick = { vm.recipients.remove(pf.person.id) },
                                    label = { Text(pf.person.displayName) },
                                    avatar = { Avatar(pf.person, InputChipDefaults.AvatarSize) },
                                    trailingIcon = { Icon(Icons.Default.Close, "Убрать", Modifier.size(16.dp)) },
                                )
                            }
                        }
                    }
                }
            }

            // --- 2. Сообщение ---
            item { StepTitle("2", "Сообщение") }
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    OutlinedTextField(
                        value = vm.text,
                        onValueChange = { vm.text = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 130.dp),
                        placeholder = { Text("Текст сообщения") },
                    )
                    Text("Подставить:", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        vm.lang.personTokens.forEach { ph ->
                            AssistChip(onClick = {
                                val t = vm.text
                                val newText = t.text.substring(0, t.selection.start) + ph + t.text.substring(t.selection.end)
                                vm.text = TextFieldValue(newText, TextRange(t.selection.start + ph.length))
                            }, label = { Text(ph) })
                        }
                    }
                    selected.firstOrNull()?.let { first ->
                        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Column(Modifier.padding(14.dp)) {
                                Text("Так увидит ${first.person.displayName}:", style = MaterialTheme.typography.labelMedium)
                                Text(vm.messageFor(first), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }

            // --- 3. Канал ---
            item { StepTitle("3", "Как отправить") }
            items(Channel.entries) { ch ->
                val missing = selected.count { !vm.canReceive(it, ch) }
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = if (vm.channel == ch) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                        .selectable(selected = vm.channel == ch, role = Role.RadioButton) { vm.channel = ch },
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = vm.channel == ch, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Icon(ch.icon, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(ch.title, style = MaterialTheme.typography.titleSmall)
                            Text(ch.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (missing > 0 && selected.isNotEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                                    Text(
                                        " Нет контакта у $missing — пропустим",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (vm.channel == Channel.WHATSAPP || vm.channel == Channel.TELEGRAM) {
                item {
                    AutoSendCard(
                        serviceOn = serviceOn,
                        auto = vm.autoMode,
                        onAuto = { vm.autoMode = it },
                        delay = delay.toIntOrNull() ?: 6,
                        onDelay = { vm.delaySec.set(it.toString()) },
                        onEnable = { AutoSend.openServiceSettings(context) },
                    )
                }
            }
            item {
                Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = vm.logToJournal, onCheckedChange = { vm.logToJournal = it })
                    Text("Записать в хронику каждого человека")
                }
            }
            item {
                val usable = selected.filter { vm.canReceive(it) }
                val enabled = vm.text.text.isNotBlank() && (vm.channel == Channel.SHARE || usable.isNotEmpty())
                Button(
                    enabled = enabled,
                    onClick = {
                        when (vm.channel) {
                            Channel.SHARE -> {
                                Messaging.share(context, selected.firstOrNull()?.let { if (selected.size == 1) vm.messageFor(it) else null }
                                    ?: ArchiveLogic.fillTemplate(vm.text.text, com.kartoteka.app.data.Person(firstName = friendsWord(vm.lang))).trim())
                                selected.forEach(vm::markSent)
                            }
                            Channel.SMS_APP -> {
                                Messaging.sms(context, usable.mapNotNull { it.phone }, if (usable.size == 1) vm.messageFor(usable[0]) else vm.text.text.replace(Regex("\\{[^}]+}"), "").trim())
                                usable.forEach(vm::markSent)
                            }
                            Channel.SMS_AUTO -> {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) confirmAuto = true
                                else smsPermission.launch(Manifest.permission.SEND_SMS)
                            }
                            Channel.WHATSAPP, Channel.TELEGRAM ->
                                if (serviceOn && vm.autoMode) confirmMessenger = true else vm.sending = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, null); Spacer(Modifier.width(8.dp))
                    Text(if (vm.channel.personal) "Начать рассылку (${usable.size})" else "Отправить")
                }
                if (vm.channel == Channel.SHARE && selected.size > 1) {
                    Text(
                        "Подсказка: в групповой чат уйдёт один общий текст, имя заменится на «${friendsWord(vm.lang)}».",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }
        } else if (autoProgressState != null) {
            // --- Автоматическая рассылка ---
            val p = autoProgressState!!
            item {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        if (p.running) "Отправляем автоматически: ${p.done} из ${p.jobs.size}" else "Готово: отправлено ${p.sent} из ${p.jobs.size}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (p.failed > 0) Text("Не удалось: ${p.failed}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.size(8.dp))
                    LinearProgressIndicator(progress = { if (p.jobs.isEmpty()) 1f else p.done / p.jobs.size.toFloat() }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.size(8.dp))
                    Text(
                        if (p.running) "Можно не трогать телефон — приложение само открывает чаты и нажимает «Отправить». Остановить можно здесь или из уведомления."
                        else "Рассылка завершена.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        if (p.running) OutlinedButton(onClick = { AutoSend.stop() }) { Text("Остановить") }
                        TextButton(onClick = vm::reset) { Text(if (p.running) "Скрыть" else "Новая рассылка") }
                    }
                }
            }
            items(p.jobs.size) { i ->
                val job = p.jobs[i]
                val pf = all.firstOrNull { it.person.id == job.personId }
                val st = p.states[i]
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    when (st) {
                        JobState.SENT -> Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        JobState.FAILED -> Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error)
                        JobState.SENDING -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        JobState.PENDING -> Icon(Icons.Default.RadioButtonUnchecked, null, tint = MaterialTheme.colorScheme.outline)
                    }
                    Spacer(Modifier.width(12.dp))
                    if (pf != null) Avatar(pf.person, 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(job.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            when (st) {
                                JobState.SENT -> "Отправлено"
                                JobState.FAILED -> "Не отправлено (нет в мессенджере или не удалось нажать)"
                                JobState.SENDING -> "Отправляем…"
                                JobState.PENDING -> "В очереди"
                            },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            // --- Очередь отправки ---
            val queue = selected.filter { vm.canReceive(it) }
            val done = queue.count { vm.sent[it.person.id] == true }
            val next = queue.firstOrNull { vm.sent[it.person.id] != true }
            item {
                Column(Modifier.padding(16.dp)) {
                    Text("Отправлено $done из ${queue.size}", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.size(8.dp))
                    LinearProgressIndicator(progress = { if (queue.isEmpty()) 1f else done / queue.size.toFloat() }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.size(12.dp))
                    if (next != null) {
                        Button(onClick = { sendOne(context, vm, next) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.AutoMirrored.Filled.Send, null); Spacer(Modifier.width(8.dp))
                            Text("Отправить: ${next.person.displayName}")
                        }
                    } else {
                        Text("Готово! Все сообщения отправлены 🎉", color = MaterialTheme.colorScheme.primary)
                    }
                    TextButton(onClick = vm::reset) { Text(if (next == null) "Новая рассылка" else "Прервать") }
                }
            }
            items(queue, key = { it.person.id }) { pf ->
                val isSent = vm.sent[pf.person.id] == true
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isSent) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null,
                        tint = if (isSent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    )
                    Spacer(Modifier.width(12.dp))
                    Avatar(pf.person, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(pf.person.displayName, style = MaterialTheme.typography.titleSmall)
                        Text(vm.messageFor(pf), style = MaterialTheme.typography.bodySmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { sendOne(context, vm, pf) }) { Text(if (isSent) "Ещё раз" else "Отправить") }
                }
            }
        }
    }

    vm.autoProgress?.let { (done, total) ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(if (done < total) "Отправка SMS…" else "SMS отправлены") },
            text = {
                Column {
                    Text("$done из $total")
                    Spacer(Modifier.size(8.dp))
                    LinearProgressIndicator(progress = { if (total == 0) 1f else done / total.toFloat() }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { if (done >= total) TextButton(onClick = vm::reset) { Text("Готово") } },
        )
    }
    if (confirmAuto) {
        val count = selected.count { vm.canReceive(it, Channel.SMS_AUTO) }
        AlertDialog(
            onDismissRequest = { confirmAuto = false },
            title = { Text("Отправить $count SMS?") },
            text = { Text("Каждый получит персональное сообщение. SMS оплачиваются по тарифу вашего оператора.") },
            confirmButton = { TextButton(onClick = { confirmAuto = false; vm.sendAutoSms(context) }) { Text("Отправить") } },
            dismissButton = { TextButton(onClick = { confirmAuto = false }) { Text("Отмена") } },
        )
    }
    if (confirmMessenger) {
        val count = selected.count { vm.canReceive(it) }
        AlertDialog(
            onDismissRequest = { confirmMessenger = false },
            title = { Text("Отправить $count ${ArchiveLogic.plural(count.toLong(), "сообщение", "сообщения", "сообщений")} в ${vm.channel.title}?") },
            text = {
                Text("Приложение по очереди откроет чаты и само нажмёт «Отправить», пауза между сообщениями — ${delay} с и немного случайности. " +
                    "Не пользуйтесь телефоном во время рассылки. Большие рассылки незнакомым людям мессенджеры могут посчитать спамом.")
            },
            confirmButton = { TextButton(onClick = { confirmMessenger = false; vm.startAuto(context) }) { Text("Начать") } },
            dismissButton = { TextButton(onClick = { confirmMessenger = false }) { Text("Отмена") } },
        )
    }
    if (criteria) {
        ModalBottomSheet(onDismissRequest = { criteria = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            CriteriaSheet(all, groups) { ids, replace ->
                if (replace) vm.recipients.clear()
                ids.forEach { if (it !in vm.recipients) vm.recipients.add(it) }
                criteria = false
            }
        }
    }
    if (picker) {
        PeoplePickerDialog(
            all = all,
            initial = vm.recipients.toSet(),
            onDismiss = { picker = false },
            onPick = { ids -> vm.recipients.clear(); vm.recipients.addAll(ids); picker = false },
        )
    }
}

private fun friendsWord(lang: com.kartoteka.app.data.MessageLang) = when (lang) {
    com.kartoteka.app.data.MessageLang.RU -> "друзья"
    com.kartoteka.app.data.MessageLang.UK -> "друзі"
    com.kartoteka.app.data.MessageLang.EN -> "friends"
}

private fun sendOne(context: android.content.Context, vm: BroadcastViewModel, pf: PersonFull) {
    val msg = vm.messageFor(pf)
    when (vm.channel) {
        Channel.WHATSAPP -> Messaging.whatsapp(context, pf.whatsapp!!, msg)
        Channel.TELEGRAM -> Messaging.telegram(context, pf.telegram!!, msg)
        else -> return
    }
    vm.markSent(pf)
}

@Composable
private fun StepTitle(num: String, title: String) {
    Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary) {
            Text(num, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

/** Настройка авто-отправки в мессенджерах. */
@Composable
private fun AutoSendCard(serviceOn: Boolean, auto: Boolean, onAuto: (Boolean) -> Unit, delay: Int, onDelay: (Int) -> Unit, onEnable: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (serviceOn) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            if (serviceOn) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoMode, null)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Отправлять автоматически", style = MaterialTheme.typography.titleSmall)
                        Text("Без нажатия «Отправить» на каждом контакте", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(auto, onAuto)
                }
                if (auto) {
                    Text("Пауза между сообщениями: $delay с", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    Slider(value = delay.toFloat(), onValueChange = { onDelay(it.toInt()) }, valueRange = 3f..30f, steps = 26)
                }
            } else {
                Text("Авто-отправка выключена", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Чтобы сообщения уходили сами, включите в настройках телефона: Спец. возможности → «Картотека: авто-отправка». " +
                        "Если переключатель неактивен: Настройки → Приложения → Картотека → ⋮ → «Разрешить ограниченные настройки».",
                    style = MaterialTheme.typography.bodySmall,
                )
                FilledTonalButton(onClick = onEnable, modifier = Modifier.padding(top = 8.dp)) { Text("Открыть настройки") }
            }
        }
    }
}
