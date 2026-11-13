package com.garfiec.librechat.feature.auth.viewmodel

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.ServerDataStore
import com.garfiec.librechat.core.data.repository.AccountSwitcher
import com.garfiec.librechat.core.data.repository.AuthRepository
import com.garfiec.librechat.core.data.repository.BannerRepository
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.PendingAddSession
import com.garfiec.librechat.core.model.Banner
import com.garfiec.librechat.core.model.LoginOutcome
import com.garfiec.librechat.core.model.User
import com.garfiec.librechat.core.model.config.InterfaceConfig
import com.garfiec.librechat.core.model.config.PrivacyPolicyConfig
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

@OptIn(ExperimentalCoroutinesApi::class)
class RegisterViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val authRepository = mockk<AuthRepository>(relaxed = true)
    private val configRepository = mockk<ConfigRepository>(relaxed = true)
    private val accountSwitcher = mockk<AccountSwitcher>(relaxed = true)
    private val serverDataStore = mockk<ServerDataStore>(relaxed = true) {
        every { currentUrlFlow } returns MutableStateFlow("https://chat.example.com")
    }

    private val configFlow = MutableStateFlow<StartupConfig?>(null)
    private val bannerRepository = mockk<BannerRepository>()

    private lateinit var viewModel: RegisterViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { configRepository.startupConfig } returns configFlow
        every { accountSwitcher.pendingAdd } returns null
        coEvery { bannerRepository.getBanner() } returns Result.Success(null)
        coEvery { accountSwitcher.withPendingIdentity<Any?>(any()) } coAnswers { firstArg<suspend () -> Any?>().invoke() }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = RegisterViewModel(
        authRepository = authRepository,
        configRepository = configRepository,
        accountSwitcher = accountSwitcher,
        serverDataStore = serverDataStore,
        bannerRepository = bannerRepository,
    )

    @Test
    fun `the server's public banner shows on register`() = runTest {
        val banner = Banner(bannerId = "b1", message = "Maintenance tonight", isPublic = true)
        coEvery { bannerRepository.getBanner() } returns Result.Success(banner)
        viewModel = createViewModel()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.banner).isEqualTo(banner)
    }

    @Test
    fun `the server's policy links reach the register footer, resolved against the server`() = runTest {
        configFlow.value = StartupConfig(
            interfaceConfig = InterfaceConfig(
                privacyPolicy = PrivacyPolicyConfig(externalUrl = "javascript:alert(1)"),
                termsOfService = TermsOfServiceConfig(externalUrl = "/terms"),
            ),
        )
        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.policyLinks)
            .isEqualTo(PolicyLinks(termsOfServiceUrl = "https://chat.example.com/terms"))
    }

    @Test
    fun `an add-account register shows the pending server's links and password rule`() = runTest {
        configFlow.value = StartupConfig(
            minPasswordLength = 20,
            interfaceConfig = InterfaceConfig(termsOfService = TermsOfServiceConfig(externalUrl = "/live-terms")),
        )
        val pending = mockk<PendingAddSession>(relaxed = true)
        every { pending.serverUrl } returns "https://pending.example.com"
        every { pending.startupConfig } returns MutableStateFlow(
            StartupConfig(
                minPasswordLength = 12,
                emailEnabled = false,
                interfaceConfig = InterfaceConfig(termsOfService = TermsOfServiceConfig(externalUrl = "/terms")),
            ),
        )
        every { accountSwitcher.pendingAdd } returns pending
        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.policyLinks.termsOfServiceUrl).isEqualTo("https://pending.example.com/terms")
        assertThat(viewModel.uiState.value.minPasswordLength).isEqualTo(12)
        assertThat(viewModel.uiState.value.emailEnabled).isFalse()
    }

    private fun fillValidFields() {
        viewModel.onNameChanged("Test User")
        viewModel.onEmailChanged("test@example.com")
        viewModel.onUsernameChanged("testuser")
        viewModel.onPasswordChanged("password123")
        viewModel.onConfirmPasswordChanged("password123")
    }

    @Test
    fun `a tenant-required 403 is worded as a refusal, and a later local error drops it`() = runTest {
        coEvery { authRepository.register(any(), any(), any(), any()) } returns Result.Error(
            exception = ApiException(403, "server message", body = TENANT_BODY, serverAuthored = true),
            message = "server message",
        )
        viewModel = createViewModel()
        advanceUntilIdle()
        fillValidFields()
        viewModel.register()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.refusal).isEqualTo(AuthRefusal.TenantRequired)
        assertThat(viewModel.uiState.value.error).isNotNull()

        viewModel.onConfirmPasswordChanged("mismatch")
        viewModel.register()
        assertThat(viewModel.uiState.value.refusal).isNull()
        assertThat(viewModel.uiState.value.registerError).isEqualTo(RegisterError.PasswordMismatch)
    }

    @Test
    fun `initial state has empty fields`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.name).isEmpty()
        assertThat(state.email).isEmpty()
        assertThat(state.username).isEmpty()
        assertThat(state.password).isEmpty()
        assertThat(state.confirmPassword).isEmpty()
        assertThat(state.isLoading).isFalse()
        assertThat(state.error).isNull()
        assertThat(state.outcome).isNull()
    }

    @Test
    fun `field changes update state and clear error`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onNameChanged("John")
        assertThat(viewModel.uiState.value.name).isEqualTo("John")

        viewModel.onEmailChanged("john@test.com")
        assertThat(viewModel.uiState.value.email).isEqualTo("john@test.com")

        viewModel.onUsernameChanged("johndoe")
        assertThat(viewModel.uiState.value.username).isEqualTo("johndoe")

        viewModel.onPasswordChanged("pass123")
        assertThat(viewModel.uiState.value.password).isEqualTo("pass123")

        viewModel.onConfirmPasswordChanged("pass123")
        assertThat(viewModel.uiState.value.confirmPassword).isEqualTo("pass123")
    }

    @Test
    fun `register with blank fields shows error`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.register()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.registerError).isEqualTo(RegisterError.MissingFields)
    }

    @Test
    fun `register with blank name shows error`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEmailChanged("test@test.com")
        viewModel.onUsernameChanged("testuser")
        viewModel.onPasswordChanged("password123")
        viewModel.onConfirmPasswordChanged("password123")
        viewModel.register()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.registerError).isEqualTo(RegisterError.MissingFields)
    }

    @Test
    fun `register with short password shows error`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onNameChanged("Test")
        viewModel.onEmailChanged("test@test.com")
        viewModel.onUsernameChanged("testuser")
        viewModel.onPasswordChanged("short")
        viewModel.onConfirmPasswordChanged("short")
        viewModel.register()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.registerError).isEqualTo(RegisterError.PasswordTooShort)
    }

    @Test
    fun `register with password mismatch shows error`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onNameChanged("Test")
        viewModel.onEmailChanged("test@test.com")
        viewModel.onUsernameChanged("testuser")
        viewModel.onPasswordChanged("password123")
        viewModel.onConfirmPasswordChanged("different456")
        viewModel.register()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.registerError).isEqualTo(RegisterError.PasswordMismatch)
    }

    @Test
    fun `with email on, a registration leads to verifying the email and does not sign in`() = runTest {
        configFlow.value = StartupConfig(emailEnabled = true)
        coEvery {
            authRepository.register("Test User", "test@example.com", "testuser", "password123")
        } returns Result.Success(Unit)

        viewModel = createViewModel()
        advanceUntilIdle()

        fillValidFields()
        viewModel.register()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.outcome).isEqualTo(RegisterOutcome.VerifyEmail("test@example.com"))
        assertThat(state.isLoading).isFalse()
        assertThat(state.error).isNull()
        coVerify(exactly = 0) { authRepository.login(any(), any()) }
    }

    @Test
    fun `registration failure shows error message`() = runTest {
        coEvery {
            authRepository.register(any(), any(), any(), any())
        } returns Result.Error(message = "Email already exists")

        viewModel = createViewModel()
        advanceUntilIdle()

        fillValidFields()
        viewModel.register()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.error).isEqualTo("Email already exists")
        assertThat(state.outcome).isNull()
        assertThat(state.isLoading).isFalse()
    }

    @Test
    fun `registration failure with null message uses default`() = runTest {
        coEvery {
            authRepository.register(any(), any(), any(), any())
        } returns Result.Error(message = null)

        viewModel = createViewModel()
        advanceUntilIdle()

        fillValidFields()
        viewModel.register()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.error).isEqualTo("Registration failed")
    }

    @Test
    fun `startup config updates minPasswordLength`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        configFlow.value = StartupConfig(minPasswordLength = 12)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.minPasswordLength).isEqualTo(12)
    }

    @Test
    fun `custom minPasswordLength is enforced during validation`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        configFlow.value = StartupConfig(minPasswordLength = 12)
        advanceUntilIdle()

        viewModel.onNameChanged("Test")
        viewModel.onEmailChanged("test@test.com")
        viewModel.onUsernameChanged("testuser")
        viewModel.onPasswordChanged("short1234") // 9 chars, less than 12
        viewModel.onConfirmPasswordChanged("short1234")
        viewModel.register()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.registerError).isEqualTo(RegisterError.PasswordTooShort)
    }

    @Test
    fun `register calls authRepository with correct parameters`() = runTest {
        coEvery { authRepository.register(any(), any(), any(), any()) } returns Result.Success(Unit)

        viewModel = createViewModel()
        advanceUntilIdle()

        fillValidFields()
        viewModel.register()
        advanceUntilIdle()

        coVerify {
            authRepository.register("Test User", "test@example.com", "testuser", "password123")
        }
    }

    @Test
    fun `a blank username registers, sent empty`() = runTest {
        coEvery { authRepository.register(any(), any(), any(), any()) } returns Result.Success(Unit)
        viewModel = createViewModel()
        advanceUntilIdle()

        fillValidFields()
        viewModel.onUsernameChanged("")
        viewModel.register()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.registerError).isNull()
        coVerify { authRepository.register("Test User", "test@example.com", "", "password123") }
    }

    @Test
    fun `with email off, a registration signs in with the same credentials`() = runTest {
        configFlow.value = StartupConfig(emailEnabled = false)
        coEvery { authRepository.register(any(), any(), any(), any()) } returns Result.Success(Unit)
        coEvery { authRepository.login("test@example.com", "password123") } returns
            Result.Success(LoginOutcome.Success(mockk<User>(relaxed = true)))
        viewModel = createViewModel()
        advanceUntilIdle()

        fillValidFields()
        viewModel.register()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.outcome).isEqualTo(RegisterOutcome.SignedIn)
        assertThat(viewModel.uiState.value.isLoading).isFalse()
    }

    @Test
    fun `with email off, a sign-in that needs 2FA setup hands over its setup token`() = runTest {
        configFlow.value = StartupConfig(emailEnabled = false)
        coEvery { authRepository.register(any(), any(), any(), any()) } returns Result.Success(Unit)
        coEvery { authRepository.login(any(), any()) } returns
            Result.Success(LoginOutcome.TwoFactorSetupRequired("setup-token"))
        viewModel = createViewModel()
        advanceUntilIdle()

        fillValidFields()
        viewModel.register()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.outcome).isEqualTo(RegisterOutcome.TwoFactorSetup("setup-token"))
    }

    @Test
    fun `with email off and email sign-in disabled, a registration returns to sign in`() = runTest {
        configFlow.value = StartupConfig(emailEnabled = false, emailLoginEnabled = false)
        coEvery { authRepository.register(any(), any(), any(), any()) } returns Result.Success(Unit)
        viewModel = createViewModel()
        advanceUntilIdle()

        fillValidFields()
        viewModel.register()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.outcome).isEqualTo(RegisterOutcome.SignIn)
        coVerify(exactly = 0) { authRepository.login(any(), any()) }
    }

    @Test
    fun `a wrong-password sign-in after registering means the email was already registered`() = runTest {
        configFlow.value = StartupConfig(emailEnabled = false)
        coEvery { authRepository.register(any(), any(), any(), any()) } returns Result.Success(Unit)
        coEvery { authRepository.login(any(), any()) } returns Result.Error(
            exception = ApiException(404, "Incorrect password.", serverAuthored = true),
            message = "Incorrect password.",
        )
        viewModel = createViewModel()
        advanceUntilIdle()

        fillValidFields()
        viewModel.register()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.registerError).isEqualTo(RegisterError.EmailTaken)
        assertThat(state.outcome).isNull()
        assertThat(state.isLoading).isFalse()
    }

    @Test
    fun `another sign-in failure after registering stays on the form with its message`() = runTest {
        configFlow.value = StartupConfig(emailEnabled = false)
        coEvery { authRepository.register(any(), any(), any(), any()) } returns Result.Success(Unit)
        coEvery { authRepository.login(any(), any()) } returns Result.Error(message = "Network unavailable")
        viewModel = createViewModel()
        advanceUntilIdle()

        fillValidFields()
        viewModel.register()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.error).isEqualTo("Network unavailable")
        assertThat(state.registerError).isNull()
        assertThat(state.outcome).isNull()
    }

    @Test
    fun `changing field after error clears the error`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.register()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.registerError).isNotNull()

        viewModel.onNameChanged("Test")
        assertThat(viewModel.uiState.value.registerError).isNull()
    }

    private companion object {
        const val TENANT_BODY = """{"message":"Tenant context required in strict isolation mode","code":"TENANT_CONTEXT_REQUIRED"}"""
    }
}
