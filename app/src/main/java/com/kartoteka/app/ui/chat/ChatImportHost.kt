package com.kartoteka.app.ui.chat

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kartoteka.app.data.ChatParser
import com.kartoteka.app.data.ParsedChat
import com.kartoteka.app.i18n.t
import com.kartoteka.app.ui.app
import com.kartoteka.app.ui.components.Avatar
import com.kartoteka.app.ui.components.OutlinedTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date

/** Шаги импорта переписки: разбор файла → (выбор человека) → «кто из участников вы» → сохранение. */
@Composable
fun ChatImportHost(onOpenPerson: (Long) -> Unit) {
    val pending = ChatImports.pending ?: return
    val app = app()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var parsed by remember(pending) { mutableStateOf<ParsedChat?>(null) }
    var failed by remember(pending) { mutableStateOf(false) }
    var personId by remember(pending) { mutableStateOf(pending.personId) }
    var saving by remember(pending) { mutableStateOf(false) }

    fun close() {
        ChatImports.pending = null
        if (pending.fromShare) ChatImports.disarmShare(context)
    }

    var personName by remember(pending) { mutableStateOf("") }
    LaunchedEffect(personId) {
        personId?.let { id -> app.repository.getPerson(id)?.person?.let { personName = it.firstName.ifBlank { it.displayName } } }
    }
    LaunchedEffect(pending) {
        val r = withContext(Dispatchers.Default) { runCatching { ChatParser.parse(pending.files) }.getOrNull() }
        if (r == null) failed = true else parsed = r
    }

    when {
        failed -> AlertDialog(
            onDismissRequest = ::close,
            title = { Text(t("Не удалось прочитать переписку")) },
            text = { Text(t("Поддерживаются выгрузки WhatsApp («Экспорт чата» → .txt или .zip) и Telegram Desktop (result.json или messages.html).")) },
            confirmButton = { TextButton(onClick = ::close) { Text("OK") } },
        )
        parsed == null -> AlertDialog(
            onDismissRequest = {},
            title = { Text(t("Читаю переписку…")) },
            text = { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() } },
            confirmButton = {},
        )
        personId == null -> PickPersonDialog(onDismiss = ::close) { personId = it }
        else -> MeDialog(parsed!!, personName, saving, onDismiss = ::close) { me ->
            saving = true
            val pid = personId!!
            scope.launch {
                app.repository.importChat(pid, parsed!!, me)
                Toast.makeText(context, t("Переписка сохранена: %1\$s %2\$s", parsed!!.messages.size, com.kartoteka.app.data.ArchiveLogic.plural(parsed!!.messages.size.toLong(), "сообщение", "сообщения", "сообщений")), Toast.LENGTH_SHORT).show()
                val openCard = pending.fromShare
                close()
                if (openCard) onOpenPerson(pid)
            }
        }
    }
}

@Composable
private fun PickPersonDialog(onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val app = app()
    val everyone by remember { app.repository.observeAll() }.collectAsState(emptyList())
    var q by remember { mutableStateOf("") }
    val list = everyone.filter { q.isBlank() || it.person.displayName.contains(q.trim(), ignoreCase = true) }
        .sortedBy { it.person.displayName }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("К кому прикрепить переписку?")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(q, { q = it }, label = { Text(t("Поиск")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(list, key = { it.person.id }) { pf ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(pf.person.id) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(pf.person, 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(pf.person.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Отмена")) } },
    )
}

@Composable
private fun MeDialog(chat: ParsedChat, personName: String, saving: Boolean, onDismiss: () -> Unit, onImport: (String) -> Unit) {
    val authors = remember(chat) { chat.authors }
    // Догадка: «вы» — участник, чьё имя не похоже на имя человека из карточки.
    var me by remember(chat, personName) {
        mutableStateOf(
            if (authors.size == 2 && personName.isNotBlank())
                authors.firstOrNull { !it.first.contains(personName, ignoreCase = true) }?.first.orEmpty()
            else ""
        )
    }
    val fmt = remember { SimpleDateFormat("d MMM yyyy", com.kartoteka.app.i18n.I18n.locale) }
    val first = chat.messages.minOf { it.time }
    val last = chat.messages.maxOf { it.time }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(t("Импорт переписки")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${chat.source} · ${chat.title}", style = MaterialTheme.typography.titleSmall)
                Text(
                    t("%1\$s %2\$s, %3\$s — %4\$s", chat.messages.size, com.kartoteka.app.data.ArchiveLogic.plural(chat.messages.size.toLong(), "сообщение", "сообщения", "сообщений"), fmt.format(Date(first)), fmt.format(Date(last))),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.padding(2.dp))
                Text(t("Кто из участников — вы?"), style = MaterialTheme.typography.titleSmall)
                authors.forEach { (name, count) ->
                    Row(Modifier.fillMaxWidth().clickable { me = name }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = me == name, onClick = { me = name })
                        Text("$name · $count", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Row(Modifier.fillMaxWidth().clickable { me = "" }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = me.isEmpty(), onClick = { me = "" })
                    Text(t("Не указывать"))
                }
                Text(
                    t("Переписка хранится только в зашифрованной базе архива."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(enabled = !saving, onClick = { onImport(me) }) { Text(t("Импортировать")) } },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text(t("Отмена")) } },
    )
}
