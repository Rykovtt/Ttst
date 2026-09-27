package com.kartoteka.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kartoteka.app.data.ContactType
import com.kartoteka.app.data.Person
import com.kartoteka.app.ui.theme.AccentPalette
import java.io.File
import kotlin.math.abs

fun initials(p: Person): String {
    val a = p.firstName.firstOrNull() ?: p.nickname.firstOrNull() ?: p.lastName.firstOrNull()
    val b = if (p.firstName.isNotBlank()) p.lastName.firstOrNull() else null
    return listOfNotNull(a, b).joinToString("").uppercase().ifBlank { "?" }
}

fun accentFor(key: String): Color = Color(AccentPalette[abs(key.hashCode()) % AccentPalette.size])

@Composable
fun Avatar(person: Person, size: Dp, modifier: Modifier = Modifier, path: String? = person.avatarPath) {
    if (path != null) {
        AsyncImage(
            model = File(path),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
        )
    } else {
        val c = accentFor(person.displayName)
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(c, c.copy(alpha = 0.65f)))),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                initials(person),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = (size.value * 0.38f).sp,
            )
        }
    }
}

@Composable
fun SectionCard(
    title: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(vertical = 14.dp)) {
            Row(Modifier.padding(horizontal = 18.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                }
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                action?.invoke()
            }
            Spacer(Modifier.size(6.dp))
            content()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InfoRow(
    label: String,
    value: String,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null || onLongClick != null)
                    Modifier.combinedClickable(onClick = { onClick?.invoke() }, onLongClick = onLongClick)
                else Modifier
            )
            .padding(horizontal = 18.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
        trailing?.invoke()
    }
}

@Composable
fun ClosenessStars(value: Int, onChange: ((Int) -> Unit)? = null, size: Dp = 22.dp, tint: Color = MaterialTheme.colorScheme.tertiary) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 1..5) {
            val filled = i <= value
            val icon = if (filled) Icons.Default.Star else Icons.Default.StarBorder
            if (onChange != null) {
                IconButton(onClick = { onChange(if (value == i) 0 else i) }, modifier = Modifier.size(size + 12.dp)) {
                    Icon(icon, null, tint = tint, modifier = Modifier.size(size))
                }
            } else {
                Icon(icon, null, tint = tint, modifier = Modifier.size(size))
            }
        }
    }
}

fun iconFor(type: ContactType): ImageVector = when (type) {
    ContactType.PHONE -> Icons.Default.Call
    ContactType.EMAIL -> Icons.Default.Email
    ContactType.TELEGRAM -> Icons.AutoMirrored.Filled.Send
    ContactType.WHATSAPP -> Icons.AutoMirrored.Filled.Chat
    ContactType.VIBER -> Icons.AutoMirrored.Filled.Chat
    ContactType.INSTAGRAM -> Icons.Default.PhotoCamera
    ContactType.VK -> Icons.Default.AlternateEmail
    ContactType.FACEBOOK -> Icons.Default.Groups
    ContactType.WEBSITE -> Icons.Default.Language
    ContactType.OTHER -> Icons.Default.MoreHoriz
}

@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, modifier: Modifier = Modifier, actions: @Composable () -> Unit = {}) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(44.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        actions()
    }
}

@Composable
fun ColorDot(color: Long, size: Dp = 10.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(Color(color)))
}

@Composable
fun Badge(text: String, container: Color = MaterialTheme.colorScheme.tertiaryContainer, content: Color = MaterialTheme.colorScheme.onTertiaryContainer) {
    Surface(shape = CircleShape, color = container) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
fun FullScreenCenter(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
