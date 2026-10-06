package com.rykov.autosend.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class TargetAppsTest {
    /** Список пакетов в конфиге службы и в коде должен совпадать. */
    @Test
    fun `config packageNames match code`() {
        val xml = File("src/main/res/xml/accessibility_service_config.xml").readText()
        val attr = Regex("""android:packageNames="([^"]*)"""").find(xml)!!.groupValues[1]
        assertEquals(TargetApps.packageNames, attr.split(',').map { it.trim() }.toSet())
    }

    @Test
    fun `whatsapp uses send view id`() {
        assertEquals(listOf("com.whatsapp:id/send"), TargetApps.forPackage("com.whatsapp")?.sendViewIds)
        assertEquals(listOf("com.whatsapp.w4b:id/send"), TargetApps.forPackage("com.whatsapp.w4b")?.sendViewIds)
    }
}
