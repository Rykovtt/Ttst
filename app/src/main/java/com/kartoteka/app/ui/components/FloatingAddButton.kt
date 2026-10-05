package com.kartoteka.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.kartoteka.app.ui.theme.AnimationTokens
import com.kartoteka.app.ui.theme.LocalReducedMotion
import com.kartoteka.app.ui.theme.PeopleDims
import com.kartoteka.app.ui.theme.RvColors
import com.kartoteka.app.ui.theme.rememberHaptics
import com.kartoteka.app.ui.theme.u
import kotlinx.coroutines.launch

/**
 * Центральная кнопка «+» (ТЗ, раздел 7): корпус 94, внутренняя кнопка 80, внешнее свечение 116.
 * Ожидание — дышит только свечение (альфа 0,12↔0,25, масштаб 1↔1,08, 1600+1600 мс); корпус неподвижен.
 * Нажатие — корпус 0,90 и иконка 0,85 за 100 мс, вспышка свечения, возврат за 180 мс; «+» поворачивается на 90° за 220 мс.
 */
@Composable
fun FloatingAddButton(open: Boolean, description: String, openState: String, closedState: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val haptics = rememberHaptics()
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()

    val idle = if (reduced) null else rememberInfiniteTransition(label = "fabIdle")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(AnimationTokens.FabIdleHalf, easing = AnimationTokens.Move), RepeatMode.Reverse), label = "fabGlow")
    val body = remember { Animatable(1f) }
    val icon = remember { Animatable(1f) }
    val burst = remember { Animatable(0f) }
    var wasPressed by remember { mutableStateOf(false) }
    LaunchedEffect(pressed) {
        if (pressed) {
            wasPressed = true
            launch { icon.animateTo(0.85f, tween(AnimationTokens.Press, easing = AnimationTokens.Move)) }
            body.animateTo(0.90f, tween(AnimationTokens.Press, easing = AnimationTokens.Move))
        } else if (wasPressed) {
            wasPressed = false
            launch { burst.snapTo(1f); burst.animateTo(0f, tween(420)) }
            launch { icon.animateTo(1f, tween(AnimationTokens.Release, easing = AnimationTokens.Move)) }
            body.animateTo(1f, tween(AnimationTokens.Release, easing = AnimationTokens.Move))
        }
    }
    val rot by animateFloatAsState(if (open) 90f else 0f, tween(AnimationTokens.FabIconTurn, easing = AnimationTokens.Move), label = "fabRot")

    val glowLayer = u(PeopleDims.FabGlowLayer)
    val glowR = u(PeopleDims.FabGlowRadius)
    Box(
        modifier.size(glowLayer)
            .drawBehind {
                val bodyR = size.minDimension * (PeopleDims.Fab / PeopleDims.FabGlowLayer) / 2
                val t = idle?.value ?: 0f
                val alpha = (0.12f + 0.13f * t + 0.25f * burst.value).coerceAtMost(0.5f)
                val gs = 1f + 0.08f * t
                val gr = (bodyR + glowR.toPx()) * gs
                drawCircle(Brush.radialGradient(listOf(RvColors.FabGlow.copy(alpha = alpha), Color.Transparent), center, gr), gr, center)
                // Тень корпуса 0 4 20 #E8B98255.
                val sc = center + Offset(0f, 4.dp.toPx())
                drawCircle(Brush.radialGradient(listOf(RvColors.FabShadow, Color.Transparent), sc, bodyR + 20.dp.toPx() * 0.6f), bodyR + 20.dp.toPx() * 0.6f, sc)
            }
            .semantics {
                contentDescription = description; role = Role.Button
                stateDescription = if (open) openState else closedState
            },
        contentAlignment = Alignment.Center,
    ) {
        // Корпус.
        Canvas(
            Modifier.size(u(PeopleDims.Fab))
                .graphicsLayer { scaleX = body.value; scaleY = body.value }
                .clickable(src, indication = null) { haptics.confirm(); onClick() },
        ) {
            val r = size.minDimension / 2
            drawCircle(Brush.verticalGradient(listOf(RvColors.FabBodyTop, RvColors.FabBodyBottom)), r, center)
            drawCircle(RvColors.FabBorder, r - 0.75.dp.toPx(), center, style = Stroke(1.5.dp.toPx()))
            // Внутренняя кнопка 80.
            val ri = r * PeopleDims.FabInner / PeopleDims.Fab
            drawCircle(Brush.linearGradient(listOf(RvColors.FabInnerTop, RvColors.FabInnerBottom), center - Offset(0f, ri), center + Offset(0f, ri)), ri, center)
        }
        // Плюс 40 с круглыми концами.
        Canvas(Modifier.size(u(PeopleDims.FabIcon)).graphicsLayer { val s = body.value * icon.value; scaleX = s; scaleY = s; rotationZ = rot }) {
            val st = PeopleDims.FabIconStroke.dp.toPx()
            drawLine(RvColors.FabIcon, Offset(size.width / 2, size.height * 0.1f), Offset(size.width / 2, size.height * 0.9f), st, StrokeCap.Round)
            drawLine(RvColors.FabIcon, Offset(size.width * 0.1f, size.height / 2), Offset(size.width * 0.9f, size.height / 2), st, StrokeCap.Round)
        }
    }
}
