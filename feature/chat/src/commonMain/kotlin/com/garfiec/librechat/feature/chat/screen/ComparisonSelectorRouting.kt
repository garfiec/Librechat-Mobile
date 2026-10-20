package com.garfiec.librechat.feature.chat.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
)

/** Both compared models' labels, for the options sheet's Model row; null when not comparing. */
@Composable
internal fun comparisonModelLabels(uiState: ChatUiState): ComparisonModelLabels? {
    val comparison = uiState.comparisonState
    val secondaryModel = comparison.secondaryModel
    if (!comparison.isEnabled || secondaryModel == null) return null
    return ComparisonModelLabels(
        primary = comparisonLaneLabel(uiState.selectedEndpoint, uiState.selectedModel, uiState.agents),
        secondary = comparisonLaneLabel(comparison.secondaryEndpoint, secondaryModel, uiState.agents),
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
                comparisonLaneLabel(uiState.selectedEndpoint, uiState.selectedModel, uiState.agents),
            )
            ComparisonLanes(
                aEndpoint = staged.primaryEndpoint,
                aModel = staged.primaryModel,
                bEndpoint = staging.endpoint,
                bModel = staging.model,
                // The setup's own pick: it knows which lane is being edited, and moves on to B
                // after an A while B is empty, as in the setup sheet.
                selectA = staging::set,
                selectB = staging::set,
            )
        }
        else -> ComparisonLanes(
            aEndpoint = uiState.selectedEndpoint,
            aModel = uiState.selectedModel,
            bEndpoint = comparison.secondaryEndpoint,
            bModel = comparison.secondaryModel,
            selectA = onSelectPrimary,
            selectB = onSelectSecondary,
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
    val primaryLabel = comparisonLaneLabel(lanes.aEndpoint, lanes.aModel, uiState.agents)
    val secondaryLabel = comparisonLaneLabel(lanes.bEndpoint, lanes.bModel, uiState.agents)
        .takeIf { lanes.bModel != null }
    val isSameModel = lanes.bEndpoint == lanes.aEndpoint && lanes.bModel == lanes.aModel
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
    return remember(selectedEndpoint, selectedModel, header) {
        ComparisonSelectorRouting(selectedEndpoint, selectedModel, onModelSelect, header)
    }
}

/** Both lanes' models and how a pick reaches each: applied while comparing, staged during setup. */
private class ComparisonLanes(
    val aEndpoint: String?,
    val aModel: String?,
    val bEndpoint: String?,
    val bModel: String?,
    val selectA: (endpoint: String, model: String) -> Unit,
    val selectB: (endpoint: String, model: String) -> Unit,
)
