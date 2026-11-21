package com.garfiec.librechat.feature.agents.screen

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.ui.components.PlatformBackHandler
import com.garfiec.librechat.feature.agents.components.AgentAclActions
import com.garfiec.librechat.feature.agents.components.AgentAclSharingSection
import com.garfiec.librechat.feature.agents.components.AgentAvatarPicker
import com.garfiec.librechat.feature.agents.components.approvalResetText
import com.garfiec.librechat.feature.agents.components.rememberAgentFilePicker
import com.garfiec.librechat.feature.agents.resources.*
import com.garfiec.librechat.feature.agents.resources.Res
import com.garfiec.librechat.feature.agents.viewmodel.AgentAclViewModel
import com.garfiec.librechat.feature.agents.viewmodel.AgentEditorEvent
import com.garfiec.librechat.feature.agents.viewmodel.AgentEditorViewModel
import com.garfiec.librechat.feature.agents.viewmodel.AgentFileSlot
import com.garfiec.librechat.feature.agents.viewmodel.delegate.isMarketplaceItemSelected
import com.garfiec.librechat.feature.agents.viewmodel.delegate.marketplaceCatalog
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentEditorScreen(
    onBack: () -> Unit,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier,
    agentId: String? = null,
    onDelete: () -> Unit = onBack,
    onDuplicate: (String) -> Unit = onSave,
    viewModel: AgentEditorViewModel = koinViewModel { parametersOf(agentId) },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val hasUnsavedChanges by viewModel.hasUnsavedChanges.collectAsStateWithLifecycle()
    val codeSection by viewModel.codeSection.collectAsStateWithLifecycle()
    val instructionsPromptView by viewModel.instructionsPromptView.collectAsStateWithLifecycle()
    val mcpRows by viewModel.mcpToolRows.collectAsStateWithLifecycle()
    val currentOnBack by rememberUpdatedState(onBack)
    val currentOnSaved by rememberUpdatedState(onSave)
    val currentOnDuplicated by rememberUpdatedState(onDuplicate)
    val currentOnDeleted by rememberUpdatedState(onDelete)
    val snackbarHostState = remember { SnackbarHostState() }

    // One picker per slot; iOS impl is a stub today.
    val codeFilePicker = rememberAgentFilePicker(
        onFilePick = { ref -> viewModel.uploadAgentFile(ref, AgentFileSlot.CODE) },
    )
    val knowledgeFilePicker = rememberAgentFilePicker(
        onFilePick = { ref -> viewModel.uploadAgentFile(ref, AgentFileSlot.KNOWLEDGE) },
    )
    val contextFilePicker = rememberAgentFilePicker(
        onFilePick = { ref -> viewModel.uploadAgentFile(ref, AgentFileSlot.CONTEXT) },
    )

    // Map VM sentinel error markers to localized snackbar messages. Other errors
    // continue to flow through the ErrorBanner banner below.
    val saveFirstMsg = stringResource(Res.string.agent_files_save_first)
    val uploadFailedMsg = stringResource(Res.string.agent_file_upload_failed)
    val removeFailedMsg = stringResource(Res.string.agent_file_remove_failed)
    // The numeric MB comes from a VM-side marker; resolve the format with a placeholder
    // we replace at LaunchedEffect time. `%1$d` -> "%d" makes the substitution trivial.
    val tooLargeTemplate = stringResource(Res.string.agent_file_too_large, 0)
        .replaceFirst("0", "%d")
    LaunchedEffect(uiState.error) {
        val err = uiState.error ?: return@LaunchedEffect
        val localized = when {
            err == AgentEditorViewModel.AGENT_FILES_SAVE_FIRST_MARKER -> saveFirstMsg
            err.startsWith(AgentEditorViewModel.AGENT_FILES_TOO_LARGE_MARKER) -> {
                val mb = err.removePrefix(AgentEditorViewModel.AGENT_FILES_TOO_LARGE_MARKER)
                    .toIntOrNull() ?: 0
                tooLargeTemplate.replace("%d", mb.toString())
            }
            err == AgentEditorViewModel.AGENT_FILE_UPLOAD_FAILED_MARKER -> uploadFailedMsg
            err == AgentEditorViewModel.AGENT_FILE_REMOVE_FAILED_MARKER -> removeFailedMsg
            else -> null
        }
        if (localized != null) {
            snackbarHostState.showSnackbar(localized)
            viewModel.dismissError()
        }
    }

    val resetText = uiState.approvalResetNotice?.let { approvalResetText(it) }
    LaunchedEffect(uiState.approvalResetNotice) {
        val text = resetText ?: return@LaunchedEffect
        // Shown first: dismissing changes this effect's key, which would cancel the snackbar.
        snackbarHostState.showSnackbar(text)
        viewModel.dismissApprovalResetNotice()
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AgentEditorEvent.SaveSuccess -> currentOnSaved(event.agentId)
                is AgentEditorEvent.DuplicateSuccess -> currentOnDuplicated(event.agentId)
                is AgentEditorEvent.DeleteSuccess -> currentOnDeleted()
                is AgentEditorEvent.Exit -> currentOnBack()
            }
        }
    }

    // Only while dirty or committing, so a clean form keeps the system's predictive-back preview.
    PlatformBackHandler(
        enabled = hasUnsavedChanges || uiState.isCommitting,
        onBack = viewModel::onBackRequested,
    )

    val toolCatalog = if (uiState.showToolsMarketplace) {
        remember(uiState) { uiState.marketplaceCatalog() }
    } else {
        emptyList()
    }
    val selectedToolKeys = if (uiState.showToolsMarketplace) {
        remember(uiState, toolCatalog) {
            toolCatalog.filter { uiState.isMarketplaceItemSelected(it) }.map { it.itemKey }.toSet()
        }
    } else {
        emptySet()
    }

    val actions = remember(viewModel, codeFilePicker, knowledgeFilePicker, contextFilePicker) {
        AgentEditorActions(
            onBackRequested = viewModel::onBackRequested,
            onShowDuplicateConfirmation = viewModel::showDuplicateConfirmation,
            onShowVersionHistory = viewModel::showVersionHistory,
            onShowDeleteConfirmation = viewModel::showDeleteConfirmation,
            onSubmitPdfPassword = viewModel::submitPdfPassword,
            onDismissPdfPassword = viewModel::dismissPdfPassword,
            onAddCodeFile = { codeFilePicker.launch("*/*") },
            onAddKnowledgeFile = { knowledgeFilePicker.launch("*/*") },
            onAddContextFile = { contextFilePicker.launch("*/*") },
            onOpenToolsMarketplace = viewModel::openToolsMarketplace,
            onCloseToolsMarketplace = viewModel::closeToolsMarketplace,
            onDismissAgentFileRemoval = viewModel::dismissAgentFileRemoval,
            onConfirmAgentFileRemoval = viewModel::confirmAgentFileRemoval,
            onDismissError = viewModel::dismissError,
            onResetAvatar = viewModel::resetAvatar,
            onNameChanged = viewModel::onNameChanged,
            onDescriptionChanged = viewModel::onDescriptionChanged,
            onCategoryChanged = viewModel::onCategoryChanged,
            onInstructionsSourceChanged = viewModel::onInstructionsSourceChanged,
            onInstructionsPromptGroupSelected = viewModel::onInstructionsPromptGroupSelected,
            onInstructionsPromptVersionSelected = viewModel::onInstructionsPromptVersionSelected,
            onRetryPromptGroups = viewModel::retryPromptGroups,
            onRetryPromptRevisions = viewModel::retryPromptRevisions,
            onInstructionsChanged = viewModel::onInstructionsChanged,
            onModelSelected = viewModel::onModelSelected,
            onCapabilitiesChanged = viewModel::onCapabilitiesChanged,
            onCodeInterpreterToggled = viewModel::onCodeInterpreterToggled,
            onRemoveAgentFile = viewModel::removeAgentFile,
            onCodeBackgroundToggled = viewModel::onCodeBackgroundToggled,
            onCodeSettingsEvent = viewModel::onCodeSettingsEvent,
            onWebSearchToggled = viewModel::onWebSearchToggled,
            onOpenWebSearchKeys = viewModel::openWebSearchKeys,
            onSaveWebSearchKeys = viewModel::saveWebSearchKeys,
            onRemoveWebSearchKeys = viewModel::removeWebSearchKeys,
            onDismissWebSearchKeys = viewModel::dismissWebSearchKeys,
            onSkillsToggled = viewModel::onSkillsToggled,
            onSkillSelectionToggled = viewModel::onSkillSelectionToggled,
            onSkillRemoved = viewModel::onSkillRemoved,
            onSubagentsToggled = viewModel::onSubagentsToggled,
            onSubagentAllowSelfToggled = viewModel::onSubagentAllowSelfToggled,
            onAddSubagent = viewModel::addSubagent,
            onRemoveSubagent = viewModel::removeSubagent,
            onFileSearchToggled = viewModel::onFileSearchToggled,
            onFileContextToggled = viewModel::onFileContextToggled,
            onToolRemoved = viewModel::onToolRemoved,
            onMcpToolToggled = viewModel::onMcpToolToggled,
            onToolApprovalModeChanged = viewModel::onToolApprovalModeChanged,
            onResetToolApprovals = viewModel::resetToolApprovals,
            onSaveAction = viewModel::saveAction,
            onDeleteAction = viewModel::deleteAction,
            onSupportContactChanged = viewModel::onSupportContactChanged,
            onConversationStarterAdded = viewModel::onConversationStarterAdded,
            onConversationStarterRemoved = viewModel::onConversationStarterRemoved,
            onSharingChanged = viewModel::onSharingChanged,
            onAddChainAgent = viewModel::addChainAgent,
            onRemoveChainAgent = viewModel::removeChainAgent,
            onAddHandoffEdge = viewModel::addHandoffEdge,
            onUpdateHandoffEdge = viewModel::updateHandoffEdge,
            onRemoveHandoffEdge = viewModel::removeHandoffEdge,
            onAdvancedSettingsChanged = viewModel::onAdvancedSettingsChanged,
            onSave = viewModel::save,
            onDiscardChanges = viewModel::discardChanges,
            onDismissDiscardConfirmation = viewModel::dismissDiscardConfirmation,
            onDelete = viewModel::delete,
            onDismissDeleteConfirmation = viewModel::dismissDeleteConfirmation,
            onDuplicate = viewModel::duplicate,
            onDismissDuplicateConfirmation = viewModel::dismissDuplicateConfirmation,
            onRevertToVersion = viewModel::revertToVersion,
            onDismissVersionHistory = viewModel::dismissVersionHistory,
            onMarketplaceQueryChanged = viewModel::onMarketplaceQueryChanged,
            onMarketplaceFilterChanged = viewModel::onMarketplaceFilterChanged,
            onMarketplaceItemToggled = viewModel::onMarketplaceItemToggled,
            onMarketplaceFavoriteToggled = viewModel::onMarketplaceFavoriteToggled,
            onSubmitCodeToolApiKey = viewModel::submitCodeToolApiKey,
            onRevokeCodeToolApiKey = viewModel::revokeCodeToolApiKey,
            onDismissCodeToolAuthDialog = viewModel::dismissCodeToolAuthDialog,
        )
    }

    AgentEditorContent(
        uiState = uiState,
        codeSection = codeSection,
        instructionsPromptView = instructionsPromptView,
        mcpRows = mcpRows,
        toolCatalog = toolCatalog,
        selectedToolKeys = selectedToolKeys,
        actions = actions,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
        avatarPicker = { avatarUrl, agentName ->
            AgentAvatarPicker(
                avatarUrl = avatarUrl,
                agentName = agentName,
                onImageSelect = viewModel::uploadAvatar,
            )
        },
        aclSection = { AgentAclSharing(loadedAgent = uiState.loadedAgent) },
    )
}

