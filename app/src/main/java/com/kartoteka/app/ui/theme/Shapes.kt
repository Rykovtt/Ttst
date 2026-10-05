package com.kartoteka.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape

/** Формы главного экрана (радиусы — в единицах базовой композиции). */
object PeopleShapes {
    @Composable fun header(): Shape = RoundedCornerShape(
        topStart = u(PeopleDims.HeaderTopRadius), topEnd = u(PeopleDims.HeaderTopRadius),
        bottomStart = u(PeopleDims.HeaderBottomRadius), bottomEnd = u(PeopleDims.HeaderBottomRadius),
    )
    @Composable fun search(): Shape = RoundedCornerShape(u(PeopleDims.SearchRadius))
    @Composable fun addTile(): Shape = RoundedCornerShape(u(PeopleDims.AddTileRadius))
    @Composable fun noa(): Shape = RoundedCornerShape(u(PeopleDims.NoaRadius))
    @Composable fun pill(): Shape = RoundedCornerShape(u(PeopleDims.PillRadius))
    @Composable fun thumb(): Shape = RoundedCornerShape(u(PeopleDims.ThumbRadius))
    @Composable fun nav(): Shape = RoundedCornerShape(u(PeopleDims.NavRadius))
    @Composable fun navPill(): Shape = RoundedCornerShape(u(PeopleDims.NavPillRadius))
    @Composable fun counter(): Shape = RoundedCornerShape(u(PeopleDims.CounterRadius))
}
