package com.kartoteka.app.ui.calendar

import com.kartoteka.app.i18n.t

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import com.kartoteka.app.ui.components.AssistChip
import com.kartoteka.app.ui.components.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import com.kartoteka.app.ui.components.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kartoteka.app.KartotekaApp
import com.kartoteka.app.data.Appointment
import com.kartoteka.app.data.AppointmentFull
import com.kartoteka.app.data.AppointmentLogic
import com.kartoteka.app.data.AppointmentReminder
import com.kartoteka.app.data.AppointmentStatus
import com.kartoteka.app.data.ArchiveLogic
import com.kartoteka.app.data.JournalEntry
import com.kartoteka.app.data.NotifyChannel
import com.kartoteka.app.data.PersonFull
import com.kartoteka.app.data.ReminderTarget
import com.kartoteka.app.data.ServiceTemplate
import com.kartoteka.app.data.SortMode
import com.kartoteka.app.data.TemplateKind
import com.kartoteka.app.messaging.Sender
import com.kartoteka.app.reminders.ReminderScheduler
import com.kartoteka.app.ui.app
import com.kartoteka.app.ui.components.Avatar
import com.kartoteka.app.ui.components.SectionCard
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

/** Какое сообщение предложить отправить человеку. */
enum class MessageKind(private val titleRu: String, val template: TemplateKind) {
    CONFIRM("Подтверждение записи", TemplateKind.CONFIRM),
    RESCHEDULE("Перенос записи", TemplateKind.RESCHEDULE),
    REMINDER("Напоминание", TemplateKind.REMINDER),
    CANCEL("Отмена записи", TemplateKind.CANCEL),
    ;

    val title: String get() = t(titleRu)
}

class AppointmentEditViewModel(private val app: KartotekaApp, val id: Long, personId: Long, dateEpoch: Long) : ViewModel() {
    private val repo = app.repository
    private val settings = app.settings
    val isNew = id == 0L
    var loaded by mutableStateOf(isNew)
    var person by mutableStateOf<PersonFull?>(null)
    var date by mutableStateOf(if (dateEpoch != 0L) LocalDate.ofEpochDay(dateEpoch) else LocalDate.now())
    var time by mutableStateOf(defaultTime(date))
    var duration by mutableStateOf(settings.apptDuration.value.value.toIntOrNull() ?: 60)
    var title by mutableStateOf("")
    var place by mutableStateOf("")
    var notes by mutableStateOf("")
    var channel by mutableStateOf(NotifyChannel.of(settings.apptChannel.value.value))
    var sendConfirm by mutableStateOf(settings.apptSendConfirm.value.value == "true")
    val clientOffsets = mutableStateListOf<Int>().apply { addAll(AppointmentLogic.offsetsFromString(settings.apptClientOffsets.value.value)) }
    val myOffsets = mutableStateListOf<Int>().apply { addAll(AppointmentLogic.offsetsFromString(settings.apptMyOffsets.value.value)) }
    var original by mutableStateOf<AppointmentFull?>(null)
    var dayOthers by mutableStateOf<List<AppointmentFull>>(emptyList())
    var saving by mutableStateOf(false)
    val everyone = repo.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val services = repo.observeServices().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    var serviceId by mutableStateOf<Long?>(null)
    val service: ServiceTemplate? get() = services.value.firstOrNull { it.id == serviceId }

    init {
        viewModelScope.launch {
            val all = repo.getAll()
            if (!isNew) {
                repo.getAppointment(id)?.let { af ->
                    val a = af.appointment
                    original = af
                    val dt = AppointmentLogic.zoned(a.start)
                    date = dt.toLocalDate(); time = dt.toLocalTime(); duration = a.durationMin
                    title = a.title; place = a.place; notes = a.notes
                    serviceId = a.serviceId
                    channel = a.notifyChannel
                    sendConfirm = false
                    clientOffsets.clear(); clientOffsets.addAll(af.reminders.filter { it.target == ReminderTarget.CLIENT.name }.map { it.offsetMin }.distinct().sorted())
                    myOffsets.clear(); myOffsets.addAll(af.reminders.filter { it.target == ReminderTarget.ME.name }.map { it.offsetMin }.distinct().sorted())
                    person = all.firstOrNull { it.person.id == a.personId }
                }
            } else if (personId != 0L) {
                all.firstOrNull { it.person.id == personId }?.let(::selectPerson)
            }
            loadDay()
            loaded = true
        }
    }

