package com.garfiec.librechat.feature.conversations.drawer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptiveButton
import com.garfiec.librechat.core.ui.components.AdaptiveFilterChip
import com.garfiec.librechat.core.ui.components.AdaptiveInputChip
import com.garfiec.librechat.core.ui.components.AdaptiveModalBottomSheet
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedButton
import com.garfiec.librechat.core.ui.components.AdaptivePillChoice
import com.garfiec.librechat.core.ui.components.AdaptiveRadioButton
import com.garfiec.librechat.core.ui.components.AdaptiveSwitch
import com.garfiec.librechat.core.ui.components.EndpointIcon
import com.garfiec.librechat.core.ui.components.LowProfileDragHandle
import com.garfiec.librechat.feature.conversations.resources.Res
import com.garfiec.librechat.feature.conversations.resources.cd_remove_named_filter
import com.garfiec.librechat.feature.conversations.resources.drawer_filter_any_bookmark
import com.garfiec.librechat.feature.conversations.resources.drawer_filter_any_time
import com.garfiec.librechat.feature.conversations.resources.drawer_filter_apply
import com.garfiec.librechat.feature.conversations.resources.drawer_filter_bookmarks
import com.garfiec.librechat.feature.conversations.resources.drawer_filter_clear
import com.garfiec.librechat.feature.conversations.resources.drawer_filter_created
import com.garfiec.librechat.feature.conversations.resources.drawer_filter_created_chip
import com.garfiec.librechat.feature.conversations.resources.drawer_filter_endpoint
import com.garfiec.librechat.feature.conversations.resources.drawer_filter_endpoint_limit
import com.garfiec.librechat.feature.conversations.resources.drawer_filter_has_attachments
import com.garfiec.librechat.feature.conversations.resources.drawer_filter_load_failed
import com.garfiec.librechat.feature.conversations.resources.drawer_filter_no_match_body
import com.garfiec.librechat.feature.conversations.resources.drawer_filter_no_match_title
import com.garfiec.librechat.feature.conversations.resources.drawer_filter_offline
import com.garfiec.librechat.feature.conversations.resources.drawer_filter_previous_30_days
import com.garfiec.librechat.feature.conversations.resources.drawer_filter_previous_7_days
import com.garfiec.librechat.feature.conversations.resources.drawer_filter_previous_year
import com.garfiec.librechat.feature.conversations.resources.drawer_filter_reset
import com.garfiec.librechat.feature.conversations.resources.drawer_filter_shared_only
import com.garfiec.librechat.feature.conversations.resources.drawer_filter_title
import com.garfiec.librechat.feature.conversations.resources.drawer_filter_today
import com.garfiec.librechat.feature.conversations.resources.drawer_filter_updated
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal fun FilterDateRange.label(): StringResource = when (this) {
    FilterDateRange.ANY -> Res.string.drawer_filter_any_time
    FilterDateRange.TODAY -> Res.string.drawer_filter_today
    FilterDateRange.WEEK -> Res.string.drawer_filter_previous_7_days
    FilterDateRange.MONTH -> Res.string.drawer_filter_previous_30_days
    FilterDateRange.YEAR -> Res.string.drawer_filter_previous_year
}

