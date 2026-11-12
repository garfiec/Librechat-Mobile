package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.common.ChatLayoutConstants
import com.garfiec.librechat.core.model.media.IconSource
import com.garfiec.librechat.core.ui.components.EndpointAvatar
import com.garfiec.librechat.core.ui.theme.themeShapes
import com.garfiec.librechat.feature.chat.components.sendflight.sendFlightReplyBody
import com.garfiec.librechat.feature.chat.components.sendflight.sendFlightReplyHeader
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.cd_assistant_responding
import com.garfiec.librechat.feature.chat.resources.cd_generating_response
import org.jetbrains.compose.resources.stringResource

/**
 * Dedicated composable for rendering a message that is currently being streamed.
 * Extracted from MessageBubble.kt to avoid merge conflicts with WS4
 * (which modifies the action row in MessageBubble).
 *
 * Shows the live cursor at the end of the streaming content, or the waiting indicator before the
 * first delta. See StreamingCursor.kt. The reply's reasoning so far ([streamingThinking]) sits above
 * it in a collapsed Thinking block, as the persisted message shows its THINK parts, rather than as
 * body text.
 *
 * [pendingSkills] are the skills the turn picked, each a "Running X" row at the top of the reply's
 * content, under the sender row: where the finalized message draws the skill cards the server
 * prepends, so the swap doesn't move them past the header.
 *
 * While [waiting], the bubble shows only its waiting dots, whatever has streamed.
 */
@Composable
fun StreamingMessageBubble(
    streamingContent: String,
    senderName: String,
    avatar: IconSource?,
    modifier: Modifier = Modifier,
    fontSizeMultiplier: Float = 1.0f,
    chatLayoutStyle: String = ChatLayoutConstants.THREAD,
    showAvatars: Boolean = true,
    showBubbles: Boolean = false,
    useKatex: Boolean = false,
    streamingThinking: String = "",
    pendingSkills: List<String> = emptyList(),
    waiting: Boolean = false,
) {
    if (chatLayoutStyle == ChatLayoutConstants.TWO_SIDED) {
        TwoSidedStreamingBubble(
            streamingContent = streamingContent,
            senderName = senderName,
            avatar = avatar,
            fontSizeMultiplier = fontSizeMultiplier,
            showAvatars = showAvatars,
            showBubbles = showBubbles,
            useKatex = useKatex,
            streamingThinking = streamingThinking,
            pendingSkills = pendingSkills,
            waiting = waiting,
            modifier = modifier,
        )
    } else {
        ThreadStreamingBubble(
            streamingContent = streamingContent,
            senderName = senderName,
            avatar = avatar,
            fontSizeMultiplier = fontSizeMultiplier,
            showAvatars = showAvatars,
            showBubbles = showBubbles,
            useKatex = useKatex,
            streamingThinking = streamingThinking,
            pendingSkills = pendingSkills,
            waiting = waiting,
            modifier = modifier,
        )
    }
}

@Composable
private fun ThreadStreamingBubble(
    streamingContent: String,
    senderName: String,
    avatar: IconSource?,
    fontSizeMultiplier: Float,
    showAvatars: Boolean,
    showBubbles: Boolean,
    useKatex: Boolean,
    streamingThinking: String,
    pendingSkills: List<String>,
    waiting: Boolean,
    modifier: Modifier = Modifier,
) {
    val liveCd = streamingLiveDescription(streamingContent.isNotBlank())
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = 16.dp,
                vertical = 8.dp,
            ),
    ) {
        // Sender row with avatar
        Row(
            modifier = sendFlightReplyHeader(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showAvatars) {
                EndpointAvatar(
                    imageUrl = avatar?.imageUrl,
                    endpointName = avatar?.endpointName,
                    provider = avatar?.provider,
                    fallbackText = senderName,
                    size = 28.dp,
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = senderName,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Streaming content with blinking cursor
        val contentStartPadding = if (showAvatars) 36.dp else 0.dp
        Column(
            modifier = Modifier
                .padding(start = contentStartPadding)
                .fillMaxWidth()
                .then(sendFlightReplyBody())
                .then(
                    if (showBubbles) {
                        Modifier
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = themeShapes().bubble,
                            )
                            .padding(12.dp)
                    } else {
                        Modifier
                    },
                )
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = liveCd
                },
        ) {
            if (waiting) {
                StreamingWaitIndicator()
            } else {
                StreamingPendingSkills(pendingSkills)
                StreamingThinking(streamingThinking, fontSizeMultiplier, useKatex)
                if (streamingContent.isNotBlank()) {
                    // Routed through TextContentPart so artifact directives render as cards while the
                    // reply streams (#302) — an unclosed artifact shows its source via IncompleteArtifact.
                    // It also owns the cursor — a cursor placed here as a sibling lands on a line of its
                    // own instead of after the last word.
                    TextContentPart(
                        text = streamingContent,
                        fontSizeMultiplier = fontSizeMultiplier,
                        useKatex = useKatex,
                        streaming = true,
                        trailingCursor = true,
                    )
                } else {
                    StreamingWaitIndicator()
                }
            }
        }
    }
}

