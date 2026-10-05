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
import androidx.compose.foundation.layout.navigationBarsPadding
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

/** Ноа открыли ярлыком с рабочего стола — начать слушать сразу. */
object NoaLaunch {
    var listenOnOpen by androidx.compose.runtime.mutableStateOf(false)
}

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

    // Голосовой диалог: если спрашиваем «да/нет» или «кого именно», а человек говорил голосом — снова слушаем.
    var byVoice by remember { mutableStateOf(false) }
    var listenAgain by remember { mutableStateOf(0) }

    fun say(text: String, expectAnswer: Boolean = false) {
        bubbles.add(Bubble(text, mine = false))
        val again = expectAnswer && byVoice
        if (voiceOn) NoaVoice.speak(context, text) { if (again) listenAgain++ }
        else if (again) listenAgain++
    }

    // Переход на экран в цепочке откладываем до конца: экран Ноа должен дожить до последнего шага.
    var deferredNav: (() -> Unit)? = null
    var chain = false

    suspend fun apply(reply: Noa.Reply) {
        fun go(block: () -> Unit) { if (chain) deferredNav = block else block() }
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
                reply.personId?.let { id -> go { onOpenPerson(id) } }
                reply.appointmentId?.let { id -> go { onOpenAppointment(id) } }
            }
            is Noa.Reply.Do -> { say(reply.text); runCatching { reply.effect(context) } }
            is Noa.Reply.Choose -> { say(reply.text, expectAnswer = true); pendingYes = null }
            is Noa.Reply.Navigate -> { say(reply.text); go { onNavigate(reply.section) } }
            is Noa.Reply.Confirm -> { say(reply.text + "  " + t("Скажите «да» или «нет»."), expectAnswer = true); pendingYes = reply.onYes }
        }
    }

    /** Выполнить команду; цепочку — по шагам. Подтверждение ставит цепочку на паузу до «да». */
    suspend fun run(intent: NoaIntent) {
        val steps = (intent as? NoaIntent.Sequence)?.steps ?: listOf(intent)
        chain = steps.size > 1
        deferredNav = null
        for ((i, step) in steps.withIndex()) {
            val reply = noa.handleIntent(step)
            if (reply is Noa.Reply.Confirm && i < steps.lastIndex) {
                val rest = NoaIntent.Sequence(steps.drop(i + 1))
                apply(reply)
                val onYes = reply.onYes
                pendingYes = { val r = onYes(); apply(r); run(rest); Noa.Reply.Say("") }
                deferredNav?.invoke(); deferredNav = null
                return
            }
            apply(reply)
            if (reply is Noa.Reply.Choose || reply is Noa.Reply.Confirm) break
        }
        chain = false
        deferredNav?.invoke(); deferredNav = null
    }

    fun send(textRaw: String, voice: Boolean = false) {
        val text = textRaw.trim()
        if (text.isBlank()) return
        byVoice = voice
        bubbles.add(Bubble(text, mine = true))
        input = ""
        val yes = pendingYes
        scope.launch {
            thinking = true
            runCatching {
                when {
                    yes != null && isYes(text) -> { pendingYes = null; val r = yes(); if (!(r is Noa.Reply.Say && r.text.isEmpty())) apply(r) }
                    yes != null && isNo(text) -> { pendingYes = null; say(t("Хорошо, отменила.")) }
                    else -> {
                        pendingYes = null
                        // Правила понимают короткие команды мгновенно; модель — свободную речь и цепочки.
                        val rules = NoaParser.parse(text)
                        val smart = if (brainReady) runCatching { interpreter.interpret(text, names = noa.knownNames()) }.getOrNull() else null
                        val intent = smart?.intent
                        when {
                            intent != null && !(rules is NoaIntent.Sequence && intent !is NoaIntent.Sequence) -> run(intent)
                            rules !is NoaIntent.Unknown -> run(rules)
                            smart?.reply != null -> say(smart.reply)
                            else -> run(rules)
                        }
                    }
                }
            }.onSuccess { flash = OrbState.SUCCESS }
                .onFailure { flash = OrbState.ERROR; say(t("Что-то пошло не так. Попробуйте ещё раз.")) }
            thinking = false
        }
    }

    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startListening(listener, { listening = it; if (!it) { level = 0f; partial = "" } }, { level = it }, { partial = it }) { send(it, voice = true) }
        else say(t("Нет доступа к микрофону — разрешите его в настройках телефона."))
    }

    LaunchedEffect(Unit) {
        if (voiceOn) NoaVoice.init(context)
        if (bubbles.isEmpty()) bubbles.add(Bubble(t("Привет! Я %1\$s. Могу записать человека, позвонить, проложить маршрут, открыть его инстаграм, добавить заметку, рассказать, что у вас сегодня, — и выполнить несколько команд подряд.", name), mine = false))
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
    // Запуск с ярлыка «Ассистент» — сразу слушаем, как голосовой помощник.
    LaunchedEffect(NoaLaunch.listenOnOpen) {
        if (NoaLaunch.listenOnOpen) {
            NoaLaunch.listenOnOpen = false
            kotlinx.coroutines.delay(350)
            if (NoaListener(context).available()) askMic.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    LaunchedEffect(listenAgain) {
        if (listenAgain > 0 && !listening && NoaListener(context).available()) askMic.launch(Manifest.permission.RECORD_AUDIO)
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
        t("Что у меня сегодня?"),
        t("Зайди в профиль Ани и добавь заметку: любит латте"),
        t("Проложи маршрут к маме"),
        t("Открой инстаграм Олега"),
    )

    // Тёмный экран как плитка «Ноа» на главном: шапка с горами, сфера, стеклянные реплики.
    Column(Modifier.fillMaxSize().background(com.kartoteka.app.ui.theme.RvColors.NoaBg).imePadding()) {
        com.kartoteka.app.ui.components.ScreenHero(
            t("Ассистент %1\$s", name), compact = true, onBack = onBack,
            subtitle = when (orbState) {
                OrbState.LISTENING -> t("Слушаю…")
                OrbState.THINKING -> t("Думаю…")
                OrbState.ERROR -> t("Не получилось")
                OrbState.SUCCESS -> t("Готово")
                OrbState.IDLE -> if (brainReady) t("Умный режим 🧠") else t("Ваш личный помощник")
            },
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                NoaOrb(Modifier.size(orbSize), orbState, level)
            }
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
                            ex, style = MaterialTheme.typography.bodyMedium, color = com.kartoteka.app.ui.theme.RvColors.ChipText,
                            modifier = Modifier.clip(CircleShape).background(com.kartoteka.app.ui.theme.RvColors.ChipBg)
                                .border(1.dp, com.kartoteka.app.ui.theme.RvColors.ChipBorder, CircleShape)
                                .pressable { send(ex) }.padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
        if (pendingYes != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(t("Да"), style = MaterialTheme.typography.labelLarge, color = com.kartoteka.app.ui.theme.RvColors.ChipActiveText,
                    modifier = Modifier.clip(CircleShape).background(com.kartoteka.app.ui.theme.RvColors.ChipActiveBg).pressable { send(t("да")) }.padding(horizontal = 22.dp, vertical = 10.dp))
                Text(t("Нет"), style = MaterialTheme.typography.labelLarge, color = Rv.HeroText,
                    modifier = Modifier.clip(CircleShape).background(com.kartoteka.app.ui.theme.RvColors.ChipBg).border(1.dp, com.kartoteka.app.ui.theme.RvColors.ChipBorder, CircleShape).pressable { send(t("нет")) }.padding(horizontal = 22.dp, vertical = 10.dp))
            }
        }
        // Поле ввода — стекло как поиск на главном, кромка тёплая, как у плитки «Ноа».
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp).clip(CircleShape)
                .border(1.dp, com.kartoteka.app.ui.theme.RvColors.NoaBorder.copy(alpha = 0.7f), CircleShape)
                .background(com.kartoteka.app.ui.theme.RvColors.SearchBg).padding(start = 6.dp, end = 6.dp),
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
                placeholder = { Text(if (listening) t("Слушаю…") else t("Скажите или напишите команду"), color = com.kartoteka.app.ui.theme.RvColors.SearchHint) },
                singleLine = true,
                colors = androidx.compose.material3.TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Rv.HeroText, unfocusedTextColor = Rv.HeroText, cursorColor = com.kartoteka.app.ui.theme.RvColors.WarmLight,
                ),
            )
            if (input.isNotBlank()) IconButton(onClick = { send(input) }) { Icon(Icons.AutoMirrored.Filled.Send, t("Отправить"), tint = com.kartoteka.app.ui.theme.RvColors.WarmLight) }
        }
    }
}

