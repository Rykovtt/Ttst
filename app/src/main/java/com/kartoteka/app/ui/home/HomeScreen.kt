package com.kartoteka.app.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.kartoteka.app.KartotekaApp
import com.kartoteka.app.R
import com.kartoteka.app.data.AppointmentFull
import com.kartoteka.app.data.AppointmentLogic
import com.kartoteka.app.data.AppointmentStatus
import com.kartoteka.app.data.ArchiveLogic
import com.kartoteka.app.data.GroupWithCount
import com.kartoteka.app.data.PeopleHints
import com.kartoteka.app.data.PersonFull
import com.kartoteka.app.data.PersonHint
import com.kartoteka.app.data.SortMode
import com.kartoteka.app.i18n.t
import com.kartoteka.app.messaging.Messaging
import com.kartoteka.app.ui.app
import com.kartoteka.app.ui.components.Avatar
import com.kartoteka.app.ui.components.ColorDot
import com.kartoteka.app.ui.components.EmptyState
import com.kartoteka.app.ui.components.NoaOrb
import com.kartoteka.app.ui.components.categoryColor
import com.kartoteka.app.ui.components.pressable
import com.kartoteka.app.ui.theme.Wordmark
import com.kartoteka.app.ui.components.SheenColors
import com.kartoteka.app.ui.components.sheenBorder
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Workspaces
import com.kartoteka.app.ui.theme.Motion
import com.kartoteka.app.ui.theme.Rv
import com.kartoteka.app.ui.theme.motion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

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
    /** Подсказка под именем для каждого человека. */
    val hints: Map<Long, PersonHint> = emptyMap(),
    /** Записи на сегодня (для круга «Сегодня»). */
    val today: List<AppointmentFull> = emptyList(),
)

class HomeViewModel(private val app: KartotekaApp) : ViewModel() {
    val query = MutableStateFlow("")
    val filter = MutableStateFlow<PeopleFilter>(PeopleFilter.All)
    val sort = app.settings.sortMode

    private val appts = run {
        val d = LocalDate.now()
        app.repository.observeAppointments(
            AppointmentLogic.millis(d.minusYears(2).atStartOfDay()),
            AppointmentLogic.millis(d.plusDays(30).atStartOfDay()),
        )
    }

    private val base = combine(
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
        ) to all
    }

    val state = combine(base, appts) { (st, all), list ->
        val now = LocalDateTime.now()
        val byPerson = list.groupBy { it.appointment.personId }
        val today = now.toLocalDate()
        st.copy(
            hints = all.mapNotNull { pf -> PeopleHints.hint(pf, byPerson[pf.person.id].orEmpty(), now)?.let { pf.person.id to it } }.toMap(),
            today = list.filter {
                it.appointment.appointmentStatus != AppointmentStatus.CANCELLED &&
                    AppointmentLogic.zoned(it.appointment.start).toLocalDate() == today
            }.sortedBy { it.appointment.start },
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
    onCalendar: () -> Unit = {},
    onNewAppointment: (Long) -> Unit = {},
) {
    val app = app()
    val vm: HomeViewModel = viewModel { HomeViewModel(app) }
    val state by vm.state.collectAsState()
    val query by vm.query.collectAsState()
    val filter by vm.filter.collectAsState()
    val sort by vm.sort.collectAsState()
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 40.dp),
    ) {
        item(key = "hero") {
            PeopleHero(
                state = state, query = query, onQuery = { vm.query.value = it },
                filter = filter, onFilter = { vm.filter.value = it },
                sort = sort, onSort = vm::setSort,
                onOpen = onOpen, onAdd = onAdd, onGroups = onGroups, onNoa = onNoa, onMap = onMap,
                onStats = onStats, onCalendar = onCalendar,
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
                    hits.forEachIndexed { i, hit ->
                        item(key = hit.person.person.id) {
                            PersonCard(hit, state.hints[hit.person.person.id], last = i == hits.lastIndex, onOpen = onOpen, onNewAppointment = onNewAppointment, modifier = Modifier.animateItem())
                        }
                    }
                }
        } else {
            if (state.results.isNotEmpty() && query.isNotBlank()) {
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
                PersonCard(hit, state.hints[hit.person.person.id], last = false, onOpen = onOpen, onNewAppointment = onNewAppointment, modifier = Modifier.animateItem())
            }
        }
    }
}

