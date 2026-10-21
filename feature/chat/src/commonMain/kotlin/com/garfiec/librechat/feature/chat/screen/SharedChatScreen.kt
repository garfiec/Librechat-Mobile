package com.garfiec.librechat.feature.chat.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.common.ChatLayoutConstants
import com.garfiec.librechat.core.common.datetime.listLabel
import com.garfiec.librechat.core.common.datetime.parseIsoInstantOrNull
import com.garfiec.librechat.core.data.datastore.AppPreferences
import com.garfiec.librechat.core.data.datastore.ChatFontSize
import com.garfiec.librechat.core.data.datastore.InlineArtifactPrefs
import com.garfiec.librechat.core.data.datastore.LatexRenderer
import com.garfiec.librechat.core.data.datastore.PreferenceStore
import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.FILE_SOURCE_TEXT
import com.garfiec.librechat.core.model.FileReference
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveExtendedFloatingActionButton
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedButton
import com.garfiec.librechat.core.ui.components.AdaptiveScaffold
import com.garfiec.librechat.core.ui.components.AdaptiveSnackbarHost
import com.garfiec.librechat.core.ui.components.EmptyState
import com.garfiec.librechat.core.ui.components.LoadingIndicator
import com.garfiec.librechat.core.ui.components.share.LocalExternalUriHandler
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBar
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBarSpec
import com.garfiec.librechat.core.ui.components.topbar.BarAction
import com.garfiec.librechat.core.ui.components.topbar.BarIcons
import com.garfiec.librechat.core.ui.components.topbar.BarMenuItem
import com.garfiec.librechat.core.ui.components.topbar.BarMenuSection
import com.garfiec.librechat.core.ui.components.topbar.BarNavigation
import com.garfiec.librechat.core.ui.components.topbar.BarTitle
import com.garfiec.librechat.core.ui.datetime.LocalDateTimeFormat
import com.garfiec.librechat.core.ui.datetime.LocalRelativeTimeReference
import com.garfiec.librechat.feature.chat.components.ChatRoot
import com.garfiec.librechat.feature.chat.components.LocalOpenFileActions
import com.garfiec.librechat.feature.chat.components.LocalOpenMessageFile
import com.garfiec.librechat.feature.chat.components.LocalOpenPdf
import com.garfiec.librechat.feature.chat.components.MessageList
import com.garfiec.librechat.feature.chat.components.ParsedMarkdownCache
import com.garfiec.librechat.feature.chat.components.SharedTextFileSheet
import com.garfiec.librechat.feature.chat.components.artifact.MermaidRenderCache
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.cd_back
import com.garfiec.librechat.feature.chat.resources.cd_more_options
import com.garfiec.librechat.feature.chat.resources.retry
import com.garfiec.librechat.feature.chat.resources.shared_chat_blocked
import com.garfiec.librechat.feature.chat.resources.shared_chat_continue
import com.garfiec.librechat.feature.chat.resources.shared_chat_continue_blocked
import com.garfiec.librechat.feature.chat.resources.shared_chat_continue_blocked_reason
import com.garfiec.librechat.feature.chat.resources.shared_chat_continue_failed
import com.garfiec.librechat.feature.chat.resources.shared_chat_continue_rate_limited
import com.garfiec.librechat.feature.chat.resources.shared_chat_continue_unsupported
import com.garfiec.librechat.feature.chat.resources.shared_chat_langfuse
import com.garfiec.librechat.feature.chat.resources.shared_chat_link_updated
import com.garfiec.librechat.feature.chat.resources.shared_chat_load_failed
import com.garfiec.librechat.feature.chat.resources.shared_chat_no_access
import com.garfiec.librechat.feature.chat.resources.shared_chat_no_access_description
import com.garfiec.librechat.feature.chat.resources.shared_chat_not_found
import com.garfiec.librechat.feature.chat.resources.shared_chat_not_found_description
import com.garfiec.librechat.feature.chat.resources.shared_chat_open_in_browser
import com.garfiec.librechat.feature.chat.resources.shared_chat_rate_limited
import com.garfiec.librechat.feature.chat.resources.shared_chat_rate_limited_description
import com.garfiec.librechat.feature.chat.resources.shared_chat_refresh_failed
import com.garfiec.librechat.feature.chat.resources.shared_chat_subtitle
import com.garfiec.librechat.feature.chat.resources.shared_chat_title_fallback
import com.garfiec.librechat.feature.chat.resources.shared_chat_user
import com.garfiec.librechat.feature.chat.util.rememberClipboardWriter
import com.garfiec.librechat.feature.chat.viewmodel.SharedChatError
import com.garfiec.librechat.feature.chat.viewmodel.SharedChatEvent
import com.garfiec.librechat.feature.chat.viewmodel.SharedChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.SharedChatViewModel
import com.garfiec.librechat.feature.chat.viewmodel.SiteIconPromptSession
import com.garfiec.librechat.feature.chat.viewmodel.SiteIconsState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** The reader's display settings, as the chat screen applies them. */
internal data class SharedChatDisplay(
    val fontSizeMultiplier: Float = 1.0f,
    val chatLayoutStyle: String = ChatLayoutConstants.THREAD,
    val showAvatars: Boolean = true,
    val showBubbles: Boolean = false,
    val showImageDescriptions: Boolean = false,
    val useKatex: Boolean = true,
    val inlineArtifactPrefs: InlineArtifactPrefs = InlineArtifactPrefs(),
    val siteIcons: SiteIconsState = SiteIconsState.OFF,
)

