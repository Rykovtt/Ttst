package com.kartoteka.app.data

import com.kartoteka.app.assistant.NoaIntent
import com.kartoteka.app.assistant.NoaMedia
import com.kartoteka.app.assistant.NoaParser
import com.kartoteka.app.assistant.WakeService
import com.kartoteka.app.assistant.WakeService.Companion.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** Управление музыкой: «Санта, плей / громче / громкость на максимум» и то же в полном ассистенте. */
class MusicCommandsTest {
    private val santa = listOf("санта")
    private fun wake(t: String) = WakeService.decide(t, santa, playing = true, final = true)
    private val now = LocalDateTime.of(2026, 10, 8, 2, 0)
    private fun parse(t: String) = NoaParser.parse(t, now)

    @Test fun quickCommandsAfterName() {
        // так их выдаёт распознаватель (сказанное «на» он проглатывает)
        mapOf(
            "санта плей" to NoaMedia.Control.RESUME, "санта воспроизведи" to NoaMedia.Control.RESUME,
            "санта продолжить" to NoaMedia.Control.RESUME, "санта включи музыку" to NoaMedia.Control.RESUME,
            "санта паузу" to NoaMedia.Control.PAUSE, "санта выключи музыку" to NoaMedia.Control.PAUSE,
            "санта сделай громче" to NoaMedia.Control.LOUDER, "санта погромче" to NoaMedia.Control.LOUDER,
            "санта сделай потише" to NoaMedia.Control.QUIETER, "санта убавь звук" to NoaMedia.Control.QUIETER,
            "санта следующую песню" to NoaMedia.Control.NEXT, "санта предыдущую" to NoaMedia.Control.PREV,
            "санта заново" to NoaMedia.Control.RESTART, "санта перемотай назад" to NoaMedia.Control.REWIND,
            "санта перемотай" to NoaMedia.Control.FORWARD, "санта перемешай" to NoaMedia.Control.SHUFFLE_ON,
            "санта назад" to NoaMedia.Control.PREV, "санта пауза" to NoaMedia.Control.PAUSE,
        ).forEach { (said, c) -> assertEquals(said, Decision.Command(c), wake("[unk] $said [unk]")) }
    }

    @Test fun quickVolumeLevels() {
        assertEquals(Decision.Volume(100), wake("санта громкость максимум"))
        assertNull(wake("санта полную"))       // голое «полную» — частое слово в песнях, не команда
        assertEquals(Decision.Volume(50), wake("санта громкость пятьдесят процентов"))
        assertEquals(Decision.Volume(30), wake("санта громкость тридцать"))
        assertEquals(Decision.Volume(10), wake("санта звук минимум"))
        // ошибочно услышанное «громкость полную» (вместо «громкость больше») — не громкость 100 %
        assertNull(wake("санта громкость полную"))
    }

    @Test fun grammarHasNoShortWordsThatEatCommands() {
        // «на», «с», «в» в грамматике перехватывали «назад» → «на»
        val words = (WakeService.COMMANDS.keys + WakeService.VOLUME.keys).flatMap { it.split(' ') }.toSet()
        assertTrue(words.intersect(setOf("на", "с", "в", "еще", "play")).isEmpty())
        // «включи» без продолжения — просьба ассистенту («Санта, включи Rammstein»), не «продолжить»
        assertNull(WakeService.COMMANDS["включи"])
    }

    @Test fun assistantUnderstandsResumeWords() {
        listOf("плей", "play", "воспроизведи", "воспроизвести", "играй", "включи обратно", "включи звук", "продолжи воспроизведение", "грай")
            .forEach { assertEquals(it, NoaIntent.Media(NoaMedia.Control.RESUME), parse(it)) }
        listOf("выключи звук", "без звука").forEach { assertEquals(it, NoaIntent.Media(NoaMedia.Control.PAUSE), parse(it)) }
    }

    @Test fun assistantVolume() {
        assertEquals(NoaIntent.Volume(NoaIntent.VolumeKind.SET, 50), parse("громкость на пятьдесят процентов"))
        assertEquals(NoaIntent.Volume(NoaIntent.VolumeKind.SET, 25), parse("громкость двадцать пять"))
        assertEquals(NoaIntent.Volume(NoaIntent.VolumeKind.MAX), parse("на полную"))
        assertEquals(NoaIntent.Volume(NoaIntent.VolumeKind.UP, 10), parse("увеличь громкость"))
        assertEquals(NoaIntent.Volume(NoaIntent.VolumeKind.DOWN, 10), parse("уменьши громкость"))
        assertEquals(NoaIntent.Volume(NoaIntent.VolumeKind.UP, 10), parse("громкость больше"))
        assertEquals(NoaIntent.Volume(NoaIntent.VolumeKind.DOWN, 10), parse("громкость меньше"))
        assertEquals(NoaIntent.Media(NoaMedia.Control.LOUDER), parse("сделай громче"))
    }