    /** Выбор услуги подставляет её название, длительность, место и напоминания. */
    fun selectService(s: ServiceTemplate?) {
        serviceId = s?.id
        if (s == null) return
        title = s.name
        duration = s.durationMin
        if (s.place.isNotBlank()) place = s.place
        clientOffsets.clear(); clientOffsets.addAll(AppointmentLogic.offsetsFromString(s.clientOffsets))
        myOffsets.clear(); myOffsets.addAll(AppointmentLogic.offsetsFromString(s.myOffsets))
    }

    /** Новая услуга из того, что уже введено в записи. */
    fun draftService(): ServiceTemplate = com.kartoteka.app.ui.services.newService(settings, title.trim(), duration).copy(
        place = place.trim(),
        clientOffsets = AppointmentLogic.offsetsToString(clientOffsets),
        myOffsets = AppointmentLogic.offsetsToString(myOffsets),
    )

    fun createService(s: ServiceTemplate) = viewModelScope.launch {
        val id = repo.saveService(s)
        serviceId = id
        title = s.name
        duration = s.durationMin
    }

    fun selectPerson(pf: PersonFull) {
        person = pf
        if (Sender.targetFor(pf, channel) == null && channel != NotifyChannel.NONE) {
            channel = listOf(NotifyChannel.WHATSAPP, NotifyChannel.TELEGRAM, NotifyChannel.SMS).firstOrNull { Sender.targetFor(pf, it) != null } ?: NotifyChannel.NONE
        }
        if (place.isBlank()) place = ""
    }

    fun loadDay() = viewModelScope.launch {
        val from = AppointmentLogic.millis(date.atStartOfDay())
        dayOthers = repo.appointmentsBetween(from, from + 86_400_000L)
    }

    val startMillis get() = AppointmentLogic.millis(LocalDateTime.of(date, time))

    fun draft(): Appointment = Appointment(
        id = id, personId = person?.person?.id ?: 0, start = startMillis, durationMin = duration,
        title = title.trim(), place = place.trim(), notes = notes.trim(), channel = channel.name,
        status = original?.appointment?.status ?: AppointmentStatus.PLANNED.name,
        createdAt = original?.appointment?.createdAt ?: System.currentTimeMillis(),
        serviceId = serviceId,
    )

    val conflicts get() = AppointmentLogic.conflicts(draft(), dayOthers)

    fun langOf(): com.kartoteka.app.data.MessageLang = person?.person?.let(settings::langFor) ?: settings.defaultLang

    fun previewText(kind: MessageKind, lang: com.kartoteka.app.data.MessageLang = langOf()): String {
        val p = person?.person ?: return ""
        val template = AppointmentLogic.messageTemplate(service, kind.template, settings.template(kind.template, lang).value.value)
        return AppointmentLogic.fill(template, draft(), p, lang)
    }

    /** Сохраняет и возвращает, какое сообщение предложить отправить (или null). */
    fun save(done: (Long, MessageKind?) -> Unit) {
        val pf = person ?: return
        if (saving) return
        saving = true
        viewModelScope.launch {
            val a = draft()
            // Старые будильники отменяем — ниже поставим новые.
            if (!isNew) ReminderScheduler.cancel(app, repo.pendingRemindersFor(id).map { it.id })
            val (savedId, reminders) = repo.saveAppointment(a, clientOffsets.toList(), myOffsets.toList())
            ReminderScheduler.schedule(app, reminders)
            val moved = original?.appointment?.let { it.start != a.start } == true
            if (isNew) {
                repo.addJournal(JournalEntry(personId = pf.person.id, kind = "Событие",
                    text = t("Запись на %1\$s в %2\$s", AppointmentLogic.dateText(AppointmentLogic.zoned(a.start)), AppointmentLogic.timeText(AppointmentLogic.zoned(a.start))) +
                        if (a.title.isNotBlank()) ": ${a.title}" else ""))
            }
            saving = false
            val kind = when {
                channel == NotifyChannel.NONE -> null
                isNew && sendConfirm -> MessageKind.CONFIRM
                moved -> MessageKind.RESCHEDULE
                else -> null
            }
            original = repo.getAppointment(savedId)
            done(savedId, kind)
        }
    }

