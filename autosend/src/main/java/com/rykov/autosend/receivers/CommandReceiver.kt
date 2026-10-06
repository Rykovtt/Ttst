package com.rykov.autosend.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rykov.autosend.core.AutoSendContract
import com.rykov.autosend.core.AutoSendController

/** Принимает команды CRM. Доступ ограничен разрешением CONTROL (см. манифест). */
class CommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AutoSendContract.ACTION_ARM_SEND -> AutoSendController.arm(context, intent)
            AutoSendContract.ACTION_CANCEL -> AutoSendController.cancel(context)
        }
    }
}
