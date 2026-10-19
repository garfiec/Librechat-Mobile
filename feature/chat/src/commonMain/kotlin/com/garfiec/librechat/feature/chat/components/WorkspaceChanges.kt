package com.garfiec.librechat.feature.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FileCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.files.fileKindOf
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveDivider
import com.garfiec.librechat.core.ui.components.AdaptiveModalBottomSheet
import com.garfiec.librechat.core.ui.components.FileTypeTile
import com.garfiec.librechat.core.ui.platform.rememberReduceMotion
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.attachment_fallback
import com.garfiec.librechat.feature.chat.resources.cd_download_file
import com.garfiec.librechat.feature.chat.resources.file_created
import com.garfiec.librechat.feature.chat.resources.file_updated
import com.garfiec.librechat.feature.chat.resources.n_files_changed
import com.garfiec.librechat.feature.chat.resources.show_all_n
import com.garfiec.librechat.feature.chat.resources.state_collapsed
import com.garfiec.librechat.feature.chat.resources.state_expanded
import com.garfiec.librechat.feature.chat.resources.workspace_changes
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

// "Workspace changes": the files a stateful code session wrote this turn, listed once under the
// reply instead of as chips under each tool call. Port of upstream `Parts/WorkspaceChanges.tsx`.

/** A message's attachments split into the ones rendered where they arose and its workspace changes. */
internal data class WorkspaceChangesSplit(val inline: List<Attachment>, val changes: List<Attachment>)

/**
 * Upstream `partitionWorkspaceChanges`. A change is a file a stateful session authored that can be
 * fetched (`workspaceChange.profile == "stateful"` and a `filepath`); everything else stays inline.
 * Changes dedupe by `fileId`, else by agent and path, and the last record wins and moves to the end:
 * a file rewritten twice in one turn is listed once, where its final write put it.
 *
 * Returns [attachments] itself as `inline` when there are no changes, so keys built on it stay equal.
 *
 * A change whose preview is still pending (or failed) stays here rather than becoming a
 * `PendingPreviewRow`: workspace rows show no preview state, and a tap goes through `openToolFile`
 * like every other row, which downloads a file that has no preview yet.
 */
internal fun partitionWorkspaceChanges(attachments: List<Attachment>?): WorkspaceChangesSplit {
    if (attachments.isNullOrEmpty()) return NoWorkspaceChanges
    if (attachments.none { it.isWorkspaceChange() }) return WorkspaceChangesSplit(attachments, emptyList())
    val inline = ArrayList<Attachment>(attachments.size)
    val byFile = LinkedHashMap<String, Attachment>()
    for (attachment in attachments) {
        if (!attachment.isWorkspaceChange()) {
            inline += attachment
            continue
        }
        val key = workspaceChangeKey(attachment)
        byFile.remove(key)
        byFile[key] = attachment
    }
    return WorkspaceChangesSplit(inline, byFile.values.toList())
}

private val NoWorkspaceChanges = WorkspaceChangesSplit(emptyList(), emptyList())

/** Identifies one file across a turn's records: its fileId, else the agent and workspace path. */
private fun workspaceChangeKey(attachment: Attachment): String = attachment.fileId
    ?: "${attachment.agentId.orEmpty()}:${attachment.workspaceChange?.path ?: attachment.filepath}"

private fun Attachment.isWorkspaceChange(): Boolean =
    workspaceChange?.profile == STATEFUL_PROFILE && !filepath.isNullOrEmpty()

/**
 * The path a change row shows under its name: the workspace path, or null when it adds nothing
 * (absent, or just the file name again).
 */
internal fun workspaceChangePath(attachment: Attachment): String? {
    val path = attachment.workspaceChange?.path?.takeIf { it.isNotBlank() } ?: return null
    return path.takeIf { it.trimStart('/') != workspaceChangeName(attachment) }
}

/**
 * The name a change row leads with: the workspace path's last segment, else the file's own name
 * (an older record without a path). Sandbox renames are undone, as on every file chip.
 */
internal fun workspaceChangeName(attachment: Attachment): String? {
    val leaf = attachment.workspaceChange?.path?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        ?: attachmentName(attachment)
    return leaf?.let { displayFilename(it) }
}

/** What the session did to the file, when the server said so in a way this client knows. */
internal enum class WorkspaceOperation { CREATED, UPDATED }

internal fun workspaceOperation(attachment: Attachment): WorkspaceOperation? =
    when (attachment.workspaceChange?.operation) {
        "created" -> WorkspaceOperation.CREATED
        "updated" -> WorkspaceOperation.UPDATED
        else -> null
    }

