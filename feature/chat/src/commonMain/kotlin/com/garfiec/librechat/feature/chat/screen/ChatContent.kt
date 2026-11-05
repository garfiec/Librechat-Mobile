package com.garfiec.librechat.feature.chat.screen

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.model.config.displayLabel
import com.garfiec.librechat.core.model.config.labelFor
import com.garfiec.librechat.core.model.media.resolveAvatarUrl
import com.garfiec.librechat.core.model.media.resolveIconSource
import com.garfiec.librechat.core.ui.components.LoadingIndicator
import com.garfiec.librechat.feature.chat.components.BindThreadScroll
import com.garfiec.librechat.feature.chat.components.ConversationSeenEffect
import com.garfiec.librechat.feature.chat.components.LandingContent
import com.garfiec.librechat.feature.chat.components.LandingHeroIcon
import com.garfiec.librechat.feature.chat.components.MessageList
import com.garfiec.librechat.feature.chat.components.MessagesUnavailable
import com.garfiec.librechat.feature.chat.components.StoppedReplyAction
import com.garfiec.librechat.feature.chat.components.ThreadScrollTarget
import com.garfiec.librechat.feature.chat.components.threadScrollTarget
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.action_continue_reply
import com.garfiec.librechat.feature.chat.resources.chat_agent_fallback_label
import com.garfiec.librechat.feature.chat.resources.sender_assistant
import com.garfiec.librechat.feature.chat.util.ClipboardWriter
import com.garfiec.librechat.feature.chat.util.MessageNode
import com.garfiec.librechat.feature.chat.util.collapseParallelToPrimary
import com.garfiec.librechat.feature.chat.util.iconSource
import com.garfiec.librechat.feature.chat.util.rememberClipboardWriter
import com.garfiec.librechat.feature.chat.viewmodel.ActiveToolCall
import com.garfiec.librechat.feature.chat.viewmodel.ChatScreenState
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ChatViewModel
import org.jetbrains.compose.resources.stringResource

/**
 * Renders the chat screen's main content area for each [ChatScreenState]: the
 * landing greeting, the loading spinner, or the active message list. In active
 * comparison mode it delegates to the shared [ComparisonPanes] (side-by-side on
 * wide screens, a tab pager on phones); otherwise it shows the single message
 * list. Lives in a [ColumnScope] so the panes can claim the remaining height via
 * `weight`, leaving the bottom of the box for the composer overlay.
 *
 * Collects `viewModel.uiState` at full rate — the one subtree that re-renders per streaming flush.
 */
