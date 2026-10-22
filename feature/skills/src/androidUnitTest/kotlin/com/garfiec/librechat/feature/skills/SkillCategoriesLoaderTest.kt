package com.garfiec.librechat.feature.skills

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.model.response.Category
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SkillCategoriesLoaderTest {

    private val configRepository = mockk<ConfigRepository>()
    private val presets = listOf(Category(value = "finance", label = "Finance"))

    @Test
    fun `presets are fetched once however often the screen asks`() = runTest {
        coEvery { configRepository.getCategories() } returns Result.Success(presets)
        val delivered = mutableListOf<List<Category>>()
        val loader = SkillCategoriesLoader(configRepository, this) { delivered += it }

        loader.load()
        advanceUntilIdle()
        loader.load()
        advanceUntilIdle()

        assertThat(delivered).containsExactly(presets)
        coVerify(exactly = 1) { configRepository.getCategories() }
    }

    @Test
    fun `a load while one is in flight doesn't send a second request`() = runTest {
        val pending = CompletableDeferred<Result<List<Category>>>()
        coEvery { configRepository.getCategories() } coAnswers { pending.await() }
        val loader = SkillCategoriesLoader(configRepository, this) {}

        loader.load()
        advanceUntilIdle()
        loader.load()
        pending.complete(Result.Success(presets))
        advanceUntilIdle()

        coVerify(exactly = 1) { configRepository.getCategories() }
    }

    @Test
    fun `a failure delivers nothing and the next load asks again`() = runTest {
        coEvery { configRepository.getCategories() } returns Result.Error(message = "boom") andThen Result.Success(presets)
        val delivered = mutableListOf<List<Category>>()
        val loader = SkillCategoriesLoader(configRepository, this) { delivered += it }

        loader.load()
        advanceUntilIdle()
        assertThat(delivered).isEmpty()

        loader.load()
        advanceUntilIdle()
        assertThat(delivered).containsExactly(presets)
    }
}
