package com.garfiec.librechat.feature.chat.viewmodel.delegate

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.common.ToolConstants
import com.garfiec.librechat.core.common.network.ConnectivityObserver
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.LastUsedSelection
import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.repository.AgentRepository
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.McpRepository
import com.garfiec.librechat.core.data.util.PermissionGate
import com.garfiec.librechat.core.logging.Diag
import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.config.ModelSpecs
import com.garfiec.librechat.core.model.mcp.McpServer
import com.garfiec.librechat.core.model.permissions.Permission
import com.garfiec.librechat.core.model.permissions.PermissionType
import com.garfiec.librechat.core.ui.components.ModelParameters
import com.garfiec.librechat.feature.chat.model.McpServerDisplayData
import com.garfiec.librechat.feature.chat.util.SpecSeed
import com.garfiec.librechat.feature.chat.util.StartChatPick
import com.garfiec.librechat.feature.chat.util.demotedForLastUsed
import com.garfiec.librechat.feature.chat.util.offeredOrUnchecked
import com.garfiec.librechat.feature.chat.util.reopenedPick
import com.garfiec.librechat.feature.chat.util.resolveDefaultModelSpec
import com.garfiec.librechat.feature.chat.util.resolvedAgainst
import com.garfiec.librechat.feature.chat.util.showsRawEndpoint
import com.garfiec.librechat.feature.chat.util.toChoice
import com.garfiec.librechat.feature.chat.util.toSeed
import com.garfiec.librechat.feature.chat.util.withoutStale
import com.garfiec.librechat.feature.chat.viewmodel.ModelChoice
import com.garfiec.librechat.feature.chat.viewmodel.ModelSelectionHandle
import com.garfiec.librechat.feature.chat.viewmodel.ModelSelectionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Failure banner published by [ModelSelectionDelegate.loadAgents]; cleared by a later success. */
private const val AGENTS_LOAD_ERROR = "Could not load available agents"

/**
 * How long an attach will wait for the selected agent's provider before routing without it.
 * Attaching must stay responsive on a dead network; overshooting the wait only costs the file
 * the routing this feature adds, which is what happens today anyway.
 */
private const val AGENT_PROVIDER_RESOLVE_TIMEOUT_MS = 3_000L

