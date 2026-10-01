package com.kartoteka.app.ui.groups

import com.kartoteka.app.i18n.t

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.kartoteka.app.ui.components.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kartoteka.app.data.ArchiveLogic
import com.kartoteka.app.data.Group
import com.kartoteka.app.ui.app
import com.kartoteka.app.ui.components.EmptyState
import com.kartoteka.app.ui.theme.AccentPalette
import kotlinx.coroutines.launch

@Composable
fun GroupsScreen(onOpen: (Long) -> Unit, onBack: () -> Unit) {
    val app = app()
    val groups by app.repository.observeGroups().collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Group?>(null) }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = Group(name = "", color = AccentPalette[(groups?.size ?: 0) % AccentPalette.size]) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(t("Группа")) },
            )
        },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                Column(Modifier.statusBarsPadding().padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 12.dp)) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Назад")) }
                    Text(t("Группы"), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(start = 12.dp))
                    Text(
                        t("Семья, работа, друзья — для быстрого поиска и рассылок"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
            val list = groups
            if (list != null && list.isEmpty()) {
                item {
                    EmptyState(Icons.Default.Workspaces, t("Пока нет групп"), t("Создайте группы вроде «Семья», «Работа», «Спортзал», чтобы делать рассылки в один тап."))
                }
            }
            items(list.orEmpty(), key = { it.group.id }) { gc ->
                val g = gc.group
                Surface(
                    onClick = { onOpen(g.id) },
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        GroupBadge(g, 48)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(g.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${gc.count} ${ArchiveLogic.plural(gc.count.toLong(), "человек", "человека", "человек")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    editing?.let { g ->
        GroupEditDialog(g, onDismiss = { editing = null }, onSave = {
            scope.launch { app.repository.saveGroup(it) }
            editing = null
        })
    }
}

@Composable
fun GroupBadge(g: Group, sizeDp: Int) {
    Box(
        Modifier.size(sizeDp.dp).clip(RoundedCornerShape((sizeDp / 3).dp)).background(Color(g.color)),
        contentAlignment = Alignment.Center,
    ) {
        Text(g.emoji.ifBlank { g.name.take(1).uppercase() }, color = Color.White, fontSize = (sizeDp * 0.42).sp)
    }
}

private val emojis = listOf("", "👨‍👩‍👧", "💼", "🎉", "⚽", "🎓", "🏠", "❤️", "⭐", "🤝", "✈️", "🎮", "🏋️", "🎵", "⛪", "💰")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GroupEditDialog(group: Group, onDismiss: () -> Unit, onSave: (Group) -> Unit) {
    var name by remember { mutableStateOf(group.name) }
    var color by remember { mutableLongStateOf(group.color) }
    var emoji by remember { mutableStateOf(group.emoji) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (group.id == 0L) t("Новая группа") else t("Группа")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(t("Название")) }, singleLine = true)
                Text(t("Цвет"), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccentPalette.forEach { c ->
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(Color(c)).clickable { color = c }
                                .then(if (c == color) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) { if (c == color) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                    }
                }
                Text(t("Значок"), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    emojis.forEach { e ->
                        Box(
                            Modifier.size(38.dp).clip(CircleShape)
                                .background(if (e == emoji) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable { emoji = e },
                            contentAlignment = Alignment.Center,
                        ) { Text(e.ifBlank { "Aa" }, fontSize = if (e.isBlank()) 12.sp else 18.sp) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(group.copy(name = name.trim(), color = color, emoji = emoji)) }) { Text(t("Сохранить")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Отмена")) } },
    )
}
