package com.garfiec.librechat.feature.chat.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.feature.chat.components.ComparisonLane
import com.garfiec.librechat.feature.chat.components.ComparisonModelLabels
import com.garfiec.librechat.feature.chat.components.ComparisonPairHeader
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState

/**
 * How a model selector behaves: what it marks selected, where a pick goes, and (in Compare) the A/B
 * pair above the list that switches lanes. Every selector (the options sheet's Model page, the A
 * and B selector sheets) goes through this, so changing a model looks and works the same everywhere.
 */
internal class ComparisonSelectorRouting(
    val selectedEndpoint: String?,
    val selectedModel: String?,
    val onModelSelect: (endpoint: String, model: String) -> Unit,
    /** The A/B pair above the list; null outside Compare. */
    val header: (@Composable () -> Unit)?,
    /** The edited lane's spec, and where a spec pick goes (staged during setup, like a model). */
    val selectedSpec: String? = null,
    val onSpecSelect: (ModelSpec) -> Unit = {},
)

/** Both compared models' labels, for the options sheet's Model row; null when not comparing. */
@Composable
internal fun comparisonModelLabels(uiState: ChatUiState): ComparisonModelLabels? {
    val comparison = uiState.comparisonState
    val secondaryModel = comparison.secondaryModel
    if (!comparison.isEnabled || secondaryModel == null) return null
    return ComparisonModelLabels(
        primary = comparisonLaneLabel(
            uiState.selectedEndpoint,
            uiState.selectedModel,
            uiState.agents,
            uiState.selectedSpec,
            uiState.modelSpecs,
        ),
        secondary = comparisonLaneLabel(
            comparison.secondaryEndpoint,
            secondaryModel,
            uiState.agents,
            uiState.laneBSpec,
            uiState.modelSpecs,
        ),
    )
}

/**
 * The routing for a selector opened for [initialTarget]. Outside Compare it passes straight
 * through to that lane's model with no header. While comparing, the A/B pair above the list picks
 * the lane it edits. During Compare setup ([setupPick] given) the pair shows the staged picks and
 * the list stages into them rather than applying, so Cancel still leaves the chat untouched.
 */
@Composable
internal fun rememberComparisonSelectorRouting(
    uiState: ChatUiState,
    initialTarget: ComparisonLane,
    onSelectPrimary: (endpoint: String, model: String) -> Unit,
    onSelectSecondary: (endpoint: String, model: String) -> Unit,
    onSelectPrimarySpec: (ModelSpec) -> Unit,
    onSelectSecondarySpec: (ModelSpec) -> Unit,
    setupPick: CompareSetupPick? = null,
): ComparisonSelectorRouting {
    var target by remember(initialTarget) { mutableStateOf(initialTarget) }
    val comparison = uiState.comparisonState
    val staging = if (comparison.isSettingUp) setupPick else null
    val lanes = when {
        staging != null -> {
            // Lane A follows the setup's rule (a staged A, else the chat's model), read from the setup.
            val staged = compareSetupLabels(
                staging,
                uiState,
                comparisonLaneLabel(
                    uiState.selectedEndpoint,
                    uiState.selectedModel,
                    uiState.agents,
                    uiState.selectedSpec,
                    uiState.modelSpecs,
                ),
            )
            val stage = { endpoint: String, model: String -> staging.set(endpoint, model) }
            ComparisonLanes(
                aEndpoint = staged.primaryEndpoint,
                aModel = staged.primaryModel,
                aSpec = staged.primarySpec,
                bEndpoint = staging.endpoint,
                bModel = staging.model,
                bSpec = staging.spec,
                // The setup's own pick: it knows which lane is being edited, and moves on to B
                // after an A while B is empty, as in the setup sheet.
                selectA = stage,
                selectB = stage,
                selectSpecA = staging::setSpec,
                selectSpecB = staging::setSpec,
            )
        }
        else -> ComparisonLanes(
            aEndpoint = uiState.selectedEndpoint,
            aModel = uiState.selectedModel,
            aSpec = uiState.selectedSpec,
            bEndpoint = comparison.secondaryEndpoint,
            bModel = comparison.secondaryModel,
            bSpec = uiState.laneBSpec,
            selectA = onSelectPrimary,
            selectB = onSelectSecondary,
            selectSpecA = onSelectPrimarySpec,
            selectSpecB = onSelectSecondarySpec,
        )
    }
    // During setup the lane being edited is the setup's, shared with its sheet and panes; while
    // comparing it is this selector's own. Outside Compare it is the lane the selector opened for.
    val inCompare = comparison.isEnabled || staging != null
    val laneTarget = staging?.target ?: target
    val editingA = (if (inCompare) laneTarget else initialTarget) == ComparisonLane.A
    val selectedEndpoint = if (editingA) lanes.aEndpoint else lanes.bEndpoint
    val selectedModel = if (editingA) lanes.aModel else lanes.bModel

    // Stable across recompositions: a new routing (or new lambdas) every time would make the
    // options sheet's ModelSelectorPageParams unequal on each chrome emission and redraw the page.
    val latestSelect by rememberUpdatedState(if (editingA) lanes.selectA else lanes.selectB)
    val onModelSelect = remember { { endpoint: String, model: String -> latestSelect(endpoint, model) } }
    val selectedSpec = if (editingA) lanes.aSpec else lanes.bSpec
    val latestSpecSelect by rememberUpdatedState(if (editingA) lanes.selectSpecA else lanes.selectSpecB)
    val onSpecSelect = remember { { spec: ModelSpec -> latestSpecSelect(spec) } }
    val specs = uiState.modelSpecs
    val primaryLabel = comparisonLaneLabel(lanes.aEndpoint, lanes.aModel, uiState.agents, lanes.aSpec, specs)
    val secondaryLabel = comparisonLaneLabel(lanes.bEndpoint, lanes.bModel, uiState.agents, lanes.bSpec, specs)
        .takeIf { lanes.bModel != null }
    // Two specs on one model are two different lanes.
    val isSameModel = lanes.bEndpoint == lanes.aEndpoint && lanes.bModel == lanes.aModel && lanes.bSpec == lanes.aSpec
    val header: (@Composable () -> Unit)? =
        remember(inCompare, primaryLabel, secondaryLabel, isSameModel, laneTarget, staging) {
            if (!inCompare) {
                null
            } else {
                {
                    ComparisonPairHeader(
                        primaryLabel = primaryLabel,
                        secondaryLabel = secondaryLabel,
                        isSameModel = isSameModel,
                        target = laneTarget,
                        onSelectTarget = { lane -> if (staging != null) staging.target = lane else target = lane },
                    )
                }
            }
        }
    return remember(selectedEndpoint, selectedModel, header, selectedSpec) {
        ComparisonSelectorRouting(selectedEndpoint, selectedModel, onModelSelect, header, selectedSpec, onSpecSelect)
    }
}

/** Both lanes' models and how a pick reaches each: applied while comparing, staged during setup. */
private class ComparisonLanes(
    val aEndpoint: String?,
    val aModel: String?,
    val aSpec: String?,
    val bEndpoint: String?,
    val bModel: String?,
    val bSpec: String?,
    val selectA: (endpoint: String, model: String) -> Unit,
    val selectB: (endpoint: String, model: String) -> Unit,
    val selectSpecA: (ModelSpec) -> Unit,
    val selectSpecB: (ModelSpec) -> Unit,
)
