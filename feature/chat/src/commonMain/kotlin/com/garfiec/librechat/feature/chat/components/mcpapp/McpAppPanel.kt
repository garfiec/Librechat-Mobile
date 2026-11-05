package com.garfiec.librechat.feature.chat.components.mcpapp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.ui.components.AdaptiveAlertDialog
import com.garfiec.librechat.core.ui.components.AdaptiveButton
import com.garfiec.librechat.core.ui.components.AdaptiveCheckbox
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveDialog
import com.garfiec.librechat.core.ui.components.AdaptiveDropdownMenu
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedButton
import com.garfiec.librechat.core.ui.components.AdaptiveTabRow
import com.garfiec.librechat.core.ui.components.AdaptiveTabRowKind
import com.garfiec.librechat.core.ui.components.AdaptiveTextButton
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBar
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBarSpec
import com.garfiec.librechat.core.ui.components.topbar.BarAction
import com.garfiec.librechat.core.ui.components.topbar.BarIcons
import com.garfiec.librechat.core.ui.components.topbar.BarNavigation
import com.garfiec.librechat.core.ui.components.topbar.BarTitle
import com.garfiec.librechat.core.ui.components.topbar.MaterialBarStyle
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.cancel
import com.garfiec.librechat.feature.chat.resources.mcp_app_action_warning
import com.garfiec.librechat.feature.chat.resources.mcp_app_ask_again
import com.garfiec.librechat.feature.chat.resources.mcp_app_auto_running
import com.garfiec.librechat.feature.chat.resources.mcp_app_binding_stale
import com.garfiec.librechat.feature.chat.resources.mcp_app_cancel_pauses
import com.garfiec.librechat.feature.chat.resources.mcp_app_close_named
import com.garfiec.librechat.feature.chat.resources.mcp_app_confirm_message
import com.garfiec.librechat.feature.chat.resources.mcp_app_confirm_tool
import com.garfiec.librechat.feature.chat.resources.mcp_app_dont_ask_again
import com.garfiec.librechat.feature.chat.resources.mcp_app_failed_to_load
import com.garfiec.librechat.feature.chat.resources.mcp_app_load_error
import com.garfiec.librechat.feature.chat.resources.mcp_app_loading_interactive_view
import com.garfiec.librechat.feature.chat.resources.mcp_app_location_allow
import com.garfiec.librechat.feature.chat.resources.mcp_app_location_deny
import com.garfiec.librechat.feature.chat.resources.mcp_app_location_text
import com.garfiec.librechat.feature.chat.resources.mcp_app_location_title
import com.garfiec.librechat.feature.chat.resources.mcp_app_message_tool_context
import com.garfiec.librechat.feature.chat.resources.mcp_app_minimize
import com.garfiec.librechat.feature.chat.resources.mcp_app_run_tool
import com.garfiec.librechat.feature.chat.resources.mcp_app_send_message
import com.garfiec.librechat.feature.chat.resources.mcp_app_stop
import com.garfiec.librechat.feature.chat.resources.mcp_app_tool_line
import com.garfiec.librechat.feature.chat.resources.retry
import com.garfiec.librechat.feature.chat.viewmodel.McpAppLocationRequest
import com.garfiec.librechat.feature.chat.viewmodel.McpAppSession
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import org.jetbrains.compose.resources.stringResource

/** Cached per core/ui Compose perf rule 13. */
private val CODE_BLOCK_SHAPE = RoundedCornerShape(8.dp)

private const val APPROVE_ARM_DELAY_MS = 500L

private val SUBTITLE_PADDING = PaddingValues(start = 16.dp, end = 16.dp, bottom = 8.dp)

/**
 * The App the user expanded, full screen over the chat (a dialog window on both platforms): a bar
 * with Back (minimize: every App returns to its card and keeps running), the tool's name, its server
 * and Close (tears this App down), a tab per open App once there are two, then the App's own web
 * view, the same live one its card showed. Loading, timeout and failure draw over the web view.
 * [dialogs] is the foreground App's question, drawn here so it sits above the panel's window.
 */
