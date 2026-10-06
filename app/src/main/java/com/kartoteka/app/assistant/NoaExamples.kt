package com.kartoteka.app.assistant

import java.io.File
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Библиотека проверенных примеров «фраза → JSON» (assets/examples.tsv — от правил, assets/examples_free.tsv — вольная речь,
 * написанная вручную; библиотека — объединение двух файлов) и быстрый поиск самых похожих на команду.
 * Маленькая модель плохо учится схеме по голым названиям действий, зато уверенно повторяет форму похожего примера:
 * в промпт кладём три ближайших вместо статичных примеров. Поиск офлайн и детерминированный: совпадение основ слов
 * (с весом редкости) плюс триграммы символов (устойчиво к ошибкам распознавания); имена людей и вежливые слова не считаются.
 */
object NoaExamples {
    /** Пример: фраза, JSON-ответ без reply, класс (первое действие; «seq» — цепочка; «chat»/«ask» — без действий). */
    class Ex internal constructor(val phrase: String, val json: String, val cls: String, internal val stems: Map<String, Double>, internal val tri: Set<String>, internal val first: String? = null) {
        val line: String get() = "$phrase → $json"
    }

    /** Откуда читать библиотеку на телефоне (задаёт приложение при запуске); в тестах читаем файл из исходников. */
    @Volatile var assets: android.content.res.AssetManager? = null

    /** Файлы библиотеки: фразы, которые понимают правила, и вольные формулировки (филлеры, суржик, косвенные просьбы). */
    internal val FILES = listOf("examples.tsv", "examples_free.tsv")

    private val default: Index by lazy { Index(readLines()) }

    /** Загружена ли библиотека (для проверок и диагностики). */
    val size: Int get() = default.items.size

    /** Все примеры (для проверок). */
    val all: List<Ex> get() = default.items

    /** [k] самых похожих на [phrase] примеров из общей библиотеки; [names] — имена людей из книжки (их слова не учитываются). */
    fun pick(phrase: String, k: Int = 3, names: List<String> = emptyList()): List<Ex> = default.pick(phrase, k, names)

    /** Самый похожий пример и его оценка 0..1 ([exclude] — фраза, которую не брать: для проверки без подсказки ответа). */
    fun best(phrase: String, names: List<String> = emptyList(), exclude: String? = null): Pair<Ex, Double>? = default.best(phrase, names, exclude)

    private fun readLines(): List<String> = FILES.flatMap { readFile(it) }

