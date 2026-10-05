package com.kartoteka.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** Формы главного экрана (радиусы — в единицах базовой композиции). */
object PeopleShapes {
    /** Шапка прилегает к верхнему краю экрана — сверху углы прямые (их скругляет сам экран), иначе в углах виден светлый фон. */
    @Composable fun header(): Shape = RoundedCornerShape(
        topStart = 0.dp, topEnd = 0.dp,
        bottomStart = u(PeopleDims.HeaderBottomRadius), bottomEnd = u(PeopleDims.HeaderBottomRadius),
    )
    @Composable fun search(): Shape = RoundedCornerShape(u(PeopleDims.SearchRadius))
    @Composable fun addTile(): Shape = RoundedCornerShape(u(PeopleDims.AddTileRadius))
    @Composable fun noa(): Shape = RoundedCornerShape(u(PeopleDims.NoaRadius))
    @Composable fun pill(): Shape = RoundedCornerShape(u(PeopleDims.PillRadius))
    @Composable fun thumb(): Shape = RoundedCornerShape(u(PeopleDims.ThumbRadius))
    /** Навигация прилегает к нижнему краю — скруглены только верхние углы. */
    @Composable fun nav(): Shape = RoundedCornerShape(
        topStart = u(PeopleDims.NavRadius), topEnd = u(PeopleDims.NavRadius), bottomStart = 0.dp, bottomEnd = 0.dp,
    )
    @Composable fun navPill(): Shape = RoundedCornerShape(u(PeopleDims.NavPillRadius))
    @Composable fun counter(): Shape = RoundedCornerShape(u(PeopleDims.CounterRadius))
}
