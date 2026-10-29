package com.garfiec.librechat.feature.conversations.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.ConversationTag
import com.garfiec.librechat.core.ui.components.AdaptiveAlertDialog
import com.garfiec.librechat.core.ui.components.AdaptiveCard
import com.garfiec.librechat.core.ui.components.AdaptiveDropdownMenu
import com.garfiec.librechat.core.ui.components.InitialTile
import com.garfiec.librechat.core.ui.components.MenuDragSelection
import com.garfiec.librechat.core.ui.components.dragSelectRowIcon
import com.garfiec.librechat.core.ui.components.menuDragAnchor
import com.garfiec.librechat.core.ui.components.menuDragTarget
import com.garfiec.librechat.core.ui.components.rememberMenuDragSelection
import com.garfiec.librechat.core.ui.resources.bookmark_chat_count
import com.garfiec.librechat.core.ui.resources.bookmark_edit
import com.garfiec.librechat.feature.conversations.resources.Res
import com.garfiec.librechat.feature.conversations.resources.bookmark_delete
import com.garfiec.librechat.feature.conversations.resources.bookmark_delete_body
import com.garfiec.librechat.feature.conversations.resources.bookmark_delete_title
import com.garfiec.librechat.feature.conversations.resources.bookmark_no_chats
import com.garfiec.librechat.feature.conversations.resources.bookmark_options
import com.garfiec.librechat.feature.conversations.resources.cancel
import com.garfiec.librechat.feature.conversations.resources.delete
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import com.garfiec.librechat.core.ui.resources.Res as CoreRes

private val CardShape = RoundedCornerShape(16.dp)
private val TileSize = 40.dp

/**
 * A bookmark on the Bookmarks index: its tile, name, description and how many chats carry it, with
 * Edit and Delete under ⋮. [lifted] while a drag holds it: raised, and opaque so the cards it passes
 * over don't show through it.
 */
@Composable
internal fun BookmarkCard(
    bookmark: ConversationTag,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    lifted: Boolean = false,
) {
    val name = bookmark.tag.orEmpty()
    val liftElevation by animateDpAsState(if (lifted) 6.dp else 0.dp, label = "BookmarkLiftElevation")
    AdaptiveCard(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .shadow(liftElevation, CardShape)
            .background(if (lifted) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent, CardShape),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            InitialTile(seed = name, label = name, size = TileSize)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Every slot keeps its height when empty, so cards in a grid row line up.
                Text(
                    text = bookmark.description?.trim().orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = if (bookmark.count > 0) {
                        pluralStringResource(CoreRes.plurals.bookmark_chat_count, bookmark.count, bookmark.count)
                    } else {
                        stringResource(Res.string.bookmark_no_chats)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            BookmarkMenuButton(onEdit = onEdit, onDelete = onDelete)
        }
    }
}

/**
 * A bookmark's ⋮: a tap opens Edit / Delete, or a hold opens them and a drag picks the item under the
 * finger on release, as on the project cards.
 */
@Composable
private fun BookmarkMenuButton(onEdit: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val drag = rememberMenuDragSelection()
    Box(
        modifier = Modifier.menuDragAnchor(
            drag,
            opensOnDrag = false,
            onOpen = { expanded = true },
            onCancel = { expanded = false },
        ),
    ) {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(Res.string.bookmark_options))
        }
        AdaptiveDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            offset = DpOffset(0.dp, 0.dp),
            dragSelection = drag,
        ) {
            BookmarkMenuItem(drag, Icons.Default.Edit, stringResource(CoreRes.string.bookmark_edit)) {
                expanded = false
                onEdit()
            }
            BookmarkMenuItem(
                drag,
                Icons.Default.Delete,
                stringResource(Res.string.bookmark_delete),
                tint = MaterialTheme.colorScheme.error,
            ) {
                expanded = false
                onDelete()
            }
        }
    }
}

@Composable
private fun BookmarkMenuItem(
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

/** First-load stand-in that mirrors the card's slots, so the grid doesn't jump on arrival. */
@Composable
internal fun BookmarkCardPlaceholder(modifier: Modifier = Modifier) {
    val block = MaterialTheme.colorScheme.surfaceContainerHighest
    val typography = MaterialTheme.typography
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(12.dp),
    ) {
        Box(Modifier.size(TileSize).clip(RoundedCornerShape(12.dp)).background(block))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            PlaceholderLine(block, typography.titleMedium, barHeight = 16.dp, widthFraction = 0.5f)
            Spacer(Modifier.height(4.dp))
            PlaceholderLine(block, typography.bodyMedium, barHeight = 12.dp, widthFraction = 0.9f)
            PlaceholderLine(block, typography.bodyMedium, barHeight = 12.dp, widthFraction = 0.6f)
            Spacer(Modifier.height(8.dp))
            PlaceholderLine(block, typography.labelMedium, barHeight = 12.dp, widthFraction = 0.3f)
        }
    }
}

/** One text line's slot at [style]'s line height, with a bar centred in it. */
@Composable
private fun PlaceholderLine(color: Color, style: TextStyle, barHeight: Dp, widthFraction: Float) {
    val lineHeight = with(LocalDensity.current) { style.lineHeight.toDp() }
    Box(Modifier.fillMaxWidth().height(lineHeight), contentAlignment = Alignment.CenterStart) {
        Box(Modifier.fillMaxWidth(widthFraction).height(barHeight).clip(RoundedCornerShape(4.dp)).background(color))
    }
}

/**
 * Confirms deleting [name]. The second line says how many chats lose it, and is left out when none
 * carry it.
 */
@Composable
internal fun BookmarkDeleteDialog(
    name: String,
    chatCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AdaptiveAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.bookmark_delete_title, name)) },
        text = if (chatCount > 0) {
            { Text(pluralStringResource(Res.plurals.bookmark_delete_body, chatCount, chatCount)) }
        } else {
            null
        },
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
