package com.kartoteka.app.ui.components

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Светлые значки статус-бара, пока под ним тёмная поверхность (фото, тёмная шапка).
 * Когда под статус-баром светлый фон — возвращаем тёмные значки. При уходе с экрана — значение темы.
 */
@Composable
fun StatusBarOverDark(dark: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val themeDark = isSystemInDarkTheme()
    val window = (view.context as? Activity)?.window ?: return
    LaunchedEffect(dark, themeDark) {
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark && !themeDark
    }
    DisposableEffect(Unit) {
        onDispose { WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !themeDark }
    }
}
