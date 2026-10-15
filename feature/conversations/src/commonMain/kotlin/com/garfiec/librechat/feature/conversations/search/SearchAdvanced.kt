package com.garfiec.librechat.feature.conversations.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.data.search.RoleFilter
import com.garfiec.librechat.core.data.search.SearchOptions
import com.garfiec.librechat.core.data.search.SearchSort
import com.garfiec.librechat.core.data.search.SearchSource
import com.garfiec.librechat.core.ui.components.AdaptiveButton
import com.garfiec.librechat.core.ui.components.AdaptiveDialog
import com.garfiec.librechat.core.ui.components.AdaptiveDropdownMenu
import com.garfiec.librechat.core.ui.components.AdaptiveFilterChip
import com.garfiec.librechat.core.ui.components.AdaptiveInputChip
import com.garfiec.librechat.core.ui.components.AdaptiveModalBottomSheet
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedButton
import com.garfiec.librechat.feature.conversations.resources.Res
import com.garfiec.librechat.feature.conversations.resources.cd_remove_filter
import com.garfiec.librechat.feature.conversations.resources.search_archived
import com.garfiec.librechat.feature.conversations.resources.search_archived_exclude
import com.garfiec.librechat.feature.conversations.resources.search_archived_include
import com.garfiec.librechat.feature.conversations.resources.search_done
import com.garfiec.librechat.feature.conversations.resources.search_filter_any
import com.garfiec.librechat.feature.conversations.resources.search_filter_date
import com.garfiec.librechat.feature.conversations.resources.search_filter_endpoint
import com.garfiec.librechat.feature.conversations.resources.search_filter_model
import com.garfiec.librechat.feature.conversations.resources.search_filter_role
import com.garfiec.librechat.feature.conversations.resources.search_filters
import com.garfiec.librechat.feature.conversations.resources.search_filters_title
import com.garfiec.librechat.feature.conversations.resources.search_group_by
import com.garfiec.librechat.feature.conversations.resources.search_include_archived
import com.garfiec.librechat.feature.conversations.resources.search_look_all
import com.garfiec.librechat.feature.conversations.resources.search_look_in
import com.garfiec.librechat.feature.conversations.resources.search_match_case
import com.garfiec.librechat.feature.conversations.resources.search_more_options
import com.garfiec.librechat.feature.conversations.resources.search_options_title
import com.garfiec.librechat.feature.conversations.resources.search_regex
import com.garfiec.librechat.feature.conversations.resources.search_reset
import com.garfiec.librechat.feature.conversations.resources.search_sort
import com.garfiec.librechat.feature.conversations.resources.search_source
import com.garfiec.librechat.feature.conversations.resources.search_visible_only_chip
import com.garfiec.librechat.feature.conversations.resources.search_whole_word
import org.jetbrains.compose.resources.stringResource

/** The sheet never covers more than this share of the window, and scrolls past it. */
private const val SHEET_MAX_HEIGHT_FRACTION = 0.82f

private val ToggleGroupShape = RoundedCornerShape(8.dp)
private val ToggleCellWidth = 40.dp
private val ToggleCellHeight = 32.dp

/** What the advanced controls change; one bundle so each layout takes the same callbacks. */
internal class AdvancedActions(
    val onOptionsChange: ((SearchOptions) -> SearchOptions) -> Unit,
    val onDateRangeChange: (DateRange) -> Unit,
    val onGroupingChange: (SearchGrouping) -> Unit,
    val onOpenFilters: () -> Unit,
    val onResetFilters: () -> Unit,
)

/**
 * The phone's row under the header: the joined `Aa │ W │ .*` toggles, the Filters button with a
 * count badge, and a removable chip per active filter. Scrolls sideways when it doesn't fit.
 */
@Composable
internal fun AdvancedSearchRow(state: SearchUiState, actions: AdvancedActions, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextToggleGroup(state.options, actions.onOptionsChange)
        FiltersButton(label = stringResource(Res.string.search_filters), count = state.activeFilterCount, onClick = actions.onOpenFilters)
        ActiveFilterChips(state, actions, rowFilters = true)
    }
}

/**
 * The wide layout's filter row beside the scope tabs: Role, Model and Date as dropdown chips, then
 * "More options" for the rest, which opens [SearchOptionsDialog].
 */
