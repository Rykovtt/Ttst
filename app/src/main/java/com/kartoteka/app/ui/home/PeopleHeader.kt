package com.kartoteka.app.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Workspaces
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kartoteka.app.R
import com.kartoteka.app.i18n.t
import com.kartoteka.app.ui.app
import com.kartoteka.app.ui.components.Avatar
import com.kartoteka.app.ui.components.NoaOrb
import com.kartoteka.app.ui.components.OrbState
import com.kartoteka.app.ui.components.SheenColors
import com.kartoteka.app.ui.components.categoryColor
import com.kartoteka.app.ui.components.pressable
import com.kartoteka.app.ui.components.sheenBorder
import com.kartoteka.app.ui.theme.Brand
import com.kartoteka.app.ui.theme.HomeDims
import com.kartoteka.app.ui.theme.Inter
import com.kartoteka.app.ui.theme.LocalReducedMotion
import com.kartoteka.app.ui.theme.Motion
import com.kartoteka.app.ui.theme.Wordmark
import com.kartoteka.app.ui.theme.categoryTint
import com.kartoteka.app.ui.theme.motion
import com.kartoteka.app.ui.theme.rememberHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Однократные «вступительные» анимации главного экрана: только при первом показе за запуск. */
object HomeIntro {
    var played = false
}

/** Тёмная шапка «Люди»: фото гор с параллаксом, бренд, заголовок, поиск, фильтры, быстрые действия. */
@Composable
fun PeopleHeader(
    state: HomeState,
    query: String,
    onQuery: (String) -> Unit,
    filter: PeopleFilter,
    onFilter: (PeopleFilter) -> Unit,
    showCounts: Boolean,
    filtersOpen: Boolean,
    onOpenFilters: () -> Unit,
    scrollOffset: () -> Int,
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
    onGroups: () -> Unit,
    onNoa: () -> Unit,
    onMap: () -> Unit,
    onStats: () -> Unit,
    onToday: () -> Unit,
    onBirthdays: () -> Unit,
    onSettings: () -> Unit,
) {
    val reduced = LocalReducedMotion.current
    val intro = !HomeIntro.played && !reduced
    val app = app()
    val assistantOn by app.settings.assistant.value.collectAsState()
    val searchSource = remember { MutableInteractionSource() }
    val searchFocused by searchSource.collectIsFocusedAsState()
    val dim by animateFloatAsState(if (searchFocused) 1f else 0f, tween(260), label = "dim")

    // Фото проявляется за 500 мс при первом запуске.
    val photoAlpha = remember { Animatable(if (intro) 0f else 1f) }
    LaunchedEffect(Unit) { if (intro) photoAlpha.animateTo(1f, tween(500)) }

    val shape = RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp)
    Box(Modifier.fillMaxWidth().clip(shape).background(Brand.Panel)) {
        Image(
            painterResource(R.drawable.hero_mountain), null,
            contentScale = ContentScale.Crop, alignment = Alignment.TopEnd,
            modifier = Modifier.matchParentSize().graphicsLayer {
                alpha = photoAlpha.value
                // Параллакс 0,25: фото отстаёт от прокрутки.
                translationY = if (reduced) 0f else scrollOffset() * 0.25f
            },
        )
        // Тёмная область слева для текста и мягкий уход в графит к низу.
        Box(
            Modifier.matchParentSize().drawBehind {
                drawRect(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.55f), 0.55f to Color.Black.copy(alpha = 0.12f), 1f to Color.Transparent))
                drawRect(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.25f), 0.35f to Color.Transparent, 0.62f to Brand.Panel.copy(alpha = 0.6f), 1f to Brand.Panel))
                if (dim > 0f) drawRect(Color.Black.copy(alpha = 0.28f * dim))
            }
        )
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(bottom = 22.dp)) {
            TopRow(intro, onGroups, onStats, onSettings)
            TitleBlock(state.total)
            SearchField(query, onQuery, searchSource, searchFocused, filtersOpen, onOpenFilters)
            FilterRow(state, filter, onFilter, showCounts)
            QuickRow(state, assistantOn, intro, onOpen, onAdd, onNoa, onMap, onToday, onBirthdays)
        }
    }
}

