package com.kartoteka.app.data

import com.kartoteka.app.assistant.NoaMedia
import com.kartoteka.app.assistant.WakeService
import com.kartoteka.app.assistant.WakeService.Companion.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeWordTest {
    private val noa = listOf("ноа")
    private fun quiet(t: String, c: Double = 1.0) = WakeService.decide(t, noa, playing = false, confidence = c)
    private fun music(t: String, final: Boolean = true, c: Double = 1.0) = WakeService.decide(t, noa, playing = true, confidence = c, final = final)

    @Test fun heardNameInAnyCallPhrase() {
        assertTrue(WakeService.heard("Привет Ноа", noa))
        assertFalse(WakeService.heard("новый год", noa))
        assertFalse(WakeService.heard("ноа", emptyList()))
    }

    @Test fun quietRoomWakesOnExactCallOnly() {
        assertEquals(Decision.Wake(false), quiet("ноа"))
        assertEquals(Decision.Wake(false), quiet("эй ноа"))
        assertEquals(Decision.Wake(true), quiet("ноа ты тут"))
        assertNull(quiet("[unk] ноа"))          // посторонняя речь рядом — не зов
        assertNull(quiet("ноа [unk]"))
        assertNull(quiet("ты тут"))
        assertNull(quiet("ноа ноа"))
        assertNull(quiet("эй ноа", c = 0.4))    // неуверенно — не зов
    }

    @Test fun playerCommandsRightAfterTheName() {
        assertEquals(Decision.Command(NoaMedia.Control.PAUSE), quiet("ноа пауза"))
        assertEquals(Decision.Command(NoaMedia.Control.NEXT), quiet("ноа дальше"))
        assertEquals(Decision.Command(NoaMedia.Control.PREV), quiet("ноа назад"))
        assertEquals(Decision.Command(NoaMedia.Control.LOUDER), quiet("ноа громче"))
        assertEquals(Decision.Command(NoaMedia.Control.QUIETER), quiet("ноа тише"))
        assertEquals(Decision.Command(NoaMedia.Control.RESUME), quiet("ноа продолжи"))
    }

    @Test fun whilePlayingNoiseAroundIsIgnoredButInnerGapIsNot() {
        // музыка/видео дают «[unk]» по краям: команду и длинный зов принимаем
        assertEquals(Decision.Command(NoaMedia.Control.PAUSE), music("[unk] [unk] ноа пауза [unk]", final = false))
        assertEquals(Decision.Command(NoaMedia.Control.NEXT), music("[unk] ноа дальше", final = true))
        assertEquals(Decision.Wake(false), music("[unk] эй ноа [unk]", final = false))
        assertNull(music("ноа [unk] пауза", final = false))   // «[unk]» внутри — не команда
    }

    @Test fun bareNameWhilePlayingNeedsHighConfidenceAndFinalResult() {
        assertNull(music("[unk] ноа", final = true))          // с шумом рядом — только длинный зов
        assertNull(music("ноа", final = false))               // обрывок — нет
        assertNull(music("ноа", final = true, c = 0.8))
        assertEquals(Decision.Wake(false), music("ноа", final = true, c = 0.95))
    }

    @Test fun twoWordNameNeedsBothWords() {
        val name = listOf("анна", "мария")
        assertEquals(Decision.Wake(false), WakeService.decide("анна мария", name, playing = false))
        assertNull(WakeService.decide("анна", name, playing = false))
        assertEquals(Decision.Command(NoaMedia.Control.PAUSE), WakeService.decide("анна мария пауза", name, playing = false))
    }
}
