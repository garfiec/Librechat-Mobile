package com.garfiec.librechat.core.data.datastore

/**
 * The token store's encrypted key/value storage (EncryptedSharedPreferences on Android, Keychain on
 * iOS), for other per-server secrets. Synchronous and possibly slow (Keystore, Keychain IPC): call
 * it off the main thread. Keys must not collide with the token store's own (`acct:`, bare names).
 */
interface SecureValueStore {
    fun readSecure(key: String): String?
    fun writeSecure(key: String, value: String)
}