@Composable
private fun TwoSidedStreamingBubble(
    streamingContent: String,
    senderName: String,
    avatar: IconSource?,
    fontSizeMultiplier: Float,
    showAvatars: Boolean,
    showBubbles: Boolean,
    useKatex: Boolean,
    streamingThinking: String,
    pendingSkills: List<String>,
    waiting: Boolean,
    modifier: Modifier = Modifier,
) {
    val liveCd = streamingLiveDescription(streamingContent.isNotBlank())
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = 8.dp,
                vertical = 4.dp,
            ),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        // Agent avatar on left
        if (showAvatars) {
            EndpointAvatar(
                imageUrl = avatar?.imageUrl,
                endpointName = avatar?.endpointName,
                provider = avatar?.provider,
                fallbackText = senderName,
                size = 28.dp,
                modifier = sendFlightReplyHeader(),
            )
            Spacer(modifier = Modifier.width(6.dp))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .then(sendFlightReplyHeader())
                .then(
                    if (showBubbles) {
                        Modifier
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = themeShapes().bubble,
                            )
                            .padding(12.dp)
                    } else {
                        Modifier.padding(
                            horizontal = 4.dp,
                            vertical = 8.dp,
                        )
                    },
                )
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = liveCd
                },
        ) {
            Text(
                text = senderName,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = if (showBubbles) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Spacer(modifier = Modifier.height(4.dp))
            Column(modifier = sendFlightReplyBody()) {
                if (waiting) {
                    StreamingWaitIndicator()
                } else {
                    StreamingPendingSkills(pendingSkills)
                    StreamingThinking(streamingThinking, fontSizeMultiplier, useKatex)
                    if (streamingContent.isNotBlank()) {
                        // Routed through TextContentPart so artifact directives render as cards while the
                        // reply streams (#302) — an unclosed artifact shows its source via IncompleteArtifact.
                        // It also owns the cursor — a cursor placed here as a sibling lands on a line of its
                        // own instead of after the last word.
                        TextContentPart(
                            text = streamingContent,
                            fontSizeMultiplier = fontSizeMultiplier,
                            useKatex = useKatex,
                            streaming = true,
                            trailingCursor = true,
                        )
                    } else {
                        StreamingWaitIndicator()
                    }
                }
            }
        }
    }
}

/**
 * The live reply's reasoning, collapsed, above its text. Keyed to one stable state key so a user who
 * expands it mid-stream keeps it expanded as deltas arrive — but not across settle: the persisted
 * THINK part has its own key and opens collapsed (an accepted limit, see feature/chat CLAUDE.md).
 */
/**
 * The live region's status as the reply streams in; never the reply text, which would be
 * re-formatted and re-announced on every delta.
 */
@Composable
private fun streamingLiveDescription(responding: Boolean): String = if (responding) {
    stringResource(Res.string.cd_assistant_responding)
} else {
    stringResource(Res.string.cd_generating_response)
}

/** One "Running X" row per picked skill, until the final message brings the real cards. */
@Composable
private fun StreamingPendingSkills(names: List<String>) {
    names.forEach { name ->
        PendingSkillCallRow(skillName = name, modifier = Modifier.padding(bottom = 4.dp))
    }
}

@Composable
private fun StreamingThinking(thinking: String, fontSizeMultiplier: Float, useKatex: Boolean) {
    if (thinking.isBlank()) return
    ThinkingContentPart(
        thinkingText = thinking,
        fontSizeMultiplier = fontSizeMultiplier,
        useKatex = useKatex,
        stateKey = "streaming",
        streaming = true,
    )
    Spacer(modifier = Modifier.height(4.dp))
}

/**
 * Padding for the live reply's sibling items (tool cards, workspace changes, office previews,
 * memory) so they sit in the column its text uses, which is where the persisted message renders
 * them: `MessageBubble`'s content column on both platforms (avatar column, plus the bubble's own
 * padding when bubbles are on). Without it they land full width while streaming and shift inward
 * when the reply settles.
 */
internal fun Modifier.streamingContentColumnPadding(
    chatLayoutStyle: String,
    showAvatars: Boolean,
    showBubbles: Boolean,
): Modifier {
    val bubblePad = if (showBubbles) 12.dp else 0.dp
    return if (chatLayoutStyle == ChatLayoutConstants.TWO_SIDED) {
        val inner = if (showBubbles) 12.dp else 4.dp
        val avatar = if (showAvatars) 28.dp + 6.dp else 0.dp
        padding(start = 8.dp + avatar + inner, end = 8.dp + inner, top = 4.dp, bottom = 4.dp)
    } else {
        val avatar = if (showAvatars) 36.dp else 0.dp
        padding(start = 16.dp + avatar + bubblePad, end = 16.dp + bubblePad, top = 4.dp, bottom = 4.dp)
    }
}
