package com.kartoteka.app.ui.person

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kartoteka.app.KartotekaApp
import com.kartoteka.app.data.ContactItem
import com.kartoteka.app.data.ContactType
import com.kartoteka.app.data.DetailField
import com.kartoteka.app.data.DetailTemplates
import com.kartoteka.app.data.Group
import com.kartoteka.app.data.Person
import com.kartoteka.app.ui.app
import com.kartoteka.app.ui.components.Avatar
import com.kartoteka.app.ui.components.ClosenessStars
import com.kartoteka.app.ui.components.ColorDot
import com.kartoteka.app.ui.components.PhotoSourceMenu
import com.kartoteka.app.ui.components.SectionCard
import com.kartoteka.app.ui.components.iconFor
import com.kartoteka.app.ui.components.rememberPhotoPicker
import com.kartoteka.app.ui.theme.AccentPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PersonEditViewModel(private val app: KartotekaApp, val id: Long) : ViewModel() {
    private val repo = app.repository
    var loaded by mutableStateOf(id == 0L)
    var person by mutableStateOf(Person())
    val contacts = mutableStateListOf<ContactItem>()
    val details = mutableStateListOf<DetailField>()
    val groupIds = mutableStateListOf<Long>()
    val allGroups = mutableStateListOf<Group>()
    var saving by mutableStateOf(false)
    var dirty by mutableStateOf(false)
    private val newFiles = mutableListOf<String>()

    // Дата рождения редактируется текстом
    var bdDay by mutableStateOf("")
    var bdMonth by mutableStateOf("")
    var bdYear by mutableStateOf("")

    init {
        viewModelScope.launch {
            allGroups.addAll(repo.getGroups())
            if (id != 0L) {
                repo.getPerson(id)?.let { pf ->
                    person = pf.person
                    contacts.addAll(pf.contacts)
                    details.addAll(pf.details.sortedBy { it.position })
                    groupIds.addAll(pf.groups.map { it.id })
                    bdDay = pf.person.birthDay?.toString().orEmpty()
                    bdMonth = pf.person.birthMonth?.toString().orEmpty()
                    bdYear = pf.person.birthYear?.toString().orEmpty()
                }
                loaded = true
            } else {
                contacts.add(ContactItem(type = ContactType.PHONE.name))
            }
        }
    }

    fun update(block: Person.() -> Person) { person = person.block(); dirty = true }

    fun setAvatar(uri: Uri) = viewModelScope.launch {
        val path = withContext(Dispatchers.IO) { repo.photos.import(uri) } ?: return@launch
        newFiles += path
        update { copy(avatarPath = path) }
    }

    fun addGroup(name: String) = viewModelScope.launch {
        val g = Group(name = name.trim(), color = AccentPalette[allGroups.size % AccentPalette.size])
        val gid = repo.saveGroup(g)
        allGroups.add(g.copy(id = gid))
        groupIds.add(gid)
        dirty = true
    }

    val birthdayError: String?
        get() {
            val d = bdDay.toIntOrNull(); val m = bdMonth.toIntOrNull(); val y = bdYear.toIntOrNull()
            if (bdDay.isBlank() && bdMonth.isBlank() && bdYear.isBlank()) return null
            if (d == null || d !in 1..31) return "День 1–31"
            if (m == null || m !in 1..12) return "Месяц 1–12"
            if (bdYear.isNotBlank() && (y == null || y !in 1900..2100)) return "Год, например 1990"
            return null
        }

    fun save(done: (Long) -> Unit) {
        if (saving || birthdayError != null) return
        saving = true
        viewModelScope.launch {
            val p = person.copy(
                birthDay = bdDay.toIntOrNull(),
                birthMonth = bdMonth.toIntOrNull(),
                birthYear = bdYear.toIntOrNull(),
                firstName = person.firstName.trim(),
                lastName = person.lastName.trim(),
                middleName = person.middleName.trim(),
            )
            val savedId = repo.savePerson(p, contacts.toList(), details.toList(), groupIds.toList())
            // Новый аватар кладём и в галерею, чтобы фото не потерялось.
            val avatar = p.avatarPath
            if (avatar != null && avatar in newFiles) {
                repo.addPhoto(savedId, avatar, makeAvatarIfEmpty = false)
            }
            newFiles.filter { it != avatar }.forEach { repo.photos.delete(it) }
            newFiles.clear()
            dirty = false
            done(savedId)
        }
    }

    fun discard() {
        newFiles.forEach { repo.photos.delete(it) }
        newFiles.clear()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PersonEditScreen(personId: Long, onBack: () -> Unit, onSaved: (Long) -> Unit) {
    val app = app()
    val vm: PersonEditViewModel = viewModel(key = "edit_$personId") { PersonEditViewModel(app, personId) }
    val p = vm.person
    var avatarMenu by remember { mutableStateOf(false) }
    var templatesSheet by remember { mutableStateOf(false) }
    var newGroupDialog by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    val picker = rememberPhotoPicker(multiple = false) { it.firstOrNull()?.let(vm::setAvatar) }

    val tryExit = { if (vm.dirty) confirmExit = true else { vm.discard(); onBack() } }
    BackHandler(onBack = tryExit)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (personId == 0L) "Новый человек" else "Редактирование") },
                navigationIcon = { IconButton(onClick = tryExit) { Icon(Icons.Default.Close, "Закрыть") } },
                actions = {
                    Button(
                        onClick = { vm.save(onSaved) },
                        enabled = !vm.saving && vm.birthdayError == null,
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Сохранить")
                    }
                },
            )
        },
    ) { padding ->
        if (!vm.loaded) return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(bottom = 48.dp),
        ) {
            item(key = "avatar") {
                Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                    Box {
                        Box(Modifier.clip(CircleShape).clickable { avatarMenu = true }) {
                            Avatar(p, 120.dp)
                        }
                        Box(
                            Modifier.align(Alignment.BottomEnd).size(40.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary).clickable { avatarMenu = true },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Default.AddAPhoto, "Фото", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp)) }
                        PhotoSourceMenu(avatarMenu, { avatarMenu = false }, picker)
                    }
                }
            }

            item(key = "names") {
                SectionCard("Кто это", Icons.Default.Badge) {
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Field("Имя", p.firstName) { v -> vm.update { copy(firstName = v) } }
                        Field("Фамилия", p.lastName) { v -> vm.update { copy(lastName = v) } }
                        Field("Отчество", p.middleName) { v -> vm.update { copy(middleName = v) } }
                        Field("Прозвище / как называю", p.nickname) { v -> vm.update { copy(nickname = v) } }
                        Text("Пол", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DetailTemplates.genders.forEach { g ->
                                FilterChip(selected = p.gender == g, onClick = { vm.update { copy(gender = if (gender == g) "" else g) } }, label = { Text(g) })
                            }
                        }
                        Text("Кем приходится", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DetailTemplates.relations.forEach { r ->
                                FilterChip(selected = p.relation == r, onClick = { vm.update { copy(relation = if (relation == r) "" else r) } }, label = { Text(r) })
                            }
                        }
                        Field("Или своё", if (p.relation in DetailTemplates.relations) "" else p.relation) { v -> vm.update { copy(relation = v) } }
                        Text("Близость", style = MaterialTheme.typography.labelLarge)
                        ClosenessStars(p.closeness, onChange = { v -> vm.update { copy(closeness = v) } }, size = 28.dp)
                    }
                }
            }

            item(key = "bd") {
                SectionCard("День рождения", Icons.Default.Cake) {
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumField("День", vm.bdDay, Modifier.weight(1f)) { vm.bdDay = it; vm.dirty = true }
                        NumField("Месяц", vm.bdMonth, Modifier.weight(1f)) { vm.bdMonth = it; vm.dirty = true }
                        NumField("Год", vm.bdYear, Modifier.weight(1.3f)) { vm.bdYear = it; vm.dirty = true }
                    }
                    vm.birthdayError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp))
                    }
                }
            }

            item(key = "contacts") {
                SectionCard("Контакты", Icons.Default.ContactPhone) {
                    vm.contacts.forEachIndexed { i, c -> ContactEditor(c, onChange = { vm.contacts[i] = it; vm.dirty = true }, onRemove = { vm.contacts.removeAt(i); vm.dirty = true }) }
                    AddContactButton { type -> vm.contacts.add(ContactItem(type = type.name)); vm.dirty = true }
                }
            }

            item(key = "work") {
                SectionCard("Работа и место", Icons.Default.Workspaces) {
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Field("Компания", p.company) { v -> vm.update { copy(company = v) } }
                        Field("Должность", p.position) { v -> vm.update { copy(position = v) } }
                        Field("Город", p.city) { v -> vm.update { copy(city = v) } }
                        Field("Адрес", p.address) { v -> vm.update { copy(address = v) } }
                        Field("Как и где познакомились", p.howMet, singleLine = false) { v -> vm.update { copy(howMet = v) } }
                    }
                }
            }

            item(key = "details") {
                SectionCard(
                    "Детали до мелочей", Icons.Default.Checklist,
                    action = { IconButton(onClick = { templatesSheet = true }) { Icon(Icons.Default.Add, "Добавить") } },
                ) {
                    if (vm.details.isEmpty()) {
                        Text(
                            "Хобби, дети, любимая еда, размеры, идеи подарков… Нажмите «+», чтобы выбрать из подсказок.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 18.dp),
                        )
                    }
                    vm.details.forEachIndexed { i, d ->
                        DetailEditor(d, onChange = { vm.details[i] = it; vm.dirty = true }, onRemove = { vm.details.removeAt(i); vm.dirty = true })
                    }
                    TextButton(onClick = { templatesSheet = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Добавить деталь")
                    }
                }
            }

            item(key = "groups") {
                SectionCard("Группы", Icons.Default.Workspaces) {
                    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        vm.allGroups.forEach { g ->
                            val sel = g.id in vm.groupIds
                            FilterChip(
                                selected = sel,
                                onClick = { if (sel) vm.groupIds.remove(g.id) else vm.groupIds.add(g.id); vm.dirty = true },
                                label = { Text(listOf(g.emoji, g.name).filter { it.isNotBlank() }.joinToString(" ")) },
                                leadingIcon = { ColorDot(g.color) },
                            )
                        }
                        SuggestionChip(onClick = { newGroupDialog = true }, label = { Text("+ Новая группа") })
                    }
                }
            }

            item(key = "notes") {
                SectionCard("Заметки", Icons.AutoMirrored.Filled.Notes) {
                    OutlinedTextField(
                        value = p.notes,
                        onValueChange = { v -> vm.update { copy(notes = v) } },
                        placeholder = { Text("Всё остальное, что важно помнить") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp).padding(horizontal = 16.dp),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                }
            }
        }
    }

    if (templatesSheet) {
        ModalBottomSheet(onDismissRequest = { templatesSheet = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            TemplatesSheet(
                onPick = { cat, name ->
                    vm.details.add(DetailField(category = cat, name = name)); vm.dirty = true
                    templatesSheet = false
                },
            )
        }
    }
    if (newGroupDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newGroupDialog = false },
            title = { Text("Новая группа") },
            text = { OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true) },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { vm.addGroup(name); newGroupDialog = false }) { Text("Создать") } },
            dismissButton = { TextButton(onClick = { newGroupDialog = false }) { Text("Отмена") } },
        )
    }
    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Не сохранять изменения?") },
            confirmButton = { TextButton(onClick = { confirmExit = false; vm.discard(); onBack() }) { Text("Выйти") } },
            dismissButton = { TextButton(onClick = { confirmExit = false; vm.save(onSaved) }) { Text("Сохранить") } },
        )
    }
}

