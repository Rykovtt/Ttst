package com.kartoteka.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kartoteka.app.i18n.t
import com.kartoteka.app.ui.components.SheenColors
import com.kartoteka.app.ui.components.sheenBorder
import com.kartoteka.app.ui.theme.LocalReducedMotion
import com.kartoteka.app.ui.theme.Motion
import com.kartoteka.app.ui.theme.Rv
import com.kartoteka.app.ui.theme.motion
import com.kartoteka.app.ui.theme.rememberHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

data class NavItem(
    val key: String,
    val title: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
    val badge: Boolean = false,
)

/** Насколько центр кнопки «+» опущен ниже верхнего края нижней панели — от него раскрывается меню. */
val QuickFabCenterBelowBarTop: Dp = 18.dp
private val FabTouch = 84.dp
private val FabFace = 76.dp

private val BarText = Color(0xFFA19C95)

/**
 * Нижняя навигация RVAULT: тёмная панель со стеклянной кромкой; слева два раздела, справа три,
 * кнопка «+» — строго по центру. Выбранный раздел — на стеклянной подложке, с точкой под подписью.
 */
@Composable
fun RvNavBar(items: List<NavItem>, selected: String?, quickOpen: Boolean, onSelect: (NavItem) -> Unit, onQuick: () -> Unit) {
    val haptics = rememberHaptics()
    val barShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    Box(Modifier.fillMaxWidth()) {
        Box(
            Modifier.matchParentSize()
                .clip(barShape)
                .background(Brush.verticalGradient(listOf(Color(0xFF1F1E20), Color(0xFF121213))))
                .sheenBorder(1.dp, barShape, SheenColors.Glass.map { it.copy(alpha = it.alpha * 0.6f) })
        )
        BoxWithConstraints(Modifier.fillMaxWidth().navigationBarsPadding().height(74.dp).padding(horizontal = 10.dp)) {
            val gap = FabTouch + 8.dp
            val half = (maxWidth - gap) / 2
            val leftCount = 2
            val rightCount = (items.size - leftCount).coerceAtLeast(1)
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                items.forEachIndexed { i, item ->
                    if (i == leftCount) Spacer(Modifier.width(gap))
                    val slot = if (i < leftCount) half / leftCount else half / rightCount
                    NavCell(item, item.key == selected, Modifier.width(slot)) {
                        if (item.key != selected) haptics.tick()
                        onSelect(item)
                    }
                }
            }
            QuickFab(
                open = quickOpen,
                onToggle = onQuick,
                modifier = Modifier.align(Alignment.TopCenter).offset(y = QuickFabCenterBelowBarTop - FabTouch / 2),
            )
        }
    }
}

@Composable
private fun NavCell(item: NavItem, sel: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val fg by animateColorAsState(if (sel) Rv.HeroText else BarText, motion(Motion.MICRO), label = "navfg")
    val bgA by animateFloatAsState(if (sel) 1f else 0f, motion(Motion.STANDARD), label = "navbg")
    val cell = RoundedCornerShape(20.dp)
    Box(modifier.padding(horizontal = 2.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().height(60.dp)
                .graphicsLayer { }
                .clip(cell)
                .drawBehind { if (bgA > 0f) drawRect(Color.White.copy(alpha = 0.07f * bgA)) }
                .then(if (sel) Modifier.sheenBorder(1.dp, cell, SheenColors.Glass) else Modifier)
                .semantics { role = Role.Tab; this.selected = sel }
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box {
                Icon(if (sel) item.selectedIcon else item.icon, null, tint = if (sel) Rv.Peach else fg, modifier = Modifier.size(23.dp))
                if (item.badge) {
                    Box(Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-2).dp).size(7.dp).clip(CircleShape).background(Rv.PeachDeep))
                }
            }
            Spacer(Modifier.height(3.dp))
            AutoSizeText(
                t(item.title), color = fg, maxSize = 11.sp, minSize = 8.sp,
                weight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
            )
            Box(Modifier.padding(top = 3.dp).size(4.dp).clip(CircleShape).background(if (sel) Rv.HeroText else Color.Transparent))
        }
    }
}

