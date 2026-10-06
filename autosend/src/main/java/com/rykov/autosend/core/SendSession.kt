package com.rykov.autosend.core

/**
 * Одна «взведённая» команда отправки от CRM.
 *
 * Без активной сессии служба ничего не нажимает. Сессия даёт ровно одну попытку
 * клика: [takeForClick] снимает её, поэтому повторного нажатия (и зацикливания) быть не может.
 * Если кнопка так и не нашлась, сессия истекает по таймауту.
 *
 * [R] — адрес ответа (PendingIntent на устройстве); в тестах — что угодно.
 */
class SendSession<R>(private val clock: () -> Long) {

    data class Request<R>(
        /** null — любой мессенджер из [TargetApps]. */
        val packageName: String?,
        val requestId: String?,
        val replyTo: R?,
        val expiresAt: Long,
        /** Текст для пустого поля ввода; вписывается не более одного раза. */
        val text: String? = null,
    )

    var current: Request<R>? = null
        private set
    private var textUsed = false

    fun arm(packageName: String?, requestId: String?, replyTo: R?, ttlMs: Long, text: String? = null): Request<R> {
        val ttl = ttlMs.coerceIn(MIN_TTL_MS, MAX_TTL_MS)
        textUsed = false
        return Request(packageName, requestId, replyTo, clock() + ttl, text?.takeIf { it.isNotBlank() }).also { current = it }
    }

    fun isArmedFor(packageName: String?): Boolean {
        val request = current ?: return false
        if (packageName == null || packageName !in TargetApps.packageNames) return false
        if (clock() >= request.expiresAt) return false
        return request.packageName == null || request.packageName == packageName
    }

    /** Текст для вставки в пустое поле — один раз за команду. */
    fun takeTextToInsert(packageName: String?): String? {
        if (textUsed || !isArmedFor(packageName)) return null
        val text = current?.text ?: return null
        textUsed = true
        return text
    }

    /** Забирает сессию под единственную попытку клика. */
    fun takeForClick(packageName: String?): Request<R>? {
        if (!isArmedFor(packageName)) return null
        return current.also { current = null }
    }

    /** Снимает и возвращает сессию, если её время вышло. */
    fun expireIfDue(): Request<R>? {
        val request = current ?: return null
        if (clock() < request.expiresAt) return null
        current = null
        return request
    }

    fun cancel(): Request<R>? = current.also { current = null }

    companion object {
        const val DEFAULT_TTL_MS = 15_000L
        const val MIN_TTL_MS = 3_000L
        const val MAX_TTL_MS = 60_000L
    }
}
