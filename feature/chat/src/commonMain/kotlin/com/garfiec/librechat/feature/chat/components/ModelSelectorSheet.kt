package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.data.datastore.StarredModelsDisplay
import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.config.ModelSpecs
import com.garfiec.librechat.core.model.config.displayLabel
import com.garfiec.librechat.core.model.endpoint.KeyState
import com.garfiec.librechat.core.model.media.IconSource
import com.garfiec.librechat.core.model.media.resolveAvatarUrl
import com.garfiec.librechat.core.model.media.resolveIconSource
import com.garfiec.librechat.core.ui.components.AdaptiveDivider
import com.garfiec.librechat.core.ui.components.AdaptiveFilledTonalButton
import com.garfiec.librechat.core.ui.components.AdaptiveModalBottomSheet
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedTextField
import com.garfiec.librechat.core.ui.components.EndpointIcon
import com.garfiec.librechat.core.ui.components.ErrorBanner
import com.garfiec.librechat.core.ui.components.ListDragSelection
import com.garfiec.librechat.core.ui.components.LowProfileDragHandle
import com.garfiec.librechat.core.ui.components.clearFocusOnTap
import com.garfiec.librechat.core.ui.components.dragSelectExclude
import com.garfiec.librechat.core.ui.components.dragSelectRow
import com.garfiec.librechat.core.ui.components.dragSelectRowIcon
import com.garfiec.librechat.core.ui.components.listDragSelection
import com.garfiec.librechat.core.ui.components.rememberListDragSelection
import com.garfiec.librechat.feature.chat.resources.*
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.util.KeyHint
import com.garfiec.librechat.feature.chat.util.SpecSection
import com.garfiec.librechat.feature.chat.util.SpecSections
import com.garfiec.librechat.feature.chat.util.blocksSelection
import com.garfiec.librechat.feature.chat.util.keyHint
import com.garfiec.librechat.feature.chat.util.menuIconSource
import com.garfiec.librechat.feature.chat.util.requiresSpec
import com.garfiec.librechat.feature.chat.util.searchByName
import com.garfiec.librechat.feature.chat.util.showsRawEndpoint
import com.garfiec.librechat.feature.chat.util.specSections
import com.garfiec.librechat.feature.chat.util.starredModels
import com.garfiec.librechat.feature.chat.util.starredSpecs
import com.garfiec.librechat.feature.chat.util.toSeed
import com.garfiec.librechat.feature.chat.util.visibleSpecs
import com.garfiec.librechat.feature.chat.viewmodel.delegate.filterModelsByEndpoint
import org.jetbrains.compose.resources.stringResource

private val IconSize = 20.dp

/** Sentinel endpoint name the optional "Starred" section's header passes to [EndpointGroupHeader]. */
private const val STARRED_GROUP_KEY = "__starred__"

// Keys are namespaced by row kind and join endpoint/model with `::`: names come from the server, and
// Compose throws on a duplicate LazyColumn key (#541).
private const val MY_AGENTS_EXPAND_KEY = "agents:mine"
private const val STARRED_EXPAND_KEY = "starred"
private fun endpointExpandKey(endpoint: String) = "ep:$endpoint"
private fun specGroupExpandKey(group: String) = "specgroup:$group"

