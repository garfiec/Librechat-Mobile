package com.garfiec.librechat.feature.chat.viewmodel.delegate

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.CodeWorkspacePreferenceStore
import com.garfiec.librechat.core.data.repository.CodeDecisionMove
import com.garfiec.librechat.core.data.repository.CodeEnvironmentRepository
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.data.repository.SavedCodeDecision
import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.code.CodeApproval
import com.garfiec.librechat.core.model.code.CodeEnvironmentMode
import com.garfiec.librechat.core.model.code.CodeWorkspaceResolver
import com.garfiec.librechat.core.model.code.CodeWorkspaceResolver.StatusResult
import com.garfiec.librechat.core.model.code.CodeWorkspaceSelection
import com.garfiec.librechat.core.model.code.CodeWorkspaceSubmission
import com.garfiec.librechat.core.model.code.CodeWorkspaceUnavailableReason
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.model.error.StreamErrorType
import com.garfiec.librechat.core.model.error.streamErrorMarker
import com.garfiec.librechat.core.model.permissions.Permission
import com.garfiec.librechat.core.model.permissions.PermissionType
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import com.garfiec.librechat.core.model.permissions.hasAccessOrPermissive
import com.garfiec.librechat.feature.chat.util.approvalChip
import com.garfiec.librechat.feature.chat.util.codeWorkspaceUiState
import com.garfiec.librechat.feature.chat.util.wire
import com.garfiec.librechat.feature.chat.viewmodel.ChatNotice
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.CodeApprovalOption
import com.garfiec.librechat.feature.chat.viewmodel.CodeCheckoutMode
import com.garfiec.librechat.feature.chat.viewmodel.CodeConfirming
import com.garfiec.librechat.feature.chat.viewmodel.CodeWorkspaceHandle
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Owns the chat's attached-machine workspace decision: which machines the chat's coding agents
 * need, their live status, what the composer row shows, and what each send carries. The rules are
 * `CodeWorkspaceResolver` (`:core:model`, a port of web's `useCodeWorkspace`); this class only
 * gathers its inputs, polls, and keeps the draft.
 *
 * - **The draft is per chat.** A new chat starts with none, so pre-selection (the agent's default
 *   workspace, a remembered pick, the only workspace) applies; switching agents re-resolves it while
 *   keeping a pick on a machine the new agent also uses. [reset] clears it.
 * - **A saved chat's decision is read live** ([CodeEnvironmentRepository.savedDecision]), only when
 *   the chat has a coding agent, and again whenever a run ends: the first turn is what records it.
 *   Until it is known the send waits; if it cannot be read the send carries nothing, which keeps
 *   whatever the server recorded.
 * - **Status is polled every [POLL_MS]** while the row is visible and the app is in front, 30 s
 *   after a 503/504, and never again for a machine that answered 404. One read is always made when
 *   the set of machines changes, since the row's visibility itself depends on it.
 * - **A saved chat's decision changes only through a transition** (move, attach, detach, recover:
 *   `PATCH …/decision`). The send waits while one is in flight, and the server's answer becomes the
 *   saved decision. A refusal as `locked`, or a write whose outcome is unknown, re-reads it.
 * - **A failed turn that carried a decision is reconciled** (web's
 *   `useReconcileConversationCodeEnvironmentMutation`): the server may or may not have recorded it,
 *   so the decision is re-read and the send waits, showing "Confirming…", until it is known. A read
 *   that fails offers a retry instead of sending nothing, which could seal the chat.
 */
