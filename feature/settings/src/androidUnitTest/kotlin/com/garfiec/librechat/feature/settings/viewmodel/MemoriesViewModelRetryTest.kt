package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.common.result.FailureKind
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.response.MemoriesResponse
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * A snackbar's Retry re-runs its action under the action's own gates; a refresh's failure is
 * reported even when a quieter reload replaced it; a hidden search keeps no query.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MemoriesViewModelRetryTest : MemoriesViewModelTestBase() {

    // ------------------------------------------------------------------------------- refresh

    @Test
    fun `Retry on a failed refresh reloads the list`() = runTest {
        val tea = memory("likes_tea")
        val coffee = memory("likes_coffee")
        coEvery { memoryRepository.getMemories() } returns page(tea)
        val viewModel = createViewModel()
        val events = collectEvents(viewModel)
        advanceUntilIdle()

        coEvery { memoryRepository.getMemories() } returns Result.Error(kind = FailureKind.Timeout)
        viewModel.refresh()
        advanceUntilIdle()
        val event = events.single() as MemoriesEvent.Failed
        assertThat(event.retryOperation).isEqualTo(MemoryOperation.Refresh)

        coEvery { memoryRepository.getMemories() } returns page(tea, coffee)
        viewModel.retry(checkNotNull(event.retryOperation))
        advanceUntilIdle()

        assertThat(viewModel.state.memories).hasSize(2)
        assertThat(viewModel.state.isRefreshing).isFalse()
        coVerify(exactly = 3) { memoryRepository.getMemories() }
    }

    /**
     * A delete lands while the pull-to-refresh is still loading, and its reload takes the refresh's
     * place. When that reload fails, the user who pulled must still hear that it did.
     */
    @Test
    fun `a pull-to-refresh replaced by a write's reload still reports its failure`() = runTest {
        val tea = memory("likes_tea")
        val coffee = memory("likes_coffee")
        coEvery { memoryRepository.getMemories() } returns page(tea, coffee)
        val viewModel = createViewModel()
        val events = collectEvents(viewModel)
        advanceUntilIdle()

        val refresh = CompletableDeferred<Result<MemoriesResponse>>()
        coEvery { memoryRepository.getMemories() } coAnswers { refresh.await() }
        viewModel.refresh()
        advanceUntilIdle()

        coEvery { memoryRepository.getMemories() } returns Result.Error(kind = FailureKind.Network)
        viewModel.deleteMemory(tea)
        advanceUntilIdle()

        val event = events.single() as MemoriesEvent.Failed
        assertThat(event.operation).isEqualTo(MemoryOperation.Refresh)
        assertThat(event.failure).isEqualTo(MemoryFailure.Network)
        assertThat(viewModel.state.isRefreshing).isFalse()
    }

    /** Only a replaced refresh carries the report; a write's own reload stays quiet afterwards. */
    @Test
    fun `a write's reload after a finished refresh stays quiet`() = runTest {
        val tea = memory("likes_tea")
        coEvery { memoryRepository.getMemories() } returns page(tea, memory("likes_coffee"))
        val viewModel = createViewModel()
        val events = collectEvents(viewModel)
        advanceUntilIdle()
        viewModel.refresh()
        advanceUntilIdle()

        coEvery { memoryRepository.getMemories() } returns Result.Error(kind = FailureKind.Network)
        viewModel.deleteMemory(tea)
        advanceUntilIdle()

        assertThat(events).isEmpty()
    }

    // -------------------------------------------------------------------------------- toggle

    private fun TestScope.failedToggle(viewModel: MemoriesViewModel): MemoriesEvent.Failed {
        val events = collectEvents(viewModel)
        advanceUntilIdle()
        coEvery { memoryRepository.updatePreferences(any()) } returns Result.Error(kind = FailureKind.Network)
        viewModel.setMemoriesEnabled(false)
        advanceUntilIdle()
        return events.single() as MemoriesEvent.Failed
    }

    /** The value changed elsewhere meanwhile (another device, the web), to what Retry would send. */
    @Test
    fun `Retry on a failed toggle sends nothing once the shared value already matches`() = runTest {
        val viewModel = createViewModel()
        val event = failedToggle(viewModel)

        memoriesEnabled.value = false
        advanceUntilIdle()
        viewModel.retry(checkNotNull(event.retryOperation))
        advanceUntilIdle()

        coVerify(exactly = 1) { memoryRepository.updatePreferences(any()) }
    }

    @Test
    fun `Retry on a failed toggle sends nothing once the role lost OPT_OUT`() = runTest {
        val viewModel = createViewModel()
        val event = failedToggle(viewModel)

        role.value = memoriesRole("USE" to true, "READ" to true, "OPT_OUT" to false)
        advanceUntilIdle()
        viewModel.retry(checkNotNull(event.retryOperation))
        advanceUntilIdle()

        coVerify(exactly = 1) { memoryRepository.updatePreferences(any()) }
    }

    /** Unknown is what an account switch leaves: the retry would land on the incoming account. */
    @Test
    fun `Retry on a failed toggle sends nothing while the value is unknown`() = runTest {
        val viewModel = createViewModel()
        val event = failedToggle(viewModel)

        memoriesEnabled.value = null
        advanceUntilIdle()
        viewModel.retry(checkNotNull(event.retryOperation))
        advanceUntilIdle()

        coVerify(exactly = 1) { memoryRepository.updatePreferences(any()) }
    }

    // -------------------------------------------------------------------------------- delete

    private fun TestScope.failedDelete(viewModel: MemoriesViewModel): MemoriesEvent.Failed {
        val tea = memory("likes_tea")
        coEvery { memoryRepository.getMemories() } returns page(tea)
        coEvery { memoryRepository.deleteMemory(any()) } returns Result.Error(kind = FailureKind.Network)
        val events = collectEvents(viewModel)
        advanceUntilIdle()
        viewModel.deleteMemory(tea)
        advanceUntilIdle()
        return events.single() as MemoriesEvent.Failed
    }

    @Test
    fun `Retry on a failed delete sends nothing once the role lost UPDATE`() = runTest {
        coEvery { memoryRepository.getMemories() } returns page(memory("likes_tea"))
        val viewModel = createViewModel()
        val event = failedDelete(viewModel)

        role.value = memoriesRole("USE" to true, "READ" to true, "UPDATE" to false)
        advanceUntilIdle()
        viewModel.retry(checkNotNull(event.retryOperation))
        advanceUntilIdle()

        coVerify(exactly = 1) { memoryRepository.deleteMemory(any()) }
    }

    @Test
    fun `Retry on a failed delete sends nothing once the page has no access`() = runTest {
        coEvery { memoryRepository.getMemories() } returns page(memory("likes_tea"))
        val viewModel = createViewModel()
        val event = failedDelete(viewModel)

        role.value = memoriesRole("USE" to false, "READ" to true, "UPDATE" to true)
        advanceUntilIdle()
        assertThat(viewModel.state.noAccess).isTrue()
        viewModel.retry(checkNotNull(event.retryOperation))
        advanceUntilIdle()

        coVerify(exactly = 1) { memoryRepository.deleteMemory(any()) }
    }

    // -------------------------------------------------------------------------------- search

    /**
     * Below the threshold the field is gone, so its text must go too: kept, it would come back
     * and filter the list unasked once a fifth memory arrived.
     */
    @Test
    fun `a search hidden by a shrinking list is cleared, and does not filter when it shows again`() = runTest {
        val five = listOf("a_tea", "b_tea", "c_tea", "d_coffee", "e_coffee").map { memory(it) }
        coEvery { memoryRepository.getMemories() } returns page(*five.toTypedArray())
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.updateQuery("tea")
        advanceUntilIdle()
        assertThat(viewModel.state.visibleMemories).hasSize(3)

        coEvery { memoryRepository.getMemories() } returns page(*five.drop(1).toTypedArray())
        viewModel.deleteMemory(five.first())
        advanceUntilIdle()
        assertThat(viewModel.state.showSearch).isFalse()
        assertThat(viewModel.state.query).isEmpty()

        coEvery { memoryRepository.getMemories() } returns page(*five.toTypedArray())
        viewModel.refresh()
        advanceUntilIdle()
        assertThat(viewModel.state.showSearch).isTrue()
        assertThat(viewModel.state.query).isEmpty()
        assertThat(viewModel.state.visibleMemories).hasSize(5)
    }
}
