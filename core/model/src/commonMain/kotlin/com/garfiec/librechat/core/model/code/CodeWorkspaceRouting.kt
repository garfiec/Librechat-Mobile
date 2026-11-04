package com.garfiec.librechat.core.model.code

/**
 * Machine routing rules, ported from upstream `packages/data-provider/src/code/workspace.ts`
 * (`isCodeWorkspaceSelection(s)`, `resolveCodeEnvironmentSelection`,
 * `resolveCodeWorkspaceInheritance`) and `agents/identity.ts` (`stripAgentIdSuffix`). The server
 * runs the same rules, so a client that resolves differently either asks for a workspace the
 * server never uses or sends a decision the server rejects. Registered in `scripts/mirrors.json`;
 * the spec tables are ported in `CodeWorkspaceSelectionTest` / `CodeWorkspaceInheritanceTest`.
 *
 * Character classes are scanned by hand rather than with `Regex`: Android's ICU engine is not the
 * JVM's, and a pattern that compiles in a unit test can still behave differently on a device.
 */
object CodeWorkspaceRouting {
    /** Wire/storage ceiling on the agents one selection may name. */
    const val MAX_AGENT_CODE_ENVIRONMENT_CHOICES = 128

    private const val ID_MAX_LENGTH = 128
    private const val AGENT_SUFFIX = "____"

    /** `^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$`. */
    fun isValidId(value: String): Boolean {
        if (value.isEmpty() || value.length > ID_MAX_LENGTH) return false
        if (!value[0].isAsciiAlphanumeric()) return false
        for (i in 1 until value.length) {
            val c = value[i]
            if (!c.isAsciiAlphanumeric() && c != '.' && c != '_' && c != ':' && c != '-') return false
        }
        return true
    }

    private fun Char.isAsciiAlphanumeric(): Boolean = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'

    /** Upstream `agentId.replace(/____\d+$/, '')`: a parallel runtime node's saved id. */
    fun stripAgentIdSuffix(agentId: String): String {
        var end = agentId.length
        while (end > 0 && agentId[end - 1] in '0'..'9') end--
        if (end == agentId.length) return agentId
        return if (end >= AGENT_SUFFIX.length &&
            agentId.regionMatches(end - AGENT_SUFFIX.length, AGENT_SUFFIX, 0, AGENT_SUFFIX.length)
        ) {
            agentId.substring(0, end - AGENT_SUFFIX.length)
        } else {
            agentId
        }
    }

    fun isSelection(selection: CodeWorkspaceSelection): Boolean {
        val agentIds = selection.agentIds
        return isValidId(selection.environmentId) &&
            isValidId(selection.workspaceId) &&
            (selection.checkout == null || selection.checkout == CodeWorkspaceCheckout.SOURCE ||
                selection.checkout == CodeWorkspaceCheckout.ISOLATED) &&
            (agentIds == null || (agentIds.isNotEmpty() && agentIds.size <= MAX_AGENT_CODE_ENVIRONMENT_CHOICES &&
                agentIds.all(::isValidId) && agentIds.toSet().size == agentIds.size))
    }

    /** One exact workspace per machine, each agent owning at most one of them. */
    fun isSelections(selections: List<CodeWorkspaceSelection>): Boolean {
        val environmentIds = HashSet<String>()
        val agentIds = HashSet<String>()
        for (selection in selections) {
            if (!isSelection(selection) || !environmentIds.add(selection.environmentId)) return false
            for (id in selection.agentIds.orEmpty()) {
                if (!agentIds.add(id)) return false
                if (agentIds.size > MAX_AGENT_CODE_ENVIRONMENT_CHOICES) return false
            }
        }
        return true
    }

    /** Explicit isolation never falls back to shared files when a capability or policy disappears. */
    fun isCheckoutAvailable(
        selection: CodeWorkspaceSelection,
        workspace: CodeWorkspaceDescriptor?,
        allowSelection: Boolean,
    ): Boolean = selection.checkout == null ||
        (allowSelection && workspace != null &&
            (selection.checkout == CodeWorkspaceCheckout.SOURCE || workspace.offersWorktree))

    private fun isAllowedEnvironment(
        candidate: String,
        environmentId: String?,
        environmentIds: List<String>?,
        allowSelection: Boolean,
    ): Boolean = candidate == environmentId || (allowSelection && environmentIds?.contains(candidate) == true)

    /** The outcome of [resolveCodeEnvironmentSelection]: the machine an agent runs on, or a rejection. */
    sealed interface Resolution {
        data class Valid(val environmentId: String?) : Resolution
        data object Invalid : Resolution
    }

