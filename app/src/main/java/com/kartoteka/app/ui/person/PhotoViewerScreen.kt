package com.kartoteka.app.ui.person

import com.kartoteka.app.i18n.t

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.kartoteka.app.ui.components.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.kartoteka.app.ui.app
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PhotoViewerScreen(personId: Long, startIndex: Int, onBack: () -> Unit) {
    val app = app()
    val vm: PersonDetailViewModel = viewModel(key = "person_${personId}") { PersonDetailViewModel(app, personId) }
    val data by vm.person.collectAsState()
    val photos = data?.photos.orEmpty()
    val scope = rememberCoroutineScope()
    var captionDialog by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (data != null && photos.isEmpty()) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onBack() }
        return
    }
    if (photos.isEmpty()) return
    val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, photos.lastIndex)) { photos.size }
    val current = photos.getOrNull(pager.currentPage.coerceAtMost(photos.lastIndex))

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { photos[it].id }) { page ->
            ZoomableImage(photos[page].path)
        }
        TopAppBar(
            title = { Text("${pager.currentPage + 1} / ${photos.size}", color = Color.White) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Назад"), tint = Color.White) } },
            actions = {
                IconButton(onClick = { current?.let { scope.launch { app.repository.setAvatar(personId, it.path) } } }) {
                    Icon(Icons.Default.AccountCircle, t("Сделать главным"), tint = Color.White)
                }
                IconButton(onClick = { captionDialog = true }) { Icon(Icons.Default.EditNote, t("Подпись"), tint = Color.White) }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, t("Удалить"), tint = Color.White) }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black.copy(alpha = 0.4f)),
        )
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.4f)).navigationBarsPadding()) {
            val isAvatar = current?.path == data?.person?.avatarPath
            val text = listOfNotNull(if (isAvatar) t("★ Главное фото") else null, current?.caption?.takeIf { it.isNotBlank() }).joinToString("\n")
            if (text.isNotBlank()) Text(text, color = Color.White, modifier = Modifier.padding(16.dp))
        }
    }

    if (captionDialog && current != null) {
        var text by remember { mutableStateOf(current.caption) }
        AlertDialog(
            onDismissRequest = { captionDialog = false },
            title = { Text(t("Подпись к фото")) },
            text = { OutlinedTextField(text, { text = it }, placeholder = { Text(t("Где, когда, с кем…")) }) },
            confirmButton = {
                TextButton(onClick = { scope.launch { app.repository.updatePhotoCaption(current, text.trim()) }; captionDialog = false }) { Text(t("Сохранить")) }
            },
            dismissButton = { TextButton(onClick = { captionDialog = false }) { Text(t("Отмена")) } },
        )
    }
    if (confirmDelete && current != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(t("Удалить фото?")) },
            confirmButton = { TextButton(onClick = { scope.launch { app.repository.deletePhoto(current) }; confirmDelete = false }) { Text(t("Удалить")) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(t("Отмена")) } },
        )
    }
}

@Composable
private fun ZoomableImage(path: String) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    AsyncImage(
        model = File(path),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // Жесты обрабатываем только при зуме, иначе свайп листает фото.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size > 1 || scale > 1f) {
                            scale = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                            offset = if (scale == 1f) Offset.Zero else offset + event.calculatePan()
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
    )
}
