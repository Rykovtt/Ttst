package com.kartoteka.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.kartoteka.app.data.AppointmentLogic
import com.kartoteka.app.data.MessageLang
import com.kartoteka.app.data.NotifyChannel
import com.kartoteka.app.data.TemplateKind
import com.kartoteka.app.data.Settings

/** Настройки записей: способ оповещения, напоминания по умолчанию, шаблоны сообщений. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalendarSettings(settings: Settings) {
    val channel by settings.apptChannel.value.collectAsState()
    val duration by settings.apptDuration.value.collectAsState()
    val client by settings.apptClientOffsets.value.collectAsState()
    val mine by settings.apptMyOffsets.value.collectAsState()
    val confirm by settings.apptSendConfirm.value.collectAsState()
    var editing by remember { mutableStateOf<Pair<String, Settings.StringPref>?>(null) }
    val defaultLang by settings.messageLang.value.collectAsState()
    var tplLang by remember(defaultLang) { mutableStateOf(MessageLang.of(defaultLang) ?: MessageLang.RU) }

    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Label("Как оповещать по умолчанию")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NotifyChannel.entries.forEach { c -> FilterChip(channel == c.name, { settings.apptChannel.set(c.name) }, label = { Text(c.title) }) }
        }
        Row(Modifier.fillMaxWidth().clickable { settings.apptSendConfirm.set((confirm != "true").toString()) }, verticalAlignment = Alignment.CenterVertically) {
            Text("Сразу отправлять подтверждение записи", modifier = Modifier.weight(1f))
            Switch(confirm == "true", { settings.apptSendConfirm.set(it.toString()) })
        }
        Label("Длительность по умолчанию")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(15, 30, 45, 60, 90, 120).forEach { d ->
                FilterChip(duration == "$d", { settings.apptDuration.set("$d") }, label = { Text(if (d < 60) "$d мин" else if (d % 60 == 0) "${d / 60} ч" else "1 ч 30") })
            }
        }
        Label("Напоминания человеку")
        OffsetPrefChips(client) { settings.apptClientOffsets.set(it) }
        Label("Напоминания мне")
        OffsetPrefChips(mine) { settings.apptMyOffsets.set(it) }
        Label("Шаблоны сообщений — ${tplLang.title}")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MessageLang.entries.forEach { l -> FilterChip(tplLang == l, { tplLang = l }, label = { Text(l.title) }) }
        }
        TemplateKind.entries.map { it.title to settings.template(it, tplLang) }.forEach { (title, pref) ->
            val v by pref.value.collectAsState()
            Row(Modifier.fillMaxWidth().clickable { editing = title to pref }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge)
                    Text(v.replace("\n", " "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
            }
        }
    }

    editing?.let { (title, pref) -> TemplateDialog(title, pref, tplLang, onDismiss = { editing = null }) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OffsetPrefChips(value: String, onChange: (String) -> Unit) {
    val selected = AppointmentLogic.offsetsFromString(value)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        AppointmentLogic.presets.forEach { off ->
            val sel = off in selected
            FilterChip(sel, { onChange(AppointmentLogic.offsetsToString(if (sel) selected - off else selected + off)) }, label = { Text(AppointmentLogic.offsetTitle(off)) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TemplateDialog(title: String, pref: Settings.StringPref, lang: MessageLang, onDismiss: () -> Unit) {
    val placeholders = lang.allTokens.filter { it != lang.allTokens[3] }
    var text by remember { mutableStateOf(TextFieldValue(pref.value.value)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$title · ${lang.title}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp))
                Text("Подставить:", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    placeholders.forEach { ph ->
                        AssistChip(onClick = {
                            val t = text
                            text = TextFieldValue(t.text.substring(0, t.selection.start) + ph + t.text.substring(t.selection.end), TextRange(t.selection.start + ph.length))
                        }, label = { Text(ph) })
                    }
                }
                Text("Пустые строки (например, без места) убираются автоматически.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { pref.set(text.text); onDismiss() }) { Text("Сохранить") } },
        dismissButton = {
            Row {
                TextButton(onClick = { text = TextFieldValue(pref.default) }) { Text("По умолчанию") }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp))
}
