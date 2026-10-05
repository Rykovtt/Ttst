package com.kartoteka.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Person
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kartoteka.app.data.ArchiveLogic
import com.kartoteka.app.data.PersonHint
import com.kartoteka.app.i18n.t
import com.kartoteka.app.messaging.Messaging
import com.kartoteka.app.ui.app
import com.kartoteka.app.ui.animations.pressScale
import com.kartoteka.app.ui.theme.AnimationTokens
import com.kartoteka.app.ui.theme.LocalReducedMotion
import com.kartoteka.app.ui.theme.PeopleDims
import com.kartoteka.app.ui.theme.PeopleShapes
import com.kartoteka.app.ui.theme.PeopleType
import com.kartoteka.app.ui.theme.RvColors
import com.kartoteka.app.ui.theme.rememberHaptics
import com.kartoteka.app.ui.theme.u
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

/** Подсветка совпадений с поисковым запросом. */
fun highlight(text: String, query: String, color: Color): AnnotatedString {
    val words = query.lowercase().split(' ').map { it.trim() }.filter { it.isNotEmpty() }
    if (words.isEmpty()) return AnnotatedString(text)
    val lower = text.lowercase()
    return buildAnnotatedString {
        append(text)
        words.forEach { w ->
            var i = lower.indexOf(w)
            while (i >= 0) {
                addStyle(SpanStyle(background = color.copy(alpha = 0.35f), fontWeight = FontWeight.Bold), i, i + w.length)
                i = lower.indexOf(w, i + w.length)
            }
        }
    }
}

/** Цвет индикатора категории: «Клиенты» — фиолетовый, «Семья» — зелёный, «Друзья» — оранжевый. */
fun categoryMarker(name: String): Color? {
    val n = name.lowercase()
    return when {
        n.contains("клиент") || n.contains("клієнт") || n.contains("client") -> RvColors.Violet
        n.contains("сем") || n.contains("сім") || n.contains("famil") -> RvColors.Green
        n.contains("друз") || n.contains("друг") || n.contains("friend") -> RvColors.Orange
        else -> null
    }
}

/**
 * Карточка контакта (высота 150 ед.): аватар слева, имя, звезда, подпись и статус по центру;
 * справа — звонок, сообщение, меню, миниатюры и стрелка. Свайп вправо — дополнительные действия,
 * свайп влево — удаление. Нажатие: масштаб 0,985 за 100 мс.
 */
