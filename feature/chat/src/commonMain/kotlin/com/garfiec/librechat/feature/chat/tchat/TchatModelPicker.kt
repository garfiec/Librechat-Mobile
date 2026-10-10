package com.garfiec.librechat.feature.chat.tchat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.ui.components.AdaptiveDropdownMenu
import com.garfiec.librechat.core.ui.components.AdaptiveFilterChip
import com.garfiec.librechat.core.ui.components.AdaptiveModalBottomSheet
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedTextField
import com.garfiec.librechat.core.ui.components.EndpointIcon
import com.garfiec.librechat.core.ui.components.ErrorBanner
import com.garfiec.librechat.core.ui.components.LowProfileDragHandle
import com.garfiec.librechat.core.ui.components.clearFocusOnTap
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.tchat_all
import com.garfiec.librechat.feature.chat.resources.tchat_choose_model
import com.garfiec.librechat.feature.chat.resources.tchat_clear_search
import com.garfiec.librechat.feature.chat.resources.tchat_current
import com.garfiec.librechat.feature.chat.resources.tchat_free
import com.garfiec.librechat.feature.chat.resources.tchat_image_gen_intro
import com.garfiec.librechat.feature.chat.resources.tchat_image_gen_on
import com.garfiec.librechat.feature.chat.resources.tchat_no_results
import com.garfiec.librechat.feature.chat.resources.tchat_pin
import com.garfiec.librechat.feature.chat.resources.tchat_pinned
import com.garfiec.librechat.feature.chat.resources.tchat_search_all_makers
import com.garfiec.librechat.feature.chat.resources.tchat_search_models
import com.garfiec.librechat.feature.chat.resources.tchat_unpin
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/*
 * The Tchat model picker (design/system/components/ModelPicker): models grouped by the company
 * that makes them, a maker rail, a Current card, Pinned, and Image Gen as a toggle. Replaces
 * upstream's endpoint-grouped ModelSelectorSheet at every call site; that file stays untouched so
 * upstream syncs merge cleanly.
 */

private val RowMinHeight = 56.dp
private val MarkSize = 28.dp
private val SmallMarkSize = 20.dp

