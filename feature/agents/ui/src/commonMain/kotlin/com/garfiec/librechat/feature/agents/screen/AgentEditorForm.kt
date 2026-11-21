package com.garfiec.librechat.feature.agents.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptiveAlertDialog
import com.garfiec.librechat.core.ui.components.AdaptiveButton
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveInputChip
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedButton
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedTextField
import com.garfiec.librechat.core.ui.components.AdaptiveTextButton
import com.garfiec.librechat.core.ui.components.ErrorBanner
import com.garfiec.librechat.feature.agents.components.AgentActionsPanel
import com.garfiec.librechat.feature.agents.components.AgentAdvancedPanel
import com.garfiec.librechat.feature.agents.components.AgentCapabilitiesSection
import com.garfiec.librechat.feature.agents.components.AgentCategorySelector
import com.garfiec.librechat.feature.agents.components.AgentChainSection
import com.garfiec.librechat.feature.agents.components.AgentCodeBackgroundRow
import com.garfiec.librechat.feature.agents.components.AgentCodeInterpreterSection
import com.garfiec.librechat.feature.agents.components.AgentCodeSettingsSection
import com.garfiec.librechat.feature.agents.components.AgentFileAttachments
import com.garfiec.librechat.feature.agents.components.AgentFileContextSection
import com.garfiec.librechat.feature.agents.components.AgentFileSearchSection
import com.garfiec.librechat.feature.agents.components.AgentHandoffsSection
import com.garfiec.librechat.feature.agents.components.AgentModelPicker
import com.garfiec.librechat.feature.agents.components.AgentSharingSection
import com.garfiec.librechat.feature.agents.components.AgentSkillsSection
import com.garfiec.librechat.feature.agents.components.AgentSubagentsSection
import com.garfiec.librechat.feature.agents.components.AgentSupportContactSection
import com.garfiec.librechat.feature.agents.components.AgentWebSearchSection
import com.garfiec.librechat.feature.agents.components.InstructionsPromptFields
import com.garfiec.librechat.feature.agents.components.InstructionsSourceChoice
import com.garfiec.librechat.feature.agents.components.ToolApprovalModeButton
import com.garfiec.librechat.feature.agents.resources.*
import com.garfiec.librechat.feature.agents.resources.Res
import com.garfiec.librechat.feature.agents.viewmodel.AGENT_CHAIN_MAX
import com.garfiec.librechat.feature.agents.viewmodel.AgentCodeSectionView
import com.garfiec.librechat.feature.agents.viewmodel.AgentEditorUiState
import com.garfiec.librechat.feature.agents.viewmodel.AgentFileSlot
import com.garfiec.librechat.feature.agents.viewmodel.InstructionsPromptView
import com.garfiec.librechat.feature.agents.viewmodel.McpToolRowDisplay
import org.jetbrains.compose.resources.stringResource

/**
 * The scrollable editor body. Field order mirrors the web app's AgentConfig:
 * avatar, name, description, category, instructions, model, capabilities,
 * tools & actions, support contact, conversation starters, sharing,
 * chain/handoffs, advanced, save. Each gated section renders only when its
 * server capability is present.
 */
