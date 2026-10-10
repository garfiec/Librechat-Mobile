package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.AuthRepository
import com.garfiec.librechat.core.data.repository.BalanceRepository
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.UserRepository
import com.garfiec.librechat.core.model.Balance
import com.garfiec.librechat.core.model.User
import com.garfiec.librechat.core.model.config.BalanceConfig
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.feature.settings.util.ContentReader
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountSettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val contentReader = mockk<ContentReader>(relaxed = true)
    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val authRepository = mockk<AuthRepository>(relaxed = true)
    private val balanceRepository = mockk<BalanceRepository>(relaxed = true)
    private val configRepository = mockk<ConfigRepository>(relaxed = true)
    private val startupConfig = MutableStateFlow<StartupConfig?>(null)

    private val testUser = User(
        email = "test@example.com",
        name = "Test User",
        username = "testuser",
        avatar = "https://example.com/avatar.png",
        twoFactorEnabled = false,
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        coEvery { userRepository.getUser() } returns Result.Success(testUser)
        coEvery { balanceRepository.getBalance() } returns Result.Error(message = "Not available")
        every { configRepository.startupConfig } returns startupConfig
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = AccountSettingsViewModel(
        userRepository = userRepository,
        authRepository = authRepository,
        balanceRepository = balanceRepository,
        contentReader = contentReader,
        configRepository = configRepository,
        ioDispatcher = testDispatcher,
    )

    @Test
    fun `initial state loads user profile`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.user).isNotNull()
        assertThat(state.user?.name).isEqualTo("Test User")
        assertThat(state.user?.email).isEqualTo("test@example.com")
        assertThat(state.profileLoadError).isNull()
    }

    @Test
    fun `user load failure routes to profileLoadError, not snackbar error`() = runTest {
        coEvery { userRepository.getUser() } returns Result.Error(message = "Network error")

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.user).isNull()
        assertThat(state.profileLoadError).isEqualTo("Network error")
        assertThat(state.error).isNull()
    }

    @Test
    fun `retry after profile load failure clears profileLoadError on success`() = runTest {
        coEvery { userRepository.getUser() } returns Result.Error(message = "Network error")
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.profileLoadError).isEqualTo("Network error")

        coEvery { userRepository.getUser() } returns Result.Success(testUser)
        viewModel.retry()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.profileLoadError).isNull()
        assertThat(state.user).isNotNull()
    }

    @Test
    fun `retry cancels in-flight load so its stale error cannot overwrite the new result`() = runTest {
        // First call hangs (simulates a 90s unreachable-server timeout) then resolves to an error.
        // Production correctness requires UserRepositoryImpl to wrap the call in safeApiCall so
        // CancellationException propagates; this test exercises the ViewModel-level guard given
        // that wrapping. A regression in UserRepositoryImpl bypassing safeApiCall would not be
        // caught here — covered separately by SafeApiCallTest.safeApiCallPropagatesCancellation.
        coEvery { userRepository.getUser() } coAnswers {
            delay(60_000)
            Result.Error(message = "stale cancelled error")
        }
        val viewModel = createViewModel()
        advanceTimeBy(100) // let init { loadUser() } start the request and suspend inside delay()

        // Second call returns success immediately.
        coEvery { userRepository.getUser() } returns Result.Success(testUser)
        viewModel.retry()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.user?.name).isEqualTo("Test User")
        assertThat(state.profileLoadError).isNull()
    }

    @Test
    fun `logout sets isLoggedOut flag without calling authRepository`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.logout()
        advanceUntilIdle()

        coVerify(exactly = 0) { authRepository.logout() }
        assertThat(viewModel.uiState.value.isLoggedOut).isTrue()
    }

    @Test
    fun `deleteAccount calls userRepository and sets isAccountDeleted without calling authRepository`() = runTest {
        coEvery { userRepository.deleteUser() } returns Result.Success(Unit)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.deleteAccount()
        advanceUntilIdle()

        coVerify { userRepository.deleteUser() }
        coVerify(exactly = 0) { authRepository.logout() }
        assertThat(viewModel.uiState.value.isAccountDeleted).isTrue()
    }

    @Test
    fun `deleteAccount failure shows error`() = runTest {
        coEvery { userRepository.deleteUser() } returns Result.Error(message = "Server error")

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.deleteAccount()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.error).isEqualTo("Server error")
        assertThat(viewModel.uiState.value.isAccountDeleted).isFalse()
    }

    @Test
    fun `twoFactorEnabled state reflects user profile`() = runTest {
        val userWith2FA = testUser.copy(twoFactorEnabled = true)
        coEvery { userRepository.getUser() } returns Result.Success(userWith2FA)

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isTwoFactorEnabled).isTrue()
    }

    @Test
    fun `retry reloads user profile`() = runTest {
        coEvery { userRepository.getUser() } returns Result.Success(testUser)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.retry()
        advanceUntilIdle()

        // Called twice: once in init, once in retry
        coVerify(exactly = 2) { userRepository.getUser() }
    }

    @Test
    fun `account deletion stays offered until the server turns it off`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.allowAccountDeletion).isTrue()

        startupConfig.value = StartupConfig(allowAccountDeletion = false)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.allowAccountDeletion).isFalse()
    }

    @Test
    fun `balance is neither fetched nor shown while the server has it off`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.balanceEnabled).isFalse()
        coVerify(exactly = 0) { balanceRepository.getBalance() }
    }

    @Test
    fun `balance is fetched once the config turns it on`() = runTest {
        coEvery { balanceRepository.getBalance() } returns Result.Success(Balance(tokenCredits = 1234))
        val viewModel = createViewModel()
        advanceUntilIdle()

        startupConfig.value = StartupConfig(balance = BalanceConfig(enabled = true))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.balanceEnabled).isTrue()
        assertThat(viewModel.uiState.value.tokenCredits).isEqualTo(1234)
        coVerify(exactly = 1) { balanceRepository.getBalance() }
    }
}
