package com.garfiec.librechat.feature.chat.util

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.data.datastore.StarredModelsDisplay
import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.config.ModelSpecs
import com.garfiec.librechat.core.model.config.displayLabel
import com.garfiec.librechat.core.model.endpoint.KeyState
import com.garfiec.librechat.core.model.endpoint.endpointDisplayName
import com.garfiec.librechat.feature.chat.viewmodel.delegate.filterModelsByEndpoint

/**
 * One row of the model picker's list, in the order [pickerRows] lays them out. Holds data only:
 * the sheet resolves strings, icons and clicks.
 *
 * Keys are namespaced by row kind and join endpoint/model with `::`: names come from the server, and
 * Compose throws on a duplicate LazyColumn key (#541).
 */
@Immutable
sealed interface PickerRow {
    val key: String
    val contentType: Any?

    /** A section header. [expandKey] is what a tap toggles; null when it can't be toggled. */
    @Immutable
    data class Header(
        override val key: String,
        val label: HeaderLabel,
        val icon: HeaderIcon,
        val count: Int,
        val expanded: Boolean,
        val expandKey: String?,
        /** Set on an endpoint with no usable key: the header greys and offers "Add key" for [keyEndpoint]. */
        val keyHint: KeyHint? = null,
        val keyEndpoint: String? = null,
    ) : PickerRow {
        override val contentType: Any get() = "header"
    }

    /** [endpoint] is the spec's own, so a tap on one that [keyHint]s opens its key. */
    @Immutable
    data class Spec(
        override val key: String,
        val spec: ModelSpec,
        val endpoint: String?,
        val keyHint: KeyHint?,
        val isSelected: Boolean,
        val isFavorite: Boolean,
        /** The Starred section's flat TOP list, which packs its rows tighter. */
        val dense: Boolean = false,
    ) : PickerRow {
        override val contentType: Any get() = "spec"
    }

    @Immutable
    data class Model(
        override val key: String,
        val endpoint: String,
        val model: String,
        val isSelected: Boolean,
        val isFavorite: Boolean,
        val enabled: Boolean,
        val dense: Boolean = false,
    ) : PickerRow {
        override val contentType: Any get() = "model"
    }

    @Immutable
    data class AgentRow(
        override val key: String,
        val agent: Agent,
        val isSelected: Boolean,
        val isFavorite: Boolean,
        val enabled: Boolean,
        val dense: Boolean = false,
    ) : PickerRow {
        override val contentType: Any get() = "agent"
    }

    /** Under `enforce`, heading the list: raw rows are listed greyed, and only specs can be sent. */
    data object SpecOnlyNote : PickerRow {
        override val key: String get() = "note:spec-only"
        override val contentType: Any get() = "note"
    }

    /** Closes the Starred section's TOP list. */
    data object StarredDivider : PickerRow {
        override val key: String get() = "div:starred"
        override val contentType: Any get() = "divider"
    }
}

/** A header's title; the sheet turns the markers into strings. */
@Immutable
sealed interface HeaderLabel {
    data object Starred : HeaderLabel
    data object MyAgents : HeaderLabel
    data class Text(val text: String) : HeaderLabel
}

/** What a header draws at its start. */
@Immutable
sealed interface HeaderIcon {
    data object Star : HeaderIcon

    data object Agents : HeaderIcon

    /** [endpoint]'s icon: its config's `iconURL`, else its glyph. */
    data class Endpoint(val endpoint: String) : HeaderIcon

    /** A spec group's `groupIcon`. */
    data class Group(val icon: String) : HeaderIcon

    data object Folder : HeaderIcon
}

