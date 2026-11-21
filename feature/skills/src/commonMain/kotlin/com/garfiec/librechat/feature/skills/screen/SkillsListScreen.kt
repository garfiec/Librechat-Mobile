package com.garfiec.librechat.feature.skills.screen

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.feature.skills.SkillErrorKind
import com.garfiec.librechat.feature.skills.components.rememberSkillFilePicker
import com.garfiec.librechat.feature.skills.resources.Res
import com.garfiec.librechat.feature.skills.resources.skills_load_more_failed
import com.garfiec.librechat.feature.skills.resources.skills_retry
import com.garfiec.librechat.feature.skills.viewmodel.SkillsListEvent
import com.garfiec.librechat.feature.skills.viewmodel.SkillsListViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SkillsListScreen(
    onSkillClick: (String) -> Unit,
    onCreateSkill: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SkillsListViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val importPicker = rememberSkillFilePicker(onPick = { doc -> viewModel.importSkill(doc) })

    // Load on first show and refresh on return from the editor (a newly-created
    // skill appears). Nav3 retains the VM, so init alone wouldn't re-fetch.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshOnReturn()
    }

    // Resolved in composition, so an in-app language switch reaches the snackbar too.
    val labels by rememberUpdatedState(
        SnackbarLabels(
            retry = stringResource(Res.string.skills_retry),
            pageFailed = stringResource(Res.string.skills_load_more_failed),
            loadFailed = stringResource(SkillErrorKind.LOAD_SKILLS.fallback),
            importFailed = stringResource(SkillErrorKind.IMPORT_SKILL.fallback),
        ),
    )
    LaunchedEffect(viewModel, snackbarHostState) {
        // A newer load failure replaces the one on screen; a save's warnings and a failed import's
        // Retry, sent once, are never cut short.
        viewModel.events.collectSnackbarEvents(slot = ::listSnackbarSlot) { event ->
            showListEvent(event, labels, snackbarHostState, viewModel)
        }
    }

    SkillsListScreenContent(
        uiState = uiState,
        actions = SkillsListActions(
            onSkillClick = onSkillClick,
            onCreateSkill = onCreateSkill,
            onBack = onBack,
            onImport = { importPicker.launch(emptyList()) },
            onRefresh = viewModel::refresh,
            onLoadMore = viewModel::loadMore,
            onRetryLoadMore = viewModel::retryLoadMore,
            onLoadFirstPage = viewModel::loadFirstPage,
            onSearchQueryChange = viewModel::onSearchQueryChanged,
            onDismissImportFailure = viewModel::dismissImportFailure,
        ),
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

/** Load failures are superseded by the next; warnings and import failures exist once. */
internal fun listSnackbarSlot(event: SkillsListEvent): SnackbarSlot = when (event) {
    is SkillsListEvent.LoadFailed, SkillsListEvent.PageFailed -> SnackbarSlot.REPLACEABLE
    is SkillsListEvent.ImportFailed, is SkillsListEvent.SaveWarnings -> SnackbarSlot.KEPT
}

private data class SnackbarLabels(
    val retry: String,
    val pageFailed: String,
    val loadFailed: String,
    val importFailed: String,
)

private suspend fun showListEvent(
    event: SkillsListEvent,
    labels: SnackbarLabels,
    host: SnackbarHostState,
    viewModel: SkillsListViewModel,
) {
    val message: String
    val onRetry: (() -> Unit)?
    when (event) {
        is SkillsListEvent.LoadFailed -> {
            message = event.error.message ?: labels.loadFailed
            onRetry = viewModel::refresh
        }
        SkillsListEvent.PageFailed -> {
            message = labels.pageFailed
            onRetry = viewModel::retryLoadMore
        }
        is SkillsListEvent.ImportFailed -> {
            message = event.error.message ?: labels.importFailed
            onRetry = if (event.canRetry) viewModel::retryImport else null
        }
        is SkillsListEvent.SaveWarnings -> {
            message = event.messages.joinToString("\n")
            onRetry = null
        }
    }
    val result = host.showSnackbar(
        message = message,
        actionLabel = labels.retry.takeIf { onRetry != null },
        // M3 holds a snackbar with an action until it is tapped; this one steps aside on its own.
        duration = SnackbarDuration.Long,
    )
    if (result == SnackbarResult.ActionPerformed) onRetry?.invoke()
}
