package com.garfiec.librechat.feature.agents.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.ToolConstants
import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.AgentRepository
import com.garfiec.librechat.core.data.repository.AgentToolsRepository
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.FileRepository
import com.garfiec.librechat.core.data.repository.McpRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.data.repository.SkillsRepository
import com.garfiec.librechat.core.data.repository.ToolFavoritesRepository
import com.garfiec.librechat.core.model.ActionMetadata
import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.AgentCategory
import com.garfiec.librechat.core.model.AgentFile
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.EndpointToolApproval
import com.garfiec.librechat.core.model.HandoffEdge
import com.garfiec.librechat.core.model.SkillSummary
import com.garfiec.librechat.core.model.code.StatefulCodeSessionsConfig
import com.garfiec.librechat.core.model.config.paramSettingsKey
import com.garfiec.librechat.core.model.mcp.McpTool
import com.garfiec.librechat.core.model.request.FunctionTool
import com.garfiec.librechat.core.ui.components.PdfPasswordPromptUi
import com.garfiec.librechat.feature.agents.AgentActionDisplayData
import com.garfiec.librechat.feature.agents.AgentHandoffDisplayData
import com.garfiec.librechat.feature.agents.AgentToolDisplayData
import com.garfiec.librechat.feature.agents.components.ModelOption
import com.garfiec.librechat.feature.agents.components.model.AgentAdvancedSettings
import com.garfiec.librechat.feature.agents.components.model.AgentCapabilities
import com.garfiec.librechat.feature.agents.components.model.AgentSharingState
import com.garfiec.librechat.feature.agents.components.model.AgentVersion
import com.garfiec.librechat.feature.agents.components.model.AgentVersionBasis
import com.garfiec.librechat.feature.agents.components.model.MarketplaceItem
import com.garfiec.librechat.feature.agents.components.model.SupportContactState
import com.garfiec.librechat.feature.agents.util.ContentReader
import com.garfiec.librechat.feature.agents.util.ToolApprovalMode
import com.garfiec.librechat.feature.agents.util.codeBackgroundEnabled
import com.garfiec.librechat.feature.agents.util.withApprovalMode
import com.garfiec.librechat.feature.agents.util.withCodeBackground
import com.garfiec.librechat.feature.agents.viewmodel.delegate.AgentActionsDelegate
import com.garfiec.librechat.feature.agents.viewmodel.delegate.AgentCapabilitiesDelegate
import com.garfiec.librechat.feature.agents.viewmodel.delegate.AgentCodeEvent
import com.garfiec.librechat.feature.agents.viewmodel.delegate.AgentCodeSettingsDelegate
import com.garfiec.librechat.feature.agents.viewmodel.delegate.AgentCodeSources
import com.garfiec.librechat.feature.agents.viewmodel.delegate.AgentFilesDelegate
import com.garfiec.librechat.feature.agents.viewmodel.delegate.AgentLoaderDelegate
import com.garfiec.librechat.feature.agents.viewmodel.delegate.AgentSaveDelegate
import com.garfiec.librechat.feature.agents.viewmodel.delegate.CodeToolAuthDelegate
import com.garfiec.librechat.feature.agents.viewmodel.delegate.MarketplaceFilter
import com.garfiec.librechat.feature.agents.viewmodel.delegate.ToolsMarketplaceDelegate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Per-capability file slot. The wire value is the `tool_resource` form field
 * the backend reads on `POST /api/files` and `DELETE /api/files` — it routes
 * the file into `tool_resources.<wire>.file_ids` on the agent.
 */
enum class AgentFileSlot(val wire: String) {
    CODE("execute_code"),
    KNOWLEDGE("file_search"),
    CONTEXT("context"),
}

/** A file removal the user has been asked to confirm. [filename] is for the prompt only. */
@Immutable
data class PendingAgentFileRemoval(
    val fileId: String,
    val slot: AgentFileSlot,
    val filename: String?,
)

