package com.kartoteka.app.ui.person

import com.kartoteka.app.i18n.t

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import com.kartoteka.app.ui.components.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import com.kartoteka.app.ui.components.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kartoteka.app.data.DetailTemplates
import com.kartoteka.app.data.JournalEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun JournalDialog(onDismiss: () -> Unit, onSave: (JournalEntry) -> Unit) {
    var kind by remember { mutableStateOf(DetailTemplates.journalKinds.first()) }
    var text by remember { mutableStateOf("") }
    var date by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var pickDate by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Запись в хронику")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DetailTemplates.journalKinds.forEach { k ->
                        FilterChip(selected = k == kind, onClick = { kind = k }, label = { Text(t(k)) })
                    }
                }
                OutlinedButton(onClick = { pickDate = true }) {
                    Icon(Icons.Default.CalendarMonth, null)
                    Text("  " + SimpleDateFormat("d MMMM yyyy", com.kartoteka.app.i18n.I18n.locale).format(Date(date)))
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(t("Что произошло")) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onSave(JournalEntry(personId = 0, date = date, kind = kind, text = text.trim())) }) {
                Text(t("Сохранить"))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Отмена")) } },
    )

    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date)
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = { state.selectedDateMillis?.let { date = it }; pickDate = false }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(t("Отмена")) } },
        ) { DatePicker(state) }
    }
}
