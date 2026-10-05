package com.kartoteka.app.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.kartoteka.app.R

/** Manrope: Light / Regular / Medium / SemiBold / Bold / ExtraBold — локальные TTF. */
val ManropeFamily = FontFamily(
    Font(R.font.manrope_light, FontWeight.Light),
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
)

private fun m(size: Float, weight: FontWeight, ls: Float = 0f, lh: Float? = null) = TextStyle(
    fontFamily = ManropeFamily, fontWeight = weight, fontSize = size.sp, letterSpacing = ls.sp,
    lineHeight = (lh ?: size * 1.25f).sp,
)

/**
 * Шкала главного экрана. Начертания — по таблице ТЗ; кегли сверены с эталонным макетом
 * (при тех же пропорциях экрана таблица ТЗ даёт текст на ~20–25 % крупнее макета).
 */
object PeopleType {
    val logo = m(28f, FontWeight.Light, ls = 1.5f, lh = 34f)
    val title = m(42f, FontWeight.ExtraBold, ls = -1f, lh = 46f)
    val counter = m(13f, FontWeight.SemiBold)
    val subtitle = m(13.5f, FontWeight.Normal)
    val search = m(13.5f, FontWeight.Normal)
    val category = m(11.5f, FontWeight.Medium)
    val contactName = m(15.5f, FontWeight.Bold, ls = -0.1f, lh = 20f)
    val contactMeta = m(11.5f, FontWeight.Normal, lh = 15f)
    val quickAction = m(11.5f, FontWeight.Medium, lh = 14.5f)
    val nav = m(11f, FontWeight.Medium)
    val letter = m(15f, FontWeight.Bold)
    val pill = m(11f, FontWeight.Medium, lh = 14f)
    val menuLabel = m(12f, FontWeight.Medium)
}
