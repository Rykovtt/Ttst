package com.kartoteka.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kartoteka.app.KartotekaApp
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
import com.kartoteka.app.ui.app
import com.kartoteka.app.ui.components.Avatar
import com.kartoteka.app.ui.components.ColorDot
import com.kartoteka.app.ui.components.EmptyState
import com.kartoteka.app.ui.components.StatusBarOverDark
import com.kartoteka.app.ui.components.categoryColor
import com.kartoteka.app.ui.theme.Brand
import com.kartoteka.app.ui.theme.HomeDims
import com.kartoteka.app.ui.theme.Inter
import com.kartoteka.app.ui.theme.LocalReducedMotion
import com.kartoteka.app.ui.theme.Motion
import com.kartoteka.app.ui.theme.Rv
import com.kartoteka.app.ui.theme.homePalette
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.LocalDateTime

sealed interface PeopleFilter {
    data object All : PeopleFilter
    data object Favorites : PeopleFilter
    data class InGroup(val id: Long) : PeopleFilter
}

data class HomeState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val total: Int = 0,
    val results: List<ArchiveLogic.SearchHit> = emptyList(),
    val birthdays: List<Pair<PersonFull, Long>> = emptyList(),
    val groups: List<GroupWithCount> = emptyList(),
    /** Подсказка под именем для каждого человека. */
    val hints: Map<Long, PersonHint> = emptyMap(),
    /** Записи на сегодня (для круга «Сегодня»). */
    val today: List<AppointmentFull> = emptyList(),
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class HomeViewModel(private val app: KartotekaApp) : ViewModel() {
    val query = MutableStateFlow("")
    val filter = MutableStateFlow<PeopleFilter>(PeopleFilter.All)
    val sort = app.settings.sortMode
    private val retry = MutableStateFlow(0)

    private val appts = run {
        val d = LocalDate.now()
        app.repository.observeAppointments(
            AppointmentLogic.millis(d.minusYears(2).atStartOfDay()),
            AppointmentLogic.millis(d.plusDays(30).atStartOfDay()),
        )
    }

    // Поиск с задержкой 300 мс; очистка — сразу.
    private val searchQuery = query.debounce { if (it.isBlank()) 0L else 300L }

    val state = retry.flatMapLatest {
        val base = combine(
            app.repository.observeAll(), app.repository.observeGroups(), searchQuery, filter, sort,
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
        combine(base, appts) { (st, all), list ->
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
        }.catch { emit(HomeState(loading = false, error = true)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeState())

    fun setSort(mode: SortMode) = app.settings.setSortMode(mode)
    fun retry() { retry.value++ }
}

/** Просьба для календаря: открыть с фильтром «Дни рождения». */
object CalendarRequest {
    var birthdays = false
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
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
    onEdit: (Long) -> Unit = {},
    onOpenPhoto: (Long, Int) -> Unit = { _, _ -> },
    onSettings: () -> Unit = {},
) {
    val app = app()
    val c = homePalette()
    val reduced = LocalReducedMotion.current
    val vm: HomeViewModel = viewModel { HomeViewModel(app) }
    val state by vm.state.collectAsState()
    val query by vm.query.collectAsState()
    val filter by vm.filter.collectAsState()
    val sort by vm.sort.collectAsState()
    val showCounts by app.settings.homeCounts.value.collectAsState()
    val listState = rememberLazyListState()
    var filtersOpen by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    // Значки статус-бара: светлые, пока под ним тёмная шапка.
    var heroHeight by remember { mutableIntStateOf(0) }
    val statusPx = WindowInsets.statusBars.getTop(density)
    val heroUnder by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < (heroHeight - statusPx).coerceAtLeast(1) }
    }
    StatusBarOverDark(heroUnder)

    // Вступление: элементы списка появляются по очереди только при первом показе.
    val intro = remember { !HomeIntro.played && !reduced }
    LaunchedEffect(Unit) { delay(1400); HomeIntro.played = true }

    // Смена фильтра — короткий переход прозрачности списка.
    val listFade = remember { Animatable(1f) }
    var firstFilter by remember { mutableStateOf(true) }
    LaunchedEffect(filter, sort) {
        if (firstFilter) { firstFilter = false; return@LaunchedEffect }
        if (!reduced) { listFade.snapTo(0.2f); listFade.animateTo(1f, tween(220)) }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(c.content)) {
        val narrow = maxWidth < 360.dp
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().align(Alignment.TopCenter).widthIn(max = 760.dp),
            contentPadding = PaddingValues(bottom = 40.dp),
        ) {
            item(key = "hero") {
                Box(Modifier.onSizeChanged { heroHeight = it.height }) {
                    PeopleHeader(
                        state = state, query = query, onQuery = { vm.query.value = it },
                        filter = filter, onFilter = { vm.filter.value = it },
                        showCounts = showCounts, filtersOpen = filtersOpen, onOpenFilters = { filtersOpen = true },
                        scrollOffset = { if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset else 0 },
                        onOpen = onOpen, onAdd = onAdd, onGroups = onGroups, onNoa = onNoa, onMap = onMap, onStats = onStats,
                        onToday = onCalendar,
                        onBirthdays = { CalendarRequest.birthdays = true; onCalendar() },
                        onSettings = onSettings,
                    )
                }
            }

            when {
                state.loading -> items(5) { SkeletonPulse() }
                state.error -> item(key = "error") {
                    EmptyState(Icons.Default.ErrorOutline, t("Не удалось загрузить данные"), t("Попробуйте ещё раз. Данные на устройстве не пострадали.")) {
                        Button(onClick = vm::retry) { Text(t("Повторить")) }
                    }
                }
                state.total == 0 -> item(key = "empty") {
                    EmptyState(Icons.Default.Contacts, t("Пока никого нет"), t("Добавьте первого человека вручную или импортируйте контакты из телефона.")) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onAdd) { Text(t("Добавить первого человека")) }
                            FilledTonalButton(onClick = onImport) { Text(t("Импорт")) }
                        }
                    }
                }
                state.results.isEmpty() -> item(key = "nothing") {
                    EmptyState(Icons.Default.SearchOff, t("Ничего не найдено"), t("Попробуйте изменить запрос или фильтр.")) {
                        FilledTonalButton(onClick = { vm.query.value = ""; vm.filter.value = PeopleFilter.All }) { Text(t("Очистить запрос")) }
                    }
                }
            }

            if (!state.loading && !state.error) {
                val grouped = sort == SortMode.NAME && query.isBlank()
                var index = 0
                if (grouped) {
                    state.results.groupBy { it.person.person.sortKey.firstOrNull()?.uppercaseChar() ?: '#' }
                        .forEach { (letter, hits) ->
                            stickyHeader(key = "h_${letter}") { LetterHeader(letter) }
                            hits.forEachIndexed { i, hit ->
                                val order = index++
                                item(key = hit.person.person.id) {
                                    IntroItem(intro, order, { listFade.value }, Modifier.animateItem()) {
                                        PersonListItem(
                                            hit, state.hints[hit.person.person.id], query, last = i == hits.lastIndex, narrow = narrow,
                                            onOpen = onOpen, onEdit = onEdit, onNewAppointment = onNewAppointment, onOpenPhoto = onOpenPhoto,
                                        )
                                    }
                                }
                            }
                        }
                } else {
                    if (state.results.isNotEmpty() && query.isNotBlank()) {
                        item(key = "count") {
                            Text(
                                t("Найдено: %1\$s", state.results.size),
                                style = TextStyle(fontFamily = Inter, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                                color = c.textSecondary,
                                modifier = Modifier.padding(start = HomeDims.sidePad + 4.dp, top = 18.dp, bottom = 4.dp),
                            )
                        }
                    }
                    itemsIndexed(state.results, key = { _, it -> it.person.person.id }) { i, hit ->
                        IntroItem(intro, i, { listFade.value }, Modifier.animateItem()) {
                            PersonListItem(
                                hit, state.hints[hit.person.person.id], query, last = i == state.results.lastIndex, narrow = narrow,
                                onOpen = onOpen, onEdit = onEdit, onNewAppointment = onNewAppointment, onOpenPhoto = onOpenPhoto,
                            )
                        }
                    }
                }
            }
        }

        FastLetter(listState, state, sort == SortMode.NAME && query.isBlank(), Modifier.align(Alignment.CenterEnd))
    }

    if (filtersOpen) {
        ModalBottomSheet(onDismissRequest = { filtersOpen = false }) {
            Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
                Text(t("Параметры поиска"), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.size(14.dp))
                Text(t("Сортировка"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SortMode.entries.forEach { m ->
                    Row(Modifier.fillMaxWidth().clip(CircleShape).clickable { vm.setSort(m) }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = m == sort, onClick = { vm.setSort(m) })
                        Text(m.title, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Spacer(Modifier.size(10.dp))
                Row(Modifier.fillMaxWidth().clickable { vm.filter.value = if (filter == PeopleFilter.Favorites) PeopleFilter.All else PeopleFilter.Favorites }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, null, tint = Brand.StarActive)
                    Spacer(Modifier.width(12.dp))
                    Text(t("Только избранные"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = filter == PeopleFilter.Favorites, onCheckedChange = { vm.filter.value = if (it) PeopleFilter.Favorites else PeopleFilter.All })
                }
                Row(Modifier.fillMaxWidth().clickable { app.settings.homeCounts.set(!showCounts) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(t("Показывать количество в фильтрах"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = showCounts, onCheckedChange = { app.settings.homeCounts.set(it) })
                }
            }
        }
    }
}

/** Последовательное появление строки: 80 мс + 45 мс на каждую следующую; затем — только мягкая смена фильтра. */
@Composable
private fun IntroItem(intro: Boolean, order: Int, fade: () -> Float, modifier: Modifier, content: @Composable () -> Unit) {
    val play = intro && order < 12
    val a = remember { Animatable(if (play) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (play) { delay(80L + order * 45L); a.animateTo(1f, tween(300, easing = Motion.Ease)) }
    }
    val density = LocalDensity.current
    Box(modifier.graphicsLayer {
        alpha = a.value * fade()
        translationY = (1f - a.value) * with(density) { 14.dp.toPx() }
    }) { content() }
}

@Composable
private fun LetterHeader(letter: Char) {
    val c = homePalette()
    Text(
        letter.toString(),
        style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
        color = c.letter,
        modifier = Modifier.fillMaxWidth().background(c.content).padding(start = HomeDims.sidePad + 4.dp, end = HomeDims.sidePad, top = 18.dp, bottom = 4.dp),
    )
}

/** Плавающая буква справа — только при быстрой прокрутке длинного списка. */
@Composable
private fun FastLetter(listState: androidx.compose.foundation.lazy.LazyListState, state: HomeState, enabled: Boolean, modifier: Modifier) {
    if (!enabled || state.results.size < 30) return
    val letters = remember(state.results) { state.results.associate { it.person.person.id to (it.person.person.sortKey.firstOrNull()?.uppercaseChar() ?: '#') } }
    val current by remember(letters) {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.firstNotNullOfOrNull { info ->
                when (val k = info.key) { is Long -> letters[k]; is String -> k.removePrefix("h_").firstOrNull()?.takeIf { k.startsWith("h_") }; else -> null }
            }
        }
    }
    var fast by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        var lastIndex = listState.firstVisibleItemIndex
        while (true) {
            delay(120)
            val idx = listState.firstVisibleItemIndex
            val speed = kotlin.math.abs(idx - lastIndex)
            lastIndex = idx
            if (listState.isScrollInProgress && speed >= 3) fast = true
            else if (!listState.isScrollInProgress && fast) { delay(600); fast = false }
        }
    }
    AnimatedVisibility(fast && current != null, modifier = modifier.padding(end = 14.dp), enter = fadeIn(tween(150)) + scaleIn(tween(150), 0.8f), exit = fadeOut(tween(250)) + scaleOut(tween(250), 0.9f)) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(Brand.Panel), contentAlignment = Alignment.Center) {
            Text((current ?: ' ').toString(), style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 22.sp), color = Brand.Milk)
        }
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

/** Скелет строки с мягкой пульсацией прозрачности. */
@Composable
private fun SkeletonPulse() {
    val reduced = LocalReducedMotion.current
    val a = if (reduced) null else rememberInfiniteTransition(label = "skeleton")
        .animateFloat(0.55f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "skeletonA")
    PersonSkeleton(alpha = { a?.value ?: 1f })
}
