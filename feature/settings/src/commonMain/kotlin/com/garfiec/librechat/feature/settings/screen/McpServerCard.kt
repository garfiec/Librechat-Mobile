package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.mcp.McpDisplayStatus
import com.garfiec.librechat.core.ui.components.AdaptiveCard
import com.garfiec.librechat.core.ui.components.AdaptiveFilledTonalButton
import com.garfiec.librechat.core.ui.components.InitialTile
import com.garfiec.librechat.core.ui.components.TileStatusDot
import com.garfiec.librechat.core.ui.platform.rememberReduceMotion
import com.garfiec.librechat.core.ui.theme.StatusColors
import com.garfiec.librechat.core.ui.theme.statusColors
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.viewmodel.McpServerRow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** The one action that fixes a server's state, if there is one; everything else lives in the server's sheet. */
internal enum class McpCardAction(val label: StringResource) {
    SIGN_IN(Res.string.mcp_action_sign_in),
    RETRY(Res.string.action_retry),
    CONNECT(Res.string.mcp_action_connect),
}

internal fun McpDisplayStatus.cardAction(): McpCardAction? = when (this) {
    McpDisplayStatus.NEEDS_SIGN_IN -> McpCardAction.SIGN_IN
    McpDisplayStatus.FAILED -> McpCardAction.RETRY
    McpDisplayStatus.DISCONNECTED -> McpCardAction.CONNECT
    else -> null
}

/** Label, icon and tint for a status. Null label: nothing reported yet, so nothing to say. */
internal data class McpStatusPresentation(val label: StringResource?, val icon: ImageVector?, val tint: Color)

internal fun McpDisplayStatus.presentation(colors: StatusColors): McpStatusPresentation = when (this) {
    McpDisplayStatus.CONNECTED -> McpStatusPresentation(Res.string.mcp_status_connected, Icons.Default.CheckCircle, colors.success)
    McpDisplayStatus.CONNECTING -> McpStatusPresentation(Res.string.mcp_status_connecting, null, colors.info)
    McpDisplayStatus.NEEDS_SIGN_IN -> McpStatusPresentation(Res.string.mcp_status_needs_sign_in, Icons.Default.Key, colors.warning)
    McpDisplayStatus.NEEDS_SETUP -> McpStatusPresentation(Res.string.mcp_status_needs_setup, Icons.Default.Tune, colors.warning)
    McpDisplayStatus.ON_DEMAND -> McpStatusPresentation(Res.string.mcp_status_on_demand, Icons.Default.Schedule, colors.neutral)
    McpDisplayStatus.DISCONNECTED -> McpStatusPresentation(Res.string.mcp_status_disconnected, Icons.Default.Power, colors.neutral)
    McpDisplayStatus.FAILED -> McpStatusPresentation(Res.string.mcp_status_failed, Icons.Default.Error, colors.error)
    McpDisplayStatus.UNKNOWN -> McpStatusPresentation(null, null, colors.neutral)
}

/**
 * One server in the list: its tile with a status dot, title, description, and a meta line saying
 * the status in words. The trailing slot holds the action that fixes the server when there is one.
 */
@Composable
internal fun McpServerCard(
    row: McpServerRow,
    onOpen: () -> Unit,
    onReconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val presentation = row.status.presentation(statusColors())
    val statusLabel = presentation.label?.let { stringResource(it) }
    val container = MaterialTheme.colorScheme.surfaceContainer
    val reduceMotion = rememberReduceMotion()

    AdaptiveCard(
        onClick = onOpen,
        // No contentDescription override: it would replace the merged text, so the description,
        // tool count and "View only" would go unread. The title and status label are Text already.
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            InitialTile(seed = row.tileSeed, label = row.title, imageUrl = row.iconUrl) {
                TileStatusDot(
                    color = presentation.tint,
                    ringColor = container,
                    pulsing = row.status == McpDisplayStatus.CONNECTING && !reduceMotion,
                )
            }
            Column(Modifier.weight(1f).padding(top = 2.dp)) {
                Text(
                    text = row.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Every slot keeps its height when empty, so cards in a grid row line up.
                Text(
                    text = row.description.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
                McpMetaLine(
                    error = row.error.takeIf { row.status == McpDisplayStatus.FAILED },
                ) {
                    if (statusLabel != null) McpMetaItem(statusLabel, presentation.icon, presentation.tint)
                    if (row.status == McpDisplayStatus.CONNECTED && row.toolCount > 0) {
                        McpMetaItem(pluralStringResource(Res.plurals.mcp_tool_count, row.toolCount, row.toolCount))
                    }
                    if (!row.access.canEdit) {
                        McpMetaItem(stringResource(Res.string.mcp_view_only), Icons.Default.Lock)
                    }
                }
            }
            val action = row.status.cardAction()
            if (action != null) {
                // Capped so a long translation ("Erneut versuchen") wraps inside the button instead of
                // squeezing the title beside it to a sliver on a narrow screen.
                AdaptiveFilledTonalButton(
                    onClick = onReconnect,
                    modifier = Modifier.align(Alignment.CenterVertically).widthIn(max = ACTION_MAX_WIDTH),
                ) {
                    Text(stringResource(action.label), maxLines = 2, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/**
 * Status, tools and View only on one line that is there even when empty. A failed server's error
 * follows on the same line and ellipsizes, instead of adding a line only failed cards have.
 */
@Composable
private fun McpMetaLine(error: String?, items: @Composable RowScope.() -> Unit) {
    Box(Modifier.padding(top = 8.dp).heightIn(min = MetaIconSize), contentAlignment = Alignment.CenterStart) {
        // Holds a text line's height when no item is shown.
        Text(" ", style = MaterialTheme.typography.labelMedium, maxLines = 1, modifier = Modifier.alpha(0f))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            items()
            if (error != null) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
    }
}

@Composable
internal fun McpMetaItem(text: String, icon: ImageVector? = null, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (icon != null) Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(MetaIconSize))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (icon != null) tint else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val ACTION_MAX_WIDTH = 120.dp

/** The meta row's icons; the row is never shorter than them. */
private val MetaIconSize = 14.dp
