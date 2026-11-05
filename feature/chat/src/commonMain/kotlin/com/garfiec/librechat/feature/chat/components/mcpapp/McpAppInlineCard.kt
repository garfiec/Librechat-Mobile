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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.model.mcp.McpAppOccurrence
import com.garfiec.librechat.core.ui.components.AdaptiveCard
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedButton
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.mcp_app_close_named
import com.garfiec.librechat.feature.chat.resources.mcp_app_expand
import com.garfiec.librechat.feature.chat.resources.mcp_app_in_panel
import com.garfiec.librechat.feature.chat.resources.mcp_app_return
import com.garfiec.librechat.feature.chat.resources.mcp_app_tap_to_resume
import com.garfiec.librechat.feature.chat.resources.mcp_app_waiting
import com.garfiec.librechat.feature.chat.viewmodel.McpAppSession
import com.garfiec.librechat.feature.chat.viewmodel.McpAppSessionRules
import com.garfiec.librechat.feature.chat.viewmodel.McpAppsState
import org.jetbrains.compose.resources.stringResource

/** Cached per core/ui Compose perf rule 13. */
private val CARD_SHAPE = RoundedCornerShape(12.dp)
private val FADE_HEIGHT = 40.dp
private val HEADER_SUBTITLE_PADDING = PaddingValues(start = 12.dp, end = 12.dp, bottom = 8.dp)
private val EVICTED_HEIGHT = McpAppSessionRules.MIN_INLINE_HEIGHT_DP.dp

/**
 * An open App in its place in the thread: a header (tool · server · Expand · Close) over the App's
 * live web view, pooled outside the list so scrolling the card away only detaches it. The slot is
 * the App's first reported height, clamped to [McpAppSessionRules.inlineHeightDp] and then frozen
 * (a slot that kept resizing would jump the list); an App wanting more gets a fade and Expand.
 * While the App is in the panel the card holds a "Return to app" line instead (one view, one place).
 */
@Composable
internal fun McpAppInlineCard(session: McpAppSession, state: McpAppsState, awaiting: Boolean, controller: McpAppsController) {
    val viewModel = controller.viewModel
    val resource = session.occurrence.resource
    val autoRunTools by session.host.autoRunTools.collectAsStateWithLifecycle()
    val inPanel = state.panelId == session.id

    // Only an App whose card is in the viewport may show its question (unplaced = not visible).
    var visible by remember(session.id) { mutableStateOf(false) }
    LaunchedEffect(session.id, visible) { viewModel.setInlineVisible(session.id, visible) }
    DisposableEffect(session.id) { onDispose { viewModel.setInlineVisible(session.id, false) } }

    AdaptiveCard(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { visible = it.boundsInWindow().height > 0f },
        shape = CARD_SHAPE,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column {
            McpAppCardHeader(
                session = session,
                awaiting = awaiting,
                onExpand = { viewModel.expand(session.id) },
                onClose = { viewModel.close(session.id) },
            )
            McpAppSubtitle(
                serverName = resource.serverName.orEmpty(),
                autoRunTools = autoRunTools,
                onAskAgain = session.host::revokeAutoRun,
                contentPadding = HEADER_SUBTITLE_PADDING,
            )
            if (inPanel) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.mcp_app_in_panel),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    AdaptiveOutlinedButton(onClick = { viewModel.expand(session.id) }) {
                        Text(stringResource(Res.string.mcp_app_return))
                    }
                }
            } else {
                InlineBody(session = session, frozenHeightDp = state.inlineHeights[session.id], controller = controller)
            }
        }
    }
}

@Composable
private fun InlineBody(session: McpAppSession, frozenHeightDp: Int?, controller: McpAppsController) {
    val viewModel = controller.viewModel
    val paneHeightDp = windowHeightDp()
    val capDp = McpAppSessionRules.inlineCapDp(paneHeightDp)
    val reported by session.host.reportedHeight.collectAsStateWithLifecycle()
    val hostState by session.host.state.collectAsStateWithLifecycle()
    LaunchedEffect(session.id, reported, frozenHeightDp) {
        val height = reported
        if (frozenHeightDp == null && height != null) {
            viewModel.freezeInlineHeight(session.id, McpAppSessionRules.inlineHeightDp(height, paneHeightDp))
        }
    }
    // Held to today's cap: a slot frozen in portrait must not overrun a landscape or folded window.
    val slotDp = (frozenHeightDp ?: McpAppSessionRules.DEFAULT_INLINE_HEIGHT_DP)
        .let { McpAppSessionRules.inlineHeightDp(it, paneHeightDp) }
    // Later size changes never move the slot; an App that wants more says so with a fade.
    val wantsMore = hostState is McpAppViewState.Ready && (reported ?: 0) > slotDp
    Box(modifier = Modifier.fillMaxWidth().height(slotDp.dp).clip(CARD_SHAPE)) {
        McpAppFrame(
            session = session,
            pool = controller.pool,
            inline = true,
            onHostContext = { viewModel.updateHostContext(session.id, it) },
            onRetry = { viewModel.retry(session.id) },
            modifier = Modifier.fillMaxSize(),
            inlineMaxHeightDp = capDp,
        )
        if (wantsMore) {
            val surface = MaterialTheme.colorScheme.surfaceContainerLow
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(FADE_HEIGHT)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, surface))),
            )
            AdaptiveOutlinedButton(
                onClick = { viewModel.expand(session.id) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
            ) {
                Text(stringResource(Res.string.mcp_app_expand))
            }
        }
    }
}

@Composable
private fun McpAppCardHeader(session: McpAppSession, awaiting: Boolean, onExpand: () -> Unit, onClose: () -> Unit) {
    val resource = session.occurrence.resource
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
                contentDescription = stringResource(Res.string.mcp_app_close_named, toolName, session.occurrence.ordinal),
            )
        }
    }
}

/**
 * An App memory pressure tore down: its last picture, dimmed, and "Tap to resume", which opens it
 * again (a reload; nothing of its state survived).
 */
@Composable
internal fun McpAppEvictedCard(occurrence: McpAppOccurrence, controller: McpAppsController, onOpen: () -> Unit) {
    val snapshot = remember(occurrence.key, controller) { controller.pool.snapshot(occurrence.key) }
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
private fun windowHeightDp(): Int {
    val density = LocalDensity.current
    val height = LocalWindowInfo.current.containerSize.height
    return with(density) { height.toDp().value.toInt() }
}