/** The callbacks a picker host wires to the ViewModel. */
class TchatPickerActions(
    val onSelectModel: (endpoint: String, model: String) -> Unit,
    /** An Image Gen row: toggles Image Gen with that entry. */
    val onImagePicked: (name: String) -> Unit,
    val onToggleModelPin: (endpoint: String, model: String) -> Unit,
    val onToggleAgentPin: (agentId: String) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TchatModelPickerSheet(
    picker: TchatPicker,
    actions: TchatPickerActions,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialFilter: String = ALL_GROUPS,
    showPinned: Boolean = true,
    serverUrl: String = "",
    errorMessage: String? = null,
    onErrorDismiss: () -> Unit = {},
) {
    AdaptiveModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { LowProfileDragHandle() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        modifier = modifier.clearFocusOnTap(),
    ) {
        TchatModelPickerContent(
            picker = picker,
            actions = actions,
            onPick = onDismiss,
            initialFilter = initialFilter,
            showPinned = showPinned,
            serverUrl = serverUrl,
            errorMessage = errorMessage,
            onErrorDismiss = onErrorDismiss,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * The picker body, no sheet chrome. A pick calls the action, then [onPick] (a model or agent
 * closes the host; an Image Gen toggle does too, as on web). Reopening starts again at
 * [initialFilter] with no search, because the host drops this composable when it closes.
 */
@Composable
fun TchatModelPickerContent(
    picker: TchatPicker,
    actions: TchatPickerActions,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
    initialFilter: String = ALL_GROUPS,
    showPinned: Boolean = true,
    serverUrl: String = "",
    errorMessage: String? = null,
    onErrorDismiss: () -> Unit = {},
    header: (@Composable () -> Unit)? = null,
) {
    var query by remember { mutableStateOf("") }
    var filter by remember(initialFilter) { mutableStateOf(initialFilter) }
    // A filter whose group vanished (Image Gen withdrawn) falls back to All.
    val effectiveFilter = filter.takeIf { it == ALL_GROUPS || picker.groups.any { g -> g.id == it } } ?: ALL_GROUPS
    val visible = remember(picker.groups, effectiveFilter, query) {
        filterPickerGroups(picker.groups, effectiveFilter, query)
    }
    val unfiltered = effectiveFilter == ALL_GROUPS && query.isBlank()
    val listState = rememberLazyListState()
    LaunchedEffect(effectiveFilter, query) { listState.scrollToItem(0) }
    val pinnedTitle = stringResource(Res.string.tchat_pinned)

    val select: (PickerRow) -> Unit = { row ->
        when (val action = row.action) {
            is PickerAction.Select -> actions.onSelectModel(action.endpoint, action.model)
            is PickerAction.Image -> actions.onImagePicked(action.name)
        }
        onPick()
    }
    val togglePin: (PickerRow) -> Unit = { row ->
        val action = row.action
        if (action is PickerAction.Select) {
            if (row.agent != null) actions.onToggleAgentPin(action.model) else actions.onToggleModelPin(action.endpoint, action.model)
        }
    }

    Column(modifier = modifier.fillMaxWidth().imePadding()) {
        header?.invoke()
        if (errorMessage != null) ErrorBanner(message = errorMessage, onDismiss = onErrorDismiss)
        Text(
            text = stringResource(Res.string.tchat_choose_model),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp).semantics { heading() },
        )
        AdaptiveOutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(Res.string.tchat_search_models)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = stringResource(Res.string.tchat_clear_search))
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        MakerRail(
            picker = picker,
            filter = effectiveFilter,
            onFilter = { filter = it },
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            val current = picker.current
            if (unfiltered && current != null) {
                item(key = "current") { CurrentCard(current, picker.activeImage?.label, serverUrl) }
            }
            if (unfiltered && showPinned && picker.pinned.isNotEmpty()) {
                pickerSection(
                    key = "pinned",
                    title = pinnedTitle,
                    caption = picker.pinned.size.toString(),
                    mark = { Icon(Icons.Default.PushPin, null, Modifier.size(SmallMarkSize)) },
                    rows = picker.pinned,
                    showRowMarks = true,
                    serverUrl = serverUrl,
                    onSelect = select,
                    onTogglePin = togglePin,
                )
            }
            visible.forEach { group ->
                pickerSection(
                    key = group.id,
                    title = group.label,
                    caption = listOfNotNull(group.caption, group.rows.size.toString()).joinToString(" · "),
                    mark = { GroupMark(group) },
                    rows = group.rows,
                    showRowMarks = group.kind != PickerGroupKind.MAKER,
                    serverUrl = serverUrl,
                    onSelect = select,
                    onTogglePin = togglePin,
                    intro = if (group.kind == PickerGroupKind.IMAGE) Res.string.tchat_image_gen_intro else null,
                )
            }
            if (visible.isEmpty()) {
                item(key = "empty") {
                    EmptyResults(
                        query = query,
                        filtered = effectiveFilter != ALL_GROUPS,
                        onSearchAll = { filter = ALL_GROUPS },
                    )
                }
            }
        }
    }
}

@Composable
private fun MakerRail(picker: TchatPicker, filter: String, onFilter: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = ALL_GROUPS) {
            RailPill(
                label = "${stringResource(Res.string.tchat_all)} ${picker.total}",
                selected = filter == ALL_GROUPS,
                isCurrent = false,
                onClick = { onFilter(ALL_GROUPS) },
            )
        }
        items(picker.groups, key = { it.id }) { group ->
            RailPill(
                label = "${group.label} ${group.rows.size}",
                selected = filter == group.id,
                isCurrent = picker.current?.groupId == group.id ||
                    (group.kind == PickerGroupKind.IMAGE && picker.activeImage != null),
                onClick = { onFilter(group.id) },
            )
        }
    }
}

@Composable
private fun RailPill(label: String, selected: Boolean, isCurrent: Boolean, onClick: () -> Unit) {
    val currentWord = stringResource(Res.string.tchat_current)
    AdaptiveFilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        trailingIcon = if (isCurrent) {
            { Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)) }
        } else {
            null
        },
        modifier = if (isCurrent) Modifier.semantics { stateDescription = currentWord } else Modifier,
    )
}