@Composable
private fun TopRow(intro: Boolean, onGroups: () -> Unit, onStats: () -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val app = app()
    val custom by app.settings.appTitle.value.collectAsState()
    val logoAlpha = remember { Animatable(if (intro) 0f else 1f) }
    val dotScale = remember { Animatable(if (intro) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (intro) {
            launch { logoAlpha.animateTo(1f, tween(400)) }
            delay(150)
            dotScale.snapTo(0.6f)
            dotScale.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
        }
    }
    var accountMenu by remember { mutableStateOf(false) }
    var mainMenu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(start = HomeDims.heroPad, end = 10.dp, top = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            com.kartoteka.app.AppIcons.title(context, custom).uppercase(),
            style = TextStyle(fontFamily = Wordmark, fontWeight = FontWeight.Light, fontSize = 29.sp, letterSpacing = 0.sp),
            color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).graphicsLayer { alpha = logoAlpha.value },
        )
        Box(
            Modifier.padding(start = 6.dp, top = 6.dp).size(7.dp)
                .graphicsLayer { scaleX = dotScale.value; scaleY = dotScale.value; alpha = dotScale.value.coerceIn(0f, 1f) }
                .clip(CircleShape).background(Brand.Glow)
        )
        Spacer(Modifier.weight(1f))

        // Аватар-сфера: меню сейфа.
        Box {
            val src = remember { MutableInteractionSource() }
            val pressed by src.collectIsPressedAsState()
            val s by animateFloatAsState(
                when { pressed -> 0.92f; accountMenu -> 1.06f; else -> 1f },
                tween(if (pressed) 90 else 180, easing = Motion.Ease), label = "avatar",
            )
            Box(
                Modifier.size(44.dp).clip(CircleShape)
                    .clickable(src, indication = null) { accountMenu = true }
                    .semantics { contentDescription = t("Меню сейфа"); role = Role.Button },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(38.dp).graphicsLayer { scaleX = s; scaleY = s }
                        .drawBehind {
                            drawCircle(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent), center.copy(y = center.y + 3.dp.toPx()), size.minDimension * 0.7f))
                        }
                        .border(2.dp, Color.White.copy(alpha = 0.9f), CircleShape).padding(2.dp),
                ) { PearlSphere(Modifier.fillMaxSize()) }
            }
            DropdownMenu(expanded = accountMenu, onDismissRequest = { accountMenu = false }, offset = androidx.compose.ui.unit.DpOffset(0.dp, 6.dp)) {
                DropdownMenuItem(
                    text = { Text(t("Закрыть сейф")) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Logout, null) },
                    onClick = { accountMenu = false; (context as? com.kartoteka.app.MainActivity)?.closeVault() },
                )
                DropdownMenuItem(text = { Text(t("Настройки")) }, leadingIcon = { Icon(Icons.Outlined.Settings, null) }, onClick = { accountMenu = false; onSettings() })
            }
        }

        // Меню: три линии превращаются в крестик.
        Box {
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable { mainMenu = !mainMenu }
                    .semantics { contentDescription = t("Ещё"); role = Role.Button },
                contentAlignment = Alignment.Center,
            ) { MenuMorph(mainMenu) }
            DropdownMenu(expanded = mainMenu, onDismissRequest = { mainMenu = false }, offset = androidx.compose.ui.unit.DpOffset(0.dp, 6.dp)) {
                DropdownMenuItem(text = { Text(t("Группы")) }, leadingIcon = { Icon(Icons.Outlined.Workspaces, null) }, onClick = { mainMenu = false; onGroups() })
                DropdownMenuItem(text = { Text(t("Статистика")) }, leadingIcon = { Icon(Icons.Outlined.Insights, null) }, onClick = { mainMenu = false; onStats() })
                HorizontalDivider()
                DropdownMenuItem(text = { Text(t("Настройки")) }, leadingIcon = { Icon(Icons.Outlined.Settings, null) }, onClick = { mainMenu = false; onSettings() })
            }
        }
    }
}

