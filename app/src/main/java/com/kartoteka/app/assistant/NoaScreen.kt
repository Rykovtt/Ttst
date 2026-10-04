package com.kartoteka.app.assistant

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kartoteka.app.KartotekaApp
import com.kartoteka.app.data.PersonFull
import com.kartoteka.app.i18n.t
import com.kartoteka.app.messaging.Messaging
import com.kartoteka.app.ui.app
import com.kartoteka.app.ui.components.OutlinedTextField
import kotlinx.coroutines.launch

private data class Bubble(val text: String, val mine: Boolean)

/** Экран ассистента Ноа: голос и текст. [onOpenPerson]/[onOpenAppointment] — переход по результату. */
@Composable
fun NoaScreen(onBack: () -> Unit, onOpenPerson: (Long) -> Unit, onOpenAppointment: (Long) -> Unit) {
    val app = app()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val noa = remember { Noa(app) }
    val listener = remember { NoaListener(context) }
    val name = app.settings.assistantName.value.collectAsState().value.ifBlank { "Ноа" }
    val voiceOn by app.settings.assistantVoice.value.collectAsState()

    val bubbles = remember { mutableListOf<Bubble>().toMutableStateList() }
    var input by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var pendingYes by remember { mutableStateOf<(suspend () -> Noa.Reply)?>(null) }
    val listState = rememberLazyListState()

    fun say(text: String) {
        bubbles.add(Bubble(text, mine = false))
        if (voiceOn) NoaVoice.speak(context, text)
    }

    suspend fun apply(reply: Noa.Reply) {
        when (reply) {
            is Noa.Reply.Say -> say(reply.text)
            is Noa.Reply.Say2Open -> {
                say(reply.text)
                NoaActions.pendingCall?.let { Messaging.dial(context, it); NoaActions.pendingCall = null }
                NoaActions.pendingMessage?.let { m ->
                    val pf = app.repository.getPerson(m.personId)
                    sendMessage(context, pf, m)
                    NoaActions.pendingMessage = null
                }
                reply.personId?.let(onOpenPerson)
                reply.appointmentId?.let(onOpenAppointment)
            }
            is Noa.Reply.Choose -> { say(reply.text); pendingYes = null }
            is Noa.Reply.Confirm -> { say(reply.text + "  " + t("Скажите «да» или «нет».")); pendingYes = reply.onYes }
        }
    }

    fun send(textRaw: String) {
        val text = textRaw.trim()
        if (text.isBlank()) return
        bubbles.add(Bubble(text, mine = true))
        input = ""
        val yes = pendingYes
        scope.launch {
            runCatching {
                if (yes != null && isYes(text)) { pendingYes = null; apply(yes()) }
                else if (yes != null && isNo(text)) { pendingYes = null; say(t("Хорошо, отменила.")) }
                else { pendingYes = null; apply(noa.handle(text)) }
            }.onFailure { say(t("Что-то пошло не так. Попробуйте ещё раз.")) }
        }
    }

    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startListening(listener, { listening = it }) { send(it) }
        else say(t("Нет доступа к микрофону — разрешите его в настройках телефона."))
    }

    LaunchedEffect(Unit) {
        if (voiceOn) NoaVoice.init(context)
        if (bubbles.isEmpty()) bubbles.add(Bubble(t("Привет! Я %1\$s. Скажите или напишите, что сделать: записать человека, позвонить, найти, добавить заметку.", name), mine = false))
    }
    LaunchedEffect(bubbles.size) { if (bubbles.isNotEmpty()) listState.animateScrollToItem(bubbles.lastIndex) }
    DisposableEffect(Unit) { onDispose { listener.stop(); NoaVoice.stop() } }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Назад")) }
            Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(bubbles) { b -> BubbleRow(b) }
        }
        if (pendingYes != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { send(t("да")) }) { Text(t("Да")) }
                TextButton(onClick = { send(t("нет")) }) { Text(t("Нет")) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f),
                placeholder = { Text(t("Команда…")) }, singleLine = true,
            )
            val scale by animateFloatAsState(if (listening) 1.15f else 1f, label = "mic")
            MicButton(active = listening, scale = scale) {
                if (listening) { listener.stop(); listening = false }
                else if (NoaListener(context).available()) {
                    askMic.launch(Manifest.permission.RECORD_AUDIO)
                } else say(t("На телефоне нет распознавания речи."))
            }
            if (input.isNotBlank()) IconButton(onClick = { send(input) }) { Icon(Icons.AutoMirrored.Filled.Send, t("Отправить")) }
        }
    }
}

@Composable
private fun BubbleRow(b: Bubble) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (b.mine) Arrangement.End else Arrangement.Start) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (b.mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Text(
                b.text, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                color = if (b.mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun MicButton(active: Boolean, scale: Float, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = CircleShape,
        color = if (active) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        modifier = Modifier.size((52 * scale).dp),
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Mic, t("Говорить"), tint = Color.White) }
    }
}

private fun startListening(listener: NoaListener, setListening: (Boolean) -> Unit, onText: (String) -> Unit) {
    setListening(true)
    listener.start(object : NoaListener.Callback {
        override fun onPartial(text: String) = Unit
        override fun onResult(text: String) { if (text.isNotBlank()) onText(text) }
        override fun onError(message: String?) = Unit
        override fun onReady() = Unit
        override fun onEnd() = setListening(false)
    })
}

private fun sendMessage(context: android.content.Context, pf: PersonFull?, m: NoaActions.Message) {
    pf ?: return
    when (m.channel) {
        NoaIntent.Channel.WHATSAPP -> pf.whatsapp?.let { Messaging.whatsapp(context, it, m.text) }
        NoaIntent.Channel.TELEGRAM -> pf.telegram?.let { Messaging.telegram(context, it, m.text) }
        NoaIntent.Channel.SMS -> pf.phone?.let { Messaging.sms(context, listOf(it), m.text) }
    }
}

private fun isYes(s: String) = Regex("\\b(да|ага|давай|подтвер|так|yes|yeah|ok|окей|добре)\\b").containsMatchIn(s.lowercase())
private fun isNo(s: String) = Regex("\\b(нет|не надо|отмен|ні|no|cancel)\\b").containsMatchIn(s.lowercase())