    private fun readFile(name: String): List<String> {
        runCatching { assets?.open(name)?.bufferedReader()?.use { it.readLines() } }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
        for (p in listOf("src/main/assets/$name", "app/src/main/assets/$name")) {
            runCatching { File(p).takeIf { it.isFile }?.readLines() }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return emptyList()
    }

    // ---- слова, основы, триграммы ----

    private val STOP = setOf(
        "на", "в", "во", "у", "по", "и", "і", "й", "та", "а", "к", "ко", "с", "со", "из", "от", "до", "за", "для", "про", "о", "об", "это", "це", "ну", "так", "вот",
        "пожалуйста", "будь", "ласка", "будьласка", "плиз", "please", "слушай", "слухай", "ноа", "noa", "санта", "эй", "hey", "the", "a", "to", "me", "my", "мне", "мені", "мою", "мой",
        "меня", "мене", "не", "ещё", "еще", "ж", "же", "ли", "бы", "уже", "тоже", "теж", "ей", "ему", "её", "ее", "его", "їй", "йому", "її", "його", "им", "ним", "ними",
    )

    private val VOWELS = "аеёиіїоуыэюяє"

    /** Основа: сводим алфавиты, все гласные считаем одной (открой/открыть/відкрий), схлопываем повторы, берём первые 5 знаков. */
    internal fun stem(w: String): String {
        val f = NoaMatch.fold(w)
        val b = StringBuilder()
        for (c in f) {
            val m = if (c in VOWELS) 'а' else if (c == 'ъ') continue else c
            if (b.isEmpty() || b.last() != m) b.append(m)
        }
        return b.toString().take(5)
    }

    private val STOP_STEMS: Set<String> = STOP.map { stem(it) }.toSet()

    /** Слова фразы: числа и время → «#», остальное — буквы нижнего регистра; второй список — слова с заглавной буквы не в начале (вероятно, имена). */
    private fun words(phrase: String): Pair<List<String>, Set<String>> {
        val s = phrase.replace(Regex("\\d+(?:[:.]\\d+)?"), " # ")
        val out = ArrayList<String>()
        val caps = HashSet<String>()
        for ((i, raw) in s.split(Regex("[^\\p{L}#]+")).filter { it.isNotEmpty() }.withIndex()) {
            val w = raw.lowercase()
            out += w
            if (i > 0 && raw.length >= 2 && raw[0].isUpperCase() && raw.drop(1).any { it.isLowerCase() }) caps += stem(w)
        }
        return out to caps
    }

    private fun trigrams(ws: List<String>): Set<String> {
        val s = "  " + ws.joinToString(" ") { NoaMatch.fold(it) } + " "
        if (s.length < 3) return emptySet()
        return (0..s.length - 3).mapTo(HashSet()) { s.substring(it, it + 3) }
    }

    /** Основы слов с весом по месту: два первых слова (глагол и объект команды) важнее остального («добавь заметку …» против «… и добавь заметку»). */
    private fun weighted(ws: List<String>): Map<String, Double> {
        val m = LinkedHashMap<String, Double>()
        for ((i, w) in ws.withIndex()) m.merge(stem(w), if (i < 2) 1.6 else 1.0, ::maxOf)
        return m
    }

    /** Класс по JSON ответа. */
    internal fun classOf(json: String): String {
        val acts = Regex("\"action\":\"([a-z_]+)\"").findAll(json).map { it.groupValues[1] }.toList()
        return when {
            acts.size > 1 -> "seq"
            acts.size == 1 -> acts[0]
            json.contains("\"ask\"") -> "ask"
            else -> "chat"
        }
    }

    /** Индекс примеров. Строки: «фраза TAB JSON»; битые строки пропускаем. */
    class Index(lines: List<String>) {
        val items: List<Ex>
        private val idf: Map<String, Double>
        private val maxIdf: Double
        private val nameStems: Set<String>

        init {
            val raw = lines.mapNotNull { l ->
                val t = l.split('\t')
                if (t.size < 2 || t[0].isBlank() || !t[1].trimStart().startsWith("{")) null else t[0].trim() to t[1].trim()
            }
            // Имена людей из ответов: их слова в фразах не считаем.
            val names = HashSet<String>()
            for ((_, j) in raw) for (m in Regex("\"person\":\"([^\"]+)\"").findAll(j)) for (w in m.groupValues[1].split(' ')) names += stem(w.lowercase())
            names.removeAll(STOP_STEMS)
            nameStems = names
            items = raw.map { (p, j) ->
                val (ws, caps) = words(p)
                val keep = ws.filter { w -> stem(w).let { it !in STOP_STEMS && it !in names && it !in caps } }
                Ex(p, j, classOf(j), weighted(keep), trigrams(keep), keep.firstOrNull()?.let { stem(it) })
            }
            val df = HashMap<String, Int>()
            for (e in items) for (s in e.stems.keys) df.merge(s, 1, Int::plus)
            val n = items.size.coerceAtLeast(1)
            idf = df.mapValues { ln(1.0 + n.toDouble() / it.value) }
            maxIdf = ln(1.0 + n)
        }

        private fun w(s: String) = idf[s] ?: (maxIdf * 0.6)

        /** Оценка сходства 0..1: косинус по основам с весами редкости и коэффициент Жаккара по триграммам. */
        private fun score(qs: Map<String, Double>, qt: Set<String>, qFirst: String?, e: Ex): Double {
            if (qs.isEmpty() && qt.isEmpty()) return 0.0
            var dot = 0.0; var qn = 0.0; var en = 0.0
            for ((s, m) in qs) qn += w(s) * m * w(s) * m
            for ((s, m) in e.stems) en += w(s) * m * w(s) * m
            for ((s, m) in qs) e.stems[s]?.let { dot += w(s) * m * w(s) * it }
            val cos = if (qn > 0 && en > 0) dot / sqrt(qn * en) else 0.0
            val inter = qt.count { it in e.tri }
            val jac = if (qt.isEmpty() || e.tri.isEmpty()) 0.0 else inter.toDouble() / (qt.size + e.tri.size - inter)
            val verb = if (qFirst != null && qFirst == e.first) 0.15 else 0.0
            val raw = 0.65 * cos + 0.35 * jac + verb
            // Короткие «уточнение/болтовня» («добавь заметку» → «К кому?») не должны перебивать полные команды с содержанием.
            return if ((e.cls == "ask" || e.cls == "chat") && qs.size > e.stems.size + 1) raw * 0.6 else raw
        }

        fun best(phrase: String, names: List<String> = emptyList(), exclude: String? = null): Pair<Ex, Double>? {
            if (items.isEmpty()) return null
            val own = names.flatMap { it.replace(Regex("\\([^)]*\\)"), " ").split(Regex("[^\\p{L}]+")) }.filter { it.length >= 2 }.map { stem(it.lowercase()) }.toSet()
            val (ws, caps) = words(phrase)
            val keep = ws.filter { w -> stem(w).let { it !in STOP_STEMS && it !in nameStems && it !in own && it !in caps } }
            val qs = weighted(keep); val qt = trigrams(keep); val qFirst = keep.firstOrNull()?.let { stem(it) }
            return items.asSequence().filter { exclude == null || !it.phrase.equals(exclude, ignoreCase = true) }
                .map { it to score(qs, qt, qFirst, it) }.maxByOrNull { it.second }
        }

        /** [k] ближайших примеров; не больше двух одного класса, если фраза не «явно этого класса» (тогда все три из него). */
        fun pick(phrase: String, k: Int = 3, names: List<String> = emptyList()): List<Ex> {
            if (items.isEmpty() || k <= 0) return emptyList()
            val own = names.flatMap { it.replace(Regex("\\([^)]*\\)"), " ").split(Regex("[^\\p{L}]+")) }.filter { it.length >= 2 }.map { stem(it.lowercase()) }.toSet()
            val (ws, caps) = words(phrase)
            val keep = ws.filter { w -> stem(w).let { it !in STOP_STEMS && it !in nameStems && it !in own && it !in caps } }
            val qs = weighted(keep)
            val qt = trigrams(keep)
            val qFirst = keep.firstOrNull()?.let { stem(it) }
            val ranked = items.withIndex().map { (i, e) -> Triple(i, e, score(qs, qt, qFirst, e)) }
                .sortedWith(compareByDescending<Triple<Int, Ex, Double>> { it.third }.thenBy { it.first })
            val top = ranked.first().third
            val out = ArrayList<Triple<Int, Ex, Double>>()
            val perClass = HashMap<String, Int>()
            // «Явно этого класса»: три первых одного класса и первый достаточно близок — тогда разнообразие не нужно.
            val clear = top >= 0.5 && ranked.take(k).all { it.second.cls == ranked[0].second.cls }
            for (r in ranked) {
                if (out.size >= k) break
                val c = perClass[r.second.cls] ?: 0
                if (c >= 1 && !clear && (c >= 2 || r.third < top * 0.8)) continue
                out += r; perClass.merge(r.second.cls, 1, Int::plus)
            }
            if (out.size < k) for (r in ranked) { if (out.size >= k) break; if (out.none { it.first == r.first }) out += r }
            return out.map { it.second }
        }
    }
}
