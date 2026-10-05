package com.kartoteka.app.ui.person

import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import com.kartoteka.app.i18n.t

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import com.kartoteka.app.ui.components.AssistChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import com.kartoteka.app.ui.components.heroBackground
import com.kartoteka.app.ui.components.pressable
import com.kartoteka.app.ui.components.sharedPhoto
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.kartoteka.app.KartotekaApp
import com.kartoteka.app.data.ArchiveLogic
import com.kartoteka.app.data.JournalEntry
import com.kartoteka.app.data.PersonFull
import com.kartoteka.app.messaging.Messaging
import com.kartoteka.app.ui.app
import com.kartoteka.app.ui.components.Avatar
import com.kartoteka.app.ui.components.ClosenessStars
import com.kartoteka.app.ui.components.ColorDot
import com.kartoteka.app.ui.components.InfoRow
import com.kartoteka.app.ui.components.PhotoSourceMenu
import com.kartoteka.app.ui.components.SectionCard
import com.kartoteka.app.ui.components.accentFor
import com.kartoteka.app.ui.components.iconFor
import com.kartoteka.app.ui.components.initials
import com.kartoteka.app.ui.components.rememberPhotoPicker
import com.kartoteka.app.ui.components.rememberGalleryMover
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import com.kartoteka.app.data.AppointmentFull
import com.kartoteka.app.data.AppointmentLogic
import com.kartoteka.app.data.AppointmentStatus
import com.kartoteka.app.data.PhoneFormat
import com.kartoteka.app.data.RelationType
import com.kartoteka.app.data.RelationView
import com.kartoteka.app.data.Relations
import com.kartoteka.app.ui.map.MapMarker
import com.kartoteka.app.ui.map.OsmMap
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PersonDetailViewModel(private val app: KartotekaApp, val id: Long) : ViewModel() {
    private val repo = app.repository
    val person = repo.observePerson(id).map { it ?: DELETED }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val everyone = repo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val relations = combine(repo.observeRelations(), everyone) { rels, all ->
        Relations.viewFor(id, rels, all.associate { it.person.id to it.person })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val appointments = repo.observePersonAppointments(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addRelation(otherId: Long, type: RelationType) = viewModelScope.launch { repo.addRelation(id, otherId, type) }
    fun removeRelation(v: RelationView) = viewModelScope.launch { repo.deleteRelation(v.relation.id) }

    fun toggleFavorite() = viewModelScope.launch {
        person.value?.person?.let { repo.setFavorite(it.id, !it.favorite) }
    }

    fun delete(done: () -> Unit) = viewModelScope.launch { repo.deletePerson(id); done() }

    /** [onDone] получает исходные адреса фото, которые удалось сохранить в архив. */
    fun addPhotos(uris: List<android.net.Uri>, onDone: (List<android.net.Uri>) -> Unit = {}) = viewModelScope.launch {
        val saved = uris.filter { uri ->
            val path = withContext(Dispatchers.IO) { repo.photos.import(uri) } ?: return@filter false
            repo.addPhoto(id, path)
            true
        }
        onDone(saved)
    }

    fun addJournal(entry: JournalEntry) = viewModelScope.launch { repo.addJournal(entry.copy(personId = id)) }
    fun deleteJournal(entry: JournalEntry) = viewModelScope.launch { repo.deleteJournal(entry) }

    companion object {
        /** Маркер «человек удалён». */
        val DELETED = PersonFull(com.kartoteka.app.data.Person(id = -1), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    }
}

private fun dateFmt() = SimpleDateFormat("d MMMM yyyy", com.kartoteka.app.i18n.I18n.locale)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PersonDetailScreen(
    personId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenPhoto: (Int) -> Unit,
    onOpenPerson: (Long) -> Unit = {},
    onNewAppointment: () -> Unit = {},
    onOpenAppointment: (Long) -> Unit = {},
    openNote: Boolean = false,
) {
    val app = app()
    val vm: PersonDetailViewModel = viewModel(key = "person_${personId}") { PersonDetailViewModel(app, personId) }
    val data by vm.person.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 700 } }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var journalDialog by remember { mutableStateOf(openNote) }
    var photoMenu by remember { mutableStateOf(false) }
    var relationDialog by remember { mutableStateOf(false) }
    val relations by vm.relations.collectAsState()
    val appointments by vm.appointments.collectAsState()
    val everyone by vm.everyone.collectAsState()
    val picker = rememberPhotoPicker(multiple = true) { vm.addPhotos(it) }
    // «Перенести из галереи»: сначала сохраняем в архив, потом удаляем оригиналы.
    var deleteOriginals by remember { mutableStateOf<List<android.net.Uri>?>(null) }
    val mover = rememberGalleryMover(
        onPicked = { uris -> vm.addPhotos(uris) { saved -> if (saved.isNotEmpty()) deleteOriginals = saved } },
        onDeleted = { r ->
            val msg = if (r.kept == 0) t("Фото перенесены в архив. Оригиналы удалены с телефона")
            else t("Фото добавлены в архив, но %1\$s не удалось удалить с телефона — удалите их вручную", r.kept)
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
        },
    )
    androidx.compose.runtime.LaunchedEffect(deleteOriginals) {
        deleteOriginals?.let { mover.deleteOriginals(it); deleteOriginals = null }
    }

    val pf = data ?: return
    if (pf === PersonDetailViewModel.DELETED) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onBack() }
        return
    }
    val p = pf.person

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 32.dp + androidx.compose.foundation.layout.WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()), modifier = Modifier.fillMaxSize()) {
            item(key = "hero") {
                Column(Modifier.fillMaxWidth().clip(com.kartoteka.app.ui.theme.PeopleShapes.header()).background(com.kartoteka.app.ui.theme.RvColors.HeaderBottom)) {
                    Hero(pf, onOpenPhoto = { if (pf.photos.isNotEmpty()) onOpenPhoto(pf.photos.indexOfFirst { it.path == p.avatarPath }.coerceAtLeast(0)) })
                    QuickActions(pf, onNewAppointment)
                }
            }

            val bdText = ArchiveLogic.formatBirthday(p)
            if (bdText != null) {
                item(key = "bd") { BirthdayBlock(p, bdText) }
            }

            if (pf.contacts.isNotEmpty()) {
                item(key = "contacts") {
                    SectionCard(t("Контакты"), Icons.Default.ContactPhone) {
                        pf.contacts.forEach { c ->
                            InfoRow(
                                label = c.label.ifBlank { c.contactType.title },
                                value = if (PhoneFormat.isPhoneType(c.contactType)) PhoneFormat.pretty(c.value) else c.value,
                                icon = iconFor(c.contactType),
                                onClick = { Messaging.openLink(context, c) },
                                onLongClick = { Messaging.copy(context, c.value) },
                                trailing = {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
                                },
                            )
                        }
                    }
                }
            }

            item(key = "main") {
                val rows = buildList {
                    if (p.relation.isNotBlank()) add(Triple(Icons.Default.Handshake, t("Кем приходится"), t(p.relation)))
                    if (p.gender.isNotBlank()) add(Triple(Icons.Default.Person, t("Пол"), t(p.gender)))
                    if (p.company.isNotBlank() || p.position.isNotBlank())
                        add(Triple(Icons.Default.Business, t("Работа"), listOf(p.position, p.company).filter { it.isNotBlank() }.joinToString(", ")))
                    if (p.city.isNotBlank()) add(Triple(Icons.Default.LocationCity, t("Город"), p.city))
                    if (p.howMet.isNotBlank()) add(Triple(Icons.Default.Info, t("Как познакомились"), p.howMet))
                }
                SectionCard(t("Основное"), Icons.Default.Badge) {
                    rows.forEach { (icon, label, value) ->
                        InfoRow(label, value, icon, onLongClick = { Messaging.copy(context, value) })
                    }
                    if (pf.groups.isNotEmpty()) {
                        FlowRow(Modifier.padding(horizontal = 18.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pf.groups.forEach { g ->
                                AssistChip(
                                    onClick = {},
                                    label = { Text(listOf(g.emoji, g.name).filter { it.isNotBlank() }.joinToString(" ")) },
                                    leadingIcon = { ColorDot(g.color) },
                                )
                            }
                        }
                    }
                    val meta = buildList {
                        add(t("Добавлен(а) ") + dateFmt().format(Date(p.createdAt)))
                        p.lastContactAt?.let { add(t("Последний контакт ") + dateFmt().format(Date(it))) }
                    }
                    Text(
                        meta.joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                    )
                }
            }

            if (pf.places.isNotEmpty()) {
                item(key = "places") {
                    SectionCard(t("Адреса"), Icons.Default.Place) {
                        val markers = pf.places.filter { it.hasCoords }.map { MapMarker("${it.id}", it.lat!!, it.lng!!, p, it.placeKind.title) }
                        if (markers.isNotEmpty()) {
                            OsmMap(
                                markers = markers,
                                interactive = false,
                                modifier = Modifier.fillMaxWidth().height(170.dp).padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(18.dp)),
                            )
                        }
                        pf.places.forEach { pl ->
                            InfoRow(
                                label = pl.label.ifBlank { pl.placeKind.title },
                                value = pl.address.ifBlank { t("Точка на карте") },
                                icon = if (pl.placeKind == com.kartoteka.app.data.PlaceKind.WORK) Icons.Default.Business else Icons.Default.Home,
                                onClick = { Messaging.navigate(context, pl) },
                                onLongClick = { Messaging.copy(context, pl.address) },
                            )
                        }
                    }
                }
            }

            item(key = "relations") {
                RelationsSection(relations, onOpen = onOpenPerson, onAdd = { relationDialog = true }, onRemove = vm::removeRelation)
            }

            item(key = "appointments") {
                AppointmentsSection(appointments, onNew = onNewAppointment, onOpen = onOpenAppointment)
            }

            val byCategory = pf.details.sortedBy { it.position }.groupBy { it.category.ifBlank { t("Разное") } }
            byCategory.forEach { (cat, fields) ->
                item(key = "cat_${cat}") {
                    SectionCard(cat, Icons.Default.Checklist) {
                        fields.forEach { f ->
                            InfoRow(f.name.ifBlank { "—" }, f.value, onLongClick = { Messaging.copy(context, f.value) })
                        }
                    }
                }
            }

            item(key = "photos") {
                SectionCard(
                    t("Фото (%1\$s)", pf.photos.size), Icons.Default.PhotoLibrary,
                    action = {
                        Box {
                            IconButton(onClick = { photoMenu = true }) { Icon(Icons.Default.AddAPhoto, t("Добавить фото")) }
                            PhotoSourceMenu(photoMenu, { photoMenu = false }, picker, onMove = mover.pick)
                        }
                    },
                ) {
                    if (pf.photos.isEmpty()) {
                        Text(
                            t("Добавьте фотографии — их увидите только вы"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                        )
                    } else {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(pf.photos, key = { _, ph -> ph.id }) { i, ph ->
                                AsyncImage(
                                    model = File(ph.path),
                                    contentDescription = ph.caption,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(104.dp).clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { onOpenPhoto(i) },
                                )
                            }
                        }
                    }
                }
            }

            item(key = "voice") { VoiceNotesSection(p.id) }

            item(key = "journal") {
                SectionCard(
                    t("Хроника"), Icons.Default.History,
                    action = { IconButton(onClick = { journalDialog = true }) { Icon(Icons.Default.Add, t("Добавить запись")) } },
                ) {
                    if (pf.journal.isEmpty()) {
                        Text(
                            t("Встречи, звонки, важные события — всё, что хочется помнить"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                        )
                    }
                    val entries = pf.journal.sortedByDescending { it.date }
                    entries.forEachIndexed { i, e ->
                        JournalRow(e, first = i == 0, last = i == entries.lastIndex, onDelete = { vm.deleteJournal(e) })
                    }
                }
            }

            if (p.notes.isNotBlank()) {
                item(key = "notes") {
                    SectionCard(t("Заметки"), Icons.AutoMirrored.Filled.Notes) {
                        Text(p.notes, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp))
                    }
                }
            }
        }

        TopAppBar(
            title = { if (scrolled) Text(p.displayName, style = com.kartoteka.app.ui.theme.PeopleType.contactName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                Box(Modifier.padding(start = 8.dp)) { com.kartoteka.app.ui.components.HeroButton(Icons.AutoMirrored.Filled.ArrowBack, t("Назад"), onBack) }
            },
            actions = {
                Row(Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                com.kartoteka.app.ui.components.HeroButton(if (p.favorite) Icons.Default.Star else Icons.Default.StarBorder, t("Избранное"), { vm.toggleFavorite() }, active = p.favorite)
                com.kartoteka.app.ui.components.HeroButton(Icons.Default.Edit, t("Редактировать"), onEdit)
                Box {
                    com.kartoteka.app.ui.components.HeroButton(Icons.Default.MoreVert, t("Ещё"), { menu = true })
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(t("Удалить")) },
                            leadingIcon = { Icon(Icons.Default.Delete, null) },
                            onClick = { menu = false; confirmDelete = true },
                        )
                    }
                }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = if (scrolled) com.kartoteka.app.ui.theme.RvColors.HeaderBottom else Color.Transparent,
                titleContentColor = Color.White,
            ),
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(t("Удалить %1\$s?", p.displayName)) },
            text = { Text(t("Карточка, фото, голосовые заметки и хроника будут удалены без возможности восстановления.")) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(onBack) }) { Text(t("Удалить")) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(t("Отмена")) } },
        )
    }
    if (relationDialog) {
        val linked = relations.map { it.other.id }.toSet() + p.id
        AddRelationDialog(
            me = p,
            candidates = everyone.filter { it.person.id !in linked },
            onDismiss = { relationDialog = false },
            onSave = { other, type -> vm.addRelation(other, type); relationDialog = false },
        )
    }
    if (journalDialog) {
        JournalDialog(onDismiss = { journalDialog = false }, onSave = { vm.addJournal(it); journalDialog = false })
    }
}

