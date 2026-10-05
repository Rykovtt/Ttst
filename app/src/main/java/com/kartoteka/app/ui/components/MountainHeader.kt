package com.kartoteka.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Workspaces
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kartoteka.app.R
import com.kartoteka.app.data.AppointmentFull
import com.kartoteka.app.data.GroupWithCount
import com.kartoteka.app.data.PersonFull
import com.kartoteka.app.i18n.t
import com.kartoteka.app.ui.app
import com.kartoteka.app.ui.theme.AnimationTokens
import com.kartoteka.app.ui.theme.LocalReducedMotion
import com.kartoteka.app.ui.theme.PeopleDims
import com.kartoteka.app.ui.theme.PeopleShapes
import com.kartoteka.app.ui.theme.PeopleType
import com.kartoteka.app.ui.theme.RvColors
import com.kartoteka.app.ui.theme.u
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Фильтр списка людей. */
sealed interface PeopleFilter {
    data object All : PeopleFilter
    data object Favorites : PeopleFilter
    data class InGroup(val id: Long) : PeopleFilter
}

/** Данные шапки. */
data class HeaderData(
    val total: Int,
    val groups: List<GroupWithCount>,
    val today: List<AppointmentFull>,
    val birthdays: List<Pair<PersonFull, Long>>,
)

/**
 * Верхний блок «Люди» по ТЗ: фото гор (фокус 60/40), затемнения (слева 75→0 %, снизу #101012 90 %→0, общее 15 %),
 * скругления 42/46, отступы 36; логотип, аватар и фильтр; заголовок со счётчиком; поиск; категории; быстрые действия.
 */
