package com.garfiec.librechat.feature.chat.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptiveButton
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedTextField
import com.garfiec.librechat.core.ui.components.clearFocusOnTap
import com.garfiec.librechat.feature.chat.components.CompareSetupPaneEmpty
import com.garfiec.librechat.feature.chat.components.ComparisonLane
import com.garfiec.librechat.feature.chat.components.ComparisonModelLabels
import com.garfiec.librechat.feature.chat.components.ComparisonPaneCard
import com.garfiec.librechat.feature.chat.components.ComparisonPaneGap
import com.garfiec.librechat.feature.chat.components.ComparisonPaneModelSelector
import com.garfiec.librechat.feature.chat.components.ComparisonPaneRadius
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.cancel
import com.garfiec.librechat.feature.chat.resources.chat_agent_fallback_label
import com.garfiec.librechat.feature.chat.resources.compare_browse_all
import com.garfiec.librechat.feature.chat.resources.compare_not_picked
import com.garfiec.librechat.feature.chat.resources.compare_pick_second_model
import com.garfiec.librechat.feature.chat.resources.compare_start
import com.garfiec.librechat.feature.chat.resources.model_selector_search
import com.garfiec.librechat.feature.chat.util.CompareQuickPick
import com.garfiec.librechat.feature.chat.util.compareQuickPicks
import com.garfiec.librechat.feature.chat.util.visibleSpecs
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

private val QuickPickRowShape = RoundedCornerShape(12.dp)
private val PickerMaxWidth = 440.dp

/**
 * Compare setup on a wide chat. Pane A is the chat as it stands, in a card, with its model pill;
 * pane B is a dashed outline holding a short picker: search, the starred models, and "Browse all
 * models" for the full grouped list (the setup sheet, A/B pair included). Picking stages B; Start
 * enables the comparison and Cancel ends setup, as in the phone sheet.
 */
@Suppress("LongParameterList")
@Composable
internal fun CompareSetupPanes(
    uiState: ChatUiState,
    pick: CompareSetupPick,
    actions: CompareSetupActions,
    primaryLabel: String,
    onChangePrimary: () -> Unit,
    onBrowseAll: () -> Unit,
    bottomContentPadding: Dp,
    primaryContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val agentCaption = stringResource(Res.string.chat_agent_fallback_label)
    // A replacement staged in "Browse all models" is what Start will apply, so pane A names it.
    val labels = compareSetupLabels(pick, uiState, primaryLabel)
    val pickLabel = labels.second

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(start = ComparisonPaneGap, end = ComparisonPaneGap, top = 8.dp, bottom = bottomContentPadding),
        horizontalArrangement = Arrangement.spacedBy(ComparisonPaneGap),
    ) {
        // The same card the live comparison's pane A is, so Start changes only pane B.
        ComparisonPaneCard(
            selector = {
                ComparisonPaneModelSelector(lane = ComparisonLane.A, label = labels.primary, onClick = onChangePrimary)
            },
            onContinue = null,
            modifier = Modifier.weight(1f),
        ) {
            if (uiState.displayMessages.isEmpty()) {
                CompareSetupPaneEmpty(
                    primaryLabel = labels.primary,
                    labels = labels.second?.let { ComparisonModelLabels(labels.primary, it) },
                    bottomPadding = 0.dp,
                )
            } else {
                primaryContent()
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .dashedOutline(ComparisonLane.B.accent),
        ) {
            ComparisonPaneModelSelector(
                lane = ComparisonLane.B,
                label = pickLabel ?: stringResource(Res.string.compare_not_picked),
                onClick = onBrowseAll.takeIf { pickLabel != null },
                modifier = Modifier.padding(8.dp),
            )
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clearFocusOnTap(),
            ) {
                QuickPicker(
                    uiState = uiState,
                    pick = pick,
                    agentCaption = agentCaption,
                    // Not mid-reply: see ComparisonModeDelegate.startComparison.
                    canStart = pickLabel != null && !uiState.isStreaming,
                    actions = actions,
                    onBrowseAll = onBrowseAll,
                    paneHeight = maxHeight,
                )
            }
        }
    }
}

