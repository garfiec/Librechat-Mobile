package com.garfiec.librechat.feature.chat.viewmodel

import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.Prompt
import com.garfiec.librechat.core.model.PromptGroup
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Text the app puts in the composer for the user is saved as the chat's draft just as typed text
 * is, so leaving the chat and coming back shows what was on screen, not the keystroke before it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelComposerDraftTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val fixture = ChatViewModelTestFixture()
    private val voiceHandle = slot<VoiceHandle>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fixture.stubDefaults()
        // A relaxed role's `hasAccess` is false, which would gate the prompt load off.
        coEvery { fixture.permissionGate.awaitRole() } returns UserRolePermissions(name = "USER")
        coEvery { fixture.promptRepository.getAllGroups() } returns
            Result.Success(listOf(group(PIRATE_ID, PIRATE), group(GREETING_ID, GREETING_TEMPLATE)))
        every { fixture.platformDelegateFactory.createVoiceInput(capture(voiceHandle), any()) } returns
            mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a prompt picked from the slash picker is saved as the draft`() = draftTest { vm ->
        vm.onInputChanged("/")
        vm.handleSlashCommand(vm.uiState.value.availablePrompts.first { it.id == PIRATE_ID })

        assertEquals(PIRATE, vm.uiState.value.inputText)
        coVerify { fixture.draftRepository.saveDraft(NEW_CHAT, PIRATE) }
    }

    @Test
    fun `a filled-in variable prompt is saved as the draft`() = draftTest { vm ->
        vm.onInputChanged("/")
        vm.handleSlashCommand(vm.uiState.value.availablePrompts.first { it.id == GREETING_ID })
        vm.confirmVariablePrompt("Hello Ada")

        coVerify { fixture.draftRepository.saveDraft(NEW_CHAT, "Hello Ada") }
    }

    @Test
    fun `a prompt handed over from the library is saved as the draft`() = draftTest { vm ->
        fixture.promptInsertionHandoff.put(PIRATE)
        vm.consumePendingPromptInsertion()

        coVerify { fixture.draftRepository.saveDraft(NEW_CHAT, PIRATE) }
    }

    @Test
    fun `dictated text is saved as the draft`() = draftTest {
        voiceHandle.captured.update { composer = composer.copy(inputText = "dictated words") }

        coVerify { fixture.draftRepository.saveDraft(NEW_CHAT, "dictated words") }
    }

    @Test
    fun `a voice state change that leaves the text alone saves nothing`() = draftTest {
        voiceHandle.captured.update { voice = voice.copy(isRecording = true) }

        coVerify(exactly = 0) { fixture.draftRepository.saveDraft(any(), any()) }
    }

    private fun draftTest(body: suspend TestScope.(ChatViewModel) -> Unit) = runTest(testDispatcher) {
        val vm = fixture.build(defaultDispatcher = testDispatcher)
        advanceUntilIdle()
        try {
            body(vm)
            advanceUntilIdle()
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    private fun group(id: String, text: String) = PromptGroup(
        id = id,
        name = id,
        author = "author-1",
        authorName = "Author",
        command = id,
        productionId = "$id-p",
        prompts = listOf(Prompt(id = "$id-p", groupId = id, author = "author-1", prompt = text, type = "text")),
    )

    private companion object {
        const val NEW_CHAT = "__new_chat__"
        const val PIRATE_ID = "pirate"
        const val PIRATE = "You are a pirate. Answer in pirate speak."
        const val GREETING_ID = "greeting"
        const val GREETING_TEMPLATE = "Hello {{name}}"
    }
}
