package com.garfiec.librechat.feature.conversations.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.common.datetime.listLabel
import com.garfiec.librechat.core.data.search.HighlightedSnippet
import com.garfiec.librechat.core.data.search.HitSource
import com.garfiec.librechat.core.data.search.MessageSearchHit
import com.garfiec.librechat.core.ui.components.AdaptiveButton
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveDivider
import com.garfiec.librechat.core.ui.datetime.LocalDateTimeFormat
import com.garfiec.librechat.core.ui.datetime.LocalRelativeTimeReference
import com.garfiec.librechat.core.ui.datetime.resolve
import com.garfiec.librechat.feature.conversations.resources.Res
import com.garfiec.librechat.feature.conversations.resources.cd_next_match
import com.garfiec.librechat.feature.conversations.resources.cd_previous_match
import com.garfiec.librechat.feature.conversations.resources.search_chat_preview_empty
import com.garfiec.librechat.feature.conversations.resources.search_from_server
import com.garfiec.librechat.feature.conversations.resources.search_key_hint
import com.garfiec.librechat.feature.conversations.resources.search_open_in_chat
import com.garfiec.librechat.feature.conversations.resources.search_preview_empty
import com.garfiec.librechat.feature.conversations.resources.search_preview_uncached
import com.garfiec.librechat.feature.conversations.resources.search_reasoning_badge
import com.garfiec.librechat.feature.conversations.resources.search_role_assistant
import com.garfiec.librechat.feature.conversations.resources.search_role_you
import com.garfiec.librechat.feature.conversations.resources.search_step_match
import com.garfiec.librechat.feature.conversations.resources.search_untitled_conversation
import com.garfiec.librechat.feature.conversations.resources.show_less
import com.garfiec.librechat.feature.conversations.resources.show_more
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

// A chat bubble with the corner nearest the speaker squared off, like the chat screen's.
private val UserBubbleShape =
    RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomEnd = 16.dp, bottomStart = 16.dp)
private val PanelShape = RoundedCornerShape(12.dp)
private val HitOutline = 2.dp
private const val CONTEXT_ALPHA = 0.75f
private const val USER_BUBBLE_FRACTION = 0.85f

// Messages around the hit start folded to a few lines, so the thread reads as context.
private const val CONTEXT_LINES = 8

// How much of the message above stays in view when the pane scrolls to a turn.
private val FocusPeek = 48.dp

/**
 * The wide layout's right pane: the whole thread through the selected hit, opened at the hit, and
 * a way into the chat; or, for a selected conversation, its thread opened at the first match.
 */
