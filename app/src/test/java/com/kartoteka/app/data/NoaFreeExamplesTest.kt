package com.kartoteka.app.data

import com.kartoteka.app.assistant.Lenient
import com.kartoteka.app.assistant.NoaBenchmark
import com.kartoteka.app.assistant.NoaExamples
import com.kartoteka.app.assistant.NoaInterpreter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.LocalDateTime

/**
 * Библиотека вольных формулировок (assets/examples_free.tsv): написана вручную, не правилами.
 * Проверяем форму файла, что каждый JSON принимает реестр интерпретатора, покрытие действий, поиск по новым фразам и скорость.
 */
class NoaFreeExamplesTest {
    private val now = LocalDateTime.of(2026, 10, 4, 10, 0)
    private val ip = NoaInterpreter(null)
    private val rawLines = File("src/main/assets/examples_free.tsv").readLines().filter { it.isNotBlank() }
    private val pairs = rawLines.map { it.split('\t').let { t -> t[0] to t[1] } }

    /** Действие реестра → класс команды (так, как его видит NoaBenchmark.signature). */
    private val classOfAction = mapOf(
        "create_appointment" to "CreateAppointment", "cancel_appointment" to "CancelAppointment", "delete_appointment" to "CancelAppointment",
        "move_appointment" to "MoveAppointment", "call" to "Call", "message" to "Message", "reply" to "Reply", "read_messages" to "ReadMessages",
        "add_note" to "AddNote", "open_person" to "Open", "find" to "Find", "open_contact" to "OpenContact", "route" to "Route", "agenda" to "Agenda",
        "person_info" to "PersonInfo", "favorite" to "Favorite", "select" to "Select", "share_data" to "ShareData", "launch_app" to "LaunchApp",
        "close_app" to "CloseApp", "web_search" to "WebSearch", "alarm" to "Alarm", "timer" to "Timer", "remind" to "Remind", "flashlight" to "Flashlight",
        "phone_settings" to "PhoneSettings", "play_music" to "Play", "media" to "Media", "go_home" to "GoHome", "open_screen" to "OpenScreen",
        "lock" to "Lock", "backup" to "Backup",
    )

    private fun actionsOf(json: String) = Regex("\"action\":\"([a-z_]+)\"").findAll(json).map { it.groupValues[1] }.toList()

    private fun norm(s: String) = s.lowercase().replace('ё', 'е').replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    @Test fun fileIsWellFormed() {
        assertTrue("примеров ${pairs.size}", pairs.size >= 1200)
        for (l in rawLines) {
            assertEquals("ровно один TAB: $l", 2, l.split('\t').size)
            val (p, j) = l.split('\t')
            assertTrue(p, p.isNotBlank() && p == p.trim())
            assertTrue(j, j.startsWith("{\"actions\":[") && j.endsWith("}"))
            val root = Lenient.extract(j) as? Map<*, *>
            assertNotNull("JSON не разбирается: $j", root)
            assertTrue(j, root!!["actions"] is List<*>)
            if (actionsOf(j).isNotEmpty()) assertFalse("для действий reply не пишем: $j", j.contains("\"reply\":"))
            else assertTrue(j, j.contains("\"reply\":") || j.contains("\"ask\":"))
            for (a in actionsOf(j)) assertTrue("неизвестное действие $a", a in classOfAction)
        }
    }

    @Test fun phrasesAreUniqueAndNotInOtherLibraries() {
        val keys = pairs.map { norm(it.first) }
        assertEquals("фразы не повторяются", keys.size, keys.toSet().size)
        val base = File("src/main/assets/examples.tsv").readLines().filter { it.isNotBlank() }.map { norm(it.substringBefore('\t')) }.toSet() +
            File("src/main/assets/golden_phrases.txt").readLines().filter { it.isNotBlank() }.map { norm(it) }
        val clash = pairs.map { it.first }.filter { norm(it) in base }
        assertTrue("совпадают с examples.tsv/golden_phrases.txt: $clash", clash.isEmpty())
    }

    @Test fun everyJsonIsAcceptedByTheRegistry() {
        val bad = ArrayList<String>()
        for ((p, j) in pairs) {
            val back = ip.fromJson(j, p, now)
            val acts = actionsOf(j)
            if (acts.isEmpty()) {
                if (back == null || (back.reply == null && back.ask == null)) bad += "«$p» $j → $back"
                continue
            }
            val want = acts.map { classOfAction.getValue(it) }.let { if (it.size > 1) "Seq(" + it.joinToString(",") + ")" else it[0] }
            val got = NoaBenchmark.signature(back?.intent)
            if (want != got) bad += "«$p» ждали $want, получили $got"
        }
        assertTrue("реестр отверг ${bad.size}:\n" + bad.take(60).joinToString("\n"), bad.isEmpty())
    }

