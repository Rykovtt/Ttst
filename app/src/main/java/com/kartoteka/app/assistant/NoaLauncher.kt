package com.kartoteka.app.assistant

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kartoteka.app.KartotekaApp
import com.kartoteka.app.MainActivity
import com.kartoteka.app.i18n.t
import com.kartoteka.app.ui.components.NoaOrb
import com.kartoteka.app.ui.components.pressable
import com.kartoteka.app.ui.theme.KartotekaTheme
import com.kartoteka.app.ui.theme.PeopleType
import com.kartoteka.app.ui.theme.RvColors
import kotlinx.coroutines.launch

/**
 * Иконка «Ассистент» на рабочем столе: сфера Ноа появляется поверх текущего экрана, сразу слушает,
 * выполняет команду, отвечает и сама закрывается. Само приложение (карточки, разделы) — только через вход с PIN.
 * Если в настройках «Сфера поверх экрана» выключена — открывает приложение с ассистентом, как раньше.
 */
open class NoaLauncher : ComponentActivity() {
    /** Открыта голосовым зовом («Ноа…»), а не иконкой: здороваемся и сразу слушаем. */
    protected open val byWake: Boolean get() = false
    private var controller: NoaController? = null
    private val askMic = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) controller?.startListening() else controller?.say(t("Нет доступа к микрофону — разрешите его в настройках телефона."))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as KartotekaApp
        if (!app.settings.assistantOverlay.value.value) {
            openApp { putExtra(MainActivity.EXTRA_OPEN_NOA, true).putExtra(MainActivity.EXTRA_NOA_LISTEN, true) }
            return
        }
        // Сфера по зову при заблокированном экране — только если человек разрешил это в настройках.
        if (byWake && android.os.Build.VERSION.SDK_INT >= 27 && app.settings.assistantWakeLocked.value.value &&
            getSystemService(android.app.KeyguardManager::class.java)?.isKeyguardLocked == true) {
            setShowWhenLocked(true); setTurnScreenOn(true)
        }
        enableEdgeToEdge()
        setContent {
            KartotekaTheme {
                val scope = rememberCoroutineScope()
                val ctl = remember {
                    NoaController(app, this, scope).also { c ->
                        controller = c
                        c.conversational = { false }           // слушаем снова, только если задан вопрос
                        c.requestMic = { askMic.launch(Manifest.permission.RECORD_AUDIO) }
                        c.onIdle = { close() }
                        c.onOpenPerson = { id -> openApp { putExtra(MainActivity.EXTRA_PERSON_ID, id) } }
                        c.onOpenAppointment = { id -> openApp { putExtra(MainActivity.EXTRA_APPOINTMENT_ID, id) } }
                        c.onNavigate = { openApp { this } }
                    }
                }
                val appear = remember { Animatable(0f) }
                LaunchedEffect(Unit) {
                    if (app.settings.assistantVoice.value.value) NoaVoice.init(this@NoaLauncher)
                    launch { appear.animateTo(1f, tween(260)) }
                    kotlinx.coroutines.delay(150)
                    if (byWake) ctl.greet(if (intent.getBooleanExtra(WakeService.EXTRA_PING, false)) t("Да, я здесь. Слушаю.") else t("Готова. Слушаю."))
                    else ctl.startVoice()
                }
                LaunchedEffect(Unit) {
                    if (byWake) ctl.lazyBrain = true else ctl.prepareBrain(app.settings.assistantBrain.value.value)
                }

                Box(
                    Modifier.fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.42f * appear.value))
                        .clickable(remember { MutableInteractionSource() }, indication = null) { close() },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Column(
                        Modifier.navigationBarsPadding().padding(14.dp).fillMaxWidth()
                            .graphicsLayer { alpha = appear.value; translationY = (1f - appear.value) * 80f }
                            .clip(RoundedCornerShape(34.dp))
                            .background(RvColors.NoaBg.copy(alpha = 0.95f))
                            .border(1.dp, RvColors.NoaBorder.copy(alpha = 0.45f), RoundedCornerShape(34.dp))
                            .clickable(remember { MutableInteractionSource() }, indication = null) { }
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.size(170.dp).pressable { ctl.orbTap() }, contentAlignment = Alignment.Center) {
                            NoaOrb(Modifier.size(170.dp), ctl.orbState, ctl.level)
                        }
                        Spacer(Modifier.height(6.dp))
                        AnimatedContent(
                            ctl.live.ifBlank { if (ctl.listening) t("Слушаю…") else t("Чем помочь?") }, label = "live",
                            transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(100)) },
                        ) { text ->
                            Text(
                                text, color = if (ctl.live.isBlank()) RvColors.SearchHint else Color.White,
                                style = PeopleType.title.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
                                textAlign = TextAlign.Center, maxLines = 4, modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (ctl.answer.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                ctl.answer, color = RvColors.NavActive, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp),
                                textAlign = TextAlign.Center, maxLines = 6, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (ctl.pendingYes != null) {
                            Spacer(Modifier.height(14.dp))
                            YesNo(onYes = { ctl.send(t("да"), voice = true) }, onNo = { ctl.send(t("нет"), voice = true) })
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            when {
                                ctl.listening -> t("Слушаю… коснитесь сферы, когда закончите")
                                ctl.thinking -> t("Думаю…")
                                else -> t("Коснитесь сферы и говорите")
                            },
                            style = PeopleType.category, color = RvColors.SearchHint,
                        )
                    }
                }
            }
        }
    }

    /** Открыть приложение (через вход/PIN) и закрыть сферу. */
    private fun openApp(extras: Intent.() -> Intent) {
        startActivity(
            Intent(this, MainActivity::class.java).extras()
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        close()
    }

    private fun close() {
        controller?.dispose()
        if (!isFinishing) finish()
        @Suppress("DEPRECATION") overridePendingTransition(0, android.R.anim.fade_out)
    }

    override fun onStop() {
        super.onStop()
        // Ушли со сферы (открылось другое приложение, свернули) — она своё дело сделала.
        if (!isChangingConfigurations) close()
    }
}

/** Та же сфера, но её вызывает голос («Ноа, ты тут?»). Отдельный компонент: он включён всегда, не зависит от иконки. */
class NoaWake : NoaLauncher() {
    override val byWake: Boolean get() = true
}