@Composable
internal fun WideFilterRow(state: SearchUiState, actions: AdvancedActions, modifier: Modifier = Modifier) {
    val options = state.options
    var moreOpen by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DropdownChip(
            label = if (options.role == RoleFilter.Any) stringResource(Res.string.search_filter_role) else roleLabel(options.role),
            active = options.role != RoleFilter.Any,
            choices = RoleFilter.entries.map { roleLabel(it) to it },
            selected = options.role,
            onChoose = { role -> actions.onOptionsChange { it.copy(role = role) } },
        )
        if (state.filterValues.models.isNotEmpty()) {
            DropdownChip(
                label = options.model.named(state.agentNames) ?: stringResource(Res.string.search_filter_model),
                active = options.model != null,
                choices = listOf(stringResource(Res.string.search_filter_any) to null) +
                    state.filterValues.models.map { it.named(state.agentNames).orEmpty() to it },
                selected = options.model,
                onChoose = { model -> actions.onOptionsChange { it.copy(model = model) } },
            )
        }
        DropdownChip(
            label = if (state.dateRange == DateRange.AnyTime) stringResource(Res.string.search_filter_date) else dateLabel(state.dateRange),
            active = state.dateRange != DateRange.AnyTime,
            choices = DateRange.entries.map { dateLabel(it) to it },
            selected = state.dateRange,
            onChoose = actions.onDateRangeChange,
        )
        FiltersButton(
            label = stringResource(Res.string.search_more_options),
            count = state.moreOptionsCount,
            onClick = { moreOpen = true },
        )
        ActiveFilterChips(state, actions, rowFilters = false)
    }
    if (moreOpen) SearchOptionsDialog(state, actions, onDismiss = { moreOpen = false })
}

/** `Aa │ W │ .*`: one outlined group of three monospace toggles. */
@Composable
internal fun TextToggleGroup(options: SearchOptions, onOptionsChange: ((SearchOptions) -> SearchOptions) -> Unit) {
    val outline = MaterialTheme.colorScheme.outline
    Row(
        modifier = Modifier.height(ToggleCellHeight).border(1.dp, outline, ToggleGroupShape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToggleCell("Aa", stringResource(Res.string.search_match_case), options.matchCase) {
            onOptionsChange { it.copy(matchCase = !it.matchCase) }
        }
        Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(outline))
        ToggleCell("W", stringResource(Res.string.search_whole_word), options.wholeWord) {
            onOptionsChange { it.copy(wholeWord = !it.wholeWord) }
        }
        Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(outline))
        ToggleCell(".*", stringResource(Res.string.search_regex), options.regex) {
            onOptionsChange { it.copy(regex = !it.regex) }
        }
    }
}

@Composable
private fun ToggleCell(label: String, description: String, selected: Boolean, onToggle: () -> Unit) {
    val container = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
    Box(
        modifier = Modifier
            .width(ToggleCellWidth)
            .fillMaxHeight()
            .background(container)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontFamily = FontFamily.Monospace,
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FiltersButton(label: String, count: Int, onClick: () -> Unit) {
    AdaptiveFilterChip(
        selected = count > 0,
        onClick = onClick,
        label = {
            Text(label)
            if (count > 0) {
                Spacer(modifier = Modifier.width(6.dp))
                CountBadge(count)
            }
        },
        leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp)) },
    )
}

@Composable
private fun CountBadge(count: Int) {
    Box(
        modifier = Modifier.size(18.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(count.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
    }
}

@Composable
private fun <T> DropdownChip(label: String, active: Boolean, choices: List<Pair<String, T>>, selected: T, onChoose: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AdaptiveFilterChip(
            selected = active,
            onClick = { open = true },
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
        AdaptiveDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            choices.forEach { (text, value) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = { open = false; onChoose(value) },
                    // The current choice is ticked, as in the filter sheet's selected pill.
                    trailingIcon = if (value == selected) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

/**
 * A removable chip per active filter. [rowFilters] includes role, model, endpoint and date, which
 * the wide layout already shows as dropdown chips.
 */
@Composable
private fun ActiveFilterChips(state: SearchUiState, actions: AdvancedActions, rowFilters: Boolean) {
    val options = state.options
    val change = actions.onOptionsChange
    if (rowFilters) {
        if (options.role != RoleFilter.Any) RemovableChip(roleLabel(options.role)) { change { it.copy(role = RoleFilter.Any) } }
        options.model?.let { model -> RemovableChip(model.named(state.agentNames).orEmpty()) { change { it.copy(model = null) } } }
        if (state.dateRange != DateRange.AnyTime) RemovableChip(dateLabel(state.dateRange)) { actions.onDateRangeChange(DateRange.AnyTime) }
    }
    options.endpoint?.let { endpoint -> RemovableChip(endpoint) { change { it.copy(endpoint = null) } } }
    if (options.source != SearchSource.All) RemovableChip(sourceLabel(options.source)) { change { it.copy(source = SearchSource.All) } }
    if (options.visibleTextOnly) {
        RemovableChip(stringResource(Res.string.search_visible_only_chip)) { change { it.copy(visibleTextOnly = false) } }
    }
    if (options.includeArchived) {
        RemovableChip(stringResource(Res.string.search_include_archived)) { change { it.copy(includeArchived = false) } }
    }
}

@Composable
private fun RemovableChip(label: String, onRemove: () -> Unit) {
    val removeLabel = stringResource(Res.string.cd_remove_filter)
    AdaptiveInputChip(
        selected = true,
        onClick = onRemove,
        label = { Text(label) },
        trailingIcon = { Icon(Icons.Default.Close, contentDescription = removeLabel, modifier = Modifier.size(16.dp)) },
    )
}

/** Every filter and option, in a sheet capped below the full height. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchFiltersSheet(state: SearchUiState, actions: AdvancedActions, onDismiss: () -> Unit) {
    val maxHeight = with(LocalDensity.current) {
        (LocalWindowInfo.current.containerSize.height * SHEET_MAX_HEIGHT_FRACTION).toDp()
    }
    AdaptiveModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight)) {
            OptionsHeader(stringResource(Res.string.search_filters_title), actions.onResetFilters, onDismiss)
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                LookInSection(state, actions)
                RoleSection(state, actions)
                ModelSections(state, actions)
                DateSection(state, actions)
                ResultSections(state, actions)
            }
        }
    }
}

/** The wide layout's "More options": what isn't already a chip on the filter row. */
@Composable
private fun SearchOptionsDialog(state: SearchUiState, actions: AdvancedActions, onDismiss: () -> Unit) {
    AdaptiveDialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), modifier = Modifier.widthIn(max = 480.dp)) {
            Column {
                OptionsHeader(stringResource(Res.string.search_options_title), actions.onResetFilters, onDismiss)
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    LookInSection(state, actions)
                    ResultSections(state, actions)
                }
            }
        }
    }
}

@Composable
private fun OptionsHeader(title: String, onReset: () -> Unit, onDone: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        AdaptiveOutlinedButton(onClick = onReset) { Text(stringResource(Res.string.search_reset)) }
        Spacer(modifier = Modifier.width(8.dp))
        AdaptiveButton(onClick = onDone) { Text(stringResource(Res.string.search_done)) }
    }
}

