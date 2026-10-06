package com.kartoteka.app.data

import com.kartoteka.app.assistant.NoaBenchmark
import com.kartoteka.app.assistant.NoaInterpreter
import com.kartoteka.app.assistant.NoaParser
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

/** Настоящие ответы Phi-4 из отчёта 3.7.2 (весь конвейер): что должно получиться после починки. */
class NoaReport372Test {
    private val now = LocalDateTime.of(2026, 10, 4, 10, 0)
    private val book = listOf("Ілля Риков", "Олег Петров", "Маша Кравець", "Оксана Мельник", "Дмитро Коваль", "Андрій Гончар", "Аня Іванова", "Мама")
    private val ip = NoaInterpreter(null)
    private fun sig(p: String, raw: String) = ip.fromJson(raw, p, now, book, false, "").let { r ->
        when { r == null -> "—"; r.ask != null -> "ask"; r.intent != null -> NoaBenchmark.signature(r.intent); else -> "chat" }
    }

    @Test fun modelAnswersFromReport() {
        val cases = listOf(
            Triple("can you ring Oleg for me", """{"actions":[{"action":"call","person":"Oлег"}]}""", "Call"),
            Triple("перешли Маше заметки Ильи", """{"actions":[{"action":"find","query":"Илья Риков заметки"}]}""", "—"),
            Triple("що мені пише Оксана", """{"actions":[{"action":"find","query":"Оксана"}]}""", "—"),
            Triple("відпиши Ільє що згоден", """{"actions":[{"action":"reply","person":"Ілья","text":"згоден"}]}""", "Reply"),
            Triple("впиши Олега на пятницу на двенадцать", """{"actions":[{"action":"find","query":"Олег"}]}""", "—"),
            Triple("пометочку Маше что в долгу", """{"actions":[{"action":"message","person":"Маша","text":"Пометочку, что в долгу"}]}""", "AddNote"),
            Triple("напомни какой номер у Ильи", """{"actions":[{"action":"remind","query":"phone number of Илья Риков"}]}""", "PersonInfo"),
            Triple("где-то был клиент Андрей поищи", """{"actions":[{"action":"web_search","query":"Андрей"}]}""", "Find"),
            Triple("запри телефон", """{"actions":[{"action":"close_app","app":"phone"}]}""", "Lock"),
            Triple("розкрий Аню", """{"actions":[{"action":"open_screen","section":"contacts"}]}""", "Open"),
            Triple("крутани Металлику", """{"actions":[{"action":"find","query":"Металлику"}]}""", "—"),
        )
        for ((p, raw, want) in cases) assertEquals(p, want, sig(p, raw))
    }

    @Test fun rulesFromReport() {
        assertEquals("AddNote", NoaBenchmark.signature(NoaParser.parse("помітка для Дмитра хоче приходити вранці", now)))
        assertEquals("Media", NoaBenchmark.signature(NoaParser.parse("поменяй на другую песню", now)))
    }
}