/** Шапка «Люди»: фото гор, бренд, крупный заголовок, поиск, фильтры и быстрые действия. */
@Composable
private fun PeopleHero(
    state: HomeState,
    query: String,
    onQuery: (String) -> Unit,
    filter: PeopleFilter,
    onFilter: (PeopleFilter) -> Unit,
    sort: SortMode,
    onSort: (SortMode) -> Unit,
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
    onGroups: () -> Unit,
    onNoa: () -> Unit,
    onMap: () -> Unit,
    onStats: () -> Unit,
    onCalendar: () -> Unit,
) {
    val context = LocalContext.current
    val app = app()
    val custom by app.settings.appTitle.value.collectAsState()
    val assistantOn by app.settings.assistant.value.collectAsState()
    var menu by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp)
    Box(Modifier.fillMaxWidth().clip(shape).background(Rv.HeroBg)) {
        // Фото гор: в тёплом свете справа, плавно уходит в тёмное к низу.
        Image(
            painterResource(R.drawable.hero_mountain), null,
            contentScale = ContentScale.Crop, alignment = Alignment.TopEnd,
            modifier = Modifier.matchParentSize(),
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.10f), 0.40f to Color.Transparent, 0.60f to Rv.HeroBg.copy(alpha = 0.55f), 1f to Rv.HeroBg)
            )
        )
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(bottom = 20.dp)) {
            // Бренд-строка: тонкий словесный знак, жемчужная сфера «сейфа» и меню.
            Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 14.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    com.kartoteka.app.AppIcons.title(context, custom).uppercase(),
                    style = TextStyle(fontFamily = Wordmark, fontWeight = FontWeight.Light, fontSize = 25.sp, letterSpacing = 0.sp),
                    color = Rv.HeroText, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Box(Modifier.padding(start = 6.dp, top = 4.dp).size(6.dp).clip(CircleShape).background(Color(0xFF8E8983)))
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(44.dp).clip(CircleShape).pressable {
                        (context as? com.kartoteka.app.MainActivity)?.closeVault()
                    }.semantics { contentDescription = t("Закрыть сейф") },
                    contentAlignment = Alignment.Center,
                ) { PearlSphere(Modifier.size(22.dp)) }
                Box {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).pressable { menu = true }.semantics { contentDescription = t("Ещё") },
                        contentAlignment = Alignment.Center,
                    ) { MenuGlyph() }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        Text(t("Сортировка"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                        SortMode.entries.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m.title) },
                                onClick = { onSort(m); menu = false },
                                trailingIcon = { if (m == sort) Icon(Icons.Default.Check, null) },
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text(t("Группы")) }, leadingIcon = { Icon(Icons.Outlined.Workspaces, null) }, onClick = { menu = false; onGroups() })
                        DropdownMenuItem(text = { Text(t("Статистика")) }, leadingIcon = { Icon(Icons.Outlined.Insights, null) }, onClick = { menu = false; onStats() })
                    }
                }
            }

            // Заголовок со счётчиком.
            Row(Modifier.padding(start = 22.dp, end = 22.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(t("Люди"), style = TextStyle(fontFamily = com.kartoteka.app.ui.theme.Inter, fontWeight = FontWeight.Bold, fontSize = 46.sp, lineHeight = 50.sp, letterSpacing = (-1.5).sp), color = Rv.HeroText)
                Spacer(Modifier.width(12.dp))
                Box(
                    Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.16f))
                        .sheenBorder(1.dp, CircleShape, SheenColors.Glass)
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                ) { Text("${state.total}", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), color = Rv.HeroText) }
            }
            Text(
                t("Ваш круг. Клиенты, друзья, семья."),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                color = Rv.HeroText,
                modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 4.dp),
            )

            // Поиск — всегда под рукой.
            SearchBar(query, onQuery, sort, onSort)

            // Фильтры.
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 12.dp),
            ) {
                item { FilterPill(t("Все"), filter == PeopleFilter.All, count = state.total) { onFilter(PeopleFilter.All) } }
                items(state.groups, key = { it.group.id }) { g ->
                    val sel = filter == PeopleFilter.InGroup(g.group.id)
                    FilterPill(g.group.name, sel, count = g.count, dot = Color(g.group.color)) {
                        onFilter(if (sel) PeopleFilter.All else PeopleFilter.InGroup(g.group.id))
                    }
                }
                item {
                    FilterPill(t("Избранные"), filter == PeopleFilter.Favorites, icon = Icons.Outlined.StarOutline) {
                        onFilter(if (filter == PeopleFilter.Favorites) PeopleFilter.All else PeopleFilter.Favorites)
                    }
                }
            }

            // Быстрые действия.
            val todayPeople = state.today.mapNotNull { it.person }.distinctBy { it.id }
            val bdSoon = state.birthdays
            LazyRow(
                contentPadding = PaddingValues(start = 16.dp, end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Top,
                modifier = Modifier.padding(top = 16.dp),
            ) {
                item {
                    val tile = RoundedCornerShape(20.dp)
                    Column(
                        Modifier.padding(end = 4.dp).width(52.dp).height(82.dp).pressable(onClick = onAdd)
                            .clip(tile).background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.03f))))
                            .sheenBorder(1.dp, tile, SheenColors.Glass),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Default.Add, null, tint = Rv.HeroText, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.height(5.dp))
                        Text(t("Добавить\nчеловека"), style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp, lineHeight = 11.sp), color = Rv.HeroText, textAlign = TextAlign.Center)
                    }
                }
                item {
                    val p = todayPeople.firstOrNull()
                    QuickCircle(t("Сегодня"), "${state.today.size}", dot = p?.let { categoryColor(it.relation).takeIf { c -> c != Color.Transparent } ?: Rv.Lavender }, onClick = onCalendar) {
                        if (p != null) Avatar(p, 52.dp) else CircleIcon(Icons.Outlined.CalendarMonth)
                    }
                }
                item {
                    val p = bdSoon.firstOrNull()?.first?.person
                    QuickCircle(t("Дни рождения"), "${bdSoon.size}", dot = if (p != null) Rv.Lime else null, onClick = { p?.let { onOpen(it.id) } ?: onCalendar() }) {
                        if (p != null) Avatar(p, 52.dp) else CircleIcon(Icons.Outlined.Redeem)
                    }
                }
                item {
                    QuickCircle(t("Карта"), null, onClick = onMap) {
                        Image(painterResource(R.drawable.hero_mountain), null, contentScale = ContentScale.Crop, alignment = Alignment.TopEnd, modifier = Modifier.fillMaxSize())
                    }
                }
                if (assistantOn) item {
                    Column(Modifier.width(72.dp).pressable(onClick = onNoa), horizontalAlignment = Alignment.CenterHorizontally) {
                        val tile = RoundedCornerShape(18.dp)
                        Box(
                            Modifier.size(58.dp).clip(tile).background(Brush.verticalGradient(listOf(Color(0xFF1A1A1D), Color(0xFF0A0A0B))))
                                .sheenBorder(1.dp, tile, SheenColors.Champagne.map { it.copy(alpha = it.alpha * 0.7f) }),
                            contentAlignment = Alignment.Center,
                        ) { NoaOrb(Modifier.size(38.dp)) }
                        Spacer(Modifier.height(6.dp))
                        Text(t("Ассистент\nНоа"), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, lineHeight = 12.sp), color = Rv.HeroText, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

/** Жемчужная сфера «сейфа» — глянцевый белый шар с мягкой тенью. */
@Composable
private fun PearlSphere(modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val r = size.minDimension / 2
        drawCircle(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent), center.copy(y = center.y + r * 0.25f), r * 1.25f), r * 1.25f, center.copy(y = center.y + r * 0.25f))
        drawCircle(
            Brush.radialGradient(listOf(Color.White, Color(0xFFF1ECE6), Color(0xFFC9C1B8)), center.copy(x = center.x - r * 0.35f, y = center.y - r * 0.4f), r * 1.7f),
            r, center,
        )
        // Внутренний «зрачок» — как замочная скважина сейфа.
        drawCircle(Brush.radialGradient(listOf(Color(0xFFCFC8C0), Color(0xFFE7E1DA)), center, r * 0.5f), r * 0.46f, center)
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f), Color.Transparent), center.copy(x = center.x - r * 0.35f, y = center.y - r * 0.4f), r * 0.4f), r * 0.4f, center.copy(x = center.x - r * 0.35f, y = center.y - r * 0.4f))
    }
}

