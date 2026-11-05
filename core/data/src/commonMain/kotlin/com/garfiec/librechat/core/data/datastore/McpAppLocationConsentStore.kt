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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** What the user said about an MCP server's Apps using the device's approximate location. */
enum class McpAppLocationConsent { NOT_ASKED, ALLOWED, REFUSED }

/**
 * Per MCP server, whether its Apps may have the device's approximate location. Asked the first time
 * one of its Apps requests it, remembered either way, and changed from that server's settings.
 *
 * Native, not a WebView grant: every App runs at the one synthetic relay origin, so a WebView-level
 * grant would cover every server. Account-scoped (`acct:<id>:…`), so the logout purge removes it
 * with the account, and an account is on one LibreChat server, which gives (account, server).
 */
class McpAppLocationConsentStore(
    private val dataStore: DataStore<Preferences>,
    private val activeAccountProvider: ActiveAccountProvider,
) {

    /** Every server's answer for the active account; a server missing from the map was never asked. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val consents: Flow<Map<String, McpAppLocationConsent>> = activeAccountProvider.state
        .map { (it as? AccountState.Resolved)?.id?.value }
        .distinctUntilChanged()
        .flatMapLatest { accountId ->
            if (accountId == null) {
                flowOf(emptyMap())
            } else {
                dataStore.data.map { decode(it[accountScopedKey(accountId, BASE)]) }
            }
        }
        .distinctUntilChanged()

    suspend fun consent(serverName: String): McpAppLocationConsent =
        bestEffortCache({
            Logger.w(it) { "Failed to read an MCP App location answer" }
            McpAppLocationConsent.NOT_ASKED
        }) { consents.first()[serverName] ?: McpAppLocationConsent.NOT_ASKED }

    /** Stores [consent] for [serverName]; [McpAppLocationConsent.NOT_ASKED] forgets it. No-op signed out. */
    suspend fun set(serverName: String, consent: McpAppLocationConsent) {
        if (serverName.isBlank()) return
        val accountId = activeAccountProvider.currentAccountId()?.value ?: return
        val key = accountScopedKey(accountId, BASE)
        withContext(NonCancellable) {
            bestEffortCache({ Logger.w(it) { "Failed to store an MCP App location answer" } }) {
                dataStore.edit { prefs ->
                    val next = decode(prefs[key]).let {
                        if (consent == McpAppLocationConsent.NOT_ASKED) it - serverName else it + (serverName to consent)
                    }
                    if (next.isEmpty()) prefs.remove(key) else prefs[key] = json.encodeToString(next.mapValues { it.value.name })
                }
            }
        }
    }

    private fun decode(raw: String?): Map<String, McpAppLocationConsent> {
        val stored = raw?.let { runCatching { json.decodeFromString<Map<String, String>>(it) }.getOrNull() }
            ?: return emptyMap()
        return stored.mapNotNull { (server, value) ->
            McpAppLocationConsent.entries.firstOrNull { it.name == value && it != McpAppLocationConsent.NOT_ASKED }
                ?.let { server to it }
        }.toMap()
    }

    private companion object {
        const val BASE = "mcp_app_location"
        val json = Json { ignoreUnknownKeys = true }
    }
}
