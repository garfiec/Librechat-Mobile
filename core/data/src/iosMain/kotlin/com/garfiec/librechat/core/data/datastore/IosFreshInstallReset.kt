package com.garfiec.librechat.core.data.datastore

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.data.di.DATASTORE_FILE_NAME
import platform.Foundation.NSFileManager
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSUserDefaults

/** Where the app keeps its files; deleted with the app, unlike the Keychain. */
internal fun iosAppSupportDirectory(): String = NSHomeDirectory() + "/Library/Application Support"

internal fun iosDatabasePath(): String = iosAppSupportDirectory() + "/librechat.db"

internal fun iosDataStoreDirectory(): String = iosAppSupportDirectory() + "/datastore"

internal fun iosDataStorePath(): String = iosDataStoreDirectory() + "/$DATASTORE_FILE_NAME.preferences_pb"

private object UserDefaultsInstallMarker : InstallMarker {
    private const val KEY = "com.garfiec.librechat.has_launched"

    override fun isSet(): Boolean = NSUserDefaults.standardUserDefaults.boolForKey(KEY)

    override fun set() = NSUserDefaults.standardUserDefaults.setBool(true, forKey = KEY)
}

/**
 * Clears the Keychain items a previous install left, on the first launch after an install (see
 * [resetCredentialsLeftByPreviousInstall]). Call from the iOS entry before Koin starts, so it lands
 * before the token store's first read. Best-effort: a failure is logged and retried next launch.
 */
fun resetKeychainLeftByPreviousInstall() {
    runCatching {
        val fileManager = NSFileManager.defaultManager
        resetCredentialsLeftByPreviousInstall(
            marker = UserDefaultsInstallMarker,
            hasPriorAppData = {
                fileManager.fileExistsAtPath(iosDataStorePath()) || fileManager.fileExistsAtPath(iosDatabasePath())
            },
            wipeCredentials = { IosTokenDataStore.deleteAllKeychainItems() },
        )
    }.onSuccess { wiped ->
        if (wiped) {
            Logger.withTag("Auth").i { "First launch after an install: cleared the Keychain items an earlier install left" }
        }
    }.onFailure {
        Logger.withTag("Auth").w(it) { "Couldn't clear the Keychain items an earlier install left; retrying next launch" }
    }
}
