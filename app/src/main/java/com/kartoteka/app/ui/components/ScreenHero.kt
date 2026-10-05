package com.kartoteka.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kartoteka.app.R
import com.kartoteka.app.i18n.t
import com.kartoteka.app.ui.animations.pressScale
import com.kartoteka.app.ui.theme.AnimationTokens
import com.kartoteka.app.ui.theme.PeopleDims
import com.kartoteka.app.ui.theme.PeopleShapes
import com.kartoteka.app.ui.theme.PeopleType
import com.kartoteka.app.ui.theme.RvColors
import com.kartoteka.app.ui.theme.rememberHaptics
import com.kartoteka.app.ui.theme.u

/*
 * Общая шапка разделов в стиле главного экрана: фото гор с теми же затемнениями,
 * скругления 42/46, отступы 36, заголовок ExtraBold со счётчиком, стеклянные кнопки и вкладки.
 */

/** Фон шапки: фото гор (фокус 60/40) + затемнения слева, снизу и общее — как на главном. */
@Composable
fun MountainBackdrop(modifier: Modifier = Modifier, key: String = "default") {
    Box(modifier) {
        HeroImage(Modifier.matchParentSize(), key = key)
        Box(
            Modifier.matchParentSize().drawBehind {
                drawRect(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.75f), 0.6f to Color.Transparent))
                drawRect(Brush.verticalGradient(0.45f to Color.Transparent, 1f to RvColors.HeaderBottom.copy(alpha = 0.9f)))
                drawRect(Color.Black.copy(alpha = 0.15f))
            }
        )
    }
}

/**
 * Шапка раздела. [content] — стеклянные элементы под подзаголовком (вкладки, чипы, поиск).
 * [compact] — для вложенных экранов: заголовок поменьше, без лишней высоты.
 */
@Composable
fun ScreenHero(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    count: Int? = null,
    onBack: (() -> Unit)? = null,
    backIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack,
    backDescription: String = t("Назад"),
    compact: Boolean = false,
    backgroundButton: Boolean = true,
    /** Ключ фона: у каждого раздела свой (меняется кнопкой на шапке). */
    backgroundKey: String = title,
    actions: @Composable RowScope.() -> Unit = {},
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val pad = u(PeopleDims.HeaderPad)
    StatusBarOverDark(true)
    Box(modifier.fillMaxWidth().clip(PeopleShapes.header()).background(RvColors.HeaderBottom)) {
        MountainBackdrop(Modifier.matchParentSize(), key = backgroundKey)
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(bottom = u(34))) {
            Row(
                Modifier.fillMaxWidth().padding(start = pad - u(8), end = pad - u(8), top = u(PeopleDims.ControlsTop) - 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) HeroButton(backIcon, backDescription, onBack)
                Spacer(Modifier.weight(1f))
                if (backgroundButton) HeroBackgroundButton(backgroundKey, Modifier.padding(end = 10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
            }
            Row(
                Modifier.fillMaxWidth().padding(start = pad, end = pad, top = if (compact) u(14) else u(PeopleDims.TitleTop) - 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FitText(
                    title, if (compact) PeopleType.title.copy(fontSize = PeopleType.title.fontSize * 0.78f, lineHeight = PeopleType.title.lineHeight * 0.78f) else PeopleType.title,
                    Color.White, Modifier.weight(1f, fill = false), minSp = 22f,
                )
                if (count != null) {
                    Spacer(Modifier.width(u(PeopleDims.CounterGap)))
                    HeroCounter(count)
                }
            }
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle, style = PeopleType.subtitle, color = RvColors.Subtitle, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = pad, end = pad, top = u(PeopleDims.SubtitleTop)),
                )
            }
            if (content != null) {
                Spacer(Modifier.height(u(PeopleDims.SearchTop)))
                content()
            }
        }
    }
}

/** Счётчик рядом с заголовком — как «128» на главном. */
@Composable
fun HeroCounter(n: Int) {
    Box(
        Modifier.clip(PeopleShapes.counter()).background(RvColors.CounterBg)
            .padding(horizontal = u(PeopleDims.CounterPadH * 1.6f), vertical = u(PeopleDims.CounterPadV)),
    ) { Text("$n", style = PeopleType.counter, color = Color.White) }
}

