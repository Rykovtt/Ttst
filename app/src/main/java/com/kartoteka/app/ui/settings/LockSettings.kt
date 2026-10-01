package com.kartoteka.app.ui.settings

import com.kartoteka.app.i18n.t

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kartoteka.app.MainActivity
import com.kartoteka.app.data.PinLock
import com.kartoteka.app.security.IntruderCamera
import com.kartoteka.app.ui.app
import com.kartoteka.app.ui.components.OutlinedTextField

private enum class PinFlow { CREATE, CHANGE, DISABLE }

/** Блокировка: свой PIN-код приложения и вход по отпечатку. */
@Composable
fun LockSettings() {
    val app = app()
    val context = LocalContext.current
    val settings = app.settings
    val pin = app.pinLock
    val lock by settings.lockEnabled.collectAsState()
    val biometric by settings.biometric.collectAsState()
    var flow by remember { mutableStateOf<PinFlow?>(null) }
    var refresh by remember { mutableStateOf(0) }
    val hasPin = remember(refresh, lock) { pin.hasPin }
    val canBio = MainActivity.canUseBiometric(context)
    val intruderPhoto by settings.intruderPhoto.value.collectAsState()
    val shake by settings.shakeToClose.value.collectAsState()
    var showLog by remember { mutableStateOf(false) }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        settings.intruderPhoto.set(ok)
    }

    ToggleRow(Icons.Default.Lock, t("Блокировка приложения"), t("Свой PIN-код приложения (не телефона) и отпечаток"), lock) { v ->
        flow = when {
            v -> PinFlow.CREATE
            hasPin -> PinFlow.DISABLE
            else -> { settings.setLockEnabled(false); null }
        }
    }
    if (lock) {
        if (hasPin) {
            ActionRow(Icons.Default.Password, t("Сменить PIN-код"), t("Сейчас %1\$s цифры", pin.length)) { flow = PinFlow.CHANGE }
        } else {
            ActionRow(Icons.Default.Password, t("Задать свой PIN-код"), t("Сейчас вход по PIN-коду телефона")) { flow = PinFlow.CREATE }
        }
        if (canBio && hasPin) {
            ToggleRow(Icons.Default.Fingerprint, t("Вход по отпечатку"), t("PIN-код остаётся запасным способом"), biometric, settings::setBiometric)
        }
        if (hasPin) {
            ToggleRow(
                Icons.Default.PhotoCamera, t("Снимок при неверном PIN-коде"),
                t("Фронтальная камера тихо фотографирует того, кто подбирает PIN-код"), intruderPhoto,
            ) { v ->
                if (v && !IntruderCamera.hasPermission(context)) askCamera.launch(Manifest.permission.CAMERA)
                else settings.intruderPhoto.set(v)
            }
            val unseen = remember(showLog) { app.intruders.unseen }
            val total = remember(showLog) { app.intruders.attempts().size }
            ActionRow(
                Icons.Default.PersonSearch, t("Попытки входа"),
                when {
                    total == 0 -> t("Неудачных попыток не было")
                    unseen > 0 -> t("Новых: %1\$s, всего: %2\$s", unseen, total)
                    else -> t("Всего: %1\$s", total)
                },
            ) { showLog = true }
        }
    }
    ToggleRow(
        Icons.Default.Vibration, t("Встряхнуть — закрыть"),
        t("Резко встряхните телефон: архив закроется и пропадёт из недавних приложений"), shake, settings.shakeToClose::set,
    )
    if (showLog) IntruderLogDialog(onDismiss = { showLog = false })

    when (flow) {
        PinFlow.CREATE -> NewPinDialog(
            onDismiss = { flow = null },
            onDone = { newPin ->
                pin.set(newPin)
                settings.setLockEnabled(true)
                if (canBio) settings.setBiometric(true)
                flow = null; refresh++
            },
        )
        PinFlow.CHANGE -> VerifyPinDialog(pin, t("Текущий PIN-код"), onDismiss = { flow = null }) { flow = PinFlow.CREATE }
        PinFlow.DISABLE -> VerifyPinDialog(pin, t("Выключить блокировку"), onDismiss = { flow = null }) {
            pin.clear()
            settings.setLockEnabled(false)
            settings.setBiometric(false)
            flow = null; refresh++
        }
        null -> Unit
    }
}

/** Новый PIN: ввести и повторить. */
@Composable
private fun NewPinDialog(onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var first by remember { mutableStateOf<String?>(null) }
    var value by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val valid = value.length in PinLock.LENGTHS
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (first == null) t("Новый PIN-код") else t("Повторите PIN-код")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (first == null) t("От 4 до 6 цифр. Его не восстановить — запомните или включите вход по отпечатку.")
                    else t("Введите тот же PIN-код ещё раз"),
                    style = MaterialTheme.typography.bodyMedium,
                )
                PinField(value) { value = it; error = null }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                val f = first
                when {
                    f == null -> { first = value; value = "" }
                    f == value -> onDone(value)
                    else -> { error = t("PIN-коды не совпадают"); first = null; value = "" }
                }
            }) { Text(if (first == null) t("Далее") else t("Готово")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Отмена")) } },
    )
}

@Composable
private fun VerifyPinDialog(pin: PinLock, title: String, onDismiss: () -> Unit, onOk: () -> Unit) {
    var value by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(t("Введите текущий PIN-код"), style = MaterialTheme.typography.bodyMedium)
                PinField(value) { value = it; error = null }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(enabled = value.length in PinLock.LENGTHS, onClick = {
                if (pin.verify(value)) onOk()
                else {
                    val wait = pin.waitMillis()
                    error = if (wait > 0) t("Слишком много попыток. Подождите %1\$s с", (wait + 999) / 1000) else t("Неверный PIN-код")
                    value = ""
                }
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Отмена")) } },
    )
}

@Composable
private fun PinField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter(Char::isDigit).take(PinLock.LENGTHS.last)) },
        label = { Text(t("PIN-код")) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
    )
}