/** What the viewer screen asks of its host. */
internal class SharedChatActions(
    val onBack: () -> Unit,
    val onRetry: () -> Unit,
    val onContinue: () -> Unit,
    val onOpenInBrowser: () -> Unit,
    /** Null hides the item: the server only sends the session link to viewers allowed to see it. */
    val onOpenLangfuse: (() -> Unit)?,
)

/**
 * The in-app viewer for a shared link: the chat as it was shared, read-only, with Continue to copy
 * the branch on screen into the user's own chats. Opened from a `/share/` link tapped in the app,
 * Android share-to, the share sheet's Preview and the Shared chats page.
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
        onSiteIconsChoice = rememberSiteIconsChoice(),
        onSiteIconsPromptDismiss = koinInject<SiteIconPromptSession>()::dismiss,
    ) {
        ProvideSharedFileOpeners(state, viewModel) {
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

/**
 * Routes the viewer's file taps through the share: a `text`-source file opens as text (the server
 * keeps no bytes for one), anything else in the file-actions sheet, whose downloads come through
 * the share's own route ([SharedChatViewModel.downloadAttachment]).
 */
@Composable
private fun ProvideSharedFileOpeners(
    state: SharedChatUiState,
    viewModel: SharedChatViewModel,
    content: @Composable () -> Unit,
) {
    val openFileActions = LocalOpenFileActions.current
    val openPdf = LocalOpenPdf.current
    val textIds = state.textSourceFileIds
    val fileActions: (Attachment) -> Unit = remember(openFileActions, textIds, viewModel) {
        { attachment ->
            val fileId = attachment.fileId
            if (fileId != null && (attachment.source == FILE_SOURCE_TEXT || fileId in textIds)) {
                viewModel.openTextFile(fileId, attachment.filename ?: fileId)
            } else {
                openFileActions(attachment)
            }
        }
    }
    val pdf: (String, String) -> Unit = remember(openPdf, textIds, viewModel) {
        { fileId, filename ->
            if (fileId in textIds) viewModel.openTextFile(fileId, filename) else openPdf(fileId, filename)
        }
    }
    val messageFile: (FileReference) -> Unit = remember(fileActions) {
        { file ->
            fileActions(
                Attachment(
                    fileId = file.fileId,
                    filename = file.filename,
                    filepath = file.filepath,
                    type = file.type,
                    width = file.width,
                    height = file.height,
                    bytes = file.bytes,
                    source = file.source,
                ),
            )
        }
    }
    CompositionLocalProvider(
        LocalOpenFileActions provides fileActions,
        LocalOpenPdf provides pdf,
        LocalOpenMessageFile provides messageFile,
        content = content,
    )
}

