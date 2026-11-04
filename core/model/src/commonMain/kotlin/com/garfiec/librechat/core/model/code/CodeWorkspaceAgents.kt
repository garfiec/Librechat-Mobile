package com.garfiec.librechat.core.model.code

import com.garfiec.librechat.core.model.Agent
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * The agent-level halves of web's machine routing, ported from upstream
 * `client/src/hooks/Agents/useCodeApprovalMode.ts` (`getCodeEnvironmentChoiceIds`,
 * `findExecutionEnvironment`, `findCodeWorkspaceDiscoveryEnvironment`, `collectReachableAgents`,
 * `resolveReachableCodeWorkspaceInheritance`). Registered in `scripts/mirrors.json`.
 *
 * Every agent read here must come from the LIST route (`GET /api/agents`): it is the only one that
 * projects `tools`, `stateful_code_sessions`, `code_environment_id(s)`, `code_workspace_id`,
 * `agent_ids`, `edges` and the `subagents` ids.
 */
object CodeWorkspaceAgents {
    const val EXECUTE_CODE = "execute_code"

    /** Runs code on a stateful machine. */
    fun routesCode(agent: Agent): Boolean =
        agent.statefulCodeSessions == true && agent.tools?.contains(EXECUTE_CODE) == true

    private fun defaultEnvironment(agent: Agent, environments: List<PublicCodeEnvironment>?): PublicCodeEnvironment? =
        if (!agent.codeEnvironmentId.isNullOrEmpty()) {
            environments?.firstOrNull { it.id == agent.codeEnvironmentId }
        } else {
            environments?.firstOrNull { it.default == true }
        }

    /** The agent's effective default machine id: its own, or the deployment default. */
    fun defaultEnvironmentId(agent: Agent, environments: List<PublicCodeEnvironment>?): String? =
        agent.codeEnvironmentId?.takeIf { it.isNotEmpty() } ?: environments?.firstOrNull { it.default == true }?.id

    /**
     * The machines a chat may pick for this agent, or null when per-chat choice does not apply to
     * it. Strict `allowEnvironmentSelection == true`, as the hook reads it.
     */
    fun choiceIds(
        agent: Agent,
        environments: List<PublicCodeEnvironment>?,
        allowEnvironmentSelection: Boolean?,
    ): List<String>? {
        val default = defaultEnvironment(agent, environments)
        if (allowEnvironmentSelection != true ||
            agent.codeEnvironmentIds.isNullOrEmpty() ||
            !(default?.isAttached == true || (default == null && !agent.codeEnvironmentId.isNullOrEmpty()))
        ) {
            return null
        }
        return (listOf(agent.codeEnvironmentId?.takeIf { it.isNotEmpty() } ?: default?.id) + agent.codeEnvironmentIds)
            .filterNotNull()
            .distinct()
    }

    fun findExecutionEnvironment(
        agent: Agent,
        environments: List<PublicCodeEnvironment>?,
        allowEnvironmentSelection: Boolean? = null,
        selections: List<CodeWorkspaceSelection>? = null,
        inheritedEnvironmentId: String? = null,
    ): PublicCodeEnvironment? {
        val default = defaultEnvironment(agent, environments)
        val allowSelection = choiceIds(agent, environments, allowEnvironmentSelection) != null
        val effectiveSelections = if (inheritedEnvironmentId != null &&
            selections?.any { it.environmentId == inheritedEnvironmentId } != true
        ) {
            selections.orEmpty() + CodeWorkspaceSelection(inheritedEnvironmentId, DRAFT_WORKSPACE)
        } else {
            selections
        }
        val selection = CodeWorkspaceRouting.resolveCodeEnvironmentSelection(
            agentId = agent.id,
            environmentId = agent.codeEnvironmentId?.takeIf { it.isNotEmpty() } ?: default?.id,
            environmentIds = agent.codeEnvironmentIds,
            allowSelection = allowSelection,
            selections = effectiveSelections,
            inheritedEnvironmentId = inheritedEnvironmentId,
        ) as? CodeWorkspaceRouting.Resolution.Valid ?: return null
        val resolved = if (!selection.environmentId.isNullOrEmpty()) {
            environments?.firstOrNull { it.id == selection.environmentId }
        } else {
            default
        }
        return if (allowSelection && resolved?.isAttached != true) null else resolved
    }

    /** Discovery may name an authorized recovery machine after a default disappears. */
    fun findDiscoveryEnvironment(
        agent: Agent,
        environments: List<PublicCodeEnvironment>?,
        allowEnvironmentSelection: Boolean?,
        selections: List<CodeWorkspaceSelection>?,
        inheritedEnvironmentId: String?,
    ): PublicCodeEnvironment? =
        findExecutionEnvironment(agent, environments, allowEnvironmentSelection, selections, inheritedEnvironmentId)
            ?: findExecutionEnvironment(agent, environments)
            ?: choiceIds(agent, environments, allowEnvironmentSelection)?.let { ids ->
                environments?.firstOrNull { it.isAttached && it.id in ids }
            }

    /** `edges[].from/to` (a string or a list) plus `agent_ids`. */
    fun linkedAgentIds(agent: Agent): List<String> =
        agent.agentIds.orEmpty() + agent.edges.orEmpty().flatMap { edge ->
            val obj = edge as? JsonObject ?: return@flatMap emptyList()
            idsOf(obj["from"]) + idsOf(obj["to"])
        }