@Composable
internal fun SearchPreviewPane(
    preview: SearchPreview?,
    chatPreview: SearchChatPreview?,
    agentNames: Map<String, String>,
    onStep: (Int) -> Unit,
    onOpen: (MessageSearchHit) -> Unit,
    onOpenConversation: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        preview != null -> HitPreview(preview, agentNames, onStep, onOpen, modifier)
        chatPreview != null -> ChatPreview(chatPreview, agentNames, onOpenConversation, modifier)
        else -> Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(Res.string.search_preview_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HitPreview(
    preview: SearchPreview,
    agentNames: Map<String, String>,
    onStep: (Int) -> Unit,
    onOpen: (MessageSearchHit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hit = preview.hit
    val uncached = hit.source == HitSource.Remote
    Column(modifier = modifier) {
        PaneHeader(
            title = hit.conversationTitle ?: stringResource(Res.string.search_untitled_conversation),
            highlights = preview.titleHighlights,
            meta = if (uncached) stringResource(Res.string.search_from_server) else metaLine(hit.model.named(agentNames), hit.createdAt),
        )
        ThreadPane(
            conversationId = hit.conversationId,
            turns = preview.thread,
            focusIndex = preview.hitIndex,
            hitId = hit.messageId,
            load = preview.load,
            failedNotice = stringResource(Res.string.search_preview_uncached),
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            // Nothing to show around it (not cached, and the fetch failed): the hit's own snippet.
            Turn(hit.snippet, isUser = hit.isCreatedByUser, isHit = true)
        }
        AdaptiveDivider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepButton(onClick = { onStep(-1) }, enabled = preview.index > 0) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(Res.string.cd_previous_match))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = stringResource(Res.string.search_step_match, preview.index + 1, preview.count),
                    style = MaterialTheme.typography.labelLarge,
                )
                if (LocalInputModeManager.current.inputMode == InputMode.Keyboard) {
                    Text(
                        text = stringResource(Res.string.search_key_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            StepButton(onClick = { onStep(1) }, enabled = preview.index < preview.count - 1) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(Res.string.cd_next_match))
            }
            Spacer(modifier = Modifier.weight(1f))
            OpenInChatButton(onClick = { onOpen(hit) })
        }
    }
}

@Composable
private fun ChatPreview(
    preview: SearchChatPreview,
    agentNames: Map<String, String>,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val group = preview.group
    Column(modifier = modifier) {
        PaneHeader(
            title = group.title ?: stringResource(Res.string.search_untitled_conversation),
            highlights = group.titleHighlights,
            meta = if (group.fromServer) {
                stringResource(Res.string.search_from_server)
            } else {
                metaLine(preview.model.named(agentNames), preview.updatedAt)
            },
        )
        ThreadPane(
            conversationId = group.conversationId,
            turns = preview.thread,
            focusIndex = preview.focusIndex,
            hitId = null,
            load = preview.load,
            failedNotice = stringResource(Res.string.search_chat_preview_empty),
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        AdaptiveDivider()
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Spacer(modifier = Modifier.weight(1f))
            OpenInChatButton(onClick = { onOpen(group.conversationId) })
        }
    }
}

/**
 * A thread, scrolled to [focusIndex] with the end of the message above still in view. Stepping
 * to another turn of the same conversation scrolls there; a new conversation opens at its turn.
 * [hitId]'s turn is outlined and shown in full, with the turns around it dimmed and folded.
 */
@Composable
private fun ThreadPane(
    conversationId: String,
    turns: List<PreviewTurn>,
    focusIndex: Int,
    hitId: String?,
    load: ThreadLoad,
    failedNotice: String,
    modifier: Modifier = Modifier,
    fallback: (@Composable () -> Unit)? = null,
) {
    val listState = remember(conversationId) { LazyListState() }
    // Hoisted out of the items: an item's own state is dropped when it scrolls away.
    var expanded by remember(conversationId) { mutableStateOf(emptySet<String>()) }
    val peek = with(LocalDensity.current) { FocusPeek.toPx() }
    // Whether this conversation's thread has been placed once: later moves animate.
    var placed by remember(conversationId) { mutableStateOf(false) }
    val focusId = turns.getOrNull(focusIndex)?.messageId
    LaunchedEffect(conversationId, focusId) {
        val target = turns.indexOfFirst { it.messageId == focusId }
        if (target < 0) return@LaunchedEffect
        if (placed) {
            listState.animateScrollToItem(target)
            if (target > 0) listState.animateScrollBy(-peek)
        } else {
            listState.scrollToItem(target)
            if (target > 0) listState.scrollBy(-peek)
            placed = true
        }
    }
    Column(modifier = modifier) {
        when {
            load == ThreadLoad.Loading -> Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                AdaptiveCircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
            load == ThreadLoad.Failed -> Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
                Notice(failedNotice)
            }
        }
        if (turns.isEmpty()) {
            if (fallback != null && load != ThreadLoad.Loading) {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) { fallback() }
            }
            return@Column
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                items = turns,
                key = { it.messageId },
                contentType = { if (it.messageId == hitId) "hit" else "turn" },
            ) { turn ->
                if (turn.messageId == hitId) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        turn.reasoning?.let { ReasoningPanel(it) }
                        Turn(turn.snippet, isUser = turn.isUser, isHit = true)
                    }
                } else {
                    ContextTurn(
                        turn = turn,
                        dimmed = hitId != null,
                        expanded = turn.messageId in expanded,
                        onToggle = {
                            expanded = if (turn.messageId in expanded) expanded - turn.messageId else expanded + turn.messageId
                        },
                    )
                }
            }
        }
    }
}