    /**
     * An agent's default machine or its chat-owned choice. Precedence: an explicit owner of a
     * selection, then the machine inherited from the parent that spawned it, then the agent's own
     * default, then a single legacy selection. A null [selections] keeps the default.
     */
    @Suppress("ReturnCount") // upstream's early-return precedence, kept in its order
    fun resolveCodeEnvironmentSelection(
        environmentId: String? = null,
        environmentIds: List<String>? = null,
        agentId: String? = null,
        allowSelection: Boolean = false,
        selections: List<CodeWorkspaceSelection>? = null,
        inheritedEnvironmentId: String? = null,
    ): Resolution {
        if (selections == null) return Resolution.Valid(environmentId)
        if (!isSelections(selections)) return Resolution.Invalid
        if (selections.isEmpty()) return Resolution.Valid(environmentId)
        val stableAgentId = agentId?.let(::stripAgentIdSuffix)
        val owned = stableAgentId?.let { id -> selections.firstOrNull { it.agentIds?.contains(id) == true } }
        if (owned != null) {
            if (owned.environmentId == environmentId) return Resolution.Valid(environmentId)
            return if (allowSelection && environmentIds?.contains(owned.environmentId) == true) {
                Resolution.Valid(owned.environmentId)
            } else {
                Resolution.Invalid
            }
        }
        if (inheritedEnvironmentId != null &&
            isAllowedEnvironment(inheritedEnvironmentId, environmentId, environmentIds, allowSelection) &&
            selections.any { it.environmentId == inheritedEnvironmentId }
        ) {
            return Resolution.Valid(inheritedEnvironmentId)
        }
        if (!allowSelection) return Resolution.Valid(environmentId)
        val allowed = environmentIds.orEmpty().toMutableSet()
        if (!environmentId.isNullOrEmpty()) allowed.add(environmentId)
        val matches = selections.filter { it.environmentId in allowed }
        if (matches.any { it.environmentId == environmentId }) return Resolution.Valid(environmentId)
        val legacy = matches.filter { it.agentIds == null }
        if (legacy.size != 1) return Resolution.Invalid
        return Resolution.Valid(legacy[0].environmentId)
    }

    /** One agent of a run's graph, reduced to what machine routing reads. */
    data class RoutingAgent(
        val id: String,
        val routesCode: Boolean = true,
        val environmentId: String? = null,
        val environmentIds: List<String>? = null,
        val allowSelection: Boolean = false,
        val subagentIds: List<String>? = null,
        /**
         * A machine already resolved for this agent, such as an initialized root (null for none);
         * read only when [hasResolved] is set, which stands in for upstream's `undefined`.
         */
        val resolvedEnvironmentId: String? = null,
        val hasResolved: Boolean = false,
    )

