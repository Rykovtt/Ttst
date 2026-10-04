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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.material.icons.filled.GraphicEq
import com.kartoteka.app.ui.components.NoaOrb
import com.kartoteka.app.ui.components.OrbState
import com.kartoteka.app.ui.components.heroBackground
import com.kartoteka.app.ui.components.pressable
import com.kartoteka.app.ui.theme.Motion
import com.kartoteka.app.ui.theme.Rv
import com.kartoteka.app.ui.theme.motion
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
fun NoaScreen(onBack: () -> Unit, onOpenPerson: (Long) -> Unit, onOpenAppointment: (Long) -> Unit, onNavigate: (NoaIntent.Section) -> Unit = {}) {
    val app = app()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val noa = remember { Noa(app) }
    val listener = remember { NoaListener(context) }
    val name = app.settings.assistantName.value.collectAsState().value.ifBlank { "Ноа" }
    val voiceOn by app.settings.assistantVoice.value.collectAsState()

    val brainOn by app.settings.assistantBrain.value.collectAsState()
    val interpreter = remember { NoaInterpreter(app.brain) }
    var brainReady by remember { mutableStateOf(false) }

    val bubbles = remember { mutableListOf<Bubble>().toMutableStateList() }
    var input by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var thinking by remember { mutableStateOf(false) }
    var level by remember { mutableStateOf(0f) }
    var partial by remember { mutableStateOf("") }
    var flash by remember { mutableStateOf<OrbState?>(null) }
    LaunchedEffect(flash) { if (flash != null) { kotlinx.coroutines.delay(700); flash = null } }
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
            is Noa.Reply.Navigate -> { say(reply.text); onNavigate(reply.section) }
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
            thinking = true
            runCatching {
                when {
                    yes != null && isYes(text) -> { pendingYes = null; apply(yes()) }
                    yes != null && isNo(text) -> { pendingYes = null; say(t("Хорошо, отменила.")) }
                    else -> {
                        pendingYes = null
                        // Сначала «мозг» (Gemini Nano), если включён и готов; иначе — быстрые команды.
                        val smart = if (brainReady) runCatching { interpreter.interpret(text) }.getOrNull() else null
                        if (smart != null) {
                            if (smart.intent != null) apply(noa.handleIntent(smart.intent))
                            else say(smart.reply ?: t("Не поняла команду."))
                        } else {
                            apply(noa.handle(text))
                        }
                    }
                }
            }.onSuccess { flash = OrbState.SUCCESS }
                .onFailure { flash = OrbState.ERROR; say(t("Что-то пошло не так. Попробуйте ещё раз.")) }
            thinking = false
        }
    }

    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startListening(listener, { listening = it; if (!it) { level = 0f; partial = "" } }, { level = it }, { partial = it }) { send(it) }
        else say(t("Нет доступа к микрофону — разрешите его в настройках телефона."))
    }

    LaunchedEffect(Unit) {
        if (voiceOn) NoaVoice.init(context)
        if (bubbles.isEmpty()) bubbles.add(Bubble(t("Привет! Я %1\$s. Скажите или напишите, что сделать: записать человека, позвонить, найти, добавить заметку.", name), mine = false))
    }
    // Готовим мозг в фоне и показываем его состояние, чтобы было видно, работает ли ИИ.
    LaunchedEffect(brainOn) {
        brainReady = false
        if (!brainOn) { bubbles.add(Bubble(t("Умный режим выключен — работают быстрые команды."), mine = false)); return@LaunchedEffect }
        if (!app.brain.supported) { bubbles.add(Bubble(t("Умный режим недоступен на этом телефоне — работают быстрые команды."), mine = false)); return@LaunchedEffect }
        if (app.brain.hasModel()) bubbles.add(Bubble(t("Запускаю умный режим…"), mine = false))
        when (app.brain.prepare()) {
            LlmBrain.State.READY -> { brainReady = true; bubbles.add(Bubble(t("Умный режим готов 🧠"), mine = false)) }
            LlmBrain.State.NEEDS_MODEL -> {
                val dl = app.brain.syncDownload()
                bubbles.add(Bubble(
                    if (dl is LlmBrain.Download.Running) t("Модель ещё скачивается (%1\$s). Пока работают быстрые команды.", "${dl.percent}%")
                    else t("Чтобы включить ум, нажмите «Скачать модель» в «Настройки → Ассистент». Пока работают быстрые команды."),
                    mine = false,
                ))
            }
            else -> bubbles.add(Bubble(t("Не удалось запустить умный режим — работают быстрые команды.") + "\n" + app.brain.detail, mine = false))
        }
    }
    LaunchedEffect(bubbles.size) { if (bubbles.isNotEmpty()) listState.animateScrollToItem(bubbles.lastIndex) }
    DisposableEffect(Unit) { onDispose { listener.stop(); NoaVoice.stop() } }

    val orbState = when {
        flash != null -> flash!!
        listening -> OrbState.LISTENING
        thinking -> OrbState.THINKING
        else -> OrbState.IDLE
    }
    val compact = bubbles.size > 2
    val orbSize by animateDpAsState(if (compact) 96.dp else 210.dp, motion(Motion.EMPHASIZED), label = "orbSize")
    val examples = listOf(
        t("Запиши Анну на завтра в 12:00"),
        t("Позвони маме"),
        t("Открой календарь"),
    )

    Column(Modifier.fillMaxSize().heroBackground(glow = Rv.Lavender).statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Назад"), tint = Rv.HeroText) }
            Column(Modifier.weight(1f)) {
                Text(t("Ассистент %1\$s", name), style = MaterialTheme.typography.headlineSmall, color = Rv.HeroText)
                Text(
                    when (orbState) {
                        OrbState.LISTENING -> t("Слушаю…")
                        OrbState.THINKING -> t("Думаю…")
                        OrbState.ERROR -> t("Не получилось")
                        OrbState.SUCCESS -> t("Готово")
                        OrbState.IDLE -> if (brainReady) t("Умный режим 🧠") else t("Ваш личный помощник")
                    },
                    style = MaterialTheme.typography.bodySmall, color = Rv.HeroMuted,
                )
            }
        }
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            NoaOrb(Modifier.size(orbSize), orbState, level)
        }
        LazyColumn(
            state = listState, modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(bubbles) { b -> BubbleRow(b) }
            if (bubbles.size <= 2 && pendingYes == null) {
                items(examples) { ex ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Text(
                            ex, style = MaterialTheme.typography.bodyMedium, color = Rv.HeroText,
                            modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.07f))
                                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(20.dp))
                                .pressable { send(ex) }.padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
        if (pendingYes != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(t("Да"), style = MaterialTheme.typography.labelLarge, color = Rv.Ink,
                    modifier = Modifier.clip(CircleShape).background(Rv.Peach).pressable { send(t("да")) }.padding(horizontal = 22.dp, vertical = 10.dp))
                Text(t("Нет"), style = MaterialTheme.typography.labelLarge, color = Rv.HeroText,
                    modifier = Modifier.clip(CircleShape).background(Rv.HeroSurface).pressable { send(t("нет")) }.padding(horizontal = 22.dp, vertical = 10.dp))
            }
        }
        // Поле ввода-«капсула» с переливающейся кромкой.
        Row(
            Modifier.fillMaxWidth().padding(12.dp).clip(CircleShape)
                .border(1.5.dp, androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Rv.Lavender, Color(0xFF6CE3FF), Rv.Peach)), CircleShape)
                .background(Rv.HeroSurface).padding(start = 6.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val scale by animateFloatAsState(if (listening) 1f + level * 0.25f else 1f, label = "mic")
            MicButton(active = listening, scale = scale) {
                if (listening) { listener.stop(); listening = false }
                else if (NoaListener(context).available()) {
                    askMic.launch(Manifest.permission.RECORD_AUDIO)
                } else say(t("На телефоне нет распознавания речи."))
            }
            androidx.compose.material3.TextField(
                value = if (listening && input.isEmpty()) partial else input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(if (listening) t("Слушаю…") else t("Скажите или напишите команду"), color = Rv.HeroMuted) },
                singleLine = true,
                colors = androidx.compose.material3.TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Rv.HeroText, unfocusedTextColor = Rv.HeroText, cursorColor = Rv.Peach,
                ),
            )
            if (input.isNotBlank()) IconButton(onClick = { send(input) }) { Icon(Icons.AutoMirrored.Filled.Send, t("Отправить"), tint = Rv.Peach) }
        }
    }
}

