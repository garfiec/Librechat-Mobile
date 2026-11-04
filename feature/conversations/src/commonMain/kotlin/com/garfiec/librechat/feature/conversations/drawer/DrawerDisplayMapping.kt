package com.garfiec.librechat.feature.conversations.drawer

import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.SAVED_TAG
import com.garfiec.librechat.core.model.config.ModelSpecs
import com.garfiec.librechat.core.model.config.labelFor
import com.garfiec.librechat.core.model.iconEndpoint
import com.garfiec.librechat.core.model.iconProvider
import com.garfiec.librechat.core.model.isCodeConversation
import com.garfiec.librechat.core.model.isUnseen
import com.garfiec.librechat.core.model.pullrequest.ConversationPullRequest
import com.garfiec.librechat.core.model.pullrequest.presentPullRequest
import com.garfiec.librechat.core.model.resolveEndpointIconUrl
import com.garfiec.librechat.feature.conversations.components.ReadStateAction
import kotlin.time.Instant

/**
 * The drawer's conversation sections, already mapped to row data.
 *
 * Derived upstream of the search query (see [DrawerViewModel.displayConversations]) so a keystroke
 * re-assembles `DrawerUiState` from these rows instead of re-running the mapping — the grouped list
 * in particular is debounced, so it is usually unchanged between keystrokes.
 */
internal data class DrawerDisplaySnapshot(
    val grouped: List<Pair<DrawerGroupKey, List<DrawerConversationDisplayData>>> = emptyList(),
    val favorites: List<DrawerConversationDisplayData> = emptyList(),
    val pinned: List<DrawerConversationDisplayData> = emptyList(),
)

internal fun Conversation.toDrawerDisplayData(
    activeConversationId: String?,
    endpointConfigs: Map<String, EndpointConfig>,
    serverUrl: String,
    modelSpecs: ModelSpecs? = null,
    runningIds: Set<String> = emptySet(),
    pullRequests: Map<String, ConversationPullRequest> = emptyMap(),
): DrawerConversationDisplayData {
    val convId = conversationId ?: ""
    val running = convId in runningIds
    return DrawerConversationDisplayData(
        conversationId = convId,
        title = title ?: "New Chat",
        model = model,
        modelLabel = modelSpecs?.labelFor(spec) ?: model,
        endpoint = endpoint,
        updatedAt = updatedAt,
        isActive = convId == activeConversationId,
        isFavorite = SAVED_TAG in tags,
        isPinned = pinned == true,
        chatProjectId = chatProjectId,
        tags = tags,
        endpointIconUrl = resolveEndpointIconUrl(endpointConfigs, serverUrl),
        iconEndpoint = iconEndpoint(endpointConfigs),
        iconProvider = iconProvider(endpointConfigs),
        // As on web, a running row shows no dot: its reply isn't finished yet.
        isUnread = isUnseen && !running,
        isRunning = running,
        // Upstream's row gate: not while generating, and only for a chat that may be a code chat.
        pullRequest = pullRequests[convId]
            ?.takeIf { !running && isCodeConversation != false }
            ?.let { DrawerPullRequestMark(it, presentPullRequest(it)) },
    )
}

/**
 * The rows to ask about when the drawer opens: upstream's row gate (`codeDecisionListed !== true ||
 * isCodeConversation`, not generating), with an answer missing or older than [staleBefore].
 */
internal fun pullRequestLookupIds(
    conversations: List<Conversation>,
    runningIds: Set<String>,
    answeredAt: Map<String, Instant>,
    staleBefore: Instant,
): List<String> = conversations.mapNotNull { convo ->
    convo.conversationId?.takeIf { id ->
        id.isNotBlank() &&
            id !in runningIds &&
            convo.isCodeConversation != false &&
            answeredAt[id]?.let { it < staleBefore } != false
    }
}.distinct()

/**
 * The long-press menu's read-state action, or null for none: none on a running row (it has no
 * finished reply to mark either way) or on the open chat when it is read (reading it would clear an
 * unread mark at once).
 */
internal fun DrawerConversationDisplayData.readStateAction(readStateEnabled: Boolean): ReadStateAction? = when {
    !readStateEnabled || isRunning -> null
    isUnread -> ReadStateAction.MarkRead
    isActive -> null
    else -> ReadStateAction.MarkUnread
}