@Suppress("ViewModelForwarding") // debt: screen split across files; state not hoisted yet
@Composable
internal fun ColumnScope.ChatContent(
    viewModel: ChatViewModel,
    agentName: String?,
    fontSizeMultiplier: Float,
    showImageDescriptions: Boolean,
    chatLayoutStyle: String,
    showAvatars: Boolean,
    showBubbles: Boolean,
    useKatex: Boolean,
    bottomContentPadding: Dp,
    /** Where side-by-side comparison cards end: just above the measured composer. */
    paneBottomInset: Dp,
    topContentPadding: Dp,
    onShowSecondaryModelSheet: () -> Unit,
    onComparisonTabChange: (Int) -> Unit,
    /** The phone pager's tab on screen while comparing (0 = A): the list the composer's drags scroll. */
    activeComparisonTab: Int,
    comparisonWide: Boolean,
    compareSetupPick: CompareSetupPick,
    onBrowseAllModels: () -> Unit,
    compareSetupActions: CompareSetupActions,
    /** Lane A's label in the panes: the chat's model, or "Select model". */
    primaryLaneLabel: String,
    // Bridges the active message list's over-scroll-past-bottom into the pull-up tools sheet
    // (nested-scroll) and re-arms the reveal on each pointer-down.
    listPullUpModifier: Modifier = Modifier,
    // Drives the pull-up sheet directly from the (non-scrollable) landing surface.
    pullUpModifier: Modifier = Modifier,
    /** The project a new chat from this landing is filed into, while it still is. */
    projectName: String? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboard = rememberClipboardWriter()
    val fallbackSenderName = stringResource(Res.string.sender_assistant)
    // Non-scrolling bodies (landing, loading, comparison panes) clear the floating bar with a
    // plain top padding; only the single active MessageList takes the inset as scrollable
    // contentPadding so its content scrolls up behind the bar's scrim.
    val topInsetModifier = Modifier
        .weight(1f)
        .padding(top = topContentPadding)
    val senderName = uiState.replySenderName(fallbackSenderName)
    // The thread's scroll position, shared by the single list and Compare's pane A: opening or
    // cancelling Compare swaps one for the other, and the reader keeps their place. A fresh one per
    // ACTIVE stretch: the landing entry outlives its chats (a handed-off new chat resets it to the
    // landing), and the next chat must not open at the last one's position. Keyed on the screen state
    // rather than the conversation, whose id arrives mid-reply on a new chat while the list is
    // composed: MessageList's scroll machinery holds the instance it first saw, so swapping it there
    // would leave the reply unfollowed. LANDING and LOADING show no list, so nothing holds the old one.
    val primaryListState = rememberSaveable(uiState.screenState, saver = LazyListState.Saver) { LazyListState() }
    // Pane B's, hoisted for the same reasons: the narrow pager disposes the off-screen pane and the
    // wide/narrow switch rebuilds both, so a pane-local state lost the reader's place on every one.
    val secondaryListState = rememberSaveable(uiState.screenState, saver = LazyListState.Saver) { LazyListState() }
    // Unread replies (v0.8.9-rc1): the open chat's reply is acknowledged once it is on screen. The
    // single list reports where it rests; Compare's panes don't, so a comparison acknowledges nothing.
    var primaryNearBottom by remember(uiState.conversationId) { mutableStateOf(false) }
    ConversationSeenEffect(
        conversationId = uiState.conversationId.takeIf { uiState.screenState == ChatScreenState.ACTIVE },
        displayMessages = uiState.displayMessages,
        isStreaming = uiState.isStreaming,
        nearBottom = primaryNearBottom && !uiState.showsComparisonPanes(comparisonWide),
    )
    val comparisonPanes: @Composable () -> Unit = {
        ComparisonPanes(
            uiState = uiState,
            viewModel = viewModel,
            senderName = senderName,
            fontSizeMultiplier = fontSizeMultiplier,
            showImageDescriptions = showImageDescriptions,
            chatLayoutStyle = chatLayoutStyle,
            showAvatars = showAvatars,
            showBubbles = showBubbles,
            useKatex = useKatex,
            bottomContentPadding = bottomContentPadding,
            paneBottomInset = paneBottomInset,
            onCopyMessage = { messageId -> copyMessageToClipboard(viewModel, clipboard, messageId) },
            onShowSecondaryModelSheet = onShowSecondaryModelSheet,
            onComparisonTabChange = onComparisonTabChange,
            wide = comparisonWide,
            compareSetupPick = compareSetupPick,
            onBrowseAllModels = onBrowseAllModels,
            compareSetupActions = compareSetupActions,
            primaryLaneLabel = primaryLaneLabel,
            primaryListState = primaryListState,
            secondaryListState = secondaryListState,
            listPullUpModifier = listPullUpModifier,
            emptyPanePullUpModifier = pullUpModifier,
            modifier = topInsetModifier,
        )
    }
    val messagesUnavailable = uiState.messagesUnavailable
    // The composer's chrome scrolls the list it covers (see ThreadScrollHandle).
    val comparing = uiState.showsComparisonPanes(comparisonWide)
    if (comparing || (uiState.screenState == ChatScreenState.ACTIVE && !messagesUnavailable)) {
        val target = threadScrollTarget(comparing = comparing, wide = comparisonWide, activeTab = activeComparisonTab)
        BindThreadScroll(if (target == ThreadScrollTarget.SECONDARY) secondaryListState else primaryListState)
    }
    // One call site for the panes in both LANDING and ACTIVE: Start on a wide landing turns one into
    // the other, and a second call site would rebuild the panes (pane A's list included) right then.
    if (comparing) {
        comparisonPanes()
    } else {
        when (uiState.screenState) {
            ChatScreenState.LANDING -> {
                val text = uiState.landingText(agentName, stringResource(Res.string.chat_agent_fallback_label))
                LandingContent(
                    selectedModel = text.caption,
                    selectedAgentName = text.agentName,
                    title = text.title,
                    description = text.description,
                    temporaryAgentName = text.temporaryAgentName,
                    temporaryModel = text.temporaryCaption,
                    onSelectModel = viewModel::openModelSheet.takeIf { uiState.modelPickerShown },
                    projectName = projectName,
                    heroIcon = uiState.landingHeroIcon(),
                    showSubtitle = !uiState.hasLandingStarters,
                    isTemporaryChat = uiState.isTemporaryChat,
                    bottomPadding = maxOf(120.dp, paneBottomInset),
                    modifier = topInsetModifier.then(pullUpModifier),
                )
            }
            ChatScreenState.LOADING -> {
                LoadingIndicator(
                    modifier = topInsetModifier,
                )
            }
            ChatScreenState.ACTIVE -> {
                if (messagesUnavailable) {
                    MessagesUnavailable(
                        onRetry = viewModel::refreshMessages,
                        bottomContentPadding = bottomContentPadding,
                        modifier = topInsetModifier,
                    )
                } else {
                    val singleDisplayMessages = remember(uiState.displayMessages) {
                        collapseParallelToPrimary(uiState.displayMessages)
                    }
                    ChatMessageListPane(
                        listState = primaryListState,
                        onNearBottomChange = { primaryNearBottom = it },
                        uiState = uiState,
                        viewModel = viewModel,
                        clipboard = clipboard,
                        fontSizeMultiplier = fontSizeMultiplier,
                        showImageDescriptions = showImageDescriptions,
                        chatLayoutStyle = chatLayoutStyle,
                        showAvatars = showAvatars,
                        showBubbles = showBubbles,
                        useKatex = useKatex,
                        topContentPadding = topContentPadding,
                        displayMessages = singleDisplayMessages,
                        isStreaming = uiState.isStreaming,
                        streamingContent = uiState.streamingContent,
                        activeToolCalls = uiState.activeToolCalls,
                        streamingSenderName = senderName,
                        bottomContentPadding = bottomContentPadding,
                        modifier = Modifier
                            .weight(1f)
                            .then(listPullUpModifier),
                    )
                }
            }
        }
    }
}

