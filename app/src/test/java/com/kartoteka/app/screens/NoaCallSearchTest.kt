package com.kartoteka.app.screens

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.kartoteka.app.assistant.Noa
import com.kartoteka.app.assistant.PhoneActions
import com.kartoteka.app.data.ContactItem
import com.kartoteka.app.data.Person
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/** «Позвони …» звонит сразу (если разрешено), поиск открывает выдачу без лишнего нажатия. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class NoaCallSearchTest {
    private val app get() = ApplicationProvider.getApplicationContext<TestApp>()
    private val now = LocalDateTime.of(2026, 10, 7, 12, 0)

    @org.junit.Before fun ru() { com.kartoteka.app.i18n.I18n.init(app, com.kartoteka.app.i18n.UiLang.RU) }

    private fun seed() = runBlocking {
        app.repository.savePerson(Person(firstName = "Мария", lastName = "Фролова"),
            listOf(ContactItem(type = "PHONE", value = "+380671112233")), emptyList(), emptyList())
    }

    private fun lastStarted(): Intent = shadowOf(app).nextStartedActivity

    @Test fun callsRightAwayWhenAllowed() = runBlocking {
        seed()
        shadowOf(app).grantPermissions(android.Manifest.permission.CALL_PHONE)
        val r = Noa(app).handle("позвони Марии", now) as Noa.Reply.Do
        assertTrue(r.text, r.text.startsWith("Звоню"))
        r.effect(app)
        val i = lastStarted()
        assertEquals(Intent.ACTION_CALL, i.action)
        assertEquals("tel:%2B380671112233", i.dataString)
    }

    @Test fun opensDialerAndExplainsWithoutPermission() = runBlocking {
        seed()
        shadowOf(app).denyPermissions(android.Manifest.permission.CALL_PHONE)
        val r = Noa(app).handle("позвони Марии", now) as Noa.Reply.Do
        assertTrue(r.text, r.text.contains("Звонить сразу"))
        r.effect(app)
        assertEquals(Intent.ACTION_DIAL, lastStarted().action)
    }

    @Test fun webSearchOpensResultsPage() {
        PhoneActions.webSearch(app, "курс доллара")
        val i = lastStarted()
        assertEquals(Intent.ACTION_VIEW, i.action)
        assertEquals("https://www.google.com/search?q=" + android.net.Uri.encode("курс доллара"), i.dataString)
    }
}