@Immutable
data class AgentEditorUiState(
    val isEditMode: Boolean = false,
    val agentId: String? = null,
    val name: String = "",
    val description: String = "",
    val instructions: String = "",
    val model: String = "",
    val provider: String = "",
    val category: String = "general",
    val selectedTools: List<String> = emptyList(),
    val conversationStarters: List<String> = emptyList(),
    val availableTools: List<AgentToolDisplayData> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val nameError: String? = null,
    val descriptionError: String? = null,
    val supportContactNameError: String? = null,
    val supportContactEmailError: String? = null,
    // Advanced editor fields
    val avatarUrl: String? = null,
    val categories: List<AgentCategory> = emptyList(),
    val availableModels: List<ModelOption> = emptyList(),
    val capabilities: AgentCapabilities = AgentCapabilities(),
    val advancedSettings: AgentAdvancedSettings = AgentAdvancedSettings(),
    val versions: List<AgentVersion> = emptyList(),
    /**
     * The loaded agent's own values, used to decide which revision is the active one.
     *
     * Captured at load rather than read from this state when the sheet opens: v0.8.8 fetches
     * history lazily, by which point the form fields may have been edited.
     */
    val versionBasis: AgentVersionBasis = AgentVersionBasis(),
    /** True while the lazy `/versions` fetch is in flight (v0.8.8 servers only). */
    val isLoadingVersions: Boolean = false,
    /**
     * The agent the form was last filled from — on load and on revert — and so what Save is
     * measured against for unsaved changes. Null when creating.
     */
    val loadedAgent: Agent? = null,
    val showDeleteConfirm: Boolean = false,
    val showDuplicateConfirm: Boolean = false,
    val showDiscardConfirm: Boolean = false,
    val showVersionHistory: Boolean = false,
    // Unified tools marketplace (v0.8.8) — the one picker over capabilities, tools, MCP, skills.
    val showToolsMarketplace: Boolean = false,
    val marketplaceQuery: String = "",
    val marketplaceFilter: MarketplaceFilter = MarketplaceFilter.ALL,
    /** Pinned marketplace items as `itemType:itemId` keys. Empty on pre-0.8.8 servers. */
    val favoriteToolKeys: Set<String> = emptySet(),
    /** Whether this server has the tool-favorites routes; hides the star column when false. */
    val areToolFavoritesSupported: Boolean = false,
    val isDeleting: Boolean = false,
    val isDuplicating: Boolean = false,
    // Actions
    val actions: List<AgentActionDisplayData> = emptyList(),
    // MCP tools
    val mcpTools: List<McpTool> = emptyList(),
    val selectedMcpTools: Set<String> = emptySet(),
    // Capabilities toggles
    val codeInterpreterEnabled: Boolean = false,
    /** The Code section's draft (stateful sessions, machines, workspace, Git identity). */
    val code: AgentCodeSettings = AgentCodeSettings(),
    /** `agents.capabilities` has `stateful_code_sessions`: the Code section shows. */
    val isStatefulCodeAvailable: Boolean = false,
    /** `agents.capabilities` has `run_in_background` (opt-in, no fail-open): see [codeBackground]. */
    val isCodeBackgroundAvailable: Boolean = false,
    /** `agents.statefulCodeSessions` from `/api/endpoints`: machines, choice limits, scopes. */
    val statefulCodeConfig: StatefulCodeSessionsConfig? = null,
    /** The status read for the default-workspace picker's machine. */
    val codeMachineStatus: AgentCodeMachineStatus? = null,
    /** Save found a half-filled or malformed Git identity. */
    val codeGitIdentityError: Boolean = false,
    val fileSearchEnabled: Boolean = false,
    val webSearchEnabled: Boolean = false,
    val fileContextEnabled: Boolean = false,
    /** Whether code interpreter is available on this server (from agents endpoint capabilities). */
    val isCodeInterpreterAvailable: Boolean = true,
    /**
     * Whether generic plugin tools are offered — the agents endpoint's `tools` capability,
     * fail-open on an empty capabilities list like the sibling gates. Mirrors web's
     * `buildCatalog`, which only walks `regularTools` under `enabled.has('tools')`.
     */
    val isGenericToolsAvailable: Boolean = true,
    /**
     * Whether `ask_user_question` may be offered — its OWN capability (like execute_code), not
     * the generic `tools` one, fail-open on empty. The catalog additionally requires
     * `/api/agents/tools` to list the plugin, which is what hides it on pre-feature servers.
     */
    val isAskUserQuestionAvailable: Boolean = true,
    /** Whether web search is configured on this server (startupConfig.webSearch != null). */
    val isWebSearchAvailable: Boolean = false,
    /** The agents endpoint's `file_search` capability, fail-open on an empty list. */
    val isFileSearchAvailable: Boolean = true,
    /**
     * The role's RUN_CODE / WEB_SEARCH / FILE_SEARCH `USE` (fail-open on an unknown role). They
     * hide a builtin's row and section ([isCodeInterpreterOffered] and siblings), never its save.
     */
    val canUseRunCode: Boolean = true,
    val canUseWebSearch: Boolean = true,
    val canUseFileSearch: Boolean = true,
    /** Whether chain (sequential multi-agent) is enabled in the agents endpoint capabilities. */
    val isChainAvailable: Boolean = false,
    /** Whether the handoffs graph feature is supported (v0.8.5+). */
    val isHandoffsAvailable: Boolean = false,
    /** Whether the granular ACL sharing API is supported (v0.8.5+). */
    val isAclAvailable: Boolean = false,
    /**
     * Whether `DELETE /api/files` unlinks an agent's file before it deletes anything (v0.8.8-rc1+).
     * Below that the route deletes an owned file outright, so removing a chip would destroy the
     * user's file everywhere. False until the version is known, so an unplaced server refuses.
     */
    val isAgentFileUnlinkAvailable: Boolean = false,
    /** The server is v0.8.9-rc1+, the line agent tool approval modes landed on. */
    val isToolApprovalVersionSupported: Boolean = false,
    /** The agents entry's `toolApproval` policy from `/api/endpoints`; null on older servers. */
    val toolApprovalPolicy: EndpointToolApproval? = null,
    /** The MCP tool id whose remembered approvals are being reset, so its menu item can't fire twice. */
    val resettingApprovalToolId: String? = null,
    /** How the last per-tool reset ended; shown once as a snackbar. */
    val approvalResetNotice: ApprovalResetNotice? = null,
    /**
     * Whether removing a file from this agent can destroy it (v0.8.8-rc4, landed 2026-09-17).
     * From rc4 the route runs the full delete — bytes and embeddings — for a file the caller owns
     * that no other agent references, and the client cannot tell ahead of time which files those
     * are, so a removal is confirmed first. Earlier rc servers only unlink. False unless the
     * server is known to carry the change, so older servers keep the one-tap removal.
     */
    val agentFileRemovalDeletes: Boolean = false,
    /** Whether thinkingDisplay `updates` may be offered (v0.8.8+); see AgentCapabilitiesDelegate. */
    val isThinkingDisplayUpdatesAvailable: Boolean = false,
    /** A removal waiting on the user's confirmation; see [agentFileRemovalDeletes]. */
    val pendingFileRemoval: PendingAgentFileRemoval? = null,
    /** `/api/config.endpointsDropParamsMap` (v0.8.8-rc3), unresolved; hides advanced-parameter
     *  controls this server strips before calling the provider. */
    val dropParamsMap: Map<String, JsonElement>? = null,
    /** `/api/endpoints`, for [parameterProvider]. */
    val endpointConfigs: Map<String, EndpointConfig> = emptyMap(),
    /**
     * Whether a save keeps `model_parameters` to the inference parameters the server accepts for
     * the agent's provider (v0.8.9-rc1+, which 400s on any other key). False until the server is
     * known to carry the check, so an older server keeps every key it stored. See VERSION_GATES.md.
     */
    val filtersInferenceParameters: Boolean = false,
    /**
     * The loaded agent's instructions come from a linked prompt group (v0.8.9-rc1+). The field is
     * then read-only here: mobile cannot change the link, and editing the text would change
     * nothing the agent runs.
     */
    val instructionsLinkedToPrompt: Boolean = false,
    /** Each endpoint's `customParams` as the agent inference filter reads it, keyed by endpoint name. */
    val inferenceEndpoints: Map<String, AgentInferenceEndpoint> = emptyMap(),
    // Skills (v0.8.6) — agent-editor skills selector.
    /** Whether the agent `skills_enabled` master toggle is on. */
    val skillsEnabled: Boolean = false,
    /** Skill `_id`s on this agent. Empty + enabled = "full catalog" (allowlist
     *  off), not "no skills" — do NOT auto-clear [skillsEnabled] when empty. */
    val selectedSkillIds: List<String> = emptyList(),
    /** Skill catalog from `GET /api/skills`, used to resolve `_id → name` for
     *  chips and to populate the picker. May be empty if the list is denied
     *  (no SKILLS access) — saved ids then render as raw id chips. */
    val availableSkills: List<SkillSummary> = emptyList(),
    /**
     * Whether the Skills section is shown. Gated on the agents endpoint
     * `capabilities` containing "skills" AND the SKILLS permission, both
     * fail-open to match the sibling capability gates (empty caps / unknown
     * role ⇒ shown).
     */
    val isSkillsAvailable: Boolean = false,
    // Subagents config (v0.8.6) — agent-editor subagent section.
    /**
     * Whether the Subagents section is shown. Gated on the agents endpoint
     * `capabilities` containing "subagents" (capability only — no permission
     * type, unlike skills), fail-open like the sibling capability gates.
     */
    val isSubagentsAvailable: Boolean = false,
    /** Master `subagents.enabled` toggle. */
    val subagentsEnabled: Boolean = false,
    /** `subagents.allowSelf` — agent may spawn itself in an isolated context.
     *  Defaults true (upstream `allowSelf !== false`). */
    val subagentAllowSelf: Boolean = true,
    /** `subagents.agent_ids` — other agents that may be spawned (cap 10, self excluded). */
    val selectedSubagentIds: List<String> = emptyList(),
    /**
     * `subagents.shareFiles` and `subagents.graphs`, carried from the loaded agent untouched. No
     * control edits them, but the server `$set`s `subagents` whole, so a save that omitted them
     * would erase file sharing and any team configured on the web.
     */
    val subagentShareFiles: Boolean? = null,
    val subagentGraphs: JsonElement? = null,
    // Sharing
    val sharingState: AgentSharingState = AgentSharingState(),
    /**
     * Whether to show the Collaborative toggle in [AgentSharingSection]. Upstream
     * v0.8.5 removed `isCollaborative` + `projectIds` from the agent model in favor
     * of ACL permissions, so the toggle is hidden on v0.8.5+. See VERSION_GATES.md.
     */
    val showCollaborativeToggle: Boolean = true,
    // Chain (sequential multi-agent) — saved as agent_ids
    val chainAgentIds: List<String> = emptyList(),
    // Handoffs (graph edges) — saved as edges; v0.8.5+ only
    val handoffEdges: List<HandoffEdge> = emptyList(),
    /** Raw edge payloads the handoff editor does not own: ones that failed to decode into
     *  [HandoffEdge] on load (e.g. upstream added a field the mobile model doesn't know about), and
     *  edges that are not handoffs at all (direct edges, see `isHandoffEdge`). Re-emitted as-is on
     *  save so we don't clobber server-side edges with an empty list. */
    val unparsedHandoffEdges: List<JsonElement> = emptyList(),
    val allAgents: List<AgentHandoffDisplayData> = emptyList(),
    // Support contact
    val supportContact: SupportContactState = SupportContactState(),
    // Tool auth (Code Interpreter key entry)
    val codeToolAuthState: ToolAuthState = ToolAuthState.Unknown,
    val showCodeAuthDialog: Boolean = false,
    // Per-capability file attachments
    val codeFiles: List<AgentFile> = emptyList(),
    val knowledgeFiles: List<AgentFile> = emptyList(),
    val contextFiles: List<AgentFile> = emptyList(),
    /** File-id set currently uploading, keyed for spinner state in chips. */
    val uploadingSlots: Set<AgentFileSlot> = emptySet(),
    /** A picked PDF needs its password before it can be uploaded; the screen asks for it. */
    val pdfPasswordPrompt: PdfPasswordPromptUi? = null,
    /** Per-tool options (`{ <tool>_mcp_<server>: { defer_loading, approval_mode, … }, … }`), keyed
     *  by the full tool id. Round-tripped on every save so values set via the web client survive a
     *  mobile edit; the save path prunes it to the agent's current tools (`pruneToolOptions`).
     *  Mobile edits only `approval_mode` / `approval_revision` (the mode picker). */
    val toolOptions: JsonObject? = null,
    /** Agent runtime `additional_instructions`. See [Agent.additionalInstructions]
     *  for the wire-level caveat: round-trip is a no-op against the current
     *  upstream Zod schema and is plumbed only for forward compatibility. */
    val additionalInstructions: String? = null,
    /** Agent runtime `tool_kwargs`. See [Agent.toolKwargs] for shape + the
     *  wire-level caveat that the field is stripped server-side today. */
    val toolKwargs: JsonElement? = null,
) {
    /**
     * A save, duplicate or delete is in flight. Each navigates when it lands, so back is held until
     * then: leaving first would let the late result pop or replace the screen the user went back to.
     */
    val isCommitting: Boolean get() = isSaving || isDuplicating || isDeleting

    /**
     * The key of [provider]'s parameter set ([paramSettingsKey]), as web's agent model panel resolves
     * it: an agent on a custom endpoint gets the parameter set of its `type` (OpenAI's), or its
     * `customParams.defaultParamsEndpoint`, not the generic one its name falls through to.
     */
    val parameterProvider: String get() = paramSettingsKey(provider, endpointConfigs)

    /**
     * The per-tool approval mode picker and its reset (v0.8.9-rc1). `agentModes` is already ANDed
     * with `enabled` by the server, and is off unless the admin opted in. See VERSION_GATES.md.
     */
    val isToolApprovalModesAvailable: Boolean
        get() = isToolApprovalVersionSupported && toolApprovalPolicy?.agentModes == true

    /**
     * Whether the editor shows a builtin: the server's capability AND the role's grant, as web's
     * catalog lists it. A hidden builtin the agent already has is still saved: the save reads
     * [isCodeInterpreterAvailable] / [isWebSearchAvailable], never these.
     */
    val isCodeInterpreterOffered: Boolean get() = isCodeInterpreterAvailable && canUseRunCode
    val isWebSearchOffered: Boolean get() = isWebSearchAvailable && canUseWebSearch
    val isFileSearchOffered: Boolean get() = isFileSearchAvailable && canUseFileSearch

    /**
     * The code interpreter's "Background execution" switch (web `Code/Background.tsx`): whether its
     * commands may run in the background, or null when the switch isn't shown (the server lacks
     * `run_in_background`, or the code interpreter is off).
     */
    val codeBackground: Boolean?
        get() = toolOptions.codeBackgroundEnabled()
            .takeIf { isCodeBackgroundAvailable && codeInterpreterEnabled && isCodeInterpreterOffered }
}

