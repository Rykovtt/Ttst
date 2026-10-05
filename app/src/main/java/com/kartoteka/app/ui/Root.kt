package com.kartoteka.app.ui

import com.kartoteka.app.i18n.t

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kartoteka.app.KartotekaApp
import com.kartoteka.app.ui.broadcast.BroadcastScreen
import com.kartoteka.app.ui.calendar.AppointmentEditScreen
import com.kartoteka.app.ui.calendar.CalendarScreen
import com.kartoteka.app.ui.map.MapScreen
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Map
import com.kartoteka.app.ui.groups.GroupDetailScreen
import com.kartoteka.app.ui.groups.GroupsScreen
import com.kartoteka.app.ui.screens.PeopleScreen
import com.kartoteka.app.ui.components.AddActionRadialMenu
import com.kartoteka.app.ui.components.CustomBottomNavigation
import com.kartoteka.app.ui.components.NavItem
import com.kartoteka.app.ui.components.QuickAction
import com.kartoteka.app.ui.components.QuickIcons
import com.kartoteka.app.ui.components.fabAnchorBelowBarTop
import com.kartoteka.app.ui.importer.ImportContactsScreen
import com.kartoteka.app.ui.person.PersonDetailScreen
import com.kartoteka.app.ui.person.PersonEditScreen
import com.kartoteka.app.ui.person.PhotoViewerScreen
import com.kartoteka.app.ui.settings.SettingsScreen

@Composable
fun app(): KartotekaApp = LocalContext.current.applicationContext as KartotekaApp

object Routes {
    const val PEOPLE = "people"
    const val GROUPS = "groups"
    const val BROADCAST = "broadcast?groupId={groupId}&personIds={personIds}"
    const val SETTINGS = "settings"
    const val PERSON = "person/{id}?note={note}"
    const val EDIT = "edit/{id}"
    const val PHOTOS = "photos/{personId}/{index}"
    const val GROUP = "group/{id}"
    const val IMPORT = "import"
    const val CALENDAR = "calendar"
    const val MAP = "map"
    const val SERVICES = "services"
    const val NOA = "noa"
    const val STATS = "stats"
    const val APPOINTMENT = "appointment/{id}?personId={personId}&date={date}&kind={kind}"

    fun person(id: Long) = "person/${id}"
    fun edit(id: Long = 0) = "edit/${id}"
    fun photos(personId: Long, index: Int) = "photos/${personId}/${index}"
    fun group(id: Long) = "group/${id}"
    fun appointment(id: Long = 0, personId: Long = 0, date: Long = 0, kind: String = "") = "appointment/${id}?personId=${personId}&date=${date}&kind=${kind}"
    fun personNote(id: Long) = "person/${id}?note=true"
    fun broadcast(groupId: Long = 0, personIds: Collection<Long> = emptyList()) =
        "broadcast?groupId=${groupId}&personIds=${personIds.joinToString(",")}"
}

private data class Tab(val route: String, val base: String, val title: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab(Routes.PEOPLE, "people", "Люди", Icons.Outlined.People, Icons.Default.People),
    Tab(Routes.CALENDAR, "calendar", "Календарь", Icons.Outlined.CalendarMonth, Icons.Default.CalendarMonth),
    Tab(Routes.MAP, "map", "Карта", Icons.Outlined.Map, Icons.Default.Map),
    Tab(Routes.broadcast(), "broadcast", "Рассылка", Icons.AutoMirrored.Outlined.Send, Icons.AutoMirrored.Filled.Send),
    Tab(Routes.SETTINGS, "settings", "Настройки", Icons.Outlined.Settings, Icons.Default.Settings),
)

/** Разделы, где видна нижняя навигация (карта и статистика открываются с «Людей»). */
private val barRoutes = listOf("people", "calendar", "broadcast", "settings", "map", "stats")

