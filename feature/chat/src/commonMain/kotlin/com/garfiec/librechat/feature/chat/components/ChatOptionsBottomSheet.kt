package com.garfiec.librechat.feature.chat.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.common.ToolConstants
import com.garfiec.librechat.core.data.datastore.ContextBarPlacement
import com.garfiec.librechat.core.data.datastore.StarredModelsDisplay
import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.ArtifactsMode
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.config.ModelSpecs
import com.garfiec.librechat.core.model.endpoint.KeyState
import com.garfiec.librechat.core.ui.artifacts.ArtifactsModeChoices
import com.garfiec.librechat.core.ui.components.AdaptiveModalBottomSheet
import com.garfiec.librechat.core.ui.components.AdaptiveSnackbarHost
import com.garfiec.librechat.core.ui.components.LowProfileDragHandle
import com.garfiec.librechat.core.ui.components.ModelParameterContent
import com.garfiec.librechat.core.ui.components.ModelParameters
import com.garfiec.librechat.core.ui.components.PlatformBackHandler
import com.garfiec.librechat.core.ui.components.clearFocusOnTap
import com.garfiec.librechat.core.ui.resources.artifacts_mode_intro
import com.garfiec.librechat.core.ui.resources.artifacts_mode_title
import com.garfiec.librechat.feature.chat.model.McpServerDisplayData
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.cd_back
import com.garfiec.librechat.feature.chat.resources.param_set_by_spec
import com.garfiec.librechat.feature.chat.resources.params_title_lane_a
import com.garfiec.librechat.feature.chat.resources.tool_model
import com.garfiec.librechat.feature.chat.resources.tool_model_parameters
import com.garfiec.librechat.feature.chat.util.ToolsSheetFocus
import com.garfiec.librechat.feature.chat.util.WebSearchKeysRow
import com.garfiec.librechat.feature.chat.util.WebSearchKind
import com.garfiec.librechat.feature.chat.util.WebSearchRoute
import com.garfiec.librechat.feature.chat.viewmodel.ChatInputGates
import com.garfiec.librechat.feature.chat.viewmodel.ContextGaugeDetails
import com.garfiec.librechat.feature.chat.viewmodel.ToolsHiddenReason
import org.jetbrains.compose.resources.stringResource
import com.garfiec.librechat.core.ui.resources.Res as CoreUiRes

/** Duration of the page slide + resize. */
private const val PageSwapMillis = 250

/** Sub-page height floor if the sheet ever measures unbounded (the selector's list needs bounds). */
private val SubPageFallbackHeight = 560.dp

/** Everything the Options (tools/attachment) page renders. See [ChatToolsSheetContent]. */
@Immutable
data class ChatToolsPageParams(
    /** The row the page opens at (a settings chip's tap), scrolled into view and marked. */
    val focus: ToolsSheetFocus? = null,
    val enabledTools: Set<String>,
    val onToggleTool: (String) -> Unit,
    val mcpServers: List<McpServerDisplayData>,
    val selectedMcpServerNames: Set<String>,
    val onToggleMcpServer: (String) -> Unit,
    val onAttachFiles: () -> Unit,
    val onTakePhoto: () -> Unit,
    val onPickPhotos: () -> Unit,
    val onAttachFromServer: () -> Unit,
    /** "Upload to Code Environment" (legacy upload UX); null hides it. */
    val onAttachToCode: (() -> Unit)? = null,
    val selectedModelDisplay: String?,
    val selection: OptionsSelection = OptionsSelection(),
    /** Why the tool controls are hidden; a note takes their place. */
    val toolsHiddenReason: ToolsHiddenReason? = null,
    /** Both models while comparing; the Model row then names A and B. */
    val comparisonModels: ComparisonModelLabels? = null,
    /** The tools the sheet offers a row for ([ChatUiState.openTools]). */
    val openTools: Set<String> = emptySet(),
    val webSearchKind: WebSearchKind = WebSearchKind(WebSearchRoute.LIBRECHAT, null),
    val mcpServersEnabled: Boolean = true,
    val gates: ChatInputGates = ChatInputGates(),
    val contextGauge: ContextGaugeDetails? = null,
    val contextUsageEnabled: Boolean = false,
    /** Null when compaction is unavailable — see [ChatUiState.canCompactNow]. */
    val onCompact: (() -> Unit)? = null,
    /** "Not now" on a compact suggestion. */
    val onSnoozeCompact: () -> Unit = {},
    val contextBarPlacement: ContextBarPlacement = ContextBarPlacement.OPTIONS_SHEET,
    val contextGaugeExpanded: Boolean = false,
    val onContextGaugeExpandedChange: (Boolean) -> Unit = {},
    /** An MCP row's "Sign in" (the server turns on once it connects) and "Cancel sign-in". */
    val onSignInMcpServer: ((String) -> Unit)? = null,
    val onCancelMcpSignIn: ((String) -> Unit)? = null,
    /** An MCP row's "Set up": its variables sheet. */
    val onSetUpMcpServer: ((String) -> Unit)? = null,
    /** The LibreChat search key row; null hides it. */
    val webSearchKeys: WebSearchKeysRow? = null,
    val onOpenWebSearchKeys: () -> Unit = {},
    val artifactsMode: ArtifactsMode = ArtifactsMode.DEFAULT,
    val onSetArtifactsMode: (ArtifactsMode) -> Unit = {},
)

