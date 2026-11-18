package com.garfiec.librechat.feature.auth.viewmodel

import com.garfiec.librechat.core.common.BackendBuildClass
import com.garfiec.librechat.core.common.DetectedBackend
import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.AuthRepository
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.MissingRefreshCookieException
import com.garfiec.librechat.core.model.User
import com.garfiec.librechat.core.model.response.TwoFactorSetupConfirmResponse
import com.garfiec.librechat.core.model.response.TwoFactorSetupResponse
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

@OptIn(ExperimentalCoroutinesApi::class)
class TwoFactorSetupViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val authRepository = mockk<AuthRepository>(relaxed = true)
    private val detectedBackend =
        MutableStateFlow<DetectedBackend?>(DetectedBackend("0.8.9", BackendBuildClass.OFFICIAL))
    private val configRepository = mockk<ConfigRepository> {
        every { detectedBackend } returns this@TwoFactorSetupViewModelTest.detectedBackend
    }

    @Before
    fun setup() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun createViewModel() = TwoFactorSetupViewModel(authRepository, configRepository, tempToken = TEMP)

    private fun apiError(status: Int) = Result.Error(exception = ApiException(status, "x"), message = "x")

    private fun stubHappyPath() {
        coEvery { authRepository.startTwoFactorSetup(TEMP) } returns Result.Success(
            TwoFactorSetupResponse(otpauthUrl = OTPAUTH, backupCodes = listOf("staged")),
        )
        coEvery { authRepository.confirmTwoFactorSetup(TEMP, "123456") } returns Result.Success(
            TwoFactorSetupConfirmResponse(backupCodes = CODES, acknowledgementToken = "ack"),
        )
        coEvery { authRepository.acknowledgeTwoFactorSetup("ack") } returns Result.Success("fin")
        coEvery { authRepository.finalizeTwoFactorSetup("fin") } returns Result.Success(User(email = "a@b.c"))
    }

    private suspend fun kotlinx.coroutines.test.TestScope.toBackupCodes(viewModel: TwoFactorSetupViewModel) {
        viewModel.start()
        advanceUntilIdle()
        viewModel.continueToVerify()
        viewModel.onCodeChanged("123456")
        advanceUntilIdle()
    }

    @Test
    fun `the four steps run in order and finish signed in`() = runTest {
        stubHappyPath()
        val viewModel = createViewModel()

        viewModel.start()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.phase).isEqualTo(TwoFactorSetupPhase.Authenticator)
        assertThat(viewModel.uiState.value.setupKey).isEqualTo("JBSWY3DPEHPK3PXP")

        viewModel.continueToVerify()
        viewModel.onCodeChanged("123456")
        advanceUntilIdle()
        val backup = viewModel.uiState.value
        assertThat(backup.phase).isEqualTo(TwoFactorSetupPhase.BackupCodes)
        // The confirmed codes, not the ones staged by the first step.
        assertThat(backup.backupCodes).isEqualTo(CODES)

        // Not before the codes are saved.
        viewModel.complete()
        advanceUntilIdle()
        coVerify(exactly = 0) { authRepository.acknowledgeTwoFactorSetup(any()) }

        viewModel.setCodesAcknowledged(true)
        viewModel.complete()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.isComplete).isTrue()
        coVerify { authRepository.finalizeTwoFactorSetup("fin") }
    }

    @Test
    fun `a wrong code clears the entry and stays on verify`() = runTest {
        stubHappyPath()
        coEvery { authRepository.confirmTwoFactorSetup(TEMP, "123456") } returns apiError(400)
        val viewModel = createViewModel()

        toBackupCodes(viewModel)

        val state = viewModel.uiState.value
        assertThat(state.phase).isEqualTo(TwoFactorSetupPhase.Verify)
        assertThat(state.error).isEqualTo(TwoFactorSetupError.InvalidCode)
        assertThat(state.code).isEmpty()
        assertThat(state.mustSignInAgain).isFalse()
    }

    @Test
    fun `a dead setup token sends the user back to sign in`() = runTest {
        listOf(401, 403).forEach { status ->
            coEvery { authRepository.startTwoFactorSetup(TEMP) } returns apiError(status)
            val viewModel = createViewModel()

            viewModel.start()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.mustSignInAgain).isTrue()
        }
    }

    @Test
    fun `a finalize with no refresh cookie names the cookie`() = runTest {
        stubHappyPath()
        coEvery { authRepository.finalizeTwoFactorSetup("fin") } returns
            Result.Error(MissingRefreshCookieException(), "incomplete")
        val viewModel = createViewModel()

        toBackupCodes(viewModel)
        viewModel.onCodesSaved()
        viewModel.complete()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.error).isEqualTo(TwoFactorSetupError.SessionCookieMissing)
        assertThat(viewModel.uiState.value.isComplete).isFalse()
    }

    @Test
    fun `a failed finalize retries with the same finalization credential`() = runTest {
        stubHappyPath()
        coEvery { authRepository.finalizeTwoFactorSetup("fin") } returnsMany listOf(
            apiError(500),
            Result.Success(User(email = "a@b.c")),
        )
        val viewModel = createViewModel()
        toBackupCodes(viewModel)
        viewModel.setCodesAcknowledged(true)

        viewModel.complete()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.error).isEqualTo(TwoFactorSetupError.FinalizeFailed)

        viewModel.complete()
        advanceUntilIdle()

        // The acknowledgement nonce is single-use: it is spent once, never replayed.
        coVerify(exactly = 1) { authRepository.acknowledgeTwoFactorSetup("ack") }
        coVerify(exactly = 2) { authRepository.finalizeTwoFactorSetup("fin") }
        assertThat(viewModel.uiState.value.isComplete).isTrue()
    }

    @Test
    fun `a spent transition credential sends the user back to sign in`() = runTest {
        stubHappyPath()
        coEvery { authRepository.acknowledgeTwoFactorSetup("ack") } returns apiError(400)
        val viewModel = createViewModel()
        toBackupCodes(viewModel)
        viewModel.setCodesAcknowledged(true)

        viewModel.complete()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.mustSignInAgain).isTrue()
        coVerify(exactly = 0) { authRepository.finalizeTwoFactorSetup(any()) }
    }

    @Test
    fun `2FA enabled without a session sends the user to sign in with the new factor`() = runTest {
        stubHappyPath()
        coEvery { authRepository.finalizeTwoFactorSetup("fin") } returns Result.Error(
            ApiException(500, "x", body = """{"code":"CREDENTIAL_RETIREMENT_INCOMPLETE"}"""),
        )
        val viewModel = createViewModel()
        toBackupCodes(viewModel)
        viewModel.setCodesAcknowledged(true)

        viewModel.complete()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.mustSignInAgain).isTrue()
        assertThat(viewModel.uiState.value.enabledWithoutSession).isTrue()
        assertThat(viewModel.uiState.value.error).isNull()
    }

    @Test
    fun `rate limiting is retryable`() = runTest {
        coEvery { authRepository.startTwoFactorSetup(TEMP) } returns apiError(429)
        val viewModel = createViewModel()

        viewModel.start()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.error).isEqualTo(TwoFactorSetupError.RateLimited)
        assertThat(viewModel.uiState.value.mustSignInAgain).isFalse()
    }

    @Test
    fun `leaving once a secret is staged asks the server to discard it`() = runTest {
        stubHappyPath()
        val viewModel = createViewModel()
        viewModel.leave()
        advanceUntilIdle()
        coVerify(exactly = 0) { authRepository.cancelTwoFactorSetup(any()) }

        viewModel.start()
        advanceUntilIdle()
        viewModel.leave()
        advanceUntilIdle()

        coVerify(exactly = 1) { authRepository.cancelTwoFactorSetup(TEMP) }
    }

    @Test
    fun `leaving setup on a server without the cancel route sends nothing`() = runTest {
        stubHappyPath()
        detectedBackend.value = DetectedBackend("0.8.9-rc1", BackendBuildClass.RC)
        val viewModel = createViewModel()
        viewModel.start()
        advanceUntilIdle()

        viewModel.leave()
        advanceUntilIdle()

        coVerify(exactly = 0) { authRepository.cancelTwoFactorSetup(any()) }
    }

    @Test
    fun `leaving setup before the server version is known still asks to discard the secret`() = runTest {
        stubHappyPath()
        detectedBackend.value = null
        val viewModel = createViewModel()
        viewModel.start()
        advanceUntilIdle()

        viewModel.leave()
        advanceUntilIdle()

        coVerify(exactly = 1) { authRepository.cancelTwoFactorSetup(TEMP) }
    }

    @Test
    fun `the setup key is read and percent-decoded from the otpauth uri`() {
        assertThat(otpauthSecret("otpauth://totp/App:a%40b.c?secret=AB%3DCD&issuer=App")).isEqualTo("AB=CD")
        assertThat(otpauthSecret("otpauth://totp/App:a?issuer=App")).isNull()
    }

    private companion object {
        const val TEMP = "setup-token"
        const val OTPAUTH = "otpauth://totp/LibreChat:a@b.c?secret=JBSWY3DPEHPK3PXP&issuer=LibreChat"
        val CODES = listOf("0123456789abcdef0123456789abcdef", "fedcba9876543210fedcba9876543210")
    }
}
