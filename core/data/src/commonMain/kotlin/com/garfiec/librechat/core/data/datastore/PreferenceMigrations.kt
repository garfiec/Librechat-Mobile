package com.garfiec.librechat.core.data.datastore

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.garfiec.librechat.core.common.ChannelPolicy

/**
 * A one-time change to device-wide preferences, run once per install like a database migration:
 * for example a store policy that requires a preference reset for everyone who already has it.
 *
 * [id] is `YYYYMMDD-<slug>` and is immutable once shipped. Changing what a shipped migration does
 * means a new id. [migrate] must be total: it runs inside the store's initialization, and a throw
 * there fails every read and write of the shared settings store. DataStore retries the
 * initialization on the next access, so a throw that always happens fails them on every launch,
 * and the corruption handler cannot recover it.
 */
class PreferenceMigration(
    val id: String,
    val appliesTo: (ChannelPolicy) -> Boolean = { true },
    val migrate: (MutablePreferences) -> Unit,
)

/** Clears the stored choice, so the preference reads its registry default again. */
fun MutablePreferences.reset(spec: PreferenceSpec<*, *>) {
    remove(spec.key)
}

/** Stores [value] as though the user had chosen it. */
fun <S : Any, V> MutablePreferences.force(spec: PreferenceSpec<S, V>, value: V & Any) {
    this[spec.key] = spec.encode(value)
}

/**
 * The migrations, in the order they run. Append only: never reorder, edit or reuse an id that has
 * shipped.
 */
object PreferenceMigrations {
    val all: List<PreferenceMigration> = emptyList()
}

/**
 * Runs pending [PreferenceMigration]s when the preferences store is first opened, before anything
 * can read it, and records them in a ledger of applied ids in the same atomic write.
 *
 * - The ledger records only migrations that ran, or were waived on a fresh install, on this
 *   channel. One for another channel stays unrecorded, so it still runs if this data is ever
 *   opened by that channel's build (iOS store and sideload builds share a bundle id).
 * - A fresh install (an empty store with no ledger) waives this channel's migrations: it already
 *   starts from the current defaults.
 * - Ids the build does not know, left by a newer one before a downgrade, are kept.
 */
class PreferenceMigrationRunner(
    private val channelPolicy: ChannelPolicy,
    private val migrations: List<PreferenceMigration> = PreferenceMigrations.all,
) : DataMigration<Preferences> {

    private fun applicable() = migrations.filter { it.appliesTo(channelPolicy) }

    private fun pending(current: Preferences): List<PreferenceMigration> {
        val applied = current[LEDGER].orEmpty()
        return applicable().filter { it.id !in applied }
    }

    override suspend fun shouldMigrate(currentData: Preferences): Boolean = pending(currentData).isNotEmpty()

    override suspend fun migrate(currentData: Preferences): Preferences {
        val pending = pending(currentData)
        val freshInstall = currentData.asMap().isEmpty()
        val prefs = currentData.toMutablePreferences()
        if (!freshInstall) pending.forEach { it.migrate(prefs) }
        prefs[LEDGER] = currentData[LEDGER].orEmpty() + pending.map { it.id }
        return prefs.toPreferences()
    }

    override suspend fun cleanUp() = Unit

    companion object {
        /** Not a preference: bookkeeping, deliberately absent from [AppPreferences.all]. */
        val LEDGER: Preferences.Key<Set<String>> = stringSetPreferencesKey("applied_pref_migrations")
    }
}