    fun setStatus(status: AppointmentStatus, done: () -> Unit) = viewModelScope.launch {
        ReminderScheduler.cancel(app, repo.pendingRemindersFor(id).map { it.id })
        repo.setAppointmentStatus(id, status)
        done()
    }

    /** Вернуть отменённую/прошедшую запись в план — с новыми напоминаниями. */
    fun restore(done: () -> Unit) {
        val o = original ?: return
        original = o.copy(appointment = o.appointment.copy(status = AppointmentStatus.PLANNED.name))
        save { _, _ -> done() }
    }

    fun delete(done: () -> Unit) = viewModelScope.launch {
        ReminderScheduler.cancel(app, repo.pendingRemindersFor(id).map { it.id })
        repo.deleteAppointment(id)
        done()
    }

    fun send(context: android.content.Context, ch: NotifyChannel, text: String, kind: MessageKind, onResult: (Sender.Result) -> Unit) {
        val pf = person ?: return
        val delay = settings.autoSendDelaySec.value.value.toIntOrNull() ?: 6
        val r = Sender.send(context, pf, ch, text, delay, interactive = true) { ok ->
            if (ok) app.appScope.launch {
                repo.addJournal(JournalEntry(personId = pf.person.id, kind = "Переписка", text = "${kind.title} (${ch.title}): ${text}"))
                repo.touchContact(pf.person.id)
            }
        }
        onResult(r)
    }

    private fun defaultTime(d: LocalDate): LocalTime {
        val now = LocalDateTime.now()
        return if (d == now.toLocalDate()) LocalTime.of(minOf(now.hour + 1, 23), 0) else LocalTime.of(10, 0)
    }
}

