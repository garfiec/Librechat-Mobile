package com.garfiec.librechat.feature.conversations.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garfiec.librechat.core.ui.components.AdaptiveAlertDialog
import com.garfiec.librechat.core.ui.components.AdaptiveButton
import com.garfiec.librechat.core.ui.components.AdaptiveDropdownMenu
import com.garfiec.librechat.core.ui.components.AdaptiveModalBottomSheet
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedTextField
import com.garfiec.librechat.core.ui.components.LowProfileDragHandle
import com.garfiec.librechat.core.ui.components.MenuDragSelection
import com.garfiec.librechat.core.ui.components.avatarColorForSeed
import com.garfiec.librechat.core.ui.components.clearFocusOnTap
import com.garfiec.librechat.core.ui.components.dragSelectRowIcon
import com.garfiec.librechat.core.ui.components.menuDragAnchor
import com.garfiec.librechat.core.ui.components.menuDragTarget
import com.garfiec.librechat.core.ui.components.rememberMenuDragSelection
import com.garfiec.librechat.feature.conversations.resources.Res
import com.garfiec.librechat.feature.conversations.resources.cancel
import com.garfiec.librechat.feature.conversations.resources.delete
import com.garfiec.librechat.feature.conversations.resources.new_chat
import com.garfiec.librechat.feature.conversations.resources.project_delete
import com.garfiec.librechat.feature.conversations.resources.project_delete_confirm
import com.garfiec.librechat.feature.conversations.resources.project_description_help
import com.garfiec.librechat.feature.conversations.resources.project_description_label
import com.garfiec.librechat.feature.conversations.resources.project_edit
import com.garfiec.librechat.feature.conversations.resources.project_name_label
import com.garfiec.librechat.feature.conversations.resources.project_options
import org.jetbrains.compose.resources.stringResource

/**
 * Shared New chat/Edit/Delete overflow menu for a project, used by both the Projects index
 * cards and the drawer folder rows (v0.8.7). Opening is the row's own tap. [onNewChat] null
 * leaves "New chat" out, for a host that can't start one.
 */
@Composable
fun ProjectActionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onNewChat: (() -> Unit)? = null,
    dragSelection: MenuDragSelection? = null,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
) {
    AdaptiveDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
        offset = offset,
        dragSelection = dragSelection,
    ) {
        if (onNewChat != null) {
            ProjectMenuItem(dragSelection, Icons.Default.AddComment, stringResource(Res.string.new_chat)) {
                onDismiss()
                onNewChat()
            }
        }
        ProjectMenuItem(dragSelection, Icons.Default.Edit, stringResource(Res.string.project_edit)) {
            onDismiss()
            onEdit()
        }
        ProjectMenuItem(
            dragSelection,
            Icons.Default.Delete,
            stringResource(Res.string.project_delete),
            tint = MaterialTheme.colorScheme.error,
        ) {
            onDismiss()
            onDelete()
        }
    }
}

@Composable
private fun ProjectMenuItem(
    dragSelection: MenuDragSelection?,
    icon: ImageVector,
    label: String,
    tint: Color = Color.Unspecified,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label, color = tint) },
        leadingIcon = {
            Icon(
                icon,
                contentDescription = null,
                tint = tint.takeOrElse { LocalContentColor.current },
                modifier = Modifier.dragSelectRowIcon(),
            )
        },
        onClick = onClick,
        modifier = Modifier.menuDragTarget(dragSelection, onClick),
    )
}

/**
 * A project's ⋮ with its menu: a tap opens it, or a hold opens it and a drag picks the item under the
 * finger on release, as on the drawer's chat rows. A drag before the hold is left to the list or the
 * drawer the button sits in, so a scroll or a swipe that starts on it still scrolls or swipes.
 */
private val MenuIconSize = 24.dp
private val MenuGapBelowIcon = 12.dp

@Composable
fun ProjectMenuButton(
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 48.dp,
    onNewChat: (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val drag = rememberMenuDragSelection()
    Box(
        modifier = modifier.menuDragAnchor(
            drag,
            opensOnDrag = false,
            onOpen = { expanded = true },
            onCancel = { expanded = false },
        ),
    ) {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(buttonSize)) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(Res.string.project_options))
        }
        ProjectActionsMenu(
            expanded = expanded,
            onDismiss = { expanded = false },
            onEdit = onEdit,
            onDelete = onDelete,
            onNewChat = onNewChat,
            dragSelection = drag,
            // The same gap below the icon whatever the button's size: a 48dp button's padding
            // already spaces it, a smaller slot needs the rest as an offset.
            offset = DpOffset(0.dp, (MenuGapBelowIcon - (buttonSize - MenuIconSize) / 2).coerceAtLeast(0.dp)),
        )
    }
}

/** A project's coloured initial, keyed on [projectId] so it survives a rename. */
@Composable
fun ProjectTile(
    projectId: String,
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val initial = remember(name) { projectInitial(name) }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * TILE_CORNER_FRACTION))
            .background(avatarColorForSeed(projectId)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontSize = (size.value * TILE_TEXT_FRACTION).sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

private const val TILE_CORNER_FRACTION = 0.3f
private const val TILE_TEXT_FRACTION = 0.45f

/** First character of [name], uppercased; a leading surrogate pair (most emoji) is kept whole. */
internal fun projectInitial(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return ""
    val first = trimmed[0]
    return if (first.isHighSurrogate() && trimmed.length > 1) trimmed.substring(0, 2) else first.uppercase()
}

/**
 * Create/edit sheet for a project: a name and an optional description. [initialName] empty is a
 * create. [onConfirm] gets the trimmed name and description; a blank description comes back empty,
 * which the caller turns into "none" on create and "clear it" on edit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectEditorSheet(
    title: String,
    confirmLabel: String,
    onConfirm: (name: String, description: String) -> Unit,
    onDismiss: () -> Unit,
    initialName: String = "",
    initialDescription: String = "",
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var description by rememberSaveable { mutableStateOf(initialDescription) }
    val focusRequester = remember { FocusRequester() }
    AdaptiveModalBottomSheet(
        modifier = Modifier.clearFocusOnTap(),
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { LowProfileDragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            AdaptiveOutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                singleLine = true,
                label = { Text(stringResource(Res.string.project_name_label)) },
            )
            AdaptiveOutlinedTextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                label = { Text(stringResource(Res.string.project_description_label)) },
                supportingText = { Text(stringResource(Res.string.project_description_help)) },
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
                AdaptiveButton(
                    enabled = name.isNotBlank(),
                    onClick = { onConfirm(name.trim(), description.trim()) },
                ) { Text(confirmLabel) }
            }
        }
    }
    // A new project starts at the name; an edit leaves the keyboard down until the user picks a field.
    if (initialName.isEmpty()) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }
}

@Composable
fun ProjectDeleteDialog(
    projectName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AdaptiveAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.project_delete)) },
        text = { Text(stringResource(Res.string.project_delete_confirm, projectName)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(Res.string.delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    )
}
