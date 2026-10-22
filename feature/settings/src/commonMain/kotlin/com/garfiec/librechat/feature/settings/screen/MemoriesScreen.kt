package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.model.Memory
import com.garfiec.librechat.core.ui.components.AdaptiveAlertDialog
import com.garfiec.librechat.core.ui.components.AdaptiveButton
import com.garfiec.librechat.core.ui.components.AdaptiveExtendedFloatingActionButton
import com.garfiec.librechat.core.ui.components.AdaptiveFilterChip
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedTextField
import com.garfiec.librechat.core.ui.components.AdaptiveScaffold
import com.garfiec.librechat.core.ui.components.AdaptiveSnackbarHost
import com.garfiec.librechat.core.ui.components.DiscardChangesDialog
import com.garfiec.librechat.core.ui.components.EmptyState
import com.garfiec.librechat.core.ui.components.ErrorBanner
import com.garfiec.librechat.core.ui.components.UpToTwoColumns
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBar
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBarSpec
import com.garfiec.librechat.core.ui.components.topbar.BarIcons
import com.garfiec.librechat.core.ui.components.topbar.BarNavigation
import com.garfiec.librechat.core.ui.components.topbar.BarTitle
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.action_cancel
import com.garfiec.librechat.feature.settings.resources.action_clear
import com.garfiec.librechat.feature.settings.resources.action_delete
import com.garfiec.librechat.feature.settings.resources.action_retry
import com.garfiec.librechat.feature.settings.resources.add_memory
import com.garfiec.librechat.feature.settings.resources.cd_back
import com.garfiec.librechat.feature.settings.resources.dialog_delete_memory_message
import com.garfiec.librechat.feature.settings.resources.dialog_title_delete_memory
import com.garfiec.librechat.feature.settings.resources.memories_empty_desc
import com.garfiec.librechat.feature.settings.resources.memories_empty_desc_no_create
import com.garfiec.librechat.feature.settings.resources.memories_empty_title
import com.garfiec.librechat.feature.settings.resources.memories_filter_all
import com.garfiec.librechat.feature.settings.resources.memories_filter_personal
import com.garfiec.librechat.feature.settings.resources.memories_no_access_desc
import com.garfiec.librechat.feature.settings.resources.memories_no_access_title
import com.garfiec.librechat.feature.settings.resources.memories_no_match
import com.garfiec.librechat.feature.settings.resources.memories_personal_empty
import com.garfiec.librechat.feature.settings.resources.memories_search_hint
import com.garfiec.librechat.feature.settings.resources.title_memories
import com.garfiec.librechat.feature.settings.viewmodel.MemoriesEvent
import com.garfiec.librechat.feature.settings.viewmodel.MemoriesUiState
import com.garfiec.librechat.feature.settings.viewmodel.MemoriesViewModel
import com.garfiec.librechat.feature.settings.viewmodel.MemoryOperation
import com.garfiec.librechat.feature.settings.viewmodel.MemoryPartition
import com.garfiec.librechat.feature.settings.viewmodel.MemoryPartitionOption
import com.garfiec.librechat.feature.settings.viewmodel.memoryRowHandle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

// One column on phones; two once each would still be this wide (tablets, an unfolded Fold).
private val MemoryCardMinWidth = 300.dp
private const val PLACEHOLDER_COUNT = 3

/** Room under the last card for the extended FAB. */
private val FAB_CLEARANCE = 96.dp

/**
 * The Memories page: the opt-out switch and pool usage, then search, the partition chips and the
 * memory cards. The switch card stays on screen in every state, the first-load error included.
 */
/**
 * Shows each failure event in a snackbar until [events] completes. A newer event replaces the one
 * on screen (collectLatest cancels its showSnackbar, which dismisses it), and a snackbar with Retry
 * times out like any other: the host has no dismiss gesture, so an indefinite one would sit there
 * and hold every later failure behind it.
 */
