package com.kartoteka.app

import com.kartoteka.app.i18n.t

import android.content.Intent
import android.hardware.SensorManager
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.kartoteka.app.messaging.AutoSend
import com.kartoteka.app.reminders.Reminders
import com.kartoteka.app.security.IntruderCamera
import com.kartoteka.app.security.ShakeDetector
import com.kartoteka.app.ui.IntruderAlert
import com.kartoteka.app.ui.settings.IntruderLogDialog
import kotlinx.coroutines.Dispatchers
import com.kartoteka.app.ui.KartotekaRoot
import com.kartoteka.app.ui.LockScreen
import com.kartoteka.app.ui.theme.KartotekaTheme
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private val app get() = application as KartotekaApp
    private var locked by mutableStateOf(false)
    private var pendingPersonId by mutableStateOf<Long?>(null)
    private var pendingAppointmentId by mutableStateOf<Long?>(null)
    private var openNoa by mutableStateOf(false)
    private var pendingReminderId: Long? = null
    private var stoppedAt = 0L
    private var authInProgress = false
    private var lastSnapAt = 0L
    /** Сколько неудачных попыток было, пока нас не было (показываем после входа). */
    private var intruderAlert by mutableStateOf(0)
    private var showIntruders by mutableStateOf(false)
    private val shake = ShakeDetector { onShake() }
    private val sensors by lazy { getSystemService(SENSOR_SERVICE) as SensorManager }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        locked = app.settings.lockEnabled.value && savedInstanceState?.getBoolean(KEY_UNLOCKED) != true
        handleIntent(intent)

        lifecycleScope.launch {
            app.settings.appTitle.value.collect { updateTaskTitle() }
        }
        lifecycleScope.launch {
            app.settings.secureScreen.collect { secure ->
                if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }

        setContent {
            KartotekaTheme {
                if (locked) {
                    val custom by app.settings.appTitle.value.collectAsState()
                    val pin = app.pinLock
                    LockScreen(
                        title = AppIcons.title(this, custom),
                        pinLength = if (pin.hasPin) pin.length else null,
                        biometric = app.settings.biometric.value && canUseBiometric(this),
                        onBiometric = ::authenticate,
                        onPin = { entered -> pin.verify(entered).also { if (it) unlock() else onWrongPin() } },
                        waitMillis = { pin.waitMillis() },
                    )
                } else {
                    KartotekaRoot(
                        openPersonId = pendingPersonId,
                        onPersonOpened = { pendingPersonId = null },
                        openAppointmentId = pendingAppointmentId,
                        onAppointmentOpened = { pendingAppointmentId = null },
                        openNoa = openNoa,
                        onNoaOpened = { openNoa = false },
                    )
                    if (intruderAlert > 0) {
                        IntruderAlert(
                            count = intruderAlert,
                            onShow = { intruderAlert = 0; showIntruders = true },
                            onDismiss = { intruderAlert = 0; app.intruders.markSeen() },
                        )
                    }
                    if (showIntruders) IntruderLogDialog(onDismiss = { showIntruders = false })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
        if (!locked) runPendingReminder()
    }

    private fun handleIntent(intent: Intent) {
        if (intent.component?.className?.endsWith(".assistant.NoaLauncher") == true || intent.getBooleanExtra(EXTRA_OPEN_NOA, false)) openNoa = true
        intent.personId()?.let { pendingPersonId = it }
        intent.getLongExtra(EXTRA_APPOINTMENT_ID, 0L).takeIf { it != 0L }?.let { pendingAppointmentId = it }
        intent.getLongExtra(EXTRA_SEND_REMINDER, 0L).takeIf { it != 0L }?.let { pendingReminderId = it }
        if (intent.getBooleanExtra(EXTRA_STOP_AUTOSEND, false)) AutoSend.stop()
    }

    /** Пользователь нажал уведомление «Напомнить …» — отправляем, как только приложение открыто. */
    private fun runPendingReminder() {
        val id = pendingReminderId ?: return
        pendingReminderId = null
        lifecycleScope.launch {
            val r = app.repository.getReminder(id)
            Reminders.fire(app, id, interactive = true)
            if (r != null) pendingAppointmentId = r.appointmentId
        }
    }

    override fun onResume() {
        super.onResume()
        shake.start(sensors)
        if (locked) authenticate() else runPendingReminder()
    }

    override fun onPause() {
        super.onPause()
        shake.stop(sensors)
    }

    /** Кнопка «Закрыть сейф»: блокируем (если включена блокировка) и убираем из недавних. */
    fun closeVault() {
        if (app.settings.lockEnabled.value) locked = true
        finishAndRemoveTask()
    }

    /** Встряхнули: закрываем архив и убираем его из списка недавних приложений. */
    private fun onShake() {
        if (!app.settings.shakeToClose.value.value) return
        if (app.settings.lockEnabled.value) locked = true
        finishAndRemoveTask()
    }

    /** Неверный PIN на экране блокировки: пишем в журнал и, если включено, тихо фотографируем. */
    private fun onWrongPin() {
        val time = System.currentTimeMillis()
        app.intruders.record(time, null)
        if (!app.settings.intruderPhoto.value.value || time - lastSnapAt < SNAP_INTERVAL_MS) return
        lastSnapAt = time
        IntruderCamera.snap(this) { jpeg ->
            if (jpeg != null) app.appScope.launch(Dispatchers.IO) { app.intruders.attachPhoto(time, jpeg) }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) stoppedAt = SystemClock.elapsedRealtime()
    }

    override fun onRestart() {
        super.onRestart()
        // Блокируем снова, если приложение было в фоне дольше минуты.
        if (app.settings.lockEnabled.value && SystemClock.elapsedRealtime() - stoppedAt > LOCK_TIMEOUT_MS) {
            locked = true
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_UNLOCKED, !locked)
    }

    fun authenticate() {
        if (authInProgress) return
        if (app.pinLock.hasPin) {
            // Свой PIN-код приложения; отпечаток — по желанию. Без отпечатка просто показываем клавиатуру.
            if (app.settings.biometric.value && canUseBiometric(this)) {
                showPrompt(BIOMETRIC_WEAK, negativeText = t("PIN-код"))
            }
            return
        }
        // Блокировка из версий до 1.4: PIN или отпечаток самого телефона.
        val authenticators = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            unlock()
            return
        }
        showPrompt(authenticators, negativeText = null)
    }

    fun unlock() {
        locked = false
        if (app.pinLock.hasPin) intruderAlert = app.intruders.unseen
        runPendingReminder()
    }

    private fun showPrompt(authenticators: Int, negativeText: String?) {
        authInProgress = true
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                authInProgress = false
                unlock()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                authInProgress = false
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(AppIcons.title(this, app.settings.appTitle.value.value))
            .setSubtitle(t("Подтвердите, что это вы"))
            .setAllowedAuthenticators(authenticators)
        if (negativeText != null) info.setNegativeButtonText(negativeText)
        prompt.authenticate(info.build())
    }

    /** Название в списке недавних приложений — под маскировку. */
    fun updateTaskTitle() {
        @Suppress("DEPRECATION")
        setTaskDescription(android.app.ActivityManager.TaskDescription(AppIcons.title(this, app.settings.appTitle.value.value)))
    }

    private fun Intent.personId(): Long? = getLongExtra(EXTRA_PERSON_ID, 0L).takeIf { it != 0L }

    companion object {
        const val EXTRA_PERSON_ID = "person_id"
        const val EXTRA_OPEN_NOA = "open_noa"
        const val EXTRA_APPOINTMENT_ID = "appointment_id"
        const val EXTRA_SEND_REMINDER = "send_reminder"
        const val EXTRA_STOP_AUTOSEND = "stop_autosend"
        private const val KEY_UNLOCKED = "unlocked"
        private const val LOCK_TIMEOUT_MS = 60_000L
        private const val SNAP_INTERVAL_MS = 5_000L

        fun canUseBiometric(context: android.content.Context): Boolean =
            BiometricManager.from(context).canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

        fun canUseLock(activity: FragmentActivity): Boolean =
            BiometricManager.from(activity).canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
    }
}
