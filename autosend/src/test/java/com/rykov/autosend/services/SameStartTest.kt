package com.rykov.autosend.services

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Viber: кнопку справа от поля жмём, только если в поле именно наш текст. */
class SameStartTest {
    @Test fun sameMessage() {
        assertTrue(SendButtonFinder.sameStart("Здравствуйте, Илья!  Вы записаны\nна завтра", "Здравствуйте, Илья! Вы записаны на завтра в 13:00"))
    }

    @Test fun otherDraft() {
        assertFalse(SendButtonFinder.sameStart("Привет, как дела?", "Здравствуйте, Илья! Вы записаны"))
        assertFalse(SendButtonFinder.sameStart("", "Здравствуйте"))
    }
}