/** A message around the hit: folded to a few lines until "Show more". */
@Composable
private fun ContextTurn(turn: PreviewTurn, dimmed: Boolean, expanded: Boolean, onToggle: () -> Unit) {
    var overflows by remember(turn.messageId) { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth().then(if (dimmed) Modifier.alpha(CONTEXT_ALPHA) else Modifier),
        horizontalAlignment = if (turn.isUser) Alignment.End else Alignment.Start,
    ) {
        Turn(
            turn.snippet,
            isUser = turn.isUser,
            maxLines = if (expanded) Int.MAX_VALUE else CONTEXT_LINES,
            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
        )
        if (overflows || expanded) {
            Text(
                text = stringResource(if (expanded) Res.string.show_less else Res.string.show_more),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(PanelShape)
                    .clickable(role = Role.Button, onClick = onToggle)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun PaneHeader(title: String, highlights: List<IntRange>?, meta: String?) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        HighlightedTitle(title = title, highlights = highlights, style = MaterialTheme.typography.titleMedium)
        if (!meta.isNullOrEmpty()) {
            Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    AdaptiveDivider()
}

/** "model · when", whichever of the two is known. */
@Composable
private fun metaLine(model: String?, at: Long?): String? {
    val format = LocalDateTimeFormat.current
    val reference = LocalRelativeTimeReference.current
    val label = remember(at, format, reference) {
        at?.let { Instant.fromEpochMilliseconds(it).listLabel(format, reference) }
    }
    return listOfNotNull(model, label?.resolve()?.takeIf { it.isNotEmpty() }).joinToString(" · ").ifEmpty { null }
}

/**
 * One turn, laid out as in the chat: the user's in a tinted bubble on the end side, the
 * assistant's as plain text. The hit itself is outlined.
 */
@Composable
private fun Turn(
    text: HighlightedSnippet,
    isUser: Boolean,
    modifier: Modifier = Modifier,
    isHit: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    val outline = if (isHit) Modifier.border(HitOutline, MaterialTheme.colorScheme.primary, PanelShape) else Modifier
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Text(
            text = stringResource(if (isUser) Res.string.search_role_you else Res.string.search_role_assistant),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp, start = 4.dp, end = 4.dp),
        )
        val body = if (isUser) {
            Modifier
                .fillMaxWidth(USER_BUBBLE_FRACTION)
                .then(outline)
                .background(MaterialTheme.colorScheme.secondaryContainer, UserBubbleShape)
                .padding(12.dp)
        } else {
            Modifier.fillMaxWidth().then(outline).padding(if (isHit) 12.dp else 4.dp)
        }
        Box(modifier = body) {
            SnippetText(
                snippet = text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isUser) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                maxLines = maxLines,
                onTextLayout = onTextLayout,
            )
        }
    }
}

/** The hit's reasoning, apart from its answer, when the match is in it. */
@Composable
private fun ReasoningPanel(text: HighlightedSnippet) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, PanelShape)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Lightbulb,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(Res.string.search_reasoning_badge),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SnippetText(
            snippet = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = Int.MAX_VALUE,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun Notice(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh, PanelShape).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun OpenInChatButton(onClick: () -> Unit) {
    AdaptiveButton(onClick = onClick, modifier = Modifier.widthIn(min = 0.dp)) {
        Text(stringResource(Res.string.search_open_in_chat))
        Spacer(modifier = Modifier.width(6.dp))
        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun StepButton(onClick: () -> Unit, enabled: Boolean, content: @Composable () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
        content = content,
    )
}