/**
 * The drawer's filter sheet: one date window (radio rows, with the Updated/Created pill), endpoints
 * (checkbox-role chips, stopped at the server's limit), switches, and the bookmarks to pick from
 * (any one matches; "Any bookmark" and picked bookmarks exclude each other). Choices are a draft until
 * Apply; Reset clears the draft. Apply waits for a connection, since a filtered list is the server's.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun DrawerFilterSheet(
    current: DrawerFilters,
    options: DrawerFilterOptions,
    onApply: (DrawerFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(current) }
    // Opened from the focused search field: the keyboard would otherwise stay up over the sheet
    // (the glass sheet draws in the activity window, so nothing moves focus off the field).
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusManager.clearFocus()
        keyboardController?.hide()
    }
    AdaptiveModalBottomSheet(onDismissRequest = onDismiss, dragHandle = { LowProfileDragHandle() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = stringResource(Res.string.drawer_filter_title), style = MaterialTheme.typography.titleLarge)

            AdaptivePillChoice(
                options = listOf(stringResource(Res.string.drawer_filter_updated), stringResource(Res.string.drawer_filter_created)),
                selectedIndex = draft.dateField.ordinal,
                onSelect = { draft = draft.copy(dateField = FilterDateField.entries[it]) },
                modifier = Modifier.fillMaxWidth(),
            )
            Column(modifier = Modifier.selectableGroup()) {
                FilterDateRange.entries.forEach { range ->
                    val selected = draft.dateRange == range
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp)
                            .selectable(selected = selected, role = Role.RadioButton) { draft = draft.copy(dateRange = range) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AdaptiveRadioButton(selected = selected, onClick = null)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(text = stringResource(range.label()), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            if (options.endpoints.isNotEmpty()) {
                Text(
                    text = stringResource(Res.string.drawer_filter_endpoint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val atLimit = draft.endpoints.size >= options.maxEndpoints
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    options.endpoints.forEach { endpoint ->
                        val selected = endpoint.name in draft.endpoints
                        AdaptiveFilterChip(
                            selected = selected,
                            enabled = selected || !atLimit,
                            onClick = {
                                draft = draft.copy(
                                    endpoints = if (selected) draft.endpoints - endpoint.name else draft.endpoints + endpoint.name,
                                )
                            },
                            label = { Text(endpoint.label, maxLines = 1) },
                            leadingIcon = {
                                EndpointIcon(
                                    endpointName = endpoint.name,
                                    iconUrl = endpoint.iconUrl,
                                    size = 18.dp,
                                    provider = endpoint.provider,
                                )
                            },
                        )
                    }
                }
                if (atLimit) {
                    Text(
                        text = stringResource(Res.string.drawer_filter_endpoint_limit, options.maxEndpoints),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            FilterSwitchRow(Icons.Default.AttachFile, stringResource(Res.string.drawer_filter_has_attachments), draft.hasFiles) {
                draft = draft.copy(hasFiles = it)
            }
            if (options.sharedOnlyAvailable || draft.sharedOnly) {
                FilterSwitchRow(Icons.Default.Link, stringResource(Res.string.drawer_filter_shared_only), draft.sharedOnly) {
                    draft = draft.copy(sharedOnly = it)
                }
            }
            if (options.bookmarksAvailable || draft.bookmarked || draft.tags.isNotEmpty()) {
                FilterSwitchRow(Icons.Default.Bookmark, stringResource(Res.string.drawer_filter_any_bookmark), draft.bookmarked) {
                    draft = draft.withAnyBookmark(it)
                }
                BookmarkChoices(options.bookmarks, draft.tags) { draft = draft.withTagToggled(it) }
            }

            if (!options.isOnline) {
                Text(
                    text = stringResource(Res.string.drawer_filter_offline),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                AdaptiveOutlinedButton(onClick = { draft = DrawerFilters.None }, enabled = draft.isActive) {
                    Text(stringResource(Res.string.drawer_filter_reset))
                }
                AdaptiveButton(onClick = { onApply(draft) }, enabled = options.isOnline) {
                    Text(stringResource(Res.string.drawer_filter_apply))
                }
            }
        }
    }
}

/** One chip per bookmark (web's bookmark submenu): any picked one matches, with each one's chat count. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookmarkChoices(bookmarks: List<FilterBookmarkOption>, selected: List<String>, onToggle: (String) -> Unit) {
    if (bookmarks.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(Res.string.drawer_filter_bookmarks),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            bookmarks.forEach { bookmark ->
                AdaptiveFilterChip(
                    selected = bookmark.tag in selected,
                    onClick = { onToggle(bookmark.tag) },
                    label = { Text(bookmark.tag, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    trailingIcon = {
                        Text(
                            text = bookmark.count.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    modifier = Modifier.widthIn(max = 240.dp),
                )
            }
        }
    }
}

@Composable
private fun FilterSwitchRow(icon: ImageVector, label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        AdaptiveSwitch(checked = checked, onCheckedChange = null)
    }
}

/** One applied filter as the chip row names it, and the filters without it. */
private class AppliedFilter(val label: String, val without: DrawerFilters)

@Composable
private fun appliedFilters(filters: DrawerFilters, options: DrawerFilterOptions): List<AppliedFilter> {
    val applied = mutableListOf<AppliedFilter>()
    if (filters.dateRange != FilterDateRange.ANY) {
        val range = stringResource(filters.dateRange.label())
        val label = if (filters.dateField == FilterDateField.CREATED) {
            stringResource(Res.string.drawer_filter_created_chip, range)
        } else {
            range
        }
        applied += AppliedFilter(label, filters.copy(dateRange = FilterDateRange.ANY))
    }
    filters.endpoints.forEach { name ->
        val label = options.endpoints.firstOrNull { it.name == name }?.label ?: name
        applied += AppliedFilter(label, filters.copy(endpoints = filters.endpoints - name))
    }
    if (filters.hasFiles) applied += AppliedFilter(stringResource(Res.string.drawer_filter_has_attachments), filters.copy(hasFiles = false))
    if (filters.sharedOnly) applied += AppliedFilter(stringResource(Res.string.drawer_filter_shared_only), filters.copy(sharedOnly = false))
    if (filters.bookmarked) {
        applied += AppliedFilter(stringResource(Res.string.drawer_filter_any_bookmark), filters.copy(bookmarked = false))
    }
    filters.tags.forEach { tag -> applied += AppliedFilter(tag, filters.copy(tags = filters.tags - tag)) }
    return applied
}

/**
 * The applied filters under New Chat, one chip each; a chip's x drops that filter. Shown while any
 * filter is on, search open or not, so a narrowed list is always labelled.
 */
@Composable
internal fun DrawerFilterChipRow(
    filters: DrawerFilters,
    options: DrawerFilterOptions,
    onChange: (DrawerFilters) -> Unit,
    onOpenSheet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        appliedFilters(filters, options).forEach { filter ->
            val removeLabel = stringResource(Res.string.cd_remove_named_filter, filter.label)
            AdaptiveInputChip(
                selected = true,
                // The chip opens the sheet; its x removes just this filter.
                onClick = onOpenSheet,
                label = { Text(filter.label, maxLines = 1) },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = removeLabel,
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button) { onChange(filter.without) },
                    )
                },
            )
        }
    }
}

/** The filtered list is empty: say so, and offer to clear every filter at once. */
@Composable
internal fun FilterEmptyState(loadFailed: Boolean, onClear: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Default.FilterList,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(32.dp),
        )
        if (loadFailed) {
            Text(
                text = stringResource(Res.string.drawer_filter_load_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        } else {
            Text(
                text = stringResource(Res.string.drawer_filter_no_match_title),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(Res.string.drawer_filter_no_match_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        AdaptiveButton(onClick = onClear) {
            Text(stringResource(Res.string.drawer_filter_clear))
        }
    }
}
