package com.kartoteka.app.ui.calendar

import com.kartoteka.app.i18n.t

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.draw.alpha
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.itemsIndexed
import com.kartoteka.app.ui.components.CategoryTag
import com.kartoteka.app.ui.components.FilterChip
import com.kartoteka.app.ui.components.ScreenTitle
import com.kartoteka.app.ui.components.categoryColor
import com.kartoteka.app.ui.components.pressable
import com.kartoteka.app.ui.theme.Motion
import com.kartoteka.app.ui.theme.NumberStyle
import com.kartoteka.app.ui.theme.Rv
import com.kartoteka.app.ui.theme.motion
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kartoteka.app.KartotekaApp
import com.kartoteka.app.data.AppointmentFull
import com.kartoteka.app.data.AppointmentLogic
import com.kartoteka.app.data.AppointmentStatus
import com.kartoteka.app.data.NotifyChannel
import com.kartoteka.app.data.ReminderTarget
import com.kartoteka.app.ui.app
import com.kartoteka.app.ui.components.Avatar
import com.kartoteka.app.ui.components.EmptyState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(app: KartotekaApp) : ViewModel() {
    val month = MutableStateFlow(YearMonth.now())
    val listMode = MutableStateFlow(false)

    /** Все записи видимой сетки месяца (с захватом соседних недель). */
    val monthAppointments = month.flatMapLatest { ym ->
        val (from, to) = gridRange(ym)
        app.repository.observeAppointments(AppointmentLogic.millis(from.atStartOfDay()), AppointmentLogic.millis(to.plusDays(1).atStartOfDay()))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Ближайшие записи для режима «Список» (60 дней вперёд). */
    val upcoming = app.repository.observeAppointments(
        AppointmentLogic.millis(LocalDate.now().atStartOfDay()),
        AppointmentLogic.millis(LocalDate.now().plusDays(60).atStartOfDay()),
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    companion object {
        fun gridRange(ym: YearMonth): Pair<LocalDate, LocalDate> {
            val first = ym.atDay(1)
            val start = first.minusDays((first.dayOfWeek.value - 1).toLong())
            return start to start.plusDays(41)
        }
    }
}

private const val PAGES = 2400
private const val MID = PAGES / 2
private val ru: Locale get() = com.kartoteka.app.i18n.I18n.locale

private enum class CalFilter { ALL, APPOINTMENTS, BIRTHDAYS }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CalendarScreen(onNew: (LocalDate) -> Unit, onOpen: (Long) -> Unit, onOpenPerson: (Long) -> Unit = {}) {
    val app = app()
    val vm: CalendarViewModel = viewModel { CalendarViewModel(app) }
    val listMode by vm.listMode.collectAsState()
    var weekMode by rememberSaveable { mutableStateOf(true) }
    var filter by rememberSaveable { mutableStateOf(CalFilter.ALL) }
    val appts by vm.monthAppointments.collectAsState()
    val upcoming by vm.upcoming.collectAsState()
    val people by app.repository.observeAll().collectAsState(initial = emptyList())
    var selectedEpoch by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    val selected = LocalDate.ofEpochDay(selectedEpoch)
    val today = LocalDate.now()
    val base = YearMonth.now()
    val pager = rememberPagerState(initialPage = MID + (YearMonth.from(selected).let { (it.year - base.year) * 12 + it.monthValue - base.monthValue })) { PAGES }
    val baseWeek = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val weekPager = rememberPagerState(initialPage = MID + ((selected.toEpochDay() - baseWeek.toEpochDay()).let { Math.floorDiv(it, 7L) }).toInt()) { PAGES }
    val scope = rememberCoroutineScope()
    val shownMonth = if (weekMode) YearMonth.from(baseWeek.plusWeeks((weekPager.currentPage - MID).toLong()).plusDays(3)) else base.plusMonths((pager.currentPage - MID).toLong())
    LaunchedEffect(shownMonth, selected, weekMode) { vm.month.value = if (weekMode) YearMonth.from(selected) else shownMonth }

    // Напоминания о записях приходят уведомлениями — попросим разрешение один раз.
    val notifPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) {}
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    val byDay = appts.groupBy { AppointmentLogic.zoned(it.appointment.start).toLocalDate() }
    fun birthdaysOn(d: LocalDate) = people.filter { it.person.birthDay == d.dayOfMonth && it.person.birthMonth == d.monthValue }

    fun goToday() {
        selectedEpoch = today.toEpochDay()
        scope.launch { if (weekMode) weekPager.animateScrollToPage(MID) else pager.animateScrollToPage(MID) }
    }

    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentPadding = PaddingValues(bottom = 40.dp)) {
        item(key = "header") {
            val todayCount = byDay[today].orEmpty().count { it.appointment.appointmentStatus != AppointmentStatus.CANCELLED }
            ScreenTitle(
                t("Календарь"),
                subtitle = if (todayCount == 0) t("Сегодня записей нет") else t("Сегодня %1\$s %2\$s", todayCount, com.kartoteka.app.data.ArchiveLogic.plural(todayCount.toLong(), "запись", "записи", "записей")),
            ) {
                IconButton(onClick = ::goToday) { Icon(Icons.Default.Today, t("Сегодня")) }
                Box(
                    Modifier.padding(start = 4.dp, end = 6.dp).size(44.dp).clip(CircleShape)
                        .border(1.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        .pressable { onNew(if (listMode) today else selected) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Add, t("Записать")) }
            }
        }
        item(key = "filters") {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(filter == CalFilter.ALL, { filter = CalFilter.ALL }, label = { Text(t("Все")) })
                FilterChip(filter == CalFilter.APPOINTMENTS, { filter = CalFilter.APPOINTMENTS }, label = { Text(t("Записи")) })
                FilterChip(filter == CalFilter.BIRTHDAYS, { filter = CalFilter.BIRTHDAYS }, label = { Text(t("Дни рождения")) })
            }
        }
        item(key = "mode") {
            com.kartoteka.app.ui.components.Segmented(
                options = listOf(t("Неделя"), t("Месяц"), t("Список")),
                selected = if (listMode) 2 else if (weekMode) 0 else 1,
                onSelect = {
                    vm.listMode.value = it == 2
                    if (it != 2) weekMode = it == 0
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        if (listMode) {
            val grouped = upcoming.filter { it.appointment.appointmentStatus != AppointmentStatus.CANCELLED }
                .groupBy { AppointmentLogic.zoned(it.appointment.start).toLocalDate() }
            if (grouped.isEmpty()) {
                item { EmptyState(Icons.Default.CalendarMonth, t("Нет ближайших записей"), t("Нажмите «Записать», чтобы запланировать встречу с человеком.")) }
            }
            grouped.forEach { (date, list) ->
                item(key = "d_${date}") { DayHeader(date) }
                itemsIndexed(list, key = { _, it -> it.appointment.id }) { i, it ->
                    TimelineItem(it, first = i == 0, last = i == list.lastIndex, onClick = { onOpen(it.appointment.id) })
                }
            }
        } else {
            item(key = "grid") {
                Column(Modifier.animateContentSize(motion(Motion.EMPHASIZED))) {
                    Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 8.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            shownMonth.month.getDisplayName(TextStyle.FULL_STANDALONE, ru).replaceFirstChar { it.uppercase() } + " " + shownMonth.year,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { scope.launch { if (weekMode) weekPager.animateScrollToPage(weekPager.currentPage - 1) else pager.animateScrollToPage(pager.currentPage - 1) } }) { Icon(Icons.Default.ChevronLeft, t("Назад")) }
                        IconButton(onClick = { scope.launch { if (weekMode) weekPager.animateScrollToPage(weekPager.currentPage + 1) else pager.animateScrollToPage(pager.currentPage + 1) } }) { Icon(Icons.Default.ChevronRight, t("Вперёд")) }
                    }
                    if (weekMode) {
                        HorizontalPager(state = weekPager, modifier = Modifier.fillMaxWidth()) { page ->
                            val start = baseWeek.plusWeeks((page - MID).toLong())
                            WeekStrip(start, selected, byDay, ::birthdaysOn) { selectedEpoch = it.toEpochDay() }
                        }
                    } else {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                            DayOfWeek.entries.forEach { d ->
                                Text(
                                    d.getDisplayName(TextStyle.SHORT_STANDALONE, ru).replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (d.value >= 6) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth()) { page ->
                            MonthGrid(base.plusMonths((page - MID).toLong()), selected, byDay) { selectedEpoch = it.toEpochDay() }
                        }
                    }
                }
            }

            // Выбранный день, а в режиме недели — ещё и следующий.
            val days = if (weekMode) listOf(selected, selected.plusDays(1)) else listOf(selected)
            days.forEachIndexed { di, day ->
                val dayAppts = if (filter == CalFilter.BIRTHDAYS) emptyList()
                    else byDay[day].orEmpty().sortedBy { it.appointment.start }
                val bds = if (filter == CalFilter.APPOINTMENTS) emptyList() else birthdaysOn(day)
                item(key = "day_${day}") { DayTitle(day) }
                if (dayAppts.isEmpty() && bds.isEmpty()) {
                    item(key = "free_${day}") {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(t("Свободный день"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            if (di == 0) FilledTonalButton(onClick = { onNew(day) }) { Text(t("Записать на этот день")) }
                        }
                    }
                }
                items(bds, key = { "bd_${day}_${it.person.id}" }) { pf ->
                    BirthdayItem(pf, onClick = { onOpenPerson(pf.person.id) })
                }
                itemsIndexed(dayAppts, key = { _, it -> it.appointment.id }) { i, it ->
                    TimelineItem(it, first = i == 0, last = i == dayAppts.lastIndex, onClick = { onOpen(it.appointment.id) })
                }
            }
        }
    }
}

/** Лента недели: сегодня — тонкая рамка, выбранный день — персиковая «таблетка». */
@Composable
private fun WeekStrip(start: LocalDate, selected: LocalDate, byDay: Map<LocalDate, List<AppointmentFull>>, birthdays: (LocalDate) -> List<com.kartoteka.app.data.PersonFull>, onSelect: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (i in 0 until 7) {
            val d = start.plusDays(i.toLong())
            val sel = d == selected
            val bg by animateColorAsState(if (sel) Rv.Peach else androidx.compose.ui.graphics.Color.Transparent, motion(Motion.MICRO), label = "wbg")
            val count = byDay[d].orEmpty().count { it.appointment.appointmentStatus != AppointmentStatus.CANCELLED }
            val hasBd = birthdays(d).isNotEmpty()
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(bg)
                    .then(if (d == today && !sel) Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp)) else Modifier)
                    .pressable { onSelect(d) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    d.dayOfWeek.getDisplayName(TextStyle.SHORT_STANDALONE, ru).replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (sel) Rv.Ink.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text("${d.dayOfMonth}", style = NumberStyle.copy(fontSize = 20.sp), color = if (sel) Rv.Ink else MaterialTheme.colorScheme.onSurface)
                Row(Modifier.height(8.dp).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (hasBd) Box(Modifier.size(4.dp).clip(CircleShape).background(if (sel) Rv.Ink else Rv.PeachDeep))
                    repeat(minOf(count, 3)) { Box(Modifier.size(4.dp).clip(CircleShape).background(if (sel) Rv.Ink else Rv.Lavender)) }
                }
            }
        }
    }
}