/** Стеклянная круглая кнопка шапки (назад, календарь, добавить). */
@Composable
fun HeroButton(icon: ImageVector, description: String, onClick: () -> Unit, badge: Boolean = false, active: Boolean = false) {
    val source = remember { MutableInteractionSource() }
    val haptics = rememberHaptics()
    Box(
        Modifier.size(42.dp).pressScale(source, 0.92f).clip(CircleShape)
            .background(if (active) RvColors.ChipActiveBg else RvColors.ChipBg)
            .border(1.dp, if (active) Color.Transparent else RvColors.ChipBorder, CircleShape)
            .clickable(source, indication = null, role = Role.Button) { haptics.tick(); onClick() }
            .semantics { contentDescription = description; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (active) RvColors.ChipActiveText else RvColors.TextOnDark, modifier = Modifier.size(20.dp))
        if (badge) Box(Modifier.align(Alignment.TopEnd).padding(9.dp).size(7.dp).clip(CircleShape).background(RvColors.Orange))
    }
}

/** Стеклянные вкладки на тёмной шапке: дорожка как поиск, выбранная — тёплая «таблетка» как активный чип. */
@Composable
fun HeroSegmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    BoxWithConstraints(
        modifier.fillMaxWidth().padding(horizontal = u(PeopleDims.HeaderPad))
            .clip(CircleShape).background(RvColors.SearchBg).border(1.dp, RvColors.SearchBorder, CircleShape).padding(4.dp),
    ) {
        val w = maxWidth / options.size.coerceAtLeast(1)
        val x by animateDpAsState(w * selected, tween(AnimationTokens.TabSwitch, easing = AnimationTokens.Move), label = "heroSeg")
        Box(Modifier.offset(x = x).width(w).height(36.dp).clip(CircleShape).background(RvColors.ChipActiveBg))
        Row {
            options.forEachIndexed { i, title ->
                val sel = i == selected
                val a by animateFloatAsState(if (sel) 1f else 0f, tween(AnimationTokens.TabSwitch), label = "heroSegFg")
                Box(
                    Modifier.weight(1f).height(36.dp).clip(CircleShape)
                        .clickable(role = Role.Tab) { if (!sel) { haptics.tick(); onSelect(i) } },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        title, style = PeopleType.category, maxLines = 1,
                        color = androidx.compose.ui.graphics.lerp(RvColors.ChipText, RvColors.ChipActiveText, a),
                    )
                }
            }
        }
    }
}

/** Ряд стеклянных чипов на шапке с отступами как у категорий главного экрана. */
@Composable
fun HeroChipRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = u(PeopleDims.HeaderPad)),
        horizontalArrangement = Arrangement.spacedBy(u(PeopleDims.ChipGap)),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Заголовок секции в теле экрана — как буквы списка людей (18/15 Bold, отступы 36/22/12). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = u(PeopleDims.HeaderPad), end = u(PeopleDims.HeaderPad) - 8.dp, top = u(30), bottom = u(14)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = PeopleType.letter, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** Кнопка-«таблетка» на шапке: основная — тёплая (как активный чип), вторичная — стеклянная. */
@Composable
fun HeroPillButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val haptics = rememberHaptics()
    val fg = if (primary) RvColors.ChipActiveText else RvColors.ChipText
    Row(
        modifier.height(u(PeopleDims.SearchHeight)).pressScale(source, 0.96f).clip(CircleShape)
            .background(if (primary) RvColors.ChipActiveBg else RvColors.ChipBg)
            .border(1.dp, if (primary) Color.Transparent else RvColors.ChipBorder, CircleShape)
            .clickable(source, indication = null, enabled = enabled, role = Role.Button) { haptics.tick(); onClick() }
            .alpha(if (enabled) 1f else 0.45f)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = PeopleType.category.copy(fontSize = PeopleType.category.fontSize * 1.1f), color = fg, maxLines = 1)
    }
}
