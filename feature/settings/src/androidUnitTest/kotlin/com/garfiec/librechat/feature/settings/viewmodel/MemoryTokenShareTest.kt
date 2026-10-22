package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.Memory
import com.garfiec.librechat.core.model.response.MemoriesResponse
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** The memory sheet's "x tokens · y% of your memory": web's formula, shown only when it means something. */
@OptIn(ExperimentalCoroutinesApi::class)
class MemoryTokenShareTest : MemoriesViewModelTestBase() {

    private val personal = Memory(id = "m1", key = "diet", value = "Vegetarian", tokenCount = 50)
    private val envelope = MemoriesResponse(memories = listOf(personal), totalTokens = 1_050, tokenLimit = 2_000)

    @Test
    fun `the share is the entry's tokens over the room the pool leaves it`() {
        // 50 / (2000 - 1050 + 50) = 5%
        assertThat(memoryTokenShare(personal, envelope)).isEqualTo(MemoryTokenShare(tokens = 50, percent = 5))
    }

    @Test
    fun `the percentage rounds half up, as web's Math round`() {
        val memory = personal.copy(tokenCount = 1)
        // 1 / (200 - 1 + 1) = 0.5%.
        val response = MemoriesResponse(totalTokens = 1, tokenLimit = 200)
        assertThat(memoryTokenShare(memory, response)?.percent).isEqualTo(1)
    }

    @Test
    fun `an agent's entry has no share, since the totals count the personal pool only`() {
        assertThat(memoryTokenShare(personal.copy(agentId = "agent_a"), envelope)).isNull()
    }

    @Test
    fun `no share without a token count, or with a zero one`() {
        assertThat(memoryTokenShare(personal.copy(tokenCount = null), envelope)).isNull()
        assertThat(memoryTokenShare(personal.copy(tokenCount = 0), envelope)).isNull()
    }

    @Test
    fun `no share without a token limit`() {
        assertThat(memoryTokenShare(personal, envelope.copy(tokenLimit = null))).isNull()
        assertThat(memoryTokenShare(personal, null)).isNull()
    }

    @Test
    fun `no share once the pool is over its limit far enough to leave no room`() {
        // Edits don't enforce the limit: 2000 - 2100 + 50 = -50.
        assertThat(memoryTokenShare(personal, envelope.copy(totalTokens = 2_100))).isNull()
        // Exactly zero room.
        assertThat(memoryTokenShare(personal, envelope.copy(totalTokens = 2_050))).isNull()
    }

    @Test
    fun `the open sheet carries the share, and a new memory has none`() = runTest {
        coEvery { memoryRepository.getMemories() } returns Result.Success(envelope)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.openMemory(personal)
        advanceUntilIdle()
        assertThat(viewModel.state.editor?.tokenShare).isEqualTo(MemoryTokenShare(tokens = 50, percent = 5))

        viewModel.dismissEditor()
        viewModel.openCreate()
        advanceUntilIdle()
        assertThat(viewModel.state.editor?.tokenShare).isNull()
    }
}
