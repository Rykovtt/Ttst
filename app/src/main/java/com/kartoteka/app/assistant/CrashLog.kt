package com.kartoteka.app.assistant

import android.content.Context
import java.io.File

/**
 * Журнал последнего сбоя: при падении приложения сохраняем причину в файл на телефоне, чтобы её можно было
 * посмотреть в настройках ассистента и переслать разработчику. Никуда сам не отправляется.
 */
object CrashLog {
    private fun file(context: Context) = File(context.filesDir, "last_crash.txt")

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            record(app, thread.name, error)
            previous?.uncaughtException(thread, error)
        }
    }

    fun record(context: Context, thread: String, error: Throwable) {
        runCatching {
            val text = buildString {
                append(java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date()))
                append("  v").append(runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull())
                append("  thread=").append(thread).append('\n')
                append(android.util.Log.getStackTraceString(error))
            }
            file(context).writeText(text.take(6000))
        }
    }

    fun read(context: Context): String? = runCatching { file(context).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() } }.getOrNull()

    fun clear(context: Context) { runCatching { file(context).delete() } }
}
