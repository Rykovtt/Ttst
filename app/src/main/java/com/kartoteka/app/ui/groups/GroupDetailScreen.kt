package com.kartoteka.app.ui.groups

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.kartoteka.app.ui.components.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.dp
import com.kartoteka.app.data.ArchiveLogic
import com.kartoteka.app.data.PersonFull
import com.kartoteka.app.ui.app
import com.kartoteka.app.ui.components.EmptyState
import com.kartoteka.app.ui.home.PersonRow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(groupId: Long, onBack: () -> Unit, onOpenPerson: (Long) -> Unit, onBroadcast: () -> Unit) {
    val app = app()
    val repo = app.repository
    val scope = rememberCoroutineScope()
    val group by remember { repo.observeGroups().map { l -> l.firstOrNull { it.group.id == groupId }?.group } }.collectAsState(initial = null)
    val all by repo.observeAll().collectAsState(initial = emptyList())
    val members = all.filter { pf -> pf.groups.any { it.id == groupId } }.sortedBy { it.person.sortKey }
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }

    val g = group ?: return
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GroupBadge(g, 34); Spacer(Modifier.width(12.dp)); Text(g.name)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
                actions = {
                    IconButton(onClick = { editing = true }) { Icon(Icons.Default.Edit, "Изменить") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Удалить") }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onBroadcast, enabled = members.isNotEmpty(), modifier = Modifier.weight(1f)) {
                        Icon(Icons.AutoMirrored.Filled.Send, null); Spacer(Modifier.width(8.dp)); Text("Рассылка")
                    }
                    FilledTonalButton(onClick = { adding = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.GroupAdd, null); Spacer(Modifier.width(8.dp)); Text("Добавить")
                    }
                }
            }
            if (members.isEmpty()) {
                item { EmptyState(Icons.Default.GroupAdd, "В группе пока никого", "Добавьте людей кнопкой выше или в карточке человека.") }
            }
            items(members, key = { it.person.id }) { pf ->
                PersonRow(ArchiveLogic.SearchHit(pf, null), onClick = { onOpenPerson(pf.person.id) }) {
                    IconButton(onClick = {
                        scope.launch {
                            val ids = pf.groups.map { it.id }.filter { it != groupId }
                            repo.savePerson(pf.person, pf.contacts, pf.details, ids)
                        }
                    }) { Icon(Icons.Default.RemoveCircleOutline, "Убрать из группы", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }

    if (editing) {
        GroupEditDialog(g, onDismiss = { editing = false }, onSave = { scope.launch { repo.saveGroup(it) }; editing = false })
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить группу «${g.name}»?") },
            text = { Text("Люди останутся в картотеке, удалится только группа.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; scope.launch { repo.deleteGroup(groupId); onBack() } }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
    if (adding) {
        PeoplePickerDialog(
            all = all.filter { pf -> pf.groups.none { it.id == groupId } },
            onDismiss = { adding = false },
            onPick = { ids -> scope.launch { repo.addPersonsToGroup(groupId, ids) }; adding = false },
        )
    }
}

@Composable
fun PeoplePickerDialog(all: List<PersonFull>, onDismiss: () -> Unit, onPick: (Set<Long>) -> Unit, initial: Set<Long> = emptySet()) {
    var query by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<Long>().apply { addAll(initial) } }
    val shown = ArchiveLogic.sort(ArchiveLogic.search(all, query), com.kartoteka.app.data.SortMode.NAME)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Выберите людей") },
        text = {
            Column {
                OutlinedTextField(query, { query = it }, leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("Поиск") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(shown, key = { it.person.person.id }) { hit ->
                        val id = hit.person.person.id
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = id in selected, onCheckedChange = { if (it) selected.add(id) else selected.remove(id) })
                            Column(Modifier.weight(1f)) {
                                Text(hit.person.person.displayName)
                                hit.matchedIn?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = selected.isNotEmpty(), onClick = { onPick(selected.toSet()) }) { Text("Готово (${selected.size})") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