@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
fun KartotekaRoot(
    openPersonId: Long?,
    onPersonOpened: () -> Unit,
    openAppointmentId: Long? = null,
    onAppointmentOpened: () -> Unit = {},
    openNoa: Boolean = false,
    onNoaOpened: () -> Unit = {},
) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route.orEmpty()
    val showBar = barRoutes.any { route.startsWith(it) }
    var quick by remember { mutableStateOf(false) }
    var notePicker by remember { mutableStateOf(false) }
    LaunchedEffect(route) { quick = false }
    val app = app()
    val assistantOn by app.settings.assistant.value.collectAsState()
    // Точка на «Календаре», если сегодня есть записи.
    val todayAppts by remember {
        val d = java.time.LocalDate.now()
        app.repository.observeAppointments(
            com.kartoteka.app.data.AppointmentLogic.millis(d.atStartOfDay()),
            com.kartoteka.app.data.AppointmentLogic.millis(d.plusDays(1).atStartOfDay()),
        )
    }.collectAsState(initial = emptyList())
    val hasToday = todayAppts.any { it.appointment.appointmentStatus == com.kartoteka.app.data.AppointmentStatus.PLANNED }

    LaunchedEffect(openNoa) {
        if (openNoa) { nav.navigate(Routes.NOA) { launchSingleTop = true }; onNoaOpened() }
    }
    LaunchedEffect(openPersonId) {
        if (openPersonId != null) {
            nav.navigate(Routes.person(openPersonId))
            onPersonOpened()
        }
    }

    LaunchedEffect(openAppointmentId) {
        if (openAppointmentId != null) {
            nav.navigate(Routes.appointment(openAppointmentId))
            onAppointmentOpened()
        }
    }

    Scaffold(
        bottomBar = {
            if (showBar) {
                val items = tabs.map { NavItem(it.base, it.title, it.icon, it.selectedIcon, badge = it.base == "calendar" && hasToday) }
                CustomBottomNavigation(
                    items = items,
                    selected = tabs.firstOrNull { route.startsWith(it.base) }?.base,
                    menuOpen = quick,
                    onSelect = { item -> quick = false; nav.switchTab(tabs.first { it.base == item.key }.route) },
                    onFab = { quick = !quick },
                )
            }
        },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
    ) { padding ->
        Box(Modifier.padding(bottom = padding.calculateBottomPadding())) {
        androidx.compose.animation.SharedTransitionLayout {
        androidx.compose.runtime.CompositionLocalProvider(com.kartoteka.app.ui.components.LocalSharedScope provides this) {
        NavHost(
            navController = nav,
            startDestination = Routes.PEOPLE,
            modifier = Modifier.fillMaxSize(),
            // Стандартные переходы 280 мс: новый экран мягко выезжает, старый чуть уходит в глубину.
            enterTransition = {
                fadeIn(androidx.compose.animation.core.tween(com.kartoteka.app.ui.theme.Motion.STANDARD, easing = com.kartoteka.app.ui.theme.Motion.Ease)) +
                    slideInHorizontally(androidx.compose.animation.core.tween(com.kartoteka.app.ui.theme.Motion.STANDARD, easing = com.kartoteka.app.ui.theme.Motion.Ease)) { it / 10 }
            },
            exitTransition = {
                fadeOut(androidx.compose.animation.core.tween(com.kartoteka.app.ui.theme.Motion.MICRO)) +
                    androidx.compose.animation.scaleOut(androidx.compose.animation.core.tween(com.kartoteka.app.ui.theme.Motion.STANDARD), targetScale = 0.97f)
            },
            popEnterTransition = {
                fadeIn(androidx.compose.animation.core.tween(com.kartoteka.app.ui.theme.Motion.STANDARD)) +
                    androidx.compose.animation.scaleIn(androidx.compose.animation.core.tween(com.kartoteka.app.ui.theme.Motion.STANDARD, easing = com.kartoteka.app.ui.theme.Motion.Ease), initialScale = 0.97f)
            },
            popExitTransition = {
                fadeOut(androidx.compose.animation.core.tween(com.kartoteka.app.ui.theme.Motion.MICRO)) +
                    slideOutHorizontally(androidx.compose.animation.core.tween(com.kartoteka.app.ui.theme.Motion.STANDARD, easing = com.kartoteka.app.ui.theme.Motion.Ease)) { it / 10 }
            },
        ) {
            composable(Routes.PEOPLE) {
                androidx.compose.runtime.CompositionLocalProvider(com.kartoteka.app.ui.components.LocalNavScope provides this) {
                PeopleScreen(
                    onOpen = { nav.navigate(Routes.person(it)) },
                    onAdd = { nav.navigate(Routes.edit()) },
                    onImport = { nav.navigate(Routes.IMPORT) },
                    onGroups = { nav.navigate(Routes.GROUPS) },
                    onNoa = { nav.navigate(Routes.NOA) },
                    onMap = { nav.switchTab(Routes.MAP) },
                    onCalendar = { nav.switchTab(Routes.CALENDAR) },
                    onNewAppointment = { nav.navigate(Routes.appointment(personId = it)) },
                    onStats = { nav.navigate(Routes.STATS) },
                    onEdit = { nav.navigate(Routes.edit(it)) },
                    onOpenPhoto = { pid, i -> nav.navigate(Routes.photos(pid, i)) },
                    onSettings = { nav.switchTab(Routes.SETTINGS) },
                )
                }
            }
            composable(Routes.GROUPS) {
                GroupsScreen(onOpen = { nav.navigate(Routes.group(it)) }, onBack = { nav.popBackStack() })
            }
            composable(Routes.CALENDAR) {
                CalendarScreen(
                    onNew = { nav.navigate(Routes.appointment(date = it.toEpochDay())) },
                    onOpen = { nav.navigate(Routes.appointment(it)) },
                    onOpenPerson = { nav.navigate(Routes.person(it)) },
                )
            }
            composable(Routes.MAP) {
                MapScreen(onOpenPerson = { nav.navigate(Routes.person(it)) })
            }
            composable(
                Routes.APPOINTMENT,
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("personId") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("date") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("kind") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { e ->
                AppointmentEditScreen(
                    kind = e.arguments?.getString("kind").orEmpty(),
                    id = e.arguments!!.getLong("id"),
                    personId = e.arguments!!.getLong("personId"),
                    dateEpoch = e.arguments!!.getLong("date"),
                    onBack = { nav.popBackStack() },
                    onOpenPerson = { nav.navigate(Routes.person(it)) },
                )
            }
            composable(
                Routes.BROADCAST,
                arguments = listOf(
                    navArgument("groupId") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("personIds") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { e ->
                BroadcastScreen(
                    initialGroupId = e.arguments?.getLong("groupId") ?: 0L,
                    initialPersonIds = e.arguments?.getString("personIds").orEmpty()
                        .split(",").mapNotNull { it.toLongOrNull() },
                    onBack = if (nav.previousBackStackEntry != null && nav.previousBackStackEntry?.destination?.route != Routes.PEOPLE) {
                        { nav.popBackStack() }
                    } else null,
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onImportContacts = { nav.navigate(Routes.IMPORT) }, onServices = { nav.navigate(Routes.SERVICES) }, onNoa = { nav.navigate(Routes.NOA) })
            }
            composable(Routes.PERSON, arguments = listOf(
                navArgument("id") { type = NavType.LongType },
                navArgument("note") { type = NavType.BoolType; defaultValue = false },
            )) { e ->
                val id = e.arguments!!.getLong("id")
                androidx.compose.runtime.CompositionLocalProvider(com.kartoteka.app.ui.components.LocalNavScope provides this) {
                PersonDetailScreen(
                    openNote = e.arguments?.getBoolean("note") ?: false,
                    personId = id,
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate(Routes.edit(id)) },
                    onOpenPhoto = { nav.navigate(Routes.photos(id, it)) },
                    onOpenPerson = { nav.navigate(Routes.person(it)) },
                    onNewAppointment = { nav.navigate(Routes.appointment(personId = id)) },
                    onOpenAppointment = { nav.navigate(Routes.appointment(it)) },
                )
                }
            }
            composable(Routes.EDIT, arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                val id = e.arguments!!.getLong("id")
                PersonEditScreen(
                    personId = id,
                    onBack = { nav.popBackStack() },
                    onSaved = { savedId ->
                        if (id == 0L) {
                            nav.popBackStack()
                            nav.navigate(Routes.person(savedId))
                        } else nav.popBackStack()
                    },
                )
            }
            composable(
                Routes.PHOTOS,
                arguments = listOf(
                    navArgument("personId") { type = NavType.LongType },
                    navArgument("index") { type = NavType.IntType },
                ),
            ) { e ->
                PhotoViewerScreen(
                    personId = e.arguments!!.getLong("personId"),
                    startIndex = e.arguments!!.getInt("index"),
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Routes.GROUP, arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                val id = e.arguments!!.getLong("id")
                GroupDetailScreen(
                    groupId = id,
                    onBack = { nav.popBackStack() },
                    onOpenPerson = { nav.navigate(Routes.person(it)) },
                    onBroadcast = { nav.navigate(Routes.broadcast(groupId = id)) },
                )
            }
            composable(Routes.SERVICES) {
                com.kartoteka.app.ui.services.ServicesScreen(onBack = { nav.popBackStack() })
            }
            composable(Routes.NOA) {
                com.kartoteka.app.assistant.NoaScreen(
                    onBack = { nav.popBackStack() },
                    onOpenPerson = { nav.navigate(Routes.person(it)) },
                    onOpenAppointment = { nav.navigate(Routes.appointment(it)) },
                    onNavigate = { section ->
                        nav.popBackStack()
                        when (section) {
                            com.kartoteka.app.assistant.NoaIntent.Section.PEOPLE -> nav.navigate(Routes.PEOPLE)
                            com.kartoteka.app.assistant.NoaIntent.Section.CALENDAR -> nav.navigate(Routes.CALENDAR)
                            com.kartoteka.app.assistant.NoaIntent.Section.MAP -> nav.navigate(Routes.MAP)
                            com.kartoteka.app.assistant.NoaIntent.Section.BROADCAST -> nav.navigate(Routes.broadcast())
                            com.kartoteka.app.assistant.NoaIntent.Section.SETTINGS -> nav.navigate(Routes.SETTINGS)
                            com.kartoteka.app.assistant.NoaIntent.Section.SERVICES -> nav.navigate(Routes.SERVICES)
                        }
                    },
                )
            }
            composable(Routes.IMPORT) {
                ImportContactsScreen(onBack = { nav.popBackStack() })
            }
            composable(Routes.STATS) {
                com.kartoteka.app.ui.stats.StatsScreen(onBack = { nav.popBackStack() }, onOpenPerson = { nav.navigate(Routes.person(it)) })
            }
        }
        }
        }
        AddActionRadialMenu(
            open = quick,
            anchorBelow = fabAnchorBelowBarTop(),
            actions = listOf(
                QuickAction(t("Добавить человека"), QuickIcons.person) { nav.navigate(Routes.edit()) },
                QuickAction(t("Создать запись"), QuickIcons.appointment) {
                    nav.navigate(Routes.appointment(date = java.time.LocalDate.now().toEpochDay()))
                },
                QuickAction(t("Добавить событие"), QuickIcons.event) { notePicker = true },
                QuickAction(t("Создать рассылку"), QuickIcons.broadcast) { nav.switchTab(Routes.broadcast()) },
            ),
            onDismiss = { quick = false },
        )
        if (notePicker) {
            val everyone by app.repository.observeAll().collectAsState(initial = emptyList())
            com.kartoteka.app.ui.calendar.PersonPickerDialog(everyone, onDismiss = { notePicker = false }) { pf ->
                notePicker = false
                nav.navigate(Routes.personNote(pf.person.id))
            }
        }
        }
    }
}

private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Экран блокировки. С собственным PIN-кодом — точки и цифровая клавиатура (+ отпечаток),
 * без него (старая блокировка) — кнопка системного подтверждения.
 */
@Composable
fun LockScreen(
    title: String,
    pinLength: Int?,
    biometric: Boolean,
    onBiometric: () -> Unit,
    onPin: (String) -> Boolean,
    waitMillis: () -> Long,
) {
    var entered by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var wait by remember { mutableStateOf(waitMillis()) }
    LaunchedEffect(wait > 0) {
        while (wait > 0) {
            kotlinx.coroutines.delay(500)
            wait = waitMillis()
        }
    }

    fun press(d: Char) {
        if (pinLength == null || wait > 0 || entered.length >= pinLength) return
        entered += d
        error = null
        if (entered.length == pinLength) {
            if (!onPin(entered)) {
                entered = ""
                wait = waitMillis()
                error = if (wait > 0) null else t("Неверный PIN-код")
            }
        }
    }

    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.size(84.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(38.dp))
            }
            Text(title, style = MaterialTheme.typography.headlineSmall)
            if (pinLength == null) {
                Text(t("Подтвердите, что это вы"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Button(onClick = onBiometric) {
                    Icon(Icons.Default.Fingerprint, null)
                    Spacer(Modifier.size(8.dp))
                    Text(t("Разблокировать"))
                }
                return@Column
            }
            Text(
                when {
                    wait > 0 -> t("Слишком много попыток. Подождите %1\$s с", (wait + 999) / 1000)
                    error != null -> error!!
                    else -> t("Введите PIN-код")
                },
                color = if (error != null || wait > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(vertical = 10.dp)) {
                repeat(pinLength) { i ->
                    Box(
                        Modifier.size(14.dp).clip(CircleShape).background(
                            if (i < entered.length) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    )
                }
            }
            val rows = listOf("123", "456", "789")
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    row.forEach { d -> PinKey(d.toString()) { press(d) } }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
                    if (biometric) {
                        IconButton(onClick = onBiometric, modifier = Modifier.size(64.dp)) {
                            Icon(Icons.Default.Fingerprint, t("Отпечаток"), modifier = Modifier.size(34.dp))
                        }
                    }
                }
                PinKey("0") { press('0') }
                Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
                    IconButton(onClick = { entered = entered.dropLast(1) }, modifier = Modifier.size(64.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Backspace, t("Стереть"))
                    }
                }
            }
        }
    }
}

@Composable
private fun PinKey(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(76.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.headlineSmall)
    }
}

/** После входа: «пока вас не было, кто-то пытался войти». */
@Composable
fun IntruderAlert(count: Int, onShow: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, null) },
        title = { Text(t("Кто-то пытался войти")) },
        text = { Text(t("С прошлого входа неверный PIN-код вводили %1\$s %2\$s.", count, com.kartoteka.app.data.ArchiveLogic.plural(count.toLong(), "раз", "раза", "раз"))) },
        confirmButton = { TextButton(onClick = onShow) { Text(t("Посмотреть")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Позже")) } },
    )
}
