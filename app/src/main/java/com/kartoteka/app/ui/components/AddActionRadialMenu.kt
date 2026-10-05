package com.kartoteka.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kartoteka.app.ui.animations.arcAngles
import com.kartoteka.app.ui.animations.arcPosition
import com.kartoteka.app.ui.animations.pressScale
import com.kartoteka.app.ui.theme.AnimationTokens
import com.kartoteka.app.ui.theme.LocalReducedMotion
import com.kartoteka.app.ui.theme.PeopleDims
import com.kartoteka.app.ui.theme.PeopleType
import com.kartoteka.app.ui.theme.RvColors
import com.kartoteka.app.ui.theme.rememberHaptics
import com.kartoteka.app.ui.theme.u
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class QuickAction(val title: String, val icon: ImageVector, val action: () -> Unit)

object QuickIcons {
    val person = Icons.Outlined.PersonAddAlt
    val appointment = Icons.Outlined.EditCalendar
    val event = Icons.Outlined.EventNote
    val broadcast = Icons.AutoMirrored.Outlined.Send
}

/**
 * Радиальное меню «+» (ТЗ, раздел 7): четыре круглые кнопки #252427 дугой над кнопкой,
 * каждая следующая — через 45 мс; открытие 260 мс пружиной (dampingRatio 0,72), масштаб 0,5→1,
 * прозрачность 0→1, движение от центра по дуге. Закрытие — обратно за 180 мс. Затемнение #00000035.
 * [anchorBelow] — насколько центр кнопки «+» ниже нижнего края этой области.
 */
@Composable
fun AddActionRadialMenu(open: Boolean, actions: List<QuickAction>, anchorBelow: Dp, onDismiss: () -> Unit) {
    val reduced = LocalReducedMotion.current
    val n = actions.size
    val progress = remember(n) { List(n) { Animatable(0f) } }
    val scrim = remember { Animatable(0f) }
    LaunchedEffect(open) {
        if (open) {
            launch { scrim.animateTo(1f, tween(AnimationTokens.Scrim, easing = AnimationTokens.Enter)) }
            progress.forEachIndexed { i, a ->
                launch {
                    delay(i * AnimationTokens.MenuStagger.toLong())
                    if (reduced) a.animateTo(1f, tween(120)) else a.animateTo(1f, spring(AnimationTokens.MenuSpringDamping, Spring.StiffnessMediumLow))
                }
            }
        } else {
            progress.forEachIndexed { i, a ->
                launch { delay((n - 1 - i) * 20L); a.animateTo(0f, tween(AnimationTokens.MenuClose, easing = AnimationTokens.Exit)) }
            }
            launch { scrim.animateTo(0f, tween(AnimationTokens.Scrim, easing = AnimationTokens.Exit)) }
        }
    }
    val visible by remember { derivedStateOf { open || scrim.value > 0f || progress.any { it.value > 0.001f } } }
    if (!visible) return

    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val itemSize = maxOf(u(PeopleDims.MenuItem), 52.dp)
    val iconSize = maxOf(u(PeopleDims.MenuIcon), 22.dp)

    BoxWithConstraints(
        Modifier.fillMaxSize()
            .drawBehind { drawRect(RvColors.Scrim.copy(alpha = RvColors.Scrim.alpha * scrim.value)) }
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    ) {
        val anchor = with(density) { Offset(maxWidth.toPx() / 2, (maxHeight + anchorBelow).toPx()) }
        // Расстояние между центрами соседних кнопок — не меньше 72 (ед.) и не меньше размера кнопки с подписью.
        val radius = with(density) { maxOf(u(PeopleDims.MenuMinDistance) / 0.72f, 150.dp).toPx() }
        val lift = with(density) { 46.dp.toPx() }
        val angles = arcAngles(n)
        val half = with(density) { (itemSize / 2).toPx() }
        val margin = with(density) { 16.dp.toPx() }
        val gap = with(density) { 6.dp.toPx() }
        val screenW = with(density) { maxWidth.toPx() }
        val labelMax = with(density) { 176.dp.roundToPx() }
        actions.forEachIndexed { i, qa ->
            val src = remember { MutableInteractionSource() }
            fun pos() = anchor + arcPosition(if (reduced) 1f else progress[i].value, angles[i], radius, lift)
            val tap = {
                haptics.tick(); onDismiss()
                scope.launch { delay(120); qa.action() }
                Unit
            }
            // Кнопка — точно на дуге.
            Box(
                Modifier
                    .offset { val c = pos(); IntOffset((c.x - half).roundToInt(), (c.y - half).roundToInt()) }
                    .graphicsLayer {
                        val p = progress[i].value
                        alpha = p.coerceIn(0f, 1f)
                        val sc = 0.5f + 0.5f * p
                        scaleX = sc; scaleY = sc
                    }
                    .size(itemSize).pressScale(src, 0.94f).clip(CircleShape).background(RvColors.MenuItemBg)
                    .clickable(src, indication = null, role = Role.Button, onClick = tap)
                    .semantics { contentDescription = qa.title },
                contentAlignment = Alignment.Center,
            ) { Icon(qa.icon, null, tint = Color.White, modifier = Modifier.size(iconSize)) }
            // Подпись под кнопкой на тёмной плашке: по центру кнопки, но не ближе 16 dp к краю экрана.
            Text(
                qa.title, style = PeopleType.menuLabel, color = RvColors.TextOnDark, textAlign = TextAlign.Center, maxLines = 2,
                modifier = Modifier
                    .layout { m, c ->
                        val pl = m.measure(c.copy(minWidth = 0, minHeight = 0, maxWidth = labelMax))
                        layout(pl.width, pl.height) {
                            val center = pos()
                            val x = (center.x - pl.width / 2f).coerceIn(margin, (screenW - margin - pl.width).coerceAtLeast(margin))
                            pl.place(x.roundToInt(), (center.y + half + gap).roundToInt())
                        }
                    }
                    .graphicsLayer { alpha = progress[i].value.coerceIn(0f, 1f) }
                    .clip(RoundedCornerShape(10.dp)).background(RvColors.MenuItemBg.copy(alpha = 0.92f))
                    .clickable(remember { MutableInteractionSource() }, indication = null, onClick = tap)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}
