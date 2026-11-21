package com.garfiec.librechat.feature.chat.screen

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.common.ChatLayoutConstants
import com.garfiec.librechat.core.data.datastore.AppPreferences
import com.garfiec.librechat.core.data.datastore.PreferenceStore
import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.model.preferences.ChatFontSize
import com.garfiec.librechat.core.model.preferences.InlineArtifactPrefs
import com.garfiec.librechat.core.model.preferences.LatexRenderer
import com.garfiec.librechat.core.ui.components.share.LocalExternalUriHandler
import com.garfiec.librechat.feature.chat.components.ChatRoot
import com.garfiec.librechat.feature.chat.components.ParsedMarkdownCache
import com.garfiec.librechat.feature.chat.components.SharedTextFileSheet
import com.garfiec.librechat.feature.chat.components.artifact.MermaidRenderCache
import com.garfiec.librechat.feature.chat.components.mcpapp.McpApps
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.shared_chat_continue_blocked
import com.garfiec.librechat.feature.chat.resources.shared_chat_continue_blocked_reason
import com.garfiec.librechat.feature.chat.resources.shared_chat_continue_failed
import com.garfiec.librechat.feature.chat.resources.shared_chat_continue_rate_limited
import com.garfiec.librechat.feature.chat.resources.shared_chat_continue_unsupported
import com.garfiec.librechat.feature.chat.resources.shared_chat_link_updated
import com.garfiec.librechat.feature.chat.resources.shared_chat_rate_limited
import com.garfiec.librechat.feature.chat.resources.shared_chat_refresh_failed
import com.garfiec.librechat.feature.chat.viewmodel.SharedChatEvent
import com.garfiec.librechat.feature.chat.viewmodel.SharedChatViewModel
import com.garfiec.librechat.feature.chat.viewmodel.SiteIconPromptSession
import com.garfiec.librechat.feature.chat.viewmodel.SiteIconsState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The in-app viewer for a shared link: the chat as it was shared, read-only, with Continue to copy
 * the branch on screen into the user's own chats.
 *
 * @param onOpenConversation Continue made [conversationId]; the host replaces the viewer with it.
 */
@Composable
fun SharedChatScreen(
    shareId: String,
    onNavigateBack: () -> Unit,
    onOpenConversation: (conversationId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SharedChatViewModel = koinViewModel { parametersOf(shareId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val display = rememberSharedChatDisplay()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    // The platform's handler, so the viewer's own link can't route back into the viewer.
    val browser = LocalExternalUriHandler.current ?: LocalUriHandler.current
    val currentOnOpenConversation by rememberUpdatedState(onOpenConversation)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message = when (event) {
                is SharedChatEvent.Continued -> {
                    currentOnOpenConversation(event.conversationId)
                    null
                }
                SharedChatEvent.LinkUpdated -> getString(Res.string.shared_chat_link_updated)
                SharedChatEvent.ContinueRateLimited -> getString(Res.string.shared_chat_continue_rate_limited)
                SharedChatEvent.ContinueFailed -> getString(Res.string.shared_chat_continue_failed)
                SharedChatEvent.ContinueUnsupported -> getString(Res.string.shared_chat_continue_unsupported)
                is SharedChatEvent.RefreshFailed -> if (event.rateLimited) {
                    getString(Res.string.shared_chat_rate_limited)
                } else {
                    getString(Res.string.shared_chat_refresh_failed)
                }
                is SharedChatEvent.ContinueBlocked -> event.serverMessage
                    ?.let { getString(Res.string.shared_chat_continue_blocked_reason, it) }
                    ?: getString(Res.string.shared_chat_continue_blocked)
            }
            if (message != null) {
                // Shown in its own coroutine: showSnackbar suspends until it hides, and the next
                // event (a retried Continue landing) must not wait behind it.
                coroutineScope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(message)
                }
            }
        }
    }

    val shareUrl = state.shareUrl
    val langfuseUrl = state.snapshot?.langfuseSessionUrl?.takeIf { it.isNotBlank() }
    val actions = SharedChatActions(
        onBack = onNavigateBack,
        onRetry = viewModel::reload,
        onContinue = viewModel::continueChat,
        onOpenInBrowser = { runCatching { browser.openUri(shareUrl) } },
        onOpenLangfuse = langfuseUrl?.let { url -> { runCatching { browser.openUri(url) } } },
    )

    ChatRoot(
        inlineArtifactPrefs = display.inlineArtifactPrefs,
        mermaidRenderCache = remember { MermaidRenderCache() },
        parsedMarkdownCache = remember { ParsedMarkdownCache() },
        subagentProgress = emptyMap(),
        ptcTraces = emptyMap(),
        previewOverrides = emptyMap(),
        // No conversation of the user's to read child threads from, and no live run to trace.
        conversationId = null,
        subagentThreadsSupported = false,
        mediaPreview = state.mediaPreview,
        onOpenMedia = viewModel::openMedia,
        onCloseMedia = viewModel::closeMedia,
        onDownloadAttachment = viewModel::downloadAttachment,
        onFileNotice = { message ->
            coroutineScope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(message)
            }
        },
        // The viewer has no prompt picker to refresh.
        promptLibraryRevision = 0L,
        onRefreshPrompts = {},
        siteIcons = display.siteIcons,
        thinkingOpenByDefault = display.thinkingOpenByDefault,
        collapseLongUserMessages = display.collapseLongUserMessages,
        onSiteIconsChoice = rememberSiteIconsChoice(),
        onSiteIconsPromptDismiss = koinInject<SiteIconPromptSession>()::dismiss,
        // The memories shown are the sharer's, not the viewer's: no Manage.
        onOpenMemories = null,
        // The sharer's keys: nothing for the viewer to add.
        userKeyErrors = null,
        agentNames = emptyMap(),
        mcpSignIn = null,
        // Public shares omit App attachments; any that remain say Apps don't run here.
        mcpApps = McpApps.ReadOnly,
        legacyUiResources = null,
    ) {
        ProvideSharedFileOpeners(state, onOpenTextFile = viewModel::openTextFile) {
            SharedChatScaffold(
                state = state,
                display = display,
                actions = actions,
                snackbarHostState = snackbarHostState,
                onSwitchBranch = viewModel::switchBranch,
                modifier = modifier,
            )
        }
    }

    state.textFile?.let { file ->
        SharedTextFileSheet(file = file, onDismiss = viewModel::closeTextFile)
    }
}