/** The viewer's page without its ViewModel: what the screen draws, and what previews render. */
@Composable
internal fun SharedChatScaffold(
    state: SharedChatUiState,
    display: SharedChatDisplay,
    actions: SharedChatActions,
    snackbarHostState: SnackbarHostState,
    onSwitchBranch: (parentKey: String, siblingIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snapshot = state.snapshot
    val title = snapshot?.title?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.shared_chat_title_fallback)
    val format = LocalDateTimeFormat.current
    val reference = LocalRelativeTimeReference.current
    val shared = remember(snapshot?.updatedAt, snapshot?.createdAt, format, reference) {
        (snapshot?.updatedAt ?: snapshot?.createdAt)?.let(::parseIsoInstantOrNull)?.listLabel(format, reference)
    }
    AdaptiveScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            AdaptiveTopBar(
                spec = AdaptiveTopBarSpec(
                    navigation = BarNavigation(BarIcons.Back, stringResource(Res.string.cd_back), actions.onBack),
                    title = BarTitle(title, maxLines = 1),
                    actions = if (snapshot != null) listOf(overflowMenu(actions)) else emptyList(),
                ),
                belowBar = shared?.let { label ->
                    {
                        Text(
                            text = stringResource(Res.string.shared_chat_subtitle, label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                        )
                    }
                },
            )
        },
        snackbarHost = { AdaptiveSnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (snapshot != null && state.canContinue) {
                ContinueButton(busy = state.isContinuing, onClick = actions.onContinue)
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val error = state.error
            when {
                snapshot != null -> MessageList(
                    displayMessages = state.displayMessages,
                    isStreaming = false,
                    streamingContent = "",
                    onSiblingNavigation = onSwitchBranch,
                    onEditMessage = {},
                    onRegenerateMessage = {},
                    onCopyMessage = rememberCopyMessage(state),
                    readOnly = true,
                    baseUrl = state.baseUrl,
                    fontSizeMultiplier = display.fontSizeMultiplier,
                    isRefreshing = state.isRefreshing,
                    onRefresh = actions.onRetry,
                    // Whoever opens a link didn't write its prompts: a neutral label, no avatar.
                    userAvatarUrl = null,
                    userName = stringResource(Res.string.shared_chat_user),
                    selectedEndpoint = null,
                    showImageDescriptions = display.showImageDescriptions,
                    chatLayoutStyle = display.chatLayoutStyle,
                    showAvatars = display.showAvatars,
                    showBubbles = display.showBubbles,
                    useKatex = display.useKatex,
                    bottomContentPadding = FAB_CLEARANCE,
                )
                error != null -> SharedChatErrorState(error, state.blockedMessage, actions.onRetry)
                else -> LoadingIndicator()
            }
        }
    }
}

/** Room under the last message for the extended FAB: its height plus its margins. */
private val FAB_CLEARANCE: Dp = 96.dp

@Composable
private fun overflowMenu(actions: SharedChatActions): BarAction.Menu {
    val openInBrowser = stringResource(Res.string.shared_chat_open_in_browser)
    val langfuse = stringResource(Res.string.shared_chat_langfuse)
    return BarAction.Menu(
        id = "more",
        icon = BarIcons.More,
        label = stringResource(Res.string.cd_more_options),
        sections = listOf(
            BarMenuSection(
                buildList {
                    add(
                        BarMenuItem(
                            id = "browser",
                            label = openInBrowser,
                            icon = BarIcons.OpenInBrowser,
                            onClick = actions.onOpenInBrowser,
                        ),
                    )
                    actions.onOpenLangfuse?.let { open ->
                        add(BarMenuItem(id = "langfuse", label = langfuse, icon = BarIcons.Trace, onClick = open))
                    }
                },
            ),
        ),
    )
}

