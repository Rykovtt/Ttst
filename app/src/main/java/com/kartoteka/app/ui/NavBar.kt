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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.border
import com.kartoteka.app.i18n.t
import com.kartoteka.app.ui.components.pressable
import com.kartoteka.app.ui.theme.Motion
import com.kartoteka.app.ui.theme.Rv
import com.kartoteka.app.ui.theme.motion
import com.kartoteka.app.ui.theme.rememberHaptics

data class NavItem(val key: String, val title: String, val icon: ImageVector, val badge: Boolean = false)

/**
 * Нижняя навигация RVAULT: тёмная панель, выбранный раздел — на светлой подложке с точкой,
 * в центре — кремовая кнопка быстрых действий со свечением.
 */
@Composable
fun RvNavBar(items: List<NavItem>, selected: String?, quickOpen: Boolean, onSelect: (NavItem) -> Unit, onQuick: () -> Unit) {
    val haptics = rememberHaptics()
    val barShape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp)
    Box(Modifier.fillMaxWidth()) {
        Box(
            Modifier.matchParentSize()
                .shadow(20.dp, barShape, ambientColor = Color.Black.copy(alpha = 0.4f), spotColor = Color.Black.copy(alpha = 0.3f))
                .clip(barShape)
                .background(Brush.verticalGradient(listOf(Color(0xFF1E1D1F), Color(0xFF111112))))
        )
        BoxWithConstraints(Modifier.fillMaxWidth().navigationBarsPadding().height(72.dp)) {
            // Кнопка строго по центру: слева два раздела, справа — остальные; каждая половина делится поровну.
            val gap = 62.dp
            val half = (maxWidth - gap) / 2
            val leftCount = 2
            val rightCount = (items.size - leftCount).coerceAtLeast(1)
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                items.forEachIndexed { i, item ->
                    if (i == leftCount) Spacer(Modifier.width(gap))
                    val slot = if (i < leftCount) half / leftCount else half / rightCount
                    val sel = item.key == selected
                    val fg by animateColorAsState(if (sel) Rv.HeroText else Color(0xFF9B968F), motion(Motion.MICRO), label = "navfg")
                    val bg by animateColorAsState(if (sel) Color.White.copy(alpha = 0.08f) else Color.Transparent, motion(Motion.STANDARD), label = "navbg")
                    Column(
                        Modifier.width(slot).padding(horizontal = 1.dp).height(60.dp).clip(RoundedCornerShape(18.dp)).background(bg)
                            .then(if (sel) Modifier.border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp)) else Modifier)
                            .semantics { role = Role.Tab; this.selected = sel }
                            .clickable(remember { MutableInteractionSource() }, indication = null) {
                                if (!sel) haptics.tick()
                                onSelect(item)
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box {
                            Icon(item.icon, null, tint = if (sel) Rv.Peach else fg, modifier = Modifier.size(22.dp))
                            if (item.badge) {
                                Box(Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-2).dp).size(8.dp).clip(CircleShape).background(Rv.PeachDeep))
                            }
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            t(item.title), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = (-0.2).sp), color = fg, maxLines = 1, softWrap = false,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                        )
                        Box(Modifier.padding(top = 3.dp).size(4.dp).clip(CircleShape).background(if (sel) Rv.HeroText else Color.Transparent))
                    }
                }
            }
            // Центральная кнопка со свечением.
            val rot by animateFloatAsState(if (quickOpen) 45f else 0f, motion(Motion.STANDARD), label = "plus")
            Box(
                Modifier.align(Alignment.TopCenter).offset(y = (-18).dp).size(80.dp)
                    .background(Brush.radialGradient(listOf(Rv.Peach.copy(alpha = 0.45f), Color.Transparent)), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape)
                        .background(Brush.radialGradient(listOf(Color(0xFFFFF4EA), Color(0xFFF3D2B6))))
                        .border(1.5.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                        .pressable(haptic = false) { haptics.heavy(); onQuick() }
                        .semantics { role = Role.Button },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Add, t("Быстрые действия"), tint = Rv.Ink, modifier = Modifier.size(28.dp).rotate(rot))
                }
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
