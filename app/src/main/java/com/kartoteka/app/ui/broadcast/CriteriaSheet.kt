package com.kartoteka.app.ui.broadcast

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import com.kartoteka.app.ui.components.FilterChip
import androidx.compose.material3.MaterialTheme
import com.kartoteka.app.ui.components.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kartoteka.app.data.DetailTemplates
import com.kartoteka.app.data.GroupWithCount
import com.kartoteka.app.data.PersonFull
import com.kartoteka.app.data.RecipientFilter
import com.kartoteka.app.ui.components.ClosenessStars
import com.kartoteka.app.ui.components.ColorDot

/** Выбор получателей по условиям: все условия работают через «И». */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CriteriaSheet(
    all: List<PersonFull>,
    groups: List<GroupWithCount>,
    onApply: (ids: List<Long>, replace: Boolean) -> Unit,
) {
    var f by remember { mutableStateOf(RecipientFilter()) }
    val matched = f.apply(all)
    val relations = (DetailTemplates.relations + all.map { it.person.relation }).filter { it.isNotBlank() }.distinct()
        .filter { r -> all.any { it.person.relation == r } }
    val cities = all.map { it.person.city.trim() }.filter { it.isNotBlank() }.groupingBy { it.lowercase() }.eachCount()
        .keys.mapNotNull { key -> all.firstOrNull { it.person.city.trim().lowercase() == key }?.person?.city?.trim() }

    fun <T> Set<T>.toggle(v: T) = if (v in this) this - v else this + v

    Column(Modifier.navigationBarsPadding()) {
        Text("Выбор по критериям", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
        Text(
            "Люди должны подходить под все отмеченные условия",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        LazyColumn(Modifier.weight(1f, fill = false), contentPadding = PaddingValues(bottom = 8.dp)) {
            if (groups.isNotEmpty()) item {
                Block("Группы (любая из)") {
                    groups.forEach { g ->
                        FilterChip(g.group.id in f.groupIds, { f = f.copy(groupIds = f.groupIds.toggle(g.group.id)) },
                            label = { Text("${g.group.emoji} ${g.group.name}".trim()) }, leadingIcon = { ColorDot(g.group.color) })
                    }
                }
            }
            item {
                Block("Пол") {
                    DetailTemplates.genders.forEach { g -> FilterChip(g in f.genders, { f = f.copy(genders = f.genders.toggle(g)) }, label = { Text(g) }) }
                }
            }
            if (relations.isNotEmpty()) item {
                Block("Кем приходится") {
                    relations.forEach { r -> FilterChip(r in f.relations, { f = f.copy(relations = f.relations.toggle(r)) }, label = { Text(r) }) }
                }
            }
            if (cities.isNotEmpty()) item {
                Block("Город") {
                    cities.forEach { c -> FilterChip(c in f.cities, { f = f.copy(cities = f.cities.toggle(c)) }, label = { Text(c) }) }
                }
            }
            item {
                Block("Возраст") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(f.ageFrom?.toString().orEmpty(), { v -> f = f.copy(ageFrom = v.filter(Char::isDigit).take(3).toIntOrNull()) },
                            label = { Text("от") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(100.dp))
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(f.ageTo?.toString().orEmpty(), { v -> f = f.copy(ageTo = v.filter(Char::isDigit).take(3).toIntOrNull()) },
                            label = { Text("до") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(100.dp))
                    }
                }
            }
            item {
                Block("Близость — не меньше") {
                    ClosenessStars(f.minCloseness, onChange = { f = f.copy(minCloseness = it) }, size = 26.dp)
                }
            }
            item {
                Block("Ещё") {
                    FilterChip(f.favoritesOnly, { f = f.copy(favoritesOnly = !f.favoritesOnly) }, label = { Text("Только избранные") })
                    listOf(7, 30).forEach { d ->
                        FilterChip(f.birthdayWithinDays == d, { f = f.copy(birthdayWithinDays = if (f.birthdayWithinDays == d) null else d) }, label = { Text("ДР в ближайшие $d дн.") })
                    }
                    listOf(30, 90, 180).forEach { d ->
                        FilterChip(f.noContactDays == d, { f = f.copy(noContactDays = if (f.noContactDays == d) null else d) }, label = { Text("Не общались $d+ дн.") })
                    }
                }
            }
        }
        Surface(tonalElevation = 3.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Подходит: ${matched.size}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (!f.isEmpty) TextButton(onClick = { f = RecipientFilter() }) { Text("Сбросить") }
                }
                Text(
                    matched.take(6).joinToString(", ") { it.person.displayName } + if (matched.size > 6) " и ещё ${matched.size - 6}" else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                )
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(enabled = matched.isNotEmpty(), onClick = { onApply(matched.map { it.person.id }, false) }, modifier = Modifier.weight(1f)) { Text("Добавить") }
                    Button(enabled = matched.isNotEmpty(), onClick = { onApply(matched.map { it.person.id }, true) }, modifier = Modifier.weight(1f)) { Text("Выбрать только их") }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Block(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}
