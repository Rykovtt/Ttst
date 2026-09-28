package com.kartoteka.app.ui.person

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import com.kartoteka.app.ui.components.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.kartoteka.app.ui.components.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kartoteka.app.data.ArchiveLogic
import com.kartoteka.app.data.Person
import com.kartoteka.app.data.PersonFull
import com.kartoteka.app.data.RelationType
import com.kartoteka.app.data.RelationView
import com.kartoteka.app.data.SortMode
import com.kartoteka.app.ui.components.Avatar
import com.kartoteka.app.ui.components.SectionCard

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RelationsSection(
    views: List<RelationView>,
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
    onRemove: (RelationView) -> Unit,
) {
    SectionCard(
        "Семья и связи", Icons.Default.FamilyRestroom,
        action = { IconButton(onClick = onAdd) { Icon(Icons.Default.Add, "Добавить связь") } },
    ) {
        if (views.isEmpty()) {
            Text(
                "Супруги, дети, родители, коллеги — связывайте карточки между собой",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
            )
        }
        views.forEach { v ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(v.other.id) }.padding(horizontal = 18.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(v.other, 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(v.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(v.other.displayName, style = MaterialTheme.typography.bodyLarge)
                }
                IconButton(onClick = { onRemove(v) }) { Icon(Icons.Default.Close, "Убрать связь", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

/** Выбор человека и типа связи: «Мария — Мать для Ивана». */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddRelationDialog(
    me: Person,
    candidates: List<PersonFull>,
    onDismiss: () -> Unit,
    onSave: (otherId: Long, type: RelationType) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var other by remember { mutableStateOf<Person?>(null) }
    var type by remember { mutableStateOf<RelationType?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (other == null) "С кем связан(а) ${me.firstName.ifBlank { me.displayName }}?" else "${other!!.displayName} — это…") },
        text = {
            val o = other
            if (o == null) {
                Column {
                    OutlinedTextField(query, { query = it }, leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("Поиск") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    val shown = ArchiveLogic.sort(ArchiveLogic.search(candidates, query), SortMode.NAME)
                    LazyColumn(Modifier.heightIn(max = 380.dp)) {
                        items(shown, key = { it.person.person.id }) { hit ->
                            Row(
                                Modifier.fillMaxWidth().clickable { other = hit.person.person }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Avatar(hit.person.person, 36.dp)
                                Spacer(Modifier.width(12.dp))
                                Text(hit.person.person.displayName)
                            }
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Семья", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RelationType.entries.filter { it.family }.forEach { t ->
                            FilterChip(type == t, { type = t }, label = { Text(t.label(o.gender)) })
                        }
                    }
                    Text("Другое", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RelationType.entries.filter { !it.family }.forEach { t ->
                            FilterChip(type == t, { type = t }, label = { Text(t.label(o.gender)) })
                        }
                    }
                    type?.let { t ->
                        Text(
                            "${o.displayName} — ${t.label(o.gender).lowercase()} для ${me.displayName}, " +
                                "а ${me.displayName} — ${t.inverse.label(me.gender).lowercase()} для ${o.displayName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = other != null && type != null, onClick = { onSave(other!!.id, type!!) }) { Text("Связать") }
        },
        dismissButton = {
            TextButton(onClick = { if (other != null) { other = null; type = null } else onDismiss() }) { Text(if (other != null) "Назад" else "Отмена") }
        },
    )
}
