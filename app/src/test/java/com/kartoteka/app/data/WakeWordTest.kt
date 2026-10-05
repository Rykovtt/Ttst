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
}
