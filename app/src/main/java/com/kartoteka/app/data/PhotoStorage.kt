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

/**
 * Фото хранятся во внутренней памяти приложения и зашифрованы [FileVault] (файлы `*.jpg.enc`).
 * Их не видно в галерее и другим приложениям; Coil показывает их через [EncryptedPhotoFetcher].
 * Фото из версий до 1.7 (обычные .jpg) шифруются при запуске — см. Repository.encryptLegacyPhotos.
 */
class PhotoStorage(private val context: Context, private val vault: FileVault) {
    val dir: File get() = File(context.filesDir, "photos").apply { mkdirs() }

    fun newCameraFile(): File =
        File(context.cacheDir, "camera").apply { mkdirs() }.let { File(it, "shot_${System.currentTimeMillis()}.jpg") }

    /** Копирует картинку, уменьшая до 2048px по длинной стороне. Возвращает абсолютный путь. */
    fun import(uri: Uri): String? = runCatching {
        val target = newFile()
        val bitmap = decode(uri)
        if (bitmap != null) {
            vault.encryptTo(target).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            bitmap.recycle()
        } else {
            context.contentResolver.openInputStream(uri)?.use { input -> vault.write(target, input) } ?: return null
        }
        // Снимок камерой лежал во временной папке — больше не нужен.
        if (uri.authority == context.packageName + ".files") File(context.cacheDir, "camera").deleteRecursively()
        target.absolutePath
    }.getOrNull()

    fun importStream(input: InputStream): String? = runCatching {
        val bitmap = BitmapFactory.decodeStream(input) ?: return null
        val target = newFile()
        vault.encryptTo(target).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        target.absolutePath
    }.getOrNull()

    fun delete(path: String?) {
        if (path != null && path.startsWith(dir.absolutePath)) File(path).delete()
    }

    fun isEncrypted(path: String) = path.endsWith(ENC)

    /** Содержимое фото (расшифрованное). */
    fun readBytes(path: String): ByteArray = if (isEncrypted(path)) vault.decrypt(File(path)) else File(path).readBytes()

    /** Картинка, уменьшенная примерно до [maxSize] px — для булавок на карте и т.п. */
    fun decodeBitmap(path: String, maxSize: Int): Bitmap? = runCatching {
        val bytes = readBytes(path)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSize) sample *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    /** Сохранить готовые байты фото зашифрованными под именем [name] (восстановление копии). */
    fun saveEncrypted(name: String, bytes: ByteArray): String {
        val target = File(dir, name.removeSuffix(ENC).let { if (it.endsWith(".jpg") || it.endsWith(".png")) it else "$it.jpg" } + ENC)
        if (!target.exists()) vault.write(target, bytes)
        return target.absolutePath
    }

    /** Зашифровать старое открытое фото. Возвращает новый путь (старый файл не удаляется). */
    fun encryptLegacy(path: String): String? {
        val src = File(path)
        if (isEncrypted(path) || !src.exists() || !path.startsWith(dir.absolutePath)) return null
        val target = File(dir, src.name + ENC)
        src.inputStream().use { vault.write(target, it) }
        return target.absolutePath
    }

    private fun newFile() = File(dir, "${UUID.randomUUID()}.jpg$ENC")

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

    companion object {
        private const val MAX = 2048
        const val ENC = ".enc"
    }
}
