package com.garfiec.librechat.feature.chat.components.mcpapp

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.mcp.MIN_APP_VIEW_HEIGHT
import com.garfiec.librechat.core.model.mcp.McpAppOccurrence
import com.garfiec.librechat.core.ui.components.AdaptiveCard
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveDropdownMenu
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedButton
import com.garfiec.librechat.core.ui.components.AdaptiveTextButton
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.mcp_app_ask_again
import com.garfiec.librechat.feature.chat.resources.mcp_app_auto_running
import com.garfiec.librechat.feature.chat.resources.mcp_app_binding_stale
import com.garfiec.librechat.feature.chat.resources.mcp_app_close_named
import com.garfiec.librechat.feature.chat.resources.mcp_app_expand
import com.garfiec.librechat.feature.chat.resources.mcp_app_failed_to_load
import com.garfiec.librechat.feature.chat.resources.mcp_app_load_error
import com.garfiec.librechat.feature.chat.resources.mcp_app_loading_interactive_view
import com.garfiec.librechat.feature.chat.resources.mcp_app_open
import com.garfiec.librechat.feature.chat.resources.mcp_app_open_named
import com.garfiec.librechat.feature.chat.resources.mcp_app_shared_unavailable
import com.garfiec.librechat.feature.chat.resources.mcp_app_show_full
import com.garfiec.librechat.feature.chat.resources.mcp_app_tap_to_resume
import com.garfiec.librechat.feature.chat.resources.mcp_app_waiting
import com.garfiec.librechat.feature.chat.resources.retry
import org.jetbrains.compose.resources.stringResource

/** Cached per core/ui Compose perf rule 13. */
private val CARD_SHAPE = RoundedCornerShape(12.dp)
private val EVICTED_HEIGHT = MIN_APP_VIEW_HEIGHT.dp
private val SUBTITLE_PADDING = PaddingValues(start = 16.dp, end = 16.dp, bottom = 8.dp)

private val ROW_SHAPE = RoundedCornerShape(12.dp)

/**
 * The server under the bar. While the user has let tools run without asking, it says so, and a tap
 * offers "Ask again" for each of them.
 */
@Suppress("ModifierMissing")
@Composable
fun McpAppSubtitle(
    serverName: String,
    autoRunTools: Set<String>,
    onAskAgain: (String) -> Unit,
    contentPadding: PaddingValues = SUBTITLE_PADDING,
) {
    val textModifier = Modifier.padding(contentPadding)
    if (autoRunTools.isEmpty()) {
        Text(
            text = serverName,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = textModifier,
        )
        return
    }
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Text(
            text = stringResource(Res.string.mcp_app_auto_running, serverName, autoRunTools.joinToString(", ")),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .clickable(role = Role.Button) { menuOpen = true }
                .then(textModifier),
        )
        AdaptiveDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            autoRunTools.forEach { tool ->
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.mcp_app_ask_again, tool)) },
                    onClick = {
                        menuOpen = false
                        onAskAgain(tool)
                    },
                )
            }
        }
    }
}

@Suppress("ModifierMissing")
@Composable
fun LoadingOverlay() {
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AdaptiveCircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(
            text = stringResource(Res.string.mcp_app_loading_interactive_view),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Suppress("ModifierMissing")
@Composable
fun FailedOverlay(reason: McpAppLoadFailure, onRetry: () -> Unit) {
    val message = when (reason) {
        McpAppLoadFailure.TIMED_OUT -> Res.string.mcp_app_failed_to_load
        McpAppLoadFailure.BINDING_STALE -> Res.string.mcp_app_binding_stale
        McpAppLoadFailure.COULD_NOT_LOAD -> Res.string.mcp_app_load_error
    }
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (reason.retryable) {
            AdaptiveOutlinedButton(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
                Text(stringResource(Res.string.retry))
            }
        }
    }
}

/** "Show full app", for under a clipped web view, never over it: there it hides content and takes taps. */
@Suppress("ModifierMissing")
@Composable
fun ShowFullAppButton(onClick: () -> Unit) {
    AdaptiveTextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.OpenInFull,
            contentDescription = null,
            modifier = Modifier.padding(end = 8.dp).size(18.dp),
        )
        Text(stringResource(Res.string.mcp_app_show_full))
    }
}

@Suppress("ModifierMissing")
@Composable
fun McpAppCardHeader(occurrence: McpAppOccurrence, awaiting: Boolean, onExpand: () -> Unit, onClose: () -> Unit) {
    val resource = occurrence.resource
    val toolName = resource.toolName.orEmpty()
    val waitingLabel = stringResource(Res.string.mcp_app_waiting)
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Widgets,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = toolName,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (awaiting) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .semantics { contentDescription = waitingLabel },
            )
        }
        IconButton(onClick = onExpand) {
            Icon(Icons.Outlined.OpenInFull, contentDescription = stringResource(Res.string.mcp_app_expand))
        }
        IconButton(onClick = onClose) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(Res.string.mcp_app_close_named, toolName, occurrence.ordinal),
            )
        }
    }
}

/**
 * An App memory pressure tore down: its last picture, dimmed, and "Tap to resume", which opens it
 * again (a reload; nothing of its state survived).
 */
@Suppress("ModifierMissing")
@Composable
fun McpAppEvictedCardContent(occurrence: McpAppOccurrence, snapshot: ImageBitmap?, onOpen: () -> Unit) {
    val toolName = occurrence.resource.toolName.orEmpty()
    AdaptiveCard(
        modifier = Modifier.fillMaxWidth(),
        shape = CARD_SHAPE,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (snapshot != null) EVICTED_HEIGHT * 2 else EVICTED_HEIGHT)
                .clickable(role = Role.Button, onClick = onOpen),
            contentAlignment = Alignment.Center,
        ) {
            if (snapshot != null) {
                Image(
                    bitmap = snapshot,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize().alpha(0.4f),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = toolName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = stringResource(Res.string.mcp_app_tap_to_resume),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
fun windowHeightDp(): Int {
    val density = LocalDensity.current
    val height = LocalWindowInfo.current.containerSize.height
    return with(density) { height.toDp().value.toInt() }
}

@Suppress("ModifierMissing")
@Composable
fun McpAppRow(occurrence: McpAppOccurrence, readOnly: Boolean, onOpen: () -> Unit) {
    val resource = occurrence.resource
    val toolName = resource.toolName.orEmpty()
    AdaptiveCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ROW_SHAPE,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Widgets,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = toolName,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        text = resource.serverName.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val note = when {
                    // Web shows this only without inline HTML; a shared view never runs either kind.
                    readOnly -> Res.string.mcp_app_shared_unavailable
                    resource.serverBinding.isNullOrEmpty() -> Res.string.mcp_app_load_error
                    else -> null
                }
                if (note != null) {
                    Text(
                        text = stringResource(note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!readOnly && !resource.serverBinding.isNullOrEmpty()) {
                val label = stringResource(Res.string.mcp_app_open_named, toolName, occurrence.ordinal)
                AdaptiveOutlinedButton(
                    onClick = onOpen,
                    modifier = Modifier.semantics { contentDescription = label },
                ) {
                    Text(stringResource(Res.string.mcp_app_open))
                }
            }
        }
    }
}
