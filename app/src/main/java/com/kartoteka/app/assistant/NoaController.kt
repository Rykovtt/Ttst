package com.kartoteka.app.assistant

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kartoteka.app.KartotekaApp
import com.kartoteka.app.data.PersonFull
import com.kartoteka.app.i18n.t
import com.kartoteka.app.messaging.Messaging
import com.kartoteka.app.ui.components.OrbState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class Bubble(val text: String, val mine: Boolean)

/**
 * Мозг разговора с Ноа — общий для экрана ассистента и «сферы поверх экрана».
 * Слушает (до паузы в речи), понимает (правила → модель), выполняет команды и цепочки, говорит ответ,
 * в голосовом режиме слушает снова. UI только рисует состояние и даёт переходы.
 */
class NoaController(private val app: KartotekaApp, private val context: Context, private val scope: CoroutineScope) {
    val noa = Noa(app)
    private val session = VoiceSession(context, silenceMs = 2000L)
    private val interpreter = NoaInterpreter(app.brain)

    val bubbles = mutableStateListOf<Bubble>()
    var listening by mutableStateOf(false); private set
    var thinking by mutableStateOf(false); private set
    var speaking by mutableStateOf(false); private set
    var level by mutableStateOf(0f); private set
    var live by mutableStateOf(""); private set
    var answer by mutableStateOf(""); private set
    var flash by mutableStateOf<OrbState?>(null)
    var pendingYes by mutableStateOf<(suspend () -> Noa.Reply)?>(null); private set
    var brainReady by mutableStateOf(false); private set
    /** Модель грузится не при открытии, а когда понадобилась (сфера по зову: ложные пробуждения не должны будить 4 ГБ). */
    var lazyBrain = false
    var brainStatus by mutableStateOf(""); private set

    /** После ответа снова слушать (голосовой режим). Иначе — только если задан вопрос «да/нет». */
    var conversational: () -> Boolean = { true }
    var voiceOn: () -> Boolean = { app.settings.assistantVoice.value.value }
    var onOpenPerson: (Long) -> Unit = {}
    var onOpenAppointment: (Long) -> Unit = {}
    var onNavigate: (NoaIntent.Section) -> Unit = {}
    /** Нет разрешения на микрофон — UI просит его и вызывает [startListening]. */
    var requestMic: () -> Unit = {}
    /** Разговор закончился: ничего не сказали или ответ договорён без продолжения. */
    var onIdle: () -> Unit = {}

    var alive = true
    private var byVoice = false
    private var leaving = false
    private var deferredNav: (() -> Unit)? = null
    private var chain = false
    /** Уточняющий вопрос, на который ждём ответ («Кому написать?»): следующая фраза читается вместе с ним. */
    private var pendingAsk: Clarify? = null
    /** Команда от модели ждёт «да/нет»: фраза и разбор — для журнала (если он включён); «нет» тоже полезно знать. */
    private class ModelPending(val phrase: String, val rules: String, val model: String, val reply: String)
    private var pendingModel: ModelPending? = null

    val orbState: OrbState get() = flash ?: when {
        listening -> OrbState.LISTENING
        thinking -> OrbState.THINKING
        else -> OrbState.IDLE
    }

    private val events = object : VoiceSession.Events {
        override fun onText(text: String) { live = text }
        override fun onLevel(l: Float) { level = l }
        override fun onListening(on: Boolean) { listening = on; syncWake() }
        override fun onPhrase(text: String) {
            if (text.isBlank()) onIdle() else send(text, voice = true)
        }
    }

    fun micAvailable() = session.available()

    /** Начать слушать (разрешение уже есть). */
    fun startListening() {
        scope.launch(kotlinx.coroutines.Dispatchers.IO) { NoaHints.refresh(app) }
        NoaVoice.stop(); speaking = false; live = ""
        session.start(events)
    }

