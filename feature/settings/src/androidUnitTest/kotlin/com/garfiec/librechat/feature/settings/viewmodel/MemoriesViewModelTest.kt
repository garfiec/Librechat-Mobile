package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.MemoryRepository
import com.garfiec.librechat.core.data.repository.UserRepository
import com.garfiec.librechat.core.model.User
import com.garfiec.librechat.core.model.UserPersonalization
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MemoriesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val memoryRepository = mockk<MemoryRepository>(relaxed = true)
    private val configRepository = mockk<ConfigRepository>(relaxed = true)
    private val userRepository = mockk<UserRepository>(relaxed = true)

    private val testUser = User(email = "test@example.com")

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { configRepository.detectedBackendVersion } returns MutableStateFlow(null)
        coEvery { memoryRepository.getMemories() } returns Result.Success(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = MemoriesViewModel(memoryRepository, configRepository, userRepository)

    @Test
    fun `memoriesEnabled reflects a saved opt-out after restart`() = runTest {
        coEvery { userRepository.getUser() } returns Result.Success(
            testUser.copy(personalization = UserPersonalization(memories = false)),
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.memoriesEnabled).isFalse()
    }

    @Test
    fun `memoriesEnabled defaults on when the profile has no personalization block`() = runTest {
        coEvery { userRepository.getUser() } returns Result.Success(testUser)

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.memoriesEnabled).isTrue()
    }

    @Test
    fun `memoriesEnabled stays at the default when the profile cannot be loaded`() = runTest {
        coEvery { userRepository.getUser() } returns Result.Error(message = "Network error")

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.memoriesEnabled).isTrue()
    }
}
