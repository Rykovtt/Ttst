package com.kartoteka.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Фирменные цвета RVAULT из ТЗ главного экрана — одинаковы в обеих темах. */
object Brand {
    val Panel = Color(0xFF171719)
    val NavBg = Color(0xFF151517)
    val Milk = Color(0xFFFFF0DE)
    val Glow = Color(0xFFF4CDA3)
    val Clients = Color(0xFF8859F5)
    val Family = Color(0xFF91D936)
    val Friends = Color(0xFFF28A32)
    val Notice = Color(0xFFF5DAD5)
    val Subtitle = Color(0xFFE7E3DF)
    val SearchHint = Color(0xFFA6A4AB)
    val NavInactive = Color(0xFF85858B)
    val NavActive = Color(0xFFF6E2CB)
    val StarActive = Color(0xFFE8A13D)
}

/** Цвета светлой рабочей области (список людей) — свои для светлой и тёмной темы. */
@Immutable
data class HomePalette(
    val content: Color,
    val text: Color,
    val textSecondary: Color,
    val letter: Color,
    val divider: Color,
    val avatarBorder: Color,
    val actionBg: Color,
    val actionBgPressed: Color,
    val actionIcon: Color,
    val chipPlanned: Color,
    val chipLast: Color,
    val chipSoon: Color,
    val chipSoonText: Color,
    val chipReminder: Color,
    val chipText: Color,
    val star: Color,
    val chevron: Color,
)

private val LightHome = HomePalette(
    content = Color(0xFFF7F5F1),
    text = Color(0xFF171719),
    textSecondary = Color(0xFF85838A),
    letter = Color(0xFF29282A),
    divider = Color(0xFFE8E5E0),
    avatarBorder = Color(0xFFE5E1DA),
    actionBg = Color(0xFFEFEEEC),
    actionBgPressed = Color(0xFFE2E0DC),
    actionIcon = Color(0xFF232325),
    chipPlanned = Color(0xFFEAE8E6),
    chipLast = Color(0xFFEFEEEC),
    chipSoon = Color(0xFFF7E2DD),
    chipSoonText = Color(0xFFB8492F),
    chipReminder = Color(0xFFE9E6F8),
    chipText = Color(0xFF2A292B),
    star = Color(0xFF76747A),
    chevron = Color(0xFF77757B),
)

private val DarkHome = HomePalette(
    content = Color(0xFF0E0E10),
    text = Color(0xFFF2EFEA),
    textSecondary = Color(0xFF9A979E),
    letter = Color(0xFFE6E2DC),
    divider = Color(0xFF232326),
    avatarBorder = Color(0xFF2E2D30),
    actionBg = Color(0xFF1E1E21),
    actionBgPressed = Color(0xFF2A2A2E),
    actionIcon = Color(0xFFEDEAE5),
    chipPlanned = Color(0xFF222225),
    chipLast = Color(0xFF1C1C1F),
    chipSoon = Color(0xFF3A2420),
    chipSoonText = Color(0xFFF2A28E),
    chipReminder = Color(0xFF26233A),
    chipText = Color(0xFFE8E5E0),
    star = Color(0xFF8E8C92),
    chevron = Color(0xFF8E8C92),
)

@Composable
fun homePalette(): HomePalette = if (isSystemInDarkTheme()) DarkHome else LightHome

/** Размеры главного экрана: значения ТЗ, адаптированные к ширине телефона (раздел «Адаптивность»). */
object HomeDims {
    val sidePad = 20.dp
    val heroPad = 22.dp
    val avatar = 58.dp
    val action = 36.dp
    val thumb = 40.dp
}

/** Цвет категории по названию группы («Клиенты», «Семья», «Друзья») — из палитры бренда, иначе свой цвет группы. */
fun categoryTint(name: String, fallback: Color): Color {
    val n = name.lowercase()
    return when {
        n.contains("клиент") || n.contains("клієнт") || n.contains("client") -> Brand.Clients
        n.contains("сем") || n.contains("сім") || n.contains("famil") || n.contains("родн") || n.contains("рідн") -> Brand.Family
        n.contains("друз") || n.contains("друг") || n.contains("friend") -> Brand.Friends
        else -> fallback
    }
}
