package com.kartoteka.app.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.kartoteka.app.ui.app

class PhotoPicker(val gallery: () -> Unit, val camera: () -> Unit)

/** Выбор фото из галереи (несколько сразу) или съёмка камерой. */
@Composable
fun rememberPhotoPicker(multiple: Boolean, onPicked: (List<Uri>) -> Unit): PhotoPicker {
    val context = LocalContext.current
    val storage = app().repository.photos
    var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }

    val single = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPicked(listOf(uri))
    }
    val many = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        if (uris.isNotEmpty()) onPicked(uris)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = cameraUri
        if (ok && uri != null) onPicked(listOf(Uri.parse(uri)))
    }
    return PhotoPicker(
        gallery = {
            val req = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            if (multiple) many.launch(req) else single.launch(req)
        },
        camera = {
            val file = storage.newCameraFile()
            val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
            cameraUri = uri.toString()
            camera.launch(uri)
        },
    )
}

@Composable
fun PhotoSourceMenu(expanded: Boolean, onDismiss: () -> Unit, picker: PhotoPicker) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("Из галереи") },
            leadingIcon = { Icon(Icons.Default.PhotoLibrary, null) },
            onClick = { onDismiss(); picker.gallery() },
        )
        DropdownMenuItem(
            text = { Text("Сделать снимок") },
            leadingIcon = { Icon(Icons.Default.PhotoCamera, null) },
            onClick = { onDismiss(); picker.camera() },
        )
    }
}
