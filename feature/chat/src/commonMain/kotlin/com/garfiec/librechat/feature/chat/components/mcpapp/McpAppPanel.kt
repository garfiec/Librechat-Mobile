package com.garfiec.librechat.feature.chat.components.mcpapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.ui.components.AdaptiveDialog
import com.garfiec.librechat.core.ui.components.AdaptiveSnackbarHost
import com.garfiec.librechat.core.ui.components.AdaptiveTabRow
import com.garfiec.librechat.core.ui.components.AdaptiveTabRowKind
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBar
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBarSpec
import com.garfiec.librechat.core.ui.components.topbar.BarAction
import com.garfiec.librechat.core.ui.components.topbar.BarIcons
import com.garfiec.librechat.core.ui.components.topbar.BarNavigation
import com.garfiec.librechat.core.ui.components.topbar.BarTitle
import com.garfiec.librechat.core.ui.components.topbar.MaterialBarStyle
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.mcp_app_close_named
import com.garfiec.librechat.feature.chat.resources.mcp_app_minimize
import com.garfiec.librechat.feature.chat.viewmodel.McpAppLocationRequest
import com.garfiec.librechat.feature.chat.viewmodel.McpAppSession
import kotlinx.serialization.json.JsonObject
import org.jetbrains.compose.resources.stringResource

/**
 * The App the user expanded, full screen over the chat (a dialog window on both platforms): a bar
 * with Back (minimize: every App returns to its card and keeps running), the tool's name, its server
 * and Close (tears this App down), a tab per open App once there are two, then the App's own web
 * view, the same live one its card showed. Loading, timeout and failure draw over the web view.
 * [dialogs] is the foreground App's question, drawn here so it sits above the panel's window.
 * [snackbarHostState] is the chat's: its host draws under this window, so its notices show here too.
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
    snackbarHostState: SnackbarHostState,
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .systemBarsPadding(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
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
            AdaptiveSnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
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
