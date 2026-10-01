package com.kartoteka.app.security

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.widget.Toast
import com.kartoteka.app.i18n.t

/**
 * Копирование из архива: текст помечается как секретный (клавиатура не показывает его в подсказках
 * и превью) и через [CLEAR_MS] стирается из буфера обмена — если там всё ещё он.
 */
object SecureClipboard {
    const val CLEAR_MS = 30_000L
    private const val LABEL = "RVault"
    /** ClipDescription.EXTRA_IS_SENSITIVE (Android 13+); Gboard понимает его и на старых версиях. */
    private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"

    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null

    fun copy(context: Context, text: String) {
        val cm = context.getSystemService(ClipboardManager::class.java) ?: return
        val clip = ClipData.newPlainText(LABEL, text)
        clip.description.extras = PersistableBundle().apply { putBoolean(EXTRA_IS_SENSITIVE, true) }
        cm.setPrimaryClip(clip)
        Toast.makeText(context, t("Скопировано. Через 30 секунд исчезнет из буфера обмена"), Toast.LENGTH_SHORT).show()

        pending?.let(handler::removeCallbacks)
        val appContext = context.applicationContext
        pending = Runnable { clearIfOurs(appContext, text) }.also { handler.postDelayed(it, CLEAR_MS) }
    }

    /** Стираем, только если в буфере всё ещё наш текст (или прочитать буфер в фоне нельзя). */
    fun clearIfOurs(context: Context, text: String) {
        pending = null
        val cm = context.getSystemService(ClipboardManager::class.java) ?: return
        val current = runCatching { cm.primaryClip }.getOrNull()
        val ours = current == null ||
            current.description?.label == LABEL && current.itemCount > 0 && current.getItemAt(0).text?.toString() == text
        if (!ours) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) runCatching { cm.clearPrimaryClip() }
        else cm.setPrimaryClip(ClipData.newPlainText("", ""))
    }
}
