package com.kartoteka.app.ui.services

import com.kartoteka.app.i18n.t

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DesignServices
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kartoteka.app.data.AppointmentLogic
import com.kartoteka.app.data.ServiceTemplate
import com.kartoteka.app.data.TemplateKind
import com.kartoteka.app.ui.app
import com.kartoteka.app.ui.components.AssistChip
import com.kartoteka.app.ui.components.EmptyState
import com.kartoteka.app.ui.components.FilterChip
import com.kartoteka.app.ui.components.OutlinedTextField
import com.kartoteka.app.ui.components.SectionCard
import kotlinx.coroutines.launch

/** Список услуг: у каждой — свои длительность, напоминания и тексты сообщений. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServicesScreen(onBack: () -> Unit) {
    val app = app()
    val services by app.repository.observeServices().collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<ServiceTemplate?>(null) }

    Scaffold(
        topBar = {
            com.kartoteka.app.ui.components.ScreenHero(t("Услуги"), backgroundKey = "services", compact = true, subtitle = t("Тексты, длительность и напоминания по умолчанию"), onBack = onBack)
        },
        floatingActionButton = {
            Button(
                onClick = { editing = newService(app.settings) },
                shape = CircleShape,
                contentPadding = PaddingValues(vertical = 16.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(t("Добавить услугу"), style = MaterialTheme.typography.titleMedium)
            }
        },
        floatingActionButtonPosition = androidx.compose.material3.FabPosition.Center,
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 112.dp)) {
            item {
                Text(
                    t("Выберите услугу при записи — подтверждение, напоминания, перенос и отмена уйдут по её шаблонам. ") +
                        t("Пустой шаблон услуги — используется общий из настроек."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            val list = services
            if (list != null && list.isEmpty()) {
                item { EmptyState(Icons.Default.DesignServices, t("Пока нет услуг"), t("Например: «Тату-сеанс», «Консультация», «Коррекция».")) }
            }
            items(list.orEmpty(), key = { it.id }) { s ->
                Surface(
                    onClick = { editing = s },
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.name, style = MaterialTheme.typography.titleMedium)
                            val info = listOfNotNull(
                                durationText(s.durationMin),
                                s.place.takeIf { it.isNotBlank() },
                                if (s.ownTemplates == 0) t("общие шаблоны") else t("своих шаблонов: %1\$s из 4", s.ownTemplates),
                            ).joinToString(" · ")
                            Text(info, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }

    editing?.let { s ->
        ServiceEditor(
            service = s,
            onDismiss = { editing = null },
            onSave = { scope.launch { app.repository.saveService(it) }; editing = null },
            onDelete = if (s.id != 0L) ({ scope.launch { app.repository.deleteService(s.id) }; editing = null }) else null,
        )
    }
}

/** Новая услуга с напоминаниями по умолчанию из настроек. */
fun newService(settings: com.kartoteka.app.data.Settings, name: String = "", duration: Int? = null) = ServiceTemplate(
    name = name,
    durationMin = duration ?: settings.apptDuration.value.value.toIntOrNull() ?: 60,
    clientOffsets = settings.apptClientOffsets.value.value,
    myOffsets = settings.apptMyOffsets.value.value,
)

fun durationText(d: Int) = if (d < 60) t("%1\$s мин", d) else if (d % 60 == 0) t("%1\$s ч", d / 60) else t("%1\$s ч %2\$s мин", d / 60, d % 60)

private val durations = listOf(15, 30, 45, 60, 90, 120, 180, 240)