    /**
     * Subagents follow their parent's attached machine when nothing names them, the machine is
     * theirs to use, attached, and already selected. Returns saved agent id → inherited machine for
     * the subagents whose route changes. See upstream for the full rule; this is a line-for-line port.
     */
    @Suppress("CyclomaticComplexMethod") // line-for-line port, diffed against upstream
    fun resolveCodeWorkspaceInheritance(
        selections: List<CodeWorkspaceSelection>?,
        rootIds: List<String>,
        agents: Map<String, RoutingAgent>,
        isAttachedEnvironment: (String) -> Boolean,
    ): Map<String, String> {
        val inherited = LinkedHashMap<String, String>()
        if (selections == null || !isSelections(selections) || selections.isEmpty()) return inherited
        val selected = selections.map { it.environmentId }.toSet()
        val owned = selections.flatMap { it.agentIds.orEmpty() }.toSet()
        // A present key with a null value is upstream's `routes.set(id, undefined)`.
        val routes = HashMap<String, String?>()

        fun routeOf(agent: RoutingAgent, parentRoute: String?): String? {
            if (!agent.routesCode) return parentRoute
            val resolved = if (agent.hasResolved) {
                agent.resolvedEnvironmentId
            } else {
                val resolution = resolveCodeEnvironmentSelection(
                    agentId = agent.id,
                    environmentId = agent.environmentId,
                    environmentIds = agent.environmentIds,
                    allowSelection = agent.allowSelection,
                    selections = selections,
                    inheritedEnvironmentId = inherited[agent.id],
                )
                (resolution as? Resolution.Valid)?.environmentId
            }
            return resolved?.takeIf { isAttachedEnvironment(it) }
        }

        fun inherits(agent: RoutingAgent, candidate: String): Boolean =
            agent.routesCode && !agent.hasResolved && candidate != agent.environmentId &&
                agent.id !in owned && candidate in selected &&
                isAllowedEnvironment(candidate, agent.environmentId, agent.environmentIds, agent.allowSelection) &&
                isAttachedEnvironment(candidate)

        val roots = LinkedHashSet(rootIds.filter { it in agents })
        val parents = collectSpawningParents(roots, agents)
        roots.forEach { id -> routes[id] = routeOf(agents.getValue(id), null) }

        fun resolve(id: String, candidate: String?) {
            val agent = agents.getValue(id)
            if (candidate != null && inherits(agent, candidate)) inherited[id] = candidate
            routes[id] = routeOf(agent, candidate)
        }

        fun resolveGroup(members: List<String>) {
            val group = members.toSet()
            val outside = LinkedHashSet<String?>()
            for (id in members) {
                parents[id]?.forEach { parentId -> if (parentId !in group) outside.add(routes[parentId]) }
            }
            val candidate = if (outside.size == 1) outside.first() else null
            members.forEach { resolve(it, candidate) }
            if (candidate != null && members.any { routes[it] != candidate }) {
                members.forEach {
                    inherited.remove(it)
                    resolve(it, null)
                }
            }
        }

        var pending = parents.keys.toList()
        while (pending.isNotEmpty()) {
            val ready = pending.filter { id -> parents[id].orEmpty().all { it in routes } }
            if (ready.isNotEmpty()) {
                for (id in ready) {
                    val candidates = parents[id].orEmpty().map { routes[it] }.toSet()
                    resolve(id, if (candidates.size == 1) candidates.first() else null)
                }
            } else {
                val groups = findSettledSpawnGroups(pending, parents, routes)
                for (members in groups.ifEmpty { listOf(pending) }) resolveGroup(members)
            }
            pending = pending.filter { it !in routes }
        }
        return inherited
    }

    private fun collectSpawningParents(
        roots: Set<String>,
        agents: Map<String, RoutingAgent>,
    ): LinkedHashMap<String, LinkedHashSet<String>> {
        val parents = LinkedHashMap<String, LinkedHashSet<String>>()
        reachableFrom(roots, agents).forEach { parentId ->
            for (childId in agents[parentId]?.subagentIds.orEmpty()) {
                if (childId in roots || childId !in agents) continue
                if (parentId !in reachableFrom(roots, agents, childId)) continue
                parents.getOrPut(childId) { LinkedHashSet() }.add(parentId)
            }
        }
        return parents
    }

    private fun reachableFrom(
        roots: Set<String>,
        agents: Map<String, RoutingAgent>,
        avoid: String? = null,
    ): LinkedHashSet<String> {
        val visited = LinkedHashSet(roots)
        val queue = ArrayList(roots)
        var index = 0
        while (index < queue.size) {
            for (childId in agents[queue[index]]?.subagentIds.orEmpty()) {
                if (childId == avoid || childId in visited || childId !in agents) continue
                visited.add(childId)
                queue.add(childId)
            }
            index++
        }
        return visited
    }

    private fun findSettledSpawnGroups(
        pending: List<String>,
        parents: Map<String, Set<String>>,
        routes: Map<String, String?>,
    ): List<List<String>> {
        val waiting = pending.toSet()
        val ancestors = pending.associateWith { waitingAncestors(it, waiting, parents) }
        val grouped = HashSet<String>()
        val groups = ArrayList<List<String>>()
        for (id in pending) {
            if (id in grouped) continue
            val members = pending.filter { other ->
                other == id || (ancestors[id]?.contains(other) == true && ancestors[other]?.contains(id) == true)
            }
            grouped.addAll(members)
            val group = members.toSet()
            val settled = members.all { member ->
                parents[member].orEmpty().all { parentId -> parentId in group || parentId in routes }
            }
            if (settled && (members.size > 1 || ancestors[id]?.contains(id) == true)) groups.add(members)
        }
        return groups
    }

    private fun waitingAncestors(id: String, waiting: Set<String>, parents: Map<String, Set<String>>): Set<String> {
        val seen = HashSet<String>()
        val stack = ArrayList(parents[id].orEmpty())
        while (stack.isNotEmpty()) {
            val current = stack.removeAt(stack.size - 1)
            if (current !in waiting || current in seen) continue
            seen.add(current)
            stack.addAll(parents[current].orEmpty())
        }
        return seen
    }
}