/**
 * Ahead of the comparison panes: with nothing to show, empty panes are less use than the failure
 * state. !isStreaming is load-bearing — a handed-off new chat is legitimately empty until the first
 * message lands.
 */
private val ChatUiState.messagesUnavailable: Boolean
    get() = screenState == ChatScreenState.ACTIVE && messagesLoadFailed && displayMessages.isEmpty() && !isStreaming

/**
 * Whether the chat area shows the comparison panes. A wide chat sets up Compare in the empty second
 * pane, so the panes show from the moment setup starts, landing included; a narrow one keeps its
 * screen under the setup sheet. Never over the loading or failure state, which is why a wide setup
 * falls back to the sheet there (ChatScreen).
 */
internal fun ChatUiState.showsComparisonPanes(wide: Boolean): Boolean {
    val comparing = comparisonState.isEnabled || (comparisonState.isSettingUp && wide)
    return comparing && screenState != ChatScreenState.LOADING && !messagesUnavailable
}

private fun copyMessageToClipboard(
    viewModel: ChatViewModel,
    clipboard: ClipboardWriter,
    messageId: String,
) {
    // Asynchronous: a message with tool-call previews fetches their full parts before anything is copied.
    viewModel.copyMessage(messageId) { text -> clipboard.copy(text, "Message") }
}

/**
 * The single (non-comparison) and comparison-primary message lists differ only in
 * their streaming source, sender label, and modifier; everything else — the action
 * callbacks, editing state, search wiring, and display prefs — is identical. This
 * wrapper holds that shared configuration so both call sites stay in sync.
 */
