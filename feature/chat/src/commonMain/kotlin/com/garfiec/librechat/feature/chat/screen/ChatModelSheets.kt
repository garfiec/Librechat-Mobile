package com.garfiec.librechat.feature.chat.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.garfiec.librechat.feature.chat.components.ComparisonLane
import com.garfiec.librechat.feature.chat.components.ModelSelectorSheet
import com.garfiec.librechat.feature.chat.components.localizedStreamError
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ChatViewModel

/*
 * The standalone model-selector sheets, shared by the Android and iOS chat screens. Unlike the
 * selector page in `ChatOptionsBottomSheet`, these open straight to the list and dismiss back to
 * the chat on selection. Both surfaces share the list body, ModelSelectorSheetContent. While
 * comparing, both show the A/B pair and edit whichever lane is picked there, and during Compare
 * setup the primary one stages instead (see [rememberComparisonSelectorRouting]).
 */

/**
 * The primary standalone selector, driven by `uiState.showModelSheet` (top-bar chip, dual-pane
 * primary, send-block auto-open, which supplies [sendBlockMessage]). Renders unconditionally.
 */
@Composable
internal fun PrimaryModelSelectorSheet(
    uiState: ChatUiState,
    viewModel: ChatViewModel,
    sendBlockMessage: String?,
    onNavigateToProviderKeys: (endpointName: String?) -> Unit,
    /** Compare setup's staged picks: during setup the list stages into them (see the routing). */
    setupPick: CompareSetupPick? = null,
) {
    val error = uiState.error?.let { localizedStreamError(it) }
    val comparison = rememberComparisonSelectorRouting(
        uiState = uiState,
        initialTarget = ComparisonLane.A,
        onSelectPrimary = viewModel::onModelSelected,
        onSelectSecondary = viewModel::setSecondaryModel,
        setupPick = setupPick,
    )
    ModelSelectorSheet(
        endpointConfigs = uiState.endpointConfigs,
        availableModels = uiState.availableModels,
        agents = uiState.agents,
        selectedEndpoint = comparison.selectedEndpoint,
        selectedModel = comparison.selectedModel,
        onModelSelect = { endpoint, model ->
            comparison.onModelSelect(endpoint, model)
            // Clear a pending snackbar for the same error so it doesn't flash behind the close.
            viewModel.dismissError()
            viewModel.dismissSendBlockReason()
            viewModel.dismissModelSheet()
        },
        onDismiss = {
            viewModel.dismissError()
            viewModel.dismissSendBlockReason()
            viewModel.dismissModelSheet()
        },
        serverUrl = uiState.serverUrl,
        // Send-block reasons take precedence: when set, the sheet was auto-opened
        // to help the user resolve the block, so surface that context inline.
        errorMessage = sendBlockMessage ?: error,
        onErrorDismiss = {
            viewModel.dismissSendBlockReason()
            viewModel.dismissError()
        },
        favoriteAgentIds = uiState.favoriteAgentIds,
        favoriteModelKeys = uiState.favoriteModelKeys,
        onToggleAgentFavorite = viewModel::toggleAgentFavorite,
        onToggleModelFavorite = viewModel::toggleModelFavorite,
        starredDisplay = uiState.starredModelsDisplay,
        endpointKeyStates = uiState.endpointKeyStates,
        onSetApiKey = { name -> onNavigateToProviderKeys(name) },
        header = comparison.header,
    )
}

/**
 * The selector opened for comparison mode's second model (pane B's header, the phone chip on the A
 * tab). Opens on lane B. Renders unconditionally.
 */
@Composable
internal fun SecondaryModelSelectorSheet(
    uiState: ChatUiState,
    viewModel: ChatViewModel,
    onDismiss: () -> Unit,
    onNavigateToProviderKeys: (endpointName: String?) -> Unit,
) {
    // Route through prepareModelSelector like every selector path, so a failed cold-start agent
    // load can self-heal here too (see its KDoc).
    LaunchedEffect(Unit) { viewModel.prepareModelSelector() }

    val comparison = rememberComparisonSelectorRouting(
        uiState = uiState,
        initialTarget = ComparisonLane.B,
        onSelectPrimary = viewModel::onModelSelected,
        onSelectSecondary = viewModel::setSecondaryModel,
    )
    ModelSelectorSheet(
        endpointConfigs = uiState.endpointConfigs,
        availableModels = uiState.availableModels,
        agents = uiState.agents,
        selectedEndpoint = comparison.selectedEndpoint,
        selectedModel = comparison.selectedModel,
        onModelSelect = { endpoint, model ->
            comparison.onModelSelect(endpoint, model)
            onDismiss()
        },
        onDismiss = onDismiss,
        serverUrl = uiState.serverUrl,
        favoriteAgentIds = uiState.favoriteAgentIds,
        favoriteModelKeys = uiState.favoriteModelKeys,
        onToggleAgentFavorite = viewModel::toggleAgentFavorite,
        onToggleModelFavorite = viewModel::toggleModelFavorite,
        starredDisplay = uiState.starredModelsDisplay,
        endpointKeyStates = uiState.endpointKeyStates,
        onSetApiKey = { name -> onNavigateToProviderKeys(name) },
        header = comparison.header,
    )
}
