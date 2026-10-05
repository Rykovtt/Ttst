package com.kartoteka.app.ui.screens

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
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
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
import com.kartoteka.app.ui.animations.appearOnce
import com.kartoteka.app.ui.app
import com.kartoteka.app.ui.components.ContactCard
import com.kartoteka.app.ui.components.EmptyState
import com.kartoteka.app.ui.components.HeaderData
import com.kartoteka.app.ui.components.MountainHeader
import com.kartoteka.app.ui.components.PeopleFilter
import com.kartoteka.app.ui.components.StatusBarOverDark
import com.kartoteka.app.ui.theme.AnimationTokens
import com.kartoteka.app.ui.theme.LocalReducedMotion
import com.kartoteka.app.ui.theme.PeopleDims
import com.kartoteka.app.ui.theme.PeopleType
import com.kartoteka.app.ui.theme.RvColors
import com.kartoteka.app.ui.theme.u
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

/** Однократные вступительные анимации главного экрана — только при первом показе за запуск. */
object HomeIntro { var played = false }

/** Просьба для календаря: открыть с фильтром «Дни рождения». */
object CalendarRequest { var birthdays = false }

data class PeopleState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val total: Int = 0,
    val results: List<ArchiveLogic.SearchHit> = emptyList(),
    val birthdays: List<Pair<PersonFull, Long>> = emptyList(),
    val groups: List<GroupWithCount> = emptyList(),
    val hints: Map<Long, PersonHint> = emptyMap(),
    val today: List<AppointmentFull> = emptyList(),
)

