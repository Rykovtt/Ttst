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
import com.kartoteka.app.ui.theme.Manrope
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
    val shape = RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp)
    Box(Modifier.fillMaxWidth().clip(shape).background(Rv.HeroBg)) {
        // Фото гор: в тёплом свете справа, плавно уходит в тёмное к низу.
        Image(
            painterResource(R.drawable.hero_mountain), null,
            contentScale = ContentScale.Crop, alignment = Alignment.TopEnd,
            modifier = Modifier.matchParentSize(),
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.15f), 0.45f to Color.Transparent, 0.62f to Rv.HeroBg.copy(alpha = 0.55f), 1f to Rv.HeroBg)
            )
        )
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(bottom = 18.dp)) {
            // Бренд-строка.
            Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 12.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    com.kartoteka.app.AppIcons.title(context, custom).uppercase(),
                    style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Light, fontSize = 28.sp, letterSpacing = 0.5.sp),
                    color = Rv.HeroText, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Box(Modifier.padding(start = 6.dp, top = 6.dp).size(7.dp).clip(CircleShape).background(Rv.HeroMuted))
                Spacer(Modifier.weight(1f))
                // Белый «замок сейфа»: мгновенно закрыть приложение.
                Box(
                    Modifier.size(42.dp).clip(CircleShape).pressable {
                        (context as? com.kartoteka.app.MainActivity)?.closeVault()
                    }.semantics { contentDescription = t("Закрыть сейф") },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size(30.dp).clip(CircleShape)
                            .background(Brush.radialGradient(listOf(Color.White, Color(0xFFE9E4DE)))),
                        contentAlignment = Alignment.Center,
                    ) { Box(Modifier.size(13.dp).clip(CircleShape).border(2.dp, Color(0xFFCFC8C0), CircleShape)) }
                }
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
                        DropdownMenuItem(text = { Text(t("Группы")) }, leadingIcon = { Icon(Icons.Default.Workspaces, null) }, onClick = { menu = false; onGroups() })
                        DropdownMenuItem(text = { Text(t("Статистика")) }, leadingIcon = { Icon(Icons.Default.Insights, null) }, onClick = { menu = false; onStats() })
                    }
                }
            }

            // Заголовок со счётчиком.
            Row(Modifier.padding(start = 22.dp, end = 22.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(t("Люди"), style = MaterialTheme.typography.displayMedium.copy(fontSize = 50.sp, lineHeight = 54.sp, letterSpacing = (-1.8).sp), color = Rv.HeroText)
                Spacer(Modifier.width(12.dp))
                Box(
                    Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.14f))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape).padding(horizontal = 10.dp, vertical = 3.dp),
                ) { Text("${state.total}", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium), color = Rv.HeroText) }
            }
            Text(
                t("Ваш круг. Клиенты, друзья, семья."),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = Rv.HeroText.copy(alpha = 0.92f),
                modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 2.dp),
            )

            // Поиск — всегда под рукой.
            SearchBar(query, onQuery, sort, onSort)

            // Фильтры.
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 14.dp),
            ) {
                item { FilterPill(t("Все"), filter == PeopleFilter.All, count = state.total) { onFilter(PeopleFilter.All) } }
                items(state.groups, key = { it.group.id }) { g ->
                    val sel = filter == PeopleFilter.InGroup(g.group.id)
                    FilterPill(g.group.name, sel, count = g.count, dot = Color(g.group.color)) {
                        onFilter(if (sel) PeopleFilter.All else PeopleFilter.InGroup(g.group.id))
                    }
                }
                item {
                    FilterPill(t("Избранные"), filter == PeopleFilter.Favorites, icon = Icons.Default.StarBorder) {
                        onFilter(if (filter == PeopleFilter.Favorites) PeopleFilter.All else PeopleFilter.Favorites)
                    }
                }
            }

            // Быстрые действия.
            val todayPeople = state.today.mapNotNull { it.person }.distinctBy { it.id }
            val bdSoon = state.birthdays
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
                modifier = Modifier.padding(top = 18.dp),
            ) {
                item {
                    Column(
                        Modifier.width(70.dp).height(108.dp).clip(RoundedCornerShape(22.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(22.dp))
                            .pressable(onClick = onAdd),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Default.Add, null, tint = Rv.HeroText, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.height(6.dp))
                        Text(t("Добавить\nчеловека"), style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium), color = Rv.HeroText, textAlign = TextAlign.Center)
                    }
                }
                item {
                    val p = todayPeople.firstOrNull()
                    QuickCircle(t("Сегодня"), "${state.today.size}", dot = p?.let { categoryColor(it.relation) }, onClick = onCalendar) {
                        if (p != null) Avatar(p, 70.dp) else CircleIcon(Icons.Default.CalendarMonth)
                    }
                }
                item {
                    val p = bdSoon.firstOrNull()?.first?.person
                    QuickCircle(t("Дни рождения"), "${bdSoon.size}", dot = if (p != null) Rv.Lime else null, onClick = { p?.let { onOpen(it.id) } ?: onCalendar() }) {
                        if (p != null) Avatar(p, 70.dp) else CircleIcon(Icons.Default.Redeem)
                    }
                }
                item {
                    QuickCircle(t("Карта"), null, onClick = onMap) {
                        Image(painterResource(R.drawable.hero_mountain), null, contentScale = ContentScale.Crop, alignment = Alignment.CenterEnd, modifier = Modifier.fillMaxSize())
                    }
                }
                if (assistantOn) item {
                    Column(Modifier.width(84.dp).pressable(onClick = onNoa), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(78.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF0B0B0D))
                                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(24.dp)),
                            contentAlignment = Alignment.Center,
                        ) { NoaOrb(Modifier.size(54.dp)) }
                        Spacer(Modifier.height(8.dp))
                        Text(t("Ассистент\nНоа"), style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium), color = Rv.HeroText, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