/** Everything the picker's row order depends on. */
data class PickerInputs(
    val endpointConfigs: Map<String, EndpointConfig> = emptyMap(),
    val availableModels: Map<String, List<String>> = emptyMap(),
    val agents: List<Agent> = emptyList(),
    /** Whether [agents] is the fetched list; until it is, no agent spec is hidden for missing from it. */
    val agentsLoaded: Boolean = false,
    val modelSpecs: ModelSpecs? = null,
    /** `interface.modelSelect`; off lists only the server's specs. */
    val modelSelect: Boolean = true,
    val selectedEndpoint: String? = null,
    val selectedModel: String? = null,
    val selectedSpec: String? = null,
    val searchQuery: String = "",
    val favoriteAgentIds: Set<String> = emptySet(),
    val favoriteModelKeys: Set<String> = emptySet(),
    val favoriteSpecNames: Set<String> = emptySet(),
    val starredDisplay: StarredModelsDisplay = StarredModelsDisplay.GROUPED,
    /** The user's section toggles by expand key; an absent key is closed unless it holds the selected spec. */
    val expandedGroups: Map<String, Boolean> = emptyMap(),
    val endpointKeyStates: Map<String, KeyState> = emptyMap(),
)

internal const val MY_AGENTS_EXPAND_KEY = "agents:mine"
internal const val STARRED_EXPAND_KEY = "starred"
internal fun endpointExpandKey(endpoint: String) = "ep:$endpoint"
internal fun specGroupExpandKey(group: String) = "specgroup:$group"

/**
 * The picker's rows, top to bottom: under `enforce` with raw rows listed, one note saying only specs
 * can be sent, so it never sits below a sendable spec; the optional Starred section; specs with no
 * group; My Agents; each endpoint's section (its grouped specs, then its models); then the custom
 * spec groups. A search lists matches flat, without grouped specs or custom groups, and opens every
 * section.
 *
 * A section whose endpoint needs the user's key keeps its specs listed but hides its models. A
 * custom group named after a configured endpoint, or `agents`, takes that endpoint's label and icon
 * but stays its own section (not under `modelSelect: false`, where web shows the raw name).
 */
fun pickerRows(inputs: PickerInputs): List<PickerRow> = PickerRowBuilder(inputs).build()

private class PickerRowBuilder(private val inputs: PickerInputs) {
    private val isSearching = inputs.searchQuery.isNotBlank()
    private val rawSelectable = !inputs.modelSpecs.requiresSpec

    // A spec selection checks only its own row, not the raw model it runs on.
    private val rawSelectedModel = inputs.selectedModel.takeIf { inputs.selectedSpec == null }
    private val showsAgents = inputs.modelSpecs.showsRawEndpoint(EndpointConstants.AGENTS, inputs.modelSelect)
    private val models = filterModelsByEndpoint(inputs.availableModels, inputs.endpointConfigs)
        .filterKeys { inputs.modelSpecs.showsRawEndpoint(it, inputs.modelSelect) }
    private val visibleSpecs = inputs.modelSpecs.visibleSpecs(inputs.agents, inputs.agentsLoaded)
    private val sections = sections()
    private val selectedSectionKey = when (val section = sections.sectionOf(inputs.selectedSpec)) {
        is SpecSection.Endpoint ->
            if (section.key == EndpointConstants.AGENTS) MY_AGENTS_EXPAND_KEY else endpointExpandKey(section.key)
        is SpecSection.Custom -> specGroupExpandKey(section.name)
        null -> null
    }
    private val listedAgents = run {
        val agents = inputs.agents.distinctBy { it.id }.takeIf { showsAgents }.orEmpty()
        if (isSearching) {
            searchByName(agents, inputs.searchQuery) { it.name ?: it.id }
        } else {
            agents.sortedByDescending { it.id in inputs.favoriteAgentIds }
        }
    }
    private val rows = mutableListOf<PickerRow>()

    // Whether the list holds a raw agent or model row, shown or in a collapsed section. A key-needed
    // endpoint's models are hidden, so they don't count.
    private var holdsRawRows = false

    fun build(): List<PickerRow> {
        starred()
        val ungrouped = if (isSearching) searchByName(visibleSpecs, inputs.searchQuery) { it.displayLabel } else sections.ungrouped
        ungrouped.forEach { rows += specRow(it, "spec:${it.name}") }
        myAgents()
        models.forEach { (endpoint, models) -> endpointSection(endpoint, models) }
        if (!isSearching) sections.custom.forEach(::customGroup)
        if (!rawSelectable && holdsRawRows) rows.add(0, PickerRow.SpecOnlyNote)
        return rows
    }

    private fun isOpen(key: String): Boolean = inputs.expandedGroups[key] ?: (key == selectedSectionKey)