class ModelSelectionDelegate(
    private val handle: ModelSelectionHandle,
    private val configRepository: ConfigRepository,
    private val agentRepository: AgentRepository,
    private val mcpRepository: McpRepository,
    private val settingsDataStore: SettingsDataStore,
    private val permissionGate: PermissionGate,
    private val connectivityObserver: ConnectivityObserver,
    /** The "Start new chats on: My last-used model" setting; see [demotedForLastUsed]. */
    private val preferLastUsedModel: Flow<Boolean>,
    initialAgentId: String? = null,
    initialEndpoint: String? = null,
    initialModel: String? = null,
    /** The spec a model shortcut was recorded from; see [pendingModelOverride]. */
    initialSpec: String? = null,
) {

    /**
     * Tier-0 explicit agent selection passed from the start-chat navigation (e.g.
     * "Start Chat" on an agent detail/card, or post-create). One-shot: applied on the
     * first [applySeed] pass and cleared, so it wins over last-used/first-agent/first-
     * model exactly once and then normal seeding governs. Authoritative — set before
     * any validation, and seeding only runs on a NEW chat (so the existing-conversation
     * clobber-guard in [refilterModels] never sees it).
     */
    private var pendingAgentOverride: String? = initialAgentId?.takeIf { it.isNotBlank() }

    /**
     * Tier-0 explicit (endpoint, model) passed from the start-chat navigation — set when a chat is
     * launched from a home-screen model shortcut / quick action, with the spec it was recorded from.
     * Same one-shot, authoritative semantics as [pendingAgentOverride] (they're mutually exclusive:
     * a route carries an agent id OR a concrete model). Not checked against the model list,
     * mirroring the agent override — a shortcut points at a recently-used model, so it's applied
     * even if the config hasn't loaded — but resolved against the spec config ([resolvedAgainst]).
     */
    private var pendingModelOverride: StartChatPick? =
        if (!initialEndpoint.isNullOrBlank() && !initialModel.isNullOrBlank()) {
            StartChatPick(initialEndpoint, initialModel, initialSpec?.takeIf { it.isNotBlank() }, shortcut = true)
        } else {
            null
        }

    /**
     * Cached last-used selection from DataStore, kept in sync by [seedInitialSelection]. Used by
     * [refilterModels]' existing-conversation fallback, which re-checks its spec. Null until the
     * first DataStore emission.
     */
    var cachedLastUsed: LastUsedSelection? = null

    /**
     * True once a conversation-model LOAD has been attempted (success, 404, or new-chat
     * short-circuit). Used to prevent [refilterModels] from overwriting the conversation
     * model with a fallback before loadConversationModel has had a chance to set it.
     *
     * NOTE: "attempted", not "succeeded" — a 404 (the created-before-save race) still flips
     * this. To tell "we actually have the conversation's real model" use
     * [conversationModelResolved].
     */
    var conversationModelLoaded = false

    /**
     * True once the conversation's authoritative (endpoint, model) has actually been applied
     * — either from the in-process new-chat handoff or from a successful conversation read.
     * Distinct from [conversationModelLoaded], which only records that a load was attempted
     * (and is set even on a 404). [ChatViewModel.handleFinal] re-derives the selection from
     * the Final event only while this is still false, so a racy 404 can't strand a fallback
     * guess on screen.
     */
    var conversationModelResolved = false

    /**
     * A Tier-0 pair applied before the spec config loaded, so nothing could be inferred for it yet
     * (see [applyPendingOverride]). Inferred once the config arrives, unless the user picked since.
     */
    private var tier0AwaitingSpecs: StartChatPick? = null

    /**
     * Applies an authoritative conversation (endpoint, model) as the active selection and
     * marks the conversation model both loaded and resolved. The single write point for a
     * resolved existing/just-created conversation's selection — used by the new-chat handoff
     * and by [ChatViewModel]'s conversation reads — so the flags can't drift from the value.
     */
    fun applyResolvedConversationModel(endpoint: String, model: String?, spec: String? = null) {
        val config = configRepository.startupConfig.value
        val choice = spec.offeredOrUnchecked(endpoint, model, config?.modelSpecs, checkable = config != null)
        applySelection(choice, reason = "conversationResolved")
        conversationModelLoaded = true
        conversationModelResolved = true
    }

    /**
     * Resolves a loaded [conversation]'s authoritative (endpoint, model) and applies it as
     * the active selection via [applyResolvedConversationModel]. Agents conversations carry
     * the agent in `agentId`, so prefer that over `model` for the AGENTS endpoint. Returns
     * true when a concrete selection was applied (i.e. the conversation model is now
     * resolved), false when the conversation lacked enough info.
     */
    fun applyConversationModel(conversation: Conversation): Boolean {
        val endpoint = conversation.endpoint
        val isAgentConversation = endpoint == EndpointConstants.AGENTS
        val resolvedModel = if (isAgentConversation) {
            conversation.agentId ?: conversation.model
        } else {
            conversation.model
        }
        if (endpoint != null && resolvedModel != null) {
            applyResolvedConversationModel(endpoint, resolvedModel, conversation.spec)
            return true
        }
        return false
    }

    /**
     * True once [loadAgents] has finished on ANY path (success, error, or
     * permission-denied). [seedInitialSelection] uses this to tell "agents not
     * loaded yet" (wait) from "agents loaded and genuinely empty" (fall through).
     *
     * Modeled as a flow (not a plain `var`) so it is an arm of the seeder's
     * `combine`: the error / permission-denied paths flip it without publishing a
     * new agent list, and a bare var would leave those transitions invisible to
     * the seeder — it would stay parked on the agents tier forever (no model on
     * the landing). `internal` for the delegate's unit tests.
     */
    internal val agentsLoaded = MutableStateFlow(false)

    /**
     * True when the last [loadAgents] attempt ended in a network error (not a
     * permission denial or a genuinely-empty account). Gates [retryAgentsIfFailed]
     * so a transient cold-start failure — e.g. the token still settling right after
     * login — doesn't strand the "My Agents" group empty until the app is recreated,
     * without needlessly re-fetching for accounts that simply have no agents.
     * Only touched on the ViewModel's main dispatcher, so a plain var suffices.
     */
    private var agentsLoadFailed = false

    /**
     * The in-flight [loadAgents] attempt. Guards [retryAgentsIfFailed] against
     * launching a duplicate concurrent fetch while one is still running.
     */
    private var agentsLoadJob: Job? = null

    /**
     * The connectivity observer that retries the agent fetch when the device comes
     * back online after a failed load. Started from [loadAgents]'s error branch and
     * cancelled on success; self-cancels once a reconnect retry fires. See
     * [scheduleAgentsRetryOnReconnect].
     */
    private var connectivityRetryJob: Job? = null

    /**
     * The last-used selection this delegate last applied as the active selection. Lets
     * [seedInitialSelection] re-apply a *changed* last-used even over an already-valid selection
     * (the retained-landing resync) without re-applying an unchanged value on every unrelated
     * emission.
     */
    private var lastAppliedLastUsed: ModelChoice? = null

    /** The "Start new chats on" setting as of the last seed; null before the first. */
    private var appliedPreferLastUsed: Boolean? = null

    /**
     * True between a Tier-0 explicit-agent application and the moment the populated
     * agents list arrives. Distinguishes the explicitly-chosen-agent case — an agent that
     * must survive the transient `(agentsLoaded=true, agents=[])` window the agents flow
     * emits before the real list (see [loadAgents]) — from a genuinely zero-agent account
     * whose stale agents last-used must NOT strand the landing model-less. Only when this
     * flag is set does an empty-but-loaded agents list HOLD the current selection; otherwise
     * an empty agents list falls through to a config model. Cleared once the agent is
     * confirmed present in a non-empty list.
     */
    private var holdAgentForPopulate = false

    /** Whether this delegate seeds a new-chat landing; set by [seedInitialSelection]. */
    private var seedsNewChat = false

    /**
     * True once something other than the seeder chose this landing's selection: a pick in the
     * model selector, a loaded preset, or a Tier-0 start-chat override. Such a choice displaces
     * the server's prioritized model spec and isn't replaced by last-used while it loads. Cleared
     * by [rearmModelSpecDefault] when the landing hands its chat off, so the next new chat opens
     * on the spec again.
     */
    private var specYielded = false

    /**
     * The landing's selection as the seeder last left it. Any other value at the next emission was
     * written by someone else, which sets [specYielded]; detecting it here covers every writer
     * (picks, presets, the composer swap) without each having to report itself.
     */
    private var seederChoice: ModelChoice? = null

    /**
     * Set by [rearmModelSpecDefault]: the selection the handoff carried over belongs to the chat
     * that just started, so the seeder picks again rather than keeping it because it is still
     * valid. Cleared once the seeder or anyone else writes the selection.
     */
    private var reseedCarried = false

    /**
     * Whether the last successful [loadAgents] returned any agents. Set before [agentsLoaded]
     * flips, so a seeder emission that sees `(agentsLoaded=true, agents=[])` can tell the
     * transient window before the list is published (true) from an account with no agents, a
     * denial or a failed load (false). Only a spec agent consults it; the Tier-0 override keeps
     * its own [holdAgentForPopulate].
     */
    private var agentsFetchedNonEmpty = false

    /**
     * What the single-agent read told us, keyed by agent id — including agents whose fetch came
     * back with no provider or no starters, so switching back and forth doesn't re-hit the network
     * for an answer we already have. Confined to the handle's Main-dispatched scope, like
     * [cachedLastUsed].
     */
    private val agentViews = mutableMapOf<String, AgentViewFacts>()

    /** The in-flight provider fetch, cancelled when the selection moves on before it lands. */
    private var agentProviderJob: Job? = null

    /**
     * Keeps [ModelSelectionState.selectedAgentProvider] and
     * [ModelSelectionState.selectedAgentStarters] in step with the selected agent.
     *
     * Both are only reachable through the single-agent read — the list projection omits them — so
     * they have to be fetched once per agent rather than derived from state. Continuous, because
     * the selection changes long after this is started: seeding, a conversation load, and the
     * model selector all move it.
     */
    fun observeSelectedAgentProvider() {
        handle.scope.launch {
            handle.stateFlow
                .map { state ->
                    state.selectedModel?.takeIf { state.selectedEndpoint == EndpointConstants.AGENTS }
                }
                .distinctUntilChanged()
                .collect { agentId -> resolveAgentProvider(agentId) }
        }
        // An agent edited in the agent editor keeps its old provider otherwise: the memo is keyed
        // by id, and the id doesn't change when the agent does. `drop(1)` skips the seed value.
        handle.scope.launch {
            agentRepository.revision.drop(1).collect {
                agentViews.clear()
                resolveAgentProvider(currentAgentId())
            }
        }
    }

    /** The selected agent's id, or null when the selection isn't an agent at all. */
    private fun currentAgentId(): String? =
        handle.state.selectedModel?.takeIf { handle.state.selectedEndpoint == EndpointConstants.AGENTS }

    private fun resolveAgentProvider(agentId: String?) {
        agentProviderJob?.cancel()
        if (agentId == null) {
            publishAgentView(provider = null, starters = null)
            return
        }
        agentViews[agentId]?.let {
            publishAgentView(it.provider, it.conversationStarters)
            return
        }
        // Clear first: the previous agent's provider must not linger over the new selection while
        // the fetch runs, or an attachment routes against the wrong provider for a network RTT.
        publishAgentView(provider = null, starters = null)
        agentProviderJob = handle.scope.launch { fetchAgentView(agentId) }
    }

    /**
     * Resolves the selected agent's provider, waiting for the fetch if it hasn't happened or
     * hasn't landed yet. Returns null when there is nothing to resolve or the fetch fails — which
     * callers must read as "unknown", i.e. route exactly as mobile did before this existed.
     *
     * Attachment routing calls this at the decision point rather than trusting whatever the
     * background observer left in state. Three ordinary situations leave that value null: the
     * fetch is still in flight (attaching within an RTT of picking the agent), it failed once (the
     * observer is driven by `distinctUntilChanged` on the agent id, so a still-selected agent
     * never re-triggers it), or the screen opened straight onto an agent and the user attached
     * immediately. In all three the file would silently take the provider path — the exact silent
     * drop this feature exists to stop.
     */
    suspend fun awaitSelectedAgentProvider(): String? {
        // Bounded rather than open-ended, and bounding the WHOLE resolution rather than the fetch
        // alone: an unreachable server, or an account still seeding its selection, must cost the
        // attach a moment — not hang the picker, and not two timeouts back to back. A timeout
        // lands on the pre-feature behaviour, not on a wrong route.
        val provider = withTimeoutOrNull(AGENT_PROVIDER_RESOLVE_TIMEOUT_MS) {
            val agentId = awaitCurrentAgentId() ?: return@withTimeoutOrNull null
            fetchAgentView(agentId)?.provider
        }
        if (provider == null) {
            Logger.d { "awaitSelectedAgentProvider: unresolved; routing as unknown" }
        }
        return provider
    }

    /**
     * The selected agent's id, waiting for the selection to seed if it hasn't yet.
     *
     * `agents` is the *default* endpoint — the value state holds before anything has chosen it —
     * so on cold start the pair (endpoint=agents, model=null) means "not seeded yet", not "no
     * agent". A share that launched the app is drained before seeding runs, and reading the id
     * straight would answer null there and route every shared document as unknown: the silent drop
     * this feature exists to stop, reached by the one entry point that cannot re-pick the file.
     *
     * Returns null without waiting on any other endpoint, and stops waiting if the selection moves
     * off the agents endpoint — which is what an account with no agents at all does.
     */
    private suspend fun awaitCurrentAgentId(): String? {
        currentAgentId()?.let { return it }
        if (handle.state.selectedEndpoint != EndpointConstants.AGENTS) return null
        handle.stateFlow.first {
            it.selectedModel != null || it.selectedEndpoint != EndpointConstants.AGENTS
        }
        return currentAgentId()
    }

    /**
     * Serves the memo or fetches, publishing to state on success. A failure is deliberately NOT
     * memoized so the next attach retries it, but it publishes no starters rather than leaving
     * them unresolved, so the landing falls back to the spec's.
     */
    private suspend fun fetchAgentView(agentId: String): AgentViewFacts? {
        agentViews[agentId]?.let { return it }
        return when (val result = agentRepository.getAgentForViewing(agentId)) {
            is Result.Success -> {
                val facts = AgentViewFacts(result.data.provider, result.data.conversationStarters)
                agentViews[agentId] = facts
                // The selection may have moved while the fetch was in flight.
                if (currentAgentId() == agentId) publishAgentView(facts.provider, facts.conversationStarters)
                facts
            }

            is Result.Error -> {
                Logger.d(result.exception) { "Failed to resolve agent provider" }
                if (currentAgentId() == agentId) publishAgentView(provider = null, starters = emptyList())
                null
            }

            is Result.Loading -> null
        }
    }

    private fun publishAgentView(provider: String?, starters: List<String>?) {
        val current = handle.state.selection
        if (current.selectedAgentProvider == provider && current.selectedAgentStarters == starters) return
        handle.update {
            selection = selection.copy(selectedAgentProvider = provider, selectedAgentStarters = starters)
        }
    }

    /** The single-agent read's answers this delegate keeps: the two the list projection omits. */
    private data class AgentViewFacts(val provider: String?, val conversationStarters: List<String>)

    /**
     * Re-filters availableModels into UI state and, for EXISTING conversations,
     * corrects a selection that the latest models/configs invalidated.
     *
     * New-chat seeding is owned entirely by [seedInitialSelection] — this method
     * deliberately does not seed/fallback for new chats, so the two never race.
     */
    fun refilterModels(isNewConversation: Boolean) {
        val filtered = filterModelsByEndpoint(
            configRepository.availableModels.value,
            configRepository.endpointConfigs.value,
        )
        handle.update { selection = selection.copy(availableModels = filtered) }

        // Don't validate before the server's config or models have loaded. When they arrive this
        // method will be called again. Configs alone are enough: an agents-only server lists no
        // models, and a conversation there on a vanished endpoint must still fall back.
        val endpointConfigs = configRepository.endpointConfigs.value
        if (filtered.isEmpty() && endpointConfigs.isEmpty()) return

        // New chats: the seeder is the single authority for the selection.
        // It re-resolves on every input change (availableModels/endpointConfigs
        // are arms of its combine), so there is nothing to correct here.
        if (isNewConversation) return

        // For existing conversations, don't apply fallbacks until the
        // conversation model has been loaded.
        if (!conversationModelLoaded) return

        // Validate current selection. An agents selection is not checked against the agents list:
        // it is one capped page (`/api/agents` defaults to 100), so an agent missing from it may
        // still exist, and replacing it would reopen the chat on a different agent.
        //
        // Every endpoint, agents included, keeps the conversation's model while the endpoint itself exists, as
        // the web client does (`useNavigateToConvo` rebuilds a conversation only when its endpoint
        // is missing from the config). The model list is not a whitelist: a fetched list can leave
        // out a model the chat ran on (a dated id like `grok-4-0709`), and replacing it would open the
        // chat on an unrelated model. Only a vanished endpoint falls back.
        val currentEndpoint = handle.state.selectedEndpoint
        val currentModel = handle.state.selectedModel
        val endpointGone = currentEndpoint != null && endpointConfigs.isNotEmpty() && currentEndpoint !in endpointConfigs
        val selectionValid = currentModel != null && !endpointGone

        if (selectionValid) return

        // --- Corrective fallback (existing conversations only) ---
        // The loaded conversation's endpoint vanished server-side. Prefer the
        // last-used model (an agents last-used must be in the loaded agents list —
        // a stale lastUsedModel that isn't an agent_id must not be restored as
        // the agents-endpoint selection), then fall back to the first model.
        val stored = cachedLastUsed
        val storedEndpoint = stored?.endpoint
        val storedModel = stored?.model
        if (storedEndpoint != null && storedModel != null) {
            // Checked on the pair it reopens on: a last spec follows the spec to its current pair.
            val lastUsed = handle.state.selection.modelSpecs.reopenedPick(storedEndpoint, storedModel, stored.spec)
            val lastEndpoint = lastUsed.endpoint
            val lastModel = lastUsed.model
            val lastUsedRestorable = if (lastEndpoint == EndpointConstants.AGENTS) {
                // Agents never appear in `filtered`, so validate the agent id against the
                // authoritative agents list instead (mirroring selectability()). Hold — don't
                // fall through to a config-model guess — until that list has loaded; loadAgents
                // re-runs refilterModels on success to un-stick this.
                when {
                    !agentsLoaded.value -> return
                    handle.state.agents.any { it.id == lastModel } -> true
                    else -> false
                }
            } else {
                val lastModelsForEndpoint = filtered[lastEndpoint]
                lastModelsForEndpoint != null && lastModel in lastModelsForEndpoint
            }
            if (lastUsedRestorable) {
                Diag.w(
                    tag = "ModelSel",
                    attrs = mapOf(
                        "branch" to "lastUsed",
                        "from" to (currentModel ?: "null"),
                        "endpoint" to lastEndpoint,
                    ),
                ) { "refilterModels corrective fallback → last-used" }
                applySelection(lastUsed, reason = "refilter-lastUsed")
                return
            }
        }

        val firstEndpoint = filtered.entries.firstOrNull()
        if (firstEndpoint != null) {
            Diag.w(
                tag = "ModelSel",
                attrs = mapOf(
                    "branch" to "firstModel",
                    "from" to (currentModel ?: "null"),
                    "endpoint" to firstEndpoint.key,
                ),
            ) { "refilterModels corrective fallback → first model" }
            applySelection(
                ModelChoice.Raw(firstEndpoint.key, firstEndpoint.value.firstOrNull()),
                reason = "refilter-firstModel",
            )
        }
    }

    /**
     * Single authority for a NEW chat's initial model selection.
     *
     * Continuous (not one-shot): the NewChat landing ViewModel is retained in the
     * back stack, so when last-used changes (e.g. the user picks a different model
     * inside a conversation) this re-syncs the blank landing to it. Gated on
     * `conversationId == null` so a started conversation's model is never overridden.
     *
     * Deterministic: every input (last-used, config models, endpoint configs, agent
     * list) is an arm of one `combine`, so each change re-resolves the precedence
     * atomically — replacing the three coroutines that used to race to seed the
     * selection. Precedence, with WAIT-on-unresolved so a lower tier never wins
     * while a higher-precedence input is still loading:
     *   0. an explicit agent or model from the start-chat navigation (one-shot)
     *   0b. the server's prioritized model spec (`modelSpecs.prioritize`, or `interface.modelSelect`
     *       off), or the spec the user last picked, until something on this landing displaces it —
     *       see [applyModelSpecDefault]. With "Start new chats on: My last-used model" it drops to
     *       1b instead, unless the server leaves no choice ([demotedForLastUsed])
     *   1. last-used (endpoint + model; a last spec on its current pair while the server still
     *      lists it, see [reopenedPick]), if present and valid
     *   1b. the server's soft-default model spec
     *   2. first agent, when the active endpoint is agents
     *   3. first available config model
     */
    fun seedInitialSelection(isNewConversation: Boolean) {
        seedsNewChat = isNewConversation
        seederChoice = handle.state.selection.choice
        handle.scope.launch {
            combine(
                settingsDataStore.lastUsedSelection,
                configRepository.availableModels,
                configRepository.endpointConfigs,
                agentsInput(),
                combine(configRepository.startupConfig, preferLastUsedModel) { config, preferLastUsed ->
                    SpecConfig(config?.modelSpecs, config?.interfaceConfig?.modelSelect ?: true, preferLastUsed)
                }.distinctUntilChanged(),
            ) { lastUsed, rawModels, endpointCfgs, agents, specConfig ->
                SeedInputs(
                    lastUsed = lastUsed,
                    rawModels = rawModels,
                    endpointConfigs = endpointCfgs,
                    agents = agents.agents,
                    agentsLoaded = agents.loaded,
                    conversationId = agents.conversationId,
                    serverSpecSeed = resolveDefaultModelSpec(specConfig.modelSpecs, specConfig.modelSelect, lastUsed.spec),
                    modelSpecs = specConfig.modelSpecs,
                    modelSelect = specConfig.modelSelect,
                    preferLastUsed = specConfig.preferLastUsed,
                )
            }.collect { input ->
                val onLanding = isNewConversation && input.conversationId == null
                if (onLanding && handle.state.selection.choice != seederChoice) {
                    specYielded = true
                    reseedCarried = false
                }
                var tier0SpecsArrived = false
                if (handle.state.selection.modelSpecs != input.modelSpecs) {
                    val awaiting = tier0AwaitingSpecs?.takeIf { input.modelSpecs != null }
                    if (awaiting != null) {
                        tier0AwaitingSpecs = null
                        tier0SpecsArrived = true
                    }
                    val current = handle.state.selection.choice.withoutStale(input.modelSpecs)
                    // Still the raw pair the entry point applied before the config: resolve it now.
                    val resolved = awaiting
                        ?.takeIf { current == ModelChoice.Raw(it.endpoint, it.model) }
                        ?.let { pick ->
                            pick.resolvedAgainst(input.modelSpecs, input.modelSelect) ?: current.also {
                                // Ruled out by the config: back through the tiers on this emission,
                                // as if the entry point had not fired.
                                specYielded = false
                                reseedCarried = true
                            }
                        }
                    // One emission: the new specs never sit next to a spec they no longer offer.
                    handle.update {
                        selection = selection.copy(modelSpecs = input.modelSpecs, choice = resolved ?: current)
                    }
                }
                // Keep the cached last-used fresh for refilterModels' existing-
                // conversation corrective fallback. This runs for every conversation
                // (new and existing) — the old eager one-shot read in ChatViewModel
                // populated the cache for both, and dropping it would leave that
                // fallback reading nulls for existing conversations.
                cachedLastUsed = input.lastUsed
                // Seeding applies only to the blank new-chat landing. Never override
                // an existing/started conversation's model (loadConversationModel
                // owns those).
                if (!onLanding) return@collect
                applySeed(input, repinLastUsed = tier0SpecsArrived)
                seederChoice = handle.state.selection.choice
            }
        }
    }

    /**
     * The agents arm of the seeder's [combine]: the (agents, conversationId) pair
     * from state plus the [agentsLoaded] flag, so a load-completion with an
     * unchanged (empty) agent list — error or permission-denied — still re-triggers
     * the seeder and lets it fall through past the agents tier.
     */
    private fun agentsInput(): Flow<AgentsInput> = combine(
        handle.stateFlow.map { it.agents to it.conversationId }.distinctUntilChanged(),
        agentsLoaded,
    ) { agentsAndConvId, loaded ->
        AgentsInput(
            agents = agentsAndConvId.first,
            conversationId = agentsAndConvId.second,
            loaded = loaded,
        )
    }

    /**
     * Tier-0 seeding: applies an explicit agent or concrete (endpoint, model) passed from the
     * start-chat navigation — an agent detail/card, or a home-screen model shortcut / quick action.
     * Authoritative and one-shot: cleared after applying so later seeder emissions don't re-pin it,
     * and trusted directly (no validation) since it was just chosen. Returns true when an override
     * was applied so [applySeed] can stop.
     *
     * CRITICAL: also pins [lastAppliedLastUsed] to the CURRENT last-used so the re-sync in [applySeed]
     * (`lastUsed != lastAppliedLastUsed`) does NOT fire on the next emission (e.g. when the agents
     * list finishes loading) and clobber the override back to a last-used value. A genuinely NEW
     * last-used picked later still differs from this pinned value, so the re-sync still follows it.
     *
     * Also yields the prioritized model spec ([specYielded]); otherwise the next emission would
     * apply the spec over the agent or model the user just chose.
     */
    private fun applyPendingOverride(lastUsed: ModelChoice?, modelSpecs: ModelSpecs?, modelSelect: Boolean): Boolean {
        // A cold launch can get here before the config: resolve the pick once it arrives instead.
        fun resolved(pick: StartChatPick): ModelChoice? {
            if (modelSpecs != null) return pick.resolvedAgainst(modelSpecs, modelSelect)
            tier0AwaitingSpecs = pick
            return ModelChoice.Raw(pick.endpoint, pick.model)
        }
        pendingAgentOverride?.let { agentId ->
            pendingAgentOverride = null
            val choice = resolved(StartChatPick(EndpointConstants.AGENTS, agentId, spec = null, shortcut = false))
                ?: return false
            specYielded = true
            lastAppliedLastUsed = lastUsed
            // Hold this explicit agent across the transient empty-agents window until the populated
            // list arrives (see [holdAgentForPopulate]); without it the next (agentsLoaded=true,
            // agents=[]) emission would score the agent INVALID/PENDING and clobber it to a config model.
            // Unconfirmed gap: the populated list is getAgents()'s first 100, so an agent beyond them
            // (started from the agent page) may never be confirmed and may fall back to another model.
            holdAgentForPopulate = true
            applySelection(choice, reason = "tier0-agentOverride")
            return true
        }
        pendingModelOverride?.let { pick ->
            pendingModelOverride = null
            // A shortcut the picker could not offer opens nothing: the landing seeds as usual.
            val choice = resolved(pick) ?: return false
            specYielded = true
            lastAppliedLastUsed = lastUsed
            applySelection(choice, reason = "tier0-modelOverride")
            return true
        }
        return false
    }

    // debt — CyclomaticComplexMethod: complexity 34
    // debt — ReturnCount: 12 returns
    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    private fun applySeed(input: SeedInputs, repinLastUsed: Boolean = false) {
        val filtered = filterModelsByEndpoint(input.rawModels, input.endpointConfigs)
        val state = handle.state

        val lastEndpoint = input.lastUsed.endpoint
        val lastModel = input.lastUsed.model
        // A last spec reopens on its current pair, as the resolver reopens it. A raw pair on an
        // endpoint `addedEndpoints` no longer lists can't be picked again, so it never seeds and
        // never outranks the server's spec. With `interface.modelSelect` off the spec already wins
        // (Tier 0b); where none applies, web falls back to the last setup, so last-used still does here.
        val lastUsed = if (lastEndpoint != null && lastModel != null) {
            input.modelSpecs.reopenedPick(lastEndpoint, lastModel, input.lastUsed.spec)
                .takeIf { it is ModelChoice.Spec || input.modelSpecs.showsRawEndpoint(lastEndpoint, modelSelect = true) }
        } else {
            null
        }
        // A Tier-0 pick made before the spec config loaded pinned last-used with no spec to check
        // its stored one against. Re-pin it: the spec now resolving is not a new pick, and the
        // re-sync below would otherwise replace the shortcut with last-used.
        if (repinLastUsed) lastAppliedLastUsed = lastUsed
        val specSeed = input.serverSpecSeed.demotedForLastUsed(
            preferLastUsed = input.preferLastUsed,
            modelSelect = input.modelSelect,
            lastUsed = lastUsed,
            specs = input.modelSpecs,
        )
        // Switching the setting on while this landing sits on the spec should hand it to last-used,
        // which the re-sync below does only for a last-used it hasn't applied yet. Only when the
        // setting just demoted the spec and nothing on this landing displaced it: a pick or a
        // start-chat override must survive the switch. Switching off needs nothing, Tier 0b re-applies.
        if (input.preferLastUsed != appliedPreferLastUsed) {
            val demotedNow = input.serverSpecSeed?.prioritized == true && specSeed?.prioritized == false
            if (appliedPreferLastUsed != null && demotedNow && !specYielded) lastAppliedLastUsed = null
            appliedPreferLastUsed = input.preferLastUsed
        }

        // Tier 0: an explicit agent or concrete model passed from the start-chat navigation wins
        // over everything (last-used / first-agent / first-model). One-shot — see [applyPendingOverride].
        if (applyPendingOverride(lastUsed, input.modelSpecs, input.modelSelect)) return

        // Tier 0b: the server's prioritized model spec, ahead of last-used.
        if (applyModelSpecDefault(specSeed, input, filtered, lastUsed)) return

        val lastUsedSelectability = lastUsed?.let {
            selectability(it.endpoint, it.model, filtered, input)
        }
        fun applyLastUsed(): Boolean {
            if (lastUsed != null && lastUsedSelectability == Selectability.VALID) {
                applySelection(lastUsed, reason = "tier1-lastUsed")
                lastAppliedLastUsed = lastUsed
                return true
            }
            return false
        }

        // Re-sync: when last-used CHANGES to a new valid value, re-apply it even
        // over a currently-valid selection, so the retained landing follows a model
        // picked elsewhere. Also covers the very first valid last-used emission.
        if (lastUsed != lastAppliedLastUsed && applyLastUsed()) return

        // Clear the explicit-agent hold once the populated agents list confirms the held agent: the
        // transient empty-agents window is over and the selection is now genuinely VALID,
        // so a later last-used change should be free to re-sync normally.
        if (holdAgentForPopulate &&
            input.agentsLoaded &&
            input.agents.any { it.id == state.selectedModel }
        ) {
            holdAgentForPopulate = false
        }

        val currentSelectability = selectability(state.selectedEndpoint, state.selectedModel, filtered, input)
        when (currentSelectability) {
            // Already usable — leave it (the re-sync above handled last-used changes), unless it is
            // the previous chat's selection, carried over by the handoff.
            Selectability.VALID -> if (!reseedCarried) return
            // The input needed to judge the current selection hasn't arrived yet.
            Selectability.PENDING -> {
                // An explicitly-chosen agent (Tier-0) in the transient empty-agents
                // window. An empty list can neither confirm nor refute the agent id, so HOLD
                // it and wait for the populated list (which re-triggers this seeder) instead
                // of downgrading to last-used / a config model. Gated on [holdAgentForPopulate]
                // so a genuinely zero-agent account (no explicit agent intent) does NOT hold —
                // it falls through below to a config model rather than stranding the landing.
                if (holdAgentForPopulate &&
                    state.selectedEndpoint == EndpointConstants.AGENTS &&
                    input.agentsLoaded &&
                    input.agents.isEmpty()
                ) {
                    return
                }
                // A choice the seeder didn't make waits for its input rather than giving way to
                // last-used, unless it is an agent on an account the fetch found no agents for.
                val agentsNoneFound = state.selectedEndpoint == EndpointConstants.AGENTS &&
                    input.agentsLoaded &&
                    input.agents.isEmpty() &&
                    !agentsFetchedNonEmpty
                if (specYielded && !agentsNoneFound) return
                // Otherwise: don't reseed through the lower tiers (that would clobber a
                // legitimately-loading choice), but DO let a known-good last-used take over
                // rather than waiting on an input that may never resolve. If last-used can't
                // apply (also empty/pending), fall through to the tier ladder so the landing
                // still resolves to a model rather than sitting empty forever.
                if (applyLastUsed()) return
                if (state.selectedEndpoint != EndpointConstants.AGENTS) return
            }
            // Definitively unusable — fall through to tier resolution.
            Selectability.INVALID -> {}
        }

        // Tier 1: last-used.
        if (lastUsed != null) {
            when (lastUsedSelectability) {
                Selectability.VALID -> {
                    applyLastUsed()
                    return
                }
                // WAIT: last-used's endpoint/agents not loaded yet — don't fall
                // through to a lower tier and lose to it. EXCEPTION: an agents last-used
                // whose list is loaded-but-EMPTY is PENDING (the empty-list hold rule), but
                // here there is no explicit agent to hold (Tier-0 didn't fire / hold cleared),
                // so it's a genuinely zero-agent account — fall through to Tier-2/3 rather
                // than waiting forever and stranding the landing model-less.
                Selectability.PENDING -> {
                    val agentsLoadedEmpty = lastUsed.endpoint == EndpointConstants.AGENTS &&
                        input.agentsLoaded &&
                        input.agents.isEmpty()
                    if (!agentsLoadedEmpty) return
                }
                // Stale (removed server-side) or absent — fall through.
                Selectability.INVALID, null -> {}
            }
        }

        // Tier 1b: the server's soft-default model spec, which yields to a usable last-used.
        if (applySoftModelSpec(specSeed, input, filtered)) return

        // Tier 2: first agent, when the active endpoint is agents.
        if (state.selectedEndpoint == EndpointConstants.AGENTS) {
            if (!input.agentsLoaded) return // WAIT for the agent list
            val firstAgent = input.agents.firstOrNull()
            if (firstAgent != null) {
                applySelection(ModelChoice.Raw(EndpointConstants.AGENTS, firstAgent.id), reason = "tier2-firstAgent")
                return
            }
            // Agents loaded and genuinely empty — fall through.
        }

        // Tier 3: first available config model.
        if (filtered.isEmpty()) return // WAIT for models
        val firstEntry = filtered.entries.firstOrNull() ?: return
        applySelection(ModelChoice.Raw(firstEntry.key, firstEntry.value.firstOrNull()), reason = "tier3-firstModel")
    }

    /**
     * Tier 0b: opens the landing on the server's prioritized model spec, as web opens every new
     * chat on it under `modelSpecs.prioritize` (or with `interface.modelSelect` off). Returns true
     * when it applied, so [applySeed] stops.
     *
     * Also the spec the user last picked, which upstream reopens on whatever `prioritize` says.
     *
     * Standing rather than one-shot: it beats last-used on every emission until [specYielded], so a
     * raw model picked inside some other conversation (which rewrites last-used) doesn't move the
     * landing off the spec. A raw pick therefore lasts for the chat it was made in; a spec pick
     * carries over (web parity; see feature/chat/CLAUDE.md). The "Start new chats on: My last-used
     * model" setting demotes the spec to Tier 1b before it gets here ([demotedForLastUsed]).
     *
     * Never persists last-used, and pins [lastAppliedLastUsed] so yielding later doesn't fire a
     * spurious re-sync. A spec agent missing from the loaded list (deleted, not shared, or an
     * account with no agents at all) falls through to the normal tiers, and so does a null config:
     * a failed fetch with nothing cached must not strand the landing. The spec takes over once the
     * config arrives.
     */
    private fun applyModelSpecDefault(
        specSeed: SpecSeed?,
        input: SeedInputs,
        filtered: Map<String, List<String>>,
        lastUsed: ModelChoice?,
    ): Boolean {
        val seed = specSeed?.takeIf { it.prioritized } ?: return false
        if (specYielded) return false
        if (specSelectability(seed, filtered, input) == Selectability.INVALID) return false
        lastAppliedLastUsed = lastUsed
        applySelection(seed.toChoice(), reason = "tier0b-prioritizedSpec")
        return true
    }

    /**
     * Tier 1b: a soft-default model spec, reached only when last-used can't apply. Returns true
     * when [applySeed] should stop — the spec applied, or it is waiting on the agent list.
     */
    private fun applySoftModelSpec(specSeed: SpecSeed?, input: SeedInputs, filtered: Map<String, List<String>>): Boolean {
        val seed = specSeed?.takeUnless { it.prioritized } ?: return false
        return when (specSelectability(seed, filtered, input)) {
            Selectability.VALID -> {
                applySelection(seed.toChoice(), reason = "tier1b-softSpec")
                true
            }
            Selectability.PENDING -> true
            Selectability.INVALID -> false
        }
    }

    /**
     * [selectability] for a spec's selection, except that an empty-but-loaded agent list decides
     * the agent rather than holding it: PENDING only while a fetch that found agents has yet to
     * publish them, INVALID when there are none to find.
     */
    private fun specSelectability(
        seed: SpecSeed,
        filtered: Map<String, List<String>>,
        input: SeedInputs,
    ): Selectability {
        if (seed.endpoint == EndpointConstants.AGENTS && input.agentsLoaded && input.agents.isEmpty()) {
            return if (agentsFetchedNonEmpty) Selectability.PENDING else Selectability.INVALID
        }
        return selectability(seed.endpoint, seed.model, filtered, input)
    }

    /** The one writer of [ModelSelectionState.choice] here, bar the seeder's spec-config update. */
    private fun applySelection(choice: ModelChoice, reason: String) {
        val previous = handle.state
        val changed = previous.selectedEndpoint != choice.endpoint || previous.selectedModel != choice.model
        reseedCarried = false
        handle.update { selection = selection.copy(choice = choice) }
        if (changed) {
            Diag.d(
                tag = "ModelSel",
                attrs = mapOf(
                    "reason" to reason,
                    "endpoint" to choice.endpoint,
                    "model" to (choice.model ?: "null"),
                ),
            ) { "applySelection" }
        }
    }

    /**
     * Three-state validity for a candidate (endpoint, model):
     * - VALID: usable right now
     * - PENDING: the input needed to judge it hasn't loaded yet (agents list, or
     *   this endpoint's model list) — callers should WAIT, not fall through
     * - INVALID: definitively not usable (null, or absent from a loaded list)
     *
     * Agents are validated against the authoritative fetched [agents] list, not
     * `availableModels` (agents are fetched separately and may not appear there).
     *
     * **This is also what makes upstream's soft-default re-arm bug (SYNC-30) unreachable here,
     * verified rather than ported.** Upstream had a stored agent selection that no longer resolved
     * keep suppressing the `softDefault` model spec, stranding New Chat on the agents endpoint
     * with nothing selected. An agent absent from a LOADED list is INVALID here, not sticky, so
     * `applySeed` falls straight through to Tier 2/3 and re-arms. The sibling bug — a `?spec=`
     * cold load rendering a chimera of spec chip and agent entity — needs a persisted selection
     * that is a bare SPEC NAME carrying no endpoint or model. This client persists a spec name
     * only alongside its `lastUsedEndpoint` / `lastUsedModel` pair, and restores it only as the
     * spec's own current pair (or that raw pair once the spec is gone), so there is no
     * bare-spec-name selection.
     *
     * Do not "port" either fix on a later sync without first re-checking those two premises.
     */
    private fun selectability(
        endpoint: String?,
        model: String?,
        filtered: Map<String, List<String>>,
        input: SeedInputs,
    ): Selectability {
        if (endpoint == null || model == null) return Selectability.INVALID
        val agents = input.agents
        if (endpoint == EndpointConstants.AGENTS) {
            return when {
                !input.agentsLoaded -> Selectability.PENDING
                // An empty-but-loaded agents list is the transient pre-populate window
                // (loadAgents flips agentsLoaded=true before publishing the list): it can
                // neither confirm nor refute a specific agent id, so PENDING (hold), not
                // INVALID. The caller distinguishes "hold an explicit agent" from "no agents
                // exist" via holdAgentForPopulate.
                agents.isEmpty() -> Selectability.PENDING
                agents.any { it.id == model } -> Selectability.VALID
                else -> Selectability.INVALID
            }
        }
        val modelsForEndpoint = filtered[endpoint]
        return when {
            modelsForEndpoint != null && model in modelsForEndpoint -> Selectability.VALID
            modelsForEndpoint != null -> Selectability.INVALID // models loaded, model gone
            // modelsForEndpoint == null: distinguish "this endpoint's models haven't
            // loaded yet" (PENDING) from an endpoint that has none (INVALID). The model list
            // arrives whole, so once it has, an endpoint missing from it (or listing no models,
            // e.g. a revoked key) has nothing to offer. Once configs have loaded, an endpoint
            // absent from them was removed server-side. Don't WAIT on either forever.
            input.rawModels.isNotEmpty() -> Selectability.INVALID
            input.endpointConfigs.isNotEmpty() && endpoint !in input.endpointConfigs -> Selectability.INVALID
            else -> Selectability.PENDING
        }
    }

    private enum class Selectability { VALID, PENDING, INVALID }

    // Transport-only snapshots: no copy, destructuring or structural equality is used.
    // Avoid generating equality/hash code over the full config and agent graphs on Native.
    private class SeedInputs(
        val lastUsed: LastUsedSelection,
        val rawModels: Map<String, List<String>>,
        val endpointConfigs: Map<String, EndpointConfig>,
        val agents: List<Agent>,
        val agentsLoaded: Boolean,
        val conversationId: String?,
        /** The server's answer, before [demotedForLastUsed]; the tiers read the demoted one. */
        val serverSpecSeed: SpecSeed?,
        val modelSpecs: ModelSpecs?,
        val modelSelect: Boolean,
        val preferLastUsed: Boolean,
    )

    // Data classes, unlike the snapshots above: `distinctUntilChanged` and the last-used re-sync
    // compare these by value. Neither holds the agent graph; SpecConfig holds only the spec list.
    private data class SpecConfig(val modelSpecs: ModelSpecs?, val modelSelect: Boolean, val preferLastUsed: Boolean)

    private class AgentsInput(
        val agents: List<Agent>,
        val conversationId: String?,
        val loaded: Boolean,
    )

    /** A spec pick. Its name is stored with the pair, so new chats can reopen on it. */
    fun onSpecSelected(spec: ModelSpec) {
        val seed = spec.toSeed() ?: return
        onChoiceSelected(seed.toChoice())
    }

    /** A user pick: a raw model or agent, or a spec staged elsewhere (Compare setup's lane A). Also saved as last-used. */
    fun onChoiceSelected(choice: ModelChoice) {
        val model = choice.model ?: return
        tier0AwaitingSpecs = null
        applySelection(choice, reason = if (choice is ModelChoice.Spec) "explicitSpec" else "explicitModel")
        // Keep the cache in sync so refilterModels uses the latest choice
        cachedLastUsed = LastUsedSelection(choice.endpoint, model, choice.spec)
        handle.scope.launch {
            settingsDataStore.setLastUsedModel(choice.endpoint, model, choice.spec)
        }
    }

    /**
     * Re-arms the server's prioritized model spec after the landing hands its chat off, so the
     * next new chat opens on the spec again whatever was picked for the last one. The handoff's
     * state reset flips `conversationId` back to null, which re-runs the seeder.
     *
     * The selection the handoff carries over is the started chat's, so it is adopted as the
     * seeder's own ([seederChoice]) and seeded over ([reseedCarried]), and [lastAppliedLastUsed]
     * is unpinned: a start-chat override or a pick lasts for its one chat, and the next new chat
     * goes back through the tiers.
     */
    fun rearmModelSpecDefault() {
        specYielded = false
        lastAppliedLastUsed = null
        seederChoice = handle.state.selection.choice
        reseedCarried = true
    }

    /**
     * Fetches the agent list into state. Auto-selecting the first agent for a new
     * chat is NOT done here — [seedInitialSelection] owns selection. This sets
     * [agentsLoaded] on every exit path so the seeder can distinguish "agents
     * still loading" (wait) from "agents loaded and empty" (fall through).
     */
    fun loadAgents(isNewConversation: Boolean) {
        agentsLoadFailed = false
        agentsLoadJob = handle.scope.launch {
            // Skip the fetch entirely when the role denies AGENTS.USE; otherwise
            // the server would return 403 and we'd have to decide whether it's a
            // genuine 403 (rate limit, tenancy) vs. permission denial.
            if (permissionGate.awaitRole()?.hasAccess(PermissionType.AGENTS, Permission.USE) == false) {
                agentsLoaded.value = true
                handle.update { selection = selection.copy(agentsLoaded = true) }
                // Un-stick the corrective-fallback hold (see below) even on this path.
                refilterModels(isNewConversation)
                return@launch
            }
            when (val result = agentRepository.getAgents()) {
                is Result.Success -> {
                    // A selector-open recovery (or a plain success) makes any pending
                    // reconnect observer moot — cancel it so it can't fire a duplicate
                    // retry after we already have the list.
                    connectivityRetryJob?.cancel()
                    connectivityRetryJob = null
                    // Set agentsLoaded BEFORE publishing the agent list so the
                    // seeder emission carrying the agents already sees the flag —
                    // otherwise tier 2 could fall through to a config model in the
                    // one-emission window before the flag flips.
                    agentsFetchedNonEmpty = result.data.isNotEmpty()
                    agentsLoaded.value = true
                    handle.update {
                        selection = selection.copy(agents = result.data, agentsLoaded = true)
                        // A successful retry must not leave the failure banner from
                        // the attempt it just recovered next to the populated list.
                        // Clear only our own message — the error slot is shared
                        // with other delegates.
                        error = error.takeUnless { it == AGENTS_LOAD_ERROR }
                    }
                }
                is Result.Error -> {
                    Logger.e(result.exception) { "Failed to load agents" }
                    // Mark the attempt as failed so retryAgentsIfFailed can re-fetch
                    // when the user next opens the selector. The AccountKeyedCache only
                    // stores successes, so the retry genuinely re-hits the network.
                    agentsLoadFailed = true
                    handle.setError(AGENTS_LOAD_ERROR)
                    // The seeder's flag only: a failed fetch says nothing about which agents exist,
                    // so the picker keeps every agent spec (web shows all while it has no map).
                    agentsLoaded.value = true
                    scheduleAgentsRetryOnReconnect(isNewConversation)
                }
                is Result.Loading -> return@launch
            }
            // The existing-conversation corrective fallback in [refilterModels] HOLDS
            // (returns early) for an agents last-used while the agents list is unloaded.
            // Re-run on EVERY terminal path that flips [agentsLoaded] — success, error,
            // AND denial — so the hold is always released; an error/denied load (empty
            // list) correctly falls through to a config model instead of staying stuck.
            refilterModels(isNewConversation)
        }
    }

    /**
     * Re-attempts the agent fetch when the initial [loadAgents] ended in a network
     * error, so a transient cold-start failure doesn't leave the "My Agents" group
     * permanently empty. Wired to the model-selector open so the list refreshes on the
     * user's next glance. No-op when a load is already in flight, the last load
     * succeeded, or the account was cleanly resolved to zero agents.
     */
    fun retryAgentsIfFailed(isNewConversation: Boolean) {
        if (agentsLoadFailed && agentsLoadJob?.isActive != true) {
            loadAgents(isNewConversation)
        }
    }

    /**
     * Observes connectivity after a failed load and re-fetches agents on the first
     * offline→online transition, so a cold-start failure (network/DNS not up yet)
     * recovers on its own instead of waiting for the user to open the model selector.
     *
     * Transition-detection (not "retry whenever connected") is deliberate: it fires
     * once per reconnect rather than looping while online-but-server-unreachable.
     * [wasConnected] starts null so the observer's initial current-state emission is
     * never mistaken for a transition. The observer self-cancels after firing; if the
     * retry fails again it re-enters [loadAgents]'s error branch and re-schedules a
     * fresh one.
     *
     * Known limit: the Android observer keys on NET_CAPABILITY_INTERNET, so a
     * "connected but DNS broken" failure produces no offline→online transition — the
     * selector-open [retryAgentsIfFailed] still covers that case.
     */
    private fun scheduleAgentsRetryOnReconnect(isNewConversation: Boolean) {
        connectivityRetryJob?.cancel()
        connectivityRetryJob = handle.scope.launch {
            var wasConnected: Boolean? = null
            connectivityObserver.isConnected.collect { connected ->
                val recovered = wasConnected == false && connected
                wasConnected = connected
                if (recovered) {
                    retryAgentsIfFailed(isNewConversation)
                    connectivityRetryJob?.cancel()
                    connectivityRetryJob = null
                }
            }
        }
    }

    fun loadMcpServers() {
        handle.scope.launch {
            when (val serversResult = mcpRepository.listServers()) {
                is Result.Success -> {
                    val servers = serversResult.data
                    // Enrich servers with connection status
                    val statusResult = mcpRepository.getConnectionStatus()
                    val statusMap = (statusResult as? Result.Success)?.data ?: emptyMap()
                    val enriched = servers.map { server ->
                        val status = statusMap[server.name]
                        server.copy(isConnected = status?.isConnected ?: false)
                    }
                    handle.update {
                        selection = selection.copy(mcpServers = enriched.map { it.toDisplayData() })
                    }
                }
                is Result.Error -> {
                    Logger.d(serversResult.exception) { "Failed to load MCP servers: ${serversResult.message}" }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun toggleMcpServer(serverName: String) {
        val current = handle.state.selectedMcpServerNames
        val updated = if (serverName in current) current - serverName else current + serverName
        handle.update { selection = selection.copy(selectedMcpServerNames = updated) }
        handle.scope.launch { settingsDataStore.setSelectedMcpServers(updated) }
    }

    fun toggleTool(toolName: String) {
        if (toolName == ToolConstants.WEB_SEARCH) {
            // Web search is backed by modelParameters.webSearch (single source of truth).
            val current = handle.state.modelParameters.webSearch
            handle.update {
                selection = selection.copy(modelParameters = selection.modelParameters.copy(webSearch = !current))
            }
        } else if (toolName == ToolConstants.URL_CONTEXT) {
            // URL context (Google-only) is backed by modelParameters.urlContext, like web search.
            val current = handle.state.modelParameters.urlContext
            handle.update {
                selection = selection.copy(modelParameters = selection.modelParameters.copy(urlContext = !current))
            }
        } else if (toolName == ToolConstants.CODE_INTERPRETER && !handle.state.isCodeInterpreterAvailable) {
            // Code interpreter is not available on this server; ignore toggle attempt.
            return
        } else {
            val current = handle.state.enabledTools
            val updated = if (toolName in current) current - toolName else current + toolName
            handle.update { selection = selection.copy(enabledTools = updated) }
            handle.scope.launch { settingsDataStore.setEnabledTools(updated) }
        }
    }

    fun updateModelParameters(parameters: ModelParameters) {
        handle.update { selection = selection.copy(modelParameters = parameters) }
    }
}

/**
 * Filters availableModels to the endpoints the user's server has enabled, dropping
 * endpoints with empty model lists. Single source of truth for "which models are
 * usable" — shared by [ModelSelectionDelegate] (seeding/validation) and the model
 * selector UI so they can't drift apart.
 *
 * Never yields an `agents` entry or a model listed twice: agents come from the agents list, and
 * either would put two rows under one key in the selector's LazyColumn (#541). The repository
 * already normalizes; this keeps every consumer safe from a map that did not come through it.
 */
internal fun filterModelsByEndpoint(
    rawModels: Map<String, List<String>>,
    endpointConfigs: Map<String, EndpointConfig>,
): Map<String, List<String>> =
    rawModels.filter { (endpoint, models) ->
        endpoint != EndpointConstants.AGENTS &&
            models.isNotEmpty() &&
            (endpointConfigs.isEmpty() || endpoint in endpointConfigs)
    }.mapValues { (_, models) -> models.distinct() }

// --- Display data mapping extensions ---

internal fun McpServer.toDisplayData() = McpServerDisplayData(
    name = name,
    title = title,
    description = description,
    isConnected = isConnected,
)
