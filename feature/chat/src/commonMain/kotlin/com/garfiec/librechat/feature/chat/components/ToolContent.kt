package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.content.AgentToolCall
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveTextButton
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.retry
import com.garfiec.librechat.feature.chat.resources.tool_content_error
import com.garfiec.librechat.feature.chat.resources.tool_content_loading
import com.garfiec.librechat.feature.chat.util.ToolPartIdentity
import com.garfiec.librechat.feature.chat.util.ToolPartKey
import com.garfiec.librechat.feature.chat.util.ToolPartStatus
import kotlinx.coroutines.awaitCancellation
import org.jetbrains.compose.resources.stringResource

/**
 * The chat screen's handle on tool-call previews (v0.8.9-rc1): which previewed parts are loading or
 * failed, and the calls that fetch and hold them. Provided by `ChatRoot` from the ViewModel; absent
 * elsewhere (the shared-link view, whose reads are never previewed), where cards render as before.
 */
@Stable
class ToolContentHost(
    val statuses: Map<ToolPartKey, ToolPartStatus>,
    private val onRequest: (ToolPartKey) -> Unit,
    private val onRetain: (ToolPartIdentity) -> Unit,
    private val onRelease: (ToolPartIdentity) -> Unit,
) {
    fun request(key: ToolPartKey) = onRequest(key)
    fun retain(identity: ToolPartIdentity) = onRetain(identity)
    fun release(identity: ToolPartIdentity) = onRelease(identity)
}

val LocalToolContent = compositionLocalOf<ToolContentHost?> { null }

/**
 * The tool call a card is drawing, as hydration sees it: [key] is non-null while its content is a
 * preview. Provided by [ToolCallDispatcher] around each top-level call, so a nested call (its own
 * dispatcher) never reads its parent's.
 */
@Stable
class ToolPreviewScope(
    val identity: ToolPartIdentity,
    val key: ToolPartKey?,
    val status: ToolPartStatus?,
    val host: ToolContentHost,
) {
    val isPreview: Boolean get() = key != null
}

val LocalToolPreview = compositionLocalOf<ToolPreviewScope?> { null }

/** The scope for [call], or null outside the chat screen or for a call with no id. */
@Composable
internal fun rememberToolPreviewScope(call: AgentToolCall?, agentId: String?): ToolPreviewScope? {
    val host = LocalToolContent.current ?: return null
    val identity = remember(call?.id, call?.stepId, agentId) { ToolPartIdentity.of(call, agentId) } ?: return null
    val key = remember(call, agentId) { ToolPartKey.of(call, agentId) }
    val status = key?.let { host.statuses[it] }
    return remember(identity, key, status, host) { ToolPreviewScope(identity, key, status, host) }
}

/** True when the call being drawn is a preview, so a copy inside a disclosure would copy shortened text. */
@Composable
internal fun toolContentIsPreview(): Boolean = LocalToolPreview.current?.isPreview == true

/**
 * Placed at the top of any disclosure that reveals a tool call's args, output or transcript. While
 * [open] it asks for the full part (a no-op when loaded or loading) and holds it against eviction;
 * an effect rather than the click, so a card restored open, or carried open from the live reply,
 * asks too. Shows the loading or failed row while the content is a preview.
 */
@Composable
internal fun ToolContentDisclosure(open: Boolean, modifier: Modifier = Modifier) {
    val scope = LocalToolPreview.current ?: return
    // A preview with no status is one the delegate isn't loading (its memory was reset under the
    // open card, say), so asking again whenever that holds keeps the row below from spinning for good.
    val untracked = scope.key != null && scope.status == null
    LaunchedEffect(open, scope.identity, scope.key, untracked) {
        if (!open) return@LaunchedEffect
        scope.host.retain(scope.identity)
        try {
            scope.key?.let(scope.host::request)
            awaitCancellation()
        } finally {
            scope.host.release(scope.identity)
        }
    }
    val key = scope.key
    if (open && key != null) {
        ToolContentStatusRow(
            status = scope.status ?: ToolPartStatus.LOADING,
            onRetry = { scope.host.request(key) },
            modifier = modifier,
        )
    }
}

/** "Loading the full tool call…" with a spinner, or the failure with Retry. A polite live region. */
@Composable
internal fun ToolContentStatusRow(status: ToolPartStatus, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 32.dp)
            .semantics(mergeDescendants = false) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (status) {
            ToolPartStatus.LOADING -> {
                AdaptiveCircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(Res.string.tool_content_loading),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
            ToolPartStatus.FAILED -> {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.error)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(Res.string.tool_content_error),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.error,
                    modifier = Modifier.weight(1f),
                )
                AdaptiveTextButton(onClick = onRetry) { Text(stringResource(Res.string.retry)) }
            }
        }
    }
}