    fun startVoice() {
        if (!session.available()) { say(t("На телефоне нет распознавания речи.")); return }
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) startListening() else requestMic()
    }

    /** Касание сферы: договорил — отправить; говорит или молчит — слушать. */
    fun orbTap() { if (listening) session.finishNow() else startVoice() }

    fun stopListening() = session.stop()

    fun dispose() { alive = false; session.stop(); WakeService.setBusy(false) }

    /** Пока ассистент слушает, думает или говорит — служба пробуждения молчит и отдаёт ему микрофон. */
    private fun syncWake() = WakeService.setBusy(alive && (listening || thinking || speaking))

    /** Проснулась по имени: коротко отвечает «Готова» и сразу слушает команду. */
    fun greet(text: String) {
        bubbles.add(Bubble(text, mine = false)); answer = text
        val go = { speaking = false; if (alive) startVoice() }
        if (voiceOn()) { speaking = true; syncWake(); NoaVoice.speak(context, text) { go() } } else go()
    }

    fun say(text: String, expectAnswer: Boolean = false) {
        if (text.isBlank()) return
        bubbles.add(Bubble(text, mine = false))
        answer = text
        val again = byVoice && !leaving && (conversational() || expectAnswer)
        val after = {
            speaking = false
            if (alive) { if (again && !listening) startVoice() else if (!expectAnswer) scope.launch { delay(250); if (!listening && !thinking) onIdle() } }
        }
        if (voiceOn()) { speaking = true; syncWake(); NoaVoice.speak(context, text) { after(); syncWake() } } else after()
    }

    private suspend fun apply(reply: Noa.Reply) {
        fun go(block: () -> Unit) { leaving = true; if (chain) deferredNav = block else block() }
        when (reply) {
            is Noa.Reply.Say -> say(reply.text)
            is Noa.Reply.Say2Open -> {
                if (reply.personId != null || reply.appointmentId != null) leaving = true
                say(reply.text)
                NoaActions.pendingCall?.let { Messaging.dial(context, it); NoaActions.pendingCall = null }
                NoaActions.pendingMessage?.let { m ->
                    sendMessage(context, app.repository.getPerson(m.personId), m)
                    NoaActions.pendingMessage = null
                }
                reply.personId?.let { id -> go { onOpenPerson(id) } }
                reply.appointmentId?.let { id -> go { onOpenAppointment(id) } }
            }
            is Noa.Reply.Do -> {
                leaving = true
                // Пауза/громче — без голоса, чтобы не перебивать музыку.
                if (reply.quiet) { bubbles.add(Bubble(reply.text, mine = false)); answer = reply.text; scope.launch { delay(400); onIdle() } }
                else say(reply.text)
                runCatching { reply.effect(context) }
            }
            is Noa.Reply.Choose -> { say(reply.text, expectAnswer = true); pendingYes = null }
            is Noa.Reply.Navigate -> { leaving = true; say(reply.text); go { onNavigate(reply.section) } }
            is Noa.Reply.Confirm -> { say(reply.text + "  " + t("Скажите «да» или «нет»."), expectAnswer = true); pendingYes = reply.onYes }
        }
    }

    /** Выполнить команду; цепочку — по шагам. Подтверждение ставит цепочку на паузу до «да». */
    private suspend fun run(intent: NoaIntent, modelReply: String? = null) {
        val steps = (intent as? NoaIntent.Sequence)?.steps ?: listOf(intent)
        chain = steps.size > 1
        deferredNav = null
        for ((i, step) in steps.withIndex()) {
            var reply = noa.handleIntent(step)
            if (steps.size == 1 && !modelReply.isNullOrBlank()) reply = when (reply) {
                is Noa.Reply.Do -> reply.copy(text = modelReply)
                is Noa.Reply.Navigate -> reply.copy(text = modelReply)
                else -> reply
            }
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

    /** Что было с последней фразой: для «это не то» и журнала. */
    private data class Exchange(val phrase: String, val rules: String, val model: String, val reply: String)
    private var lastExchange: Exchange? = null

    private fun log(kind: String, phrase: String, rules: NoaIntent?, model: NoaIntent? = null, reply: String = "") =
        logSig(kind, phrase, NoaBenchmark.signature(rules), if (model != null) NoaBenchmark.signature(model) else "", reply)

    private fun logSig(kind: String, phrase: String, rules: String, model: String, reply: String) {
        if (!app.settings.assistantLog.value.value) return
        NoaFeedbackLog.add(context, kind, phrase, rules, model, reply)
    }

    /** После «это не то» следующая фраза — как надо было; записываем её рядом с ошибочной. */
    private var fixFor: String? = null

    /** Последний обмен (моя реплика и фраза человека до текущей) — короткая память для модели. */
    private fun recentDialogue(): String =
        bubbles.dropLast(1).takeLast(2).joinToString("\n") { (if (it.mine) "User" else "Noa") + ": " + it.text }

    fun send(textRaw: String, voice: Boolean = false) {
        val text = textRaw.trim()
        if (text.isBlank()) return
        byVoice = voice
        leaving = false
        bubbles.add(Bubble(text, mine = true))
        live = text
        answer = ""
        val yes = pendingYes
        val modelCmd = pendingModel
        pendingModel = null
        val asked = pendingAsk
        pendingAsk = null
        NoaParser.setAssistantName(app.settings.assistantName.value.value)
        scope.launch {
            thinking = true; syncWake()
            fixFor?.let { logSig("fix", text, "", "", "к фразе: $it"); fixFor = null }
            runCatching {
                // Поправка к ожидающей записи («нет, на пятницу», «отмена», «повтори») — раньше, чем обычное «нет».
                val yn = NoaParser.yesNo(text)
                val fix = if (yes != null && yn == true) null else noa.continueBooking(text)
                when {
                    yes != null && yn == true -> {
                        pendingYes = null
                        modelCmd?.let { logSig("model", it.phrase, it.rules, it.model, "confirmed") }
                        val r = yes(); if (!(r is Noa.Reply.Say && r.text.isEmpty())) apply(r)
                    }
                    fix != null -> { pendingYes = null; run(fix) }
                    yes != null && yn == false -> {
                        pendingYes = null
                        modelCmd?.let { logSig("model", it.phrase, it.rules, it.model, "denied") }
                        say(t("Хорошо, отменила."))
                    }
                    else -> {
                        pendingYes = null
                        // Правила — точные и мгновенные: если поняли команду (в т.ч. цепочку), выполняем их разбор.
                        // Модель — только для того, что правила не поняли: свободная речь и разговор.
                        val rules = NoaParser.parse(text)
                        if (rules is NoaIntent.Wrong) {
                            // «Это не то»: отмечаем прошлую команду в журнале (если он включён) и просим сказать, как надо.
                            lastExchange?.let { logSig("wrong", it.phrase, it.rules, it.model, it.reply); fixFor = it.phrase }
                            say(t("Поняла, что ошиблась. Скажите, что нужно было сделать, — я запишу."), expectAnswer = true)
                            return@runCatching
                        }
                        // Правила поняли команду, но человека с таким именем нет («запись ильи рыкова») —
                        // скорее всего, фраза разобрана неверно: пусть её прочитает модель.
                        val steps = (rules as? NoaIntent.Sequence)?.steps ?: listOf(rules)
                        val doubtfulSteps = steps.any { st ->
                            !noa.knows(NoaParser.personOf(st)) &&
                                !(st is NoaIntent.Open && PhoneActions.find(context, st.personQuery) != null) &&
                                // Маршрут в любое место и переписка с теми, кого нет в книжке, — не ошибка разбора.
                                !(st is NoaIntent.Route && st.place.isNotBlank()) && st !is NoaIntent.Reply && st !is NoaIntent.ReadMessages
                        }
                        // Сфера по зову «Ноа» не грузит тяжёлую модель заранее: только когда правила не справились.
                        if (lazyBrain && !brainReady && (rules is NoaIntent.Unknown || doubtfulSteps) && app.settings.assistantBrain.value.value) {
                            // Загрузка умной модели — десятки секунд: говорим об этом, а не молчим («зависла»).
                            val wait = t("Секунду, включаю умный режим…")
                            answer = wait; bubbles.add(Bubble(wait, mine = false))
                            prepareBrain(true)
                        }
                        val doubtful = brainReady && doubtfulSteps
                        // Ответ на уточняющий вопрос — короткая фраза без команды («Ане», «в пять»): правила её не разбирают.
                        val answering = asked != null && rules is NoaIntent.Unknown
                        if (rules !is NoaIntent.Unknown && !doubtful) { lastExchange = Exchange(text, NoaBenchmark.signature(rules), "", ""); run(rules) }
                        else if (answering || brainReady) {
                            // Модель думает на фоновом потоке; если долго — показываем, что не зависли (без голоса, чтобы не мешать микрофону).
                            val slow = scope.launch { delay(7000); if (thinking && answer.isBlank()) answer = t("Думаю…") }
                            val smart = runCatching {
                                kotlinx.coroutines.withTimeoutOrNull(BRAIN_TIMEOUT_MS) {
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        val names = noa.knownNames(200)
                                        val last = noa.lastPerson != null
                                        if (answering) interpreter.answer(asked!!, text, names = names, context = noa.contextFor(asked.phrase + " " + text), history = recentDialogue(), hasLast = last)
                                        else interpreter.interpret(text, names = names, context = noa.contextFor(text), history = recentDialogue(), hasLast = last)
                                    }
                                }
                            }.getOrNull()
                            slow.cancel()
                            when {
                                // Не хватило данных — спрашиваем и ждём ответ (микрофон остаётся открытым).
                                smart?.ask != null -> { pendingAsk = smart.ask; say(smart.ask.question, expectAnswer = true) }
                                smart?.intent != null -> {
                                    lastExchange = Exchange(text, NoaBenchmark.signature(rules), NoaBenchmark.signature(smart.intent), smart.reply.orEmpty())
                                    val intent = smart.intent
                                    if (NoaTrust.needsConfirm(intent)) {
                                        // Модель ненадёжна: всё, что пишет, звонит, меняет данные или лезет в другие приложения, — только после «да».
                                        pendingModel = ModelPending(text, NoaBenchmark.signature(rules), NoaBenchmark.signature(intent), smart.reply.orEmpty())
                                        apply(Noa.Reply.Confirm(NoaDescribe.confirmQuestion(intent)) { run(intent, smart.reply); Noa.Reply.Say("") })
                                    } else { log("model", text, rules, intent); run(intent, smart.reply) }
                                }
                                !smart?.reply.isNullOrBlank() -> say(smart!!.reply!!)
                                // Модель промолчала или вернула мусор: подсказка по теме фразы вместо общего «не поняла».
                                rules is NoaIntent.Unknown -> { log("unknown", text, rules, reply = app.brain.lastError.orEmpty()); say(NoaFallback.message(if (answering) asked!!.phrase + " " + text else text)) }
                                else -> { lastExchange = Exchange(text, NoaBenchmark.signature(rules), "", ""); run(rules) }
                            }
                        } else if (rules is NoaIntent.Unknown) { log("unknown", text, rules); say(NoaFallback.message(text)) }
                        else { lastExchange = Exchange(text, NoaBenchmark.signature(rules), "", ""); run(rules) }
                    }
                }
            }.onSuccess { flash = OrbState.SUCCESS }
                .onFailure { flash = OrbState.ERROR; log("error", text, null, null, it.message.orEmpty()); say(t("Что-то пошло не так. Попробуйте ещё раз.")) }
            thinking = false; syncWake()
            delay(700); flash = null
        }
    }

    /** Подготовить модель в фоне; состояние — короткой строкой. */
    suspend fun prepareBrain(on: Boolean) {
        brainReady = false
        if (!on || !app.brain.supported) { brainStatus = t("Быстрые команды"); return }
        if (app.brain.hasModel()) brainStatus = t("Просыпаюсь…")
        brainStatus = when (app.brain.prepare()) {
            LlmBrain.State.READY -> {
                // «Готова» — только если модель реально ответила, а не просто загрузилась.
                if (app.brain.selfTest()) {
                    brainReady = true
                    t("ИИ на устройстве") + when (app.brain.backend) { "gpu" -> " · GPU"; "cpu" -> " · CPU"; else -> "" }
                } else {
                    app.brain.detail.takeIf { it.isNotBlank() }?.let { bubbles.add(Bubble(it, mine = false)) }
                    t("ИИ не отвечает · быстрые команды")
                }
            }
            LlmBrain.State.NEEDS_MODEL -> {
                val dl = app.brain.syncDownload()
                if (dl is LlmBrain.Download.Running) t("Модель скачивается · %1\$s", "${dl.percent}%") else t("Без ИИ · скачайте модель")
            }
            else -> {
                // Модель не запустилась (чаще всего — не хватило памяти): объясняем один раз, работаем на правилах.
                app.brain.detail.takeIf { it.isNotBlank() }?.let { bubbles.add(Bubble(it, mine = false)) }
                if (app.brain.crashed) t("Модель не запустилась · быстрые команды") else t("Быстрые команды")
            }
        }
    }
}