/** Значок меню: три линии разной длины. */
@Composable
private fun MenuGlyph() {
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.width(26.dp).height(2.dp).clip(CircleShape).background(Rv.HeroText))
        Box(Modifier.width(20.dp).height(2.dp).clip(CircleShape).background(Rv.HeroText))
        Box(Modifier.width(14.dp).height(2.dp).clip(CircleShape).background(Rv.HeroText))
    }
}

@Composable
private fun SearchBar(query: String, onQuery: (String) -> Unit, sort: SortMode, onSort: (SortMode) -> Unit) {
    var sortMenu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp).height(48.dp).clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.38f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
            .padding(start = 18.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Search, null, tint = Rv.HeroText.copy(alpha = 0.85f), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text(t("Поиск: имя, город, заметка…"), style = MaterialTheme.typography.bodyMedium, color = Rv.HeroMuted, maxLines = 1)
            BasicTextField(
                value = query, onValueChange = onQuery, singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Rv.HeroText),
                cursorBrush = SolidColor(Rv.Peach),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = t("Поиск") },
            )
        }
        if (query.isNotEmpty()) {
            Box(Modifier.size(40.dp).clip(CircleShape).clickable { onQuery("") }, contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Close, t("Очистить"), tint = Rv.HeroText)
            }
        }
        Box(Modifier.width(1.dp).height(26.dp).background(Color.White.copy(alpha = 0.18f)))
        Box {
            Box(Modifier.size(40.dp).clip(CircleShape).clickable { sortMenu = true }, contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Tune, t("Сортировка"), tint = Rv.HeroText)
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

/** Фильтр-«таблетка»: выбранный — кремовый, остальные — тёмное стекло; число в отдельной капсуле. */
@Composable
private fun FilterPill(label: String, selected: Boolean, count: Int? = null, dot: Color? = null, icon: ImageVector? = null, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Color(0xFFF6E3D3) else Color.Black.copy(alpha = 0.35f), motion(Motion.MICRO), label = "pillbg")
    val fg by animateColorAsState(if (selected) Rv.Ink else Rv.HeroText, motion(Motion.MICRO), label = "pillfg")
    Row(
        Modifier.height(36.dp).clip(CircleShape).background(bg)
            .border(1.dp, if (selected) Color.Transparent else Color.White.copy(alpha = 0.14f), CircleShape)
            .pressable(onClick = onClick).padding(start = 12.dp, end = if (count != null) 5.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) { Box(Modifier.size(8.dp).clip(CircleShape).background(dot)); Spacer(Modifier.width(7.dp)) }
        if (icon != null) { Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
        Text(label, style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium), color = fg, maxLines = 1)
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.clip(CircleShape).background(if (selected) Color.Black.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.10f))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            ) { Text("$count", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium), color = fg.copy(alpha = 0.85f)) }
        }
    }
}

