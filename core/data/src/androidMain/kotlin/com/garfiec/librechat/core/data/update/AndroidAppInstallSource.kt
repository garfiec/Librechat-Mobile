package com.garfiec.librechat.core.data.update

import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Reads the installer package Android recorded for this app.
 *
 * Only a hint: Obtainium installing through Shizuku can report itself as the Play Store, and a
 * user may update from a different source than the one that first installed the app. That is
 * why it picks a button, never whether the check runs.
 *
 * The packages named here must also be listed under `<queries>` in the app manifest, or Android 11+
 * hides them and [openInstaller] finds no launch intent.
 */
class AndroidAppInstallSource(private val context: Context) : AppInstallSource {

    private val installer: String? by lazy { readInstaller() }

    override val channel: InstallChannel by lazy {
        when (installer) {
            in OBTAINIUM_PACKAGES -> InstallChannel.OBTAINIUM
            in FDROID_PACKAGES -> InstallChannel.FDROID
            else -> InstallChannel.DIRECT
        }
    }

    override fun openInstaller(): Boolean {
        val pkg = installer ?: return false
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        return runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
    }

    private fun readInstaller(): String? = runCatching {
        val pm = context.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pm.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            pm.getInstallerPackageName(context.packageName)
        }
    }.getOrNull()

    private companion object {
        val OBTAINIUM_PACKAGES = setOf("dev.imranr.obtainium", "dev.imranr.obtainium.fdroid")
        val FDROID_PACKAGES = setOf(
            "org.fdroid.fdroid",
            "org.fdroid.basic",
            "com.looker.droidify",
            "com.machiav3lli.fdroid",
        )
    }
}
