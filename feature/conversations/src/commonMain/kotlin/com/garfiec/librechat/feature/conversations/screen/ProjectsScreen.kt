package com.garfiec.librechat.feature.conversations.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.common.datetime.listLabel
import com.garfiec.librechat.core.common.datetime.parseIsoInstantOrNull
import com.garfiec.librechat.core.model.ChatProject
import com.garfiec.librechat.core.ui.components.AdaptiveButton
import com.garfiec.librechat.core.ui.components.AdaptiveCard
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveDropdownMenu
import com.garfiec.librechat.core.ui.components.AdaptiveFilterChip
import com.garfiec.librechat.core.ui.components.AdaptiveFloatingActionButton
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedTextField
import com.garfiec.librechat.core.ui.components.AdaptiveScaffold
import com.garfiec.librechat.core.ui.components.AdaptiveSnackbarHost
import com.garfiec.librechat.core.ui.components.EmptyState
import com.garfiec.librechat.core.ui.components.UpToTwoColumns
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBar
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBarSpec
import com.garfiec.librechat.core.ui.components.topbar.BarIcons
import com.garfiec.librechat.core.ui.components.topbar.BarNavigation
import com.garfiec.librechat.core.ui.components.topbar.BarTitle
import com.garfiec.librechat.core.ui.datetime.LocalDateTimeFormat
import com.garfiec.librechat.core.ui.datetime.LocalRelativeTimeReference
import com.garfiec.librechat.core.ui.datetime.resolve
import com.garfiec.librechat.feature.conversations.components.ProjectDeleteDialog
import com.garfiec.librechat.feature.conversations.components.ProjectEditorSheet
import com.garfiec.librechat.feature.conversations.components.ProjectMenuButton
import com.garfiec.librechat.feature.conversations.components.ProjectTile
import com.garfiec.librechat.feature.conversations.resources.Res
import com.garfiec.librechat.feature.conversations.resources.back
import com.garfiec.librechat.feature.conversations.resources.project_chat_count
import com.garfiec.librechat.feature.conversations.resources.project_create
import com.garfiec.librechat.feature.conversations.resources.project_create_confirm
import com.garfiec.librechat.feature.conversations.resources.project_edit
import com.garfiec.librechat.feature.conversations.resources.project_new
import com.garfiec.librechat.feature.conversations.resources.project_sort_activity
import com.garfiec.librechat.feature.conversations.resources.project_sort_created
import com.garfiec.librechat.feature.conversations.resources.project_sort_custom
import com.garfiec.librechat.feature.conversations.resources.project_sort_label
import com.garfiec.librechat.feature.conversations.resources.project_sort_name
import com.garfiec.librechat.feature.conversations.resources.project_unassigned
import com.garfiec.librechat.feature.conversations.resources.project_unassigned_subtitle
import com.garfiec.librechat.feature.conversations.resources.projects_count
import com.garfiec.librechat.feature.conversations.resources.projects_empty
import com.garfiec.librechat.feature.conversations.resources.projects_empty_description
import com.garfiec.librechat.feature.conversations.resources.projects_no_match
import com.garfiec.librechat.feature.conversations.resources.projects_search
import com.garfiec.librechat.feature.conversations.resources.projects_title
import com.garfiec.librechat.feature.conversations.resources.save
import com.garfiec.librechat.feature.conversations.viewmodel.ProjectSort
import com.garfiec.librechat.feature.conversations.viewmodel.ProjectsEvent
import com.garfiec.librechat.feature.conversations.viewmodel.ProjectsUiState
import com.garfiec.librechat.feature.conversations.viewmodel.ProjectsViewModel
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

