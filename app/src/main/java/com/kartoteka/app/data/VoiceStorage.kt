package com.kartoteka.app.data

import android.content.Context
import java.io.File
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * Голосовые заметки: сырой звук PCM 16 кГц, моно, 16 бит — зашифрован [FileVault] прямо во время записи.
 * На диске нет ни одного незашифрованного байта, кроме временного файла на время прослушивания.
 */
class VoiceStorage(private val context: Context, private val vault: FileVault) {
    val dir: File get() = File(context.filesDir, "voice").apply { mkdirs() }

    fun newName(): String = "${UUID.randomUUID()}.pcm.enc"

    fun open(name: String): OutputStream = vault.encryptTo(File(dir, name))

    fun pcm(name: String): ByteArray = vault.decrypt(File(dir, name))

    fun exists(name: String) = File(dir, name).exists()

    fun delete(name: String) {
        File(dir, File(name).name).delete()
    }

    /** Сохранить готовый PCM (восстановление из резервной копии). */
    fun save(name: String, pcm: ByteArray) = vault.write(File(dir, File(name).name), pcm)

    /** Временный WAV для плеера; удаляйте после прослушивания ([clearPlayback]). */
    fun playbackFile(name: String): File {
        val out = File(context.cacheDir, "voice_play").apply { deleteRecursively(); mkdirs() }.let { File(it, "note.wav") }
        out.outputStream().use { it.write(wav(pcm(name))) }
        return out
    }

    fun clearPlayback() {
        File(context.cacheDir, "voice_play").deleteRecursively()
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val BYTES_PER_SECOND = SAMPLE_RATE * 2

        fun durationMs(pcmBytes: Long): Long = pcmBytes * 1000 / BYTES_PER_SECOND

        /** WAV-заголовок перед PCM 16 кГц / моно / 16 бит. */
        fun wav(pcm: ByteArray): ByteArray {
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray()); putInt(36 + pcm.size); put("WAVE".toByteArray())
                put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
                putInt(SAMPLE_RATE); putInt(BYTES_PER_SECOND); putShort(2); putShort(16)
                put("data".toByteArray()); putInt(pcm.size)
            }.array()
            return header + pcm
        }
    }
}
