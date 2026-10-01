package com.kartoteka.app.ui.chat

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kartoteka.app.data.NamedFile

/**
 * Импорт переписки: файлы ждут здесь, пока пользователь не разблокирует архив и не подтвердит импорт.
 * Пункт «RVault: импорт переписки» в меню «Поделиться» включается только на время импорта.
 */
object ChatImports {
    class Pending(val personId: Long?, val files: List<NamedFile>, val fromShare: Boolean = false)

    var pending by mutableStateOf<Pending?>(null)

    private const val ALIAS = "com.kartoteka.app.share.ChatImport"
    private const val PREFS = "rvault_chat_import"
    private const val TARGET = "target_person"
    private const val SINCE = "since"
    private const val WINDOW_MS = 15 * 60_000L
    private const val MAX_BYTES = 50L * 1024 * 1024

    /** Включить пункт в «Поделиться» и запомнить, к кому прикрепить переписку. */
    fun armShare(context: Context, personId: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(TARGET, personId).putLong(SINCE, System.currentTimeMillis()).apply()
        setShareEnabled(context, true)
    }

    fun disarmShare(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        setShareEnabled(context, false)
    }

    /** При запуске: если импорт начали давно и бросили — прячем пункт обратно. */
    fun expire(context: Context) {
        val since = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(SINCE, 0)
        if (since == 0L || System.currentTimeMillis() - since > WINDOW_MS) setShareEnabled(context, false)
    }

    private fun setShareEnabled(context: Context, on: Boolean) = runCatching {
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context.packageName, ALIAS),
            if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    /** Пришли файлы через «Поделиться». Возвращает true, если это наш импорт. */
    fun handleShare(context: Context, intent: Intent): Boolean {
        if (intent.action != Intent.ACTION_SEND && intent.action != Intent.ACTION_SEND_MULTIPLE) return false
        val uris = streams(intent)
        if (uris.isEmpty()) return false
        val target = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(TARGET, 0L).takeIf { it != 0L }
        val files = uris.mapNotNull { read(context, it) }.filter { f ->
            val n = f.name.lowercase()
            n.endsWith(".txt") || n.endsWith(".zip") || n.endsWith(".json") || n.endsWith(".html") || !n.contains('.')
        }
        if (files.isEmpty()) return false
        pending = Pending(target, files, fromShare = true)
        return true
    }

    fun read(context: Context, uri: Uri): NamedFile? = runCatching {
        val cr = context.contentResolver
        var name = uri.lastPathSegment ?: "chat.txt"
        var size = -1L
        cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getString(0)?.let { name = it }
                if (!c.isNull(1)) size = c.getLong(1)
            }
        }
        if (size > MAX_BYTES) return null
        val bytes = cr.openInputStream(uri)?.use { it.readBytes() } ?: return null
        NamedFile(name, bytes)
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun streams(intent: Intent): List<Uri> = when (intent.action) {
        Intent.ACTION_SEND -> listOfNotNull(
            if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else intent.getParcelableExtra(Intent.EXTRA_STREAM)
        )
        else -> (if (Build.VERSION.SDK_INT >= 33) intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)).orEmpty()
    }
}