internal suspend fun showMemoryFailures(
    events: Flow<MemoriesEvent>,
    snackbarHostState: SnackbarHostState,
    retryLabel: String,
    onRetry: (MemoryOperation.Retryable) -> Unit,
    message: suspend (MemoriesEvent.Failed) -> String = { memoryFailureMessage(it.failure, it.operation) },
) {
    events.collectLatest { event ->
        when (event) {
            is MemoriesEvent.Failed -> {
                val result = snackbarHostState.showSnackbar(
                    message = message(event),
                    actionLabel = retryLabel.takeIf { event.canRetry },
                    duration = SnackbarDuration.Long,
                )
                val retry = event.retryOperation
                if (result == SnackbarResult.ActionPerformed && retry != null) onRetry(retry)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoriesScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MemoriesViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val retryLabel = stringResource(Res.string.action_retry)
    val gridState = rememberLazyGridState()
    val fabExpanded by remember { derivedStateOf { gridState.firstVisibleItemIndex == 0 } }
    var pendingDelete by remember { mutableStateOf<Memory?>(null) }
    val focusManager = LocalFocusManager.current
    val sheetOpen = uiState.editor != null

    LaunchedEffect(viewModel) {
        showMemoryFailures(
            events = viewModel.events,
            snackbarHostState = snackbarHostState,
            retryLabel = retryLabel,
            onRetry = viewModel::retry,
        )
    }
    LaunchedEffect(viewModel) {
        viewModel.scrollToTop.collect { gridState.animateScrollToItem(0) }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshOnReturn() }
    // The sheet is its own window, and the page's focused field (the search) keeps its focus under
    // it, so without this the keyboard comes back up on that field when the sheet closes. The FAB
    // and the cards consume their taps, so clearFocusOnTap doesn't run.
    LaunchedEffect(sheetOpen) {
        if (sheetOpen) focusManager.clearFocus()
    }

    AdaptiveScaffold(
        // The search is typed here, so the grid, the FAB and the snackbar end where the keyboard begins.
        modifier = modifier.imePadding(),
        snackbarHost = { AdaptiveSnackbarHost(snackbarHostState) },
        topBar = {
            AdaptiveTopBar(
                spec = AdaptiveTopBarSpec(
                    navigation = BarNavigation(BarIcons.Back, stringResource(Res.string.cd_back), onNavigateBack),
                    title = BarTitle(stringResource(Res.string.title_memories)),
                    actions = emptyList(),
                ),
            )
        },
        floatingActionButton = {
            // The empty state carries its own Add button; two of them would compete.
            if (!uiState.listDenied && uiState.canCreate && uiState.memories.isNotEmpty()) {
                AdaptiveExtendedFloatingActionButton(
                    text = stringResource(Res.string.add_memory),
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    onClick = viewModel::openCreate,
                    expanded = fabExpanded,
                )
            }
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            val minCardWidth = MemoryCardMinWidth * LocalDensity.current.fontScale
            LazyVerticalGrid(
                state = gridState,
                columns = remember(minCardWidth) { UpToTwoColumns(minCardWidth) },
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = FAB_CLEARANCE),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (uiState.noAccess) {
                    fullWidthItem(key = "no_access") { MemoriesNoAccessState() }
                } else if (uiState.switchOnly) {
                    // No READ: the switch is all the role may use, so nothing that reads the list shows.
                    memoriesSwitch(uiState, onToggle = viewModel::setMemoriesEnabled, onRetryPreference = viewModel::retryPreference)
                } else {
                    memoriesHeader(
                        uiState = uiState,
                        onToggle = viewModel::setMemoriesEnabled,
                        onRetryPreference = viewModel::retryPreference,
                        onRetryLoad = viewModel::retryLoad,
                        onQueryChange = viewModel::updateQuery,
                        onPartitionChange = viewModel::selectPartition,
                    )
                    memoriesBody(
                        uiState = uiState,
                        onOpen = viewModel::openMemory,
                        onDelete = { pendingDelete = it },
                        onAdd = viewModel::openCreate,
                    )
                }
            }
        }
    }

    pendingDelete?.takeUnless { uiState.listDenied }?.let { memory ->
        AdaptiveAlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(Res.string.dialog_title_delete_memory)) },
            text = { Text(stringResource(Res.string.dialog_delete_memory_message, memoryDisplayKey(memory))) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        viewModel.deleteMemory(memory)
                    },
                ) {
                    Text(stringResource(Res.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }

    // The list behind it is gone, so a sheet left open when access was lost goes with it.
    val editor = uiState.editor?.takeUnless { uiState.listDenied }
    if (editor != null) {
        MemoryEditSheet(
            editor = editor,
            charLimit = uiState.charLimit,
            canDelete = uiState.canUpdate,
            onKeyChange = viewModel::updateEditorKey,
            onValueChange = viewModel::updateEditorValue,
            onDismiss = viewModel::dismissEditor,
            onSave = viewModel::saveEditor,
        )
        // Composed after the sheet, so it stacks above it in both styles.
        if (editor.showDiscardConfirm) {
            DiscardChangesDialog(onDiscard = viewModel::confirmDiscard, onDismiss = viewModel::keepEditing)
        }
    }
}

/** Switch card, first-load error, view-only notice, search and chips: everything above the cards. */
private fun LazyGridScope.memoriesHeader(
    uiState: MemoriesUiState,
    onToggle: (Boolean) -> Unit,
    onRetryPreference: () -> Unit,
    onRetryLoad: () -> Unit,
    onQueryChange: (String) -> Unit,
    onPartitionChange: (MemoryPartition) -> Unit,
) {
    memoriesSwitch(uiState, onToggle, onRetryPreference)
    val loadError = uiState.loadError
    if (loadError != null) {
        fullWidthItem(key = "load_error") {
            ErrorBanner(message = memoryFailureText(loadError, operation = null), onRetry = onRetryLoad)
        }
    }
    if (!uiState.canCreate && !uiState.canUpdate) {
        fullWidthItem(key = "view_only") { MemoriesViewOnlyNotice() }
    }
    if (uiState.showSearch) {
        fullWidthItem(key = "search") {
            AdaptiveOutlinedTextField(
                value = uiState.query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(stringResource(Res.string.memories_search_hint), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = if (uiState.query.isNotEmpty()) {
                    {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.action_clear))
                        }
                    }
                } else {
                    null
                },
                singleLine = true,
            )
        }
    }
    val options = uiState.partitionOptions
    if (options != null) {
        fullWidthItem(key = "partitions") {
            PartitionChips(options = options, selected = uiState.partition, onSelect = onPartitionChange)
        }
    }
}

