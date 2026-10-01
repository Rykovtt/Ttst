package com.kartoteka.app.ui.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.kartoteka.app.data.Chat
import com.kartoteka.app.i18n.t
import com.kartoteka.app.ui.app
import com.kartoteka.app.ui.components.SectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date

/** Раздел карточки: импортированная переписка WhatsApp / Telegram. */
@Composable
fun ChatsSection(personId: Long, onOpenChat: (Long) -> Unit) {
    val app = app()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val chats by remember(personId) { app.repository.observeChats(personId) }.collectAsState(emptyList())
    var chooser by remember { mutableStateOf(false) }
    var shareHelp by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Chat?>(null) }
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val files = withContext(Dispatchers.IO) { uris.mapNotNull { ChatImports.read(context, it) } }
            ChatImports.pending = ChatImports.Pending(personId, files)
        }
    }

    SectionCard(
        t("Переписка"), Icons.Default.Forum,
        action = { IconButton(onClick = { chooser = true }) { Icon(Icons.Default.UploadFile, t("Импортировать переписку")) } },
    ) {
        if (chats.isEmpty()) {
            Text(
                t("Сохраните сюда переписку из WhatsApp или Telegram — она будет храниться в архиве, и по ней работает поиск"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
            )
        }
        chats.forEach { c -> ChatRow(c, onOpen = { onOpenChat(c.id) }, onDelete = { deleting = c }) }
    }

    if (chooser) {
        AlertDialog(
            onDismissRequest = { chooser = false },
            title = { Text(t("Импорт переписки")) },
            text = {
                Column {
                    ChoiceRow(Icons.Default.Share, t("Из WhatsApp через «Поделиться»"), t("Экспорт чата прямо в архив")) {
                        chooser = false
                        ChatImports.armShare(context, personId)
                        shareHelp = true
                    }
                    ChoiceRow(Icons.Default.FolderOpen, t("Выбрать файл"), t("WhatsApp .txt / .zip, Telegram result.json или messages.html")) {
                        chooser = false
                        pickFiles.launch(arrayOf("*/*"))
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { chooser = false }) { Text(t("Отмена")) } },
        )
    }
    if (shareHelp) {
        AlertDialog(
            onDismissRequest = { shareHelp = false },
            title = { Text(t("Как отправить чат из WhatsApp")) },
            text = {
                Text(t("1. Откройте чат в WhatsApp → ⋮ → Ещё → Экспорт чата.\n2. Выберите «Без медиафайлов».\n3. В списке приложений нажмите «RVault: импорт переписки».\n\nЭтот пункт появится в меню «Поделиться» только на 15 минут."))
            },
            confirmButton = { TextButton(onClick = { shareHelp = false }) { Text(t("Понятно")) } },
        )
    }
    deleting?.let { c ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(t("Удалить переписку?")) },
            text = { Text(t("Все сообщения этой переписки будут удалены из архива.")) },
            confirmButton = { TextButton(onClick = { deleting = null; scope.launch { app.repository.deleteChat(c.id) } }) { Text(t("Удалить")) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(t("Отмена")) } },
        )
    }
}

@Composable
private fun ChoiceRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChatRow(c: Chat, onOpen: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val year = remember { SimpleDateFormat("MMM yyyy", com.kartoteka.app.i18n.I18n.locale) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 18.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Filled.Chat, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(c.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${c.source} · ${t("%1\$s сообщ.", c.messageCount)} · ${year.format(Date(c.firstAt))} — ${year.format(Date(c.lastAt))}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.MoreVert, null, Modifier.size(18.dp)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(t("Удалить переписку")) }, onClick = { menu = false; onDelete() })
            }
        }
    }
}
