package com.kartoteka.app.assistant

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin

/**
 * Своя нейросеть на слово-имя («Санта»), целиком на телефоне: без библиотек, сервисов и интернета.
 *
 * Вход — последняя секунда звука (16 кГц): 97 кадров по 40 мел-полос (окно 512 с Ханном 400 по центру, шаг 160, log).
 * Сеть — 4 свёртки 3×3 (шаг 2, 2, 2, 1; BatchNorm уже свёрнут в веса) → плотный слой 32 (эмбеддинг) → вероятность.
 * Веса и мел-матрица лежат в файле модели — признаки считаются ровно так же, как при обучении.
 */
class KwsModel private constructor(
    private val window: FloatArray,
    private val mel: FloatArray,          // NMEL × NBIN
    private val mu: FloatArray,
    private val sd: FloatArray,
    private val convs: List<Conv>,
    private val fcW: FloatArray, private val fcB: FloatArray,
    private val outW: FloatArray, private val outB: Float,
) {
    class Conv(val inC: Int, val outC: Int, val stride: Int, val w: FloatArray, val b: FloatArray)

    /** Результат: вероятность имени и эмбеддинг (для подстройки под голос). */
    class Out(val prob: Float, val embedding: FloatArray)

    /** Лог-мел одного кадра из [NFFT] отсчётов (−1..1). */
    fun frame(samples: FloatArray, offset: Int, out: FloatArray, outOffset: Int) {
        val re = FloatArray(NFFT); val im = FloatArray(NFFT)
        for (i in 0 until NFFT) re[i] = samples[offset + i] * window[i]
        fft(re, im)
        val nb = NFFT / 2 + 1
        val p = FloatArray(nb) { re[it] * re[it] + im[it] * im[it] }
        for (m in 0 until NMEL) {
            var s = 0f
            val row = m * nb
            for (k in 0 until nb) { val c = mel[row + k]; if (c != 0f) s += c * p[k] }
            out[outOffset + m] = ln(s + 1e-6f)
        }
    }

    /** [feats] — FRAMES кадров подряд по NMEL (кадр за кадром), ненормированные. */
    fun run(feats: FloatArray): Out {
        // (1, 40, 97): ось частот — высота, время — ширина; как в PyTorch (B, 1, 40, 97)
        var h = NMEL; var w = FRAMES
        var x = FloatArray(NMEL * FRAMES)
        for (t in 0 until FRAMES) for (m in 0 until NMEL) x[m * FRAMES + t] = (feats[t * NMEL + m] - mu[m]) / sd[m]
        var c = 1
        for (cv in convs) {
            val oh = (h + 2 - 3) / cv.stride + 1; val ow = (w + 2 - 3) / cv.stride + 1
            val y = FloatArray(cv.outC * oh * ow)
            val st = cv.stride
            for (o in 0 until cv.outC) {
                val ob = o * oh * ow
                java.util.Arrays.fill(y, ob, ob + oh * ow, cv.b[o])
                for (i in 0 until c) {
                    val xb = i * h * w
                    val wb = ((o * c) + i) * 9
                    for (ky in 0 until 3) for (kx in 0 until 3) {
                        val wv = cv.w[wb + ky * 3 + kx]
                        if (wv == 0f) continue
                        // выходные столбцы, для которых входной ix = xx*st - 1 + kx попадает в [0, w)
                        val x0 = if (kx >= 1) 0 else 1
                        val x1 = minOf(ow - 1, (w - kx) / st)            // ix ≤ w-1 ⇔ xx ≤ (w - kx)/st
                        if (x1 < x0) continue
                        for (yy in 0 until oh) {
                            val iy = yy * st - 1 + ky
                            if (iy < 0 || iy >= h) continue
                            val ri = xb + iy * w - 1 + kx
                            val ro = ob + yy * ow
                            var xx = x0
                            while (xx <= x1) { y[ro + xx] += wv * x[ri + xx * st]; xx++ }
                        }
                    }
                }
                for (k in ob until ob + oh * ow) if (y[k] < 0f) y[k] = 0f
            }
            x = y; h = oh; w = ow; c = cv.outC
        }
        val emb = FloatArray(fcB.size)
        for (j in emb.indices) {
            var s = fcB[j]; val base = j * x.size
            for (k in x.indices) s += fcW[base + k] * x[k]
            emb[j] = if (s > 0f) s else 0f
        }
        var z = outB
        for (j in emb.indices) z += outW[j] * emb[j]
        return Out((1.0 / (1.0 + kotlin.math.exp(-z.toDouble()))).toFloat(), emb)
    }

    companion object {
        const val SR = 16_000
        const val NFFT = 512
        const val HOP = 160
        const val NMEL = 40
        const val FRAMES = 1 + (SR - NFFT) / HOP     // 97
        private const val MAGIC = 0x4B575331          // "KWS1"

        fun load(input: InputStream): KwsModel {
            val bytes = input.readBytes()
            val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            require(bb.int == MAGIC) { "not a KWS model" }
            fun arr(): FloatArray { val n = bb.int; return FloatArray(n) { bb.float } }
            val window = arr(); val mel = arr(); val mu = arr(); val sd = arr()
            val nConv = bb.int
            val convs = List(nConv) {
                val inC = bb.int; val outC = bb.int; val stride = bb.int
                Conv(inC, outC, stride, arr(), arr())
            }
            val fcW = arr(); val fcB = arr(); val outW = arr(); val outB = arr()[0]
            return KwsModel(window, mel, mu, sd, convs, fcW, fcB, outW, outB)
        }

        /** Комплексное БПФ на месте, длина — степень двойки. */
        internal fun fft(re: FloatArray, im: FloatArray) {
            val n = re.size
            var j = 0
            for (i in 1 until n) {
                var bit = n shr 1
                while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
                j = j xor bit
                if (i < j) { var t = re[i]; re[i] = re[j]; re[j] = t; t = im[i]; im[i] = im[j]; im[j] = t }
            }
            var len = 2
            while (len <= n) {
                val ang = -2.0 * Math.PI / len
                val wr = cos(ang).toFloat(); val wi = sin(ang).toFloat()
                var i = 0
                while (i < n) {
                    var cr = 1f; var ci = 0f
                    for (k in 0 until len / 2) {
                        val a = i + k; val b = a + len / 2
                        val tr = re[b] * cr - im[b] * ci; val ti = re[b] * ci + im[b] * cr
                        re[b] = re[a] - tr; im[b] = im[a] - ti
                        re[a] += tr; im[a] += ti
                        val ncr = cr * wr - ci * wi; ci = cr * wi + ci * wr; cr = ncr
                    }
                    i += len
                }
                len = len shl 1
            }
        }
    }
}

