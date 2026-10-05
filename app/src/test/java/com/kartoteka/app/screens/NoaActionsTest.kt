package com.kartoteka.app.screens

import androidx.test.core.app.ApplicationProvider
import com.kartoteka.app.assistant.Noa
import com.kartoteka.app.assistant.NoaIntent
import com.kartoteka.app.assistant.NoaMedia
import com.kartoteka.app.data.AppointmentLogic
import com.kartoteka.app.data.MessageLang
import com.kartoteka.app.data.Person
import com.kartoteka.app.data.ServiceTemplate
import com.kartoteka.app.data.TemplateKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/** Действия ассистента на телефоне: маршрут в любое место, музыка, сообщения, язык шаблонов. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class NoaActionsTest {
    private val app get() = ApplicationProvider.getApplicationContext<TestApp>()
    private val now = LocalDateTime.of(2026, 10, 4, 10, 0)

    @org.junit.Before fun ru() { com.kartoteka.app.i18n.I18n.init(app, com.kartoteka.app.i18n.UiLang.RU) }

    @Test fun routeToFreeAddressAndRerouteKeepsNavigator() = runBlocking {
        val noa = Noa(app)
        val r = noa.handleIntent(NoaIntent.Route("киевской", null, "waze", "киевской 5"), now)
        assertTrue("r=$r", r is Noa.Reply.Do && r.text.contains("киевской 5") && r.text.contains("Waze"))
        val again = noa.handleIntent(NoaIntent.Route("", null, null, "хрещатик 10"), now)
        assertTrue("again=$again", again is Noa.Reply.Do && again.text.contains("Waze"))
    }

    @Test fun mediaControlsAreQuiet() = runBlocking {
        val r = Noa(app).handleIntent(NoaIntent.Media(NoaMedia.Control.PAUSE), now)
        assertTrue("r=$r", r is Noa.Reply.Do && r.quiet)
    }

    @Test fun replyWithoutNotificationAccessExplainsHow() = runBlocking {
        val r = Noa(app).handleIntent(NoaIntent.Reply("", "ок"), now)
        assertTrue("r=$r", r is Noa.Reply.Say && r.text.contains("доступ к уведомлениям"))
    }

    @Test fun messageTextComesFromSpeech() = runBlocking {
        app.repository.savePerson(Person(firstName = "Илья", lastName = "Рыков"),
            listOf(com.kartoteka.app.data.ContactItem(type = "PHONE", value = "+380671112233")), emptyList(), emptyList())
        val noa = Noa(app)
        val r = noa.handle("напиши Илье что буду через 10 минут", now)
        assertTrue("r=$r", r is Noa.Reply.Do)
        assertEquals("Буду через 10 минут", com.kartoteka.app.assistant.NoaActions.lastMessage?.text)
    }

    @Test fun serviceTemplateInAnotherLanguageIsNotUsed() {
        val ru = ServiceTemplate(name = "Тату", tplConfirm = "Здравствуйте, {имя}! Вы записаны на {дата}.")
        val general = MessageLang.UK.template(TemplateKind.CONFIRM)
        assertEquals(general, AppointmentLogic.messageTemplate(ru, TemplateKind.CONFIRM, general, MessageLang.UK))
        assertEquals(ru.tplConfirm, AppointmentLogic.messageTemplate(ru, TemplateKind.CONFIRM, MessageLang.RU.template(TemplateKind.CONFIRM), MessageLang.RU))
        // общий шаблон, сохранённый по-русски для украинского, заменяется стандартным украинским
        assertEquals(general, AppointmentLogic.messageTemplate(null, TemplateKind.CONFIRM, "Здравствуйте, {имя}! Ждём вас.", MessageLang.UK))
    }
}