/** Значок меню: три тонкие линии разной длины, выровненные вправо. */
@Composable
private fun MenuGlyph() {
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.width(22.dp).height(1.6.dp).clip(CircleShape).background(Rv.HeroText))
        Box(Modifier.width(17.dp).height(1.6.dp).clip(CircleShape).background(Rv.HeroText))
        Box(Modifier.width(20.dp).height(1.6.dp).clip(CircleShape).background(Rv.HeroText))
    }
}

@Composable
private fun SearchBar(query: String, onQuery: (String) -> Unit, sort: SortMode, onSort: (SortMode) -> Unit) {
    var sortMenu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp).height(40.dp).clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Color(0x8C1A1816), Color(0xA60C0B0A))))
            .sheenBorder(1.dp, CircleShape, SheenColors.Glass)
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, null, tint = Rv.HeroText.copy(alpha = 0.85f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text(t("Поиск: имя, город, заметка…"), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), color = Color(0xFFADA79F), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(
                value = query, onValueChange = onQuery, singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, color = Rv.HeroText),
                cursorBrush = SolidColor(Rv.Peach),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = t("Поиск") },
            )
        }
        if (query.isNotEmpty()) {
            Box(Modifier.size(36.dp).clip(CircleShape).clickable { onQuery("") }, contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Close, t("Очистить"), tint = Rv.HeroText, modifier = Modifier.size(18.dp))
            }
        }
        Box(Modifier.width(1.dp).height(22.dp).background(Color.White.copy(alpha = 0.16f)))
        Box {
            Box(Modifier.size(40.dp).clip(CircleShape).clickable { sortMenu = true }, contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Tune, t("Сортировка"), tint = Rv.HeroText, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                SortMode.entries.forEach { m ->
                    DropdownMenuItem(
                        text = { Text(m.title) },
                        onClick = { onSort(m); sortMenu = false },
                        trailingIcon = { if (m == sort) Icon(Icons.Default.Check, null) },
                    )
                }
            }
        }
    }
}

