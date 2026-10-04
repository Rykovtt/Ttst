package com.kartoteka.app.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.IconButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.kartoteka.app.ui.components.CountPill
import com.kartoteka.app.ui.components.pressable
import com.kartoteka.app.ui.theme.Motion
import com.kartoteka.app.ui.theme.Rv
import com.kartoteka.app.ui.theme.motion

import com.kartoteka.app.i18n.t

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kartoteka.app.data.Geo
import com.kartoteka.app.data.Place
import com.kartoteka.app.data.PlaceKind
import com.kartoteka.app.messaging.Messaging
import com.kartoteka.app.ui.app
import com.kartoteka.app.ui.components.Avatar
import com.kartoteka.app.ui.components.ColorDot
import com.kartoteka.app.ui.components.EmptyState

@Composable
fun MapScreen(onOpenPerson: (Long) -> Unit, onBack: (() -> Unit)? = null) {
    val app = app()
    val context = LocalContext.current
    val all by app.repository.observeAll().collectAsState(initial = null)
    val groups by app.repository.observeGroups().collectAsState(initial = emptyList())
    var kind by remember { mutableStateOf<PlaceKind?>(null) }
    var groupId by remember { mutableStateOf<Long?>(null) }
    var selected by remember { mutableStateOf<MapMarker?>(null) }
    var fitKey by remember { mutableIntStateOf(0) }

    // Досчитываем координаты для адресов, добавленных без точки.
    LaunchedEffect(Unit) { runCatching { Geo.fillMissing(context, app.repository) } }

    val people = all.orEmpty().filter { pf -> groupId == null || pf.groups.any { it.id == groupId } }
    val places: List<Pair<Place, com.kartoteka.app.data.PersonFull>> = people.flatMap { pf -> pf.places.map { it to pf } }
        .filter { (pl, _) -> pl.hasCoords && (kind == null || pl.placeKind == kind) }
    val markers = places.map { (pl, pf) ->
        MapMarker("${pl.id}", pl.lat!!, pl.lng!!, pf.person, pf.person.displayName, ring = pf.groups.firstOrNull()?.color?.toInt())
    }
    val placeByKey = places.associateBy { "${it.first.id}" }

    Box(Modifier.fillMaxSize()) {
        OsmMap(
            markers = markers,
            fitKey = listOf(fitKey, kind, groupId, all == null),
            onMarkerClick = { selected = it },
            onTap = { selected = null },
            styled = true,
            modifier = Modifier.fillMaxSize(),
        )
        // Затемнение сверху — под заголовок и фильтры.
        Box(Modifier.fillMaxWidth().height(190.dp).background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Rv.HeroBg.copy(alpha = 0.92f), Color.Transparent))))

        Column(Modifier.statusBarsPadding().padding(top = 4.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = if (onBack != null) 6.dp else 22.dp, end = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Назад"), tint = Rv.HeroText) }
                Text(t("Карта"), style = MaterialTheme.typography.headlineLarge, color = Rv.HeroText, modifier = Modifier.weight(1f))
                CountPill(markers.size, dark = true)
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(kind == null, { kind = null }, label = { Text(t("Все адреса")) }, leadingIcon = { Icon(Icons.Default.Map, null, Modifier.size(18.dp)) }, colors = chipColors(), shape = CircleShape, border = null, elevation = null)
                }
                item {
                    FilterChip(kind == PlaceKind.HOME, { kind = if (kind == PlaceKind.HOME) null else PlaceKind.HOME }, label = { Text(t("Где живут")) }, leadingIcon = { Icon(Icons.Default.Home, null, Modifier.size(18.dp)) }, colors = chipColors(), shape = CircleShape, border = null, elevation = null)
                }
                item {
                    FilterChip(kind == PlaceKind.WORK, { kind = if (kind == PlaceKind.WORK) null else PlaceKind.WORK }, label = { Text(t("Где работают")) }, leadingIcon = { Icon(Icons.Default.Work, null, Modifier.size(18.dp)) }, colors = chipColors(), shape = CircleShape, border = null, elevation = null)
                }
                items(groups, key = { it.group.id }) { g ->
                    val sel = groupId == g.group.id
                    FilterChip(sel, { groupId = if (sel) null else g.group.id }, label = { Text("${g.group.emoji} ${g.group.name}".trim()) }, leadingIcon = { ColorDot(g.group.color) }, colors = chipColors(), shape = CircleShape, border = null, elevation = null)
                }
            }
        }

        if (all != null && markers.isEmpty()) {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.95f), modifier = Modifier.align(Alignment.Center).padding(24.dp)) {
                EmptyState(Icons.Default.Map, t("Пока нет адресов на карте"), t("Добавьте адрес «Дом» или «Работа» в карточке человека — точка появится здесь."))
            }
        }

        SmallFloatingActionButton(
            onClick = { fitKey++ },
            shape = CircleShape,
            containerColor = Rv.HeroSurface,
            contentColor = Rv.HeroText,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = if (selected != null) 120.dp else 16.dp),
        ) { Icon(Icons.Default.CenterFocusStrong, t("Показать всех")) }

        val sel = selected?.let { placeByKey[it.key] }
        if (sel != null) {
            val (pl, pf) = sel
            androidx.compose.animation.AnimatedVisibility(
                visible = true,
                enter = androidx.compose.animation.slideInVertically(motion(Motion.STANDARD)) { it } + androidx.compose.animation.fadeIn(motion(Motion.STANDARD)),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(28.dp)).background(Rv.HeroSurface)
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(28.dp))
                        .pressable { onOpenPerson(pf.person.id) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (pf.person.avatarPath != null) {
                        coil.compose.AsyncImage(
                            model = java.io.File(pf.person.avatarPath), contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)),
                        )
                    } else Avatar(pf.person, 64.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(pf.person.displayName, style = MaterialTheme.typography.titleMedium, color = Rv.HeroText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(pl.label.ifBlank { pl.placeKind.title }, style = MaterialTheme.typography.labelMedium, color = Rv.Peach)
                        Text(pl.address, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = Rv.HeroMuted)
                    }
                    Box(
                        Modifier.size(46.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)).pressable { Messaging.navigate(context, pl) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.Directions, t("Маршрут"), tint = Rv.HeroText) }
                }
            }
        }
    }
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    containerColor = Rv.HeroSurface,
    labelColor = Rv.HeroText,
    iconColor = Rv.HeroText,
    selectedContainerColor = Rv.Peach,
    selectedLabelColor = Rv.Ink,
    selectedLeadingIconColor = Rv.Ink,
)
