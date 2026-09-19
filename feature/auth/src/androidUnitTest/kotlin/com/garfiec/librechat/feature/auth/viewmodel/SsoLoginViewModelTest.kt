package com.garfiec.librechat.feature.auth.viewmodel

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.ServerDataStore
import com.garfiec.librechat.core.data.repository.AccountSwitcher
import com.garfiec.librechat.core.data.repository.AuthRepository
import com.garfiec.librechat.core.data.repository.PendingAddSession
import com.garfiec.librechat.core.model.User
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SsoLoginViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val authRepository = mockk<AuthRepository>(relaxed = true)
    private val serverDataStore = mockk<ServerDataStore>(relaxed = true)
    private val accountSwitcher = mockk<AccountSwitcher>(relaxed = true)

    private val user = mockk<User>(relaxed = true)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { serverDataStore.getBaseUrl() } returns LIVE_SERVER
        every { accountSwitcher.pendingAdd } returns null
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun createViewModel() = SsoLoginViewModel(
        authRepository = authRepository,
        serverDataStore = serverDataStore,
        accountSwitcher = accountSwitcher,
    )

    @Test
    fun `serverUrl is the live server when no add is pending`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.serverUrl).isEqualTo(LIVE_SERVER)
    }

    @Test
    fun `serverUrl is the pending add target when an add is in progress`() = runTest {
        val pending = mockk<PendingAddSession>(relaxed = true)
        every { pending.serverUrl } returns ADD_SERVER
        every { accountSwitcher.pendingAdd } returns pending

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.serverUrl).isEqualTo(ADD_SERVER)
        verify(exactly = 0) { serverDataStore.getBaseUrl() }
    }

    @Test
    fun `a captured token signs in`() = runTest {
        coEvery { authRepository.loginWithOAuthToken(TOKEN) } returns Result.Success(user)
        val viewModel = createViewModel()

        viewModel.onTokenCapture(TOKEN)
        advanceUntilIdle()

        coVerify(exactly = 1) { authRepository.loginWithOAuthToken(TOKEN) }
        with(viewModel.uiState.value) {
            assertThat(isLoggedIn).isTrue()
            assertThat(isLoading).isFalse()
            assertThat(error).isNull()
        }
    }

    @Test
    fun `a second capture is ignored`() = runTest {
        coEvery { authRepository.loginWithOAuthToken(any()) } returns Result.Success(user)
        val viewModel = createViewModel()

        viewModel.onTokenCapture(TOKEN)
        viewModel.onTokenCapture("a-different-token")
        advanceUntilIdle()

        coVerify(exactly = 1) { authRepository.loginWithOAuthToken(any()) }
    }

    @Test
    fun `a rejected token surfaces the server's message`() = runTest {
        coEvery { authRepository.loginWithOAuthToken(TOKEN) } returns
            Result.Error(message = "Refresh token expired")
        val viewModel = createViewModel()

        viewModel.onTokenCapture(TOKEN)
        advanceUntilIdle()

        with(viewModel.uiState.value) {
            assertThat(error).isEqualTo(SsoLoginError.Exchange("Refresh token expired"))
            assertThat(isLoggedIn).isFalse()
            assertThat(isLoading).isFalse()
        }
    }

    @Test
    fun `a rejected token with no message leaves the wording to the screen`() = runTest {
        coEvery { authRepository.loginWithOAuthToken(TOKEN) } returns Result.Error(message = null)
        val viewModel = createViewModel()

        viewModel.onTokenCapture(TOKEN)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.error).isEqualTo(SsoLoginError.Exchange(null))
    }

    @Test
    fun `an OAuth error fails without calling the repository`() = runTest {
        val viewModel = createViewModel()

        viewModel.onOAuthError("AUTH_FAILED")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.error).isEqualTo(SsoLoginError.Provider("AUTH_FAILED"))
        coVerify(exactly = 0) { authRepository.loginWithOAuthToken(any()) }
    }

    @Test
    fun `an OAuth error with no code still fails`() = runTest {
        val viewModel = createViewModel()

        viewModel.onOAuthError(null)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.error).isEqualTo(SsoLoginError.Provider(null))
    }

    @Test
    fun `a capture failure fails without a code`() = runTest {
        val viewModel = createViewModel()

        viewModel.onCaptureFailed()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.error).isEqualTo(SsoLoginError.CaptureFailed)
        coVerify(exactly = 0) { authRepository.loginWithOAuthToken(any()) }
    }

    @Test
    fun `a late error does not clobber a completed sign-in`() = runTest {
        coEvery { authRepository.loginWithOAuthToken(TOKEN) } returns Result.Success(user)
        val viewModel = createViewModel()

        viewModel.onTokenCapture(TOKEN)
        advanceUntilIdle()
        viewModel.onOAuthError("LATE")
        viewModel.onCaptureFailed()
        advanceUntilIdle()

        with(viewModel.uiState.value) {
            assertThat(isLoggedIn).isTrue()
            assertThat(error).isNull()
        }
    }

    private companion object {
        const val LIVE_SERVER = "https://chat.example.com"
        const val ADD_SERVER = "https://other.example.com"
        const val TOKEN = "refresh-token-value"
    }
}
