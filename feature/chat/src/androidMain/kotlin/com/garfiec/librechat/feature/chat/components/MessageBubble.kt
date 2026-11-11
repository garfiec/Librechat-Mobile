package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.common.ChatLayoutConstants
import com.garfiec.librechat.core.model.FeedbackRating
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.MinimalFeedback
import com.garfiec.librechat.core.model.media.IconSource
import com.garfiec.librechat.core.ui.components.AvatarImage
import com.garfiec.librechat.core.ui.components.EndpointAvatar
import com.garfiec.librechat.core.ui.theme.chatColors
import com.garfiec.librechat.core.ui.theme.themeShapes
import com.garfiec.librechat.feature.chat.components.sendflight.sendFlightBubble
import com.garfiec.librechat.feature.chat.components.sendflight.sendFlightHeader
import com.garfiec.librechat.feature.chat.resources.*
import com.garfiec.librechat.feature.chat.resources.Res
import org.jetbrains.compose.resources.stringResource

@Composable
actual fun MessageBubble(
    message: Message,
    modifier: Modifier,
    siblingIndex: Int,
    siblingCount: Int,
    onSiblingNavigation: ((Int) -> Unit)?,
    onEdit: (() -> Unit)?,
    onRegenerate: (() -> Unit)?,
    onCopy: (() -> Unit)?,
    onFeedback: ((MinimalFeedback?) -> Unit)?,
    onReadAloud: (() -> Unit)?,
    onFork: (() -> Unit)?,
    baseUrl: String,
    fontSizeMultiplier: Float,
    isReading: Boolean,
    currentFeedback: FeedbackRating?,
    editSession: MessageEditSession?,
    userAvatarUrl: String?,
    userName: String?,
    showImageDescriptions: Boolean,
    showActionsInitially: Boolean,
    searchQuery: String?,
    isSearchMatch: Boolean,
    isCurrentSearchMatch: Boolean,
    searchFocusedOccurrence: Int,
    onFocusedOccurrencePosition: ((LayoutCoordinates, Rect) -> Unit)?,
    useKatex: Boolean,
    chatLayoutStyle: String,
    showAvatars: Boolean,
    showBubbles: Boolean,
    avatar: IconSource?,
) {
    if (chatLayoutStyle == ChatLayoutConstants.TWO_SIDED) {
        TwoSidedMessageBubble(
            message = message,
            modifier = modifier,
            siblingIndex = siblingIndex,
            siblingCount = siblingCount,
            onSiblingNavigation = onSiblingNavigation,
            onEdit = onEdit,
            onRegenerate = onRegenerate,
            onCopy = onCopy,
            onFeedback = onFeedback,
            onReadAloud = onReadAloud,
            onFork = onFork,
            baseUrl = baseUrl,
            fontSizeMultiplier = fontSizeMultiplier,
            useKatex = useKatex,
            isReading = isReading,
            currentFeedback = currentFeedback,
            editSession = editSession,
            userAvatarUrl = userAvatarUrl,
            userName = userName,
            showImageDescriptions = showImageDescriptions,
            showActionsInitially = showActionsInitially,
            searchQuery = searchQuery,
            isSearchMatch = isSearchMatch,
            isCurrentSearchMatch = isCurrentSearchMatch,
            searchFocusedOccurrence = searchFocusedOccurrence,
            onFocusedOccurrencePosition = onFocusedOccurrencePosition,
            showAvatars = showAvatars,
            showBubbles = showBubbles,
            avatar = avatar,
        )
    } else {
        ThreadMessageBubble(
            message = message,
            modifier = modifier,
            siblingIndex = siblingIndex,
            siblingCount = siblingCount,
            onSiblingNavigation = onSiblingNavigation,
            onEdit = onEdit,
            onRegenerate = onRegenerate,
            onCopy = onCopy,
            onFeedback = onFeedback,
            onReadAloud = onReadAloud,
            onFork = onFork,
            baseUrl = baseUrl,
            fontSizeMultiplier = fontSizeMultiplier,
            useKatex = useKatex,
            isReading = isReading,
            currentFeedback = currentFeedback,
            editSession = editSession,
            userAvatarUrl = userAvatarUrl,
            userName = userName,
            showImageDescriptions = showImageDescriptions,
            showActionsInitially = showActionsInitially,
            searchQuery = searchQuery,
            isSearchMatch = isSearchMatch,
            isCurrentSearchMatch = isCurrentSearchMatch,
            searchFocusedOccurrence = searchFocusedOccurrence,
            onFocusedOccurrencePosition = onFocusedOccurrencePosition,
            showAvatars = showAvatars,
            showBubbles = showBubbles,
            avatar = avatar,
        )
    }
}

/**
 * Original thread-style layout: avatar on left, name + time, content below.
 */
