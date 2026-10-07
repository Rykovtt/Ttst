package com.kartoteka.app.assistant

import com.kartoteka.app.KartotekaApp
import com.kartoteka.app.i18n.t
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDateTime

/**
 * Проверка на самом телефоне. Два набора фраз:
 *  • «весь конвейер» — как люди говорят на самом деле (assets/golden_free.tsv, ответ размечен вручную): сначала правила, и только
 *    что они не поняли — модель, а если и она промолчала — запасной разбор по библиотеке примеров. Это то, что видит человек;
 *  • «эталон по правилам» (диагностика модели, только в полной проверке) — фразы, которые правила понимают; модель должна дать то же самое.
 * Команды, которые решают только правила (время, калькулятор, переводы единиц, вопросы о записях), в проверку модели не входят:
 * в работе она их не увидит. Результат — отчёт, который можно скопировать.
 */
object NoaBenchmark {
    /** Короткая запись разбора: «Message», «Seq(Message,GoHome)», «ask», «chat», «—» (нет ответа). */
    fun signature(i: NoaIntent?): String = when (i) {
        null -> "—"
        is NoaIntent.Sequence -> "Seq(" + i.steps.joinToString(",") { signature(it) } + ")"
        else -> i::class.simpleName.orEmpty()
    }

    internal fun firstStep(sig: String) = sig.removePrefix("Seq(").substringBefore(',').removeSuffix(")")

    /** Эти команды модель выразить не может (их нет среди её действий) — их разбирают только правила. */
    private val RULES_ONLY = setOf("Tool", "Calc", "Convert", "Crm", "Dismiss", "Repeat", "Wrong", "Unknown")

    data class Item(val phrase: String, val expected: String, val group: String)

    private fun lines(app: KartotekaApp, file: String): List<String> =
        runCatching { app.assets.open(file).bufferedReader().readLines() }.getOrDefault(emptyList()).map { it.trim() }.filter { it.isNotEmpty() }

    /** Эталон по правилам: фразы из assets/golden_phrases.txt, ожидаемое берём у разбора по правилам. */
    internal fun rulesSet(app: KartotekaApp, now: LocalDateTime): List<Item> =
        lines(app, "golden_phrases.txt").mapNotNull { p ->
            val sig = signature(NoaParser.parse(p, now))
            if (firstStep(sig) in RULES_ONLY) null else Item(p, sig, "rules")
        }

    /** Свободные формулировки: assets/golden_free.tsv — «фраза<TAB>ожидаемое» (подпись разбора), размечено вручную. */
    internal fun freeSet(app: KartotekaApp): List<Item> =
        lines(app, "golden_free.tsv").mapNotNull { l ->
            val p = l.split('\t'); if (p.size < 2 || p[0].isBlank()) null else Item(p[0].trim(), p[1].trim(), "pipe")
        }

    /** Короткая проверка — [count] фраз «всего конвейера» (равномерно по набору); полная — весь набор и ещё эталон по правилам для модели. */
    fun items(app: KartotekaApp, count: Int, now: LocalDateTime): List<Item> {
        val pipe = freeSet(app)
        if (pipe.isEmpty()) return pick(rulesSet(app, now), if (count <= 0) 0 else count)
        return if (count <= 0) pipe + rulesSet(app, now) else pick(pipe, count)
    }

    internal fun <T> pick(all: List<T>, count: Int): List<T> {
        if (count >= all.size || count <= 0) return all
        return List(count) { all[(it.toLong() * all.size / count).toInt()] }
    }

    /** [via] — кто ответил: «rules» (правила), «ai» (модель), «lib» (запасной разбор по примерам), пусто — никто. */
    data class Row(val item: Item, val got: String, val millis: Long, val raw: String, val personOk: Boolean?, val via: String = "") {
        val phrase get() = item.phrase; val expected get() = item.expected
    }

    data class Report(val model: String, val backend: String, val rows: List<Row>, val note: String? = null, val tokens: List<Int> = emptyList(), val lastError: String? = null) {
        private fun pct(a: Int, b: Int) = if (b == 0) 0 else a * 100 / b
        private fun stats(list: List<Row>): String {
            val answered = list.count { it.got != "—" }
            val exact = list.count { it.got == it.expected }
            val first = list.count { firstStep(it.got) == firstStep(it.expected) }
            val people = list.mapNotNull { it.personOk }
            return buildString {
                append(t("Ответ получен: %1\$s из %2\$s (%3\$s%%)", answered, list.size, pct(answered, list.size))).append('\n')
                append(t("Команда верная целиком: %1\$s из %2\$s (%3\$s%%)", exact, list.size, pct(exact, list.size))).append('\n')
                append(t("Первое действие верное: %1\$s из %2\$s (%3\$s%%)", first, list.size, pct(first, list.size))).append('\n')
                if (people.isNotEmpty()) append(t("Имя понято верно: %1\$s из %2\$s", people.count { it }, people.size)).append('\n')
                // «Уверенно неверно» — самое вредное: помощник сделал не то, не переспросив.
                val unsure = setOf("—", "ask", "chat", "Unknown")
                val wrong = list.count { it.got !in unsure && it.got != it.expected }
                append(t("Уверенно неверно: %1\$s из %2\$s (%3\$s%%)", wrong, list.size, pct(wrong, list.size))).append('\n')
                val byVia = list.groupBy { it.via }.filterKeys { it.isNotEmpty() }
                if (byVia.isNotEmpty()) append(t("Кто отвечал")).append(": ").append(listOf("rules" to t("правила"), "ai" to t("модель"), "lib" to t("библиотека")).mapNotNull { (k, label) ->
                    byVia[k]?.let { "$label ${it.size} (${t("верно")} ${it.count { r -> r.got == r.expected }})" }
                }.joinToString(", ")).append('\n')
            }
        }

