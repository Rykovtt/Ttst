package com.kartoteka.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kartoteka.app.i18n.t
import com.kartoteka.app.ui.components.pressable
import com.kartoteka.app.ui.theme.Motion
import com.kartoteka.app.ui.theme.Rv
import com.kartoteka.app.ui.theme.motion
import com.kartoteka.app.ui.theme.rememberHaptics

data class NavItem(val key: String, val title: String, val icon: ImageVector, val badge: Boolean = false)

/**
 * Нижняя навигация RVAULT: четыре раздела и центральная кнопка быстрых действий.
 * Активный раздел подсвечивается «каплей», которая плавно переезжает между пунктами.
 */
@Composable
fun RvNavBar(items: List<NavItem>, selected: String?, quickOpen: Boolean, onSelect: (NavItem) -> Unit, onQuick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val haptics = rememberHaptics()
    // Подложка отдельно от содержимого — чтобы центральная кнопка могла выступать над панелью.
    Box(Modifier.fillMaxWidth()) {
        Box(
            Modifier.matchParentSize()
                .shadow(18.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.18f))
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(c.surfaceContainerLowest)
        )
        BoxWithConstraints(Modifier.fillMaxWidth().navigationBarsPadding().height(66.dp)) {
            val slot = maxWidth / (items.size + 1)
            // Позиция «капли» с учётом центрального слота.
            val idx = items.indexOfFirst { it.key == selected }
            val slotIdx = if (idx < 0) -1 else if (idx >= items.size / 2) idx + 1 else idx
            val x by animateDpAsState(slot * slotIdx.coerceAtLeast(0) + slot / 2 - 14.dp, motion(Motion.STANDARD), label = "drop")
            val a by animateFloatAsState(if (slotIdx >= 0) 1f else 0f, motion(Motion.MICRO), label = "dropA")
            Box(
                Modifier.offset(x = x, y = 6.dp).size(28.dp, 4.dp).clip(CircleShape)
                    .background(Rv.PeachDeep.copy(alpha = a))
            )
            Row(Modifier.fillMaxSize()) {
                items.forEachIndexed { i, item ->
                    if (i == items.size / 2) Spacer(Modifier.width(slot))
                    val sel = item.key == selected
                    val fg by animateColorAsState(if (sel) c.onSurface else c.outline, motion(Motion.MICRO), label = "navfg")
                    Column(
                        Modifier.width(slot).fillMaxSize()
                            .semantics { role = Role.Tab; this.selected = sel }
                            .clickable(remember { MutableInteractionSource() }, indication = null) {
                                if (!sel) haptics.tick()
                                onSelect(item)
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box {
                            Icon(item.icon, null, tint = fg, modifier = Modifier.size(24.dp))
                            if (item.badge) {
                                Box(
                                    Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-2).dp).size(8.dp)
                                        .clip(CircleShape).background(c.surfaceContainerLowest).padding(1.5.dp)
                                        .clip(CircleShape).background(Rv.PeachDeep)
                                )
                            }
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            t(item.title), style = MaterialTheme.typography.labelSmall, color = fg, maxLines = 1, softWrap = false,
                            fontWeight = if (sel) FontWeight.ExtraBold else FontWeight.SemiBold,
                        )
                    }
                }
            }
            // Центральная кнопка.
            val rot by animateFloatAsState(if (quickOpen) 45f else 0f, motion(Motion.STANDARD), label = "plus")
            Box(
                Modifier.align(Alignment.TopCenter).offset(y = (-18).dp).size(58.dp)
                    .shadow(10.dp, CircleShape, spotColor = Rv.PeachDeep.copy(alpha = 0.5f))
                    .clip(CircleShape).background(c.primary)
                    .pressable(haptic = false) { haptics.heavy(); onQuick() }
                    .semantics { role = Role.Button },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Add, t("Быстрые действия"), tint = c.onPrimary, modifier = Modifier.size(28.dp).rotate(rot))
            }
        }
    }
}

data class QuickAction(val title: String, val subtitle: String, val icon: ImageVector, val tint: Color, val action: () -> Unit)

/** Меню быстрых действий: вырастает из центральной кнопки, пункты появляются лесенкой. */
@Composable
fun QuickMenu(visible: Boolean, actions: List<QuickAction>, onDismiss: () -> Unit) {
    AnimatedVisibility(visible, enter = fadeIn(motion(Motion.STANDARD)), exit = fadeOut(motion(Motion.MICRO))) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        ) {
            Column(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 30.dp, start = 28.dp, end = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                actions.forEachIndexed { i, qa ->
                    val delay = (actions.size - 1 - i) * 45
                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn(motion(Motion.STANDARD, delay)) + slideInVertically(motion(Motion.EMPHASIZED, delay)) { it } +
                            scaleIn(motion(Motion.EMPHASIZED, delay), initialScale = 0.85f),
                        exit = fadeOut(motion(Motion.MICRO)) + slideOutVertically(motion(Motion.MICRO)) { it / 2 } + scaleOut(motion(Motion.MICRO), targetScale = 0.9f),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                                .pressable { onDismiss(); qa.action() }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(qa.tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                                Icon(qa.icon, null, tint = qa.tint)
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(qa.title, style = MaterialTheme.typography.titleMedium)
                                Text(qa.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

object QuickIcons {
    val person = Icons.Default.PersonAdd
    val appointment = Icons.Default.EventAvailable
    val noa = Icons.Default.GraphicEq
    val import = Icons.Default.Contacts
}
