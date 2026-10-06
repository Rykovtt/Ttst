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

    @Test fun secondBatch() {
        assertEquals(NoaIntent.Flashlight(false), NoaParser.parse("виключи фонарик", now))
        val yt = NoaParser.parse("найди на YouTube аудиоспектакль и включи", now) as NoaIntent.Play
        assertEquals("аудиоспектакль", yt.query); assertEquals("youtube", yt.app)
        assertEquals("сериал ольга 1 сезон", (NoaParser.parse("включи YouTube и Открой сериал Ольга 1 сезон", now) as NoaIntent.Play).query)
        assertEquals("серіал ольга 1 сезон", (NoaParser.parse("открой YouTube серіал Ольга 1 сезон", now) as NoaIntent.Play).query)
        assertTrue(NoaParser.parse("какой средний курс доллара в Украине сейчас", now) is NoaIntent.WebSearch)
        val seq = NoaParser.parse("включи YouTube Music выбери плейлист понравившийся и включив случайном порядке на максимальной громкости", now) as NoaIntent.Sequence
        assertEquals(NoaIntent.Volume(NoaIntent.VolumeKind.MAX), seq.steps[0])
        val pl = seq.steps[1] as NoaIntent.Play
        assertTrue(NoaMedia.isLiked(pl.query)); assertTrue(pl.shuffle); assertEquals("youtube music", pl.app)
        val m = NoaParser.parse("подтверждение Илья рыкову насчёт его записи на завтра Отправь на украинском языке", now) as NoaIntent.Message
        assertTrue(m.aboutAppointment)
    }

    @Test fun thirdBatch() {
        val ytm = NoaParser.parse("найди открой YouTube Music и найди мне трек rufus Я недоволен и включи его", now) as NoaIntent.Play
        assertEquals("rufus я недоволен", ytm.query); assertEquals("youtube music", ytm.app)
        assertTrue(NoaParser.parse("кто у меня записан на ближайшее время в календаре", now) is NoaIntent.Crm)
        val seq = NoaParser.parse("открой YouTube сериал Реальные пацаны на максимальной громкости", now) as NoaIntent.Sequence
        assertEquals(NoaIntent.Volume(NoaIntent.VolumeKind.MAX), seq.steps[0])
        assertEquals("сериал реальные пацаны", (seq.steps[1] as NoaIntent.Play).query)
    }

    @Test fun volumeInPercent() {
        fun v(p: String) = NoaParser.parse(p, now)
        assertEquals(NoaIntent.Volume(NoaIntent.VolumeKind.SET, 40), v("громкость на 40 процентов"))
        assertEquals(NoaIntent.Volume(NoaIntent.VolumeKind.SET, 70), v("громкость 70"))
        assertEquals(NoaIntent.Volume(NoaIntent.VolumeKind.UP, 20), v("сделай громче на 20 процентов"))
        assertEquals(NoaIntent.Volume(NoaIntent.VolumeKind.DOWN, 15), v("сделай тише на 15 процентов"))
        assertEquals(NoaIntent.Volume(NoaIntent.VolumeKind.DOWN, 10), v("убавь громкость на 10%"))
        assertEquals(NoaIntent.Volume(NoaIntent.VolumeKind.MAX), v("поставь громкость на максимум"))
        assertEquals(NoaIntent.Volume(NoaIntent.VolumeKind.MIN), v("громкость на минимум"))
        assertEquals(NoaIntent.Volume(NoaIntent.VolumeKind.SET, 50), v("звук на половину"))
        assertEquals(NoaIntent.Media(NoaMedia.Control.LOUDER), v("сделай погромче"))
        assertTrue(v("поставь будильник на 7 утра") is NoaIntent.Alarm)
    }

    @Test fun voiceNoteCommand() {
        for ((p, who) in listOf("открой голосовые заметки об Илье Рыкове и начни запись" to "илье рыкове",
            "запиши голосовую заметку про Олега" to "олега", "надиктую заметку про Аню" to "аню", "начни запись голосовой заметки о Маше" to "маше")) {
            assertEquals(p, NoaIntent.VoiceNote(who), NoaParser.parse(p, now))
        }
        // обычная текстовая заметка не превращается в запись голоса
        assertTrue(NoaParser.parse("добавь Ане заметку любит кофе", now) is NoaIntent.AddNote)
    }

    @Test fun voiceStopPhrase() {
        for (p in listOf("стоп запись", "ну всё закончи запись", "останови запись пожалуйста")) assertTrue(p, com.kartoteka.app.assistant.VoiceNoteService.isStop(p))
        for (p in listOf("стоп", "я сказал стоп машина", "запись на завтра")) assertTrue(p, !com.kartoteka.app.assistant.VoiceNoteService.isStop(p))
    }
}
