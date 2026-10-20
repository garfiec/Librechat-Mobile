package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.common.ChatLayoutConstants
import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.feature.chat.resources.*
import com.garfiec.librechat.feature.chat.util.MessageNode
import com.garfiec.librechat.feature.chat.util.withPreviews
import com.garfiec.librechat.feature.chat.viewmodel.ActiveToolCall

/**
 * Simplified message list for the secondary comparison pane.
 * Does not support editing, forking, sibling navigation, or search highlighting.
 * Just displays messages and streaming content for the comparison model.
 */
@Composable
fun SecondaryMessageList(
    displayMessages: List<MessageNode>,
    isStreaming: Boolean,
    streamingContent: String,
    modifier: Modifier = Modifier,
    justSettledMessageId: String? = null,
    activeToolCalls: List<ActiveToolCall> = emptyList(),
    sandboxStartingToolCallIds: Set<String> = emptySet(),
    streamingAttachments: List<Attachment> = emptyList(),
    error: String? = null,
    baseUrl: String = "",
    fontSizeMultiplier: Float = 1.0f,
    selectedEndpoint: String? = null,
    streamingSenderName: String = "Assistant",
    showImageDescriptions: Boolean = true,
    chatLayoutStyle: String = ChatLayoutConstants.THREAD,
    showAvatars: Boolean = true,
    showBubbles: Boolean = false,
    useKatex: Boolean = false,
    bottomContentPadding: Dp = 160.dp,
    /** The user's own name and avatar, so pane B labels their messages as pane A does. */
    userAvatarUrl: String? = null,
    userName: String? = null,
) {
    val listState = rememberLazyListState()
    // This pane hosts no AskUserQuestionPanel, so an unanswered ask has no affordance to resolve it.
    val renderedToolCalls = remember(activeToolCalls) { activeToolCalls.withoutAnyUnansweredQuestions() }
    // Parsed once per list; `contentType` runs on every measure pass.
    val wakeups = remember(displayMessages) { wakeupsByMessageId(displayMessages) }
    val totalItemCount = displayMessages.size +
        (if (isStreaming) 1 else 0) +
        (if (isStreaming) renderedToolCalls.size else 0)

    ClearExpansionStatesAfterReply(isStreaming, ExpansionPane.SECONDARY)

    // Auto-scroll to bottom during streaming
    LaunchedEffect(streamingContent.length, totalItemCount) {
        if (totalItemCount > 0) {
            listState.scrollToItem(totalItemCount - 1, scrollOffset = Int.MAX_VALUE)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (error != null) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp),
            )
        } else {
            // An empty pane stays empty, like the primary one beside it: nothing has been sent yet.
            val previewOverrides = LocalPreviewOverrides.current
            val liveAttachments = remember(streamingAttachments, previewOverrides) {
                streamingAttachments.withPreviews(previewOverrides)
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(top = 8.dp, bottom = bottomContentPadding),
            ) {
                items(
                    items = displayMessages,
                    key = { node -> "secondary_${node.message.messageId}" },
                    // A wake-up renders as a system row, not a bubble; its own type keeps slot reuse
                    // from crossing the two layouts.
                    contentType = { node ->
                        if (wakeups.wakeupOf(node, isEditing = false) != null) "system" else "message"
                    },
                ) { node ->
                    CompositionLocalProvider(
                        LocalSuppressGroupAutoCollapse provides
                            (node.message.messageId == justSettledMessageId),
                        // No feedback affordance in a comparison pane, but provided for the same
                        // reason as the primary list so the two cannot drift.
                        LocalFeedbackEnabled provides !isStreaming,
                        LocalExpansionScope provides
                            expansionScope(ExpansionPane.SECONDARY, node.message.parentMessageId),
                    ) {
                    // Same branch as MessageList; this pane never edits. Its bubbles take no
                    // actions and no sibling navigation (their callbacks are no-ops), so neither
                    // does the row.
                    val systemEvent = wakeups.wakeupOf(node, isEditing = false)
                    if (systemEvent != null) {
                        SystemEventRow(
                            display = systemEvent,
                            messageId = node.message.messageId,
                            fontSizeMultiplier = fontSizeMultiplier,
                            useKatex = useKatex,
                        )
                    } else {
                    MessageBubble(
                        message = node.message,
                        siblingIndex = node.siblingIndex,
                        siblingCount = node.siblingCount,
                        onSiblingNavigation = { /* no-op for secondary */ },
                        onEdit = {},
                        onCopy = {},
                        baseUrl = baseUrl,
                        fontSizeMultiplier = fontSizeMultiplier,
                        userAvatarUrl = userAvatarUrl,
                        userName = userName,
                        selectedEndpoint = selectedEndpoint,
                        showImageDescriptions = showImageDescriptions,
                        chatLayoutStyle = chatLayoutStyle,
                        showAvatars = showAvatars,
                        showBubbles = showBubbles,
                        useKatex = useKatex,
                    )
                    }
                    }
                }

                if (isStreaming) {
                    val streamingWorkspace = partitionWorkspaceChanges(liveAttachments)
                    // The message the reply answers, which its saved message names as its parent.
                    val replyScope = expansionScope(
                        ExpansionPane.SECONDARY,
                        displayMessages.lastOrNull()?.message?.messageId,
                    )
                    item(key = "secondary_streaming_message") {
                        ProvideMarkdownMedia(liveAttachments, baseUrl) {
                            StreamingMessageBubble(
                                streamingContent = streamingContent,
                                senderName = streamingSenderName,
                                senderIconUrl = null,
                                fontSizeMultiplier = fontSizeMultiplier,
                                selectedEndpoint = selectedEndpoint,
                                chatLayoutStyle = chatLayoutStyle,
                                showAvatars = showAvatars,
                                showBubbles = showBubbles,
                                useKatex = useKatex,
                            )
                        }
                    }

                    if (renderedToolCalls.isNotEmpty()) {
                        items(
                            items = renderedToolCalls,
                            key = { "secondary_tool_call_${it.id}" },
                            contentType = { "tool_call" },
                        ) { toolCall ->
                            CompositionLocalProvider(LocalExpansionScope provides replyScope) {
                                StreamingToolCallCard(
                                    toolCall = toolCall,
                                    modifier = Modifier.streamingContentColumnPadding(chatLayoutStyle, showAvatars, showBubbles),
                                    baseUrl = baseUrl,
                                    streamingAttachments = streamingWorkspace.inline,
                                    sandboxStarting = toolCall.id in sandboxStartingToolCallIds,
                                )
                            }
                        }
                    }

                    if (streamingWorkspace.changes.isNotEmpty()) {
                        item(key = "secondary_streaming_workspace_changes", contentType = "workspace_changes") {
                            CompositionLocalProvider(LocalExpansionScope provides replyScope) {
                                WorkspaceChangesSection(
                                    changes = streamingWorkspace.changes,
                                    baseUrl = baseUrl,
                                    stateKey = "secondary_streaming",
                                    modifier = Modifier.streamingContentColumnPadding(chatLayoutStyle, showAvatars, showBubbles),
                                    live = true,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