internal fun sendMessage(context: Context, pf: PersonFull?, m: NoaActions.Message) {
    pf ?: return
    when (m.channel) {
        NoaIntent.Channel.WHATSAPP -> pf.whatsapp?.let { Messaging.whatsapp(context, it, m.text) }
        NoaIntent.Channel.TELEGRAM -> pf.telegram?.let { Messaging.telegram(context, it, m.text) }
        NoaIntent.Channel.SMS -> pf.phone?.let { Messaging.sms(context, listOf(it), m.text) }
    }
}

/** Дольше модель не ждём: лучше подсказка, чем зависший ассистент. */
private const val BRAIN_TIMEOUT_MS = 60_000L

// Без \b — на Android он не ловит кириллические границы. Сравниваем по словам.
private val YES = listOf("да", "ага", "давай", "подтвер", "так", "yes", "yeah", "ok", "окей", "добре")
private val NO = listOf("нет", "отмен", "ні", "no", "cancel", "скасуй", "неа")
private fun words(s: String) = s.lowercase().split(Regex("[^\\p{L}]+")).filter { it.isNotBlank() }
internal fun isYes(s: String) = words(s).any { w -> YES.any { w == it || w.startsWith(it) } }
internal fun isNo(s: String) = words(s).any { w -> NO.any { w == it || w.startsWith(it) } }