/** The opt-out switch and pool usage, when there is either to show. */
private fun LazyGridScope.memoriesSwitch(
    uiState: MemoriesUiState,
    onToggle: (Boolean) -> Unit,
    onRetryPreference: () -> Unit,
) {
    val usage = memoryUsage(uiState.snapshot)
    if (uiState.canOptOut || usage != null) {
        fullWidthItem(key = "switch") {
            MemoriesSwitchCard(
                showSwitch = uiState.canOptOut,
                enabled = uiState.memoriesEnabled,
                busy = uiState.isTogglingPreference,
                usage = usage,
                onToggle = onToggle,
                loadFailed = uiState.preferenceLoadFailed,
                onRetryLoad = onRetryPreference,
            )
        }
    }
}

/** Placeholders, the empty state, a no-match line, or the cards. */
private fun LazyGridScope.memoriesBody(
    uiState: MemoriesUiState,
    onOpen: (Memory) -> Unit,
    onDelete: (Memory) -> Unit,
    onAdd: () -> Unit,
) {
    when {
        uiState.isFirstLoad && uiState.snapshot == null ->
            items(PLACEHOLDER_COUNT, key = { "placeholder_$it" }, contentType = { "placeholder" }) { MemoryCardPlaceholder() }

        // The banner above says why; an empty state would claim there are no memories.
        uiState.loadError != null -> Unit

        uiState.memories.isEmpty() -> fullWidthItem(key = "empty") {
            MemoriesEmptyState(canCreate = uiState.canCreate, onAdd = onAdd)
        }

        uiState.visibleMemories.isEmpty() -> fullWidthItem(key = "no_match") {
            Text(
                text = if (uiState.isSearchEmpty) {
                    stringResource(Res.string.memories_no_match, uiState.activeQuery)
                } else {
                    // Only the Personal chip can be empty: an agent chip exists only while it has memories.
                    stringResource(Res.string.memories_personal_empty)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                // Announced: a search that empties the list otherwise changes nothing a reader hears.
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        // Prefixed: the row handle could otherwise collide with a header item's key.
        else -> items(
            items = uiState.visibleMemories,
            key = { "memory:${memoryRowHandle(it)}" },
            contentType = { "memory" },
        ) { memory ->
            MemoryCard(
                memory = memory,
                canUpdate = uiState.canUpdate,
                agentOrdinal = memory.agentId?.let { uiState.agentOrdinals[it] },
                deleting = memoryRowHandle(memory) in uiState.deletingHandles,
                onOpen = { onOpen(memory) },
                onEdit = { onOpen(memory) },
                onDelete = { onDelete(memory) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

/** No memories at all. Carries the Add button itself (the FAB hides meanwhile) when the role may add. */
@Composable
internal fun MemoriesEmptyState(canCreate: Boolean, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    EmptyState(
        title = stringResource(Res.string.memories_empty_title),
        description = stringResource(
            if (canCreate) Res.string.memories_empty_desc else Res.string.memories_empty_desc_no_create,
        ),
        icon = Icons.Default.Lightbulb,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 48.dp),
        action = if (canCreate) {
            {
                AdaptiveButton(onClick = onAdd) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.add_memory))
                }
            }
        } else {
            null
        },
    )
}

/** The role may not read memories: this replaces the whole page, switch card included, as on web. */
@Composable
internal fun MemoriesNoAccessState(modifier: Modifier = Modifier) {
    EmptyState(
        title = stringResource(Res.string.memories_no_access_title),
        description = stringResource(Res.string.memories_no_access_desc),
        icon = Icons.Default.Lock,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 48.dp),
    )
}

@Composable
private fun PartitionChips(
    options: List<MemoryPartitionOption>,
    selected: MemoryPartition,
    onSelect: (MemoryPartition) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            val isSelected = option.partition == selected
            val label = when (option.partition) {
                MemoryPartition.All -> stringResource(Res.string.memories_filter_all)
                MemoryPartition.Personal -> stringResource(Res.string.memories_filter_personal)
                is MemoryPartition.Agent -> memoryAgentLabel(option.agentName, option.ordinal)
            }
            AdaptiveFilterChip(
                selected = isSelected,
                onClick = { onSelect(option.partition) },
                // Bounded, as the row scrolls sideways and would otherwise give a long agent name
                // all the width it asks for, so it would never ellipsize.
                label = {
                    Text(
                        label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = MEMORY_AGENT_LABEL_MAX_WIDTH),
                    )
                },
                leadingIcon = if (isSelected) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else {
                    null
                },
            )
        }
    }
}

private fun LazyGridScope.fullWidthItem(
    key: String,
    content: @Composable LazyGridItemScope.() -> Unit,
) = item(key = key, span = { GridItemSpan(maxLineSpan) }, contentType = key, content = content)
