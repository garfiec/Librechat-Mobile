package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.feature.chat.components.countMessageOccurrences
import com.garfiec.librechat.feature.chat.components.occurrencesInReasoning
import com.garfiec.librechat.feature.chat.util.MessageNode
import com.garfiec.librechat.feature.chat.util.branchSelectionFor
import com.garfiec.librechat.feature.chat.util.collapseParallelToPrimary
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.JumpFlash
import com.garfiec.librechat.feature.chat.viewmodel.JumpHandle
import com.garfiec.librechat.feature.chat.viewmodel.SearchFocusRequest
import com.garfiec.librechat.feature.chat.viewmodel.SearchMatch
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Opens a chat at one message, for a search hit: selects the branch that contains it, scrolls to it,
 * and opens find-in-page on [highlightQuery] with the current match inside that message.
 *
 * Waits for the message to appear (a cached chat has it at once; otherwise the network refresh
 * brings it) and for any stream to end, since branch selection must not change mid-stream (see
 * `MessageTreeDelegate.switchBranch`). A stream that outlasts [streamWait] leaves the branches as
 * they are, and the jump lands if the message is on screen anyway. The match list is built off the
 * main thread: counting occurrences renders each message's markdown. If the message is still
 * missing once the refresh has settled, or after [appearTimeout], [notFound] fires.
 *
 * `jumpPending` is set from [start] until the jump has focused or given up, so the list holds its
 * first scroll to the bottom instead of showing the bottom and then scrolling up to the message.
 *
 * Find-in-page takes the query's words each on its own, under the index's rule for short words, so
 * it opens on the whole query and focuses the target's first match: in its reply, or in its
 * reasoning when the hit was there. With none of the words literally there (the index folds
 * diacritics) the chat still scrolls to the message and flashes it, without opening find-in-page.
 * Either way the target stays outlined until find-in-page closes, and [branchSwitched] says when
 * reaching it changed the branch shown.
 */
