package com.garfiec.librechat.feature.chat.viewmodel

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.request.NO_PARENT
import com.garfiec.librechat.core.model.usage.ContextUsage
import com.garfiec.librechat.core.model.usage.TokenBudgetBreakdown
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * The context gauge through the real load path: the Room read-through re-emitting with the
 * server's copy (same ids, `metadata` filled in), and a branch switch via `activeBranches`, where
 * `displayMessages` is rebuilt off the main dispatcher before the gauge sees it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelContextGaugeTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val fixture = ChatViewModelTestFixture()

    private val room = MutableStateFlow<List<Message>>(emptyList())

    private val first = snapshot(remaining = 90_000)
    private val second = snapshot(remaining = 60_000)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fixture.stubDefaults()
        every { fixture.settingsDataStore.selectedMcpServers } returns flowOf(emptySet())
        every { fixture.settingsDataStore.enabledTools } returns flowOf(emptySet())
        coEvery { fixture.conversationRepository.getConversation(any(), any()) } returns Result.Error(message = "test")
        every { fixture.messageRepository.observeMessages(any()) } returns room
        coEvery { fixture.messageRepository.getMessages(any()) } answers { Result.Success(room.value) }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun snapshot(remaining: Int) = ContextUsage(
        breakdown = TokenBudgetBreakdown(maxContextTokens = 100_000, instructionTokens = 2_000, messageTokens = 500),
        remainingContextTokens = remaining,
    )

    private fun saved(usage: ContextUsage) = JsonObject(
        mapOf("contextUsage" to Json.encodeToJsonElement(ContextUsage.serializer(), usage)),
    )

    private fun message(id: String, parent: String, createdAt: String, metadata: JsonObject? = null) = Message(
        messageId = id,
        conversationId = "conv-1",
        parentMessageId = parent,
        isCreatedByUser = id.startsWith("u"),
        text = "text of $id",
        createdAt = createdAt,
        metadata = metadata,
    )

    @Test
    fun `the server's copy of a cached reply brings its saved snapshot`() = runTest(testDispatcher) {
        room.value = listOf(
            message("u1", NO_PARENT, "2026-10-01T00:00:00Z"),
            message("a1", "u1", "2026-10-01T00:00:01Z"),
        )
        val vm = fixture.build(defaultDispatcher = testDispatcher, initialConversationId = "conv-1")
        advanceUntilIdle()
        assertThat(vm.uiState.value.contextUsageSource).isNotEqualTo(ContextUsageSource.SNAPSHOT)

        // Same ids and tail; only the reply's metadata is new.
        room.value = listOf(
            message("u1", NO_PARENT, "2026-10-01T00:00:00Z"),
            message("a1", "u1", "2026-10-01T00:00:01Z", metadata = saved(first)),
        )
        advanceUntilIdle()

        assertThat(vm.uiState.value.contextUsage).isEqualTo(first)
        assertThat(vm.uiState.value.contextUsageSource).isEqualTo(ContextUsageSource.SNAPSHOT)
    }

    @Test
    fun `switching branches shows the other branch's snapshot`() = runTest(testDispatcher) {
        room.value = listOf(
            message("u1", NO_PARENT, "2026-10-01T00:00:00Z"),
            message("a1", "u1", "2026-10-01T00:00:01Z", metadata = saved(first)),
            message("a1b", "u1", "2026-10-01T00:00:02Z", metadata = saved(second)),
        )
        val vm = fixture.build(defaultDispatcher = testDispatcher, initialConversationId = "conv-1")
        advanceUntilIdle()
        // The newest sibling is shown by default.
        assertThat(vm.uiState.value.contextUsage).isEqualTo(second)

        vm.switchBranch("u1", 0)
        advanceUntilIdle()

        assertThat(vm.uiState.value.displayMessages.last().message.messageId).isEqualTo("a1")
        assertThat(vm.uiState.value.contextUsage).isEqualTo(first)
    }
}
