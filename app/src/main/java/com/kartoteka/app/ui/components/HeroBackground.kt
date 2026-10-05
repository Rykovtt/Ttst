package com.kartoteka.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kartoteka.app.R
import com.kartoteka.app.i18n.t
import com.kartoteka.app.ui.animations.pressScale
import com.kartoteka.app.ui.app
import com.kartoteka.app.ui.theme.RvColors
import com.kartoteka.app.ui.theme.rememberHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Фото шапки: своё (выбрано пользователем, хранится зашифрованным) или стандартные горы. */
@Composable
fun HeroImage(modifier: Modifier, alignment: Alignment = BiasAlignment(0.2f, -0.2f)) {
    val path by app().settings.heroImage.value.collectAsState()
    if (path.isNotBlank() && File(path).exists()) {
        AsyncImage(File(path), null, contentScale = ContentScale.Crop, alignment = Alignment.Center, modifier = modifier)
    } else {
        Image(painterResource(R.drawable.hero_mountain), null, contentScale = ContentScale.Crop, alignment = alignment, modifier = modifier)
    }
}

/** Маленькая стеклянная кнопка на шапке: заменить фон своим фото или вернуть горы. Фон общий для всех разделов. */
@Composable
fun HeroBackgroundButton(modifier: Modifier = Modifier) {
    val app = app()
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val path by app.settings.heroImage.value.collectAsState()
    var menu by remember { mutableStateOf(false) }
    val picker = rememberPhotoPicker(multiple = false) { uris ->
        val uri = uris.firstOrNull() ?: return@rememberPhotoPicker
        scope.launch {
            val saved = withContext(Dispatchers.IO) { app.repository.photos.import(uri) }
            if (saved != null) {
                val old = app.settings.heroImage.value.value
                app.settings.heroImage.set(saved)
                if (old.isNotBlank() && old != saved) withContext(Dispatchers.IO) { runCatching { File(old).delete() } }
            }
        }
    }
    val source = remember { MutableInteractionSource() }
    Box(modifier) {
        Box(
            Modifier.size(32.dp).pressScale(source, 0.9f).clip(CircleShape)
                .background(RvColors.ChipBg).border(1.dp, RvColors.ChipBorder, CircleShape)
                .clickable(source, indication = null, role = Role.Button) { haptics.tick(); menu = true }
                .semantics { contentDescription = t("Фон шапки"); role = Role.Button },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Wallpaper, null, tint = RvColors.TextOnDark.copy(alpha = 0.85f), modifier = Modifier.size(16.dp)) }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(t("Своё фото фона")) },
                leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, null) },
                onClick = { menu = false; picker.gallery() },
            )
            if (path.isNotBlank()) DropdownMenuItem(
                text = { Text(t("Стандартный фон")) },
                leadingIcon = { Icon(Icons.Outlined.Landscape, null) },
                onClick = {
                    menu = false
                    val old = path
                    app.settings.heroImage.set("")
                    scope.launch(Dispatchers.IO) { runCatching { File(old).delete() } }
                },
            )
        }
    }
}