@Composable
private fun LookInSection(state: SearchUiState, actions: AdvancedActions) {
    ChoiceSection(
        title = stringResource(Res.string.search_look_in),
        choices = listOf(stringResource(Res.string.search_look_all) to false, stringResource(Res.string.search_visible_only_chip) to true),
        selected = state.options.visibleTextOnly,
        onChoose = { only -> actions.onOptionsChange { it.copy(visibleTextOnly = only) } },
    )
}

@Composable
private fun RoleSection(state: SearchUiState, actions: AdvancedActions) {
    ChoiceSection(
        title = stringResource(Res.string.search_filter_role),
        choices = RoleFilter.entries.map { roleLabel(it) to it },
        selected = state.options.role,
        onChoose = { role -> actions.onOptionsChange { it.copy(role = role) } },
    )
}

@Composable
private fun ModelSections(state: SearchUiState, actions: AdvancedActions) {
    val any = stringResource(Res.string.search_filter_any)
    if (state.filterValues.models.isNotEmpty()) {
        ChoiceSection(
            title = stringResource(Res.string.search_filter_model),
            choices = listOf(any to null) + state.filterValues.models.map { it.named(state.agentNames).orEmpty() to it },
            selected = state.options.model,
            onChoose = { model -> actions.onOptionsChange { it.copy(model = model) } },
        )
    }
    if (state.filterValues.endpoints.size > 1) {
        ChoiceSection(
            title = stringResource(Res.string.search_filter_endpoint),
            choices = listOf(any to null) + state.filterValues.endpoints.map { it to it },
            selected = state.options.endpoint,
            onChoose = { endpoint -> actions.onOptionsChange { it.copy(endpoint = endpoint) } },
        )
    }
}

@Composable
private fun DateSection(state: SearchUiState, actions: AdvancedActions) {
    ChoiceSection(
        title = stringResource(Res.string.search_filter_date),
        choices = DateRange.entries.map { dateLabel(it) to it },
        selected = state.dateRange,
        onChoose = actions.onDateRangeChange,
    )
}

/** Archived, source, sort and grouping: how results are gathered and laid out. */
@Composable
private fun ResultSections(state: SearchUiState, actions: AdvancedActions) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ChoiceSection(
            title = stringResource(Res.string.search_archived),
            choices = listOf(
                stringResource(Res.string.search_archived_exclude) to false,
                stringResource(Res.string.search_archived_include) to true,
            ),
            selected = state.options.includeArchived,
            onChoose = { include -> actions.onOptionsChange { it.copy(includeArchived = include) } },
        )
        ChoiceSection(
            title = stringResource(Res.string.search_source),
            choices = SearchSource.entries.map { sourceLabel(it) to it },
            selected = state.options.source,
            onChoose = { source -> actions.onOptionsChange { it.copy(source = source) } },
        )
        ChoiceSection(
            title = stringResource(Res.string.search_sort),
            choices = SearchSort.entries.map { sortLabel(it) to it },
            selected = state.options.sort,
            onChoose = { sort -> actions.onOptionsChange { it.copy(sort = sort) } },
        )
        ChoiceSection(
            title = stringResource(Res.string.search_group_by),
            choices = SearchGrouping.entries.map { groupingLabel(it) to it },
            selected = state.grouping,
            onChoose = actions.onGroupingChange,
        )
    }
}

@Composable
private fun <T> ChoiceSection(title: String, choices: List<Pair<String, T>>, selected: T, onChoose: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            choices.forEach { (label, value) ->
                AdaptiveFilterChip(selected = value == selected, onClick = { onChoose(value) }, label = { Text(label) })
            }
        }
    }
}
