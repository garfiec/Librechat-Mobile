package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.request.NO_PARENT
import com.garfiec.librechat.feature.chat.util.branchSelectionFor
import com.garfiec.librechat.feature.chat.util.buildActiveMessagePath
import com.garfiec.librechat.feature.chat.viewmodel.ChatStateHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.JumpHandle
import com.garfiec.librechat.feature.chat.viewmodel.MessagesState
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class JumpToMessageDelegateTest {

    private fun msg(id: String, parent: String?, text: String, user: Boolean = false) =
        Message(messageId = id, conversationId = "c", parentMessageId = parent, text = text, isCreatedByUser = user)

    /**
     * root(user) ─┬─ a1 "first answer"
     *             └─ a2 "second answer"   (newest, shown by default)
     * a1 ─ u2 "follow up" ─┬─ b1 "pelican one"   (target)
     *                       └─ b2 "pelican two"
     */
    private val tree = listOf(
        msg("root", NO_PARENT, "question", user = true),
        msg("a1", "root", "first answer"),
        msg("a2", "root", "second answer"),
        msg("u2", "a1", "follow up", user = true),
        msg("b1", "u2", "pelican one"),
        msg("b2", "u2", "pelican two"),
    )

    // ---- branchSelectionFor ----

    @Test
    fun `selection walks every ancestor to the target`() {
        assertThat(branchSelectionFor(tree, "b1")).containsExactly("root", 0, NO_PARENT, 0, "a1", 0, "u2", 0)
    }

    @Test
    fun `the selection puts the target on the active path`() {
        val path = buildActiveMessagePath(tree, branchSelectionFor(tree, "b1")!!)
        assertThat(path.map { it.message.messageId }).containsExactly("root", "a1", "u2", "b1").inOrder()
    }

    @Test
    fun `orphans are keyed under the root like the walk keys them`() {
        val orphaned = listOf(msg("x", "missing-system-root", "hello", user = true), msg("y", "x", "reply"))
        assertThat(branchSelectionFor(orphaned, "y")).containsExactly(NO_PARENT, 0, "x", 0)
    }

    @Test
    fun `an unknown target has no selection`() {
        assertThat(branchSelectionFor(tree, "nope")).isNull()
    }

    // ---- delegate ----

    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)
    private val stateFlow = MutableStateFlow(ChatUiState())
    private val delegate = JumpToMessageDelegate(JumpHandle(ChatStateHandle(stateFlow, scope)), dispatcher)

    /** Stands in for ConversationLoadDelegate: rebuilds the display path whenever branches change. */
    private fun TestScope.rebuildDisplayOnBranchChange() = backgroundScope.launch {
        stateFlow.map { it.content.messages to it.content.activeBranches }.distinctUntilChanged().collect { (messages, branches) ->
            val path = buildActiveMessagePath(messages, branches)
            stateFlow.value = stateFlow.value.copy(content = stateFlow.value.content.copy(displayMessages = path))
        }
    }

    private fun load(messages: List<Message>, streaming: Boolean = false) {
        stateFlow.value = ChatUiState(content = MessagesState(messages = messages, isStreaming = streaming))
    }

    @Test
    fun `opens the target's branch and focuses its match`() = scope.runTest {
        rebuildDisplayOnBranchChange()
        load(tree)

        delegate.start("b1", "pelican")
        runCurrent()

        val state = stateFlow.value
        assertThat(state.displayMessages.map { it.message.messageId }).containsExactly("root", "a1", "u2", "b1").inOrder()
        assertThat(state.isSearchOpen).isTrue()
        assertThat(state.searchQuery).isEqualTo("pelican")
        val current = state.searchMatchIndices[state.currentSearchMatchIndex]
        assertThat(current.messageIndex).isEqualTo(3)
        assertThat(state.searchFocusRequest?.messageIndex).isEqualTo(3)
    }

    @Test
    fun `find opens on every word of the query, and outlines the target`() = scope.runTest {
        rebuildDisplayOnBranchChange()
        load(tree)

        delegate.start("b1", "zebra pelican")
        runCurrent()

        val state = stateFlow.value
        assertThat(state.isSearchOpen).isTrue()
        assertThat(state.searchQuery).isEqualTo("zebra pelican")
        assertThat(state.jumpFlash?.messageIndex).isEqualTo(3)
        assertThat(state.jumpFlash?.fade).isFalse()
    }

    @Test
    fun `each word of the query is counted on its own`() = scope.runTest {
        rebuildDisplayOnBranchChange()
        load(listOf(msg("q", NO_PARENT, "does it assume?", user = true), msg("t", "q", "I assume you are referring to it")))

        delegate.start("t", "assume referring")
        runCurrent()

        val state = stateFlow.value
        assertThat(state.searchMatchIndices.map { it.messageIndex }).containsExactly(0, 1, 1).inOrder()
        assertThat(state.searchMatchIndices[state.currentSearchMatchIndex].messageIndex).isEqualTo(1)
    }

    @Test
    fun `a short word is found only as a whole word, as the index matched it`() = scope.runTest {
        rebuildDisplayOnBranchChange()
        load(listOf(msg("t", NO_PARENT, "SoftEther is so good, some say")))

        delegate.start("t", "so")
        runCurrent()

        assertThat(stateFlow.value.searchMatchIndices).hasSize(1)
    }

    @Test
    fun `a hit in the reply lands past a match in legacy reasoning, and a reasoning hit lands in it`() = scope.runTest {
        rebuildDisplayOnBranchChange()
        val text = ":::thinking\nthe user asks about hashcat\n:::\n# Hashcat and RSA keys"
        load(listOf(msg("t", NO_PARENT, text)))

        delegate.start("t", "hashcat")
        runCurrent()
        val reply = stateFlow.value
        assertThat(reply.searchMatchIndices[reply.currentSearchMatchIndex].occurrenceInMessage).isEqualTo(1)

        delegate.start("t", "hashcat", inReasoning = true)
        runCurrent()
        val reasoning = stateFlow.value
        assertThat(reasoning.searchMatchIndices[reasoning.currentSearchMatchIndex].occurrenceInMessage).isEqualTo(0)
    }

    @Test
    fun `reaching a message on another branch says which response is shown`() = scope.runTest {
        rebuildDisplayOnBranchChange()
        load(tree)
        runCurrent() // the default path (root, a2) is on screen before the jump
        val switches = mutableListOf<BranchSwitch>()
        backgroundScope.launch { delegate.branchSwitched.collect { switches += it } }

        delegate.start("b1", "pelican")
        runCurrent()

        assertThat(switches).containsExactly(BranchSwitch(index = 1, count = 2))
    }

    @Test
    fun `waits for the stream to end before touching the branches`() = scope.runTest {
        rebuildDisplayOnBranchChange()
        load(tree, streaming = true)

        delegate.start("b1", "pelican")
        runCurrent()
        assertThat(stateFlow.value.content.activeBranches).isEmpty()

        stateFlow.value = stateFlow.value.copy(content = stateFlow.value.content.copy(isStreaming = false))
        runCurrent()
        assertThat(stateFlow.value.content.activeBranches).isNotEmpty()
    }

    @Test
    fun `a stream that starts while the selection is computed holds the branch write`() = scope.runTest {
        rebuildDisplayOnBranchChange()
        load(tree)
        // Starts a stream whenever work is sent off Main, as a resume landing mid-compute would.
        val startsStream = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                stateFlow.value = stateFlow.value.copy(content = stateFlow.value.content.copy(isStreaming = true))
                dispatcher.dispatch(context, block)
            }
        }
        val racing = JumpToMessageDelegate(JumpHandle(ChatStateHandle(stateFlow, scope)), startsStream)

        racing.start("b1", "pelican")
        runCurrent()
        assertThat(stateFlow.value.content.activeBranches).isEmpty()

        stateFlow.value = stateFlow.value.copy(content = stateFlow.value.content.copy(isStreaming = false))
        runCurrent()
        assertThat(stateFlow.value.content.activeBranches).isNotEmpty()
    }

    @Test
    fun `a flash with nothing to highlight is cleared once it has played`() = scope.runTest {
        rebuildDisplayOnBranchChange()
        load(tree)

        delegate.start("b1", "pélican")
        runCurrent()
        assertThat(stateFlow.value.jumpFlash?.fade).isTrue()

        advanceTimeBy(4.seconds)
        runCurrent()
        assertThat(stateFlow.value.jumpFlash).isNull()
    }

    @Test
    fun `keeps choices the user made off the target's path`() = scope.runTest {
        rebuildDisplayOnBranchChange()
        load(tree)
        stateFlow.value = stateFlow.value.copy(content = stateFlow.value.content.copy(activeBranches = mapOf("elsewhere" to 2)))

        delegate.start("b1", null)
        runCurrent()

        assertThat(stateFlow.value.content.activeBranches).containsEntry("elsewhere", 2)
    }

    @Test
    fun `no literal match still scrolls to the message without opening find`() = scope.runTest {
        rebuildDisplayOnBranchChange()
        load(tree)

        delegate.start("b1", "pélican")
        runCurrent()

        assertThat(stateFlow.value.isSearchOpen).isFalse()
        assertThat(stateFlow.value.searchFocusRequest?.messageIndex).isEqualTo(3)
    }

    @Test
    fun `a message that never appears is reported not found`() = scope.runTest {
        load(tree)
        val reports = mutableListOf<Unit>()
        backgroundScope.launch { delegate.notFound.toList(reports) }

        delegate.start("gone", "pelican")
        advanceTimeBy(31.seconds)
        runCurrent()

        assertThat(reports).hasSize(1)
        assertThat(stateFlow.value.content.activeBranches).isEmpty()
        assertThat(stateFlow.value.jumpPending).isFalse()
    }

    @Test
    fun `a refresh that settles without the message reports it at once`() = scope.runTest {
        load(tree)
        stateFlow.value = stateFlow.value.copy(content = stateFlow.value.content.copy(isRefreshingMessages = true))
        val reports = mutableListOf<Unit>()
        backgroundScope.launch { delegate.notFound.toList(reports) }

        delegate.start("gone", "pelican")
        runCurrent()
        stateFlow.value = stateFlow.value.copy(content = stateFlow.value.content.copy(isRefreshingMessages = false))
        advanceTimeBy(3.seconds)
        runCurrent()

        assertThat(reports).hasSize(1)
    }

    @Test
    fun `a slow first load still lands`() = scope.runTest {
        rebuildDisplayOnBranchChange()
        load(emptyList())
        stateFlow.value = stateFlow.value.copy(content = stateFlow.value.content.copy(isRefreshingMessages = true))
        val reports = mutableListOf<Unit>()
        backgroundScope.launch { delegate.notFound.toList(reports) }

        delegate.start("b1", "pelican")
        advanceTimeBy(15.seconds)
        stateFlow.value = stateFlow.value.copy(content = stateFlow.value.content.copy(messages = tree, isRefreshingMessages = false))
        runCurrent()

        assertThat(reports).isEmpty()
        assertThat(stateFlow.value.displayMessages.last().message.messageId).isEqualTo("b1")
        assertThat(stateFlow.value.searchFocusRequest).isNotNull()
    }

    @Test
    fun `the jump is pending until it focuses`() = scope.runTest {
        rebuildDisplayOnBranchChange()
        load(tree, streaming = true)

        delegate.start("b1", "pelican")
        runCurrent()
        assertThat(stateFlow.value.jumpPending).isTrue()

        stateFlow.value = stateFlow.value.copy(content = stateFlow.value.content.copy(isStreaming = false))
        runCurrent()
        assertThat(stateFlow.value.searchFocusRequest).isNotNull()
        assertThat(stateFlow.value.jumpPending).isFalse()
    }

    @Test
    fun `a stream that never ends still focuses a message already on screen`() = scope.runTest {
        rebuildDisplayOnBranchChange()
        load(tree, streaming = true)
        runCurrent()

        // "a2" is on the default path, so it is shown without any branch change.
        delegate.start("a2", "second")
        advanceTimeBy(31.seconds)
        runCurrent()

        assertThat(stateFlow.value.content.activeBranches).isEmpty()
        assertThat(stateFlow.value.searchFocusRequest?.messageIndex).isEqualTo(1)
        assertThat(stateFlow.value.jumpPending).isFalse()
    }

    @Test
    fun `a stream that never ends reports a message on another branch as not found`() = scope.runTest {
        rebuildDisplayOnBranchChange()
        load(tree, streaming = true)
        val reports = mutableListOf<Unit>()
        backgroundScope.launch { delegate.notFound.toList(reports) }

        delegate.start("b1", "pelican")
        advanceTimeBy(31.seconds)
        runCurrent()

        assertThat(reports).hasSize(1)
        assertThat(stateFlow.value.content.activeBranches).isEmpty()
    }

    @Test
    fun `a cancelled jump is no longer pending`() = scope.runTest {
        load(tree.dropLast(2))
        val jumpScope = CoroutineScope(coroutineContext + Job())
        val cancellable = JumpToMessageDelegate(JumpHandle(ChatStateHandle(stateFlow, jumpScope)), dispatcher)

        cancellable.start("b1", "pelican")
        runCurrent()
        assertThat(stateFlow.value.jumpPending).isTrue()
        jumpScope.cancel()
        runCurrent()

        assertThat(stateFlow.value.jumpPending).isFalse()
    }

    @Test
    fun `a message the refresh brings in is still found`() = scope.runTest {
        rebuildDisplayOnBranchChange()
        load(tree.dropLast(2))

        delegate.start("b1", "pelican")
        advanceTimeBy(2.seconds)
        stateFlow.value = stateFlow.value.copy(content = stateFlow.value.content.copy(messages = tree))
        runCurrent()

        assertThat(stateFlow.value.displayMessages.last().message.messageId).isEqualTo("b1")
    }

    @Test
    fun `a 500-message jump does its heavy work off the main dispatcher`() = scope.runTest {
        // A linear 500-message chat whose middle message is the target.
        val long = (0 until 500).map { i ->
            msg("m$i", if (i == 0) NO_PARENT else "m${i - 1}", "line $i about pelicans", user = i % 2 == 0)
        }
        val compute = CountingDispatcher(dispatcher)
        val counted = JumpToMessageDelegate(JumpHandle(ChatStateHandle(stateFlow, scope)), compute)
        rebuildDisplayOnBranchChange()
        load(long)
        runCurrent()

        counted.start("m250", "pelicans")
        runCurrent()

        // branchSelectionFor and the match enumeration each ran on the compute dispatcher.
        assertThat(compute.dispatches).isAtLeast(2)
        assertThat(stateFlow.value.searchMatchIndices).hasSize(500)
        assertThat(stateFlow.value.searchMatchIndices[stateFlow.value.currentSearchMatchIndex].messageIndex).isEqualTo(250)
    }
}

/** Forwards to [delegate], counting dispatches so a test can see work was sent there. */
private class CountingDispatcher(private val delegate: CoroutineDispatcher) : CoroutineDispatcher() {
    var dispatches = 0
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        dispatches++
        delegate.dispatch(context, block)
    }
}
