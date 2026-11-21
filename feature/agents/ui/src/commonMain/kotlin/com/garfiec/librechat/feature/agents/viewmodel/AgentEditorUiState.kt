package com.garfiec.librechat.feature.agents.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.AgentCategory
import com.garfiec.librechat.core.model.AgentFile
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.EndpointToolApproval
import com.garfiec.librechat.core.model.HandoffEdge
import com.garfiec.librechat.core.model.PROMPT_SOURCE_LANGFUSE
import com.garfiec.librechat.core.model.PromptGroup
import com.garfiec.librechat.core.model.SkillSummary
import com.garfiec.librechat.core.model.code.StatefulCodeSessionsConfig
import com.garfiec.librechat.core.model.config.paramSettingsKey
import com.garfiec.librechat.core.model.mcp.McpTool
import com.garfiec.librechat.core.ui.components.PdfPasswordPromptUi
import com.garfiec.librechat.core.ui.websearch.WebSearchKeysState
import com.garfiec.librechat.feature.agents.AgentActionDisplayData
import com.garfiec.librechat.feature.agents.AgentHandoffDisplayData
import com.garfiec.librechat.feature.agents.AgentToolDisplayData
import com.garfiec.librechat.feature.agents.components.ModelOption
import com.garfiec.librechat.feature.agents.components.WebSearchKeysAction
import com.garfiec.librechat.feature.agents.components.model.AgentAdvancedSettings
import com.garfiec.librechat.feature.agents.components.model.AgentCapabilities
import com.garfiec.librechat.feature.agents.components.model.AgentSharingState
import com.garfiec.librechat.feature.agents.components.model.AgentVersion
import com.garfiec.librechat.feature.agents.components.model.AgentVersionBasis
import com.garfiec.librechat.feature.agents.components.model.SupportContactState
import com.garfiec.librechat.feature.agents.util.codeBackgroundEnabled
import com.garfiec.librechat.feature.agents.viewmodel.delegate.MarketplaceFilter
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
    /** Each MCP server's configured `title`, by server key; a server without one is shown by its key. */
    val mcpServerTitles: Map<String, String> = emptyMap(),
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
    /** Whether the server offers web search to agents (the agents endpoint's capabilities; fail-open). */
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
    /** Whether Gemini Hydrogen's `thinkingLevel` list (with `xhigh`) may be offered (v0.8.9+); see AgentCapabilitiesDelegate. */
    val isThinkingLevelXhighAvailable: Boolean = false,
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
     * The loaded agent's instructions come from a linked prompt group (v0.8.9-rc1+). Unless
     * [isInstructionsLinkEditable], the field is then read-only: editing the text would change
     * nothing the agent runs.
     */
    val instructionsLinkedToPrompt: Boolean = false,
    /** The server has the prompt revisions route (v0.8.9, landed 2026-10-30). See VERSION_GATES.md. */
    val isLinkedInstructionsVersionSupported: Boolean = false,
    /** `/api/endpoints` `agents.linkedInstructions.langfuse.enabled`: Langfuse groups are offered. */
    val isLangfuseLinkingEnabled: Boolean = false,
    /** Form: Prompt rather than Inline instructions. */
    val instructionsFromPrompt: Boolean = false,
    /** Form: the linked group and revision; null until a group is picked. */
    val instructionsLink: InstructionsPromptLink? = null,
    /** Save found Prompt mode with no group picked. */
    val instructionsPromptError: Boolean = false,
    val promptGroups: PromptLinkLoad<List<PromptGroup>> = PromptLinkLoad.Idle,
    val promptRevisions: PromptLinkLoad<GroupRevisions> = PromptLinkLoad.Idle,
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
    /** `subagents.agent_ids` — other agents that may be spawned (cap [maxSubagents], self excluded). */
    val selectedSubagentIds: List<String> = emptyList(),
    /** The server's cap on [selectedSubagentIds] (agents `maxSubagents`). */
    val maxSubagents: Int = AGENT_MAX_SUBAGENTS,
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
    /** The server's answer on the user's own web search keys; null until it answers (or when it failed). */
    val webSearchKeys: WebSearchKeysState? = null,
    val showWebSearchKeysSheet: Boolean = false,
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
     * The instructions link can be edited here: the revisions route and a loaded link this client
     * can read (not the restricted stub, nor a Langfuse link while the admin's switch is off).
     * Otherwise a linked agent keeps the read-only note. The switch only adds Langfuse groups.
     */
    val isInstructionsLinkEditable: Boolean
        get() {
            val stored = loadedAgent?.instructionsPrompt
            return isLinkedInstructionsVersionSupported && !isLinkedInstructionsSourceDisabled &&
                (stored !is JsonObject || stored.toInstructionsPromptLink() != null)
        }

    /** Prompt mode with no group picked: Save is blocked. */
    val isInstructionsPromptMissing: Boolean
        get() = isInstructionsLinkEditable && instructionsFromPrompt && instructionsLink == null

    /** A saved Langfuse link while the admin's switch is off: the agent runs without these instructions. */
    val isLinkedInstructionsSourceDisabled: Boolean
        get() = !isLangfuseLinkingEnabled &&
            loadedAgent?.instructionsPrompt.instructionsPromptSource() == PROMPT_SOURCE_LANGFUSE

    /**
     * Whether the editor shows a builtin: the server's capability AND the role's grant, as web's
     * catalog lists it. A hidden builtin the agent already has is still saved: the save reads
     * [isCodeInterpreterAvailable] / [isWebSearchAvailable], never these.
     */
    val isCodeInterpreterOffered: Boolean get() = isCodeInterpreterAvailable && canUseRunCode
    val isWebSearchOffered: Boolean get() = isWebSearchAvailable && canUseWebSearch

    /** The server leaves web search keys to the user and this user has none: turning it on asks for them. */
    val webSearchNeedsKeys: Boolean get() = webSearchKeys?.let { it.offered && !it.authenticated } == true

    val webSearchKeysAction: WebSearchKeysAction?
        get() = webSearchKeys?.takeIf { it.offered }?.let { if (it.authenticated) WebSearchKeysAction.MANAGE else WebSearchKeysAction.ADD }
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

/**
 * What an endpoint's `customParams` says about agent inference (served on `/api/endpoints`): the
 * family its parameters follow and the extra keys an administrator declared.
 */
data class AgentInferenceEndpoint(
    val defaultParamsEndpoint: String? = null,
    val declaredKeys: Set<String> = emptySet(),
)

/** Upstream caps chain (sequential multi-agent) at 10 entries. */
const val AGENT_CHAIN_MAX = 10

/** Upstream `MAX_SUBAGENTS` (limits.ts): the cap when the server sends no `agents.maxSubagents`. */
const val AGENT_MAX_SUBAGENTS = 10
