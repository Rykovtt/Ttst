package com.kartoteka.app.assistant

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Небольшая офлайн-модель распознавания (~45 МБ), которая слушает только фразу-пробуждение.
 * Скачивается один раз, дальше работает без интернета; звук никуда не уходит.
 */
object WakeModel {
    private const val URL_RU = "https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip"

    fun dir(context: Context) = File(context.filesDir, "wake/model")

    /** Модель распакована и целая (есть конфиг и граф). */
    fun ready(context: Context): Boolean = File(dir(context), "conf/model.conf").exists() && File(dir(context), "graph").isDirectory

    /** Скачать и распаковать. [onProgress] — проценты 0..100. Возвращает false при любой ошибке. */
    fun download(context: Context, onProgress: (Int) -> Unit): Boolean = runCatching {
        if (ready(context)) return true
        val root = File(context.filesDir, "wake").apply { mkdirs() }
        val zip = File(context.cacheDir, "wake-model.zip")
        val c = URL(URL_RU).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000; c.readTimeout = 30_000
        val total = c.contentLengthLong.takeIf { it > 0 } ?: 46_236_750L
        c.inputStream.use { input ->
            zip.outputStream().use { out ->
                val buf = ByteArray(64 * 1024); var done = 0L; var last = -1
                while (true) {
                    val n = input.read(buf); if (n < 0) break
                    out.write(buf, 0, n); done += n
                    val p = (done * 90 / total).toInt().coerceIn(0, 90)
                    if (p != last) { last = p; onProgress(p) }
                }
            }
        }
        c.disconnect()
        // Распаковка во временную папку, затем переименование — недокачанная модель не считается готовой.
        val tmp = File(root, "tmp").apply { deleteRecursively(); mkdirs() }
        ZipInputStream(zip.inputStream().buffered()).use { zin ->
            while (true) {
                val e = zin.nextEntry ?: break
                // Верхняя папка архива отбрасывается: model/<содержимое>.
                val rel = e.name.substringAfter('/', "")
                if (rel.isEmpty() || rel.contains("..")) continue
                val f = File(tmp, rel)
                if (e.isDirectory) f.mkdirs() else { f.parentFile?.mkdirs(); f.outputStream().use { zin.copyTo(it) } }
            }
        }
        dir(context).deleteRecursively()
        check(tmp.renameTo(dir(context)))
        zip.delete()
        onProgress(100)
        ready(context)
    }.getOrDefault(false)

    fun delete(context: Context) { File(context.filesDir, "wake").deleteRecursively() }
}
