package com.garfiec.librechat.feature.agents.screen

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import com.garfiec.librechat.core.ui.components.AdaptiveScaffold
import com.garfiec.librechat.core.ui.components.AdaptiveSnackbarHost
import com.garfiec.librechat.core.ui.components.LoadingIndicator
import com.garfiec.librechat.core.ui.components.PdfPasswordDialog
import com.garfiec.librechat.feature.agents.components.AgentAvatarTile
import com.garfiec.librechat.feature.agents.components.model.MarketplaceItem
import com.garfiec.librechat.feature.agents.viewmodel.AgentCodeSectionView
import com.garfiec.librechat.feature.agents.viewmodel.AgentEditorUiState
import com.garfiec.librechat.feature.agents.viewmodel.InstructionsPromptView
import com.garfiec.librechat.feature.agents.viewmodel.McpToolRowDisplay

/**
 * The agent editor: its dialogs, top bar and form. [toolCatalog] and [selectedToolKeys] feed the tools
 * picker while it is open. [avatarPicker] draws the avatar (the platform's image picker; without one,
 * the agent's initial). [aclSection] is the sharing section, drawn only for a saved agent on a server
 * with ACL sharing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentEditorContent(
    uiState: AgentEditorUiState,
    codeSection: AgentCodeSectionView?,
    instructionsPromptView: InstructionsPromptView?,
    mcpRows: List<McpToolRowDisplay>,
    toolCatalog: List<MarketplaceItem>,
    selectedToolKeys: Set<String>,
    actions: AgentEditorActions,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    avatarPicker: @Composable (avatarUrl: String?, agentName: String) -> Unit = { _, agentName ->
        AgentAvatarTile(agentName = agentName)
    },
    aclSection: @Composable () -> Unit = {},
) {
    uiState.pdfPasswordPrompt?.let { prompt ->
        // Keyed so a re-prompt after a wrong password starts from an empty field.
        key(prompt) {
            PdfPasswordDialog(
                filename = prompt.filename,
                incorrectPassword = prompt.incorrectPassword,
                onSubmit = actions.onSubmitPdfPassword,
                onDismiss = actions.onDismissPdfPassword,
            )
        }
    }

    AgentEditorDialogs(
        uiState = uiState,
        toolCatalog = toolCatalog,
        selectedToolKeys = selectedToolKeys,
        actions = actions,
    )

    AdaptiveScaffold(
        modifier = modifier,
        snackbarHost = { AdaptiveSnackbarHost(snackbarHostState) },
        topBar = {
            AgentEditorTopBar(
                isEditMode = uiState.isEditMode,
                onBack = actions.onBackRequested,
                onDuplicate = actions.onShowDuplicateConfirmation,
                onVersionHistory = actions.onShowVersionHistory,
                onDelete = actions.onShowDeleteConfirmation,
            )
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> {
                LoadingIndicator(modifier = Modifier.padding(innerPadding))
            }

            else -> {
                AgentEditorForm(
                    uiState = uiState,
                    codeSection = codeSection,
                    instructionsPromptView = instructionsPromptView,
                    mcpRows = mcpRows,
                    actions = actions,
                    avatarPicker = avatarPicker,
                    aclSection = aclSection,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
            }
        }
    }
}
