package com.kartoteka.app.data

import com.kartoteka.app.assistant.NoaBenchmark
import com.kartoteka.app.assistant.NoaIntent
import com.kartoteka.app.assistant.NoaInterpreter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDateTime

/** Запасной разбор по библиотеке примеров и запрет лишних вопросов про названное имя. */
class NoaRescueTest {
    private val now = LocalDateTime.of(2026, 10, 4, 10, 0)
    private val book = listOf("Ілля Риков", "Олексій Олексієнко", "Анна Іванова", "Ольга Сидорова", "Дмитро Коваль", "Оксана Мельник", "Олег Петров", "Мама")
    private val rows = File("src/main/assets/golden_free.tsv").readLines().mapNotNull { l -> l.split('\t').let { if (it.size >= 2) it[0].trim() to it[1].trim() else null } }
    private val ip = NoaInterpreter(null)

    /** Без подсказки ответа (сама фраза из библиотеки исключена): уверенно неверных — не больше ~15% от того, что запасной разбор берёт на себя. */
    @Test fun rescuePrecision() {
        var right = 0; var wrong = 0
        for ((p, exp) in rows) {
            val r = ip.rescue(p, now, book, false, "", exclude = p) ?: continue
            if (NoaBenchmark.signature(r.intent) == exp) right++ else wrong++
        }
        println("rescue: верно=$right неверно=$wrong из ${rows.size}")
        assertTrue("запасной разбор берёт на себя слишком мало: $right", right >= 30)
        assertTrue("слишком много неверных: $wrong при $right верных", wrong * 100 <= (right + wrong) * 15)
    }

    @Test fun rescueIsMarkedAndSimple() {
        // Сообщения и заметки (нужен свободный текст) запасной разбор не трогает.
        assertNull(ip.rescue("напиши Ане что опоздаю", now, book, false, "", exclude = "напиши Ане что опоздаю"))
    }

    /** Модель спросила «кого вы имеете в виду», хотя человек назван и он один в книжке, — вопрос отбрасывается. */
    @Test fun namedPersonIsNotAskedAbout() {
        val raw = """{"actions":[{"action":"ask","reply":"Кого ви маєте на увазі, коли говорите про 'Олега'?"}]}"""
        val r = ip.fromJson(raw, "подзвони Олегу", now, book, false, "")
        assertTrue("ask=${r?.ask}", r?.ask == null)
        val raw2 = """{"actions":[],"ask":"Кого ви маєте на увазі?"}"""
        assertTrue(ip.fromJson(raw2, "подзвони Олегу", now, book, false, "")?.ask == null)
        // Имя не названо — вопрос законен.
        assertTrue(ip.fromJson(raw2, "подзвони", now, book, false, "")?.ask != null)
    }
}