/**
 * Поток для [KwsModel]: принимает звук кусками, держит последнюю секунду кадров и раз в [everyFrames]
 * кадров (по умолчанию 10 = 100 мс) считает вероятность имени.
 */
class KwsStream(private val model: KwsModel, private val everyFrames: Int = 10) {
    private val ring = FloatArray(KwsModel.NFFT)
    private var pos = 0
    private var filled = 0
    private var sinceHop = 0
    private val frameBuf = FloatArray(KwsModel.NFFT)
    private val feats = FloatArray(KwsModel.FRAMES * KwsModel.NMEL)
    private var framesFilled = 0
    private var framesSinceRun = everyFrames - 1        // первый прогон — как только набралась секунда
    private val tmp = FloatArray(KwsModel.NMEL)

    fun reset() { pos = 0; filled = 0; sinceHop = 0; framesFilled = 0; framesSinceRun = everyFrames - 1 }

    /** Добавить отсчёты; [onResult] вызывается на каждом прогоне сети. */
    fun push(pcm: ShortArray, n: Int, onResult: (KwsModel.Out) -> Unit) {
        for (k in 0 until n) {
            ring[pos] = pcm[k] / 32768f
            pos = (pos + 1) % KwsModel.NFFT
            if (filled < KwsModel.NFFT) { filled++; if (filled < KwsModel.NFFT) continue; sinceHop = KwsModel.HOP }
            if (++sinceHop < KwsModel.HOP) continue
            sinceHop = 0
            // кадр — последние 512 отсчётов по порядку
            val head = KwsModel.NFFT - pos
            System.arraycopy(ring, pos, frameBuf, 0, head)
            System.arraycopy(ring, 0, frameBuf, head, pos)
            model.frame(frameBuf, 0, tmp, 0)
            System.arraycopy(feats, KwsModel.NMEL, feats, 0, feats.size - KwsModel.NMEL)
            System.arraycopy(tmp, 0, feats, feats.size - KwsModel.NMEL, KwsModel.NMEL)
            if (framesFilled < KwsModel.FRAMES) framesFilled++
            if (framesFilled == KwsModel.FRAMES && ++framesSinceRun >= everyFrames) {
                framesSinceRun = 0
                onResult(model.run(feats))
            }
        }
    }
}
