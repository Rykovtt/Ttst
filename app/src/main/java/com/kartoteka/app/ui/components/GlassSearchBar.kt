package com.kartoteka.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kartoteka.app.ui.animations.pressScale
import com.kartoteka.app.ui.theme.AnimationTokens
import com.kartoteka.app.ui.theme.PeopleDims
import com.kartoteka.app.ui.theme.PeopleShapes
import com.kartoteka.app.ui.theme.PeopleType
import com.kartoteka.app.ui.theme.RvColors
import com.kartoteka.app.ui.theme.u

/**
 * Стеклянный поиск: полупрозрачная поверхность поверх размытого фона ([backdrop] рисует размытую
 * копию фото шапки ровно под полем), обводка #FFFFFF30 → #FFFFFF65 при фокусе за 180 мс,
 * иконка поиска, вертикальный разделитель и кнопка расширенных фильтров.
 */
@Composable
fun GlassSearchBar(
    query: String,
    onQuery: (String) -> Unit,
    hint: String,
    searchDescription: String,
    filterDescription: String,
    clearDescription: String,
    onFilters: (() -> Unit)?,
    modifier: Modifier = Modifier,
    source: MutableInteractionSource = remember { MutableInteractionSource() },
    backdrop: @Composable BoxScope.() -> Unit = {},
) {
    val focused by source.collectIsFocusedAsState()
    val border by animateColorAsState(
        if (focused) RvColors.SearchBorderFocused else RvColors.SearchBorder,
        tween(AnimationTokens.SearchFocus, easing = AnimationTokens.Move), label = "searchBorder",
    )
    val shape = PeopleShapes.search()
    Box(modifier.fillMaxWidth().height(u(PeopleDims.SearchHeight)).clip(shape)) {
        backdrop()
        Row(
            Modifier.fillMaxSize().background(RvColors.SearchBg).border(1.dp, border, shape)
                .padding(start = u(PeopleDims.SearchIconLeft), end = u(6)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchGlyph(Modifier.size(u(PeopleDims.SearchIcon)), RvColors.SearchIcon)
            Spacer(Modifier.width(u(PeopleDims.SearchTextGap)))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) Text(hint, style = PeopleType.search, color = RvColors.SearchHint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicTextField(
                    value = query, onValueChange = onQuery, singleLine = true, interactionSource = source,
                    textStyle = PeopleType.search.copy(color = Color.White),
                    cursorBrush = SolidColor(RvColors.WarmLight),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = searchDescription },
                )
            }
            if (query.isNotEmpty()) {
                Box(
                    Modifier.size(maxOf(u(PeopleDims.SearchFilter), 36.dp)).clip(CircleShape).clickable { onQuery("") }
                        .semantics { contentDescription = clearDescription; role = Role.Button },
                    contentAlignment = Alignment.Center,
                ) { CrossGlyph(Modifier.size(u(22)), Color.White) }
            }
            if (onFilters == null) return@Row
            Box(Modifier.width(1.dp).height(u(PeopleDims.SearchDividerH)).background(RvColors.SearchDivider))
            val src = remember { MutableInteractionSource() }
            Box(
                Modifier.size(maxOf(u(PeopleDims.SearchFilter), 36.dp)).pressScale(src, 0.9f)
                    .clickable(src, indication = null, onClick = onFilters)
                    .semantics { contentDescription = filterDescription; role = Role.Button },
                contentAlignment = Alignment.Center,
            ) { SlidersGlyph(Modifier.size(u(26)), Color.White) }
        }
    }
}

/** Лупа: окружность и ручка. */
@Composable
fun SearchGlyph(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val st = size.minDimension * 0.09f
        val r = size.minDimension * 0.34f
        val c = Offset(size.width * 0.44f, size.height * 0.44f)
        drawCircle(color, r, c, style = Stroke(st))
        val d = r * 0.72f
        drawLine(color, c + Offset(d, d), Offset(size.width * 0.9f, size.height * 0.9f), st, StrokeCap.Round)
    }
}

/** Три горизонтальных ползунка. */
@Composable
fun SlidersGlyph(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val st = size.minDimension * 0.08f
        val ys = listOf(0.22f, 0.5f, 0.78f)
        val knobs = listOf(0.68f, 0.32f, 0.58f)
        ys.forEachIndexed { i, fy ->
            val y = size.height * fy
            drawLine(color, Offset(size.width * 0.06f, y), Offset(size.width * 0.94f, y), st, StrokeCap.Round)
            drawCircle(Color.Black.copy(alpha = 0.001f), size.minDimension * 0.1f, Offset(size.width * knobs[i], y))
            drawCircle(color, size.minDimension * 0.1f, Offset(size.width * knobs[i], y), style = Stroke(st))
        }
    }
}

/** Крестик очистки. */
@Composable
fun CrossGlyph(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val st = size.minDimension * 0.09f
        drawLine(color, Offset(size.width * 0.25f, size.height * 0.25f), Offset(size.width * 0.75f, size.height * 0.75f), st, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.75f, size.height * 0.25f), Offset(size.width * 0.25f, size.height * 0.75f), st, StrokeCap.Round)
    }
}
