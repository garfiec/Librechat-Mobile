package com.garfiec.librechat.feature.chat.tchat

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.config.ImageSpec

/*
 * What the Tchat model picker shows, as plain data: built from the chat state by
 * [buildTchatPicker] and narrowed by [filterPickerGroups]. Kept free of Compose so the grouping,
 * dedupe and search rules are unit-tested (this module has no Compose test harness).
 */

/** What tapping a row does. */
sealed interface PickerAction {
    /** Select a chat model, or an agent when [endpoint] is the agents endpoint. */
    data class Select(val endpoint: String, val model: String) : PickerAction

    /** Toggle Image Gen with the `imageList` entry [name]. */
    data class Image(val name: String) : PickerAction
}

enum class PickerGroupKind { MAKER, IMAGE, AGENTS }

@Immutable
data class PickerRow(
    /** Unique across the picker. */
    val key: String,
    val label: String,
    /** The raw model id, shown under the label so formatting hides nothing. */
    val detail: String? = null,
    val free: Boolean = false,
    val selected: Boolean = false,
    /** Null when the row can't be pinned (Image Gen rows). */
    val pinned: Boolean? = null,
    val action: PickerAction,
    /** Leading mark for rows whose group mark doesn't already say what they are. */
    val owner: OwnerId? = null,
    val agent: Agent? = null,
    /** Lowercased text search matches against. */
    val searchText: String,
)

@Immutable
data class PickerGroup(
    /** Rail filter id: an [OwnerId] name, [IMAGE_GROUP_ID] or [AGENTS_GROUP_ID]. */
    val id: String,
    val kind: PickerGroupKind,
    val label: String,
    /** Model family, shown beside the label where it differs ("Claude" for Anthropic). */
    val caption: String? = null,
    val owner: OwnerId? = null,
    val rows: List<PickerRow>,
)

@Immutable
data class PickerCurrent(
    val label: String,
    /** The maker, or "Agent". */
    val caption: String,
    /** The raw model id; null for an agent. */
    val detail: String? = null,
    val free: Boolean = false,
    val owner: OwnerId? = null,
    val agent: Agent? = null,
    /** The rail group holding the current pick, so its pill can carry a dot. */
    val groupId: String,
)

@Immutable
data class TchatPicker(
    /** Makers in owner order, then Image Gen, then agents. */
    val groups: List<PickerGroup>,
    /** Pinned models and agents, in pin order. Empty when nothing is pinned. */
    val pinned: List<PickerRow>,
    val current: PickerCurrent?,
    /** The image entry Image Gen is using, or null while it's off. */
    val activeImage: ImageSpec?,
) {
    val total: Int get() = groups.sumOf { it.rows.size }
}

/** The localized words the builder needs. Resolved by the caller. */
data class PickerLabels(
    val imageGen: String,
    val agents: String,
    val agentCaption: String,
    val free: String,
)

const val IMAGE_GROUP_ID: String = "image"
const val AGENTS_GROUP_ID: String = "agents"
const val ALL_GROUPS: String = "all"

/**
 * An endpoint whose every model has one maker is that maker's dedicated route (Tchat's
 * TensorGrid-Claude). A model served by both a dedicated and a general endpoint is listed once,
 * on the dedicated one.
 */
private fun dedicatedOwner(models: List<String>): OwnerId? {
    val first = models.firstOrNull()?.let(::modelOwnerId) ?: return null
    if (first == OwnerId.OTHER) return null
    return first.takeIf { models.all { modelOwnerId(it) == first } }
}

internal data class ListedModel(val endpoint: String, val modelId: String, val owner: OwnerId)

/**
 * Chat models grouped by maker, each listed once, without anything that can't hold a chat. Port of
 * web's `buildOwnerGroups`. [availableModels] is endpoint → model ids, in the server's order.
 */
internal fun ownerModels(
    availableModels: Map<String, List<String>>,
    catalog: ModelCatalogView,
): Map<OwnerId, List<ListedModel>> {
    val dedicated = availableModels.mapValues { (_, models) -> dedicatedOwner(models) }
    val byModelId = LinkedHashMap<String, ListedModel>()
    for ((endpoint, models) in availableModels) {
        if (endpoint == EndpointConstants.AGENTS) continue
        for (modelId in models) {
            if (!catalog.isChatModel(modelId)) continue
            val owner = modelOwnerId(modelId)
            val existing = byModelId[modelId]
            if (existing != null) {
                val existingIsDedicated = dedicated[existing.endpoint] == owner
                val thisIsDedicated = dedicated[endpoint] == owner
                if (existingIsDedicated || !thisIsDedicated) continue
            }
            byModelId[modelId] = ListedModel(endpoint, modelId, owner)
        }
    }
    val grouped = byModelId.values.groupBy { it.owner }
    return MODEL_OWNERS.mapNotNull { owner -> grouped[owner.id]?.let { owner.id to it } }.toMap()
}

fun favoriteKey(endpoint: String, model: String): String = "$endpoint::$model"

