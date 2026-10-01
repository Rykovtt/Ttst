package com.kartoteka.app.data

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Собственный PIN-код приложения (не PIN телефона).
 * Хранится только хэш PBKDF2 с солью; после 5 ошибок — пауза 30 секунд (переживает перезапуск).
 */
class PinLock(context: Context) {
    private val prefs = context.getSharedPreferences("kartoteka_pin", Context.MODE_PRIVATE)

    val hasPin: Boolean get() = prefs.contains(HASH)
    val length: Int get() = prefs.getInt(LENGTH, 4)

    fun set(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(HASH, Base64.encodeToString(hash(pin, salt), Base64.NO_WRAP))
            .putInt(LENGTH, pin.length)
            .putInt(FAILS, 0).putLong(LOCKED_UNTIL, 0)
            .apply()
    }

    fun clear() = prefs.edit().clear().apply()

    /** Сколько миллисекунд ещё ждать после серии ошибок (0 — можно вводить). */
    fun waitMillis(now: Long = System.currentTimeMillis()): Long = (prefs.getLong(LOCKED_UNTIL, 0) - now).coerceAtLeast(0)

    fun verify(pin: String): Boolean {
        if (waitMillis() > 0) return false
        val salt = Base64.decode(prefs.getString(SALT, null) ?: return false, Base64.NO_WRAP)
        val expected = Base64.decode(prefs.getString(HASH, null) ?: return false, Base64.NO_WRAP)
        val ok = MessageDigest.isEqual(hash(pin, salt), expected)
        if (ok) {
            prefs.edit().putInt(FAILS, 0).apply()
        } else {
            val fails = prefs.getInt(FAILS, 0) + 1
            val edit = prefs.edit().putInt(FAILS, fails)
            if (fails % MAX_FAILS == 0) edit.putLong(LOCKED_UNTIL, System.currentTimeMillis() + PAUSE_MS)
            edit.apply()
        }
        return ok
    }

    private fun hash(pin: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(pin.toCharArray(), salt, 20_000, 256)).encoded

    companion object {
        private const val HASH = "hash"
        private const val SALT = "salt"
        private const val LENGTH = "length"
        private const val FAILS = "fails"
        private const val LOCKED_UNTIL = "locked_until"
        const val MAX_FAILS = 5
        const val PAUSE_MS = 30_000L
        val LENGTHS = 4..6
    }
}
