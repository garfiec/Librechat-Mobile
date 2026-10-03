package com.garfiec.librechat.feature.chat.prompts

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.PromptRepository
import com.garfiec.librechat.core.model.Prompt
import com.garfiec.librechat.core.model.PromptGroup
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Back from the prompt editor asks before throwing edits away — and only then. */
@OptIn(ExperimentalCoroutinesApi::class)
class PromptEditorUnsavedChangesTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val promptRepository = mockk<PromptRepository>(relaxed = true)

    private val production = Prompt(id = "p-2", groupId = "g-1", author = "a", prompt = "new body", type = "text")
    private val older = Prompt(id = "p-1", groupId = "g-1", author = "a", prompt = "old body", type = "text")
    private val group = PromptGroup(
        id = "g-1",
        name = "Summarize",
        author = "a",
        authorName = "A",
        oneliner = "Short summaries",
        command = "sum",
        productionId = "p-2",
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { promptRepository.revision } returns MutableStateFlow(0L)
        coEvery { promptRepository.getGroup("g-1") } returns Result.Success(group)
        coEvery { promptRepository.getPromptsByGroupId("g-1") } returns Result.Success(listOf(older, production))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun editor(groupId: String? = "g-1") = PromptEditorViewModel(promptRepository, groupId)

    @Test
    fun aFreshlyLoadedPromptLeavesWithoutAsking() = runTest(testDispatcher) {
        val viewModel = editor()

        assertFalse(viewModel.hasUnsavedChanges())
        viewModel.onBackRequested()

        assertFalse(viewModel.uiState.value.showDiscardConfirm)
        assertTrue(viewModel.uiState.value.exitRequested)
    }

    @Test
    fun anEditAsksBeforeLeavingAndDiscardingThenLeaves() = runTest(testDispatcher) {
        val viewModel = editor()

        viewModel.updatePromptText("newer body")
        viewModel.onBackRequested()
        assertTrue(viewModel.uiState.value.showDiscardConfirm)
        assertFalse(viewModel.uiState.value.exitRequested)

        viewModel.discardChanges()
        assertFalse(viewModel.uiState.value.showDiscardConfirm)
        assertTrue(viewModel.uiState.value.exitRequested)
    }

    /** The command lives in its own field state, outside the UI state the rest is compared in. */
    @Test
    fun aCommandEditAsks() = runTest(testDispatcher) {
        val viewModel = editor()

        viewModel.commandState.setTextAndPlaceCursorAtEnd("summary")
        viewModel.onBackRequested()

        assertTrue(viewModel.uiState.value.showDiscardConfirm)
    }

    @Test
    fun cancellingThePromptStaysInTheEditor() = runTest(testDispatcher) {
        val viewModel = editor()
        viewModel.updateName("Summarise")
        viewModel.onBackRequested()

        viewModel.dismissDiscardConfirmation()

        assertFalse(viewModel.uiState.value.showDiscardConfirm)
        assertFalse(viewModel.uiState.value.exitRequested)
    }

    @Test
    fun undoingAnEditByHandIsCleanAgain() = runTest(testDispatcher) {
        val viewModel = editor()

        viewModel.updateOneliner("Long summaries")
        assertTrue(viewModel.hasUnsavedChanges())
        viewModel.updateOneliner("Short summaries")

        assertFalse(viewModel.hasUnsavedChanges())
    }

    /** Variable values only fill the preview; Save never sends them. */
    @Test
    fun fillingInAVariableIsNotAnEdit() = runTest(testDispatcher) {
        val viewModel = editor()

        viewModel.updateVariable("text", "some text")

        assertFalse(viewModel.hasUnsavedChanges())
    }

    @Test
    fun anUntouchedCreateFormLeavesWithoutAskingANamedOneAsks() = runTest(testDispatcher) {
        val viewModel = editor(groupId = null)
        assertFalse(viewModel.hasUnsavedChanges())

        viewModel.updateName("New prompt")
        assertTrue(viewModel.hasUnsavedChanges())
    }

    /** Making another version live reloads the group, and the reloaded copy is the new clean state. */
    @Test
    fun switchingTheLiveVersionMovesTheCleanState() = runTest(testDispatcher) {
        coEvery { promptRepository.updatePromptProductionTag("p-1") } returns Result.Success(Unit)
        val viewModel = editor()
        coEvery { promptRepository.getGroup("g-1") } returns Result.Success(group.copy(productionId = "p-1"))

        viewModel.setProductionTag("p-1")

        assertTrue(viewModel.uiState.value.promptText == "old body")
        assertFalse(viewModel.hasUnsavedChanges())
    }
}