    // Grouped specs sit in sections named after their group, so the sections come from the
    // unsearched lists. With the Starred section off, starred specs lead each section instead.
    private fun sections(): SpecSections {
        val shown = models.keys + listOfNotNull(EndpointConstants.AGENTS.takeIf { inputs.agents.isNotEmpty() && showsAgents })
        val split = specSections(visibleSpecs, shown)
        if (inputs.starredDisplay != StarredModelsDisplay.OFF) return split
        val starSorted = { specs: List<ModelSpec> -> specs.sortedByDescending { it.name in inputs.favoriteSpecNames } }
        return SpecSections(
            ungrouped = starSorted(split.ungrouped),
            byEndpoint = split.byEndpoint.mapValues { (_, specs) -> starSorted(specs) },
            custom = split.custom.map { it.copy(specs = starSorted(it.specs)) },
        )
    }

    private fun groupedSpecs(endpoint: String): List<ModelSpec> =
        if (isSearching) emptyList() else sections.byEndpoint[endpoint].orEmpty()

    private fun specRow(spec: ModelSpec, key: String, dense: Boolean = false): PickerRow.Spec {
        val endpoint = spec.toSeed()?.endpoint
        return PickerRow.Spec(
            key = key,
            spec = spec,
            endpoint = endpoint,
            keyHint = endpoint?.let { inputs.endpointKeyStates[it].keyHint() },
            isSelected = inputs.selectedSpec == spec.name,
            isFavorite = spec.name in inputs.favoriteSpecNames,
            dense = dense,
        )
    }

    private fun agentRow(agent: Agent, key: String, isFavorite: Boolean, dense: Boolean = false) = PickerRow.AgentRow(
        key = key,
        agent = agent,
        isSelected = inputs.selectedEndpoint == EndpointConstants.AGENTS && agent.id == rawSelectedModel,
        isFavorite = isFavorite,
        enabled = rawSelectable,
        dense = dense,
    )

    private fun modelRow(endpoint: String, model: String, key: String, isFavorite: Boolean, dense: Boolean = false) =
        PickerRow.Model(
            key = key,
            endpoint = endpoint,
            model = model,
            isSelected = endpoint == inputs.selectedEndpoint && model == rawSelectedModel,
            isFavorite = isFavorite,
            enabled = rawSelectable,
            dense = dense,
        )

    // Mobile-only: pinned items repeated at the top, unless searching (search already reorders by
    // relevance) or the preference is OFF. They still render in their own sections too.
    private fun starred() {
        val display = inputs.starredDisplay
        if (isSearching || display == StarredModelsDisplay.OFF) return
        // The favorite sets are LinkedHashSets from FavoritesDelegate.publish, so they keep pin order.
        val agentsById = inputs.agents.takeIf { showsAgents }.orEmpty().associateBy { it.id }
        val agents = inputs.favoriteAgentIds.mapNotNull { agentsById[it] }
        val models = starredModels(inputs.favoriteModelKeys, models)
        val specs = starredSpecs(inputs.favoriteSpecNames, visibleSpecs)
        if (specs.isEmpty() && agents.isEmpty() && models.isEmpty()) return
        if (agents.isNotEmpty() || models.isNotEmpty()) holdsRawRows = true
        // GROUPED is a collapsible header; TOP a flat, always-shown list closed off with a divider.
        val visible = display == StarredModelsDisplay.TOP || inputs.expandedGroups[STARRED_EXPAND_KEY] == true
        if (display == StarredModelsDisplay.GROUPED) {
            rows += PickerRow.Header(
                key = "hdr:starred",
                label = HeaderLabel.Starred,
                icon = HeaderIcon.Star,
                count = specs.size + agents.size + models.size,
                expanded = visible,
                expandKey = STARRED_EXPAND_KEY,
            )
        }
        if (visible) {
            val dense = display == StarredModelsDisplay.TOP
            specs.forEach { rows += specRow(it, "starred:spec:${it.name}", dense) }
            agents.forEach { rows += agentRow(it, "starred:agent:${it.id}", isFavorite = true, dense = dense) }
            models.forEach { (endpoint, model) ->
                rows += modelRow(endpoint, model, "starred:model:$endpoint::$model", isFavorite = true, dense = dense)
            }
        }
        if (display == StarredModelsDisplay.TOP) rows += PickerRow.StarredDivider
    }