/**
 * The turn's workspace changes, collapsed to a "Workspace changes · N files changed" header. Open,
 * it lists up to [INLINE_ROW_LIMIT] files; more opens the full list in a sheet. Each row opens the
 * file and has its own download button, both through [ToolFileActions].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkspaceChangesSection(
    changes: List<Attachment>,
    baseUrl: String,
    stateKey: String,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
) {
    if (changes.isEmpty()) return
    var isExpanded by rememberSaveable(key = "workspace:$stateKey") { mutableStateOf(initiallyExpanded) }
    var showAll by rememberSaveable(key = "workspace-all:$stateKey") { mutableStateOf(false) }
    val actions = rememberToolFileActions(baseUrl)
    val reduceMotion = rememberReduceMotion()

    Column(modifier = modifier.fillMaxWidth()) {
        WorkspaceChangesHeader(count = changes.size, isExpanded = isExpanded, onToggle = { isExpanded = !isExpanded })
        AnimatedVisibility(
            visible = isExpanded,
            enter = if (reduceMotion) EnterTransition.None else expandVertically(),
            exit = if (reduceMotion) ExitTransition.None else shrinkVertically(),
        ) {
            Column(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .fillMaxWidth()
                    .clip(ListShape)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, ListShape),
            ) {
                changes.take(INLINE_ROW_LIMIT).forEachIndexed { index, attachment ->
                    if (index > 0) AdaptiveDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    WorkspaceChangeRow(attachment = attachment, actions = actions)
                }
                if (changes.size > INLINE_ROW_LIMIT) {
                    AdaptiveDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ShowAllRow(count = changes.size, onClick = { showAll = true })
                }
            }
        }
    }

    if (showAll) {
        AdaptiveModalBottomSheet(onDismissRequest = { showAll = false }) {
            // A viewer opened from the sheet would open beneath it.
            WorkspaceChangesSheetContent(
                changes = changes,
                actions = actions,
                onOpen = { attachment ->
                    showAll = false
                    actions.openToolFile(attachment)
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkspaceChangesHeader(count: Int, isExpanded: Boolean, onToggle: () -> Unit) {
    val state = stringResource(if (isExpanded) Res.string.state_expanded else Res.string.state_collapsed)
    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(HeaderShape)
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics { stateDescription = state }
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.FileCopy,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(8.dp))
        // Wraps the count under the title rather than cutting it off on a narrow screen.
        FlowRow(
            modifier = Modifier.weight(1f, fill = false),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.workspace_changes),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = pluralStringResource(Res.plurals.n_files_changed, count, count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WorkspaceChangeRow(
    attachment: Attachment,
    actions: ToolFileActions,
    modifier: Modifier = Modifier,
    onOpen: (Attachment) -> Unit = actions::openToolFile,
) {
    val name = workspaceChangeName(attachment) ?: stringResource(Res.string.attachment_fallback)
    val path = remember(attachment) { workspaceChangePath(attachment) }
    val kind = remember(attachment) { fileKindOf(name, attachment.type) }
    val operation = workspaceOperation(attachment)
    val isDownloading = actions.isDownloading(attachment)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = actions.canOpen(attachment), role = Role.Button) {
                onOpen(attachment)
            }
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileTypeTile(
            kind = kind,
            size = 32.dp,
            content = if (isDownloading) {
                {
                    AdaptiveCircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                        trackColor = Color.Transparent,
                    )
                }
            } else {
                null
            },
        )
        Spacer(modifier = Modifier.width(12.dp))
        // The name gets the whole first line; the tag sits with the path, so a narrow screen or a
        // large font never trades the file name for it.
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
            if (operation != null || path != null) {
                Row(modifier = Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (operation != null) {
                        OperationBadge(operation)
                        if (path != null) Spacer(modifier = Modifier.width(8.dp))
                    }
                    if (path != null) {
                        Text(
                            text = path,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            // The end of a path names the file; its start is the folder the user knows.
                            overflow = TextOverflow.StartEllipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                }
            }
        }
        IconButton(
            onClick = { actions.downloadToolFile(attachment) },
            enabled = actions.canDownload(attachment) && !isDownloading,
        ) {
            Icon(
                imageVector = Icons.Outlined.Download,
                contentDescription = stringResource(Res.string.cd_download_file, name),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** A status label, not a control: plain text on a tinted pill, no button role. */
@Composable
private fun OperationBadge(operation: WorkspaceOperation) {
    val (label, container, content) = when (operation) {
        WorkspaceOperation.CREATED -> Triple(
            stringResource(Res.string.file_created),
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
        )
        WorkspaceOperation.UPDATED -> Triple(
            stringResource(Res.string.file_updated),
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = content,
        maxLines = 1,
        modifier = Modifier
            .background(container, BadgeShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun ShowAllRow(count: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.show_all_n, count),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun WorkspaceChangesSheetContent(
    changes: List<Attachment>,
    actions: ToolFileActions,
    onOpen: (Attachment) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.workspace_changes),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = pluralStringResource(Res.plurals.n_files_changed, changes.size, changes.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            itemsIndexed(changes, key = { _, attachment -> workspaceChangeKey(attachment) }) { index, attachment ->
                Column {
                    if (index > 0) AdaptiveDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    WorkspaceChangeRow(
                        attachment = attachment,
                        actions = actions,
                        modifier = Modifier.padding(end = 4.dp),
                        onOpen = onOpen,
                    )
                }
            }
        }
    }
}

/** Upstream shows them all; past this many a reply's tail would be mostly file rows. */
private const val INLINE_ROW_LIMIT = 5
private const val STATEFUL_PROFILE = "stateful"
private val ListShape = RoundedCornerShape(8.dp)
private val HeaderShape = RoundedCornerShape(999.dp)
private val BadgeShape = RoundedCornerShape(999.dp)
