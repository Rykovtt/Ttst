package com.kartoteka.app.messaging

import com.kartoteka.app.i18n.t

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.widget.Toast
import com.kartoteka.app.data.ArchiveLogic
import com.kartoteka.app.data.ContactItem
import com.kartoteka.app.data.ContactType

/** Звонки, SMS и мессенджеры через системные интенты. */
object Messaging {
    const val WHATSAPP = "com.whatsapp"
    const val TELEGRAM = "org.telegram.messenger"
    const val VIBER = "com.viber.voip"

    fun isInstalled(context: Context, pkg: String): Boolean =
        runCatching { context.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)

    fun dial(context: Context, phone: String) =
        start(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phone))))

    fun sms(context: Context, phones: List<String>, text: String = "") {
        // Большинство SMS-приложений понимают несколько номеров через «;», Samsung — через «,».
        val sep = if (Build.MANUFACTURER.equals("samsung", true)) "," else ";"
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + phones.joinToString(sep) { Uri.encode(it) }))
        if (text.isNotEmpty()) intent.putExtra("sms_body", text)
        start(context, intent)
    }

    /** Отправка SMS напрямую, без открытия приложения (нужно разрешение SEND_SMS). */
    fun sendSmsDirect(context: Context, phone: String, text: String) {
        val manager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            context.getSystemService(SmsManager::class.java)
        else @Suppress("DEPRECATION") SmsManager.getDefault()
        val parts = manager.divideMessage(text)
        manager.sendMultipartTextMessage(phone, null, parts, null, null)
    }

    fun whatsapp(context: Context, phone: String, text: String = "") {
        val digits = ArchiveLogic.normalizePhone(phone).removePrefix("+")
        val url = "https://wa.me/${digits}" + if (text.isNotEmpty()) "?text=" + Uri.encode(text) else ""
        start(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    /** Ссылка на чат Telegram по @username или номеру. */
    fun telegramUrl(handle: String): String {
        val h = handle.trim()
        val isPhone = h.count { it.isDigit() } >= 7 && !h.any { it.isLetter() }
        return if (isPhone) "https://t.me/" + ArchiveLogic.normalizePhone(h)
        else "https://t.me/" + h.removePrefix("@").substringAfterLast("t.me/")
    }

    /** Telegram: открываем чат с черновиком; текст также копируется на случай, если черновик не подставится. */
    fun telegram(context: Context, handle: String, text: String = "") {
        if (text.isNotEmpty()) copy(context, text)
        val url = telegramUrl(handle) + if (text.isNotEmpty()) "?text=" + Uri.encode(text) else ""
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        if (isInstalled(context, TELEGRAM)) intent.setPackage(TELEGRAM)
        start(context, intent)
    }

    fun viber(context: Context, phone: String) {
        val p = ArchiveLogic.normalizePhone(phone)
        start(context, Intent(Intent.ACTION_VIEW, Uri.parse("viber://chat?number=" + Uri.encode(p))))
    }

    fun email(context: Context, emails: List<String>, subject: String = "", text: String = "") {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
            putExtra(Intent.EXTRA_EMAIL, emails.toTypedArray())
            if (subject.isNotEmpty()) putExtra(Intent.EXTRA_SUBJECT, subject)
            if (text.isNotEmpty()) putExtra(Intent.EXTRA_TEXT, text)
        }
        start(context, intent)
    }

    /** «Поделиться» текстом — можно выбрать любой чат/группу в любом мессенджере. */
    fun share(context: Context, text: String, pkg: String? = null) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            if (pkg != null) setPackage(pkg)
        }
        if (pkg != null) start(context, intent)
        else start(context, Intent.createChooser(intent, t("Отправить через…")))
    }

    fun openLink(context: Context, item: ContactItem) {
        val v = item.value.trim()
        when (item.contactType) {
            ContactType.PHONE -> dial(context, v)
            ContactType.EMAIL -> email(context, listOf(v))
            ContactType.TELEGRAM -> telegram(context, v)
            ContactType.WHATSAPP -> whatsapp(context, v)
            ContactType.VIBER -> viber(context, v)
            ContactType.INSTAGRAM -> web(context, if (v.startsWith("http")) v else "https://instagram.com/" + v.removePrefix("@"))
            ContactType.VK -> web(context, if (v.startsWith("http")) v else "https://vk.com/" + v.removePrefix("@"))
            ContactType.FACEBOOK -> web(context, if (v.startsWith("http")) v else "https://facebook.com/${v}")
            ContactType.WEBSITE -> web(context, if (v.startsWith("http")) v else "https://${v}")
            ContactType.OTHER -> copy(context, v)
        }
    }

    /** Открывает адрес в навигаторе / картах телефона. */
    fun navigate(context: Context, place: com.kartoteka.app.data.Place, app: String? = null) {
        val q = Uri.encode(place.address)
        // Названный навигатор («через Waze», «в гугл картах»); нет его на телефоне — обычные карты.
        val direct = when (app) {
            "waze" -> Intent(Intent.ACTION_VIEW, Uri.parse(
                if (place.hasCoords) "https://waze.com/ul?ll=${place.lat},${place.lng}&navigate=yes" else "https://waze.com/ul?q=$q&navigate=yes"
            )).setPackage("com.waze")
            "google" -> Intent(Intent.ACTION_VIEW, Uri.parse(
                if (place.hasCoords) "google.navigation:q=${place.lat},${place.lng}" else "google.navigation:q=$q"
            )).setPackage("com.google.android.apps.maps")
            else -> null
        }
        if (direct != null) {
            if (context !is android.app.Activity) direct.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(direct) }.isSuccess) return
        }
        val uri = if (place.hasCoords) "geo:${place.lat},${place.lng}?q=${place.lat},${place.lng}(${Uri.encode(place.address.ifBlank { place.placeKind.title })})"
        else "geo:0,0?q=${q}"
        start(context, Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
    }

    /** Маршрут в любое место по тексту («Киевская 5», «ближайшая заправка») в выбранном навигаторе. */
    fun navigateTo(context: Context, query: String, app: String? = null) {
        val q = Uri.encode(query)
        val direct = when (app) {
            "waze" -> Intent(Intent.ACTION_VIEW, Uri.parse("https://waze.com/ul?q=$q&navigate=yes")).setPackage("com.waze")
            "google" -> Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$q")).setPackage("com.google.android.apps.maps")
            "yandex" -> Intent(Intent.ACTION_VIEW, Uri.parse("yandexnavi://map_search?text=$q")).setPackage("ru.yandex.yandexnavi")
            "organic" -> Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$q")).setPackage("app.organicmaps")
            else -> null
        }
        if (direct != null) {
            if (context !is android.app.Activity) direct.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(direct) }.isSuccess) return
        }
        start(context, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$q")))
    }

    fun web(context: Context, url: String) = start(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    /** Копирование с пометкой «секретно» и автоочисткой буфера через 30 секунд. */
    fun copy(context: Context, text: String) = com.kartoteka.app.security.SecureClipboard.copy(context, text)

    private fun start(context: Context, intent: Intent) {
        try {
            if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, t("Нет приложения для этого действия"), Toast.LENGTH_SHORT).show()
        }
    }
}
