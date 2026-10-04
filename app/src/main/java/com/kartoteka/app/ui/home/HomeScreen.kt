package com.kartoteka.app.ui.home

import com.kartoteka.app.i18n.t
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.sp
import com.kartoteka.app.ui.components.CircleAction
import com.kartoteka.app.ui.components.CountPill
import com.kartoteka.app.ui.components.categoryColor
import com.kartoteka.app.ui.components.heroBackground
import com.kartoteka.app.ui.components.pressable
import com.kartoteka.app.ui.theme.Motion
import com.kartoteka.app.ui.theme.Rv
import com.kartoteka.app.ui.theme.motion
import com.kartoteka.app.ui.theme.rememberHaptics
import kotlinx.coroutines.launch

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import com.kartoteka.app.ui.components.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FilledTonalIconButton
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
fun HomeScreen(
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
    onImport: () -> Unit,
    onGroups: () -> Unit = {},
    onNoa: () -> Unit = {},
    onMap: () -> Unit = {},
    onStats: () -> Unit = {},
) {
    val app = app()
    val vm: HomeViewModel = viewModel { HomeViewModel(app) }
    val state by vm.state.collectAsState()
    val query by vm.query.collectAsState()
    val filter by vm.filter.collectAsState()
    val sort by vm.sort.collectAsState()
    val listState = rememberLazyListState()
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var birthdaysOpen by rememberSaveable { mutableStateOf(false) }
    // Открытая «шторка» свайпа — только одна за раз.
    var swiped by remember { mutableStateOf<Long?>(null) }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 40.dp),
    ) {
        item(key = "hero") {
            PeopleHero(
                total = state.total,
                query = query,
                searchOpen = searchOpen || query.isNotEmpty(),
                onSearchToggle = { if (searchOpen || query.isNotEmpty()) { vm.query.value = ""; searchOpen = false } else searchOpen = true },
                onQuery = { vm.query.value = it },
                filter = filter,
                groups = state.groups,
                onFilter = { vm.filter.value = it },
                sort = sort,
                onSort = vm::setSort,
                birthdays = state.birthdays,
                birthdaysOpen = birthdaysOpen,
                onBirthdays = { birthdaysOpen = !birthdaysOpen },
                onOpen = onOpen,
                onAdd = onAdd,
                onGroups = onGroups,
                onNoa = onNoa,
                onMap = onMap,
                onStats = onStats,
            )
        }

        if (!state.loading && state.total == 0) {
            item(key = "empty") {
                EmptyState(
                    Icons.Default.Contacts,
                    t("Пока никого нет"),
                    t("Добавьте первого человека вручную или импортируйте контакты из телефона."),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onAdd) { Text(t("Добавить")) }
                        FilledTonalButton(onClick = onImport) { Text(t("Импорт контактов")) }
                    }
                }
            }
        } else if (!state.loading && state.results.isEmpty()) {
            item(key = "nothing") {
                EmptyState(Icons.Default.SearchOff, t("Ничего не найдено"), t("Попробуйте изменить запрос или фильтр."))
            }
        }

        val grouped = sort == SortMode.NAME && query.isBlank()
        if (grouped) {
            state.results.groupBy { it.person.person.sortKey.firstOrNull()?.uppercaseChar() ?: '#' }
                .forEach { (letter, hits) ->
                    stickyHeader(key = "h_${letter}") { LetterHeader(letter) }
                    items(hits, key = { it.person.person.id }) { hit ->
                        SwipeActionsRow(hit.person, swiped == hit.person.person.id, { swiped = if (it) hit.person.person.id else null }, Modifier.animateItem()) {
                            PersonRow(hit, onClick = { onOpen(hit.person.person.id) })
                        }
                    }
                }
        } else {
            if (state.results.isNotEmpty()) {
                item(key = "count") {
                    Text(
                        t("Найдено: %1\$s", state.results.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
                    )
                }
            }
            items(state.results, key = { it.person.person.id }) { hit ->
                SwipeActionsRow(hit.person, swiped == hit.person.person.id, { swiped = if (it) hit.person.person.id else null }, Modifier.animateItem()) {
                    PersonRow(hit, onClick = { onOpen(hit.person.person.id) })
                }
            }
        }
    }
}

