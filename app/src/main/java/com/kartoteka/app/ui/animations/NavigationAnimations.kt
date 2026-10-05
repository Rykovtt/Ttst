package com.kartoteka.app.ui.animations

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.navigation.NavBackStackEntry
import com.kartoteka.app.ui.theme.AnimationTokens

/** Смена раздела — короткий fade (220 мс): появление и исчезновение со своими кривыми. */
object NavigationAnimations {
    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        fadeIn(tween(AnimationTokens.TabSwitch, easing = AnimationTokens.Enter))
    }
    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        fadeOut(tween(AnimationTokens.TabSwitch - 60, easing = AnimationTokens.Exit))
    }
}
