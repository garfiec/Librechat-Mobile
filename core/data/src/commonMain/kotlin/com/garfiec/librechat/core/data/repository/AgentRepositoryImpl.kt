package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.identity.ActiveAccountProvider
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.safeApiCall
import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.AgentAction
import com.garfiec.librechat.core.model.AgentCategory
import com.garfiec.librechat.core.model.AgentTool
import com.garfiec.librechat.core.model.PaginatedAgents
import com.garfiec.librechat.core.model.request.CreateActionRequest
import com.garfiec.librechat.core.model.request.CreateAgentRequest
import com.garfiec.librechat.core.model.request.RevertAgentRequest
import com.garfiec.librechat.core.model.request.UpdateAgentRequest
import com.garfiec.librechat.core.network.api.AgentsApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

class AgentRepositoryImpl(
    private val agentsApi: AgentsApi,
    activeAccountProvider: ActiveAccountProvider,
) : AgentRepository {

    // Account-keyed in-memory cache: the only isolation tier for agents (no Room/accountId scoping).
    private val cache = AccountKeyedCache<List<Agent>>(activeAccountProvider)

    // The EDIT verdicts the list routes stamp per row; nothing else carries them (see
    // [listedEditVerdict]).
    private val editVerdicts = AgentEditVerdicts(activeAccountProvider)

    // The category and Top pick mark the list rows carry and the VIEW read lacks (see [getAgentForViewing]).
    private val listedLabels = AgentListedLabels(activeAccountProvider)

    private val _revision = MutableStateFlow(0L)
    override val revision: StateFlow<Long> = _revision.asStateFlow()

    // --- Agent CRUD ---

    override suspend fun getAgents(category: String?): Result<List<Agent>> {
        return safeApiCall {
            if (category != null) {
                return@safeApiCall agentsApi.getAgents(category).data.also { recordListed(it) }
            }
            cache.getOrFetch { agentsApi.getAgents(null).data }.also { recordListed(it) }
        }
    }

    override suspend fun getAgentsPaginated(
        limit: Int,
        cursor: String?,
        search: String?,
        category: String?,
        promoted: Boolean,
        requiredPermission: Int?,
    ): Result<PaginatedAgents> {
        return safeApiCall {
            val response = agentsApi.getAgentsPaginated(
                limit = limit,
                cursor = cursor,
                search = search,
                category = category,
                promoted = promoted,
                requiredPermission = requiredPermission,
            )
            recordListed(response.data)
            PaginatedAgents(
                agents = response.data,
                // Like the web client (`getNextPageParam: lastPage.after`), there is a next page
                // only when there is a cursor to read it with; without one, load-more would
                // re-read the first page forever.
                hasMore = response.hasMore && response.after != null,
                nextCursor = response.after,
            )
        }
    }

    override suspend fun listedEditVerdict(id: String): Boolean? = editVerdicts.get(id)

    private suspend fun recordListed(agents: List<Agent>) {
        editVerdicts.record(agents)
        listedLabels.record(agents)
    }

    override suspend fun getAgent(id: String): Result<Agent> {
        return safeApiCall {
            cache.peek { agents -> agents.find { it.id == id } }
                ?.let { return@safeApiCall it }
            agentsApi.getAgent(id)
        }
    }

    override suspend fun getAgentForViewing(id: String): Result<Agent> {
        // No cache read: the warm one holds list-projection agents, which lack model, provider,
        // isPublic and the contacts' detail.
        return safeApiCall {
            val agent = agentsApi.getAgent(id)
            // Decoding the VIEW body fills in "general"/false; those are not the agent's.
            val listed = listedLabels.get(id)
            agent.copy(category = listed?.category, isPromoted = listed?.isPromoted == true)
        }
    }

    override suspend fun getAgentProvider(id: String): Result<String?> {
        // No cache read: the warm one holds list-projection agents, whose provider is always null.
        return safeApiCall { agentsApi.getAgent(id).provider }
    }

    override suspend fun getAgentForEditing(id: String): Result<Agent> {
        // Always fetch from server via /expanded endpoint -- never use cache.
        // The cache and standard getAgent endpoint only have basic view fields.
        return safeApiCall {
            agentsApi.getAgentForEditing(id)
        }
    }

    override suspend fun createAgent(request: CreateAgentRequest): Result<Agent> {
        return safeApiCall {
            val agent = agentsApi.createAgent(request)
            invalidateCache()
            agent
        }
    }

    override suspend fun updateAgent(id: String, request: UpdateAgentRequest): Result<Agent> {
        return safeApiCall {
            val agent = agentsApi.updateAgent(id, request)
            // The RESPONSE body must never be written into the cache: pre-0.8.8-rc1 servers
            // could answer an update with a stale 200 (upstream da390fa9, "Save reverted" on
            // web, which cached it). Invalidate-and-refetch means a stale body can at most be
            // returned to the caller, which reads only its id.
            invalidateCache()
            agent
        }
    }

    override suspend fun deleteAgent(id: String): Result<Unit> {
        return safeApiCall {
            agentsApi.deleteAgent(id)
            invalidateCache()
        }
    }

    override suspend fun duplicateAgent(id: String): Result<Agent> {
        return safeApiCall {
            val agent = agentsApi.duplicateAgent(id).agent
            invalidateCache()
            agent
        }
    }

    override suspend fun revertAgent(id: String, request: RevertAgentRequest): Result<Agent> {
        return safeApiCall {
            val agent = agentsApi.revertAgent(id, request)
            invalidateCache()
            agent
        }
    }

    override suspend fun getAgentVersions(id: String): Result<List<JsonElement>> =
        safeApiCall { agentsApi.getAgentVersions(id) }

    override suspend fun getAgentCategories(): Result<List<AgentCategory>> {
        return safeApiCall {
            agentsApi.getAgentCategories()
        }
    }

    // --- Avatar ---

    override suspend fun uploadAgentAvatar(
        id: String,
        imageBytes: ByteArray,
        mimeType: String,
    ): Result<Agent> {
        return safeApiCall {
            val agent = agentsApi.uploadAgentAvatar(id, imageBytes, mimeType)
            invalidateCache()
            agent
        }
    }

    override suspend fun resetAgentAvatar(id: String): Result<Agent> {
        return safeApiCall {
            val agent = agentsApi.resetAgentAvatar(id)
            invalidateCache()
            agent
        }
    }

    // --- Actions ---

    override suspend fun getAgentActions(): Result<List<AgentAction>> {
        return safeApiCall {
            agentsApi.getAgentActions()
        }
    }

    override suspend fun addOrUpdateAction(
        agentId: String,
        request: CreateActionRequest,
    ): Result<Pair<Agent, AgentAction>> {
        val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
        return safeApiCall {
            val jsonArray = agentsApi.addOrUpdateAction(agentId, request)
            // Response is [Agent, Action] JsonArray
            if (jsonArray.size < 2) {
                throw IllegalStateException("Expected array of 2 elements for agent action response, got ${jsonArray.size}")
            }
            val agent = json.decodeFromJsonElement(Agent.serializer(), jsonArray[0])
            val action = json.decodeFromJsonElement(AgentAction.serializer(), jsonArray[1])
            invalidateCache()
            Pair(agent, action)
        }
    }

    override suspend fun deleteAction(agentId: String, actionId: String): Result<Unit> {
        return safeApiCall {
            agentsApi.deleteAction(agentId, actionId)
        }
    }

    // --- Tools ---

    override suspend fun getAvailableTools(): Result<List<AgentTool>> {
        return safeApiCall {
            agentsApi.getAvailableTools()
        }
    }

    private suspend fun invalidateCache() {
        cache.invalidate()
        // A mutation can change who may edit what (an ACL grant rides one), so the verdicts
        // learned from the previous list are no longer evidence. Cleared rather than refreshed:
        // absence hands the decision back to the detail screen's own permission probe.
        editVerdicts.clear()
        // Announce it as well as clearing: consumers outside this module memoize per-agent fields
        // (upload routing reads `provider`) and cannot see the cache drop any other way.
        _revision.value += 1
    }
}