/** Тёмная шапка «Люди»: бренд, заголовок со счётчиком, поиск, живые фильтры и круглые быстрые действия. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PeopleHero(
    total: Int,
    query: String,
    searchOpen: Boolean,
    onSearchToggle: () -> Unit,
    onQuery: (String) -> Unit,
    filter: PeopleFilter,
    groups: List<GroupWithCount>,
    onFilter: (PeopleFilter) -> Unit,
    sort: SortMode,
    onSort: (SortMode) -> Unit,
    birthdays: List<Pair<PersonFull, Long>>,
    birthdaysOpen: Boolean,
    onBirthdays: () -> Unit,
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
    onGroups: () -> Unit,
    onNoa: () -> Unit,
    onMap: () -> Unit,
    onStats: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = app()
    val custom by app.settings.appTitle.value.collectAsState()
    val assistantOn by app.settings.assistant.value.collectAsState()
    var menu by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 34.dp, bottomEnd = 34.dp))
            .heroBackground()
            .statusBarsPadding()
            .padding(bottom = 18.dp),
    ) {
        // Бренд-строка.
        Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 8.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            BrandMark()
            Spacer(Modifier.width(10.dp))
            Text(
                com.kartoteka.app.AppIcons.title(context, custom).uppercase(),
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp),
                color = Rv.HeroText, modifier = Modifier.weight(1f), maxLines = 1,
            )
            if (assistantOn) IconButton(onClick = onNoa) { Icon(Icons.Default.Mic, t("Ассистент"), tint = Rv.HeroText) }
            IconButton(onClick = onSearchToggle) {
                Icon(if (searchOpen) Icons.Default.Close else Icons.Default.Search, t("Поиск"), tint = Rv.HeroText)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, t("Ещё"), tint = Rv.HeroText) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    Text(t("Сортировка"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                    SortMode.entries.forEach { m ->
                        DropdownMenuItem(
                            text = { Text(m.title) },
                            onClick = { onSort(m); menu = false },
                            trailingIcon = { if (m == sort) Icon(Icons.Default.Check, null) },
                        )
                    }
                    androidx.compose.material3.HorizontalDivider()
                    DropdownMenuItem(text = { Text(t("Группы")) }, leadingIcon = { Icon(Icons.Default.Workspaces, null) }, onClick = { menu = false; onGroups() })
                    DropdownMenuItem(text = { Text(t("Статистика")) }, leadingIcon = { Icon(Icons.Default.Insights, null) }, onClick = { menu = false; onStats() })
                }
            }
        }

        // Заголовок с характером.
        Row(Modifier.padding(start = 22.dp, end = 22.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(t("Люди"), style = MaterialTheme.typography.displaySmall, color = Rv.HeroText)
            Spacer(Modifier.width(12.dp))
            CountPill(total, dark = true)
        }
        Text(
            t("Ваш круг. Клиенты, друзья, семья."),
            style = MaterialTheme.typography.bodyMedium, color = Rv.HeroMuted,
            modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 4.dp),
        )

        // Поиск раскрывается по нажатию, освобождая место под результаты.
        AnimatedVisibility(
            visible = searchOpen,
            enter = expandVertically(motion(Motion.STANDARD)) + fadeIn(motion(Motion.STANDARD)),
            exit = shrinkVertically(motion(Motion.STANDARD)) + fadeOut(motion(Motion.MICRO)),
        ) {
            val focus = remember { androidx.compose.ui.focus.FocusRequester() }
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
            TextField(
                value = query,
                onValueChange = onQuery,
                placeholder = { Text(t("Имя, город, профессия, заметки…")) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Default.Close, t("Очистить")) } },
                singleLine = true,
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = Rv.HeroSurface, unfocusedContainerColor = Rv.HeroSurface,
                    focusedTextColor = Rv.HeroText, unfocusedTextColor = Rv.HeroText,
                    focusedPlaceholderColor = Rv.HeroMuted, unfocusedPlaceholderColor = Rv.HeroMuted,
                    focusedLeadingIconColor = Rv.HeroMuted, unfocusedLeadingIconColor = Rv.HeroMuted,
                    focusedTrailingIconColor = Rv.HeroText, unfocusedTrailingIconColor = Rv.HeroText,
                    cursorColor = Rv.Peach,
                ),
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp).focusRequester(focus),
            )
        }

        // Живые фильтры.
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 16.dp),
        ) {
            item { HeroChip(t("Все"), filter == PeopleFilter.All, count = total) { onFilter(PeopleFilter.All) } }
            item {
                HeroChip(t("Избранные"), filter == PeopleFilter.Favorites, icon = Icons.Default.Star) {
                    onFilter(if (filter == PeopleFilter.Favorites) PeopleFilter.All else PeopleFilter.Favorites)
                }
            }
            items(groups, key = { it.group.id }) { g ->
                val sel = filter == PeopleFilter.InGroup(g.group.id)
                HeroChip(
                    listOf(g.group.emoji, g.group.name).filter { it.isNotBlank() }.joinToString(" "),
                    sel, count = g.count, dot = Color(g.group.color),
                ) { onFilter(if (sel) PeopleFilter.All else PeopleFilter.InGroup(g.group.id)) }
            }
        }

        // Круглые быстрые действия.
        val todayBd = birthdays.filter { it.second == 0L }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 18.dp),
        ) {
            item { CircleAction(t("Добавить"), onClick = onAdd) { Icon(Icons.Default.Add, null, tint = Rv.HeroText, modifier = Modifier.size(26.dp)) } }
            items(todayBd, key = { "bd_${it.first.person.id}" }) { (pf, _) ->
                CircleAction(t("Сегодня"), ring = Rv.Peach, onClick = { onOpen(pf.person.id) }) { Avatar(pf.person, 60.dp) }
            }
            if (birthdays.isNotEmpty()) item {
                CircleAction(t("Дни рожд."), container = if (birthdaysOpen) Rv.Peach else Rv.HeroSurface, onClick = onBirthdays) {
                    Icon(Icons.Default.Cake, null, tint = if (birthdaysOpen) Rv.Ink else Rv.HeroText)
                }
            }
            item { CircleAction(t("Карта"), onClick = onMap) { Icon(Icons.Default.Map, null, tint = Rv.HeroText) } }
            item { CircleAction(t("Группы"), onClick = onGroups) { Icon(Icons.Default.Workspaces, t("Группы"), tint = Rv.HeroText) } }
            item { CircleAction(t("Статистика"), onClick = onStats) { Icon(Icons.Default.Insights, null, tint = Rv.HeroText) } }
        }

        AnimatedVisibility(
            visible = birthdaysOpen && birthdays.isNotEmpty(),
            enter = expandVertically(motion(Motion.EMPHASIZED)) + fadeIn(motion(Motion.STANDARD)),
            exit = shrinkVertically(motion(Motion.STANDARD)) + fadeOut(motion(Motion.MICRO)),
        ) { BirthdayStrip(birthdays, onOpen) }
    }
}

/** Фирменный знак: персиковая плашка с литерой R. */
@Composable
private fun BrandMark() {
    Box(
        Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(Brush.linearGradient(listOf(Rv.Peach, Rv.PeachDeep))),
        contentAlignment = Alignment.Center,
    ) {
        Text("R", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, fontSize = 17.sp), color = Rv.Ink)
    }
}

