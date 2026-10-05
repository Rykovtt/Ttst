package com.kartoteka.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.kartoteka.app.ui.animations.pressScale
import com.kartoteka.app.ui.theme.PeopleDims
import com.kartoteka.app.ui.theme.RvColors
import com.kartoteka.app.ui.theme.rememberHaptics
import com.kartoteka.app.ui.theme.u

/** Круглая кнопка действия 52 (ед.): фон #EDEAE6, иконка 23 #161619; при нажатии 0,92 и темнеет. */
@Composable
fun ContactActionButton(icon: ImageVector, description: String, enabled: Boolean = true, dark: Boolean = false, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val base = if (dark) Color(0xFF1F1F22) else RvColors.ActionBg
    val bg by animateColorAsState(if (pressed) base.copy(red = base.red * 0.94f, green = base.green * 0.94f, blue = base.blue * 0.94f) else base, tween(120), label = "actionBg")
    Box(
        Modifier.size(u(PeopleDims.Action)).pressScale(src, 0.92f)
            .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
            .clip(CircleShape).background(bg)
            .then(if (enabled) Modifier.clickable(src, indication = null, role = Role.Button) { haptics.tick(); onClick() } else Modifier)
            .semantics { contentDescription = description; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (dark) Color(0xFFEDEAE5) else RvColors.ActionIcon, modifier = Modifier.size(u(PeopleDims.ActionIcon)))
    }
}
