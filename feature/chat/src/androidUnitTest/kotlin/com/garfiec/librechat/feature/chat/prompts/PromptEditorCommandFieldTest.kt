package com.garfiec.librechat.feature.chat.prompts

import com.garfiec.librechat.core.data.repository.PromptRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * The Command field renders `/` as a prefix, so its value must be exactly what the user typed.
 * A value that differs from what the field emitted strands the caret; that half is a
 * composition fault no unit test here can see. What these pin is the other half: whatever
 * reaches the state is a command the group update accepts, since it rejects anything outside
 * `[a-z0-9-]` and fails the whole save.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PromptEditorCommandFieldTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val promptRepository = mockk<PromptRepository>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { promptRepository.revision } returns MutableStateFlow(0L)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun commandAfterTyping(input: String): String {
        val editor = PromptEditorViewModel(promptRepository, null)
        editor.updateCommand(input)
        return editor.uiState.value.command
    }

    @Test
    fun aValidCommandIsStoredExactlyAsTyped() {
        listOf("o", "outline", "my-command-2", "-", "a".repeat(COMMAND_MAX_LENGTH)).forEach {
            assertEquals(it, commandAfterTyping(it))
        }
    }

    @Test
    fun aSlashTypedIntoAnEmptyFieldLeavesItEmptyWithoutLosingLaterInput() {
        assertEquals("", commandAfterTyping("/"))
        assertEquals("outline", commandAfterTyping("/outline"))
        assertEquals("outline", commandAfterTyping("//outline"))
    }

    @Test
    fun aSlashInsertedMidCommandIsNeverStored() {
        assertEquals("xoutline", commandAfterTyping("x/outline"))
    }

    @Test
    fun inputIsNormalisedTheWayTheWebFieldDoes() {
        assertEquals("my-command", commandAfterTyping("My Command"))
        assertEquals("summarize", commandAfterTyping("Summarize!"))
        assertEquals("caf", commandAfterTyping("café"))
    }

    @Test
    fun anEditPastTheLengthLimitIsIgnoredRatherThanTruncated() {
        val editor = PromptEditorViewModel(promptRepository, null)
        val full = "a".repeat(COMMAND_MAX_LENGTH - 1) + "z"
        editor.updateCommand(full)

        // Truncating an insert made mid-command would keep the new char and drop the `z`.
        editor.updateCommand("a" + "x" + full.drop(1))

        assertEquals(full, editor.uiState.value.command)
    }
}