@Composable
private fun ContinueButton(busy: Boolean, onClick: () -> Unit) {
    AdaptiveExtendedFloatingActionButton(
        text = { Text(stringResource(Res.string.shared_chat_continue)) },
        icon = {
            if (busy) {
                AdaptiveCircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Outlined.AddComment, contentDescription = null)
            }
        },
        // A second tap while the copy is under way does nothing (the ViewModel guards it too).
        onClick = { if (!busy) onClick() },
    )
}

@Composable
private fun SharedChatErrorState(error: SharedChatError, blockedMessage: String?, onRetry: () -> Unit) {
    val (title, description, icon) = when (error) {
        SharedChatError.NOT_FOUND -> Triple(
            stringResource(Res.string.shared_chat_not_found),
            stringResource(Res.string.shared_chat_not_found_description),
            Icons.Outlined.LinkOff,
        )
        SharedChatError.NO_ACCESS -> Triple(
            stringResource(Res.string.shared_chat_no_access),
            stringResource(Res.string.shared_chat_no_access_description),
            Icons.Outlined.Lock,
        )
        SharedChatError.RATE_LIMITED -> Triple(
            stringResource(Res.string.shared_chat_rate_limited),
            stringResource(Res.string.shared_chat_rate_limited_description),
            Icons.Outlined.HourglassEmpty,
        )
        SharedChatError.BLOCKED -> Triple(stringResource(Res.string.shared_chat_blocked), blockedMessage, Icons.Outlined.Block)
        SharedChatError.NETWORK, SharedChatError.GENERIC ->
            Triple(stringResource(Res.string.shared_chat_load_failed), null, Icons.Outlined.CloudOff)
    }
    // A filter's own explanation can run to several sentences: a gutter keeps it off the screen
    // edges, and the page scrolls once it outgrows a short screen, still centred until it does.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        EmptyState(
            title = title,
            description = description,
            icon = icon,
            // Access and the content filter are the server's answer; asking again won't change it.
            action = if (error == SharedChatError.NO_ACCESS || error == SharedChatError.BLOCKED) {
                null
            } else {
                { AdaptiveOutlinedButton(onClick = onRetry) { Text(stringResource(Res.string.retry)) } }
            },
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .heightIn(min = viewportHeight)
                .padding(horizontal = 24.dp, vertical = 16.dp),
        )
    }
}

@Composable
private fun rememberCopyMessage(state: SharedChatUiState): (String) -> Unit {
    val clipboard = rememberClipboardWriter()
    val nodes by rememberUpdatedState(state.displayMessages)
    return remember(clipboard) {
        { messageId ->
            val text = nodes.firstOrNull { it.message.messageId == messageId }?.message?.text.orEmpty()
            if (text.isNotBlank()) clipboard.copy(text, "Message")
        }
    }
}

/** The reader's chat display settings, read as the chat screen reads them. */
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
    val siteIconsChoice by preferences.observe(AppPreferences.SiteIcons)
        .collectAsStateWithLifecycle(preferences.initial(AppPreferences.SiteIcons))
    val promptDismissed by promptSession.dismissed.collectAsStateWithLifecycle()
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
        siteIcons = when (siteIconsChoice) {
            true -> SiteIconsState.ON
            false -> SiteIconsState.OFF
            null -> if (promptDismissed) SiteIconsState.OFF else SiteIconsState.ASK
        },
    )
}

/** The website-icons prompt's answer, stored as the chat screen stores it. */
@Composable
private fun rememberSiteIconsChoice(): (Boolean) -> Unit {
    val preferences = koinInject<PreferenceStore>()
    val scope = rememberCoroutineScope()
    return remember(preferences, scope) {
        { show -> scope.launch { preferences.set(AppPreferences.SiteIcons, show) } }
    }
}
