package com.garfiec.librechat.core.data.datastore

/** A flag that this install has launched before, kept where an uninstall deletes it. */
internal interface InstallMarker {
    fun isSet(): Boolean
    fun set()
}

/**
 * Wipes credentials a previous install left behind, on the first launch of this one. Must run before
 * anything reads a token.
 *
 * iOS keeps Keychain items across an uninstall while the app's files and defaults go, so a reinstall
 * otherwise starts on the old sessions with no account roster to match them. The marker alone can't
 * tell a reinstall from an upgrade of a build that never wrote it, so a missing marker wipes only when
 * no earlier app data exists either ([hasPriorAppData]); an upgrade just records the marker.
 *
 * Wipes before recording, so a launch killed in between wipes again next time. Returns whether it wiped.
 */
internal fun resetCredentialsLeftByPreviousInstall(
    marker: InstallMarker,
    hasPriorAppData: () -> Boolean,
    wipeCredentials: () -> Unit,
): Boolean {
    if (marker.isSet()) return false
    val reinstalled = !hasPriorAppData()
    if (reinstalled) wipeCredentials()
    marker.set()
    return reinstalled
}