@Composable
private fun rememberSharedChatDisplay(): SharedChatDisplay {
    val settings = koinInject<SettingsDataStore>()
    val preferences = koinInject<PreferenceStore>()
    val promptSession = koinInject<SiteIconPromptSession>()
    val fontSize by settings.chatFontSize.collectAsStateWithLifecycle(ChatFontSize.MEDIUM)
    val layout by settings.chatLayoutStyle.collectAsStateWithLifecycle(ChatLayoutConstants.THREAD)
    val avatars by settings.showAvatars.collectAsStateWithLifecycle(true)
    val bubbles by settings.showBubbles.collectAsStateWithLifecycle(false)
    val imageDescriptions by settings.showImageDescriptions.collectAsStateWithLifecycle(false)
    val latex by settings.latexRenderer.collectAsStateWithLifecycle(LatexRenderer.KATEX)
    val inlineArtifacts by settings.inlineArtifactPrefs.collectAsStateWithLifecycle(InlineArtifactPrefs())
    val thinkingOpen by settings.thinkingOpenByDefault.collectAsStateWithLifecycle(false)
    val siteIconsChoice by preferences.observe(AppPreferences.SiteIcons)
        .collectAsStateWithLifecycle(preferences.initial(AppPreferences.SiteIcons))
    val promptDismissed by promptSession.dismissed.collectAsStateWithLifecycle()
    val collapseLong by preferences.observe(AppPreferences.CollapseLongUserMessages)
        .collectAsStateWithLifecycle(preferences.initial(AppPreferences.CollapseLongUserMessages))
    return SharedChatDisplay(
        fontSizeMultiplier = when (fontSize) {
            ChatFontSize.SMALL -> 0.85f
            ChatFontSize.MEDIUM -> 1.0f
            ChatFontSize.LARGE -> 1.2f
        },
        chatLayoutStyle = layout,
        showAvatars = avatars,
        showBubbles = bubbles,
        showImageDescriptions = imageDescriptions,
        useKatex = latex == LatexRenderer.KATEX,
        inlineArtifactPrefs = inlineArtifacts,
        thinkingOpenByDefault = thinkingOpen,
        collapseLongUserMessages = collapseLong,
        siteIcons = when (siteIconsChoice) {
            true -> SiteIconsState.ON
            false -> SiteIconsState.OFF
            null -> if (promptDismissed) SiteIconsState.OFF else SiteIconsState.ASK
        },
    )
}

@Composable
private fun rememberSiteIconsChoice(): (Boolean) -> Unit {
    val preferences = koinInject<PreferenceStore>()
    val scope = rememberCoroutineScope()
    return remember(preferences, scope) {
        { show -> scope.launch { preferences.set(AppPreferences.SiteIcons, show) } }
    }
}