    private fun myAgents() {
        if (listedAgents.isEmpty()) return
        holdsRawRows = true
        val expanded = isSearching || isOpen(MY_AGENTS_EXPAND_KEY)
        val specs = groupedSpecs(EndpointConstants.AGENTS)
        rows += PickerRow.Header(
            key = "hdr:agents",
            label = HeaderLabel.MyAgents,
            icon = HeaderIcon.Agents,
            count = specs.size + listedAgents.size,
            expanded = expanded,
            expandKey = MY_AGENTS_EXPAND_KEY.takeUnless { isSearching },
        )
        if (!expanded) return
        specs.forEach { rows += specRow(it, "ep:${EndpointConstants.AGENTS}:spec:${it.name}") }
        listedAgents.forEach { rows += agentRow(it, "agent:${it.id}", isFavorite = it.id in inputs.favoriteAgentIds) }
    }

    private fun endpointSection(endpoint: String, models: List<String>) {
        val listed = if (isSearching) {
            searchByName(models, inputs.searchQuery) { it }
        } else {
            models.sortedByDescending { "$endpoint::$it" in inputs.favoriteModelKeys }
        }
        val specs = groupedSpecs(endpoint)
        if (listed.isEmpty() && specs.isEmpty()) return
        val expanded = isSearching || isOpen(endpointExpandKey(endpoint))
        // A user-provided key that is Unset or Expired greys the header behind "Add key". It can't be
        // toggled then, so its specs stay listed (a tap opens the key screen) and its models hidden,
        // as web's EndpointItem lists them beside its key button.
        val keyHint = inputs.endpointKeyStates[endpoint].keyHint()
        val shownModels = if (keyHint == null) listed else emptyList()
        if (shownModels.isNotEmpty()) holdsRawRows = true
        rows += PickerRow.Header(
            key = "hdr:ep:$endpoint",
            label = HeaderLabel.Text(endpointDisplayName(endpoint, inputs.endpointConfigs[endpoint])),
            icon = HeaderIcon.Endpoint(endpoint),
            count = specs.size + shownModels.size,
            expanded = expanded && keyHint == null,
            expandKey = endpointExpandKey(endpoint).takeUnless { isSearching },
            keyHint = keyHint,
            keyEndpoint = endpoint,
        )
        if (expanded || keyHint != null) specs.forEach { rows += specRow(it, "ep:$endpoint:spec:${it.name}") }
        if (expanded) {
            shownModels.forEach { rows += modelRow(endpoint, it, "model:$endpoint::$it", "$endpoint::$it" in inputs.favoriteModelKeys) }
        }
    }

    // A group named after an endpoint the picker doesn't list (under `enforce`, or one with no
    // models) reads as that endpoint, as web nests it under the endpoint's own header. Under
    // `modelSelect: false` web titles every group with its raw name (`CustomGroup`), and so does this.
    private fun customGroup(group: SpecGroup) {
        val expandKey = specGroupExpandKey(group.name)
        val expanded = isOpen(expandKey)
        val isAgents = inputs.modelSelect && group.name == EndpointConstants.AGENTS
        val endpointConfig = inputs.endpointConfigs[group.name]?.takeIf { inputs.modelSelect }
        rows += PickerRow.Header(
            key = "hdr:$expandKey",
            label = when {
                isAgents -> HeaderLabel.MyAgents
                endpointConfig != null -> HeaderLabel.Text(endpointDisplayName(group.name, endpointConfig))
                else -> HeaderLabel.Text(group.name)
            },
            icon = when {
                isAgents -> HeaderIcon.Agents
                endpointConfig != null -> HeaderIcon.Endpoint(group.name)
                group.icon != null -> HeaderIcon.Group(group.icon)
                else -> HeaderIcon.Folder
            },
            count = group.specs.size,
            expanded = expanded,
            expandKey = expandKey,
        )
        if (expanded) group.specs.forEach { rows += specRow(it, "$expandKey:spec:${it.name}") }
    }
}
