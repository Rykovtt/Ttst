package com.kartoteka.app.data

import com.kartoteka.app.assistant.BrainService
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Остановка генерации: JSON закрылся — дальше ждать нечего. */
class BrainStopTest {
    @Test fun detectsClosedJson() {
        assertFalse(BrainService.jsonClosed(""))
        assertFalse(BrainService.jsonClosed("{\"actions\":[{\"action\":\"call\""))
        assertTrue(BrainService.jsonClosed("{\"actions\":[{\"action\":\"call\",\"person\":\"Аня\"}]}"))
        assertTrue(BrainService.jsonClosed("Вот ответ: {\"actions\":[]} и ещё немного текста"))
    }

    @Test fun bracesInsideStringsDoNotCount() {
        assertFalse(BrainService.jsonClosed("{\"reply\":\"закрой } скобку"))
        assertTrue(BrainService.jsonClosed("{\"reply\":\"символ } внутри \\\" строки\"}"))
    }
}
