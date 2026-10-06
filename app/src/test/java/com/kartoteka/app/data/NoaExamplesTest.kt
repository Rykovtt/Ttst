package com.kartoteka.app.data

import com.kartoteka.app.assistant.Clarify
import com.kartoteka.app.assistant.NoaBenchmark
import com.kartoteka.app.assistant.NoaContext
import com.kartoteka.app.assistant.NoaExamples
import com.kartoteka.app.assistant.NoaIntent
import com.kartoteka.app.assistant.NoaIntentJson
import com.kartoteka.app.assistant.NoaInterpreter
import com.kartoteka.app.assistant.NoaMedia
import com.kartoteka.app.assistant.NoaParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Random

/** Библиотека примеров (assets/examples.tsv): состав, круг «правила → JSON → интерпретатор», поиск похожих и запись команд в JSON. */
class NoaExamplesTest {
    private val now = LocalDateTime.of(2026, 10, 4, 10, 0)
    private val ip = NoaInterpreter(null)
    private val lines = File("src/main/assets/examples.tsv").readLines().filter { it.isNotBlank() }
    private val pairs = lines.map { it.split('\t').let { t -> t[0] to t[1] } }

    @Test fun libraryShapeAndBalance() {
        assertTrue("примеров ${pairs.size}", pairs.size in 500..900)
        assertEquals("фразы не повторяются", pairs.size, pairs.map { it.first.lowercase() }.toSet().size)
        for ((p, j) in pairs) {
            assertTrue(p, !p.contains('\t') && !p.contains('\n'))
            assertTrue(j, j.startsWith("{\"actions\":[") && j.endsWith("}") && !j.contains("\n"))
            // для действий reply не пишем: исполнители озвучивают сами
            if (j.contains("\"action\"")) assertFalse(j, j.contains("\"reply\":"))
        }
        val byClass = NoaExamples.all.groupBy { it.cls }
        println("классы: " + byClass.entries.sortedByDescending { it.value.size }.joinToString { "${it.key}=${it.value.size}" })
        assertTrue("классов ${byClass.size}", byClass.size >= 30)
        // библиотека — объединение examples.tsv и examples_free.tsv
        assertTrue("ни один класс не больше 12% библиотеки", byClass.values.all { it.size <= NoaExamples.all.size * 12 / 100 })
        assertTrue(byClass.getValue("seq").size >= 30)
        val uk = pairs.count { (p, _) -> p.any { it in "іїєґ" } }
        val en = pairs.count { (p, _) -> p.none { it in 'а'..'я' } }
        println("укр=$uk англ=$en всего=${pairs.size}")
        assertTrue(uk >= 80 && en >= 25)
    }

    @Test fun everyExampleRoundTripsToTheRuleParserResult() {
        var ok = 0; var total = 0
        val bad = ArrayList<String>()
        for ((p, j) in pairs) {
            val back = ip.fromJson(j, p, now)
            if (!j.contains("\"action\"")) { // разговор и уточнения
                assertNotNull(p, back)
                assertTrue(p, back!!.reply != null || back.ask != null)
                continue
            }
            total++
            val want = NoaBenchmark.signature(NoaParser.parse(p, now))
            val got = NoaBenchmark.signature(back?.intent)
            if (want == got) ok++ else bad += "«$p» rules=$want json=$got"
        }
        println("круг: $ok из $total; ${bad.take(5)}")
        assertTrue("круг $ok/$total: $bad", ok * 100 >= total * 97)
    }