/** Row vertical padding for grouped list items vs. the denser starred-at-top section. */
private val ListItemVerticalPadding = 12.dp
private val StarredItemVerticalPadding = 6.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectorSheet(
    endpointConfigs: Map<String, EndpointConfig>,
    availableModels: Map<String, List<String>>,
    agents: List<Agent>,
    selectedEndpoint: String?,
    selectedModel: String?,
    onModelSelect: (endpoint: String, model: String) -> Unit,
    /** `interface.modelSelect`; off lists only the server's specs, as web does. */
    modelSelect: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    modelSpecs: ModelSpecs? = null,
    selectedSpec: String? = null,
    onSpecSelect: (ModelSpec) -> Unit = {},
    agentsLoaded: Boolean = false,
    serverUrl: String = "",
    errorMessage: String? = null,
    onErrorDismiss: () -> Unit = {},
    favoriteAgentIds: Set<String> = emptySet(),
    favoriteModelKeys: Set<String> = emptySet(),
    favoriteSpecNames: Set<String> = emptySet(),
    onToggleAgentFavorite: ((agentId: String) -> Unit)? = null,
    onToggleModelFavorite: ((endpoint: String, model: String) -> Unit)? = null,
    onToggleSpecFavorite: ((name: String) -> Unit)? = null,
    /**
     * Mobile-only preference controlling whether pinned items also appear in a
     * dedicated section at the top of the sheet. [StarredModelsDisplay.OFF] skips it
     * (favorites float to the top within their own group).
     */
    starredDisplay: StarredModelsDisplay = StarredModelsDisplay.GROUPED,
    /**
     * Per-endpoint user-provided-key state. Endpoints whose key is [KeyState.Unset]
     * or [KeyState.Expired] render a greyed group with an "Add key" action, and spec
     * rows on them say so. Absent keys (built-in endpoints) and [KeyState.Loading] /
     * [KeyState.Set] all fail-open to the normal selectable rendering.
     */
    endpointKeyStates: Map<String, KeyState> = emptyMap(),
    /** Above the list; the comparison A/B pair while comparing. */
    header: (@Composable () -> Unit)? = null,
    /**
     * Invoked with the endpoint name when the user taps "Add key" on a greyed group, or a
     * spec row that needs a key. Implementations should navigate to Settings → Provider API Keys.
     */
    onSetApiKey: (endpointName: String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    AdaptiveModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { LowProfileDragHandle() },
        sheetState = sheetState,
        modifier = modifier.clearFocusOnTap(),
    ) {
        ModelSelectorSheetContent(
            endpointConfigs = endpointConfigs,
            availableModels = availableModels,
            agents = agents,
            selectedEndpoint = selectedEndpoint,
            selectedModel = selectedModel,
            onModelSelect = onModelSelect,
            modelSelect = modelSelect,
            modelSpecs = modelSpecs,
            selectedSpec = selectedSpec,
            onSpecSelect = onSpecSelect,
            agentsLoaded = agentsLoaded,
            onSetApiKey = onSetApiKey,
            modifier = Modifier.fillMaxSize(),
            serverUrl = serverUrl,
            errorMessage = errorMessage,
            onErrorDismiss = onErrorDismiss,
            favoriteAgentIds = favoriteAgentIds,
            favoriteModelKeys = favoriteModelKeys,
            favoriteSpecNames = favoriteSpecNames,
            onToggleAgentFavorite = onToggleAgentFavorite,
            onToggleModelFavorite = onToggleModelFavorite,
            onToggleSpecFavorite = onToggleSpecFavorite,
            starredDisplay = starredDisplay,
            endpointKeyStates = endpointKeyStates,
            header = header,
        )
    }
}

/**
 * The model selector body (header, search, grouped list), no sheet chrome. Rendered by the
 * standalone [ModelSelectorSheet] and by `ChatOptionsBottomSheet`'s selector page. Has no
 * `onDismiss` — each host decides what selection does (swap to Options vs. dismiss).
 */
