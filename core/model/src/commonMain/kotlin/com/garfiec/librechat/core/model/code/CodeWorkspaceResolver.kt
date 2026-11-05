package com.garfiec.librechat.core.model.code

import com.garfiec.librechat.core.model.Agent

/**
 * What the composer knows about a chat's code workspace, as web's `useCodeWorkspace`
 * (`client/src/hooks/Agents/useCodeWorkspace.ts`) computes it, including the [Transition] that
 * moves, attaches, detaches or recovers a saved chat's decision. Pure, so every rule is
 * unit-tested here rather than through a ViewModel. Registered in `scripts/mirrors.json`.
 *
 * Two deliberate divergences:
 * - **An incomplete agent graph does not by itself require a workspace.** Web counts a linked agent
 *   it has not loaded as "maybe needs a machine" and answers `unavailable`, which with the decision
 *   version seals the chat as "No workspace". Mobile loads one page of agents, so a plain agent
 *   linking one past it would be sealed that way on every new chat. The server's
 *   `code_workspace_unavailable` / `required` rejection is the backstop instead.
 * - **Nothing is pre-selected into a send when the user has to choose** (`CHOOSE`): web sends
 *   "No workspace" there, which a phone has no way to undo (see [Resolution.canSubmit]).
 */
object CodeWorkspaceResolver {

    enum class State { NOT_REQUIRED, WITHOUT_ATTACHED, LOADING, CHOOSE, RELOCATABLE, READY, MISSING, UNAVAILABLE, UNSUPPORTED }

    /** One machine's status poll. */
    sealed interface StatusResult {
        data object Loading : StatusResult
        data object Failed : StatusResult
        data class Loaded(val status: CodeEnvironmentStatus) : StatusResult
    }

    /** Server-side capability inputs. */
    data class Capabilities(
        /** `RUN_CODE.USE`. */
        val canRunCode: Boolean,
        /** `agents.capabilities` from `GET /api/endpoints`. */
        val agentsCapabilities: List<String>,
        val statefulCodeSessions: StatefulCodeSessionsConfig?,
        val decisionVersion: Int?,
        val inheritanceVersion: Int?,
        val moveVersion: Int? = null,
        val transitionVersion: Int? = null,
        val recoveryVersion: Int? = null,
    ) {
        val supportsDecisions: Boolean get() = decisionVersion == CodeCapabilityVersions.DECISION
        val supportsInheritance: Boolean get() = inheritanceVersion == CodeCapabilityVersions.INHERITANCE
        val supportsMoves: Boolean get() = moveVersion == CodeCapabilityVersions.MOVE

        /** Attach and detach ride beside the move version, and need the decision protocol too. */
        val supportsTransitions: Boolean
            get() = supportsDecisions && transitionVersion == CodeCapabilityVersions.TRANSITION
        val supportsRecovery: Boolean get() = supportsMoves && recoveryVersion == CodeCapabilityVersions.RECOVERY
        val codeEnabled: Boolean
            get() = canRunCode && CodeWorkspaceAgents.EXECUTE_CODE in agentsCapabilities &&
                STATEFUL_CODE_SESSIONS in agentsCapabilities
    }

    /** The chat side: which agents it runs, and the decision it holds or is drafting. */
    data class Chat(
        val isAgentsConversation: Boolean,
        /** No conversation id yet. */
        val isNewChat: Boolean,
        val agentId: String?,
        val addedAgentId: String? = null,
        /**
         * The saved conversation's recorded decision ([mode] / [selections]) when it holds one, or
         * the composer's draft when it does not. [holdsSavedDecision] says which.
         */
        val mode: String?,
        val selections: List<CodeWorkspaceSelection>?,
        val holdsSavedDecision: Boolean,
    )

