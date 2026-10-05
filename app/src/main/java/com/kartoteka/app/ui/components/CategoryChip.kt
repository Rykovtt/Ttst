package com.kartoteka.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kartoteka.app.ui.theme.AnimationTokens
import com.kartoteka.app.ui.theme.PeopleDims
import com.kartoteka.app.ui.theme.PeopleType
import com.kartoteka.app.ui.theme.RvColors
import com.kartoteka.app.ui.theme.rememberHaptics
import com.kartoteka.app.ui.theme.u

/**
 * Капсула категории: активная — светлая #F5E9DC с тёмным текстом и масштабом 1,02;
 * остальные — #FFFFFF15 с обводкой #FFFFFF25 и цветным индикатором. Смена — 220 мс.
 */
@Composable
fun CategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    count: Int? = null,
    dot: Color? = null,
    star: Boolean = false,
) {
    val haptics = rememberHaptics()
    val bg by animateColorAsState(if (selected) RvColors.ChipActiveBg else RvColors.ChipBg, tween(AnimationTokens.Category, easing = AnimationTokens.Move), label = "chipBg")
    val fg by animateColorAsState(if (selected) RvColors.ChipActiveText else RvColors.ChipText, tween(AnimationTokens.Category, easing = AnimationTokens.Move), label = "chipFg")
    val border by animateColorAsState(if (selected) Color.Transparent else RvColors.ChipBorder, tween(AnimationTokens.Category), label = "chipBorder")
    val s by animateFloatAsState(if (selected) 1.02f else 1f, tween(AnimationTokens.Category, easing = AnimationTokens.Move), label = "chipScale")
    Row(
        Modifier.height(u(PeopleDims.ChipHeight))
            .graphicsLayer { scaleX = s; scaleY = s }
            .clip(CircleShape).background(bg).border(1.dp, border, CircleShape)
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Tab) { haptics.tick(); onClick() }
            .semantics { this.selected = selected }
            .padding(start = u(PeopleDims.ChipPad), end = if (count != null) u(8) else u(PeopleDims.ChipPad)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            Box(Modifier.size(u(PeopleDims.ChipDot)).clip(CircleShape).background(dot))
            Spacer(Modifier.width(u(10)))
        }
        if (star) {
            Icon(if (selected) Icons.Default.Star else Icons.Outlined.StarOutline, null, tint = fg, modifier = Modifier.size(u(26)))
            Spacer(Modifier.width(u(8)))
        }
        Text(label, style = PeopleType.category, color = fg, maxLines = 1)
        if (count != null) {
            Spacer(Modifier.width(u(10)))
            Box(
                Modifier.clip(CircleShape).background(if (selected) Color(0x14000000) else Color(0x14FFFFFF))
                    .padding(horizontal = u(12), vertical = u(3)),
            ) { Text("$count", style = PeopleType.category, color = fg.copy(alpha = 0.8f)) }
        }
    }
}
