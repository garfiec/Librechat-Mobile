package com.garfiec.librechat.core.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.garfiec.librechat.core.common.ChannelPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Whether web-search results may load site icons from external servers (Google's favicon service,
 * or an icon URL the search result carries). Device-wide, not per account; shares the app's
 * preferences [DataStore] with [SettingsDataStore].
 */
class SiteIconsStore(
    private val dataStore: DataStore<Preferences>,
    channelPolicy: ChannelPolicy,
) {
    /**
     * What [choice] is before the user has chosen: on for a store build, null on the open-source
     * one, which the chat screen treats as "off, and ask once". For seeding state ahead of the
     * first read.
     */
    val default: Boolean? = channelPolicy.siteIconsDefault

    /** The user's choice, or [default] until they make one. */
    val choice: Flow<Boolean?> = dataStore.data.map { prefs ->
        prefs[KEY_SHOW_SITE_ICONS] ?: default
    }.distinctUntilChanged()

    suspend fun set(show: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_SHOW_SITE_ICONS] = show }
    }

    private companion object {
        // SettingsDataStore's former key: renaming it drops every existing user's choice.
        val KEY_SHOW_SITE_ICONS = booleanPreferencesKey("show_site_icons")
    }
}
