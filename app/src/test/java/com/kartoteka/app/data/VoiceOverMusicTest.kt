package com.kartoteka.app.data

import com.kartoteka.app.assistant.VoiceOverMusic
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/** Голос поверх музыки: микрофон громче, чем объясняется тем, что играет телефон. */
class VoiceOverMusicTest {
    private fun rmsAt(db: Double) = 32768.0 * 10.0.pow(db / 20)

    @Test fun songAloneIsNotVoice() {
        val v = VoiceOverMusic(); var t = 0L
        // песня то громче, то тише: микрофон повторяет её (−12 дБ от выхода)
        repeat(200) { val play = -20f + (it % 7) * 2f; v.add(t, rmsAt(play - 12.0), play); t += 100 }
        assertFalse(v.voice(t))
    }

    @Test fun speechOverSongIsVoice() {
        val v = VoiceOverMusic(); var t = 0L
        repeat(150) { val play = -20f + (it % 7) * 2f; v.add(t, rmsAt(play - 12.0), play); t += 100 }
        // человек говорит: микрофон на 8 дБ громче обычного при той же песне
        repeat(8) { val play = -20f + (it % 7) * 2f; v.add(t, rmsAt(play - 4.0), play); t += 100 }
        assertTrue(v.voice(t))
    }

    @Test fun noPlaybackDataDoesNotBlock() {
        val v = VoiceOverMusic()
        repeat(50) { v.add(it * 100L, 1000.0, null) }
        assertNull(v.excess(5_000))
        assertTrue(v.voice(5_000))
    }
}
