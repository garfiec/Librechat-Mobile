package com.garfiec.librechat.core.ui.media

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.files.fileKindOf
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveDropdownMenu
import com.garfiec.librechat.core.ui.components.AdaptiveModalBottomSheet
import com.garfiec.librechat.core.ui.components.FileTypeTile
import com.garfiec.librechat.core.ui.components.LowProfileDragHandle
import com.garfiec.librechat.core.ui.components.fileTypeSubtitle
import com.garfiec.librechat.core.ui.resources.Res
import com.garfiec.librechat.core.ui.resources.cd_file_actions
import com.garfiec.librechat.core.ui.resources.from_code_sandbox
import com.garfiec.librechat.core.ui.resources.open_in
import com.garfiec.librechat.core.ui.resources.save_to_device
import com.garfiec.librechat.core.ui.resources.share
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** What can be done with a file's bytes once they are on the device. */
enum class FileAction { OPEN_IN, SAVE, SHARE }

/**
 * A bottom sheet for a file no viewer opens: its tile, name, size and where it came from, then
 * Open in…, Save to device and Share. [busy] is the action whose download is in flight; every
 * action is held until it lands.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileActionsSheet(
    filename: String,
    type: String?,
    bytes: Long?,
    fromCodeSandbox: Boolean,
    busy: FileAction?,
    onAction: (FileAction) -> Unit,
    onDismiss: () -> Unit,
) {
    AdaptiveModalBottomSheet(onDismissRequest = onDismiss, dragHandle = { LowProfileDragHandle() }) {
        FileActionsSheetContent(
            filename = filename,
            type = type,
            bytes = bytes,
            fromCodeSandbox = fromCodeSandbox,
            busy = busy,
            onAction = onAction,
        )
    }
}

@Composable
fun FileActionsSheetContent(
    filename: String,
    type: String?,
    bytes: Long?,
    fromCodeSandbox: Boolean,
    busy: FileAction?,
    onAction: (FileAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val kind = remember(filename, type) { fileKindOf(filename, type) }
    Column(modifier = modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FileTypeTile(kind = kind, size = 40.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = filename,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.MiddleEllipsis,
                )
                val origin = if (fromCodeSandbox) stringResource(Res.string.from_code_sandbox) else null
                Text(
                    text = listOfNotNull(fileTypeSubtitle(kind, filename, bytes), origin).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // Three lines so a large font wraps the origin rather than cutting it off.
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(modifier = Modifier.size(4.dp))
        FILE_ACTIONS.forEach { (action, icon, label) ->
            FileActionRow(
                icon = icon,
                label = stringResource(label),
                busy = busy == action,
                enabled = busy == null,
                onClick = { onAction(action) },
            )
        }
    }
}

@Composable
private fun FileActionRow(icon: ImageVector, label: String, busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (busy) AdaptiveCircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
    }
}

/**
 * An overflow button whose menu holds the three [FileAction]s, for a viewer's top bar where three
 * more icons would not fit. [busy] swaps the icon for a spinner and holds the menu.
 */
@Composable
fun FileActionsMenu(
    onAction: (FileAction) -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }, enabled = !busy) {
            if (busy) {
                AdaptiveCircularProgressIndicator(modifier = Modifier.size(20.dp), color = tint, strokeWidth = 2.dp)
            } else {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(Res.string.cd_file_actions),
                    tint = tint,
                )
            }
        }
        AdaptiveDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            FILE_ACTIONS.forEach { (action, icon, label) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    leadingIcon = { Icon(icon, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onAction(action)
                    },
                )
            }
        }
    }
}

private data class FileActionEntry(val action: FileAction, val icon: ImageVector, val label: StringResource)

private val FILE_ACTIONS = listOf(
    FileActionEntry(FileAction.OPEN_IN, Icons.AutoMirrored.Filled.OpenInNew, Res.string.open_in),
    FileActionEntry(FileAction.SAVE, Icons.Outlined.SaveAlt, Res.string.save_to_device),
    FileActionEntry(FileAction.SHARE, Icons.Default.Share, Res.string.share),
)
