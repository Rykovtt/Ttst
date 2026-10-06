package com.rykov.autosend.core

import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.security.MessageDigest

/** Проверяет, что команду прислала доверенная CRM (по создателю PendingIntent и сертификату). */
object CallerVerifier {
    private const val TAG = "AutoSend"

    fun isTrusted(context: Context, callback: PendingIntent?): Boolean {
        val caller = callback?.creatorPackage ?: return false
        if (caller == context.packageName) return true
        if (!TrustedCallers.knows(caller)) {
            Log.w(TAG, "unknown caller $caller")
            return false
        }
        val trusted = TrustedCallers.isTrusted(caller, certDigests(context, caller), context.packageName)
        if (!trusted) Log.w(TAG, "certificate mismatch for $caller")
        return trusted
    }

    @Suppress("DEPRECATION")
    private fun certDigests(context: Context, packageName: String): List<String> = try {
        val pm = context.packageManager
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo
                ?.apkContentsSigners.orEmpty()
        } else {
            pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures.orEmpty()
        }
        val sha = MessageDigest.getInstance("SHA-256")
        signatures.map { TrustedCallers.hex(sha.digest(it.toByteArray())) }
    } catch (e: PackageManager.NameNotFoundException) {
        emptyList()
    }
}