class CodeWorkspaceDelegate(
    private val handle: CodeWorkspaceHandle,
    private val configRepository: ConfigRepository,
    private val roleRepository: RoleRepository,
    private val environments: CodeEnvironmentRepository,
    private val preferences: CodeWorkspacePreferenceStore,
    private val clock: Clock = Clock.System,
) {
    private data class Draft(val chatKey: String?, val mode: String? = null, val selections: List<CodeWorkspaceSelection>? = null)

    private sealed interface Saved {
        data object Pending : Saved
        data object Unreadable : Saved

        /** A reconciling read failed: unknown whether the failed turn recorded its decision. */
        data object ReconcileFailed : Saved
        data class Known(val decision: SavedCodeDecision) : Saved
    }

    /** A transition's picks for one chat, machine id → workspace (with its checkout). */
    private data class TransitionDraft(val conversationId: String, val choices: Map<String, CodeWorkspaceSelection>)

    private data class ChatInputs(
        val endpoint: String?,
        val agentId: String?,
        val addedAgentId: String?,
        val agents: List<Agent>,
        val agentsCapabilities: List<String>,
        val stateful: com.garfiec.librechat.core.model.code.StatefulCodeSessionsConfig?,
        val conversationId: String?,
        val isStreaming: Boolean,
        /** The selected spec's preset `codeApprovalMode`. */
        val specApprovalMode: String?,
    )

    private val draft = MutableStateFlow(Draft(chatKey = null))
    private val saved = MutableStateFlow<Pair<String, Saved>?>(null)
    private val statuses = MutableStateFlow<Map<String, StatusResult>>(emptyMap())
    private val remembered = MutableStateFlow<Map<Pair<String, String>, String>>(emptyMap())
    private val foreground = MutableStateFlow(true)
    private val browsing = MutableStateFlow<String?>(null)
    private val sheetOpen = MutableStateFlow(false)
    private val attention = MutableStateFlow(false)
    private val transitionDraft = MutableStateFlow<TransitionDraft?>(null)
    private val moving = MutableStateFlow(false)

    /** The chat is confirming its decision after a failed turn or an unconfirmed transition. */
    private val reconciling = MutableStateFlow(false)

    /** The last send carried an explicit decision: if its turn fails, the decision is reconciled. */
    private var sentDecision = false

    /** A decision taken while a run was going (a queued follow-up's): it belongs to the next run. */
    private var queuedDecision = false

    /** The approval mode picked in this chat (keyed like the draft), and the device's last pick. */
    private val approvalPick = MutableStateFlow<Pair<String?, String>?>(null)
    private val approvalRemembered = MutableStateFlow<String?>(null)
    private var latestApproval: CodeApproval.Picker = CodeApproval.Picker.NONE
    private var latestApprovalRequest: String? = null

    private val lastFetched = HashMap<String, Instant>()
    private val goneMachines = HashSet<String>()
    private var backoffUntil: Instant? = null

    /** The latest resolution; what [submissionForSend] answers from. Main-confined, like the scope. */
    private var latest: CodeWorkspaceResolver.Resolution = CodeWorkspaceResolver.Resolution.NOT_REQUIRED

    private var started = false
    private var pollJob: Job? = null

    fun start() {
        if (started) return
        started = true
        handle.scope.launch {
            remembered.value = preferences.snapshot()
            approvalRemembered.value = preferences.lastApprovalMode()
        }
        val chatInputs = handle.stateFlow.map(::chatInputsOf).distinctUntilChanged()
        val capabilities = combine(configRepository.startupConfig, roleRepository.userPermissions) { config, role ->
            config to role
        }.distinctUntilChanged()

        // Keep the draft and the saved decision keyed to the chat on screen.
        handle.scope.launch {
            chatInputs.map { it.conversationId to it.isStreaming }.distinctUntilChanged().collect { (conversationId, streaming) ->
                onChatChanged(conversationId, streaming)
            }
        }

        handle.scope.launch {
            combine(
                chatInputs,
                capabilities,
                combine(draft, saved, statuses, remembered) { d, s, st, r -> Quad(d, s, st, r) },
                combine(browsing, sheetOpen, attention) { b, o, a -> Triple(b, o, a) },
                combine(transitionDraft, moving, reconciling, approvalPick, approvalRemembered) { t, m, r, pick, last ->
                    Extras(t, m, r, pick, last)
                },
            ) { inputs, (config, role), quad, (b, open, att), extras ->
                val transition = extras.transition
                val isMoving = extras.moving
                val isReconciling = extras.reconciling
                val d = quad.a
                val st = quad.c
                val savedState = quad.b?.takeIf { it.first == inputs.conversationId }?.second
                val resolution = resolve(inputs, config, role, quad, Pending(isMoving, isReconciling))
                latest = resolution
                val confirming = when {
                    !isReconciling || inputs.conversationId == null -> null
                    savedState == Saved.ReconcileFailed -> CodeConfirming.FAILED
                    savedState is Saved.Known -> null
                    else -> CodeConfirming.PENDING
                }
                val choices = transition?.takeIf { it.conversationId == inputs.conversationId }?.choices.orEmpty()
                val ui = codeWorkspaceUiState(
                    resolution, d.mode, b, b?.let { st[it] }, open, att,
                    transitionChoices = choices,
                    moving = isMoving,
                    confirming = confirming,
                )
                val requested = requestedApprovalMode(
                    pick = extras.approvalPick?.takeIf { it.first == inputs.conversationId }?.second,
                    saved = (savedState as? Saved.Known)?.decision,
                    specPreset = inputs.specApprovalMode,
                    remembered = extras.lastApproval,
                )
                val picker = approvalPicker(inputs, resolution, d, savedState, requested)
                latestApproval = picker
                latestApprovalRequest = requested
                Pair(resolution, ui.copy(approval = approvalChip(picker)))
            }.collect { (resolution, ui) ->
                handle.update { codeWorkspace = ui }
                retarget(resolution)
            }
        }

        // A rejection that names the workspace: refresh the machines and tint the chip. A failed
        // turn that carried a decision also re-reads what the server recorded.
        handle.scope.launch {
            handle.stateFlow.map { it.error }.distinctUntilChanged().collect { error ->
                if (error != null) reconcileAfterFailedTurn()
                val reason = error?.let(StreamErrorType::codeWorkspaceReasonOfMarker) ?: return@collect
                if (reason.needsWorkspaceAttention) {
                    attention.value = true
                    refresh()
                }
            }
        }
    }

    /** Web's `reconcileFailedCodeDecision`: only a turn that sent a decision, once it has ended. */
    private fun reconcileAfterFailedTurn() {
        val state = handle.state
        val conversationId = state.conversationId ?: return
        // A notice (`CODE_WORKSPACE_SENT_WITHOUT`, an upload or rename failure, a refused send) is
        // not a turn the server ran and failed: nothing it did can have changed the decision.
        val failedTurn = state.error?.startsWith(ChatNotice.MARKER_PREFIX) == false
        if (!sentDecision || state.isStreaming || !failedTurn) return
        sentDecision = false
        startReconcile(conversationId)
    }

    private fun startReconcile(conversationId: String) {
        reconciling.value = true
        if (saved.value?.first == conversationId) saved.value = null
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    /** A transition being written, or a decision being confirmed: either holds the send. */
    private data class Pending(val moving: Boolean, val reconciling: Boolean)

    private data class Extras(
        val transition: TransitionDraft?,
        val moving: Boolean,
        val reconciling: Boolean,
        val approvalPick: Pair<String?, String>?,
        val lastApproval: String?,
    )

    private fun approvalPicker(
        inputs: ChatInputs,
        resolution: CodeWorkspaceResolver.Resolution,
        draft: Draft,
        savedState: Saved?,
        requested: String?,
    ): CodeApproval.Picker {
        val agentsById = inputs.agents.associateBy { it.id }
        val decision = (savedState as? Saved.Known)?.decision?.takeIf { it.holdsDecision }
        return CodeApproval.resolve(
            config = inputs.stateful,
            isAgentsConversation = inputs.endpoint == EndpointConstants.AGENTS,
            roots = listOf(inputs.agentId?.let(agentsById::get), inputs.addedAgentId?.let(agentsById::get)),
            rootIds = listOf(inputs.agentId, inputs.addedAgentId),
            agentsById = agentsById,
            selections = decision?.workspaces ?: draft.selections,
            environmentMode = resolution.mode,
            requested = requested,
        )
    }

    private fun chatInputsOf(state: ChatUiState): ChatInputs {
        val comparison = state.comparisonState
        val agents = state.selection.endpointConfigs[EndpointConstants.AGENTS]
        return ChatInputs(
            endpoint = state.selectedEndpoint,
            agentId = state.selectedModel.takeIf { state.selectedEndpoint == EndpointConstants.AGENTS },
            addedAgentId = comparison.secondaryModel
                .takeIf { comparison.isEnabled && comparison.secondaryEndpoint == EndpointConstants.AGENTS },
            agents = state.selection.agents,
            agentsCapabilities = agents?.capabilities.orEmpty(),
            stateful = agents?.statefulCodeSessions,
            conversationId = state.conversationId,
            isStreaming = state.isStreaming,
            specApprovalMode = state.selectedSpec?.let { spec ->
                state.modelSpecs?.list?.firstOrNull { it.name == spec }?.preset?.codeApprovalMode
            },
        )
    }

    private var wasStreaming = false

    private fun onChatChanged(conversationId: String?, streaming: Boolean) {
        val current = draft.value
        if (current.chatKey != conversationId) {
            // A new chat's first turn gives it an id; its draft is what that turn sent, so it stays
            // until the recorded decision is read. Any other change of chat starts over.
            draft.value = if (current.chatKey == null && conversationId != null) {
                current.copy(chatKey = conversationId)
            } else {
                Draft(chatKey = conversationId)
            }
            attention.value = false
            browsing.value = null
            sheetOpen.value = false
            saved.value = null
            transitionDraft.value = null
            reconciling.value = false
            // A new chat's first turn keeps what it sent; another chat's turn is not this one's.
            if (current.chatKey != null || conversationId == null) {
                sentDecision = false
                queuedDecision = false
            }
            // A new chat's pick follows it to its id, like the draft; any other change of chat drops it.
            approvalPick.update { pick ->
                pick?.takeIf { current.chatKey == null && conversationId != null && it.first == null }
                    ?.copy(first = conversationId)
            }
        }
        // The first turn records the decision, so read it again whenever a run ends.
        if (!wasStreaming && streaming && queuedDecision) {
            sentDecision = true
            queuedDecision = false
        }
        if (wasStreaming && !streaming && conversationId != null) {
            saved.value = null
            reconcileAfterFailedTurn()
            // Settled either way: a later error (a notice, an unrelated failure) is not this turn's.
            sentDecision = false
        }
        wasStreaming = streaming
    }

    private fun resolve(
        inputs: ChatInputs,
        config: StartupConfig?,
        role: UserRolePermissions?,
        chatState: Quad<Draft, Pair<String, Saved>?, Map<String, StatusResult>, Map<Pair<String, String>, String>>,
        pending: Pending,
    ): CodeWorkspaceResolver.Resolution {
        val draft = chatState.a
        val savedEntry = chatState.b
        val statuses = chatState.c
        val remembered = chatState.d
        val capabilities = CodeWorkspaceResolver.Capabilities(
            canRunCode = role.hasAccessOrPermissive(PermissionType.RUN_CODE, Permission.USE),
            agentsCapabilities = inputs.agentsCapabilities,
            statefulCodeSessions = inputs.stateful,
            decisionVersion = config?.codeEnvironmentDecisionVersion,
            inheritanceVersion = config?.codeWorkspaceInheritanceVersion,
            moveVersion = config?.codeEnvironmentMoveVersion,
            transitionVersion = config?.codeEnvironmentTransitionVersion,
            recoveryVersion = config?.codeWorkspaceRecoveryVersion,
        )
        val agentsById = inputs.agents.associateBy { it.id }
        val conversationId = inputs.conversationId
        val savedState = savedEntry?.takeIf { it.first == conversationId }?.second
        val decision = (savedState as? Saved.Known)?.decision
        val holds = decision?.holdsDecision == true
        val chat = CodeWorkspaceResolver.Chat(
            isAgentsConversation = inputs.endpoint == EndpointConstants.AGENTS,
            isNewChat = conversationId == null,
            agentId = inputs.agentId,
            addedAgentId = inputs.addedAgentId,
            mode = if (holds) decision?.mode else draft.mode,
            selections = if (holds) decision?.workspaces else draft.selections,
            holdsSavedDecision = holds,
        )
        val resolution = CodeWorkspaceResolver.resolve(capabilities, chat, agentsById, statuses) { env, agent ->
            remembered[agent to env]
        }
        if (conversationId == null) return resolution
        // Web's `replacingDecision` and `recovery`: no decided answer yet, so the send waits.
        val held = resolution.copy(submission = null, canSubmit = false)
        if (pending.moving) return held
        if (pending.reconciling && savedState !is Saved.Known) {
            if (savedState == null && !inputs.isStreaming) fetchSaved(conversationId)
            return held.copy(visible = true)
        }
        if (!resolution.required && resolution.transition == null) return resolution
        val unknown = resolution.copy(visible = false, submission = CodeWorkspaceSubmission.NONE, canSubmit = true)
        return when (savedState) {
            is Saved.Known -> resolution
            Saved.ReconcileFailed -> held
            // Unknown whether this chat decided: send nothing if the read failed, which keeps
            // whatever the server recorded.
            Saved.Unreadable -> unknown
            // A run is out, usually the one recording the decision: what it sent is the server's
            // now, so a follow-up queued meanwhile sends nothing rather than a pre-selection that
            // could differ from it (`locked`). The read waits for the run to end.
            else -> if (inputs.isStreaming) {
                unknown
            } else {
                fetchSaved(conversationId)
                resolution.copy(visible = false, submission = null, canSubmit = false)
            }
        }
    }

    private fun fetchSaved(conversationId: String) {
        // Read the live value, not the snapshot the caller resolved from: combine can hand a
        // transform a superseded Pending after the read already answered, and re-reading on it
        // would never settle. A failed read is retried only when a run ends (saved reset to null).
        val entry = saved.value
        if (entry?.first == conversationId) return
        saved.value = conversationId to Saved.Pending
        handle.scope.launch {
            val result = environments.savedDecision(conversationId)
            if (saved.value?.first != conversationId) return@launch
            val known = when (result) {
                is Result.Success -> result.data
                // The server never saved this chat (a first turn it refused): nothing is recorded.
                is Result.Error -> SavedCodeDecision(mode = null, workspaces = null)
                    .takeIf { (result.exception as? ApiException)?.statusCode == HTTP_NOT_FOUND }
                Result.Loading -> null
            }
            saved.value = conversationId to when {
                known != null -> {
                    // Once recorded, the decision is the server's; the draft has done its job.
                    if (known.holdsDecision) draft.update { it.copy(mode = null, selections = null) }
                    reconciling.value = false
                    Saved.Known(known)
                }
                reconciling.value -> Saved.ReconcileFailed
                else -> Saved.Unreadable
            }
        }
    }

    private var pollTarget: Pair<Set<String>, Boolean>? = null

    private fun retarget(resolution: CodeWorkspaceResolver.Resolution) {
        val ids = (resolution.environments.map { it.environment.id } + listOfNotNull(browsing.value)).toSet()
        if (resolution.state != CodeWorkspaceResolver.State.LOADING &&
            attention.value && latestStateKey != stateKey(resolution)
        ) {
            attention.value = false
        }
        latestStateKey = stateKey(resolution)
        val active = resolution.visible && foreground.value
        val target = ids to active
        // Compared before launching, not by the job's state: on an immediate dispatcher the launch
        // runs inline, its first read re-enters here, and a check on a job not yet assigned would
        // start another poll from inside the first.
        if (target == pollTarget) return
        pollTarget = target
        pollJob?.cancel()
        if (ids.isEmpty()) return
        pollJob = handle.scope.launch { poll(ids, active) }
    }

    private var latestStateKey: Any? = null
    private fun stateKey(resolution: CodeWorkspaceResolver.Resolution): Any =
        resolution.state to resolution.environments.map { it.state }

    private suspend fun poll(ids: Set<String>, active: Boolean) {
        while (true) {
            fetchStatuses(ids)
            if (!active) return
            delay(if ((backoffUntil ?: Instant.DISTANT_PAST) > clock.now()) BACKOFF_MS else POLL_MS)
        }
    }

    private suspend fun fetchStatuses(ids: Set<String>) = coroutineScope {
        val now = clock.now()
        // A restart of the loop (a row that appeared, the app back in front) re-reads only what is stale.
        val due = ids.filter { id -> id !in goneMachines && lastFetched[id]?.let { now - it < MIN_REFETCH } != true }
        due.forEach { id -> if (statuses.value[id] == null) statuses.update { it + (id to StatusResult.Loading) } }
        due.map { id ->
            async {
                val result = environments.status(id)
                lastFetched[id] = clock.now()
                id to result
            }
        }.awaitAll().forEach { (id, result) ->
            val next = when (result) {
                is Result.Success -> StatusResult.Loaded(result.data)
                is Result.Error -> {
                    when ((result.exception as? ApiException)?.statusCode) {
                        HTTP_NOT_FOUND -> goneMachines.add(id)
                        HTTP_UNAVAILABLE, HTTP_TIMEOUT -> backoffUntil = clock.now() + BACKOFF_MS.milliseconds
                    }
                    Logger.d { "Code machine status failed: ${result.message}" }
                    StatusResult.Failed
                }
                Result.Loading -> null
            }
            if (next != null) statuses.update { it + (id to next) }
        }
    }

    fun onForeground(inFront: Boolean) {
        foreground.value = inFront
        pollTarget = null
        retarget(latest)
    }

    /** A new chat on this screen: the previous chat's draft, picks and status say nothing about it. */
    fun reset() {
        draft.value = Draft(chatKey = null)
        approvalPick.value = null
        saved.value = null
        browsing.value = null
        sheetOpen.value = false
        attention.value = false
    }

    /**
     * Refetches every machine's status now (the sheet's Refresh, the locked status chip), and
     * re-reads a decision that could not be read or confirmed (web's "Retry workspace check").
     */
    fun refresh() {
        // A saved decision that could not be read is read again too (otherwise only when a run
        // ends), so a chat opened offline recovers from Refresh.
        if (saved.value?.second == Saved.Unreadable || saved.value?.second == Saved.ReconcileFailed) saved.value = null
        goneMachines.clear()
        lastFetched.clear()
        pollTarget = null
        retarget(latest)
    }

    fun openSheet(machineId: String? = null) {
        // A machine picked from the machine chip narrows the sheet to it, as web's menu does.
        browsing.value = machineId
        sheetOpen.value = true
        if (machineId != null) {
            pollTarget = null
            retarget(latest)
        }
    }

    fun dismissSheet() {
        sheetOpen.value = false
        browsing.value = null
    }

    fun selectWorkspace(environmentId: String, workspaceId: String) {
        val resolution = latest
        if (resolution.locked) return
        val selection = CodeWorkspaceSelection(environmentId, workspaceId)
        val next = CodeWorkspaceResolver.selectWorkspace(resolution, draft.value.selections, selection)
        draft.update { it.copy(mode = CodeEnvironmentMode.ATTACHED, selections = next) }
        val owners = resolution.preferenceAgentIds[environmentId].orEmpty()
            .ifEmpty {
                val state = handle.state
                setOfNotNull(state.selectedModel.takeIf { state.selectedEndpoint == EndpointConstants.AGENTS })
            }
        handle.scope.launch {
            preferences.remember(environmentId, workspaceId, owners)
            remembered.value = preferences.snapshot()
        }
        sheetOpen.value = false
        browsing.value = null
    }

    /** The checkout for the selected workspace on [environmentId]; never closes the sheet. */
    fun setCheckout(environmentId: String, mode: CodeCheckoutMode) {
        val resolution = latest
        if (resolution.locked) return
        val current = resolution.environments.firstOrNull { it.environment.id == environmentId }?.selected ?: return
        val withCheckout = current.copy(agentIds = null, checkout = mode.wire())
        val base = CodeWorkspaceResolver.selectWorkspace(resolution, draft.value.selections, withCheckout)
        val next = base.map { if (it.environmentId == environmentId) it.copy(checkout = mode.wire()) else it }
        draft.update { it.copy(mode = CodeEnvironmentMode.ATTACHED, selections = next) }
    }

    fun selectNoWorkspace() {
        if (latest.locked || !latest.supportsDecisions) return
        draft.update { it.copy(mode = CodeEnvironmentMode.WITHOUT_ATTACHED, selections = null) }
        sheetOpen.value = false
        browsing.value = null
    }

    /** A transition's pick: the workspace on [environmentId], keeping the checkout already chosen there. */
    fun selectTransitionWorkspace(environmentId: String, workspaceId: String) {
        val conversationId = handle.state.conversationId ?: return
        if (latest.transition == null) return
        transitionDraft.update { current ->
            val choices = current?.takeIf { it.conversationId == conversationId }?.choices.orEmpty()
            val checkout = choices[environmentId]?.checkout ?: chosenFor(environmentId)?.checkout
            TransitionDraft(conversationId, choices + (environmentId to CodeWorkspaceSelection(environmentId, workspaceId, checkout)))
        }
    }

    /** A transition target's checkout; applies to the workspace currently chosen there. */
    fun setTransitionCheckout(environmentId: String, mode: CodeCheckoutMode) {
        val conversationId = handle.state.conversationId ?: return
        val chosen = chosenFor(environmentId) ?: return
        transitionDraft.update { current ->
            val choices = current?.takeIf { it.conversationId == conversationId }?.choices.orEmpty()
            TransitionDraft(conversationId, choices + (environmentId to chosen.copy(agentIds = null, checkout = mode.wire())))
        }
    }

    /** The transition's current pick on [environmentId], drafted or implied (a sole workspace). */
    private fun chosenFor(environmentId: String): CodeWorkspaceSelection? {
        val transition = latest.transition ?: return null
        val choices = transitionDraft.value?.takeIf { it.conversationId == handle.state.conversationId }?.choices.orEmpty()
        return CodeWorkspaceResolver.proposeTransition(transition, choices).chosen
            .firstOrNull { it.environmentId == environmentId }
    }

    /** Confirms the move, attach or recovery as drafted. */
    fun confirmTransition() {
        val transition = latest.transition ?: return
        val choices = transitionDraft.value?.takeIf { it.conversationId == handle.state.conversationId }?.choices.orEmpty()
        val proposal = CodeWorkspaceResolver.proposeTransition(transition, choices)
        if (!proposal.ready) return
        applyTransition(transition, proposal.to)
    }

    /** "Continue without workspace": an empty target set. */
    fun detach() {
        val transition = latest.transition?.takeIf { it.detachable } ?: return
        applyTransition(transition, emptyList())
    }

    private fun applyTransition(transition: CodeWorkspaceResolver.Transition, to: List<CodeWorkspaceSelection>) {
        val conversationId = handle.state.conversationId ?: return
        if (moving.value || handle.state.isStreaming) return
        moving.value = true
        sheetOpen.value = false
        // The move answers only the workspace; the chat keeps its approval mode (web spreads the
        // answer over the cached conversation). Dropping it would fall back to the device-wide last
        // pick, possibly more access than the chat had. Read before the request: the saved decision
        // can be reset while it is out.
        val approvalMode = (saved.value?.takeIf { it.first == conversationId }?.second as? Saved.Known)
            ?.decision?.approvalMode
        handle.scope.launch {
            val result = environments.moveDecision(conversationId, transition.from, to)
            moving.value = false
            if (handle.state.conversationId != conversationId) return@launch
            when (result) {
                is CodeDecisionMove.Moved -> {
                    transitionDraft.value = null
                    saved.value = conversationId to Saved.Known(
                        result.decision.copy(approvalMode = result.decision.approvalMode ?: approvalMode),
                    )
                    to.forEach { selection ->
                        val owners = latest.preferenceAgentIds[selection.environmentId].orEmpty()
                        if (owners.isNotEmpty()) preferences.remember(selection.environmentId, selection.workspaceId, owners)
                    }
                    remembered.value = preferences.snapshot()
                }
                is CodeDecisionMove.Refused -> {
                    // A conflict says nothing about what the chat holds now: read it again.
                    if (result.reason == CodeWorkspaceUnavailableReason.LOCKED) startReconcile(conversationId)
                    val marker = if (result.reason == CodeWorkspaceUnavailableReason.LOCKED) {
                        ChatNotice.CODE_WORKSPACE_MOVE_STALE.marker()
                    } else {
                        result.reason.streamErrorMarker()
                    }
                    handle.update { error = marker }
                }
                CodeDecisionMove.Busy -> handle.update { error = ChatNotice.CODE_WORKSPACE_MOVE_BUSY.marker() }
                CodeDecisionMove.Disabled, CodeDecisionMove.NotFound ->
                    handle.update { error = ChatNotice.CODE_WORKSPACE_MOVE_FAILED.marker() }
                is CodeDecisionMove.Failed -> {
                    if (result.unknownOutcome) startReconcile(conversationId)
                    handle.update { error = ChatNotice.CODE_WORKSPACE_MOVE_FAILED.marker() }
                }
            }
        }
    }

    /** Picks the approval mode for this chat's next turns, and remembers it for new chats. */
    fun selectApprovalMode(option: CodeApprovalOption) {
        val mode = option.wire()
        if (mode !in latestApproval.modes) return
        approvalPick.value = handle.state.conversationId to mode
        approvalRemembered.value = mode
        handle.scope.launch { preferences.rememberApprovalMode(mode) }
    }

    /** What a send carries now, with the turn's approval mode; null while the row holds the send. */
    fun submissionForSend(): CodeWorkspaceSubmission? = latest.submission
        ?.let { submission ->
            submission.copy(
                approvalMode = CodeApproval.submitted(latestApproval, latestApprovalRequest, submission.mode ?: latest.mode),
            )
        }
        .also { taken ->
            val carries = taken?.mode != null
            if (handle.state.isStreaming) queuedDecision = queuedDecision || carries else sentDecision = carries
        }

    /** The send is going out without a workspace because there was nothing to pick. */
    fun sendsWithoutWorkspace(): Boolean = latest.sendsWithoutWorkspaceForLackOfChoice

    private companion object {
        const val POLL_MS = 10_000L
        const val BACKOFF_MS = 30_000L
        val MIN_REFETCH = 9.seconds
        const val HTTP_NOT_FOUND = 404
        const val HTTP_UNAVAILABLE = 503
        const val HTTP_TIMEOUT = 504
    }
}

/**
 * The mode a chat asks for before policy clamps it (`CodeApproval.resolve`): this chat's pick, else
 * the mode its saved decision recorded, else, while the chat holds no decision, the selected spec's
 * preset (web copies the preset into a new conversation, whose `codeApprovalMode` then beats the
 * remembered pick), else the device's last pick. A decided chat without a mode keeps the last pick,
 * as web reopens one. Never remembered: only an explicit pick is.
 */
internal fun requestedApprovalMode(
    pick: String?,
    saved: SavedCodeDecision?,
    specPreset: String?,
    remembered: String?,
): String? = pick ?: saved?.approvalMode ?: specPreset.takeIf { saved?.holdsDecision != true } ?: remembered
