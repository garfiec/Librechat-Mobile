package com.garfiec.librechat.core.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.identity.ActiveAccountProvider
import com.garfiec.librechat.core.common.identity.currentAccountId
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The code workspace this device last picked per agent and attached machine, so a new chat with an
 * agent that names no default workspace pre-selects it. Hints only, never a binding: a pick the
 * machine no longer lists is ignored. Mirrors web's browser-local `workspacePreferences.ts`.
 *
 * Keyed by account (`acct:<id>:…`, which already names the server and the user, and is swept by
 * the logout purge), then agent and machine; most recent first, capped at [MAX_ENTRIES].
 */
class CodeWorkspacePreferenceStore(
    private val dataStore: DataStore<Preferences>,
    private val activeAccountProvider: ActiveAccountProvider,
) {

    /** Every remembered pick for the active account, `(agentId, environmentId) → workspaceId`. */
    suspend fun snapshot(): Map<Pair<String, String>, String> {
        val accountId = activeAccountProvider.currentAccountId()?.value ?: return emptyMap()
        return bestEffortCache({
            Logger.w(it) { "Failed to read remembered code workspaces" }
            emptyMap()
        }) {
            decode(dataStore.data.first()[accountScopedKey(accountId, BASE)])
                .associate { (it.agentId to it.environmentId) to it.workspaceId }
        }
    }

    /** Remembers [workspaceId] on [environmentId] for each of [agentIds]. No-ops without an account. */
    suspend fun remember(environmentId: String, workspaceId: String, agentIds: Collection<String>) {
        val owners = agentIds.filter { it.isNotBlank() }
        if (owners.isEmpty()) return
        val accountId = activeAccountProvider.currentAccountId()?.value ?: return
        val key = accountScopedKey(accountId, BASE)
        withContext(NonCancellable) {
            bestEffortCache({ Logger.w(it) { "Failed to remember a code workspace" } }) {
                dataStore.edit { prefs ->
                    prefs[key] = json.encodeToString(merged(decode(prefs[key]), environmentId, workspaceId, owners))
                }
            }
        }
    }

    private fun decode(raw: String?): List<Entry> =
        raw?.let { runCatching { json.decodeFromString<List<Entry>>(it) }.getOrNull() } ?: emptyList()

    @Serializable
    internal data class Entry(val agentId: String, val environmentId: String, val workspaceId: String)

    internal companion object {
        const val BASE = "code_workspace_preferences"
        const val MAX_ENTRIES = 100
        val json = Json { ignoreUnknownKeys = true }

        /** Upstream's MRU update: the new keys first, then the rest without them, cut at [MAX_ENTRIES]. */
        fun merged(current: List<Entry>, environmentId: String, workspaceId: String, agentIds: List<String>): List<Entry> {
            val fresh = agentIds.distinct().map { Entry(it, environmentId, workspaceId) }
            val keys = fresh.map { it.agentId to it.environmentId }.toSet()
            return (fresh + current.filter { (it.agentId to it.environmentId) !in keys }).take(MAX_ENTRIES)
        }
    }
}
