package com.garfiec.librechat.feature.chat.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.model.media.IconSource
import com.garfiec.librechat.core.model.media.resolveAvatarUrl
import com.garfiec.librechat.feature.chat.components.ComparisonDualPane
import com.garfiec.librechat.feature.chat.components.ComparisonLane
import com.garfiec.librechat.feature.chat.components.ComparisonPane
import com.garfiec.librechat.feature.chat.components.ComparisonPaneEmpty
import com.garfiec.librechat.feature.chat.components.ComparisonPaneModelSelector
import com.garfiec.librechat.feature.chat.components.ComparisonTabBar
import com.garfiec.librechat.feature.chat.components.MESSAGE_LIST_BOTTOM_GAP
import com.garfiec.librechat.feature.chat.components.MessageList
import com.garfiec.librechat.feature.chat.components.SecondaryMessageList
import com.garfiec.librechat.feature.chat.components.comparisonSyncLead
import com.garfiec.librechat.feature.chat.components.rememberComparisonScrollSync
import com.garfiec.librechat.feature.chat.util.buildComparisonDisplayMessages
import com.garfiec.librechat.feature.chat.util.iconSource
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ChatViewModel

/**
 * Shared Compare Models layout for both platforms. Lays out the primary/secondary
 * panes side-by-side on wide chats ([wide]) or as a tab pager on phones. On a wide chat it also
 * hosts Compare setup: the second pane is the picker until the user starts the comparison.
 * Derives each pane's content from the persisted per-agent parts (see
 * [buildComparisonDisplayMessages]). All pieces are `commonMain`; the only
 * platform-variant is [onCopyMessage] (Android writes the system clipboard, iOS
 * keeps its own copy behavior) and the [modifier] each caller supplies for sizing
 * (Android weights it in a Column, iOS fills the Box above the input overlay).
 */
