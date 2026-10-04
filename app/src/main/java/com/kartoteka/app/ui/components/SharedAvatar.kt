package com.kartoteka.app.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import com.kartoteka.app.ui.theme.LocalReducedMotion
import com.kartoteka.app.ui.theme.Motion

/*
 * Общий элемент «фото человека»: при открытии карточки аватар из списка
 * плавно перетекает в большое фото профиля (и обратно).
 */

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedPhoto(personId: Long): Modifier {
    val shared = LocalSharedScope.current ?: return this
    val nav = LocalNavScope.current ?: return this
    if (LocalReducedMotion.current) return this
    return with(shared) {
        this@sharedPhoto.sharedBounds(
            rememberSharedContentState("photo-$personId"),
            nav,
            boundsTransform = { _, _ -> tween(360, easing = Motion.Ease) },
        )
    }
}
