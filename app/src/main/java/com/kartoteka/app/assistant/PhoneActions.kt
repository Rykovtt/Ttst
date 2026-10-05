package com.kartoteka.app.assistant

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.widget.Toast
import com.kartoteka.app.i18n.t

/**
 * Действия на телефоне по команде ассистента: запустить приложение, передать текст в блокнот,
 * Google или любое приложение, будильник, таймер, фонарик. Только через стандартные системные
 * переходы (Intent) — внутри чужих приложений ассистент ничего не нажимает.
 */
object PhoneActions {
    data class App(val label: String, val pkg: String)

    /** Привычные названия → пакеты (метки приложений на телефоне бывают на любом языке). */
    internal val ALIASES: List<Pair<List<String>, List<String>>> = listOf(
        listOf("блокнот", "заметки", "нотатки", "notes", "notepad", "кип", "keep") to
            listOf("com.samsung.android.app.notes", "com.google.android.keep", "com.socialnmobile.dragonnote", "com.miui.notes", "com.coloros.note"),
        listOf("гугл", "google", "гугол") to listOf("com.google.android.googlequicksearchbox"),
        listOf("хром", "chrome", "браузер", "browser") to listOf("com.android.chrome", "com.sec.android.app.sbrowser"),
        listOf("ютуб мьюзик", "ютуб мюзик", "ютуб музик", "ютуб музыка", "ютуб музыку", "ютуб музика", "ютуб музику", "ютуб мьющик", "ютуб мьюзек",
            "youtube music", "yt music", "ютуб мьюзік", "ютуб мюзік") to listOf("com.google.android.apps.youtube.music"),
        listOf("ютуб", "youtube", "ютюб") to listOf("com.google.android.youtube"),
        listOf("телеграм", "telegram", "тг") to listOf("org.telegram.messenger", "org.thunderdog.challegram"),
        listOf("вотсап", "ватсап", "вацап", "whatsapp") to listOf("com.whatsapp", "com.whatsapp.w4b"),
        listOf("вайбер", "viber") to listOf("com.viber.voip"),
        listOf("инстаграм", "інстаграм", "instagram", "инста") to listOf("com.instagram.android"),
        listOf("карты", "карти", "мапи", "maps", "гугл карты") to listOf("com.google.android.apps.maps"),
        listOf("вейз", "вэйз", "waze") to listOf("com.waze"),
        listOf("камера", "camera") to listOf("com.sec.android.app.camera", "com.google.android.GoogleCamera"),
        listOf("галерея", "галерею", "gallery", "фото") to listOf("com.sec.android.gallery3d", "com.google.android.apps.photos"),
        listOf("календарь", "календар", "calendar") to listOf("com.samsung.android.calendar", "com.google.android.calendar"),
        listOf("почта", "пошта", "почту", "пошту", "gmail", "мейл") to listOf("com.google.android.gm", "com.samsung.android.email.provider"),
        listOf("калькулятор", "calculator") to listOf("com.sec.android.app.popupcalculator", "com.google.android.calculator"),
        listOf("часы", "годинник", "будильник", "clock") to listOf("com.sec.android.app.clockpackage", "com.google.android.deskclock"),
        listOf("настройки", "налаштування", "settings") to listOf("com.android.settings"),
        listOf("музыка", "музика", "spotify", "спотифай") to listOf("com.spotify.music", "com.google.android.apps.youtube.music"),
        listOf("плей маркет", "плеймаркет", "маркет", "play store", "play market") to listOf("com.android.vending"),
    )

    /** Все привычные названия приложений — парсер узнаёт по ним «открой ютуб», «скинь в телеграм». */
    val ALIAS_NAMES: Set<String> by lazy { ALIASES.flatMap { it.first }.toSet() }

