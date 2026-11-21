package com.garfiec.librechat.feature.agents.screen

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.garfiec.librechat.core.ui.components.AdaptiveAlertDialog
import com.garfiec.librechat.core.ui.components.DiscardChangesDialog
import com.garfiec.librechat.core.ui.websearch.WebSearchKeysSheet
import com.garfiec.librechat.feature.agents.components.AgentVersionHistory
import com.garfiec.librechat.feature.agents.components.ToolAuthDialog
import com.garfiec.librechat.feature.agents.components.ToolsMarketplaceDialog
import com.garfiec.librechat.feature.agents.components.model.MarketplaceItem
import com.garfiec.librechat.feature.agents.resources.*
import com.garfiec.librechat.feature.agents.resources.Res
import com.garfiec.librechat.feature.agents.viewmodel.AgentEditorUiState
import com.garfiec.librechat.feature.agents.viewmodel.ToolAuthState
import org.jetbrains.compose.resources.stringResource

/**
 * The editor's modal layer: discard/delete/duplicate confirmations, version history,
 * tool selection, and the Code Interpreter API-key dialog. Each is gated on its
 * own state flag, so this composable renders nothing when no dialog is active.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AgentEditorDialogs(
    uiState: AgentEditorUiState,
    toolCatalog: List<MarketplaceItem>,
    selectedToolKeys: Set<String>,
    actions: AgentEditorActions,
) {
    if (uiState.showDiscardConfirm) {
        DiscardChangesDialog(
            onDiscard = actions.onDiscardChanges,
            onDismiss = actions.onDismissDiscardConfirmation,
        )
    }

    // Delete confirmation dialog
    if (uiState.showDeleteConfirm) {
        AdaptiveAlertDialog(
            onDismissRequest = actions.onDismissDeleteConfirmation,
            title = { Text(stringResource(Res.string.delete_agent)) },
            text = { Text(stringResource(Res.string.delete_agent_editor_confirm)) },
            confirmButton = {
                TextButton(onClick = actions.onDelete) {
                    Text(stringResource(Res.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = actions.onDismissDeleteConfirmation) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }

    // Duplicate confirmation dialog
    if (uiState.showDuplicateConfirm) {
        AdaptiveAlertDialog(
            onDismissRequest = actions.onDismissDuplicateConfirmation,
            title = { Text(stringResource(Res.string.duplicate_agent)) },
            text = { Text(stringResource(Res.string.duplicate_agent_confirm)) },
            confirmButton = {
                TextButton(onClick = actions.onDuplicate) {
                    Text(stringResource(Res.string.duplicate))
                }
            },
            dismissButton = {
                TextButton(onClick = actions.onDismissDuplicateConfirmation) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }

    // Version history sheet
    if (uiState.showVersionHistory) {
        AgentVersionHistory(
            versions = uiState.versions,
            onRevert = actions.onRevertToVersion,
            onDismiss = actions.onDismissVersionHistory,
            isLoading = uiState.isLoadingVersions,
        )
    }

    // Unified tools picker (capabilities + plugin tools + MCP + skills, v0.8.8)
    if (uiState.showToolsMarketplace) {
        ToolsMarketplaceDialog(
            catalog = toolCatalog,
            selectedKeys = selectedToolKeys,
            favoriteKeys = uiState.favoriteToolKeys,
            favoritesSupported = uiState.areToolFavoritesSupported,
            query = uiState.marketplaceQuery,
            filter = uiState.marketplaceFilter,
            onQueryChange = actions.onMarketplaceQueryChanged,
            onFilterChange = actions.onMarketplaceFilterChanged,
            onToggleItem = actions.onMarketplaceItemToggled,
            onToggleFavorite = actions.onMarketplaceFavoriteToggled,
            onDismiss = actions.onCloseToolsMarketplace,
        )
    }

    val webSearchKeys = uiState.webSearchKeys
    if (uiState.showWebSearchKeysSheet && webSearchKeys != null) {
        WebSearchKeysSheet(
            keys = webSearchKeys,
            onSave = actions.onSaveWebSearchKeys,
            onRemove = actions.onRemoveWebSearchKeys,
            onDismiss = actions.onDismissWebSearchKeys,
        )
    }

    // Code Interpreter API key dialog
    if (uiState.showCodeAuthDialog) {
        val alreadyAuthed = uiState.codeToolAuthState == ToolAuthState.UserProvided
        ToolAuthDialog(
            title = stringResource(Res.string.tool_auth_code_title),
            fieldLabel = stringResource(Res.string.tool_auth_code_field),
            description = stringResource(Res.string.tool_auth_code_description),
            isAlreadyAuthenticated = alreadyAuthed,
            onSubmit = actions.onSubmitCodeToolApiKey,
            onRevoke = actions.onRevokeCodeToolApiKey,
            onDismiss = actions.onDismissCodeToolAuthDialog,
        )
    }
}
