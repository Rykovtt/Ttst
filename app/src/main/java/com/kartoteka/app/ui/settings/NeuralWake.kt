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
    val on by s.assistantNeuralWake.value.collectAsState()
    ToggleRow(
        Icons.Default.RecordVoiceOver, t("Экспериментальный слух (нейросеть)"),
        t("Пока хуже обычного: чаще срабатывает сама на песни и речь. Включайте только для проверки. Выключено — обычный слух."),
        on,
    ) { s.assistantNeuralWake.set(it); WakeService.restart(context) }
}