    fun installed(context: Context): List<App> = runCatching {
        val pm = context.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(main, 0).map { App(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .distinctBy { it.pkg }
    }.getOrDefault(emptyList())

    /** Приложение по названию: сначала привычные имена, затем метки установленных приложений (с учётом опечаток). */
    fun find(context: Context, nameRaw: String): App? {
        val name = nameRaw.lowercase().trim().removePrefix("приложение ").removePrefix("застосунок ").trim()
        if (name.length < 2) return null
        val apps = installed(context)
        val byPkg = apps.associateBy { it.pkg }
        // «ютуб мьющик» — распознаватель коверкает «music»: ютуб + слово на «м» — это YouTube Music.
        val ytMusic = Regex("^(ютуб|ютюб|youtube|yt)\\s+(м|m)").containsMatchIn(name)
        // Самое длинное совпадение: «ютуб мьюзик» важнее «ютуб».
        ALIASES.filter { (names, _) -> names.any { name.startsWith(it) } || (ytMusic && "youtube music" in names) }
            .maxByOrNull { (names, _) -> if (ytMusic && "youtube music" in names) 100 else names.filter { name.startsWith(it) }.maxOf { it.length } }
            ?.second?.firstNotNullOfOrNull { byPkg[it] ?: if (isInstalled(context, it)) App(name, it) else null }
            ?.let { return it }
        val n = fold(name)
        val lat = fold(com.kartoteka.app.data.NameLocalizer.latin(name))
        return apps.map { it to fold(it.label.lowercase()) }.minByOrNull { (_, l) ->
            when {
                l == n || l == lat -> 0
                l.startsWith(n) || l.startsWith(lat) || n.startsWith(l) -> 1
                l.contains(n) || l.contains(lat) -> 2
                lev(l, n) <= maxOf(1, n.length / 4) || lev(l, lat) <= maxOf(1, lat.length / 4) -> 3
                else -> Int.MAX_VALUE
            }.let { if (it == Int.MAX_VALUE) it else it * 1000 + l.length }
        }?.takeIf { (_, l) ->
            l == n || l == lat || l.startsWith(n) || l.startsWith(lat) || n.startsWith(l) || l.contains(n) || l.contains(lat) ||
                lev(l, n) <= maxOf(1, n.length / 4) || lev(l, lat) <= maxOf(1, lat.length / 4)
        }?.first
    }

    private fun isInstalled(context: Context, pkg: String) =
        runCatching { context.packageManager.getLaunchIntentForPackage(pkg) != null }.getOrDefault(false)

    /** Блокнот телефона: Samsung Notes, Google Keep… — первый установленный. */
    fun notesApp(context: Context): App? = find(context, "блокнот")

    fun launch(context: Context, app: App): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(app.pkg) ?: return false
        return start(context, intent)
    }

    /** Передать текст в приложение («Поделиться» напрямую); не принимает — общий выбор приложений. */
    fun shareTo(context: Context, text: String, app: App?, subject: String? = null): Boolean {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        subject?.let { send.putExtra(Intent.EXTRA_SUBJECT, it); send.putExtra(Intent.EXTRA_TITLE, it) }
        if (app != null && start(context, Intent(send).setPackage(app.pkg), quiet = true)) return true
        return start(context, Intent.createChooser(send, subject ?: t("Отправить")))
    }

    /** Распознаётся ли фраза как название приложения («ютуб мьюзик», «телеграм»). */
    fun isAppName(name: String): Boolean {
        val n = name.lowercase().trim()
        return n in ALIAS_NAMES || ALIAS_NAMES.any { n.startsWith("$it ") } || (n.endsWith("у") && n.dropLast(1) + "а" in ALIAS_NAMES)
    }

    /**
     * Включить музыку: «играть по запросу» (понимают YouTube Music, Spotify и др.); пустой запрос — «что-нибудь / продолжить».
     * Не поддерживает — открываем приложение и жмём системную «Play».
     */
    fun play(context: Context, query: String, app: App?, playlist: Boolean, artist: Boolean = false): Boolean {
        // Подсказка «что именно» — исполнитель / плейлист: так приложение сразу играет, а не показывает поиск.
        val focus = when {
            query.isBlank() -> "vnd.android.cursor.item/*"
            playlist -> "vnd.android.cursor.item/playlist"
            artist -> android.provider.MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE
            else -> "vnd.android.cursor.item/*"
        }
        val i = Intent(android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            .putExtra(android.app.SearchManager.QUERY, query)
            .putExtra(android.provider.MediaStore.EXTRA_MEDIA_FOCUS, focus)
        if (playlist && query.isNotBlank()) i.putExtra("android.intent.extra.playlist", query)
        if (artist && query.isNotBlank()) i.putExtra(android.provider.MediaStore.EXTRA_MEDIA_ARTIST, query)
        app?.let { i.setPackage(it.pkg) }
        if (start(context, i, quiet = true)) return true
        if (app != null && launch(context, app)) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ mediaPlay(context) }, 2500)
            return true
        }
        mediaPlay(context); return true
    }

    /** Системная кнопка «Play» — продолжить последнее, что играло. */
    fun mediaPlay(context: Context) = runCatching {
        val am = context.getSystemService(android.media.AudioManager::class.java)
        am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PLAY))
        am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_PLAY))
    }

    /** Поиск в Google (приложение Google или браузер). */
    fun webSearch(context: Context, query: String): Boolean {
        val search = Intent(Intent.ACTION_WEB_SEARCH).putExtra(android.app.SearchManager.QUERY, query)
        if (start(context, search, quiet = true)) return true
        return start(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))))
    }

    fun alarm(context: Context, hour: Int, minute: Int, label: String?): Boolean = start(context,
        Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, hour).putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .apply { label?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) } })

    fun timer(context: Context, seconds: Int): Boolean = start(context,
        Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, seconds).putExtra(AlarmClock.EXTRA_SKIP_UI, true))

    fun flashlight(context: Context, on: Boolean): Boolean = runCatching {
        val cm = context.getSystemService(android.hardware.camera2.CameraManager::class.java)
        val id = cm.cameraIdList.first { cm.getCameraCharacteristics(it).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        cm.setTorchMode(id, on)
    }.isSuccess

    fun settings(context: Context, what: String?): Boolean = start(context, Intent(when (what) {
        "wifi" -> android.provider.Settings.ACTION_WIFI_SETTINGS
        "bluetooth" -> android.provider.Settings.ACTION_BLUETOOTH_SETTINGS
        "display" -> android.provider.Settings.ACTION_DISPLAY_SETTINGS
        "sound" -> android.provider.Settings.ACTION_SOUND_SETTINGS
        else -> android.provider.Settings.ACTION_SETTINGS
    }))

    private fun start(context: Context, intent: Intent, quiet: Boolean = false): Boolean = try {
        if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent); true
    } catch (e: ActivityNotFoundException) {
        if (!quiet) Toast.makeText(context, t("Нет приложения для этого действия"), Toast.LENGTH_SHORT).show()
        false
    } catch (e: SecurityException) {
        false
    }

    private fun fold(w: String): String = buildString {
        for (c in w) when (c) {
            'і', 'ї', 'ы', 'й' -> append('и'); 'є', 'ё', 'э' -> append('е'); 'ґ' -> append('г')
            ' ', '-', '_', '.', '\'', '’', 'ь' -> {}
            else -> append(c)
        }
    }

    private fun lev(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]; dp[0] = i
            for (j in 1..b.length) {
                val tmp = dp[j]
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return dp[b.length]
    }
}
