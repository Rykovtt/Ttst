package com.rykov.autosend.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrustedCallersTest {
    private val self = "com.rykov.autosend"

    @Test
    fun `self is always trusted`() {
        assertTrue(TrustedCallers.isTrusted(self, emptyList(), self))
    }

    @Test
    fun `unknown package or missing caller is rejected`() {
        assertFalse(TrustedCallers.isTrusted(null, emptyList(), self))
        assertFalse(TrustedCallers.isTrusted("com.evil", listOf("00"), self))
        assertFalse(TrustedCallers.knows("com.evil"))
    }

    @Test
    fun `rvault needs the pinned certificate`() {
        assertTrue(TrustedCallers.knows("com.rykov.rvault"))
        assertFalse(TrustedCallers.isTrusted("com.rykov.rvault", listOf("ab".repeat(32)), self))
        assertFalse(TrustedCallers.isTrusted("com.rykov.rvault", emptyList(), self))
        assertTrue(TrustedCallers.isTrusted("com.rykov.rvault", listOf(RVAULT.uppercase()), self))
        // Чужой пакет с сертификатом RVault не доверен: важна пара «пакет + ключ».
        assertFalse(TrustedCallers.isTrusted("com.evil", listOf(RVAULT), self))
    }

    @Test
    fun `digest formats`() {
        assertEquals("abcd", TrustedCallers.normalize("AB:CD"))
        assertEquals("00ff10", TrustedCallers.hex(byteArrayOf(0, -1, 16)))
    }

    private companion object {
        const val RVAULT = "a6599a0653fcef4944cf5e70bc28627f5471663d57e76d9410645cb994ff7296"
    }
}
