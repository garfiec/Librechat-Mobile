package com.garfiec.librechat.feature.chat.screen

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.ui.components.AdaptiveModalBottomSheet
import com.garfiec.librechat.core.ui.components.LowProfileDragHandle
import com.garfiec.librechat.core.ui.components.clearFocusOnTap
import com.garfiec.librechat.feature.chat.components.CompareSetupHeader
import com.garfiec.librechat.feature.chat.components.ComparisonLane
import com.garfiec.librechat.feature.chat.components.ModelSelectorSheetContent
import com.garfiec.librechat.feature.chat.util.toSeed
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ChatViewModel
import com.garfiec.librechat.feature.chat.viewmodel.ComparisonPick

/**
 * Chat width at which Compare shows both panes side by side. Below it, the panes are tabs and
 * setup is a sheet; at or above it, setup happens in the empty second pane.
 */
internal val ComparisonWideBreakpoint = 600.dp

/**
 * What the user has staged during Compare setup: the second model, a replacement for the first if
 * they changed it, and which of the two the list is editing. Nothing reaches the ViewModel until
 * Start, so Cancel leaves the chat's own model untouched too. Held at screen level, above the width
 * switch, so folding or unfolding mid-setup moves between the sheet and the inline picker without
 * losing it.
 */
@Stable
internal class CompareSetupPick {
    var endpoint by mutableStateOf<String?>(null)
    var model by mutableStateOf<String?>(null)

    /** The spec B was picked as; null for a raw model. */
    var spec by mutableStateOf<String?>(null)

    /** Null while A is still the chat's own model. */
    var primaryEndpoint by mutableStateOf<String?>(null)
    var primaryModel by mutableStateOf<String?>(null)
    var primarySpec by mutableStateOf<String?>(null)

    var target by mutableStateOf(ComparisonLane.B)

    /**
     * Stages [endpoint]/[model] (and [spec], for a spec row) for the lane being edited. Picking A
     * moves on to B while B is still empty.
     */
    fun set(endpoint: String, model: String, spec: String? = null) {
        when (target) {
            ComparisonLane.A -> {
                primaryEndpoint = endpoint
                primaryModel = model
                primarySpec = spec
                if (this.model == null) target = ComparisonLane.B
            }
            ComparisonLane.B -> setSecond(endpoint, model, spec)
        }
    }

    /** Stages a spec row for the lane being edited, as [set] does a model. */
    fun setSpec(spec: ModelSpec) {
        val seed = spec.toSeed() ?: return
        set(seed.endpoint, seed.model, seed.spec)
    }

    /** Stages [endpoint]/[model] as B whichever lane the list is editing (the wide pane B picker). */
    fun setSecond(endpoint: String, model: String, spec: String? = null) {
        this.endpoint = endpoint
        this.model = model
        this.spec = spec
    }

    /** Starts the comparison against B with any staged A. A no-op until B is picked. */
    fun start(actions: CompareSetupActions) {
        val secondEndpoint = endpoint ?: return
        val secondModel = model ?: return
        val stagedEndpoint = primaryEndpoint
        val stagedModel = primaryModel
        val stagedPrimary = if (stagedEndpoint != null && stagedModel != null) {
            ComparisonPick(stagedEndpoint, stagedModel, primarySpec)
        } else {
            null
        }
        actions.onStart(ComparisonPick(secondEndpoint, secondModel, spec), stagedPrimary)
    }

    fun clear() {
        endpoint = null
        model = null
        spec = null
        primaryEndpoint = null
        primaryModel = null
        primarySpec = null
        target = ComparisonLane.B
    }
}

/**
 * What a [CompareSetupPick] stages, as the setup shows it. A is the staged replacement when there
 * is one (it is what Start applies), else the chat's own model.
 */
@Immutable
internal data class CompareSetupLabels(
    val primary: String,
    val primaryEndpoint: String?,
    val primaryModel: String?,
    val primarySpec: String?,
    /** Null until B is picked. */
    val second: String?,
)

@Composable
internal fun compareSetupLabels(pick: CompareSetupPick, uiState: ChatUiState, chatPrimaryLabel: String): CompareSetupLabels {
    val stagedEndpoint = pick.primaryEndpoint
    val stagedModel = pick.primaryModel
    val staged = stagedEndpoint != null && stagedModel != null
    val specs = uiState.modelSpecs
    val stagedLabel = comparisonLaneLabel(stagedEndpoint, stagedModel, uiState.agents, pick.primarySpec, specs)
    val secondLabel = comparisonLaneLabel(pick.endpoint, pick.model, uiState.agents, pick.spec, specs)
    return CompareSetupLabels(
        primary = if (staged) stagedLabel else chatPrimaryLabel,
        primaryEndpoint = if (staged) stagedEndpoint else uiState.selectedEndpoint,
        primaryModel = if (staged) stagedModel else uiState.selectedModel,
        primarySpec = if (staged) pick.primarySpec else uiState.selectedSpec,
        second = secondLabel.takeIf { pick.endpoint != null && pick.model != null },
    )
}

/** A [CompareSetupPick] that survives configuration changes and clears when setup ends. */
@Composable
internal fun rememberCompareSetupPick(isSettingUp: Boolean): CompareSetupPick {
    val pick = rememberSaveable(saver = CompareSetupPickSaver) { CompareSetupPick() }
    LaunchedEffect(isSettingUp) { if (!isSettingUp) pick.clear() }
    return pick
}

