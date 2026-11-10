package com.garfiec.librechat.feature.chat.components.mcpapp

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.intl.Locale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.model.mcp.McpAppOccurrence
import com.garfiec.librechat.core.model.mcp.UiResource
import com.garfiec.librechat.core.ui.theme.isSurfaceDark
import com.garfiec.librechat.core.ui.theme.pageColors
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.mcp_app_at_capacity
import com.garfiec.librechat.feature.chat.resources.mcp_app_busy
import com.garfiec.librechat.feature.chat.resources.mcp_app_disabled
import com.garfiec.librechat.feature.chat.resources.mcp_app_reauth
import com.garfiec.librechat.feature.chat.resources.mcp_app_show
import com.garfiec.librechat.feature.chat.resources.mcp_app_show_apps
import com.garfiec.librechat.feature.chat.resources.mcp_app_waiting_message
import com.garfiec.librechat.feature.chat.resources.mcp_app_waiting_tool
import com.garfiec.librechat.feature.chat.viewmodel.McpAppEvent
import com.garfiec.librechat.feature.chat.viewmodel.McpAppsViewModel
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.getString

/**
 * The open Apps as the thread's cards see them: the view model's sessions and the pooled web views
 * that outlive the cards.
 */
@Stable
class McpAppsController internal constructor(
    internal val viewModel: McpAppsViewModel,
    internal val pool: McpAppViewPool,
)

/**
 * What an App row can do. [enabled] mirrors `mcpApps.enabled`; rows draw nothing without it.
 * [readOnly] is a surface where Apps can't run (a shared conversation): the row says so instead.
 * [controller] runs open Apps inline in their cards; without one (read-only) rows stay rows.
 */
@Immutable
data class McpApps(
    val enabled: Boolean,
    val readOnly: Boolean,
    val open: (McpAppOccurrence) -> Unit,
    val controller: McpAppsController? = null,
) {
    companion object {
        /** For the shared-chat viewer: rows explain that Apps don't run there. */
        val ReadOnly = McpApps(enabled = true, readOnly = true, open = {})
    }
}

/** Provided by `ChatRoot`; null where Apps never show (previews), in which case rows draw nothing. */
val LocalMcpApps = compositionLocalOf<McpApps?> { null }

/**
 * Hosts the open Apps: their pooled web views (above the message list, so a card scrolling away
 * only detaches its view), the full-screen panel, the one question that may show and the notices,
 * and returns the [McpApps] the rows read. [onSendMessage] sends an approved App message as a user
 * turn (false when refused). Leaving the chat disposes this, which closes every App; Android
 * recreating its Activity (rotation, fold) disposes it too but keeps the Apps, which start over in
 * new web views.
 */
