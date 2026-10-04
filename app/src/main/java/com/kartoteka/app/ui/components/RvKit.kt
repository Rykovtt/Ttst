package com.kartoteka.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kartoteka.app.ui.theme.Motion
import com.kartoteka.app.ui.theme.Rv
import com.kartoteka.app.ui.theme.motion
import com.kartoteka.app.ui.theme.rememberHaptics

/*
 * Фирменные элементы RVAULT: заголовок экрана, счётчик-«таблетка», круглые действия,
 * градиентная кнопка отправки, нажатие с «проседанием» и тёмная героическая поверхность.
 */

/** Нажатие с лёгким проседанием (микроанимация 150 мс) + тактильный щелчок. */
@Composable
fun Modifier.pressable(haptic: Boolean = true, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.94f else 1f, motion(Motion.MICRO), label = "press")
    val haptics = rememberHaptics()
    return this.scale(s).clickable(interactionSource = source, indication = null) {
        if (haptic) haptics.tick()
        onClick()
    }
}

/** Крупный заголовок раздела с подписью и действиями справа. */
@Composable
fun ScreenTitle(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    count: Int? = null,
    color: Color = MaterialTheme.colorScheme.onBackground,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.statusBarsPadding().fillMaxWidth().padding(start = 22.dp, end = 10.dp, top = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.headlineLarge, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (count != null) {
                    Spacer(Modifier.width(10.dp))
                    CountPill(count, dark = color != MaterialTheme.colorScheme.onBackground)
                }
            }
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle, style = MaterialTheme.typography.bodyMedium,
                    color = if (color == MaterialTheme.colorScheme.onBackground) MaterialTheme.colorScheme.onSurfaceVariant else Rv.HeroMuted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** Компактный счётчик рядом с заголовком. */
@Composable
fun CountPill(n: Int, dark: Boolean = false) {
    Box(
        Modifier.clip(CircleShape)
            .background(if (dark) Color.White.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text("$n", style = MaterialTheme.typography.labelMedium, color = if (dark) Rv.HeroText else MaterialTheme.colorScheme.onSurface)
    }
}

/** Круглое действие с подписью (быстрые действия в шапке). */
@Composable
fun CircleAction(
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 60.dp,
    container: Color = Rv.HeroSurface,
    ring: Color? = null,
    labelColor: Color = Rv.HeroText,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(modifier.width(size + 18.dp).pressable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(size)
                .then(if (ring != null) Modifier.border(2.dp, ring, CircleShape).padding(4.dp) else Modifier)
                .clip(CircleShape).background(container),
            contentAlignment = Alignment.Center,
        ) { content() }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = labelColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Кнопка главного действия: графит, перетекающий в персиковый. */
@Composable
fun GradientButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trailing: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val haptics = rememberHaptics()
    val brush = Brush.horizontalGradient(listOf(Rv.Ink, Color(0xFF3A2A22), Rv.PeachDeep))
    Row(
        modifier.fillMaxWidth().height(58.dp).clip(CircleShape)
            .background(if (enabled) brush else Brush.horizontalGradient(listOf(Color(0xFFBDB7AF), Color(0xFFCFC9C1))))
            .then(if (enabled) Modifier.pressable(haptic = false) { haptics.confirm(); onClick() } else Modifier)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
        }
        Text(text, color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (trailing != null) Text(trailing, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelLarge)
    }
}

/** Карточка-панель RVAULT: тёплая бумага, крупное скругление. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    padding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(26.dp)).background(color).padding(padding),
        content = content,
    )
}

/** Подзаголовок панели: значок + жирная подпись + действие справа. */
@Composable
fun PanelHeader(title: String, icon: ImageVector? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(10.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** Тёмная «героическая» поверхность с тёплым свечением — шапки и фирменные блоки. */
fun Modifier.heroBackground(glow: Color = Rv.Peach, glowAt: Offset? = null): Modifier = this
    .background(Rv.HeroBg)
    .drawBehind {
        val c = glowAt ?: Offset(size.width * 0.92f, size.height * 0.08f)
        drawCircle(
            Brush.radialGradient(listOf(glow.copy(alpha = 0.32f), Color.Transparent), center = c, radius = size.maxDimension * 0.75f),
            radius = size.maxDimension * 0.75f, center = c,
        )
        val c2 = Offset(size.width * 0.05f, size.height * 1.05f)
        drawCircle(
            Brush.radialGradient(listOf(Rv.Lavender.copy(alpha = 0.16f), Color.Transparent), center = c2, radius = size.maxDimension * 0.6f),
            radius = size.maxDimension * 0.6f, center = c2,
        )
        // Тонкая фирменная сетка-штриховка — «картотека».
        val step = 22.dp.toPx()
        var x = size.width - step * 7
        while (x < size.width) {
            drawLine(Color.White.copy(alpha = 0.035f), Offset(x, 0f), Offset(x + size.height * 0.4f, size.height), strokeWidth = 1f)
            x += step
        }
    }

/** Цвет категории по названию «кем приходится». */
fun categoryColor(relation: String): Color {
    val r = relation.lowercase()
    return when {
        r.contains("клиент") || r.contains("клієнт") || r.contains("client") -> Rv.Lavender
        r.contains("сем") || r.contains("сім") || r.contains("family") || r.contains("мам") || r.contains("пап") -> Rv.Lime
        r.contains("друг") || r.contains("friend") -> Rv.Peach
        r.contains("колег") || r.contains("работ") || r.contains("colleague") -> Rv.Cobalt
        r.isBlank() -> Color.Transparent
        else -> Rv.Coral
    }
}

/** Маленькая метка-категория: точка + подпись на мягком фоне. */
@Composable
fun CategoryTag(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(CircleShape).background(color.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}
