package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.common.DetectedBackend
import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.ServerDataStore
import com.garfiec.librechat.core.data.repository.AuthRepository
import com.garfiec.librechat.core.data.repository.BalanceRepository
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.KeyRepository
import com.garfiec.librechat.core.data.repository.PasskeyRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.data.repository.UserRepository
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.User
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import com.garfiec.librechat.core.model.response.TwoFactorSetupResponse
import com.garfiec.librechat.feature.settings.util.ContentReader
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/** The Account tab's 2FA code prompts: a rejected code is shown in the prompt that sent it. */
class AccountTwoFactorCodePromptTest {

    private val testDispatcher = StandardTestDispatcher()
    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val authRepository = mockk<AuthRepository>(relaxed = true)
    private val configRepository = mockk<ConfigRepository>(relaxed = true)
    private val roleRepository = mockk<RoleRepository> {
        every { userPermissions } returns MutableStateFlow<UserRolePermissions?>(null)
    }
    private val serverDataStore = mockk<ServerDataStore> {
        every { currentUrlFlow } returns MutableStateFlow("https://chat.example.com")
    }
    private val localUser = User(email = "maya@example.com", provider = "local", twoFactorEnabled = false)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        coEvery { userRepository.getUser() } returns Result.Success(localUser)
        every { configRepository.startupConfig } returns MutableStateFlow<StartupConfig?>(null)
        every { configRepository.endpointConfigs } returns MutableStateFlow<Map<String, EndpointConfig>>(emptyMap())
        every { configRepository.detectedBackend } returns MutableStateFlow<DetectedBackend?>(null)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = AccountSettingsViewModel(
        userRepository = userRepository,
        authRepository = authRepository,
        balanceRepository = mockk<BalanceRepository>(relaxed = true),
        contentReader = mockk<ContentReader>(relaxed = true),
        avatarImagePreparer = { null },
        configRepository = configRepository,
        serverDataStore = serverDataStore,
        keyRepository = mockk<KeyRepository>(relaxed = true),
        roleRepository = roleRepository,
        passkeyRepository = mockk<PasskeyRepository>(relaxed = true),
        ioDispatcher = testDispatcher,
    )

    private fun withTwoFactorOn() {
        coEvery { userRepository.getUser() } returns Result.Success(localUser.copy(twoFactorEnabled = true))
    }

    private fun failure(status: Int, message: String) =
        Result.Error(ApiException(statusCode = status, message = message, serverAuthored = true), message)

    @Test
    fun `a wrong code at 2FA setup shows in the setup dialog, not on the page`() = runTest {
        coEvery { authRepository.enableTwoFactor(null, null) } returns
            Result.Success(TwoFactorSetupResponse(otpauthUrl = "otpauth://totp/x?secret=ABC", backupCodes = listOf("c")))
        coEvery { authRepository.confirmTwoFactor("000000") } returns failure(400, "Invalid token.")
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.toggleTwoFactor()
        advanceUntilIdle()

        viewModel.confirmEnableTwoFactor("000000")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.error).isNull()
        assertThat(state.showTwoFactorSetupDialog).isTrue()
        assertThat(state.twoFactorCodeError).isEqualTo(TwoFactorCodeError.Rejected(byBackupCode = false))

        viewModel.dismissTwoFactorSetupDialog()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.twoFactorCodeError).isNull()
    }

    @Test
    fun `a wrong code to turn 2FA off stays in the prompt, by the kind of code sent`() = runTest {
        withTwoFactorOn()
        coEvery { authRepository.disableTwoFactor(any(), any()) } returns
            failure(401, "Invalid token or backup code")
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.toggleTwoFactor()

        viewModel.confirmDisableTwoFactor(token = "000000", backupCode = null)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.error).isNull()
        assertThat(viewModel.uiState.value.showDisableTwoFactorDialog).isTrue()
        assertThat(viewModel.uiState.value.twoFactorCodeError)
            .isEqualTo(TwoFactorCodeError.Rejected(byBackupCode = false))

        viewModel.confirmDisableTwoFactor(token = null, backupCode = "bad")
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.error).isNull()
        assertThat(viewModel.uiState.value.twoFactorCodeError)
            .isEqualTo(TwoFactorCodeError.Rejected(byBackupCode = true))

        viewModel.dismissDisableTwoFactorDialog()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.twoFactorCodeError).isNull()
    }

    @Test
    fun `a wrong code to regenerate backup codes stays in the prompt`() = runTest {
        withTwoFactorOn()
        coEvery { authRepository.regenerateBackupCodes(token = "000000", backupCode = null) } returns
            failure(401, "Invalid token or backup code")
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.requestRegenerateBackupCodes()
        viewModel.confirmRegenerateBackupCodes()

        viewModel.regenerateBackupCodesWithOtp(token = "000000", backupCode = null)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.error).isNull()
        assertThat(state.showBackupCodesOtpDialog).isTrue()
        assertThat(state.twoFactorCodeError).isEqualTo(TwoFactorCodeError.Rejected(byBackupCode = false))

        viewModel.dismissBackupCodesOtpDialog()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.twoFactorCodeError).isNull()
    }

    @Test
    fun `a wrong code to re-enrol stays in the prompt`() = runTest {
        coEvery { authRepository.enableTwoFactor(null, null) } returns
            failure(400, "OTP token or backup code is required to re-enroll 2FA")
        coEvery { authRepository.enableTwoFactor(token = "000000", backupCode = null) } returns
            failure(401, "Invalid token or backup code")
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.toggleTwoFactor()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.showEnableTwoFactorOtpDialog).isTrue()

        viewModel.enableTwoFactorWithOtp(token = "000000", backupCode = null)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.error).isNull()
        assertThat(state.showEnableTwoFactorOtpDialog).isTrue()
        assertThat(state.twoFactorCodeError).isEqualTo(TwoFactorCodeError.Rejected(byBackupCode = false))

        viewModel.dismissEnableTwoFactorOtpDialog()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.twoFactorCodeError).isNull()
    }

    @Test
    fun `a wrong code to delete the account stays in the prompt, and a server failure shows its message there`() =
        runTest {
            withTwoFactorOn()
            coEvery { userRepository.deleteUser(token = "000000", backupCode = null) } returns
                failure(401, "Invalid token or backup code")
            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.deleteAccount()

            viewModel.deleteAccount(token = "000000")
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.error).isNull()
            assertThat(viewModel.uiState.value.showDeleteAccountOtpDialog).isTrue()
            assertThat(viewModel.uiState.value.twoFactorCodeError)
                .isEqualTo(TwoFactorCodeError.Rejected(byBackupCode = false))

            coEvery { userRepository.deleteUser(token = "000000", backupCode = null) } returns
                failure(500, "Server error")
            viewModel.deleteAccount(token = "000000")
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.error).isNull()
            assertThat(viewModel.uiState.value.twoFactorCodeError).isEqualTo(TwoFactorCodeError.Failed("Server error"))

            viewModel.dismissDeleteAccountOtpDialog()
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.twoFactorCodeError).isNull()
        }
}
