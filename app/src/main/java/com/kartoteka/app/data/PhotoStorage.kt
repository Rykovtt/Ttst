package com.kartoteka.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.InputStream
import java.util.UUID

/** Фото хранятся во внутренней памяти приложения — их не видно в галерее и другим приложениям. */
class PhotoStorage(private val context: Context) {
    val dir: File get() = File(context.filesDir, "photos").apply { mkdirs() }

    fun newCameraFile(): File =
        File(context.cacheDir, "camera").apply { mkdirs() }.let { File(it, "shot_${System.currentTimeMillis()}.jpg") }

    /** Копирует картинку, уменьшая до 2048px по длинной стороне. Возвращает абсолютный путь. */
    fun import(uri: Uri): String? = runCatching {
        val target = File(dir, "${UUID.randomUUID()}.jpg")
        val bitmap = decode(uri)
        if (bitmap != null) {
            target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            bitmap.recycle()
        } else {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { input.copyTo(it) }
            } ?: return null
        }
        target.absolutePath
    }.getOrNull()

    fun importStream(input: InputStream): String? = runCatching {
        val bitmap = BitmapFactory.decodeStream(input) ?: return null
        val target = File(dir, "${UUID.randomUUID()}.jpg")
        target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        target.absolutePath
    }.getOrNull()

    fun delete(path: String?) {
        if (path != null && path.startsWith(dir.absolutePath)) File(path).delete()
    }

    private fun decode(uri: Uri): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val longest = maxOf(w, h)
                if (longest > MAX) {
                    val scale = MAX.toFloat() / longest
                    decoder.setTargetSize((w * scale).toInt(), (h * scale).toInt())
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        }
    }.getOrNull()

    private companion object {
        const val MAX = 2048
    }
}