    data class EnvironmentResult(
        val environment: PublicCodeEnvironment,
        val state: State,
        val workspaces: List<CodeWorkspaceDescriptor>,
        val selected: CodeWorkspaceSelection?,
        /** Coding agents that need this machine, subagents included. */
        val requiredBy: List<Agent>,
        /**
         * A locked chat's recorded pick on this machine. Unlike [selected], it is known while the
         * machine is offline or no longer lists the workspace, so the row can still name its checkout.
         */
        val recorded: CodeWorkspaceSelection? = null,
        /** Agents choosing this machine per chat; a transition's pick here carries them as `agentIds`. */
        val selectionOwners: List<String> = emptyList(),
    ) {
        val allowCheckoutSelection: Boolean
            get() = environment.configSchema?.workspaces?.allowCheckoutSelection == true
    }

    data class MachineChoiceOwner(val agentId: String, val environmentIds: List<String>)

    enum class TransitionKind { MOVE, ATTACH, DETACH }

    /** A machine the recorded decision covered that the agents no longer use. */
    data class PreviousMachine(val id: String, val name: String?) {
        val displayName: String get() = name?.takeIf { it.isNotBlank() } ?: id
    }

    /**
     * Web's `CodeWorkspaceTransition`: an explicit change of a saved chat's recorded decision. It
     * replaces the decision whole and never touches messages or copies files between machines.
     * - [TransitionKind.MOVE]: the decision no longer covers every machine the agents use (an
     *   agent was pointed elsewhere, or its saved workspace disappeared: then it is a recovery).
     * - [TransitionKind.ATTACH]: the chat runs without a workspace and can now take one.
     * - [TransitionKind.DETACH]: the workspace is healthy, but its owner wants plain chat.
     */
    data class Transition(
        val kind: TransitionKind,
        /** Exactly what the chat stores; empty for a chat running without a workspace. */
        val from: List<CodeWorkspaceSelection>,
        val previous: List<PreviousMachine>,
        /** Recorded picks the agents still use, carried over unchanged. */
        val retained: List<CodeWorkspaceSelection>,
        /** Machines needing a new pick, including ones whose workspace disappeared. */
        val targets: List<EnvironmentResult>,
        /** "Continue without workspace" is on offer. */
        val detachable: Boolean,
    ) {
        /** A target lost its recorded workspace: web labels the whole transition "Recover". */
        val isRecovery: Boolean get() = targets.any { it.state == State.MISSING }

        /** A move or attach has something to confirm; otherwise only the detach is offered. */
        val offersMove: Boolean
            get() = kind != TransitionKind.DETACH && (targets.isNotEmpty() || retained.isNotEmpty())
    }

    data class Resolution(
        val required: Boolean,
        val supportsDecisions: Boolean,
        val locked: Boolean,
        val mode: String?,
        val state: State,
        /** Web's `canSubmit` for this decision. */
        val canSubmit: Boolean,
        /** Web's `visible`: shown while choosing, not sendable, or without a workspace. */
        val visible: Boolean,
        val environments: List<EnvironmentResult>,
        val machineOptions: List<PublicCodeEnvironment>,
        val machineOptionGroups: List<List<String>>,
        val machineChoiceOwners: List<MachineChoiceOwner>,
        val fixedMachineIds: List<String>,
        /** The decision's selections, resolved against the machines' current state. */
        val selections: List<CodeWorkspaceSelection>?,
        /** Machine id → root agents whose remembered pick applies there. */
        val preferenceAgentIds: Map<String, Set<String>>,
        /**
         * What the next send carries, or null while it must wait. On a chat holding a recorded
         * decision this is [CodeWorkspaceSubmission.NONE] even when sendable: the server applies
         * that decision to a turn that sends none, and re-sending it could only disagree.
         */
        val submission: CodeWorkspaceSubmission?,
        /** The submission sends "No workspace" because there was nothing to pick. */
        val sendsWithoutWorkspaceForLackOfChoice: Boolean,
        val transition: Transition? = null,
    ) {
        companion object {
            val NOT_REQUIRED = Resolution(
                required = false, supportsDecisions = false, locked = false, mode = null,
                state = State.NOT_REQUIRED, canSubmit = true, visible = false, environments = emptyList(),
                machineOptions = emptyList(), machineOptionGroups = emptyList(), machineChoiceOwners = emptyList(),
                fixedMachineIds = emptyList(), selections = null, preferenceAgentIds = emptyMap(),
                submission = CodeWorkspaceSubmission.NONE, sendsWithoutWorkspaceForLackOfChoice = false,
            )
        }
    }

