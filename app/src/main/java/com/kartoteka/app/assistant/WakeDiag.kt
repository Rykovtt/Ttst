package com.kartoteka.app.assistant

/** Что услышал распознаватель имени и что решил (последние ~60 строк, только в памяти): помогает понять, почему «не слышит» при музыке. */
object WakeDiag {
    private val lines = ArrayDeque<String>()
    private var last = ""

    @Synchronized fun add(playing: Boolean, final: Boolean, text: String, verdict: String) {
        val line = (if (playing) "♪ " else "· ") + (if (final) "F " else "p ") + text.take(80) + " → " + verdict
        if (line == last) return
        last = line
        val now = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        lines.addLast("$now $line")
        while (lines.size > 60) lines.removeFirst()
    }

    @Synchronized fun text(): String = lines.joinToString("\n")
    @Synchronized fun clear() { lines.clear(); last = "" }
}
