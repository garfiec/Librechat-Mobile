package com.garfiec.librechat.feature.chat.viewmodel

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.media.ProviderId
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Reply avatars are resolved in the exposed state, so a custom endpoint's served `providerId`
 * reaches messages that loaded before the endpoint configs did.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelReplyAvatarTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val fixture = ChatViewModelTestFixture()
    private val endpointConfigs = MutableStateFlow<Map<String, EndpointConfig>>(emptyMap())

    private val messages = listOf(
        Message(messageId = "u-1", conversationId = "conv-1", isCreatedByUser = true, text = "hi"),
        Message(messageId = "r-1", conversationId = "conv-1", parentMessageId = "u-1", endpoint = "My Router", text = "hello"),
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
            coEvery { messageRepository.getMessages(any(), any()) } returns Result.Success(messages)
            coEvery { conversationRepository.getConversation(any(), any()) } returns Result.Error(message = "test")
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun configsArrivingAfterTheMessagesGiveTheReplyItsServedBrand() = runTest(testDispatcher) {
        val viewModel = fixture.build(defaultDispatcher = testDispatcher, initialConversationId = "conv-1")
        advanceUntilIdle()

        val before = viewModel.uiState.value.displayMessages
        assertEquals(2, before.size)
        assertNull("the user's own message has no reply avatar", before[0].avatar)
        assertNull(before[1].avatar?.provider)

        endpointConfigs.value = mapOf("My Router" to EndpointConfig(providerId = "openrouter"))
        advanceUntilIdle()

        val reply = viewModel.uiState.value.displayMessages[1]
        assertEquals(ProviderId.OPENROUTER, reply.avatar?.provider)
        assertEquals("My Router", reply.avatar?.endpointName)
    }
}
