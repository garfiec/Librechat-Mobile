package com.garfiec.librechat.feature.chat.screen

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.garfiec.librechat.core.model.config.displayLabel
import com.garfiec.librechat.feature.chat.components.ChatOptionsBottomSheet
import com.garfiec.librechat.feature.chat.components.ChatOptionsSheetController
import com.garfiec.librechat.feature.chat.components.ChatToolsPageParams
import com.garfiec.librechat.feature.chat.components.ComparisonLane
import com.garfiec.librechat.feature.chat.components.ModelParametersPageParams
import com.garfiec.librechat.feature.chat.components.ModelSelectorPageParams
import com.garfiec.librechat.feature.chat.components.OptionsSelection
import com.garfiec.librechat.feature.chat.components.localizedStreamError
import com.garfiec.librechat.feature.chat.util.keyHint
import com.garfiec.librechat.feature.chat.viewmodel.ChatActions
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState

/**
 * Hosts the chat options sheet (the "+" menu, with selector/parameters as pages) for both
 * platforms, opened through [controller]. In commonMain so the Android compile type-checks the
 * wiring iosMain shares. Callers pass their own attach actions, which are platform plumbing.
 */
@Composable
fun ChatOptionsSheetHost(
    controller: ChatOptionsSheetController,
    uiState: ChatUiState,
    actions: ChatActions,
    /**
     * True when comparison mode is on and its second tab is active, i.e. the composer is editing
     * the secondary model. Points the selector page at that model instead of the primary.
     */
    isSecondaryTab: Boolean,
    /** Model label for the tools page's Model row; the caller resolves it for the active tab. */
    selectedModelDisplay: String?,
    onAttachFiles: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickPhotos: () -> Unit,
    onAttachFromServer: () -> Unit,
    onAttachToCode: (() -> Unit)?,
    /** An MCP row's sign-in, the settings line's with the server turned on once it connects. */
    onSignInMcpServer: (String) -> Unit,
    onCancelMcpSignIn: (String) -> Unit,
    onSetUpMcpServer: (String) -> Unit,
    onOpenWebSearchKeys: () -> Unit,
    onNavigateToProviderKeys: (endpointName: String?) -> Unit,
    onShowSavePresetDialog: () -> Unit,
    /** Compare setup's staged picks: during setup the Model page stages into them. */
    compareSetupPick: CompareSetupPick,
    snackbarHostState: SnackbarHostState,
) {
    // A send-block auto-opens the standalone selector; close this one so they can't stack. Gate on
    // the flag (not just its change) to also catch it being already true when this sheet opens.
    LaunchedEffect(uiState.showModelSheet) {
        if (uiState.showModelSheet) controller.close()
    }
    val page = controller.openPage
    if (page == null || uiState.showModelSheet) return

    // The parameter set the selection resolves to (an agent's provider's, a custom endpoint's type's),
    // the same one the send payload is built from. Remembered: `uiState` emits per SSE delta.
    val parameterTarget =
        remember(uiState.agents, uiState.selectedModel, uiState.selectedEndpoint, uiState.endpointConfigs) {
            uiState.parameterTarget
        }

    val error = uiState.error?.let { localizedStreamError(it) }
    val optionsSelection = rememberOptionsSelection(uiState, selectedModelDisplay)
    val comparison = rememberComparisonSelectorRouting(
        uiState = uiState,
        initialTarget = if (isSecondaryTab) ComparisonLane.B else ComparisonLane.A,
        onSelectPrimary = actions::onModelSelected,
        onSelectSecondary = actions::setSecondaryModel,
        setupPick = compareSetupPick,
        onSelectPrimarySpec = actions::onSpecSelected,
        onSelectSecondarySpec = actions::setSecondarySpec,
    )

    ChatOptionsBottomSheet(
        page = page,
        entryPage = controller.entryPage ?: page,
        onPageChange = controller::open,
        onDismiss = controller::close,
        snackbarHostState = snackbarHostState,
        tools = ChatToolsPageParams(
            focus = controller.focus,
            enabledTools = uiState.effectiveEnabledTools,
            onToggleTool = actions::toggleTool,
            artifactsMode = uiState.selection.artifactsMode,
            onSetArtifactsMode = actions::setArtifactsMode,
            mcpServers = uiState.offeredMcpServers,
            selectedMcpServerNames = uiState.effectiveMcpServerNames,
            onToggleMcpServer = actions::toggleMcpServer,
            onAttachFiles = onAttachFiles,
            onTakePhoto = onTakePhoto,
            onPickPhotos = onPickPhotos,
            onAttachFromServer = onAttachFromServer,
            onAttachToCode = onAttachToCode,
            selectedModelDisplay = selectedModelDisplay,
            selection = optionsSelection,
            toolsHiddenReason = uiState.toolsHiddenReason,
            comparisonModels = comparisonModelLabels(uiState),
            openTools = uiState.sheetTools,
            webSearchKind = uiState.webSearchKind,
            mcpServersEnabled = uiState.mcpServersEnabled,
            gates = uiState.chatInputGates,
            contextGauge = uiState.contextGaugeDetails,
            contextUsageEnabled = uiState.contextUsageEnabled,
            onCompact = actions::compactConversation.takeIf { uiState.canCompactNow },
            onSnoozeCompact = actions::snoozeCompactNudge,
            contextBarPlacement = uiState.contextBarPlacement,
            contextGaugeExpanded = uiState.contextGaugeExpanded,
            onContextGaugeExpandedChange = actions::setContextGaugeExpanded,
            onSignInMcpServer = onSignInMcpServer,
            onCancelMcpSignIn = onCancelMcpSignIn,
            onSetUpMcpServer = onSetUpMcpServer,
            webSearchKeys = uiState.webSearchKeysRow,
            onOpenWebSearchKeys = onOpenWebSearchKeys,
        ),
        // While comparing, the page edits whichever lane its A/B pair picks, starting on the active
        // tab's; the sheet itself needs no comparison branch.
        selector = ModelSelectorPageParams(
            modelSelect = uiState.modelSelectEnabled,
            modelSpecs = uiState.modelSpecs,
            selectedSpec = comparison.selectedSpec,
            onSpecSelect = comparison.onSpecSelect,
            endpointConfigs = uiState.endpointConfigs,
            availableModels = uiState.availableModels,
            agents = uiState.agents,
            agentsLoaded = uiState.agentsLoaded,
            selectedEndpoint = comparison.selectedEndpoint,
            selectedModel = comparison.selectedModel,
            // Neither branch clears the error: the sheet clears the selector's inline banner on
            // page-leave itself (see ChatOptionsBottomSheet), the only error that can flash.
            onModelSelect = comparison.onModelSelect,
            comparisonHeader = comparison.header,
            onSetApiKey = { name -> onNavigateToProviderKeys(name) },
            onSurfaced = actions::prepareModelSelector,
            // Inline because the Scaffold snackbar draws behind the sheet scrim.
            errorMessage = error,
            onErrorDismiss = actions::dismissError,
            serverUrl = uiState.serverUrl,
            favoriteAgentIds = uiState.favoriteAgentIds,
            favoriteModelKeys = uiState.favoriteModelKeys,
            favoriteSpecNames = uiState.favoriteSpecNames,
            onToggleAgentFavorite = actions::toggleAgentFavorite,
            onToggleModelFavorite = actions::toggleModelFavorite,
            onToggleSpecFavorite = actions::toggleSpecFavorite,
            starredDisplay = uiState.starredModelsDisplay,
            endpointKeyStates = uiState.endpointKeyStates,
        ),
        // Always the primary model, even on the secondary tab: ComparisonState carries no per-pane
        // parameters, so there is nothing else to point this at (pre-existing; porting web's
        // per-addedConvo parameters would be the real fix).
        parameters = ModelParametersPageParams(
            parameters = uiState.selection.sendParameters,
            onParametersChange = actions::updateModelParameters,
            selectedEndpoint = parameterTarget.settingsEndpoint,
            extendedEffortSupported = uiState.extendedEffortSupported,
            thinkingDisplayUpdatesSupported = uiState.thinkingDisplayUpdatesSupported,
            thinkingLevelXhighSupported = uiState.thinkingLevelXhighSupported,
            selectedProvider = parameterTarget.settingsProvider,
            selectedModel = parameterTarget.model,
            // Remembered: `uiState` emits per SSE delta, so an open sheet recomposes once per
            // token and this would walk the map and allocate a fresh list every time, for a value
            // that only moves when the endpoint, model or config does. `AgentAdvancedPanel` wraps
            // the identical call the same way.
            dropParams = remember(uiState.gates.dropParamsMap, parameterTarget) {
                parameterTarget.dropParams(uiState.gates.dropParamsMap)
            },
            onSaveAsPreset = {
                controller.close()
                onShowSavePresetDialog()
            },
            setBySpec = uiState.parametersSetBySpec,
            comparing = uiState.comparisonState.isEnabled,
        ),
    )
}

/** Remembered on exactly the inputs it reads: `uiState` emits per SSE delta and per parameter edit. */
@Composable
internal fun rememberOptionsSelection(uiState: ChatUiState, title: String?): OptionsSelection {
    val selection = uiState.selection
    return remember(
        selection.choice,
        selection.modelSpecs,
        selection.endpointConfigs,
        selection.endpointKeyStates,
        selection.agents,
        uiState.serverUrl,
        title,
    ) {
        val spec = uiState.activeSpec
        OptionsSelection(
            modelIcon = uiState.selectionIcon(),
            modelSubtitle = selectionModelSubtitle(uiState.selectedEndpoint, uiState.selectedModel, uiState.agents, title),
            modelKeyHint = uiState.endpointKeyStates[uiState.selectedEndpoint].keyHint(),
            lockedBy = spec?.displayLabel?.takeIf(String::isNotBlank),
        )
    }
}
