package com.kartoteka.app

import android.content.Intent
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
import com.kartoteka.app.ui.KartotekaRoot
import com.kartoteka.app.ui.LockScreen
import com.kartoteka.app.ui.theme.KartotekaTheme
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private val app get() = application as KartotekaApp
    private var locked by mutableStateOf(false)
    private var pendingPersonId by mutableStateOf<Long?>(null)
    private var pendingAppointmentId by mutableStateOf<Long?>(null)
    private var pendingReminderId: Long? = null
    private var stoppedAt = 0L
    private var authInProgress = false

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
                    LockScreen(title = AppIcons.title(this, custom), onUnlock = ::authenticate)
                } else {
                    KartotekaRoot(
                        openPersonId = pendingPersonId,
                        onPersonOpened = { pendingPersonId = null },
                        openAppointmentId = pendingAppointmentId,
                        onAppointmentOpened = { pendingAppointmentId = null },
                    )
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
        if (locked) authenticate() else runPendingReminder()
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
        val authenticators = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            // На устройстве нет ни отпечатка, ни PIN — блокировка невозможна.
            locked = false
            return
        }
        authInProgress = true
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                authInProgress = false
                locked = false
                runPendingReminder()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                authInProgress = false
            }
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(AppIcons.title(this, app.settings.appTitle.value.value))
                .setSubtitle("Подтвердите, что это вы")
                .setAllowedAuthenticators(authenticators)
                .build()
        )
    }

    /** Название в списке недавних приложений — под маскировку. */
    fun updateTaskTitle() {
        @Suppress("DEPRECATION")
        setTaskDescription(android.app.ActivityManager.TaskDescription(AppIcons.title(this, app.settings.appTitle.value.value)))
    }

    private fun Intent.personId(): Long? = getLongExtra(EXTRA_PERSON_ID, 0L).takeIf { it != 0L }

    companion object {
        const val EXTRA_PERSON_ID = "person_id"
        const val EXTRA_APPOINTMENT_ID = "appointment_id"
        const val EXTRA_SEND_REMINDER = "send_reminder"
        const val EXTRA_STOP_AUTOSEND = "stop_autosend"
        private const val KEY_UNLOCKED = "unlocked"
        private const val LOCK_TIMEOUT_MS = 60_000L

        fun canUseLock(activity: FragmentActivity): Boolean =
            BiometricManager.from(activity).canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
    }
}
