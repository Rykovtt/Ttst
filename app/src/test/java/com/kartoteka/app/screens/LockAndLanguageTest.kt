package com.kartoteka.app.screens

import androidx.test.core.app.ApplicationProvider
import com.kartoteka.app.data.ArchiveLogic
import com.kartoteka.app.data.PinLock
import com.kartoteka.app.i18n.I18n
import com.kartoteka.app.i18n.UiLang
import com.kartoteka.app.i18n.t
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class LockAndLanguageTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @After fun resetLang() = I18n.init(context, UiLang.RU)

    @Test fun pinIsVerifiedAndPausedAfterFiveFails() {
        val lock = PinLock(context)
        assertFalse(lock.hasPin)
        lock.set("4821")
        assertTrue(lock.hasPin)
        assertEquals(4, lock.length)
        assertTrue(lock.verify("4821"))
        repeat(5) { assertFalse(lock.verify("0000")) }
        assertTrue(lock.waitMillis() > 0)
        assertFalse("во время паузы даже верный PIN не принимается", lock.verify("4821"))
        lock.clear()
        assertFalse(lock.hasPin)
    }

    @Test fun interfaceFollowsChosenLanguage() {
        I18n.init(context, UiLang.UK)
        assertEquals("Українська", UiLang.UK.title)
        assertEquals("1 людина", "1 ${ArchiveLogic.plural(1, "человек", "человека", "человек")}")
        assertEquals("3 людини", "3 ${ArchiveLogic.plural(3, "человек", "человека", "человек")}")
        I18n.init(context, UiLang.EN)
        assertEquals("Male", t("Мужской"))
        assertEquals("1 person", "1 ${ArchiveLogic.plural(1, "человек", "человека", "человек")}")
        assertEquals("5 people", "5 ${ArchiveLogic.plural(5, "человек", "человека", "человек")}")
        assertEquals("1 time", "1 ${ArchiveLogic.plural(1, "раз", "раза", "раз")}")
        assertEquals("3 times", "3 ${ArchiveLogic.plural(3, "раз", "раза", "раз")}")
        I18n.init(context, UiLang.UK)
        assertEquals("3 рази", "3 ${ArchiveLogic.plural(3, "раз", "раза", "раз")}")
        assertEquals("5 разів", "5 ${ArchiveLogic.plural(5, "раз", "раза", "раз")}")
        I18n.init(context, UiLang.RU)
        assertEquals("Мужской", t("Мужской"))
        assertEquals("21 раз", "21 ${ArchiveLogic.plural(21, "раз", "раза", "раз")}")
    }
}
