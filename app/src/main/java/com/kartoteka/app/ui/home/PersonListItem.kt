package com.kartoteka.app.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kartoteka.app.data.ArchiveLogic
import com.kartoteka.app.data.PersonHint
import com.kartoteka.app.i18n.t
import com.kartoteka.app.messaging.Messaging
import com.kartoteka.app.ui.app
import com.kartoteka.app.ui.components.Avatar
import com.kartoteka.app.ui.components.categoryColor
import com.kartoteka.app.ui.components.sharedPhoto
import com.kartoteka.app.ui.theme.Brand
import com.kartoteka.app.ui.theme.HomeDims
import com.kartoteka.app.ui.theme.HomePalette
import com.kartoteka.app.ui.theme.Inter
import com.kartoteka.app.ui.theme.LocalReducedMotion
import com.kartoteka.app.ui.theme.Motion
import com.kartoteka.app.ui.theme.categoryTint
import com.kartoteka.app.ui.theme.homePalette
import com.kartoteka.app.ui.theme.rememberHaptics
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import kotlin.math.cos
import kotlin.math.sin

/** Подсветка совпадений с поисковым запросом. */
fun highlight(text: String, query: String, color: Color): AnnotatedString {
    val words = query.lowercase().split(' ').map { it.trim() }.filter { it.length >= 1 }
    if (words.isEmpty()) return AnnotatedString(text)
    val lower = text.lowercase()
    return buildAnnotatedString {
        append(text)
        words.forEach { w ->
            var i = lower.indexOf(w)
            while (i >= 0) {
                addStyle(SpanStyle(background = color.copy(alpha = 0.28f), fontWeight = FontWeight.SemiBold), i, i + w.length)
                i = lower.indexOf(w, i + w.length)
            }
        }
    }
}

/**
 * Элемент списка людей. Слева — аватар с индикатором категории, имя со звездой, подпись и плашка события;
 * справа — звонок, сообщение, меню, миниатюры фото и стрелка. Без отдельной карточки: элементы образуют единый список.
 */
@Composable
fun PersonListItem(
    hit: ArchiveLogic.SearchHit,
    hint: PersonHint?,
    query: String,
    last: Boolean,
    narrow: Boolean,
    onOpen: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    onNewAppointment: (Long) -> Unit,
    onOpenPhoto: (Long, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = homePalette()
    val context = LocalContext.current
    val app = app()
    val scope = rememberCoroutineScope()
    val pf = hit.person
    val p = pf.person
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val avatarScale by animateFloatAsState(if (pressed) 1.04f else 1f, tween(160), label = "avatarPress")
    val chevronShift by animateFloatAsState(if (pressed) 3f else 0f, tween(140), label = "chevron")
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val dot = pf.groups.firstOrNull()?.let { categoryTint(it.name, Color(it.color)) } ?: categoryColor(p.relation).let { col ->
        when {
            p.relation.lowercase().let { it.contains("клиент") || it.contains("клієнт") } -> Brand.Clients
            p.relation.lowercase().let { it.contains("сем") || it.contains("сім") } -> Brand.Family
            p.relation.lowercase().contains("друг") -> Brand.Friends
            else -> col
        }
    }
    val photos = pf.photos.filter { it.path != p.avatarPath }.takeLast(2).ifEmpty { pf.photos.takeLast(2) }

    Column(modifier.fillMaxWidth().background(c.content).clickable(src, indication = null) { onOpen(p.id) }) {
        Row(Modifier.fillMaxWidth().padding(start = HomeDims.sidePad, end = HomeDims.sidePad - 6.dp, top = 16.dp, bottom = 16.dp)) {
            // Аватар с индикатором категории.
            Box(Modifier.size(HomeDims.avatar + 4.dp).graphicsLayer { scaleX = avatarScale; scaleY = avatarScale }) {
                Avatar(
                    p, HomeDims.avatar,
                    Modifier.align(Alignment.Center).sharedPhoto(p.id).border(1.dp, c.avatarBorder, CircleShape),
                )
                if (dot != Color.Transparent) {
                    Box(
                        Modifier.align(Alignment.TopEnd).size(16.dp).clip(CircleShape)
                            .background(c.content).padding(3.dp).clip(CircleShape).background(dot)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).padding(top = 1.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        highlight(p.displayName, query, Brand.Glow),
                        style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 20.sp, letterSpacing = (-0.3).sp),
                        color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(3.dp))
                    FavoriteStar(p.favorite, c) { scope.launch { app.repository.setFavorite(p.id, !p.favorite) } }
                }
                val sub = listOf(t(p.relation), p.position.ifBlank { p.company }, p.city).filter { it.isNotBlank() }
                if (hit.matchedIn != null && query.isNotBlank()) {
                    Text(highlight(hit.matchedIn, query, Brand.Glow), style = TextStyle(fontFamily = Inter, fontSize = 14.sp), color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else if (sub.isNotEmpty()) {
                    Text(
                        highlight(sub.joinToString(" · "), query, Brand.Glow),
                        style = TextStyle(fontFamily = Inter, fontSize = 14.sp), color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                if (hint != null) {
                    Spacer(Modifier.height(9.dp))
                    HintChip(hint, c)
                }
                if (narrow && photos.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Thumbs(photos.map { it.path }, c) { i -> onOpenPhoto(p.id, pf.photos.indexOf(photos[i]).coerceAtLeast(0)) }
                }
            }
            Spacer(Modifier.width(6.dp))
            Column(horizontalAlignment = Alignment.End) {
                Row(horizontalArrangement = Arrangement.spacedBy(if (narrow) 3.dp else 6.dp)) {
                    RoundAction(Icons.Default.Call, t("Позвонить"), c, enabled = pf.phone != null) { pf.phone?.let { Messaging.dial(context, it) } }
                    RoundAction(Icons.AutoMirrored.Filled.Chat, t("Написать"), c, enabled = pf.whatsapp != null || pf.phone != null) {
                        pf.whatsapp?.let { Messaging.whatsapp(context, it) } ?: pf.phone?.let { Messaging.sms(context, listOf(it)) }
                    }
                    Box {
                        RoundAction(Icons.Default.MoreVert, t("Действия"), c) { menu = true }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(t("Открыть карточку")) }, leadingIcon = { Icon(Icons.Outlined.Person, null) }, onClick = { menu = false; onOpen(p.id) })
                            DropdownMenuItem(text = { Text(t("Редактировать")) }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; onEdit(p.id) })
                            DropdownMenuItem(text = { Text(t("Записать")) }, leadingIcon = { Icon(Icons.Outlined.EventAvailable, null) }, onClick = { menu = false; onNewAppointment(p.id) })
                            DropdownMenuItem(
                                text = { Text(if (p.favorite) t("Убрать из избранного") else t("В избранное")) },
                                leadingIcon = { Icon(if (p.favorite) Icons.Outlined.StarOutline else Icons.Default.Star, null) },
                                onClick = { menu = false; scope.launch { app.repository.setFavorite(p.id, !p.favorite) } },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(t("Удалить"), color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { menu = false; confirmDelete = true },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!narrow && photos.isNotEmpty()) {
                        Thumbs(photos.map { it.path }, c) { i -> onOpenPhoto(p.id, pf.photos.indexOf(photos[i]).coerceAtLeast(0)) }
                        Spacer(Modifier.width(4.dp))
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = c.chevron,
                        modifier = Modifier.size(22.dp).graphicsLayer { translationX = chevronShift * density },
                    )
                }
            }
        }
        if (!last) HorizontalDivider(Modifier.padding(horizontal = HomeDims.sidePad), thickness = 1.dp, color = c.divider)
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(t("Удалить %1\$s?", p.displayName)) },
            text = { Text(t("Карточка, фото, голосовые заметки и хроника будут удалены без возможности восстановления.")) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; scope.launch { app.repository.deletePerson(p.id) } }) { Text(t("Удалить")) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(t("Отмена")) } },
        )
    }
}