class JumpToMessageDelegate(
    private val handle: JumpHandle,
    private val computeDispatcher: CoroutineDispatcher,
    private val appearTimeout: Duration = 30.seconds,
    private val streamWait: Duration = 30.seconds,
    private val settleGrace: Duration = 2.seconds,
) {
    private val _notFound = Channel<Unit>(Channel.BUFFERED)
    val notFound: Flow<Unit> = _notFound.receiveAsFlow()

    private val _branchSwitched = Channel<BranchSwitch>(Channel.BUFFERED)

    /** Reaching the message switched the shown branch; carries the response's position among its siblings. */
    val branchSwitched: Flow<BranchSwitch> = _branchSwitched.receiveAsFlow()

    /** Distinct from find-in-page's own ids (equality is what re-fires the scroll). */
    private var nextRequestId = REQUEST_ID_BASE

    fun start(targetMessageId: String, highlightQuery: String?, inReasoning: Boolean = false) {
        handle.update { search = search.copy(jumpPending = true) }
        handle.scope.launch {
            try {
                if (!jump(targetMessageId, highlightQuery?.trim().orEmpty(), inReasoning)) _notFound.trySend(Unit)
            } finally {
                handle.update { search = search.copy(jumpPending = false) }
            }
        }
    }

    /** False when the message can't be reached. */
    private suspend fun jump(targetMessageId: String, query: String, inReasoning: Boolean): Boolean {
        if (!awaitTarget(targetMessageId)) return false
        val wasShown = handle.state.displayMessages.any { it.message.messageId == targetMessageId }
        // A message already on screen needs no branch change, so it doesn't wait out a stream.
        val streamEnded = if (wasShown) {
            !handle.state.isStreaming
        } else {
            withTimeoutOrNull(streamWait) { handle.stateFlow.first { !it.isStreaming } } != null
        }
        // Still streaming, the branches can't move: the message is reachable only if it is on screen.
        if (!streamEnded && !wasShown) return false
        if (streamEnded) {
            val messages = handle.state.messages
            val selection = withContext(computeDispatcher) { branchSelectionFor(messages, targetMessageId) } ?: return false
            // A stream can start while the selection is computed off Main; branches must not move
            // mid-stream, so the write re-checks in the same update and waits out a stream that began.
            withTimeoutOrNull(streamWait) {
                var applied = false
                while (!applied) {
                    handle.stateFlow.first { !it.isStreaming }
                    handle.update {
                        applied = !content.isStreaming
                        if (applied) content = content.copy(activeBranches = content.activeBranches + selection)
                    }
                }
            }
        }
        val shown = withTimeoutOrNull(appearTimeout) {
            handle.stateFlow.first { state -> state.displayMessages.any { it.message.messageId == targetMessageId } }
        } ?: return false
        if (!wasShown) switchedBranchOf(shown.displayMessages, targetMessageId)?.let { _branchSwitched.trySend(it) }
        return focus(shown.displayMessages, targetMessageId, query, inReasoning)
    }

    /**
     * Waits for [targetMessageId] to be loaded: false once the refresh has settled without it (with
     * a moment for its rows to reach the list), or after [appearTimeout].
     */
    private suspend fun awaitTarget(targetMessageId: String): Boolean {
        fun ChatUiState.hasTarget() = messages.any { it.messageId == targetMessageId }
        var refreshSeen = false
        val outcome = withTimeoutOrNull(appearTimeout) {
            handle.stateFlow.first { state ->
                if (state.isRefreshingMessages) refreshSeen = true
                state.hasTarget() || state.content.messagesLoadFailed || (refreshSeen && !state.isRefreshingMessages)
            }
        } ?: return false
        if (outcome.hasTarget()) return true
        return withTimeoutOrNull(settleGrace) { handle.stateFlow.first { it.hasTarget() } } != null
    }

    /** The nearest turn at or above the target that has siblings: the branch point that moved. */
    private fun switchedBranchOf(nodes: List<MessageNode>, targetMessageId: String): BranchSwitch? {
        val targetIndex = nodes.indexOfFirst { it.message.messageId == targetMessageId }
        if (targetIndex < 0) return null
        val branchPoint = (targetIndex downTo 0).map { nodes[it] }.firstOrNull { it.siblingCount > 1 } ?: return null
        return BranchSwitch(index = branchPoint.siblingIndex + 1, count = branchPoint.siblingCount)
    }

    private suspend fun focus(
        nodes: List<MessageNode>,
        targetMessageId: String,
        query: String,
        inReasoning: Boolean,
    ): Boolean {
        val rendered = collapseParallelToPrimary(nodes)
        val targetIndex = rendered.indexOfFirst { it.message.messageId == targetMessageId }
        if (targetIndex < 0) return false
        val (findText, matches, first) = withContext(computeDispatcher) {
            val target = rendered[targetIndex].message
            // The target's first match on the side the hit was on; any of its matches otherwise.
            val kinds = if (query.isEmpty()) emptyList() else occurrencesInReasoning(target, query)
            val first = kinds.indexOfFirst { it == inReasoning }.takeIf { it >= 0 } ?: 0
            val findText = query.takeIf { kinds.isNotEmpty() }
            val matches = if (findText == null) {
                emptyList()
            } else {
                buildList {
                    rendered.forEachIndexed { index, node ->
                        repeat(countMessageOccurrences(node.message, findText)) { occurrence ->
                            add(SearchMatch(messageIndex = index, occurrenceInMessage = occurrence))
                        }
                    }
                }
            }
            Triple(findText, matches, first)
        }
        val current = matches.indexOfFirst { it.messageIndex == targetIndex && it.occurrenceInMessage == first }
        val request = SearchFocusRequest(messageIndex = targetIndex, requestId = nextRequestId++)
        val found = findText != null && current >= 0
        val target = JumpFlash(targetIndex, request.requestId, fade = !found)
        handle.update {
            search = if (found) {
                search.copy(
                    isSearchOpen = true,
                    searchQuery = findText.orEmpty(),
                    searchMatchIndices = matches,
                    currentSearchMatchIndex = current,
                    searchFocusRequest = request,
                    jumpFlash = target,
                    jumpPending = false,
                )
            } else {
                search.copy(searchFocusRequest = request, jumpFlash = target, jumpPending = false)
            }
        }
        if (!found) {
            // The flash is one-shot, but a lazy row remounting (scrolled away and back) would replay
            // it while the target is still set, so the target is dropped once the flash has played.
            delay(FLASH_CLEAR_AFTER)
            handle.update {
                if (search.jumpFlash?.requestId == request.requestId) search = search.copy(jumpFlash = null)
            }
        }
        return true
    }

    private companion object {
        const val REQUEST_ID_BASE = 1L shl 40

        /** Past `rememberJumpFlash`'s ~2s fade in, hold and out. */
        val FLASH_CLEAR_AFTER = 3.seconds
    }
}

/** A jump moved the shown branch: the response it now shows is [index] of [count]. */
data class BranchSwitch(val index: Int, val count: Int)