@Composable
fun MountainHeader(
    data: HeaderData,
    query: String,
    onQuery: (String) -> Unit,
    filter: PeopleFilter,
    onFilter: (PeopleFilter) -> Unit,
    showCounts: Boolean,
    filtersOpen: Boolean,
    onOpenFilters: () -> Unit,
    intro: Boolean,
    scrollOffset: () -> Int,
    onAdd: () -> Unit,
    onToday: () -> Unit,
    onBirthdays: () -> Unit,
    onMap: () -> Unit,
    onNoa: () -> Unit,
    onGroups: () -> Unit,
    onStats: () -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val app = app()
    val reduced = LocalReducedMotion.current
    val title by app.settings.appTitle.value.collectAsState()
    val assistantOn by app.settings.assistant.value.collectAsState()
    val photoAlpha = remember { Animatable(if (intro) 0f else 1f) }
    LaunchedEffect(Unit) { if (intro) photoAlpha.animateTo(1f, tween(500, easing = AnimationTokens.Enter)) }
    val pad = u(PeopleDims.HeaderPad)
    val focus = BiasAlignment(0.2f, -0.2f)

    // Положение поиска внутри шапки — для размытой «подложки» под стеклом.
    var headerCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var headerSize by remember { mutableStateOf(IntSize.Zero) }
    var searchCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    Box(
        Modifier.fillMaxWidth().clip(PeopleShapes.header()).background(RvColors.HeaderBottom)
            .onGloballyPositioned { headerCoords = it; headerSize = it.size },
    ) {
        HeroImage(
            alignment = focus, key = "people",
            modifier = Modifier.matchParentSize().graphicsLayer {
                alpha = photoAlpha.value
                translationY = if (reduced) 0f else scrollOffset() * 0.25f
            },
        )
        Box(
            Modifier.matchParentSize().drawBehind {
                drawRect(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.75f), 0.6f to Color.Transparent))
                drawRect(Brush.verticalGradient(0.45f to Color.Transparent, 1f to RvColors.HeaderBottom.copy(alpha = 0.9f)))
                drawRect(Color.Black.copy(alpha = 0.15f))
            }
        )
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(bottom = u(34))) {
            // Логотип · аватар · фильтр.
            Row(Modifier.fillMaxWidth().padding(start = pad, end = pad - u(10), top = u(PeopleDims.ControlsTop)), verticalAlignment = Alignment.CenterVertically) {
                HeaderLogo(com.kartoteka.app.AppIcons.title(context, title), intro, Modifier.weight(1f))
                HeroBackgroundButton("people", Modifier.padding(end = 8.dp))
                OwnerAvatar(onGroups, onStats, onSettings)
            }
            // Заголовок и счётчик.
            Row(Modifier.padding(start = pad, end = pad, top = u(PeopleDims.TitleTop)), verticalAlignment = Alignment.CenterVertically) {
                Text(t("Люди"), style = PeopleType.title, color = Color.White)
                Spacer(Modifier.width(u(PeopleDims.CounterGap)))
                Box(
                    Modifier.clip(PeopleShapes.counter()).background(RvColors.CounterBg)
                        .padding(horizontal = u(PeopleDims.CounterPadH * 1.6f), vertical = u(PeopleDims.CounterPadV)),
                ) {
                    AnimatedContent(
                        data.total,
                        transitionSpec = {
                            val up = targetState > initialState
                            (slideInVertically(tween(220)) { if (up) it else -it } + fadeIn(tween(220))) togetherWith
                                (slideOutVertically(tween(220)) { if (up) -it else it } + fadeOut(tween(160)))
                        },
                        label = "counter",
                    ) { n -> Text("$n", style = PeopleType.counter, color = Color.White) }
                }
            }
            Text(
                t("Ваш круг. Клиенты, друзья, семья."), style = PeopleType.subtitle, color = RvColors.Subtitle,
                modifier = Modifier.padding(start = pad, end = pad, top = u(PeopleDims.SubtitleTop)),
            )
            // Стеклянный поиск с размытым фоном.
            GlassSearchBar(
                query = query, onQuery = onQuery,
                hint = t("Поиск: имя, город, заметка…"), searchDescription = t("Поиск"),
                filterDescription = t("Расширенные фильтры"), clearDescription = t("Очистить"),
                onFilters = onOpenFilters,
                modifier = Modifier.padding(start = pad, end = pad, top = u(PeopleDims.SearchTop)).onGloballyPositioned { searchCoords = it },
                backdrop = {
                    val hc = headerCoords; val sc = searchCoords
                    if (hc != null && sc != null && hc.isAttached && sc.isAttached) {
                        val pos = hc.localPositionOf(sc, Offset.Zero)
                        BlurredBackdrop(headerSize, pos, focus, { photoAlpha.value }, { if (reduced) 0f else scrollOffset() * 0.25f })
                    }
                },
            )
            // Категории.
            CategoryRow(data, filter, onFilter, showCounts)
            // Быстрые действия.
            val labelSize = rememberSharedTextSize(PeopleType.quickAction.fontSize.value)
            LazyRow(
                contentPadding = PaddingValues(horizontal = pad),
                horizontalArrangement = Arrangement.spacedBy(u(PeopleDims.QuickGap)),
                verticalAlignment = Alignment.Top,
                modifier = Modifier.padding(top = u(PeopleDims.QuickTop)),
            ) {
                item { AddContactTile(t("Добавить\nчеловека"), onAdd, labelSize) }
                item {
                    val p = data.today.mapNotNull { it.person }.firstOrNull()
                    PhotoQuickAction(t("Сегодня"), "${data.today.size}", RvColors.TodayRing, if (p != null) RvColors.Violet else null, onToday, labelSize) {
                        if (p != null) Avatar(p, u(PeopleDims.Circle)) else Icon(Icons.Outlined.CalendarMonth, null, tint = Color.White, modifier = Modifier.size(u(44)))
                    }
                }
                item {
                    val p = data.birthdays.firstOrNull()?.first?.person
                    PhotoQuickAction(t("Дни рождения"), "${data.birthdays.size}", RvColors.BirthdaysRing, if (p != null) RvColors.Green else null, onBirthdays, labelSize) {
                        if (p != null) Avatar(p, u(PeopleDims.Circle)) else Icon(Icons.Outlined.Redeem, null, tint = Color.White, modifier = Modifier.size(u(44)))
                    }
                }
                item {
                    PhotoQuickAction(t("Карта"), null, RvColors.MapRing, null, onMap, labelSize) {
                        Image(painterResource(R.drawable.hero_mountain), null, contentScale = ContentScale.Crop, alignment = focus, modifier = Modifier.fillMaxSize())
                    }
                }
                if (assistantOn) item { NoaQuickAction(t("Ассистент\nНоа"), onNoa, labelSize) }
            }
        }
    }
}

/** Размытая копия фото шапки ровно под стеклянным полем (backdrop blur 18 ед.). */
@Composable
private fun BoxScope.BlurredBackdrop(headerSize: IntSize, pos: Offset, focus: Alignment, alpha: () -> Float, parallax: () -> Float) {
    HeroImage(
        alignment = focus, key = "people",
        modifier = Modifier.matchParentSize()
            .layout { m, c ->
                val p = m.measure(Constraints.fixed(headerSize.width.coerceAtLeast(1), headerSize.height.coerceAtLeast(1)))
                layout(c.maxWidth, c.maxHeight) { p.place(-pos.x.toInt(), -pos.y.toInt()) }
            }
            .graphicsLayer { this.alpha = alpha(); translationY = parallax() }
            .blur(u(PeopleDims.SearchBlur)),
    )
    Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.32f)))
}