@Composable
private fun Field(label: String, value: String, singleLine: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun NumField(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() }.take(4)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
private fun ContactEditor(c: ContactItem, onChange: (ContactItem) -> Unit, onRemove: () -> Unit) {
    var typeMenu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box {
            IconButton(onClick = { typeMenu = true }) { Icon(iconFor(c.contactType), c.contactType.title, tint = MaterialTheme.colorScheme.primary) }
            DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                ContactType.entries.forEach { t ->
                    DropdownMenuItem(text = { Text(t.title) }, leadingIcon = { Icon(iconFor(t), null) }, onClick = { onChange(c.copy(type = t.name)); typeMenu = false })
                }
            }
        }
        val keyboard = when (c.contactType) {
            ContactType.PHONE, ContactType.WHATSAPP, ContactType.VIBER -> KeyboardType.Phone
            ContactType.EMAIL -> KeyboardType.Email
            ContactType.WEBSITE -> KeyboardType.Uri
            else -> KeyboardType.Text
        }
        val hint = when (c.contactType) {
            ContactType.TELEGRAM -> "@username или номер"
            ContactType.INSTAGRAM, ContactType.VK -> "ник или ссылка"
            else -> c.contactType.title
        }
        OutlinedTextField(
            value = c.value,
            onValueChange = { onChange(c.copy(value = it)) },
            label = { Text(hint) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRemove) { Icon(Icons.Default.Close, "Удалить") }
    }
}

