package com.kartoteka.app.assistant

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Музыка и видео по голосу. Как «включить», а не «показать поиск»:
 *  1) плеер уже открыт и дан доступ к уведомлениям — команда «играй по запросу» прямо в его сеанс;
 *  2) YouTube / YouTube Music — находим конкретное видео/плейлист и открываем его ссылкой: так они сразу играют;
 *  3) остальные (Spotify и др.) — системная команда «играть по запросу», которую они выполняют сами.
 * Перемешивание, пауза, «дальше» — через сеанс плеера (доступ к уведомлениям) или системные медиакнопки.
 */
object NoaMedia {
    const val YT = "com.google.android.youtube"
    const val YT_MUSIC = "com.google.android.apps.youtube.music"
    const val SPOTIFY = "com.spotify.music"

    /** Что именно включить: готовая ссылка (видео/плейлист) или запрос для плеера. */
    data class Target(val pkg: String?, val label: String, val url: String? = null, val title: String? = null)

    enum class Control { PAUSE, RESUME, NEXT, PREV, SHUFFLE_ON, SHUFFLE_OFF, REPEAT, STOP, LOUDER, QUIETER, WHAT }

    // ---------- поиск на YouTube (только по команде человека; в запросе — только то, что он сказал) ----------

    data class Found(val videoId: String?, val playlistId: String?, val title: String?)

