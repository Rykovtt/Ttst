package com.kartoteka.app.data

import com.kartoteka.app.assistant.NoaIntent
import com.kartoteka.app.assistant.NoaMedia
import com.kartoteka.app.assistant.NoaParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** Фразы со скриншотов пользователя: неделя, «понравившиеся», «найди на ютубе … и включи его». */
class NoaScreenshotCasesTest {
    private val now = LocalDateTime.of(2026, 10, 6, 17, 50) // вторник

    @Test fun weekIsNotToday() {
        val a = NoaParser.parse("проверь есть ли записи на эту неделю", now) as NoaIntent.Agenda
        assertEquals(LocalDate.of(2026, 10, 6), a.date); assertEquals(6, a.days)
        assertEquals(6, (NoaParser.parse("что у меня на этой неделе", now) as NoaIntent.Agenda).days)
    }

    @Test fun likedPlaylist() {
        for (p in listOf("открой YouTube Music и запусти плейлист с треками которые я лайкал",
            "открой плейлист музыка яка сподобалась в YouTube Music",
            "открой ютуб музик и включи плейлист с последними понравившимися песнями")) {
            val r = NoaParser.parse(p, now) as NoaIntent.Play
            assertTrue(p, NoaMedia.isLiked(r.query)); assertTrue(p, r.playlist)
        }
    }

    @Test fun findOnYoutubeAndPlayIt() {
        val r = NoaParser.parse("найди на Ютубе какой-то аудиоспектакль и включи его", now) as NoaIntent.Play
        assertEquals("аудиоспектакль", r.query); assertTrue(r.video)
    }
}
