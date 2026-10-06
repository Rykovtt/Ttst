package com.kartoteka.app.data

import com.kartoteka.app.assistant.NoaBenchmark
import com.kartoteka.app.assistant.NoaParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDateTime

/** Контроль качества «свободного» набора (assets/golden_free.tsv): живая речь, которую правила не разбирают. */
class NoaFreeSetTest {
    private val now = LocalDateTime.of(2026, 10, 4, 10, 0)

    // Классы, которые модель способна выразить через действия интерпретатора
    private val expressible = setOf(
        "Call", "Message", "CreateAppointment", "MoveAppointment", "CancelAppointment", "AddNote", "Open", "OpenScreen",
        "LaunchApp", "CloseApp", "GoHome", "Lock", "Route", "Play", "Media", "WebSearch", "ShareData", "Remind", "Alarm",
        "Timer", "Flashlight", "PhoneSettings", "OpenContact", "Favorite", "Select", "Agenda", "PersonInfo", "Find",
        "Reply", "ReadMessages", "Backup",
    )

    private fun read(path: String) = File(path).readLines().filter { it.isNotBlank() }

    private val rows = read("src/main/assets/golden_free.tsv")

    private fun validSignature(sig: String): Boolean {
        if (sig in expressible) return true
        val m = Regex("^Seq\\(([A-Za-z]+(?:,[A-Za-z]+){1,2})\\)$").matchEntire(sig) ?: return false
        return m.groupValues[1].split(',').all { it in expressible }
    }

    @Test fun shapeAndLanguageMix() {
        assertTrue("строк ${rows.size}", rows.size >= 180)
        val phrases = ArrayList<String>()
        for (line in rows) {
            val f = line.split('\t')
            assertEquals("ровно два поля: $line", 2, f.size)
            assertTrue("сигнатура: $line", validSignature(f[1]))
            assertTrue("пустая фраза: $line", f[0].isNotBlank())
            phrases += f[0]
        }
        assertEquals("фразы не повторяются", phrases.size, phrases.map { it.lowercase().trim() }.toSet().size)
        val golden = read("src/main/assets/golden_phrases.txt").map { it.lowercase().trim() }.toSet()
        val examples = read("src/main/assets/examples.tsv").map { it.substringBefore('\t').lowercase().trim() }.toSet()
        for (p in phrases) {
            val k = p.lowercase().trim()
            assertTrue("совпадает с golden_phrases: $p", k !in golden)
            assertTrue("совпадает с examples.tsv: $p", k !in examples)
        }
        // Язык: латиница (английский и суржик с английскими словами) — en; украинские буквы і ї є ґ — украинский; прочее кириллическое — русский
        val en = phrases.count { p -> p.any { it in 'a'..'z' || it in 'A'..'Z' } }
        val uk = phrases.count { p -> p.none { it in 'a'..'z' || it in 'A'..'Z' } && p.any { it in "іїєґІЇЄҐ" } }
        val ru = phrases.size - en - uk
        val n = phrases.size
        println("язык: ru=${ru * 100 / n}% uk=${uk * 100 / n}% en=${en * 100 / n}% (n=$n)")
        assertTrue("ru ${ru * 100 / n}%", ru * 100 / n in 45..65)
        assertTrue("uk ${uk * 100 / n}%", uk * 100 / n in 25..45)
        assertTrue("en ${en * 100 / n}%", en * 100 / n in 5..15)
        val byClass = rows.groupingBy { it.split('\t')[1] }.eachCount().toSortedMap()
        println("классы: $byClass")
        val single = byClass.filterKeys { !it.startsWith("Seq") }
        for (c in expressible) assertTrue("класс $c: ${single[c] ?: 0} < 4", (single[c] ?: 0) >= 4)
        assertTrue("цепочек ${byClass.filterKeys { it.startsWith("Seq") }.values.sum()}", byClass.filterKeys { it.startsWith("Seq") }.values.sum() >= 8)
    }

    @Test fun rulesDoNotSolveTheSet() {
        var right = 0; var wrong = 0; var none = 0
        val solved = ArrayList<String>()
        val report = StringBuilder()
        for (line in rows) {
            val (p, exp) = line.split('\t')
            val got = NoaBenchmark.signature(runCatching { NoaParser.parse(p, now) }.getOrNull())
            report.append(if (got == exp) "OK" else if (got == "Unknown" || got == "—") "NONE" else "WRONG").append('\t').append(p).append('\t').append(exp).append('\t').append(got).append('\n')
            when {
                got == exp -> { right++; solved += "$p -> $got" }
                got == "Unknown" || got == "—" -> none++
                else -> wrong++
            }
        }
        File("build/golden_free_report.tsv").writeText(report.toString()) // разбор по фразам: статус, фраза, ожидалось, получено
        val n = rows.size
        println("правила: верно=$right (${right * 100 / n}%) неверно=$wrong (${wrong * 100 / n}%) не поняли=$none (${none * 100 / n}%)")
        solved.forEach { println("  РАЗОБРАНО ПРАВИЛАМИ: $it") }
        // Раньше правила намеренно «не решали» набор (≤ 10%); после доработки разбора живой речи доля верных выросла — потолок ослаблен, ошибки ограничивает NoaFreeParseTest.
        assertTrue("правила разбирают верно ${right * 100 / n}% (допустимо ≤ 50%)", right * 100 <= n * 50)
    }
}
