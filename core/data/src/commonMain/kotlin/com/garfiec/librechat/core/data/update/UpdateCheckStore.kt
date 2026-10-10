package com.garfiec.librechat.core.data.update

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Update-check state. Global, not account-scoped: which app version is installed has nothing to
 * do with who is signed in.
 */
class UpdateCheckStore(private val dataStore: DataStore<Preferences>) {

    /**
     * Off unless the user turns it on. A check that runs by default earns F-Droid's Tracking
     * anti-feature, and F-Droid ships this same APK — so this default is not cosmetic.
     */
    val autoCheckEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_AUTO_CHECK] ?: false }

    val lastCheckedAt: Flow<Long?> = dataStore.data.map { it[KEY_LAST_CHECKED_AT] }

    /**
     * Tag of the newest release the last successful check found, or null when it found none.
     * Persisted so the chat banner survives a restart inside the 24-hour throttle window.
     */
    val availableTag: Flow<String?> = dataStore.data.map { it[KEY_AVAILABLE_TAG] }

    /** Tag the user has already been told about; the banner never repeats for it. */
    val notifiedTag: Flow<String?> = dataStore.data.map { it[KEY_NOTIFIED_TAG] }

    suspend fun setAutoCheckEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_AUTO_CHECK] = enabled }
    }

    suspend fun setLastCheckedAt(epochMillis: Long) {
        dataStore.edit { it[KEY_LAST_CHECKED_AT] = epochMillis }
    }

    suspend fun setAvailableTag(tag: String?) {
        dataStore.edit { prefs ->
            if (tag == null) prefs.remove(KEY_AVAILABLE_TAG) else prefs[KEY_AVAILABLE_TAG] = tag
        }
    }

    suspend fun setNotifiedTag(tag: String) {
        dataStore.edit { it[KEY_NOTIFIED_TAG] = tag }
    }

    private companion object {
        val KEY_AUTO_CHECK = booleanPreferencesKey("update_auto_check_enabled")
        val KEY_LAST_CHECKED_AT = longPreferencesKey("update_last_checked_at")
        val KEY_AVAILABLE_TAG = stringPreferencesKey("update_available_tag")
        val KEY_NOTIFIED_TAG = stringPreferencesKey("update_notified_tag")
    }
}
