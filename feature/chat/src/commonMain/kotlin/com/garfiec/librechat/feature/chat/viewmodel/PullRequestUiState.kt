package com.garfiec.librechat.feature.chat.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.pullrequest.ConversationPullRequest

/**
 * The open chat's pull request (v0.8.9-rc1), written only by `PullRequestDelegate`. [pullRequest] is
 * null while nothing is known, the chat has none, or the chat isn't a code chat: the ⋮ item is then
 * absent. [refreshFailed] means a later lookup failed and [pullRequest] is the last known value.
 */
@Immutable
data class PullRequestUiState(
    val pullRequest: ConversationPullRequest? = null,
    val refreshFailed: Boolean = false,
)
