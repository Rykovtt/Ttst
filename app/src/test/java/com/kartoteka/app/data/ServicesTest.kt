package com.kartoteka.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class ServicesTest {
    private val client = Person(firstName = "Максим")
    private val start = LocalDateTime.of(2026, 10, 5, 12, 0)
    private val general = "{имя}, напоминаю о тату-сеансе {когда} в {время}. Не забудьте поесть и выспаться!"

    private val tattoo = ServiceTemplate(id = 1, name = "Тату-сеанс")
    private val consult = ServiceTemplate(
        id = 2, name = "Консультация",
        tplReminder = "{имя}, жду вас на консультацию {когда} в {время}. Возьмите референсы эскиза.",
    )

    private fun reminderFor(service: ServiceTemplate?): String {
        val a = Appointment(personId = 1, start = AppointmentLogic.millis(start), title = service?.name ?: "", serviceId = service?.id)
        val template = AppointmentLogic.messageTemplate(service, TemplateKind.REMINDER, general)
        return AppointmentLogic.fill(template, a, client, now = start.minusDays(1))
    }

    @Test fun consultationGetsItsOwnReminder() {
        assertEquals("Максим, жду вас на консультацию завтра в 12:00. Возьмите референсы эскиза.", reminderFor(consult))
    }

    @Test fun serviceWithoutOwnTextFallsBackToGeneral() {
        assertEquals("Максим, напоминаю о тату-сеансе завтра в 12:00. Не забудьте поесть и выспаться!", reminderFor(tattoo))
    }

    @Test fun noServiceUsesGeneral() {
        assertEquals(reminderFor(tattoo), reminderFor(null))
    }

    @Test fun blankServiceTextCountsAsEmpty() {
        val s = consult.withTemplate(TemplateKind.REMINDER, "   ")
        assertEquals(general, AppointmentLogic.messageTemplate(s, TemplateKind.REMINDER, general))
        assertEquals(0, s.ownTemplates)
    }
}
