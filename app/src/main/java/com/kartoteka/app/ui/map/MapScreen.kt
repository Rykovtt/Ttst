package com.kartoteka.app.ui.map

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
fun MapScreen(onOpenPerson: (Long) -> Unit) {
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
        MapMarker("${pl.id}", pl.lat!!, pl.lng!!, pf.person, pf.person.displayName)
    }
    val placeByKey = places.associateBy { "${it.first.id}" }

    Box(Modifier.fillMaxSize()) {
        OsmMap(
            markers = markers,
            fitKey = listOf(fitKey, kind, groupId, all == null),
            onMarkerClick = { selected = it },
            onTap = { selected = null },
            modifier = Modifier.fillMaxSize(),
        )

        Column(Modifier.statusBarsPadding().padding(top = 8.dp)) {
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(kind == null, { kind = null }, label = { Text(t("Все адреса")) }, leadingIcon = { Icon(Icons.Default.Map, null, Modifier.size(18.dp)) }, colors = chipColors(), shape = CircleShape, border = null, elevation = FilterChipDefaults.filterChipElevation(elevation = 3.dp))
                }
                item {
                    FilterChip(kind == PlaceKind.HOME, { kind = if (kind == PlaceKind.HOME) null else PlaceKind.HOME }, label = { Text(t("Где живут")) }, leadingIcon = { Icon(Icons.Default.Home, null, Modifier.size(18.dp)) }, colors = chipColors(), shape = CircleShape, border = null, elevation = FilterChipDefaults.filterChipElevation(elevation = 3.dp))
                }
                item {
                    FilterChip(kind == PlaceKind.WORK, { kind = if (kind == PlaceKind.WORK) null else PlaceKind.WORK }, label = { Text(t("Где работают")) }, leadingIcon = { Icon(Icons.Default.Work, null, Modifier.size(18.dp)) }, colors = chipColors(), shape = CircleShape, border = null, elevation = FilterChipDefaults.filterChipElevation(elevation = 3.dp))
                }
                items(groups, key = { it.group.id }) { g ->
                    val sel = groupId == g.group.id
                    FilterChip(sel, { groupId = if (sel) null else g.group.id }, label = { Text("${g.group.emoji} ${g.group.name}".trim()) }, leadingIcon = { ColorDot(g.group.color) }, colors = chipColors(), shape = CircleShape, border = null, elevation = FilterChipDefaults.filterChipElevation(elevation = 3.dp))
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
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = if (selected != null) 150.dp else 16.dp),
        ) { Icon(Icons.Default.CenterFocusStrong, t("Показать всех")) }

        val sel = selected?.let { placeByKey[it.key] }
        if (sel != null) {
            val (pl, pf) = sel
            Surface(
                onClick = { onOpenPerson(pf.person.id) },
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shadowElevation = 6.dp,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(pf.person, 52.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(pf.person.displayName, style = MaterialTheme.typography.titleMedium)
                        Text(pl.label.ifBlank { pl.placeKind.title }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(pl.address, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FilledTonalIconButton(onClick = { Messaging.navigate(context, pl) }) { Icon(Icons.Default.Directions, t("Маршрут")) }
                }
            }
        }
    }
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
)
