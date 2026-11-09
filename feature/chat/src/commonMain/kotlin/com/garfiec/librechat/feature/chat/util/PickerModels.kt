package com.garfiec.librechat.feature.chat.util

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.data.datastore.StarredModelsDisplay
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.config.displayLabel
import com.garfiec.librechat.core.model.media.IconSource
import com.garfiec.librechat.core.model.media.resolveAvatarUrl
import com.garfiec.librechat.core.model.media.resolveIconSource

/** What a tap on a `/model ` row does: what the model sheet does with the same row. */
@Immutable
sealed interface ModelPick {
    data class Spec(val spec: ModelSpec) : ModelPick

    /** A raw model, or an agent ([EndpointConstants.AGENTS] and its id). */
    data class Raw(val endpoint: String, val model: String) : ModelPick

    /** The endpoint (or the spec's) has no usable key: the tap opens Provider API Keys for [endpoint]. */
    data class AddKey(val endpoint: String) : ModelPick
}

/**
 * A spec, agent or model as a row of the composer's `/model ` list. [group] is the sheet section it
 * sits in (null for a spec in no section); [keyHint] is set when [pick] opens a key instead. An
 * endpoint without its key is one row of its own, as the sheet's greyed header, and its models are
 * [hiddenModels]: a search for one finds the row.
 */
@Immutable
data class PickerModel(
    val key: String,
    val title: String,
    val description: String?,
    val group: HeaderLabel?,
    val icon: IconSource?,
    val selected: Boolean,
    val starred: Boolean,
    val keyHint: KeyHint?,
    val pick: ModelPick,
    val hiddenModels: List<String> = emptyList(),
)

/** Why `/model ` lists nothing here, or null when it lists the chat's models. */
enum class ModelPickerBlock {
    /** No model picker in this chat (`ChatUiState.modelPickerShown`). */
    NO_PICKER,

    /** Comparing or setting a comparison up: lanes are picked from the comparison's own selector. */
    COMPARING,
}

/**
 * The model sheet's rows as one flat list for `/model `: starred first, then the sheet's order with
 * every section open. Rows the sheet greys (raw rows under `enforce`) are left out: they can't be sent.
 * A header that offers "Add key" becomes a row that opens the key.
 */
fun pickerModelsOf(inputs: PickerInputs, serverUrl: String): List<PickerModel> {
    fun endpointIcon(endpoint: String) =
        resolveIconSource(inputs.endpointConfigs[endpoint]?.iconURL, endpoint, serverUrl, inputs.endpointConfigs)
    var group: HeaderLabel? = null
    val models = mutableListOf<PickerModel>()
    val rows = pickerRows(inputs.copy(searchQuery = "", starredDisplay = StarredModelsDisplay.OFF, expandAll = true))
    rows.forEach { row ->
        when (row) {
            is PickerRow.Header -> {
                group = row.label
                val endpoint = row.keyEndpoint
                if (row.keyHint != null && endpoint != null) {
                    models += PickerModel(
                        key = row.key,
                        title = (row.label as? HeaderLabel.Text)?.text ?: endpoint,
                        description = null,
                        group = null,
                        icon = endpointIcon(endpoint),
                        selected = false,
                        starred = false,
                        keyHint = row.keyHint,
                        pick = ModelPick.AddKey(endpoint),
                        hiddenModels = inputs.availableModels[endpoint].orEmpty(),
                    )
                }
            }
            is PickerRow.Spec -> models += PickerModel(
                key = row.key,
                title = row.spec.displayLabel,
                description = row.spec.description?.takeIf { it.isNotBlank() },
                group = group,
                icon = row.spec.menuIconSource(inputs.agents, serverUrl, inputs.endpointConfigs),
                selected = row.isSelected,
                starred = row.isFavorite,
                keyHint = row.keyHint,
                pick = row.endpoint?.takeIf { row.keyHint != null }?.let(ModelPick::AddKey) ?: ModelPick.Spec(row.spec),
            )
            is PickerRow.AgentRow -> if (row.enabled) {
                models += PickerModel(
                    key = row.key,
                    title = row.agent.name ?: row.agent.id,
                    description = row.agent.description?.takeIf { it.isNotBlank() },
                    group = group,
                    icon = IconSource(EndpointConstants.AGENTS, resolveAvatarUrl(row.agent.avatarUrl, serverUrl)),
                    selected = row.isSelected,
                    starred = row.isFavorite,
                    keyHint = null,
                    pick = ModelPick.Raw(EndpointConstants.AGENTS, row.agent.id),
                )
            }
            is PickerRow.Model -> if (row.enabled) {
                models += PickerModel(
                    key = row.key,
                    title = row.model,
                    description = null,
                    group = group,
                    icon = endpointIcon(row.endpoint),
                    selected = row.isSelected,
                    starred = row.isFavorite,
                    keyHint = null,
                    pick = ModelPick.Raw(row.endpoint, row.model),
                )
            }
            PickerRow.SpecOnlyNote, PickerRow.StarredDivider -> Unit
        }
    }
    return models.sortedByDescending { it.starred }
}
