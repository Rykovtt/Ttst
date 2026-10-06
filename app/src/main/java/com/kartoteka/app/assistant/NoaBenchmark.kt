package com.kartoteka.app.assistant

import com.kartoteka.app.KartotekaApp
import com.kartoteka.app.i18n.t
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDateTime

/**
 * Проверка ИИ на самом телефоне: прогоняет через модель эталонные фразы и сравнивает её разбор с тем,
 * что даёт быстрый разбор по правилам (для этих фраз он проверен тестами). Результат — отчёт, который можно
 * скопировать: по нему видно, где модель ошибается, насколько она быстра и на чём работает (GPU / процессор).
 */
object NoaBenchmark {
    /** Короткая запись разбора: «Message», «Seq(Message,GoHome)», «ask», «chat», «—» (нет ответа). */
    fun signature(i: NoaIntent?): String = when (i) {
        null -> "—"
        is NoaIntent.Sequence -> "Seq(" + i.steps.joinToString(",") { signature(it) } + ")"
        else -> i::class.simpleName.orEmpty()
    }

    internal fun firstStep(sig: String) = sig.removePrefix("Seq(").substringBefore(',').removeSuffix(")")

    /** Эталонные фразы (assets/golden_phrases.txt), равномерно прореженные до [count]. */
    fun phrases(app: KartotekaApp, count: Int): List<String> {
        val all = runCatching { app.assets.open("golden_phrases.txt").bufferedReader().readLines() }.getOrDefault(emptyList())
            .map { it.trim() }.filter { it.isNotEmpty() }
        return pick(all, count)
    }

    internal fun pick(all: List<String>, count: Int): List<String> {
        if (count >= all.size || count <= 0) return all
        return List(count) { all[(it.toLong() * all.size / count).toInt()] }
    }

    data class Row(val phrase: String, val expected: String, val got: String, val millis: Long, val raw: String, val personOk: Boolean?)

    data class Report(val model: String, val backend: String, val rows: List<Row>, val note: String? = null, val tokens: List<Int> = emptyList(), val lastError: String? = null) {
        private val answered get() = rows.count { it.got != "—" }
        private val exact get() = rows.count { it.got == it.expected }
        private val firstOk get() = rows.count { firstStep(it.got) == firstStep(it.expected) }
        private fun pct(a: Int, b: Int) = if (b == 0) 0 else a * 100 / b

        fun text(version: String): String = buildString {
            append("RVault ").append(version).append(" · ").append(model).append(" · ").append(backend.ifBlank { "CPU" }).append('\n')
            note?.let { append(it).append('\n') }
            if (rows.isEmpty()) return@buildString
            val ms = rows.map { it.millis }.sorted()
            val people = rows.mapNotNull { it.personOk }
            append(t("Фраз: %1\$s", rows.size)).append('\n')
            if (tokens.isNotEmpty()) append(t("Размер запроса: в среднем %1\$s токенов (окно модели — 1280)", tokens.average().toInt())).append('\n')
            if (answered == 0 && lastError != null) append(t("Причина: %1\$s", explain(lastError))).append('\n')
            append(t("Ответ получен: %1\$s из %2\$s (%3\$s%%)", answered, rows.size, pct(answered, rows.size))).append('\n')
            append(t("Команда верная целиком: %1\$s из %2\$s (%3\$s%%)", exact, rows.size, pct(exact, rows.size))).append('\n')
            append(t("Первое действие верное: %1\$s из %2\$s (%3\$s%%)", firstOk, rows.size, pct(firstOk, rows.size))).append('\n')
            if (people.isNotEmpty()) append(t("Имя понято верно: %1\$s из %2\$s", people.count { it }, people.size)).append('\n')
            append(t("Время ответа: в среднем %1\$s с, 95%% — до %2\$s с", "%.1f".format(ms.average() / 1000), "%.1f".format(ms[(ms.size * 95 / 100).coerceAtMost(ms.size - 1)] / 1000.0))).append('\n')
            val bad = rows.filter { it.got != it.expected }
            if (bad.isNotEmpty()) {
                append('\n').append(t("Ошибки (первые %1\$s):", minOf(bad.size, 25))).append('\n')
                bad.take(25).forEachIndexed { n, r ->
                    append("${n + 1}. «${r.phrase}» → ${r.got} (${t("ожидалось")} ${r.expected})")
                    if (r.raw.isNotBlank()) append("\n   ").append(r.raw.replace('\n', ' ').take(90))
                    append('\n')
                }
            }
        }
    }

