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
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * The tool switches of each conversation the server doesn't save, so a reopened chat comes back with
 * them as it was left: the web-search switch, and a model spec chat's tools ([SpecChatTools]).
 *
 * The server can't hold them: when the chat runs LibreChat's search, the request carries only
 * `ephemeralAgent.web_search`, which is not saved on the conversation (only the provider's
 * `web_search` parameter is), and no `ephemeralAgent` field is. Web keeps the same things in local
 * storage per conversation (`LAST_WEB_SEARCH_TOGGLE_<id>` and its siblings, `useToolToggle.ts`; a
 * spec chat layers them over the spec's values, `applyModelSpecEphemeralAgent`).
 *
 * Account-scoped (`acct:<id>:…`), so the logout prefix purge sweeps it with the account, and bounded
 * by recency to [MAX_ENTRIES] conversations per kind, like the Skills toggles.
 */
class ConversationWebSearchStore(
    private val dataStore: DataStore<Preferences>,
    private val activeAccountProvider: ActiveAccountProvider,
) {

    /** The switch stored for [conversationId], or null when nothing is (the server's value applies). */
    suspend fun get(conversationId: String): Boolean? = read(WEB_SEARCH, conversationId, Entry.serializer())?.on

    /** Stores the switch for [conversationId]. No-ops without an account. */
    suspend fun set(conversationId: String, on: Boolean) {
        write(WEB_SEARCH, conversationId, Entry.serializer()) { seq -> Entry(on, seq) }
    }

    /** The spec chat tools stored for [conversationId], or null when nothing is (the spec's own apply). */
    suspend fun specTools(conversationId: String): SpecChatTools? =
        read(SPEC_TOOLS, conversationId, SpecEntry.serializer())?.tools

    /** Stores [tools] for [conversationId]. No-ops without an account. */
    suspend fun setSpecTools(conversationId: String, tools: SpecChatTools) {
        write(SPEC_TOOLS, conversationId, SpecEntry.serializer()) { seq -> SpecEntry(tools, seq) }
    }

    private suspend fun <T : Sequenced> read(base: String, conversationId: String, serializer: KSerializer<T>): T? {
        val accountId = activeAccountProvider.currentAccountId()?.value ?: return null
        return bestEffortCache({
            Logger.w(it) { "Failed to read a conversation's tools" }
            null
        }) {
            decode(dataStore.data.first()[accountScopedKey(accountId, base)], serializer)[conversationId]
        }
    }

    private suspend fun <T : Sequenced> write(
        base: String,
        conversationId: String,
        serializer: KSerializer<T>,
        entry: (seq: Long) -> T,
    ) {
        if (conversationId.isBlank()) return
        val accountId = activeAccountProvider.currentAccountId()?.value ?: return
        val key = accountScopedKey(accountId, base)
        // Durable: the new-chat handoff clears the landing ViewModel right after the first turn.
        withContext(NonCancellable) {
            bestEffortCache({ Logger.w(it) { "Failed to store a conversation's tools" } }) {
                dataStore.edit { prefs ->
                    val current = decode(prefs[key], serializer)
                    val seq = (current.values.maxOfOrNull { it.seq } ?: 0L) + 1
                    val merged = current + (conversationId to entry(seq))
                    val trimmed = if (merged.size > MAX_ENTRIES) {
                        merged.entries.sortedByDescending { it.value.seq }.take(MAX_ENTRIES).associate { it.toPair() }
                    } else {
                        merged
                    }
                    prefs[key] = json.encodeToString(MapSerializer(String.serializer(), serializer), trimmed)
                }
            }
        }
    }

    private fun <T> decode(raw: String?, serializer: KSerializer<T>): Map<String, T> =
        raw?.let { runCatching { json.decodeFromString(MapSerializer(String.serializer(), serializer), it) }.getOrNull() }
            ?: emptyMap()

    private interface Sequenced {
        val seq: Long
    }

    @Serializable
    private data class Entry(val on: Boolean, override val seq: Long) : Sequenced

    @Serializable
    private data class SpecEntry(val tools: SpecChatTools, override val seq: Long) : Sequenced

    private companion object {
        const val WEB_SEARCH = "conversation_web_search"
        const val SPEC_TOOLS = "conversation_spec_tools"
        const val MAX_ENTRIES = 200
        val json = Json { ignoreUnknownKeys = true }
    }
}

/**
 * A model spec chat's tools on top of what [spec] locks: toolbar keys in [tools] and MCP server names
 * in [mcp]. They apply only while the chat is still on [spec].
 */
@Serializable
data class SpecChatTools(
    val spec: String,
    val tools: Set<String> = emptySet(),
    val mcp: Set<String> = emptySet(),
)
