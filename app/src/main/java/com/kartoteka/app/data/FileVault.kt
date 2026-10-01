package com.kartoteka.app.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Шифрование файлов (голосовые заметки, снимки при неверном PIN-коде): AES-256-GCM,
 * ключ живёт в Android Keystore и не покидает устройство. Формат файла: IV (12 байт) + шифротекст.
 */
open class FileVault {
    open fun encryptTo(file: File): OutputStream {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val out = file.outputStream()
        out.write(cipher.iv)
        return CipherOutputStream(out, cipher)
    }

    open fun decrypt(file: File): ByteArray {
        val bytes = file.readBytes()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV))
        return cipher.doFinal(bytes, IV, bytes.size - IV)
    }

    fun write(file: File, data: ByteArray) = encryptTo(file).use { it.write(data) }

    fun write(file: File, input: InputStream) = encryptTo(file).use { input.copyTo(it) }

    protected open fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ALIAS = "rvault_files"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV = 12
    }
}
