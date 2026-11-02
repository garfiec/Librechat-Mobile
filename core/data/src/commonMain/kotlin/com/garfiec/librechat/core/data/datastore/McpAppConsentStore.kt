package com.garfiec.librechat.core.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.identity.AccountState
import com.garfiec.librechat.core.common.identity.ActiveAccountProvider
import com.garfiec.librechat.core.common.identity.currentAccountId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * The MCP servers whose Apps this account agreed to run without asking again ("Don't ask again for
 * <server>"), keyed by the MCP server's name: that server's author wrote the code that runs, and
 * its CSP declaration decides what else the App may load.
 *
 * Account-scoped (`acct:<id>:…`), so the logout prefix purge sweeps it with the account. Settings ›
 * Chat clears it. Most recent first, capped, so the oldest consent is the one dropped. Never
 * consulted unless `ChannelPolicy.mcpAppsNeedConsent` asks for consent.
 */
class McpAppConsentStore(
    private val dataStore: DataStore<Preferences>,
    private val activeAccountProvider: ActiveAccountProvider,
) {

    /** The active account's allowed servers; empty when signed out or unreadable. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val allowedServers: Flow<Set<String>> = activeAccountProvider.state
        .map { (it as? AccountState.Resolved)?.id?.value }
        .distinctUntilChanged()
        .flatMapLatest { accountId ->
            if (accountId == null) {
                flowOf(emptySet())
            } else {
                dataStore.data
                    .map { decode(it[accountScopedKey(accountId, BASE)]) }
                    .catch {
                        Logger.w(it) { "Failed to read MCP app consent" }
                        emit(emptySet())
                    }
            }
        }
        .distinctUntilChanged()

    suspend fun isAllowed(serverName: String): Boolean {
        val accountId = activeAccountProvider.currentAccountId()?.value ?: return false
        return bestEffortCache({
            Logger.w(it) { "Failed to read MCP app consent" }
            false
        }) {
            serverName in decode(dataStore.data.first()[accountScopedKey(accountId, BASE)])
        }
    }

    /** Remembers [serverName] for the active account. No-ops without an account. */
    suspend fun allow(serverName: String) {
        if (serverName.isBlank()) return
        val accountId = activeAccountProvider.currentAccountId()?.value ?: return
        val key = accountScopedKey(accountId, BASE)
        withContext(NonCancellable) {
            bestEffortCache({ Logger.w(it) { "Failed to store MCP app consent" } }) {
                dataStore.edit { prefs ->
                    prefs[key] = json.encodeToString(withAllowed(decodeList(prefs[key]), serverName))
                }
            }
        }
    }

    /** Forgets every server for the active account, so each asks again on its next open. */
    suspend fun revokeAll() {
        val accountId = activeAccountProvider.currentAccountId()?.value ?: return
        withContext(NonCancellable) {
            bestEffortCache({ Logger.w(it) { "Failed to clear MCP app consent" } }) {
                dataStore.edit { it.remove(accountScopedKey(accountId, BASE)) }
            }
        }
    }

    private fun decode(raw: String?): Set<String> = decodeList(raw).toSet()

    private fun decodeList(raw: String?): List<String> =
        raw?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrNull() } ?: emptyList()

    internal companion object {
        private const val BASE = "mcp_app_consent"
        const val MAX_ENTRIES = 200
        private val json = Json { ignoreUnknownKeys = true }

        /** Most recent first, so the cap evicts the oldest consent, never the one just given. */
        fun withAllowed(current: List<String>, serverName: String): List<String> =
            (listOf(serverName) + current.filter { it != serverName }).take(MAX_ENTRIES)
    }
}