@Composable
private fun ChatMessageListPane(
    onNearBottomChange: (Boolean) -> Unit,
    uiState: ChatUiState,
    viewModel: ChatViewModel,
    clipboard: ClipboardWriter,
    fontSizeMultiplier: Float,
    showImageDescriptions: Boolean,
    chatLayoutStyle: String,
    showAvatars: Boolean,
    showBubbles: Boolean,
    useKatex: Boolean,
    topContentPadding: Dp,
    displayMessages: List<MessageNode>,
    isStreaming: Boolean,
    streamingContent: String,
    activeToolCalls: List<ActiveToolCall>,
    streamingSenderName: String,
    bottomContentPadding: Dp,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val continueLabel = stringResource(Res.string.action_continue_reply)
    MessageList(
        listState = listState,
        canResubmit = uiState.canStartRun,
        stoppedReply = uiState.continuableReplyId?.let { id ->
            StoppedReplyAction(id, continueLabel, viewModel::continueGeneration)
        },
        displayMessages = displayMessages,
        isStreaming = isStreaming,
        justSettledMessageId = uiState.justSettledMessageId,
        streamingContent = streamingContent,
        // Single-stream only: comparison panes fold reasoning into their own buffers.
        streamingThinking = uiState.streamingThinking,
        activeToolCalls = activeToolCalls,
        sandboxStartingToolCallIds = uiState.sandboxStartingToolCallIds,
        streamingAttachments = uiState.streamingAttachments,
        onSiblingNavigation = viewModel::switchBranch,
        onEditMessage = viewModel::startEditing,
        onRegenerateMessage = viewModel::regenerateMessage,
        onCopyMessage = { messageId -> copyMessageToClipboard(viewModel, clipboard, messageId) },
        onFeedback = viewModel::submitFeedback,
        feedbackEnabled = uiState.gates.feedbackEnabled,
        onReadAloud = viewModel::readAloud,
        // Web's `quotesSupported` rule: everything but the Assistants endpoints, which keep every
        // file upstream, so neither a retry nor a local copy leaves them behind.
        recoveryAllowed = uiState.quotesSupportedOnEndpoint,
        recoveryBranchSupported = uiState.forkExcludeFilesSupported,
        recoveryBusy = uiState.isForkInProgress,
        onRetryWithoutFiles = viewModel::retryWithoutFiles,
        onBranchWithoutFiles = viewModel::branchWithoutFiles,
        ownerTexts = uiState.ownerTexts,
        onRetryOwnerTexts = viewModel::retryOwnerTexts,
        onFork = viewModel::showForkOptions,
        currentlyReadingMessageId = uiState.currentlyReadingMessageId,
        editing = uiState.editing,
        onEditTextChange = viewModel::onEditTextChanged,
        onEditPartTextChange = viewModel::onEditPartTextChanged,
        onEditSave = viewModel::saveEdit,
        onEditRerun = viewModel::rerunEdit,
        onEditCancel = viewModel::cancelEditing,
        baseUrl = uiState.serverUrl,
        fontSizeMultiplier = fontSizeMultiplier,
        isRefreshing = uiState.isRefreshingMessages,
        onRefresh = viewModel::refreshMessages,
        userAvatarUrl = uiState.userAvatarUrl,
        userName = uiState.userName,
        streamingAvatar = uiState.avatars.streaming,
        streamingSenderName = streamingSenderName,
        showImageDescriptions = showImageDescriptions,
        chatLayoutStyle = chatLayoutStyle,
        showAvatars = showAvatars,
        showBubbles = showBubbles,
        useKatex = useKatex,
        searchQuery = if (uiState.isSearchOpen) uiState.searchQuery else null,
        searchMatchIndices = uiState.searchMatchIndices,
        currentSearchMatchIndex = uiState.currentSearchMatchIndex,
        searchFocusRequest = uiState.searchFocusRequest,
        jumpFlash = uiState.jumpFlash,
        jumpPending = uiState.jumpPending,
        onSearchScrollHandle = viewModel::onSearchScrollHandled,
        bottomContentPadding = bottomContentPadding,
        topContentPadding = topContentPadding,
        pendingAction = uiState.renderablePendingAction,
        onNearBottomChange = onNearBottomChange,
        modifier = modifier,
    )
}

/**
 * The new chat's icon, as web's landing draws it (`ConvoIcon`): the selected spec's ([iconSource]: its
 * `iconURL`, its preset's, its agent's avatar, else its endpoint's brand), the selected agent's avatar
 * (the agents glyph without one), else the selected endpoint's brand. Null, before anything is
 * selected, keeps the app tile.
 */
