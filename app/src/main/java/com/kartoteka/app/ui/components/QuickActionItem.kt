package com.kartoteka.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kartoteka.app.ui.animations.pressScale
import com.kartoteka.app.ui.theme.PeopleDims
import com.kartoteka.app.ui.theme.PeopleShapes
import com.kartoteka.app.ui.theme.PeopleType
import com.kartoteka.app.ui.theme.RvColors
import com.kartoteka.app.ui.theme.rememberHaptics
import com.kartoteka.app.ui.theme.u
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Ширина колонки быстрого действия — круг плюс зазор, чтобы подписи не наезжали. */
@Composable
private fun slot() = u(PeopleDims.Circle + PeopleDims.QuickGap)

/** «Добавить человека»: вертикальная стеклянная карточка 102×160, скругление 34, обводка #FFFFFF45, тень. */
@Composable
fun AddContactTile(label: String, onClick: () -> Unit, shared: androidx.compose.runtime.MutableFloatState? = null) {
    val src = remember { MutableInteractionSource() }
    val haptics = rememberHaptics()
    val shape = PeopleShapes.addTile()
    Column(
        Modifier.width(u(PeopleDims.AddTileW)).height(u(PeopleDims.AddTileH))
            .pressScale(src)
            .drawBehind {
                // Тень 0 8 24 #00000035.
                drawRoundRect(
                    Brush.verticalGradient(listOf(Color(0x35000000), Color.Transparent), startY = size.height * 0.6f, endY = size.height + 24.dp.toPx()),
                    topLeft = Offset(0f, 8.dp.toPx()), size = size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.33f),
                )
            }
            .clip(shape).background(RvColors.AddTileBg).border(1.dp, RvColors.AddTileBorder, shape)
            .clickable(src, indication = null, role = Role.Button) { haptics.tick(); onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PlusGlyph(Modifier.size(u(PeopleDims.AddPlus)), Color.White, u(3))
        Spacer(Modifier.height(u(14)))
        FitText(label, PeopleType.quickAction, Color.White, minSp = 8f, align = TextAlign.Center, modifier = Modifier.padding(horizontal = u(6)), shared = shared)
    }
}

/** Круглое фото с обводкой и цветным индикатором; подпись и число под ним. */
@Composable
fun PhotoQuickAction(
    label: String,
    value: String?,
    ring: Color,
    dot: Color?,
    onClick: () -> Unit,
    shared: androidx.compose.runtime.MutableFloatState? = null,
    content: @Composable () -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    Column(
        Modifier.width(slot()).pressScale(src).clickable(src, indication = null, role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(u(PeopleDims.Circle))) {
            Box(
                Modifier.fillMaxSize().clip(CircleShape).border(u(PeopleDims.CircleRing), ring, CircleShape)
                    .padding(u(PeopleDims.CircleRing)).clip(CircleShape).background(Color(0xFF1B1B1E)),
                contentAlignment = Alignment.Center,
            ) { content() }
            if (dot != null) {
                Box(
                    Modifier.align(Alignment.BottomEnd).offset(x = -u(6), y = -u(8)).size(u(PeopleDims.CircleDot + 6))
                        .clip(CircleShape).background(RvColors.HeaderBottom).padding(u(3)).clip(CircleShape).background(dot)
                )
            }
        }
        Spacer(Modifier.height(u(10)))
        FitText(label, PeopleType.quickAction, Color.White, minSp = 8f, align = TextAlign.Center, shared = shared)
        if (value != null) Text(value, style = PeopleType.quickAction.copy(fontSize = (shared?.floatValue ?: PeopleType.quickAction.fontSize.value).sp), color = Color.White.copy(alpha = 0.85f))
    }
}

/** «Ассистент Ноа»: скруглённый квадрат #0C0C11 с обводкой #D5B89B и светящейся сферой. */
@Composable
fun NoaQuickAction(label: String, onClick: () -> Unit, shared: androidx.compose.runtime.MutableFloatState? = null) {
    val src = remember { MutableInteractionSource() }
    val scope = rememberCoroutineScope()
    val ring = remember { Animatable(1f) }
    var state by remember { mutableStateOf(OrbState.IDLE) }
    val shape = PeopleShapes.noa()
    Column(
        Modifier.width(slot()).pressScale(src).clickable(src, indication = null, role = Role.Button) {
            scope.launch {
                state = OrbState.SUCCESS
                ring.snapTo(0f); ring.animateTo(1f, tween(280))
                onClick()
                delay(300); state = OrbState.IDLE
            }
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(u(PeopleDims.Circle)).clip(shape).background(RvColors.NoaBg).border(1.dp, RvColors.NoaBorder, shape)
                .drawBehind {
                    val p = ring.value
                    if (p < 1f) drawCircle(RvColors.NoaBorder.copy(alpha = 0.6f * (1f - p)), size.minDimension * (0.3f + 0.45f * p), style = Stroke(1.4.dp.toPx()))
                },
            contentAlignment = Alignment.Center,
        ) {
            // Внешнее свечение сферы ~18 (ед.).
            NoaOrb(Modifier.size(u(PeopleDims.Circle - 24)).graphicsLayer { val s = 1f + 0.12f * (1f - ring.value).let { if (ring.value < 1f) it else 0f }; scaleX = s; scaleY = s }, state)
        }
        Spacer(Modifier.height(u(10)))
        FitText(label, PeopleType.quickAction, Color.White, minSp = 8f, align = TextAlign.Center, shared = shared)
    }
}

/** Плюс из двух линий с круглыми концами. */
@Composable
fun PlusGlyph(modifier: Modifier, color: Color, stroke: androidx.compose.ui.unit.Dp) {
    Canvas(modifier) {
        val st = stroke.toPx()
        drawLine(color, Offset(size.width / 2, size.height * 0.08f), Offset(size.width / 2, size.height * 0.92f), st, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.08f, size.height / 2), Offset(size.width * 0.92f, size.height / 2), st, StrokeCap.Round)
    }
}
