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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.kartoteka.app.ui.KartotekaRoot
import com.kartoteka.app.ui.LockScreen
import com.kartoteka.app.ui.theme.KartotekaTheme
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private val app get() = application as KartotekaApp
    private var locked by mutableStateOf(false)
    private var pendingPersonId by mutableStateOf<Long?>(null)
    private var stoppedAt = 0L
    private var authInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        locked = app.settings.lockEnabled.value && savedInstanceState?.getBoolean(KEY_UNLOCKED) != true
        pendingPersonId = intent.personId()

        lifecycleScope.launch {
            app.settings.secureScreen.collect { secure ->
                if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }

        setContent {
            KartotekaTheme {
                if (locked) {
                    LockScreen(onUnlock = ::authenticate)
                } else {
                    KartotekaRoot(
                        openPersonId = pendingPersonId,
                        onPersonOpened = { pendingPersonId = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.personId()?.let { pendingPersonId = it }
    }

    override fun onResume() {
        super.onResume()
        if (locked) authenticate()
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
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                authInProgress = false
            }
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Картотека")
                .setSubtitle("Подтвердите, что это вы")
                .setAllowedAuthenticators(authenticators)
                .build()
        )
    }

    private fun Intent.personId(): Long? = getLongExtra(EXTRA_PERSON_ID, 0L).takeIf { it != 0L }

    companion object {
        const val EXTRA_PERSON_ID = "person_id"
        private const val KEY_UNLOCKED = "unlocked"
        private const val LOCK_TIMEOUT_MS = 60_000L

        fun canUseLock(activity: FragmentActivity): Boolean =
            BiometricManager.from(activity).canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
    }
}