private val durations = listOf(15, 30, 45, 60, 90, 120, 180)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppointmentEditScreen(id: Long, personId: Long, dateEpoch: Long, onBack: () -> Unit, onOpenPerson: (Long) -> Unit) {
    val app = app()
    val context = LocalContext.current
    val vm: AppointmentEditViewModel = viewModel(key = "appt_${id}_${personId}_${dateEpoch}") { AppointmentEditViewModel(app, id, personId, dateEpoch) }
    val everyone by vm.everyone.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var pickPerson by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var customFor by remember { mutableStateOf<ReminderTarget?>(null) }
    var message by remember { mutableStateOf<MessageKind?>(null) }
    var closeAfterMessage by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var customDuration by remember { mutableStateOf(false) }
    var newService by remember { mutableStateOf<ServiceTemplate?>(null) }
    val services by vm.services.collectAsState()

    BackHandler(onBack = onBack)
    val status = vm.original?.appointment?.appointmentStatus ?: AppointmentStatus.PLANNED

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(if (vm.isNew) t("Новая запись") else t("Запись")) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.Close, t("Закрыть")) } },
                actions = {
                    Button(
                        enabled = vm.person != null && !vm.saving,
                        onClick = {
                            vm.save { _, kind ->
                                if (kind != null) { message = kind; closeAfterMessage = true } else onBack()
                            }
                        },
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Icon(Icons.Default.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Сохранить"))
                    }
                },
            )
        },
    ) { padding ->
        if (!vm.loaded) return@Scaffold
        LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding(), contentPadding = PaddingValues(bottom = 40.dp)) {
            item(key = "who") {
                SectionCard(t("Кто"), Icons.Default.Person) {
                    val pf = vm.person
                    Row(
                        Modifier.fillMaxWidth().clickable { if (vm.isNew) pickPerson = true else pf?.let { onOpenPerson(it.person.id) } }
                            .padding(horizontal = 18.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (pf != null) {
                            Avatar(pf.person, 48.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(pf.person.displayName, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    listOfNotNull(pf.phone?.let { com.kartoteka.app.data.PhoneFormat.pretty(it) }, pf.telegram).joinToString(" · ").ifBlank { t("нет контактов") },
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (vm.isNew) TextButton(onClick = { pickPerson = true }) { Text(t("Сменить")) }
                        } else {
                            FilledTonalButton(onClick = { pickPerson = true }) { Icon(Icons.Default.Search, null); Spacer(Modifier.width(6.dp)); Text(t("Выбрать человека")) }
                        }
                    }
                }
            }

            item(key = "when") {
                SectionCard(t("Когда"), Icons.Default.Event) {
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.weight(1.4f)) {
                            Icon(Icons.Default.CalendarMonth, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                            Text(vm.date.format(java.time.format.DateTimeFormatter.ofPattern("d MMM, EE", com.kartoteka.app.i18n.I18n.locale)))
                        }
                        OutlinedButton(onClick = { pickTime = true }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.AccessTime, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                            Text(AppointmentLogic.timeText(LocalDateTime.of(vm.date, vm.time)))
                        }
                    }
                    Text(t("Длительность"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 18.dp, top = 10.dp))
                    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        durations.forEach { d ->
                            FilterChip(vm.duration == d, { vm.duration = d }, label = { Text(if (d < 60) t("%1\$s мин", d) else if (d % 60 == 0) t("%1\$s ч", d / 60) else t("%1\$s ч %2\$s мин", d / 60, d % 60)) })
                        }
                        FilterChip(vm.duration !in durations, { customDuration = true }, label = { Text(if (vm.duration !in durations) t("%1\$s мин", vm.duration) else t("Другая…")) })
                    }
                    val end = LocalDateTime.of(vm.date, vm.time).plusMinutes(vm.duration.toLong())
                    Text(
                        t("до %1\$s", AppointmentLogic.timeText(end)),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 18.dp, top = 4.dp),
                    )
                    vm.conflicts.forEach { c ->
                        Row(Modifier.padding(horizontal = 18.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            val t = AppointmentLogic.zoned(c.appointment.start)
                            Text(
                                t("Пересекается: %1\$s в %2\$s", c.person?.displayName.orEmpty(), AppointmentLogic.timeText(t)),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            item(key = "what") {
                SectionCard(t("Что и где"), Icons.Default.Campaign) {
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(t("Услуга"), style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(vm.serviceId == null, { vm.selectService(null) }, label = { Text(t("Без услуги")) })
                            services.forEach { s ->
                                FilterChip(vm.serviceId == s.id, { vm.selectService(s) }, label = { Text(s.name) })
                            }
                            AssistChip(onClick = { newService = vm.draftService() }, label = { Text(t("Услуга")) },
                                leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(16.dp)) })
                        }
                        Text(
                            vm.service?.let { s ->
                                if (s.ownTemplates == 0) t("Сообщения — по общим шаблонам (у «%1\$s» своих нет)", s.name)
                                else t("Сообщения — по шаблонам услуги «%1\$s»", s.name)
                            } ?: t("Сообщения — по общим шаблонам"),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            vm.title, { vm.title = it }, label = { Text(t("Услуга / тема")) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            vm.place, { vm.place = it }, label = { Text(t("Место")) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            vm.notes, { vm.notes = it }, label = { Text(t("Заметка для себя")) },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                        )
                    }
                }
            }

            item(key = "notify") {
                SectionCard(t("Оповестить человека"), Icons.Default.Send) {
                    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        NotifyChannel.entries.forEach { ch ->
                            val available = ch == NotifyChannel.NONE || vm.person?.let { Sender.targetFor(it, ch) } != null
                            FilterChip(
                                selected = vm.channel == ch,
                                enabled = available,
                                onClick = { vm.channel = ch },
                                label = { Text(ch.title) },
                                leadingIcon = channelIcon(ch)?.let { icon -> { Icon(icon, null, Modifier.size(18.dp)) } },
                            )
                        }
                    }
                    if (vm.channel != NotifyChannel.NONE) {
                        if (vm.isNew) {
                            Row(Modifier.fillMaxWidth().clickable { vm.sendConfirm = !vm.sendConfirm }.padding(horizontal = 18.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(t("Сразу отправить подтверждение"))
                                    Text(t("Покажем текст перед отправкой — его можно поправить"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(vm.sendConfirm, { vm.sendConfirm = it })
                            }
                        }
                        Text(t("Напоминания человеку"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 18.dp, top = 8.dp))
                        OffsetChips(vm.clientOffsets, onCustom = { customFor = ReminderTarget.CLIENT })
                        ReminderSchedule(vm.startMillis, vm.clientOffsets, vm.original?.reminders.orEmpty().filter { it.target == ReminderTarget.CLIENT.name })
                    } else {
                        Text(
                            t("Человек не получит ни подтверждения, ни напоминаний"),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            item(key = "me") {
                SectionCard(t("Напомнить мне"), Icons.Default.NotificationsActive) {
                    OffsetChips(vm.myOffsets, onCustom = { customFor = ReminderTarget.ME })
                    ReminderSchedule(vm.startMillis, vm.myOffsets, emptyList())
                }
            }

            if (!vm.isNew) {
                item(key = "actions") {
                    SectionCard(t("Действия"), Icons.Default.Check) {
                        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(t("Статус: %1\$s", status.title), style = MaterialTheme.typography.bodyMedium)
                            if (vm.channel != NotifyChannel.NONE && status == AppointmentStatus.PLANNED) {
                                FilledTonalButton(onClick = { message = MessageKind.REMINDER; closeAfterMessage = false }, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.Send, null); Spacer(Modifier.width(8.dp)); Text(t("Напомнить сейчас"))
                                }
                            }
                            if (status != AppointmentStatus.DONE) {
                                OutlinedButton(onClick = { vm.setStatus(AppointmentStatus.DONE, onBack) }, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.CheckCircle, null); Spacer(Modifier.width(8.dp)); Text(t("Состоялось"))
                                }
                            }
                            if (status == AppointmentStatus.PLANNED) {
                                OutlinedButton(onClick = {
                                    vm.setStatus(AppointmentStatus.CANCELLED) {
                                        if (vm.channel != NotifyChannel.NONE) { message = MessageKind.CANCEL; closeAfterMessage = true } else onBack()
                                    }
                                }, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.Cancel, null); Spacer(Modifier.width(8.dp)); Text(t("Отменить запись"))
                                }
                            } else {
                                OutlinedButton(onClick = { vm.restore(onBack) }, modifier = Modifier.fillMaxWidth()) {
                                    Text(t("Вернуть в план"))
                                }
                            }
                            TextButton(onClick = { confirmDelete = true }) {
                                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error); Spacer(Modifier.width(8.dp))
                                Text(t("Удалить"), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    if (pickPerson) {
        PersonPickerDialog(everyone, onDismiss = { pickPerson = false }, onPick = { vm.selectPerson(it); pickPerson = false })
    }
    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = vm.date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { vm.date = LocalDate.ofEpochDay(it / 86_400_000L); vm.loadDay() }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(t("Отмена")) } },
        ) { DatePicker(state) }
    }
    if (pickTime) {
        val state = rememberTimePickerState(vm.time.hour, vm.time.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            title = { Text(t("Время")) },
            text = { TimePicker(state) },
            confirmButton = { TextButton(onClick = { vm.time = LocalTime.of(state.hour, state.minute); pickTime = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text(t("Отмена")) } },
        )
    }
    if (customDuration) {
        NumberDialog(t("Длительность, минут"), vm.duration, onDismiss = { customDuration = false }) { vm.duration = it.coerceIn(5, 24 * 60); customDuration = false }
    }
    customFor?.let { target ->
        CustomOffsetDialog(onDismiss = { customFor = null }) { minutes ->
            val list = if (target == ReminderTarget.CLIENT) vm.clientOffsets else vm.myOffsets
            if (minutes !in list) { list.add(minutes); list.sort() }
            customFor = null
        }
    }
    message?.let { kind ->
        SendMessageDialog(
            kind = kind,
            person = vm.person,
            initialChannel = vm.channel,
            initialLang = vm.langOf(),
            textFor = { lang -> vm.previewText(kind, lang) },
            onDismiss = { message = null; if (closeAfterMessage) onBack() },
            onSend = { ch, text ->
                vm.send(context, ch, text, kind) { r -> scope.launch { snackbar.showSnackbar(r.message) } }
                message = null
                if (closeAfterMessage) onBack()
            },
        )
    }
    newService?.let { s ->
        com.kartoteka.app.ui.services.ServiceEditor(
            service = s,
            onDismiss = { newService = null },
            onSave = { vm.createService(it); newService = null },
            onDelete = null,
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(t("Удалить запись?")) },
            text = { Text(t("Запланированные напоминания тоже будут отменены.")) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(onBack) }) { Text(t("Удалить")) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(t("Отмена")) } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OffsetChips(list: MutableList<Int>, onCustom: () -> Unit) {
    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (AppointmentLogic.presets + list.filter { it !in AppointmentLogic.presets }).distinct().sorted().forEach { off ->
            val sel = off in list
            FilterChip(sel, { if (sel) list.remove(off) else { list.add(off); list.sort() } }, label = { Text(AppointmentLogic.offsetTitle(off)) })
        }
        FilterChip(false, onCustom, label = { Text(t("Своё…")) }, leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(16.dp)) })
    }
}

/** Когда именно уйдут напоминания — чтобы не гадать. */
@Composable
private fun ReminderSchedule(start: Long, offsets: List<Int>, existing: List<AppointmentReminder>) {
    if (offsets.isEmpty()) return
    val now = System.currentTimeMillis()
    Column(Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) {
        offsets.sortedDescending().forEach { off ->
            val at = start - off * 60_000L
            val dt = AppointmentLogic.zoned(at)
            val sent = existing.any { it.offsetMin == off && it.sentAt != null }
            val text = when {
                sent -> t("✓ отправлено")
                at <= now -> t("время уже прошло — не будет отправлено")
                else -> t("%1\$s в %2\$s", AppointmentLogic.dateText(dt), AppointmentLogic.timeText(dt))
            }
            Text(
                "• ${AppointmentLogic.offsetTitle(off)}: ${text}",
                style = MaterialTheme.typography.bodySmall,
                color = if (at <= now && !sent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun PersonPickerDialog(all: List<PersonFull>, onDismiss: () -> Unit, onPick: (PersonFull) -> Unit) {
    var q by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Кого записать?")) },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text(t("Имя, телефон, город…")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(ArchiveLogic.sort(ArchiveLogic.search(all, q), SortMode.NAME), key = { it.person.person.id }) { hit ->
                        Row(Modifier.fillMaxWidth().clickable { onPick(hit.person) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(hit.person.person, 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(hit.person.person.displayName)
                                hit.matchedIn?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Отмена")) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomOffsetDialog(onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var value by remember { mutableStateOf("2") }
    val units = listOf(t("минут") to 1, t("часов") to 60, t("дней") to 24 * 60, t("недель") to 7 * 24 * 60)
    var unit by remember { mutableStateOf(units[1]) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("За сколько напомнить")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value, { v -> value = v.filter { it.isDigit() }.take(3) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    units.forEach { u -> FilterChip(unit == u, { unit = u }, label = { Text(u.first) }) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = (value.toIntOrNull() ?: 0) > 0, onClick = { onSave(value.toInt() * unit.second) }) { Text(t("Добавить")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Отмена")) } },
    )
}

@Composable
private fun NumberDialog(title: String, initial: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var value by remember { mutableStateOf(initial.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value, { v -> value = v.filter { it.isDigit() }.take(4) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) },
        confirmButton = { TextButton(enabled = (value.toIntOrNull() ?: 0) > 0, onClick = { onSave(value.toInt()) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Отмена")) } },
    )
}

/** Показываем сообщение перед отправкой: можно сменить способ и поправить текст. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SendMessageDialog(
    kind: MessageKind,
    person: PersonFull?,
    initialChannel: NotifyChannel,
    initialLang: com.kartoteka.app.data.MessageLang,
    textFor: (com.kartoteka.app.data.MessageLang) -> String,
    onDismiss: () -> Unit,
    onSend: (NotifyChannel, String) -> Unit,
) {
    var ch by remember { mutableStateOf(initialChannel) }
    var lang by remember { mutableStateOf(initialLang) }
    var text by remember { mutableStateOf(textFor(initialLang)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${kind.title}: ${person?.person?.displayName.orEmpty()}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NotifyChannel.entries.filter { it != NotifyChannel.NONE }.forEach { c ->
                        FilterChip(ch == c, { ch = c }, enabled = person?.let { Sender.targetFor(it, c) } != null, label = { Text(c.title) })
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    com.kartoteka.app.data.MessageLang.entries.forEach { l ->
                        FilterChip(lang == l, { lang = l; text = textFor(l) }, label = { Text(l.title) })
                    }
                }
                OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp))
            }
        },
        confirmButton = {
            Button(enabled = text.isNotBlank() && person?.let { Sender.targetFor(it, ch) } != null, onClick = { onSend(ch, text.trim()) }) {
                Icon(Icons.Default.Send, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Отправить"))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Не отправлять")) } },
    )
}