/** Фильтр-«таблетка»: выбранный — кремовый, остальные — тёмное стекло с переливающейся кромкой. */
@Composable
private fun FilterPill(label: String, selected: Boolean, count: Int? = null, dot: Color? = null, icon: ImageVector? = null, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Color(0xFFF7E6D7) else Color(0x80141210), motion(Motion.MICRO), label = "pillbg")
    val fg by animateColorAsState(if (selected) Rv.Ink else Rv.HeroText, motion(Motion.MICRO), label = "pillfg")
    Row(
        Modifier.height(28.dp).pressable(onClick = onClick).clip(CircleShape).background(bg)
            .then(if (selected) Modifier else Modifier.sheenBorder(1.dp, CircleShape, SheenColors.Glass))
            .padding(start = 10.dp, end = if (count != null) 4.dp else 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) { Box(Modifier.size(7.dp).clip(CircleShape).background(dot)); Spacer(Modifier.width(6.dp)) }
        if (icon != null) { Icon(icon, null, tint = fg, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(5.dp)) }
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal), color = fg, maxLines = 1)
        if (count != null) {
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier.clip(CircleShape).background(if (selected) Color(0x14000000) else Color.White.copy(alpha = 0.10f))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            ) { Text("$count", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = fg.copy(alpha = 0.8f)) }
        }
    }
}

