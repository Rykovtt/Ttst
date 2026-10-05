package com.kartoteka.app.ui.settings

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.NoPhotography
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kartoteka.app.data.IntruderLog
import com.kartoteka.app.i18n.t
import com.kartoteka.app.ui.app
import com.kartoteka.app.ui.person.ZoomableImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date

/** Журнал неудачных попыток входа со снимками. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntruderLogDialog(onDismiss: () -> Unit) {
    val log = app().intruders
    var refresh by remember { mutableStateOf(0) }
    val attempts = remember(refresh) { log.attempts() }
    var confirmClear by remember { mutableStateOf(false) }
    var opened by remember { mutableStateOf<Pair<Bitmap, IntruderLog.Attempt>?>(null) }
    LaunchedEffect(Unit) { log.markSeen() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                com.kartoteka.app.ui.components.ScreenHero(
                    t("Попытки входа"), compact = true, backgroundButton = false, onBack = onDismiss,
                    actions = {
                        if (attempts.isNotEmpty()) com.kartoteka.app.ui.components.HeroButton(Icons.Default.DeleteSweep, t("Очистить журнал"), { confirmClear = true })
                    },
                )
            },
        ) { pad ->
            if (attempts.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                    Text(t("Неудачных попыток входа не было"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp, pad.calculateTopPadding(), 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        Text(
                            t("Неверный PIN-код вводили %1\$s %2\$s. Снимок делается фронтальной камерой без звука.", attempts.size, com.kartoteka.app.data.ArchiveLogic.plural(attempts.size.toLong(), "раз", "раза", "раз")),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(attempts, key = { it.time }) { a -> AttemptRow(log, a, onOpen = { opened = it to a }) }
                }
            }
        }
    }
    opened?.let { (bmp, a) -> IntruderPhotoViewer(bmp, a.time, onDismiss = { opened = null }) }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(t("Очистить журнал?")) },
            text = { Text(t("Все записи и снимки будут удалены.")) },
            confirmButton = { TextButton(onClick = { log.clear(); confirmClear = false; refresh++ }) { Text(t("Очистить")) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(t("Отмена")) } },
        )
    }
}

@Composable
private fun AttemptRow(log: IntruderLog, a: IntruderLog.Attempt, onOpen: (Bitmap) -> Unit) {
    val photo by produceState<Bitmap?>(null, a) { value = withContext(Dispatchers.IO) { log.photo(a) } }
    val fmt = remember { SimpleDateFormat("d MMMM yyyy, HH:mm:ss", com.kartoteka.app.i18n.I18n.locale) }
    Surface(
        shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
        onClick = { photo?.let(onOpen) }, enabled = photo != null,
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(96.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                val bmp = photo
                if (bmp != null) Image(bmp.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Icon(Icons.Default.NoPhotography, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(t("Неверный PIN-код"), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(fmt.format(Date(a.time)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!a.hasPhoto) Text(t("Без снимка"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Снимок на весь экран: можно приблизить двумя пальцами. */
@Composable
private fun IntruderPhotoViewer(bmp: Bitmap, time: Long, onDismiss: () -> Unit) {
    val fmt = remember { SimpleDateFormat("d MMMM yyyy, HH:mm:ss", com.kartoteka.app.i18n.I18n.locale) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            ZoomableImage(bmp)
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, t("Закрыть"), tint = Color.White) }
                Text(fmt.format(Date(time)), color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
