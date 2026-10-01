package com.kartoteka.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.zip.ZipInputStream

data class ParsedMessage(val time: Long, val author: String, val text: String)

data class ParsedChat(val source: String, val title: String, val messages: List<ParsedMessage>) {
    /** Участники по числу сообщений (без системных). */
    val authors: List<Pair<String, Int>>
        get() = messages.filter { it.author.isNotBlank() }.groupingBy { it.author }.eachCount()
            .toList().sortedByDescending { it.second }
}

/** Файл, выбранный пользователем или присланный через «Поделиться». */
class NamedFile(val name: String, val bytes: ByteArray)

/**
 * Разбор выгрузок переписки:
 * - WhatsApp «Экспорт чата» — .txt (или .zip с .txt внутри), любые форматы даты Android/iOS;
 * - Telegram Desktop — result.json или messages*.html.
 */
object ChatParser {
    const val WHATSAPP = "WhatsApp"
    const val TELEGRAM = "Telegram"

    fun parse(files: List<NamedFile>, zone: ZoneId = ZoneId.systemDefault()): ParsedChat? {
        val expanded = files.flatMap { f -> if (f.isZip()) unzip(f) else listOf(f) }
        expanded.firstOrNull { it.name.endsWith(".json", true) }?.let { f ->
            parseTelegramJson(f.text(), zone)?.let { return it }
        }
        val html = expanded.filter { it.name.endsWith(".html", true) || it.name.endsWith(".htm", true) }
        if (html.isNotEmpty()) parseTelegramHtml(html.sortedBy { htmlOrder(it.name) }.map { it.text() }, zone)?.let { return it }
        val txt = expanded.filter { it.name.endsWith(".txt", true) || !it.name.contains('.') }
            .sortedByDescending { it.name.contains("chat", true) || it.name.contains("чат", true) }
        txt.firstOrNull()?.let { f -> parseWhatsApp(f.text(), whatsAppTitle(f.name), zone)?.let { return it } }
        return null
    }

    // ---------------- WhatsApp ----------------

    private val WA_LINE = Regex(
        """^\[?(\d{1,4})[./-](\d{1,2})[./-](\d{2,4}),?\s+(\d{1,2})[:.](\d{2})(?:[:.](\d{2}))?\s*([AaPp]\.?\s?[Mm]\.?)?\]?\s*[-–]?\s*(.*)$"""
    )
    private val INVISIBLE = Regex("[‎‏‪-‮⁦-⁩﻿]")

    private class Raw(val a: Int, val b: Int, val c: Int, val h: Int, val m: Int, val s: Int, val ampm: String?, val rest: String)

    fun parseWhatsApp(text: String, title: String = WHATSAPP, zone: ZoneId = ZoneId.systemDefault()): ParsedChat? {
        val raws = mutableListOf<Pair<Raw, StringBuilder>>()
        text.lineSequence().forEach { line0 ->
            val line = line0.replace(INVISIBLE, "").replace(' ', ' ').replace(' ', ' ')
            val m = WA_LINE.matchEntire(line)
            if (m != null) {
                val g = m.groupValues
                raws += Raw(g[1].toInt(), g[2].toInt(), g[3].toInt(), g[4].toInt(), g[5].toInt(), g[6].toIntOrNull() ?: 0,
                    g[7].ifBlank { null }, g[8]) to StringBuilder()
            } else if (raws.isNotEmpty()) {
                raws.last().second.append('\n').append(line)
            }
        }
        if (raws.isEmpty()) return null
        // Порядок дня и месяца: ищем подсказку (число > 12), иначе — по стилю (AM/PM со слэшем — американский).
        val yearFirst = raws.first().first.a > 31
        val dayFirst = when {
            yearFirst -> false
            raws.any { it.first.a > 12 } -> true
            raws.any { it.first.b > 12 } -> false
            else -> !(raws.any { it.first.ampm != null } && text.contains('/'))
        }
        val messages = raws.mapNotNull { (r, more) ->
            val (y0, mo, d) = when {
                yearFirst -> Triple(r.a, r.b, r.c)
                dayFirst -> Triple(r.c, r.b, r.a)
                else -> Triple(r.c, r.a, r.b)
            }
            val year = if (y0 < 100) 2000 + y0 else y0
            var hour = r.h
            r.ampm?.lowercase()?.let { ap -> if (ap.startsWith("p") && hour < 12) hour += 12; if (ap.startsWith("a") && hour == 12) hour = 0 }
            val time = runCatching { LocalDateTime.of(year, mo, d, hour, r.m, r.s).atZone(zone).toInstant().toEpochMilli() }.getOrNull()
                ?: return@mapNotNull null
            val idx = r.rest.indexOf(": ")
            val (author, body) = if (idx > 0) r.rest.substring(0, idx).trim() to r.rest.substring(idx + 2) else "" to r.rest
            ParsedMessage(time, author, (body + more).trim())
        }
        return ParsedChat(WHATSAPP, title, messages).takeIf { messages.isNotEmpty() }
    }

