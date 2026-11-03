package com.garfiec.librechat.feature.chat.components.mcpapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.ui.components.AdaptiveAlertDialog
import com.garfiec.librechat.core.ui.components.AdaptiveButton
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveDialog
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedButton
import com.garfiec.librechat.core.ui.components.AdaptiveTextButton
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBar
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBarSpec
import com.garfiec.librechat.core.ui.components.topbar.BarIcons
import com.garfiec.librechat.core.ui.components.topbar.BarNavigation
import com.garfiec.librechat.core.ui.components.topbar.BarTitle
import com.garfiec.librechat.core.ui.components.topbar.MaterialBarStyle
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.cancel
import com.garfiec.librechat.feature.chat.resources.mcp_app_action_warning
import com.garfiec.librechat.feature.chat.resources.mcp_app_binding_stale
import com.garfiec.librechat.feature.chat.resources.mcp_app_cancel_pauses
import com.garfiec.librechat.feature.chat.resources.mcp_app_close_named
import com.garfiec.librechat.feature.chat.resources.mcp_app_confirm_message
import com.garfiec.librechat.feature.chat.resources.mcp_app_confirm_tool
import com.garfiec.librechat.feature.chat.resources.mcp_app_failed_to_load
import com.garfiec.librechat.feature.chat.resources.mcp_app_load_error
import com.garfiec.librechat.feature.chat.resources.mcp_app_loading_interactive_view
import com.garfiec.librechat.feature.chat.resources.mcp_app_message_tool_context
import com.garfiec.librechat.feature.chat.resources.mcp_app_run_tool
import com.garfiec.librechat.feature.chat.resources.mcp_app_send_message
import com.garfiec.librechat.feature.chat.resources.mcp_app_stop
import com.garfiec.librechat.feature.chat.resources.mcp_app_tool_line
import com.garfiec.librechat.feature.chat.resources.retry
import com.garfiec.librechat.feature.chat.viewmodel.McpAppSession
import org.jetbrains.compose.resources.stringResource

/** Cached per core/ui Compose perf rule 13. */
private val CODE_BLOCK_SHAPE = RoundedCornerShape(8.dp)

/**
 * The open App, full screen over the chat (a dialog window on both platforms): a bar with Close,
 * the tool's name and its server, then the App's web view filling the rest. Loading, timeout and
 * failure draw over the web view; an approval request draws as a dialog over the panel.
 *
 * Full screen rather than inline (web's placement): a WebView in the thread's lazy list is disposed
 * when it scrolls away, which would tear the App down mid-request.
 */
@Composable
internal fun McpAppPanel(
    session: McpAppSession,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onHostContext: (kotlinx.serialization.json.JsonObject) -> Unit,
) {
    val host = session.host
    val state by host.state.collectAsStateWithLifecycle()
    val pending by host.pendingAction.collectAsStateWithLifecycle()
    val resource = session.occurrence.resource
    val toolName = resource.toolName.orEmpty()
    val serverName = resource.serverName.orEmpty()

    AdaptiveDialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .systemBarsPadding(),
        ) {
            AdaptiveTopBar(
                spec = AdaptiveTopBarSpec(
                    navigation = BarNavigation(
                        icon = BarIcons.Close,
                        contentDescription = stringResource(Res.string.mcp_app_close_named, toolName, session.occurrence.ordinal),
                        onClick = onClose,
                    ),
                    title = BarTitle(text = toolName, maxLines = 1),
                ),
                materialStyle = MaterialBarStyle(containerColor = MaterialTheme.colorScheme.surface),
            )
            Text(
                text = serverName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                McpAppHostContextReporter(onHostContext)
                val page = when (val current = state) {
                    is McpAppViewState.Loading -> current.page
                    is McpAppViewState.Ready -> current.page
                    else -> null
                }
                if (page != null) {
                    // A new attempt is a new host and a new web view.
                    key(session.attempt) {
                        McpAppWebView(page = page, host = host, modifier = Modifier.fillMaxSize())
                    }
                }
                when (val current = state) {
                    McpAppViewState.Resolving, is McpAppViewState.Loading -> LoadingOverlay()
                    is McpAppViewState.Failed -> FailedOverlay(current.reason, onRetry)
                    is McpAppViewState.Ready -> Unit
                }
            }
        }

        pending?.let { action ->
            McpAppActionDialog(
                action = action,
                onApprove = host::approve,
                onCancel = host::cancelAction,
                onStop = {
                    host.cancelAction()
                    onClose()
                },
            )
        }
    }
}

@Composable
private fun LoadingOverlay() {
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

@Composable
private fun FailedOverlay(reason: McpAppLoadFailure, onRetry: () -> Unit) {
    val message = when (reason) {
        McpAppLoadFailure.TIMED_OUT -> Res.string.mcp_app_failed_to_load
        McpAppLoadFailure.BINDING_STALE -> Res.string.mcp_app_binding_stale
        McpAppLoadFailure.COULD_NOT_LOAD, McpAppLoadFailure.UNSUPPORTED -> Res.string.mcp_app_load_error
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

/**
 * Upstream `approval.tsx`'s confirmation: the server and tool (or the server alone for a message),
 * then the exact arguments or text, whole, in a scrolling block. Three stacked buttons, since they
 * never fit one row on a phone.
 */
@Composable
private fun McpAppActionDialog(
    action: McpAppAction,
    onApprove: () -> Unit,
    onCancel: () -> Unit,
    onStop: () -> Unit,
) {
    val isMessage = action is McpAppAction.Message
    AdaptiveAlertDialog(
        // Back or an outside tap is a Cancel, which pauses the App like upstream's Cancel.
        onDismissRequest = onCancel,
        title = {
            Text(stringResource(if (isMessage) Res.string.mcp_app_confirm_message else Res.string.mcp_app_confirm_tool))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val warning = buildString {
                    append(stringResource(Res.string.mcp_app_action_warning))
                    if (isMessage) append(' ').append(stringResource(Res.string.mcp_app_message_tool_context))
                    append(' ').append(stringResource(Res.string.mcp_app_cancel_pauses))
                }
                Text(warning, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = when (action) {
                        is McpAppAction.ToolCall -> stringResource(Res.string.mcp_app_tool_line, action.serverName, action.toolName)
                        is McpAppAction.Message -> action.serverName
                    },
                    style = MaterialTheme.typography.titleSmall,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest, CODE_BLOCK_SHAPE)
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                ) {
                    Text(
                        text = when (action) {
                            is McpAppAction.ToolCall -> action.argumentsText
                            is McpAppAction.Message -> action.text
                        },
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                }
            }
        },
        confirmButton = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AdaptiveButton(onClick = onApprove, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (isMessage) Res.string.mcp_app_send_message else Res.string.mcp_app_run_tool))
                }
                AdaptiveOutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.mcp_app_stop), textAlign = TextAlign.Center)
                }
                AdaptiveTextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        },
        // The arguments block scrolls itself; the text slot must not wrap it in a second scroll.
        scrollableText = false,
    )
}