        fun text(version: String): String = buildString {
            append("RVault ").append(version).append(" · ").append(model).append(" · ").append(backend.ifBlank { "CPU" }).append('\n')
            note?.let { append(it).append('\n') }
            if (rows.isEmpty()) return@buildString
            val ms = rows.map { it.millis }.sorted()
            append(t("Фраз: %1\$s", rows.size)).append('\n')
            if (tokens.isNotEmpty()) append(t("Размер запроса: в среднем %1\$s токенов (окно модели — 1280)", tokens.average().toInt())).append('\n')
            if (rows.count { it.got != "—" } == 0 && lastError != null) append(t("Причина: %1\$s", explain(lastError))).append('\n')
            val rules = rows.filter { it.item.group == "rules" }; val pipe = rows.filter { it.item.group == "pipe" }
            if (pipe.isNotEmpty()) append('\n').append(t("Весь конвейер (правила → модель → библиотека)")).append(": ").append(pipe.size).append('\n').append(stats(pipe))
            if (rules.isNotEmpty()) append('\n').append(t("Диагностика модели (эталон по правилам)")).append(": ").append(rules.size).append('\n').append(stats(rules))
            append('\n').append(t("Время ответа: в среднем %1\$s с, 95%% — до %2\$s с", "%.1f".format(ms.average() / 1000), "%.1f".format(ms[(ms.size * 95 / 100).coerceAtMost(ms.size - 1)] / 1000.0))).append('\n')
            val bad = rows.filter { it.got != it.expected }
            if (bad.isNotEmpty()) {
                append('\n').append(t("Ошибки (первые %1\$s):", minOf(bad.size, 25))).append('\n')
                bad.take(25).forEachIndexed { n, r ->
                    append("${n + 1}. [${if (r.item.group == "pipe") "кв" else "эт"}${if (r.via.isNotEmpty()) "/" + r.via else ""}] «${r.phrase}» → ${r.got} (${t("ожидалось")} ${r.expected})")
                    if (r.raw.isNotBlank()) append("\n   ").append(r.raw.replace('\n', ' ').take(110))
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

    /** Прогон. [count] — сколько фраз (короткая ~30, 0 — все); [cancelled] прерывает между фразами. */
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
                t("Модель загрузилась, но не отвечает. Причина: %1\$s. Перезапустите приложение и попробуйте снова.", why))
        }
        val now = LocalDateTime.of(2026, 10, 4, 10, 0)
        val noa = Noa(app)
        val names = noa.knownNames(60)
        val interpreter = NoaInterpreter(app.brain)
        val list = items(app, count, now)
        val rows = ArrayList<Row>()
        val tokens = ArrayList<Int>()
        var lastErr: String? = null
        for ((n, item) in list.withIndex()) {
            if (cancelled()) break
            onProgress(n, list.size)
            val t0 = System.nanoTime()
            // Конвейер: правила первыми; модель — только если правила не поняли; библиотека — если не ответила и модель.
            val ruled = if (item.group == "pipe") NoaParser.parse(item.phrase, now).takeIf { it !is NoaIntent.Unknown } else null
            val r = if (ruled != null) Interpreted(ruled, null)
                else runCatching { withTimeoutOrNull(90_000) { interpreter.interpret(item.phrase, now, names = names) } }.getOrNull()
            val ms = (System.nanoTime() - t0) / 1_000_000
            val got = when {
                r == null -> "—"
                r.ask != null -> "ask"
                r.intent != null -> signature(r.intent)
                else -> "chat"
            }
            val via = when { ruled != null -> "rules"; r?.rescued == true -> "lib"; r?.intent != null || r?.ask != null -> "ai"; else -> "" }
            val wantPerson = personOfExpected(item, now)
            val gotPerson = r?.intent?.let { NoaParser.personOf(if (it is NoaIntent.Sequence) it.steps.first() else it) }?.trim().orEmpty()
            val personOk = wantPerson.takeIf { it.isNotBlank() }?.let { samePerson(it, gotPerson) }
            val err = if (ruled != null) null else app.brain.lastError
            if (ruled == null && app.brain.lastTokens > 0) tokens += app.brain.lastTokens
            if (err != null) lastErr = err
            rows += Row(item, got, ms, if (ruled != null) "" else if (got == "—" && err != null) "${t("ошибка")}: ${explain(err)}" else interpreter.lastRaw.orEmpty(), personOk, via)
        }
        onProgress(list.size, list.size)
        return Report(title, app.brain.backend.uppercase(), rows, tokens = tokens, lastError = lastErr)
    }

    /** Имя из фразы: у эталона — по разбору правил; у свободной фразы — по правилам, если поняли (иначе не проверяем). */
    private fun personOfExpected(item: Item, now: LocalDateTime): String {
        val i = NoaParser.parse(item.phrase, now)
        return NoaParser.personOf(if (i is NoaIntent.Sequence) i.steps.firstOrNull() ?: i else i).trim()
    }

    /** Имя из фразы («Илью», «Ани») и из ответа модели («Илья Риков», «Анна Иванова») — один человек, с учётом падежей и русских/украинских букв. */
    internal fun samePerson(a: String, b: String): Boolean {
        fun first(s: String) = s.lowercase().split(Regex("[^\\p{L}]+")).firstOrNull { it.length >= 2 }.orEmpty()
        val x = NoaMatch.stem(first(a)).replace(Regex("(.)\\1"), "$1")
        val y = NoaMatch.stem(first(b)).replace(Regex("(.)\\1"), "$1")
        return x.length >= 2 && y.length >= 2 && NoaMatch.stemMatch(x, y)
    }
}
