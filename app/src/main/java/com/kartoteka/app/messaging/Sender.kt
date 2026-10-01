package com.kartoteka.app.messaging

import com.kartoteka.app.i18n.t

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.kartoteka.app.data.NotifyChannel
import com.kartoteka.app.data.PersonFull

/** Отправка одного сообщения человеку выбранным способом — максимально автоматически. */
object Sender {
    enum class Result(private val messageRu: String) {
        SENT("Отправлено"),
        QUEUED("Отправляется автоматически…"),
        OPENED("Чат открыт — нажмите «Отправить»"),
        NO_CONTACT("Нет контакта для выбранного способа"),
        FAILED("Не удалось отправить"),
        ;

        val message: String get() = t(messageRu)
    }

    fun targetFor(pf: PersonFull, ch: NotifyChannel): String? = when (ch) {
        NotifyChannel.WHATSAPP -> pf.whatsapp
        NotifyChannel.TELEGRAM -> pf.telegram
        NotifyChannel.SMS -> pf.phone
        NotifyChannel.NONE -> null
    }

    fun canSmsDirect(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    /**
     * @param interactive true — пользователь сейчас в приложении, можно открыть чат для ручной отправки.
     */
    fun send(
        context: Context,
        pf: PersonFull,
        ch: NotifyChannel,
        text: String,
        delaySec: Int,
        interactive: Boolean,
        onResult: (Boolean) -> Unit,
    ): Result {
        val target = targetFor(pf, ch) ?: return Result.NO_CONTACT
        return when (ch) {
            NotifyChannel.SMS ->
                if (canSmsDirect(context)) {
                    val ok = runCatching { Messaging.sendSmsDirect(context, target, text) }.isSuccess
                    onResult(ok)
                    if (ok) Result.SENT else Result.FAILED
                } else if (interactive) {
                    Messaging.sms(context, listOf(target), text); onResult(true); Result.OPENED
                } else Result.FAILED

            NotifyChannel.WHATSAPP, NotifyChannel.TELEGRAM ->
                if (AutoSend.isServiceEnabled(context)) {
                    AutoSend.start(context, listOf(SendJob(pf.person.id, pf.person.displayName, ch, target, text)), delaySec) { _, ok -> onResult(ok) }
                    Result.QUEUED
                } else if (interactive) {
                    if (ch == NotifyChannel.WHATSAPP) Messaging.whatsapp(context, target, text) else Messaging.telegram(context, target, text)
                    onResult(true)
                    Result.OPENED
                } else Result.FAILED

            NotifyChannel.NONE -> Result.NO_CONTACT
        }
    }
}
