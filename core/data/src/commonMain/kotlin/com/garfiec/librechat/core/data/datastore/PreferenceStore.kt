package com.garfiec.librechat.core.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.garfiec.librechat.core.common.ChannelPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Reads and writes the preferences declared in [AppPreferences], on the app's preferences [DataStore]. */
class PreferenceStore(
    private val dataStore: DataStore<Preferences>,
    private val channelPolicy: ChannelPolicy,
) {
    fun <S : Any, V> observe(spec: PreferenceSpec<S, V>): Flow<V> = dataStore.data.map { prefs ->
        prefs[spec.key]?.let(spec.decode) ?: spec.default(channelPolicy)
    }.distinctUntilChanged()

    /** The default, for seeding UI state before the first read lands. */
    fun <S : Any, V> initial(spec: PreferenceSpec<S, V>): V = spec.default(channelPolicy)

    suspend fun <S : Any, V> set(spec: PreferenceSpec<S, V>, value: V & Any) {
        dataStore.edit { prefs -> prefs[spec.key] = spec.encode(value) }
    }
}
