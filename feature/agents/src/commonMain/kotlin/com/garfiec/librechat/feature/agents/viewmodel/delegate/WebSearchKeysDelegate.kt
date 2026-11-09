package com.garfiec.librechat.feature.agents.viewmodel.delegate

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.AgentToolsRepository
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.WebSearchReadinessRepository
import com.garfiec.librechat.core.ui.websearch.webSearchKeysState
import com.garfiec.librechat.feature.agents.viewmodel.AgentEditorStateHandle
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AgentToolKeySources(
    val tools: AgentToolsRepository,
    val webSearch: WebSearchReadinessRepository,
)

/**
 * The user's own web search keys on the Web Search capability, as web's agent builder offers them.
 * Keys belong to the user, not the agent: the server runs an agent's web search with the keys of
 * whoever chats with it and silently skips it without them. So while the server leaves keys to the
 * user and this user has none, turning the capability on opens the key sheet instead; a save turns it
 * on and a removal turns it off. A check that failed leaves the switch as it was before keys existed.
 */
class WebSearchKeysDelegate(
    private val stateHandle: AgentEditorStateHandle,
    private val webSearchReadiness: WebSearchReadinessRepository,
    private val configRepository: ConfigRepository,
) {

    fun observe() {
        stateHandle.scope.launch {
            combine(
                configRepository.startupConfig.filterNotNull().map { it.webSearch }.distinctUntilChanged(),
                webSearchReadiness.account,
                webSearchReadiness.changes,
            ) { searchConfig, _, _ -> searchConfig }.collectLatest { searchConfig ->
                val answer = webSearchReadiness.cached(searchConfig) ?: webSearchReadiness.check(searchConfig)
                stateHandle.update { copy(webSearchKeys = answer?.let { webSearchKeysState(it, searchConfig) }) }
            }
        }
    }

    fun onWebSearchToggled(enabled: Boolean) {
        if (enabled && stateHandle.state.webSearchNeedsKeys) {
            openSheet()
        } else {
            stateHandle.update { copy(webSearchEnabled = enabled) }
        }
    }

    fun openSheet() {
        stateHandle.update { copy(showWebSearchKeysSheet = true) }
    }

    fun dismissSheet() {
        stateHandle.update { copy(showWebSearchKeysSheet = false) }
    }

    suspend fun saveKeys(auth: Map<String, String>): Boolean {
        val result = webSearchReadiness.saveKeys(auth)
        if (result is Result.Error) Logger.w(result.exception) { "Saving web search keys failed" }
        if (result is Result.Success) stateHandle.update { copy(webSearchEnabled = true) }
        return result is Result.Success
    }

    suspend fun removeKeys(): Boolean {
        val result = webSearchReadiness.removeKeys()
        if (result is Result.Error) Logger.w(result.exception) { "Removing web search keys failed" }
        if (result is Result.Success) stateHandle.update { copy(webSearchEnabled = false) }
        return result is Result.Success
    }
}
