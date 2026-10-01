package com.kartoteka.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * Журнал неудачных попыток входа: время и (если включено) тихий снимок фронтальной камерой.
 * Снимки зашифрованы [FileVault]. Хранятся последние [MAX] попыток.
 */
class IntruderLog(private val context: Context, private val vault: FileVault) {
    data class Attempt(val time: Long, val hasPhoto: Boolean)

    private val prefs = context.getSharedPreferences("rvault_intruders", Context.MODE_PRIVATE)
    private val dir: File get() = File(context.filesDir, "intruders").apply { mkdirs() }

    fun attempts(): List<Attempt> =
        prefs.getString(LOG, "").orEmpty().split(',').filter { it.isNotBlank() }.mapNotNull { s ->
            val (t, ph) = s.split(':').let { it[0] to it.getOrNull(1) }
            t.toLongOrNull()?.let { Attempt(it, ph == "1") }
        }.sortedByDescending { it.time }

    /** Записать неудачную попытку; [jpeg] — снимок или null. */
    @Synchronized
    fun record(time: Long, jpeg: ByteArray?) {
        if (jpeg != null) vault.write(photoFile(time), jpeg)
        val all = (attempts() + Attempt(time, jpeg != null)).sortedByDescending { it.time }
        all.drop(MAX).forEach { photoFile(it.time).delete() }
        save(all.take(MAX))
        prefs.edit().putInt(UNSEEN, prefs.getInt(UNSEEN, 0) + 1).apply()
    }

    /** Добавить снимок к уже записанной попытке (камера отвечает с задержкой). */
    @Synchronized
    fun attachPhoto(time: Long, jpeg: ByteArray) {
        val all = attempts()
        if (all.none { it.time == time }) return
        vault.write(photoFile(time), jpeg)
        save(all.map { if (it.time == time) it.copy(hasPhoto = true) else it })
    }

    fun photo(a: Attempt): Bitmap? = if (!a.hasPhoto) null else runCatching {
        val bytes = vault.decrypt(photoFile(a.time))
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()

    /** Сколько попыток было с последнего просмотра журнала. */
    val unseen: Int get() = prefs.getInt(UNSEEN, 0)

    fun markSeen() = prefs.edit().putInt(UNSEEN, 0).apply()

    fun clear() {
        dir.deleteRecursively()
        prefs.edit().clear().apply()
    }

    private fun save(list: List<Attempt>) =
        prefs.edit().putString(LOG, list.joinToString(",") { "${it.time}:${if (it.hasPhoto) 1 else 0}" }).apply()

    private fun photoFile(time: Long) = File(dir, "$time.jpg.enc")

    private companion object {
        const val LOG = "log"
        const val UNSEEN = "unseen"
        const val MAX = 50
    }
}
