package com.rykov.autosend.core

/**
 * Публичный протокол обмена Intent-ами между CRM и этим приложением.
 * Копия констант для клиента — com.kartoteka.app.messaging.AutoSendLink в RVault.
 */
object AutoSendContract {
    const val PACKAGE = "com.rykov.autosend"

    /** CRM → служба: нажать «Отправить» один раз, когда чат откроется. */
    const val ACTION_ARM_SEND = "com.rykov.autosend.action.ARM_SEND"
    /** CRM → служба: отменить взведённую команду. */
    const val ACTION_CANCEL = "com.rykov.autosend.action.CANCEL"
    /** Служба → CRM: итог команды (заполняется в PendingIntent из [EXTRA_CALLBACK]). */
    const val ACTION_SEND_RESULT = "com.rykov.autosend.action.SEND_RESULT"

    /**
     * PendingIntent, обязательно (FLAG_MUTABLE, явный адресат). Служба по нему узнаёт,
     * кто прислал команду (создателя PendingIntent подделать нельзя), и через него же отвечает.
     */
    const val EXTRA_CALLBACK = "callback"
    /** String: пакет мессенджера (com.whatsapp, ...). Без него — любой из поддерживаемых. */
    const val EXTRA_TARGET_PACKAGE = "target_package"
    /**
     * String, необязательно: ссылка на чат. Служба сама откроет её в [EXTRA_TARGET_PACKAGE] —
     * CRM из фона окно открыть не может, а служба специальных возможностей может.
     */
    const val EXTRA_OPEN_URI = "open_uri"
    /**
     * String, необязательно: текст, который служба впишет в поле ввода, если мессенджер
     * не подставил его по ссылке (Telegram по номеру, Viber). Не сохраняется.
     */
    const val EXTRA_TEXT = "text"
    /** String, необязательно: идентификатор запроса, вернётся в ответе. */
    const val EXTRA_REQUEST_ID = "request_id"
    /** Long, необязательно: сколько ждать кнопку, мс (3000–60000, по умолчанию 15000). */
    const val EXTRA_TIMEOUT_MS = "timeout_ms"

    /** String в ответе: одно из STATUS_*. */
    const val EXTRA_STATUS = "status"

    const val STATUS_SENT = "sent"
    const val STATUS_CLICK_FAILED = "click_failed"
    const val STATUS_TIMEOUT = "timeout"
    const val STATUS_CANCELLED = "cancelled"
    const val STATUS_SERVICE_DISABLED = "service_disabled"
    const val STATUS_UNSUPPORTED_PACKAGE = "unsupported_package"
    const val STATUS_OPEN_FAILED = "open_failed"
    const val STATUS_UNTRUSTED_CALLER = "untrusted_caller"
}
