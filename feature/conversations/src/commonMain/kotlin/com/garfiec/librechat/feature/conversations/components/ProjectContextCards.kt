package com.garfiec.librechat.feature.conversations.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptiveCard
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveDropdownMenu
import com.garfiec.librechat.core.ui.components.AdaptiveModalBottomSheet
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedTextField
import com.garfiec.librechat.core.ui.components.AdaptiveTextButton
import com.garfiec.librechat.feature.conversations.resources.Res
import com.garfiec.librechat.feature.conversations.resources.project_available_empty
import com.garfiec.librechat.feature.conversations.resources.project_available_load_failed
import com.garfiec.librechat.feature.conversations.resources.project_available_search
import com.garfiec.librechat.feature.conversations.resources.project_file_attach_failed
import com.garfiec.librechat.feature.conversations.resources.project_file_dismiss_cd
import com.garfiec.librechat.feature.conversations.resources.project_file_not_searchable
import com.garfiec.librechat.feature.conversations.resources.project_file_processing
import com.garfiec.librechat.feature.conversations.resources.project_file_ready
import com.garfiec.librechat.feature.conversations.resources.project_file_remove_cd
import com.garfiec.librechat.feature.conversations.resources.project_file_retry
import com.garfiec.librechat.feature.conversations.resources.project_file_row_cd
import com.garfiec.librechat.feature.conversations.resources.project_file_unavailable
import com.garfiec.librechat.feature.conversations.resources.project_file_unnamed
import com.garfiec.librechat.feature.conversations.resources.project_file_unreadable
import com.garfiec.librechat.feature.conversations.resources.project_file_upload_failed
import com.garfiec.librechat.feature.conversations.resources.project_files_add
import com.garfiec.librechat.feature.conversations.resources.project_files_at_limit
import com.garfiec.librechat.feature.conversations.resources.project_files_choose
import com.garfiec.librechat.feature.conversations.resources.project_files_count
import com.garfiec.librechat.feature.conversations.resources.project_files_footer
import com.garfiec.librechat.feature.conversations.resources.project_files_hide_cd
import com.garfiec.librechat.feature.conversations.resources.project_files_load_failed
import com.garfiec.librechat.feature.conversations.resources.project_files_retry
import com.garfiec.librechat.feature.conversations.resources.project_files_show_cd
import com.garfiec.librechat.feature.conversations.resources.project_files_summary
import com.garfiec.librechat.feature.conversations.resources.project_files_summary_none
import com.garfiec.librechat.feature.conversations.resources.project_files_summary_unavailable
import com.garfiec.librechat.feature.conversations.resources.project_files_title
import com.garfiec.librechat.feature.conversations.resources.project_files_upload
import com.garfiec.librechat.feature.conversations.resources.project_instructions_edit
import com.garfiec.librechat.feature.conversations.resources.project_instructions_edit_cd
import com.garfiec.librechat.feature.conversations.resources.project_instructions_empty
import com.garfiec.librechat.feature.conversations.resources.project_instructions_footer
import com.garfiec.librechat.feature.conversations.resources.project_instructions_title
import com.garfiec.librechat.feature.conversations.viewmodel.AvailableFilesUi
import com.garfiec.librechat.feature.conversations.viewmodel.ProjectFileRow
import com.garfiec.librechat.feature.conversations.viewmodel.ProjectFileStatus
import com.garfiec.librechat.feature.conversations.viewmodel.ProjectFilesUi
import com.garfiec.librechat.feature.conversations.viewmodel.ProjectUploadFailure
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private val CardModifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)

/** The project's saved instructions, three lines at most; the card and its Edit open the editor. */
@Composable
fun ProjectInstructionsCard(instructions: String, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    val editLabel = stringResource(Res.string.project_instructions_edit_cd)
    AdaptiveCard(
        onClick = onEdit,
        modifier = modifier.then(CardModifier),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(Res.string.project_instructions_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                AdaptiveTextButton(
                    onClick = onEdit,
                    modifier = Modifier.semantics { contentDescription = editLabel },
                ) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.project_instructions_edit))
                }
            }
            Text(
                text = instructions.trim().ifEmpty { stringResource(Res.string.project_instructions_empty) },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 8.dp),
            )
            Text(
                text = stringResource(Res.string.project_instructions_footer),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, end = 8.dp),
            )
        }
    }
}

