package com.garfiec.librechat.core.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Tchat-only preferences, in the same DataStore as [SettingsDataStore] but kept out of that
 * upstream file so syncs don't conflict on it.
 */
class TchatSettingsStore(
    private val dataStore: DataStore<Preferences>,
) {
    /**
     * The Image Gen selection, app-wide like [SettingsDataStore.enabledTools]. Null when Image Gen
     * is off, [IMAGE_GEN_DEFAULT] for the server's default `imageList` entry, else an entry's name.
     */
    val imageGenSelection: Flow<String?> = dataStore.data.map { prefs -> prefs[KEY_IMAGE_GEN] }

    suspend fun setImageGenSelection(selection: String?) {
        dataStore.edit { prefs ->
            if (selection == null) prefs.remove(KEY_IMAGE_GEN) else prefs[KEY_IMAGE_GEN] = selection
        }
    }

    companion object {
        private val KEY_IMAGE_GEN = stringPreferencesKey("tchat_image_gen")

        /** [imageGenSelection]'s value for the server's default image entry. */
        const val IMAGE_GEN_DEFAULT: String = "__default__"
    }
}