/** Круг быстрого действия: фото в тонком кольце, цветная точка-статус, подпись и число. */
@Composable
private fun QuickCircle(label: String, value: String?, dot: Color? = null, onClick: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.width(84.dp).pressable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(78.dp)) {
            Box(
                Modifier.fillMaxSize().clip(CircleShape)
                    .border(2.dp, Brush.verticalGradient(listOf(Color(0xFFF2D2B6), Color(0xFF6B5444))), CircleShape)
                    .padding(4.dp).clip(CircleShape).background(Rv.HeroSurface),
                contentAlignment = Alignment.Center,
            ) { content() }
            if (dot != null && dot != Color.Transparent) {
                Box(
                    Modifier.align(Alignment.BottomEnd).offset(x = (-4).dp, y = (-6).dp).size(14.dp).clip(CircleShape)
                        .background(Rv.HeroBg).padding(2.5.dp).clip(CircleShape).background(dot)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium), color = Rv.HeroText, maxLines = 1, textAlign = TextAlign.Center)
        if (value != null) Text(value, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium), color = Rv.HeroText.copy(alpha = 0.85f))
    }
}

@Composable
private fun CircleIcon(icon: ImageVector) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Rv.HeroText, modifier = Modifier.size(26.dp)) }
}

@Composable
private fun LetterHeader(letter: Char) {
    Text(
        letter.toString(),
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium, fontSize = 17.sp),
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
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 10.dp, bottom = 12.dp)) {
            // Фото с точкой категории.
            Box(Modifier.size(58.dp)) {
                Avatar(p, 56.dp, Modifier.align(Alignment.Center))
                if (dot != Color.Transparent) {
                    Box(
                        Modifier.align(Alignment.TopEnd).size(15.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.background).padding(2.5.dp).clip(CircleShape).background(dot)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).padding(top = 2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        p.displayName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, fontSize = 17.sp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        if (p.favorite) Icons.Default.Star else Icons.Default.StarBorder,
                        t("Избранное"),
                        tint = if (p.favorite) Rv.Peach else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp).clip(CircleShape).clickable {
                            scope.launch { app.repository.setFavorite(p.id, !p.favorite) }
                        },
                    )
                }
                val sub = listOf(t(p.relation), p.position.ifBlank { p.company }, p.city).filter { it.isNotBlank() }
                if (hit.matchedIn != null) {
                    Text(hit.matchedIn, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else if (sub.isNotEmpty()) {
                    Text(sub.joinToString(" · "), style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                        RoundAction(Icons.Default.MoreVert, t("Действия"), plain = true) { menu = true }
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
                            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                    }
                    if (thumbs.isNotEmpty()) Spacer(Modifier.width(2.dp))
                    Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (!last) HorizontalDivider(Modifier.padding(start = 18.dp, end = 18.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun RoundAction(icon: ImageVector, desc: String, enabled: Boolean = true, plain: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).alpha(if (enabled) 1f else 0.35f).clip(CircleShape)
            .background(if (plain) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f))
            .then(if (enabled) Modifier.pressable(onClick = onClick) else Modifier)
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp)) }
}

/** Чип-подсказка: сегодня / день рождения / ближайшая запись / когда виделись. */
@Composable
private fun HintChip(h: PersonHint) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val (icon, text, accent) = when (h) {
        is PersonHint.Today -> Triple(Icons.Default.CalendarMonth, t("Сегодня %1\$s", h.time), false)
        is PersonHint.Upcoming -> Triple(
            Icons.Default.CalendarMonth,
            (if (h.date == LocalDate.now().plusDays(1)) t("Завтра") else com.kartoteka.app.i18n.I18n.dayMonth(h.date.dayOfMonth, h.date.monthValue)) + " " + h.time,
            false,
        )
        is PersonHint.Birthday -> Triple(
            Icons.Default.Redeem,
            if (h.days == 0L) t("День рождения сегодня") else t("День рождения %1\$s", com.kartoteka.app.i18n.I18n.dayMonth(h.day, h.month)),
            true,
        )
        is PersonHint.LastMet -> Triple(
            Icons.Default.Schedule,
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
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (accent) Rv.PeachDeep else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium), color = if (accent) accentText else MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
