package com.garfiec.librechat.feature.skills.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.model.SkillSummary
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveFloatingActionButton
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedTextField
import com.garfiec.librechat.core.ui.components.AdaptiveScaffold
import com.garfiec.librechat.core.ui.components.EmptyState
import com.garfiec.librechat.core.ui.components.ErrorBanner
import com.garfiec.librechat.core.ui.components.ListDragSelection
import com.garfiec.librechat.core.ui.components.dragSelectRow
import com.garfiec.librechat.core.ui.components.dragSelectRowIcon
import com.garfiec.librechat.core.ui.components.listDragSelection
import com.garfiec.librechat.core.ui.components.rememberListDragSelection
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBar
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBarSpec
import com.garfiec.librechat.core.ui.components.topbar.BarAction
import com.garfiec.librechat.core.ui.components.topbar.BarIcons
import com.garfiec.librechat.core.ui.components.topbar.BarNavigation
import com.garfiec.librechat.core.ui.components.topbar.BarTitle
import com.garfiec.librechat.feature.skills.components.SkillImportFailureDialog
import com.garfiec.librechat.feature.skills.components.rememberSkillFilePicker
import com.garfiec.librechat.feature.skills.resources.*
import com.garfiec.librechat.feature.skills.resources.Res
import com.garfiec.librechat.feature.skills.viewmodel.SkillsListViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillsListScreen(
    onSkillClick: (String) -> Unit,
    onCreateSkill: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SkillsListViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val importPicker = rememberSkillFilePicker(onPick = { doc -> viewModel.importSkill(doc) })

    // Load on first show and refresh on return from the editor (a newly-created
    // skill appears). Nav3 retains the VM, so init alone wouldn't re-fetch.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshOnReturn()
    }

    // Infinite scroll: load more when within 3 of the end.
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= uiState.skills.size - 3 && uiState.hasMore && !uiState.isLoadingMore
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMore()
    }

    uiState.importFailure?.let { failure ->
        SkillImportFailureDialog(failure = failure, onDismiss = viewModel::dismissImportFailure)
    }

    AdaptiveScaffold(
        modifier = modifier,
        topBar = {
            AdaptiveTopBar(
                spec = AdaptiveTopBarSpec(
                    navigation = BarNavigation(BarIcons.Back, stringResource(Res.string.skills_back), onBack),
                    title = BarTitle(stringResource(Res.string.skills_title)),
                    actions = if (uiState.canCreate) {
                        listOf(
                            BarAction.Icon(
                                id = "import",
                                icon = BarIcons.Import,
                                label = stringResource(Res.string.skills_import),
                                busy = uiState.isImporting,
                                onClick = { importPicker.launch(emptyList()) },
                            ),
                        )
                    } else {
                        emptyList()
                    },
                ),
            )
        },
        floatingActionButton = {
            if (uiState.canCreate) {
                AdaptiveFloatingActionButton(onClick = onCreateSkill) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.skills_create))
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            AdaptiveOutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQueryChanged,
                label = { Text(stringResource(Res.string.skills_search_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            )

            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    uiState.isLoading && uiState.skills.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            AdaptiveCircularProgressIndicator()
                        }
                    }
                    uiState.error != null && uiState.skills.isEmpty() -> {
                        // Scrollable so pull-to-refresh has something to pull.
                        LazyColumn(Modifier.fillMaxSize()) {
                            item(contentType = "error") {
                                ErrorBanner(
                                    message = uiState.error ?: "",
                                    onRetry = viewModel::loadFirstPage,
                                    retryLabel = stringResource(Res.string.skills_retry),
                                )
                            }
                        }
                    }
                    uiState.skills.isEmpty() -> {
                        // Scrollable so pull-to-refresh has something to pull; fillParentMaxSize keeps
                        // it centred, which a verticalScroll's unbounded height would not.
                        val noMatches = uiState.searchQuery.isNotBlank()
                        val title = stringResource(
                            if (noMatches) Res.string.skills_no_matches else Res.string.skills_empty,
                        )
                        val hint = if (!noMatches && uiState.canCreate) stringResource(Res.string.skills_empty_hint) else null
                        LazyColumn(Modifier.fillMaxSize()) {
                            item(contentType = "empty") {
                                EmptyState(
                                    title = title,
                                    description = hint,
                                    icon = if (noMatches) null else Icons.Default.Extension,
                                    modifier = Modifier.fillParentMaxSize(),
                                )
                            }
                        }
                    }
                    else -> {
                        val dragSelection = rememberListDragSelection()
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .listDragSelection(dragSelection, holdOnly = true),
                            contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 88.dp),
                        ) {
                            items(uiState.skills, key = { it.id }, contentType = { "skill" }) { skill ->
                                SkillRow(skill = skill, dragSelection = dragSelection, onClick = { onSkillClick(skill.id) })
                            }
                            if (uiState.isLoadingMore) {
                                item(contentType = "loader") {
                                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                        AdaptiveCircularProgressIndicator()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SkillRow(skill: SkillSummary, dragSelection: ListDragSelection, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .dragSelectRow(dragSelection, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .dragSelectRowIcon()
                .size(40.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, IconTileShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Extension,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = skill.displayTitle?.takeIf { it.isNotBlank() } ?: skill.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            skill.description?.takeIf { it.isNotBlank() }?.let { desc ->
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            skill.category?.takeIf { it.isNotBlank() }?.let { category ->
                Text(
                    text = category,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

private val IconTileShape = RoundedCornerShape(12.dp)
