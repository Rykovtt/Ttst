package com.kartoteka.app.data

import com.kartoteka.app.assistant.MusicDuck
import com.kartoteka.app.assistant.WakeService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Приглушение музыки на время команды: «Санта…» — тише, после команды или через 4 с — как было. */
class MusicDuckTest {
    private var vol = 10
    private var now = 0L
    private val duck = MusicDuck(object : MusicDuck.VolumeIO {
        override fun get() = vol
        override fun set(index: Int) { vol = index }
    }) { now }

    @Test fun ducksAndRestoresAfterTimeout() {
        assertTrue(duck.duck())
        assertEquals(3, vol)                       // 30 % от 10
        now += MusicDuck.HOLD_MS - 1
        assertFalse(duck.restoreIfDue()); assertEquals(3, vol)
        now += 1
        assertTrue(duck.restoreIfDue()); assertEquals(10, vol)
        assertFalse(duck.active)
    }

    @Test fun extendKeepsItQuietWhileAssistantTalks() {
        duck.duck()
        now += 3_000; duck.extend(5_000)
        now += 4_000; assertFalse(duck.restoreIfDue())
        now += 1_000; assertTrue(duck.restoreIfDue()); assertEquals(10, vol)
    }

    @Test fun userChangedVolumeIsKept() {
        duck.duck()
        vol = 7                                    // человек сам поменял громкость
        duck.restore()
        assertEquals(7, vol)
    }

    @Test fun forgetLeavesVolumeForExplicitLevel() {
        duck.duck()
        duck.forget()
        vol = 5                                    // «громкость на 50» выставит своё
        now += 10_000; duck.restoreIfDue()
        assertEquals(5, vol)
    }

    @Test fun nothingToDuckWhenAlmostSilent() {
        vol = 1
        assertFalse(duck.duck()); assertEquals(1, vol)
        vol = 2
        assertTrue(duck.duck()); assertEquals(1, vol)
    }

    @Test fun secondDuckDoesNotStackQuieter() {
        duck.duck(); duck.duck()
        assertEquals(3, vol)
        duck.restore(); assertEquals(10, vol)
    }

    @Test fun namePresenceInPartialText() {
        val santa = listOf("санта")
        assertTrue(WakeService.namePresent("[unk] санта", santa))
        assertTrue(WakeService.namePresent("санта пауза", santa))
        assertFalse(WakeService.namePresent("[unk] [unk]", santa))
        assertFalse(WakeService.namePresent("", santa))
    }
}
