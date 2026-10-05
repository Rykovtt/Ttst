package com.kartoteka.app.ui.groups

import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.itemsIndexed
import com.kartoteka.app.ui.animations.pressScale
import com.kartoteka.app.ui.components.HeroButton
import com.kartoteka.app.ui.components.ScreenHero
import com.kartoteka.app.ui.components.SectionLabel
import com.kartoteka.app.ui.theme.PeopleDims
import com.kartoteka.app.ui.theme.PeopleType
import com.kartoteka.app.ui.theme.RvColors
import com.kartoteka.app.ui.theme.u
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

    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentPadding = PaddingValues(bottom = 40.dp + androidx.compose.foundation.layout.WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())) {
        item {
            ScreenHero(backgroundKey = "groups", title = 
                t("Группы"),
                count = groups?.size,
                subtitle = t("Семья, работа, друзья — для быстрого поиска и рассылок"),
                onBack = onBack,
                actions = {
                    HeroButton(Icons.Default.Add, t("Новая группа"), {
                        editing = Group(name = "", color = AccentPalette[(groups?.size ?: 0) % AccentPalette.size])
                    }, active = true)
                },
            )
        }
        val list = groups
        if (list != null && list.isEmpty()) {
            item {
                EmptyState(Icons.Default.Workspaces, t("Пока нет групп"), t("Создайте группы вроде «Семья», «Работа», «Спортзал», чтобы делать рассылки в один тап."))
            }
        }
        if (!list.isNullOrEmpty()) item { SectionLabel(t("Все группы")) }
        itemsIndexed(list.orEmpty(), key = { _, it -> it.group.id }) { i, gc ->
            GroupRow(gc, last = i == list.orEmpty().lastIndex) { onOpen(gc.group.id) }
        }
    }

    editing?.let { g ->
        GroupEditDialog(g, onDismiss = { editing = null }, onSave = {
            scope.launch { app.repository.saveGroup(it) }
            editing = null
        })
    }
}

/** Строка группы — в ритме карточки человека: круглый значок, имя 15.5 Bold, подпись, шеврон, разделитель. */
@Composable
private fun GroupRow(gc: com.kartoteka.app.data.GroupWithCount, last: Boolean, onClick: () -> Unit) {
    val g = gc.group
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Column(Modifier.fillMaxWidth().pressScale(src, 0.985f).clickable(src, indication = null, onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = u(PeopleDims.CardPad), vertical = u(22)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GroupBadge(g, u(PeopleDims.ContactAvatar).value.toInt())
            Spacer(Modifier.width(u(22)))
            Column(Modifier.weight(1f)) {
                Text(g.name, style = PeopleType.contactName, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                Text(
                    "${gc.count} ${ArchiveLogic.plural(gc.count.toLong(), "человек", "человека", "человек")}",
                    style = PeopleType.contactMeta, color = RvColors.TextSecondary, modifier = Modifier.padding(top = u(4)),
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = RvColors.Chevron, modifier = Modifier.size(u(PeopleDims.Chevron) + 8.dp))
        }
        if (!last) androidx.compose.material3.HorizontalDivider(Modifier.padding(horizontal = u(PeopleDims.CardPad)), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
fun GroupBadge(g: Group, sizeDp: Int) {
    Box(
        Modifier.size(sizeDp.dp).clip(CircleShape).background(Color(g.color)),
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
