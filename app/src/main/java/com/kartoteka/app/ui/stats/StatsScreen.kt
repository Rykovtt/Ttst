package com.kartoteka.app.ui.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kartoteka.app.data.AppointmentLogic
import com.kartoteka.app.data.StatsLogic
import com.kartoteka.app.i18n.t
import com.kartoteka.app.ui.app
import com.kartoteka.app.ui.components.Avatar
import com.kartoteka.app.ui.components.heroBackground
import com.kartoteka.app.ui.components.pressable
import com.kartoteka.app.ui.theme.Motion
import com.kartoteka.app.ui.theme.NumberStyle
import com.kartoteka.app.ui.theme.Rv
import com.kartoteka.app.ui.theme.motion
import java.time.LocalDate

/** Статистика: тёмная «приборная панель» — крупные цифры, столбики активности, кто чаще всего. */
@Composable
fun StatsScreen(onBack: () -> Unit, onOpenPerson: (Long) -> Unit) {
    val app = app()
    var period by rememberSaveable { mutableStateOf(StatsLogic.Period.MONTH) }
    val range = remember(period) { StatsLogic.range(period) }
    val appts by remember(period) {
        app.repository.observeAppointments(AppointmentLogic.millis(range.prevFrom.atStartOfDay()), AppointmentLogic.millis(range.toExclusive.atStartOfDay()))
    }.collectAsState(initial = emptyList())
    val people by app.repository.observeAll().collectAsState(initial = emptyList())
    val r = remember(appts, people, period) { StatsLogic.compute(period, appts, people) }

    Column(
        Modifier.fillMaxSize().heroBackground(glow = Rv.PeachDeep).verticalScroll(rememberScrollState()).statusBarsPadding().padding(bottom = 24.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Назад"), tint = Rv.HeroText) }
            Text(t("Статистика"), style = MaterialTheme.typography.headlineLarge, color = Rv.HeroText, modifier = Modifier.weight(1f))
        }
        // Период.
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                StatsLogic.Period.MONTH to t("Этот месяц"),
                StatsLogic.Period.PREV_MONTH to t("Прошлый"),
                StatsLogic.Period.YEAR to t("Год"),
            ).forEach { (p, label) ->
                val sel = p == period
                Box(
                    Modifier.clip(CircleShape).background(if (sel) Rv.Peach else Rv.HeroSurface).pressable { period = p }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) { Text(label, style = MaterialTheme.typography.labelLarge, color = if (sel) Rv.Ink else Rv.HeroText) }
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(t("Записи"), r.appointments, r.appointmentsDelta, Modifier.weight(1f))
            StatTile(t("Новые люди"), r.newPeople, r.newPeopleDelta, Modifier.weight(1f))
            StatTile(t("Напоминания"), r.reminders, r.remindersDelta, Modifier.weight(1f))
        }

        Bars(r.bars, period, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp))

        Text(t("Активность"), style = MaterialTheme.typography.titleLarge, color = Rv.HeroText, modifier = Modifier.padding(start = 22.dp, top = 10.dp, bottom = 8.dp))
        InsightRow(
            Icons.Default.EventAvailable, t("Больше всего встреч"),
            r.busiestWeekday?.let { AppointmentLogic.weekday(LocalDate.now().with(java.time.temporal.TemporalAdjusters.nextOrSame(it))).replaceFirstChar { c -> c.uppercase() } } ?: t("Пока нет данных"),
        )
        r.topPerson?.let { (p, n) ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).clip(RoundedCornerShape(22.dp))
                    .background(Rv.HeroSurface).pressable { onOpenPerson(p.id) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(p, 44.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(t("Чаще всего"), style = MaterialTheme.typography.bodySmall, color = Rv.HeroMuted)
                    Text(p.displayName, style = MaterialTheme.typography.titleMedium, color = Rv.HeroText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("$n", style = NumberStyle.copy(fontSize = 24.sp), color = Rv.Peach)
                Icon(Icons.Default.ChevronRight, null, tint = Rv.HeroMuted)
            }
        }
        InsightRow(Icons.Default.Star, t("Всего людей в картотеке"), "${people.size}")
    }
}

@Composable
private fun StatTile(label: String, value: Int, delta: Int?, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(22.dp)).background(Rv.HeroSurface).padding(14.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Rv.HeroMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        Text("$value", style = NumberStyle.copy(fontSize = 30.sp), color = Rv.HeroText)
        if (delta != null) {
            val up = delta >= 0
            Text(
                (if (up) "↑ +" else "↓ ") + "$delta%", style = MaterialTheme.typography.labelMedium,
                color = if (up) Rv.Lime else Rv.Coral,
            )
        } else Text("—", style = MaterialTheme.typography.labelMedium, color = Rv.HeroMuted)
    }
}

/** Столбики с персиковым градиентом; вырастают при смене периода. */
@Composable
private fun Bars(values: List<Int>, period: StatsLogic.Period, modifier: Modifier) {
    val grow = remember(period) { Animatable(0f) }
    val spec = motion<Float>(Motion.EMPHASIZED)
    LaunchedEffect(period) { grow.animateTo(1f, spec) }
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    Column(modifier.clip(RoundedCornerShape(24.dp)).background(Rv.HeroSurface).padding(16.dp)) {
        Canvas(Modifier.fillMaxWidth().height(150.dp).semantics { contentDescription = values.joinToString() }) {
            val n = values.size.coerceAtLeast(1)
            val gap = size.width / n
            val w = (gap * 0.55f).coerceAtMost(14.dp.toPx())
            values.forEachIndexed { i, v ->
                val h = if (v == 0) 3.dp.toPx() else (v.toFloat() / max) * size.height * grow.value
                val x = gap * i + (gap - w) / 2
                drawRoundRect(
                    brush = if (v == 0) Brush.verticalGradient(listOf(Rv.HeroLine, Rv.HeroLine))
                    else Brush.verticalGradient(listOf(Rv.Peach, Rv.PeachDeep.copy(alpha = 0.55f)), startY = size.height - h, endY = size.height),
                    topLeft = Offset(x, size.height - h), size = Size(w, h), cornerRadius = CornerRadius(w / 2),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val labels = if (period == StatsLogic.Period.YEAR) listOf("1", "3", "6", "9", "12") else listOf("1", "7", "14", "21", "${values.size}")
            labels.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = Rv.HeroMuted) }
        }
    }
}

@Composable
private fun InsightRow(icon: ImageVector, title: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).clip(RoundedCornerShape(22.dp)).background(Rv.HeroSurface).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Rv.Peach.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Rv.Peach)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodySmall, color = Rv.HeroMuted)
            Text(value, style = MaterialTheme.typography.titleMedium, color = Rv.HeroText)
        }
    }
}
