package com.rykov.autosend.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnabledServicesTest {
    private val pkg = "com.rykov.autosend"
    private val cls = "com.rykov.autosend.services.AutoSendAccessibilityService"

    @Test
    fun `short and full class names`() {
        assertTrue(EnabledServices.contains("$pkg/.services.AutoSendAccessibilityService", pkg, cls))
        assertTrue(EnabledServices.contains("other/.X:$pkg/$cls", pkg, cls))
    }

    @Test
    fun `absent or foreign`() {
        assertFalse(EnabledServices.contains(null, pkg, cls))
        assertFalse(EnabledServices.contains("", pkg, cls))
        assertFalse(EnabledServices.contains("other/.services.AutoSendAccessibilityService", pkg, cls))
        assertFalse(EnabledServices.contains("$pkg/.services.Other", pkg, cls))
    }
}