@Composable
private fun CategoryRow(data: HeaderData, filter: PeopleFilter, onFilter: (PeopleFilter) -> Unit, showCounts: Boolean) {
    val state = rememberLazyListState()
    val selectedIndex = when (filter) {
        PeopleFilter.All -> 0
        is PeopleFilter.InGroup -> 1 + data.groups.indexOfFirst { it.group.id == filter.id }.coerceAtLeast(0)
        PeopleFilter.Favorites -> 1 + data.groups.size
    }
    LaunchedEffect(selectedIndex) {
        val info = state.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == selectedIndex }
        if (item == null || item.offset < 0 || item.offset + item.size > info.viewportEndOffset) state.animateScrollToItem(selectedIndex, -60)
    }
    LazyRow(
        state = state,
        contentPadding = PaddingValues(horizontal = u(PeopleDims.HeaderPad)),
        horizontalArrangement = Arrangement.spacedBy(u(PeopleDims.ChipGap)),
        modifier = Modifier.padding(top = u(PeopleDims.ChipsTop)),
    ) {
        item { CategoryChip(t("Все"), filter == PeopleFilter.All, { onFilter(PeopleFilter.All) }, count = data.total.takeIf { showCounts }) }
        items(data.groups, key = { it.group.id }) { g ->
            val sel = filter == PeopleFilter.InGroup(g.group.id)
            CategoryChip(
                g.group.name, sel, { onFilter(if (sel) PeopleFilter.All else PeopleFilter.InGroup(g.group.id)) },
                count = g.count.takeIf { showCounts }, dot = categoryMarker(g.group.name) ?: Color(g.group.color),
            )
        }
        item {
            CategoryChip(t("Избранные"), filter == PeopleFilter.Favorites, {
                onFilter(if (filter == PeopleFilter.Favorites) PeopleFilter.All else PeopleFilter.Favorites)
            }, star = true)
        }
    }
}

/** Аватар владельца 34 (ед.) с обводкой 70 % белого; меню: своё фото, группы, статистика, настройки, закрыть сейф. */
@Composable
private fun OwnerAvatar(onGroups: () -> Unit, onStats: () -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val app = app()
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    val src = remember { MutableInteractionSource() }
    Box {
        // Меню разделов — такая же стеклянная кнопка, как «фон шапки» рядом.
        Box(
            Modifier.size(maxOf(u(PeopleDims.FilterButton), 40.dp)).clickable(src, indication = null) { menu = true }
                .semantics { contentDescription = t("Меню"); role = Role.Button },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(32.dp).graphicsLayer { val s = if (menu) 1.06f else 1f; scaleX = s; scaleY = s }
                    .clip(CircleShape).background(RvColors.ChipBg).border(1.dp, RvColors.ChipBorder, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.GridView, null, tint = RvColors.TextOnDark.copy(alpha = 0.85f), modifier = Modifier.size(16.dp)) }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, offset = androidx.compose.ui.unit.DpOffset(0.dp, 6.dp)) {
            DropdownMenuItem(text = { Text(t("Группы")) }, leadingIcon = { Icon(Icons.Outlined.Workspaces, null) }, onClick = { menu = false; onGroups() })
            DropdownMenuItem(text = { Text(t("Статистика")) }, leadingIcon = { Icon(Icons.Outlined.Insights, null) }, onClick = { menu = false; onStats() })
            DropdownMenuItem(text = { Text(t("Настройки")) }, leadingIcon = { Icon(Icons.Outlined.Settings, null) }, onClick = { menu = false; onSettings() })
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(t("Закрыть сейф")) }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Logout, null) },
                onClick = { menu = false; (context as? com.kartoteka.app.MainActivity)?.closeVault() },
            )
        }
    }
}

/** Жемчужная сфера — аватар, пока не выбрано своё фото. */
@Composable
fun PearlSphere(modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val r = size.minDimension / 2
        drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFF1ECE6), Color(0xFFC9C1B8)), center.copy(x = center.x - r * 0.35f, y = center.y - r * 0.4f), r * 1.7f), r, center)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFCFC8C0), Color(0xFFE7E1DA)), center, r * 0.5f), r * 0.44f, center)
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f), Color.Transparent), center.copy(x = center.x - r * 0.35f, y = center.y - r * 0.4f), r * 0.4f), r * 0.4f, center.copy(x = center.x - r * 0.35f, y = center.y - r * 0.4f))
    }
}