/** Чип в тёмной шапке: выбранный — персиковый, переход цвета плавный. */
@Composable
private fun HeroChip(label: String, selected: Boolean, count: Int? = null, dot: Color? = null, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Rv.Peach else Rv.HeroSurface, motion(Motion.MICRO), label = "chipbg")
    val fg by animateColorAsState(if (selected) Rv.Ink else Rv.HeroText, motion(Motion.MICRO), label = "chipfg")
    Row(
        Modifier.height(36.dp).clip(CircleShape).background(bg).pressable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) { Box(Modifier.size(8.dp).clip(CircleShape).background(dot)); Spacer(Modifier.width(7.dp)) }
        if (icon != null) { Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
        if (count != null) {
            Spacer(Modifier.width(7.dp))
            Text("$count", style = MaterialTheme.typography.labelMedium, color = fg.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun BirthdayStrip(list: List<Pair<PersonFull, Long>>, onOpen: (Long) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(top = 14.dp),
    ) {
        items(list, key = { it.first.person.id }) { (pf, days) ->
            val p = pf.person
            Column(
                Modifier.width(118.dp).clip(RoundedCornerShape(22.dp))
                    .background(if (days == 0L) Rv.Peach else Rv.HeroSurface)
                    .pressable { onOpen(p.id) }
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Avatar(p, 50.dp)
                Spacer(Modifier.height(8.dp))
                val fg = if (days == 0L) Rv.Ink else Rv.HeroText
                Text(p.firstName.ifBlank { p.displayName }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, color = fg)
                Text(ArchiveLogic.daysString(days), style = MaterialTheme.typography.labelSmall, color = if (days == 0L) Rv.Ink.copy(alpha = 0.7f) else Rv.Peach)
                ArchiveLogic.turningAge(p)?.let {
                    Text(ArchiveLogic.ageString(it), style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.6f))
                }
            }
        }
    }
}

@Composable
private fun LetterHeader(letter: Char) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(letter.toString(), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
    }
}