@Composable
private fun ThreadMessageBubble(
    message: Message,
    siblingIndex: Int,
    siblingCount: Int,
    onSiblingNavigation: ((Int) -> Unit)?,
    onEdit: (() -> Unit)?,
    onRegenerate: (() -> Unit)?,
    onCopy: (() -> Unit)?,
    onFeedback: ((MinimalFeedback?) -> Unit)?,
    onReadAloud: (() -> Unit)?,
    onFork: (() -> Unit)?,
    baseUrl: String,
    fontSizeMultiplier: Float,
    useKatex: Boolean,
    isReading: Boolean,
    currentFeedback: FeedbackRating?,
    editSession: MessageEditSession?,
    userAvatarUrl: String?,
    userName: String?,
    showImageDescriptions: Boolean,
    showActionsInitially: Boolean,
    searchQuery: String?,
    isSearchMatch: Boolean,
    isCurrentSearchMatch: Boolean,
    searchFocusedOccurrence: Int,
    onFocusedOccurrencePosition: ((LayoutCoordinates, Rect) -> Unit)?,
    showAvatars: Boolean,
    showBubbles: Boolean,
    avatar: IconSource?,
    modifier: Modifier = Modifier,
) {
    val isUser = message.isCreatedByUser
    var showActions by rememberActionRowShown(message.messageId, showActionsInitially, isReading)
    // The thread's tap handler leaves focus inside the list alone; a tap on a message ends an inline edit.
    val focusManager = LocalFocusManager.current

    // Determine background color for search state
    val searchBackground = when {
        isCurrentSearchMatch -> SearchHighlightOrange.copy(alpha = 0.18f)
        isSearchMatch -> SearchHighlightYellow.copy(alpha = 0.12f)
        else -> null
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (searchBackground != null) {
                    Modifier.background(
                        color = searchBackground,
                        shape = RoundedCornerShape(8.dp),
                    )
                } else {
                    Modifier
                },
            )
            .clickable(
                interactionSource = null,
                indication = null,
            ) {
                focusManager.clearFocus()
                showActions = !showActions
            }
            .padding(
                horizontal = 16.dp,
                vertical = 8.dp,
            ),
    ) {
        // Sender row with avatar
        Row(
            modifier = sendFlightHeader(message.messageId, isUser),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showAvatars) {
                if (isUser) {
                    AvatarImage(
                        imageUrl = userAvatarUrl,
                        fallbackText = userName ?: stringResource(Res.string.sender_you),
                        showPersonIcon = userAvatarUrl == null,
                        size = 28.dp,
                    )
                } else {
                    EndpointAvatar(
                        imageUrl = avatar?.imageUrl,
                        endpointName = avatar?.endpointName,
                        provider = avatar?.provider,
                        fallbackText = message.sender ?: stringResource(Res.string.sender_assistant),
                        size = 28.dp,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = if (isUser) {
                    userName ?: stringResource(Res.string.sender_you)
                } else {
                    message.sender ?: stringResource(Res.string.sender_assistant)
                },
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )

            // Timestamp
            val timestamp = message.createdAt
            if (timestamp != null) {
                Spacer(modifier = Modifier.width(8.dp))
                MessageTimestamp(isoTimestamp = timestamp)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Message content -- indented to align with the text after avatar
        val contentStartPadding = if (showAvatars) 36.dp else 0.dp

        // When bubbles are ON in thread mode, wrap content in a subtle rounded background
        val threadBubbleBackground = if (showBubbles) {
            if (isUser) {
                chatColors().userMessage
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        } else {
            null
        }

        Column(
            modifier = Modifier
                .padding(start = contentStartPadding)
                .then(sendFlightBubble(message.messageId, isUser))
                .then(
                    if (threadBubbleBackground != null) {
                        Modifier
                            .background(
                                color = threadBubbleBackground,
                                shape = if (isUser) themeShapes().userBubble else themeShapes().bubble,
                            )
                            .padding(12.dp)
                    } else {
                        Modifier
                    },
                ),
        ) {
            MessageContentAndActions(
                message = message,
                avatar = avatar,
                isUser = isUser,
                editSession = editSession,
                baseUrl = baseUrl,
                fontSizeMultiplier = fontSizeMultiplier,
                useKatex = useKatex,
                showImageDescriptions = showImageDescriptions,
                searchQuery = searchQuery,
                isSearchMatch = isSearchMatch,
                isCurrentSearchMatch = isCurrentSearchMatch,
                searchFocusedOccurrence = searchFocusedOccurrence,
                onFocusedOccurrencePosition = onFocusedOccurrencePosition,
                showActions = showActions,
                siblingIndex = siblingIndex,
                siblingCount = siblingCount,
                onSiblingNavigation = onSiblingNavigation,
                onEdit = onEdit,
                onRegenerate = onRegenerate,
                onCopy = onCopy,
                onFeedback = onFeedback,
                onReadAloud = onReadAloud,
                onFork = onFork,
                isReading = isReading,
                currentFeedback = currentFeedback,
                userName = userName,
                userAvatarUrl = userAvatarUrl,
                alignImagesEnd = false,
            )
        }
    }
}

/**
 * Two-sided chat layout: user messages on right, agent on left, with bubble backgrounds.
 */
@Composable
private fun TwoSidedMessageBubble(
    message: Message,
    siblingIndex: Int,
    siblingCount: Int,
    onSiblingNavigation: ((Int) -> Unit)?,
    onEdit: (() -> Unit)?,
    onRegenerate: (() -> Unit)?,
    onCopy: (() -> Unit)?,
    onFeedback: ((MinimalFeedback?) -> Unit)?,
    onReadAloud: (() -> Unit)?,
    onFork: (() -> Unit)?,
    baseUrl: String,
    fontSizeMultiplier: Float,
    useKatex: Boolean,
    isReading: Boolean,
    currentFeedback: FeedbackRating?,
    editSession: MessageEditSession?,
    userAvatarUrl: String?,
    userName: String?,
    showImageDescriptions: Boolean,
    showActionsInitially: Boolean,
    searchQuery: String?,
    isSearchMatch: Boolean,
    isCurrentSearchMatch: Boolean,
    searchFocusedOccurrence: Int,
    onFocusedOccurrencePosition: ((LayoutCoordinates, Rect) -> Unit)?,
    showAvatars: Boolean,
    showBubbles: Boolean,
    avatar: IconSource?,
    modifier: Modifier = Modifier,
) {
    val isUser = message.isCreatedByUser
    var showActions by rememberActionRowShown(message.messageId, showActionsInitially, isReading)
    // The thread's tap handler leaves focus inside the list alone; a tap on a message ends an inline edit.
    val focusManager = LocalFocusManager.current

    // Determine background color for search state
    val searchBackground = when {
        isCurrentSearchMatch -> SearchHighlightOrange.copy(alpha = 0.18f)
        isSearchMatch -> SearchHighlightYellow.copy(alpha = 0.12f)
        else -> null
    }

    val bubbleBackground = if (showBubbles) {
        if (isUser) {
            chatColors().userMessage
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    } else {
        null
    }

    // Composite search highlight over bubble background so the highlight is visible
    // even when an opaque bubble background is present.
    val effectiveBubbleBackground = if (searchBackground != null && bubbleBackground != null) {
        searchBackground.compositeOver(bubbleBackground)
    } else {
        searchBackground ?: bubbleBackground
    }

    val textColor = if (showBubbles) {
        if (isUser) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = null,
                indication = null,
            ) {
                focusManager.clearFocus()
                showActions = !showActions
            }
            .padding(
                horizontal = 8.dp,
                vertical = 4.dp,
            ),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        // Agent avatar on left
        if (!isUser && showAvatars) {
            EndpointAvatar(
                imageUrl = avatar?.imageUrl,
                endpointName = avatar?.endpointName,
                provider = avatar?.provider,
                fallbackText = message.sender ?: stringResource(Res.string.sender_assistant),
                size = 28.dp,
            )
            Spacer(modifier = Modifier.width(6.dp))
        }

        // Bubble content
        Column(
            modifier = Modifier
                .weight(1f)
                .then(sendFlightBubble(message.messageId, isUser))
                .then(
                    if (effectiveBubbleBackground != null) {
                        Modifier
                            .background(
                                color = effectiveBubbleBackground,
                                shape = if (isUser) themeShapes().userBubble else themeShapes().bubble,
                            )
                            .padding(12.dp)
                    } else {
                        Modifier.padding(
                            horizontal = 4.dp,
                            vertical = 8.dp,
                        )
                    },
                ),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        ) {
            // Name + timestamp inside bubble
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (isUser) {
                        userName ?: stringResource(Res.string.sender_you)
                    } else {
                        message.sender ?: stringResource(Res.string.sender_assistant)
                    },
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                val timestamp = message.createdAt
                if (timestamp != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    MessageTimestamp(isoTimestamp = timestamp)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Message content
            MessageContentAndActions(
                message = message,
                avatar = avatar,
                isUser = isUser,
                editSession = editSession,
                baseUrl = baseUrl,
                fontSizeMultiplier = fontSizeMultiplier,
                useKatex = useKatex,
                showImageDescriptions = showImageDescriptions,
                searchQuery = searchQuery,
                isSearchMatch = isSearchMatch,
                isCurrentSearchMatch = isCurrentSearchMatch,
                searchFocusedOccurrence = searchFocusedOccurrence,
                onFocusedOccurrencePosition = onFocusedOccurrencePosition,
                showActions = showActions,
                siblingIndex = siblingIndex,
                siblingCount = siblingCount,
                onSiblingNavigation = onSiblingNavigation,
                onEdit = onEdit,
                onRegenerate = onRegenerate,
                onCopy = onCopy,
                onFeedback = onFeedback,
                onReadAloud = onReadAloud,
                onFork = onFork,
                isReading = isReading,
                currentFeedback = currentFeedback,
                userName = userName,
                userAvatarUrl = userAvatarUrl,
                alignImagesEnd = isUser,
            )
        }

        // User avatar on right
        if (isUser && showAvatars) {
            Spacer(modifier = Modifier.width(6.dp))
            AvatarImage(
                imageUrl = userAvatarUrl,
                modifier = sendFlightHeader(message.messageId, isUser = true),
                fallbackText = userName ?: stringResource(Res.string.sender_you),
                showPersonIcon = userAvatarUrl == null,
                size = 28.dp,
            )
        }
    }
}