    @Test fun everyActionHasEnoughDistinctPhrases() {
        val single = pairs.filter { actionsOf(it.second).size == 1 }.groupBy { actionsOf(it.second)[0] }
        val counts = classOfAction.keys.associateWith { single[it]?.map { e -> norm(e.first) }?.toSet()?.size ?: 0 }
        println("вольные примеры по действиям: " + counts.entries.sortedByDescending { it.value }.joinToString { "${it.key}=${it.value}" })
        println("цепочек: ${pairs.count { actionsOf(it.second).size > 1 }}, разговор/вопросы: ${pairs.count { actionsOf(it.second).isEmpty() }}")
        for ((a, n) in counts) assertTrue("$a: $n фраз", n >= 30)
        val uk = pairs.count { (p, _) -> p.any { it in "іїєґ" } || Regex("(?i)\\b(будь ласка|слухай|зроби|відкрий|постав|додай|нагадай|покажи-но|знайди|скасуй|вимкни|увімкни)\\b").containsMatchIn(p) }
        val en = pairs.count { (p, _) -> p.none { it in 'а'..'я' || it in "іїєґ" } }
        println("вольные: укр=$uk (${uk * 100 / pairs.size}%) англ=$en (${en * 100 / pairs.size}%) всего=${pairs.size}")
        assertTrue("укр $uk", uk * 100 >= pairs.size * 25)
        assertTrue("англ $en", en * 100 >= pairs.size * 4)
    }

    // ---- подключение к поиску ----

    @Test fun unionIsLoadedAndRetrievalIsFast() {
        val baseCount = File("src/main/assets/examples.tsv").readLines().count { it.isNotBlank() }
        val t0 = System.nanoTime()
        val size = NoaExamples.size
        val loadMs = (System.nanoTime() - t0) / 1_000_000
        assertEquals("библиотека — объединение двух файлов", baseCount + pairs.size, size)
        val queries = pairs.map { it.first }.shuffled(java.util.Random(7)).take(200)
        repeat(20) { NoaExamples.pick(queries[it], 3) } // прогрев JIT
        val t1 = System.nanoTime()
        for (q in queries) assertEquals(3, NoaExamples.pick(q, 3).size)
        val avg = (System.nanoTime() - t1) / 1_000_000.0 / queries.size
        println("библиотека $size примеров, загрузка ${loadMs} мс (если не была загружена раньше), поиск top-3: ${"%.2f".format(avg)} мс в среднем")
        assertTrue("поиск $avg мс", avg < 50.0)
        assertEquals(NoaExamples.pick(queries[0], 3).map { it.phrase }, NoaExamples.pick(queries[0], 3).map { it.phrase })
    }

    @Test fun holdOutFreePhrasesFindTheirClass() {
        val hold = File("src/test/resources/free_holdout.tsv").readLines().filter { it.isNotBlank() }.map { it.split('\t').let { t -> t[0] to t[1] } }
        assertTrue("контрольных фраз ${hold.size}", hold.size >= 100)
        val lib = NoaExamples.all.map { norm(it.phrase) }.toSet()
        val inLib = hold.filter { norm(it.first) in lib }
        assertTrue("контрольные фразы не должны быть в библиотеке: $inLib", inLib.isEmpty())
        val miss = ArrayList<String>()
        var top1 = 0
        for ((p, cls) in hold) {
            val top = NoaExamples.pick(p, 3)
            if (top.first().cls == cls) top1++
            if (top.none { it.cls == cls }) miss += "«$p» ждали $cls, нашли " + top.joinToString { "${it.cls} «${it.phrase}»" }
        }
        val pct = (hold.size - miss.size) * 100 / hold.size
        println("контроль: класс среди трёх ближайших у ${hold.size - miss.size} из ${hold.size} ($pct%), первым — ${top1 * 100 / hold.size}%")
        miss.take(30).forEach { println("HOLDOUT MISS $it") }
        assertTrue("$pct%: $miss", pct >= 85)
    }

    @Test fun libraryIsDisjointFromGoldenFree() {
        val f = File("src/main/assets/golden_free.tsv")
        assumeTrue("golden_free.tsv появится после слияния", f.isFile)
        val lib = NoaExamples.all.map { norm(it.phrase) }.toSet()
        val clash = f.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.substringBefore('\t') }.filter { norm(it) in lib }
        assertTrue("фразы из golden_free.tsv есть в библиотеке примеров: $clash", clash.isEmpty())
    }
}
