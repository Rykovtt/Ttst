package com.rykov.autosend.core

/**
 * Одна «взведённая» команда отправки от CRM.
 *
 * Без активной сессии служба ничего не нажимает. Сессия даёт ровно одну попытку
 * клика: [takeForClick] снимает её, поэтому повторного нажатия (и зацикливания) быть не может.
 * Если кнопка так и не нашлась, сессия истекает по таймауту.
 */
class SendSession(private val clock: () -> Long) {

    data class Request(
        /** null — любой мессенджер из [TargetApps]. */
        val packageName: String?,
        val requestId: String?,
        val replyPackage: String?,
        val expiresAt: Long,
    )

    var current: Request? = null
        private set

    fun arm(packageName: String?, requestId: String?, replyPackage: String?, ttlMs: Long): Request {
        val ttl = ttlMs.coerceIn(MIN_TTL_MS, MAX_TTL_MS)
        return Request(packageName, requestId, replyPackage, clock() + ttl).also { current = it }
    }

    fun isArmedFor(packageName: String?): Boolean {
        val request = current ?: return false
        if (packageName == null || packageName !in TargetApps.packageNames) return false
        if (clock() >= request.expiresAt) return false
        return request.packageName == null || request.packageName == packageName
    }

    /** Забирает сессию под единственную попытку клика. */
    fun takeForClick(packageName: String?): Request? {
        if (!isArmedFor(packageName)) return null
        return current.also { current = null }
    }

    /** Снимает и возвращает сессию, если её время вышло. */
    fun expireIfDue(): Request? {
        val request = current ?: return null
        if (clock() < request.expiresAt) return null
        current = null
        return request
    }

    fun cancel(): Request? = current.also { current = null }

    companion object {
        const val DEFAULT_TTL_MS = 15_000L
        const val MIN_TTL_MS = 3_000L
        const val MAX_TTL_MS = 60_000L
    }
}
