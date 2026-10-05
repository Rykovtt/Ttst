package com.kartoteka.app.ui.map

import com.kartoteka.app.i18n.t

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.kartoteka.app.ui.components.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kartoteka.app.data.Geo
import com.kartoteka.app.data.Person
import kotlinx.coroutines.launch

/** Полноэкранный выбор точки на карте: поиск по адресу или касание. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationPickerDialog(
    initialAddress: String,
    initialLat: Double?,
    initialLng: Double?,
    person: Person?,
    onDismiss: () -> Unit,
    onPick: (lat: Double, lng: Double, address: String?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf(initialAddress) }
    var point by remember { mutableStateOf(if (initialLat != null && initialLng != null) initialLat to initialLng else null) }
    var fitKey by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf<String?>(null) }

    fun search() {
        scope.launch {
            status = t("Ищем…")
            val found = Geo.locate(context, query)
            if (found != null) { point = found; fitKey++; status = null } else status = t("Адрес не найден — отметьте точку касанием")
        }
    }

    LaunchedEffect(Unit) { if (point == null && initialAddress.isNotBlank()) search() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                com.kartoteka.app.ui.components.ScreenHero(t("Точка на карте"), compact = true, backgroundButton = false, onBack = onDismiss, backIcon = Icons.Default.Close, backDescription = t("Закрыть"))
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        query, { query = it }, singleLine = true, placeholder = { Text(t("Город, улица, дом")) },
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = ::search) { Icon(Icons.Default.Search, t("Найти")) }
                }
                Box(Modifier.weight(1f)) {
                    val p = point
                    OsmMap(
                        markers = if (p != null) listOf(MapMarker("pick", p.first, p.second, person, "")) else emptyList(),
                        fitKey = fitKey,
                        onTap = { g -> point = g.latitude to g.longitude },
                        modifier = Modifier.fillMaxSize(),
                    )
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
                        modifier = Modifier.align(Alignment.TopCenter).padding(10.dp),
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.TouchApp, null, Modifier.padding(end = 6.dp))
                            Text(status ?: t("Коснитесь карты, чтобы поставить точку"), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp)) {
                    Spacer(Modifier.weight(1f))
                    Button(enabled = point != null, onClick = {
                        val p = point ?: return@Button
                        scope.launch {
                            val addr = if (query.isBlank()) Geo.addressOf(context, p.first, p.second) else null
                            onPick(p.first, p.second, addr)
                        }
                    }) { Text(t("Готово")) }
                    Spacer(Modifier.width(4.dp))
                }
            }
        }
    }
}