/** What the Files card can ask for. */
class ProjectFilesActions(
    val onUpload: () -> Unit,
    val onChooseExisting: () -> Unit,
    val onRemove: (fileId: String) -> Unit,
    val onRetryUpload: (key: String) -> Unit,
    val onDismissUpload: (key: String) -> Unit,
    val onRetryLoad: () -> Unit,
)

/**
 * The project's reference files. Starts collapsed to one line so the chat list stays near the top
 * of a phone screen; the open state survives rotation.
 */
@Composable
fun ProjectFilesCard(
    files: ProjectFilesUi,
    maxFiles: Int,
    canUpload: Boolean,
    atLimit: Boolean,
    actions: ProjectFilesActions,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    AdaptiveCard(
        modifier = modifier.then(CardModifier),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        if (!expanded) {
            CollapsedFilesRow(files, onExpand = { expanded = true })
        } else {
            Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 12.dp)) {
                FilesHeader(files, maxFiles, canUpload, atLimit, actions, onCollapse = { expanded = false })
                when {
                    files.loadFailed && files.rows.isEmpty() -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(Res.string.project_files_load_failed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                        )
                        AdaptiveTextButton(onClick = actions.onRetryLoad) {
                            Text(stringResource(Res.string.project_files_retry))
                        }
                    }
                    files.isLoading && files.rows.isEmpty() -> Box(
                        Modifier.fillMaxWidth().padding(8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        AdaptiveCircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                    else -> files.rows.forEach { row -> ProjectFileRowItem(row, maxFiles, actions) }
                }
                Text(
                    text = if (atLimit) {
                        stringResource(Res.string.project_files_at_limit, maxFiles)
                    } else {
                        stringResource(Res.string.project_files_footer)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, end = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun CollapsedFilesRow(files: ProjectFilesUi, onExpand: () -> Unit) {
    val count = files.attachedCount
    val summary = when {
        files.loadFailed && files.rows.isEmpty() -> stringResource(Res.string.project_files_load_failed)
        count == 0 -> stringResource(Res.string.project_files_summary_none)
        files.unavailableCount > 0 -> stringResource(
            Res.string.project_files_summary_unavailable,
            pluralStringResource(Res.plurals.project_files_summary, count, count),
            files.unavailableCount,
        )
        else -> pluralStringResource(Res.plurals.project_files_summary, count, count)
    }
    val expandLabel = stringResource(Res.string.project_files_show_cd)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClickLabel = expandLabel, onClick = onExpand)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(stringResource(Res.string.project_files_title), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.width(12.dp))
        Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.Default.ExpandMore, contentDescription = null)
    }
}

@Composable
private fun FilesHeader(
    files: ProjectFilesUi,
    maxFiles: Int,
    canUpload: Boolean,
    atLimit: Boolean,
    actions: ProjectFilesActions,
    onCollapse: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Wraps rather than squeezing at a large font: the count drops under the title.
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(Res.string.project_files_title), style = MaterialTheme.typography.titleSmall)
            Text(
                text = stringResource(Res.string.project_files_count, files.attachedCount, maxFiles),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            AdaptiveTextButton(onClick = { menuOpen = true }, enabled = !atLimit) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(Res.string.project_files_add))
            }
            AdaptiveDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (canUpload) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.project_files_upload)) },
                        leadingIcon = { Icon(Icons.Default.Upload, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            actions.onUpload()
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.project_files_choose)) },
                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        actions.onChooseExisting()
                    },
                )
            }
        }
        IconButton(onClick = onCollapse) {
            Icon(Icons.Default.ExpandLess, contentDescription = stringResource(Res.string.project_files_hide_cd))
        }
    }
}