    @Test fun jsonSerializerRoundTrip() {
        val d = LocalDate.of(2026, 10, 5)
        val intents = listOf(
            NoaIntent.CreateAppointment("Аня", LocalDateTime.of(2026, 10, 5, 15, 0), true, "тату", false), NoaIntent.CancelAppointment("Аня", d, false), NoaIntent.CancelAppointment("Аня", null, true),
            NoaIntent.MoveAppointment("Аня", null, true, false), NoaIntent.Call("Аня"), NoaIntent.Message("Аня", NoaIntent.Channel.TELEGRAM, "привет \"друг\""), NoaIntent.Message("Аня", NoaIntent.Channel.WHATSAPP, null, aboutAppointment = true),
            NoaIntent.Reply("Олег", "буду в семь"), NoaIntent.ReadMessages("Аня", true), NoaIntent.AddNote("Аня", "любит кофе"), NoaIntent.Open("Аня"), NoaIntent.Find("клиенты"),
            NoaIntent.OpenContact("Аня", com.kartoteka.app.data.ContactType.INSTAGRAM), NoaIntent.Route("Аня", com.kartoteka.app.data.PlaceKind.WORK, "waze"), NoaIntent.Route("", null, null, "Киевская 5"),
            NoaIntent.Agenda(d), NoaIntent.PersonInfo("Аня", NoaIntent.Topic.PHONE, "Аня"), NoaIntent.Favorite("Аня", false), NoaIntent.Select("Аня"),
            NoaIntent.ShareData("Аня", NoaIntent.Data.PHONE, "clipboard"), NoaIntent.ShareData("Аня", NoaIntent.Data.CARD, "app:telegram"), NoaIntent.LaunchApp("spotify"), NoaIntent.CloseApp("waze"),
            NoaIntent.WebSearch("рецепт борща"), NoaIntent.Alarm(7, 30), NoaIntent.Timer(5400), NoaIntent.Timer(45), NoaIntent.Remind("позвонить в банк", null, false), NoaIntent.Flashlight(false),
            NoaIntent.PhoneSettings("wifi"), NoaIntent.Play("queen", null, false, artist = true, shuffle = true), NoaIntent.Media(NoaMedia.Control.QUIETER), NoaIntent.OpenScreen(NoaIntent.Section.CALENDAR),
            NoaIntent.GoHome, NoaIntent.Lock, NoaIntent.Backup,
            NoaIntent.Sequence(listOf(NoaIntent.Message("Аня", NoaIntent.Channel.SMS, "опаздываю"), NoaIntent.GoHome)),
        )
        for (i in intents) {
            val json = NoaIntentJson.toJson(i)!!
            assertTrue(json, json.startsWith("{\"actions\":[{\"action\":\"") && !json.contains("\n"))
            // Человек должен быть назван во фразе — иначе защита от выдумок модели его отбросит.
            val who = NoaParser.personOf(if (i is NoaIntent.Sequence) i.steps.first() else i).ifBlank { "x" }
            val phrase = when (i) {
                is NoaIntent.Reply -> "ответь $who"
                is NoaIntent.AddNote -> "добавь заметку $who ${i.text}"
                else -> who
            }
            val back = ip.fromJson(json, phrase, now)?.intent
            // та же команда; даты и время берутся из фразы, поэтому сравниваем тип и значимые поля
            assertEquals(json, NoaBenchmark.signature(i), NoaBenchmark.signature(back))
            if (i is NoaIntent.Message || i is NoaIntent.Play || i is NoaIntent.Timer || i is NoaIntent.Alarm || i is NoaIntent.Media || i is NoaIntent.ShareData ||
                i is NoaIntent.Route || i is NoaIntent.Favorite || i is NoaIntent.PersonInfo || i is NoaIntent.OpenContact || i is NoaIntent.Sequence)
                assertEquals(json, i, back)
        }
        // пустое и значения по умолчанию не пишем
        assertEquals("""{"actions":[{"action":"call","person":"Аня"}]}""", NoaIntentJson.toJson(NoaIntent.Call("аня")))
        assertEquals("""{"actions":[{"action":"flashlight"}]}""", NoaIntentJson.toJson(NoaIntent.Flashlight(true)))
        assertEquals("""{"actions":[{"action":"go_home"}]}""", NoaIntentJson.toJson(NoaIntent.GoHome))
        assertEquals("""{"actions":[{"action":"message","person":"Аня","text":"привет \"друг\""}]}""", NoaIntentJson.toJson(NoaIntent.Message("Аня", NoaIntent.Channel.WHATSAPP, "привет \"друг\"")))
        // то, чего нет среди действий интерпретатора, в JSON не превращается
        assertNull(NoaIntentJson.toJson(NoaIntent.Tool(NoaIntent.ToolKind.TIME)))
        assertNull(NoaIntentJson.toJson(NoaIntent.Repeat))
        assertNull(NoaIntentJson.toJson(NoaIntent.Sequence(listOf(NoaIntent.Call("Аня"), NoaIntent.Dismiss))))
    }

