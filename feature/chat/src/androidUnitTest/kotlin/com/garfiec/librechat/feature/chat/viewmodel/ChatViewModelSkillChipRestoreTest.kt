package com.garfiec.librechat.feature.chat.viewmodel

import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.common.ToolConstants
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.StreamEvent
import com.garfiec.librechat.core.model.response.ChatStatusResponse
import com.garfiec.librechat.feature.chat.components.AttachedFile
import com.garfiec.librechat.feature.chat.viewmodel.delegate.PlatformFileHandler
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * A send that fails before the server kept the turn gives its skill chips back with the rest of
 * the composer: an un-send before `created`, or a created turn the settled reload doesn't find.
 * A turn of files alone has no text to put back, and its chips still come back.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelSkillChipRestoreTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val fixture = ChatViewModelTestFixture()
    private val fileHandler = mockk<PlatformFileHandler>(relaxed = true)
    private val messages = MutableStateFlow<List<Message>>(emptyList())
    private val attachedFiles = MutableStateFlow<List<AttachedFile>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fixture.stubDefaults()
        with(fixture) {
            every { configRepository.endpointConfigs } returns MutableStateFlow(
                mapOf(
                    ENDPOINT to EndpointConfig(),
                    EndpointConstants.AGENTS to EndpointConfig(capabilities = listOf(ToolConstants.SKILLS)),
                ),
            )
            every { configRepository.availableModels } returns MutableStateFlow(mapOf(ENDPOINT to listOf(MODEL)))
            every { platformDelegateFactory.createFileHandler(any()) } returns fileHandler
            every { fileHandler.attachedFiles } returns attachedFiles
            every { messageRepository.observeMessages(any()) } returns messages
            coEvery { conversationRepository.getConversation(any(), any()) } returns Result.Error(message = "test")
            coEvery { chatRepository.checkStreamStatus(any(), any()) } returns ChatStatusResponse(active = false)
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a send that fails before created puts the skill chips back with the text`() = skillsTest { vm ->
        streamEvery(StreamEvent.Error("Request failed (HTTP 302)"))
        vm.addPendingManualSkill("pdf-tools")
        send(vm)

        assertThat(vm.uiState.value.isStreaming).isFalse()
        assertThat(vm.uiState.value.inputText).isEqualTo("hello")
        assertThat(vm.uiState.value.pendingManualSkills).containsExactly("pdf-tools")
    }

    @Test
    fun `a file-only send that fails before created puts the skill chips back`() = skillsTest { vm ->
        streamEvery(StreamEvent.Error("Request failed (HTTP 302)"))
        attachedFiles.value = listOf(AttachedFile(uri = "content://a.pdf", name = "a.pdf", fileId = "file-1"))
        vm.addPendingManualSkill("pdf-tools")
        send(vm, text = "")

        assertThat(vm.uiState.value.isStreaming).isFalse()
        assertThat(vm.uiState.value.inputText).isEmpty()
        assertThat(vm.uiState.value.pendingManualSkills).containsExactly("pdf-tools")
    }

    @Test
    fun `a created file-only turn the server never saved puts the skill chips back`() =
        skillsTest(conversationId = "conv-1") { vm ->
            streamEvery(
                created("conv-1"),
                StreamEvent.Error("An error occurred while processing the request: 401"),
            )
            coEvery { fixture.messageRepository.getMessages("conv-1") } returns Result.Success(emptyList())
            attachedFiles.value = listOf(AttachedFile(uri = "content://a.pdf", name = "a.pdf", fileId = "file-1"))
            vm.addPendingManualSkill("pdf-tools")
            send(vm, text = "")

            val state = vm.uiState.value
            assertThat(state.isStreaming).isFalse()
            assertThat(state.inputText).isEmpty()
            assertThat(state.pendingManualSkills).containsExactly("pdf-tools")
        }

    @Test
    fun `a created turn the server never saved puts the skill chips back with the text`() =
        skillsTest(conversationId = "conv-1") { vm ->
            streamEvery(
                created("conv-1"),
                StreamEvent.Error("An error occurred while processing the request: 401"),
            )
            // The settled reload after the failure: the server kept nothing of the turn.
            coEvery { fixture.messageRepository.getMessages("conv-1") } returns Result.Success(emptyList())
            vm.addPendingManualSkill("pdf-tools")
            send(vm)

            val state = vm.uiState.value
            assertThat(state.isStreaming).isFalse()
            assertThat(state.inputText).isEqualTo("hello")
            assertThat(state.pendingManualSkills).containsExactly("pdf-tools")
        }

    // ─── helpers ─────────────────────────────────────────────────────

    /** Every turn from here on streams [events] and ends. */
    private fun streamEvery(vararg events: StreamEvent) {
        every {
            fixture.chatRepository.startChat(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(),
            )
        } returns flowOf(*events)
    }

    private fun TestScope.send(vm: ChatViewModel, text: String = "hello") {
        vm.onModelSelected(ENDPOINT, MODEL)
        vm.onInputChanged(text)
        vm.sendMessage()
        runCurrent()
    }

    private fun created(conversationId: String) = StreamEvent.Created(
        conversationId = conversationId,
        messageId = "u-created",
        parentMessageId = "00000000-0000-0000-0000-000000000000",
    )

    private fun skillsTest(
        conversationId: String? = null,
        body: suspend TestScope.(ChatViewModel) -> Unit,
    ) = runTest(testDispatcher) {
        val vm = fixture.build(defaultDispatcher = testDispatcher, initialConversationId = conversationId)
        runCurrent()
        vm.onModelSelected(ENDPOINT, MODEL)
        runCurrent()
        try {
            body(vm)
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    private companion object {
        const val ENDPOINT = "anthropic"
        const val MODEL = "claude-haiku-4-5"
    }
}