    internal fun explain(err: String): String = when {
        err.startsWith("too_long") -> t("запрос не помещается в окно модели (%1\$s токенов)", err.substringAfter(':'))
        err == "no_model" -> t("модель не загружена в службе")
        err == "not_ready" -> t("модель не готова")
        err == "engine_crash" -> t("движок ИИ аварийно закрывается при ответе (и на видеокарте, и на процессоре)")
        err == "no_reply" -> t("служба модели не ответила (возможно, не хватило памяти)")
        else -> err
    }

    /** Прогон. [count] — сколько фраз (короткая проверка ~30, полная ~140); [cancelled] прерывает между фразами. */
    suspend fun run(app: KartotekaApp, count: Int, onProgress: (Int, Int) -> Unit, cancelled: () -> Boolean): Report {
        val title = app.brain.installed()?.title ?: t("своя модель")
        if (app.brain.prepare() != LlmBrain.State.READY) {
            return Report(title, "", emptyList(), app.brain.detail.ifBlank { t("Модель не запущена. Откройте ассистента и дождитесь «ИИ на устройстве».") })
        }
        // Сначала короткий настоящий ответ: нет смысла гнать фразы, если движок падает.
        onProgress(0, 1)
        if (!app.brain.selfTest()) {
            val why = app.brain.lastError?.let { explain(it) }.orEmpty()
            return Report(title, app.brain.backend.uppercase(), emptyList(),
                t("Модель загрузилась, но не отвечает. Причина: %1\$s. Выберите быструю модель в настройках ассистента.", why))
        }
        val now = LocalDateTime.of(2026, 10, 4, 10, 0)
        val noa = Noa(app)
        val names = noa.knownNames(60)
        val interpreter = NoaInterpreter(app.brain)
        val list = phrases(app, count)
        val rows = ArrayList<Row>()
        val tokens = ArrayList<Int>()
        var lastErr: String? = null
        for ((n, phrase) in list.withIndex()) {
            if (cancelled()) break
            onProgress(n, list.size)
            val rules = NoaParser.parse(phrase, now)
            val expected = signature(rules)
            val t0 = System.nanoTime()
            val r = runCatching { withTimeoutOrNull(90_000) { interpreter.interpret(phrase, now, names = names) } }.getOrNull()
            val ms = (System.nanoTime() - t0) / 1_000_000
            val got = when {
                r == null -> "—"
                r.ask != null -> "ask"
                r.intent != null -> signature(r.intent)
                else -> "chat"
            }
            val wantPerson = NoaParser.personOf(if (rules is NoaIntent.Sequence) rules.steps.first() else rules).trim()
            val gotPerson = r?.intent?.let { NoaParser.personOf(if (it is NoaIntent.Sequence) it.steps.first() else it) }?.trim().orEmpty()
            val personOk = wantPerson.takeIf { it.isNotBlank() }?.let { samePerson(it, gotPerson) }
            val err = app.brain.lastError
            if (app.brain.lastTokens > 0) tokens += app.brain.lastTokens
            if (err != null) lastErr = err
            rows += Row(phrase, expected, got, ms, if (got == "—" && err != null) "${t("ошибка")}: ${explain(err)}" else interpreter.lastRaw.orEmpty(), personOk)
        }
        onProgress(list.size, list.size)
        return Report(title, app.brain.backend.uppercase(), rows, tokens = tokens, lastError = lastErr)
    }

    /** Имя из фразы («Илью») и из ответа модели («Илья») — один человек: совпадают первые 3–4 буквы. */
    internal fun samePerson(a: String, b: String): Boolean {
        fun key(s: String) = s.lowercase().split(Regex("[^\\p{L}]+")).firstOrNull { it.length >= 2 }.orEmpty()
            .replace('ё', 'е').replace('і', 'и').replace('ї', 'и').replace('й', 'и').replace('ы', 'и').let { it.take(maxOf(3, it.length - 2)).take(4) }
        val x = key(a); val y = key(b)
        return x.isNotEmpty() && y.isNotEmpty() && (x == y || x.startsWith(y) || y.startsWith(x))
    }
}
