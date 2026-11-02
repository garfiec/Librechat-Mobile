package com.garfiec.librechat.feature.chat.viewmodel.delegate

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.CodeWorkspacePreferenceStore
import com.garfiec.librechat.core.data.repository.CodeEnvironmentRepository
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.data.repository.SavedCodeDecision
import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.code.CodeEnvironmentMode
import com.garfiec.librechat.core.model.code.CodeWorkspaceResolver
import com.garfiec.librechat.core.model.code.CodeWorkspaceResolver.StatusResult
import com.garfiec.librechat.core.model.code.CodeWorkspaceSelection
import com.garfiec.librechat.core.model.code.CodeWorkspaceSubmission
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.model.error.StreamErrorType
import com.garfiec.librechat.core.model.permissions.Permission
import com.garfiec.librechat.core.model.permissions.PermissionType
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import com.garfiec.librechat.core.model.permissions.hasAccessOrPermissive
import com.garfiec.librechat.feature.chat.util.codeWorkspaceUiState
import com.garfiec.librechat.feature.chat.util.wire
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.CodeCheckoutMode
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
        data class Known(val decision: SavedCodeDecision) : Saved
    }

    private data class ChatInputs(
        val endpoint: String?,
        val agentId: String?,
        val addedAgentId: String?,
        val agents: List<Agent>,
        val agentsCapabilities: List<String>,
        val stateful: com.garfiec.librechat.core.model.code.StatefulCodeSessionsConfig?,
        val conversationId: String?,
        val isStreaming: Boolean,
    )

    private val draft = MutableStateFlow(Draft(chatKey = null))
    private val saved = MutableStateFlow<Pair<String, Saved>?>(null)
    private val statuses = MutableStateFlow<Map<String, StatusResult>>(emptyMap())
    private val remembered = MutableStateFlow<Map<Pair<String, String>, String>>(emptyMap())
    private val foreground = MutableStateFlow(true)
    private val browsing = MutableStateFlow<String?>(null)
    private val sheetOpen = MutableStateFlow(false)
    private val attention = MutableStateFlow(false)

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
        handle.scope.launch { remembered.value = preferences.snapshot() }
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
            ) { inputs, (config, role), quad, (b, open, att) ->
                val d = quad.a
                val st = quad.c
                val resolution = resolve(inputs, config, role, d, quad.b, st, quad.d)
                latest = resolution
                Pair(resolution, codeWorkspaceUiState(resolution, d.mode, b, b?.let { st[it] }, open, att))
            }.collect { (resolution, ui) ->
                handle.update { codeWorkspace = ui }
                retarget(resolution)
            }
        }

        // A rejection that names the workspace: refresh the machines and tint the chip.
        handle.scope.launch {
            handle.stateFlow.map { it.error }.distinctUntilChanged().collect { error ->
                val reason = error?.let(StreamErrorType::codeWorkspaceReasonOfMarker) ?: return@collect
                if (reason.needsWorkspaceAttention) {
                    attention.value = true
                    refresh()
                }
            }
        }
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

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
        }
        // The first turn records the decision, so read it again whenever a run ends.
        if (wasStreaming && !streaming && conversationId != null) saved.value = null
        wasStreaming = streaming
    }

    private fun resolve(
        inputs: ChatInputs,
        config: StartupConfig?,
        role: UserRolePermissions?,
        draft: Draft,
        savedEntry: Pair<String, Saved>?,
        statuses: Map<String, StatusResult>,
        remembered: Map<Pair<String, String>, String>,
    ): CodeWorkspaceResolver.Resolution {
        val capabilities = CodeWorkspaceResolver.Capabilities(
            canRunCode = role.hasAccessOrPermissive(PermissionType.RUN_CODE, Permission.USE),
            agentsCapabilities = inputs.agentsCapabilities,
            statefulCodeSessions = inputs.stateful,
            decisionVersion = config?.codeEnvironmentDecisionVersion,
            inheritanceVersion = config?.codeWorkspaceInheritanceVersion,
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
        if (!resolution.required || conversationId == null) return resolution
        val unknown = resolution.copy(visible = false, submission = CodeWorkspaceSubmission.NONE, canSubmit = true)
        return when (savedState) {
            is Saved.Known -> resolution
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
            saved.value = conversationId to when (result) {
                is Result.Success -> {
                    // Once recorded, the decision is the server's; the draft has done its job.
                    if (result.data.holdsDecision) draft.update { it.copy(mode = null, selections = null) }
                    Saved.Known(result.data)
                }
                else -> Saved.Unreadable
            }
        }
    }

    // ── Status polling ───────────────────────────────────────────────────────

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

    // ── Lifecycle and actions ────────────────────────────────────────────────

    fun onForeground(inFront: Boolean) {
        foreground.value = inFront
        pollTarget = null
        retarget(latest)
    }

    /** A new chat on this screen: the previous chat's draft, picks and status say nothing about it. */
    fun reset() {
        draft.value = Draft(chatKey = null)
        saved.value = null
        browsing.value = null
        sheetOpen.value = false
        attention.value = false
    }

    /** Refetches every machine's status now (the sheet's Refresh, the locked status chip). */
    fun refresh() {
        // A saved decision that could not be read is read again too (otherwise only when a run
        // ends), so a chat opened offline recovers from Refresh.
        if (saved.value?.second == Saved.Unreadable) saved.value = null
        goneMachines.clear()
        lastFetched.clear()
        pollTarget = null
        retarget(latest)
    }

    fun openSheet(machineId: String? = null) {
        browsing.value = machineId?.takeUnless { id -> latest.environments.any { it.environment.id == id } }
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

    /** What a send carries now; null while the row holds the send. */
    fun submissionForSend(): CodeWorkspaceSubmission? = latest.submission

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
