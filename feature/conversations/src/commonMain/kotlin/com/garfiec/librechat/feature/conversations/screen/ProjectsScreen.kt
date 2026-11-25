package com.garfiec.librechat.feature.conversations.screen

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.feature.conversations.viewmodel.ProjectsEvent
import com.garfiec.librechat.feature.conversations.viewmodel.ProjectsViewModel
import org.jetbrains.compose.resources.getString
import org.koin.compose.viewmodel.koinViewModel

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
    val projectLimits by viewModel.projectLimits.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val currentOnProjectClick by rememberUpdatedState(onProjectClick)

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshOnReturn() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ProjectsEvent.ShowError -> snackbarHostState.showSnackbar(event.message)
                is ProjectsEvent.ShowErrorRes -> snackbarHostState.showSnackbar(getString(event.message))
                is ProjectsEvent.OpenProject -> currentOnProjectClick(event.projectId, event.projectName)
            }
        }
    }

    ProjectsScreenContent(
        state = uiState,
        descriptionLimit = projectLimits.maxDescriptionLength,
        snackbarHostState = snackbarHostState,
        onProjectClick = onProjectClick,
        onNavigateBack = onNavigateBack,
        onNewChatInProject = onNewChatInProject,
        onLoadMore = viewModel::loadMore,
        onRefresh = { viewModel.refresh() },
        onRetry = viewModel::load,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onSortChange = viewModel::setSort,
        onCreateProject = viewModel::createProject,
        onEditProject = viewModel::editProject,
        onDeleteProject = viewModel::deleteProject,
        modifier = modifier,
    )
}
