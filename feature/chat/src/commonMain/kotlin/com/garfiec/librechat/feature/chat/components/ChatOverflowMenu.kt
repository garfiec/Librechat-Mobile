package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Compare
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.SaveAs
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.usage.ContextUsage
import com.garfiec.librechat.core.ui.components.AdaptiveDivider
import com.garfiec.librechat.core.ui.components.AdaptiveDropdownMenu
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.action_archive
import com.garfiec.librechat.feature.chat.resources.action_duplicate
import com.garfiec.librechat.feature.chat.resources.action_rename
import com.garfiec.librechat.feature.chat.resources.action_search
import com.garfiec.librechat.feature.chat.resources.action_share
import com.garfiec.librechat.feature.chat.resources.action_show_all_media
import com.garfiec.librechat.feature.chat.resources.cd_comparison_enabled
import com.garfiec.librechat.feature.chat.resources.compare_models
import com.garfiec.librechat.feature.chat.resources.delete
import com.garfiec.librechat.feature.chat.resources.load_preset
import com.garfiec.librechat.feature.chat.resources.prompts_library
import com.garfiec.librechat.feature.chat.resources.save_as_preset
import com.garfiec.librechat.feature.chat.resources.trace_open
import org.jetbrains.compose.resources.stringResource

/**
 * The chat top bar's overflow menu in Material style, shared by the Android and iOS floating top
 * bars. What it shows comes from [sections] ([chatOverflowSections]) — the same list the native iOS
 * menu is built from — so this composable only decides how each item looks. Each action dismisses the
 * menu before running.
 */
@Composable
internal fun ChatOverflowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    sections: List<List<ChatOverflowItem>>,
    isComparisonEnabled: Boolean,
    contextUsage: ContextUsage?,
    onItem: (ChatOverflowItem) -> Unit,
) {
    AdaptiveDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        offset = DpOffset(x = 0.dp, y = 8.dp),
    ) {
        // No conversation-title header here: a long title's full (untruncated) width is what a
        // single-line Text reports as its max intrinsic width, and DropdownMenu measures at
        // IntrinsicSize.Max — so the title would stretch the whole menu to full screen width.
        sections.forEachIndexed { index, section ->
            if (index > 0) AdaptiveDivider(modifier = Modifier.padding(vertical = 4.dp))
            section.forEach { item ->
                val select = {
                    onDismiss()
                    onItem(item)
                }
                when (item) {
                    ChatOverflowItem.SEARCH ->
                        OverflowItem(stringResource(Res.string.action_search), Icons.Default.Search, select)
                    ChatOverflowItem.SHOW_ALL_MEDIA ->
                        OverflowItem(stringResource(Res.string.action_show_all_media), Icons.Outlined.PhotoLibrary, select)
                    ChatOverflowItem.LOAD_PRESET ->
                        OverflowItem(stringResource(Res.string.load_preset), Icons.Outlined.FileOpen, select)
                    ChatOverflowItem.SAVE_PRESET ->
                        OverflowItem(stringResource(Res.string.save_as_preset), Icons.Outlined.SaveAs, select)
                    ChatOverflowItem.PROMPTS_LIBRARY ->
                        OverflowItem(stringResource(Res.string.prompts_library), Icons.Outlined.AutoAwesome, select)
                    ChatOverflowItem.COMPARE -> CompareItem(isComparisonEnabled, select)
                    // The conversation trace (v0.8.8-rc3). Upstream puts it here on mobile too — the
                    // desktop header has a dedicated button, and HeaderMenu carries the same action
                    // behind the overflow control.
                    ChatOverflowItem.TRACE ->
                        OverflowItem(stringResource(Res.string.trace_open), Icons.Outlined.Timeline, select)
                    // Opening the breakdown sheet is the host's job: that modal can't be opened from
                    // inside this popup without nesting modal surfaces.
                    ChatOverflowItem.CONTEXT_USAGE ->
                        contextUsage?.let { ContextUsageMenuItem(usage = it, onClick = select) }
                    ChatOverflowItem.SHARE ->
                        OverflowItem(stringResource(Res.string.action_share), Icons.Outlined.Share, select)
                    ChatOverflowItem.RENAME ->
                        OverflowItem(stringResource(Res.string.action_rename), Icons.Outlined.Edit, select)
                    ChatOverflowItem.DUPLICATE ->
                        OverflowItem(stringResource(Res.string.action_duplicate), Icons.Outlined.ContentCopy, select)
                    ChatOverflowItem.ARCHIVE ->
                        OverflowItem(stringResource(Res.string.action_archive), Icons.Outlined.Archive, select)
                    ChatOverflowItem.DELETE -> DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(Res.string.delete),
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = select,
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun OverflowItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        leadingIcon = { Icon(icon, contentDescription = null) },
    )
}

@Composable
private fun CompareItem(isComparisonEnabled: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(Res.string.compare_models),
                    modifier = Modifier.weight(1f),
                )
                if (isComparisonEnabled) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = stringResource(Res.string.cd_comparison_enabled),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        },
        onClick = onClick,
        leadingIcon = { Icon(Icons.Outlined.Compare, contentDescription = null) },
    )
}
