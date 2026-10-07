package com.kartoteka.app.ui.components

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
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

/**
 * Нижний отступ для кнопки внизу полноэкранного диалога: системная навигация или клавиатура — что выше.
 * В диалоге Compose получает высоту навигации не на всех телефонах (Samsung, Android 15: кнопка уходила
 * под панель навигации), поэтому берём наибольшее из своих отступов и отступа окна приложения.
 */
@Composable
fun dialogBottomInset(): androidx.compose.ui.unit.Dp {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val view = LocalView.current
    val own = maxOf(
        WindowInsets.navigationBars.getBottom(density),
        WindowInsets.ime.getBottom(density),
    )
    val activityNav = androidx.compose.runtime.remember(view) {
        var c = view.context
        while (c !is Activity && c is android.content.ContextWrapper) c = c.baseContext
        (c as? Activity)?.window?.decorView?.let {
            androidx.core.view.ViewCompat.getRootWindowInsets(it)
                ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars())?.bottom
        } ?: 0
    }
    return with(density) { maxOf(own, activityNav).toDp() }
}

/**
 * Отступ нижней панели с кнопкой («Сохранить» и т.п.): над системной навигацией, а при открытой клавиатуре — над ней.
 * Высота панели уже входит в padding Scaffold, поэтому содержимому экрана свой imePadding не нужен
 * (иначе клавиатура учитывается дважды и над ней остаётся пустая плашка).
 */
@Composable
fun androidx.compose.ui.Modifier.bottomBarInsets(): androidx.compose.ui.Modifier =
    windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