@Composable
internal fun AgentEditorForm(
    uiState: AgentEditorUiState,
    codeSection: AgentCodeSectionView?,
    instructionsPromptView: InstructionsPromptView?,
    mcpRows: List<McpToolRowDisplay>,
    actions: AgentEditorActions,
    avatarPicker: @Composable (avatarUrl: String?, agentName: String) -> Unit,
    aclSection: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    uiState.pendingFileRemoval?.let { pending ->
        AdaptiveAlertDialog(
            onDismissRequest = actions.onDismissAgentFileRemoval,
            title = {
                Text(
                    pending.filename?.takeIf { it.isNotBlank() }
                        ?.let { stringResource(Res.string.agent_file_remove_confirm_title, it) }
                        ?: stringResource(Res.string.agent_file_remove_confirm_title_generic),
                )
            },
            text = { Text(stringResource(Res.string.agent_file_remove_confirm_message)) },
            confirmButton = {
                AdaptiveTextButton(onClick = actions.onConfirmAgentFileRemoval) {
                    Text(stringResource(Res.string.remove))
                }
            },
            dismissButton = {
                AdaptiveTextButton(onClick = actions.onDismissAgentFileRemoval) { Text(stringResource(Res.string.cancel)) }
            },
        )
    }

    Column(
        modifier = modifier
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        if (uiState.error != null) {
            ErrorBanner(
                message = uiState.error ?: stringResource(Res.string.error_unknown),
                onRetry = { actions.onDismissError() },
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // 1. Avatar picker at top center
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            avatarPicker(uiState.avatarUrl, uiState.name)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(Res.string.tap_to_change_avatar),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (uiState.isEditMode && !uiState.avatarUrl.isNullOrBlank()) {
                AdaptiveTextButton(onClick = actions.onResetAvatar) {
                    Text(stringResource(Res.string.remove_avatar))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Name field (required)
        AdaptiveOutlinedTextField(
            value = uiState.name,
            onValueChange = actions.onNameChanged,
            label = {
                Text(stringResource(Res.string.agent_name_label))
            },
            placeholder = {
                Text(stringResource(Res.string.agent_name_placeholder), maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            isError = uiState.nameError != null,
            supportingText = uiState.nameError?.let { error ->
                { Text(error) }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 3. Description field
        AdaptiveOutlinedTextField(
            value = uiState.description,
            onValueChange = actions.onDescriptionChanged,
            label = { Text(stringResource(Res.string.agent_description_label)) },
            placeholder = { Text(stringResource(Res.string.agent_description_placeholder)) },
            isError = uiState.descriptionError != null,
            supportingText = uiState.descriptionError?.let { error ->
                { Text(error) }
            },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 4. Category selector (required, defaults to "general")
        AgentCategorySelector(
            selectedCategory = uiState.category,
            categories = uiState.categories,
            onCategorySelect = actions.onCategoryChanged,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 5. Instructions: inline text (+ Insert-variable menu), or a linked prompt
        val promptView = instructionsPromptView
        when {
            promptView != null -> {
                InstructionsSourceChoice(
                    fromPrompt = promptView.fromPrompt,
                    onSourceChange = actions.onInstructionsSourceChanged,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                if (promptView.fromPrompt) {
                    InstructionsPromptFields(
                        view = promptView,
                        onGroupSelect = actions.onInstructionsPromptGroupSelected,
                        onVersionSelect = actions.onInstructionsPromptVersionSelected,
                        onRetryGroups = actions.onRetryPromptGroups,
                        onRetryVersions = actions.onRetryPromptRevisions,
                    )
                } else {
                    InstructionsField(
                        value = uiState.instructions,
                        onValueChange = actions.onInstructionsChanged,
                    )
                }
            }
            uiState.instructionsLinkedToPrompt ->
                LinkedInstructionsNote(sourceDisabled = uiState.isLinkedInstructionsSourceDisabled)
            else -> InstructionsField(
                value = uiState.instructions,
                onValueChange = actions.onInstructionsChanged,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 6. Model picker (required)
        AgentModelPicker(
            selectedModel = uiState.model,
            availableModels = uiState.availableModels,
            onModelSelect = actions.onModelSelected,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 7. Capabilities section (Artifacts, EndAfterTools, HideSeq, RecursionLimit)
        AgentCapabilitiesSection(
            capabilities = uiState.capabilities,
            onCapabilitiesChange = actions.onCapabilitiesChanged,
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Code Interpreter toggle (the server's capability and the role's RUN_CODE)
        if (uiState.isCodeInterpreterOffered) {
            AgentCodeInterpreterSection(
                enabled = uiState.codeInterpreterEnabled,
                onToggle = actions.onCodeInterpreterToggled,
            )
            if (uiState.codeInterpreterEnabled) {
                AgentFileAttachments(
                    files = uiState.codeFiles,
                    isUploading = AgentFileSlot.CODE in uiState.uploadingSlots,
                    onAddClick = actions.onAddCodeFile,
                    onRemove = { id ->
                        actions.onRemoveAgentFile(id, AgentFileSlot.CODE)
                    },
                )
            }
            uiState.codeBackground?.let { checked ->
                Spacer(modifier = Modifier.height(8.dp))
                AgentCodeBackgroundRow(
                    checked = checked,
                    onToggle = actions.onCodeBackgroundToggled,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            codeSection?.let { section ->
                Spacer(modifier = Modifier.height(8.dp))
                AgentCodeSettingsSection(
                    view = section,
                    onEvent = actions.onCodeSettingsEvent,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // Web Search toggle (the server's configuration and the role's WEB_SEARCH)
        if (uiState.isWebSearchOffered) {
            AgentWebSearchSection(
                enabled = uiState.webSearchEnabled,
                onToggle = actions.onWebSearchToggled,
                keysAction = uiState.webSearchKeysAction,
                onKeys = actions.onOpenWebSearchKeys,
            )

            Spacer(modifier = Modifier.height(8.dp))
        }

        // Skills selector (gated on agents `skills` capability + SKILLS permission)
        if (uiState.isSkillsAvailable) {
            AgentSkillsSection(
                enabled = uiState.skillsEnabled,
                selectedSkillIds = uiState.selectedSkillIds,
                availableSkills = uiState.availableSkills,
                onToggle = actions.onSkillsToggled,
                onSkillToggle = actions.onSkillSelectionToggled,
                onSkillRemove = actions.onSkillRemoved,
            )

            Spacer(modifier = Modifier.height(8.dp))
        }

        // Subagents config (gated on agents `subagents` capability)
        if (uiState.isSubagentsAvailable) {
            AgentSubagentsSection(
                enabled = uiState.subagentsEnabled,
                allowSelf = uiState.subagentAllowSelf,
                selectedSubagentIds = uiState.selectedSubagentIds,
                // Self can't be a listed subagent — it spawns itself via allowSelf.
                availableAgents = uiState.allAgents.filter { it.id != uiState.agentId },
                maxSubagents = uiState.maxSubagents,
                onToggle = actions.onSubagentsToggled,
                onAllowSelfToggle = actions.onSubagentAllowSelfToggled,
                onAddSubagent = actions.onAddSubagent,
                onRemoveSubagent = actions.onRemoveSubagent,
            )

            Spacer(modifier = Modifier.height(8.dp))
        }

        // File Search toggle (the server's capability and the role's FILE_SEARCH)
        if (uiState.isFileSearchOffered) {
            AgentFileSearchSection(
                enabled = uiState.fileSearchEnabled,
                onToggle = actions.onFileSearchToggled,
            )
            if (uiState.fileSearchEnabled) {
                AgentFileAttachments(
                    files = uiState.knowledgeFiles,
                    isUploading = AgentFileSlot.KNOWLEDGE in uiState.uploadingSlots,
                    onAddClick = actions.onAddKnowledgeFile,
                    onRemove = { id ->
                        actions.onRemoveAgentFile(id, AgentFileSlot.KNOWLEDGE)
                    },
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // File Context toggle (persistent files attached as context)
        AgentFileContextSection(
            enabled = uiState.fileContextEnabled,
            onToggle = actions.onFileContextToggled,
        )
        if (uiState.fileContextEnabled) {
            AgentFileAttachments(
                files = uiState.contextFiles,
                isUploading = AgentFileSlot.CONTEXT in uiState.uploadingSlots,
                onAddClick = actions.onAddContextFile,
                onRemove = { id ->
                    actions.onRemoveAgentFile(id, AgentFileSlot.CONTEXT)
                },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 8. Tools & Actions section
        Text(
            text = stringResource(Res.string.label_tools_and_actions),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Show selected tools as removable items with icon
        if (uiState.selectedTools.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                uiState.selectedTools.forEach { toolId ->
                    val toolData = uiState.availableTools.find {
                        (it.toolId ?: it.name) == toolId
                    }
                    SelectedToolRow(
                        toolName = toolData?.name ?: toolId,
                        toolDescription = toolData?.description,
                        onRemove = { actions.onToolRemoved(toolId) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // MCP tools sit in the same selected list as plugin tools: which server a capability was
        // installed from is a detail of its plumbing, not of what the agent can do. Only the ones
        // actually on the agent are listed — browsing the rest is the picker's job.
        // A tool whose server has since gone away still renders, by raw name, so it can be removed
        // rather than sitting invisibly on the agent. The approval mode button shows only on a
        // tool with a known server, and only when the server enables agent approval modes.
        if (mcpRows.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                mcpRows.forEach { row ->
                    SelectedToolRow(
                        toolName = row.name,
                        toolDescription = row.subtitle,
                        onRemove = { actions.onMcpToolToggled(row.selectionKey) },
                        trailing = row.approval?.let { approval ->
                            {
                                ToolApprovalModeButton(
                                    approval = approval,
                                    onModeChange = { mode -> actions.onToolApprovalModeChanged(approval.toolId, mode) },
                                    onReset = { actions.onResetToolApprovals(approval.toolId) },
                                )
                            }
                        },
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Show selected actions
        if (uiState.actions.isNotEmpty()) {
            uiState.actions.forEach { action ->
                val actionId = action.actionId
                if (actionId != null) {
                    val authLabel = when (action.authType) {
                        "service_http" -> stringResource(Res.string.auth_api_key)
                        "oauth" -> stringResource(Res.string.auth_oauth)
                        else -> stringResource(Res.string.auth_none)
                    }
                    SelectedToolRow(
                        toolName = action.domain ?: stringResource(Res.string.label_action),
                        toolDescription = stringResource(Res.string.action_auth_info, authLabel, action.functionCount),
                        onRemove = { actions.onDeleteAction(actionId) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Opens the unified picker (capabilities + tools + MCP + skills), not just the plugin
        // tool list — so it is offered even when that list happens to be empty.
        AdaptiveOutlinedButton(
            onClick = actions.onOpenToolsMarketplace,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Default.Build,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(Res.string.tools_browse))
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Actions panel (collapsible with full editor)
        AgentActionsPanel(
            actions = uiState.actions,
            onSaveAction = actions.onSaveAction,
            onDeleteAction = actions.onDeleteAction,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 9. Support Contact
        AgentSupportContactSection(
            supportContact = uiState.supportContact,
            onSupportContactChange = actions.onSupportContactChanged,
            nameError = uiState.supportContactNameError,
            emailError = uiState.supportContactEmailError,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 10. Conversation starters
        Text(
            text = stringResource(Res.string.label_conversation_starters),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(modifier = Modifier.height(8.dp))

        uiState.conversationStarters.forEachIndexed { index, starter ->
            AdaptiveInputChip(
                selected = false,
                onClick = {},
                label = { Text(starter) },
                trailingIcon = {
                    IconButton(
                        onClick = { actions.onConversationStarterRemoved(index) },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(Res.string.remove),
                        )
                    }
                },
            )
        }

        var newStarter by rememberSaveable { mutableStateOf("") }
        Row(
            modifier = Modifier.fillMaxWidth(),
        ) {
            AdaptiveOutlinedTextField(
                value = newStarter,
                onValueChange = { newStarter = it },
                placeholder = {
                    Text(stringResource(Res.string.add_starter_hint), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    actions.onConversationStarterAdded(newStarter)
                    newStarter = ""
                },
                enabled = newStarter.isNotBlank(),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(Res.string.cd_add_starter),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Sharing & Permissions:
        // v0.8.5+ servers expose a granular ACL surface; older servers keep the
        // legacy Private / Team / Public + Collaborative toggle.
        // The ACL section is only meaningful for an existing agent (the API
        // operates on a specific agentId). In create-mode on v0.8.5+ we show
        // a notice instead of the legacy Private/Team/Public toggle — which
        // would silently no-op since v0.8.5+ dropped the projects/isCollaborative
        // model the toggle wrote to.
        when {
            uiState.isAclAvailable && uiState.agentId != null -> aclSection()
            uiState.isAclAvailable && uiState.agentId == null -> {
                Text(
                    text = stringResource(Res.string.acl_create_mode_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> {
                AgentSharingSection(
                    sharingState = uiState.sharingState,
                    onSharingChange = actions.onSharingChanged,
                    showCollaborativeToggle = uiState.showCollaborativeToggle,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Chain (sequential multi-agent), gated on agents-endpoint capabilities
        if (uiState.isChainAvailable) {
            AgentChainSection(
                chainAgentIds = uiState.chainAgentIds,
                availableAgents = uiState.allAgents,
                chainMax = AGENT_CHAIN_MAX,
                onAddAgent = actions.onAddChainAgent,
                onRemoveAgent = actions.onRemoveChainAgent,
            )

            Spacer(modifier = Modifier.height(8.dp))
        }

        // Handoffs graph editor, v0.8.5+ only
        if (uiState.isHandoffsAvailable) {
            AgentHandoffsSection(
                edges = uiState.handoffEdges,
                availableAgents = uiState.allAgents,
                currentAgentId = uiState.agentId,
                onAddEdge = actions.onAddHandoffEdge,
                onUpdateEdge = actions.onUpdateHandoffEdge,
                onRemoveEdge = actions.onRemoveHandoffEdge,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 11. Advanced settings (collapsible)
        AgentAdvancedPanel(
            settings = uiState.advancedSettings,
            onSettingsChange = actions.onAdvancedSettingsChanged,
            provider = uiState.provider,
            model = uiState.model,
            extendedEffortSupported = uiState.isHandoffsAvailable,
            thinkingDisplayUpdatesSupported = uiState.isThinkingDisplayUpdatesAvailable,
            thinkingLevelXhighSupported = uiState.isThinkingLevelXhighAvailable,
            dropParamsMap = uiState.dropParamsMap,
            parameterProvider = uiState.parameterProvider,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Save button
        AdaptiveButton(
            onClick = { actions.onSave() },
            enabled = !uiState.isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (uiState.isSaving) {
                AdaptiveCircularProgressIndicator(
                    modifier = Modifier
                        .height(20.dp)
                        .width(20.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                if (uiState.isEditMode) stringResource(Res.string.save_changes) else stringResource(Res.string.create_agent),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
