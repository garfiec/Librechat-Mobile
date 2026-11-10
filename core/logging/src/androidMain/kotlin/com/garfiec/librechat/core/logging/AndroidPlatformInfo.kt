package com.garfiec.librechat.core.logging

import android.content.Context
import android.os.Build

internal class AndroidPlatformInfo(private val context: Context) : PlatformInfo {
    override val osName: String = "Android"
    override val osVersion: String = Build.VERSION.RELEASE ?: "unknown"
    override val deviceModel: String = "${Build.MANUFACTURER} ${Build.MODEL}"

    override val installSource: String by lazy {
        val installer = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(context.packageName)
            }
        }.getOrNull()
        installerNames[installer] ?: installer ?: "adb or unknown"
    }
}

private val installerNames = mapOf(
    "com.android.vending" to "Google Play",
    "org.fdroid.fdroid" to "F-Droid",
    "org.fdroid.basic" to "F-Droid",
    "com.looker.droidify" to "Droid-ify",
    "com.machiav3lli.fdroid" to "Neo Store",
    "dev.imranr.obtainium" to "Obtainium",
    "dev.imranr.obtainium.fdroid" to "Obtainium",
    "com.google.android.packageinstaller" to "APK file",
    "com.android.packageinstaller" to "APK file",
)