/** Три линии разной длины, плавно складывающиеся в «×». */
@Composable
private fun MenuMorph(open: Boolean) {
    val p by animateFloatAsState(if (open) 1f else 0f, motion(Motion.STANDARD), label = "menuMorph")
    Canvas(Modifier.size(24.dp)) {
        val w = size.width
        val stroke = 1.7.dp.toPx()
        val cy = size.height / 2
        val gap = 6.dp.toPx()
        val c = Color.White
        // Верхняя: длинная → диагональ «\».
        rotate(45f * p, Offset(w / 2, cy)) {
            val y = cy - gap * (1 - p)
            drawLine(c, Offset(w * (0.04f + 0.08f * p), y), Offset(w * (0.96f - 0.04f * p), y), stroke, StrokeCap.Round)
        }
        // Средняя — короче, исчезает.
        drawLine(c.copy(alpha = 1f - p), Offset(w * 0.30f, cy), Offset(w * 0.96f, cy), stroke, StrokeCap.Round)
        // Нижняя: средняя длина → диагональ «/».
        rotate(-45f * p, Offset(w / 2, cy)) {
            val y = cy + gap * (1 - p)
            drawLine(c, Offset(w * (0.18f - 0.06f * p), y), Offset(w * (0.96f - 0.04f * p), y), stroke, StrokeCap.Round)
        }
    }
}

private inline fun androidx.compose.ui.graphics.drawscope.DrawScope.rotate(deg: Float, pivot: Offset, block: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
    drawContext.transform.rotate(deg, pivot)
    block()
    drawContext.transform.rotate(-deg, pivot)
}

/** Жемчужная сфера — знак сейфа. */
@Composable
fun PearlSphere(modifier: Modifier) {
    Canvas(modifier) {
        val r = size.minDimension / 2
        drawCircle(
            Brush.radialGradient(listOf(Color.White, Color(0xFFF1ECE6), Color(0xFFC9C1B8)), center.copy(x = center.x - r * 0.35f, y = center.y - r * 0.4f), r * 1.7f),
            r, center,
        )
        drawCircle(Brush.radialGradient(listOf(Color(0xFFCFC8C0), Color(0xFFE7E1DA)), center, r * 0.5f), r * 0.44f, center)
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f), Color.Transparent), center.copy(x = center.x - r * 0.35f, y = center.y - r * 0.4f), r * 0.4f), r * 0.4f, center.copy(x = center.x - r * 0.35f, y = center.y - r * 0.4f))
    }
}

@Composable
private fun TitleBlock(total: Int) {
    Row(Modifier.padding(start = HomeDims.heroPad, end = HomeDims.heroPad, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            t("Люди"),
            style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 52.sp, lineHeight = 55.sp, letterSpacing = (-1.6).sp),
            color = Color.White,
        )
        Spacer(Modifier.width(12.dp))
        // Счётчик: цифры перелистываются по вертикали только при реальном изменении.
        Box(
            Modifier.clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(alpha = 0.32f))
                .sheenBorder(1.dp, RoundedCornerShape(20.dp), SheenColors.Glass)
                .padding(horizontal = 12.dp, vertical = 5.dp),
        ) {
            AnimatedContent(
                targetState = total,
                transitionSpec = {
                    val up = targetState > initialState
                    (slideInVertically(tween(220)) { if (up) it else -it } + fadeIn(tween(220))) togetherWith
                        (slideOutVertically(tween(220)) { if (up) -it else it } + fadeOut(tween(160)))
                },
                label = "count",
            ) { n ->
                Text("$n", style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 15.sp), color = Color.White.copy(alpha = 0.92f))
            }
        }
    }
    Text(
        t("Ваш круг. Клиенты, друзья, семья."),
        style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 22.sp),
        color = Brand.Subtitle,
        modifier = Modifier.padding(start = HomeDims.heroPad, end = HomeDims.heroPad, top = 8.dp),
    )
}