@Composable
internal fun ComparisonPanes(
    uiState: ChatUiState,
    viewModel: ChatViewModel,
    senderName: String,
    fontSizeMultiplier: Float,
    showImageDescriptions: Boolean,
    chatLayoutStyle: String,
    showAvatars: Boolean,
    showBubbles: Boolean,
    useKatex: Boolean,
    bottomContentPadding: Dp,
    /** Where the side-by-side cards end; see ChatScreen. */
    paneBottomInset: Dp,
    onCopyMessage: (String) -> Unit,
    onShowSecondaryModelSheet: () -> Unit,
    onComparisonTabChange: (Int) -> Unit,
    /** Side by side (≥ [ComparisonWideBreakpoint]) rather than tabs; measured once by the screen. */
    wide: Boolean,
    compareSetupPick: CompareSetupPick,
    /** Wide setup's "Browse all models": the full setup sheet over the panes. */
    onBrowseAllModels: () -> Unit,
    compareSetupActions: CompareSetupActions,
    primaryLaneLabel: String,
    /** Pane A's scroll position, shared with the single list it replaces (see ChatContent). */
    primaryListState: LazyListState,
    /** Pane B's scroll position, hoisted beside pane A's (see ChatContent). */
    secondaryListState: LazyListState,
    modifier: Modifier = Modifier,
    /** The pull-up tools sheet's nested-scroll bridge, for the live panes (not setup). */
    listPullUpModifier: Modifier = Modifier,
    /** The pull-up sheet's direct drag, for a live pane with no messages: only its centred column scrolls. */
    emptyPanePullUpModifier: Modifier = Modifier,
) {
    val comparisonState = uiState.comparisonState

    val canBranch = comparisonState.parallelMessageId != null &&
        !comparisonState.primaryIsStreaming &&
        !comparisonState.secondaryIsStreaming

    val primaryDisplayMessages = remember(
        uiState.displayMessages,
        comparisonState.parallelMessageId,
        comparisonState.primaryFinalContent,
        senderName,
    ) {
        buildComparisonDisplayMessages(
            uiState.displayMessages,
            secondary = false,
            comparisonState.parallelMessageId,
            comparisonState.primaryFinalContent,
            senderName,
        )
    }
    // Named like the lane everywhere else (tab, pane header, composer).
    val secondaryModelName = comparisonLaneLabel(
        comparisonState.secondaryEndpoint,
        comparisonState.secondaryModel,
        uiState.agents,
        comparisonState.secondarySpec,
        uiState.modelSpecs,
    )
    val secondaryEndpoint = comparisonState.secondaryEndpoint ?: EndpointConstants.AGENTS
    // Re-point the secondary bubble avatar at its own lane: the persisted parallel message only
    // carries the primary's endpoint/iconURL. A spec lane shows the spec's icon, an agents lane its
    // agent's avatar; otherwise the endpoint's icon.
    val secondaryIcon = remember(
        secondaryEndpoint,
        comparisonState.secondaryModel,
        comparisonState.secondarySpec,
        uiState.modelSpecs,
        uiState.agents,
        uiState.serverUrl,
    ) {
        val model = comparisonState.secondaryModel
        val spec = uiState.modelSpecs?.list?.find { it.name == comparisonState.secondarySpec }
        when {
            spec != null -> spec.iconSource(uiState.agents, uiState.serverUrl)
            secondaryEndpoint == EndpointConstants.AGENTS && model != null -> IconSource(
                secondaryEndpoint,
                resolveAvatarUrl(uiState.agents.find { it.id == model }?.avatarUrl, uiState.serverUrl),
            )
            else -> IconSource(secondaryEndpoint, null)
        }
    }
    val secondaryIconEndpoint = secondaryIcon.endpointName ?: secondaryEndpoint
    val secondaryIconUrl = secondaryIcon.imageUrl
    val secondaryDisplayMessages = remember(
        uiState.displayMessages,
        comparisonState.parallelMessageId,
        comparisonState.secondaryFinalContent,
        secondaryModelName,
        secondaryIconEndpoint,
        secondaryIconUrl,
    ) {
        buildComparisonDisplayMessages(
            uiState.displayMessages,
            secondary = true,
            comparisonState.parallelMessageId,
            comparisonState.secondaryFinalContent,
            secondaryModelName,
            secondaryEndpoint = secondaryIconEndpoint,
            secondaryIconUrl = secondaryIconUrl,
        )
    }

    // Side by side, the cards themselves end above the composer, so a list inside one needs only
    // a little room at its foot; the tab pager's lists run under the composer and need all of it.
    val listBottomPadding = if (wide) PaneListBottomPadding else bottomContentPadding

    // Reading room past the last line, so a reply's end can be scrolled up clear of the composer.
    // The same in both panes, so their ends still meet. Measured from pane A's list; both panes are
    // the same height. In both layouts: on a phone it gives each tab the same comfortable end to
    // read into.
    val settingUp = comparisonState.isSettingUp
    val emptyPaneModifier = if (settingUp) Modifier else emptyPanePullUpModifier
    var paneListHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val extraEndSpace = with(density) { (paneListHeightPx * PaneEndSpaceFraction).toDp() }
        .minus(listBottomPadding + MESSAGE_LIST_BOTTOM_GAP)
        .coerceAtLeast(0.dp)
    val extraEndPx = with(density) { extraEndSpace.toPx() }

    val syncScrollPreferred by viewModel.compareSyncScroll.collectAsState()
    val scrollSync = rememberComparisonScrollSync(
        primary = primaryListState,
        secondary = secondaryListState,
        enabled = wide && !settingUp && syncScrollPreferred &&
            primaryDisplayMessages.isNotEmpty() && secondaryDisplayMessages.isNotEmpty(),
        isStreaming = uiState.isStreaming || comparisonState.primaryIsStreaming || comparisonState.secondaryIsStreaming,
        extraEndPx = extraEndPx,
    )
    val syncHold = { scrollSync.holdingTails }
    val primaryMessageListBody: @Composable () -> Unit = {
        if (primaryDisplayMessages.isEmpty() && !comparisonState.primaryIsStreaming && !uiState.isStreaming) {
            ComparisonPaneEmpty(
                ComparisonLane.A,
                primaryLaneLabel,
                bottomPadding = listBottomPadding,
                modifier = emptyPaneModifier,
            )
        } else {
            MessageList(
                recordsGlassBackdrop = false,
                listState = primaryListState,
                displayMessages = primaryDisplayMessages,
                isStreaming = comparisonState.primaryIsStreaming || uiState.isStreaming,
                justSettledMessageId = uiState.justSettledMessageId,
                streamingContent = if (comparisonState.primaryIsStreaming) {
                    comparisonState.primaryStreamingContent
                } else {
                    uiState.streamingContent
                },
                activeToolCalls = if (comparisonState.primaryIsStreaming) {
                    comparisonState.primaryActiveToolCalls
                } else {
                    uiState.activeToolCalls
                },
                sandboxStartingToolCallIds = uiState.sandboxStartingToolCallIds,
                streamingAttachments = uiState.streamingAttachments,
                onSiblingNavigation = viewModel::switchBranch,
                onEditMessage = viewModel::startEditing,
                onRegenerateMessage = viewModel::regenerateMessage,
                onCopyMessage = onCopyMessage,
                onFeedback = viewModel::submitFeedback,
                feedbackEnabled = uiState.gates.feedbackEnabled,
                onContinue = { viewModel.continueGeneration() },
                canResubmit = uiState.canStartRun,
                onReadAloud = viewModel::readAloud,
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
                selectedEndpoint = uiState.selectedEndpoint,
                streamingSenderName = senderName,
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
                bottomContentPadding = listBottomPadding,
                // The comparison container is already padded clear of the floating bar.
                topContentPadding = 0.dp,
                syncHold = syncHold,
                extraEndSpace = extraEndSpace,
                modifier = Modifier.fillMaxSize().onSizeChanged { paneListHeightPx = it.height },
            )
        }
    }
    // Movable so pane A's list (its scroll position and rendered messages) survives Start, which
    // moves it from the setup panes into the live ones.
    val latestPrimaryMessageList by rememberUpdatedState(primaryMessageListBody)
    val primaryMessageList = remember { movableContentOf { latestPrimaryMessageList() } }

    val secondaryMessageList: @Composable () -> Unit = {
        if (secondaryDisplayMessages.isEmpty() && !comparisonState.secondaryIsStreaming) {
            ComparisonPaneEmpty(
                ComparisonLane.B,
                secondaryModelName,
                bottomPadding = listBottomPadding,
                modifier = emptyPaneModifier,
            )
        } else {
            SecondaryMessageList(
                displayMessages = secondaryDisplayMessages,
                isStreaming = comparisonState.secondaryIsStreaming,
                justSettledMessageId = uiState.justSettledMessageId,
                streamingContent = comparisonState.secondaryStreamingContent,
                activeToolCalls = comparisonState.secondaryActiveToolCalls,
                sandboxStartingToolCallIds = uiState.sandboxStartingToolCallIds,
                streamingAttachments = uiState.streamingAttachments,
                error = null,
                baseUrl = uiState.serverUrl,
                fontSizeMultiplier = fontSizeMultiplier,
                selectedEndpoint = secondaryEndpoint,
                streamingSenderName = secondaryModelName,
                showImageDescriptions = showImageDescriptions,
                chatLayoutStyle = chatLayoutStyle,
                showAvatars = showAvatars,
                showBubbles = showBubbles,
                useKatex = useKatex,
                bottomContentPadding = listBottomPadding,
                userAvatarUrl = uiState.userAvatarUrl,
                userName = uiState.userName,
                listState = secondaryListState,
                syncHold = syncHold,
                extraEndSpace = extraEndSpace,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    val onContinuePrimary = if (canBranch && comparisonState.primaryAgentId != null) {
        { viewModel.branchFromComparison(comparisonState.primaryAgentId) }
    } else {
        null
    }
    val onContinueSecondary = if (canBranch && comparisonState.secondaryAgentId != null) {
        { viewModel.branchFromComparison(comparisonState.secondaryAgentId) }
    } else {
        null
    }

    // Setup only reaches here on a wide chat (a narrow one sets up in a sheet over its own screen).
    Box(modifier = modifier) {
        if (wide && settingUp) {
            CompareSetupPanes(
                uiState = uiState,
                pick = compareSetupPick,
                actions = compareSetupActions,
                primaryLabel = primaryLaneLabel,
                // Staged like the phone sheet's A, so Cancel leaves the chat's own model untouched.
                onChangePrimary = {
                    compareSetupPick.target = ComparisonLane.A
                    onBrowseAllModels()
                },
                onBrowseAll = {
                    compareSetupPick.target = ComparisonLane.B
                    onBrowseAllModels()
                },
                // The composer floats over the panes; the message lists inset for it, so must these.
                bottomContentPadding = paneBottomInset,
                primaryContent = primaryMessageList,
            )
        } else if (wide) {
            ComparisonDualPane(
                primaryModelSelector = {
                    ComparisonPaneModelSelector(
                        lane = ComparisonLane.A,
                        label = primaryLaneLabel,
                        onClick = viewModel::openModelSheet,
                    )
                },
                secondaryModelSelector = {
                    ComparisonPaneModelSelector(
                        lane = ComparisonLane.B,
                        label = secondaryModelName,
                        onClick = onShowSecondaryModelSheet,
                    )
                },
                // The lead goes to whichever pane a finger lands on; outside the movable content so
                // pane A's list itself is untouched.
                primaryContent = {
                    Box(Modifier.fillMaxSize().comparisonSyncLead(scrollSync, ComparisonPane.A)) { primaryMessageList() }
                },
                secondaryContent = {
                    Box(Modifier.fillMaxSize().comparisonSyncLead(scrollSync, ComparisonPane.B)) { secondaryMessageList() }
                },
                bottomInset = paneBottomInset,
                onContinueWithPrimary = onContinuePrimary,
                onContinueWithSecondary = onContinueSecondary,
                syncScroll = syncScrollPreferred.takeIf {
                    primaryDisplayMessages.isNotEmpty() && secondaryDisplayMessages.isNotEmpty()
                },
                onSyncScrollChange = viewModel::setCompareSyncScroll,
                modifier = Modifier.fillMaxSize().then(listPullUpModifier),
            )
        } else {
            ComparisonTabBar(
                primaryModelName = primaryLaneLabel,
                secondaryModelName = secondaryModelName,
                primaryContent = primaryMessageList,
                secondaryContent = secondaryMessageList,
                onContinueWithPrimary = onContinuePrimary,
                onContinueWithSecondary = onContinueSecondary,
                onTabChange = onComparisonTabChange,
                modifier = Modifier.fillMaxSize().then(listPullUpModifier),
            )
        }
    }
}

private val PaneListBottomPadding = 16.dp
private const val PaneEndSpaceFraction = 1f / 3f
