package com.kartoteka.app.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kartoteka.app.data.ChatMessage
import com.kartoteka.app.i18n.t
import com.kartoteka.app.ui.app
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

private sealed interface ChatRowItem {
    data class Day(val date: LocalDate) : ChatRowItem
    data class Msg(val m: ChatMessage, val showAuthor: Boolean) : ChatRowItem
}

/** Просмотр импортированной переписки с поиском. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(chatId: Long, onBack: () -> Unit) {
    val app = app()
    val chat by remember(chatId) { app.repository.observeChat(chatId) }.collectAsState(null)
    val messages by produceState<List<ChatMessage>?>(null, chatId) { value = app.repository.chatMessages(chatId) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val zone = remember { ZoneId.systemDefault() }
    val me = chat?.meAuthor.orEmpty()
    val multi = remember(messages) { (messages?.map { it.author }?.distinct()?.size ?: 0) > 2 }

    val rows = remember(messages) {
        val out = mutableListOf<ChatRowItem>()
        var day: LocalDate? = null
        var prevAuthor: String? = null
        messages.orEmpty().forEach { m ->
            val d = Instant.ofEpochMilli(m.time).atZone(zone).toLocalDate()
            if (d != day) { out += ChatRowItem.Day(d); day = d; prevAuthor = null }
            out += ChatRowItem.Msg(m, m.author != prevAuthor)
            prevAuthor = m.author
        }
        out
    }
    var jumpTo by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(rows) { if (rows.isNotEmpty()) listState.scrollToItem(rows.lastIndex) }
    LaunchedEffect(jumpTo, searching) {
        val i = jumpTo ?: return@LaunchedEffect
        if (!searching) { listState.scrollToItem((i - 2).coerceAtLeast(0)); jumpTo = null }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { if (searching) { searching = false; query = "" } else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Назад"))
                    }
                },
                title = {
                    if (searching) {
                        TextField(
                            query, { query = it }, singleLine = true, placeholder = { Text(t("Поиск по переписке")) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else Column {
                        Text(chat?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${chat?.source.orEmpty()} · ${t("%1\$s сообщ.", chat?.messageCount ?: 0)}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    if (searching) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, t("Очистить")) }
                    else IconButton(onClick = { searching = true }) { Icon(Icons.Default.Search, t("Поиск")) }
                },
            )
        },
    ) { pad ->
        val all = messages
        if (all == null) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (searching && words.isNotEmpty()) {
            val found = rows.withIndex().filter { (_, r) -> r is ChatRowItem.Msg && words.all { w -> r.m.textLower.contains(w) } }
            val fmt = remember { SimpleDateFormat("d MMM yyyy, HH:mm", com.kartoteka.app.i18n.I18n.locale) }
            LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Text(t("Найдено: %1\$s", found.size), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                itemsIndexed(found, key = { _, iv -> iv.index }) { _, iv ->
                    val m = (iv.value as ChatRowItem.Msg).m
                    Surface(
                        shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth().clickable { jumpTo = iv.index; searching = false; query = "" },
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("${m.author} · ${fmt.format(Date(m.time))}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(highlight(m.text, words), style = MaterialTheme.typography.bodyMedium, maxLines = 6, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        } else {
            val dayFmt = remember { java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", com.kartoteka.app.i18n.I18n.locale) }
            val timeFmt = remember { SimpleDateFormat("HH:mm", com.kartoteka.app.i18n.I18n.locale) }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                itemsIndexed(rows, key = { i, _ -> i }) { _, r ->
                    when (r) {
                        is ChatRowItem.Day -> Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                                Text(dayFmt.format(r.date), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                            }
                        }
                        is ChatRowItem.Msg -> Bubble(r.m, mine = me.isNotEmpty() && r.m.author == me, showAuthor = r.showAuthor && (multi || me.isEmpty()), time = timeFmt.format(Date(r.m.time)))
                    }
                }
            }
        }
    }
}

@Composable
private fun Bubble(m: ChatMessage, mine: Boolean, showAuthor: Boolean, time: String) {
    val bg = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Surface(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = if (mine) 16.dp else 4.dp, bottomEnd = if (mine) 4.dp else 16.dp),
            color = bg,
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 7.dp)) {
                if (showAuthor && m.author.isNotBlank() && !mine) {
                    Text(m.author, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = fg)
                }
                if (m.author.isBlank()) {
                    Text(m.text, style = MaterialTheme.typography.bodySmall, color = fg.copy(alpha = 0.7f))
                } else {
                    Text(m.text, style = MaterialTheme.typography.bodyMedium, color = fg)
                }
                Text(time, style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.End))
            }
        }
    }
}

@Composable
private fun highlight(text: String, words: List<String>): AnnotatedString {
    val style = SpanStyle(background = MaterialTheme.colorScheme.tertiaryContainer, fontWeight = FontWeight.SemiBold)
    val lower = text.lowercase()
    return buildAnnotatedString {
        append(text)
        words.forEach { w ->
            var i = lower.indexOf(w)
            while (i >= 0) { addStyle(style, i, i + w.length); i = lower.indexOf(w, i + w.length) }
        }
    }
}

