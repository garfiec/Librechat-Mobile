package com.garfiec.librechat.core.common

/**
 * Where this build is distributed, fixed at build time: the Android `channel` product flavor
 * writes it into the manifest, and the iOS build configuration into Info.plist.
 *
 * Not the same fact as `InstallChannel`, which is which app installed the open-source APK. A store
 * build has to differ from the open-source one in ways a runtime check cannot decide (Play policy
 * forbids a self-update check outright), so this is a build variant rather than a detection.
 * What each channel turns on or off lives in [ChannelPolicy], not at the call sites.
 */
enum class DistributionChannel(val wireName: String) {
    /** GitHub releases, Obtainium and F-Droid on Android; the sideloaded IPA on iOS. */
    OPEN_SOURCE("oss"),
    PLAY_STORE("play"),
    APP_STORE("appstore"),
    ;

    companion object {
        /**
         * The channel whose [wireName] is [value]. Throws on anything else: a build that cannot
         * say which channel it is must not guess, because either guess is wrong somewhere — a
         * store build that falls back to open source shows a self-update check, and the reverse
         * turns privacy-off defaults on.
         */
        fun fromWireName(value: String?, source: String): DistributionChannel =
            entries.firstOrNull { it.wireName == value }
                ?: error(
                    "Unknown distribution channel '$value' from $source. Expected one of " +
                        entries.joinToString { it.wireName } + ".",
                )
    }
}
