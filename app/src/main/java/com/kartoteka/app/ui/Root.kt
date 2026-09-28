package com.kartoteka.app.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.kartoteka.app.ui.home.HomeScreen
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
    const val PERSON = "person/{id}"
    const val EDIT = "edit/{id}"
    const val PHOTOS = "photos/{personId}/{index}"
    const val GROUP = "group/{id}"
    const val IMPORT = "import"
    const val CALENDAR = "calendar"
    const val MAP = "map"
    const val APPOINTMENT = "appointment/{id}?personId={personId}&date={date}"

    fun person(id: Long) = "person/$id"
    fun edit(id: Long = 0) = "edit/$id"
    fun photos(personId: Long, index: Int) = "photos/$personId/$index"
    fun group(id: Long) = "group/$id"
    fun appointment(id: Long = 0, personId: Long = 0, date: Long = 0) = "appointment/$id?personId=$personId&date=$date"
    fun broadcast(groupId: Long = 0, personIds: Collection<Long> = emptyList()) =
        "broadcast?groupId=$groupId&personIds=${personIds.joinToString(",")}"
}

private data class Tab(val route: String, val base: String, val title: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.PEOPLE, "people", "Люди", Icons.Default.People),
    Tab(Routes.CALENDAR, "calendar", "Календарь", Icons.Default.CalendarMonth),
    Tab(Routes.MAP, "map", "Карта", Icons.Default.Map),
    Tab(Routes.broadcast(), "broadcast", "Рассылка", Icons.AutoMirrored.Filled.Send),
    Tab(Routes.SETTINGS, "settings", "Настройки", Icons.Default.Settings),
)

@Composable
fun KartotekaRoot(
    openPersonId: Long?,
    onPersonOpened: () -> Unit,
    openAppointmentId: Long? = null,
    onAppointmentOpened: () -> Unit = {},
) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route.orEmpty()
    val showBar = tabs.any { route.startsWith(it.base) }

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
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest, tonalElevation = 0.dp) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route.startsWith(tab.base),
                            onClick = { nav.switchTab(tab.route) },
                            icon = { Icon(tab.icon, null) },
                            label = { Text(tab.title, maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onSurface,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                indicatorColor = Color.Transparent,
                                unselectedIconColor = MaterialTheme.colorScheme.outline,
                                unselectedTextColor = MaterialTheme.colorScheme.outline,
                            ),
                        )
                    }
                }
            }
        },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.PEOPLE,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
            enterTransition = { fadeIn() + slideInHorizontally { it / 12 } },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { fadeOut() + slideOutHorizontally { it / 12 } },
        ) {
            composable(Routes.PEOPLE) {
                HomeScreen(
                    onOpen = { nav.navigate(Routes.person(it)) },
                    onAdd = { nav.navigate(Routes.edit()) },
                    onImport = { nav.navigate(Routes.IMPORT) },
                    onGroups = { nav.navigate(Routes.GROUPS) },
                )
            }
            composable(Routes.GROUPS) {
                GroupsScreen(onOpen = { nav.navigate(Routes.group(it)) }, onBack = { nav.popBackStack() })
            }
            composable(Routes.CALENDAR) {
                CalendarScreen(
                    onNew = { nav.navigate(Routes.appointment(date = it.toEpochDay())) },
                    onOpen = { nav.navigate(Routes.appointment(it)) },
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
                ),
            ) { e ->
                AppointmentEditScreen(
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
                SettingsScreen(onImportContacts = { nav.navigate(Routes.IMPORT) })
            }
            composable(Routes.PERSON, arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                val id = e.arguments!!.getLong("id")
                PersonDetailScreen(
                    personId = id,
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate(Routes.edit(id)) },
                    onOpenPhoto = { nav.navigate(Routes.photos(id, it)) },
                    onMessage = { nav.navigate(Routes.broadcast(personIds = listOf(id))) },
                    onOpenPerson = { nav.navigate(Routes.person(it)) },
                    onNewAppointment = { nav.navigate(Routes.appointment(personId = id)) },
                    onOpenAppointment = { nav.navigate(Routes.appointment(it)) },
                )
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
            composable(Routes.IMPORT) {
                ImportContactsScreen(onBack = { nav.popBackStack() })
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

@Composable
fun LockScreen(title: String, onUnlock: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                Modifier.size(112.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(52.dp))
            }
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(
                "Подтвердите личность, чтобы продолжить",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onUnlock) {
                Icon(Icons.Default.Fingerprint, null)
                Spacer(Modifier.size(8.dp))
                Text("Разблокировать")
            }
        }
    }
}