@Composable
private fun QuickPicker(
    uiState: ChatUiState,
    pick: CompareSetupPick,
    agentCaption: String,
    canStart: Boolean,
    actions: CompareSetupActions,
    onBrowseAll: () -> Unit,
    paneHeight: Dp,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val specs = remember(uiState.modelSpecs, uiState.agents, uiState.agentsLoaded) {
        uiState.modelSpecs.visibleSpecs(uiState.agents, uiState.agentsLoaded)
    }
    // Off the main thread: a query fuzzy-matches every agent and model on each keystroke. The last
    // list stays up while the next one is worked out.
    val picks by produceState(
        initialValue = emptyList<CompareQuickPick>(),
        query,
        uiState.availableModels,
        uiState.endpointConfigs,
        uiState.agents,
        uiState.favoriteModelKeys,
        uiState.favoriteAgentIds,
        uiState.favoriteSpecNames,
        uiState.endpointKeyStates,
        specs,
        uiState.modelSpecs,
        agentCaption,
    ) {
        value = withContext(Dispatchers.Default) {
            compareQuickPicks(
                query = query,
                availableModels = uiState.availableModels,
                endpointConfigs = uiState.endpointConfigs,
                agents = uiState.agents,
                favoriteModelKeys = uiState.favoriteModelKeys,
                favoriteAgentIds = uiState.favoriteAgentIds,
                favoriteSpecNames = uiState.favoriteSpecNames,
                agentCaption = agentCaption,
                endpointKeyStates = uiState.endpointKeyStates,
                specs = specs,
                modelSpecs = uiState.modelSpecs,
            )
        }
    }
    // Centred in the pane while it fits; when it doesn't (the keyboard is up, or the window is short)
    // it starts at the top and scrolls, so the search box and the list stay in view.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .heightIn(min = paneHeight)
                .widthIn(max = PickerMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            Text(
                text = stringResource(Res.string.compare_pick_second_model),
                style = MaterialTheme.typography.headlineSmall,
            )
            AdaptiveOutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(Res.string.model_selector_search)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Column {
                picks.forEach { quick ->
                    QuickPickRow(
                        quick = quick,
                        selected = quick.endpoint == pick.endpoint && quick.model == pick.model &&
                            quick.spec == pick.spec,
                        // This pane only ever picks B, whichever lane the full list was last editing.
                        onClick = { pick.setSecond(quick.endpoint, quick.model, quick.spec) },
                    )
                }
            }
            TextButton(onClick = onBrowseAll) { Text(stringResource(Res.string.compare_browse_all)) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = actions.onCancel) { Text(stringResource(Res.string.cancel)) }
                AdaptiveButton(
                    onClick = { pick.start(actions) },
                    enabled = canStart,
                ) {
                    Text(stringResource(Res.string.compare_start))
                }
            }
        }
    }
}

@Composable
private fun QuickPickRow(quick: CompareQuickPick, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(QuickPickRowShape)
            .background(if (selected) ComparisonLane.B.container else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (quick.starred) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Spacer(Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = quick.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
            Text(
                text = quick.source,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = ComparisonLane.B.accent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** A rounded dashed outline: B's pane while it has no model. */
private fun Modifier.dashedOutline(color: Color): Modifier = drawBehind {
    val stroke = DashStroke.toPx()
    val dashes = PathEffect.dashPathEffect(floatArrayOf(DashOn.toPx(), DashOff.toPx()))
    drawRoundRect(
        color = color,
        // Inset by half the stroke so the outline sits inside the pane rather than half outside it.
        topLeft = Offset(stroke / 2, stroke / 2),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(ComparisonPaneRadius.toPx()),
        style = Stroke(width = stroke, pathEffect = dashes),
    )
}

private val DashStroke = 1.5.dp
private val DashOn = 6.dp
private val DashOff = 4.dp
