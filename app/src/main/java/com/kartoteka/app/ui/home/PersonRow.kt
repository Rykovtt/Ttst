package com.kartoteka.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kartoteka.app.data.ArchiveLogic
import com.kartoteka.app.i18n.t
import com.kartoteka.app.ui.components.Avatar
import com.kartoteka.app.ui.components.ColorDot
import com.kartoteka.app.ui.components.categoryColor
import com.kartoteka.app.ui.theme.Rv

/** Строка человека для других экранов (группы и т. п.) — компактная. */
@Composable
fun PersonRow(hit: ArchiveLogic.SearchHit, onClick: () -> Unit, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    val pf = hit.person
    val p = pf.person
    val days = ArchiveLogic.daysUntilBirthday(p)
    val ring = pf.groups.firstOrNull()?.let { Color(it.color) } ?: categoryColor(p.relation)
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(56.dp)) {
            Avatar(p, 52.dp, Modifier.align(Alignment.Center))
            if (ring != Color.Transparent) {
                Box(Modifier.align(Alignment.TopEnd).size(14.dp).clip(CircleShape).background(MaterialTheme.colorScheme.background).padding(2.5.dp).clip(CircleShape).background(ring))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (p.favorite) {
                    Spacer(Modifier.width(5.dp))
                    Icon(Icons.Default.Star, null, tint = Rv.Peach, modifier = Modifier.size(15.dp))
                }
                pf.groups.drop(1).take(3).forEach { Spacer(Modifier.width(4.dp)); ColorDot(it.color, 7.dp) }
            }
            val sub = listOf(t(p.relation), p.company.ifBlank { p.position }, p.city).filter { it.isNotBlank() }.joinToString(" · ")
            if (hit.matchedIn != null) {
                Text(hit.matchedIn, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else if (sub.isNotBlank()) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (days != null && days <= 7) {
            Spacer(Modifier.width(8.dp))
            com.kartoteka.app.ui.components.Badge("🎂 " + ArchiveLogic.daysString(days))
        }
        trailing()
    }
}