@Composable
private fun Thumbs(paths: List<String>, c: HomePalette, onClick: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        paths.forEachIndexed { i, path ->
            val src = remember { MutableInteractionSource() }
            val pressed by src.collectIsPressedAsState()
            val s by animateFloatAsState(if (pressed) 0.94f else 1f, tween(120), label = "thumb")
            AsyncImage(
                model = File(path), contentDescription = t("Фото"), contentScale = ContentScale.Crop,
                modifier = Modifier.size(HomeDims.thumb).graphicsLayer { scaleX = s; scaleY = s }
                    .clip(RoundedCornerShape(11.dp)).background(c.chipLast)
                    .clickable(src, indication = null) { onClick(i) },
            )
        }
    }
}

/**
 * Звезда избранного: при добавлении увеличивается до 1,3 и возвращается, заливка проявляется за 180 мс,
 * вокруг коротко расходятся лучи.
 */
@Composable
private fun FavoriteStar(active: Boolean, c: HomePalette, onToggle: () -> Unit) {
    val reduced = LocalReducedMotion.current
    val haptics = rememberHaptics()
    val scale = remember { Animatable(1f) }
    val rays = remember { Animatable(1f) }
    val fill by animateFloatAsState(if (active) 1f else 0f, tween(180), label = "starFill")
    var prev by remember { mutableStateOf(active) }
    LaunchedEffect(active) {
        if (active && !prev && !reduced) {
            launch { rays.snapTo(0f); rays.animateTo(1f, tween(320)) }
            scale.animateTo(1f, keyframes { durationMillis = 250; 1.3f at 110 })
        }
        prev = active
    }
    Box(
        Modifier.size(24.dp).clip(CircleShape)
            .clickable(role = Role.Button) { haptics.tick(); onToggle() }
            .semantics { contentDescription = if (active) t("Убрать из избранного") else t("В избранное") }
            .drawBehind {
                val p = rays.value
                if (p < 1f) {
                    for (k in 0 until 6) {
                        val a = Math.toRadians(k * 60.0 - 90.0)
                        val r1 = size.minDimension * (0.42f + 0.25f * p)
                        val r2 = r1 + size.minDimension * 0.14f
                        drawLine(
                            Brand.StarActive.copy(alpha = 1f - p),
                            center + Offset((cos(a) * r1).toFloat(), (sin(a) * r1).toFloat()),
                            center + Offset((cos(a) * r2).toFloat(), (sin(a) * r2).toFloat()),
                            strokeWidth = 1.6.dp.toPx(), cap = StrokeCap.Round,
                        )
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(20.dp).graphicsLayer { scaleX = scale.value; scaleY = scale.value }) {
            Icon(Icons.Outlined.StarOutline, null, tint = androidx.compose.ui.graphics.lerp(c.star, Brand.StarActive, fill), modifier = Modifier.size(20.dp))
            Icon(Icons.Default.Star, null, tint = Brand.StarActive, modifier = Modifier.size(20.dp).graphicsLayer { alpha = fill })
        }
    }
}

/** Круглая кнопка действия: при нажатии 0,92, фон темнеет, тактильный отклик. */
@Composable
private fun RoundAction(icon: ImageVector, desc: String, c: HomePalette, enabled: Boolean = true, size: Dp = HomeDims.action, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.92f else 1f, tween(if (pressed) 90 else 180), label = "actScale")
    val bg by animateColorAsState(if (pressed) c.actionBgPressed else c.actionBg, tween(120), label = "actBg")
    Box(
        Modifier.size(size).graphicsLayer { scaleX = s; scaleY = s; alpha = if (enabled) 1f else 0.38f }
            .clip(CircleShape).background(bg)
            .then(if (enabled) Modifier.clickable(src, indication = null, role = Role.Button) { haptics.tick(); onClick() } else Modifier)
            .semantics { contentDescription = desc; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = c.actionIcon, modifier = Modifier.size(19.dp)) }
}

/** Плашка события: цвет по типу, плавная смена цвета и текста. */
@Composable
private fun HintChip(h: PersonHint, c: HomePalette) {
    data class Look(val icon: ImageVector, val text: String, val bg: Color, val fg: Color)
    val look = when (h) {
        is PersonHint.Today -> Look(
            if (h.reminder) Icons.Outlined.NotificationsActive else Icons.Outlined.CalendarMonth,
            t("Сегодня %1\$s", h.time), if (h.reminder) c.chipReminder else c.chipPlanned, c.chipText,
        )
        is PersonHint.Upcoming -> Look(
            if (h.reminder) Icons.Outlined.NotificationsActive else Icons.Outlined.CalendarMonth,
            (if (h.date == LocalDate.now().plusDays(1)) t("Завтра") else com.kartoteka.app.i18n.I18n.dayMonthShort(h.date.dayOfMonth, h.date.monthValue)) + " " + h.time,
            if (h.reminder) c.chipReminder else c.chipPlanned, c.chipText,
        )
        is PersonHint.Birthday -> Look(
            Icons.Outlined.Redeem,
            if (h.days == 0L) t("День рождения сегодня") else t("День рождения %1\$s", com.kartoteka.app.i18n.I18n.dayMonthShort(h.day, h.month)),
            c.chipSoon, c.chipSoonText,
        )
        is PersonHint.LastMet -> Look(
            Icons.Outlined.Schedule,
            when (h.days) {
                0L -> t("Виделись сегодня")
                1L -> t("Виделись вчера")
                else -> t("Была встреча %1\$s дн. назад", h.days)
            },
            c.chipLast, c.chipText,
        )
    }
    val bg by animateColorAsState(look.bg, tween(220), label = "chipBg")
    val appear = remember { Animatable(if (h is PersonHint.Today && h.reminder) 0.96f else 1f) }
    LaunchedEffect(h) { if (appear.value < 1f) appear.animateTo(1f, tween(200)) }
    Row(
        Modifier.height(32.dp).graphicsLayer { scaleX = appear.value; scaleY = appear.value }
            .clip(RoundedCornerShape(22.dp)).background(bg).padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(look.icon, null, tint = look.fg, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(7.dp))
        AnimatedContent(look.text, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) }, label = "chipText") { txt ->
            com.kartoteka.app.ui.FitText(txt, look.fg, 13.5f, 10.5f, FontWeight.Medium)
        }
    }
}

/** Скелет строки при первой загрузке. */
@Composable
fun PersonSkeleton(alpha: () -> Float) {
    val c = homePalette()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = HomeDims.sidePad, vertical = 18.dp).graphicsLayer { this.alpha = alpha() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(HomeDims.avatar).clip(CircleShape).background(c.chipPlanned))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Box(Modifier.fillMaxWidth(0.6f).height(16.dp).clip(RoundedCornerShape(8.dp)).background(c.chipPlanned))
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth(0.4f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(c.chipLast))
            Spacer(Modifier.height(12.dp))
            Box(Modifier.width(140.dp).height(30.dp).clip(RoundedCornerShape(16.dp)).background(c.chipLast))
        }
    }
}