@Composable
private fun BubbleRow(b: Bubble) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (b.mine) Arrangement.End else Arrangement.Start) {
        if (b.mine) {
            Text(
                b.text, color = com.kartoteka.app.ui.theme.RvColors.ChipActiveText, style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.clip(RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp)).background(com.kartoteka.app.ui.theme.RvColors.ChipActiveBg).padding(horizontal = 14.dp, vertical = 10.dp),
            )
        } else {
            Text(
                b.text, color = Rv.HeroText, style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.clip(RoundedCornerShape(22.dp, 22.dp, 22.dp, 6.dp)).background(com.kartoteka.app.ui.theme.RvColors.ChipBg)
                    .border(1.dp, com.kartoteka.app.ui.theme.RvColors.ChipBorder, RoundedCornerShape(22.dp, 22.dp, 22.dp, 6.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun MicButton(active: Boolean, scale: Float, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).scale(scale).clip(CircleShape)
            .background(
                if (active) androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Rv.Coral, Rv.Coral))
                else androidx.compose.ui.graphics.Brush.verticalGradient(listOf(com.kartoteka.app.ui.theme.RvColors.FabBodyTop, com.kartoteka.app.ui.theme.RvColors.FabBodyBottom))
            )
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(if (active) Icons.Default.GraphicEq else Icons.Default.Mic, t("Говорить"), tint = com.kartoteka.app.ui.theme.RvColors.DarkSurface) }
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

