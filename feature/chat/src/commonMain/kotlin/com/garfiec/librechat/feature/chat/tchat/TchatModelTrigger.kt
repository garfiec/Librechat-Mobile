package com.garfiec.librechat.feature.chat.tchat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.data.datastore.StarredModelsDisplay
import com.garfiec.librechat.core.ui.components.AdaptiveInputChip
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.tchat_agent
import com.garfiec.librechat.feature.chat.resources.tchat_agents
import com.garfiec.librechat.feature.chat.resources.tchat_choose_model
import com.garfiec.librechat.feature.chat.resources.tchat_free
import com.garfiec.librechat.feature.chat.resources.tchat_image_chip_cd
import com.garfiec.librechat.feature.chat.resources.tchat_image_chip_remove
import com.garfiec.librechat.feature.chat.resources.tchat_image_gen
import com.garfiec.librechat.feature.chat.resources.tchat_image_model_moved
import com.garfiec.librechat.feature.chat.resources.tchat_trigger_cd
import com.garfiec.librechat.feature.chat.resources.tchat_trigger_cd_image
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ChatViewModel
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/*
 * The Tchat model trigger (design/system/components/ModelTrigger), the Image Gen composer chip,
 * the repair notice, and the glue that builds a [TchatPicker] from the chat state.
 */

/**
 * The picker for a selection. [showImageGen] is false for comparison mode's second model, which
 * can't carry Image Gen (only the primary request has an ephemeral agent).
 */
@Composable
fun rememberTchatPicker(
    uiState: ChatUiState,
    selectedEndpoint: String?,
    selectedModel: String?,
    showImageGen: Boolean,
): TchatPicker {
    val labels = PickerLabels(
        imageGen = stringResource(Res.string.tchat_image_gen),
        agents = stringResource(Res.string.tchat_agents),
        agentCaption = stringResource(Res.string.tchat_agent),
        free = stringResource(Res.string.tchat_free),
    )
    val tchat = uiState.tchat
    return remember(
        uiState.availableModels,
        uiState.agents,
        tchat,
        selectedEndpoint,
        selectedModel,
        uiState.favoriteModelKeys,
        uiState.favoriteAgentIds,
        showImageGen,
        labels,
    ) {
        buildTchatPicker(
            availableModels = uiState.availableModels,
            agents = uiState.agents,
            catalog = tchat.catalogView,
            selectedEndpoint = selectedEndpoint,
            selectedModel = selectedModel,
            favoriteModelKeys = uiState.favoriteModelKeys,
            favoriteAgentIds = uiState.favoriteAgentIds,
            imageSpecs = tchat.imageSpecs,
            imageGen = tchat.imageGen,
            showImageGen = showImageGen && tchat.imageGenAvailable,
            labels = labels,
        )
    }
}

fun tchatPickerActions(
    viewModel: ChatViewModel,
    onSelectModel: (endpoint: String, model: String) -> Unit,
): TchatPickerActions = TchatPickerActions(
    onSelectModel = onSelectModel,
    onImagePicked = viewModel::onImageRowPicked,
    onToggleModelPin = viewModel::toggleModelFavorite,
    onToggleAgentPin = viewModel::toggleAgentFavorite,
)

/** Off hides the Pinned section; Grouped and Top both show it (requirements D11). */
val StarredModelsDisplay.showsPinned: Boolean get() = this != StarredModelsDisplay.OFF

/**
 * The image entry to show beside the chat model, or null. Hidden while an agent is selected: the
 * server ignores ephemeral tools on agent runs, so Image Gen isn't sent there (requirements D6).
 */
val ChatUiState.imageGenIndicatorLabel: String?
    get() = if (showEphemeralTools) tchat.activeImage?.label else null

/** What the trigger shows for the current selection. */
data class TriggerLabel(val label: String, val caption: String, val free: Boolean, val imageLabel: String?)

@Composable
fun rememberTriggerLabel(uiState: ChatUiState): TriggerLabel? {
    val agentWord = stringResource(Res.string.tchat_agent)
    val model = uiState.selectedModel ?: return null
    val imageLabel = uiState.imageGenIndicatorLabel
    val catalog = uiState.tchat.catalogView
    val agents = uiState.agents
    return remember(uiState.selectedEndpoint, model, agents, catalog, imageLabel, agentWord) {
        if (uiState.selectedEndpoint == EndpointConstants.AGENTS) {
            val name = agents.firstOrNull { it.id == model }?.name ?: agentWord
            TriggerLabel(label = name, caption = agentWord, free = false, imageLabel = null)
        } else {
            TriggerLabel(
                label = formatModelLabel(model),
                caption = ownerOf(modelOwnerId(model)).label,
                free = catalog.isFree(model),
                imageLabel = imageLabel,
            )
        }
    }
}

/** One line for bars that can't draw two (Liquid Glass): "GPT-6 Luna · GPT Image". */
fun TriggerLabel.singleLine(): String = listOfNotNull(label, imageLabel).joinToString(" · ")

/**
 * The trigger's content, drawn inside the top bar's content chip. Line one: the model, a Free tag
 * and a chevron. Line two: the maker, plus the Image Gen model when it's on.
 */
@Composable
fun TchatModelTriggerContent(label: TriggerLabel?, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val chooseModel = stringResource(Res.string.tchat_choose_model)
    val description = when {
        label == null -> chooseModel
        label.imageLabel != null -> stringResource(Res.string.tchat_trigger_cd_image, label.label, label.imageLabel)
        else -> stringResource(Res.string.tchat_trigger_cd, label.label)
    }
    Row(
        modifier = modifier
            .padding(start = 16.dp, end = 12.dp)
            .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f, fill = false)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label?.label ?: chooseModel,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (label?.free == true) {
                    Spacer(Modifier.width(8.dp))
                    FreeTag()
                }
            }
            if (label != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = label.caption,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                    )
                    if (label.imageLabel != null) {
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Default.Image, null, Modifier.size(12.dp), tint = colors.primary)
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = label.imageLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(18.dp), tint = colors.onSurfaceVariant)
    }
}

/**
 * The input chip above the composer while Image Gen is on (requirements R20): tap opens the picker
 * on Image Gen, ✕ turns it off.
 */
@Composable
fun ImageGenComposerChip(
    imageLabel: String,
    onOpen: () -> Unit,
    onTurnOff: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(Res.string.tchat_image_chip_cd, imageLabel)
    val removeLabel = stringResource(Res.string.tchat_image_chip_remove)
    // The chip opens the picker; the ✕ beside it is its own 48dp button (design: `size-touch`).
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        AdaptiveInputChip(
            selected = true,
            onClick = onOpen,
            label = { Text(imageLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp)) },
            modifier = Modifier.semantics { contentDescription = description },
        )
        IconButton(onClick = onTurnOff) {
            Icon(Icons.Default.Close, contentDescription = removeLabel, modifier = Modifier.size(20.dp))
        }
    }
}

/** Shows the one-time repair notice (requirements R24), with web's exact wording. */
@Composable
fun ImageModelRepairSnackbarEffect(viewModel: ChatViewModel, snackbarHostState: SnackbarHostState) {
    LaunchedEffect(viewModel) {
        viewModel.imageModelRepairs.collect { repair ->
            val message = getString(
                Res.string.tchat_image_model_moved,
                formatModelLabel(repair.fromModel),
                formatModelLabel(repair.toModel),
            )
            snackbarHostState.showSnackbar(message = message)
        }
    }
}
