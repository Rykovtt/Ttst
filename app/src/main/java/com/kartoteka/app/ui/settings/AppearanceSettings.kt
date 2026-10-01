package com.kartoteka.app.ui.settings

import com.kartoteka.app.i18n.t

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import com.kartoteka.app.ui.components.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kartoteka.app.AppIcon
import com.kartoteka.app.AppIcons
import com.kartoteka.app.data.Settings

/** Значок и название на рабочем столе + название внутри приложения. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppearanceSettings(settings: Settings) {
    val context = LocalContext.current
    var current by remember { mutableStateOf(AppIcons.current(context)) }
    var pending by remember { mutableStateOf<AppIcon?>(null) }
    val title by settings.appTitle.value.collectAsState()

    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(
            t("Значок и название на рабочем столе. Маскировка («Калькулятор», «Погода»…) скрывает, что это за приложение."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AppIcon.entries.forEach { icon ->
                val selected = icon == current
                Column(
                    Modifier.width(74.dp).clip(RoundedCornerShape(16.dp))
                        .then(if (selected) Modifier.background(MaterialTheme.colorScheme.primaryContainer) else Modifier)
                        .clickable { if (!selected) pending = icon }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    IconPreview(icon, selected)
                    Text(
                        stringResource(icon.label) + if (icon == AppIcon.DARK) " ○" else "",
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        OutlinedTextField(
            value = title,
            onValueChange = { settings.appTitle.set(it.take(30)) },
            label = { Text(t("Название внутри приложения")) },
            placeholder = { Text(stringResource(current.label)) },
            supportingText = { Text(t("В шапке, на экране блокировки и в списке недавних приложений")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    pending?.let { icon ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(t("Сменить на «%1\$s»?", stringResource(icon.label))) },
            text = {
                Text(
                    t("Значок на рабочем столе обновится через несколько секунд. Если он был вынесен на главный экран — ") +
                        t("перетащите его заново из списка приложений. Данные не затрагиваются. ") +
                        t("В системных настройках (Приложения) название останется «RVault»."),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    AppIcons.apply(context, icon)
                    current = icon
                    (context as? com.kartoteka.app.MainActivity)?.updateTaskTitle()
                    pending = null
                }) { Text(t("Сменить")) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(t("Отмена")) } },
        )
    }
}

@Composable
private fun IconPreview(icon: AppIcon, selected: Boolean) {
    Box(
        Modifier.size(54.dp).clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(Color(icon.bgStart), Color(icon.bgEnd))))
            .then(if (selected) Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        // Передний слой адаптивного значка рассчитан на 108dp, видимая часть — центральные 72dp.
        Image(painterResource(icon.foreground), null, Modifier.requiredSize(81.dp))
    }
}
