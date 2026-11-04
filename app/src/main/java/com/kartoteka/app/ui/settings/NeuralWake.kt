package com.kartoteka.app.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.kartoteka.app.assistant.WakeService
import com.kartoteka.app.i18n.t
import com.kartoteka.app.ui.app

/** Новый слух (нейросеть на «Санта»). Показывается, только когда имя ассистента — «Санта». */
@Composable
fun NeuralWakeRows(name: String) {
    if (name.trim().lowercase() != "санта") return
    val context = LocalContext.current
    val s = app().settings
    val confirm by s.assistantNeuralConfirm.value.collectAsState()
    ToggleRow(
        Icons.Default.RecordVoiceOver, t("Проверять имя нейросетью"),
        t("При музыке имя засчитывается, только если его услышали и обычный слух, и нейросеть на телефоне. Песни реже глушат звук и переключают треки сами. Без интернета."),
        confirm,
    ) { s.assistantNeuralConfirm.set(it); WakeService.restart(context) }
    val on by s.assistantNeuralWake.value.collectAsState()
    ToggleRow(
        Icons.Default.RecordVoiceOver, t("Экспериментальный слух (нейросеть)"),
        t("Пока хуже обычного: чаще срабатывает сама на песни и речь. Включайте только для проверки. Выключено — обычный слух."),
        on,
    ) { s.assistantNeuralWake.set(it); WakeService.restart(context) }
}
