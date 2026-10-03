package com.garfiec.librechat.feature.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.data.datastore.ChatHeaderAlignment
import com.garfiec.librechat.core.data.datastore.ChatHeaderContent
import com.garfiec.librechat.core.ui.components.consumeUnhandledTouches
import com.garfiec.librechat.core.ui.components.menuDragAnchor
import com.garfiec.librechat.core.ui.components.menuDragPressEffect
import com.garfiec.librechat.core.ui.components.pressBounce
import com.garfiec.librechat.core.ui.components.rememberMenuDragSelection
import com.garfiec.librechat.core.ui.components.rememberPressBounce
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBar
import com.garfiec.librechat.core.ui.theme.isLiquidGlass
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.cd_edit_title
import com.garfiec.librechat.feature.chat.resources.cd_more_options
import com.garfiec.librechat.feature.chat.resources.cd_open_drawer
import com.garfiec.librechat.feature.chat.resources.select_model
import com.garfiec.librechat.feature.chat.screen.rememberChatModelLabel
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ChatViewModel
import org.jetbrains.compose.resources.stringResource

/**
 * The chat screen's floating top bar, shared by Android and iOS. Chips (hamburger, the configurable
 * content bubble, optional temp-chat toggle, options) are drawn over a top-down [chatTopBarScrim]
 * so chat content scrolls up *behind* a gently dimmed status-bar region rather than being capped by
 * an opaque app bar — a ChatGPT/Telegram-style header. The in-conversation search bar is pinned
 * directly beneath the chips so the overlay measures as one unit.
 *
 * The bubble is configurable along two mobile-only axes ([ChatUiState.chatHeaderContent] /
 * [ChatUiState.chatHeaderAlignment]): it shows the conversation title (long-press to edit in place),
 * the selected model (tap to open the model selector), or nothing. Showing the model here is an
 * opt-in that partially reverses the default decluttering choice of keeping model/params on the
 * composer "+" menu; the title remains the default.
 *
 * Most actions are wired straight to [viewModel]; only the triggers whose dialog hosting differs by
 * platform (preset load/save, rename) and the navigation callbacks are passed in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatFloatingTopBar(
    uiState: ChatUiState,
    viewModel: ChatViewModel,
    onLoadPreset: () -> Unit,
    onSavePreset: () -> Unit,
    onRename: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenDrawer: (() -> Unit)? = null,
    onShowAllMedia: (() -> Unit)? = null,
    onOpenPromptsLibrary: (() -> Unit)? = null,
) {
    var showContextSheet by remember { mutableStateOf(false) }
    var showTraceViewer by remember { mutableStateOf(false) }
    val conversationId = uiState.conversationId
    val conversationTitle = uiState.conversationTitle
    // Interactive on the new-chat landing; once a temporary chat is active it stays visible (ON) as
    // a persistent indicator. Gated on TEMPORARY_CHAT.USE.
    val showTempChatToggle = (conversationId == null || uiState.isTemporaryChat) &&
        uiState.temporaryChatEnabled

    // Equal gates across streamed tokens, so the menu's item lists are rebuilt only when a gate moves.
    val overflowGates = ChatOverflowGates(
        conversationId = conversationId,
        hasShowAllMedia = onShowAllMedia != null,
        presetsEnabled = uiState.presetsEnabled,
        hasPromptsLibrary = onOpenPromptsLibrary != null,
        promptsEnabled = uiState.promptsEnabled,
        multiConvoEnabled = uiState.multiConvoEnabled,
        traceViewerAvailable = uiState.traceViewerAvailable,
        contextUsage = uiState.contextUsage,
        contextUsageEnabled = uiState.contextUsageEnabled,
        contextBarPlacement = uiState.contextBarPlacement,
        sharedLinksEnabled = uiState.sharedLinksEnabled,
    )
    val overflowSections = remember(overflowGates) { chatOverflowSections(overflowGates) }
    val onOverflowItem: (ChatOverflowItem) -> Unit = { item ->
        when (item) {
            ChatOverflowItem.SEARCH -> viewModel.openSearch()
            ChatOverflowItem.SHOW_ALL_MEDIA -> onShowAllMedia?.invoke()
            ChatOverflowItem.LOAD_PRESET -> onLoadPreset()
            ChatOverflowItem.SAVE_PRESET -> onSavePreset()
            ChatOverflowItem.PROMPTS_LIBRARY -> onOpenPromptsLibrary?.invoke()
            ChatOverflowItem.COMPARE -> viewModel.toggleComparison()
            ChatOverflowItem.TRACE -> showTraceViewer = true
            ChatOverflowItem.CONTEXT_USAGE -> showContextSheet = true
            ChatOverflowItem.SHARE -> viewModel.shareConversation()
            ChatOverflowItem.RENAME -> onRename()
            ChatOverflowItem.DUPLICATE -> viewModel.duplicateConversation()
            ChatOverflowItem.ARCHIVE -> viewModel.archiveConversation()
            ChatOverflowItem.DELETE -> viewModel.showDeleteConfirmation()
        }
    }

    val barActions = remember(viewModel) {
        ChatTopBarActions(
            onRename = viewModel::renameConversation,
            onOpenModelSheet = viewModel::openModelSheet,
            onToggleTemporaryChat = viewModel::toggleTemporaryChat,
            onSearchQueryChange = viewModel::onSearchQueryChanged,
            onPreviousMatch = viewModel::previousSearchMatch,
            onNextMatch = viewModel::nextSearchMatch,
            onCloseSearch = viewModel::closeSearch,
        )
    }

    if (isLiquidGlass) {
        GlassChatTopBar(
            uiState = uiState,
            actions = barActions,
            sections = overflowSections,
            onItem = onOverflowItem,
            onOpenDrawer = onOpenDrawer,
            showTempChatToggle = showTempChatToggle,
            modifier = modifier,
        )
    } else {
        MaterialChatTopBar(
            uiState = uiState,
            actions = barActions,
            sections = overflowSections,
            onItem = onOverflowItem,
            onOpenDrawer = onOpenDrawer,
            showTempChatToggle = showTempChatToggle,
            modifier = modifier,
        )
    }

    // The context-usage gauge's default home is just above the composer (Settings → Chat picks
    // its placement); see CommonChatInputCore. When the user routes it to the overflow menu, the
    // menu item hands the trigger here so the breakdown sheet opens outside the menu popup.
    val sheetContextUsage = uiState.contextUsage
    if (showContextSheet && sheetContextUsage != null) {
        ContextUsageSheet(
            usage = sheetContextUsage,
            tokenUsage = uiState.tokenUsage,
            onDismiss = { showContextSheet = false },
            isCompacting = uiState.isCompacting,
            onCompact = viewModel::compactConversation.takeIf { uiState.canCompactNow },
        )
    }

    // Hosted here rather than from the menu item, for the same reason the context sheet is:
    // a modal surface cannot open from inside the dropdown's popup.
    if (showTraceViewer && conversationId != null) {
        TraceViewerSheet(
            conversationId = conversationId,
            isStreaming = uiState.isStreaming,
            onDismiss = { showTraceViewer = false },
        )
    }
}

/** The Liquid Glass bar, with the in-conversation search field pinned beneath it. */
@Composable
private fun GlassChatTopBar(
    uiState: ChatUiState,
    actions: ChatTopBarActions,
    sections: List<List<ChatOverflowItem>>,
    onItem: (ChatOverflowItem) -> Unit,
    onOpenDrawer: (() -> Unit)?,
    showTempChatToggle: Boolean,
    modifier: Modifier = Modifier,
) {
    val spec = rememberChatTopBarSpec(
        uiState = uiState,
        actions = actions,
        sections = sections,
        onItem = onItem,
        onOpenDrawer = onOpenDrawer,
        showTempChatToggle = showTempChatToggle,
    )
    AdaptiveTopBar(
        spec = spec,
        modifier = modifier,
        belowBar = {
            AnimatedVisibility(
                visible = uiState.isSearchOpen,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                InConvoSearchBar(
                    query = uiState.searchQuery,
                    onQueryChange = actions.onSearchQueryChange,
                    currentMatchIndex = uiState.currentSearchMatchIndex,
                    totalMatches = uiState.searchMatchIndices.size,
                    onPreviousMatch = actions.onPreviousMatch,
                    onNextMatch = actions.onNextMatch,
                    onClose = actions.onCloseSearch,
                )
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaterialChatTopBar(
    uiState: ChatUiState,
    actions: ChatTopBarActions,
    sections: List<List<ChatOverflowItem>>,
    onItem: (ChatOverflowItem) -> Unit,
    onOpenDrawer: (() -> Unit)?,
    showTempChatToggle: Boolean,
    modifier: Modifier = Modifier,
) {
    var showOverflowMenu by remember { mutableStateOf(false) }
    val conversationId = uiState.conversationId
    val conversationTitle = uiState.conversationTitle
    val fillWidth = uiState.chatHeaderAlignment == ChatHeaderAlignment.FILL
    val contentAlignment = when (uiState.chatHeaderAlignment) {
        ChatHeaderAlignment.LEFT, ChatHeaderAlignment.FILL -> Alignment.CenterStart
        ChatHeaderAlignment.CENTER -> Alignment.Center
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(brush = chatTopBarScrim())
                .consumeUnhandledTouches()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onOpenDrawer != null) {
                val drawerBounce = rememberPressBounce()
                FloatingBarIconButton(
                    icon = Icons.Default.Menu,
                    contentDescription = stringResource(Res.string.cd_open_drawer),
                    onClick = onOpenDrawer,
                    modifier = Modifier.pressBounce(drawerBounce, enabled = !isLiquidGlass),
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            // The configurable content bubble. The flexible region always reserves the space between
            // the hamburger and the right-pinned controls, so the bubble can hug its content
            // (left/center) or fill the region, and `NONE` simply leaves the region empty.
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = contentAlignment,
            ) {
                when (uiState.chatHeaderContent) {
                    ChatHeaderContent.TITLE ->
                        if (conversationId != null && !conversationTitle.isNullOrBlank()) {
                            HeaderTitleChip(
                                title = conversationTitle,
                                conversationKey = conversationId,
                                fillWidth = fillWidth,
                                onCommit = actions.onRename,
                            )
                        }

                    ChatHeaderContent.MODEL -> {
                        val label = rememberChatModelLabel(
                            selectedEndpoint = uiState.selectedEndpoint,
                            selectedModel = uiState.selectedModel,
                            agents = uiState.agents,
                        )
                        FloatingBarLabelChip(
                            text = label.displayModel ?: stringResource(Res.string.select_model),
                            fillWidth = fillWidth,
                            onClick = actions.onOpenModelSheet,
                        )
                    }

                    ChatHeaderContent.NONE -> Unit
                }
            }
            Spacer(modifier = Modifier.width(8.dp))

            // Temporary chats are never persisted, so they have no server-side task list.
            if (!uiState.isTemporaryChat) {
                BackgroundTasksChip(conversationId = conversationId, isStreaming = uiState.isStreaming)
            }

            if (showTempChatToggle) {
                val tempChatBounce = rememberPressBounce()
                FloatingBarChip(
                    modifier = Modifier
                        .size(FloatingBarChipSize)
                        .pressBounce(tempChatBounce, enabled = !isLiquidGlass),
                ) {
                    TempChatToggle(
                        isTemporary = uiState.isTemporaryChat,
                        onToggle = actions.onToggleTemporaryChat,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            val overflowDrag = rememberMenuDragSelection()
            Box(
                modifier = Modifier.menuDragAnchor(
                    overflowDrag,
                    enabled = !isLiquidGlass,
                    onOpen = { showOverflowMenu = true },
                    onCancel = { showOverflowMenu = false },
                ),
            ) {
                // Material only: ⋮ turns into ⋯ while its menu is open.
                val moreRotation by animateFloatAsState(
                    targetValue = if (showOverflowMenu && !isLiquidGlass) 90f else 0f,
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 200f),
                    label = "moreRotation",
                )
                FloatingBarIconButton(
                    icon = Icons.Default.MoreVert,
                    contentDescription = stringResource(Res.string.cd_more_options),
                    onClick = { showOverflowMenu = true },
                    modifier = Modifier.menuDragPressEffect(overflowDrag),
                    iconRotation = { moreRotation },
                )
                ChatOverflowMenu(
                    expanded = showOverflowMenu,
                    onDismiss = { showOverflowMenu = false },
                    sections = sections,
                    isComparisonEnabled = uiState.comparisonState.isEnabled,
                    contextUsage = uiState.contextUsage,
                    onItem = onItem,
                    dragSelection = overflowDrag.takeIf { !isLiquidGlass },
                )
            }
        }

        // In-conversation search bar, pinned directly under the floating bar.
        AnimatedVisibility(
            visible = uiState.isSearchOpen,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            InConvoSearchBar(
                query = uiState.searchQuery,
                onQueryChange = actions.onSearchQueryChange,
                currentMatchIndex = uiState.currentSearchMatchIndex,
                totalMatches = uiState.searchMatchIndices.size,
                onPreviousMatch = actions.onPreviousMatch,
                onNextMatch = actions.onNextMatch,
                onClose = actions.onCloseSearch,
            )
        }
    }
}

/**
 * The conversation-title bubble. Renders the title (ellipsized) and, on long-press, swaps in an
 * inline editor that commits via [onCommit] (the same [ChatViewModel.renameConversation] path as the
 * overflow Rename dialog). Edit state resets when [conversationKey] changes so switching chats never
 * leaves a stale editor open.
 */
@Composable
private fun HeaderTitleChip(
    title: String,
    conversationKey: String,
    fillWidth: Boolean,
    onCommit: (String) -> Unit,
) {
    var isEditing by remember(conversationKey) { mutableStateOf(false) }
    // On both chips, so the pop that opens the editor plays out on the editor.
    val holdCharge = rememberHoldCharge()

    if (isEditing) {
        FloatingBarContentChip(fillWidth = fillWidth, modifier = Modifier.holdChargeScale(holdCharge)) {
            HeaderTitleEditor(
                initial = title,
                fillWidth = fillWidth,
                onCommit = {
                    isEditing = false
                    onCommit(it)
                },
                onCancel = { isEditing = false },
            )
        }
    } else {
        FloatingBarLabelChip(
            text = title,
            fillWidth = fillWidth,
            modifier = Modifier.holdChargeScale(holdCharge),
            onLongClick = { isEditing = true },
            onLongClickLabel = stringResource(Res.string.cd_edit_title),
            holdCharge = holdCharge,
        )
    }
}

/**
 * Inline single-line title editor. Commit happens ONLY on the explicit IME Done action; any focus
 * loss (tapping the composer, opening the overflow menu, switching conversations, config change) or
 * the Escape key DISCARDS the edit. This makes a rename an explicit, confirmed action — the bar's
 * scrim ([consumeUnhandledTouches]) swallows background taps, so a commit-on-blur would otherwise
 * persist abandoned, half-typed titles. Done with an unchanged or blank value also discards, which
 * covers the case where the title updated underneath an untouched editor (e.g. async gen_title).
 */
@Composable
private fun HeaderTitleEditor(
    initial: String,
    fillWidth: Boolean,
    onCommit: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    var settled by remember { mutableStateOf(false) }
    var everFocused by remember { mutableStateOf(false) }

    fun commit() {
        if (settled) return
        settled = true
        val trimmed = value.text.trim()
        if (trimmed.isNotEmpty() && trimmed != initial) onCommit(trimmed) else onCancel()
    }
    fun cancel() {
        if (settled) return
        settled = true
        onCancel()
    }

    BasicTextField(
        value = value,
        onValueChange = { value = it },
        singleLine = true,
        textStyle = MaterialTheme.typography.titleSmall.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { commit() }),
        modifier = Modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier.widthIn(min = 120.dp, max = 240.dp))
            .padding(horizontal = 16.dp)
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                if (event.key == Key.Escape && event.type == KeyEventType.KeyUp) {
                    cancel()
                    true
                } else {
                    false
                }
            }
            .onFocusChanged { state ->
                if (state.isFocused) {
                    everFocused = true
                } else if (everFocused) {
                    // Focus left (composer, overflow, chat switch) — discard the unconfirmed edit.
                    cancel()
                }
            },
    )

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

/** The ViewModel calls the bar renderers make, so the ViewModel itself stays in [ChatFloatingTopBar]. */
@Immutable
internal class ChatTopBarActions(
    val onRename: (String) -> Unit,
    val onOpenModelSheet: () -> Unit,
    val onToggleTemporaryChat: () -> Unit,
    val onSearchQueryChange: (String) -> Unit,
    val onPreviousMatch: () -> Unit,
    val onNextMatch: () -> Unit,
    val onCloseSearch: () -> Unit,
)
