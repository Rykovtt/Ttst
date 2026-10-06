package com.rykov.autosend.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.rykov.autosend.R
import com.rykov.autosend.core.AutoSendContract
import com.rykov.autosend.core.EnabledServices
import com.rykov.autosend.services.AutoSendAccessibilityService

/**
 * Экран проверки: включена ли служба, инструкция с переходом в настройки
 * и тестовый запуск, повторяющий то, что делает CRM.
 */
class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var instruction: TextView
    private lateinit var testPhone: EditText
    private lateinit var testText: EditText
    private lateinit var testResult: TextView

    /** Ответ службы (тот же ACTION_SEND_RESULT, что получает CRM). */
    private val resultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val text = getString(resultText(intent.getStringExtra(AutoSendContract.EXTRA_STATUS)))
            testResult.text = text
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.status)
        instruction = findViewById(R.id.instruction)
        testPhone = findViewById(R.id.test_phone)
        testText = findViewById(R.id.test_text)
        testResult = findViewById(R.id.test_result)
        findViewById<Button>(R.id.open_settings).setOnClickListener { openAccessibilitySettings() }
        findViewById<Button>(R.id.test_whatsapp).setOnClickListener { runTest("com.whatsapp") }
        findViewById<Button>(R.id.test_whatsapp_business).setOnClickListener { runTest("com.whatsapp.w4b") }

        // Регистрируем на всё время жизни экрана: ответ приходит, пока на экране WhatsApp.
        val filter = IntentFilter(AutoSendContract.ACTION_SEND_RESULT)
        val flags = if (Build.VERSION.SDK_INT >= 33) RECEIVER_NOT_EXPORTED else 0
        registerReceiver(resultReceiver, filter, AutoSendContract.PERMISSION_CONTROL, null, flags)
    }

    override fun onDestroy() {
        unregisterReceiver(resultReceiver)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        // Пользователь возвращается из настроек — перепроверяем.
        val enabled = isServiceEnabled(this)
        status.setText(if (enabled) R.string.status_enabled else R.string.status_disabled)
        instruction.visibility = if (enabled) TextView.GONE else TextView.VISIBLE
    }

    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.settings_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    /** Делает то же, что CRM: взводит службу и открывает чат с подставленным текстом. */
    private fun runTest(messengerPackage: String) {
        val phone = testPhone.text.toString().filter { it.isDigit() }
        if (phone.isEmpty()) {
            testPhone.error = getString(R.string.test_need_phone)
            return
        }
        if (!isServiceEnabled(this)) {
            Toast.makeText(this, R.string.test_need_service, Toast.LENGTH_LONG).show()
            return
        }
        val chat = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://wa.me/$phone?text=" + Uri.encode(testText.text.toString())),
        ).setPackage(messengerPackage)
        if (chat.resolveActivity(packageManager) == null) {
            Toast.makeText(this, R.string.test_not_installed, Toast.LENGTH_LONG).show()
            return
        }

        sendBroadcast(
            Intent(AutoSendContract.ACTION_ARM_SEND)
                .setPackage(packageName)
                .putExtra(AutoSendContract.EXTRA_TARGET_PACKAGE, messengerPackage)
                .putExtra(AutoSendContract.EXTRA_REQUEST_ID, "test")
                .putExtra(AutoSendContract.EXTRA_REPLY_PACKAGE, packageName),
        )
        testResult.setText(R.string.test_waiting)
        startActivity(chat)
    }

    private fun resultText(status: String?): Int = when (status) {
        AutoSendContract.STATUS_SENT -> R.string.result_sent
        AutoSendContract.STATUS_CLICK_FAILED -> R.string.result_click_failed
        AutoSendContract.STATUS_TIMEOUT -> R.string.result_timeout
        AutoSendContract.STATUS_SERVICE_DISABLED -> R.string.result_service_disabled
        AutoSendContract.STATUS_UNSUPPORTED_PACKAGE -> R.string.result_unsupported_package
        else -> R.string.result_cancelled
    }

    companion object {
        fun isServiceEnabled(context: Context): Boolean {
            val setting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            )
            return EnabledServices.contains(
                setting,
                context.packageName,
                AutoSendAccessibilityService::class.java.name,
            )
        }
    }
}
