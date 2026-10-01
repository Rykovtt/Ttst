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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CalendarScreen(onNew: (LocalDate) -> Unit, onOpen: (Long) -> Unit) {
    val app = app()
    val vm: CalendarViewModel = viewModel { CalendarViewModel(app) }
    val listMode by vm.listMode.collectAsState()
    val appts by vm.monthAppointments.collectAsState()
    val upcoming by vm.upcoming.collectAsState()
    var selectedEpoch by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    val selected = LocalDate.ofEpochDay(selectedEpoch)
    val base = YearMonth.now()
    val pager = rememberPagerState(initialPage = MID + (YearMonth.from(selected).let { (it.year - base.year) * 12 + it.monthValue - base.monthValue })) { PAGES }
    val scope = rememberCoroutineScope()
    val shownMonth = base.plusMonths((pager.currentPage - MID).toLong())
    LaunchedEffect(shownMonth) { vm.month.value = shownMonth }

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

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNew(if (listMode) LocalDate.now() else selected) },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(60.dp),
            ) { Icon(Icons.Default.Add, t("Записать"), Modifier.size(28.dp)) }
        },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
            item(key = "header") {
                Column {
                Row(
                    Modifier.statusBarsPadding().fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(t("Календарь"), style = MaterialTheme.typography.headlineLarge)
                        val todayCount = byDay[LocalDate.now()].orEmpty().count { it.appointment.appointmentStatus != AppointmentStatus.CANCELLED }
                        Text(
                            if (todayCount == 0) t("Сегодня записей нет") else t("Сегодня %1\$s %2\$s", todayCount, com.kartoteka.app.data.ArchiveLogic.plural(todayCount.toLong(), "запись", "записи", "записей")),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = {
                        selectedEpoch = LocalDate.now().toEpochDay()
                        scope.launch { pager.animateScrollToPage(MID) }
                    }) { Icon(Icons.Default.Today, t("Сегодня")) }
                }
                com.kartoteka.app.ui.components.Segmented(
                    options = listOf(t("Месяц"), t("Список")),
                    selected = if (listMode) 1 else 0,
                    onSelect = { vm.listMode.value = it == 1 },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                )
                }
            }

            if (listMode) {
                val grouped = upcoming.filter { it.appointment.appointmentStatus != AppointmentStatus.CANCELLED }
                    .groupBy { AppointmentLogic.zoned(it.appointment.start).toLocalDate() }
                if (grouped.isEmpty()) {
                    item { EmptyState(Icons.Default.CalendarMonth, t("Нет ближайших записей"), t("Нажмите «Записать», чтобы запланировать встречу с человеком.")) }
                }
                grouped.forEach { (date, list) ->
                    item(key = "d_${date}") { DayHeader(date) }
                    items(list, key = { it.appointment.id }) { AppointmentCard(it, onClick = { onOpen(it.appointment.id) }) }
                }
            } else {
                item(key = "month") {
                    Column {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }) { Icon(Icons.Default.ChevronLeft, t("Назад")) }
                            Text(
                                shownMonth.month.getDisplayName(TextStyle.FULL_STANDALONE, ru).replaceFirstChar { it.uppercase() } + " " + shownMonth.year,
                                style = MaterialTheme.typography.titleLarge,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }) { Icon(Icons.Default.ChevronRight, t("Вперёд")) }
                        }
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
                item(key = "day_title") { DayHeader(selected) }
                val dayList = byDay[selected].orEmpty().sortedBy { it.appointment.start }
                if (dayList.isEmpty()) {
                    item(key = "free") {
                        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(t("Свободный день"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            FilledTonalButton(onClick = { onNew(selected) }) { Text(t("Записать на этот день")) }
                        }
                    }
                }
                items(dayList, key = { it.appointment.id }) { AppointmentCard(it, onClick = { onOpen(it.appointment.id) }) }
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
                        Modifier.weight(1f).aspectRatio(1f).padding(4.dp).clip(CircleShape)
                            .background(
                                when {
                                    isSel -> MaterialTheme.colorScheme.primary
                                    date == today -> MaterialTheme.colorScheme.surfaceContainerHighest
                                    else -> androidx.compose.ui.graphics.Color.Transparent
                                }
                            )
                            .clickable { onSelect(date) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${date.dayOfMonth}",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (date == today || isSel) FontWeight.Bold else FontWeight.Normal,
                                color = when {
                                    isSel -> MaterialTheme.colorScheme.onPrimary
                                    !inMonth -> MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.height(6.dp)) {
                                repeat(minOf(count, 3)) {
                                    Box(Modifier.size(4.dp).clip(CircleShape).background(if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.tertiary))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayHeader(date: LocalDate) {
    val today = LocalDate.now()
    val prefix = when (date) {
        today -> t("Сегодня, ")
        today.plusDays(1) -> t("Завтра, ")
        else -> ""
    }
    Text(
        prefix + com.kartoteka.app.i18n.I18n.dayMonth(date.dayOfMonth, date.monthValue) + ", " + AppointmentLogic.weekday(date),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp),
    )
}

@Composable
fun AppointmentCard(af: AppointmentFull, onClick: () -> Unit) {
    val a = af.appointment
    val p = af.person ?: return
    val start = AppointmentLogic.zoned(a.start)
    val end = AppointmentLogic.zoned(a.end)
    val cancelled = a.appointmentStatus == AppointmentStatus.CANCELLED
    val past = a.end < System.currentTimeMillis()
    val (tileBg, tileFg) = channelColors(a.notifyChannel)
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(48.dp)) {
            Text(AppointmentLogic.timeText(start), style = MaterialTheme.typography.titleSmall)
            Text(AppointmentLogic.timeText(end), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
        Spacer(Modifier.width(8.dp))
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.weight(1f).alpha(if (cancelled || past) 0.6f else 1f),
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(tileBg), contentAlignment = Alignment.Center) {
                    Icon(channelIcon(a.notifyChannel) ?: Icons.Default.EventAvailable, null, tint = tileFg, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        a.title.ifBlank { t("Встреча") },
                        style = MaterialTheme.typography.titleSmall,
                        textDecoration = if (cancelled) TextDecoration.LineThrough else null,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    val pending = af.reminders.count { it.sentAt == null }
                    val sub = listOfNotNull(
                        p.displayName,
                        a.place.takeIf { it.isNotBlank() },
                        when (a.appointmentStatus) {
                            AppointmentStatus.CANCELLED -> t("отменено")
                            AppointmentStatus.DONE -> t("состоялось")
                            AppointmentStatus.PLANNED -> if (pending > 0) "🔔 ${pending}" else null
                        },
                    ).joinToString(" · ")
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(8.dp))
                Avatar(p, 40.dp)
            }
        }
    }
}

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

