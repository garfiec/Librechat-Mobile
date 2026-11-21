package com.garfiec.librechat.core.data.update

import com.garfiec.librechat.core.model.update.InstallChannel

interface AppInstallSource {
    val channel: InstallChannel

    /** Opens the app store that installed this build. False when it can't be launched. */
    fun openInstaller(): Boolean
}

/** For builds where the update check is unsupported anyway: iOS, and every store build. */
class NoopAppInstallSource : AppInstallSource {
    override val channel: InstallChannel = InstallChannel.DIRECT
    override fun openInstaller(): Boolean = false
}