private val CompareSetupPickSaver = listSaver<CompareSetupPick, String?>(
    save = {
        listOf(it.endpoint, it.model, it.spec, it.primaryEndpoint, it.primaryModel, it.primarySpec, it.target.name)
    },
    restore = { saved ->
        CompareSetupPick().apply {
            endpoint = saved[0]
            model = saved[1]
            spec = saved[2]
            primaryEndpoint = saved[3]
            primaryModel = saved[4]
            primarySpec = saved[5]
            target = saved[6]?.let { name -> ComparisonLane.entries.firstOrNull { it.name == name } } ?: ComparisonLane.B
        }
    },
)

/**
 * The model list under the Compare setup header. Picking only stages a model for the lane being
 * edited; Start applies A (if changed) and enables the comparison, Cancel ends setup with nothing
 * changed.
 */
@Composable
internal fun CompareSetupPicker(
    uiState: ChatUiState,
    pick: CompareSetupPick,
    primaryLabel: String,
    actions: CompareSetupActions,
    onNavigateToProviderKeys: (endpointName: String?) -> Unit,
    /** The header's Cancel: ends setup, or on a wide chat just closes "Browse all models". */
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels = compareSetupLabels(pick, uiState, primaryLabel)
    val pickEndpoint = pick.endpoint
    val pickModel = pick.model
    val aEndpoint = labels.primaryEndpoint
    val aModel = labels.primaryModel
    val editingA = pick.target == ComparisonLane.A

    ModelSelectorSheetContent(
        endpointConfigs = uiState.endpointConfigs,
        availableModels = uiState.availableModels,
        agents = uiState.agents,
        // The checkmark follows the lane being edited.
        selectedEndpoint = if (editingA) aEndpoint else pickEndpoint,
        selectedModel = if (editingA) aModel else pickModel,
        onModelSelect = { endpoint, model -> pick.set(endpoint, model) },
        modelSelect = uiState.modelSelectEnabled,
        modelSpecs = uiState.modelSpecs,
        selectedSpec = if (editingA) labels.primarySpec else pick.spec,
        onSpecSelect = pick::setSpec,
        agentsLoaded = uiState.agentsLoaded,
        onSetApiKey = { name -> onNavigateToProviderKeys(name) },
        modifier = modifier,
        serverUrl = uiState.serverUrl,
        favoriteAgentIds = uiState.favoriteAgentIds,
        favoriteModelKeys = uiState.favoriteModelKeys,
        favoriteSpecNames = uiState.favoriteSpecNames,
        onToggleAgentFavorite = actions.onToggleAgentFavorite,
        onToggleModelFavorite = actions.onToggleModelFavorite,
        onToggleSpecFavorite = actions.onToggleSpecFavorite,
        starredDisplay = uiState.starredModelsDisplay,
        endpointKeyStates = uiState.endpointKeyStates,
        showTitle = false,
        header = {
            CompareSetupHeader(
                primaryLabel = labels.primary,
                pickLabel = labels.second,
                isSameModel = pickEndpoint == aEndpoint && pickModel == aModel && pick.spec == labels.primarySpec,
                target = pick.target,
                onSelectTarget = { pick.target = it },
                onCancel = onCancel,
                onStart = { pick.start(actions) },
                // Not mid-reply: see ComparisonModeDelegate.startComparison.
                canStart = !uiState.isStreaming,
            )
        },
    )
}

/** What Compare setup asks of the ViewModel, so the picker takes callbacks rather than the VM. */
@Immutable
internal data class CompareSetupActions(
    val onCancel: () -> Unit,
    /** Starts against B, first applying a changed A when there is one. */
    val onStart: (second: ComparisonPick, primary: ComparisonPick?) -> Unit,
    val onToggleAgentFavorite: (agentId: String) -> Unit,
    val onToggleModelFavorite: (endpoint: String, model: String) -> Unit,
    val onToggleSpecFavorite: (name: String) -> Unit,
)

internal fun ChatViewModel.compareSetupActions(): CompareSetupActions = CompareSetupActions(
    onCancel = ::cancelComparisonSetup,
    onStart = { second, primary -> startComparison(second.endpoint, second.model, second.spec, primary) },
    onToggleAgentFavorite = ::toggleAgentFavorite,
    onToggleModelFavorite = ::toggleModelFavorite,
    onToggleSpecFavorite = ::toggleSpecFavorite,
)

/**
 * Compare setup as a sheet: a narrow chat's only picker (dismissing it is Cancel), or a wide chat's
 * "Browse all models" over the setup panes (dismissing it returns to them).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CompareSetupSheet(
    uiState: ChatUiState,
    pick: CompareSetupPick,
    primaryLabel: String,
    actions: CompareSetupActions,
    onNavigateToProviderKeys: (endpointName: String?) -> Unit,
    /** Dismissing without Start: cancels setup unless the panes behind still hold it (wide). */
    onDismiss: (() -> Unit)? = null,
) {
    val dismiss = onDismiss ?: actions.onCancel
    AdaptiveModalBottomSheet(
        onDismissRequest = dismiss,
        dragHandle = { LowProfileDragHandle() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        modifier = Modifier.clearFocusOnTap(),
    ) {
        CompareSetupPicker(
            uiState = uiState,
            pick = pick,
            primaryLabel = primaryLabel,
            actions = actions,
            onNavigateToProviderKeys = onNavigateToProviderKeys,
            onCancel = dismiss,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