@Composable
private fun SearchField(
    query: String,
    onQuery: (String) -> Unit,
    source: MutableInteractionSource,
    focused: Boolean,
    filtersOpen: Boolean,
    onOpenFilters: () -> Unit,
) {
    val border by animateFloatAsState(if (focused) 0.32f else 0.15f, tween(200), label = "searchBorder")
    val depth by animateFloatAsState(if (focused) 1f else 0f, tween(240), label = "searchDepth")
    val tuneRot by animateFloatAsState(if (filtersOpen) 45f else 0f, motion(Motion.STANDARD), label = "tune")
    val shape = RoundedCornerShape(26.dp)
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp).height(52.dp)
            .drawBehind {
                // Глубина: тень под полем усиливается при фокусе.
                if (depth > 0f) drawRoundRect(Color.Black.copy(alpha = 0.35f * depth), topLeft = Offset(0f, 4.dp.toPx()), size = size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(26.dp.toPx()))
            }
            .clip(shape)
            .background(Color.White.copy(alpha = 0.09f))
            .background(Color.Black.copy(alpha = 0.18f))
            .border(1.dp, Color.White.copy(alpha = border), shape)
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Text(t("Поиск: имя, город, заметка…"), style = TextStyle(fontFamily = Inter, fontSize = 16.sp), color = Brand.SearchHint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(
                value = query, onValueChange = onQuery, singleLine = true,
                interactionSource = source,
                textStyle = TextStyle(fontFamily = Inter, fontSize = 16.sp, color = Color.White),
                cursorBrush = SolidColor(Brand.Glow),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = t("Поиск") },
            )
        }
        if (query.isNotEmpty()) {
            Box(Modifier.size(40.dp).clip(CircleShape).clickable { onQuery("") }, contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Close, t("Очистить"), tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
        Box(Modifier.width(1.dp).height(24.dp).background(Color.White.copy(alpha = 0.16f)))
        Box(
            Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onOpenFilters)
                .semantics { contentDescription = t("Фильтры"); role = Role.Button },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Tune, null, tint = Color.White, modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = tuneRot })
        }
    }
}

@Composable
private fun FilterRow(state: HomeState, filter: PeopleFilter, onFilter: (PeopleFilter) -> Unit, showCounts: Boolean) {
    val rowState = rememberLazyListState()
    // Выбранная капсула всегда остаётся в видимой области.
    val selectedIndex = when (filter) {
        PeopleFilter.All -> 0
        is PeopleFilter.InGroup -> 1 + state.groups.indexOfFirst { it.group.id == filter.id }.coerceAtLeast(0)
        PeopleFilter.Favorites -> 1 + state.groups.size
    }
    LaunchedEffect(selectedIndex) {
        val visible = rowState.layoutInfo.visibleItemsInfo
        val item = visible.firstOrNull { it.index == selectedIndex }
        val vpEnd = rowState.layoutInfo.viewportEndOffset
        if (item == null || item.offset < 0 || item.offset + item.size > vpEnd) rowState.animateScrollToItem(selectedIndex, -40)
    }
    LazyRow(
        state = rowState,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 14.dp),
    ) {
        item { FilterPill(t("Все"), filter == PeopleFilter.All, count = state.total.takeIf { showCounts }) { onFilter(PeopleFilter.All) } }
        items(state.groups, key = { it.group.id }) { g ->
            val sel = filter == PeopleFilter.InGroup(g.group.id)
            FilterPill(g.group.name, sel, count = g.count.takeIf { showCounts }, dot = categoryTint(g.group.name, Color(g.group.color))) {
                onFilter(if (sel) PeopleFilter.All else PeopleFilter.InGroup(g.group.id))
            }
        }
        item {
            FilterPill(t("Избранные"), filter == PeopleFilter.Favorites, icon = Icons.Outlined.StarOutline) {
                onFilter(if (filter == PeopleFilter.Favorites) PeopleFilter.All else PeopleFilter.Favorites)
            }
        }
    }
}