/** Полноэкранный редактор услуги. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ServiceEditor(
    service: ServiceTemplate,
    onDismiss: () -> Unit,
    onSave: (ServiceTemplate) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val app = app()
    val lang = app.settings.defaultLang
    var s by remember { mutableStateOf(service) }
    val texts = remember { mutableStateMapOf<TemplateKind, TextFieldValue>().apply { TemplateKind.entries.forEach { put(it, TextFieldValue(service.template(it))) } } }
    var focused by remember { mutableStateOf<TemplateKind?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun result() = TemplateKind.entries.fold(s.copy(name = s.name.trim())) { acc, k -> acc.withTemplate(k, texts[k]?.text.orEmpty().trim()) }

    // decorFitsSystemWindows = false — отступы клавиатуры и системной навигации приходят в Compose,
    // иначе на Android 15 кнопка «Сохранить» уходит под панель навигации.
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Scaffold(
            topBar = {
                com.kartoteka.app.ui.components.ScreenHero(
                    if (service.id == 0L) t("Новая услуга") else t("Услуга"), compact = true,
                    onBack = onDismiss, backIcon = Icons.Default.Close, backDescription = t("Закрыть"), backgroundButton = false,
                    actions = {
                        if (onDelete != null) com.kartoteka.app.ui.components.HeroButton(Icons.Default.Delete, t("Удалить"), { confirmDelete = true })
                    },
                )
            },
            bottomBar = {
                androidx.compose.foundation.layout.Box(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
                        // навигация или клавиатура — что выше (сумма давала лишний зазор над клавиатурой)
                        .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    com.kartoteka.app.ui.components.GradientButton(t("Сохранить"), icon = Icons.Default.Check, enabled = s.name.isNotBlank()) { onSave(result()) }
                }
            },
        ) { padding ->
            // Клавиатуру уже учитывает нижняя панель (отступ ime), а Scaffold передаёт её высоту в padding:
            // второй imePadding здесь сжимал поля до пустого экрана.
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 40.dp)) {
                item {
                    SectionCard(t("Услуга"), Icons.Default.DesignServices) {
                        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                s.name, { s = s.copy(name = it) }, label = { Text(t("Название")) }, placeholder = { Text(t("Например, Консультация")) },
                                singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(t("Длительность"), style = MaterialTheme.typography.labelLarge)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                durations.forEach { d -> FilterChip(s.durationMin == d, { s = s.copy(durationMin = d) }, label = { Text(durationText(d)) }) }
                            }
                            OutlinedTextField(
                                s.durationMin.toString(), { v -> v.filter(Char::isDigit).take(4).toIntOrNull()?.let { s = s.copy(durationMin = it) } },
                                label = { Text(t("Своя длительность, минут")) }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                s.place, { s = s.copy(place = it) }, label = { Text(t("Место по умолчанию")) }, singleLine = true,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                item {
                    SectionCard(t("Напоминания"), Icons.Default.NotificationsActive) {
                        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(t("Человеку"), style = MaterialTheme.typography.labelLarge)
                            OffsetChips(s.clientOffsets) { s = s.copy(clientOffsets = it) }
                            Text(t("Мне"), style = MaterialTheme.typography.labelLarge)
                            OffsetChips(s.myOffsets) { s = s.copy(myOffsets = it) }
                        }
                    }
                }
                item {
                    SectionCard(t("Тексты сообщений"), Icons.AutoMirrored.Filled.Chat) {
                        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                t("Пусто — уйдёт общий шаблон (%1\$s). Нажмите «Скопировать общий», чтобы взять его за основу и поправить.", lang.title),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TemplateKind.entries.forEach { kind ->
                                val value = texts[kind] ?: TextFieldValue()
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(kind.title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                                        if (value.text.isBlank()) {
                                            TextButton(onClick = {
                                                val general = app.settings.template(kind, lang).value.value
                                                texts[kind] = TextFieldValue(general, TextRange(general.length))
                                            }) { Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(t("Скопировать общий")) }
                                        } else {
                                            TextButton(onClick = { texts[kind] = TextFieldValue() }) { Text(t("Очистить")) }
                                        }
                                    }
                                    OutlinedTextField(
                                        value, { texts[kind] = it },
                                        placeholder = { Text(t("Общий шаблон")) },
                                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp).onFocusChanged { if (it.isFocused) focused = kind },
                                    )
                                }
                            }
                            Text(t("Подставить в выбранное поле:"), style = MaterialTheme.typography.labelMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                lang.allTokens.forEach { ph ->
                                    AssistChip(onClick = {
                                        val k = focused ?: return@AssistChip
                                        val t = texts[k] ?: TextFieldValue()
                                        texts[k] = TextFieldValue(
                                            t.text.substring(0, t.selection.start) + ph + t.text.substring(t.selection.end),
                                            TextRange(t.selection.start + ph.length),
                                        )
                                    }, label = { Text(ph) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(t("Удалить услугу «%1\$s»?", service.name)) },
            text = { Text(t("Записи останутся, но сообщения по ним будут идти по общим шаблонам.")) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(t("Удалить")) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(t("Отмена")) } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OffsetChips(value: String, onChange: (String) -> Unit) {
    val selected = AppointmentLogic.offsetsFromString(value)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (AppointmentLogic.presets + selected).distinct().sorted().forEach { off ->
            val sel = off in selected
            FilterChip(sel, { onChange(AppointmentLogic.offsetsToString(if (sel) selected - off else selected + off)) }, label = { Text(AppointmentLogic.offsetTitle(off)) })
        }
    }
}