@Composable
private fun MonthGrid(month: YearMonth, selected: LocalDate, byDay: Map<LocalDate, List<AppointmentFull>>, onSelect: (LocalDate) -> Unit) {
    val (start, _) = CalendarViewModel.gridRange(month)
    val today = LocalDate.now()
    Column(Modifier.padding(horizontal = 12.dp)) {
        for (w in 0 until 6) {
            Row(Modifier.fillMaxWidth()) {
                for (d in 0 until 7) {
                    val date = start.plusDays((w * 7 + d).toLong())
                    val inMonth = date.month == month.month
                    val isSel = date == selected
                    val count = byDay[date].orEmpty().count { it.appointment.appointmentStatus != AppointmentStatus.CANCELLED }
                    Box(
                        Modifier.weight(1f).aspectRatio(1f).padding(3.dp).clip(RoundedCornerShape(14.dp))
                            .background(if (isSel) Rv.Peach else androidx.compose.ui.graphics.Color.Transparent)
                            .then(if (date == today && !isSel) Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp)) else Modifier)
                            .clickable { onSelect(date) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${date.dayOfMonth}",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (date == today || isSel) FontWeight.ExtraBold else FontWeight.Medium,
                                color = when {
                                    isSel -> Rv.Ink
                                    !inMonth -> MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.height(6.dp)) {
                                repeat(minOf(count, 3)) {
                                    Box(Modifier.size(4.dp).clip(CircleShape).background(if (isSel) Rv.Ink else Rv.Lavender))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Заголовок дня: «Сегодня» крупно, дата справа. */
@Composable
private fun DayTitle(date: LocalDate) {
    val today = LocalDate.now()
    val name = when (date) {
        today -> t("Сегодня")
        today.plusDays(1) -> t("Завтра")
        today.minusDays(1) -> t("Вчера")
        else -> AppointmentLogic.weekday(date).replaceFirstChar { it.uppercase() }
    }
    Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
        Text(name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        Text(
            com.kartoteka.app.i18n.I18n.dayMonth(date.dayOfMonth, date.monthValue) + ", " + AppointmentLogic.weekday(date),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DayHeader(date: LocalDate) = DayTitle(date)

/** День рождения в расписании дня. */
@Composable
private fun BirthdayItem(pf: com.kartoteka.app.data.PersonFull, onClick: () -> Unit) {
    val p = pf.person
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(56.dp), contentAlignment = Alignment.CenterStart) {
            Text("🎂", style = MaterialTheme.typography.titleLarge)
        }
        Row(
            Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(Rv.PeachSoft.copy(alpha = if (androidx.compose.foundation.isSystemInDarkTheme()) 0.14f else 1f))
                .pressable(onClick = onClick).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(p, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(t("День рождения"), com.kartoteka.app.data.ArchiveLogic.turningAge(p)?.let { com.kartoteka.app.data.ArchiveLogic.ageString(it) }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Запись на вертикальной временной шкале: время — крупно слева, узел на линии, карточка с человеком. */
@Composable
private fun TimelineItem(af: AppointmentFull, first: Boolean, last: Boolean, onClick: () -> Unit) {
    val a = af.appointment
    val p = af.person ?: return
    val start = AppointmentLogic.zoned(a.start)
    val end = AppointmentLogic.zoned(a.end)
    val cancelled = a.appointmentStatus == AppointmentStatus.CANCELLED
    val now = System.currentTimeMillis()
    val past = a.end < now
    val live = a.start <= now && now < a.end && !cancelled
    val (tileBg, tileFg) = channelColors(a.notifyChannel)
    val line = MaterialTheme.colorScheme.outlineVariant
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(start = 16.dp, end = 16.dp)) {
        Column(Modifier.width(52.dp).padding(top = 14.dp)) {
            Text(AppointmentLogic.timeText(start), style = NumberStyle.copy(fontSize = 16.sp), color = if (past) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface)
            Text(AppointmentLogic.timeText(end), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
        Box(Modifier.width(18.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.width(2.dp).fillMaxHeight().padding(top = if (first) 18.dp else 0.dp).background(if (first && last) androidx.compose.ui.graphics.Color.Transparent else line))
            Box(
                Modifier.padding(top = 18.dp).size(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.background).padding(2.dp)
                    .clip(CircleShape).background(if (live) Rv.Lime else if (past || cancelled) MaterialTheme.colorScheme.outline else Rv.PeachDeep)
            )
        }
        Spacer(Modifier.width(8.dp))
        Row(
            Modifier.weight(1f).padding(vertical = 5.dp).alpha(if (cancelled || past) 0.6f else 1f).clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .pressable(onClick = onClick)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(p, 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    a.title.ifBlank { t("Встреча") },
                    style = MaterialTheme.typography.titleSmall,
                    textDecoration = if (cancelled) TextDecoration.LineThrough else null,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                val sub = listOfNotNull(p.displayName, a.place.takeIf { it.isNotBlank() }).joinToString(" · ")
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val pending = af.reminders.count { it.sentAt == null }
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (p.relation.isNotBlank()) CategoryTag(t(p.relation), categoryColor(p.relation).takeIf { it != androidx.compose.ui.graphics.Color.Transparent } ?: Rv.Lavender)
                    when (a.appointmentStatus) {
                        AppointmentStatus.CANCELLED -> CategoryTag(t("отменено"), Rv.Coral)
                        AppointmentStatus.DONE -> CategoryTag(t("состоялось"), Rv.Lime)
                        AppointmentStatus.PLANNED -> if (live) CategoryTag(t("сейчас"), Rv.Lime) else if (pending > 0) CategoryTag("🔔 ${pending}", Rv.PeachDeep)
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(tileBg), contentAlignment = Alignment.Center) {
                Icon(channelIcon(a.notifyChannel) ?: Icons.Default.EventAvailable, null, tint = tileFg, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Карточка записи (для других экранов) — та же, что на шкале. */
@Composable
fun AppointmentCard(af: AppointmentFull, onClick: () -> Unit) = TimelineItem(af, first = true, last = true, onClick = onClick)

/** Пастельная плашка и цвет значка для способа оповещения. */
@Composable
fun channelColors(ch: NotifyChannel): Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> = when (ch) {
    NotifyChannel.WHATSAPP -> androidx.compose.ui.graphics.Color(0xFFDDF7E6) to androidx.compose.ui.graphics.Color(0xFF1FA855)
    NotifyChannel.TELEGRAM -> androidx.compose.ui.graphics.Color(0xFFDDF0FC) to androidx.compose.ui.graphics.Color(0xFF229ED9)
    NotifyChannel.SMS -> androidx.compose.ui.graphics.Color(0xFFEAE4FD) to androidx.compose.ui.graphics.Color(0xFF7456E8)
    NotifyChannel.NONE -> androidx.compose.ui.graphics.Color(0xFFFDE6EE) to androidx.compose.ui.graphics.Color(0xFFE0406E)
}

fun channelIcon(ch: NotifyChannel): ImageVector? = when (ch) {
    NotifyChannel.WHATSAPP -> Icons.AutoMirrored.Filled.Chat
    NotifyChannel.TELEGRAM -> Icons.AutoMirrored.Filled.Send
    NotifyChannel.SMS -> Icons.Default.Sms
    NotifyChannel.NONE -> null
}