    @Test fun assistantSeekRestartRepeat() {
        assertEquals(NoaIntent.Media(NoaMedia.Control.RESTART), parse("включи песню заново"))
        assertEquals(NoaIntent.Media(NoaMedia.Control.RESTART), parse("сначала"))
        assertEquals(NoaIntent.Media(NoaMedia.Control.FORWARD), parse("перемотай вперёд"))
        assertEquals(NoaIntent.Media(NoaMedia.Control.FORWARD, 30), parse("перемотай на тридцать секунд"))
        assertEquals(NoaIntent.Media(NoaMedia.Control.REWIND, 60), parse("отмотай назад на минуту"))
        assertEquals(NoaIntent.Media(NoaMedia.Control.REPEAT), parse("повтори эту песню"))
        // голое «повтори» — повторить ответ ассистента
        assertEquals(NoaIntent.Repeat, parse("повтори"))
    }

    @Test fun nothingElseBroke() {
        assertTrue(parse("включи Rammstein") is NoaIntent.Play)
        assertTrue(parse("запиши Аню на завтра в 10") is NoaIntent.CreateAppointment)
        assertTrue(parse("поставь будильник на 7") is NoaIntent.Alarm)
    }

    /** Дослушанная фраза: самая длинная команда после имени, хвост из музыки не мешает. */
    @Test fun settledPhrase() {
        assertEquals(Decision.Volume(70), WakeService.decideSettled("санта громкость семьдесят процентов", santa))
        assertEquals(Decision.Volume(70), WakeService.decideSettled("[unk] санта громкость семьдесят река", santa))
        assertEquals(Decision.Command(NoaMedia.Control.NEXT), WakeService.decideSettled("санта следующий трек", santa))
        assertNull(WakeService.decideSettled("санта река стоп", santa))
        assertNull(WakeService.decideSettled("сам там стоп", santa))
    }

    @Test fun commandStartedAfterName() {
        assertTrue(WakeService.commandStarted("[unk] санта громкость", santa))
        assertTrue(WakeService.commandStarted("санта пауза", santa))
        assertTrue(!WakeService.commandStarted("санта клаус", santa) && !WakeService.commandStarted("санта", santa))
    }

    /** Приманки не пересекаются с именем, его созвучиями на одну букву и словами команд. */
    @Test fun decoysDoNotStealCommands() {
        val d = WakeService.decoys(santa)
        assertTrue(d.size > 150)
        val cmdWords = (WakeService.COMMANDS.keys + WakeService.VOLUME.keys).flatMap { it.split(' ') }.toSet()
        assertTrue(d.none { it in cmdWords || it == "санта" || it.length <= 2 })
        assertTrue("сам" in d && "сантехник" in d)
        assertTrue("саня" !in WakeService.decoys(listOf("саня")))
    }

    /** При музыке вокруг имени — слова песни: команда всё равно находится сразу после имени. */
    @Test fun commandAmongSongWords() {
        assertEquals(Decision.Command(NoaMedia.Control.NEXT), wake("нет санта дальше небо"))
        assertEquals(Decision.Volume(70), wake("сама санта громкость семьдесят санта"))
        assertEquals(Decision.Command(NoaMedia.Control.PAUSE), WakeService.decideSettled("небо там санта пауза река", santa))
        assertNull(wake("санта небо пауза"))
        assertNull(WakeService.decide("нет санта дальше", santa, playing = false))
    }

    /** Окно команды после имени: «Санта… (пауза в речи) …включи обратно». */
    @Test fun followUpWithoutName() {
        assertEquals(Decision.Command(NoaMedia.Control.RESUME), WakeService.decideFollowUp("включи обратно", santa))
        assertEquals(Decision.Command(NoaMedia.Control.PAUSE), WakeService.decideFollowUp("[unk] замолчи небо", santa))
        assertEquals(Decision.Volume(70), WakeService.decideFollowUp("громкость семьдесят", santa))
        assertEquals(Decision.Command(NoaMedia.Control.NEXT), WakeService.decideFollowUp("небо санта дальше", santa))
        assertNull(WakeService.decideFollowUp("давай назад", santa))       // слова песни перед командой — не команда
        assertNull(WakeService.decideFollowUp("сейчас", santa))
    }
}
