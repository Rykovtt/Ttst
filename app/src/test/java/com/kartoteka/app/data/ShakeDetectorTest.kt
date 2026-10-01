package com.kartoteka.app.data

import com.kartoteka.app.security.ShakeDetector
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShakeDetectorTest {
    private fun ShakeDetector.feed(vararg samples: Pair<Float, Long>) = samples.map { (g, t) -> accept(g, t) }

    @Test fun threeSharpJerksCloseTheApp() {
        val d = ShakeDetector {}
        val r = d.feed(2.8f to 0, 2.9f to 300, 3.1f to 600)
        assertTrue(r.last())
    }

    @Test fun walkingAndSingleDropDoNotTrigger() {
        val d = ShakeDetector {}
        // ходьба: ~1.3 g
        assertFalse((0 until 50).map { d.accept(1.3f, it * 100L) }.any { it })
        // телефон бросили на стол: один удар, несколько показаний подряд
        assertFalse(d.feed(4f to 10_000, 3.5f to 10_020, 3f to 10_040).any { it })
        // рывки слишком далеко друг от друга
        assertFalse(d.feed(3f to 20_000, 3f to 21_500, 3f to 23_000).any { it })
    }

    @Test fun cooldownPreventsDoubleTrigger() {
        val d = ShakeDetector {}
        assertTrue(d.feed(3f to 0, 3f to 200, 3f to 400).last())
        assertFalse(d.feed(3f to 600, 3f to 800, 3f to 1000).any { it })
    }
}