/** The ACL sharing section, over its own ViewModel; created only where the section is drawn. */
@Composable
private fun AgentAclSharing(loadedAgent: Agent?) {
    val aclViewModel: AgentAclViewModel = koinViewModel()
    // The permissions routes take the agent's database _id, not its agent_… id; until the
    // agent has loaded there is nothing to ask for, and the section stays hidden.
    val agentDbId = loadedAgent?.mongoId
    val authorId = loadedAgent?.author
    LaunchedEffect(agentDbId, authorId) {
        if (agentDbId != null) aclViewModel.load(agentDbId, authorId)
    }
    val aclState by aclViewModel.uiState.collectAsState()
    AgentAclSharingSection(
        uiState = aclState,
        actions = AgentAclActions(
            onRetryShareCheck = aclViewModel::retryShareCheck,
            onSetPublic = aclViewModel::setPublic,
            onRetryLoad = aclViewModel::retryLoad,
            onRevoke = aclViewModel::revoke,
            onOpenGrantDialog = aclViewModel::openGrantDialog,
            onSearchQueryChanged = aclViewModel::onSearchQueryChanged,
            onRetrySearch = aclViewModel::retrySearch,
            onRetryRoles = aclViewModel::retryRoles,
            onGrant = aclViewModel::grant,
            onDismissGrantDialog = aclViewModel::dismissGrantDialog,
        ),
    )
}
