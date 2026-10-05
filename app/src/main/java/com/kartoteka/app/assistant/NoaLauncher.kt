package com.kartoteka.app.assistant

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.kartoteka.app.MainActivity

/**
 * Ярлык «Ассистент» на рабочем столе. Отдельная активность-переходник: передаёт запуск в уже открытое
 * приложение (onNewIntent), открывает Ноа и сразу включает микрофон. Раньше это был псевдоним MainActivity —
 * при работающем приложении система просто показывала его без перехода к ассистенту.
 */
class NoaLauncher : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_NOA, true)
                .putExtra(MainActivity.EXTRA_NOA_LISTEN, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }
}
