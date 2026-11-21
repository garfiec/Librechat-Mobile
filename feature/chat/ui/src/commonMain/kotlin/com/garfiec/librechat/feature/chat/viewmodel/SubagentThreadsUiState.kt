package com.garfiec.librechat.feature.chat.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.subagent.SubagentSummary
import com.garfiec.librechat.core.model.subagent.SubagentThreadView

@Immutable
data class SubagentThreadsUiState(
    val children: List<SubagentSummary> = emptyList(),
    val childrenTruncated: Boolean = false,
    val isLoadingIndex: Boolean = false,
    /** The child being viewed, or null while the list is showing. */
    val openThread: SubagentThreadView? = null,
    val openThreadId: String? = null,
    val isLoadingThread: Boolean = false,
    val isLoadingOlder: Boolean = false,
    /**
     * The conversation has no child view — it has no children, it is not the caller's, or it is
     * itself a subagent thread. One 404 covers all three, and none of them is a verdict about the
     * server, so this never turns the feature off anywhere else.
     */
    val hasNoChildView: Boolean = false,
    val error: String? = null,
) {
    val isShowingThread: Boolean get() = openThreadId != null

    /** Older history exists and can still be asked for. */
    val canLoadOlder: Boolean
        get() = openThread?.nextCursor != null && openThread.historyUnavailable != true

    /**
     * Older history is gone and no cursor will bring it back — the retained chain vanished between
     * requests. Rendering a load-more here would spin forever on nothing.
     */
    val olderHistoryUnavailable: Boolean
        get() = openThread?.historyUnavailable == true || (openThread?.historyTruncated == true && !canLoadOlder)
}