/** Круг быстрого действия: фото в кольце цвета шампань (кольцо медленно переливается), точка-статус, подпись и число. */
@Composable
private fun QuickCircle(label: String, value: String?, dot: Color? = null, onClick: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.width(72.dp).pressable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(60.dp)) {
            Box(
                Modifier.fillMaxSize().sheenBorder(1.6.dp, CircleShape, SheenColors.Champagne)
                    .padding(3.dp).clip(CircleShape).background(Rv.HeroSurface),
                contentAlignment = Alignment.Center,
            ) { content() }
            if (dot != null && dot != Color.Transparent) {
                Box(
                    Modifier.align(Alignment.BottomEnd).offset(x = (-2).dp, y = (-4).dp).size(12.dp).clip(CircleShape)
                        .background(Rv.HeroBg).padding(2.dp).clip(CircleShape).background(dot)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp), color = Rv.HeroText, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        if (value != null) Text(value, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp), color = Rv.HeroText.copy(alpha = 0.85f))
    }
}

@Composable
private fun CircleIcon(icon: ImageVector) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Rv.HeroText, modifier = Modifier.size(22.dp)) }
}

@Composable
private fun LetterHeader(letter: Char) {
    Text(
        letter.toString(),
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal, fontSize = 15.sp),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 2.dp),
    )
}

/**
 * Карточка человека в списке: фото с точкой категории, имя и звёздочка, роль · работа · город,
 * чип с ближайшим важным; справа — звонок, сообщение, меню и два последних фото.
 */
