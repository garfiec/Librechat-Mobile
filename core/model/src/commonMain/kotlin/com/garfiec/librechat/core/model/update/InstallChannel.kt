package com.garfiec.librechat.core.model.update

/** How the installed build arrived, which decides where What's new sends the user to update. */
enum class InstallChannel {
    /** Sideloaded APK, adb, or an installer we don't recognise. */
    DIRECT,
    OBTAINIUM,

    /** F-Droid or one of its clients (Droid-ify, Neo Store, F-Droid Basic). */
    FDROID,
}