@Composable
internal fun McpAppPanel(
    current: McpAppSession,
    sessions: List<McpAppSession>,
    awaiting: Set<Long>,
    pool: McpAppViewPool,
    onSelect: (Long) -> Unit,
    onMinimize: () -> Unit,
    onClose: (Long) -> Unit,
    onRetry: (Long) -> Unit,
    onHostContext: (Long, JsonObject) -> Unit,
    dialogs: @Composable () -> Unit,
) {
    val autoRunTools by current.host.autoRunTools.collectAsStateWithLifecycle()
    val resource = current.occurrence.resource
    val toolName = resource.toolName.orEmpty()
    val closeLabel = stringResource(Res.string.mcp_app_close_named, toolName, current.occurrence.ordinal)
    val waitingDot = MaterialTheme.colorScheme.primary

    // Back minimizes rather than closes: without it a second App could never be opened, since the
    // panel covers every row.
    AdaptiveDialog(
        onDismissRequest = onMinimize,
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
                        icon = BarIcons.Back,
                        contentDescription = stringResource(Res.string.mcp_app_minimize),
                        onClick = onMinimize,
                    ),
                    title = BarTitle(text = toolName, maxLines = 1),
                    actions = listOf(
                        BarAction.Icon(id = "close", icon = BarIcons.Close, label = closeLabel, onClick = { onClose(current.id) }),
                    ),
                ),
                materialStyle = MaterialBarStyle(containerColor = MaterialTheme.colorScheme.surface),
                belowBar = if (sessions.size < 2) {
                    null
                } else {
                    {
                        AdaptiveTabRow(
                            titles = sessions.map { it.occurrence.resource.toolName.orEmpty() },
                            selectedIndex = sessions.indexOfFirst { it.id == current.id }.coerceAtLeast(0),
                            onSelect = { index -> sessions.getOrNull(index)?.let { onSelect(it.id) } },
                            kind = AdaptiveTabRowKind.SECONDARY,
                            // A tab whose App waits on the user carries a dot.
                            dotColors = sessions.map { if (it.id in awaiting) waitingDot else Color.Transparent },
                        )
                    }
                },
            )
            McpAppSubtitle(
                serverName = resource.serverName.orEmpty(),
                autoRunTools = autoRunTools,
                onAskAgain = current.host::revokeAutoRun,
            )
            key(current.id) {
                McpAppFrame(
                    session = current,
                    pool = pool,
                    inline = false,
                    onHostContext = { onHostContext(current.id, it) },
                    onRetry = { onRetry(current.id) },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            }
        }
        dialogs()
    }
}

/**
 * An App's web view with its loading and failure overlays, reporting the host context for where it
 * is drawn ([inline]: the card, at most [inlineMaxHeightDp] tall; otherwise the panel).
 */
@Composable
internal fun McpAppFrame(
    session: McpAppSession,
    pool: McpAppViewPool,
    inline: Boolean,
    onHostContext: (JsonObject) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    inlineMaxHeightDp: Int? = null,
) {
    val state by session.host.state.collectAsStateWithLifecycle()
    Box(modifier = modifier) {
        McpAppHostContextReporter(
            displayMode = if (inline) McpAppHost.DISPLAY_MODE_INLINE else McpAppHost.DISPLAY_MODE_FULLSCREEN,
            onHostContext = onHostContext,
            heightDp = inlineMaxHeightDp,
        )
        val page = when (val current = state) {
            is McpAppViewState.Loading -> current.page
            is McpAppViewState.Ready -> current.page
            else -> null
        }
        if (page != null) {
            McpAppWebView(session = session, page = page, pool = pool, inline = inline, modifier = Modifier.fillMaxSize())
        }
        when (val current = state) {
            McpAppViewState.Resolving, is McpAppViewState.Loading -> LoadingOverlay()
            is McpAppViewState.Failed -> FailedOverlay(current.reason, onRetry)
            is McpAppViewState.Ready -> Unit
        }
    }
}

/**
 * The question of [session], the App on screen that waits on the user: its location question first
 * (a web view callback is blocked on it), else the head of its approval queue. Nothing for an App
 * the user can't see; those wait with a dot until shown.
 */
