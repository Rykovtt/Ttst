package com.kartoteka.app.ui.person

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kartoteka.app.KartotekaApp
import com.kartoteka.app.data.VoiceNote
import com.kartoteka.app.i18n.t
import com.kartoteka.app.ui.app
import com.kartoteka.app.ui.components.OutlinedTextField
import com.kartoteka.app.ui.components.SectionCard
import com.kartoteka.app.voice.VoiceNotes
import com.kartoteka.app.voice.VoiceRecorder
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date

fun durationText(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

/** Голосовые заметки: запись, прослушивание и расшифровка на устройстве. */
@Composable
fun VoiceNotesSection(personId: Long) {
    val app = app()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val notes by remember(personId) { app.repository.observeVoiceNotes(personId) }.collectAsState(emptyList())
    var recording by remember { mutableStateOf(false) }
    var denied by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<VoiceNote?>(null) }
    var deleting by remember { mutableStateOf<VoiceNote?>(null) }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) recording = true else denied = true
    }
    DisposableEffect(Unit) { onDispose { VoiceNotes.stop(app) } }

    SectionCard(
        t("Голосовые заметки"), Icons.Default.GraphicEq,
        action = {
            IconButton(onClick = {
                if (VoiceRecorder.hasPermission(context)) recording = true else askMic.launch(Manifest.permission.RECORD_AUDIO)
            }) { Icon(Icons.Default.Mic, t("Записать")) }
        },
    ) {
        if (notes.isEmpty()) {
            Text(
                t("Наговорите после встречи — запись зашифрована и расшифровывается в текст прямо на телефоне"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
            )
        }
        notes.forEach { n ->
            VoiceRow(app, n, onEdit = { editing = n }, onDelete = { deleting = n })
        }
        if (denied) {
            Text(
                t("Нет доступа к микрофону — разрешите его в настройках телефона"),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
            )
        }
    }

    if (recording) {
        RecordDialog(app, onClose = { recording = false }) { file, duration ->
            recording = false
            scope.launch {
                val note = VoiceNote(personId = personId, file = file, durationMs = duration)
                val id = app.repository.addVoiceNote(note)
                app.repository.touchContact(personId)
                VoiceNotes.transcribe(app, note.copy(id = id))
            }
        }
    }
    editing?.let { n ->
        EditTextDialog(n.text, onDismiss = { editing = null }) { text ->
            editing = null
            scope.launch { app.repository.setVoiceText(n.id, text.trim()); VoiceNotes.status.remove(n.id) }
        }
    }
    deleting?.let { n ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(t("Удалить голосовую заметку?")) },
            text = { Text(t("Запись и расшифровка будут удалены без возможности восстановления.")) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    if (VoiceNotes.playingId == n.id) VoiceNotes.stop(app)
                    scope.launch { app.repository.deleteVoiceNote(n) }
                }) { Text(t("Удалить")) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(t("Отмена")) } },
        )
    }
}

@Composable
private fun VoiceRow(app: KartotekaApp, n: VoiceNote, onEdit: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val playing = VoiceNotes.playingId == n.id
    val status = VoiceNotes.status[n.id]
    val fmt = remember { SimpleDateFormat("d MMM yyyy, HH:mm", com.kartoteka.app.i18n.I18n.locale) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.Top) {
        FilledTonalIconButton(onClick = { VoiceNotes.toggle(app, n) }) {
            Icon(if (playing) Icons.Default.Stop else Icons.Default.PlayArrow, if (playing) t("Стоп") else t("Слушать"))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).padding(top = 4.dp)) {
            Text(
                "${fmt.format(Date(n.createdAt))} · ${durationText(n.durationMs)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            when {
                n.text.isNotBlank() -> Text(n.text, style = MaterialTheme.typography.bodyMedium)
                status != null -> Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> Text(t("Без расшифровки"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.MoreVert, null, Modifier.size(18.dp)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(t("Изменить текст")) }, onClick = { menu = false; onEdit() })
                DropdownMenuItem(text = { Text(t("Расшифровать заново")) }, onClick = { menu = false; VoiceNotes.transcribe(app, n) })
                DropdownMenuItem(text = { Text(t("Удалить")) }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

@Composable
private fun RecordDialog(app: KartotekaApp, onClose: () -> Unit, onSaved: (String, Long) -> Unit) {
    val scope = rememberCoroutineScope()
    val recorder = remember { VoiceRecorder(app.repository.voices, app.appScope) }
    var failed by remember { mutableStateOf(false) }
    var finishing by remember { mutableStateOf(false) }
    val elapsed by recorder.elapsedMs.collectAsState()
    val level by recorder.level.collectAsState()

    fun finish() {
        if (finishing) return
        finishing = true
        scope.launch {
            val r = recorder.stop()
            if (r == null || r.second < 500) { r?.let { app.repository.voices.delete(it.first) }; onClose() }
            else onSaved(r.first, r.second)
        }
    }

    LaunchedEffect(Unit) {
        VoiceNotes.stop(app)
        if (!recorder.start()) failed = true
    }
    LaunchedEffect(elapsed) { if (elapsed >= VoiceRecorder.MAX_MS) finish() }

    AlertDialog(
        onDismissRequest = {},
        icon = { Icon(Icons.Default.Mic, null) },
        title = { Text(if (failed) t("Микрофон недоступен") else t("Идёт запись")) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                if (failed) {
                    Text(t("Не удалось включить микрофон. Возможно, он занят другим приложением."))
                } else {
                    Text(durationText(elapsed), fontSize = 40.sp, fontWeight = FontWeight.SemiBold)
                    LinearProgressIndicator(progress = { level }, modifier = Modifier.fillMaxWidth())
                    Text(
                        t("Запись шифруется прямо во время разговора. Максимум — 15 минут."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            if (!failed) TextButton(enabled = !finishing, onClick = ::finish) { Text(t("Готово")) }
        },
        dismissButton = {
            TextButton(onClick = {
                scope.launch { recorder.cancel(); onClose() }
            }) { Text(if (failed) t("Закрыть") else t("Отмена")) }
        },
    )
}

@Composable
private fun EditTextDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Текст заметки")) },
        text = { OutlinedTextField(value, { value = it }, modifier = Modifier.fillMaxWidth(), label = { Text(t("Расшифровка")) }) },
        confirmButton = { TextButton(onClick = { onSave(value) }) { Text(t("Сохранить")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Отмена")) } },
    )
}