@Composable
fun ModelSelectorSheetContent(
    endpointConfigs: Map<String, EndpointConfig>,
    availableModels: Map<String, List<String>>,
    agents: List<Agent>,
    selectedEndpoint: String?,
    selectedModel: String?,
    onModelSelect: (endpoint: String, model: String) -> Unit,
    /** `interface.modelSelect`; off lists only the server's specs, as web does. */
    modelSelect: Boolean,
    /**
     * Invoked with the endpoint name when the user taps "Add key" on a greyed group, or a
     * spec row that needs a key. Implementations should navigate to Settings → Provider API Keys.
     */
    onSetApiKey: (endpointName: String) -> Unit,
    modifier: Modifier = Modifier,
    modelSpecs: ModelSpecs? = null,
    selectedSpec: String? = null,
    onSpecSelect: (ModelSpec) -> Unit = {},
    /** Whether [agents] is the fetched list; until it is, no agent spec is hidden for missing from it. */
    agentsLoaded: Boolean = false,
    serverUrl: String = "",
    errorMessage: String? = null,
    onErrorDismiss: () -> Unit = {},
    favoriteAgentIds: Set<String> = emptySet(),
    favoriteModelKeys: Set<String> = emptySet(),
    favoriteSpecNames: Set<String> = emptySet(),
    onToggleAgentFavorite: ((agentId: String) -> Unit)? = null,
    onToggleModelFavorite: ((endpoint: String, model: String) -> Unit)? = null,
    onToggleSpecFavorite: ((name: String) -> Unit)? = null,
    /**
     * Mobile-only preference controlling whether pinned items also appear in a
     * dedicated section at the top of the sheet. [StarredModelsDisplay.OFF] skips it
     * (favorites float to the top within their own group).
     */
    starredDisplay: StarredModelsDisplay = StarredModelsDisplay.GROUPED,
    /**
     * Per-endpoint user-provided-key state. Endpoints whose key is [KeyState.Unset]
     * or [KeyState.Expired] render a greyed group with an "Add key" action, and spec
     * rows on them say so. Absent keys (built-in endpoints) and [KeyState.Loading] /
     * [KeyState.Set] all fail-open to the normal selectable rendering.
     */
    endpointKeyStates: Map<String, KeyState> = emptyMap(),
    /** Rendered above the error banner and title; the paged host passes a back-arrow row. */
    header: (@Composable () -> Unit)? = null,
    /** False when [header] already titles the list (Compare setup). */
    showTitle: Boolean = true,
) {
    var searchQuery by remember { mutableStateOf("") }
    // Only user toggles land here; an absent key is collapsed unless it holds the selected spec
    // (`isOpen`). Not seeded — the sheet can mount before models/agents load, and a seed would miss
    // late arrivals.
    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }
    val isSearching = searchQuery.isNotBlank()

    // Filter to only show models for endpoints the user's server has enabled
    val filteredByEndpoint = remember(availableModels, endpointConfigs, modelSpecs, modelSelect) {
        filterModelsByEndpoint(availableModels, endpointConfigs).filterKeys { endpoint ->
            modelSpecs.showsRawEndpoint(endpoint, modelSelect)
        }
    }
    // Raw rows the server would reject still show, greyed, where web lists them.
    val rawSelectable = !modelSpecs.requiresSpec
    // A spec selection checks only its own row, not the raw model it runs on.
    val rawSelectedModel = selectedModel.takeIf { selectedSpec == null }
    val visibleSpecs = remember(modelSpecs, agents, agentsLoaded) { modelSpecs.visibleSpecs(agents, agentsLoaded) }
    // Grouped specs sit in sections named after their group, so the section keys come from the
    // unsearched lists: a search shows every spec flat, without headers, as web's search does.
    val hasAgentsSection = remember(agents, modelSpecs, modelSelect) {
        agents.isNotEmpty() && modelSpecs.showsRawEndpoint(EndpointConstants.AGENTS, modelSelect)
    }
    // With the starred section off, starred specs lead each section instead.
    val sections = remember(
        visibleSpecs,
        filteredByEndpoint.keys,
        hasAgentsSection,
        starredDisplay,
        favoriteSpecNames,
    ) {
        val shown = filteredByEndpoint.keys + listOfNotNull(EndpointConstants.AGENTS.takeIf { hasAgentsSection })
        val split = specSections(visibleSpecs, shown)
        if (starredDisplay != StarredModelsDisplay.OFF) return@remember split
        val starSorted = { specs: List<ModelSpec> -> specs.sortedByDescending { it.name in favoriteSpecNames } }
        SpecSections(
            ungrouped = starSorted(split.ungrouped),
            byEndpoint = split.byEndpoint.mapValues { (_, specs) -> starSorted(specs) },
            custom = split.custom.map { it.copy(specs = starSorted(it.specs)) },
        )
    }
    val shownSpecs = remember(visibleSpecs, sections, searchQuery) {
        if (isSearching) searchByName(visibleSpecs, searchQuery) { it.displayLabel } else sections.ungrouped
    }
    val groupedSpecs: (String) -> List<ModelSpec> = { endpoint ->
        if (isSearching) emptyList() else sections.byEndpoint[endpoint].orEmpty()
    }
    // The section holding the selected spec opens until the user toggles it.
    val selectedSectionKey = remember(sections, selectedSpec) {
        when (val section = sections.sectionOf(selectedSpec)) {
            is SpecSection.Endpoint ->
                if (section.key == EndpointConstants.AGENTS) MY_AGENTS_EXPAND_KEY else endpointExpandKey(section.key)
            is SpecSection.Custom -> specGroupExpandKey(section.name)
            null -> null
        }
    }
    val isOpen: (String) -> Boolean = { key -> expandedGroups[key] ?: (key == selectedSectionKey) }
    val specIcons = remember(visibleSpecs, agents, serverUrl) {
        visibleSpecs.associate { it.name to it.menuIconSource(agents, serverUrl) }
    }

    // Filter agents by search query (fuzzy matching), sorted by score when searching.
    // When not searching, pinned agents (v0.8.5+) sort to the top of the list.
    val filteredAgents = remember(agents, searchQuery, favoriteAgentIds, modelSpecs, modelSelect) {
        val agents = agents.distinctBy { it.id }
            .takeIf { modelSpecs.showsRawEndpoint(EndpointConstants.AGENTS, modelSelect) }
            .orEmpty()
        val base = if (!isSearching) {
            agents
        } else {
            searchByName(agents, searchQuery) { it.name ?: it.id }
        }
        if (isSearching) {
            base
        } else {
            base.sortedByDescending { it.id in favoriteAgentIds }
        }
    }

    // Optional dedicated "Starred" section (mobile-only). Shown only when not searching
    // (search already reorders by relevance) and the preference is not OFF. Starred items
    // are also still rendered within their own groups below — nothing is removed.
    val showStarred = !isSearching && starredDisplay != StarredModelsDisplay.OFF
    // favoriteAgentIds / favoriteModelKeys are LinkedHashSets from FavoritesDelegate.publish,
    // so iterating them preserves the server-side pin order.
    val agentsById = remember(agents, modelSpecs, modelSelect) {
        agents.takeIf { modelSpecs.showsRawEndpoint(EndpointConstants.AGENTS, modelSelect) }.orEmpty().associateBy { it.id }
    }
    val starredAgents = remember(agentsById, favoriteAgentIds, showStarred) {
        if (!showStarred) emptyList() else favoriteAgentIds.mapNotNull { agentsById[it] }
    }
    val starredModels = remember(filteredByEndpoint, favoriteModelKeys, showStarred) {
        if (!showStarred) {
            emptyList()
        } else {
            starredModels(favoriteModelKeys, filteredByEndpoint)
        }
    }
    val starredSpecs = remember(visibleSpecs, favoriteSpecNames, showStarred) {
        if (!showStarred) emptyList() else starredSpecs(favoriteSpecNames, visibleSpecs)
    }
    val hasStarredRaw = starredAgents.isNotEmpty() || starredModels.isNotEmpty()
    val hasStarred = starredSpecs.isNotEmpty() || hasStarredRaw
    val starredItemsVisible = showStarred && hasStarred &&
        (starredDisplay == StarredModelsDisplay.TOP || expandedGroups[STARRED_EXPAND_KEY] == true)
    // Under enforce the note heads the first raw row: the starred agents and models when they show.
    val specOnlyNoteInStarred = !rawSelectable && starredItemsVisible && hasStarredRaw

    // A spec on an endpoint without a usable key opens its key instead; its row says so first.
    val onSpecClick: (ModelSpec, String?) -> Unit = { spec, endpoint ->
        if (endpoint != null && endpointKeyStates[endpoint].blocksSelection()) onSetApiKey(endpoint) else onSpecSelect(spec)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .padding(bottom = 32.dp),
    ) {
        header?.invoke()
        // Banner applies its own 16dp inset, so it sits outside the Column's
        // horizontal padding to line up with the other elements.
        if (errorMessage != null) {
            ErrorBanner(message = errorMessage, onDismiss = onErrorDismiss)
        }
        if (showTitle) {
            Text(
                text = stringResource(Res.string.select_a_model),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
            )
        }

        AdaptiveOutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(stringResource(Res.string.model_selector_search)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            singleLine = true,
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = stringResource(Res.string.cd_clear_search),
                        )
                    }
                }
            },
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Drag-select reaches only the rows on screen: the list doesn't auto-scroll under it.
        val dragSelection = rememberListDragSelection()
        val specRow: @Composable LazyItemScope.(ModelSpec, Dp) -> Unit = { spec, verticalPadding ->
            val endpoint = spec.toSeed()?.endpoint
            SpecListItem(
                spec = spec,
                icon = specIcons[spec.name],
                isSelected = selectedSpec == spec.name,
                onClick = { onSpecClick(spec, endpoint) },
                dragSelection = dragSelection,
                keyHint = endpoint?.let { endpointKeyStates[it].keyHint() },
                isFavorite = spec.name in favoriteSpecNames,
                onToggleFavorite = onToggleSpecFavorite?.let { toggle -> { toggle(spec.name) } },
                verticalPadding = verticalPadding,
            )
        }
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .listDragSelection(dragSelection, holdOnly = true),
        ) {
            // Optional "Starred" section at the very top (mobile-only). Starred items
            // are duplicated here — they still render in their own groups below.
            if (showStarred && hasStarred) {
                // GROUPED renders a collapsible header and respects its toggle; TOP is a
                // flat, always-shown list closed off with a divider. OFF never reaches here.
                if (starredDisplay == StarredModelsDisplay.GROUPED) {
                    item(key = "hdr:starred") {
                        EndpointGroupHeader(
                            endpointName = STARRED_GROUP_KEY,
                            displayLabel = stringResource(Res.string.starred_group),
                            modelCount = starredSpecs.size + starredAgents.size + starredModels.size,
                            isExpanded = starredItemsVisible,
                            iconUrl = null,
                            leadingIcon = Icons.Default.Star,
                            onToggle = { expandedGroups[STARRED_EXPAND_KEY] = !starredItemsVisible },
                            dragSelection = dragSelection,
                        )
                    }
                }
                if (starredItemsVisible) {
                    // The flat TOP list reads as a dense quick-access strip; GROUPED keeps
                    // the standard row rhythm since it sits behind a collapsible header.
                    val starredPadding = if (starredDisplay == StarredModelsDisplay.TOP) {
                        StarredItemVerticalPadding
                    } else {
                        ListItemVerticalPadding
                    }
                    items(starredSpecs, key = { "starred:spec:${it.name}" }, contentType = { "spec" }) { spec ->
                        specRow(spec, starredPadding)
                    }
                    if (specOnlyNoteInStarred) {
                        item(key = "note:spec-only", contentType = "note") { SpecOnlyNote() }
                    }
                    items(starredAgents, key = { "starred:agent:${it.id}" }, contentType = { "agent" }) { agent ->
                        AgentListItem(
                            agent = agent,
                            isSelected = selectedEndpoint == EndpointConstants.AGENTS && agent.id == rawSelectedModel,
                            enabled = rawSelectable,
                            serverUrl = serverUrl,
                            onClick = { onModelSelect(EndpointConstants.AGENTS, agent.id) },
                            isFavorite = true,
                            onToggleFavorite = onToggleAgentFavorite?.let { toggle -> { toggle(agent.id) } },
                            verticalPadding = starredPadding,
                            dragSelection = dragSelection,
                        )
                    }
                    items(
                        starredModels,
                        key = { (endpoint, model) -> "starred:model:$endpoint::$model" },
                        contentType = { "model" },
                    ) { (endpoint, model) ->
                        ModelListItem(
                            model = model,
                            isSelected = endpoint == selectedEndpoint && model == rawSelectedModel,
                            enabled = rawSelectable,
                            isFavorite = true,
                            onClick = { onModelSelect(endpoint, model) },
                            onToggleFavorite = onToggleModelFavorite?.let { toggle -> { toggle(endpoint, model) } },
                            verticalPadding = starredPadding,
                            dragSelection = dragSelection,
                        )
                    }
                }
                if (starredDisplay == StarredModelsDisplay.TOP) {
                    item(key = "div:starred") {
                        AdaptiveDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                }
            }

            items(
                items = shownSpecs,
                key = { "spec:${it.name}" },
                contentType = { "spec" },
            ) { spec ->
                specRow(spec, ListItemVerticalPadding)
            }
            // Shown once: the Starred-section placement uses the same "note:spec-only" key, and a
            // duplicate LazyColumn key crashes.
            if (!rawSelectable && !specOnlyNoteInStarred && (filteredAgents.isNotEmpty() || filteredByEndpoint.isNotEmpty())) {
                item(key = "note:spec-only", contentType = "note") { SpecOnlyNote() }
            }

            // "My Agents" group (shown first, like the web frontend)
            if (filteredAgents.isNotEmpty()) {
                val agentsExpanded = isSearching || isOpen(MY_AGENTS_EXPAND_KEY)
                val agentSpecs = groupedSpecs(EndpointConstants.AGENTS)
                item(key = "hdr:agents") {
                    EndpointGroupHeader(
                        endpointName = EndpointConstants.AGENTS,
                        displayLabel = stringResource(Res.string.model_selector_my_agents),
                        modelCount = agentSpecs.size + filteredAgents.size,
                        isExpanded = agentsExpanded,
                        iconUrl = null,
                        onToggle = { if (!isSearching) expandedGroups[MY_AGENTS_EXPAND_KEY] = !agentsExpanded },
                        dragSelection = dragSelection,
                    )
                }
                if (agentsExpanded) {
                    items(agentSpecs, key = { "ep:${EndpointConstants.AGENTS}:spec:${it.name}" }, contentType = { "spec" }) { spec ->
                        specRow(spec, ListItemVerticalPadding)
                    }
                    items(filteredAgents, key = { "agent:${it.id}" }, contentType = { "agent" }) { agent ->
                        AgentListItem(
                            agent = agent,
                            isSelected = selectedEndpoint == EndpointConstants.AGENTS && agent.id == rawSelectedModel,
                            enabled = rawSelectable,
                            serverUrl = serverUrl,
                            onClick = { onModelSelect(EndpointConstants.AGENTS, agent.id) },
                            isFavorite = agent.id in favoriteAgentIds,
                            onToggleFavorite = onToggleAgentFavorite?.let { toggle -> { toggle(agent.id) } },
                            dragSelection = dragSelection,
                        )
                    }
                }
            }

            // Endpoint model groups
            filteredByEndpoint.forEach { (endpointName, models) ->
                val baseFiltered = if (!isSearching) models else searchByName(models, searchQuery) { it }
                // Pinned models (v0.8.5+) sort to the top when not searching.
                val filteredModels = if (isSearching) {
                    baseFiltered
                } else {
                    baseFiltered.sortedByDescending { "$endpointName::$it" in favoriteModelKeys }
                }
                val endpointSpecs = groupedSpecs(endpointName)
                if (filteredModels.isNotEmpty() || endpointSpecs.isNotEmpty()) {
                    val config = endpointConfigs[endpointName]
                    val displayLabel = config?.modelDisplayLabel ?: endpointName
                    // Auto-expand while searching so matches in collapsed groups are visible;
                    // otherwise use manual toggle state.
                    val isExpanded = isSearching || isOpen(endpointExpandKey(endpointName))
                    // Endpoints with userProvide=true and Unset/Expired key render disabled
                    // with an "Add key" action. Loading and absent states fail-open.
                    val keyHint = endpointKeyStates[endpointName].keyHint()
                    val effectiveExpanded = isExpanded && keyHint == null
                    val headerIcon = resolveIconSource(config?.iconURL, endpointName, serverUrl)
                    item(key = "hdr:ep:$endpointName") {
                        EndpointGroupHeader(
                            endpointName = headerIcon.endpointName ?: endpointName,
                            displayLabel = displayLabel,
                            modelCount = endpointSpecs.size + filteredModels.size,
                            isExpanded = effectiveExpanded,
                            iconUrl = headerIcon.imageUrl,
                            onToggle = { if (!isSearching) expandedGroups[endpointExpandKey(endpointName)] = !isExpanded },
                            keyHint = keyHint,
                            onSetApiKey = { onSetApiKey(endpointName) },
                            dragSelection = dragSelection,
                        )
                    }
                    if (effectiveExpanded) {
                        items(endpointSpecs, key = { "ep:$endpointName:spec:${it.name}" }, contentType = { "spec" }) { spec ->
                            specRow(spec, ListItemVerticalPadding)
                        }
                        items(filteredModels, key = { "model:$endpointName::$it" }, contentType = { "model" }) { model ->
                            ModelListItem(
                                model = model,
                                isSelected = endpointName == selectedEndpoint && model == rawSelectedModel,
                                enabled = rawSelectable,
                                isFavorite = "$endpointName::$model" in favoriteModelKeys,
                                onClick = { onModelSelect(endpointName, model) },
                                onToggleFavorite = onToggleModelFavorite?.let { toggle -> { toggle(endpointName, model) } },
                                dragSelection = dragSelection,
                            )
                        }
                    }
                }
            }

            // Spec groups naming no shown endpoint come last, as on web.
            if (!isSearching) {
                sections.custom.forEach { group ->
                    val expandKey = specGroupExpandKey(group.name)
                    val isExpanded = isOpen(expandKey)
                    val groupIcon = group.icon?.let { resolveIconSource(it, null, serverUrl) }
                    item(key = "hdr:$expandKey") {
                        EndpointGroupHeader(
                            endpointName = groupIcon?.endpointName ?: group.name,
                            displayLabel = group.name,
                            modelCount = group.specs.size,
                            isExpanded = isExpanded,
                            iconUrl = groupIcon?.imageUrl,
                            onToggle = { expandedGroups[expandKey] = !isExpanded },
                            dragSelection = dragSelection,
                            leadingIcon = Icons.Outlined.Folder.takeIf { groupIcon == null },
                        )
                    }
                    if (isExpanded) {
                        items(group.specs, key = { "$expandKey:spec:${it.name}" }, contentType = { "spec" }) { spec ->
                            specRow(spec, ListItemVerticalPadding)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun EndpointGroupHeader(
    endpointName: String,
    displayLabel: String,
    modelCount: Int,
    isExpanded: Boolean,
    iconUrl: String?,
    onToggle: () -> Unit,
    dragSelection: ListDragSelection,
    /** Non-null when the endpoint has no usable key: the group is greyed behind "Add key". */
    keyHint: KeyHint? = null,
    onSetApiKey: () -> Unit = {},
    /** When set, renders this vector instead of an [EndpointIcon] (used by the Starred group). */
    leadingIcon: ImageVector? = null,
) {
    val needsKey = keyHint != null
    // Add key drops under the label rather than squeeze it narrower than MinGroupLabelWidth.
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            // Drop the clickable modifier entirely when needsKey — Compose's
            // `clickable(enabled = false)` still consumes touch events and
            // announces "disabled" to TalkBack/VoiceOver. Only the inner
            // AddKeyButton remains interactive in that branch.
            .then(
                if (needsKey) {
                    Modifier.padding(horizontal = 4.dp)
                } else {
                    Modifier
                        .dragSelectRow(dragSelection, onClick = onToggle)
                        .padding(horizontal = 4.dp)
                },
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.End,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        // Disabled icon + label use Material's standard 0.38 disabled alpha. The
        // CTA chip below stays full-opacity so the action remains discoverable.
        val labelAlpha = if (needsKey) 0.38f else 1f
        Row(
            modifier = Modifier.weight(1f).widthIn(min = MinGroupLabelWidth),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .size(IconSize)
                        .alpha(labelAlpha)
                        .dragSelectRowIcon(),
                )
            } else {
                EndpointIcon(
                    endpointName = endpointName,
                    iconUrl = iconUrl,
                    size = IconSize,
                    contentDescription = null,
                    glyphTint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.alpha(labelAlpha).dragSelectRowIcon(),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "$displayLabel ($modelCount)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.alpha(labelAlpha),
                )
                // Full opacity: the reason the group is greyed is not itself disabled.
                if (keyHint != null) KeyHintLine(keyHint)
            }
        }
        if (needsKey) {
            AddKeyButton(
                endpointLabel = displayLabel,
                onClick = onSetApiKey,
            )
        } else {
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = stringResource(
                    if (isExpanded) Res.string.cd_collapse_section else Res.string.cd_expand_section,
                    displayLabel,
                ),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private val MinGroupLabelWidth = 120.dp

/**
 * The "Add key" action on a greyed endpoint group, web's "Set API Key" pill. Tapping navigates to
 * Settings → Provider API Keys with the endpoint pre-targeted. The key icon is the one the
 * Provider Keys row in Settings uses.
 */
@Composable
private fun AddKeyButton(
    endpointLabel: String,
    onClick: () -> Unit,
) {
    val cd = stringResource(Res.string.cd_set_api_key, endpointLabel)
    AdaptiveFilledTonalButton(
        onClick = onClick,
        // Small: a 32dp button in a 20dp-icon header row. The touch target stays 48dp.
        modifier = Modifier.heightIn(min = AddKeyButtonHeight),
        contentPadding = PaddingValues(start = 12.dp, top = 4.dp, end = 16.dp, bottom = 4.dp),
    ) {
        Icon(
            imageVector = Icons.Default.VpnKey,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        // Read as "Add a key for <endpoint>": the visible label alone doesn't say which.
        Text(
            text = stringResource(Res.string.set_api_key_action),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            modifier = Modifier.semantics { contentDescription = cd },
        )
    }
}

private val AddKeyButtonHeight = 32.dp

/** "Needs your key" or "Key expired", under an endpoint group's or a spec row's name. */
@Composable
private fun KeyHintLine(hint: KeyHint) {
    Text(
        text = stringResource(
            when (hint) {
                KeyHint.NEEDS_KEY -> Res.string.model_selector_needs_key
                KeyHint.EXPIRED -> Res.string.model_selector_key_expired
            },
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun LazyItemScope.ModelListItem(
    model: String,
    isSelected: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: (() -> Unit)?,
    dragSelection: ListDragSelection,
    verticalPadding: Dp = ListItemVerticalPadding,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.dragSelectRow(dragSelection, onClick = onClick) else Modifier.alpha(DisabledAlpha))
            .padding(vertical = verticalPadding, horizontal = 16.dp)
            .animateItem(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = model,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        if (isSelected) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                Icons.Default.Check,
                contentDescription = stringResource(Res.string.cd_selected),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        if (onToggleFavorite != null && enabled) {
            Spacer(modifier = Modifier.width(4.dp))
            FavoriteStarButton(
                isFavorite = isFavorite,
                onToggle = onToggleFavorite,
                modifier = Modifier.dragSelectExclude(dragSelection),
            )
        }
    }
}

@Composable
private fun LazyItemScope.AgentListItem(
    agent: Agent,
    isSelected: Boolean,
    serverUrl: String,
    onClick: () -> Unit,
    dragSelection: ListDragSelection,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    verticalPadding: Dp = ListItemVerticalPadding,
    enabled: Boolean = true,
) {
    val agentName = agent.name ?: agent.id
    val resolvedAvatarUrl = resolveAvatarUrl(agent.avatarUrl, serverUrl)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.dragSelectRow(dragSelection, onClick = onClick) else Modifier.alpha(DisabledAlpha))
            .padding(vertical = verticalPadding, horizontal = 16.dp)
            .animateItem(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Agent avatar
        if (resolvedAvatarUrl != null) {
            AsyncImage(
                model = resolvedAvatarUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(IconSize)
                    .clip(CircleShape)
                    .dragSelectRowIcon(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Create,
                contentDescription = null,
                modifier = Modifier.size(IconSize).dragSelectRowIcon(),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = agentName,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        if (agent.isPublic == true) {
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Outlined.Public,
                contentDescription = stringResource(Res.string.cd_public_agent),
                modifier = Modifier.size(16.dp),
                tint = Color(0xFF4CAF50),
            )
        }
        if (isSelected) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                Icons.Default.Check,
                contentDescription = stringResource(Res.string.cd_selected),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        if (onToggleFavorite != null && enabled) {
            Spacer(modifier = Modifier.width(4.dp))
            FavoriteStarButton(
                isFavorite = isFavorite,
                onToggle = onToggleFavorite,
                modifier = Modifier.dragSelectExclude(dragSelection),
            )
        }
    }
}

/**
 * A named model spec: its own icon, label and description, apart from the raw model it runs on.
 * [keyHint] is set when the spec's endpoint has no usable key, so the row says a tap opens its key.
 */
@Composable
internal fun LazyItemScope.SpecListItem(
    spec: ModelSpec,
    /** Null when the spec hides its icon (`showIconInMenu: false`); the label keeps its column. */
    icon: IconSource?,
    isSelected: Boolean,
    onClick: () -> Unit,
    dragSelection: ListDragSelection,
    keyHint: KeyHint? = null,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    verticalPadding: Dp = ListItemVerticalPadding,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .dragSelectRow(dragSelection, onClick = onClick)
            .padding(vertical = verticalPadding, horizontal = 16.dp)
            .animateItem(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            EndpointIcon(
                endpointName = icon.endpointName,
                iconUrl = icon.imageUrl,
                size = IconSize,
                contentDescription = null,
                glyphTint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.dragSelectRowIcon(),
            )
        } else {
            Spacer(modifier = Modifier.width(IconSize))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = spec.displayLabel,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (keyHint != null) KeyHintLine(keyHint)
            spec.description?.takeIf { it.isNotBlank() }?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (isSelected) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                Icons.Default.Check,
                contentDescription = stringResource(Res.string.cd_selected),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        if (onToggleFavorite != null) {
            Spacer(modifier = Modifier.width(4.dp))
            FavoriteStarButton(
                isFavorite = isFavorite,
                onToggle = onToggleFavorite,
                modifier = Modifier.dragSelectExclude(dragSelection),
            )
        }
    }
}

/** Under `enforce`: the raw rows below are listed, greyed, but can't be sent. */
@Composable
private fun SpecOnlyNote() {
    Text(
        text = stringResource(Res.string.model_selector_spec_only_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

private const val DisabledAlpha = 0.38f

@Composable
private fun FavoriteStarButton(
    isFavorite: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onToggle, modifier = modifier.size(32.dp)) {
        Icon(
            imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
            contentDescription = stringResource(
                if (isFavorite) Res.string.cd_unpin_favorite else Res.string.cd_pin_favorite,
            ),
            tint = if (isFavorite) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(18.dp),
        )
    }
}