/**
 * Строка с контекстными действиями: свайп влево открывает «Позвонить / Написать / В избранное».
 * Открыта только одна строка — остальные закрываются.
 */
@Composable
private fun SwipeActionsRow(pf: PersonFull, open: Boolean, onOpenChange: (Boolean) -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = app()
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val actions = buildList<Triple<androidx.compose.ui.graphics.vector.ImageVector, Color, () -> Unit>> {
        pf.phone?.let { add(Triple(Icons.Default.Call, Rv.Lime) { com.kartoteka.app.messaging.Messaging.dial(context, it) }) }
        pf.whatsapp?.let { add(Triple(Icons.AutoMirrored.Filled.Chat, Rv.Lavender) { com.kartoteka.app.messaging.Messaging.whatsapp(context, it) }) }
        add(Triple(if (pf.person.favorite) Icons.Default.StarBorder else Icons.Default.Star, Rv.PeachDeep) {
            scope.launch { app.repository.setFavorite(pf.person.id, !pf.person.favorite) }
        })
    }
    val revealPx = with(density) { (actions.size * 58 + 12).dp.toPx() }
    val offset = remember { androidx.compose.animation.core.Animatable(0f) }
    val spec = motion<Float>(Motion.STANDARD)
    LaunchedEffect(open) { offset.animateTo(if (open) -revealPx else 0f, spec) }

    Box(modifier.fillMaxWidth()) {
        // Подложка с действиями.
        Row(
            Modifier.matchParentSize().padding(end = 12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            actions.forEach { (icon, tint, action) ->
                val a = (-offset.value / revealPx).coerceIn(0f, 1f)
                Box(
                    Modifier.padding(start = 8.dp).size(50.dp).graphicsLayer { alpha = a; scaleX = 0.6f + 0.4f * a; scaleY = 0.6f + 0.4f * a }
                        .clip(CircleShape).background(tint.copy(alpha = 0.16f))
                        .clickable { onOpenChange(false); action() },
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, null, tint = tint) }
            }
        }
        Box(
            Modifier.fillMaxWidth()
                .graphicsLayer { translationX = offset.value }
                .background(MaterialTheme.colorScheme.background)
                .draggable(
                    orientation = androidx.compose.foundation.gestures.Orientation.Horizontal,
                    state = androidx.compose.foundation.gestures.rememberDraggableState { d ->
                        scope.launch { offset.snapTo((offset.value + d).coerceIn(-revealPx * 1.15f, 0f)) }
                    },
                    onDragStopped = { v ->
                        val shouldOpen = offset.value < -revealPx / 2 || v < -1200f
                        if (shouldOpen && !open) haptics.tick()
                        onOpenChange(shouldOpen)
                        offset.animateTo(if (shouldOpen) -revealPx else 0f, spec)
                    },
                ),
        ) { content() }
    }
}

@Composable
fun PersonRow(hit: ArchiveLogic.SearchHit, onClick: () -> Unit, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    val pf = hit.person
    val p = pf.person
    val days = ArchiveLogic.daysUntilBirthday(p)
    val ring = pf.groups.firstOrNull()?.let { Color(it.color) } ?: categoryColor(p.relation)
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Кольцо категории вокруг аватара.
        Box(
            Modifier.size(58.dp).clip(CircleShape).background(ring.copy(alpha = if (ring == Color.Transparent) 0f else 0.9f)).padding(2.5.dp)
                .clip(CircleShape).background(MaterialTheme.colorScheme.background).padding(2.dp),
            contentAlignment = Alignment.Center,
        ) { Avatar(p, 49.dp) }
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
                    Spacer(Modifier.width(5.dp))
                    Icon(Icons.Default.Star, null, tint = Rv.PeachDeep, modifier = Modifier.size(15.dp))
                }
                pf.groups.drop(1).take(3).forEach {
                    Spacer(Modifier.width(4.dp))
                    ColorDot(it.color, 7.dp)
                }
            }
            val sub = listOf(t(p.relation), p.company.ifBlank { p.position }, p.city).filter { it.isNotBlank() }.joinToString(" · ")
            if (hit.matchedIn != null) {
                Text(hit.matchedIn, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else if (sub.isNotBlank()) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (days != null && days <= 7) {
            Spacer(Modifier.width(8.dp))
            Badge("🎂 " + ArchiveLogic.daysString(days))
        }
        trailing()
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
    }
}
