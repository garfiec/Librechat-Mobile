package com.garfiec.librechat.feature.skills.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.ui.components.AdaptiveAlertDialog
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveScaffold
import com.garfiec.librechat.core.ui.components.AdaptiveSwitch
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBar
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBarSpec
import com.garfiec.librechat.core.ui.components.topbar.BarAction
import com.garfiec.librechat.core.ui.components.topbar.BarIcons
import com.garfiec.librechat.core.ui.components.topbar.BarNavigation
import com.garfiec.librechat.core.ui.components.topbar.BarTitle
import com.garfiec.librechat.feature.skills.components.SkillAclSharingSection
import com.garfiec.librechat.feature.skills.components.SkillFileEditorDialog
import com.garfiec.librechat.feature.skills.components.SkillFilesSection
import com.garfiec.librechat.feature.skills.components.rememberSkillFilePicker
import com.garfiec.librechat.feature.skills.resources.*
import com.garfiec.librechat.feature.skills.resources.Res
import com.garfiec.librechat.feature.skills.viewmodel.SkillAclViewModel
import com.garfiec.librechat.feature.skills.viewmodel.SkillDetailEvent
import com.garfiec.librechat.feature.skills.viewmodel.SkillDetailViewModel
import com.garfiec.librechat.feature.skills.viewmodel.SkillFilesViewModel
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillDetailScreen(
    skillId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
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

    // Externally managed skills' files are read-only on the server (v0.8.8).
    LaunchedEffect(uiState.skill?.id, uiState.skill?.source) {
        if (uiState.skill != null) filesViewModel.setSkillSource(uiState.skill?.source)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SkillDetailEvent.Deleted -> currentOnDelete()
            }
        }
    }

    AdaptiveScaffold(
        modifier = modifier,
        topBar = {
            AdaptiveTopBar(
                spec = AdaptiveTopBarSpec(
                    navigation = BarNavigation(BarIcons.Back, stringResource(Res.string.skills_back), onBack),
                    title = BarTitle(
                        uiState.skill?.let { it.displayTitle?.takeIf { t -> t.isNotBlank() } ?: it.name }
                            ?: stringResource(Res.string.skills_title),
                    ),
                    actions = uiState.skill?.let { skill ->
                        buildList {
                            add(
                                BarAction.Toggle(
                                    id = "source",
                                    iconOff = BarIcons.Source,
                                    iconOn = BarIcons.Rendered,
                                    label = stringResource(Res.string.skill_toggle_source),
                                    checked = uiState.showSource,
                                    onCheckedChange = { viewModel.toggleSource() },
                                ),
                            )
                            if (uiState.canEdit) {
                                add(
                                    BarAction.Icon(
                                        id = "edit",
                                        icon = BarIcons.EditFilled,
                                        label = stringResource(Res.string.skill_edit),
                                        onClick = { onEdit(skill.id) },
                                    ),
                                )
                            }
                            if (uiState.canDelete) {
                                add(
                                    BarAction.Icon(
                                        id = "delete",
                                        icon = BarIcons.DeleteFilled,
                                        label = stringResource(Res.string.skill_delete),
                                        onClick = viewModel::requestDelete,
                                    ),
                                )
                            }
                        }
                    }.orEmpty(),
                ),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> AdaptiveCircularProgressIndicator(Modifier.align(Alignment.Center))
                uiState.error != null && uiState.skill == null -> Text(
                    text = uiState.error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                uiState.skill != null -> {
                    val skill = uiState.skill!!
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                    ) {
                        if (skill.description.isNotBlank()) {
                            Text(skill.description, style = MaterialTheme.typography.bodyMedium)
                        }
                        skill.category?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                text = stringResource(Res.string.skill_category_label, it),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        Text(
                            text = stringResource(Res.string.skill_version_label, skill.version),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(Res.string.skill_active_label),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = stringResource(Res.string.skill_active_description),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            AdaptiveSwitch(
                                checked = uiState.isActive,
                                // Disabled until the full states map loads — toggling on a
                                // null snapshot would full-replace-clobber other overrides.
                                enabled = uiState.activeStateLoaded,
                                onCheckedChange = { viewModel.toggleActive() },
                            )
                        }

                        when {
                            skill.body.isBlank() -> Text(
                                text = stringResource(Res.string.skill_empty_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            uiState.showSource -> Text(
                                text = skill.body,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            else -> Markdown(
                                content = skill.body,
                                colors = markdownColor(),
                                typography = markdownTypography(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        // Attached files (flat list; upload/delete gated on canEditFiles).
                        Spacer(modifier = Modifier.height(16.dp))
                        SkillFilesSection(
                            state = filesState,
                            onAddFile = { filePicker.launch(emptyList()) },
                            onRemoveFile = { file -> filesViewModel.delete(file) },
                            onOpenFile = filesViewModel::openFile,
                        )

                        // Sharing (ACL) — fail-CLOSED on SKILLS.SHARE.
                        if (aclState.canShare) {
                            Spacer(modifier = Modifier.height(16.dp))
                            SkillAclSharingSection(viewModel = aclViewModel)
                        }
                    }
                }
            }
        }
    }

    filesState.editor?.let { editor ->
        SkillFileEditorDialog(
            editor = editor,
            editable = editor.isEditable(filesState.canEditFiles),
            onSave = filesViewModel::saveEditor,
            onReload = filesViewModel::reloadEditor,
            onDismiss = filesViewModel::closeEditor,
        )
    }

    if (uiState.showDeleteConfirm) {
        val name = uiState.skill?.let { it.displayTitle?.takeIf { t -> t.isNotBlank() } ?: it.name } ?: ""
        AdaptiveAlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text(stringResource(Res.string.skill_delete_confirm_title)) },
            text = { Text(stringResource(Res.string.skill_delete_confirm_message, name)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) {
                    Text(stringResource(Res.string.skill_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDelete) {
                    Text(stringResource(Res.string.skill_cancel))
                }
            },
        )
    }
}
