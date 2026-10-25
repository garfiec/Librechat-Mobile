package com.garfiec.librechat.feature.skills.screen

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.model.Skill
import com.garfiec.librechat.core.ui.components.AdaptiveAlertDialog
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveGroupedPage
import com.garfiec.librechat.core.ui.components.AdaptiveScaffold
import com.garfiec.librechat.core.ui.components.AdaptiveSnackbarHost
import com.garfiec.librechat.core.ui.components.AdaptiveSwitch
import com.garfiec.librechat.core.ui.components.ErrorBanner
import com.garfiec.librechat.core.ui.components.PlatformBackHandler
import com.garfiec.librechat.core.ui.components.adaptiveSection
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBar
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBarSpec
import com.garfiec.librechat.core.ui.components.topbar.BarAction
import com.garfiec.librechat.core.ui.components.topbar.BarIcons
import com.garfiec.librechat.core.ui.components.topbar.BarMenuItem
import com.garfiec.librechat.core.ui.components.topbar.BarMenuSection
import com.garfiec.librechat.core.ui.components.topbar.BarNavigation
import com.garfiec.librechat.core.ui.components.topbar.BarTitle
import com.garfiec.librechat.core.ui.glass.GlassControlColors
import com.garfiec.librechat.feature.skills.ShareSummary
import com.garfiec.librechat.feature.skills.SkillErrorKind
import com.garfiec.librechat.feature.skills.components.SkillDetailHeader
import com.garfiec.librechat.feature.skills.components.SkillFileRemoveDialog
import com.garfiec.librechat.feature.skills.components.SkillFileReplaceDialog
import com.garfiec.librechat.feature.skills.components.SkillFilesActions
import com.garfiec.librechat.feature.skills.components.SkillShareActions
import com.garfiec.librechat.feature.skills.components.SkillShareSheet
import com.garfiec.librechat.feature.skills.components.rememberSkillFilePicker
import com.garfiec.librechat.feature.skills.components.skillFilesSection
import com.garfiec.librechat.feature.skills.components.title
import com.garfiec.librechat.feature.skills.resources.Res
import com.garfiec.librechat.feature.skills.resources.skill_active_load_failed
import com.garfiec.librechat.feature.skills.resources.skill_cancel
import com.garfiec.librechat.feature.skills.resources.skill_delete
import com.garfiec.librechat.feature.skills.resources.skill_delete_confirm_message
import com.garfiec.librechat.feature.skills.resources.skill_delete_confirm_title
import com.garfiec.librechat.feature.skills.resources.skill_delete_failed
import com.garfiec.librechat.feature.skills.resources.skill_deleting
import com.garfiec.librechat.feature.skills.resources.skill_edit
import com.garfiec.librechat.feature.skills.resources.skill_empty_body
import com.garfiec.librechat.feature.skills.resources.skill_share
import com.garfiec.librechat.feature.skills.resources.skill_share_load_failed
import com.garfiec.librechat.feature.skills.resources.skill_share_private
import com.garfiec.librechat.feature.skills.resources.skill_share_shared
import com.garfiec.librechat.feature.skills.resources.skill_sharing
import com.garfiec.librechat.feature.skills.resources.skill_toggle_failed
import com.garfiec.librechat.feature.skills.resources.skill_toggle_rendered
import com.garfiec.librechat.feature.skills.resources.skill_toggle_source
import com.garfiec.librechat.feature.skills.resources.skill_toggle_too_many_overrides
import com.garfiec.librechat.feature.skills.resources.skill_use_in_chats
import com.garfiec.librechat.feature.skills.resources.skill_version_label
import com.garfiec.librechat.feature.skills.resources.skills_back
import com.garfiec.librechat.feature.skills.resources.skills_badge_public
import com.garfiec.librechat.feature.skills.resources.skills_more_options
import com.garfiec.librechat.feature.skills.resources.skills_retry
import com.garfiec.librechat.feature.skills.resources.skills_title
import com.garfiec.librechat.feature.skills.shareSummary
import com.garfiec.librechat.feature.skills.text
import com.garfiec.librechat.feature.skills.util.FrontmatterField
import com.garfiec.librechat.feature.skills.util.HEADER_FRONTMATTER_KEYS
import com.garfiec.librechat.feature.skills.util.parseFrontmatter
import com.garfiec.librechat.feature.skills.viewmodel.SkillAclUiState
import com.garfiec.librechat.feature.skills.viewmodel.SkillAclViewModel
import com.garfiec.librechat.feature.skills.viewmodel.SkillDetailAction
import com.garfiec.librechat.feature.skills.viewmodel.SkillDetailEvent
import com.garfiec.librechat.feature.skills.viewmodel.SkillDetailUiState
import com.garfiec.librechat.feature.skills.viewmodel.SkillDetailViewModel
import com.garfiec.librechat.feature.skills.viewmodel.SkillFilesEvent
import com.garfiec.librechat.feature.skills.viewmodel.SkillFilesUiState
import com.garfiec.librechat.feature.skills.viewmodel.SkillFilesViewModel
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.components.MarkdownComponents
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownHeader
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.MarkdownTypography
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.compose.resources.pluralStringResource
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
    // Obtained locally (not forwarded params) so detekt's
    // ComposableForwardingViewModel rule is satisfied — each is passed to a
    // single section, mirroring the agent editor's pattern.
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
            uploadFailed = stringResource(SkillErrorKind.UPLOAD_FILE.fallback),
            removeFailed = stringResource(SkillErrorKind.DELETE_FILE.fallback),
            skillMdRefused = stringResource(SkillErrorKind.SKILL_MD_UPLOAD.fallback),
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

    AdaptiveScaffold(
        modifier = modifier,
        // Matches the grouped page, so the bar and system-bar insets share its colour.
        containerColor = GlassControlColors.groupedBackground,
        snackbarHost = { AdaptiveSnackbarHost(snackbarHostState) },
        topBar = {
            AdaptiveTopBar(
                spec = detailTopBarSpec(
                    state = uiState,
                    canShare = aclState.canShare,
                    onBack = viewModel::onBackRequested,
                    onEdit = onEdit,
                    onToggleSource = viewModel::toggleSource,
                    onShare = aclViewModel::openSheet,
                    onDelete = viewModel::requestDelete,
                ),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val skill = uiState.skill
            when {
                uiState.isLoading -> AdaptiveCircularProgressIndicator(Modifier.align(Alignment.Center))
                skill == null -> uiState.error?.let { error ->
                    // Same shape as the list's failed load: a Retry here, not only back-and-reopen.
                    ErrorBanner(
                        message = error.text(),
                        onRetry = viewModel::load,
                        retryLabel = stringResource(Res.string.skills_retry),
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }
                else -> SkillDetailBody(
                    skill = skill,
                    state = uiState,
                    aclState = aclState,
                    filesState = filesState,
                    onToggleActive = viewModel::toggleActive,
                    onRetryActive = viewModel::retryActiveState,
                    onOpenSharing = aclViewModel::openSheet,
                    filesActions = SkillFilesActions(
                        onOpen = { file -> onOpenFile(file.relativePath) },
                        onRemove = filesViewModel::requestRemove,
                        onAdd = { filePicker.launch(emptyList()) },
                        onRetry = filesViewModel::load,
                    ),
                    // The deleting overlay blocks touch only; a screen reader would still reach the
                    // rows beneath it, so the body leaves the accessibility tree while it is up.
                    modifier = if (uiState.isDeleting) Modifier.clearAndSetSemantics {} else Modifier,
                )
            }
            if (uiState.isDeleting) DeletingOverlay()
        }
    }

    filesState.pendingRemoval?.let { file ->
        SkillFileRemoveDialog(file = file, onConfirm = filesViewModel::confirmRemove, onDismiss = filesViewModel::dismissRemove)
    }
    filesState.pendingReplacement?.let { path ->
        SkillFileReplaceDialog(path = path, onConfirm = filesViewModel::confirmReplace, onDismiss = filesViewModel::dismissReplace)
    }
    if (aclState.showSheet && aclState.canShare) {
        SkillShareSheet(
            state = aclState,
            authorId = uiState.skill?.author,
            viewerIsAuthor = uiState.isOwnedByViewer,
            actions = SkillShareActions(
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
        )
    }
    if (uiState.showDeleteConfirm) {
        SkillDeleteDialog(
            name = uiState.skill?.title.orEmpty(),
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDelete,
        )
    }
}

/**
 * Over the page while the DELETE runs: back waits for it (its landing pops this page), so the
 * page says it is working instead of looking frozen, and taps don't reach the rows beneath.
 */
@Composable
private fun DeletingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GlassControlColors.groupedBackground.copy(alpha = DELETING_SCRIM_ALPHA))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 32.dp).semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            AdaptiveCircularProgressIndicator()
            Text(
                text = stringResource(Res.string.skill_deleting),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

private const val DELETING_SCRIM_ALPHA = 0.72f

private data class DetailSnackbarLabels(
    val deleteFailed: String,
    val toggleFailed: String,
    val tooManyOverrides: String,
    val uploadFailed: String,
    val removeFailed: String,
    val skillMdRefused: String,
    val reloadFailed: String,
    val retry: String,
) {
    fun fileActionFailed(kind: SkillErrorKind): String = when (kind) {
        SkillErrorKind.DELETE_FILE -> removeFailed
        SkillErrorKind.SKILL_MD_UPLOAD -> skillMdRefused
        else -> uploadFailed
    }
}

/**
 * Edit as an icon; the rest in ⋮: show source or rendered, Share, Delete. Each appears only where
 * the viewer may use it, and none can be used while a delete or a leave is in flight.
 */
@Composable
private fun detailTopBarSpec(
    state: SkillDetailUiState,
    canShare: Boolean,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onToggleSource: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
): AdaptiveTopBarSpec {
    val skill = state.skill
    val idle = !state.isHeld
    val actions = if (skill == null) {
        emptyList()
    } else {
        val menuItems = buildList {
            add(
                BarMenuItem(
                    id = "source",
                    label = stringResource(if (state.showSource) Res.string.skill_toggle_rendered else Res.string.skill_toggle_source),
                    icon = if (state.showSource) BarIcons.Rendered else BarIcons.Source,
                    onClick = onToggleSource,
                    enabled = idle,
                ),
            )
            if (canShare) {
                add(BarMenuItem("share", stringResource(Res.string.skill_share), BarIcons.Share, onShare, enabled = idle))
            }
            if (state.canDelete) {
                add(
                    BarMenuItem(
                        id = "delete",
                        label = stringResource(Res.string.skill_delete),
                        icon = BarIcons.Delete,
                        onClick = onDelete,
                        destructive = true,
                        enabled = idle,
                    ),
                )
            }
        }
        buildList {
            if (state.canEdit) {
                add(
                    BarAction.Icon(
                        id = "edit",
                        icon = BarIcons.Edit,
                        label = stringResource(Res.string.skill_edit),
                        enabled = idle,
                        onClick = { onEdit(skill.id) },
                    ),
                )
            }
            add(
                BarAction.Menu(
                    id = "more",
                    icon = BarIcons.More,
                    label = stringResource(Res.string.skills_more_options),
                    sections = listOf(BarMenuSection(menuItems)),
                ),
            )
        }
    }
    return AdaptiveTopBarSpec(
        navigation = BarNavigation(BarIcons.Back, stringResource(Res.string.skills_back), onBack),
        title = BarTitle(skill?.title ?: stringResource(Res.string.skills_title)),
        actions = actions,
    )
}

@Composable
private fun SkillDetailBody(
    skill: Skill,
    state: SkillDetailUiState,
    aclState: SkillAclUiState,
    filesState: SkillFilesUiState,
    onToggleActive: () -> Unit,
    onRetryActive: () -> Unit,
    onOpenSharing: () -> Unit,
    filesActions: SkillFilesActions,
    modifier: Modifier = Modifier,
) {
    // The sharing sheet's public state is fresher than the skill's once it has loaded.
    val isPublic = if (aclState.canShare && aclState.loaded) aclState.isPublic else skill.isPublic == true
    // Nothing opens another page or starts a change while a delete or a leave is in flight.
    val interactive = !state.isHeld
    AdaptiveGroupedPage(modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "header", contentType = "header") {
                SkillDetailHeader(
                    skill = skill,
                    isOwnedByViewer = state.isOwnedByViewer,
                    isPublic = isPublic,
                    categories = state.categories,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                )
            }
            if (skill.description.isNotBlank()) {
                item(key = "description", contentType = "description") {
                    Text(
                        text = skill.description,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            item(key = "settings_gap", contentType = "gap") { Spacer(Modifier.height(8.dp)) }
            adaptiveSection {
                row("active", contentType = "active") {
                    UseInChatsRow(state = state, onToggle = onToggleActive, onRetry = onRetryActive)
                }
                // Without SHARE the header's Public badge is all a viewer needs to know.
                if (aclState.canShare) {
                    row("sharing", contentType = "sharing") {
                        SharingRow(
                            summary = shareSummary(aclState.loaded, aclState.principals, aclState.isPublic, skill.author),
                            failed = aclState.loadFailed,
                            enabled = interactive,
                            onClick = onOpenSharing,
                        )
                    }
                }
            }
            item(key = "body", contentType = "body") { SkillBody(skill = skill, showSource = state.showSource) }
            skillFilesSection(filesState, filesActions, enabled = interactive)
            item(key = "version", contentType = "version") {
                Text(
                    text = stringResource(Res.string.skill_version_label, skill.version),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
                )
            }
        }
    }
}

/**
 * The switch appears only once the on/off state is known: before then a switch would claim Off for
 * a skill that may well be on (and toggling on a missing snapshot would full-replace-clobber the
 * user's other overrides). While loading it shows a spinner; after a failed load, the reason and
 * Retry.
 */
@Composable
private fun UseInChatsRow(state: SkillDetailUiState, onToggle: () -> Unit, onRetry: () -> Unit) {
    if (state.activeStateFailed) {
        UseInChatsFailedRow(onRetry)
        return
    }
    val loaded = state.activeStateLoaded
    val toggle = if (loaded) {
        Modifier.toggleable(
            value = state.isActive,
            enabled = !state.isSavingActive && !state.isHeld,
            role = Role.Switch,
            onValueChange = { onToggle() },
        )
    } else {
        Modifier
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(toggle)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.skill_use_in_chats),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).padding(end = 16.dp),
        )
        if (loaded) {
            AdaptiveSwitch(checked = state.isActive, onCheckedChange = null, enabled = !state.isSavingActive && !state.isHeld)
        } else {
            AdaptiveCircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun UseInChatsFailedRow(onRetry: () -> Unit) {
    // Retry under the message, as the files section does: beside it, a long translated label
    // leaves the message a sliver to wrap in.
    Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
        Text(
            text = stringResource(Res.string.skill_use_in_chats),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(
            text = stringResource(Res.string.skill_active_load_failed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
        )
        TextButton(onClick = onRetry, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(Res.string.skills_retry))
        }
    }
}

@Composable
private fun SharingRow(summary: ShareSummary, failed: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val text = when {
        failed -> stringResource(Res.string.skill_share_load_failed)
        else -> shareSummaryText(summary)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, onClick = onClick, role = Role.Button)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(text = stringResource(Res.string.skill_sharing), style = MaterialTheme.typography.bodyLarge)
            if (text != null) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "Private", "Shared with 3 people or groups", "Public", or both of the last; null while unknown. */
@Composable
internal fun shareSummaryText(summary: ShareSummary): String? = when (summary) {
    ShareSummary.Unknown -> null
    ShareSummary.Private -> stringResource(Res.string.skill_share_private)
    is ShareSummary.Shared -> listOfNotNull(
        summary.count.takeIf { it > 0 }?.let { pluralStringResource(Res.plurals.skill_share_shared, it, it) },
        if (summary.isPublic) stringResource(Res.string.skills_badge_public) else null,
    ).joinToString(" · ")
}

@Composable
private fun SkillBody(skill: Skill, showSource: Boolean) {
    val modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp)
    when {
        skill.body.isBlank() -> Text(
            text = stringResource(Res.string.skill_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        showSource -> Text(
            text = skill.body,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            modifier = modifier,
        )
        else -> {
            val parsed = remember(skill.body) { parseFrontmatter(skill.body, HEADER_FRONTMATTER_KEYS) }
            Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (parsed.fields.isNotEmpty()) FrontmatterFields(parsed.fields)
                Markdown(
                    content = parsed.body,
                    colors = markdownColor(
                        inlineCodeBackground = MaterialTheme.colorScheme.surfaceContainerHigh,
                        codeBackground = MaterialTheme.colorScheme.surfaceContainerHigh,
                        dividerColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                    typography = skillMarkdownTypography(),
                    components = SkillMarkdownComponents,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun FrontmatterFields(fields: List<FrontmatterField>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        fields.forEach { field ->
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = field.key,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.widthIn(min = 72.dp, max = 128.dp).padding(top = 2.dp, end = 12.dp),
                )
                Text(text = field.value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }
    }
}

private val SkillMarkdownComponents: MarkdownComponents = markdownComponents(
    heading1 = { SpacedHeading(it, it.typography.h1, top = 12.dp) },
    heading2 = { SpacedHeading(it, it.typography.h2, top = 12.dp) },
    heading3 = { SpacedHeading(it, it.typography.h3, top = 8.dp) },
    heading4 = { SpacedHeading(it, it.typography.h4, top = 8.dp) },
    heading5 = { SpacedHeading(it, it.typography.h5, top = 4.dp) },
    heading6 = { SpacedHeading(it, it.typography.h6, top = 4.dp) },
)

@Composable
private fun SpacedHeading(model: MarkdownComponentModel, style: TextStyle, top: Dp) {
    Box(Modifier.padding(top = top)) { MarkdownHeader(model.content, model.node, style) }
}

@Composable
private fun skillMarkdownTypography(): MarkdownTypography {
    val type = MaterialTheme.typography
    val body = type.bodyMedium
    val mono = type.bodySmall.copy(fontFamily = FontFamily.Monospace)
    return markdownTypography(
        h1 = type.titleLarge,
        h2 = type.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        h3 = type.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        h4 = type.titleSmall,
        h5 = type.labelLarge,
        h6 = type.labelLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
        text = body,
        paragraph = body,
        quote = body.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
        code = mono,
        inlineCode = mono,
        ordered = body,
        bullet = body,
        list = body,
        table = body,
    )
}

@Composable
private fun SkillDeleteDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AdaptiveAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.skill_delete_confirm_title)) },
        text = { Text(stringResource(Res.string.skill_delete_confirm_message, name)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(Res.string.skill_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.skill_cancel)) }
        },
    )
}

/** The save's warnings exist once; failures are superseded by newer ones; Deleted and Exit leave the page. */
internal fun detailSnackbarSlot(event: SkillDetailEvent): SnackbarSlot = when (event) {
    SkillDetailEvent.Deleted, SkillDetailEvent.Exit -> SnackbarSlot.NONE
    is SkillDetailEvent.SaveWarnings -> SnackbarSlot.KEPT
    is SkillDetailEvent.ActionFailed, is SkillDetailEvent.ReloadFailed, SkillDetailEvent.TooManyOverrides ->
        SnackbarSlot.REPLACEABLE
}
