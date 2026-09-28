package com.kartoteka.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kartoteka.app.data.Country
import com.kartoteka.app.data.PhoneFormat

/**
 * Поле телефона со страной: пишете «093 074 38 29» — ниже видно, что сохранится «+380 93 074 38 29».
 */
@Composable
fun PhoneField(
    value: String,
    onChange: (String) -> Unit,
    country: Country,
    onCountryChange: (Country) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    var picker by remember { mutableStateOf(false) }
    val normalized = PhoneFormat.normalize(value, country)
    Column(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            leadingIcon = {
                TextButton(onClick = { picker = true }) {
                    Text("${country.flag} +${country.code}")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (value.isNotBlank() && normalized != value.trim()) {
            Text(
                "Сохранится: ${PhoneFormat.pretty(normalized, country)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 12.dp, top = 2.dp),
            )
        }
    }
    if (picker) CountryPickerDialog(onDismiss = { picker = false }, onPick = { onCountryChange(it); picker = false })
}

@Composable
fun CountryPickerDialog(onDismiss: () -> Unit, onPick: (Country) -> Unit) {
    var q by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Страна") },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("Поиск") }, singleLine = true)
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(PhoneFormat.countries.filter { q.isBlank() || it.name.contains(q.trim(), true) || it.code.startsWith(q.trim().removePrefix("+")) }) { c ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(c) }.padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(c.flag, style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.width(12.dp))
                            Text(c.name, modifier = Modifier.weight(1f))
                            Text("+${c.code}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
