package com.garfiec.librechat.feature.chat.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.garfiec.librechat.feature.chat.components.localizedStreamError
import com.garfiec.librechat.feature.chat.tchat.ALL_GROUPS
import com.garfiec.librechat.feature.chat.tchat.TchatModelPickerSheet
import com.garfiec.librechat.feature.chat.tchat.rememberTchatPicker
import com.garfiec.librechat.feature.chat.tchat.showsPinned
import com.garfiec.librechat.feature.chat.tchat.tchatPickerActions
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ChatViewModel

/*
 * The standalone model-selector sheets, shared by the Android and iOS chat screens. Unlike the
 * selector page in `ChatOptionsBottomSheet`, these open straight to the list and dismiss back to
 * the chat on selection. Both surfaces share the list body, TchatModelPickerContent (TCHAT: was
 * upstream's ModelSelectorSheetContent).
 */

/**
 * The primary standalone selector, driven by `uiState.showModelSheet` (top-bar chip, dual-pane
 * primary, send-block auto-open, which supplies [sendBlockMessage]). Renders unconditionally.
 */
// TCHAT: onNavigateToProviderKeys is kept so upstream's call site merges unchanged.
@Suppress("UnusedParameter")
@Composable
internal fun PrimaryModelSelectorSheet(
    uiState: ChatUiState,
    viewModel: ChatViewModel,
    sendBlockMessage: String?,
    onNavigateToProviderKeys: (endpointName: String?) -> Unit,
) {
    val error = uiState.error?.let { localizedStreamError(it) }
    // TCHAT: the Tchat picker replaces upstream's ModelSelectorSheet here (models by maker, Image
    // Gen as a toggle). Tchat has no user-provided keys, so there is no "Set API key" action.
    val picker = rememberTchatPicker(
        uiState = uiState,
        selectedEndpoint = uiState.selectedEndpoint,
        selectedModel = uiState.selectedModel,
        showImageGen = true,
    )
    val close = {
        // Clear a pending snackbar for the same error so it doesn't flash behind the close.
        viewModel.dismissError()
        viewModel.dismissSendBlockReason()
        viewModel.dismissModelSheet()
    }
    TchatModelPickerSheet(
        picker = picker,
        actions = tchatPickerActions(viewModel, onSelectModel = viewModel::onModelSelected),
        onDismiss = close,
        initialFilter = uiState.tchat.pickerOpenOn ?: ALL_GROUPS,
        showPinned = uiState.starredModelsDisplay.showsPinned,
        serverUrl = uiState.serverUrl,
        // Send-block reasons take precedence: when set, the sheet was auto-opened
        // to help the user resolve the block, so surface that context inline.
        errorMessage = sendBlockMessage ?: error,
        onErrorDismiss = {
            viewModel.dismissSendBlockReason()
            viewModel.dismissError()
        },
    )
}

/**
 * The secondary selector for comparison mode's second model (dual-pane button only; the composer's
 * "+" edits it through the options sheet). Renders unconditionally.
 */
@Suppress("UnusedParameter") // TCHAT: see PrimaryModelSelectorSheet
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

    // TCHAT: the Tchat picker, without Image Gen (only the primary request carries it).
    val picker = rememberTchatPicker(
        uiState = uiState,
        selectedEndpoint = uiState.comparisonState.secondaryEndpoint,
        selectedModel = uiState.comparisonState.secondaryModel,
        showImageGen = false,
    )
    TchatModelPickerSheet(
        picker = picker,
        actions = tchatPickerActions(viewModel, onSelectModel = viewModel::setSecondaryModel),
        onDismiss = onDismiss,
        showPinned = uiState.starredModelsDisplay.showsPinned,
        serverUrl = uiState.serverUrl,
    )
}
