package com.kartoteka.app.data

import com.kartoteka.app.assistant.WakeService
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeWordTest {
    private val noa = listOf("ноа")

    @Test fun wakesOnNameInAnyCallPhrase() {
        assertTrue(WakeService.heard("ноа", noa))
        assertTrue(WakeService.heard("ноа ты тут", noa))
        assertTrue(WakeService.heard("эй ноа", noa))
        assertTrue(WakeService.heard("Привет Ноа", noa))
    }

    @Test fun doesNotWakeOnOtherSpeechOrEmpty() {
        assertFalse(WakeService.heard("ты тут", noa))
        assertFalse(WakeService.heard("", noa))
        assertFalse(WakeService.heard("новый год", noa))
        assertFalse(WakeService.heard("ноа", emptyList()))
    }

    @Test fun twoWordNameNeedsBothWords() {
        val name = listOf("анна", "мария")
        assertTrue(WakeService.heard("анна мария", name))
        assertFalse(WakeService.heard("анна", name))
    }

    @Test fun acceptsOnlyExactCallPhrases() {
        assertTrue(WakeService.accepts("ноа", noa, strict = false))
        assertTrue(WakeService.accepts("эй ноа", noa, strict = false))
        assertTrue(WakeService.accepts("ноа ты тут", noa, strict = false))
        // чужая речь вокруг: в тексте появляется [unk] — это не зов
        assertFalse(WakeService.accepts("[unk] ноа", noa, strict = false))
        assertFalse(WakeService.accepts("ноа [unk]", noa, strict = false))
        assertFalse(WakeService.accepts("ты тут", noa, strict = false))
        assertFalse(WakeService.accepts("ноа ноа", noa, strict = false))
    }

    @Test fun whilePlayingNeedsLongerCallAndGoodConfidence() {
        assertFalse(WakeService.accepts("ноа", noa, strict = true))
        assertTrue(WakeService.accepts("эй ноа", noa, strict = true))
        assertTrue(WakeService.accepts("привет ноа", noa, strict = true))
        assertFalse(WakeService.accepts("эй ноа", noa, strict = false, confidence = 0.4))
    }
}
