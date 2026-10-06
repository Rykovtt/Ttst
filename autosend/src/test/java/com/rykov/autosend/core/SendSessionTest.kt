package com.rykov.autosend.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SendSessionTest {
    private var now = 1_000L
    private val session = SendSession { now }

    @Test
    fun `not armed by default`() {
        assertFalse(session.isArmedFor("com.whatsapp"))
        assertNull(session.takeForClick("com.whatsapp"))
    }

    @Test
    fun `exactly one click per arming`() {
        session.arm("com.whatsapp", "r1", null, 10_000)
        val request = session.takeForClick("com.whatsapp")
        assertEquals("r1", request?.requestId)
        assertFalse(session.isArmedFor("com.whatsapp"))
        assertNull(session.takeForClick("com.whatsapp"))
    }

    @Test
    fun `armed only for requested package`() {
        session.arm("com.whatsapp", null, null, 10_000)
        assertFalse(session.isArmedFor("org.telegram.messenger"))
        assertFalse(session.isArmedFor("com.whatsapp.w4b"))
        assertTrue(session.isArmedFor("com.whatsapp"))
    }

    @Test
    fun `without package any supported messenger but nothing else`() {
        session.arm(null, null, null, 10_000)
        assertTrue(session.isArmedFor("org.telegram.messenger"))
        assertTrue(session.isArmedFor("com.whatsapp.w4b"))
        assertFalse(session.isArmedFor("com.android.chrome"))
        assertFalse(session.isArmedFor(null))
    }

    @Test
    fun `expires after ttl`() {
        session.arm("com.whatsapp", "r1", null, 10_000)
        now += 9_999
        assertNull(session.expireIfDue())
        assertTrue(session.isArmedFor("com.whatsapp"))
        now += 1
        assertFalse(session.isArmedFor("com.whatsapp"))
        assertEquals("r1", session.expireIfDue()?.requestId)
        assertNull(session.current)
    }

    @Test
    fun `ttl is clamped`() {
        assertEquals(now + SendSession.MIN_TTL_MS, session.arm(null, null, null, 0).expiresAt)
        assertEquals(now + SendSession.MAX_TTL_MS, session.arm(null, null, null, Long.MAX_VALUE).expiresAt)
    }

    @Test
    fun `cancel returns pending request`() {
        session.arm("com.whatsapp", "r1", "crm", 10_000)
        assertNotNull(session.cancel())
        assertNull(session.cancel())
        assertFalse(session.isArmedFor("com.whatsapp"))
    }
}