@Composable
private fun CurrentCard(current: PickerCurrent, imageLabel: String?, serverUrl: String) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.primaryContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (current.agent != null) {
            AgentMark(current.agent, serverUrl, MarkSize)
        } else {
            OwnerMark(current.owner ?: OwnerId.OTHER, MarkSize)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = current.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (current.free) {
                    Spacer(Modifier.width(6.dp))
                    FreeTag()
                }
            }
            Text(
                text = listOfNotNull(current.caption, current.detail).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (imageLabel != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Image, null, Modifier.size(14.dp), tint = colors.onPrimaryContainer)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = imageLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onPrimaryContainer,
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Default.Check, null, Modifier.size(18.dp), tint = colors.onPrimaryContainer)
        Spacer(Modifier.width(4.dp))
        Text(
            text = stringResource(Res.string.tchat_current),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onPrimaryContainer,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Suppress("LongParameterList")
private fun LazyListScope.pickerSection(
    key: String,
    title: String,
    caption: String,
    mark: @Composable () -> Unit,
    rows: List<PickerRow>,
    showRowMarks: Boolean,
    serverUrl: String,
    onSelect: (PickerRow) -> Unit,
    onTogglePin: (PickerRow) -> Unit,
    intro: StringResource? = null,
) {
    stickyHeader(key = "header_$key", contentType = "header") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .semantics(mergeDescendants = true) { heading() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            mark()
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(6.dp))
            Text(caption, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (intro != null) {
        item(key = "intro_$key", contentType = "intro") {
            Text(
                text = stringResource(intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
    items(rows, key = { "${key}_${it.key}" }, contentType = { "row" }) { row ->
        PickerRowItem(
            row = row,
            showMark = showRowMarks,
            serverUrl = serverUrl,
            onClick = { onSelect(row) },
            onTogglePin = { onTogglePin(row) },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PickerRowItem(
    row: PickerRow,
    showMark: Boolean,
    serverUrl: String,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }
    val pinLabel = stringResource(if (row.pinned == true) Res.string.tchat_unpin else Res.string.tchat_pin)
    val currentWord = stringResource(Res.string.tchat_current)
    val onWord = stringResource(Res.string.tchat_image_gen_on)
    val isImage = row.action is PickerAction.Image
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = RowMinHeight)
                .combinedClickable(
                    role = Role.Button,
                    onClick = onClick,
                    onLongClick = if (row.pinned != null) {
                        {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuOpen = true
                        }
                    } else {
                        null
                    },
                    onLongClickLabel = pinLabel.takeIf { row.pinned != null },
                )
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .semantics(mergeDescendants = true) {
                    if (row.selected) stateDescription = if (isImage) onWord else currentWord
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showMark) {
                when {
                    row.agent != null -> AgentMark(row.agent, serverUrl, MarkSize)
                    row.owner != null -> OwnerMark(row.owner, MarkSize)
                    row.detail != null -> OwnerMark(modelOwnerId(row.detail), MarkSize)
                }
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = row.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (row.free) {
                        Spacer(Modifier.width(6.dp))
                        FreeTag()
                    }
                    if (row.pinned == true) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = stringResource(Res.string.tchat_pinned),
                            modifier = Modifier.size(14.dp),
                            tint = colors.onSurfaceVariant,
                        )
                    }
                }
                if (row.detail != null) {
                    Text(
                        text = row.detail,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (row.selected) {
                Spacer(Modifier.width(8.dp))
                if (isImage) {
                    Text(onWord, style = MaterialTheme.typography.labelMedium, color = colors.primary)
                    Spacer(Modifier.width(4.dp))
                }
                Icon(Icons.Default.Check, contentDescription = null, tint = colors.primary)
            }
        }
        AdaptiveDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(pinLabel) },
                leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onTogglePin()
                },
            )
        }
    }
}

@Composable
private fun EmptyResults(query: String, filtered: Boolean, onSearchAll: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.tchat_no_results, query.trim()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (filtered) {
            TextButton(onClick = onSearchAll) { Text(stringResource(Res.string.tchat_search_all_makers)) }
        }
    }
}

/** Marks a model TensorGrid prices at zero. */
@Composable
internal fun FreeTag(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(Res.string.tchat_free),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

@Composable
private fun GroupMark(group: PickerGroup) {
    when (group.kind) {
        PickerGroupKind.MAKER -> OwnerMark(group.owner ?: OwnerId.OTHER, SmallMarkSize)
        PickerGroupKind.IMAGE -> ImageMark(SmallMarkSize)
        PickerGroupKind.AGENTS -> Icon(Icons.Outlined.Create, null, Modifier.size(SmallMarkSize))
    }
}

/** The endpoint drawables that carry a maker's real logo; other makers get a monogram tile. */
private fun ownerArtEndpoint(owner: OwnerId): String? = when (owner) {
    OwnerId.OPENAI -> "openAI"
    OwnerId.ANTHROPIC -> "anthropic"
    OwnerId.GOOGLE -> "google"
    else -> null
}

@Composable
internal fun OwnerMark(owner: OwnerId, size: Dp, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val tile = modifier
        .size(size)
        .clip(RoundedCornerShape(size / 4))
        .background(colors.surfaceContainerHighest)
    val art = ownerArtEndpoint(owner)
    Box(tile, contentAlignment = Alignment.Center) {
        if (art != null) {
            EndpointIcon(endpointName = art, iconUrl = null, size = size * 0.7f, glyphTint = colors.onSurface)
        } else {
            Text(
                text = ownerOf(owner).label.take(1),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
            )
        }
    }
}

@Composable
internal fun ImageMark(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 4))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(size * 0.65f))
    }
}

@Composable
internal fun AgentMark(agent: Agent, serverUrl: String, size: Dp) {
    val avatar = agent.avatarUrl?.let { if (it.startsWith("http")) it else "$serverUrl$it" }
    if (avatar != null) {
        AsyncImage(
            model = avatar,
            contentDescription = null,
            modifier = Modifier.size(size).clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Create, contentDescription = null, modifier = Modifier.size(size * 0.6f))
        }
    }
}
