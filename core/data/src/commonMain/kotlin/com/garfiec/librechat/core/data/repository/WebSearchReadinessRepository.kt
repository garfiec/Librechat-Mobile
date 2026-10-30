package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.ToolConstants
import com.garfiec.librechat.core.common.identity.AccountState
import com.garfiec.librechat.core.common.identity.ActiveAccountProvider
import com.garfiec.librechat.core.common.identity.currentAccountId
import com.garfiec.librechat.core.common.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject

/**
 * Whether LibreChat's own web search is set up for the active account: the server's key check,
 * `GET /api/agents/tools/web_search/auth`, remembered per account and served `webSearch` config so
 * each chat screen doesn't start from "unknown" and ask again.
 *
 * Only an answer is remembered. A failed request (offline, a timeout, a 5xx) returns null and
 * caches nothing, so the caller can retry it rather than read it as "not set up". Keys can change
 * on the server mid-session; a new config (`webSearch` differs) or a new process asks again.
 */
class WebSearchReadinessRepository(
    private val agentToolsRepository: AgentToolsRepository,
    private val activeAccountProvider: ActiveAccountProvider,
) {
    private val mutex = Mutex()
    private val answers = mutableMapOf<Pair<String, JsonObject>, Boolean>()

    /** The active account, so a switch on the same server (same config) asks again. */
    val account: Flow<String?> = activeAccountProvider.state.map { (it as? AccountState.Resolved)?.id?.value }
        .distinctUntilChanged()

    /** The remembered answer for the active account and [config], or null when there is none. */
    suspend fun cached(config: JsonObject): Boolean? {
        val account = activeAccountProvider.currentAccountId()?.value ?: return null
        return mutex.withLock { answers[account to config] }
    }

    /**
     * Asks the server, remembering an answer; null when the request failed or the account changed
     * while it was out, since the request then went with the other account's token. One request at
     * a time: a screen that asks while another's request is out takes its answer instead of sending
     * a second.
     */
    suspend fun check(config: JsonObject): Boolean? {
        val account = activeAccountProvider.currentAccountId()?.value ?: return null
        return mutex.withLock {
            answers[account to config]?.let { return@withLock it }
            val result = agentToolsRepository.verifyToolAuth(ToolConstants.WEB_SEARCH)
            if (result !is Result.Success) return@withLock null
            if (activeAccountProvider.currentAccountId()?.value != account) return@withLock null
            (result.data.authenticated == true).also { answers[account to config] = it }
        }
    }
}
