package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.common.result.FailureKind
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.Memory
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** When the memory sheet's Save may send, and where a save's failure lands. */
@OptIn(ExperimentalCoroutinesApi::class)
class MemoriesViewModelSaveGuardTest : MemoriesViewModelTestBase() {

    /** A PATCH of the stored text would still bump updated_at and jump the row to the top. */
    @Test
    fun `an untouched edit cannot be saved and sends nothing`() = runTest {
        val tea = memory("likes_tea", value = "Earl Grey")
        coEvery { memoryRepository.getMemories() } returns page(tea)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.openMemory(tea)
        advanceUntilIdle()
        assertThat(viewModel.state.editor?.canSave).isFalse()
        viewModel.saveEditor()
        advanceUntilIdle()

        coVerify(exactly = 0) { memoryRepository.updateMemory(any(), any(), any()) }
        assertThat(viewModel.state.editor).isNotNull()
    }

    @Test
    fun `an edit is saveable once the value or the key changes, and not once changed back`() = runTest {
        val tea = memory("likes_tea", value = "Earl Grey")
        coEvery { memoryRepository.getMemories() } returns page(tea)
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.openMemory(tea)

        viewModel.updateEditorValue("Assam")
        advanceUntilIdle()
        assertThat(viewModel.state.editor?.canSave).isTrue()
        viewModel.updateEditorValue("Earl Grey")
        advanceUntilIdle()
        assertThat(viewModel.state.editor?.canSave).isFalse()
        viewModel.updateEditorKey("likes_black_tea")
        advanceUntilIdle()
        assertThat(viewModel.state.editor?.canSave).isTrue()
    }

    @Test
    fun `whitespace around an unchanged value is no change to save`() = runTest {
        val tea = memory("likes_tea", value = "Earl Grey")
        coEvery { memoryRepository.getMemories() } returns page(tea)
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.openMemory(tea)

        viewModel.updateEditorValue("Earl Grey ")
        viewModel.updateEditorKey(" likes_tea")
        advanceUntilIdle()

        assertThat(viewModel.state.editor?.isDirty).isTrue()
        assertThat(viewModel.state.editor?.canSave).isFalse()
    }

    /** Replacing the sheet would leave its save's failure nowhere to show and nothing to retry it. */
    @Test
    fun `no other sheet opens while a save is in flight, so its failure lands in its own sheet`() = runTest {
        val tea = memory("likes_tea", value = "Earl Grey")
        val coffee = memory("likes_coffee", value = "Flat white")
        coEvery { memoryRepository.getMemories() } returns page(tea, coffee)
        val gate = CompletableDeferred<Result<Memory?>>()
        coEvery { memoryRepository.updateMemory(any(), any(), any()) } coAnswers { gate.await() }
        val viewModel = createViewModel()
        val events = collectEvents(viewModel)
        advanceUntilIdle()
        viewModel.openMemory(tea)
        viewModel.updateEditorValue("Assam")
        viewModel.saveEditor()
        advanceUntilIdle()

        viewModel.openMemory(coffee)
        viewModel.openCreate()
        advanceUntilIdle()
        assertThat(viewModel.state.editor?.original).isEqualTo(tea)

        gate.complete(Result.Error(kind = FailureKind.Network))
        advanceUntilIdle()
        assertThat(viewModel.state.editor?.original).isEqualTo(tea)
        assertThat(viewModel.state.editor?.saveFailure).isEqualTo(MemoryFailure.Network)
        assertThat(events).isEmpty()
    }

    /** A save retries from its sheet; a snackbar never offers Retry for one. */
    @Test
    fun `a save's failure event offers no Retry`() {
        val create = MemoriesEvent.Failed(MemoryOperation.Create(key = "likes_tea", value = "Earl Grey"), MemoryFailure.Network)
        val update = MemoriesEvent.Failed(
            MemoryOperation.Update(memory = memory("likes_tea"), value = "Assam", newKey = "likes_tea"),
            MemoryFailure.Network,
        )

        assertThat(create.canRetry).isFalse()
        assertThat(update.retryOperation).isNull()
    }

    /** The request already carries the draft; typing meanwhile would show text the save never sent. */
    @Test
    fun `draft edits are ignored while a save is in flight`() = runTest {
        val gate = CompletableDeferred<Result<Memory?>>()
        coEvery { memoryRepository.createMemory(any()) } coAnswers { gate.await() }
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.openCreate()
        viewModel.updateEditorKey("likes_tea")
        viewModel.updateEditorValue("Earl Grey")
        viewModel.saveEditor()
        advanceUntilIdle()
        assertThat(viewModel.state.editor?.isSaving).isTrue()

        viewModel.updateEditorValue("other")
        viewModel.updateEditorKey("other_key")
        advanceUntilIdle()
        assertThat(viewModel.state.editor?.value).isEqualTo("Earl Grey")
        assertThat(viewModel.state.editor?.key).isEqualTo("likes_tea")

        // Once the save has answered (here, failed), the draft is editable again.
        gate.complete(Result.Error(kind = FailureKind.Network))
        advanceUntilIdle()
        viewModel.updateEditorValue("Assam")
        advanceUntilIdle()
        assertThat(viewModel.state.editor?.value).isEqualTo("Assam")
        assertThat(viewModel.state.editor?.saveFailure).isNull()
    }
}
