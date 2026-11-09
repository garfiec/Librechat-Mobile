package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.ToolConstants
import com.garfiec.librechat.core.common.identity.AccountState
import com.garfiec.librechat.core.common.identity.ActiveAccountProvider
import com.garfiec.librechat.core.common.identity.currentAccountId
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.ToolAuthResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject

/**
 * Whether LibreChat's own web search is set up for the active account: the server's key check,
 * `GET /api/agents/tools/web_search/auth`, remembered per account and served `webSearch` config (null
 * when the server serves none) so each chat screen doesn't start from "unknown" and ask again.
 *
 * Only an answer is remembered. A failed request (offline, a timeout, a 5xx) returns null and
 * caches nothing, so the caller can retry it rather than read it as "not set up". Keys can change
 * on the server mid-session; a new config (`webSearch` differs), a new process, or a save or removal
 * of the user's keys here ([invalidate], which bumps [changes]) asks again.
 */
class WebSearchReadinessRepository(
    private val agentToolsRepository: AgentToolsRepository,
    private val activeAccountProvider: ActiveAccountProvider,
) {
    private val mutex = Mutex()
    private val answers = mutableMapOf<Pair<String, JsonObject?>, ToolAuthResult>()
    private val _changes = MutableStateFlow(0)

    /** The active account, so a switch on the same server (same config) asks again. */
    val account: Flow<String?> = activeAccountProvider.state.map { (it as? AccountState.Resolved)?.id?.value }
        .distinctUntilChanged()

    /** Bumped by [invalidate], so every screen holding an answer asks again. */
    val changes: StateFlow<Int> = _changes.asStateFlow()

    /** The remembered answer for the active account and [config], or null when there is none. */
    suspend fun cached(config: JsonObject?): ToolAuthResult? {
        val account = activeAccountProvider.currentAccountId()?.value ?: return null
        return mutex.withLock { answers[account to config] }
    }

    /**
     * Asks the server, remembering an answer; null when the request failed or the account changed
     * while it was out, since the request then went with the other account's token. One request at
     * a time: a screen that asks while another's request is out takes its answer instead of sending
     * a second.
     */
    suspend fun check(config: JsonObject?): ToolAuthResult? {
        val account = activeAccountProvider.currentAccountId()?.value ?: return null
        return mutex.withLock {
            answers[account to config]?.let { return@withLock it }
            val result = agentToolsRepository.verifyToolAuth(ToolConstants.WEB_SEARCH)
            if (result !is Result.Success) return@withLock null
            if (activeAccountProvider.currentAccountId()?.value != account) return@withLock null
            result.data.also { answers[account to config] = it }
        }
    }

    /** Forgets every answer and bumps [changes]. */
    suspend fun invalidate() {
        mutex.withLock { answers.clear() }
        _changes.update { it + 1 }
    }

    /**
     * Saves the user's search keys, [auth] in web's form (`selectedProvider`, `serperApiKey`, ...),
     * then [invalidate]s whatever the outcome: a failed save may have written some of them.
     */
    suspend fun saveKeys(auth: Map<String, String>): Result<Unit> =
        agentToolsRepository.installToolKey(ToolConstants.WEB_SEARCH, auth).also { invalidate() }

    /** Removes all of the user's search keys (the server expands an empty list to every field), then [invalidate]s. */
    suspend fun removeKeys(): Result<Unit> =
        agentToolsRepository.removeToolKey(ToolConstants.WEB_SEARCH, emptyList()).also { invalidate() }
}