    @Test fun answersWithoutReplyAreUnderstood() {
        // action-ответ без reply: команда есть, реплики нет — исполнитель озвучит сам
        val r = ip.fromJson("""{"actions":[{"action":"call","person":"Аня"}]}""", "позвони Ане", now)!!
        assertEquals(NoaIntent.Call("Аня"), r.intent); assertNull(r.reply); assertNull(r.ask)
        val s = ip.fromJson("""{"actions":[{"action":"message","person":"Аня","channel":"telegram","text":"опоздаю"},{"action":"go_home"}]}""", "напиши Ане в телеграм что опоздаю и сверни", now)!!
        assertTrue(s.intent is NoaIntent.Sequence); assertNull(s.reply)
        assertEquals(NoaIntent.GoHome, ip.fromJson("""{"actions":[{"action":"go_home"}]}""", "закрой приложение", now)!!.intent)
        // только reply (разговор) и только ask (вопрос) тоже работают
        assertEquals("Привет!", ip.fromJson("""{"actions":[],"reply":"Привет!"}""", "привет", now)!!.reply)
        assertNotNull(ip.fromJson("""{"actions":[],"ask":"Кому позвонить?"}""", "позвони", now)!!.ask)
    }

    // ---- поиск похожих ----

    private val swapNames = listOf("Виктория", "Зоя", "Борис", "Лидия", "Родион", "Милана")
    private val fillers = listOf("Ноа слушай ", "ну ", "пожалуйста ", "Санта, ")

    private fun noise(s: String, r: Random): String {
        val ws = s.split(' ').toMutableList()
        repeat(2) {
            val i = r.nextInt(ws.size); val w = ws[i]
            if (w.length >= 5) when (r.nextInt(3)) {
                0 -> { val k = 1 + r.nextInt(w.length - 2); ws[i] = w.removeRange(k, k + 1) }
                1 -> { val k = 1 + r.nextInt(w.length - 2); ws[i] = w.substring(0, k) + w[k + 1] + w[k] + w.substring(k + 2) }
                else -> { val k = 1 + r.nextInt(w.length - 2); ws[i] = w.substring(0, k) + w[k] + w.substring(k) }
            }
        }
        return ws.joinToString(" ").lowercase()
    }

    private fun variants(p: String, j: String, r: Random): List<String> {
        val ws = p.split(' ')
        val out = ArrayList<String>()
        // имя другим человеком
        val person = Regex("\"person\":\"([^\"]+)\"").find(j)?.groupValues?.get(1)?.lowercase()?.take(3)
        val sw = swapNames[r.nextInt(swapNames.size)]
        out += ws.joinToString(" ") { w -> if (person != null && w.lowercase().startsWith(person)) sw else w }
        // убрали одно слово (не первое)
        if (ws.size >= 4) out += ws.filterIndexed { i, _ -> i != 1 + r.nextInt(ws.size - 1) }.joinToString(" ")
        // шум распознавания
        out += noise(p, r)
        // обращение / вежливость
        out += fillers[r.nextInt(fillers.size)] + p.replaceFirstChar { it.lowercase() } + if (r.nextBoolean()) " пожалуйста" else ""
        return out.filter { it != p && it.isNotBlank() }
    }

    @Test fun retrievalTop1ClassOnParaphrases() {
        val r = Random(42)
        var total = 0; var hit = 0; var strict = 0; var loTotal = 0; var loHit = 0
        val misses = ArrayList<String>(); val loMiss = ArrayList<String>()
        val sample = NoaExamples.all.shuffled(r).take(70)
        for (e in sample) for (v in variants(e.phrase, e.json, r)) {
            total++
            val top = NoaExamples.pick(v, 3)
            if (top.first().cls == e.cls) hit++ else misses += "«$v» (из «${e.phrase}» ${e.cls}) → ${top.first().cls} «${top.first().phrase}»"
            // сам пример исключаем: ищет ли библиотека класс по другим формулировкам
            val others = NoaExamples.pick(v, 8).filter { it.phrase != e.phrase }
            loTotal++
            if (others.isNotEmpty() && others.first().cls == e.cls) loHit++ else loMiss += "«$v» (из «${e.phrase}» ${e.cls}) → ${others.firstOrNull()?.cls} «${others.firstOrNull()?.phrase}»"
            if (top.any { it.cls == e.cls }) strict++
        }
        println("top-1 класс: $hit из $total (${hit * 100 / total}%), класс среди трёх: ${strict * 100 / total}%, без самого примера: ${loHit * 100 / loTotal}%")
        misses.take(12).forEach { println("MISS $it") }
        loMiss.take(25).forEach { println("LOMISS $it") }
        assertTrue("вариантов $total", total >= 150)
        assertTrue("top-1 ${hit * 100 / total}%", hit * 100 >= total * 90)
    }

