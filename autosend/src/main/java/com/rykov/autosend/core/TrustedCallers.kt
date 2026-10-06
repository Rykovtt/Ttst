package com.rykov.autosend.core

/**
 * Кому разрешено управлять службой: пакет CRM и SHA-256 его сертификата подписи.
 * Само это приложение (экран «Проверка») доверено всегда.
 */
object TrustedCallers {
    /** RVault (com.rykov.rvault), ключ app/kartoteka.keystore. */
    private val trusted: Map<String, Set<String>> = mapOf(
        "com.rykov.rvault" to setOf("a6599a0653fcef4944cf5e70bc28627f5471663d57e76d9410645cb994ff7296"),
    )

    fun isTrusted(callerPackage: String?, certSha256: Collection<String>, selfPackage: String): Boolean {
        if (callerPackage.isNullOrBlank()) return false
        if (callerPackage == selfPackage) return true
        val allowed = trusted[callerPackage] ?: return false
        return certSha256.any { normalize(it) in allowed }
    }

    fun knows(callerPackage: String?): Boolean = callerPackage in trusted

    /** "AB:CD:..." или "abcd..." → "abcd...". */
    fun normalize(digest: String): String = digest.replace(":", "").trim().lowercase()

    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
}
