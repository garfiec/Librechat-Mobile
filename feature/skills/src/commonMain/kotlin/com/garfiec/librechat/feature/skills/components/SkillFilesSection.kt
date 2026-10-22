package com.garfiec.librechat.feature.skills.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.SkillFile
import com.garfiec.librechat.core.model.files.fileKindOf
import com.garfiec.librechat.core.ui.components.AdaptiveAlertDialog
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveDropdownMenu
import com.garfiec.librechat.core.ui.components.AdaptiveSectionHeader
import com.garfiec.librechat.core.ui.components.FileTypeTile
import com.garfiec.librechat.core.ui.components.adaptiveSection
import com.garfiec.librechat.core.ui.components.fileTypeSubtitle
import com.garfiec.librechat.feature.skills.resources.Res
import com.garfiec.librechat.feature.skills.resources.skill_cancel
import com.garfiec.librechat.feature.skills.resources.skill_file_open
import com.garfiec.librechat.feature.skills.resources.skill_file_options
import com.garfiec.librechat.feature.skills.resources.skill_file_remove
import com.garfiec.librechat.feature.skills.resources.skill_file_remove_confirm_message
import com.garfiec.librechat.feature.skills.resources.skill_file_remove_confirm_title
import com.garfiec.librechat.feature.skills.resources.skill_file_replace
import com.garfiec.librechat.feature.skills.resources.skill_file_replace_confirm_message
import com.garfiec.librechat.feature.skills.resources.skill_file_replace_confirm_title
import com.garfiec.librechat.feature.skills.resources.skill_files_add
import com.garfiec.librechat.feature.skills.resources.skill_files_empty
import com.garfiec.librechat.feature.skills.resources.skill_files_title
import com.garfiec.librechat.feature.skills.resources.skills_retry
import com.garfiec.librechat.feature.skills.text
import com.garfiec.librechat.feature.skills.viewmodel.SkillFilesUiState
import org.jetbrains.compose.resources.stringResource

/** What the files section can ask for. */
internal class SkillFilesActions(
    val onOpen: (SkillFile) -> Unit,
    val onRemove: (SkillFile) -> Unit,
    val onAdd: () -> Unit,
    val onRetry: () -> Unit,
)

/**
 * A skill's files as a grouped section (a flat list: upstream has no folder tree): each file a row
 * with its type tile and "Python · 4.1 KB", opened by a tap. With [SkillFilesUiState.canEditFiles]
 * each row has a ⋮ with Open and Remove (confirmed first), and the section ends in Add file.
 * [enabled] false holds every row and Add file still (the page is deleting or leaving).
 */