    const val STATEFUL_CODE_SESSIONS = "stateful_code_sessions"

    // A line-for-line port of web's hook, kept in one piece so it can be diffed against upstream.
    @Suppress("CyclomaticComplexMethod", "LongMethod", "ComplexCondition")
    fun resolve(
        capabilities: Capabilities,
        chat: Chat,
        agentsById: Map<String, Agent>,
        statuses: Map<String, StatusResult>,
        /** A remembered pick for (machine, root agent), or null. */
        rememberedWorkspace: (environmentId: String, agentId: String) -> String? = { _, _ -> null },
    ): Resolution {
        if (!capabilities.codeEnabled || !chat.isAgentsConversation) return Resolution.NOT_REQUIRED
        val config = capabilities.statefulCodeSessions
        val environmentsConfig = config?.environments
        val allowEnvSelection = config?.allowEnvironmentSelection
        val supportsDecisions = capabilities.supportsDecisions
        val primary = chat.agentId?.let { agentsById[it] }
        val added = chat.addedAgentId?.let { agentsById[it] }
        val roots = listOf(primary, added)
        val reachable = CodeWorkspaceAgents.collectReachableAgents(
            roots, agentsById, listOf(chat.agentId, chat.addedAgentId),
        )
        val storedSelections = chat.selections
        val holdsDecision = chat.holdsSavedDecision &&
            (chat.mode != null || storedSelections.orEmpty().isNotEmpty())
        val locked = !chat.isNewChat && (holdsDecision || !supportsDecisions)
        val undecided = !locked

        val inheritance = if (capabilities.supportsInheritance) {
            CodeWorkspaceAgents.resolveReachableInheritance(
                roots, agentsById, environmentsConfig, allowEnvSelection, storedSelections, draft = undecided,
            )
        } else {
            emptyMap()
        }

        val unique = LinkedHashMap<String, PublicCodeEnvironment>()
        val defaults = HashMap<String, MutableSet<String>>()
        val requiredBy = LinkedHashMap<String, MutableList<Agent>>()
        val formerEnvironmentIds = HashSet<String>()
        var complete = true
        for (agent in reachable.agents) {
            if (!CodeWorkspaceAgents.routesCode(agent)) continue
            val environment = CodeWorkspaceAgents.findDiscoveryEnvironment(
                agent, environmentsConfig, allowEnvSelection, storedSelections, inheritance[agent.id],
            )
            // An agent pinned to a machine this user cannot see: nothing to pick, so unavailable.
            if (environment == null && !agent.codeEnvironmentId.isNullOrEmpty()) complete = false
            if (inheritance.containsKey(agent.id)) {
                val own = CodeWorkspaceRouting.resolveCodeEnvironmentSelection(
                    agentId = agent.id,
                    environmentId = CodeWorkspaceAgents.defaultEnvironmentId(agent, environmentsConfig),
                    environmentIds = agent.codeEnvironmentIds,
                    allowSelection = CodeWorkspaceAgents.choiceIds(agent, environmentsConfig, allowEnvSelection) != null,
                    selections = storedSelections,
                )
                val ownId = (own as? CodeWorkspaceRouting.Resolution.Valid)?.environmentId
                if (ownId != null && storedSelections?.any { it.environmentId == ownId } == true) {
                    formerEnvironmentIds.add(ownId)
                }
            }
            if (environment?.isAttached != true) continue
            unique[environment.id] = environment
            requiredBy.getOrPut(environment.id) { ArrayList() }.add(agent)
            val workspaceId = agent.codeWorkspaceId
            if (agent.codeEnvironmentId == environment.id && !workspaceId.isNullOrEmpty()) {
                defaults.getOrPut(environment.id) { LinkedHashSet() }.add(workspaceId)
            }
        }
        val preferenceAgentIds = LinkedHashMap<String, MutableSet<String>>()
        for ((root, rootId) in listOf(primary to chat.agentId, added to chat.addedAgentId)) {
            if (root == null || rootId == null) continue
            for (agent in CodeWorkspaceAgents.collectReachableAgents(listOf(root), agentsById, listOf(rootId)).agents) {
                if (!CodeWorkspaceAgents.routesCode(agent)) continue
                val environment = CodeWorkspaceAgents.findDiscoveryEnvironment(
                    agent, environmentsConfig, allowEnvSelection, storedSelections, inheritance[agent.id],
                )
                if (environment?.isAttached != true) continue
                preferenceAgentIds.getOrPut(environment.id) { LinkedHashSet() }.add(rootId)
            }
        }
        val attachedEnvironments = unique.values.sortedBy { it.id }
        formerEnvironmentIds.removeAll(unique.keys)
        val required = attachedEnvironments.isNotEmpty() || !complete
        if (!required) {
            // Dropping the last coding agent makes plain chat sendable but leaves the attached seal
            // on the chat; web keeps the explicit detach on offer for it.
            val sealed = locked && CodeEnvironmentMode.of(chat.mode) != CodeEnvironmentMode.WITHOUT_ATTACHED &&
                storedSelections.orEmpty().isNotEmpty()
            val detach = if (capabilities.supportsMoves && capabilities.supportsTransitions && sealed &&
                !chat.isNewChat
            ) {
                Transition(
                    kind = TransitionKind.MOVE,
                    from = storedSelections.orEmpty(),
                    previous = previousMachines(storedSelections.orEmpty(), emptySet(), environmentsConfig),
                    retained = emptyList(),
                    targets = emptyList(),
                    detachable = true,
                )
            } else {
                null
            }
            return Resolution.NOT_REQUIRED.copy(
                supportsDecisions = supportsDecisions,
                locked = locked,
                visible = detach != null,
                transition = detach,
            )
        }

        val attachedIds = attachedEnvironments.map { it.id }.toSet()
        val hasForeignStoredSelection = storedSelections?.any {
            it.environmentId !in attachedIds && it.environmentId !in formerEnvironmentIds
        } == true

        val machineChoiceOwners = if (supportsDecisions && allowEnvSelection == true) {
            reachable.agents.mapNotNull { agent ->
                if (!CodeWorkspaceAgents.routesCode(agent)) return@mapNotNull null
                CodeWorkspaceAgents.choiceIds(agent, environmentsConfig, true)?.let { MachineChoiceOwner(agent.id, it) }
            }
        } else {
            emptyList()
        }
        val machineOptionGroups = if (undecided) machineChoiceOwners.map { it.environmentIds } else emptyList()
        val maxChoices = config?.maxEnvironmentChoices
        val machineOptions = if (machineOptionGroups.isNotEmpty()) {
            environmentsConfig.orEmpty().filter { env ->
                env.isAttached && machineOptionGroups.any { env.id in it }
            }.let { options ->
                // The default machine is always allowed; the cap counts the extras.
                if (maxChoices != null && maxChoices >= 0) options.take(maxChoices + 1) else options
            }
        } else {
            emptyList()
        }
        val fixedMachineIds = reachable.agents.flatMap { agent ->
            if (!CodeWorkspaceAgents.routesCode(agent)) return@flatMap emptyList()
            if (CodeWorkspaceAgents.choiceIds(agent, environmentsConfig, allowEnvSelection) != null) {
                return@flatMap emptyList()
            }
            val environment = CodeWorkspaceAgents.findExecutionEnvironment(agent, environmentsConfig)
            if (environment?.isAttached == true) listOf(environment.id) else emptyList()
        }

        val environmentResults = attachedEnvironments.map { environment ->
            val status = statuses[environment.id]
            val loaded = (status as? StatusResult.Loaded)?.status
                ?.takeIf { it.environmentId == environment.id }
            val workspaces = if (loaded?.isReady == true) loaded.workspaces.orEmpty() else emptyList()
            var stored = storedSelections?.firstOrNull { it.environmentId == environment.id }
            var conflictingDefaults = false
            if (stored == null && undecided && !hasForeignStoredSelection) {
                val machineDefaults = defaults[environment.id].orEmpty()
                var preferred: String? = null
                if (machineDefaults.size == 1) {
                    preferred = machineDefaults.first()
                } else if (machineDefaults.isEmpty()) {
                    preferred = preferenceAgentIds[environment.id].orEmpty()
                        .mapNotNull { rememberedWorkspace(environment.id, it) }
                        .firstOrNull { id -> workspaces.any { it.id == id } }
                }
                if (preferred != null && (machineDefaults.isNotEmpty() || workspaces.any { it.id == preferred })) {
                    stored = CodeWorkspaceSelection(environment.id, preferred)
                }
                conflictingDefaults = machineDefaults.size > 1
            }
            val hasStoredSelections =
                (locked && (supportsDecisions || storedSelections.orEmpty().isNotEmpty())) ||
                    stored != null || conflictingDefaults || hasForeignStoredSelection
            val selected = if (loaded?.isReady != true) {
                null
            } else if (stored != null && workspaces.any { it.id == stored.workspaceId }) {
                stored.copy(environmentId = environment.id)
            } else if (!hasStoredSelections && workspaces.size == 1) {
                CodeWorkspaceSelection(environment.id, workspaces[0].id)
            } else {
                null
            }
            val state = when {
                status == null || status is StatusResult.Loading -> State.LOADING
                loaded == null || !loaded.isReady -> State.UNAVAILABLE
                loaded.workspaces == null -> State.UNSUPPORTED
                selected != null -> if (CodeWorkspaceRouting.isCheckoutAvailable(
                        selected,
                        workspaces.firstOrNull { it.id == selected.workspaceId },
                        environment.configSchema?.workspaces?.allowCheckoutSelection == true,
                    )
                ) {
                    State.READY
                } else {
                    State.UNSUPPORTED
                }
                stored != null -> State.MISSING
                workspaces.isEmpty() -> State.UNAVAILABLE
                else -> State.CHOOSE
            }
            EnvironmentResult(
                environment, state, workspaces, selected, requiredBy[environment.id].orEmpty(),
                recorded = if (locked) storedSelections?.firstOrNull { it.environmentId == environment.id } else null,
                selectionOwners = requiredBy[environment.id].orEmpty()
                    .map { it.id }
                    .filter { id -> machineChoiceOwners.any { it.agentId == id } },
            )
        }

        fun resolveSelections(selections: List<CodeWorkspaceSelection>?): List<CodeWorkspaceSelection>? {
            if (!complete || !CodeWorkspaceRouting.isSelections(selections.orEmpty())) return null
            if (selections?.any { it.environmentId !in attachedIds && it.environmentId !in formerEnvironmentIds } == true) {
                return null
            }
            val resolved = selections?.filter { it.environmentId in formerEnvironmentIds }.orEmpty().toMutableList()
            for (result in environmentResults) {
                val requested = selections?.firstOrNull { it.environmentId == result.environment.id }
                if (requested != null &&
                    result.state != State.LOADING && result.state != State.UNAVAILABLE &&
                    result.state != State.UNSUPPORTED &&
                    result.workspaces.any { descriptor ->
                        descriptor.id == requested.workspaceId &&
                            CodeWorkspaceRouting.isCheckoutAvailable(requested, descriptor, result.allowCheckoutSelection)
                    }
                ) {
                    resolved.add(requested.copy(environmentId = result.environment.id))
                    continue
                }
                if (requested == null && result.selected != null && result.state == State.READY) {
                    resolved.add(result.selected)
                    continue
                }
                return null
            }
            if (allowEnvSelection == true) {
                val graphInheritance = if (capabilities.supportsInheritance) {
                    CodeWorkspaceAgents.resolveReachableInheritance(
                        roots, agentsById, environmentsConfig, true, resolved,
                    )
                } else {
                    null
                }
                val conflict = reachable.agents.any { agent ->
                    CodeWorkspaceAgents.routesCode(agent) &&
                        CodeWorkspaceAgents.choiceIds(agent, environmentsConfig, true) != null &&
                        CodeWorkspaceRouting.resolveCodeEnvironmentSelection(
                            agentId = agent.id,
                            environmentId = CodeWorkspaceAgents.defaultEnvironmentId(agent, environmentsConfig),
                            environmentIds = agent.codeEnvironmentIds,
                            allowSelection = true,
                            selections = resolved,
                            inheritedEnvironmentId = graphInheritance?.get(agent.id),
                        ) == CodeWorkspaceRouting.Resolution.Invalid
                }
                if (conflict) return null
            }
            return resolved.sortedBy { it.environmentId }
        }

        val selections = resolveSelections(storedSelections)
        var inferredMode: String? = CodeEnvironmentMode.of(chat.mode)
        if (inferredMode == null && storedSelections != null) {
            inferredMode = CodeEnvironmentMode.ATTACHED
        } else if (inferredMode == null && !chat.isNewChat && supportsDecisions) {
            // Suggestions are not consent: a saved chat that never decided starts without a workspace.
            inferredMode = CodeEnvironmentMode.WITHOUT_ATTACHED
        } else if (inferredMode == null && selections != null) {
            inferredMode = CodeEnvironmentMode.ATTACHED
        } else if (inferredMode == null && supportsDecisions) {
            inferredMode = CodeEnvironmentMode.WITHOUT_ATTACHED
        }
        val explicitMode = CodeEnvironmentMode.of(chat.mode)
        val state = if (inferredMode == CodeEnvironmentMode.WITHOUT_ATTACHED &&
            (explicitMode == CodeEnvironmentMode.WITHOUT_ATTACHED || locked)
        ) {
            State.WITHOUT_ATTACHED
        } else {
            aggregateState(complete, environmentResults, selections)
        }

        // Web's resolveSubmission, then the phone's rule for an unmade choice.
        fun webSubmission(): CodeWorkspaceSubmission? {
            val requestedMode = explicitMode ?: inferredMode
                ?: if (storedSelections.orEmpty().isNotEmpty()) CodeEnvironmentMode.ATTACHED else null
            if (requestedMode == CodeEnvironmentMode.WITHOUT_ATTACHED) {
                return if (supportsDecisions) CodeWorkspaceSubmission(mode = CodeEnvironmentMode.WITHOUT_ATTACHED) else null
            }
            if (requestedMode == null && supportsDecisions) {
                return CodeWorkspaceSubmission(mode = CodeEnvironmentMode.WITHOUT_ATTACHED)
            }
            val workspaces = resolveSelections(storedSelections) ?: return null
            return CodeWorkspaceSubmission(mode = CodeEnvironmentMode.ATTACHED, workspaces = workspaces)
        }
        val web = webSubmission()
        val canSubmit = web != null
        // The user never chose "No workspace": web would send it, which seals a chat this phone
        // cannot reopen. Hold the send while there is something to pick or still loading; send it
        // only when there is nothing to pick at all.
        val implicitWithout = !locked && explicitMode == null &&
            web?.mode == CodeEnvironmentMode.WITHOUT_ATTACHED
        val nothingToPick = state == State.UNAVAILABLE || state == State.UNSUPPORTED
        val submission = when {
            web == null -> null
            // The server applies a recorded decision to a turn that sends none.
            holdsDecision -> CodeWorkspaceSubmission.NONE
            implicitWithout && !nothingToPick -> null
            else -> web
        }
        // A saved chat that never decided is not attached by a suggestion (web infers "No
        // workspace" there). Its send is held, so show it as choosing, not as a pick it isn't.
        val unconsented = implicitWithout && !nothingToPick && !chat.isNewChat &&
            environmentResults.any { it.state == State.READY }
        var finalState = if (unconsented && state == State.READY) State.CHOOSE else state
        var transition: Transition? = null
        if (capabilities.supportsMoves && locked && complete && finalState != State.LOADING && !chat.isNewChat) {
            val from = storedSelections.orEmpty()
            val retained = environmentResults.mapNotNull { if (it.state == State.READY) it.selected else null }
            val targets = environmentResults.filter {
                it.state == State.CHOOSE || (capabilities.supportsRecovery && it.state == State.MISSING)
            }
            // A transition replaces the decision whole: one that cannot name every machine the
            // agents use would seal a decision the next turn refuses. Detaching stays the escape.
            val coversEveryEnvironment = retained.size + targets.size == environmentResults.size
            val previous = previousMachines(from, attachedIds, environmentsConfig)
            if (capabilities.supportsTransitions && finalState == State.WITHOUT_ATTACHED &&
                explicitMode == CodeEnvironmentMode.WITHOUT_ATTACHED && targets.isNotEmpty() && coversEveryEnvironment
            ) {
                transition = Transition(TransitionKind.ATTACH, from, previous, retained, targets, detachable = false)
            } else if (inferredMode == CodeEnvironmentMode.ATTACHED && from.isNotEmpty() &&
                (finalState == State.CHOOSE || web == null)
            ) {
                val move = Transition(
                    TransitionKind.MOVE, from, previous,
                    retained = if (coversEveryEnvironment) retained else emptyList(),
                    targets = if (coversEveryEnvironment) targets else emptyList(),
                    detachable = capabilities.supportsTransitions,
                )
                if (move.targets.isNotEmpty() || move.retained.isNotEmpty() || move.detachable) {
                    transition = move
                    if (finalState == State.CHOOSE ||
                        (capabilities.supportsRecovery && finalState == State.MISSING && coversEveryEnvironment)
                    ) {
                        finalState = State.RELOCATABLE
                    }
                }
            } else if (capabilities.supportsTransitions && inferredMode == CodeEnvironmentMode.ATTACHED &&
                from.isNotEmpty() && finalState == State.READY
            ) {
                transition = Transition(TransitionKind.DETACH, from, previous, retained, targets, detachable = true)
            }
        }
        val visible = transition != null || !locked || !canSubmit || inferredMode == CodeEnvironmentMode.WITHOUT_ATTACHED
        val shownResults = if (unconsented) {
            environmentResults.map { if (it.state == State.READY) it.copy(state = State.CHOOSE, selected = null) else it }
        } else {
            environmentResults
        }
        return Resolution(
            required = true,
            supportsDecisions = supportsDecisions,
            locked = locked,
            mode = inferredMode,
            state = finalState,
            canSubmit = submission != null,
            visible = visible,
            environments = shownResults,
            machineOptions = machineOptions,
            machineOptionGroups = machineOptionGroups,
            machineChoiceOwners = machineChoiceOwners,
            fixedMachineIds = fixedMachineIds,
            selections = selections,
            preferenceAgentIds = preferenceAgentIds,
            submission = submission,
            sendsWithoutWorkspaceForLackOfChoice = implicitWithout && nothingToPick && submission != null,
            transition = transition,
        )
    }

