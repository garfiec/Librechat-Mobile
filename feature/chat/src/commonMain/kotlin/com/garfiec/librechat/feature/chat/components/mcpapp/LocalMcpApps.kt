package com.garfiec.librechat.feature.chat.components.mcpapp

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.intl.Locale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.model.mcp.McpAppOccurrence
import com.garfiec.librechat.core.ui.theme.isSurfaceDark
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.mcp_app_busy
import com.garfiec.librechat.feature.chat.resources.mcp_app_disabled
import com.garfiec.librechat.feature.chat.resources.mcp_app_reauth
import com.garfiec.librechat.feature.chat.viewmodel.McpAppEvent
import com.garfiec.librechat.feature.chat.viewmodel.McpAppsViewModel
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.getString

/**
 * What an App row can do. [enabled] mirrors `mcpApps.enabled`; rows draw nothing without it.
 * [readOnly] is a surface where Apps can't run (a shared conversation): the row says so instead.
 */
@Immutable
data class McpApps(
    val enabled: Boolean,
    val readOnly: Boolean,
    val open: (McpAppOccurrence) -> Unit,
) {
    companion object {
        /** For the shared-chat viewer: rows explain that Apps don't run there. */
        val ReadOnly = McpApps(enabled = true, readOnly = true, open = {})
    }
}

/** Provided by `ChatRoot`; null where Apps never show (previews), in which case rows draw nothing. */
val LocalMcpApps = compositionLocalOf<McpApps?> { null }

/**
 * Hosts the open App's panel, its approval dialogs and its notices, and returns the [McpApps] the
 * rows read. [onSendMessage] sends an approved App message as a user turn (false when refused).
 * Leaving the chat disposes this, which closes the App; Android recreating its Activity (rotation,
 * fold) disposes it too but keeps the App, which starts over in the new web view.
 */
@Composable
fun rememberMcpApps(
    viewModel: McpAppsViewModel,
    snackbarHostState: SnackbarHostState,
    onSendMessage: (String) -> Boolean,
): McpApps {
    val policy by viewModel.policy.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val send by rememberUpdatedState(onSendMessage)
    val isRecreatingUi = rememberIsRecreatingUi()

    DisposableEffect(viewModel) {
        viewModel.attachMessageSink { text -> send(text) }
        onDispose {
            viewModel.attachMessageSink(null)
            // Navigating away from the chat closes the App; the view model outlives a recreation.
            if (!isRecreatingUi()) viewModel.close()
        }
    }

    val uriHandler = LocalUriHandler.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is McpAppEvent.OpenLink -> runCatching { uriHandler.openUri(event.url) }
                is McpAppEvent.Notice -> {
                    val text = when (event.notice) {
                        McpAppNotice.APPS_DISABLED -> getString(Res.string.mcp_app_disabled)
                        McpAppNotice.BUSY -> getString(Res.string.mcp_app_busy)
                        McpAppNotice.REAUTH_REQUIRED -> getString(Res.string.mcp_app_reauth, event.serverName)
                    }
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(text)
                }
            }
        }
    }

    session?.let { open ->
        McpAppPanel(
            session = open,
            onClose = viewModel::close,
            onRetry = viewModel::retry,
            onHostContext = viewModel::updateHostContext,
        )
    }

    return remember(policy.enabled, viewModel) {
        McpApps(enabled = policy.enabled, readOnly = false, open = viewModel::open)
    }
}

/** Measures the panel body and reports the host context for it, re-sent on theme or size change. */
@Composable
internal fun McpAppHostContextReporter(onHostContext: (kotlinx.serialization.json.JsonObject) -> Unit) {
    val dark = isSurfaceDark()
    val scheme = MaterialTheme.colorScheme
    val locale = Locale.current.toLanguageTag()
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val width = maxWidth.value.toInt()
        val height = maxHeight.value.toInt()
        val context = remember(dark, scheme, locale, width, height) {
            buildHostContext(
                dark = dark,
                locale = locale,
                timeZone = TimeZone.currentSystemDefault().id,
                widthDp = width,
                heightDp = height,
                styles = hostStyleVariables(scheme),
            )
        }
        val report by rememberUpdatedState(onHostContext)
        LaunchedEffect(context) { report(context) }
    }
}