/**
 * Everything the ModelSelector page renders. In comparison mode the host points the selection
 * fields at whichever lane [comparisonHeader]'s A/B pair is editing, so this page is agnostic to
 * which tab is active.
 */
@Immutable
data class ModelSelectorPageParams(
    val endpointConfigs: Map<String, EndpointConfig>,
    val availableModels: Map<String, List<String>>,
    val agents: List<Agent>,
    val selectedEndpoint: String?,
    val selectedModel: String?,
    val onModelSelect: (endpoint: String, model: String) -> Unit,
    /** `interface.modelSelect`; off lists only the server's specs. */
    val modelSelect: Boolean,
    val agentsLoaded: Boolean = false,
    val modelSpecs: ModelSpecs? = null,
    val selectedSpec: String? = null,
    val onSpecSelect: (ModelSpec) -> Unit = {},
    val onSetApiKey: (endpointName: String) -> Unit,
    /** Fired on each surfacing; hosts route it to `ChatViewModel.prepareModelSelector()`. */
    val onSurfaced: () -> Unit = {},
    /** Inline error; required because the Scaffold snackbar draws behind the sheet scrim. */
    val errorMessage: String? = null,
    val onErrorDismiss: () -> Unit = {},
    val serverUrl: String = "",
    val favoriteAgentIds: Set<String> = emptySet(),
    val favoriteModelKeys: Set<String> = emptySet(),
    val favoriteSpecNames: Set<String> = emptySet(),
    val onToggleAgentFavorite: ((agentId: String) -> Unit)? = null,
    val onToggleModelFavorite: ((endpoint: String, model: String) -> Unit)? = null,
    val onToggleSpecFavorite: ((name: String) -> Unit)? = null,
    val starredDisplay: StarredModelsDisplay = StarredModelsDisplay.GROUPED,
    val endpointKeyStates: Map<String, KeyState> = emptyMap(),
    /** Under the page's back row; the comparison A/B pair while comparing. */
    val comparisonHeader: (@Composable () -> Unit)? = null,
)

/** Everything the ModelParameters page renders. See [ModelParameterContent]. */
@Immutable
data class ModelParametersPageParams(
    val parameters: ModelParameters,
    val onParametersChange: (ModelParameters) -> Unit,
    val selectedEndpoint: String = "",
    val extendedEffortSupported: Boolean = false,
    val thinkingDisplayUpdatesSupported: Boolean = false,
    /** Underlying provider when the endpoint is "agents"; routes to that provider's param set. */
    val selectedProvider: String? = null,
    val selectedModel: String? = null,
    /** Resolved `/api/config.endpointsDropParamsMap` entry; see [ModelParameterContent]. */
    val dropParams: List<String> = emptyList(),
    val onSaveAsPreset: () -> Unit = {},
    /** The server sets the parameters (an enforced spec): the page shows them read-only. */
    val setBySpec: Boolean = false,
    /** Comparing: the page edits Model A's parameters on either tab, and its title says so. */
    val comparing: Boolean = false,
)