    private fun previousMachines(
        from: List<CodeWorkspaceSelection>,
        usedIds: Set<String>,
        environments: List<PublicCodeEnvironment>?,
    ): List<PreviousMachine> = from.filter { it.environmentId !in usedIds }.map { selection ->
        PreviousMachine(selection.environmentId, environments?.firstOrNull { it.id == selection.environmentId }?.name)
    }

    private fun aggregateState(
        complete: Boolean,
        environments: List<EnvironmentResult>,
        selections: List<CodeWorkspaceSelection>?,
    ): State = when {
        !complete || environments.any { it.state == State.UNAVAILABLE } -> State.UNAVAILABLE
        environments.any { it.state == State.UNSUPPORTED } -> State.UNSUPPORTED
        environments.any { it.state == State.MISSING } -> State.MISSING
        environments.any { it.state == State.LOADING } -> State.LOADING
        selections == null -> State.CHOOSE
        else -> State.READY
    }

    /** What a transition would write: the picks for its targets, and the whole decision. */
    data class TransitionProposal(
        val chosen: List<CodeWorkspaceSelection>,
        val to: List<CodeWorkspaceSelection>,
        /** Every target has a pick whose checkout the machine allows, and the decision names one. */
        val ready: Boolean,
    )

    /**
     * Web's move draft (`CodeWorkspaceMenu.tsx`, `chosenTargets` / `moveReady`). A target's sole
     * workspace counts as chosen. A pick inherits the checkout the replaced decision used there,
     * or its owners used elsewhere; when those disagree the reader must choose one. Picks carry
     * the target's per-chat machine owners as `agentIds`.
     */
    fun proposeTransition(
        transition: Transition,
        choices: Map<String, CodeWorkspaceSelection>,
    ): TransitionProposal {
        val chosen = transition.targets.mapNotNull { target ->
            val id = target.environment.id
            val choice = choices[id]
            val workspaceId = choice?.workspaceId?.takeIf { picked -> target.workspaces.any { it.id == picked } }
                ?: target.workspaces.singleOrNull()?.id
                ?: return@mapNotNull null
            val previous = transition.from.filter { selection ->
                selection.environmentId == id || selection.agentIds?.any { it in target.selectionOwners } == true
            }
            val priorModes = previous.ifEmpty { transition.from }.map { it.checkout }.toSet()
            if (priorModes.size > 1 && choice?.checkout == null) return@mapNotNull null
            CodeWorkspaceSelection(
                environmentId = id,
                workspaceId = workspaceId,
                checkout = choice?.checkout ?: priorModes.singleOrNull(),
                agentIds = target.selectionOwners.takeIf { it.isNotEmpty() },
            )
        }
        val to = transition.retained + chosen
        val ready = chosen.size == transition.targets.size && to.isNotEmpty() && chosen.all { selection ->
            val target = transition.targets.first { it.environment.id == selection.environmentId }
            CodeWorkspaceRouting.isCheckoutAvailable(
                selection,
                target.workspaces.firstOrNull { it.id == selection.workspaceId },
                target.allowCheckoutSelection,
            )
        }
        return TransitionProposal(chosen, to, ready)
    }

