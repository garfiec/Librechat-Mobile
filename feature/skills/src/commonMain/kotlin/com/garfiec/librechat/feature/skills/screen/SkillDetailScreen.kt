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
import com.garfiec.librechat.core.ui.components.PlatformBackHandler
import com.garfiec.librechat.feature.skills.SkillErrorKind
import com.garfiec.librechat.feature.skills.components.SkillFilesActions
import com.garfiec.librechat.feature.skills.components.SkillShareActions
import com.garfiec.librechat.feature.skills.components.rememberSkillFilePicker
import com.garfiec.librechat.feature.skills.resources.Res
import com.garfiec.librechat.feature.skills.resources.skill_delete_failed
import com.garfiec.librechat.feature.skills.resources.skill_toggle_failed
import com.garfiec.librechat.feature.skills.resources.skill_toggle_too_many_overrides
import com.garfiec.librechat.feature.skills.resources.skills_retry
import com.garfiec.librechat.feature.skills.viewmodel.SkillAclViewModel
import com.garfiec.librechat.feature.skills.viewmodel.SkillDetailAction
import com.garfiec.librechat.feature.skills.viewmodel.SkillDetailEvent
import com.garfiec.librechat.feature.skills.viewmodel.SkillDetailViewModel
import com.garfiec.librechat.feature.skills.viewmodel.SkillFilesEvent
import com.garfiec.librechat.feature.skills.viewmodel.SkillFilesViewModel
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun SkillDetailScreen(
    skillId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SkillDetailViewModel = koinViewModel { parametersOf(skillId) },
) {
    // Obtained here, not taken as parameters, so detekt's ComposableForwardingViewModel rule holds;
    // the content below sees only their state and the …Actions bound to them.
    val aclViewModel: SkillAclViewModel = koinViewModel()
    val filesViewModel: SkillFilesViewModel = koinViewModel { parametersOf(skillId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val aclState by aclViewModel.uiState.collectAsStateWithLifecycle()
    val filesState by filesViewModel.uiState.collectAsStateWithLifecycle()
    val currentOnDelete by rememberUpdatedState(onDelete)
    val currentOnBack by rememberUpdatedState(onBack)
    val snackbarHostState = remember { SnackbarHostState() }

    // Held while a delete is in flight: its landing pops this page, so a back first would let it
    // pop the list behind. Held while the on/off POST is in flight too, which leaving would cancel;
    // the page leaves when it lands. The top bar's back goes through the same check (iOS has no
    // system back).
    PlatformBackHandler(
        enabled = uiState.isDeleting || uiState.isSavingActive,
        onBack = viewModel::onBackRequested,
    )

    val filePicker = rememberSkillFilePicker(onPick = { doc -> filesViewModel.upload(doc) })

    // Load on first show and refetch on return from the editor so an edit's new
    // content replaces the pre-edit view. Nav3 retains the VM, so init alone
    // wouldn't re-fetch. load() skips the spinner when content is already present.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.load()
        filesViewModel.load()
        // Only fetch ACL grants when the user can actually share (fail-closed).
        if (aclState.canShare) aclViewModel.load(skillId)
    }

    // The per-skill gates resolve on the detail VM (role, ACL bits, owner, source); the files and
    // sharing sections follow them. The grants load when sharing opens, which can be after the
    // first resume.
    LaunchedEffect(uiState.access.canEditFiles) {
        filesViewModel.setCanEditFiles(uiState.access.canEditFiles)
    }
    LaunchedEffect(uiState.access.canShare) {
        aclViewModel.setCanShare(skillId, uiState.access.canShare)
    }

    // Resolved in composition, so an in-app language switch reaches the snackbar too.
    val labels by rememberUpdatedState(
        DetailSnackbarLabels(
            deleteFailed = stringResource(Res.string.skill_delete_failed),
            toggleFailed = stringResource(Res.string.skill_toggle_failed),
            tooManyOverrides = stringResource(Res.string.skill_toggle_too_many_overrides),
            fileErrors = SkillErrorKind.entries.associateWith { stringResource(it.fallback) },
            reloadFailed = stringResource(SkillErrorKind.LOAD_SKILL.fallback),
            retry = stringResource(Res.string.skills_retry),
        ),
    )
    LaunchedEffect(filesViewModel, snackbarHostState) {
        filesViewModel.events.collectLatest { event ->
            when (event) {
                is SkillFilesEvent.ActionFailed -> snackbarHostState.showSnackbar(
                    event.error.message ?: labels.fileActionFailed(event.error.kind),
                    duration = SnackbarDuration.Long,
                )
            }
        }
    }
    LaunchedEffect(viewModel, snackbarHostState) {
        // A newer failure replaces the one on screen; a save's warnings, sent once, are never cut short.
        viewModel.events.collectSnackbarEvents(slot = ::detailSnackbarSlot) { event ->
            val message = when (event) {
                is SkillDetailEvent.Deleted -> {
                    currentOnDelete()
                    null
                }
                is SkillDetailEvent.Exit -> {
                    currentOnBack()
                    null
                }
                is SkillDetailEvent.ActionFailed -> event.message ?: when (event.action) {
                    SkillDetailAction.DELETE -> labels.deleteFailed
                    SkillDetailAction.TOGGLE_ACTIVE -> labels.toggleFailed
                }
                SkillDetailEvent.TooManyOverrides -> labels.tooManyOverrides
                is SkillDetailEvent.SaveWarnings -> event.messages.joinToString("\n")
                is SkillDetailEvent.ReloadFailed -> {
                    val result = snackbarHostState.showSnackbar(
                        message = event.error.message ?: labels.reloadFailed,
                        actionLabel = labels.retry,
                        // M3 holds a snackbar with an action until it is tapped; this one steps aside on its own.
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.load()
                    null
                }
            }
            if (message != null) snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Long)
        }
    }

    SkillDetailScreenContent(
        uiState = uiState,
        aclState = aclState,
        filesState = filesState,
        actions = SkillDetailActions(
            onBack = viewModel::onBackRequested,
            onEdit = onEdit,
            onToggleSource = viewModel::toggleSource,
            onRequestDelete = viewModel::requestDelete,
            onConfirmDelete = viewModel::confirmDelete,
            onDismissDelete = viewModel::dismissDelete,
            onRetryLoad = viewModel::load,
            onToggleActive = viewModel::toggleActive,
            onRetryActive = viewModel::retryActiveState,
            onOpenSharing = aclViewModel::openSheet,
            onConfirmRemoveFile = filesViewModel::confirmRemove,
            onDismissRemoveFile = filesViewModel::dismissRemove,
            onConfirmReplaceFile = filesViewModel::confirmReplace,
            onDismissReplaceFile = filesViewModel::dismissReplace,
        ),
        filesActions = SkillFilesActions(
            onOpen = { file -> onOpenFile(file.relativePath) },
            onRemove = filesViewModel::requestRemove,
            onAdd = { filePicker.launch(emptyList()) },
            onRetry = filesViewModel::load,
        ),
        shareActions = SkillShareActions(
            onDismiss = aclViewModel::closeSheet,
            onSetPublic = { enabled -> aclViewModel.setPublic(enabled) },
            onPublicRoleChange = { roleId -> aclViewModel.setPublic(true, roleId) },
            onQueryChange = aclViewModel::onSearchQueryChanged,
            onRetrySearch = aclViewModel::retrySearch,
            onAdd = aclViewModel::add,
            onChangeRole = aclViewModel::changeRole,
            onRevoke = aclViewModel::revoke,
            onRetryLoad = aclViewModel::retryLoad,
            onDismissError = aclViewModel::dismissError,
        ),
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

private data class DetailSnackbarLabels(
    val deleteFailed: String,
    val toggleFailed: String,
    val tooManyOverrides: String,
    val fileErrors: Map<SkillErrorKind, String>,
    val reloadFailed: String,
    val retry: String,
) {
    fun fileActionFailed(kind: SkillErrorKind): String = fileErrors.getValue(kind)
}

/** The save's warnings exist once; failures are superseded by newer ones; Deleted and Exit leave the page. */
internal fun detailSnackbarSlot(event: SkillDetailEvent): SnackbarSlot = when (event) {
    SkillDetailEvent.Deleted, SkillDetailEvent.Exit -> SnackbarSlot.NONE
    is SkillDetailEvent.SaveWarnings -> SnackbarSlot.KEPT
    is SkillDetailEvent.ActionFailed, is SkillDetailEvent.ReloadFailed, SkillDetailEvent.TooManyOverrides ->
        SnackbarSlot.REPLACEABLE
}
