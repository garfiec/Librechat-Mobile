package com.garfiec.librechat.feature.chat.viewmodel

import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.feature.chat.viewmodel.delegate.PlatformFileHandler
import io.mockk.MockKMatcherScope
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
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
 * A new chat started from a project files itself there by sending `chatProjectId` on its first turn.
 * The server saves the field on every turn that carries it, so it must not ride anywhere else.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelProjectScopeTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val fixture = ChatViewModelTestFixture()
    private val fileHandler = mockk<PlatformFileHandler>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fixture.stubDefaults()
        with(fixture) {
            every { configRepository.endpointConfigs } returns MutableStateFlow(mapOf(ENDPOINT to EndpointConfig()))
            every { configRepository.availableModels } returns MutableStateFlow(mapOf(ENDPOINT to listOf(MODEL)))
            every { platformDelegateFactory.createFileHandler(any()) } returns fileHandler
            every { fileHandler.attachedFiles } returns MutableStateFlow(emptyList())
            every {
                chatRepository.startChat(
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                    any(), any(), any(),
                )
            } returns MutableSharedFlow()
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the first turn of a project's new chat carries the project`() = scopeTest("p1") { vm ->
        send(vm)
        verify(exactly = 1) { startChatWith(projectId = "p1") }
    }

    @Test
    fun `a project's landing keeps its own draft`() = scopeTest("p1") { vm ->
        vm.onInputChanged("for p1")
        coVerify { fixture.draftRepository.awaitDraft("__new_chat__:p1") }
        coVerify { fixture.draftRepository.saveDraft("__new_chat__:p1", "for p1") }
        coVerify(exactly = 0) { fixture.draftRepository.awaitDraft("__new_chat__") }
        coVerify(exactly = 0) { fixture.draftRepository.saveDraft("__new_chat__", any()) }
    }

    @Test
    fun `the plain landing keeps the shared draft`() = scopeTest(null) { vm ->
        vm.onInputChanged("plain")
        coVerify { fixture.draftRepository.awaitDraft("__new_chat__") }
        coVerify { fixture.draftRepository.saveDraft("__new_chat__", "plain") }
    }

    @Test
    fun `a temporary chat is never filed`() = scopeTest("p1") { vm ->
        vm.toggleTemporaryChat()
        send(vm)
        verify(exactly = 1) { startChatWith(projectId = null) }
    }

    @Test
    fun `an ordinary new chat sends no project`() = scopeTest(null) { vm ->
        send(vm)
        verify(exactly = 1) { startChatWith(projectId = null) }
    }

    private fun MockKMatcherScope.startChatWith(projectId: String?) = fixture.chatRepository.startChat(
        any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
        any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
        any(), any(), projectId,
    )

    private fun TestScope.send(vm: ChatViewModel) {
        vm.onModelSelected(ENDPOINT, MODEL)
        vm.onInputChanged("hello")
        vm.sendMessage()
        runCurrent()
    }

    private fun scopeTest(projectId: String?, body: suspend TestScope.(ChatViewModel) -> Unit) =
        runTest(testDispatcher) {
            val vm = fixture.build(defaultDispatcher = testDispatcher, initialChatProjectId = projectId)
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
