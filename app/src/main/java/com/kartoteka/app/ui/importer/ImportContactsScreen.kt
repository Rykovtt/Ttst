package com.kartoteka.app.ui.importer

import com.kartoteka.app.i18n.t

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.kartoteka.app.ui.components.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kartoteka.app.data.ArchiveLogic
import com.kartoteka.app.data.PhoneContact
import com.kartoteka.app.ui.app
import com.kartoteka.app.ui.components.EmptyState
import com.kartoteka.app.ui.components.FullScreenCenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportContactsScreen(onBack: () -> Unit) {
    val app = app()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
    }
    var contacts by remember { mutableStateOf<List<PhoneContact>?>(null) }
    var existingPhones by remember { mutableStateOf(emptySet<String>()) }
    val selected = remember { mutableStateListOf<Long>() }
    var query by remember { mutableStateOf("") }
    var importing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Int?>(null) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    LaunchedEffect(granted) {
        if (!granted) return@LaunchedEffect
        val (list, phones) = withContext(Dispatchers.IO) {
            val known = app.repository.getAll().flatMap { pf -> pf.contacts.map { ArchiveLogic.normalizePhone(it.value) } }.toSet()
            app.phoneContacts.load() to known
        }
        existingPhones = phones
        contacts = list
        // По умолчанию выбираем тех, кого ещё нет в картотеке.
        selected.addAll(list.filter { c -> c.phones.none { ArchiveLogic.normalizePhone(it) in phones } }.map { it.id })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t("Импорт контактов")) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Назад")) } },
                actions = {
                    val list = contacts
                    if (list != null) {
                        TextButton(onClick = {
                            if (selected.size == list.size) selected.clear() else { selected.clear(); selected.addAll(list.map { it.id }) }
                        }) { Text(if (selected.size == list.size) t("Снять все") else t("Выбрать все")) }
                    }
                },
            )
        },
        bottomBar = {
            if (contacts != null && result == null) {
                Surface(tonalElevation = 3.dp) {
                    Button(
                        enabled = selected.isNotEmpty() && !importing,
                        onClick = {
                            importing = true
                            scope.launch {
                                val chosen = contacts.orEmpty().filter { it.id in selected }
                                result = withContext(Dispatchers.IO) { app.phoneContacts.import(chosen, app.repository, app.settings.defaultCountry) }
                                importing = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    ) { Text(if (importing) t("Импортируем…") else t("Импортировать (%1\$s)", selected.size)) }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when {
                !granted -> EmptyState(
                    Icons.Default.Contacts, t("Доступ к контактам"),
                    t("Нужно разрешение, чтобы прочитать телефонную книгу. Данные никуда не отправляются."),
                ) { Button(onClick = { permission.launch(Manifest.permission.READ_CONTACTS) }) { Text(t("Разрешить")) } }

                result != null -> EmptyState(Icons.Default.Contacts, t("Готово!"), t("Добавлено людей: %1\$s. Теперь дополните их карточки деталями и фото.", result)) {
                    Button(onClick = onBack) { Text(t("К картотеке")) }
                }

                contacts == null -> FullScreenCenter { CircularProgressIndicator() }

                else -> {
                    OutlinedTextField(
                        query, { query = it },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        placeholder = { Text(t("Поиск")) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    val q = query.trim().lowercase()
                    val shown = contacts.orEmpty().filter { q.isEmpty() || it.name.lowercase().contains(q) || it.phones.any { p -> p.contains(q) } }
                    LazyColumn {
                        items(shown, key = { it.id }) { c ->
                            val exists = c.phones.any { ArchiveLogic.normalizePhone(it) in existingPhones }
                            Row(
                                Modifier.fillMaxWidth().clickable { if (c.id in selected) selected.remove(c.id) else selected.add(c.id) }
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = c.id in selected, onCheckedChange = { if (it) selected.add(c.id) else selected.remove(c.id) })
                                Spacer(Modifier.width(4.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(c.name, style = MaterialTheme.typography.bodyLarge)
                                    val sub = listOfNotNull(c.phones.firstOrNull(), if (exists) t("уже в картотеке") else null).joinToString(" · ")
                                    if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = if (exists) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
