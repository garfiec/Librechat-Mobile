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
import com.garfiec.librechat.core.model.media.ProviderId
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
import com.garfiec.librechat.feature.chat.util.HeaderIcon
import com.garfiec.librechat.feature.chat.util.HeaderLabel
import com.garfiec.librechat.feature.chat.util.KeyHint
import com.garfiec.librechat.feature.chat.util.PickerInputs
import com.garfiec.librechat.feature.chat.util.PickerRow
import com.garfiec.librechat.feature.chat.util.blocksSelection
import com.garfiec.librechat.feature.chat.util.menuIconSource
import com.garfiec.librechat.feature.chat.util.pickerRows
import org.jetbrains.compose.resources.stringResource

private val IconSize = 20.dp

/** Sentinel endpoint name the optional "Starred" section's header passes to [EndpointGroupHeader]. */
private const val STARRED_GROUP_KEY = "__starred__"

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
    // Only user toggles land here; an absent key is collapsed unless it holds the selected spec.
    // Not seeded — the sheet can mount before models/agents load, and a seed would miss late arrivals.
    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }
    val inputs = PickerInputs(
        endpointConfigs = endpointConfigs,
        availableModels = availableModels,
        agents = agents,
        agentsLoaded = agentsLoaded,
        modelSpecs = modelSpecs,
        modelSelect = modelSelect,
        selectedEndpoint = selectedEndpoint,
        selectedModel = selectedModel,
        selectedSpec = selectedSpec,
        searchQuery = searchQuery,
        favoriteAgentIds = favoriteAgentIds,
        favoriteModelKeys = favoriteModelKeys,
        favoriteSpecNames = favoriteSpecNames,
        starredDisplay = starredDisplay,
        expandedGroups = expandedGroups.toMap(),
        endpointKeyStates = endpointKeyStates,
    )
    val rows = remember(inputs) { pickerRows(inputs) }
    val specIcons = remember(modelSpecs, agents, serverUrl, endpointConfigs) {
        modelSpecs?.list.orEmpty().associate { it.name to it.menuIconSource(agents, serverUrl, endpointConfigs) }
    }

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
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .listDragSelection(dragSelection, holdOnly = true),
        ) {
            items(rows, key = { it.key }, contentType = { it.contentType }) { row ->
                when (row) {
                    is PickerRow.Header -> PickerHeader(
                        row = row,
                        endpointConfigs = endpointConfigs,
                        serverUrl = serverUrl,
                        onToggle = { row.expandKey?.let { expandedGroups[it] = !row.expanded } },
                        onSetApiKey = onSetApiKey,
                        dragSelection = dragSelection,
                    )
                    is PickerRow.Spec -> SpecListItem(
                        spec = row.spec,
                        icon = specIcons[row.spec.name],
                        isSelected = row.isSelected,
                        onClick = { onSpecClick(row.spec, row.endpoint) },
                        dragSelection = dragSelection,
                        keyHint = row.keyHint,
                        isFavorite = row.isFavorite,
                        onToggleFavorite = onToggleSpecFavorite?.let { toggle -> { toggle(row.spec.name) } },
                        verticalPadding = rowPadding(row.dense),
                    )
                    is PickerRow.AgentRow -> AgentListItem(
                        agent = row.agent,
                        isSelected = row.isSelected,
                        enabled = row.enabled,
                        serverUrl = serverUrl,
                        onClick = { onModelSelect(EndpointConstants.AGENTS, row.agent.id) },
                        isFavorite = row.isFavorite,
                        onToggleFavorite = onToggleAgentFavorite?.let { toggle -> { toggle(row.agent.id) } },
                        verticalPadding = rowPadding(row.dense),
                        dragSelection = dragSelection,
                    )
                    is PickerRow.Model -> ModelListItem(
                        model = row.model,
                        isSelected = row.isSelected,
                        enabled = row.enabled,
                        isFavorite = row.isFavorite,
                        onClick = { onModelSelect(row.endpoint, row.model) },
                        onToggleFavorite = onToggleModelFavorite?.let { toggle -> { toggle(row.endpoint, row.model) } },
                        verticalPadding = rowPadding(row.dense),
                        dragSelection = dragSelection,
                    )
                    PickerRow.SpecOnlyNote -> SpecOnlyNote()
                    PickerRow.StarredDivider ->
                        AdaptiveDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
            }
        }
    }
}

/** The flat Starred TOP list is a dense quick-access strip; every other row keeps the standard rhythm. */
private fun rowPadding(dense: Boolean): Dp = if (dense) StarredItemVerticalPadding else ListItemVerticalPadding

@Composable
private fun PickerHeader(
    row: PickerRow.Header,
    endpointConfigs: Map<String, EndpointConfig>,
    serverUrl: String,
    onToggle: () -> Unit,
    onSetApiKey: (endpointName: String) -> Unit,
    dragSelection: ListDragSelection,
) {
    val label = when (val label = row.label) {
        HeaderLabel.Starred -> stringResource(Res.string.starred_group)
        HeaderLabel.MyAgents -> stringResource(Res.string.model_selector_my_agents)
        is HeaderLabel.Text -> label.text
    }
    var iconName = label
    var iconUrl: String? = null
    var provider: ProviderId? = null
    var leadingIcon: ImageVector? = null
    when (val icon = row.icon) {
        HeaderIcon.Star -> {
            iconName = STARRED_GROUP_KEY
            leadingIcon = Icons.Default.Star
        }
        HeaderIcon.Agents -> iconName = EndpointConstants.AGENTS
        is HeaderIcon.Endpoint -> {
            val source =
                resolveIconSource(endpointConfigs[icon.endpoint]?.iconURL, icon.endpoint, serverUrl, endpointConfigs)
            iconName = source.endpointName ?: icon.endpoint
            iconUrl = source.imageUrl
            provider = source.provider
        }
        is HeaderIcon.Group -> {
            // A group icon naming an endpoint key draws that endpoint's served brand.
            val endpoint = icon.icon.takeIf { it in endpointConfigs }
            val source = resolveIconSource(icon.icon, endpoint, serverUrl, endpointConfigs)
            iconName = source.endpointName ?: label
            iconUrl = source.imageUrl
            provider = source.provider
        }
        HeaderIcon.Folder -> leadingIcon = Icons.Outlined.Folder
    }
    EndpointGroupHeader(
        endpointName = iconName,
        displayLabel = label,
        modelCount = row.count,
        isExpanded = row.expanded,
        iconUrl = iconUrl,
        onToggle = onToggle,
        dragSelection = dragSelection,
        keyHint = row.keyHint,
        onSetApiKey = { row.keyEndpoint?.let(onSetApiKey) },
        leadingIcon = leadingIcon,
        provider = provider,
    )
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
    provider: ProviderId? = null,
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
                    provider = provider,
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
                provider = icon.provider,
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

/** Under `enforce`, atop the list: the greyed raw rows are listed but can't be sent. */
@Composable
private fun SpecOnlyNote() {
    Text(
        text = stringResource(Res.string.model_selector_spec_only_top_note),
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
