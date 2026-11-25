package com.garfiec.librechat.feature.agents.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.ToolConstants
import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.AgentRepository
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.FileRepository
import com.garfiec.librechat.core.data.repository.McpRepository
import com.garfiec.librechat.core.data.repository.PromptRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.data.repository.SkillsRepository
import com.garfiec.librechat.core.data.repository.ToolFavoritesRepository
import com.garfiec.librechat.core.model.ActionMetadata
import com.garfiec.librechat.core.model.HandoffEdge
import com.garfiec.librechat.core.model.request.FunctionTool
import com.garfiec.librechat.feature.agents.components.model.AgentAdvancedSettings
import com.garfiec.librechat.feature.agents.components.model.AgentCapabilities
import com.garfiec.librechat.feature.agents.components.model.AgentSharingState
import com.garfiec.librechat.feature.agents.components.model.MarketplaceItem
import com.garfiec.librechat.feature.agents.components.model.SupportContactState
import com.garfiec.librechat.feature.agents.util.ContentReader
import com.garfiec.librechat.feature.agents.util.ToolApprovalMode
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
import com.garfiec.librechat.feature.agents.viewmodel.delegate.AgentToolKeySources
import com.garfiec.librechat.feature.agents.viewmodel.delegate.CodeToolAuthDelegate
import com.garfiec.librechat.feature.agents.viewmodel.delegate.InstructionsPromptDelegate
import com.garfiec.librechat.feature.agents.viewmodel.delegate.MarketplaceFilter
import com.garfiec.librechat.feature.agents.viewmodel.delegate.ToolsMarketplaceDelegate
import com.garfiec.librechat.feature.agents.viewmodel.delegate.WebSearchKeysDelegate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
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

sealed interface AgentEditorEvent {
    data class SaveSuccess(val agentId: String) : AgentEditorEvent
    data class DuplicateSuccess(val agentId: String) : AgentEditorEvent
    data object DeleteSuccess : AgentEditorEvent
    data object Exit : AgentEditorEvent
}

@Suppress("TooManyFunctions", "LongParameterList") // debt: 68 functions, constructor dependencies
class AgentEditorViewModel(
    private val agentRepository: AgentRepository,
    private val configRepository: ConfigRepository,
    private val mcpRepository: McpRepository,
    toolKeys: AgentToolKeySources,
    private val fileRepository: FileRepository,
    private val skillsRepository: SkillsRepository,
    private val roleRepository: RoleRepository,
    private val toolFavoritesRepository: ToolFavoritesRepository,
    codeSources: AgentCodeSources,
    private val contentReader: ContentReader,
    promptRepository: PromptRepository,
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

    private val instructionsPromptDelegate = InstructionsPromptDelegate(stateHandle, configRepository, promptRepository)

    /** The editable linked-instructions section; null keeps the inline field or the read-only note. */
    val instructionsPromptView: StateFlow<InstructionsPromptView?> = _uiState
        .map { it.instructionsPromptView() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun onInstructionsSourceChanged(fromPrompt: Boolean) = instructionsPromptDelegate.onSourceChanged(fromPrompt)

    fun onInstructionsPromptGroupSelected(groupId: String) = instructionsPromptDelegate.onGroupSelected(groupId)

    fun onInstructionsPromptVersionSelected(selection: InstructionsPromptSelection) =
        instructionsPromptDelegate.onVersionSelected(selection)

    fun retryPromptGroups() = instructionsPromptDelegate.retryGroups()

    fun retryPromptRevisions() = instructionsPromptDelegate.retryRevisions()

    private val codeAuthDelegate = CodeToolAuthDelegate(
        stateHandle = stateHandle,
        agentToolsRepository = toolKeys.tools,
    )

    private val webSearchKeysDelegate = WebSearchKeysDelegate(
        stateHandle = stateHandle,
        webSearchReadiness = toolKeys.webSearch,
        configRepository = configRepository,
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
        instructionsPromptDelegate.start()
        codeAuthDelegate.verifyCodeToolAuth()
        webSearchKeysDelegate.observe()
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

    /** [key] is an [com.garfiec.librechat.feature.agents.util.mcpToolKey], or a whole server's raw name. */
    fun onMcpToolToggled(key: String) {
        stateHandle.update {
            copy(selectedMcpTools = if (key in selectedMcpTools) selectedMcpTools - key else selectedMcpTools + key)
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

    fun onWebSearchToggled(enabled: Boolean) = webSearchKeysDelegate.onWebSearchToggled(enabled)

    fun openWebSearchKeys() = webSearchKeysDelegate.openSheet()

    fun dismissWebSearchKeys() = webSearchKeysDelegate.dismissSheet()

    /** In the ViewModel's scope, so a save the sheet started still lands if the sheet leaves first. */
    suspend fun saveWebSearchKeys(auth: Map<String, String>): Boolean =
        viewModelScope.async { webSearchKeysDelegate.saveKeys(auth) }.await()

    suspend fun removeWebSearchKeys(): Boolean = viewModelScope.async { webSearchKeysDelegate.removeKeys() }.await()

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
