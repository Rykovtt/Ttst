package com.kartoteka.app.screens

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.kartoteka.app.data.Person
import com.kartoteka.app.data.VoiceNote
import com.kartoteka.app.data.VoiceStorage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class VaultFeaturesTest {
    private val app get() = ApplicationProvider.getApplicationContext<TestApp>()

    private fun samplePcm() = ByteArray(VoiceStorage.BYTES_PER_SECOND) { (it % 251).toByte() }

    @Test fun voiceIsEncryptedOnDiskAndPlaysAsWav() {
        val voices = app.repository.voices
        val name = voices.newName()
        val pcm = samplePcm()
        voices.save(name, pcm)
        val raw = File(voices.dir, name).readBytes()
        assertFalse("на диске не должно быть открытого звука", raw.toList().windowed(64).any { it == pcm.take(64) })
        assertArrayEquals(pcm, voices.pcm(name))
        val wav = voices.playbackFile(name).readBytes()
        assertEquals("RIFF", String(wav, 0, 4))
        assertEquals(44 + pcm.size, wav.size)
        voices.clearPlayback()
        assertEquals(1000L, VoiceStorage.durationMs(pcm.size.toLong()))
    }

    @Test fun deletingPersonRemovesVoiceFiles() = runBlocking {
        val repo = app.repository
        val id = repo.savePerson(Person(firstName = "Тест"), emptyList(), emptyList(), emptyList())
        val name = repo.voices.newName()
        repo.voices.save(name, samplePcm())
        repo.addVoiceNote(VoiceNote(personId = id, file = name, durationMs = 1000))
        repo.deletePerson(id)
        assertFalse(repo.voices.exists(name))
        assertTrue(repo.allVoiceNotes().isEmpty())
    }

    @Test fun backupCarriesVoiceNotesToAnotherPhone() = runBlocking {
        val repo = app.repository
        val id = repo.savePerson(Person(firstName = "Максим"), emptyList(), emptyList(), emptyList())
        val name = repo.voices.newName()
        val pcm = samplePcm()
        repo.voices.save(name, pcm)
        repo.addVoiceNote(VoiceNote(personId = id, file = name, durationMs = 1000, text = "Любит чёрный кофе"))

        val file = File(app.cacheDir, "test.krtk")
        app.backup.export(Uri.fromFile(file), "secret")
        app.backup.import(Uri.fromFile(file), "secret", replace = true)

        val notes = repo.allVoiceNotes()
        assertEquals(1, notes.size)
        assertEquals("Любит чёрный кофе", notes[0].text)
        assertArrayEquals(pcm, repo.voices.pcm(notes[0].file))
        assertFalse("старый файл удалён при замене", repo.voices.exists(name))
    }

    @Test fun intruderLogKeepsEncryptedPhotos() {
        val log = app.intruders
        log.clear()
        val jpeg = java.io.ByteArrayOutputStream().also {
            android.graphics.Bitmap.createBitmap(20, 20, android.graphics.Bitmap.Config.ARGB_8888)
                .compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it)
        }.toByteArray()
        log.record(1_000, null)
        log.attachPhoto(1_000, jpeg)
        log.record(2_000, null)
        val list = log.attempts()
        assertEquals(listOf(2_000L, 1_000L), list.map { it.time })
        assertTrue(list[1].hasPhoto)
        assertFalse(list[0].hasPhoto)
        assertNotNull(log.photo(list[1]))
        assertEquals(2, log.unseen)
        log.markSeen()
        assertEquals(0, log.unseen)
        repeat(60) { log.record(10_000L + it, null) }
        assertEquals(50, log.attempts().size)
        log.clear()
        assertTrue(log.attempts().isEmpty())
    }
}
