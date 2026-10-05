package com.kartoteka.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import com.kartoteka.app.ui.theme.AnimationTokens
import com.kartoteka.app.ui.theme.PeopleDims
import com.kartoteka.app.ui.theme.PeopleType
import com.kartoteka.app.ui.theme.RvColors
import com.kartoteka.app.ui.theme.u
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Логотип RVAULT: Manrope Light 34 sp и точка 7 (ед.) цвета #C2A98D. При первом показе проявляется. */
@Composable
fun HeaderLogo(title: String, intro: Boolean, modifier: Modifier = Modifier) {
    val alpha = remember { Animatable(if (intro) 0f else 1f) }
    val dot = remember { Animatable(if (intro) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (intro) {
            launch { alpha.animateTo(1f, tween(400, easing = AnimationTokens.Enter)) }
            delay(150)
            dot.snapTo(0.6f)
            dot.animateTo(1f, tween(260, easing = AnimationTokens.Move))
        }
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            title.uppercase(), style = PeopleType.logo, color = RvColors.Logo, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).graphicsLayer { this.alpha = alpha.value },
        )
        Box(
            Modifier.padding(start = u(8)).size(u(PeopleDims.LogoDot))
                .graphicsLayer { scaleX = dot.value; scaleY = dot.value; this.alpha = dot.value.coerceIn(0f, 1f) }
                .clip(CircleShape).background(RvColors.LogoDot)
        )
    }
}