@Composable
fun rememberMcpApps(
    viewModel: McpAppsViewModel,
    snackbarHostState: SnackbarHostState,
    onSendMessage: (String) -> Boolean,
): McpApps {
    val policy by viewModel.policy.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val awaiting by viewModel.awaiting.collectAsStateWithLifecycle()
    val presentedId by viewModel.presented.collectAsStateWithLifecycle()
    val locationRequest by viewModel.locationRequest.collectAsStateWithLifecycle()
    val send by rememberUpdatedState(onSendMessage)
    val isRecreatingUi = rememberIsRecreatingUi()
    val lowRam = rememberIsLowRamDevice()
    val pool = rememberMcpAppViewPool(
        onMemoryPressure = viewModel::onMemoryPressure,
        onRendererExit = viewModel::onViewLost,
    )

    DisposableEffect(viewModel) {
        viewModel.attachMessageSink { text -> send(text) }
        onDispose {
            viewModel.attachMessageSink(null)
            // Navigating away from the chat closes the Apps; the view model outlives a recreation.
            if (!isRecreatingUi()) viewModel.closeAll()
        }
    }
    LaunchedEffect(viewModel, lowRam) { viewModel.setLowRamDevice(lowRam) }
    // A closed (or evicted) App's web view goes with it.
    LaunchedEffect(pool, state.sessions, state.evicted) {
        pool.retain(state.sessions.mapTo(HashSet()) { it.id }, state.evicted)
    }

    val uriHandler = LocalUriHandler.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is McpAppEvent.OpenLink -> runCatching { uriHandler.openUri(event.url) }
                is McpAppEvent.Notice -> launch {
                    val text = when (event.notice) {
                        McpAppNotice.APPS_DISABLED -> getString(Res.string.mcp_app_disabled)
                        McpAppNotice.BUSY -> getString(Res.string.mcp_app_busy)
                        McpAppNotice.REAUTH_REQUIRED -> getString(Res.string.mcp_app_reauth, event.serverName)
                    }
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(text)
                }
                McpAppEvent.AtCapacity -> launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    val result = snackbarHostState.showSnackbar(
                        message = getString(Res.string.mcp_app_at_capacity),
                        actionLabel = getString(Res.string.mcp_app_show_apps),
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.showApps()
                }
                is McpAppEvent.Waiting -> launch {
                    val text = getString(
                        if (event.isMessage) Res.string.mcp_app_waiting_message else Res.string.mcp_app_waiting_tool,
                        event.serverName,
                    )
                    snackbarHostState.currentSnackbarData?.dismiss()
                    val result = snackbarHostState.showSnackbar(message = text, actionLabel = getString(Res.string.mcp_app_show))
                    // Shown full screen, where it is the App on screen and its question comes up.
                    if (result == SnackbarResult.ActionPerformed) viewModel.expand(event.sessionId)
                }
            }
        }
    }

    // Registered whether or not it is used, as an activity-result launcher must be.
    val askOsForLocation = rememberCoarseLocationRequester(viewModel::answerLocationPermission)
    val presented = presentedId?.let { id -> state.sessions.firstOrNull { it.id == id } }
    val dialogs: @Composable () -> Unit = {
        McpAppPresentedDialogs(
            session = presented,
            locationRequest = locationRequest,
            onLocationConsent = viewModel::answerLocationConsent,
            askOsForLocation = askOsForLocation,
            onStop = viewModel::close,
        )
    }
    val panelSession = state.panelSession
    if (panelSession != null) {
        McpAppPanel(
            current = panelSession,
            sessions = state.sessions,
            awaiting = awaiting,
            pool = pool,
            onSelect = viewModel::expand,
            onMinimize = viewModel::minimize,
            onClose = viewModel::close,
            onRetry = viewModel::retry,
            onHostContext = viewModel::updateHostContext,
            dialogs = dialogs,
        )
    } else {
        dialogs()
    }

    return remember(policy.enabled, viewModel, pool) {
        McpApps(
            enabled = policy.enabled,
            readOnly = false,
            open = viewModel::open,
            controller = controllerOf(viewModel, pool),
        )
    }
}

/** Not a composable: the cards reach the view model through it, never as a parameter. */
private fun controllerOf(viewModel: McpAppsViewModel, pool: McpAppViewPool) = McpAppsController(viewModel, pool)

/**
 * Measures where the App is drawn and reports the host context for it in [displayMode], re-sent on
 * theme or size change. [heightDp] overrides the measured height (inline: the most it may take).
 */
@Composable
internal fun McpAppHostContextReporter(
    displayMode: String,
    onHostContext: (kotlinx.serialization.json.JsonObject) -> Unit,
    heightDp: Int? = null,
) {
    val dark = isSurfaceDark()
    val page = pageColors()
    val locale = Locale.current.toLanguageTag()
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val width = maxWidth.value.toInt()
        val height = heightDp ?: maxHeight.value.toInt()
        val context = remember(dark, page, locale, displayMode, width, height) {
            buildHostContext(
                dark = dark,
                locale = locale,
                timeZone = TimeZone.currentSystemDefault().id,
                displayMode = displayMode,
                widthDp = width,
                heightDp = height,
                styles = hostStyleVariables(page),
            )
        }
        val report by rememberUpdatedState(onHostContext)
        LaunchedEffect(context) { report(context) }
    }
}

/**
 * What `\ui{…}` markers render from: [byId] (the conversation's resources, [conversationUiResources]
 * at the top) while the server allows legacy HTML, and the confirmation for a resource's message,
 * drawn here so a resource scrolling away never takes the question with it.
 */
@Composable
fun rememberLegacyUiResources(viewModel: McpAppsViewModel, byId: Map<String, UiResource>): LegacyUiResources {
    val policy by viewModel.policy.collectAsStateWithLifecycle()
    val pending by viewModel.legacyMessage.collectAsStateWithLifecycle()
    pending?.let { request ->
        key(request.id) {
            LegacyUiMessageDialog(
                text = request.text,
                onAnswer = { send -> viewModel.answerLegacyMessage(request.id, send) },
            )
        }
    }
    return remember(policy.legacyHtmlEnabled, byId, viewModel) {
        LegacyUiResources(enabled = policy.legacyHtmlEnabled, byId = byId, onMessage = viewModel::requestLegacyMessage)
    }
}
