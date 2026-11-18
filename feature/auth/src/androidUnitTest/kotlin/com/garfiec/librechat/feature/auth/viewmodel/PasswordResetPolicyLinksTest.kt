package com.garfiec.librechat.feature.auth.viewmodel

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.ServerDataStore
import com.garfiec.librechat.core.data.repository.AccountSwitcher
import com.garfiec.librechat.core.data.repository.AuthRepository
import com.garfiec.librechat.core.data.repository.BannerRepository
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.model.Banner
import com.garfiec.librechat.core.model.config.InterfaceConfig
import com.garfiec.librechat.core.model.config.PrivacyPolicyConfig
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.model.config.TermsOfServiceConfig
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

/**
 * The forgot and reset password screens: the same policy footer as sign-in, as upstream's AuthLayout
 * does, and what each says once the server answers.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PasswordResetPolicyLinksTest {

    private val testDispatcher = StandardTestDispatcher()
    private val authRepository = mockk<AuthRepository>(relaxed = true)
    private val configFlow = MutableStateFlow<StartupConfig?>(null)
    private val configRepository = mockk<ConfigRepository> { every { startupConfig } returns configFlow }
    private val accountSwitcher = mockk<AccountSwitcher> {
        every { pendingAdd } returns null
        coEvery { withPendingIdentity<Any?>(any()) } coAnswers { firstArg<suspend () -> Any?>().invoke() }
    }
    private val bannerRepository = mockk<BannerRepository> { coEvery { getBanner() } returns Result.Success(null) }
    private val serverDataStore = mockk<ServerDataStore> {
        every { currentUrlFlow } returns MutableStateFlow("https://chat.example.com")
    }

    private fun forgotPassword() =
        ForgotPasswordViewModel(authRepository, configRepository, accountSwitcher, serverDataStore, bannerRepository)

    @Before
    fun setup() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val published = StartupConfig(
        interfaceConfig = InterfaceConfig(
            privacyPolicy = PrivacyPolicyConfig(externalUrl = "https://x.example/privacy"),
            termsOfService = TermsOfServiceConfig(externalUrl = "ftp://x.example/terms"),
        ),
    )

    @Test
    fun `forgot password shows the safe links once the config arrives`() = runTest {
        val viewModel = forgotPassword()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.policyLinks).isEqualTo(PolicyLinks())

        configFlow.value = published
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.policyLinks)
            .isEqualTo(PolicyLinks(privacyPolicyUrl = "https://x.example/privacy"))
    }

    @Test
    fun `reset password shows the safe links`() = runTest {
        configFlow.value = published
        val viewModel = ResetPasswordViewModel(
            authRepository,
            configRepository,
            accountSwitcher,
            serverDataStore,
            bannerRepository,
            initialUserId = "u1",
            initialToken = "t1",
        )
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.policyLinks)
            .isEqualTo(PolicyLinks(privacyPolicyUrl = "https://x.example/privacy"))
    }

    @Test
    fun `the server's public banner shows on forgot and reset password`() = runTest {
        val banner = Banner(bannerId = "b1", message = "Maintenance tonight", isPublic = true)
        coEvery { bannerRepository.getBanner() } returns Result.Success(banner)
        val forgot = forgotPassword()
        val reset =
            ResetPasswordViewModel(authRepository, configRepository, accountSwitcher, serverDataStore, bannerRepository)
        advanceUntilIdle()
        assertThat(forgot.uiState.value.banner).isEqualTo(banner)
        assertThat(reset.uiState.value.banner).isEqualTo(banner)
    }

    @Test
    fun `a reset that could not sign out every session still reports the new password as active`() = runTest {
        coEvery { authRepository.resetPassword("u1", "t1", "new-password", "new-password") } returns Result.Error(
            ApiException(500, "x", body = """{"code":"CREDENTIAL_RETIREMENT_INCOMPLETE"}"""),
        )
        val viewModel = ResetPasswordViewModel(
            authRepository,
            configRepository,
            accountSwitcher,
            serverDataStore,
            bannerRepository,
            initialUserId = "u1",
            initialToken = "t1",
        )
        viewModel.onPasswordChanged("new-password")
        viewModel.onConfirmPasswordChanged("new-password")

        viewModel.resetPassword()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isReset).isTrue()
        assertThat(viewModel.uiState.value.sessionsNotRetired).isTrue()
        assertThat(viewModel.uiState.value.error).isNull()
    }

    @Test
    fun `forgot password says to ask an administrator when the server sends no mail`() = runTest {
        val viewModel = forgotPassword()
        configFlow.value = StartupConfig(emailEnabled = true)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.emailUnavailable).isFalse()

        configFlow.value = StartupConfig(emailEnabled = false)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.emailUnavailable).isTrue()
    }
}
