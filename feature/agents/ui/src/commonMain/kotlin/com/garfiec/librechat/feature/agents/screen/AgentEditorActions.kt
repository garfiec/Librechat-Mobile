package com.garfiec.librechat.feature.agents.screen

import com.garfiec.librechat.core.model.ActionMetadata
import com.garfiec.librechat.core.model.HandoffEdge
import com.garfiec.librechat.core.model.request.FunctionTool
import com.garfiec.librechat.feature.agents.components.model.AgentAdvancedSettings
import com.garfiec.librechat.feature.agents.components.model.AgentCapabilities
import com.garfiec.librechat.feature.agents.components.model.AgentSharingState
import com.garfiec.librechat.feature.agents.components.model.MarketplaceItem
import com.garfiec.librechat.feature.agents.components.model.SupportContactState
import com.garfiec.librechat.feature.agents.util.ToolApprovalMode
import com.garfiec.librechat.feature.agents.viewmodel.AgentFileSlot
import com.garfiec.librechat.feature.agents.viewmodel.InstructionsPromptSelection
import com.garfiec.librechat.feature.agents.viewmodel.delegate.AgentCodeEvent
import com.garfiec.librechat.feature.agents.viewmodel.delegate.MarketplaceFilter

class AgentEditorActions(
    val onBackRequested: () -> Unit = {},
    val onShowDuplicateConfirmation: () -> Unit = {},
    val onShowVersionHistory: () -> Unit = {},
    val onShowDeleteConfirmation: () -> Unit = {},
    val onSubmitPdfPassword: (password: String) -> Unit = {},
    val onDismissPdfPassword: () -> Unit = {},
    val onAddCodeFile: () -> Unit = {},
    val onAddKnowledgeFile: () -> Unit = {},
    val onAddContextFile: () -> Unit = {},
    val onOpenToolsMarketplace: () -> Unit = {},
    val onCloseToolsMarketplace: () -> Unit = {},
    val onDismissAgentFileRemoval: () -> Unit = {},
    val onConfirmAgentFileRemoval: () -> Unit = {},
    val onDismissError: () -> Unit = {},
    val onResetAvatar: () -> Unit = {},
    val onNameChanged: (name: String) -> Unit = {},
    val onDescriptionChanged: (description: String) -> Unit = {},
    val onCategoryChanged: (category: String) -> Unit = {},
    val onInstructionsSourceChanged: (fromPrompt: Boolean) -> Unit = {},
    val onInstructionsPromptGroupSelected: (groupId: String) -> Unit = {},
    val onInstructionsPromptVersionSelected: (selection: InstructionsPromptSelection) -> Unit = {},
    val onRetryPromptGroups: () -> Unit = {},
    val onRetryPromptRevisions: () -> Unit = {},
    val onInstructionsChanged: (instructions: String) -> Unit = {},
    val onModelSelected: (modelId: String, provider: String) -> Unit = { _, _ -> },
    val onCapabilitiesChanged: (capabilities: AgentCapabilities) -> Unit = {},
    val onCodeInterpreterToggled: (enabled: Boolean) -> Unit = {},
    val onRemoveAgentFile: (fileId: String, slot: AgentFileSlot) -> Unit = { _, _ -> },
    val onCodeBackgroundToggled: (enabled: Boolean) -> Unit = {},
    val onCodeSettingsEvent: (event: AgentCodeEvent) -> Unit = {},
    val onWebSearchToggled: (enabled: Boolean) -> Unit = {},
    val onOpenWebSearchKeys: () -> Unit = {},
    val onSaveWebSearchKeys: suspend (auth: Map<String, String>) -> Boolean = { false },
    val onRemoveWebSearchKeys: suspend () -> Boolean = { false },
    val onDismissWebSearchKeys: () -> Unit = {},
    val onSkillsToggled: (enabled: Boolean) -> Unit = {},
    val onSkillSelectionToggled: (skillId: String) -> Unit = {},
    val onSkillRemoved: (skillId: String) -> Unit = {},
    val onSubagentsToggled: (enabled: Boolean) -> Unit = {},
    val onSubagentAllowSelfToggled: (allow: Boolean) -> Unit = {},
    val onAddSubagent: (agentId: String) -> Unit = {},
    val onRemoveSubagent: (agentId: String) -> Unit = {},
    val onFileSearchToggled: (enabled: Boolean) -> Unit = {},
    val onFileContextToggled: (enabled: Boolean) -> Unit = {},
    val onToolRemoved: (toolId: String) -> Unit = {},
    val onMcpToolToggled: (key: String) -> Unit = {},
    val onToolApprovalModeChanged: (toolId: String, mode: ToolApprovalMode?) -> Unit = { _, _ -> },
    val onResetToolApprovals: (toolId: String) -> Unit = {},
    val onSaveAction: (actionId: String?, metadata: ActionMetadata, functions: List<FunctionTool>) -> Unit = { _, _, _ -> },
    val onDeleteAction: (actionId: String) -> Unit = {},
    val onSupportContactChanged: (supportContact: SupportContactState) -> Unit = {},
    val onConversationStarterAdded: (starter: String) -> Unit = {},
    val onConversationStarterRemoved: (index: Int) -> Unit = {},
    val onSharingChanged: (sharingState: AgentSharingState) -> Unit = {},
    val onAddChainAgent: (agentId: String) -> Unit = {},
    val onRemoveChainAgent: (agentId: String) -> Unit = {},
    val onAddHandoffEdge: (edge: HandoffEdge) -> Unit = {},
    val onUpdateHandoffEdge: (index: Int, edge: HandoffEdge) -> Unit = { _, _ -> },
    val onRemoveHandoffEdge: (index: Int) -> Unit = {},
    val onAdvancedSettingsChanged: (settings: AgentAdvancedSettings) -> Unit = {},
    val onSave: () -> Unit = {},
    val onDiscardChanges: () -> Unit = {},
    val onDismissDiscardConfirmation: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onDismissDeleteConfirmation: () -> Unit = {},
    val onDuplicate: () -> Unit = {},
    val onDismissDuplicateConfirmation: () -> Unit = {},
    val onRevertToVersion: (version: Int) -> Unit = {},
    val onDismissVersionHistory: () -> Unit = {},
    val onMarketplaceQueryChanged: (query: String) -> Unit = {},
    val onMarketplaceFilterChanged: (filter: MarketplaceFilter) -> Unit = {},
    val onMarketplaceItemToggled: (item: MarketplaceItem) -> Unit = {},
    val onMarketplaceFavoriteToggled: (item: MarketplaceItem) -> Unit = {},
    val onSubmitCodeToolApiKey: (apiKey: String) -> Unit = {},
    val onRevokeCodeToolApiKey: () -> Unit = {},
    val onDismissCodeToolAuthDialog: () -> Unit = {},
)