internal fun ChatUiState.landingHeroIcon(): LandingHeroIcon? {
    val spec = modelSpecs?.list?.find { it.name == selectedSpec }
    val icon = when {
        spec != null -> spec.iconSource(agents, serverUrl, endpointConfigs)
        selectedEndpoint.isBlank() -> return null
        // No agent yet is the selection before seeding, not a choice to draw.
        selectedEndpoint == EndpointConstants.AGENTS ->
            return if (selectedModel == null) {
                null
            } else {
                LandingHeroIcon(
                    endpointName = EndpointConstants.AGENTS,
                    iconUrl = selectedAgent()?.avatarUrl?.let { resolveAvatarUrl(it, serverUrl) },
                )
            }
        else -> resolveIconSource(null, selectedEndpoint, serverUrl, endpointConfigs)
    }
    return LandingHeroIcon(endpointName = icon.endpointName, iconUrl = icon.imageUrl, provider = icon.provider)
}

/**
 * The name a reply in progress goes by until the server's saved one replaces it: the agent's name
 * under the agents endpoint, else the selected spec's label (the names the server saves those
 * replies under), else the model, where the server's own name (`getResponseSender`) isn't ported.
 */
internal fun ChatUiState.replySenderName(fallback: String): String {
    val model = selectedModel
    if (selectedEndpoint == EndpointConstants.AGENTS && model != null) {
        return agents.find { it.id == model }?.name ?: model
    }
    return modelSpecs?.labelFor(selectedSpec) ?: model ?: fallback
}

private fun ChatUiState.selectedAgent() =
    agents.takeIf { selectedEndpoint == EndpointConstants.AGENTS }?.find { it.id == selectedModel }

/** What the new-chat screen says. Null fields keep [LandingContent]'s defaults. */
internal data class LandingText(
    /** Replaces the time-of-day greeting. */
    val title: String? = null,
    /** Replaces "How can I help". */
    val description: String? = null,
    /** The prominent agent line under the greeting; only a temporary chat, whose title is fixed, uses it. */
    val agentName: String? = null,
    /** The small model label that opens the picker. */
    val caption: String? = null,
    /** [agentName] once temporary chat's fixed title has replaced [title]. */
    val temporaryAgentName: String? = null,
    /** [caption] once temporary chat's fixed title has replaced [title]: never folded into it. */
    val temporaryCaption: String? = null,
)

/**
 * The landing's words, as web's `Landing.tsx`: the selected agent's name, else the spec's label when
 * it has `showOnLanding`, is the title; the description is the agent's, then that spec's, then the
 * spec preset's `greeting` (which applies without `showOnLanding`, as a new chat carries the preset's
 * greeting). A temporary chat keeps its own title and subtitle: both sets are returned whatever the
 * flag, because the landing switches each line as temporary chat's band reaches it. [agentName] is the selected agent's,
 * resolved by the caller; for an agent spec it wins over the spec's label, as on web. An agent not
 * (yet) in the list is captioned [agentFallbackLabel], so the landing still opens the picker.
 * Under `interface.landingHeading: "greeting"` the greeting stays the title whatever is selected.
 */
internal fun ChatUiState.landingText(agentName: String?, agentFallbackLabel: String? = null): LandingText {
    val spec = modelSpecs?.list?.find { it.name == selectedSpec }
    val specLabel = modelSpecs?.labelFor(selectedSpec)
    // An agent's raw "model" is its id, which says nothing.
    val modelLabel = specLabel ?: when {
        selectedEndpoint != EndpointConstants.AGENTS -> selectedModel
        agentName == null && selectedModel != null -> agentFallbackLabel
        else -> null
    }
    val branded = spec?.takeIf { it.showOnLanding == true }
    val description = listOf(selectedAgent()?.description, branded?.description, spec?.preset?.greeting)
        .firstOrNull { !it.isNullOrBlank() }
    // `interface.landingHeading: "greeting"`: the greeting stays the title, as web's empty `name`.
    // Web's header selector still names the agent; mobile has no header selector, so the caption
    // under the greeting names it instead.
    if (gates.landingGreetingHeading) {
        return LandingText(
            description = description,
            caption = agentName ?: modelLabel,
            temporaryAgentName = agentName,
            temporaryCaption = modelLabel,
        )
    }
    val title = agentName ?: branded?.displayLabel
    return LandingText(
        title = title,
        description = description,
        caption = modelLabel.takeUnless { it == title },
        temporaryAgentName = agentName,
        temporaryCaption = modelLabel,
    )
}
