package com.rykov.autosend.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SendLabelsTest {
    @Test
    fun `button labels match`() {
        listOf("Send", "send", " Отправить ", "ОТПРАВИТЬ", "Надіслати", "Send message").forEach {
            assertTrue(it, SendLabels.matches(it))
        }
    }

    @Test
    fun `chat text containing the word does not match`() {
        listOf(null, "", "  ", "Please send me the invoice", "Отправить завтра?", "Sending…").forEach {
            assertFalse(it.toString(), SendLabels.matches(it))
        }
    }
}