@Composable
private fun AddContactButton(onAdd: (ContactType) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box(Modifier.padding(horizontal = 8.dp)) {
        TextButton(onClick = { menu = true }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Добавить контакт") }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            ContactType.entries.forEach { t ->
                DropdownMenuItem(text = { Text(t.title) }, leadingIcon = { Icon(iconFor(t), null) }, onClick = { onAdd(t); menu = false })
            }
        }
    }
}

@Composable
private fun DetailEditor(d: DetailField, onChange: (DetailField) -> Unit, onRemove: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InputChip(selected = false, onClick = {}, label = { Text(d.category.ifBlank { "Разное" }) })
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = d.name,
                onValueChange = { onChange(d.copy(name = it)) },
                label = { Text("Что") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRemove) { Icon(Icons.Default.Close, "Удалить") }
        }
        OutlinedTextField(
            value = d.value,
            onValueChange = { onChange(d.copy(value = it)) },
            label = { Text("Значение") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        HorizontalDivider(Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TemplatesSheet(onPick: (String, String) -> Unit) {
    var customCat by remember { mutableStateOf("") }
    var customName by remember { mutableStateOf("") }
    LazyColumn(Modifier.navigationBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Text("Что добавить?", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        }
        items(DetailTemplates.categories) { (cat, names) ->
            Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                Text(cat, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    names.forEach { n -> SuggestionChip(onClick = { onPick(cat, n) }, label = { Text(n) }) }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Своё поле", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(customCat, { customCat = it }, label = { Text("Раздел (необязательно)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(customName, { customName = it }, label = { Text("Название поля") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(enabled = customName.isNotBlank(), onClick = { onPick(customCat.trim().ifBlank { "Разное" }, customName.trim()) }) { Text("Добавить") }
            }
        }
    }
}
