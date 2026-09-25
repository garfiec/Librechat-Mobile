package com.garfiec.librechat.feature.chat.viewmodel

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.SubagentRepository
import com.garfiec.librechat.core.model.subagent.SubagentIndex
import com.garfiec.librechat.core.model.subagent.SubagentSummary
import com.garfiec.librechat.core.model.subagent.SubagentThreadView
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * The seam the lead named: what a 404 from the index is allowed to mean.
 *
 * It covers three unrelated conditions behind one body — the conversation is missing, is not the
 * caller's, or **is itself a subagent thread**. None of them says the deployment lacks the routes,
 * so remembering one would let a single conversation with no children turn the feature off
 * everywhere else. That is the queued-turn latch mistake in a different shape, which is why it is
 * asserted rather than described.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SubagentThreadsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val repository = mockk<SubagentRepository>()

    private val toolChild = SubagentSummary(
        threadId = "child-1",
        parentToolCallId = "call_abc",
        subagentType = "researcher",
        title = "Research",
        origin = "tool",
        status = "completed",
    )
    private val eventChild = SubagentSummary(
        threadId = "child-2",
        subagentType = "watcher",
        title = "Inbox watcher",
        origin = "event",
        status = "running",
    )

    private fun view(threadId: String, nextCursor: String? = null) = SubagentThreadView(
        threadId = threadId,
        title = "Research",
        nextCursor = nextCursor,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { repository.isRuledOutForServer() } returns false
        coEvery { repository.getChildren(any()) } returns
            Result.Success(SubagentIndex(children = listOf(toolChild, eventChild)))
        coEvery { repository.getThread(any(), any()) } answers {
            Result.Success(view(secondArg()))
        }
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun a_404_never_becomes_a_verdict_about_the_server() = runTest(dispatcher) {
        coEvery { repository.getChildren("convo-1") } returns
            Result.Error(ApiException(statusCode = 404, message = "Conversation not found"))
        val vm = SubagentThreadsViewModel(repository)

        vm.openFor("convo-1", null)
        assertThat(vm.uiState.value.hasNoChildView).isTrue()

        // Reopening asks again. A remembered 404 here is how one childless conversation would
        // suppress the feature for every other one.
        vm.openFor("convo-1", null)

        coVerify(exactly = 2) { repository.getChildren("convo-1") }
    }

    @Test
    fun a_transient_failure_is_not_remembered_either() = runTest(dispatcher) {
        coEvery { repository.getChildren("convo-1") } returns Result.Error(message = "offline")
        val vm = SubagentThreadsViewModel(repository)

        vm.openFor("convo-1", null)
        assertThat(vm.uiState.value.error).isEqualTo("offline")
        assertThat(vm.uiState.value.hasNoChildView).isFalse()

        vm.openFor("convo-1", null)

        // Pinned, the sheet would stay broken for this conversation until the entry is recreated.
        coVerify(exactly = 2) { repository.getChildren("convo-1") }
    }

    @Test
    fun a_server_known_to_predate_the_routes_is_never_asked() = runTest(dispatcher) {
        every { repository.isRuledOutForServer() } returns true
        val vm = SubagentThreadsViewModel(repository)

        vm.openFor("convo-1", null)

        coVerify(exactly = 0) { repository.getChildren(any()) }
        assertThat(vm.uiState.value.hasNoChildView).isTrue()
    }

    @Test
    fun a_successful_index_is_read_once_per_conversation() = runTest(dispatcher) {
        val vm = SubagentThreadsViewModel(repository)

        vm.openFor("convo-1", null)
        vm.openFor("convo-1", null)

        coVerify(exactly = 1) { repository.getChildren("convo-1") }
        assertThat(vm.uiState.value.children).hasSize(2)
    }

    @Test
    fun a_card_opens_its_own_child_by_joining_on_the_tool_call() = runTest(dispatcher) {
        // The card knows a tool_call id; `threadId` exists only in the index, so the join is the
        // only way a card can reach its child at all.
        val vm = SubagentThreadsViewModel(repository)

        vm.openFor("convo-1", "call_abc")

        coVerify(exactly = 1) { repository.getThread("convo-1", "child-1") }
        assertThat(vm.uiState.value.openThreadId).isEqualTo("child-1")
    }

    @Test
    fun a_tool_call_the_index_does_not_name_opens_the_list() = runTest(dispatcher) {
        val vm = SubagentThreadsViewModel(repository)

        vm.openFor("convo-1", "call_unknown")

        coVerify(exactly = 0) { repository.getThread(any(), any()) }
        assertThat(vm.uiState.value.isShowingThread).isFalse()
        // The event-spawned child is still listed — it has no tool call and no card, so this is
        // the only surface it can ever be reached from.
        assertThat(vm.uiState.value.children.map { it.threadId }).contains("child-2")
    }

    @Test
    fun reopening_after_viewing_a_child_lands_on_the_list_again() = runTest(dispatcher) {
        val vm = SubagentThreadsViewModel(repository)
        vm.openFor("convo-1", "call_abc")
        assertThat(vm.uiState.value.isShowingThread).isTrue()

        // Dismissed while a child was open, then reopened from somewhere with no focus.
        vm.openFor("convo-1", null)

        assertThat(vm.uiState.value.isShowingThread).isFalse()
    }

    @Test
    fun older_history_is_paged_by_the_cursor_the_server_gave() = runTest(dispatcher) {
        coEvery { repository.getThread("convo-1", "child-1") } returns
            Result.Success(view("child-1", nextCursor = "task-0:assistant"))
        coEvery { repository.getOlderPage(any(), any(), any()) } returns
            Result.Success(view("child-1", nextCursor = null))
        val vm = SubagentThreadsViewModel(repository)
        vm.openFor("convo-1", "call_abc")
        assertThat(vm.uiState.value.canLoadOlder).isTrue()

        vm.loadOlder("convo-1")

        coVerify(exactly = 1) { repository.getOlderPage("convo-1", "child-1", "task-0:assistant") }
        assertThat(vm.uiState.value.canLoadOlder).isFalse()
    }

    @Test
    fun a_vanished_history_chain_is_not_offered_as_a_load_more() = runTest(dispatcher) {
        // `historyUnavailable` means no cursor will bring the missing rows back. Offering the
        // button would spin on nothing.
        coEvery { repository.getThread("convo-1", "child-1") } returns Result.Success(
            SubagentThreadView(
                threadId = "child-1",
                nextCursor = "task-0:assistant",
                historyTruncated = true,
                historyUnavailable = true,
            ),
        )
        val vm = SubagentThreadsViewModel(repository)

        vm.openFor("convo-1", "call_abc")

        assertThat(vm.uiState.value.canLoadOlder).isFalse()
        assertThat(vm.uiState.value.olderHistoryUnavailable).isTrue()
    }

    @Test
    fun paging_does_not_stack_requests() = runTest(dispatcher) {
        coEvery { repository.getThread("convo-1", "child-1") } returns
            Result.Success(view("child-1", nextCursor = "task-0:assistant"))
        coEvery { repository.getOlderPage(any(), any(), any()) } returns
            Result.Success(view("child-1", nextCursor = "task--1:assistant"))
        val vm = SubagentThreadsViewModel(repository)
        vm.openFor("convo-1", "call_abc")

        vm.loadOlder("convo-1")
        vm.loadOlder("convo-1")

        // Two taps, two pages — but each waits for the last. The guard is on `isLoadingOlder`,
        // which the unconfined dispatcher clears between these, so this pins the cursor advancing
        // rather than the same page being asked for twice.
        coVerify(exactly = 1) { repository.getOlderPage("convo-1", "child-1", "task-0:assistant") }
    }
}