    @Test fun retrievalIsDeterministicDiverseAndIgnoresNames() {
        val a = NoaExamples.pick("запиши Анну на завтра в 15:00", 3)
        assertEquals(3, a.size)
        assertEquals(a.map { it.phrase }, NoaExamples.pick("запиши Анну на завтра в 15:00", 3).map { it.phrase })
        assertEquals("create_appointment", a[0].cls)
        // тот же запрос с другим именем (и именем из книжки) даёт тот же верх
        assertEquals(a[0].cls, NoaExamples.pick("запиши Викторию на завтра в 15:00", 3).first().cls)
        assertEquals(a[0].cls, NoaExamples.pick("запиши Зою на завтра в 15:00", 3, listOf("Зоя Кошкина")).first().cls)
        // путаемые команды: каждая попадает в свой класс
        assertEquals("open_screen", NoaExamples.pick("открой календарь", 3)[0].cls)
        assertEquals("launch_app", NoaExamples.pick("открой калькулятор", 3)[0].cls)
        assertEquals("close_app", NoaExamples.pick("закрой waze", 3)[0].cls)
        assertTrue(NoaExamples.pick("закрой приложение", 3).any { it.cls == "go_home" })
        assertEquals("person_info", NoaExamples.pick("когда день рождения у Ани", 3)[0].cls)
        // три одного класса только если фраза явно этого класса; иначе — разные
        val mixed = NoaExamples.pick("закрой", 3)
        assertTrue(mixed.map { it.cls }.toSet().size >= 2)
        assertTrue(NoaExamples.pick("", 3).size <= 3)
    }

    // ---- промпт ----

    @Test fun promptHasThreeRetrievedExamplesInsteadOfStaticOnes() {
        val names = listOf("Анна Иванова (Аня)", "Илья Рыков", "Олег Петренко")
        for (phrase in listOf("запиши Анну на завтра в 15:00", "открой калькулятор", "закрой waze", "що в мене завтра")) {
            val p = ip.prompt(phrase, now, names, "", "", null)
            val ex = NoaExamples.pick(phrase, NoaInterpreter.EXAMPLES_K, names)
            assertEquals(3, ex.size)
            for (e in ex) assertTrue("${e.line} нет в промпте", p.contains(e.line))
            assertTrue(p.indexOf("Examples:") in 0 until p.indexOf("People:") && p.indexOf("People:") < p.indexOf("Today:") && p.indexOf("Today:") < p.indexOf("Command:"))
            assertFalse("статичные примеры убраны", p.contains("сколько будет 15% от 80") && p.contains("який у Тараса телефон"))
            assertTrue(p.endsWith("JSON:"))
            println("prompt «$phrase» = ${p.length} chars, static = ${NoaInterpreter.staticPrompt().length}")
            assertTrue(p.length <= NoaContext.TOTAL_CHARS + 1)
        }
        // уточняющий ответ («Ане»): примеры ищем по прежней команде, а не по одному слову
        val pending = Clarify("Кому написать?", null, emptyList(), 0, emptyList(), "напиши что опоздаю")
        val p = ip.prompt("Ане", now, names, "", "", pending)
        assertTrue(NoaExamples.pick("напиши что опоздаю Ане", 3, names).all { p.contains(it.line) })
    }

    @Test fun promptWithExamplesStaysInsideBudgetWithHugeInputs() {
        val bigNames = (1..300).map { "Имя$it Фамилия$it" }
        val bigData = (1..60).joinToString("\n") { "Строка данных номер $it с довольно длинным описанием события" }
        val hist = "User: " + "длинная реплика ".repeat(40) + "\nNoa: " + "ответ ".repeat(60)
        for (phrase in listOf("позвони маме", "очень длинная фраза пользователя ".repeat(40), "напиши Олегу в телеграм что буду через десять минут и сверни")) {
            val p = ip.prompt(phrase, now, bigNames, bigData, hist, null)
            assertTrue("${p.length}", p.length <= NoaContext.TOTAL_CHARS + 1)
            assertTrue(p.contains("Examples:") && p.contains("Command: \"") && p.endsWith("JSON:"))
        }
    }
}