@Composable
internal fun McpAppPresentedDialogs(
    session: McpAppSession?,
    locationRequest: McpAppLocationRequest?,
    onLocationConsent: (allow: Boolean) -> Unit,
    askOsForLocation: () -> Unit,
    onStop: (Long) -> Unit,
) {
    session ?: return
    val host = session.host
    val askOs by rememberUpdatedState(askOsForLocation)
    val pending by host.pendingAction.collectAsStateWithLifecycle()
    val location = locationRequest?.takeIf { it.sessionId == session.id }
    when (location?.stage) {
        McpAppLocationRequest.Stage.CONSENT -> {
            McpAppLocationDialog(request = location, onAnswer = onLocationConsent)
            return
        }
        McpAppLocationRequest.Stage.OS_PERMISSION -> {
            LaunchedEffect(location) { askOs() }
            return
        }
        null -> Unit
    }
    pending?.let { action ->
        // A new action is a new dialog, so its Approve button arms afresh.
        key(session.id, action) {
            McpAppActionDialog(
                action = action,
                onApprove = { dontAskAgain -> host.approve(action, dontAskAgain) },
                onCancel = host::cancelAction,
                onStop = {
                    host.cancelAction()
                    onStop(session.id)
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

/**
 * Upstream `approval.tsx`'s confirmation: the server and tool (or the server alone for a message),
 * then the exact arguments or text, whole, in a scrolling block. Three stacked buttons, since they
 * never fit one row on a phone.
 */
@Composable
private fun McpAppActionDialog(
    action: McpAppAction,
    onApprove: (dontAskAgain: Boolean) -> Unit,
    onCancel: () -> Unit,
    onStop: () -> Unit,
) {
    val isMessage = action is McpAppAction.Message
    // Approve arms only after the dialog has been up a moment, so a tap meant for the previous
    // dialog (or the App underneath) can't approve an action the user hasn't read.
    var armed by remember(action) { mutableStateOf(false) }
    // Unchecked on every dialog: a grant is never carried from one decision to the next.
    var dontAskAgain by remember(action) { mutableStateOf(false) }
    LaunchedEffect(action) {
        delay(APPROVE_ARM_DELAY_MS)
        armed = true
    }
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
                // Never for a message: it starts a model response that may use the agent's tools.
                if (action is McpAppAction.ToolCall) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = dontAskAgain,
                                enabled = armed,
                                role = Role.Checkbox,
                                onValueChange = { dontAskAgain = it },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        AdaptiveCheckbox(checked = dontAskAgain, onCheckedChange = null, enabled = armed)
                        Text(
                            text = stringResource(Res.string.mcp_app_dont_ask_again, action.toolName),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AdaptiveButton(onClick = { onApprove(dontAskAgain) }, enabled = armed, modifier = Modifier.fillMaxWidth()) {
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

/**
 * The server under the bar. While the user has let tools run without asking, it says so, and a tap
 * offers "Ask again" for each of them.
 */
@Composable
internal fun McpAppSubtitle(
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

/**
 * Asked once per server, the first time one of its Apps wants the location. This is about the
 * device capability, not about running the App. Back or an outside tap is "Don't allow".
 */
@Composable
private fun McpAppLocationDialog(request: McpAppLocationRequest, onAnswer: (allow: Boolean) -> Unit) {
    val serverName = request.serverName
    // Allow arms only after the dialog has been up a moment: an App can time its request so a tap
    // meant for the page lands here, and Allow is remembered for the server.
    var armed by remember(request) { mutableStateOf(false) }
    LaunchedEffect(request) {
        delay(APPROVE_ARM_DELAY_MS)
        armed = true
    }
    AdaptiveAlertDialog(
        onDismissRequest = { onAnswer(false) },
        title = { Text(stringResource(Res.string.mcp_app_location_title, serverName)) },
        text = { Text(stringResource(Res.string.mcp_app_location_text, serverName)) },
        confirmButton = {
            AdaptiveButton(onClick = { onAnswer(true) }, enabled = armed) {
                Text(stringResource(Res.string.mcp_app_location_allow))
            }
        },
        dismissButton = {
            AdaptiveTextButton(onClick = { onAnswer(false) }) { Text(stringResource(Res.string.mcp_app_location_deny)) }
        },
    )
}

/**
 * "Send message from app?" for a legacy MCP-UI resource's action: the exact text, Send once armed,
 * or Cancel (also Back and an outside tap). Web sends these without asking.
 */
@Composable
internal fun LegacyUiMessageDialog(text: String, onAnswer: (send: Boolean) -> Unit) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(APPROVE_ARM_DELAY_MS)
        armed = true
    }
    AdaptiveAlertDialog(
        onDismissRequest = { onAnswer(false) },
        title = { Text(stringResource(Res.string.mcp_app_confirm_message)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(Res.string.mcp_app_action_warning) + " " +
                        stringResource(Res.string.mcp_app_message_tool_context),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest, CODE_BLOCK_SHAPE)
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                ) {
                    Text(text = text, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                }
            }
        },
        confirmButton = {
            AdaptiveButton(onClick = { onAnswer(true) }, enabled = armed) {
                Text(stringResource(Res.string.mcp_app_send_message))
            }
        },
        dismissButton = {
            AdaptiveTextButton(onClick = { onAnswer(false) }) { Text(stringResource(Res.string.cancel)) }
        },
        scrollableText = false,
    )
}
