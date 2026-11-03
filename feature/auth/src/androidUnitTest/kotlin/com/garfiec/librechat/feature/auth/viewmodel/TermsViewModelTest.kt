package com.garfiec.librechat.feature.auth.viewmodel

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.UserRepository
import com.garfiec.librechat.core.model.config.InterfaceConfig
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.model.config.TermsOfServiceConfig
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
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

/** The Terms screen shows the startup config's `modalTitle` / `modalContent`, as web's modal does. */
@OptIn(ExperimentalCoroutinesApi::class)
class TermsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val configFlow = MutableStateFlow<StartupConfig?>(null)
    private val configRepository = mockk<ConfigRepository> {
        every { startupConfig } returns configFlow
    }

    @Before
    fun setup() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun config(title: String? = null, content: String? = null) = StartupConfig(
        interfaceConfig = InterfaceConfig(
            termsOfService = TermsOfServiceConfig(modalAcceptance = true, modalTitle = title, modalContent = content),
        ),
    )

    @Test
    fun `reads the terms from the loaded startup config without fetching`() = runTest {
        configFlow.value = config(title = "Sync089 Terms", content = "You agree to **this**.")
        val viewModel = TermsViewModel(userRepository, configRepository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.termsLoaded).isTrue()
        assertThat(state.title).isEqualTo("Sync089 Terms")
        assertThat(state.termsText).isEqualTo("You agree to **this**.")
        coVerify(exactly = 0) { configRepository.fetchStartupConfig() }
        coVerify(exactly = 0) { userRepository.getTerms() }
    }

    @Test
    fun `a blank title falls back to the app's and absent content stays blank`() = runTest {
        configFlow.value = config(title = " ")
        val viewModel = TermsViewModel(userRepository, configRepository)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.title).isNull()
        assertThat(viewModel.uiState.value.termsText).isEmpty()
        assertThat(viewModel.uiState.value.termsLoaded).isTrue()
    }

    @Test
    fun `fetches the config when none is loaded and takes the terms from it`() = runTest {
        coEvery { configRepository.fetchStartupConfig() } coAnswers {
            config(content = "Body").also { configFlow.value = it }.let { Result.Success(it) }
        }
        val viewModel = TermsViewModel(userRepository, configRepository)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.termsText).isEqualTo("Body")
        assertThat(viewModel.uiState.value.error).isNull()
    }

    @Test
    fun `a failed fetch with no config shows the load error, and a retry clears it`() = runTest {
        coEvery { configRepository.fetchStartupConfig() } returns Result.Error(message = "offline")
        val viewModel = TermsViewModel(userRepository, configRepository)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.termsLoaded).isFalse()
        assertThat(viewModel.uiState.value.error).isNotNull()

        coEvery { configRepository.fetchStartupConfig() } coAnswers {
            config(content = "Body").also { configFlow.value = it }.let { Result.Success(it) }
        }
        viewModel.loadTerms()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.error).isNull()
        assertThat(viewModel.uiState.value.termsText).isEqualTo("Body")
    }
}