// One column on phones; two once each would still be this wide (tablets, an unfolded Fold).
private val ProjectCardMinWidth = 300.dp
private val CardShape = RoundedCornerShape(16.dp)
private const val PLACEHOLDER_COUNT = 3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    onProjectClick: (projectId: String, projectName: String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onNewChatInProject: (projectId: String, projectName: String) -> Unit = { _, _ -> },
    viewModel: ProjectsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showCreateDialog by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<ChatProject?>(null) }
    var deleteTarget by remember { mutableStateOf<ChatProject?>(null) }

    val unassignedLabel = stringResource(Res.string.project_unassigned)
    val currentOnProjectClick by rememberUpdatedState(onProjectClick)

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshOnReturn() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ProjectsEvent.ShowError -> snackbarHostState.showSnackbar(event.message)
                is ProjectsEvent.OpenProject -> currentOnProjectClick(event.projectId, event.projectName)
            }
        }
    }

    val gridState = rememberLazyGridState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleIndex = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = gridState.layoutInfo.totalItemsCount
            lastVisibleIndex >= totalItems - 3 && uiState.hasMore && !uiState.isLoading
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { shouldLoadMore }.collect { if (it) viewModel.loadMore() }
    }

    // From the search the shown list answers, not the typed one, which runs ahead of the results.
    val isSearching = uiState.shownQuery.isNotEmpty()
    val isFirstLoad = uiState.isLoading && uiState.projects.isEmpty()
    val hasNoProjects = uiState.projects.isEmpty() && !uiState.isLoading && !isSearching

    AdaptiveScaffold(
        modifier = modifier,
        snackbarHost = { AdaptiveSnackbarHost(snackbarHostState) },
        topBar = {
            AdaptiveTopBar(
                spec = AdaptiveTopBarSpec(
                    navigation = BarNavigation(BarIcons.Back, stringResource(Res.string.back), onNavigateBack),
                    title = BarTitle(stringResource(Res.string.projects_title)),
                ),
            )
        },
        floatingActionButton = {
            // The empty state carries its own create button; two would compete.
            if (!hasNoProjects) {
                AdaptiveFloatingActionButton(onClick = { showCreateDialog = true }) {
                    Row(
                        modifier = Modifier.padding(start = 16.dp, end = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(Res.string.project_new), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            val minCardWidth = ProjectCardMinWidth * LocalDensity.current.fontScale
            LazyVerticalGrid(
                state = gridState,
                columns = remember(minCardWidth) { UpToTwoColumns(minCardWidth) },
                modifier = Modifier.fillMaxSize(),
                // Bottom room so the last card scrolls clear of the FAB.
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!hasNoProjects) {
                    fullWidthItem(key = "search") {
                        AdaptiveOutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = viewModel::onSearchQueryChange,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(stringResource(Res.string.projects_search)) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true,
                        )
                    }
                    fullWidthItem(key = "sort") {
                        SortRow(uiState = uiState, onSortChange = viewModel::setSort)
                    }
                }

                if (!isSearching) {
                    fullWidthItem(key = "unassigned") {
                        UnassignedRow(
                            label = unassignedLabel,
                            onClick = { onProjectClick(ChatProject.UNASSIGNED, unassignedLabel) },
                        )
                    }
                }

                when {
                    isFirstLoad -> items(PLACEHOLDER_COUNT, key = { "placeholder_$it" }) { ProjectCardPlaceholder() }

                    hasNoProjects -> fullWidthItem(key = "empty") {
                        EmptyState(
                            title = stringResource(Res.string.projects_empty),
                            description = stringResource(Res.string.projects_empty_description),
                            icon = Icons.Default.Folder,
                            modifier = Modifier.padding(vertical = 48.dp),
                            action = {
                                AdaptiveButton(onClick = { showCreateDialog = true }) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(Res.string.project_create))
                                }
                            },
                        )
                    }

                    uiState.projects.isEmpty() && !uiState.isLoading -> fullWidthItem(key = "no_match") {
                        EmptyState(
                            title = stringResource(Res.string.projects_no_match, uiState.shownQuery),
                            icon = Icons.Default.Search,
                            modifier = Modifier.padding(vertical = 48.dp),
                        )
                    }

                    else -> items(uiState.projects, key = { it.id }, contentType = { "project" }) { project ->
                        ProjectCard(
                            project = project,
                            onClick = { onProjectClick(project.id, project.name) },
                            onEdit = { editTarget = project },
                            onNewChat = { onNewChatInProject(project.id, project.name) },
                            onDelete = { deleteTarget = project },
                        )
                    }
                }

                if (uiState.isLoading && uiState.projects.isNotEmpty()) {
                    fullWidthItem(key = "loading_more") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            AdaptiveCircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        ProjectEditorSheet(
            title = stringResource(Res.string.project_new),
            confirmLabel = stringResource(Res.string.project_create_confirm),
            onConfirm = { name, description ->
                viewModel.createProject(name, description)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false },
        )
    }

    editTarget?.let { target ->
        ProjectEditorSheet(
            title = stringResource(Res.string.project_edit),
            confirmLabel = stringResource(Res.string.save),
            initialName = target.name,
            initialDescription = target.description.orEmpty(),
            onConfirm = { name, description ->
                viewModel.editProject(target.id, name, description)
                editTarget = null
            },
            onDismiss = { editTarget = null },
        )
    }

    deleteTarget?.let { target ->
        ProjectDeleteDialog(
            projectName = target.name,
            onConfirm = {
                viewModel.deleteProject(target.id)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

private fun LazyGridScope.fullWidthItem(
    key: String,
    content: @Composable LazyGridItemScope.() -> Unit,
) = item(key = key, span = { GridItemSpan(maxLineSpan) }, contentType = key, content = content)

private val ProjectSort.label: StringResource
    get() = when (this) {
        ProjectSort.ACTIVITY -> Res.string.project_sort_activity
        ProjectSort.CREATED -> Res.string.project_sort_created
        ProjectSort.NAME -> Res.string.project_sort_name
        ProjectSort.CUSTOM -> Res.string.project_sort_custom
    }

@Composable
private fun SortRow(uiState: ProjectsUiState, onSortChange: (ProjectSort) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Weighted without fill: the chip keeps its size until the row is tight, then gives way first.
        Box(modifier = Modifier.weight(1f, fill = false)) {
            AdaptiveFilterChip(
                selected = true,
                onClick = { menuOpen = true },
                label = { Text(stringResource(uiState.sort.label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = {
                    Icon(
                        Icons.AutoMirrored.Filled.Sort,
                        contentDescription = stringResource(Res.string.project_sort_label),
                        modifier = Modifier.size(18.dp),
                    )
                },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
            AdaptiveDropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                ProjectSort.entries.forEach { sort ->
                    DropdownMenuItem(
                        text = { Text(stringResource(sort.label)) },
                        trailingIcon = {
                            if (sort == uiState.sort) Icon(Icons.Default.Check, contentDescription = null)
                        },
                        onClick = {
                            menuOpen = false
                            onSortChange(sort)
                        },
                    )
                }
            }
        }
        // Only a complete list has a count worth stating; a paged one would understate it.
        if (!uiState.hasMore && uiState.projects.isNotEmpty()) {
            Text(
                text = pluralStringResource(Res.plurals.projects_count, uiState.projects.size, uiState.projects.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

/** The read-side "no project" filter. Not a project: no tile, no count, no menu. */
@Composable
private fun UnassignedRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Inbox, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(Res.string.project_unassigned_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun ProjectCard(
    project: ChatProject,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onNewChat: (() -> Unit)? = null,
) {
    val format = LocalDateTimeFormat.current
    val reference = LocalRelativeTimeReference.current
    val activityIso = project.lastConversationAt ?: project.updatedAt ?: project.createdAt
    val activity = remember(activityIso, format, reference) {
        activityIso?.let(::parseIsoInstantOrNull)?.listLabel(format, reference)
    }
    AdaptiveCard(
        modifier = modifier.fillMaxWidth().clip(CardShape).clickable(onClick = onClick),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            ProjectTile(projectId = project.id, name = project.name, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val description = project.description?.trim().orEmpty()
                if (description.isNotEmpty()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = pluralStringResource(
                            Res.plurals.project_chat_count,
                            project.conversationCount,
                            project.conversationCount,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (activity != null) {
                        Text(
                            text = activity.resolve(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            ProjectMenuButton(onEdit = onEdit, onDelete = onDelete, onNewChat = onNewChat)
        }
    }
}

/** First-load stand-in, sized like a card with a description, so the grid doesn't jump on arrival. */
@Composable
private fun ProjectCardPlaceholder() {
    val block = MaterialTheme.colorScheme.surfaceContainerHighest
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(block))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth(0.5f).height(16.dp).clip(RoundedCornerShape(4.dp)).background(block))
            Box(Modifier.fillMaxWidth(0.9f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(block))
            Box(Modifier.fillMaxWidth(0.3f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(block))
        }
    }
}
