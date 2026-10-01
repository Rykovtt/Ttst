package com.kartoteka.app.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kartoteka.app.KartotekaApp
import com.kartoteka.app.i18n.t
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Автоматическая резервная копия: раз в день/неделю архив сам кладёт зашифрованный паролем файл .krtk
 * в выбранную папку (телефон, SD-карта, флешка) и хранит несколько последних копий. Без облака.
 * Пароль копий хранится зашифрованным ключом телефона ([FileVault]) — иначе копию нельзя сделать без вас.
 */
object AutoBackup {
    private const val WORK = "auto_backup"
    const val PREFIX = "RVault_"
    const val EXT = ".krtk"

    private fun secretFile(context: Context) = File(context.filesDir, "backup_secret.enc")

    fun setPassword(app: KartotekaApp, password: String) = app.fileVault.write(secretFile(app), password.toByteArray())

    fun password(app: KartotekaApp): String? = runCatching { app.fileVault.decrypt(secretFile(app)).decodeToString() }.getOrNull()

    fun hasPassword(app: KartotekaApp) = secretFile(app).exists()

    fun schedule(context: Context, days: Int) {
        val req = PeriodicWorkRequestBuilder<Worker>(days.toLong().coerceAtLeast(1), TimeUnit.DAYS)
            .setInitialDelay(1, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun cancel(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(WORK)

    /** Сделать копию сейчас. Возвращает null при успехе или текст ошибки. */
    suspend fun run(app: KartotekaApp, now: Long = System.currentTimeMillis()): String? {
        val s = app.settings
        val error = runCatching {
            val folder = s.autoBackupFolder.value.value.takeIf { it.isNotBlank() }?.let(Uri::parse)
                ?: return@runCatching t("Не выбрана папка для копий")
            val password = password(app) ?: return@runCatching t("Не задан пароль копий")
            val cr = app.contentResolver
            val parent = DocumentsContract.buildDocumentUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder))
            val name = PREFIX + SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date(now)) + EXT
            val doc = DocumentsContract.createDocument(cr, parent, "application/octet-stream", name)
                ?: return@runCatching t("Папка недоступна — возможно, флешка отключена")
            try {
                app.backup.export(doc, password)
            } catch (e: Exception) {
                runCatching { DocumentsContract.deleteDocument(cr, doc) }
                throw e
            }
            rotate(app, folder, s.autoBackupKeep.value.value.toIntOrNull() ?: 5)
            null
        }.getOrElse { e -> t("Ошибка: %1\$s", e.message ?: e.javaClass.simpleName) }
        if (error == null) {
            s.autoBackupLastAt.set(now.toString())
            s.autoBackupError.set("")
        } else {
            s.autoBackupError.set(error)
        }
        return error
    }

    /** Удаляет старые копии RVault_*.krtk сверх [keep]; чужие файлы в папке не трогаем. */
    private fun rotate(context: Context, tree: Uri, keep: Int) {
        val cr = context.contentResolver
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val ours = mutableListOf<Pair<String, String>>() // имя → id
        cr.query(children, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val n = c.getString(0) ?: continue
                if (n.startsWith(PREFIX) && n.endsWith(EXT)) ours += n to c.getString(1)
            }
        }
        backupsToDelete(ours.map { it.first }, keep).forEach { n ->
            val id = ours.first { it.first == n }.second
            runCatching { DocumentsContract.deleteDocument(cr, DocumentsContract.buildDocumentUriUsingTree(tree, id)) }
        }
    }

    /** Какие копии удалить: имена с датой сортируются по времени, оставляем [keep] самых новых. */
    fun backupsToDelete(names: List<String>, keep: Int): List<String> =
        names.filter { it.startsWith(PREFIX) && it.endsWith(EXT) }.sortedDescending().drop(keep.coerceAtLeast(1))

    /** Человеку понятное имя папки. */
    fun folderName(context: Context, tree: Uri): String = runCatching {
        val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        context.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull() ?: tree.lastPathSegment.orEmpty()

    class Worker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val app = applicationContext as KartotekaApp
            if (!app.settings.autoBackup.value.value) return Result.success()
            return if (run(app) == null) Result.success() else Result.retry()
        }
    }
}