/** Капсула фильтра: выбранная — молочная с мягкой тенью и чуть крупнее; остальные — графитовое стекло. */
@Composable
private fun FilterPill(label: String, selected: Boolean, count: Int? = null, dot: Color? = null, icon: ImageVector? = null, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Brand.Milk else Color(0x66141315), tween(210), label = "pillbg")
    val fg by animateColorAsState(if (selected) Color(0xFF171719) else Color.White, tween(210), label = "pillfg")
    val s by animateFloatAsState(if (selected) 1.03f else 1f, tween(210), label = "pillScale")
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier.height(38.dp)
            .graphicsLayer { scaleX = s; scaleY = s }
            .drawBehind {
                if (selected) drawRoundRect(Brand.Glow.copy(alpha = 0.28f), topLeft = Offset(0f, 3.dp.toPx()), size = size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(24.dp.toPx()))
            }
            .pressable(onClick = onClick)
            .clip(shape).background(bg)
            .then(if (selected) Modifier else Modifier.sheenBorder(1.dp, shape, SheenColors.Glass))
            .padding(start = 14.dp, end = if (count != null) 5.dp else 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) { Box(Modifier.size(9.dp).clip(CircleShape).background(dot)); Spacer(Modifier.width(8.dp)) }
        if (icon != null) { Icon(icon, null, tint = fg, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)) }
        Text(label, style = TextStyle(fontFamily = Inter, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal), color = fg, maxLines = 1)
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.clip(CircleShape).background(if (selected) Color(0x14000000) else Color.White.copy(alpha = 0.10f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) { Text("$count", style = TextStyle(fontFamily = Inter, fontSize = 13.sp), color = fg.copy(alpha = 0.8f)) }
        }
    }
}

@Composable
private fun QuickRow(
    state: HomeState,
    assistantOn: Boolean,
    intro: Boolean,
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
    onNoa: () -> Unit,
    onMap: () -> Unit,
    onToday: () -> Unit,
    onBirthdays: () -> Unit,
) {
    val todayPeople = state.today.mapNotNull { it.person }.distinctBy { it.id }
    LazyRow(
        contentPadding = PaddingValues(start = 16.dp, end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Top,
        modifier = Modifier.padding(top = 18.dp),
    ) {
        item { AddTile(onAdd) }
        item {
            val p = todayPeople.firstOrNull()
            QuickCircle(t("Сегодня"), "${state.today.size}", intro, dot = p?.let { categoryColor(it.relation).takeIf { c -> c != Color.Transparent } ?: Brand.Clients }, onClick = onToday) {
                if (p != null) Avatar(p, 68.dp) else CircleIcon(Icons.Outlined.CalendarMonth)
            }
        }
        item {
            val p = state.birthdays.firstOrNull()?.first?.person
            QuickCircle(t("Дни рождения"), "${state.birthdays.size}", intro, dot = if (p != null) Brand.Family else null, onClick = onBirthdays) {
                if (p != null) Avatar(p, 68.dp) else CircleIcon(Icons.Outlined.Redeem)
            }
        }
        item {
            QuickCircle(t("Карта"), null, intro, onClick = onMap) {
                Image(painterResource(R.drawable.hero_mountain), null, contentScale = ContentScale.Crop, alignment = Alignment.TopEnd, modifier = Modifier.fillMaxSize())
            }
        }
        if (assistantOn) item { NoaTile(onNoa) }
    }
}

/** «Добавить человека»: стеклянная карточка, при касании сжимается до 0,96, «+» поворачивается на 90°. */
@Composable
private fun AddTile(onAdd: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val haptics = rememberHaptics()
    val s by animateFloatAsState(if (pressed) 0.96f else 1f, tween(if (pressed) 100 else 180), label = "addScale")
    val r by animateFloatAsState(if (pressed) 90f else 0f, tween(180, easing = Motion.Ease), label = "addRot")
    val tile = RoundedCornerShape(24.dp)
    Column(
        Modifier.padding(end = 4.dp).width(70.dp).height(112.dp)
            .graphicsLayer { scaleX = s; scaleY = s }
            .clip(tile).background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.11f), Color.White.copy(alpha = 0.03f))))
            .sheenBorder(1.dp, tile, SheenColors.Glass)
            .clickable(src, indication = null, role = Role.Button) { haptics.tick(); onAdd() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(30.dp).graphicsLayer { rotationZ = r })
        Spacer(Modifier.height(8.dp))
        Text(t("Добавить\nчеловека"), style = TextStyle(fontFamily = Inter, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium), color = Color.White, textAlign = TextAlign.Center)
    }
}

