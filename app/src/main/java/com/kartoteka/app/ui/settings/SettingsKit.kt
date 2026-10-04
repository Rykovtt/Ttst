package com.kartoteka.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kartoteka.app.data.Settings
import com.kartoteka.app.i18n.t
import com.kartoteka.app.ui.components.NoaOrb
import com.kartoteka.app.ui.components.heroBackground
import com.kartoteka.app.ui.components.pressable
import com.kartoteka.app.ui.theme.Motion
import com.kartoteka.app.ui.theme.Rv
import com.kartoteka.app.ui.theme.motion
import com.kartoteka.app.ui.theme.rememberHaptics

/** Раздел настроек: заголовок, состояние одной строкой, ключевые слова для поиска и содержимое. */
class SettingEntry(
    val title: String,
    val icon: ImageVector,
    val subtitle: String,
    val keywords: String,
    val accent: Boolean = false,
    val content: @Composable ColumnScope.() -> Unit,
)

/** Фирменная карточка вверху настроек. */
@Composable
fun SettingsHero(settings: Settings, people: Int) {
    val context = LocalContext.current
    val custom by settings.appTitle.value.collectAsState()
    val lock by settings.lockEnabled.collectAsState()
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(30.dp))
            .heroBackground().padding(22.dp),
    ) {
        Text(com.kartoteka.app.AppIcons.title(context, custom).uppercase(), style = MaterialTheme.typography.headlineMedium, color = Rv.HeroText)
        Spacer(Modifier.height(6.dp))
        Text(t("Ваш порядок\nв людях, встречах\nи важных деталях."), style = MaterialTheme.typography.titleMedium, color = Rv.HeroText.copy(alpha = 0.86f))
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeroStat("$people", t("в картотеке"))
            Spacer(Modifier.width(10.dp))
            Row(
                Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.08f)).padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Lock, null, tint = if (lock) Rv.Lime else Rv.HeroMuted, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(t("Шифрование AES-256"), style = MaterialTheme.typography.labelMedium, color = Rv.HeroText)
            }
        }
    }
}

@Composable
private fun HeroStat(value: String, label: String) {
    Row(
        Modifier.clip(CircleShape).background(Rv.Peach).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(value, style = MaterialTheme.typography.labelLarge, color = Rv.Ink)
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = Rv.Ink.copy(alpha = 0.75f))
    }
}

@Composable
fun SettingsSearch(value: String, onChange: (String) -> Unit) {
    TextField(
        value = value, onValueChange = onChange, singleLine = true,
        placeholder = { Text(t("Поиск по настройкам")) },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = { if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Default.Close, t("Очистить")) } },
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            unfocusedPlaceholderColor = MaterialTheme.colorScheme.outline,
            unfocusedLeadingIconColor = MaterialTheme.colorScheme.outline,
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

/**
 * Разделы одной сгруппированной панелью: строка раскрывается по нажатию плавным изменением высоты и прозрачности.
 * Последний открытый раздел запоминается — к нему можно быстро вернуться.
 */
@Composable
fun SettingsGroup(entries: List<SettingEntry>, forceOpen: Boolean, onNoa: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ui", android.content.Context.MODE_PRIVATE) }
    var open by rememberSaveable { mutableStateOf(listOf<String>()) }
    var recent by remember { mutableStateOf(prefs.getString("recent_setting", null)) }
    val haptics = rememberHaptics()

    fun toggle(title: String) {
        haptics.tick()
        open = if (title in open) open - title else open + title
        if (title !in open) return
        recent = title
        prefs.edit().putString("recent_setting", title).apply()
    }

    val last = recent?.let { r -> entries.firstOrNull { it.title == r } }
    if (last != null && last.title !in open && entries.size > 1) {
        Row(
            Modifier.padding(horizontal = 16.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .pressable { toggle(last.title) }.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.History, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text(t("Недавно: %1\$s", last.title), style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(6.dp))
    }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(26.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        entries.forEachIndexed { i, e ->
            val expanded = forceOpen || e.title in open
            val rot by animateFloatAsState(if (expanded) 90f else 0f, motion(Motion.STANDARD), label = "chev")
            Row(
                Modifier.fillMaxWidth().clickable { toggle(e.title) }.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(e.icon, null, tint = if (expanded) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.title, style = MaterialTheme.typography.titleSmall)
                    Text(e.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (e.accent) {
                    NoaOrb(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onNoa))
                    Spacer(Modifier.width(6.dp))
                }
                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.rotate(rot))
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(motion(Motion.STANDARD), expandFrom = Alignment.Top) + fadeIn(motion(Motion.STANDARD, 60)),
                exit = shrinkVertically(motion(Motion.STANDARD), shrinkTowards = Alignment.Top) + fadeOut(motion(Motion.MICRO)),
            ) {
                Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) { e.content(this) }
            }
            if (i < entries.lastIndex) HorizontalDivider(Modifier.padding(start = 68.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}