    /** Страница YouTube (настольная разметка, без аккаунта и ключей), первые ~1.6 МБ. */
    private fun page(url: String, timeoutMs: Int): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = timeoutMs; c.readTimeout = timeoutMs
        // Настольная версия: мобильная перенаправляет на m.youtube.com с другой разметкой.
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36")
        c.setRequestProperty("Accept-Language", "uk,ru;q=0.9,en;q=0.8")
        c.setRequestProperty("Cookie", "CONSENT=YES+1; SOCS=CAI")
        val html = c.inputStream.bufferedReader().use { r ->
            // Результаты — в начале ytInitialData (~750 КБ от начала страницы); читаем до 1.6 МБ.
            val buf = CharArray(1_600_000); var n = 0
            while (n < buf.size) { val k = r.read(buf, n, buf.size - n); if (k < 0) break; n += k }
            String(buf, 0, n)
        }
        c.disconnect()
        return html
    }

    /** Первое видео / плейлист по запросу со страницы поиска YouTube. */
    fun searchYoutube(query: String, playlist: Boolean, timeoutMs: Int = 6000): Found? = runCatching {
        val sp = if (playlist) "&sp=EgIQAw%253D%253D" else "&sp=EgIQAQ%253D%253D"
        parseResults(page("https://www.youtube.com/results?search_query=" + URLEncoder.encode(query, "UTF-8") + sp, timeoutMs), playlist)
    }.getOrNull()

    data class Channel(val id: String, val name: String?)

    /** Канал по названию: первый результат поиска среди каналов (YouTube сам прощает неточное написание). */
    fun searchChannel(query: String, timeoutMs: Int = 6000): Channel? = runCatching {
        parseChannel(page("https://www.youtube.com/results?search_query=" + URLEncoder.encode(query, "UTF-8") + "&sp=EgIQAg%253D%253D", timeoutMs))
    }.getOrNull()

    fun parseChannel(html: String): Channel? {
        val data = html.substring(html.indexOf("ytInitialData").takeIf { it >= 0 } ?: 0)
        val m = Regex("\"channelRenderer\":\\{\"channelId\":\"(UC[\\w-]{22})\"").find(data) ?: return null
        val name = Regex("\"title\":\\{\"simpleText\":\"((?:[^\"\\\\]|\\\\.){1,100})\"").find(data, m.range.last)?.groupValues?.get(1)
        return Channel(m.groupValues[1], name)
    }

    /** Последние видео канала (со страницы «Видео»): id и названия. */
    fun channelVideos(channelId: String, timeoutMs: Int = 7000): List<Found> = runCatching {
        parseChannelVideos(page("https://www.youtube.com/channel/$channelId/videos", timeoutMs))
    }.getOrDefault(emptyList())

    fun parseChannelVideos(html: String): List<Found> {
        val data = html.substring(html.indexOf("ytInitialData").takeIf { it >= 0 } ?: 0)
        val ids = Regex("\"contentId\":\"([\\w-]{11})\"").findAll(data).map { it.groupValues[1] }.distinct().toList()
        val titles = Regex("\"lockupMetadataViewModel\":\\{\"title\":\\{\"content\":\"((?:[^\"\\\\]|\\\\.){1,120})\"").findAll(data).map { it.groupValues[1] }.toList()
        return ids.mapIndexed { i, id -> Found(id, null, titles.getOrNull(i)) }
    }

    /** Разбор ytInitialData: id и заголовок первого подходящего результата. */
    fun parseResults(html: String, playlist: Boolean): Found? {
        val start = html.indexOf("ytInitialData").takeIf { it >= 0 } ?: 0
        val data = html.substring(start)
        if (playlist) {
            Regex("\"playlistId\":\"((?:PL|OL|RD|VL)[\\w-]{10,})\"").find(data)?.let { m ->
                val pid = m.groupValues[1].removePrefix("VL")
                val video = Regex("\"watchEndpoint\":\\{\"videoId\":\"([\\w-]{11})\",\"playlistId\":\"" + Regex.escape(m.groupValues[1]))
                    .find(data)?.groupValues?.get(1)
                val name = Regex("\"lockupMetadataViewModel\":\\{\"title\":\\{\"content\":\"((?:[^\"\\\\]|\\\\.){1,120})\"").find(data)?.groupValues?.get(1)
                return Found(video, pid, name ?: title(data, m.range.first))
            }
        }
        val v = Regex("\"videoRenderer\":\\{\"videoId\":\"([\\w-]{11})\"").find(data)
            ?: Regex("\"videoId\":\"([\\w-]{11})\"").find(data) ?: return null
        return Found(v.groupValues[1], null, title(data, v.range.first))
    }

    private fun title(data: String, from: Int): String? =
        Regex("\"title\":\\{\"runs\":\\[\\{\"text\":\"((?:[^\"\\\\]|\\\\.){1,120})\"").find(data, from)?.groupValues?.get(1)
            ?.replace("\\u0026", "&")?.replace("\\\"", "\"")

    /** Ссылка, по которой приложение сразу начинает играть. */
    fun playUrl(pkg: String, f: Found): String? {
        val host = if (pkg == YT_MUSIC) "https://music.youtube.com" else "https://www.youtube.com"
        return when {
            f.videoId != null && f.playlistId != null -> "$host/watch?v=${f.videoId}&list=${f.playlistId}"
            f.playlistId != null -> "$host/watch?list=${f.playlistId}"
            f.videoId != null -> "$host/watch?v=${f.videoId}"
            else -> null
        }
    }

    /** Метка в запросе: «плейлист Понравившиеся» (в YouTube Music — LM, в YouTube — LL), а не слова для поиска. */
    const val LIKED_QUERY = "понравившиеся"

    fun isLiked(query: String) = query.trim().equals(LIKED_QUERY, ignoreCase = true)

    fun likedUrl(pkg: String) = if (pkg == YT_MUSIC) "https://music.youtube.com/watch?list=LM" else "https://www.youtube.com/watch?list=LL"

    // ---------- запуск ----------

    fun installed(context: Context, pkg: String) =
        runCatching { context.packageManager.getLaunchIntentForPackage(pkg) != null }.getOrDefault(false)

    /** Музыкальное приложение по умолчанию: то, что играло последним, иначе YouTube Music, Spotify. */
    fun defaultMusic(context: Context): String? =
        NoaNotifications.controllers(context).map { it.packageName }.firstOrNull { it != YT && it != context.packageName }
            ?: listOf(YT_MUSIC, SPOTIFY, "deezer.android.app", "com.apple.android.music").firstOrNull { installed(context, it) }

    /** Открыть найденное (вызывается из Activity-контекста, сразу после ответа). */
    fun open(context: Context, pkg: String?, url: String?, query: String, playlist: Boolean, artist: Boolean, shuffle: Boolean): Boolean {
        var ok = false
        if (url != null) {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { pkg?.let { setPackage(it) } }
            ok = start(context, i) || start(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
        if (!ok && pkg != null && query.isNotBlank()) {
            // Плеер уже открыт — команда прямо в его сеанс.
            NoaNotifications.player(context, pkg)?.let { mc ->
                runCatching { mc.transportControls.playFromSearch(query, searchExtras(query, playlist, artist)) }.onSuccess { ok = true }
            }
        }
        if (!ok) ok = PhoneActions.play(context, query, pkg?.let { PhoneActions.App(it, it) }, playlist, artist)
        if (shuffle) later(4000) { control(context, Control.SHUFFLE_ON, pkg) }
        return ok
    }

    private fun searchExtras(query: String, playlist: Boolean, artist: Boolean) = Bundle().apply {
        putString(android.provider.MediaStore.EXTRA_MEDIA_FOCUS, when {
            playlist -> "vnd.android.cursor.item/playlist"
            artist -> android.provider.MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE
            else -> "vnd.android.cursor.item/*"
        })
        if (artist) putString(android.provider.MediaStore.EXTRA_MEDIA_ARTIST, query)
        if (playlist) putString("android.intent.extra.playlist", query)
    }

    // ---------- управление ----------

    /** Пауза/дальше/перемешать… Возвращает false, если нужное действие недоступно без доступа к уведомлениям. */
    fun control(context: Context, c: Control, pkg: String? = null): Boolean {
        val mc = NoaNotifications.player(context, pkg) ?: NoaNotifications.player(context)
        val am = context.getSystemService(AudioManager::class.java)
        if (mc != null) {
            val tc = mc.transportControls
            return runCatching {
                when (c) {
                    Control.PAUSE -> tc.pause()
                    Control.RESUME -> tc.play()
                    Control.NEXT -> tc.skipToNext()
                    Control.PREV -> tc.skipToPrevious()
                    Control.STOP -> tc.stop()
                    Control.SHUFFLE_ON -> shuffleCompat(context, mc, true)
                    Control.SHUFFLE_OFF -> shuffleCompat(context, mc, false)
                    Control.REPEAT -> shuffleCompat(context, mc, null)
                    Control.LOUDER -> mc.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI).also { mc.adjustVolume(AudioManager.ADJUST_RAISE, 0) }
                    Control.QUIETER -> mc.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI).also { mc.adjustVolume(AudioManager.ADJUST_LOWER, 0) }
                    Control.WHAT -> Unit
                }
            }.isSuccess
        }
        // Без доступа к уведомлениям: системные медиакнопки и громкость.
        fun key(code: Int) { am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code)); am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code)) }
        return when (c) {
            Control.PAUSE -> { key(KeyEvent.KEYCODE_MEDIA_PAUSE); true }
            Control.RESUME -> { key(KeyEvent.KEYCODE_MEDIA_PLAY); true }
            Control.NEXT -> { key(KeyEvent.KEYCODE_MEDIA_NEXT); true }
            Control.PREV -> { key(KeyEvent.KEYCODE_MEDIA_PREVIOUS); true }
            Control.STOP -> { key(KeyEvent.KEYCODE_MEDIA_STOP); true }
            Control.LOUDER -> { repeat(2) { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, if (it == 0) AudioManager.FLAG_SHOW_UI else 0) }; true }
            Control.QUIETER -> { repeat(2) { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, if (it == 0) AudioManager.FLAG_SHOW_UI else 0) }; true }
            else -> false
        }
    }

    /** Перемешивание/повтор через MediaControllerCompat (его понимают YouTube Music, Spotify и др.). */
    private fun shuffleCompat(context: Context, mc: android.media.session.MediaController, shuffle: Boolean?) {
        val token = android.support.v4.media.session.MediaSessionCompat.Token.fromToken(mc.sessionToken)
        val compat = android.support.v4.media.session.MediaControllerCompat(context, token)
        if (shuffle == null) compat.transportControls.setRepeatMode(android.support.v4.media.session.PlaybackStateCompat.REPEAT_MODE_ALL)
        else compat.transportControls.setShuffleMode(
            if (shuffle) android.support.v4.media.session.PlaybackStateCompat.SHUFFLE_MODE_ALL
            else android.support.v4.media.session.PlaybackStateCompat.SHUFFLE_MODE_NONE)
    }

    /** «Что сейчас играет». */
    fun nowPlaying(context: Context): String? {
        val mc = NoaNotifications.player(context) ?: return null
        val md = mc.metadata ?: return null
        val title = md.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: return null
        val artist = md.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST)
            ?: md.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        val playing = mc.playbackState?.state == PlaybackState.STATE_PLAYING
        return listOfNotNull(artist, title).joinToString(" — ") + if (playing) "" else " (" + com.kartoteka.app.i18n.t("пауза") + ")"
    }

    private fun later(ms: Long, block: () -> Unit) { Handler(Looper.getMainLooper()).postDelayed({ runCatching(block) }, ms) }

    private fun start(context: Context, i: Intent): Boolean = try {
        if (context !is android.app.Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(i); true
    } catch (e: Exception) { false }
}