internal fun LazyListScope.skillFilesSection(state: SkillFilesUiState, actions: SkillFilesActions, enabled: Boolean = true) {
    item(key = "files_header", contentType = "files_header") { AdaptiveSectionHeader(stringResource(Res.string.skill_files_title)) }
    adaptiveSection {
        when {
            state.isLoading && state.files.isEmpty() -> row("files_loading", contentType = "files_message") {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    AdaptiveCircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
            // A failed fetch isn't "no files": say it failed and offer to try again.
            state.listFailed -> row("files_load_failed", contentType = "files_load_failed") {
                FilesLoadFailedRow(message = state.error?.text().orEmpty(), onRetry = actions.onRetry)
            }
            state.files.isEmpty() -> row("files_empty", contentType = "files_message") {
                FilesMessageRow(stringResource(Res.string.skill_files_empty))
            }
            else -> state.files.forEach { file ->
                row("file:${file.relativePath}", contentType = "file") {
                    SkillFileRow(
                        file = file,
                        canEdit = state.canEditFiles,
                        onOpen = { actions.onOpen(file) },
                        onRemove = { actions.onRemove(file) },
                        enabled = enabled,
                        isRemoving = file.relativePath in state.removing,
                    )
                }
            }
        }
        // A refresh failure under a list already shown. Upload and remove failures are snackbars.
        state.error?.takeIf { !state.listFailed }?.let { error ->
            row("files_error", contentType = "files_message") {
                FilesMessageRow(error.text(), isError = true)
            }
        }
        // Not under a list that never loaded: adding to it would be adding blind.
        if (state.canEditFiles && state.loaded) {
            row("files_add", contentType = "files_add") {
                AddFileRow(isUploading = state.isUploading, enabled = enabled, onClick = actions.onAdd)
            }
        }
    }
}

/** [isRemoving]: its DELETE is in flight, so the row waits with a spinner where its ⋮ was. */
@Composable
internal fun SkillFileRow(
    file: SkillFile,
    canEdit: Boolean,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isRemoving: Boolean = false,
) {
    val name = file.relativePath
    // The leaf names the file; a path cut at the end would show only the folder, so files in one
    // folder would look alike. The folder goes last on the second line, where an ellipsis costs least.
    val leaf = name.substringAfterLast('/')
    val folder = name.substringBeforeLast('/', missingDelimiterValue = "")
    val kind = fileKindOf(file.filename ?: leaf, file.mimeType)
    val typeAndSize = fileTypeSubtitle(kind, file.filename ?: name, file.bytes.takeIf { it > 0 })
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(
                enabled = enabled && !isRemoving,
                onClick = onOpen,
                onClickLabel = stringResource(Res.string.skill_file_open),
                role = Role.Button,
            )
            .padding(start = 16.dp, end = if (canEdit) 4.dp else 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FileTypeTile(kind = kind)
        Column(Modifier.weight(1f)) {
            Text(
                text = leaf,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (folder.isEmpty()) typeAndSize else "$typeAndSize · $folder",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when {
            isRemoving -> Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                AdaptiveCircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            canEdit -> SkillFileMenu(name = name, enabled = enabled, onOpen = onOpen, onRemove = onRemove)
        }
    }
}

@Composable
private fun SkillFileMenu(name: String, enabled: Boolean, onOpen: () -> Unit, onRemove: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, enabled = enabled) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(Res.string.skill_file_options, name))
        }
        AdaptiveDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.skill_file_open)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                onClick = {
                    expanded = false
                    onOpen()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.skill_file_remove), color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                onClick = {
                    expanded = false
                    onRemove()
                },
            )
        }
    }
}

@Composable
private fun AddFileRow(isUploading: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled && !isUploading, onClick = onClick, role = Role.Button)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            if (isUploading) {
                AdaptiveCircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Text(
            text = stringResource(Res.string.skill_files_add),
            style = MaterialTheme.typography.bodyLarge,
            color = if (isUploading) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun FilesMessageRow(text: String, isError: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    )
}

@Composable
private fun FilesLoadFailedRow(message: String, onRetry: () -> Unit) {
    // Retry under the message, as ErrorBanner does: beside it, a long translated label leaves the
    // message a sliver to wrap in.
    Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
        )
        TextButton(onClick = onRetry, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(Res.string.skills_retry))
        }
    }
}

/** Confirms removing [file] from the skill; the server deletes it for everyone. */
@Composable
internal fun SkillFileRemoveDialog(file: SkillFile, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AdaptiveAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.skill_file_remove_confirm_title)) },
        text = { Text(stringResource(Res.string.skill_file_remove_confirm_message, file.relativePath)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(Res.string.skill_file_remove), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.skill_cancel)) }
        },
    )
}

/**
 * Confirms that the picked file replaces the one already at [path]: the server overwrites it in
 * place and drops the old content, so Add doesn't do it unasked.
 */
@Composable
internal fun SkillFileReplaceDialog(path: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AdaptiveAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.skill_file_replace_confirm_title)) },
        text = { Text(stringResource(Res.string.skill_file_replace_confirm_message, path)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(Res.string.skill_file_replace), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.skill_cancel)) }
        },
    )
}