    /**
     * Web's `selectWorkspace` (`CodeWorkspaceMenu.tsx`): the draft after picking [selection]. It
     * replaces the pick on any machine the same agents could choose instead, keeps fixed machines
     * other agents need, and records which agents own a per-chat machine choice.
     */
    fun selectWorkspace(
        resolution: Resolution,
        current: List<CodeWorkspaceSelection>?,
        selection: CodeWorkspaceSelection,
    ): List<CodeWorkspaceSelection> {
        val environmentIds = resolution.environments.map { it.environment.id }.toSet()
        val previous = resolution.environments.firstOrNull { it.environment.id == selection.environmentId }?.selected
        val picked = if (selection.checkout == null && previous?.checkout != null) {
            selection.copy(checkout = previous.checkout)
        } else {
            selection
        }
        val replacedIds = resolution.machineOptionGroups.filter { selection.environmentId in it }.flatten().toSet()
        val owners = resolution.machineChoiceOwners
            .filter { selection.environmentId in it.environmentIds }
            .map { it.agentId }
        val retained = (current ?: resolution.selections).orEmpty().mapNotNull { existing ->
            if (existing.environmentId !in environmentIds || existing.environmentId == selection.environmentId) {
                return@mapNotNull null
            }
            val remaining = existing.agentIds?.filter { it !in owners }
            val fixed = existing.environmentId in resolution.fixedMachineIds
            val drop = !fixed &&
                if (existing.agentIds == null) existing.environmentId in replacedIds else remaining.isNullOrEmpty()
            if (drop) return@mapNotNull null
            existing.copy(agentIds = remaining?.takeIf { it.isNotEmpty() })
        }
        return (retained + picked.copy(agentIds = owners.takeIf { it.isNotEmpty() }))
            .sortedBy { it.environmentId }
    }
}
