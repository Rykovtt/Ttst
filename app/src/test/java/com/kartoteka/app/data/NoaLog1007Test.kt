package com.kartoteka.app.data

import com.kartoteka.app.assistant.NoaIntent
import com.kartoteka.app.assistant.NoaParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** Журнал нераспознанных фраз за 07–08.10: что правила должны понимать сами. */
class NoaLog1007Test {
    private val now = LocalDateTime.of(2026, 10, 8, 13, 0)
    private fun p(s: String) = NoaParser.parse(s, now)

    @Test fun openHoursGoToWebSearch() {
        assertEquals(NoaIntent.WebSearch("Работает ли сейчас Макдональдс на воздухофлотском проспекте"), p("Работает ли сейчас Макдональдс на воздухофлотском проспекте"))
        assertTrue(p("до скольки работает АТБ на Победе") is NoaIntent.WebSearch)
        assertTrue(p("во сколько закрывается Эпицентр") is NoaIntent.WebSearch)
    }

    @Test fun ping() {
        for (s in listOf("ти тут", "ты тут", "ты здесь", "ты меня слышишь", "алло")) assertEquals(s, NoaIntent.Ping, p(s))
    }

    @Test fun misheardIncludeWithVolume() {
        val r = p("ключі плейлист понравившиеся на средней громкости")
        assertTrue(r.toString(), r is NoaIntent.Sequence)
        val steps = (r as NoaIntent.Sequence).steps
        assertEquals(NoaIntent.Volume(NoaIntent.VolumeKind.SET, 50), steps[0])
        assertTrue(steps[1].toString(), steps[1] is NoaIntent.Play && (steps[1] as NoaIntent.Play).playlist)
    }

    @Test fun cardWithoutVerb() {
        val r = p("карточку ильи рыкова")
        assertTrue(r.toString(), r is NoaIntent.Open && (r as NoaIntent.Open).personQuery.contains("ильи"))
    }

    @Test fun volumeNumbers() {
        assertEquals(NoaIntent.Volume(NoaIntent.VolumeKind.SET, 70), p("громкость на семьдесят"))
        assertEquals(NoaIntent.Volume(NoaIntent.VolumeKind.SET, 50), p("громкость на среднюю"))
    }
}