@Composable
fun ContactCard(
    hit: ArchiveLogic.SearchHit,
    hint: PersonHint?,
    query: String,
    last: Boolean,
    dark: Boolean,
    background: Color,
    onOpen: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    onNewAppointment: (Long) -> Unit,
    onOpenPhoto: (Long, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = app()
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    val pf = hit.person
    val p = pf.person
    val textColor = if (dark) Color(0xFFF2EFEA) else RvColors.Text
    val metaColor = if (dark) Color(0xFF9A979E) else RvColors.TextSecondary
    val dividerColor = if (dark) Color(0xFF232326) else RvColors.Divider
    var menu by remember { mutableStateOf(false) }
    var messengers by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val dot = pf.groups.firstNotNullOfOrNull { categoryMarker(it.name) } ?: categoryMarker(p.relation)
        ?: pf.groups.firstOrNull()?.let { Color(it.color) }
    val photos = pf.photos.filter { it.path != p.avatarPath }.takeLast(2).ifEmpty { pf.photos.takeLast(2) }
    val cardSrc = remember { MutableInteractionSource() }
    val pressed by cardSrc.collectIsPressedAsState()
    val chevronShift by animateFloatAsState(if (pressed) 1f else 0f, tween(AnimationTokens.Press), label = "chevron")

    // Свайп: >0 — дополнительные действия слева, <0 — удаление справа.
    val leftTray = u(PeopleDims.Action * 3 + PeopleDims.ActionGap * 4)
    val rightTray = u(PeopleDims.Action + PeopleDims.ActionGap * 2)
    val leftPx = with(density) { leftTray.toPx() }
    val rightPx = with(density) { rightTray.toPx() }
    val offset = remember { Animatable(0f) }
    val spec = tween<Float>(AnimationTokens.Release, easing = AnimationTokens.Move)

    Box(modifier.fillMaxWidth().background(background)) {
        // Подложки действий.
        Row(Modifier.matchParentSize().padding(horizontal = u(PeopleDims.CardPad)), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.graphicsLayer { alpha = (offset.value / leftPx).coerceIn(0f, 1f) },
                horizontalArrangement = Arrangement.spacedBy(u(PeopleDims.ActionGap)),
            ) {
                TrayAction(if (p.favorite) Icons.Outlined.StarOutline else Icons.Default.Star, RvColors.StarActive, t("В избранное")) {
                    scope.launch { offset.animateTo(0f, spec); app.repository.setFavorite(p.id, !p.favorite) }
                }
                TrayAction(Icons.Outlined.EventAvailable, RvColors.Violet, t("Записать")) { scope.launch { offset.animateTo(0f, spec) }; onNewAppointment(p.id) }
                TrayAction(Icons.Outlined.Edit, RvColors.TextSecondary, t("Редактировать")) { scope.launch { offset.animateTo(0f, spec) }; onEdit(p.id) }
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.graphicsLayer { alpha = (-offset.value / rightPx).coerceIn(0f, 1f) }) {
                TrayAction(Icons.Outlined.Delete, MaterialTheme.colorScheme.error, t("Удалить")) {
                    scope.launch { offset.animateTo(0f, spec) }; confirmDelete = true
                }
            }
        }

        Column(
            Modifier.fillMaxWidth()
                .graphicsLayer { translationX = offset.value }
                .background(background)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { d -> scope.launch { offset.snapTo((offset.value + d).coerceIn(-rightPx * 1.2f, leftPx * 1.1f)) } },
                    onDragStopped = { v ->
                        val target = when {
                            offset.value > leftPx / 2 || v > 1400f -> leftPx
                            offset.value < -rightPx / 2 || v < -1400f -> -rightPx
                            else -> 0f
                        }
                        if (target != 0f) haptics.tick()
                        offset.animateTo(target, spec)
                    },
                )
                .pressScale(cardSrc, 0.985f)
                .clickable(cardSrc, indication = null) { if (offset.value != 0f) scope.launch { offset.animateTo(0f, spec) } else onOpen(p.id) },
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = u(PeopleDims.CardHeight))
                    .padding(horizontal = u(PeopleDims.CardPad), vertical = u(22)),
                verticalAlignment = Alignment.Top,
            ) {
                ContactAvatar(p, dot, background)
                Spacer(Modifier.width(u(22)))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            highlight(p.displayName, query, RvColors.WarmLight), style = PeopleType.contactName, color = textColor,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(u(10)))
                        FavoriteStar(p.favorite) { scope.launch { app.repository.setFavorite(p.id, !p.favorite) } }
                    }
                    val meta = listOf(t(p.relation), p.position.ifBlank { p.company }, p.city).filter { it.isNotBlank() }
                    val metaText = if (hit.matchedIn != null && query.isNotBlank()) hit.matchedIn else meta.joinToString(" · ")
                    if (metaText.isNotBlank()) {
                        Text(
                            highlight(metaText, query, RvColors.WarmLight), style = PeopleType.contactMeta, color = metaColor,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = u(4)),
                        )
                    }
                    if (hint != null) {
                        Spacer(Modifier.height(u(14)))
                        ContactStatusPill(hint, dark)
                    }
                }
                Spacer(Modifier.width(u(12)))
                Column(horizontalAlignment = Alignment.End) {
                    Row(horizontalArrangement = Arrangement.spacedBy(u(PeopleDims.ActionGap))) {
                        ContactActionButton(Icons.Default.Call, t("Позвонить"), enabled = pf.phone != null, dark = dark) { pf.phone?.let { Messaging.dial(context, it) } }
                        Box {
                            ContactActionButton(Icons.AutoMirrored.Filled.Chat, t("Написать"), enabled = pf.phone != null || pf.telegram != null, dark = dark) { messengers = true }
                            DropdownMenu(expanded = messengers, onDismissRequest = { messengers = false }) {
                                pf.whatsapp?.let { w -> DropdownMenuItem(text = { Text("WhatsApp") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.Chat, null) }, onClick = { messengers = false; Messaging.whatsapp(context, w) }) }
                                pf.telegram?.let { tg -> DropdownMenuItem(text = { Text("Telegram") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.Send, null) }, onClick = { messengers = false; Messaging.telegram(context, tg) }) }
                                pf.phone?.let { ph -> DropdownMenuItem(text = { Text("SMS") }, leadingIcon = { Icon(Icons.Default.Sms, null) }, onClick = { messengers = false; Messaging.sms(context, listOf(ph)) }) }
                            }
                        }
                        Box {
                            ContactActionButton(Icons.Default.MoreVert, t("Действия"), dark = dark) { menu = true }
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
                    Spacer(Modifier.height(u(14)))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (photos.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(u(PeopleDims.ThumbGap))) {
                                photos.forEach { ph ->
                                    val src = remember { MutableInteractionSource() }
                                    AsyncImage(
                                        model = File(ph.path), contentDescription = t("Фото"), contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(u(PeopleDims.Thumb)).pressScale(src)
                                            .clip(PeopleShapes.thumb()).background(if (dark) Color(0xFF232226) else RvColors.PillBg)
                                            .clickable(src, indication = null) { onOpenPhoto(p.id, pf.photos.indexOf(ph).coerceAtLeast(0)) },
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(u(PeopleDims.ChevronLeft)))
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = RvColors.Chevron,
                            modifier = Modifier.size(maxOf(u(PeopleDims.Chevron) * 1.3f, 14.dp)).graphicsLayer { translationX = chevronShift * 3.dp.toPx() },
                        )
                    }
                }
            }
            if (!last) HorizontalDivider(Modifier.padding(horizontal = u(PeopleDims.CardPad)), thickness = 1.dp, color = dividerColor)
        }
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
private fun TrayAction(icon: ImageVector, tint: Color, description: String, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    Box(
        Modifier.size(u(PeopleDims.Action)).pressScale(src, 0.92f).clip(CircleShape).background(tint.copy(alpha = 0.16f))
            .clickable(src, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(u(PeopleDims.ActionIcon))) }
}