@Composable
private fun ProjectFileRowItem(row: ProjectFileRow, maxFiles: Int, actions: ProjectFilesActions) {
    val name = row.name ?: stringResource(Res.string.project_file_unnamed)
    val status = when (row.status) {
        ProjectFileStatus.READY -> stringResource(Res.string.project_file_ready)
        ProjectFileStatus.UNAVAILABLE -> stringResource(Res.string.project_file_unavailable)
        ProjectFileStatus.PROCESSING -> stringResource(Res.string.project_file_processing)
        ProjectFileStatus.FAILED -> failureText(row.failure, maxFiles)
    }
    val isError = row.status == ProjectFileStatus.UNAVAILABLE || row.status == ProjectFileStatus.FAILED
    val rowDescription = stringResource(Res.string.project_file_row_cd, name, status)
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f).clearAndSetSemantics { contentDescription = rowDescription },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (row.status == ProjectFileStatus.PROCESSING || row.isRemoving) {
                AdaptiveCircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        when {
            row.isUpload && row.status == ProjectFileStatus.FAILED -> {
                if (row.canRetry) {
                    IconButton(onClick = { actions.onRetryUpload(row.key) }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(Res.string.project_file_retry))
                    }
                }
                IconButton(onClick = { actions.onDismissUpload(row.key) }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.project_file_dismiss_cd, name))
                }
            }
            !row.isUpload && row.fileId != null -> IconButton(
                onClick = { actions.onRemove(row.fileId) },
                enabled = !row.isRemoving,
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = stringResource(Res.string.project_file_remove_cd, name),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun failureText(failure: ProjectUploadFailure?, maxFiles: Int): String = when (failure) {
    ProjectUploadFailure.UNREADABLE -> stringResource(Res.string.project_file_unreadable)
    ProjectUploadFailure.NOT_SEARCHABLE -> stringResource(Res.string.project_file_not_searchable)
    ProjectUploadFailure.FILE_LIMIT -> stringResource(Res.string.project_files_at_limit, maxFiles)
    ProjectUploadFailure.ATTACH_FAILED -> stringResource(Res.string.project_file_attach_failed)
    ProjectUploadFailure.UPLOAD_FAILED, null -> stringResource(Res.string.project_file_upload_failed)
}

/**
 * "Choose from your files": the user's own files that could join the project, searchable and
 * paged as the list scrolls. The server already leaves out the attached ones.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvailableProjectFilesSheet(
    state: AvailableFilesUi,
    onQueryChange: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onPick: (fileId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    AdaptiveModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(
                text = stringResource(Res.string.project_files_choose),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            )
            AdaptiveOutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                singleLine = true,
                placeholder = { Text(stringResource(Res.string.project_available_search)) },
                modifier = Modifier.fillMaxWidth(),
            )
            val listState = rememberLazyListState()
            val nearEnd by remember {
                derivedStateOf {
                    val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    last >= listState.layoutInfo.totalItemsCount - 3
                }
            }
            val currentOnLoadMore by rememberUpdatedState(onLoadMore)
            LaunchedEffect(listState) {
                snapshotFlow { nearEnd }.collect { if (it) currentOnLoadMore() }
            }
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                items(state.files, key = { it.fileId }) { file ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable(enabled = state.addingFileId == null, role = Role.Button) { onPick(file.fileId) }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (state.addingFileId == file.fileId) {
                            AdaptiveCircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                Icons.AutoMirrored.Filled.InsertDriveFile,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = file.filename ?: file.fileId,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                item(key = "footer") {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        when {
                            state.isLoading -> AdaptiveCircularProgressIndicator(modifier = Modifier.size(24.dp))
                            state.loadFailed -> Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(Res.string.project_available_load_failed),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                AdaptiveTextButton(onClick = onRetry) { Text(stringResource(Res.string.project_files_retry)) }
                            }
                            state.files.isEmpty() -> Text(
                                text = stringResource(Res.string.project_available_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