/**
 * The chat options sheet: one [AdaptiveModalBottomSheet] that swaps between the tools menu and the model
 * selector / parameters, so choosing a model returns to the Options page rather than the chat. The
 * standalone selector (top-bar chip, send-block, dual-pane) lives in `ChatModelSheets.kt`. Opened
 * via [ChatOptionsSheetController].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatOptionsBottomSheet(
    page: ChatOptionsPage,
    /** The page the sheet opened on: Back there dismisses, and a model pick returns to Options only from Options. */
    entryPage: ChatOptionsPage,
    onPageChange: (ChatOptionsPage) -> Unit,
    onDismiss: () -> Unit,
    tools: ChatToolsPageParams,
    selector: ModelSelectorPageParams,
    parameters: ModelParametersPageParams,
    /**
     * The chat's snackbar, shown again inside the sheet: the sheet is its own window, so the chat's
     * host draws under it and a notice raised from the sheet (a failed MCP sign-in) would be unseen.
     */
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    // Held here, not in ChatToolsSheetContent: AnimatedContent drops the Options page from
    // composition on a sub-page, so state remembered there would reset. Saveable to survive the
    // host teardown the page survives (the "Add key" round trip, config change).
    var mcpExpanded by rememberSaveable { mutableStateOf(false) }
    val optionsScrollState = rememberScrollState()
    // Or the Options page — the common path — would open at half height.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // On every arrival at the selector. Not latched: both side effects are idempotent (the agent
    // retry self-guards; the favourites refetch conflates by equality), matching the pre-refactor
    // "every open self-heals" behaviour.
    LaunchedEffect(page) {
        if (page == ChatOptionsPage.ModelSelector) {
            selector.onSurfaced()
        }
    }

    // Clear the selector's inline error when leaving that page, so its snackbar (drawn behind the
    // scrim) doesn't re-surface after close. Scoped to the selector: an Options-page error must
    // reach the snackbar untouched. onErrorDismiss no-ops when there's no error.
    val leaveSelectorError: () -> Unit = {
        if (page == ChatOptionsPage.ModelSelector) selector.onErrorDismiss()
    }
    val goTo: (ChatOptionsPage) -> Unit = { target ->
        leaveSelectorError()
        onPageChange(target)
    }
    val dismiss: () -> Unit = {
        leaveSelectorError()
        onDismiss()
    }
    val back: () -> Unit = {
        if (page == entryPage) dismiss() else goTo(ChatOptionsPage.Options)
    }
    val afterPick: () -> Unit = {
        if (entryPage == ChatOptionsPage.Options) goTo(ChatOptionsPage.Options) else dismiss()
    }

    AdaptiveModalBottomSheet(
        onDismissRequest = dismiss,
        modifier = modifier.clearFocusOnTap(),
        sheetState = sheetState,
        dragHandle = { LowProfileDragHandle() },
    ) {
        // Inside the sheet: on Android the content is its own dialog window, so an outer handler
        // never sees its back events. Takes precedence over Material3's back-to-dismiss so a
        // sub-page pops to Options. Never fires on iOS — hence the header back arrows.
        PlatformBackHandler(enabled = page != entryPage, onBack = back)

        BoxWithConstraints {
            // Sub-pages need a bounded height for the selector's weight(1f) LazyColumn; Options and
            // Parameters wrap their content and SizeTransform animates between the heights.
            val subPageHeight = if (constraints.hasBoundedHeight) maxHeight else SubPageFallbackHeight

            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    // Forward slides in from the right, back from the left, reading as depth.
                    val direction = if (initialState == ChatOptionsPage.Options) 1 else -1
                    val spec = tween<Float>(PageSwapMillis)
                    (
                        slideInHorizontally(tween(PageSwapMillis)) { width -> direction * width } +
                            fadeIn(spec)
                        ).togetherWith(
                        slideOutHorizontally(tween(PageSwapMillis)) { width -> -direction * width } +
                            fadeOut(spec),
                    ).using(SizeTransform { _, _ -> tween(PageSwapMillis) })
                },
                label = "chatOptionsPage",
            ) { currentPage ->
                when (currentPage) {
                    ChatOptionsPage.Options -> ChatToolsSheetContent(
                        enabledTools = tools.enabledTools,
                        onToggleTool = tools.onToggleTool,
                        mcpServers = tools.mcpServers,
                        selectedMcpServerNames = tools.selectedMcpServerNames,
                        onToggleMcpServer = tools.onToggleMcpServer,
                        onAttachFiles = tools.onAttachFiles,
                        onTakePhoto = tools.onTakePhoto,
                        onPickPhotos = tools.onPickPhotos,
                        onAttachFromServer = tools.onAttachFromServer,
                        onAttachToCode = tools.onAttachToCode,
                        onOpenModelParameters = { goTo(ChatOptionsPage.ModelParameters) },
                        onOpenModelSelector = { goTo(ChatOptionsPage.ModelSelector) },
                        selectedModelDisplay = tools.selectedModelDisplay,
                        selection = tools.selection,
                        toolsHiddenReason = tools.toolsHiddenReason,
                        comparisonModels = tools.comparisonModels,
                        onDismiss = dismiss,
                        openTools = tools.openTools,
                        webSearchKind = tools.webSearchKind,
                        mcpServersEnabled = tools.mcpServersEnabled,
                        gates = tools.gates,
                        contextGauge = tools.contextGauge,
                        contextUsageEnabled = tools.contextUsageEnabled,
                        onCompact = tools.onCompact,
                        onSnoozeCompact = tools.onSnoozeCompact,
                        contextBarPlacement = tools.contextBarPlacement,
                        contextGaugeExpanded = tools.contextGaugeExpanded,
                        onContextGaugeExpandedChange = tools.onContextGaugeExpandedChange,
                        mcpExpanded = mcpExpanded,
                        onMcpExpandedChange = { mcpExpanded = it },
                        scrollState = optionsScrollState,
                        focus = tools.focus,
                        onSignInMcpServer = tools.onSignInMcpServer,
                        onCancelMcpSignIn = tools.onCancelMcpSignIn,
                        onSetUpMcpServer = tools.onSetUpMcpServer,
                        webSearchKeys = tools.webSearchKeys,
                        onOpenWebSearchKeys = tools.onOpenWebSearchKeys,
                        artifactsMode = tools.artifactsMode,
                        onOpenArtifactsMode = { goTo(ChatOptionsPage.ArtifactsMode) },
                    )

                    ChatOptionsPage.ModelSelector -> ModelSelectorSheetContent(
                        endpointConfigs = selector.endpointConfigs,
                        availableModels = selector.availableModels,
                        agents = selector.agents,
                        selectedEndpoint = selector.selectedEndpoint,
                        selectedModel = selector.selectedModel,
                        modelSelect = selector.modelSelect,
                        selectedSpec = selector.selectedSpec,
                        modelSpecs = selector.modelSpecs,
                        agentsLoaded = selector.agentsLoaded,
                        onSpecSelect = { spec ->
                            selector.onSpecSelect(spec)
                            afterPick()
                        },
                        onModelSelect = { endpoint, model ->
                            selector.onModelSelect(endpoint, model)
                            afterPick()
                        },
                        onSetApiKey = selector.onSetApiKey,
                        modifier = Modifier.height(subPageHeight),
                        serverUrl = selector.serverUrl,
                        errorMessage = selector.errorMessage,
                        onErrorDismiss = selector.onErrorDismiss,
                        favoriteAgentIds = selector.favoriteAgentIds,
                        favoriteModelKeys = selector.favoriteModelKeys,
                        favoriteSpecNames = selector.favoriteSpecNames,
                        onToggleAgentFavorite = selector.onToggleAgentFavorite,
                        onToggleModelFavorite = selector.onToggleModelFavorite,
                        onToggleSpecFavorite = selector.onToggleSpecFavorite,
                        starredDisplay = selector.starredDisplay,
                        endpointKeyStates = selector.endpointKeyStates,
                        showTitle = false,
                        header = {
                            Column {
                                SheetPageBackRow(title = stringResource(Res.string.tool_model), onBack = back)
                                selector.comparisonHeader?.invoke()
                            }
                        },
                    )

                    ChatOptionsPage.ModelParameters -> Column {
                        SheetPageBackRow(
                            title = stringResource(
                                if (parameters.comparing) Res.string.params_title_lane_a else Res.string.tool_model_parameters,
                            ),
                            onBack = back,
                        )
                        ModelParameterContent(
                            parameters = parameters.parameters,
                            onParametersChange = parameters.onParametersChange,
                            selectedEndpoint = parameters.selectedEndpoint,
                            extendedEffortSupported = parameters.extendedEffortSupported,
                            thinkingDisplayUpdatesSupported = parameters.thinkingDisplayUpdatesSupported,
                            selectedProvider = parameters.selectedProvider,
                            selectedModel = parameters.selectedModel,
                            dropParams = parameters.dropParams,
                            onSaveAsPreset = parameters.onSaveAsPreset,
                            readOnlyNote = if (parameters.setBySpec) {
                                stringResource(Res.string.param_set_by_spec)
                            } else {
                                null
                            },
                            // The back row above replaces the content's own title.
                            showHeader = false,
                            // The Web Search and URL Context tool tiles are the only switches for those.
                            hiddenKeys = setOf(ToolConstants.WEB_SEARCH, ToolConstants.URL_CONTEXT),
                            modifier = Modifier
                                .padding(horizontal = 24.dp)
                                .navigationBarsPadding()
                                .imePadding(),
                        )
                    }

                    ChatOptionsPage.ArtifactsMode -> Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .navigationBarsPadding()
                            .padding(bottom = 16.dp),
                    ) {
                        SheetPageBackRow(title = stringResource(CoreUiRes.string.artifacts_mode_title), onBack = back)
                        Text(
                            text = stringResource(CoreUiRes.string.artifacts_mode_intro),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        )
                        ArtifactsModeChoices(
                            selected = tools.artifactsMode,
                            onSelect = { mode ->
                                tools.onSetArtifactsMode(mode)
                                afterPick()
                            },
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                    }
                }
            }
            // The selector shows its error inline; the snackbar there would repeat it.
            if (page != ChatOptionsPage.ModelSelector) {
                AdaptiveSnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

/** A sub-page's back affordance — the only way back on iOS, where `PlatformBackHandler` is inert. */
@Composable
private fun SheetPageBackRow(
    title: String?,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(start = 4.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(Res.string.cd_back),
            )
        }
        if (title != null) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
        }
    }
}
