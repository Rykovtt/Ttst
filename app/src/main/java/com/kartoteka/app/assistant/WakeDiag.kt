package com.kartoteka.app.assistant

import android.content.Context
import java.io.File

/**
 * Что услышал распознаватель имени и что решил: помогает понять, почему «не слышит» при музыке.
 * Последние [MAX] строк; хранится в файле приложения (только на телефоне), переживает перезапуск.
 */
object WakeDiag {
    private const val MAX = 2_000
    private val lines = ArrayDeque<String>()
    private var last = ""
    private var file: File? = null
    /** Строк в файле сейчас (с хвостом сверх [MAX], который ещё не срезан). */
    private var fileLines = 0

    /** Подключить файл и подгрузить сохранённое (вызывается из службы и экрана настроек). */
    @Synchronized fun init(context: Context) {
        if (file != null) return
        val f = File(context.applicationContext.filesDir, "wake_diag.txt")
        file = f
        val saved = runCatching { if (f.exists()) f.readLines() else emptyList() }.getOrDefault(emptyList())
        lines.clear()
        saved.takeLast(MAX).forEach { lines.addLast(it) }
        fileLines = saved.size
    }

    @Synchronized fun add(playing: Boolean, final: Boolean, text: String, verdict: String) {
        val line = (if (playing) "♪ " else "· ") + (if (final) "F " else "p ") + text.take(80) + " → " + verdict
        if (line == last) return
        last = line
        val now = java.text.SimpleDateFormat("dd.MM HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        val entry = "$now $line"
        lines.addLast(entry)
        while (lines.size > MAX) lines.removeFirst()
        file?.let { f ->
            runCatching {
                // Дописываем строку; файл переписываем целиком, только когда он перерос предел на 500 строк.
                if (fileLines + 1 > MAX + 500) { f.writeText(lines.joinToString("\n", postfix = "\n")); fileLines = lines.size }
                else { f.appendText(entry + "\n"); fileLines++ }
            }
        }
    }

    @Synchronized fun text(): String = lines.joinToString("\n")
    @Synchronized fun clear() {
        lines.clear(); last = ""; fileLines = 0
        file?.let { runCatching { it.delete() } }
    }
}
