package com.kartoteka.app.data

import com.kartoteka.app.assistant.KwsModel
import com.kartoteka.app.assistant.KwsStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Нейросеть на «Санта» в Kotlin считает то же, что при обучении (PyTorch): тот же звук → те же вероятности. */
class KwsModelTest {
    private val model = File("src/main/assets/kws/santa.bin").inputStream().use { KwsModel.load(it) }

    @Test fun matchesPythonReference() {
        val bb = ByteBuffer.wrap(javaClass.getResource("/kws_vector.bin")!!.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        val pcm = ShortArray(bb.int) { bb.short }
        val expected = FloatArray(bb.int) { bb.float }
        val got = ArrayList<Float>()
        KwsStream(model).push(pcm, pcm.size) { got += it.prob }
        assertEquals(expected.size, got.size)
        for (i in expected.indices) assertEquals("run $i", expected[i], got[i], 2e-3f)
        assertTrue(expected.max() > 0.5f)
    }

    @Test fun fftMatchesNaiveDft() {
        val n = 16
        val re = FloatArray(n) { kotlin.math.sin(it * 0.7).toFloat() + it % 3 }; val im = FloatArray(n)
        val r0 = re.copyOf()
        KwsModel.fft(re, im)
        for (k in 0 until n) {
            var sr = 0.0; var si = 0.0
            for (t in 0 until n) { val a = -2 * Math.PI * k * t / n; sr += r0[t] * kotlin.math.cos(a); si += r0[t] * kotlin.math.sin(a) }
            assertEquals(sr.toFloat(), re[k], 1e-3f); assertEquals(si.toFloat(), im[k], 1e-3f)
        }
    }

    /** Скорость: один прогон сети (раз в 100 мс на телефоне). На ПК — доли миллисекунды; на телефоне в разы медленнее. */
    @Test fun runIsCheap() {
        val feats = FloatArray(KwsModel.FRAMES * KwsModel.NMEL) { (it % 17) * 0.1f - 3f }
        repeat(500) { model.run(feats) }
        val t0 = System.nanoTime(); val n = 500
        repeat(n) { model.run(feats) }
        val ms = (System.nanoTime() - t0) / 1e6 / n
        println("KWS run: %.2f ms".format(ms))
        assertTrue(ms < 20.0)
    }

    @Test fun mediaFromAssistantParse() {
        val d = com.kartoteka.app.assistant.WakeService.Companion
        assertEquals(com.kartoteka.app.assistant.WakeService.Companion.Decision.Volume(70), d.mediaDecision(com.kartoteka.app.assistant.NoaIntent.Volume(com.kartoteka.app.assistant.NoaIntent.VolumeKind.SET, 70)))
        assertEquals(com.kartoteka.app.assistant.WakeService.Companion.Decision.Command(com.kartoteka.app.assistant.NoaMedia.Control.QUIETER),
            d.mediaDecision(com.kartoteka.app.assistant.NoaIntent.Volume(com.kartoteka.app.assistant.NoaIntent.VolumeKind.DOWN, 10)))
    }
}