/** Звезда избранного 24 (ед.): #777780 / #E9A64C; при добавлении — пульс до 1,3 и короткие лучи. */
@Composable
fun FavoriteStar(active: Boolean, onToggle: () -> Unit) {
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
    val size = u(PeopleDims.Star)
    Box(
        Modifier.size(maxOf(size, 24.dp)).clip(CircleShape)
            .clickable(role = Role.Button) { haptics.tick(); onToggle() }
            .semantics { contentDescription = if (active) t("Убрать из избранного") else t("В избранное") }
            .drawBehind {
                val p = rays.value
                if (p < 1f) for (k in 0 until 6) {
                    val a = Math.toRadians(k * 60.0 - 90.0)
                    val r1 = this.size.minDimension * (0.36f + 0.22f * p)
                    val r2 = r1 + this.size.minDimension * 0.14f
                    drawLine(
                        RvColors.StarActive.copy(alpha = 1f - p),
                        center + Offset((cos(a) * r1).toFloat(), (sin(a) * r1).toFloat()),
                        center + Offset((cos(a) * r2).toFloat(), (sin(a) * r2).toFloat()),
                        strokeWidth = 1.4.dp.toPx(), cap = StrokeCap.Round,
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(size).graphicsLayer { scaleX = scale.value; scaleY = scale.value }) {
            Icon(Icons.Outlined.StarOutline, null, tint = androidx.compose.ui.graphics.lerp(RvColors.Star, RvColors.StarActive, fill), modifier = Modifier.size(size))
            Icon(Icons.Default.Star, null, tint = RvColors.StarActive, modifier = Modifier.size(size).graphicsLayer { alpha = fill })
        }
    }
}
