package com.garfiec.librechat.feature.chat.viewmodel

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ToolCallPartRef
import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.content.AgentToolCall
import com.garfiec.librechat.core.model.content.MessageContentPart
import com.garfiec.librechat.core.model.media.ProviderId
import com.garfiec.librechat.feature.chat.util.ToolPartKey
import io.mockk.coEvery
import io.mockk.coVerify
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A loaded full tool call reaches the exposed display path: the overlay is not undone by the
 * reply-avatar pass that runs after it in the same combine.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelToolPartOverlayTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val fixture = ChatViewModelTestFixture()
    private val endpointConfigs = MutableStateFlow(mapOf("My Router" to EndpointConfig(providerId = "openrouter")))

    private val fullOutput = "stdout:\n" + "x".repeat(600) + "\n[exit code: 0]"
    private val preview = AgentToolCall(
        id = "call-1",
        stepId = "step-1",
        name = "bash_tool",
        output = fullOutput.take(20) + "\n…\n" + fullOutput.takeLast(20),
        outputTruncated = true,
        outputLength = fullOutput.length,
        previewRevision = "rev",
    )
    private val full = preview.copy(output = fullOutput, outputTruncated = null, outputLength = null, previewRevision = null)
    private val ref = ToolCallPartRef("r-1", "call-1", "step-1", null)

    private val messages = listOf(
        Message(messageId = "u-1", conversationId = "conv-1", isCreatedByUser = true, text = "run it"),
        Message(
            messageId = "r-1",
            conversationId = "conv-1",
            parentMessageId = "u-1",
            endpoint = "My Router",
            content = listOf(MessageContentPart(type = ContentType.TOOL_CALL, toolCall = preview)),
        ),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fixture.stubDefaults()
        with(fixture) {
            every { settingsDataStore.selectedMcpServers } returns flowOf(emptySet())
            every { settingsDataStore.enabledTools } returns flowOf(emptySet())
            every { configRepository.endpointConfigs } returns endpointConfigs
            every { messageRepository.observeMessages(any()) } returns flowOf(messages)
            coEvery { messageRepository.getMessages(any(), any()) } returns Result.Error(message = "offline")
            coEvery { conversationRepository.getConversation(any(), any()) } returns Result.Error(message = "offline")
            coEvery { messageRepository.storedToolCallParts("conv-1", listOf(ref)) } returns mapOf(ref to full)
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun aStoredFullCallOpensOnScreenWithoutAFetch() = runTest(testDispatcher) {
        val viewModel = fixture.build(defaultDispatcher = testDispatcher, initialConversationId = "conv-1")
        advanceUntilIdle()
        val key = requireNotNull(ToolPartKey.of(preview, null))

        viewModel.requestToolPart(key)
        advanceUntilIdle()

        val reply = viewModel.uiState.value.displayMessages.single { it.message.messageId == "r-1" }
        val shown = requireNotNull(reply.message.content?.single()?.toolCall)
        assertFalse("the card still holds the preview", shown.isPreview)
        assertEquals(fullOutput, shown.output)
        assertTrue(viewModel.toolPartStatuses.value.isEmpty())
        // The avatar pass still ran over the hydrated path.
        assertEquals(ProviderId.OPENROUTER, reply.avatar?.provider)
        coVerify(exactly = 0) { fixture.messageRepository.getToolCallPart(any(), any(), any(), any(), any(), any()) }
    }
}
