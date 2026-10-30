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
    /**
     * The preference's value, decoded only when its own stored value changes: a write to another key
     * re-emits [DataStore.data] but is dropped before [PreferenceSpec.decode] runs.
     */
    fun <S : Any, V> observe(spec: PreferenceSpec<S, V>): Flow<V> = dataStore.data
        .map { prefs -> prefs[spec.key] }
        .distinctUntilChanged()
        .map { stored -> stored?.let(spec.decode) ?: spec.default(channelPolicy) }
        .distinctUntilChanged()

    /** The default, for seeding UI state before the first read lands. */
    fun <S : Any, V> initial(spec: PreferenceSpec<S, V>): V = spec.default(channelPolicy)

    suspend fun <S : Any, V> set(spec: PreferenceSpec<S, V>, value: V & Any) {
        dataStore.edit { prefs -> prefs[spec.key] = spec.encode(value) }
    }

    /** Read-modify-write in one edit, so two quick updates can't drop one another. */
    suspend fun <S : Any, V> update(spec: PreferenceSpec<S, V>, transform: (V) -> V & Any) {
        dataStore.edit { prefs ->
            val current = prefs[spec.key]?.let(spec.decode) ?: spec.default(channelPolicy)
            prefs[spec.key] = spec.encode(transform(current))
        }
    }
}