    /** «WhatsApp Chat with Анна.txt», «Чат WhatsApp с Анна.txt» → «Анна». */
    fun whatsAppTitle(fileName: String): String {
        val base = fileName.substringAfterLast('/').substringBeforeLast('.')
        val cleaned = base
            .replace(Regex("(?iu)^(whatsapp chat with|whatsapp chat -|чат whatsapp с|чат whatsapp з|чат в whatsapp с)\\s*"), "")
            .replace(Regex("(?iu)^_?chat$"), "")
            .trim()
        return cleaned.ifBlank { WHATSAPP }
    }

    // ---------------- Telegram JSON ----------------

    fun parseTelegramJson(text: String, zone: ZoneId = ZoneId.systemDefault()): ParsedChat? = runCatching {
        var root = JSONObject(text)
        if (!root.has("messages")) {
            // Выгрузка всех чатов: берём первый личный.
            val list = root.optJSONObject("chats")?.optJSONArray("list") ?: return null
            root = (0 until list.length()).map { list.getJSONObject(it) }.firstOrNull { it.optString("type") == "personal_chat" }
                ?: list.optJSONObject(0) ?: return null
        }
        val arr = root.getJSONArray("messages")
        val msgs = (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            if (o.optString("type") != "message") return@mapNotNull null
            val time = o.optString("date_unixtime").toLongOrNull()?.times(1000)
                ?: runCatching { LocalDateTime.parse(o.optString("date")).atZone(zone).toInstant().toEpochMilli() }.getOrNull()
                ?: return@mapNotNull null
            var body = telegramText(o.opt("text"))
            if (body.isBlank()) body = when {
                o.has("photo") -> "[фото]"
                o.has("sticker_emoji") -> o.optString("sticker_emoji")
                o.optString("media_type") == "voice_message" -> "[голосовое сообщение]"
                o.optString("media_type") == "video_message" -> "[видеосообщение]"
                o.has("file") -> "[файл]"
                else -> ""
            }
            if (body.isBlank()) return@mapNotNull null
            ParsedMessage(time, o.optString("from").takeIf { it.isNotBlank() && it != "null" } ?: "?", body)
        }
        ParsedChat(TELEGRAM, root.optString("name").ifBlank { TELEGRAM }, msgs).takeIf { msgs.isNotEmpty() }
    }.getOrNull()

    private fun telegramText(v: Any?): String = when (v) {
        is String -> v
        is JSONArray -> (0 until v.length()).joinToString("") { i ->
            when (val part = v.get(i)) {
                is String -> part
                is JSONObject -> part.optString("text")
                else -> ""
            }
        }
        else -> ""
    }

    // ---------------- Telegram HTML ----------------

