package com.garfiec.librechat.core.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Whether the user has acknowledged that social sign-in runs in an in-app browser.
 *
 * Deliberately not account-scoped: it is read on the login screen, before any account exists, so
 * [SettingsDataStore]'s resolved-account path cannot serve it.
 */
class SsoRiskDataStore(private val dataStore: DataStore<Preferences>) {

    val acknowledged: Flow<Boolean> = dataStore.data.map { it[KEY] ?: false }

    suspend fun acknowledge() {
        dataStore.edit { it[KEY] = true }
    }

    private companion object {
        val KEY = booleanPreferencesKey("sso_embedded_browser_risk_acknowledged")
    }
}