    /** Explicit subagents and the members of the agent's subagent graphs, when subagents are on. */
    fun subagentIds(agent: Agent): List<String>? {
        val subagents = agent.subagents?.takeIf { it.enabled == true } ?: return null
        val graphIds = (subagents.graphs as? JsonArray).orEmpty().flatMap { graph ->
            idsOf((graph as? JsonObject)?.get("agent_ids"))
        }
        return subagents.agentIds.orEmpty() + graphIds
    }

    private fun idsOf(element: JsonElement?): List<String> = when (element) {
        is JsonPrimitive -> listOfNotNull(element.contentOrNull?.takeIf { element.isString })
        is JsonArray -> element.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.contentOrNull }
        else -> emptyList()
    }

    data class Reachable(val agents: List<Agent>, val complete: Boolean)

    /**
     * The conversation agents plus every agent they hand off to or spawn, transitively. [complete]
     * is false when a linked id is not among the loaded agents.
     */
    fun collectReachableAgents(
        roots: List<Agent?>,
        agentsById: Map<String, Agent>,
        expectedRootIds: List<String?>,
        isEphemeralAgentId: (String?) -> Boolean = { it == null },
    ): Reachable {
        val pending = ArrayList(roots.filterNotNull())
        val visited = HashSet<String>()
        val agents = ArrayList<Agent>()
        var complete = expectedRootIds.all { id -> isEphemeralAgentId(id) || roots.any { it?.id == id } }
        while (pending.isNotEmpty()) {
            val agent = pending.removeAt(pending.size - 1)
            if (!visited.add(agent.id)) continue
            agents.add(agent)
            val subagents = agent.subagents?.takeIf { it.enabled == true }
            val graphIds = (subagents?.graphs as? JsonArray).orEmpty().flatMap { graph ->
                idsOf((graph as? JsonObject)?.get("agent_ids"))
            }
            val ids = agent.agentIds.orEmpty() + subagents?.agentIds.orEmpty() +
                agent.edges.orEmpty().flatMap { edge ->
                    val obj = edge as? JsonObject ?: return@flatMap emptyList()
                    idsOf(obj["from"]) + idsOf(obj["to"])
                } + graphIds
            for (id in ids) {
                if (id in visited) continue
                val candidate = roots.firstOrNull { it?.id == id } ?: agentsById[id]
                if (candidate != null) pending.add(candidate) else complete = false
            }
        }
        return Reachable(agents, complete)
    }

    private fun toRoutingAgent(
        agent: Agent,
        environments: List<PublicCodeEnvironment>?,
        allowEnvironmentSelection: Boolean?,
    ) = CodeWorkspaceRouting.RoutingAgent(
        id = agent.id,
        routesCode = routesCode(agent),
        environmentId = defaultEnvironmentId(agent, environments),
        environmentIds = agent.codeEnvironmentIds,
        allowSelection = choiceIds(agent, environments, allowEnvironmentSelection) != null,
        subagentIds = subagentIds(agent)?.filter { it.isNotEmpty() && it != agent.id },
    )

    /**
     * Mirrors the server's subagent machine inheritance for the composer. Conversation agents and
     * the agents they hand off to are roots. [draft]: the chat has not decided yet, so the
     * submission will also select each root's machine.
     */
    fun resolveReachableInheritance(
        roots: List<Agent?>,
        agentsById: Map<String, Agent>,
        environments: List<PublicCodeEnvironment>?,
        allowEnvironmentSelection: Boolean?,
        selections: List<CodeWorkspaceSelection>?,
        draft: Boolean = false,
    ): Map<String, String> {
        if (!draft && selections.isNullOrEmpty()) return emptyMap()
        fun lookup(id: String): Agent? = roots.firstOrNull { it?.id == id } ?: agentsById[id]
        val agents = LinkedHashMap<String, CodeWorkspaceRouting.RoutingAgent>()
        val rootIds = ArrayList<String>()
        val pendingRoots = ArrayDeque(roots.filterNotNull())
        while (pendingRoots.isNotEmpty()) {
            val agent = pendingRoots.removeFirst()
            if (agent.id in agents) continue
            agents[agent.id] = toRoutingAgent(agent, environments, allowEnvironmentSelection)
            rootIds.add(agent.id)
            for (id in linkedAgentIds(agent)) {
                val linked = if (id in agents) null else lookup(id)
                if (linked != null) pendingRoots.addLast(linked)
            }
        }
        val pending = ArrayDeque(rootIds.flatMap { agents[it]?.subagentIds.orEmpty() })
        while (pending.isNotEmpty()) {
            val id = pending.removeFirst()
            val agent = if (id in agents) null else lookup(id)
            if (agent == null) continue
            val node = toRoutingAgent(agent, environments, allowEnvironmentSelection)
            agents[id] = node
            pending.addAll(node.subagentIds.orEmpty())
        }
        val prospective = if (draft) {
            rootIds.fold(selections.orEmpty()) { planned, id ->
                val root = lookup(id)
                val environment = if (root != null && agents[id]?.routesCode == true) {
                    findExecutionEnvironment(root, environments, allowEnvironmentSelection, selections)
                } else {
                    null
                }
                if (environment?.isAttached == true && planned.none { it.environmentId == environment.id }) {
                    planned + CodeWorkspaceSelection(environment.id, DRAFT_WORKSPACE)
                } else {
                    planned
                }
            }
        } else {
            selections
        }
        return CodeWorkspaceRouting.resolveCodeWorkspaceInheritance(
            selections = prospective,
            rootIds = rootIds,
            agents = agents,
            isAttachedEnvironment = { id -> environments?.any { it.id == id && it.isAttached } == true },
        )
    }

    /** Placeholder workspace for a machine a draft will select before the reader picks one. */
    private const val DRAFT_WORKSPACE = "draft"
}
