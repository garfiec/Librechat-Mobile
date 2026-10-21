package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.common.datetime.listLabel
import com.garfiec.librechat.core.common.datetime.parseIsoInstantOrNull
import com.garfiec.librechat.core.ui.components.AdaptiveCard
import com.garfiec.librechat.core.ui.components.AdaptiveDropdownMenu
import com.garfiec.librechat.core.ui.components.InitialTile
import com.garfiec.librechat.core.ui.components.MenuDragSelection
import com.garfiec.librechat.core.ui.components.dragSelectRowIcon
import com.garfiec.librechat.core.ui.components.menuDragAnchor
import com.garfiec.librechat.core.ui.components.menuDragTarget
import com.garfiec.librechat.core.ui.components.rememberMenuDragSelection
import com.garfiec.librechat.core.ui.datetime.LocalDateTimeFormat
import com.garfiec.librechat.core.ui.datetime.LocalRelativeTimeReference
import com.garfiec.librechat.core.ui.datetime.resolve
import com.garfiec.librechat.feature.settings.model.SharedLinkDisplayData
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.action_delete
import com.garfiec.librechat.feature.settings.resources.shared_chats_copy_link
import com.garfiec.librechat.feature.settings.resources.shared_chats_open_chat
import com.garfiec.librechat.feature.settings.resources.shared_chats_options
import com.garfiec.librechat.feature.settings.resources.shared_chats_shared_on
import com.garfiec.librechat.feature.settings.resources.shared_chats_untitled
import com.garfiec.librechat.feature.settings.resources.shared_chats_view_shared
import org.jetbrains.compose.resources.stringResource

internal val SharedLinkCardShape = RoundedCornerShape(16.dp)

/**
 * One shared link: the chat's tile and title, and when it was shared. A tap opens the share sheet
 * on it; ⋮ holds Open chat, Copy link and Delete. The URL isn't shown (the sheet has it), and
 * neither is a public/private label: nothing on the wire says which a link is.
 *
 * @param onViewSharedVersion opens the link in the in-app viewer; null hides the item (no viewer, as in
 *   previews).
 */
@Composable
internal fun SharedLinkCard(
    link: SharedLinkDisplayData,
    onClick: () -> Unit,
    onOpenChat: () -> Unit,
    onCopyLink: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onViewSharedVersion: (() -> Unit)? = null,
) {
    val title = link.title ?: stringResource(Res.string.shared_chats_untitled)
    val format = LocalDateTimeFormat.current
    val reference = LocalRelativeTimeReference.current
    val shared = remember(link.createdAt, format, reference) {
        link.createdAt?.let(::parseIsoInstantOrNull)?.listLabel(format, reference)
    }
    AdaptiveCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = SharedLinkCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Seeded by the conversation, so every link to one chat wears the same colour.
            InitialTile(seed = link.conversationId, label = title)
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (shared != null) {
                    Text(
                        text = stringResource(Res.string.shared_chats_shared_on, shared.resolve()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            SharedLinkMenuButton(
                title = title,
                onOpenChat = onOpenChat,
                onViewSharedVersion = onViewSharedVersion,
                onCopyLink = onCopyLink,
                onDelete = onDelete,
            )
        }
    }
}

private val MenuIconSize = 24.dp
private val MenuButtonSize = 48.dp
private val MenuGapBelowIcon = 12.dp

/**
 * The card's ⋮: a tap opens the menu, or a hold opens it and a drag picks the item under the
 * finger on release, as on the Projects cards. A drag before the hold is left to the list.
 */
@Composable
private fun SharedLinkMenuButton(
    title: String,
    onOpenChat: () -> Unit,
    onViewSharedVersion: (() -> Unit)?,
    onCopyLink: () -> Unit,
    onDelete: () -> Unit,
) {
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
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(MenuButtonSize)) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(Res.string.shared_chats_options, title))
        }
        AdaptiveDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            offset = DpOffset(0.dp, (MenuGapBelowIcon - (MenuButtonSize - MenuIconSize) / 2).coerceAtLeast(0.dp)),
            dragSelection = drag,
        ) {
            val close: (() -> Unit) -> () -> Unit = { action ->
                {
                    expanded = false
                    action()
                }
            }
            SharedLinkMenuItem(
                drag,
                Icons.Default.ChatBubbleOutline,
                stringResource(Res.string.shared_chats_open_chat),
                onClick = close(onOpenChat),
            )
            if (onViewSharedVersion != null) {
                SharedLinkMenuItem(
                    drag,
                    Icons.Default.Visibility,
                    stringResource(Res.string.shared_chats_view_shared),
                    onClick = close(onViewSharedVersion),
                )
            }
            SharedLinkMenuItem(
                drag,
                Icons.Default.ContentCopy,
                stringResource(Res.string.shared_chats_copy_link),
                onClick = close(onCopyLink),
            )
            SharedLinkMenuItem(
                drag,
                Icons.Default.Delete,
                stringResource(Res.string.action_delete),
                tint = MaterialTheme.colorScheme.error,
                onClick = close(onDelete),
            )
        }
    }
}

@Composable
private fun SharedLinkMenuItem(
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

/** First-load stand-in, sized like a card, so the grid doesn't jump when the links arrive. */
@Composable
internal fun SharedLinkCardPlaceholder(modifier: Modifier = Modifier) {
    val block = MaterialTheme.colorScheme.surfaceContainerHighest
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(SharedLinkCardShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(block))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth(0.6f).height(16.dp).clip(RoundedCornerShape(4.dp)).background(block))
            Box(Modifier.fillMaxWidth(0.3f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(block))
        }
    }
}
