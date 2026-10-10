package com.garfiec.librechat.core.data.update

/** How the installed build arrived, which decides where What's new sends the user to update. */
enum class InstallChannel {
    /** Sideloaded APK, adb, or an installer we don't recognise. */
    DIRECT,
    OBTAINIUM,

    /** F-Droid or one of its clients (Droid-ify, Neo Store, F-Droid Basic). */
    FDROID,
}

interface AppInstallSource {
    val channel: InstallChannel

    /** Opens the app store that installed this build. False when it can't be launched. */
    fun openInstaller(): Boolean
}

/** For platforms without sideloading, where the update check is unsupported anyway. */
class NoopAppInstallSource : AppInstallSource {
    override val channel: InstallChannel = InstallChannel.DIRECT
    override fun openInstaller(): Boolean = false
}