    private val HTML_MESSAGE = Regex("""<div class="message default[^"]*"[^>]*>""")
    private val HTML_DATE = Regex("""title="(\d{2})\.(\d{2})\.(\d{4}) (\d{2}):(\d{2}):(\d{2})(?: UTC([+-]\d{2}):(\d{2}))?"""")
    private val HTML_FROM = Regex("""<div class="from_name">\s*(.*?)\s*</div>""", RegexOption.DOT_MATCHES_ALL)
    private val HTML_TEXT = Regex("""<div class="text">\s*(.*?)\s*</div>""", RegexOption.DOT_MATCHES_ALL)
    private val HTML_TITLE = Regex("""<div class="page_header">.*?<div class="text bold">\s*(.*?)\s*</div>""", RegexOption.DOT_MATCHES_ALL)

    fun parseTelegramHtml(pages: List<String>, zone: ZoneId = ZoneId.systemDefault()): ParsedChat? {
        var title = TELEGRAM
        var lastAuthor = "?"
        val msgs = mutableListOf<ParsedMessage>()
        pages.forEachIndexed { pi, page ->
            if (pi == 0) HTML_TITLE.find(page)?.let { title = htmlToText(it.groupValues[1]).ifBlank { TELEGRAM } }
            val starts = HTML_MESSAGE.findAll(page).map { it.range.first }.toList()
            starts.forEachIndexed { i, start ->
                val chunk = page.substring(start, starts.getOrNull(i + 1) ?: page.length)
                val d = HTML_DATE.find(chunk)?.groupValues ?: return@forEachIndexed
                val local = LocalDateTime.of(d[3].toInt(), d[2].toInt(), d[1].toInt(), d[4].toInt(), d[5].toInt(), d[6].toInt())
                val time = if (d[7].isNotEmpty()) {
                    val sign = if (d[7].startsWith("-")) -1 else 1
                    val offset = ZoneOffset.ofHoursMinutes(d[7].toInt(), sign * d[8].toInt())
                    OffsetDateTime.of(local, offset).toInstant().toEpochMilli()
                } else local.atZone(zone).toInstant().toEpochMilli()
                HTML_FROM.find(chunk)?.let { lastAuthor = htmlToText(it.groupValues[1]).ifBlank { lastAuthor } }
                val body = HTML_TEXT.find(chunk)?.let { htmlToText(it.groupValues[1]) }
                    ?: if (chunk.contains("photo_wrap")) "[фото]" else if (chunk.contains("media_voice_message")) "[голосовое сообщение]" else ""
                if (body.isNotBlank()) msgs += ParsedMessage(time, lastAuthor, body)
            }
        }
        return ParsedChat(TELEGRAM, title, msgs).takeIf { msgs.isNotEmpty() }
    }

    private fun htmlToText(html: String): String = html
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&#39;", "'").replace("&apos;", "'").replace("&nbsp;", " ")
        .replace(Regex("&#(\\d+);")) { it.groupValues[1].toIntOrNull()?.let { c -> String(Character.toChars(c)) } ?: "" }
        .replace("&amp;", "&")
        .trim()

    /** messages.html, messages2.html, … messages10.html — по порядку. */
    private fun htmlOrder(name: String) = Regex("(\\d+)\\.html?$").find(name)?.groupValues?.get(1)?.toIntOrNull() ?: 1

    // ---------------- файлы ----------------

    private fun NamedFile.isZip() = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()

    private fun NamedFile.text() = bytes.decodeToString().removePrefix("﻿")

    private fun unzip(f: NamedFile): List<NamedFile> = runCatching {
        val out = mutableListOf<NamedFile>()
        ZipInputStream(ByteArrayInputStream(f.bytes)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val n = e.name.substringAfterLast('/')
                if (!e.isDirectory && (n.endsWith(".txt", true) || n.endsWith(".json", true) || n.endsWith(".html", true))) {
                    // У .zip из WhatsApp внутри «_chat.txt» — название берём из имени архива.
                    val name = if (n.equals("_chat.txt", true)) f.name.substringBeforeLast('.') + ".txt" else n
                    out += NamedFile(name, zip.readBytes())
                }
            }
        }
        out
    }.getOrDefault(emptyList())
}
