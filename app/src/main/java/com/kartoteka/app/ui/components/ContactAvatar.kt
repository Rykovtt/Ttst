package com.kartoteka.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kartoteka.app.data.Person
import com.kartoteka.app.ui.theme.PeopleDims
import com.kartoteka.app.ui.theme.RvColors
import com.kartoteka.app.ui.theme.u

/** Аватар контакта 96 (ед.): круглое фото, обводка #FFFFFF80, индикатор категории 18 в правом верхнем секторе. */
@Composable
fun ContactAvatar(person: Person, dot: Color?, background: Color, modifier: Modifier = Modifier) {
    val size = u(PeopleDims.ContactAvatar)
    Box(modifier.size(size)) {
        Avatar(person, size, Modifier.sharedPhoto(person.id).border(1.dp, RvColors.AvatarBorder, CircleShape))
        if (dot != null && dot != Color.Transparent) {
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = u(2), y = u(2)).size(u(PeopleDims.AvatarDot + 6))
                    .clip(CircleShape).background(background).padding(u(3)).clip(CircleShape).background(dot)
            )
        }
    }
}