/** Круг быстрого действия: фото в кольце шампань, мягкое появление 0,92→1, точка категории — с задержкой 120 мс. */
@Composable
private fun QuickCircle(label: String, value: String?, intro: Boolean, dot: Color? = null, onClick: () -> Unit, content: @Composable () -> Unit) {
    val appear = remember { Animatable(if (intro) 0.92f else 1f) }
    val dotA = remember { Animatable(if (intro) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (intro) {
            launch { appear.animateTo(1f, tween(360, easing = Motion.Ease)) }
            delay(120 + 240)
            dotA.animateTo(1f, tween(200))
        }
    }
    Column(Modifier.width(84.dp).pressable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(76.dp).graphicsLayer { scaleX = appear.value; scaleY = appear.value }) {
            Box(
                Modifier.fillMaxSize().sheenBorder(1.6.dp, CircleShape, SheenColors.Champagne)
                    .padding(4.dp).clip(CircleShape).background(Color(0xFF1C1C20)),
                contentAlignment = Alignment.Center,
            ) { content() }
            if (dot != null && dot != Color.Transparent) {
                Box(
                    Modifier.align(Alignment.BottomEnd).offset(x = (-3).dp, y = (-5).dp).size(15.dp)
                        .graphicsLayer { alpha = dotA.value; scaleX = 0.5f + dotA.value * 0.5f; scaleY = 0.5f + dotA.value * 0.5f }
                        .clip(CircleShape).background(Brand.Panel).padding(2.5.dp).clip(CircleShape).background(dot)
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        com.kartoteka.app.ui.FitText(label, Color.White, 13f, 10f, align = TextAlign.Center)
        if (value != null) Text(value, style = TextStyle(fontFamily = Inter, fontSize = 13.sp), color = Color.White.copy(alpha = 0.85f))
    }
}

@Composable
private fun CircleIcon(icon: ImageVector) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
}

/** «Ассистент Ноа»: сфера дышит; при нажатии увеличивается и даёт короткое световое кольцо, затем открывается ассистент. */
@Composable
private fun NoaTile(onNoa: () -> Unit) {
    val scope = rememberCoroutineScope()
    val activate = remember { Animatable(0f) }
    var state by remember { mutableStateOf(OrbState.IDLE) }
    val tile = RoundedCornerShape(24.dp)
    Column(
        Modifier.width(84.dp).pressable {
            scope.launch {
                state = OrbState.SUCCESS
                activate.snapTo(0f)
                activate.animateTo(1f, tween(280, easing = Motion.Ease))
                onNoa()
                delay(300); activate.snapTo(0f); state = OrbState.IDLE
            }
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(76.dp).clip(tile).background(Brush.verticalGradient(listOf(Color(0xFF1C1C1F), Color(0xFF0B0B0C))))
                .sheenBorder(1.dp, tile, SheenColors.Champagne.map { it.copy(alpha = it.alpha * 0.7f) })
                .drawBehind {
                    val p = activate.value
                    if (p > 0f && p < 1f) drawCircle(Brand.Glow.copy(alpha = 0.6f * (1f - p)), size.minDimension * (0.3f + 0.45f * p), style = androidx.compose.ui.graphics.drawscope.Stroke(1.4.dp.toPx()))
                },
            contentAlignment = Alignment.Center,
        ) {
            NoaOrb(Modifier.size(50.dp).graphicsLayer { val s = 1f + 0.15f * activate.value; scaleX = s; scaleY = s }, state)
        }
        Spacer(Modifier.height(7.dp))
        Text(t("Ассистент\nНоа"), style = TextStyle(fontFamily = Inter, fontSize = 13.sp, lineHeight = 15.sp), color = Color.White, textAlign = TextAlign.Center)
    }
}