@Suppress("LongParameterList")
fun buildTchatPicker(
    availableModels: Map<String, List<String>>,
    agents: List<Agent>,
    catalog: ModelCatalogView,
    selectedEndpoint: String?,
    selectedModel: String?,
    favoriteModelKeys: Set<String>,
    favoriteAgentIds: Set<String>,
    imageSpecs: List<ImageSpec>,
    imageGen: ImageGenSelection?,
    /** False hides the Image Gen group: no `image_gen_oai` tool, or a picker that can't carry it. */
    showImageGen: Boolean,
    labels: PickerLabels,
): TchatPicker {
    val onAgent = selectedEndpoint == EndpointConstants.AGENTS
    val chatModel = selectedModel.takeIf { !onAgent }
    val activeImage = if (showImageGen) activeImageSpec(imageGen, imageSpecs) else null
    val groups = buildList {
        addAll(makerGroups(availableModels, catalog, chatModel, favoriteModelKeys, labels))
        if (showImageGen && imageSpecs.isNotEmpty()) add(imageGroup(imageSpecs, activeImage, catalog, labels))
        if (agents.isNotEmpty()) add(agentsGroup(agents, selectedModel.takeIf { onAgent }, favoriteAgentIds, labels))
    }

    // Pins keep their server order (FavoritesDelegate publishes LinkedHashSets).
    val rowsByKey = groups.flatMap { it.rows }.associateBy { it.key }
    val pinned = favoriteAgentIds.mapNotNull { rowsByKey[favoriteKey(EndpointConstants.AGENTS, it)] } +
        favoriteModelKeys.mapNotNull { rowsByKey[it] }

    val current = when {
        onAgent && selectedModel != null -> {
            val agent = agents.firstOrNull { it.id == selectedModel }
            PickerCurrent(
                label = agent?.name ?: labels.agentCaption,
                caption = labels.agentCaption,
                agent = agent,
                groupId = AGENTS_GROUP_ID,
            )
        }
        selectedModel != null -> {
            val ownerId = modelOwnerId(selectedModel)
            PickerCurrent(
                label = formatModelLabel(selectedModel),
                caption = ownerOf(ownerId).label,
                detail = selectedModel,
                free = catalog.isFree(selectedModel),
                owner = ownerId,
                groupId = ownerId.name,
            )
        }
        else -> null
    }

    return TchatPicker(groups = groups, pinned = pinned, current = current, activeImage = activeImage)
}

private fun makerGroups(
    availableModels: Map<String, List<String>>,
    catalog: ModelCatalogView,
    selectedModel: String?,
    favoriteModelKeys: Set<String>,
    labels: PickerLabels,
): List<PickerGroup> = ownerModels(availableModels, catalog).map { (ownerId, models) ->
    val owner = ownerOf(ownerId)
    PickerGroup(
        id = ownerId.name,
        kind = PickerGroupKind.MAKER,
        label = owner.label,
        caption = owner.family,
        owner = ownerId,
        rows = models.map { listed ->
            val label = formatModelLabel(listed.modelId)
            val free = catalog.isFree(listed.modelId)
            val key = favoriteKey(listed.endpoint, listed.modelId)
            PickerRow(
                key = key,
                label = label,
                detail = listed.modelId,
                free = free,
                selected = selectedModel == listed.modelId,
                pinned = key in favoriteModelKeys,
                action = PickerAction.Select(listed.endpoint, listed.modelId),
                searchText = searchText(label, listed.modelId, owner.label, owner.family, labels.free.takeIf { free }),
            )
        },
    )
}

/**
 * Image models can't hold a chat, so picking one here turns on Image Gen with that model and
 * leaves the chat model as it is. Picking the active one again turns Image Gen off.
 */
private fun imageGroup(
    imageSpecs: List<ImageSpec>,
    activeImage: ImageSpec?,
    catalog: ModelCatalogView,
    labels: PickerLabels,
): PickerGroup = PickerGroup(
    id = IMAGE_GROUP_ID,
    kind = PickerGroupKind.IMAGE,
    label = labels.imageGen,
    rows = imageSpecs.map { spec ->
        val free = catalog.isFree(spec.model)
        val owner = modelOwnerId(spec.model)
        PickerRow(
            key = "image::${spec.name}",
            label = spec.label,
            detail = spec.model,
            free = free,
            selected = spec.name == activeImage?.name,
            action = PickerAction.Image(spec.name),
            owner = owner,
            searchText = searchText(
                spec.label,
                spec.model,
                spec.description,
                labels.imageGen,
                ownerOf(owner).label,
                labels.free.takeIf { free },
            ),
        )
    },
)

private fun agentsGroup(
    agents: List<Agent>,
    selectedAgentId: String?,
    favoriteAgentIds: Set<String>,
    labels: PickerLabels,
): PickerGroup = PickerGroup(
    id = AGENTS_GROUP_ID,
    kind = PickerGroupKind.AGENTS,
    label = labels.agents,
    rows = agents.map { agent ->
        val name = agent.name ?: agent.id
        PickerRow(
            key = favoriteKey(EndpointConstants.AGENTS, agent.id),
            label = name,
            selected = selectedAgentId == agent.id,
            pinned = agent.id in favoriteAgentIds,
            action = PickerAction.Select(EndpointConstants.AGENTS, agent.id),
            agent = agent,
            searchText = searchText(name, labels.agents),
        )
    },
)

private fun searchText(vararg parts: String?): String = parts.filterNotNull().joinToString(" ").lowercase()

/**
 * The groups shown for a rail [filter] ([ALL_GROUPS] or a group id) and a search [query]: a
 * case-insensitive substring match over each row's search text. Empty groups are dropped.
 */
fun filterPickerGroups(groups: List<PickerGroup>, filter: String, query: String): List<PickerGroup> {
    val term = query.trim().lowercase()
    return groups
        .filter { filter == ALL_GROUPS || it.id == filter }
        .map { group -> if (term.isEmpty()) group else group.copy(rows = group.rows.filter { term in it.searchText }) }
        .filter { it.rows.isNotEmpty() }
}
