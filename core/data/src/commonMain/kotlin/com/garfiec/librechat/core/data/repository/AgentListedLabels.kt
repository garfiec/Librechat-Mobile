package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.identity.ActiveAccountProvider
import com.garfiec.librechat.core.common.identity.currentAccountId
import com.garfiec.librechat.core.model.Agent
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The labels an agent LIST row carries that the single-agent VIEW read does not. */
internal data class ListedAgentLabels(
    val category: String?,
    val isPromoted: Boolean,
)

/**
 * The per-agent [ListedAgentLabels] last seen on a list row.
 *
 * `GET /api/agents/:id` answers without `category` or `is_promoted` (upstream `getAgentHandler`'s
 * VIEW projection), so a page opened from the list keeps them by id here. Account-keyed like
 * [AgentEditVerdicts]: the key is read under the lock at access time.
 *
 * Not cleared when a mutation invalidates the agent cache: a category is not a permission, and the
 * next list read overwrites each row's labels anyway. An editor's own page reads `/expanded`, which
 * carries both fields, so a category it just changed never comes from here.
 */
internal class AgentListedLabels(private val activeAccountProvider: ActiveAccountProvider) {

    private val mutex = Mutex()
    private val labels = mutableMapOf<String, ListedAgentLabels>()
    private var recordedFor: String? = null

    suspend fun record(agents: List<Agent>) = mutex.withLock {
        val account = activeAccountProvider.currentAccountId()?.value
        if (recordedFor != account) {
            labels.clear()
            recordedFor = account
        }
        agents.forEach { agent ->
            labels[agent.id] = ListedAgentLabels(category = agent.category, isPromoted = agent.isPromoted)
        }
    }

    suspend fun get(id: String): ListedAgentLabels? = mutex.withLock {
        val account = activeAccountProvider.currentAccountId()?.value
        labels[id].takeIf { recordedFor == account }
    }
}
