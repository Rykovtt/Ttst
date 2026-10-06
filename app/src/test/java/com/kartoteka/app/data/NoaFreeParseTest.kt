package com.kartoteka.app.data

import com.kartoteka.app.assistant.NoaBenchmark
import com.kartoteka.app.assistant.NoaParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDateTime

/**
 * Живая речь (assets/golden_free.tsv): правила не должны уверенно выполнять не то, что сказано.
 * Лучше Unknown (разберёт модель), чем неверное действие. Нечётные строки набора — отложенная половина:
 * по ней правила не подгонялись, она показывает честное улучшение.
 */
class NoaFreeParseTest {
    private val now = LocalDateTime.of(2026, 10, 4, 10, 0)
    private val rows = File("src/main/assets/golden_free.tsv").readLines().filter { it.isNotBlank() }.map { it.split('\t') }

    private fun sig(p: String) = NoaBenchmark.signature(runCatching { NoaParser.parse(p, now) }.getOrNull())
    private fun isNone(s: String) = s == "Unknown" || s == "—"

    private class Tally(var right: Int = 0, var wrong: Int = 0, var none: Int = 0) { val n get() = right + wrong + none }

    private fun tally(sel: (Int) -> Boolean): Tally {
        val t = Tally()
        for ((i, r) in rows.withIndex()) {
            if (!sel(i + 1)) continue
            val got = sig(r[0])
            when { got == r[1] -> t.right++; isNone(got) -> t.none++; else -> { t.wrong++; println("WRONG «${r[0]}» → $got (ждали ${r[1]})") } }
        }
        return t
    }

    @Test fun wholeSetFewWrongParses() {
        val t = tally { true }
        println("весь набор: верно=${t.right} неверно=${t.wrong} Unknown=${t.none} из ${t.n}")
        assertTrue("неверных ${t.wrong} из ${t.n} (допустимо ≤ 3%)", t.wrong * 100 <= t.n * 3)
        assertTrue("верных ${t.right} из ${t.n} (не меньше прежних 6%)", t.right * 100 >= t.n * 6)
    }

    @Test fun heldOutHalfFewWrongParses() {
        val t = tally { it % 2 == 1 } // нечётные строки — отложенная половина
        val dev = tally { it % 2 == 0 }
        println("отложенная (нечётные): верно=${t.right} неверно=${t.wrong} Unknown=${t.none} из ${t.n}; разработка (чётные): верно=${dev.right} неверно=${dev.wrong} Unknown=${dev.none} из ${dev.n}")
        assertTrue("неверных ${t.wrong} из ${t.n} (допустимо ≤ 5%)", t.wrong * 100 <= t.n * 5)
    }

    // ---- собственные фразы по общим принципам (не из golden_free.tsv) ----

    private val correct = listOf(
        // глагол «выключить» + приложение = закрыть приложение
        "вимкни вайбер" to "CloseApp", "выключи ватсап" to "CloseApp", "прибери інстаграм" to "CloseApp", "turn off telegram" to "CloseApp",
        // «поставь» + замок/блокировка = блокировка; «выключи экран» = блокировка
        "поставь замок" to "Lock", "постав блокування" to "Lock", "включи блокировку" to "Lock", "погаси экран" to "Lock", "вимкни екран" to "Lock",
        // только название приложения — запуск
        "відкрий ватсап" to "LaunchApp", "хочу в вайбер" to "LaunchApp", "покажи ютуб" to "LaunchApp",
        // сообщения и ответы разговорными глаголами
        "напиши в ответ Маше ок" to "Reply", "дай знать Олегу что опаздываю" to "Message", "дай знати Оксані що буду о шостій" to "Message",
        "скинь Ане в телеграм что задержусь" to "Message", "Олегу напиши що виїжджаю і заблокуй екран" to "Seq(Message,Lock)",
        // запись: убрать/снять/отказывается
        "убери Машу из расписания на пятницу" to "CancelAppointment", "сними запись Ильи на завтра" to "CancelAppointment",
        "Оксана отказывается от записи на четверг" to "CancelAppointment", "Оксана не придёт в пятницу сними запись" to "CancelAppointment",
        "поставь Аню в расписание на пятницу на три" to "CreateAppointment",
        // настройки телефона и разделы приложения
        "настройки звука открой" to "PhoneSettings", "відкрий налаштування сповіщень" to "PhoneSettings", "покажи настройки экрана" to "PhoneSettings",
        "відкрий вкладку сервісів" to "OpenScreen", "відкрий картку Максима" to "Open", "відкрий телеграм Олега" to "OpenContact",
        // прочее
        "через час напомни выключить чайник" to "Remind", "як у мене завтра по записах" to "Agenda", "пошукай в мережі курс долара" to "WebSearch",
    )

    // Конфликт улик / лишнее содержание / чужие данные в «тексте»: правила обязаны промолчать
    private val abstain = listOf(
        "скинь Дмитру в вайбер що буду пізніше", "напиши Ане в инстаграм привет", "сбрось Олегу в фейсбук что освободился",
        "найди Машу открой карточку и позвони ей", "знайди Олега відкрий картку і напиши йому",
        "отправь Олегу карточку Маши", "перешли Ане заметки Максима", "надішли Оксані адресу Максима",
        "напомни какой адрес у Оксаны", "нагадай який номер у Максима",
        "расскажи что у меня есть про Илью", "хто в мене там Оксана шукай",
        "про що мені Олег повідомляє", "хочу чтобы экран был темнее зайди куда нужно",
        "закрой телеграм и напиши Ане привет как дела у тебя вообще", "set an alarm for seven",
    )

    @Test fun ownCorrectPhrases() {
        val bad = correct.mapNotNull { (p, exp) -> sig(p).let { if (it == exp) null else "«$p» → $it (ждали $exp)" } }
        assertTrue("неверно:\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    @Test fun ownAbstainPhrases() {
        val bad = abstain.mapNotNull { p -> sig(p).let { if (isNone(it)) null else "«$p» → $it (ждали Unknown)" } }
        assertTrue("должны молчать:\n" + bad.joinToString("\n"), bad.isEmpty())
        assertTrue("мало своих фраз: ${correct.size + abstain.size}", correct.size + abstain.size >= 40)
    }

    @Test fun existingBehaviourStillParses() {
        // Короткие проверенные команды из прежних наборов остаются разобранными (охранный минимум)
        assertEquals("CloseApp", sig("закрой вайбер"))
        assertEquals("Message", sig("напиши Илье что опаздываю"))
        assertEquals("Lock", sig("заблокируй телефон"))
        assertEquals("Call", sig("позвони Ане"))
    }
}