/** MVVM: поиск с задержкой 150 мс, фильтры, подсказки по встречам и дням рождения. */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class PeopleViewModel(private val app: KartotekaApp) : ViewModel() {
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
    private val searchQuery = query.debounce { if (it.isBlank()) 0L else AnimationTokens.SearchDebounce }

    val state = retry.flatMapLatest {
        val base = combine(app.repository.observeAll(), app.repository.observeGroups(), searchQuery, filter, sort) { all, groups, q, f, s ->
            val filtered = when (f) {
                PeopleFilter.All -> all
                PeopleFilter.Favorites -> all.filter { it.person.favorite }
                is PeopleFilter.InGroup -> all.filter { pf -> pf.groups.any { it.id == f.id } }
            }
            PeopleState(
                loading = false, total = all.size,
                results = ArchiveLogic.sort(ArchiveLogic.search(filtered, q), s),
                birthdays = ArchiveLogic.upcomingBirthdays(all, 30), groups = groups,
            ) to all
        }
        combine(base, appts) { (st, all), list ->
            val now = LocalDateTime.now()
            val byPerson = list.groupBy { it.appointment.personId }
            st.copy(
                hints = all.mapNotNull { pf -> PeopleHints.hint(pf, byPerson[pf.person.id].orEmpty(), now)?.let { pf.person.id to it } }.toMap(),
                today = list.filter {
                    it.appointment.appointmentStatus != AppointmentStatus.CANCELLED &&
                        AppointmentLogic.zoned(it.appointment.start).toLocalDate() == now.toLocalDate()
                }.sortedBy { it.appointment.start },
            )
        }.catch { emit(PeopleState(loading = false, error = true)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PeopleState())

    fun setSort(mode: SortMode) = app.settings.setSortMode(mode)
    fun retry() { retry.value++ }
}

/** Главный экран «Люди». */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PeopleScreen(
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
    val dark = isSystemInDarkTheme()
    val bg = if (dark) Color(0xFF0E0E10) else RvColors.Background
    val reduced = LocalReducedMotion.current
    val vm: PeopleViewModel = viewModel { PeopleViewModel(app) }
    val state by vm.state.collectAsState()
    val query by vm.query.collectAsState()
    val filter by vm.filter.collectAsState()
    val sort by vm.sort.collectAsState()
    val showCounts by app.settings.homeCounts.value.collectAsState()
    val listState = rememberLazyListState()
    var filtersOpen by remember { mutableStateOf(false) }
    val seen = remember { mutableSetOf<Any>() }
    val intro = remember { !HomeIntro.played && !reduced }
    LaunchedEffect(Unit) { delay(1200); HomeIntro.played = true }

    // Значки статус-бара светлые, пока под ними тёмная шапка.
    val density = LocalDensity.current
    var heroHeight by remember { mutableIntStateOf(0) }
    val statusPx = WindowInsets.statusBars.getTop(density)
    val heroUnder by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < (heroHeight - statusPx).coerceAtLeast(1) } }
    StatusBarOverDark(heroUnder)

    // Смена фильтра — короткий переход прозрачности списка.
    val listFade = remember { Animatable(1f) }
    var firstFilter by remember { mutableStateOf(true) }
    LaunchedEffect(filter, sort) {
        if (firstFilter) { firstFilter = false; return@LaunchedEffect }
        if (!reduced) { listFade.snapTo(0.2f); listFade.animateTo(1f, tween(AnimationTokens.Category, easing = AnimationTokens.Enter)) }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(bg)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().align(Alignment.TopCenter).widthIn(max = 760.dp),
            contentPadding = PaddingValues(bottom = 40.dp),
        ) {
            item(key = "hero") {
                Box(Modifier.onSizeChanged { heroHeight = it.height }) {
                    MountainHeader(
                        data = HeaderData(state.total, state.groups, state.today, state.birthdays),
                        query = query, onQuery = { vm.query.value = it },
                        filter = filter, onFilter = { vm.filter.value = it },
                        showCounts = showCounts, filtersOpen = filtersOpen, onOpenFilters = { filtersOpen = true },
                        intro = intro,
                        scrollOffset = { if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset else 0 },
                        onAdd = onAdd, onToday = onCalendar,
                        onBirthdays = { CalendarRequest.birthdays = true; onCalendar() },
                        onMap = onMap, onNoa = onNoa, onGroups = onGroups, onStats = onStats, onSettings = onSettings,
                    )
                }
            }

            when {
                state.loading -> items(5) { SkeletonRow(dark) }
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
                val firstVisible = { listState.firstVisibleItemIndex }
                val grouped = sort == SortMode.NAME && query.isBlank()
                if (grouped) {
                    var order = 0
                    state.results.groupBy { it.person.person.sortKey.firstOrNull()?.uppercaseChar() ?: '#' }.forEach { (letter, hits) ->
                        stickyHeader(key = "h_$letter") { LetterHeader(letter, bg, dark) }
                        hits.forEachIndexed { i, hit ->
                            val o = order++
                            item(key = hit.person.person.id) {
                                Box(Modifier.animateItem().appearOnce(hit.person.person.id, o - firstVisible(), seen).graphicsLayer { alpha = listFade.value }) {
                                    ContactCard(
                                        hit, state.hints[hit.person.person.id], query, last = i == hits.lastIndex, dark = dark, background = bg,
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
                                t("Найдено: %1\$s", state.results.size), style = PeopleType.contactMeta,
                                color = if (dark) Color(0xFF9A979E) else RvColors.TextSecondary,
                                modifier = Modifier.padding(start = u(PeopleDims.LetterLeft), top = u(PeopleDims.LetterTop)),
                            )
                        }
                    }
                    itemsIndexed(state.results, key = { _, it -> it.person.person.id }) { i, hit ->
                        Box(Modifier.animateItem().appearOnce(hit.person.person.id, i - firstVisible(), seen).graphicsLayer { alpha = listFade.value }) {
                            ContactCard(
                                hit, state.hints[hit.person.person.id], query, last = i == state.results.lastIndex, dark = dark, background = bg,
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
                Row(
                    Modifier.fillMaxWidth().clickable { vm.filter.value = if (filter == PeopleFilter.Favorites) PeopleFilter.All else PeopleFilter.Favorites }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Star, null, tint = RvColors.StarActive)
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

/** Буква алфавитной группы: 18 sp Bold, отступы 36 / 22 / 12 (ед.). */
@Composable
private fun LetterHeader(letter: Char, bg: Color, dark: Boolean) {
    Text(
        letter.toString(), style = PeopleType.letter, color = if (dark) Color(0xFFE6E2DC) else RvColors.Letter,
        modifier = Modifier.fillMaxWidth().background(bg)
            .padding(start = u(PeopleDims.LetterLeft), end = u(PeopleDims.LetterLeft), top = u(PeopleDims.LetterTop), bottom = u(PeopleDims.LetterBottom)),
    )
}

/** Плавающая буква справа — только при быстрой прокрутке длинного списка. */
@Composable
private fun FastLetter(listState: LazyListState, state: PeopleState, enabled: Boolean, modifier: Modifier) {
    if (!enabled || state.results.size < 30) return
    val letters = remember(state.results) { state.results.associate { it.person.person.id to (it.person.person.sortKey.firstOrNull()?.uppercaseChar() ?: '#') } }
    val current by remember(letters) {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.firstNotNullOfOrNull { info ->
                when (val k = info.key) { is Long -> letters[k]; is String -> if (k.startsWith("h_")) k.removePrefix("h_").firstOrNull() else null; else -> null }
            }
        }
    }
    var fast by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        var last = listState.firstVisibleItemIndex
        while (true) {
            delay(120)
            val idx = listState.firstVisibleItemIndex
            val speed = kotlin.math.abs(idx - last); last = idx
            if (listState.isScrollInProgress && speed >= 3) fast = true
            else if (!listState.isScrollInProgress && fast) { delay(600); fast = false }
        }
    }
    AnimatedVisibility(fast && current != null, modifier = modifier.padding(end = 14.dp), enter = fadeIn(tween(150)) + scaleIn(tween(150), 0.8f), exit = fadeOut(tween(250)) + scaleOut(tween(250), 0.9f)) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(RvColors.DarkSurface), contentAlignment = Alignment.Center) {
            Text((current ?: ' ').toString(), style = PeopleType.letter, color = RvColors.WarmLight)
        }
    }
}

/** Скелет строки при загрузке. */
@Composable
private fun SkeletonRow(dark: Boolean) {
    val reduced = LocalReducedMotion.current
    val a = if (reduced) null else rememberInfiniteTransition(label = "skeleton").animateFloat(0.55f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "skA")
    val c1 = if (dark) Color(0xFF222225) else RvColors.PillBg
    Row(Modifier.fillMaxWidth().padding(horizontal = u(PeopleDims.CardPad), vertical = u(26)).graphicsLayer { alpha = a?.value ?: 1f }, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(u(PeopleDims.ContactAvatar)).clip(CircleShape).background(c1))
        Spacer(Modifier.width(u(22)))
        Column(Modifier.weight(1f)) {
            Box(Modifier.fillMaxWidth(0.6f).height(u(26)).clip(RoundedCornerShape(8.dp)).background(c1))
            Spacer(Modifier.height(u(14)))
            Box(Modifier.fillMaxWidth(0.4f).height(u(18)).clip(RoundedCornerShape(6.dp)).background(c1))
        }
    }
}
