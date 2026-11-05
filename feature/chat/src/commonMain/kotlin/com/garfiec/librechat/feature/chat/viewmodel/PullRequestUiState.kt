package com.garfiec.librechat.feature.chat.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.pullrequest.ConversationPullRequest
import com.garfiec.librechat.core.model.pullrequest.PullRequestPresentation

/**
 * The open chat's pull request (v0.8.9-rc1), written only by `PullRequestDelegate`. [pullRequest] is
 * null while nothing is known, the chat has none, or the chat isn't a code chat: every PR surface is
 * then absent. [refreshFailed] means a later lookup failed and [pullRequest] is the last known value.
 */
@Immutable
data class PullRequestUiState(
    val pullRequest: ConversationPullRequest? = null,
    /** How [pullRequest] looks (glyph, tones, labels); null exactly when it is. */
    val presentation: PullRequestPresentation? = null,
    val refreshFailed: Boolean = false,
    /**
     * The composer chip spells out its state: the user hasn't seen this state and checks of this
     * chat's pull request yet. Turns false after the sheet opens or the chip has been on screen a
     * while; back to true when the state or the checks change. Kept in memory only.
     */
    val unseen: Boolean = false,
    /** The pull request sheet is up (opened from the ⋮ item, the header chip or the composer chip). */
    val sheetOpen: Boolean = false,
)
