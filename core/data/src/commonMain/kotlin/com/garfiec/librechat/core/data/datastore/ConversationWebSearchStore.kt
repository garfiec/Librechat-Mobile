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
 * Each conversation's web-search switch, so a reopened chat comes back with it as it was left.
 *
 * The server can't hold it: when the chat runs LibreChat's search, the request carries only
 * `ephemeralAgent.web_search`, which is not saved on the conversation (only the provider's
 * `web_search` parameter is). Web keeps the same thing in local storage per conversation
 * (`LAST_WEB_SEARCH_TOGGLE_<id>`, `useToolToggle.ts`).
 *
 * Account-scoped (`acct:<id>:…`), so the logout prefix purge sweeps it with the account, and bounded
 * by recency to [MAX_ENTRIES] conversations, like the Skills toggles.
 */
class ConversationWebSearchStore(
    private val dataStore: DataStore<Preferences>,
    private val activeAccountProvider: ActiveAccountProvider,
) {

    /** The switch stored for [conversationId], or null when nothing is (the server's value applies). */
    suspend fun get(conversationId: String): Boolean? {
        val accountId = activeAccountProvider.currentAccountId()?.value ?: return null
        return bestEffortCache({
            Logger.w(it) { "Failed to read a conversation's web search" }
            null
        }) {
            decode(dataStore.data.first()[accountScopedKey(accountId, BASE)])[conversationId]?.on
        }
    }

    /** Stores the switch for [conversationId]. No-ops without an account. */
    suspend fun set(conversationId: String, on: Boolean) {
        if (conversationId.isBlank()) return
        val accountId = activeAccountProvider.currentAccountId()?.value ?: return
        val key = accountScopedKey(accountId, BASE)
        // Durable: the new-chat handoff clears the landing ViewModel right after the first turn.
        withContext(NonCancellable) {
            bestEffortCache({ Logger.w(it) { "Failed to store a conversation's web search" } }) {
                dataStore.edit { prefs ->
                    val current = decode(prefs[key])
                    val seq = (current.values.maxOfOrNull { it.seq } ?: 0L) + 1
                    val merged = current + (conversationId to Entry(on, seq))
                    val trimmed = if (merged.size > MAX_ENTRIES) {
                        merged.entries.sortedByDescending { it.value.seq }.take(MAX_ENTRIES).associate { it.toPair() }
                    } else {
                        merged
                    }
                    prefs[key] = json.encodeToString(trimmed)
                }
            }
        }
    }

    private fun decode(raw: String?): Map<String, Entry> =
        raw?.let { runCatching { json.decodeFromString<Map<String, Entry>>(it) }.getOrNull() } ?: emptyMap()

    @Serializable
    private data class Entry(val on: Boolean, val seq: Long)

    private companion object {
        const val BASE = "conversation_web_search"
        const val MAX_ENTRIES = 200
        val json = Json { ignoreUnknownKeys = true }
    }
}
