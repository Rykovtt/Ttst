package com.kartoteka.app.data

import com.kartoteka.app.assistant.NoaBenchmark
import com.kartoteka.app.assistant.NoaFeedbackLog
import com.kartoteka.app.assistant.NoaHints
import com.kartoteka.app.assistant.NoaIntent
import com.kartoteka.app.assistant.NoaParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDateTime

/** Проверка ИИ, журнал непонятых фраз и подсказки распознавателю. */
class NoaQualityTest {
    private val now = LocalDateTime.of(2026, 10, 4, 10, 0)

    @Test fun goldenPhrasesAreVerifiedByRules() {
        val lines = File("src/main/assets/golden_phrases.txt").readLines().filter { it.isNotBlank() }
        assertTrue("эталонных фраз ${lines.size}", lines.size >= 120)
        val classes = HashSet<String>()
        val bad = ArrayList<String>()
        for (p in lines) {
            val i = NoaParser.parse(p, now)
            if (i is NoaIntent.Unknown) bad += p else classes += NoaBenchmark.signature(i)
        }
        assertTrue("правила не понимают эталонные фразы: $bad", bad.isEmpty())
        assertTrue("разнообразие команд: ${classes.size}", classes.size >= 25)
    }

    @Test fun signatureAndPick() {
        assertEquals("Call", NoaBenchmark.signature(NoaParser.parse("позвони маме", now)))
        assertEquals("—", NoaBenchmark.signature(null))
        val seq = NoaParser.parse("напиши Ане что опаздываю и сверни", now)
        assertTrue(NoaBenchmark.signature(seq).startsWith("Seq(Message"))
        assertEquals("Message", NoaBenchmark.firstStep(NoaBenchmark.signature(seq)))
        assertEquals(3, NoaBenchmark.pick((1..9).map { "p$it" }, 3).size)
        assertEquals(9, NoaBenchmark.pick((1..9).map { "p$it" }, 0).size)
    }

    @Test fun personComparisonToleratesCasesAndLetters() {
        assertTrue(NoaBenchmark.samePerson("Илью", "Илья"))
        assertTrue(NoaBenchmark.samePerson("Ірину", "Ирина"))
        assertFalse(NoaBenchmark.samePerson("Олю", "Анна"))
    }

    @Test fun logMasksNamesAndNumbers() {
        val names = listOf("Илья Рыков", "Анна")
        assertEquals("напиши ИМЯ что я на месте", NoaFeedbackLog.mask("напиши Илье что я на месте", names))
        assertEquals("позвони ИМЯ ИМЯ", NoaFeedbackLog.mask("позвони Ильи Рыкову", names))
        assertEquals("мой номер №", NoaFeedbackLog.mask("мой номер +380 67 111 22 33", names))
        assertEquals("создай заметку", NoaFeedbackLog.mask("создай заметку", names))      // обычные слова не трогаем
        val out = NoaFeedbackLog.export(listOf(NoaFeedbackLog.Entry("2026-10-06 10:00", "unknown", "запиши Анну на завтра", "Unknown", "", "")), names, hideNames = true)
        assertTrue(out.contains("не поняла") && out.contains("запиши ИМЯ на завтра"))
    }

    @Test fun nameFormsForRecognizerHints() {
        assertTrue(NoaHints.forms("Илья").containsAll(listOf("Илья", "Ильи", "Илье", "Илью")))
        assertTrue(NoaHints.forms("Рыков").containsAll(listOf("Рыкова", "Рыкову")))
        assertTrue(NoaHints.forms("Анна").containsAll(listOf("Анны", "Анне", "Анну")))
        assertTrue(NoaHints.forms("Ірина").containsAll(listOf("Ірини", "Ірині", "Ірину")))
        assertEquals(listOf("Kate"), NoaHints.forms("Kate"))      // латиница — как есть
        assertTrue(NoaHints.forms("").isEmpty())
    }

    @Test fun wrongCommandIsRecognised() {
        listOf("это не то", "ты неправильно поняла", "ти не так зрозуміла", "неправильно").forEach {
            assertEquals(it, NoaIntent.Wrong, NoaParser.parse(it, now))
        }
        assertTrue(NoaParser.parse("позвони маме", now) !is NoaIntent.Wrong)
    }
}
