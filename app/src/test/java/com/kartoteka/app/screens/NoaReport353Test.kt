package com.kartoteka.app.screens

import androidx.test.core.app.ApplicationProvider
import com.kartoteka.app.assistant.NoaBenchmark
import com.kartoteka.app.assistant.NoaInterpreter
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
/** Настоящие ответы Phi-4 из отчёта 3.5.3: что должно получиться после починки. */
class NoaReport353Test {
    private val now = LocalDateTime.of(2026, 10, 4, 10, 0)
    private val ip = NoaInterpreter(null)
    private val app get() = ApplicationProvider.getApplicationContext<TestApp>()
    @Before fun ru() { com.kartoteka.app.i18n.I18n.init(app, com.kartoteka.app.i18n.UiLang.RU) }
    private val book = listOf("Ілля Риков", "Олексій Олексієнко", "Анна Іванова", "Ольга Сидорова", "Дмитро Коваль")

    private val EXPECTED = mapOf(
        "открой инстаграм Ани" to "drop",
        "убери Олю из избранного" to "Favorite",
        "покажи контакт Ильи" to "Open",
        "маршрут до ближайшей заправки" to "ask",
        "перестрой маршрут на Хрещатик 10" to "drop",
        "открой Аню" to "Open",
        "занеси в карточку Ильи что он вегетарианец" to "AddNote",
        "перешли Маше заметки Ильи" to "drop",
        "расскажи мой день на послезавтра" to "Agenda",
        "давай послушаем что-нибудь из Ляписа" to "Play",
        "подсвети Дмитра" to "drop",
        "Ані краще на четвер замість середи" to "MoveAppointment"
    )

    @Test fun replay() {
        val cs = listOf(
            "открой инстаграм Ани" to """{"actions":[{"action":"ask","text":"Кого ви маєте на увазі 'Ани'?"}]}""",
            "убери Олю из избранного" to """{"actions":[{"action":"favorite","person":"Ольга","on":false}]}""",
            "покажи контакт Ильи" to """{"actions":[{"action":"find","query":"Илья Риков"}]}""",
            "маршрут до ближайшей заправки" to """{"actions":[],"ask":"Где ехать?"}""",
            "перестрой маршрут на Хрещатик 10" to """{"actions": []}""",
            "открой Аню" to """{"actions":[{"action":"open_person","person":"Анна"}]}""",
            "занеси в карточку Ильи что он вегетарианец" to """{"actions":[{"action":"add_note","person":"Ілля","text":"вегетарианец"}]}""",
            "перешли Маше заметки Ильи" to """{"actions":[{"action":"read_messages"}]}""",
            "расскажи мой день на послезавтра" to """{"actions":[{"action":"web_search","query":"my day on Sunday 2026-10-05"}]}""",
            "давай послушаем что-нибудь из Ляписа" to """{"actions":[{"action":"find","query":"ляпис"}]}""",
            "подсвети Дмитра" to """{"actions":[{"action":"flashlight"}]}""",
            "Ані краще на четвер замість середи" to """{"actions":[{"action":"find","query":"Ані краще на четвер замість середи"}]}""",
        )
        for ((p, raw) in cs) {
            val r = ip.fromJson(raw, p, now, book, false, "")
            val o = when { r == null -> "drop"; r.ask != null -> "ask"; r.intent != null -> NoaBenchmark.signature(r.intent); r.reply != null -> "chat"; else -> "drop" }
            org.junit.Assert.assertEquals(p, EXPECTED[p], o)
        }
    }
}
