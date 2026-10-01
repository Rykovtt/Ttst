package com.kartoteka.app.ui.components

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * «Перенести из галереи»: выбрать фото → скопировать в архив → удалить оригиналы с телефона.
 * Удаление идёт мимо корзины: на Android 11+ — системным запросом MediaStore.createDeleteRequest
 * (удаляет навсегда, не в «Недавно удалённые»), на старых — через DocumentsContract.deleteDocument.
 */
class GalleryMover(val pick: () -> Unit, val deleteOriginals: (List<Uri>) -> Unit)

/** Итог удаления оригиналов: сколько удалено и сколько осталось на телефоне. */
data class MoveResult(val deleted: Int, val kept: Int)

@Composable
fun rememberGalleryMover(onPicked: (List<Uri>) -> Unit, onDeleted: (MoveResult) -> Unit): GalleryMover {
    val context = LocalContext.current
    var waiting by remember { mutableStateOf<Pair<Int, Int>?>(null) } // (в запросе, не удалось сопоставить)

    val confirm = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        val (asked, failed) = waiting ?: return@rememberLauncherForActivityResult
        waiting = null
        onDeleted(if (r.resultCode == Activity.RESULT_OK) MoveResult(asked, failed) else MoveResult(0, asked + failed))
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) onPicked(uris)
    }

    return GalleryMover(
        pick = { picker.launch(arrayOf("image/*")) },
        deleteOriginals = { uris ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val media = uris.mapNotNull { mediaUri(context, it) }
                val failed = uris.size - media.size
                if (media.isEmpty()) {
                    onDeleted(MoveResult(0, failed))
                } else {
                    waiting = media.size to failed
                    val pi = MediaStore.createDeleteRequest(context.contentResolver, media)
                    confirm.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                }
            } else {
                val ok = uris.count { u -> runCatching { DocumentsContract.deleteDocument(context.contentResolver, u) }.getOrDefault(false) }
                onDeleted(MoveResult(ok, uris.size - ok))
            }
        },
    )
}

/** Документ из системного выбора файлов → запись MediaStore (нужна для удаления на Android 11+). */
private fun mediaUri(context: Context, uri: Uri): Uri? {
    if (uri.authority == MediaStore.AUTHORITY) return uri
    runCatching { MediaStore.getMediaUri(context, uri) }.getOrNull()?.let { return it }
    // Запасной путь для «Изображения»: document id вида "image:123".
    if (uri.authority == "com.android.providers.media.documents") {
        val id = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()?.substringAfter(':')?.toLongOrNull() ?: return null
        return android.content.ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
    }
    return null
}
