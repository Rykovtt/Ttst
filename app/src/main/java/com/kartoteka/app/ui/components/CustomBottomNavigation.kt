package com.kartoteka.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kartoteka.app.i18n.t
import com.kartoteka.app.ui.theme.AnimationTokens
import com.kartoteka.app.ui.theme.PeopleDims
import com.kartoteka.app.ui.theme.PeopleShapes
import com.kartoteka.app.ui.theme.PeopleType
import com.kartoteka.app.ui.theme.Rv
import com.kartoteka.app.ui.theme.RvColors
import com.kartoteka.app.ui.theme.rememberHaptics
import com.kartoteka.app.ui.theme.u
import kotlin.random.Random

data class NavItem(
    val key: String,
    val title: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
    val badge: Boolean = false,
)

/** Насколько центр кнопки «+» ниже верхнего края панели: половина высоты панели минус подъём 16 (ед.). */
@Composable
fun fabAnchorBelowBarTop(): Dp = u(PeopleDims.NavHeight / 2 - PeopleDims.FabLift)

/** Едва заметная матовая текстура панели (шум 3 %), один раз на процесс. */
private val noise: ImageBitmap by lazy {
    val n = 96
    val r = Random(7)
    val px = IntArray(n * n) { val v = r.nextInt(256); (8 shl 24) or (v shl 16) or (v shl 8) or v }
    android.graphics.Bitmap.createBitmap(px, n, n, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}

/**
 * Нижняя навигация (ТЗ, раздел 6): тёмная матовая панель #111113 высотой 112 (ед.), скругление 40,
 * внешняя тень вверх; слева два раздела, справа три, по центру — кнопка «+» с подъёмом 16.
 */
@Composable
fun CustomBottomNavigation(items: List<NavItem>, selected: String?, menuOpen: Boolean, onSelect: (NavItem) -> Unit, onFab: () -> Unit) {
    val haptics = rememberHaptics()
    val shape = PeopleShapes.nav()
    Box(Modifier.fillMaxWidth()) {
        Box(
            Modifier.matchParentSize()
                .drawBehind {
                    // Тень 0 −8 30 #00000025 над панелью.
                    val h = 30.dp.toPx()
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, RvColors.NavShadow), startY = -h - 8.dp.toPx(), endY = 0f), topLeft = Offset(0f, -h - 8.dp.toPx()), size = size.copy(height = h + 8.dp.toPx()))
                }
                .clip(shape).background(RvColors.NavBg)
                .drawBehind { drawRect(ShaderBrush(ImageShader(noise, TileMode.Repeated, TileMode.Repeated))) }
        )
        BoxWithConstraints(Modifier.fillMaxWidth().navigationBarsPadding().height(u(PeopleDims.NavHeight)).padding(horizontal = u(PeopleDims.NavPad))) {
            val gap = u(PeopleDims.Fab) + 8.dp // кнопка «+» и немного воздуха; свечение может заходить на соседей
            val half = (maxWidth - gap) / 2
            val leftCount = 2
            val rightCount = (items.size - leftCount).coerceAtLeast(1)
            val labelSize = rememberSharedTextSize(PeopleType.nav.fontSize.value)
            // Ширина ячейки — по длине подписи (как в макете): «Налаштування» шире, чем «Карта».
            // Лишнее место делится поровну, нехватка — пропорционально; общий кегль подписей ужимается FitText.
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val titles = items.map { t(it.title) }
            val natural = remember(titles, density) {
                titles.map { title ->
                    val px = measurer.measure(title, PeopleType.nav.copy(fontWeight = FontWeight.SemiBold)).size.width
                    with(density) { px.toDp() } + 10.dp
                }.map { maxOf(it, 44.dp) }
            }
            fun slots(range: IntRange): List<androidx.compose.ui.unit.Dp> {
                val ws = range.map { natural[it] }
                val sum = ws.fold(0.dp) { acc, w -> acc + w }
                return if (sum <= half) ws.map { it + (half - sum) / ws.size } else ws.map { it * (half / sum) }
            }
            val widths = slots(0 until leftCount) + slots(leftCount until items.size)
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                items.forEachIndexed { i, item ->
                    if (i == leftCount) Spacer(Modifier.width(gap))
                    val slot = widths[i]
                    NavCell(item, item.key == selected, Modifier.width(slot), labelSize) {
                        if (item.key != selected) haptics.tick()
                        onSelect(item)
                    }
                }
            }
            FloatingAddButton(
                open = menuOpen, description = t("Быстрые действия"), openState = t("меню открыто"), closedState = t("меню закрыто"),
                onClick = onFab,
                modifier = Modifier.align(Alignment.Center).offset(y = -u(PeopleDims.FabLift)),
            )
        }
    }
}

@Composable
private fun NavCell(item: NavItem, sel: Boolean, modifier: Modifier, labelSize: androidx.compose.runtime.MutableFloatState, onClick: () -> Unit) {
    val spec = tween<Color>(AnimationTokens.TabSwitch, easing = AnimationTokens.Move)
    val fg by animateColorAsState(if (sel) RvColors.NavActive else RvColors.NavInactive, spec, label = "navFg")
    val pill by animateFloatAsState(if (sel) 1f else 0f, tween(AnimationTokens.TabSwitch, easing = AnimationTokens.Enter), label = "navPill")
    val dot by animateFloatAsState(if (sel) 1f else 0f, tween(AnimationTokens.TabSwitch, easing = AnimationTokens.Move), label = "navDot")
    val pillW = u(PeopleDims.NavPillW); val pillH = u(PeopleDims.NavPillH); val radius = u(PeopleDims.NavPillRadius)
    Box(
        modifier.height(pillH)
            .semantics { role = Role.Tab; this.selected = sel }
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Подложка активной вкладки 84×90, #FFFFFF0D, обводка #FFFFFF12.
        Box(
            Modifier.size(pillW, pillH).drawBehind {
                if (pill > 0f) {
                    val cr = CornerRadius(radius.toPx())
                    drawRoundRect(RvColors.NavPill.copy(alpha = RvColors.NavPill.alpha * pill), cornerRadius = cr)
                    drawRoundRect(RvColors.NavPillBorder.copy(alpha = RvColors.NavPillBorder.alpha * pill), cornerRadius = cr, style = Stroke(1.dp.toPx()))
                }
            }
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box {
                Icon(if (sel) item.selectedIcon else item.icon, null, tint = fg, modifier = Modifier.size(maxOf(u(PeopleDims.NavIcon), 18.dp)))
                if (item.badge) {
                    Box(Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-2).dp).size(6.dp).clip(CircleShape).background(Rv.PeachDeep))
                }
            }
            Spacer(Modifier.height(u(PeopleDims.NavIconGap)))
            FitText(t(item.title), PeopleType.nav.copy(fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium), fg, minSp = 8f, align = TextAlign.Center, shared = labelSize, modifier = Modifier.padding(horizontal = 2.dp))
            Box(
                Modifier.padding(top = u(6)).size(maxOf(u(PeopleDims.NavDot), 3.5.dp)).graphicsLayer { scaleX = dot; scaleY = dot }
                    .clip(CircleShape).background(RvColors.NavDot)
            )
        }
    }
}
