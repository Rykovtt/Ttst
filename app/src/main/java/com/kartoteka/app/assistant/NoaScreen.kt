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
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.alpha
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


/** Ноа открыли ярлыком с рабочего стола — начать слушать сразу. */
object NoaLaunch {
    var listenOnOpen by androidx.compose.runtime.mutableStateOf(false)
}

/**
 * Ассистент Ноа. Главный режим — голосовой: большая живая сфера, ваша речь крупно в реальном времени,
 * ответ под ней и озвучка. Слушает до паузы в речи (не обрывает на полуслове), после ответа слушает снова —
 * разговор без нажатий. Касание сферы: закончить фразу / перебить ответ / начать говорить.
 * Текстовый чат с историей — вторым режимом.
 */
@Composable
fun NoaScreen(onBack: () -> Unit, onOpenPerson: (Long) -> Unit, onOpenAppointment: (Long) -> Unit, onNavigate: (NoaIntent.Section) -> Unit = {}) {
    val app = app()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ctl = remember { NoaController(app, context, scope) }
    val name = app.settings.assistantName.value.collectAsState().value.ifBlank { "Ноа" }
    val brainOn by app.settings.assistantBrain.value.collectAsState()
    var voiceMode by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(true) }
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) ctl.startListening() else ctl.say(t("Нет доступа к микрофону — разрешите его в настройках телефона."))
    }
    ctl.conversational = { voiceMode }
    ctl.onOpenPerson = onOpenPerson
    ctl.onOpenAppointment = onOpenAppointment
    ctl.onNavigate = onNavigate
    ctl.requestMic = { askMic.launch(Manifest.permission.RECORD_AUDIO) }

    val bubbles = ctl.bubbles
    val listening = ctl.listening
    val level = ctl.level
    val live = ctl.live
    val pendingYes = ctl.pendingYes
    val brainStatus = ctl.brainStatus
    val orbState = ctl.orbState
    fun send(text: String, voice: Boolean = false) { input = ""; ctl.send(text, voice) }
    fun orbTap() = ctl.orbTap()

    LaunchedEffect(Unit) {
        if (app.settings.assistantVoice.value.value) NoaVoice.init(context)
        if (bubbles.isEmpty()) bubbles.add(Bubble(t("Привет! Я %1\$s. Записываю, переношу и отменяю встречи, пишу и читаю ответы, звоню, включаю музыку и видео, ставлю на паузу, прокладываю маршрут в любом навигаторе, запускаю приложения — и выполняю несколько команд подряд.", name), mine = false))
    }
    LaunchedEffect(brainOn) { ctl.prepareBrain(brainOn) }
    // Запуск с ярлыка «Ассистент» внутри приложения — сразу слушаем.
    LaunchedEffect(NoaLaunch.listenOnOpen) {
        if (NoaLaunch.listenOnOpen) {
            NoaLaunch.listenOnOpen = false
            voiceMode = true
            kotlinx.coroutines.delay(300)
            ctl.startVoice()
        }
    }
    LaunchedEffect(bubbles.size) { if (bubbles.isNotEmpty() && !voiceMode) listState.animateScrollToItem(bubbles.lastIndex) }
    DisposableEffect(Unit) { onDispose { ctl.dispose() } }

    if (voiceMode) {
        VoiceMode(
            name = name, status = brainStatus, orbState = orbState, level = level,
            listening = listening, thinking = ctl.thinking, speaking = ctl.speaking,
            live = live, answer = ctl.answer, confirm = pendingYes != null,
            onOrb = ::orbTap, onBack = onBack, onChat = { ctl.stopListening(); voiceMode = false },
            onYes = { send(t("да"), voice = true) }, onNo = { send(t("нет"), voice = true) },
        )
        return
    }

    val examples = listOf(
        t("Что у меня сегодня?"),
        t("Зайди в профиль Ани и добавь заметку: любит латте"),
        t("Найди плейлист хиты 90-х и включи в случайном порядке"),
        t("Напиши Илье, что буду через 10 минут, и прочитай ответ"),
        t("Проложи маршрут до Хрещатика 22 в Waze"),
        t("Перенеси Аню на пятницу в 15:00"),
        t("Скопируй номер Анны и вставь в Google"),
        t("Поставь на паузу"),
        t("Открой инстаграм Олега"),
    )

    // Текстовый чат: шапка с горами, история, поле ввода.
    // imePadding помечает клавиатуру учтённой: navigationBarsPadding поля ввода при открытой клавиатуре становится 0,
    // поле стоит вплотную к ней. (ime.exclude(navigationBars) здесь прятал поле под клавиатуру на высоту навигации.)
    Column(Modifier.fillMaxSize().background(com.kartoteka.app.ui.theme.RvColors.NoaBg).imePadding()) {
        com.kartoteka.app.ui.components.ScreenHero(backgroundKey = "noa", title = 
            t("Ассистент %1\$s", name), compact = true, onBack = onBack,
            subtitle = when (orbState) {
                OrbState.LISTENING -> t("Слушаю…")
                OrbState.THINKING -> t("Думаю…")
                else -> brainStatus.ifBlank { t("Ваш личный помощник") }
            },
            actions = { com.kartoteka.app.ui.components.HeroButton(Icons.Default.GraphicEq, t("Голосовой режим"), { voiceMode = true }) },
        )
        LazyColumn(
            state = listState, modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(bubbles) { b -> BubbleRow(b) }
            if (bubbles.size <= 1 && pendingYes == null) {
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
        if (pendingYes != null) YesNo(onYes = { send(t("да")) }, onNo = { send(t("нет")) }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        // Поле ввода — стекло как поиск на главном, кромка тёплая, как у плитки «Ноа».
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp).clip(CircleShape)
                .border(1.dp, com.kartoteka.app.ui.theme.RvColors.NoaBorder.copy(alpha = 0.7f), CircleShape)
                .background(com.kartoteka.app.ui.theme.RvColors.SearchBg).padding(start = 6.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val scale by animateFloatAsState(if (listening) 1f + level * 0.25f else 1f, label = "mic")
            MicButton(active = listening, scale = scale) { orbTap() }
            androidx.compose.material3.TextField(
                value = if (listening && input.isEmpty()) live else input,
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

/** Голосовой режим: сфера в центре, живой текст речи, ответ, подсказка состояния. */
@Composable
private fun VoiceMode(
    name: String, status: String, orbState: OrbState, level: Float,
    listening: Boolean, thinking: Boolean, speaking: Boolean,
    live: String, answer: String, confirm: Boolean,
    onOrb: () -> Unit, onBack: () -> Unit, onChat: () -> Unit, onYes: () -> Unit, onNo: () -> Unit,
) {
    com.kartoteka.app.ui.components.StatusBarOverDark(true)
    val lvl by animateFloatAsState(if (listening) level else 0f, androidx.compose.animation.core.tween(120), label = "lvl")
    val breathe = androidx.compose.animation.core.rememberInfiniteTransition(label = "glow")
    val pulse by breathe.animateFloat(
        0.18f, 0.32f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween<Float>(1800), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "pulse",
    )
    Box(Modifier.fillMaxSize().background(com.kartoteka.app.ui.theme.RvColors.NoaBg)) {
        com.kartoteka.app.ui.components.MountainBackdrop(Modifier.fillMaxSize().alpha(0.32f), key = "noa")
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                com.kartoteka.app.ui.components.HeroButton(Icons.AutoMirrored.Filled.ArrowBack, t("Назад"), onBack)
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Row(
                        Modifier.clip(CircleShape).background(com.kartoteka.app.ui.theme.RvColors.ChipBg).border(1.dp, com.kartoteka.app.ui.theme.RvColors.ChipBorder, CircleShape)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(com.kartoteka.app.ui.theme.RvColors.NoaBorder))
                        Spacer(Modifier.width(7.dp))
                        Text(status.ifBlank { name }, style = com.kartoteka.app.ui.theme.PeopleType.category, color = com.kartoteka.app.ui.theme.RvColors.ChipText, maxLines = 1)
                    }
                }
                com.kartoteka.app.ui.components.HeroButton(Icons.AutoMirrored.Filled.Chat, t("Текстовый чат"), onChat)
            }
            Spacer(Modifier.weight(0.8f))
            // Сфера со свечением: дышит в покое, пульсирует от голоса.
            Box(
                Modifier.size(300.dp).drawBehind {
                    val a = (pulse + lvl * 0.45f).coerceAtMost(0.8f)
                    drawCircle(
                        androidx.compose.ui.graphics.Brush.radialGradient(
                            listOf(com.kartoteka.app.ui.theme.RvColors.Violet.copy(alpha = a), com.kartoteka.app.ui.theme.RvColors.NoaBorder.copy(alpha = a * 0.35f), Color.Transparent),
                        ),
                    )
                }.pressable(onClick = onOrb),
                contentAlignment = Alignment.Center,
            ) {
                NoaOrb(Modifier.size(210.dp).scale(1f + lvl * 0.12f), orbState, level)
            }
            Spacer(Modifier.height(28.dp))
            androidx.compose.animation.AnimatedContent(
                live.ifBlank { if (answer.isBlank()) t("Чем помочь?") else "" }, label = "live",
                transitionSpec = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(160)) togetherWith androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120)) },
            ) { text ->
                Text(
                    text, color = if (live.isBlank()) com.kartoteka.app.ui.theme.RvColors.SearchHint else Color.White,
                    style = com.kartoteka.app.ui.theme.PeopleType.title.copy(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 4,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                )
            }
            if (answer.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    answer, color = com.kartoteka.app.ui.theme.RvColors.NavActive, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 24.sp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 7,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                )
            }
            if (confirm) {
                Spacer(Modifier.height(18.dp))
                YesNo(onYes = onYes, onNo = onNo)
            }
            Spacer(Modifier.weight(1f))
            Text(
                when {
                    listening -> t("Слушаю… коснитесь сферы, когда закончите")
                    thinking -> t("Думаю…")
                    speaking -> t("Коснитесь сферы, чтобы перебить")
                    else -> t("Коснитесь сферы и говорите")
                },
                style = com.kartoteka.app.ui.theme.PeopleType.category, color = com.kartoteka.app.ui.theme.RvColors.SearchHint,
                modifier = Modifier.padding(bottom = 22.dp),
            )
        }
    }
}

@Composable
internal fun YesNo(onYes: () -> Unit, onNo: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(t("Да"), style = MaterialTheme.typography.labelLarge, color = com.kartoteka.app.ui.theme.RvColors.ChipActiveText,
            modifier = Modifier.clip(CircleShape).background(com.kartoteka.app.ui.theme.RvColors.ChipActiveBg).pressable(onClick = onYes).padding(horizontal = 26.dp, vertical = 11.dp))
        Text(t("Нет"), style = MaterialTheme.typography.labelLarge, color = Rv.HeroText,
            modifier = Modifier.clip(CircleShape).background(com.kartoteka.app.ui.theme.RvColors.ChipBg).border(1.dp, com.kartoteka.app.ui.theme.RvColors.ChipBorder, CircleShape).pressable(onClick = onNo).padding(horizontal = 26.dp, vertical = 11.dp))
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

