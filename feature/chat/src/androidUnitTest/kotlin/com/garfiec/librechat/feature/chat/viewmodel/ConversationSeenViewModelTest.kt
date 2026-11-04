package com.garfiec.librechat.feature.chat.viewmodel

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.ConversationRepository
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.UNSEEN_REPLY_WATERMARK
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationSeenViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val reply = Instant.parse("2026-10-30T10:00:00Z")
    private val row = MutableStateFlow<Conversation?>(
        Conversation(
            conversationId = "c1",
            lastResponseAt = reply,
            lastResponseMessageId = "m1",
            isMarkedUnread = false,
            lastSeenAt = UNSEEN_REPLY_WATERMARK,
        ),
    )
    private val version = MutableStateFlow<String?>("0.8.9-rc1")
    private val repository = mockk<ConversationRepository>(relaxed = true) {
        every { observeConversation("c1") } returns row
        coEvery { markSeen(any(), any()) } returns Result.Success(true)
    }
    private val config = mockk<ConfigRepository>(relaxed = true) {
        every { detectedBackendVersion } returns version
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun newViewModel(): ConversationSeenViewModel = ConversationSeenViewModel(repository, config)

    @Test
    fun `an unseen reply on screen near the bottom in front is acknowledged once`() = runTest(dispatcher) {
        val vm = newViewModel()
        backgroundScope.launch { vm.target.collect {} }
        vm.bind("c1")
        advanceUntilIdle()
        val target = vm.target.value
        assertThat(target).isEqualTo(SeenTarget("c1", reply, "m1"))

        vm.maybeMarkSeen(target, replyRendered = true, nearBottom = true, inForeground = true, isStreaming = false)
        vm.maybeMarkSeen(target, replyRendered = true, nearBottom = true, inForeground = true, isStreaming = false)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.markSeen("c1", reply) }
    }

    @Test
    fun `nothing is sent while any condition is missing`() = runTest(dispatcher) {
        val vm = newViewModel()
        backgroundScope.launch { vm.target.collect {} }
        vm.bind("c1")
        advanceUntilIdle()
        val target = vm.target.value

        vm.maybeMarkSeen(target, replyRendered = false, nearBottom = true, inForeground = true, isStreaming = false)
        vm.maybeMarkSeen(target, replyRendered = true, nearBottom = false, inForeground = true, isStreaming = false)
        vm.maybeMarkSeen(target, replyRendered = true, nearBottom = true, inForeground = false, isStreaming = false)
        vm.maybeMarkSeen(target, replyRendered = true, nearBottom = true, inForeground = true, isStreaming = true)
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.markSeen(any(), any()) }
    }

    @Test
    fun `coming back to the app re-arms a reply already tried`() = runTest(dispatcher) {
        val vm = newViewModel()
        backgroundScope.launch { vm.target.collect {} }
        vm.bind("c1")
        advanceUntilIdle()
        val target = vm.target.value
        vm.maybeMarkSeen(target, replyRendered = true, nearBottom = true, inForeground = true, isStreaming = false)
        vm.onForeground()
        vm.maybeMarkSeen(target, replyRendered = true, nearBottom = true, inForeground = true, isStreaming = false)
        advanceUntilIdle()

        coVerify(exactly = 2) { repository.markSeen("c1", reply) }
    }

    @Test
    fun `a caught-up chat or an older server has no target`() = runTest(dispatcher) {
        val vm = newViewModel()
        backgroundScope.launch { vm.target.collect {} }
        vm.bind("c1")
        advanceUntilIdle()
        row.value = row.value?.copy(lastSeenAt = reply)
        advanceUntilIdle()
        assertThat(vm.target.value).isNull()

        row.value = row.value?.copy(lastSeenAt = UNSEEN_REPLY_WATERMARK)
        version.value = "0.8.8"
        advanceUntilIdle()
        assertThat(vm.target.value).isNull()
    }

    @Test
    fun `a hand-marked unread needs no particular message on screen`() {
        val manual = Conversation(
            conversationId = "c1",
            lastResponseAt = reply,
            lastResponseIsManual = true,
            isMarkedUnread = true,
        )
        assertThat(manual.seenTarget()).isEqualTo(SeenTarget("c1", reply, null))
    }
}