@Composable
private fun overlayButtonColors(scrolled: Boolean) = IconButtonDefaults.filledTonalIconButtonColors(
    containerColor = if (scrolled) Color.Transparent else Color.Black.copy(alpha = 0.28f),
    contentColor = if (scrolled) MaterialTheme.colorScheme.onSurface else Color.White,
)

@Composable
private fun Hero(pf: PersonFull, onOpenPhoto: () -> Unit) {
    val p = pf.person
    val hero = com.kartoteka.app.ui.theme.RvColors.HeaderBottom
    Box(Modifier.fillMaxWidth().aspectRatio(0.92f)) {
        if (p.avatarPath != null) {
            AsyncImage(
                model = File(p.avatarPath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().sharedPhoto(p.id).clickable(onClick = onOpenPhoto),
            )
        } else {
            // Без фото — горы, как на главном, и круглый аватар с инициалами в светлой кромке.
            com.kartoteka.app.ui.components.MountainBackdrop(Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().padding(bottom = 70.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(132.dp).clip(CircleShape).border(2.dp, com.kartoteka.app.ui.theme.RvColors.AvatarBorder, CircleShape)) {
                    Avatar(p, 132.dp)
                }
            }
        }
        // Затемнение сверху (под кнопки) и плавный переход в тёмный блок снизу.
        Box(Modifier.fillMaxWidth().height(120.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent))))
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.45f), 0.6f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to hero.copy(alpha = 0.95f))))
        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = com.kartoteka.app.ui.theme.u(36), vertical = 6.dp)) {
            // Точка категории идёт сразу за последним словом имени, даже при переносе строки.
            val dot = pf.groups.firstOrNull()?.let { Color(it.color) } ?: com.kartoteka.app.ui.components.categoryColor(p.relation)
            Text(
                androidx.compose.ui.text.buildAnnotatedString {
                    append(p.fullName)
                    if (dot != Color.Transparent) {
                        withStyle(androidx.compose.ui.text.SpanStyle(color = dot, fontSize = 22.sp)) { append(" ●") }
                    }
                },
                style = com.kartoteka.app.ui.theme.PeopleType.title.copy(
                    fontSize = if (p.fullName.length > 22) 28.sp else 34.sp, lineHeight = if (p.fullName.length > 22) 32.sp else 38.sp,
                ),
                color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            val sub = listOfNotNull(
                p.nickname.takeIf { it.isNotBlank() }?.let { "«${it}»" },
                p.relation.takeIf { it.isNotBlank() }?.let { t(it) },
                p.company.ifBlank { p.position }.takeIf { it.isNotBlank() },
                p.city.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (sub.isNotBlank()) Text(sub, style = com.kartoteka.app.ui.theme.PeopleType.subtitle, color = com.kartoteka.app.ui.theme.RvColors.Subtitle, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            if (p.closeness > 0) {
                Spacer(Modifier.height(4.dp))
                ClosenessStars(p.closeness, size = 16.dp, tint = com.kartoteka.app.ui.theme.RvColors.StarActive)
            }
        }
    }
}

/** День рождения: дата слева, до него и возраст — крупной цифрой справа. */
@Composable
private fun BirthdayBlock(p: com.kartoteka.app.data.Person, bdText: String) {
    val days = ArchiveLogic.daysUntilBirthday(p)
    val turning = ArchiveLogic.turningAge(p)
    com.kartoteka.app.ui.components.Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Cake, null, tint = com.kartoteka.app.ui.theme.Rv.PeachDeep, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(t("День рождения"), style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(10.dp))
                Text(bdText, style = MaterialTheme.typography.titleLarge)
                if (days != null) {
                    Text(
                        if (days == 0L) "🎉 " + ArchiveLogic.daysString(days) else ArchiveLogic.daysString(days),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (days <= 7) com.kartoteka.app.ui.theme.Rv.PeachDeep else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (turning != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text("$turning", style = com.kartoteka.app.ui.theme.NumberStyle.copy(fontSize = 44.sp, lineHeight = 46.sp))
                    Text(ArchiveLogic.plural(turning.toLong(), "год", "года", "лет").let { t(it) }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun QuickActions(pf: PersonFull, onNewAppointment: () -> Unit) {
    val context = LocalContext.current
    val actions = buildList<Triple<ImageVector, String, () -> Unit>> {
        pf.phone?.let { add(Triple(Icons.Default.Call, t("Звонок")) { Messaging.dial(context, it) }) }
        pf.phone?.let { add(Triple(Icons.Default.Sms, "SMS") { Messaging.sms(context, listOf(it)) }) }
        pf.whatsapp?.let { add(Triple(Icons.AutoMirrored.Filled.Chat, "WhatsApp") { Messaging.whatsapp(context, it) }) }
        pf.telegram?.let { add(Triple(Icons.AutoMirrored.Filled.Send, "Telegram") { Messaging.telegram(context, it) }) }
        pf.email?.let { add(Triple(Icons.Default.Email, t("Почта")) { Messaging.email(context, listOf(it)) }) }
        add(Triple(Icons.Default.EventAvailable, t("Записать"), onNewAppointment))
    }
    // «Стеклянные» плитки на тёмном: полупрозрачная заливка и тонкая светлая кромка.
    @Composable
    fun Tile(icon: ImageVector, label: String, action: () -> Unit, modifier: Modifier) {
        Column(modifier.pressable(onClick = action), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(54.dp).clip(CircleShape).background(com.kartoteka.app.ui.theme.RvColors.ChipBg).border(1.dp, com.kartoteka.app.ui.theme.RvColors.ChipBorder, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, label, tint = com.kartoteka.app.ui.theme.RvColors.TextOnDark, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.height(7.dp))
            Text(label, style = com.kartoteka.app.ui.theme.PeopleType.quickAction, color = com.kartoteka.app.ui.theme.RvColors.ChipText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    if (actions.size <= 5) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 26.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            actions.forEach { (icon, label, action) -> Tile(icon, label, action, Modifier.weight(1f)) }
        }
    } else {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 26.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            actions.forEach { (icon, label, action) -> Tile(icon, label, action, Modifier.width(76.dp)) }
        }
    }
}

/** Событие хроники на вертикальной шкале: цвет узла зависит от типа. */
@Composable
private fun JournalRow(e: JournalEntry, first: Boolean, last: Boolean, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val node = journalColor(e.kind)
    val line = MaterialTheme.colorScheme.outlineVariant
    Row(
        Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)
            .clickable { expanded = !expanded }.padding(start = 18.dp, end = 8.dp),
    ) {
        // Шкала: линия и узел.
        Box(Modifier.width(18.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.width(2.dp).fillMaxHeight().padding(top = if (first) 14.dp else 0.dp, bottom = if (last) 0.dp else 0.dp)
                .background(if (last && first) Color.Transparent else line))
            Box(
                Modifier.padding(top = 10.dp).size(14.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(2.dp)
                    .clip(CircleShape).background(node)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t(e.kind), style = MaterialTheme.typography.labelLarge, color = node)
                Spacer(Modifier.width(8.dp))
                Text(dateFmt().format(Date(e.date)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(2.dp))
            Text(
                e.text, style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.animateContentSize(com.kartoteka.app.ui.theme.motion()),
            )
        }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.MoreVert, null, Modifier.size(18.dp)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(t("Удалить запись")) }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

private fun journalColor(kind: String): Color {
    val k = kind.lowercase()
    return when {
        k.contains("встреч") || k.contains("зустр") || k.contains("meet") -> com.kartoteka.app.ui.theme.Rv.Lavender
        k.contains("звон") || k.contains("дзв") || k.contains("call") -> com.kartoteka.app.ui.theme.Rv.Lime
        k.contains("подар") || k.contains("gift") -> com.kartoteka.app.ui.theme.Rv.PeachDeep
        k.contains("рассыл") || k.contains("розсил") || k.contains("сообщ") || k.contains("повідом") || k.contains("message") -> com.kartoteka.app.ui.theme.Rv.Cobalt
        else -> com.kartoteka.app.ui.theme.Rv.Coral
    }
}

@Composable
private fun AppointmentsSection(list: List<AppointmentFull>, onNew: () -> Unit, onOpen: (Long) -> Unit) {
    val now = System.currentTimeMillis()
    val upcoming = list.filter { it.appointment.end >= now && it.appointment.appointmentStatus == AppointmentStatus.PLANNED }.sortedBy { it.appointment.start }
    val past = list.filter { it !in upcoming }.take(3)
    SectionCard(
        t("Записи"), Icons.Default.Event,
        action = { IconButton(onClick = onNew) { Icon(Icons.Default.Add, t("Записать")) } },
    ) {
        if (list.isEmpty()) {
            Text(
                t("Запишите человека на встречу, приём или звонок — с напоминаниями ему и вам"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
            )
        }
        (upcoming + past).forEach { af ->
            val a = af.appointment
            val dt = AppointmentLogic.zoned(a.start)
            val status = when {
                a.appointmentStatus == AppointmentStatus.CANCELLED -> t(" · отменено")
                a.appointmentStatus == AppointmentStatus.DONE -> t(" · состоялось")
                a.end < now -> t(" · прошло")
                else -> ""
            }
            InfoRow(
                label = "${AppointmentLogic.dateText(dt)}, ${AppointmentLogic.weekday(dt.toLocalDate())} · ${AppointmentLogic.timeText(dt)}${status}",
                value = a.title.ifBlank { t("Запись") },
                icon = Icons.Default.Event,
                onClick = { onOpen(a.id) },
            )
        }
    }
}
