package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.common.result.FailureKind
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.Memory
import com.garfiec.librechat.core.model.MemoryKeyProblem
import com.garfiec.librechat.core.model.request.CreateMemoryRequest
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** The memory sheet and the mutations: save, rename, validation, discard, delete, error routing. */
@OptIn(ExperimentalCoroutinesApi::class)
class MemoriesViewModelEditorTest : MemoriesViewModelTestBase() {

    // ------------------------------------------------------------------------------------- save

    @Test
    fun `create sends the trimmed draft and a null echo still closes and reloads`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.openCreate()
        viewModel.updateEditorKey("  likes_tea ")
        viewModel.updateEditorValue("  Earl Grey\n")
        advanceUntilIdle()
        viewModel.saveEditor()
        advanceUntilIdle()

        coVerify { memoryRepository.createMemory(CreateMemoryRequest(key = "likes_tea", value = "Earl Grey")) }
        assertThat(viewModel.state.editor).isNull()
        coVerify(exactly = 2) { memoryRepository.getMemories() }
    }

    @Test
    fun `a second save while the first is in flight sends nothing`() = runTest {
        val gate = CompletableDeferred<Result<Memory?>>()
        coEvery { memoryRepository.createMemory(any()) } coAnswers { gate.await() }
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.openCreate()
        viewModel.updateEditorKey("likes_tea")
        viewModel.updateEditorValue("Earl Grey")
        advanceUntilIdle()

        viewModel.saveEditor()
        advanceUntilIdle()
        assertThat(viewModel.state.editor?.isSaving).isTrue()
        assertThat(viewModel.state.editor?.canSave).isFalse()
        viewModel.saveEditor()
        advanceUntilIdle()

        gate.complete(Result.Success(null))
        advanceUntilIdle()
        coVerify(exactly = 1) { memoryRepository.createMemory(any()) }
    }

    @Test
    fun `a 409 lands on the key field, not in a snackbar`() = runTest {
        coEvery { memoryRepository.createMemory(any()) } returns
            serverError(409, "Memory with this key already exists.")
        val viewModel = createViewModel()
        val events = collectEvents(viewModel)
        advanceUntilIdle()
        viewModel.openCreate()
        viewModel.updateEditorKey("likes_tea")
        viewModel.updateEditorValue("Earl Grey")
        advanceUntilIdle()

        viewModel.saveEditor()
        advanceUntilIdle()

        val editor = viewModel.state.editor
        assertThat(editor).isNotNull()
        assertThat(editor?.keyError).isEqualTo(MemoryKeyProblem.DUPLICATE)
        assertThat(editor?.isSaving).isFalse()
        assertThat(editor?.canSave).isFalse()
        assertThat(events).isEmpty()

        viewModel.updateEditorKey("likes_coffee")
        advanceUntilIdle()
        assertThat(viewModel.state.editor?.keyError).isNull()
        assertThat(viewModel.state.editor?.canSave).isTrue()
    }

    /**
     * A create 409 means a chat or the web stored the key after the list loaded, so the list lacks
     * the row the user is told exists. The reload brings it in, and the list itself then keeps
     * the key flagged after the user types away from it and back.
     */
    @Test
    fun `a 409 on a create reloads the list, which then holds the conflicting key`() = runTest {
        val tone = memory("preferences_tone")
        coEvery { memoryRepository.getMemories() } returnsMany listOf(page(), page(tone))
        coEvery { memoryRepository.createMemory(any()) } returns
            serverError(409, "Memory with this key already exists.")
        val viewModel = createViewModel()
        val events = collectEvents(viewModel)
        advanceUntilIdle()
        assertThat(viewModel.state.memories).isEmpty()
        viewModel.openCreate()
        viewModel.updateEditorKey("preferences_tone")
        viewModel.updateEditorValue("Dry")
        advanceUntilIdle()

        viewModel.saveEditor()
        advanceUntilIdle()

        coVerify(exactly = 2) { memoryRepository.getMemories() }
        assertThat(viewModel.state.memories.map { it.key }).containsExactly("preferences_tone")
        assertThat(viewModel.state.editor?.keyError).isEqualTo(MemoryKeyProblem.DUPLICATE)
        assertThat(events).isEmpty()

        viewModel.updateEditorKey("preferences_mood")
        advanceUntilIdle()
        viewModel.updateEditorKey("preferences_tone")
        advanceUntilIdle()
        assertThat(viewModel.state.editor?.keyError).isEqualTo(MemoryKeyProblem.DUPLICATE)
        assertThat(viewModel.state.editor?.canSave).isFalse()
    }

    @Test
    fun `an edit sends the trimmed value with the key, which the repository omits when unchanged`() = runTest {
        val tea = memory("likes_tea", value = "Earl Grey")
        coEvery { memoryRepository.getMemories() } returns page(tea)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.openMemory(tea)
        viewModel.updateEditorValue("Assam ")
        advanceUntilIdle()
        viewModel.saveEditor()
        advanceUntilIdle()

        coVerify { memoryRepository.updateMemory(tea, "Assam", "likes_tea") }
        assertThat(viewModel.state.editor).isNull()
    }

    @Test
    fun `a rename sends the new key`() = runTest {
        val tea = memory("likes_tea")
        coEvery { memoryRepository.getMemories() } returns page(tea)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.openMemory(tea)
        viewModel.updateEditorKey("favourite_tea")
        advanceUntilIdle()
        viewModel.saveEditor()
        advanceUntilIdle()

        coVerify { memoryRepository.updateMemory(tea, tea.value, "favourite_tea") }
    }

    @Test
    fun `a failed rename reloads the list as well as reporting in the sheet`() = runTest {
        val tea = memory("likes_tea")
        coEvery { memoryRepository.getMemories() } returns page(tea)
        coEvery { memoryRepository.updateMemory(any(), any(), any()) } returns
            serverError(500, "Failed to delete old memory.")
        val viewModel = createViewModel()
        val events = collectEvents(viewModel)
        advanceUntilIdle()

        viewModel.openMemory(tea)
        viewModel.updateEditorKey("favourite_tea")
        advanceUntilIdle()
        viewModel.saveEditor()
        advanceUntilIdle()

        coVerify(exactly = 2) { memoryRepository.getMemories() }
        assertThat(viewModel.state.editor?.saveFailure).isEqualTo(MemoryFailure.Generic)
        assertThat(events).isEmpty()
        assertThat(viewModel.state.editor?.key).isEqualTo("favourite_tea")
    }

    /**
     * Both rename routes answer 409 when the new key was taken after the list loaded (another
     * device, a chat's set_memory), which the local duplicate check can't see.
     */
    @Test
    fun `a 409 on a rename lands on the key field and reloads the list`() = runTest {
        val tea = memory("likes_tea")
        coEvery { memoryRepository.getMemories() } returns page(tea)
        coEvery { memoryRepository.updateMemory(any(), any(), any()) } returns
            serverError(409, "Memory with this key already exists.")
        val viewModel = createViewModel()
        val events = collectEvents(viewModel)
        advanceUntilIdle()

        viewModel.openMemory(tea)
        viewModel.updateEditorKey("favourite_tea")
        advanceUntilIdle()
        viewModel.saveEditor()
        advanceUntilIdle()

        val editor = viewModel.state.editor
        assertThat(editor).isNotNull()
        assertThat(editor?.keyError).isEqualTo(MemoryKeyProblem.DUPLICATE)
        assertThat(editor?.saveFailure).isNull()
        assertThat(editor?.isSaving).isFalse()
        assertThat(editor?.canSave).isFalse()
        assertThat(events).isEmpty()
        coVerify(exactly = 2) { memoryRepository.getMemories() }

        viewModel.updateEditorKey("best_tea")
        advanceUntilIdle()
        assertThat(viewModel.state.editor?.keyError).isNull()
        assertThat(viewModel.state.editor?.canSave).isTrue()
    }

    @Test
    fun `a failed value edit does not reload`() = runTest {
        val tea = memory("likes_tea")
        coEvery { memoryRepository.getMemories() } returns page(tea)
        coEvery { memoryRepository.updateMemory(any(), any(), any()) } returns Result.Error(kind = FailureKind.Network)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.openMemory(tea)
        viewModel.updateEditorValue("Assam")
        advanceUntilIdle()
        viewModel.saveEditor()
        advanceUntilIdle()

        coVerify(exactly = 1) { memoryRepository.getMemories() }
    }

    @Test
    fun `a rename is checked for duplicates in the memory's own partition only`() = runTest {
        val tea = memory("likes_tea", agentId = "agent_1")
        coEvery { memoryRepository.getMemories() } returns page(
            tea,
            memory("likes_coffee"),
            memory("likes_milk", agentId = "agent_1"),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.openMemory(tea)

        viewModel.updateEditorKey("likes_coffee")
        advanceUntilIdle()
        assertThat(viewModel.state.editor?.keyError).isNull()

        viewModel.updateEditorKey("likes_milk")
        advanceUntilIdle()
        assertThat(viewModel.state.editor?.keyError).isEqualTo(MemoryKeyProblem.DUPLICATE)
        assertThat(viewModel.state.editor?.canSave).isFalse()
    }

    @Test
    fun `the key pattern is enforced whatever the server version`() = runTest {
        // Every version's schema refuses this key (pre-rc3 as a bare 500), so no version gate.
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.openCreate()
        viewModel.updateEditorKey("Likes Tea")
        viewModel.updateEditorValue("v")
        advanceUntilIdle()
        assertThat(viewModel.state.editor?.keyError).isEqualTo(MemoryKeyProblem.PATTERN)

        viewModel.saveEditor()
        advanceUntilIdle()
        coVerify(exactly = 0) { memoryRepository.createMemory(any()) }
    }

    @Test
    fun `a rename to a key outside the pattern is refused, an unchanged legacy key is not`() = runTest {
        val legacy = memory("Work Notes")
        coEvery { memoryRepository.getMemories() } returns page(legacy)
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.openMemory(legacy)
        viewModel.updateEditorValue("edited")
        advanceUntilIdle()
        assertThat(viewModel.state.editor?.keyError).isNull()

        viewModel.updateEditorKey("Work Stuff")
        advanceUntilIdle()
        assertThat(viewModel.state.editor?.keyError).isEqualTo(MemoryKeyProblem.PATTERN)
        assertThat(viewModel.state.editor?.canSave).isFalse()
    }

    @Test
    fun `the reserved key is refused locally`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.openCreate()
        viewModel.updateEditorKey("nothing")
        viewModel.updateEditorValue("v")
        advanceUntilIdle()

        assertThat(viewModel.state.editor?.keyError).isEqualTo(MemoryKeyProblem.RESERVED)
        viewModel.saveEditor()
        advanceUntilIdle()
        coVerify(exactly = 0) { memoryRepository.createMemory(any()) }
    }

    @Test
    fun `a value over the server's char limit blocks save, counted trimmed`() = runTest {
        coEvery { memoryRepository.getMemories() } returns page(charLimit = 10)
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertThat(viewModel.state.charLimit).isEqualTo(10)
        viewModel.openCreate()
        viewModel.updateEditorKey("likes_tea")

        viewModel.updateEditorValue("   0123456789   ")
        advanceUntilIdle()
        assertThat(viewModel.state.editor?.valueLength).isEqualTo(10)
        assertThat(viewModel.state.editor?.valueError).isNull()

        viewModel.updateEditorValue("0123456789A")
        advanceUntilIdle()
        assertThat(viewModel.state.editor?.valueError).isEqualTo(MemoryValueProblem.TOO_LONG)
        assertThat(viewModel.state.editor?.canSave).isFalse()
    }

    @Test
    fun `the char limit defaults to ten thousand when the server omits it`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.charLimit).isEqualTo(DEFAULT_MEMORY_CHAR_LIMIT)
        viewModel.openCreate()
        viewModel.updateEditorKey("likes_tea")
        viewModel.updateEditorValue("a".repeat(DEFAULT_MEMORY_CHAR_LIMIT + 1))
        advanceUntilIdle()
        assertThat(viewModel.state.editor?.valueError).isEqualTo(MemoryValueProblem.TOO_LONG)
    }

    @Test
    fun `a blank value cannot be saved`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.openCreate()
        viewModel.updateEditorKey("likes_tea")
        viewModel.updateEditorValue("   ")
        advanceUntilIdle()

        assertThat(viewModel.state.editor?.valueError).isEqualTo(MemoryValueProblem.REQUIRED)
        assertThat(viewModel.state.editor?.canSave).isFalse()
    }

    /** The sheet covers the page's snackbar host, so a failure while it is open is said in the sheet. */
    @Test
    fun `a save that fails with the sheet open reports in the sheet, not in a snackbar`() = runTest {
        coEvery { memoryRepository.createMemory(any()) } returns Result.Error(kind = FailureKind.Network)
        val viewModel = createViewModel()
        val events = collectEvents(viewModel)
        advanceUntilIdle()
        viewModel.openCreate()
        viewModel.updateEditorKey("likes_tea")
        viewModel.updateEditorValue("Earl Grey")
        advanceUntilIdle()

        viewModel.saveEditor()
        advanceUntilIdle()

        assertThat(viewModel.state.editor?.saveFailure).isEqualTo(MemoryFailure.Network)
        assertThat(viewModel.state.editor?.isSaving).isFalse()
        assertThat(viewModel.state.editor?.canSave).isTrue()
        assertThat(events).isEmpty()

        viewModel.updateEditorValue("Earl Grey, no milk")
        advanceUntilIdle()
        assertThat(viewModel.state.editor?.saveFailure).isNull()
    }

    @Test
    fun `Save again after a failure re-sends the same request and clears the failure`() = runTest {
        val gate = CompletableDeferred<Result<Memory?>>()
        coEvery { memoryRepository.createMemory(any()) } returns Result.Error(kind = FailureKind.Network)
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.openCreate()
        viewModel.updateEditorKey("likes_tea")
        viewModel.updateEditorValue("Earl Grey")
        advanceUntilIdle()
        viewModel.saveEditor()
        advanceUntilIdle()
        assertThat(viewModel.state.editor?.saveFailure).isEqualTo(MemoryFailure.Network)

        coEvery { memoryRepository.createMemory(any()) } coAnswers { gate.await() }
        viewModel.saveEditor()
        advanceUntilIdle()
        assertThat(viewModel.state.editor?.saveFailure).isNull()

        gate.complete(Result.Success(null))
        advanceUntilIdle()
        coVerify(exactly = 2) { memoryRepository.createMemory(CreateMemoryRequest(key = "likes_tea", value = "Earl Grey")) }
        assertThat(viewModel.state.editor).isNull()
    }

    // ------------------------------------------------------------------------ dirty and discard

    @Test
    fun `an untouched sheet closes at once`() = runTest {
        val tea = memory("likes_tea")
        coEvery { memoryRepository.getMemories() } returns page(tea)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.openMemory(tea)
        advanceUntilIdle()
        assertThat(viewModel.state.editor?.isDirty).isFalse()
        viewModel.dismissEditor()
        advanceUntilIdle()

        assertThat(viewModel.state.editor).isNull()
    }

    @Test
    fun `a changed draft asks before it is discarded`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.openCreate()
        viewModel.updateEditorValue("half a thought")
        advanceUntilIdle()

        viewModel.dismissEditor()
        advanceUntilIdle()
        assertThat(viewModel.state.editor?.showDiscardConfirm).isTrue()

        viewModel.keepEditing()
        advanceUntilIdle()
        assertThat(viewModel.state.editor?.showDiscardConfirm).isFalse()
        assertThat(viewModel.state.editor?.value).isEqualTo("half a thought")

        viewModel.dismissEditor()
        viewModel.confirmDiscard()
        advanceUntilIdle()
        assertThat(viewModel.state.editor).isNull()
    }

    @Test
    fun `typing back the original value leaves the draft clean`() = runTest {
        val tea = memory("likes_tea", value = "Earl Grey")
        coEvery { memoryRepository.getMemories() } returns page(tea)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.openMemory(tea)
        viewModel.updateEditorValue("Assam")
        advanceUntilIdle()
        assertThat(viewModel.state.editor?.isDirty).isTrue()
        viewModel.updateEditorValue("Earl Grey")
        advanceUntilIdle()
        assertThat(viewModel.state.editor?.isDirty).isFalse()
    }

    /** The save's answer belongs to this sheet: closing it would let that answer land on the next one. */
    @Test
    fun `the sheet cannot be dismissed or discarded while its save is in flight`() = runTest {
        val gate = CompletableDeferred<Result<Memory?>>()
        coEvery { memoryRepository.createMemory(any()) } coAnswers { gate.await() }
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.openCreate()
        viewModel.updateEditorKey("likes_tea")
        viewModel.updateEditorValue("Earl Grey")
        advanceUntilIdle()
        viewModel.saveEditor()
        advanceUntilIdle()
        assertThat(viewModel.state.editor?.isSaving).isTrue()

        viewModel.dismissEditor()
        advanceUntilIdle()
        assertThat(viewModel.state.editor).isNotNull()
        assertThat(viewModel.state.editor?.showDiscardConfirm).isFalse()
        viewModel.confirmDiscard()
        advanceUntilIdle()
        assertThat(viewModel.state.editor).isNotNull()

        gate.complete(Result.Error(kind = FailureKind.Network))
        advanceUntilIdle()
        viewModel.dismissEditor()
        advanceUntilIdle()
        assertThat(viewModel.state.editor?.showDiscardConfirm).isTrue()
        viewModel.confirmDiscard()
        advanceUntilIdle()
        assertThat(viewModel.state.editor).isNull()
    }

    // ----------------------------------------------------------------------------------- delete

    @Test
    fun `delete reloads, and a second tap while it runs sends nothing`() = runTest {
        val tea = memory("likes_tea")
        coEvery { memoryRepository.getMemories() } returns page(tea)
        val gate = CompletableDeferred<Result<Unit>>()
        coEvery { memoryRepository.deleteMemory(any()) } coAnswers { gate.await() }
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.deleteMemory(tea)
        viewModel.deleteMemory(tea)
        advanceUntilIdle()
        assertThat(viewModel.state.deletingHandles).containsExactly(memoryRowHandle(tea))

        gate.complete(Result.Success(Unit))
        advanceUntilIdle()
        coVerify(exactly = 1) { memoryRepository.deleteMemory(tea) }
        coVerify(exactly = 2) { memoryRepository.getMemories() }
        assertThat(viewModel.state.deletingHandles).isEmpty()
    }

    @Test
    fun `Retry on a failed delete deletes the same memory`() = runTest {
        val tea = memory("likes_tea")
        coEvery { memoryRepository.getMemories() } returns page(tea)
        coEvery { memoryRepository.deleteMemory(any()) } returns serverError(503, "Server error. Please try again later.")
        val viewModel = createViewModel()
        val events = collectEvents(viewModel)
        advanceUntilIdle()

        viewModel.deleteMemory(tea)
        advanceUntilIdle()
        val event = events.single() as MemoriesEvent.Failed
        assertThat(event.operation).isEqualTo(MemoryOperation.Delete(tea))
        assertThat(event.canRetry).isTrue()

        viewModel.retry(checkNotNull(event.retryOperation))
        advanceUntilIdle()
        coVerify(exactly = 2) { memoryRepository.deleteMemory(tea) }
    }

    // ---------------------------------------------------------------------------- error mapping

    private suspend fun TestScope.failureOfDelete(error: Result.Error): MemoriesEvent.Failed {
        val tea = memory("likes_tea", agentId = "agent_1")
        coEvery { memoryRepository.getMemories() } returns page(tea)
        coEvery { memoryRepository.deleteMemory(any()) } returns error
        val viewModel = createViewModel()
        val events = collectEvents(viewModel)
        advanceUntilIdle()
        viewModel.deleteMemory(tea)
        advanceUntilIdle()
        return events.single() as MemoriesEvent.Failed
    }

    @Test
    fun `a content-filter refusal maps to its own string and offers no Retry`() = runTest {
        val event = failureOfDelete(
            serverError(
                400,
                "Submitted content contains a phone number. Remove it and try again.",
                body = """{"error":"content_filter_block","message":"Submitted content contains a phone number."}""",
            ),
        )
        assertThat(event.failure).isEqualTo(MemoryFailure.ContentFiltered)
        assertThat(event.canRetry).isFalse()
    }

    @Test
    fun `an agent-partition 404 is the agent's failure, not a missing memory`() = runTest {
        val event = failureOfDelete(serverError(404, "Agent not found.", body = """{"error":"Agent not found."}"""))
        assertThat(event.failure).isEqualTo(MemoryFailure.AgentUnavailable)
    }

    @Test
    fun `an agent-partition 403 is the agent's failure, not the role's`() = runTest {
        val event = failureOfDelete(
            serverError(403, "Agent access denied.", body = """{"error":"Agent access denied."}"""),
        )
        assertThat(event.failure).isEqualTo(MemoryFailure.AgentUnavailable)
    }

    @Test
    fun `a role 403 is Forbidden`() = runTest {
        val event = failureOfDelete(serverError(403, "Forbidden", body = """{"message":"Forbidden"}"""))
        assertThat(event.failure).isEqualTo(MemoryFailure.Forbidden)
        assertThat(event.canRetry).isFalse()
    }

    @Test
    fun `any other 400 shows the server's own sentence`() = runTest {
        val text = "Adding this memory would exceed the token limit of 4000. Current usage: 3990 tokens."
        val event = failureOfDelete(serverError(400, text, body = """{"error":"$text"}"""))
        assertThat(event.failure).isEqualTo(MemoryFailure.ServerMessage(text))
        assertThat(event.canRetry).isFalse()
    }

    @Test
    fun `a 400 whose text failed the display screen is generic`() = runTest {
        val event = failureOfDelete(serverError(400, "<html>gateway</html>", screened = false))
        assertThat(event.failure).isEqualTo(MemoryFailure.Generic)
    }

    /** Already gone is what the user asked for: no "couldn't delete", and no Retry that can't work. */
    @Test
    fun `a delete answered Memory not found is a delete, and reloads the stale list`() = runTest {
        val tea = memory("likes_tea")
        val coffee = memory("likes_coffee")
        coEvery { memoryRepository.getMemories() } returns page(tea, coffee)
        coEvery { memoryRepository.deleteMemory(any()) } returns
            serverError(404, "Memory not found.", body = """{"error":"Memory not found."}""")
        val viewModel = createViewModel()
        val events = collectEvents(viewModel)
        advanceUntilIdle()
        viewModel.openMemory(tea)

        // The reload fails, so only the VM's own removal can take the row off the page.
        coEvery { memoryRepository.getMemories() } returns Result.Error(kind = FailureKind.Network)
        viewModel.deleteMemory(tea)
        advanceUntilIdle()

        assertThat(events).isEmpty()
        assertThat(viewModel.state.memories.map { it.key }).containsExactly("likes_coffee")
        assertThat(viewModel.state.editor).isNull()
        coVerify(exactly = 2) { memoryRepository.getMemories() }
    }

    /** The memory is still there; its agent is what is missing, so the list is not stale. */
    @Test
    fun `an agent-partition 404 on a value edit does not reload`() = runTest {
        val tea = memory("likes_tea", agentId = "agent_1")
        coEvery { memoryRepository.getMemories() } returns page(tea)
        coEvery { memoryRepository.updateMemory(any(), any(), any()) } returns
            serverError(404, "Agent not found.", body = """{"error":"Agent not found."}""")
        val viewModel = createViewModel()
        val events = collectEvents(viewModel)
        advanceUntilIdle()

        viewModel.openMemory(tea)
        viewModel.updateEditorValue("Assam")
        viewModel.saveEditor()
        advanceUntilIdle()

        assertThat(viewModel.state.editor?.saveFailure).isEqualTo(MemoryFailure.AgentUnavailable)
        assertThat(events).isEmpty()
        coVerify(exactly = 1) { memoryRepository.getMemories() }
    }

    private val memoryNotFound get() = serverError(404, "Memory not found.", body = """{"error":"Memory not found."}""")

    /** Deleted elsewhere: the PATCH can only 404 again, so the draft becomes an add of the same text. */
    @Test
    fun `an edit whose memory is gone turns the sheet into an add of the same draft`() = runTest {
        val tea = memory("likes_tea", value = "Earl Grey")
        coEvery { memoryRepository.getMemories() } returns page(tea)
        coEvery { memoryRepository.updateMemory(any(), any(), any()) } returns memoryNotFound
        val viewModel = createViewModel()
        val events = collectEvents(viewModel)
        advanceUntilIdle()

        // The reload fails, so the row must go by the VM's own hand, or the add would read as a duplicate.
        coEvery { memoryRepository.getMemories() } returns Result.Error(kind = FailureKind.Network)
        viewModel.openMemory(tea)
        viewModel.updateEditorValue("Assam")
        viewModel.saveEditor()
        advanceUntilIdle()

        val editor = viewModel.state.editor
        assertThat(editor?.mode).isEqualTo(MemoryEditorMode.Create)
        assertThat(editor?.original).isNull()
        assertThat(editor?.key).isEqualTo("likes_tea")
        assertThat(editor?.value).isEqualTo("Assam")
        assertThat(editor?.saveFailure).isEqualTo(MemoryFailure.NotFound)
        assertThat(editor?.keyError).isNull()
        assertThat(editor?.canSave).isTrue()
        assertThat(viewModel.state.memories).isEmpty()
        assertThat(events).isEmpty()
        coVerify(exactly = 2) { memoryRepository.getMemories() }

        viewModel.saveEditor()
        advanceUntilIdle()
        coVerify(exactly = 1) { memoryRepository.updateMemory(any(), any(), any()) }
        coVerify(exactly = 1) { memoryRepository.createMemory(CreateMemoryRequest(key = "likes_tea", value = "Assam")) }
        assertThat(viewModel.state.editor).isNull()
    }

    /** A create is always personal, so an agent's memory can't be re-added from its sheet. */
    @Test
    fun `an agent memory's edit whose memory is gone closes the sheet, with no Retry`() = runTest {
        val notes = memory("bot_notes", agentId = "agent_1", agentName = "Helper")
        coEvery { memoryRepository.getMemories() } returns page(notes)
        coEvery { memoryRepository.updateMemory(any(), any(), any()) } returns memoryNotFound
        val viewModel = createViewModel()
        val events = collectEvents(viewModel)
        advanceUntilIdle()

        viewModel.openMemory(notes)
        viewModel.updateEditorValue("Prefers tables")
        viewModel.saveEditor()
        advanceUntilIdle()

        assertThat(viewModel.state.editor).isNull()
        val event = events.single() as MemoriesEvent.Failed
        assertThat(event.failure).isEqualTo(MemoryFailure.NotFound)
        assertThat(event.canRetry).isFalse()
        coVerify(exactly = 0) { memoryRepository.createMemory(any()) }
    }

    @Test
    fun `an edit whose memory is gone closes the sheet when the role cannot create`() = runTest {
        role.value = memoriesRole("USE" to true, "UPDATE" to true, "CREATE" to false)
        val tea = memory("likes_tea")
        coEvery { memoryRepository.getMemories() } returns page(tea)
        coEvery { memoryRepository.updateMemory(any(), any(), any()) } returns memoryNotFound
        val viewModel = createViewModel()
        val events = collectEvents(viewModel)
        advanceUntilIdle()

        viewModel.openMemory(tea)
        viewModel.updateEditorValue("Assam")
        viewModel.saveEditor()
        advanceUntilIdle()

        assertThat(viewModel.state.editor).isNull()
        assertThat((events.single() as MemoriesEvent.Failed).canRetry).isFalse()
    }

    @Test
    fun `an unreachable server is the network failure, with Retry`() = runTest {
        val event = failureOfDelete(Result.Error(kind = FailureKind.Network))
        assertThat(event.failure).isEqualTo(MemoryFailure.Network)
        assertThat(event.canRetry).isTrue()
    }
}