@Composable
private fun PersonCard(
    hit: ArchiveLogic.SearchHit,
    hint: PersonHint?,
    last: Boolean,
    onOpen: (Long) -> Unit,
    onNewAppointment: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = app()
    val scope = rememberCoroutineScope()
    val pf = hit.person
    val p = pf.person
    var menu by remember { mutableStateOf(false) }
    val dot = pf.groups.firstOrNull()?.let { Color(it.color) } ?: categoryColor(p.relation)
    val thumbs = pf.photos.filter { it.path != p.avatarPath }.takeLast(2).ifEmpty { pf.photos.takeLast(2) }
    Column(modifier.fillMaxWidth().clickable { onOpen(p.id) }) {
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 14.dp, top = 12.dp, bottom = 14.dp)) {
            // Фото с точкой категории.
            Box(Modifier.size(54.dp)) {
                Avatar(p, 52.dp, Modifier.align(Alignment.Center))
                if (dot != Color.Transparent) {
                    Box(
                        Modifier.align(Alignment.TopEnd).size(13.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.background).padding(2.dp).clip(CircleShape).background(dot)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).padding(top = 2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        p.displayName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = (-0.3).sp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        if (p.favorite) Icons.Default.Star else Icons.Outlined.StarOutline,
                        t("Избранное"),
                        tint = if (p.favorite) Rv.Peach else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp).clip(CircleShape).clickable {
                            scope.launch { app.repository.setFavorite(p.id, !p.favorite) }
                        },
                    )
                }
                val sub = listOf(t(p.relation), p.position.ifBlank { p.company }, p.city).filter { it.isNotBlank() }
                if (hit.matchedIn != null) {
                    Text(hit.matchedIn, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else if (sub.isNotEmpty()) {
                    Text(sub.joinToString("  ·  "), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (hint != null) {
                    Spacer(Modifier.height(8.dp))
                    HintChip(hint)
                }
            }
            Spacer(Modifier.width(6.dp))
            Column(horizontalAlignment = Alignment.End) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RoundAction(Icons.Default.Call, t("Позвонить"), enabled = pf.phone != null) { pf.phone?.let { Messaging.dial(context, it) } }
                    RoundAction(Icons.AutoMirrored.Filled.Chat, t("Написать"), enabled = pf.whatsapp != null || pf.phone != null) {
                        pf.whatsapp?.let { Messaging.whatsapp(context, it) } ?: pf.phone?.let { Messaging.sms(context, listOf(it)) }
                    }
                    Box {
                        RoundAction(Icons.Default.MoreVert, t("Действия")) { menu = true }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(t("Открыть карточку")) }, leadingIcon = { Icon(Icons.Default.Person, null) }, onClick = { menu = false; onOpen(p.id) })
                            DropdownMenuItem(text = { Text(t("Записать")) }, leadingIcon = { Icon(Icons.Default.EventAvailable, null) }, onClick = { menu = false; onNewAppointment(p.id) })
                            DropdownMenuItem(
                                text = { Text(if (p.favorite) t("Убрать из избранного") else t("В избранное")) },
                                leadingIcon = { Icon(if (p.favorite) Icons.Default.StarBorder else Icons.Default.Star, null) },
                                onClick = { menu = false; scope.launch { app.repository.setFavorite(p.id, !p.favorite) } },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    thumbs.forEach { ph ->
                        AsyncImage(
                            model = File(ph.path), contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(28.dp).clip(RoundedCornerShape(7.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                    }
                    if (thumbs.isNotEmpty()) Spacer(Modifier.width(2.dp))
                    Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
                }
            }
        }
        if (!last) HorizontalDivider(Modifier.padding(start = 20.dp, end = 20.dp), thickness = 0.8.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun RoundAction(icon: ImageVector, desc: String, enabled: Boolean = true, plain: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(30.dp).alpha(if (enabled) 1f else 0.35f).clip(CircleShape)
            .background(if (plain) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.75f))
            .then(if (enabled) Modifier.pressable(onClick = onClick) else Modifier)
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(15.dp)) }
}

/** Чип-подсказка: сегодня / день рождения / ближайшая запись / когда виделись. */
@Composable
private fun HintChip(h: PersonHint) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val (icon, text, accent) = when (h) {
        is PersonHint.Today -> Triple(Icons.Outlined.CalendarMonth, t("Сегодня %1\$s", h.time), false)
        is PersonHint.Upcoming -> Triple(
            Icons.Outlined.CalendarMonth,
            (if (h.date == LocalDate.now().plusDays(1)) t("Завтра") else com.kartoteka.app.i18n.I18n.dayMonth(h.date.dayOfMonth, h.date.monthValue)) + " " + h.time,
            false,
        )
        is PersonHint.Birthday -> Triple(
            Icons.Outlined.Redeem,
            if (h.days == 0L) t("День рождения сегодня") else t("День рождения %1\$s", com.kartoteka.app.i18n.I18n.dayMonth(h.day, h.month)),
            true,
        )
        is PersonHint.LastMet -> Triple(
            Icons.Outlined.Schedule,
            when (h.days) {
                0L -> t("Виделись сегодня")
                1L -> t("Виделись вчера")
                else -> t("Была встреча %1\$s дн. назад", h.days)
            },
            false,
        )
    }
    val accentText = if (dark) Rv.Peach else Color(0xFFB4532A)
    Row(
        Modifier.clip(CircleShape)
            .background(
                if (accent) (if (dark) Rv.PeachDeep.copy(alpha = 0.18f) else Color(0xFFFBE3DA))
                else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (accent) Color(0xFFC4573A) else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(7.dp))
        Text(text, style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Normal), color = if (accent) accentText else MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Строка человека для других экранов (группы и т. п.) — компактная. */
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
        Box(Modifier.size(56.dp)) {
            Avatar(p, 52.dp, Modifier.align(Alignment.Center))
            if (ring != Color.Transparent) {
                Box(Modifier.align(Alignment.TopEnd).size(14.dp).clip(CircleShape).background(MaterialTheme.colorScheme.background).padding(2.5.dp).clip(CircleShape).background(ring))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (p.favorite) {
                    Spacer(Modifier.width(5.dp))
                    Icon(Icons.Default.Star, null, tint = Rv.Peach, modifier = Modifier.size(15.dp))
                }
                pf.groups.drop(1).take(3).forEach { Spacer(Modifier.width(4.dp)); ColorDot(it.color, 7.dp) }
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
            com.kartoteka.app.ui.components.Badge("🎂 " + ArchiveLogic.daysString(days))
        }
        trailing()
    }
}