/** Подпись в одну строку, которая уменьшается, пока не поместится (длинные украинские слова). */
@Composable
fun AutoSizeText(text: String, color: Color, maxSize: TextUnit, minSize: TextUnit, weight: FontWeight = FontWeight.Normal) {
    var size by remember(text) { mutableStateOf(maxSize) }
    var ready by remember(text) { mutableStateOf(false) }
    Text(
        text, color = color, maxLines = 1, softWrap = false, textAlign = TextAlign.Center,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = size, fontWeight = weight, letterSpacing = (-0.1).sp),
        onTextLayout = { r ->
            if (r.didOverflowWidth && size.value > minSize.value) size = (size.value * 0.92f).sp else ready = true
        },
        modifier = Modifier.drawWithContent { if (ready) drawContent() },
    )
}

/**
 * Центральная кнопка «+» RVAULT.
 * Покой: почти незаметное «дыхание» 1–1,025 и мягкое свечение; через 25 с без касаний — полностью статична.
 * Касание: сжатие до 0,91 за 100 мс, свечение гаснет. Отпускание: упругий возврат с короткой вспышкой.
 * Открытие: кнопка чуть растёт, «+» поворачивается в «×», изнутри расходится тонкое световое кольцо,
 * оттенок становится теплее. Все значения читаются в фазе рисования — панель не пересчитывается.
 */
@Composable
fun QuickFab(open: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val haptics = rememberHaptics()
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()

    // Покой: дышит, пока недавно было взаимодействие; потом затихает.
    var touches by remember { mutableIntStateOf(0) }
    var idle by remember { mutableStateOf(true) }
    LaunchedEffect(touches, open) {
        idle = !open
        if (!open) { delay(25_000); idle = false }
    }
    val breath = if (reduced) null else rememberInfiniteTransition(label = "fabIdle")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breath")
    val idleAmt by animateFloatAsState(if (idle && !reduced) 1f else 0f, tween(700), label = "idleAmt")

    val scale = remember { Animatable(1f) }
    val glow = remember { Animatable(0.32f) }
    var wasPressed by remember { mutableStateOf(false) }
    LaunchedEffect(pressed, open) {
        if (pressed) {
            wasPressed = true
            launch { glow.animateTo(0.10f, tween(100)) }
            scale.animateTo(0.91f, tween(100, easing = FastOutSlowInEasing))
        } else {
            val target = if (open) 1.06f else 1f
            if (wasPressed) {
                wasPressed = false
                launch { glow.animateTo(0.8f, tween(140)); glow.animateTo(0.32f, tween(460)) }
            }
            scale.animateTo(target, if (reduced) snap() else spring(dampingRatio = 0.58f, stiffness = 520f))
        }
    }
    val rot by animateFloatAsState(
        if (open) 45f else 0f,
        if (reduced) snap() else if (open) tween(340, easing = Motion.Ease) else tween(220, easing = Motion.Ease),
        label = "plusRot",
    )
    val warm by animateFloatAsState(if (open) 1f else 0f, tween(if (open) 340 else 220), label = "warm")
    val ring = remember { Animatable(1f) }
    LaunchedEffect(open) {
        if (open && !reduced) { ring.snapTo(0f); ring.animateTo(1f, tween(620, easing = LinearOutSlowInEasing)) }
    }

    val ringColor = Color(0xFFF6DCC2)
    Box(
        modifier.size(FabTouch)
            .drawBehind {
                val r = FabFace.toPx() / 2
                val s = scale.value * (1f + 0.025f * (breath?.value ?: 0f) * idleAmt)
                // Мягкая объёмная тень.
                val sh = center + Offset(0f, 5.dp.toPx())
                drawCircle(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.55f * s.coerceAtMost(1f)), Color.Transparent), sh, r * 1.35f * s), r * 1.35f * s, sh)
                // Деликатное тёплое свечение.
                val g = (glow.value + 0.08f * (breath?.value ?: 0f) * idleAmt).coerceIn(0f, 1f)
                drawCircle(Brush.radialGradient(listOf(Rv.Peach.copy(alpha = 0.55f * g), Color.Transparent), center, r * 1.75f), r * 1.75f, center)
                // Световое кольцо при раскрытии.
                val p = ring.value
                if (p < 1f) {
                    val rr = r * 0.55f + (r * 4.2f) * p
                    drawCircle(ringColor.copy(alpha = 0.55f * (1f - p)), rr, center, style = Stroke(1.4.dp.toPx()))
                }
            }
            .semantics {
                contentDescription = t("Быстрые действия")
                stateDescription = if (open) t("меню открыто") else t("меню закрыто")
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(FabFace)
                .graphicsLayer {
                    val s = scale.value * (1f + 0.025f * (breath?.value ?: 0f) * idleAmt)
                    scaleX = s; scaleY = s
                }
                .clip(CircleShape)
                .drawBehind {
                    val edge = androidx.compose.ui.graphics.lerp(Color(0xFFEFD8C3), Color(0xFFF2C29E), warm)
                    drawRect(Brush.radialGradient(listOf(Color(0xFFFFF9F2), Color(0xFFF8EADC), edge), center - Offset(size.width * 0.12f, size.height * 0.16f), size.width * 0.75f))
                }
                .sheenBorder(1.dp, CircleShape, listOf(Color.White, Color(0xFFE9CDB2), Color(0xFFB8957A), Color(0xFFFFF3E6)))
                .clickable(source, indication = null) {
                    touches++
                    if (!open) haptics.confirm() else haptics.tick()
                    onToggle()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Add, null, tint = Color(0xFF1B1A19),
                modifier = Modifier.size(32.dp).graphicsLayer { rotationZ = rot },
            )
        }
    }
}

