package com.kartoteka.app.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kartoteka.app.KartotekaApp
import com.kartoteka.app.data.ArchiveLogic
import com.kartoteka.app.data.GroupWithCount
import com.kartoteka.app.data.PersonFull
import com.kartoteka.app.data.SortMode
import com.kartoteka.app.ui.app
import com.kartoteka.app.ui.components.Avatar
import com.kartoteka.app.ui.components.Badge
import com.kartoteka.app.ui.components.ColorDot
import com.kartoteka.app.ui.components.EmptyState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed interface PeopleFilter {
    data object All : PeopleFilter
    data object Favorites : PeopleFilter
    data class InGroup(val id: Long) : PeopleFilter
}

data class HomeState(
    val loading: Boolean = true,
    val total: Int = 0,
    val results: List<ArchiveLogic.SearchHit> = emptyList(),
    val birthdays: List<Pair<PersonFull, Long>> = emptyList(),
    val groups: List<GroupWithCount> = emptyList(),
)

class HomeViewModel(private val app: KartotekaApp) : ViewModel() {
    val query = MutableStateFlow("")
    val filter = MutableStateFlow<PeopleFilter>(PeopleFilter.All)
    val sort = app.settings.sortMode

    val state = combine(
        app.repository.observeAll(), app.repository.observeGroups(), query, filter, sort,
    ) { all, groups, q, f, s ->
        val filtered = when (f) {
            PeopleFilter.All -> all
            PeopleFilter.Favorites -> all.filter { it.person.favorite }
            is PeopleFilter.InGroup -> all.filter { pf -> pf.groups.any { it.id == f.id } }
        }
        HomeState(
            loading = false,
            total = all.size,
            results = ArchiveLogic.sort(ArchiveLogic.search(filtered, q), s),
            birthdays = ArchiveLogic.upcomingBirthdays(all, 30),
            groups = groups,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeState())

    fun setSort(mode: SortMode) = app.settings.setSortMode(mode)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(onOpen: (Long) -> Unit, onAdd: () -> Unit, onImport: () -> Unit, onGroups: () -> Unit = {}) {
    val app = app()
    val vm: HomeViewModel = viewModel { HomeViewModel(app) }
    val state by vm.state.collectAsState()
    val query by vm.query.collectAsState()
    val filter by vm.filter.collectAsState()
    val sort by vm.sort.collectAsState()
    val listState = rememberLazyListState()
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex < 2 } }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                expanded = fabExpanded,
                icon = { Icon(Icons.Default.PersonAdd, null) },
                text = { Text("Добавить") },
            )
        },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item(key = "header") {
                Header(total = state.total, sort = sort, onSort = vm::setSort, onGroups = onGroups)
            }
            item(key = "search") {
                SearchField(query, onChange = { vm.query.value = it })
            }
            item(key = "filters") {
                FilterRow(filter, state.groups, onGroups) { vm.filter.value = it }
            }
            if (query.isBlank() && filter == PeopleFilter.All && state.birthdays.isNotEmpty()) {
                item(key = "birthdays") { BirthdayStrip(state.birthdays, onOpen) }
            }

            if (!state.loading && state.total == 0) {
                item(key = "empty") {
                    EmptyState(
                        Icons.Default.Contacts,
                        "Пока никого нет",
                        "Добавьте первого человека вручную или импортируйте контакты из телефона.",
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onAdd) { Text("Добавить") }
                            FilledTonalButton(onClick = onImport) { Text("Импорт контактов") }
                        }
                    }
                }
            } else if (!state.loading && state.results.isEmpty()) {
                item(key = "nothing") {
                    EmptyState(Icons.Default.SearchOff, "Ничего не найдено", "Попробуйте изменить запрос или фильтр.")
                }
            }

            val grouped = sort == SortMode.NAME && query.isBlank()
            if (grouped) {
                state.results.groupBy { it.person.person.sortKey.firstOrNull()?.uppercaseChar() ?: '#' }
                    .forEach { (letter, hits) ->
                        stickyHeader(key = "h_$letter") { LetterHeader(letter) }
                        items(hits, key = { it.person.person.id }) { hit ->
                            PersonRow(hit, onClick = { onOpen(hit.person.person.id) }, Modifier.animateItem())
                        }
                    }
            } else {
                if (state.results.isNotEmpty()) {
                    item(key = "count") {
                        Text(
                            "Найдено: ${state.results.size}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                    }
                }
                items(state.results, key = { it.person.person.id }) { hit ->
                    PersonRow(hit, onClick = { onOpen(hit.person.person.id) }, Modifier.animateItem())
                }
            }
        }
    }
}

