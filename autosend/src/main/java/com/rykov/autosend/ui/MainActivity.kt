package com.rykov.autosend.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.rykov.autosend.R
import com.rykov.autosend.core.EnabledServices
import com.rykov.autosend.services.AutoSendAccessibilityService

/** Экран проверки: включена ли служба, и инструкция с переходом в настройки. */
class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var instruction: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.status)
        instruction = findViewById(R.id.instruction)
        findViewById<Button>(R.id.open_settings).setOnClickListener { openAccessibilitySettings() }
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
