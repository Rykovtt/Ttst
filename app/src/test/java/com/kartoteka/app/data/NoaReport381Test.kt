package com.kartoteka.app.data

import com.kartoteka.app.assistant.NoaBenchmark
import com.kartoteka.app.assistant.NoaInterpreter
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

/** Настоящие ответы Gemma 4 E2B из отчёта 3.8.1 (в ```json-обёртке): что должно получиться после починки. */
class NoaReport381Test {
    private val now = LocalDateTime.of(2026, 10, 4, 10, 0)
    private val book = listOf("Ілля Риков", "Олег Петров", "Олексій Олексієнко", "Маша Кравець", "Оксана Мельник", "Дмитро Коваль", "Максим Бойко", "Аня Іванова", "Мама")
    private val ip = NoaInterpreter(null)
    private fun sig(p: String, raw: String) = ip.fromJson(raw, p, now, book, false, "").let { r ->
        when { r == null -> "—"; r.ask != null -> "ask"; r.intent != null -> NoaBenchmark.signature(r.intent); else -> "chat" }
    }
    private fun j(a: String) = "```json {\"actions\":[$a]} ```"

    @Test fun gemmaAnswersFromReport() {
        val cases = listOf(
            Triple("мені треба в календар", """{"actions":[{"action":"open_screen","section":"agenda"}]}""", "OpenScreen"),
            Triple("шепни Дмитру в месенджері що запізнююсь", j("""{"action":"message","channel":"whatsapp","text":"що запізнююсь"}"""), "Message"),
            Triple("впиши Олега на пятницу на двенадцать", j("""{"action":"move_appointment","person":"Олег","date":"2026-10-09"}"""), "CreateAppointment"),
            Triple("Ілля на завтра на дві години", j("""{"action":"create_appointment","person":"Ілля Риков"},{"action":"timer","hours":2}"""), "CreateAppointment"),
            Triple("Оксана сьогодні не прийде знімай її", j("""{"action":"delete_appointment"}"""), "CancelAppointment"),
            Triple("розкрий Аню", j("""{"action":"person_info","person":"Аню"}"""), "Open"),
            Triple("хочу не забыть поздравить маму вечером", j("""{"action":"add_note","note":"поздравить маму вечером"}"""), "Remind"),
            Triple("крутани Металлику", j("""{"action":"web_search","query":"крутани Металлику"}"""), "Play"),
            Triple("покажи мені Оксану в базі", j("""{"action":"find","query":"Оксана"}"""), "Open"),
            Triple("розшукай мені Максима", j("""{"action":"find","query":"Максим"}"""), "Find"),
            Triple("jot down that Anya is allergic to nuts", j("""{"action":"add_note","person":"Anya","text":"allergic to nuts"}"""), "AddNote"),
            Triple("хочу змінити мелодію дзвінка де це", j("""{"action":"phone_settings","section":"sound_settings"}"""), "PhoneSettings"),
        )
        for ((p, raw, want) in cases) assertEquals(p, want, sig(p, raw))
    }
}
