package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.common.result.FailureKind
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.MemoryKeyRouteShadowedException
import com.garfiec.librechat.core.model.Memory
import com.garfiec.librechat.core.model.MemoryKeyProblem
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * What a write's answer does to the page: a confirmed write shows before the reload that follows it,
 * and a snackbar Retry's answer lands only on the sheet it was saved from.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MemoriesViewModelWriteTest : MemoriesViewModelTestBase() {

    // ------------------------------------------------------- confirmed writes outlive the reload

    @Test
    fun `a deleted row stays gone when the reload after it fails`() = runTest {
        val tea = memory("likes_tea")
        val coffee = memory("likes_coffee")
        coEvery { memoryRepository.getMemories() } returns page(tea, coffee)
        val viewModel = createViewModel()
        val events = collectEvents(viewModel)
        advanceUntilIdle()

        coEvery { memoryRepository.getMemories() } returns Result.Error(kind = FailureKind.Network)
        viewModel.deleteMemory(tea)
        advanceUntilIdle()

        coVerify(exactly = 2) { memoryRepository.getMemories() }
        assertThat(viewModel.state.memories.map { it.key }).containsExactly("likes_coffee")
        assertThat(viewModel.state.deletingHandles).isEmpty()
        assertThat(events).isEmpty()
    }

    @Test
    fun `a created memory shows when the reload after it fails, and its key is then taken`() = runTest {
        val tea = memory("likes_tea")
        coEvery { memoryRepository.getMemories() } returns page(tea)
        val milk = memory("likes_milk", value = "Oat")
        coEvery { memoryRepository.createMemory(any()) } returns Result.Success(milk)
        val viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { memoryRepository.getMemories() } returns Result.Error(kind = FailureKind.Network)
        viewModel.openCreate()
        viewModel.updateEditorKey("likes_milk")
        viewModel.updateEditorValue("Oat")
        viewModel.saveEditor()
        advanceUntilIdle()

        assertThat(viewModel.state.editor).isNull()
        assertThat(viewModel.state.memories).containsExactly(milk, tea).inOrder()

        viewModel.openCreate()
        viewModel.updateEditorKey("likes_milk")
        viewModel.updateEditorValue("Oat")
        advanceUntilIdle()
        assertThat(viewModel.state.editor?.keyError).isEqualTo(MemoryKeyProblem.DUPLICATE)
    }

    @Test
    fun `a renamed memory shows under its new key when the reload after it fails`() = runTest {
        val tea = memory("likes_tea", value = "Earl Grey")
        val coffee = memory("likes_coffee")
        coEvery { memoryRepository.getMemories() } returns page(coffee, tea)
        // A key-route rename is create-then-delete, so the echo is a new row with a new id.
        val renamed = memory("favourite_tea", value = "Assam")
        coEvery { memoryRepository.updateMemory(any(), any(), any()) } returns Result.Success(renamed)
        val viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { memoryRepository.getMemories() } returns Result.Error(kind = FailureKind.Network)
        viewModel.openMemory(tea)
        viewModel.updateEditorKey("favourite_tea")
        viewModel.updateEditorValue("Assam")
        viewModel.saveEditor()
        advanceUntilIdle()

        assertThat(viewModel.state.memories).containsExactly(renamed, coffee).inOrder()
    }

    @Test
    fun `an edited agent memory keeps its agent's name when the echo lacks it and the reload fails`() = runTest {
        val notes = memory("research_notes", value = "Draft", agentId = "a1", agentName = "Researcher")
        val tea = memory("likes_tea")
        coEvery { memoryRepository.getMemories() } returns page(notes, tea)
        // The PATCH echo is the stored record: no server-resolved agentName.
        val echoed = notes.copy(value = "Final", agentName = null)
        coEvery { memoryRepository.updateMemory(any(), any(), any()) } returns Result.Success(echoed)
        val viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { memoryRepository.getMemories() } returns Result.Error(kind = FailureKind.Network)
        viewModel.openMemory(notes)
        viewModel.updateEditorValue("Final")
        viewModel.saveEditor()
        advanceUntilIdle()

        val row = viewModel.state.memories.first()
        assertThat(row.value).isEqualTo("Final")
        assertThat(row.agentName).isEqualTo("Researcher")
        assertThat(viewModel.state.partitionOptions?.single { it.partition == MemoryPartition.Agent("a1") }?.agentName)
            .isEqualTo("Researcher")
    }

    @Test
    fun `a write the server does not echo waits for the reload`() = runTest {
        val tea = memory("likes_tea", value = "Earl Grey")
        coEvery { memoryRepository.getMemories() } returns page(tea)
        val viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { memoryRepository.getMemories() } returns page(tea.copy(value = "Assam"))
        viewModel.openMemory(tea)
        viewModel.updateEditorValue("Assam")
        viewModel.saveEditor()
        advanceUntilIdle()

        assertThat(viewModel.state.memories.single().value).isEqualTo("Assam")
    }

    // ----------------------------------------------------------- a created memory is on screen

    private suspend fun TestScope.createUnderFilter(
        select: MemoriesViewModel.() -> Unit,
    ): Pair<MemoriesViewModel, Memory> {
        val notes = memory("bot_notes", value = "Prefers bullet lists", agentId = "agent_1", agentName = "Helper")
        val tea = memory("likes_tea", value = "Earl Grey")
        coEvery { memoryRepository.getMemories() } returns page(
            notes,
            tea,
            memory("likes_coffee"),
            memory("likes_milk"),
            memory("likes_sugar"),
        )
        val color = memory("favorite_color", value = "Teal")
        coEvery { memoryRepository.createMemory(any()) } returns Result.Success(color)
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.select()
        advanceUntilIdle()

        // The reload after the save fails, so only the VM's own reveal can put the row on screen.
        coEvery { memoryRepository.getMemories() } returns Result.Error(kind = FailureKind.Network)
        viewModel.openCreate()
        viewModel.updateEditorKey("favorite_color")
        viewModel.updateEditorValue("Teal")
        viewModel.saveEditor()
        advanceUntilIdle()
        return viewModel to color
    }

    @Test
    fun `a memory created under an agent chip is shown, with the chip back on All`() = runTest {
        val (viewModel, color) = createUnderFilter { selectPartition(MemoryPartition.Agent("agent_1")) }

        assertThat(viewModel.state.partition).isEqualTo(MemoryPartition.All)
        assertThat(viewModel.state.visibleMemories).contains(color)
    }

    @Test
    fun `a memory created under the Personal chip keeps the chip`() = runTest {
        val (viewModel, color) = createUnderFilter { selectPartition(MemoryPartition.Personal) }

        assertThat(viewModel.state.partition).isEqualTo(MemoryPartition.Personal)
        assertThat(viewModel.state.visibleMemories).contains(color)
    }

    @Test
    fun `a memory created under a search it does not match clears the search`() = runTest {
        val (viewModel, color) = createUnderFilter { updateQuery("earl") }

        assertThat(viewModel.state.query).isEmpty()
        assertThat(viewModel.state.visibleMemories).contains(color)
    }

    @Test
    fun `a memory created under a search it matches keeps the search`() = runTest {
        val (viewModel, color) = createUnderFilter { updateQuery("teal") }

        assertThat(viewModel.state.query).isEqualTo("teal")
        assertThat(viewModel.state.visibleMemories).containsExactly(color)
    }

    @Test
    fun `a created memory asks the screen to scroll to the top`() = runTest {
        coEvery { memoryRepository.getMemories() } returns page(memory("likes_tea"))
        val viewModel = createViewModel()
        val scrolls = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.scrollToTop.toList(scrolls) }
        advanceUntilIdle()

        viewModel.openCreate()
        viewModel.updateEditorKey("favorite_color")
        viewModel.updateEditorValue("Teal")
        viewModel.saveEditor()
        advanceUntilIdle()

        assertThat(scrolls).hasSize(1)
    }

    @Test
    fun `an edit neither moves the filters nor scrolls`() = runTest {
        val tea = memory("likes_tea", value = "Earl Grey")
        val notes = memory("bot_notes", agentId = "agent_1")
        coEvery { memoryRepository.getMemories() } returns page(notes, tea)
        val viewModel = createViewModel()
        val scrolls = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.scrollToTop.toList(scrolls) }
        advanceUntilIdle()
        viewModel.selectPartition(MemoryPartition.Agent("agent_1"))

        viewModel.openMemory(notes)
        viewModel.updateEditorValue("Updated")
        viewModel.saveEditor()
        advanceUntilIdle()

        assertThat(viewModel.state.partition).isEqualTo(MemoryPartition.Agent("agent_1"))
        assertThat(scrolls).isEmpty()
    }

    // ------------------------------------------------ a retried delete stays off another sheet

    @Test
    fun `a retried delete leaves another memory's sheet open`() = runTest {
        val tea = memory("likes_tea")
        val coffee = memory("likes_coffee", value = "Flat white")
        coEvery { memoryRepository.getMemories() } returns page(tea, coffee)
        val gate = CompletableDeferred<Result<Unit>>()
        coEvery { memoryRepository.deleteMemory(any()) } coAnswers { gate.await() }
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.retry(MemoryOperation.Delete(tea))
        advanceUntilIdle()
        viewModel.openMemory(coffee)
        viewModel.updateEditorValue("Cortado")
        gate.complete(Result.Success(Unit))
        advanceUntilIdle()

        assertThat(viewModel.state.editor?.original).isEqualTo(coffee)
        assertThat(viewModel.state.editor?.value).isEqualTo("Cortado")
    }

    @Test
    fun `a retried delete closes the deleted memory's own sheet`() = runTest {
        val tea = memory("likes_tea")
        coEvery { memoryRepository.getMemories() } returns page(tea)
        val gate = CompletableDeferred<Result<Unit>>()
        coEvery { memoryRepository.deleteMemory(any()) } coAnswers { gate.await() }
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.retry(MemoryOperation.Delete(tea))
        advanceUntilIdle()
        viewModel.openMemory(tea)
        advanceUntilIdle()
        assertThat(viewModel.state.editor).isNotNull()
        gate.complete(Result.Success(Unit))
        advanceUntilIdle()

        assertThat(viewModel.state.editor).isNull()
    }

    /** A memory keyed `preferences` on a server without the by-id routes: the sheet says why. */
    @Test
    fun `an edit the key route cannot carry fails in the sheet with its own failure`() = runTest {
        val prefs = memory("preferences")
        coEvery { memoryRepository.getMemories() } returns page(prefs)
        coEvery { memoryRepository.updateMemory(any(), any(), any()) } returns
            Result.Error(exception = MemoryKeyRouteShadowedException())
        val viewModel = createViewModel()
        val events = collectEvents(viewModel)
        advanceUntilIdle()
        viewModel.openMemory(prefs)
        viewModel.updateEditorValue("dark mode")
        viewModel.saveEditor()
        advanceUntilIdle()

        assertThat(viewModel.state.editor?.saveFailure).isEqualTo(MemoryFailure.KeyRouteShadowed)
        assertThat(viewModel.state.memories).containsExactly(prefs)
        assertThat(events).isEmpty()
    }

    /** A route the request never reached (a blank key with no by-id route) says nothing about the row. */
    @Test
    fun `a delete answering a 404 no memory handler wrote keeps the row and reports it`() = runTest {
        val tea = memory("likes_tea")
        coEvery { memoryRepository.getMemories() } returns page(tea)
        coEvery { memoryRepository.deleteMemory(any()) } returns
            serverError(404, "Not Found", body = "<html>Cannot DELETE /api/memories/</html>")
        val viewModel = createViewModel()
        val events = collectEvents(viewModel)
        advanceUntilIdle()

        viewModel.deleteMemory(tea)
        advanceUntilIdle()

        val event = events.single() as MemoriesEvent.Failed
        assertThat(event.failure).isEqualTo(MemoryFailure.Generic)
        assertThat(viewModel.state.memories).containsExactly(tea)
        assertThat(viewModel.state.deletingHandles).isEmpty()
        // Not stale, so no reload: the one load is the page's own.
        coVerify(exactly = 1) { memoryRepository.getMemories() }
    }
}
