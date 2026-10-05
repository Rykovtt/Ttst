package com.kartoteka.app.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/** Общий размер для группы подписей: все уменьшаются до размера самой длинной — подписи одинаковые. */
@Composable
fun rememberSharedTextSize(max: Float): MutableFloatState = remember(max) { mutableFloatStateOf(max) }

/**
 * Текст, который не обрезается: если не помещается по ширине — плавно уменьшается до [minSp].
 * Строки можно разделять «\n» (каждая строка — без переноса). [shared] — общий размер для группы подписей.
 */
@Composable
fun FitText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    minSp: Float = 9f,
    align: TextAlign = TextAlign.Start,
    shared: MutableFloatState? = null,
) {
    val max = style.fontSize.value
    val own = remember(text, max) { mutableFloatStateOf(max) }
    val state = shared ?: own
    var ready by remember(text, max) { mutableStateOf(false) }
    val size = state.floatValue
    val lines = text.count { it == '\n' } + 1
    Text(
        text, color = color, maxLines = lines, softWrap = false, textAlign = align,
        style = style.copy(fontSize = size.sp, lineHeight = (size * 1.22f).sp),
        onTextLayout = { r ->
            if (r.didOverflowWidth && state.floatValue > minSp) state.floatValue = (state.floatValue - 0.5f).coerceAtLeast(minSp) else ready = true
        },
        modifier = modifier.drawWithContent { if (ready) drawContent() },
    )
}