/** [title] — короткая подпись под кнопкой; [description] — полное название для экранного диктора. */
data class QuickAction(val title: String, val icon: ImageVector, val tint: Color, val description: String = title, val action: () -> Unit)

/** Мягкое «выстреливание» с едва заметной упругостью. */
private val SoftBack = CubicBezierEasing(0.3f, 1.35f, 0.6f, 1f)

/**
 * Меню быстрых действий: пункты рождаются из кнопки «+» и раскрываются дугой над ней
 * (на узких экранах — столбиком), по очереди, с подписями под каждым.
 * Закрытие — обратное движение: подписи гаснут, кнопки возвращаются к центру и растворяются.
 * [anchorBelow] — насколько центр кнопки «+» ниже нижнего края этой области.
 */
@Composable
fun QuickActionsOverlay(open: Boolean, actions: List<QuickAction>, anchorBelow: Dp, onDismiss: () -> Unit) {
    val reduced = LocalReducedMotion.current
    val transition = updateTransition(open, label = "quick")
    if (!transition.currentState && !transition.targetState) return
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val n = actions.size

    val scrim by transition.animateFloat({ if (targetState) tween(280) else tween(220, delayMillis = 60) }, label = "scrim") { if (it) 1f else 0f }
    val progress = actions.indices.map { i ->
        transition.animateFloat(
            {
                if (reduced) tween(150)
                else if (targetState) tween(360, delayMillis = 60 + i * 55, easing = SoftBack)
                else tween(200, delayMillis = 40 + (n - 1 - i) * 25, easing = Motion.Ease)
            },
            label = "item$i",
        ) { if (it) 1f else 0f }
    }
    val labels = actions.indices.map { i ->
        transition.animateFloat(
            { if (targetState) tween(200, delayMillis = 240 + i * 55) else tween(80) },
            label = "label$i",
        ) { if (it) 1f else 0f }
    }

    BoxWithConstraints(
        Modifier.fillMaxSize()
            .drawBehind { drawRect(Color.Black.copy(alpha = 0.62f * scrim)) }
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    ) {
        val radial = maxWidth >= 360.dp
        val anchor = with(density) { Offset(maxWidth.toPx() / 2, (maxHeight + anchorBelow).toPx()) }
        // Целевые смещения пунктов относительно центра кнопки.
        val targets: List<Offset> = with(density) {
            if (radial) {
                // Дуга-эллипс, приподнятая над кнопкой, чтобы подписи крайних пунктов не уходили под панель.
                val rx = 146.dp.toPx(); val ry = 146.dp.toPx(); val lift = 58.dp.toPx()
                val angles = when (n) { 1 -> listOf(90.0); 2 -> listOf(130.0, 50.0); 3 -> listOf(155.0, 90.0, 25.0); else -> listOf(160.0, 118.0, 62.0, 20.0) }
                angles.map { a -> val rad = Math.toRadians(a); Offset((cos(rad) * rx).toFloat(), (-sin(rad) * ry).toFloat() - lift) }
            } else {
                actions.indices.map { i -> Offset(0f, -(110.dp.toPx() + i * 92.dp.toPx())) }
            }
        }

        // Тонкие световые траектории: видны в момент раскрытия и растворяются.
        Canvas(Modifier.fillMaxSize()) {
            if (reduced) return@Canvas
            targets.forEachIndexed { i, t ->
                val p = progress[i].value
                val a = 0.32f * p * (1f - labels[i].value)
                if (a > 0.01f) {
                    drawLine(
                        Brush.linearGradient(listOf(Color(0xFFF6DCC2).copy(alpha = a), Color.Transparent), anchor, anchor + t),
                        anchor, anchor + t * p, strokeWidth = 1.2.dp.toPx(), cap = StrokeCap.Round,
                    )
                }
            }
        }

        actions.forEachIndexed { i, qa ->
            val itemSize = 58.dp
            val half = with(density) { (itemSize / 2).toPx() }
            Column(
                Modifier
                    .offset {
                        val p = if (reduced) 1f else progress[i].value
                        val pos = anchor + targets[i] * p
                        IntOffset((pos.x - with(density) { 56.dp.toPx() }).roundToInt(), (pos.y - half).roundToInt())
                    }
                    .width(112.dp)
                    .graphicsLayer {
                        val p = progress[i].value
                        alpha = p.coerceIn(0f, 1f)
                        val s = if (reduced) 1f else 0.35f + 0.65f * p
                        scaleX = s; scaleY = s
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, half / size.height)
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                QuickActionButton(qa, itemSize) {
                    haptics.tick()
                    onDismiss()
                    scope.launch { delay(140); qa.action() }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    qa.title, color = Color.White, textAlign = TextAlign.Center, maxLines = 1,
                    style = TextStyle(fontFamily = com.kartoteka.app.ui.theme.Inter, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 14.sp),
                    modifier = Modifier.graphicsLayer { alpha = labels[i].value; translationY = (1f - labels[i].value) * 6.dp.toPx() }
                        .clip(RoundedCornerShape(10.dp)).background(Color(0xCC151413)).padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun QuickActionButton(qa: QuickAction, size: Dp, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.94f else 1f, tween(Motion.MICRO), label = "qaScale")
    val dim by animateFloatAsState(if (pressed) 1f else 0f, tween(Motion.MICRO), label = "qaDim")
    val iconPop = remember { Animatable(1f) }
    LaunchedEffect(pressed) { if (pressed) { iconPop.animateTo(1.18f, tween(90)); iconPop.animateTo(1f, spring(0.5f, 700f)) } }
    Box(
        Modifier.size(size)
            .graphicsLayer { scaleX = s; scaleY = s; shadowElevation = 10f; shape = CircleShape; clip = true }
            .background(Brush.radialGradient(listOf(Color(0xFFFFFBF6), Color(0xFFF3E7DA))))
            .drawBehind { if (dim > 0f) drawRect(Color.Black.copy(alpha = 0.08f * dim)) }
            .sheenBorder(1.dp, CircleShape, listOf(Color.White, Color(0xFFE6CDB6), Color(0xFFC9AE96), Color.White))
            .clickable(source, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(qa.icon, qa.description, tint = qa.tint, modifier = Modifier.size(24.dp).graphicsLayer { scaleX = iconPop.value; scaleY = iconPop.value })
    }
}

object QuickIcons {
    val person = Icons.Outlined.PersonAddAlt
    val appointment = Icons.Outlined.EventAvailable
    val note = Icons.Outlined.EditNote
    val reminder = Icons.Outlined.NotificationsActive
    val noa = Icons.Outlined.GraphicEq
    val import = Icons.Outlined.Contacts
}