/** How a reset of remembered tool approvals ended. Worded by the screen. */
enum class ApprovalResetNotice { SUCCESS, FORBIDDEN, FAILED }

/**
 * Per-tool authentication state derived from `GET /agents/tools/:id/auth`.
 * [Unknown] is the pre-fetch default; the toggle stays disabled until we
 * learn whether the user has a key (or the server has one configured).
 */
sealed interface ToolAuthState {
    data object Unknown : ToolAuthState

    /** Server has a key configured for this user (`message=system_defined`). */
    data object SystemDefined : ToolAuthState

    /** User has installed their own key (`message=user_provided`, authenticated). */
    data object UserProvided : ToolAuthState

    /** Tool requires a user key but none is installed. */
    data object Unauthenticated : ToolAuthState
}

sealed interface AgentEditorEvent {
    data class SaveSuccess(val agentId: String) : AgentEditorEvent
    data class DuplicateSuccess(val agentId: String) : AgentEditorEvent
    data object DeleteSuccess : AgentEditorEvent
    data object Exit : AgentEditorEvent
}

@Suppress("TooManyFunctions") // debt: 63 functions
class AgentEditorViewModel(
    private val agentRepository: AgentRepository,
    private val configRepository: ConfigRepository,
    private val mcpRepository: McpRepository,
    private val agentToolsRepository: AgentToolsRepository,
    private val fileRepository: FileRepository,
    private val skillsRepository: SkillsRepository,
    private val roleRepository: RoleRepository,
    private val toolFavoritesRepository: ToolFavoritesRepository,
    codeSources: AgentCodeSources,
    private val contentReader: ContentReader,
    private val ioDispatcher: CoroutineDispatcher,
    initialAgentId: String? = null,
) : ViewModel() {

    private val editAgentId: String? = initialAgentId

    private val _uiState = MutableStateFlow(
        AgentEditorUiState(
            isEditMode = editAgentId != null,
            agentId = editAgentId,
        ),
    )
    val uiState: StateFlow<AgentEditorUiState> = _uiState.asStateFlow()

    /**
     * Drives the system-back interception, so a clean form keeps the predictive-back preview.
     * Off the main thread because it re-maps the loaded agent on every keystroke.
     */
    val hasUnsavedChanges: StateFlow<Boolean> = _uiState
        .map { it.hasUnsavedChanges() }
        .flowOn(ioDispatcher)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** The MCP rows of "Tools & Actions", approval mode resolved; see [selectedMcpToolRows]. */
    val mcpToolRows: StateFlow<List<McpToolRowDisplay>> = _uiState
        .map { it.selectedMcpToolRows() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _events = MutableSharedFlow<AgentEditorEvent>()
    val events: SharedFlow<AgentEditorEvent> = _events.asSharedFlow()

    private val stateHandle = AgentEditorStateHandle(_uiState, viewModelScope)

    private val filesDelegate = AgentFilesDelegate(
        stateHandle = stateHandle,
        agentRepository = agentRepository,
        fileRepository = fileRepository,
        contentReader = contentReader,
        ioDispatcher = ioDispatcher,
    )

    private val loaderDelegate = AgentLoaderDelegate(
        stateHandle = stateHandle,
        agentRepository = agentRepository,
        configRepository = configRepository,
        mcpRepository = mcpRepository,
        filesDelegate = filesDelegate,
        editAgentId = editAgentId,
    )

    private val codeSettingsDelegate = AgentCodeSettingsDelegate(stateHandle, codeSources)

    /** The Code section, mapped from the form and config; null hides it. */
    val codeSection: StateFlow<AgentCodeSectionView?> = _uiState
        .map { state ->
            agentCodeSection(
                settings = state.code,
                config = state.statefulCodeConfig,
                available = state.isStatefulCodeAvailable,
                codeEnabled = state.codeInterpreterEnabled && state.isCodeInterpreterAvailable,
                status = state.codeMachineStatus,
                gitIdentityError = state.codeGitIdentityError,
            )
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun onCodeSettingsEvent(event: AgentCodeEvent) = codeSettingsDelegate.onEvent(event)

    private val codeAuthDelegate = CodeToolAuthDelegate(
        stateHandle = stateHandle,
        agentToolsRepository = agentToolsRepository,
    )

    private val capabilitiesDelegate = AgentCapabilitiesDelegate(
        stateHandle = stateHandle,
        configRepository = configRepository,
        roleRepository = roleRepository,
        skillsRepository = skillsRepository,
    )

    private val actionsDelegate = AgentActionsDelegate(
        stateHandle = stateHandle,
        agentRepository = agentRepository,
        editAgentId = editAgentId,
    )

    private val saveDelegate = AgentSaveDelegate(
        stateHandle = stateHandle,
        agentRepository = agentRepository,
        filesDelegate = filesDelegate,
        events = _events,
    )

    private val marketplaceDelegate = ToolsMarketplaceDelegate(
        stateHandle = stateHandle,
        toolFavoritesRepository = toolFavoritesRepository,
        capabilitiesDelegate = capabilitiesDelegate,
        // Routed through the existing per-capability entry points rather than writing state
        // directly: code interpreter has an auth check hanging off its toggle, and the picker
        // must not be a second path that skips it.
        setCapability = { id, enabled ->
            when (id) {
                ToolConstants.EXECUTE_CODE -> onCodeInterpreterToggled(enabled)
                ToolConstants.FILE_SEARCH -> onFileSearchToggled(enabled)
                ToolConstants.WEB_SEARCH -> onWebSearchToggled(enabled)
                else -> onFileContextToggled(enabled)
            }
        },
        toggleTool = ::onToolToggled,
        toggleMcpTool = ::onMcpToolToggled,
    )

    init {
        loaderDelegate.loadReferenceData()
        capabilitiesDelegate.observeAvailability()
        codeSettingsDelegate.start()
        codeAuthDelegate.verifyCodeToolAuth()
        if (editAgentId != null) {
            loaderDelegate.loadAgent(editAgentId)
            actionsDelegate.loadActions()
            filesDelegate.loadAgentFiles(editAgentId)
        }
    }

    // --- Basic fields ---

    fun onNameChanged(name: String) {
        stateHandle.update { copy(name = name, nameError = null) }
    }

    fun onDescriptionChanged(description: String) {
        stateHandle.update { copy(description = description, descriptionError = null) }
    }

    fun onInstructionsChanged(instructions: String) {
        stateHandle.update { copy(instructions = instructions) }
    }

    fun onModelSelected(modelId: String, provider: String) {
        stateHandle.update { copy(model = modelId, provider = provider) }
    }

    fun onCategoryChanged(category: String) {
        stateHandle.update { copy(category = category) }
    }

    fun onToolToggled(toolId: String) {
        stateHandle.update {
            copy(selectedTools = if (toolId in selectedTools) selectedTools - toolId else selectedTools + toolId)
        }
    }

    fun onToolRemoved(toolId: String) {
        stateHandle.update { copy(selectedTools = selectedTools - toolId) }
    }

    fun onConversationStarterAdded(starter: String) {
        if (starter.isBlank()) return
        stateHandle.update { copy(conversationStarters = conversationStarters + starter.trim()) }
    }

    fun onConversationStarterRemoved(index: Int) {
        stateHandle.update {
            if (index in conversationStarters.indices) {
                copy(conversationStarters = conversationStarters.filterIndexed { i, _ -> i != index })
            } else {
                this
            }
        }
    }

    fun onCapabilitiesChanged(capabilities: AgentCapabilities) {
        stateHandle.update { copy(capabilities = capabilities) }
    }

    fun onAdvancedSettingsChanged(settings: AgentAdvancedSettings) {
        stateHandle.update { copy(advancedSettings = settings) }
    }

    // --- Support Contact ---

    fun onSupportContactChanged(supportContact: SupportContactState) {
        stateHandle.update {
            copy(
                supportContact = supportContact,
                supportContactNameError = null,
                supportContactEmailError = null,
            )
        }
    }

    // --- Actions ---

    fun saveAction(
        actionId: String?,
        metadata: ActionMetadata,
        functions: List<FunctionTool>,
    ) = actionsDelegate.saveAction(actionId, metadata, functions)

    fun deleteAction(actionId: String) = actionsDelegate.deleteAction(actionId)

    // --- MCP Tools ---

    fun onMcpToolToggled(toolName: String) {
        stateHandle.update {
            copy(selectedMcpTools = if (toolName in selectedMcpTools) selectedMcpTools - toolName else selectedMcpTools + toolName)
        }
    }

    // --- Tool approval modes (v0.8.9-rc1) ---

    /** Sets [toolId]'s mode in the form (null = inherit). Saved with the agent, like any field. */
    fun onToolApprovalModeChanged(toolId: String, mode: ToolApprovalMode?) {
        if (!uiState.value.isToolApprovalModesAvailable) return
        stateHandle.update { copy(toolOptions = toolOptions.withApprovalMode(toolId, mode)) }
    }

    /**
     * Forgets the user's remembered approvals for one tool of this saved agent. The server checks
     * the SAVED mode, so the screen offers this only when that one is chat/always.
     */
    fun resetToolApprovals(toolId: String) {
        val state = uiState.value
        val agentId = state.agentId ?: return
        if (!state.isToolApprovalModesAvailable || state.resettingApprovalToolId != null) return
        stateHandle.update { copy(resettingApprovalToolId = toolId) }
        viewModelScope.launch {
            val result = agentRepository.resetToolApprovals(agentId, toolId)
            stateHandle.update {
                copy(resettingApprovalToolId = null, approvalResetNotice = result.toApprovalResetNotice())
            }
        }
    }

    fun dismissApprovalResetNotice() {
        stateHandle.update { copy(approvalResetNotice = null) }
    }

    // --- Capability toggles ---

    fun onCodeInterpreterToggled(enabled: Boolean) = codeAuthDelegate.onCodeInterpreterToggled(enabled)

    fun dismissCodeToolAuthDialog() = codeAuthDelegate.dismissCodeToolAuthDialog()

    fun submitCodeToolApiKey(apiKey: String) = codeAuthDelegate.submitCodeToolApiKey(apiKey)

    fun revokeCodeToolApiKey() = codeAuthDelegate.revokeCodeToolApiKey()

    /** The code interpreter's "Background execution" switch; edits the form's `tool_options` only. */
    fun onCodeBackgroundToggled(enabled: Boolean) {
        if (uiState.value.codeBackground == null) return
        stateHandle.update { copy(toolOptions = toolOptions.withCodeBackground(enabled)) }
    }

    fun onFileSearchToggled(enabled: Boolean) {
        stateHandle.update { copy(fileSearchEnabled = enabled) }
    }

    fun onWebSearchToggled(enabled: Boolean) {
        stateHandle.update { copy(webSearchEnabled = enabled) }
    }

    fun onFileContextToggled(enabled: Boolean) {
        stateHandle.update { copy(fileContextEnabled = enabled) }
    }

    // --- Per-capability file attachments ---

    fun uploadAgentFile(fileRef: Any, slot: AgentFileSlot) = filesDelegate.uploadAgentFile(fileRef, slot)
    fun submitPdfPassword(password: String) = filesDelegate.submitPdfPassword(password)
    fun dismissPdfPassword() = filesDelegate.dismissPdfPassword()

    fun removeAgentFile(fileId: String, slot: AgentFileSlot) = filesDelegate.removeAgentFile(fileId, slot)

    fun confirmAgentFileRemoval() = filesDelegate.confirmAgentFileRemoval()

    fun dismissAgentFileRemoval() = filesDelegate.dismissAgentFileRemoval()

    // --- Sharing ---

    fun onSharingChanged(sharingState: AgentSharingState) {
        stateHandle.update { copy(sharingState = sharingState) }
    }

    // --- Skills (v0.8.6) ---

    fun onSkillsToggled(enabled: Boolean) = capabilitiesDelegate.onSkillsToggled(enabled)

    fun onSkillSelectionToggled(skillId: String) = capabilitiesDelegate.onSkillSelectionToggled(skillId)

    fun onSkillRemoved(skillId: String) = capabilitiesDelegate.onSkillRemoved(skillId)

    // --- Subagents (v0.8.6) ---

    fun onSubagentsToggled(enabled: Boolean) = capabilitiesDelegate.onSubagentsToggled(enabled)

    fun onSubagentAllowSelfToggled(allow: Boolean) = capabilitiesDelegate.onSubagentAllowSelfToggled(allow)

    fun addSubagent(agentId: String) = capabilitiesDelegate.addSubagent(agentId)

    fun removeSubagent(agentId: String) = capabilitiesDelegate.removeSubagent(agentId)

    // --- Chain (sequential multi-agent) ---

    fun addChainAgent(agentId: String) = capabilitiesDelegate.addChainAgent(agentId)

    fun removeChainAgent(agentId: String) = capabilitiesDelegate.removeChainAgent(agentId)

    // --- Handoffs (graph edges) ---

    fun addHandoffEdge(edge: HandoffEdge) = capabilitiesDelegate.addHandoffEdge(edge)

    fun updateHandoffEdge(index: Int, edge: HandoffEdge) = capabilitiesDelegate.updateHandoffEdge(index, edge)

    fun removeHandoffEdge(index: Int) = capabilitiesDelegate.removeHandoffEdge(index)

    // --- Dialog state ---

    fun dismissError() {
        stateHandle.update { copy(error = null) }
    }

    fun showDeleteConfirmation() {
        stateHandle.update { copy(showDeleteConfirm = true) }
    }

    fun dismissDeleteConfirmation() {
        stateHandle.update { copy(showDeleteConfirm = false) }
    }

    fun showDuplicateConfirmation() {
        stateHandle.update { copy(showDuplicateConfirm = true) }
    }

    fun dismissDuplicateConfirmation() {
        stateHandle.update { copy(showDuplicateConfirm = false) }
    }

    fun onBackRequested() {
        if (stateHandle.state.isCommitting) return
        if (stateHandle.state.hasUnsavedChanges()) {
            stateHandle.update { copy(showDiscardConfirm = true) }
        } else {
            viewModelScope.launch { _events.emit(AgentEditorEvent.Exit) }
        }
    }

    fun discardChanges() {
        stateHandle.update { copy(showDiscardConfirm = false) }
        viewModelScope.launch { _events.emit(AgentEditorEvent.Exit) }
    }

    fun dismissDiscardConfirmation() {
        stateHandle.update { copy(showDiscardConfirm = false) }
    }

    // --- Unified tools marketplace ---

    fun openToolsMarketplace() = marketplaceDelegate.openMarketplace()

    fun closeToolsMarketplace() = marketplaceDelegate.closeMarketplace()

    fun onMarketplaceQueryChanged(query: String) = marketplaceDelegate.onQueryChanged(query)

    fun onMarketplaceFilterChanged(filter: MarketplaceFilter) =
        marketplaceDelegate.onFilterChanged(filter)

    fun onMarketplaceItemToggled(item: MarketplaceItem) = marketplaceDelegate.toggleItem(item)

    fun onMarketplaceFavoriteToggled(item: MarketplaceItem) =
        marketplaceDelegate.toggleFavorite(item)

    fun showVersionHistory() {
        stateHandle.update { copy(showVersionHistory = true) }
        // v0.8.8 stopped inlining the history in the agent document, so on those servers the
        // list is empty until asked for. Guarded on emptiness so older servers, which still
        // inline it, do not pay for a second fetch.
        val agentId = editAgentId
        if (agentId != null && stateHandle.state.versions.isEmpty()) {
            loaderDelegate.loadVersions(agentId)
        }
    }

    fun dismissVersionHistory() {
        stateHandle.update { copy(showVersionHistory = false) }
    }

    // --- Avatar ---

    fun uploadAvatar(uri: Any) = filesDelegate.uploadAvatar(uri)

    fun resetAvatar() = filesDelegate.resetAvatar()

    // --- Duplicate / Delete / Revert ---

    fun duplicate() = saveDelegate.duplicate()

    fun delete() = saveDelegate.delete()

    fun revertToVersion(version: Int) = saveDelegate.revertToVersion(version)

    // --- Save ---

    fun save() = saveDelegate.save()

    companion object {

        /** Upstream caps chain (sequential multi-agent) at 10 entries. */
        const val CHAIN_MAX = 10

        /** Upstream `MAX_SUBAGENTS` (config.ts) — subagent agent_ids cap. */
        const val MAX_SUBAGENTS = 10

        // Sentinel error strings the screen layer recognizes and substitutes with
        // localized resources. Routing errors as identifiable markers keeps the
        // VM string-resource-agnostic without growing a parallel "errorKind" channel.
        const val AGENT_FILES_SAVE_FIRST_MARKER = "agent_files_save_first"
        const val AGENT_FILES_TOO_LARGE_MARKER = "agent_file_too_large:"
        const val AGENT_FILE_UPLOAD_FAILED_MARKER = "agent_file_upload_failed"
        const val AGENT_FILE_REMOVE_FAILED_MARKER = "agent_file_remove_failed"
    }
}

/** 403 is "not allowed for this agent or tool"; anything else (503, 429, offline) is worth a retry. */
internal fun Result<Unit>.toApprovalResetNotice(): ApprovalResetNotice? = when (this) {
    is Result.Success -> ApprovalResetNotice.SUCCESS
    is Result.Error -> if ((exception as? ApiException)?.statusCode == HTTP_FORBIDDEN) {
        ApprovalResetNotice.FORBIDDEN
    } else {
        ApprovalResetNotice.FAILED
    }
    is Result.Loading -> null
}

private const val HTTP_FORBIDDEN = 403