@Composable
private fun Header(total: Int, sort: SortMode, onSort: (SortMode) -> Unit, onGroups: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.statusBarsPadding().fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val custom by com.kartoteka.app.ui.app().settings.appTitle.value.collectAsState()
            Text(com.kartoteka.app.AppIcons.title(context, custom), style = MaterialTheme.typography.headlineLarge)
            Text(
                "$total ${ArchiveLogic.plural(total.toLong(), "человек", "человека", "человек")} в архиве",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onGroups) { Icon(Icons.Default.Workspaces, "Группы") }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Сортировка") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                SortMode.entries.forEach { m ->
                    DropdownMenuItem(
                        text = { Text(m.title) },
                        onClick = { onSort(m); menu = false },
                        trailingIcon = { if (m == sort) Icon(Icons.Default.Check, null) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onChange,
        placeholder = { Text("Поиск: имя, хобби, город…") },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = {
            if (query.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Default.Close, "Очистить") }
        },
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun FilterRow(filter: PeopleFilter, groups: List<GroupWithCount>, onGroups: () -> Unit, onChange: (PeopleFilter) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(selected = filter == PeopleFilter.All, onClick = { onChange(PeopleFilter.All) }, label = { Text("Все") })
        }
        item {
            FilterChip(
                selected = filter == PeopleFilter.Favorites,
                onClick = { onChange(PeopleFilter.Favorites) },
                label = { Text("Избранные") },
                leadingIcon = { Icon(Icons.Default.Star, null, Modifier.size(18.dp)) },
            )
        }
        items(groups, key = { it.group.id }) { g ->
            val selected = filter == PeopleFilter.InGroup(g.group.id)
            FilterChip(
                selected = selected,
                onClick = { onChange(if (selected) PeopleFilter.All else PeopleFilter.InGroup(g.group.id)) },
                label = { Text(listOf(g.group.emoji, g.group.name).filter { it.isNotBlank() }.joinToString(" ")) },
                leadingIcon = { ColorDot(g.group.color) },
            )
        }
        item {
            androidx.compose.material3.AssistChip(onClick = onGroups, label = { Text(if (groups.isEmpty()) "+ Группы" else "Группы…") })
        }
    }
}

@Composable
private fun BirthdayStrip(list: List<Pair<PersonFull, Long>>, onOpen: (Long) -> Unit) {
    Column(Modifier.padding(top = 4.dp, bottom = 8.dp)) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Cake, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Скоро дни рождения", style = MaterialTheme.typography.titleMedium)
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list, key = { it.first.person.id }) { (pf, days) ->
                val p = pf.person
                Surface(
                    onClick = { onOpen(p.id) },
                    shape = RoundedCornerShape(22.dp),
                    color = if (days == 0L) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.width(128.dp),
                ) {
                    Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Avatar(p, 56.dp)
                        Spacer(Modifier.height(8.dp))
                        Text(p.firstName.ifBlank { p.displayName }, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        Text(
                            ArchiveLogic.daysString(days),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (days == 0L) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.tertiary,
                        )
                        ArchiveLogic.turningAge(p)?.let {
                            Text(ArchiveLogic.ageString(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LetterHeader(letter: Char) {
    Text(
        letter.toString(),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 24.dp, vertical = 6.dp),
    )
}

@Composable
fun PersonRow(hit: ArchiveLogic.SearchHit, onClick: () -> Unit, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    val pf = hit.person
    val p = pf.person
    val days = ArchiveLogic.daysUntilBirthday(p)
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(p, 52.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    p.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (p.favorite) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.Star, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                }
                pf.groups.take(4).forEach {
                    Spacer(Modifier.width(4.dp))
                    ColorDot(it.color, 8.dp)
                }
            }
            val sub = listOf(p.relation, p.company.ifBlank { p.position }, p.city).filter { it.isNotBlank() }.joinToString(" · ")
            if (hit.matchedIn != null) {
                Text(hit.matchedIn, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else if (sub.isNotBlank()) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (days != null && days <= 7) {
            Spacer(Modifier.width(8.dp))
            Badge("🎂 " + ArchiveLogic.daysString(days))
        }
        trailing()
    }
}
