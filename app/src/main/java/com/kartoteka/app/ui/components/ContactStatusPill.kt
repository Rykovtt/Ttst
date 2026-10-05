package com.kartoteka.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.kartoteka.app.data.PersonHint
import com.kartoteka.app.i18n.I18n
import com.kartoteka.app.i18n.t
import com.kartoteka.app.ui.theme.AnimationTokens
import com.kartoteka.app.ui.theme.PeopleDims
import com.kartoteka.app.ui.theme.PeopleShapes
import com.kartoteka.app.ui.theme.PeopleType
import com.kartoteka.app.ui.theme.RvColors
import com.kartoteka.app.ui.theme.u
import java.time.LocalDate

/** Статусная плашка: ближайшая встреча, прошедшая встреча или день рождения (розовая, бордовая иконка). */
@Composable
fun ContactStatusPill(hint: PersonHint, dark: Boolean) {
    data class Look(val icon: ImageVector, val text: String, val bg: Color, val iconTint: Color)
    val baseBg = if (dark) Color(0xFF232226) else RvColors.PillBg
    val text = if (dark) Color(0xFFE8E5E0) else RvColors.PillText
    val look = when (hint) {
        is PersonHint.Today -> Look(if (hint.reminder) Icons.Outlined.NotificationsActive else Icons.Outlined.CalendarMonth, t("Сегодня %1\$s", hint.time), baseBg, text)
        is PersonHint.Upcoming -> Look(
            if (hint.reminder) Icons.Outlined.NotificationsActive else Icons.Outlined.CalendarMonth,
            (if (hint.date == LocalDate.now().plusDays(1)) t("Завтра") else I18n.dayMonthShort(hint.date.dayOfMonth, hint.date.monthValue)) + " " + hint.time,
            baseBg, text,
        )
        is PersonHint.Birthday -> Look(
            Icons.Outlined.Redeem,
            if (hint.days == 0L) t("День рождения сегодня") else t("День рождения %1\$s", I18n.dayMonthShort(hint.day, hint.month)),
            if (dark) Color(0xFF3A2224) else RvColors.PillBirthdayBg, if (dark) Color(0xFFF0A0A8) else RvColors.PillBirthdayIcon,
        )
        is PersonHint.LastMet -> Look(
            Icons.Outlined.Schedule,
            when (hint.days) { 0L -> t("Виделись сегодня"); 1L -> t("Виделись вчера"); else -> t("Была встреча %1\$s дн. назад", hint.days) },
            baseBg, text,
        )
    }
    val bg by animateColorAsState(look.bg, tween(AnimationTokens.Category), label = "pillBg")
    Row(
        Modifier.height(maxOf(u(PeopleDims.PillHeight), 22.dp)).clip(PeopleShapes.pill()).background(bg).padding(horizontal = u(16)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(look.icon, null, tint = look.iconTint, modifier = Modifier.size(maxOf(u(PeopleDims.PillIcon), 12.dp)))
        Spacer(Modifier.width(u(10)))
        FitText(look.text, PeopleType.pill, text, minSp = 10f)
    }
}