@Composable
private fun BubbleRow(b: Bubble) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (b.mine) Arrangement.End else Arrangement.Start) {
        if (b.mine) {
            Text(
                b.text, color = Rv.Ink, style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.clip(RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)).background(Rv.Peach).padding(horizontal = 14.dp, vertical = 10.dp),
            )
        } else {
            Text(
                b.text, color = Rv.HeroText, style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.clip(RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)).background(Color.White.copy(alpha = 0.07f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun MicButton(active: Boolean, scale: Float, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).scale(scale).clip(CircleShape)
            .background(if (active) Rv.Coral else Rv.Peach)
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(if (active) Icons.Default.GraphicEq else Icons.Default.Mic, t("Говорить"), tint = Rv.Ink) }
}

private fun startListening(listener: NoaListener, setListening: (Boolean) -> Unit, onLevel: (Float) -> Unit, onPartial: (String) -> Unit, onText: (String) -> Unit) {
    setListening(true)
    listener.start(object : NoaListener.Callback {
        override fun onLevel(level: Float) = onLevel(level)
        override fun onPartial(text: String) = onPartial(text)
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

// Без \b — на Android он не ловит кириллические границы. Сравниваем по словам.
private val YES = listOf("да", "ага", "давай", "подтвер", "так", "yes", "yeah", "ok", "окей", "добре")
private val NO = listOf("нет", "отмен", "ні", "no", "cancel", "скасуй", "неа")
private fun words(s: String) = s.lowercase().split(Regex("[^\\p{L}]+")).filter { it.isNotBlank() }
private fun isYes(s: String) = words(s).any { w -> YES.any { w == it || w.startsWith(it) } }
private fun isNo(s: String) = words(s).any { w -> NO.any { w == it || w.startsWith(it) } }

