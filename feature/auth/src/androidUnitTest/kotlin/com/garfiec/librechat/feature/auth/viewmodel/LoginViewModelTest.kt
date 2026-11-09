package com.garfiec.librechat.feature.auth.viewmodel

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.AccountEntry
import com.garfiec.librechat.core.data.datastore.ServerDataStore
import com.garfiec.librechat.core.data.datastore.SsoRiskDataStore
import com.garfiec.librechat.core.data.repository.AccountSwitcher
import com.garfiec.librechat.core.data.repository.AuthRepository
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.PendingAddSession
import com.garfiec.librechat.core.model.LoginOutcome
import com.garfiec.librechat.core.model.User
import com.garfiec.librechat.core.model.config.InterfaceConfig
import com.garfiec.librechat.core.model.config.LdapConfig
import com.garfiec.librechat.core.model.config.PrivacyPolicyConfig
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.model.config.TermsOfServiceConfig
import com.garfiec.librechat.core.network.client.CertificateAuthority
import com.garfiec.librechat.core.network.client.CertificateTrustRegistry
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val authRepository = mockk<AuthRepository>(relaxed = true)
    private val configRepository = mockk<ConfigRepository>(relaxed = true)
    private val accountSwitcher = mockk<AccountSwitcher>(relaxed = true)
    private val ssoRiskDataStore = mockk<SsoRiskDataStore>(relaxed = true)
    private val serverDataStore = mockk<ServerDataStore>(relaxed = true) {
        every { currentUrlFlow } returns MutableStateFlow("https://chat.example.com")
    }
    private val certificateTrust = CertificateTrustRegistry()

    private val configFlow = MutableStateFlow<StartupConfig?>(null)

    private lateinit var viewModel: LoginViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { configRepository.startupConfig } returns configFlow
        // No add-account flow pending: the VM reads the global config + live server URL.
        every { accountSwitcher.pendingAdd } returns null
        every { ssoRiskDataStore.acknowledged } returns flowOf(false)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = LoginViewModel(
        authRepository = authRepository,
        configRepository = configRepository,
        accountSwitcher = accountSwitcher,
        ssoRiskDataStore = ssoRiskDataStore,
        serverDataStore = serverDataStore,
        certificateTrust = certificateTrust,
    )

    @Test
    fun `published policy links reach the footer only when safe, resolved against the server`() = runTest {
        configFlow.value = StartupConfig(
            interfaceConfig = InterfaceConfig(
                privacyPolicy = PrivacyPolicyConfig(externalUrl = "javascript:alert(1)"),
                termsOfService = TermsOfServiceConfig(externalUrl = "/terms"),
            ),
        )
        viewModel = createViewModel()
        advanceUntilIdle()

        val links = viewModel.uiState.value.policyLinks
        assertThat(links.privacyPolicyUrl).isNull()
        assertThat(links.termsOfServiceUrl).isEqualTo("https://chat.example.com/terms")

        configFlow.value = StartupConfig(
            interfaceConfig = InterfaceConfig(privacyPolicy = PrivacyPolicyConfig(externalUrl = "https://x.example/p")),
        )
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.policyLinks)
            .isEqualTo(PolicyLinks(privacyPolicyUrl = "https://x.example/p"))
    }

    @Test
    fun `fetches the server config when nothing has loaded it`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        coVerify(exactly = 1) { configRepository.fetchStartupConfig() }
    }

    @Test
    fun `leaves an already-loaded config alone`() = runTest {
        configFlow.value = StartupConfig()
        viewModel = createViewModel()
        advanceUntilIdle()
        coVerify(exactly = 0) { configRepository.fetchStartupConfig() }
    }

    @Test
    fun `a server trusted through a user-installed CA is noted`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.trustedViaUserCa).isFalse()

        certificateTrust.record("chat.example.com", 443, CertificateAuthority.UserInstalled)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.trustedViaUserCa).isTrue()
    }

    @Test
    fun `the add-account flow reads trust for the pending server, not the live one`() = runTest {
        val pending = mockk<PendingAddSession>(relaxed = true)
        every { pending.serverUrl } returns "https://pending.example.com:8443"
        every { pending.startupConfig } returns MutableStateFlow(null)
        every { accountSwitcher.pendingAdd } returns pending
        viewModel = createViewModel()
        certificateTrust.record("chat.example.com", 443, CertificateAuthority.UserInstalled)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.trustedViaUserCa).isFalse()

        certificateTrust.record("pending.example.com", 8443, CertificateAuthority.UserInstalled)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.trustedViaUserCa).isTrue()
    }

    @Test
    fun `an account on this server that owes 2FA setup is named on sign-in`() = runTest {
        coEvery { accountSwitcher.awaitingTwoFactorSetupOn("https://chat.example.com") } returns listOf(
            AccountEntry(accountId = "a1", serverUrl = "https://chat.example.com", displayLabel = "me@chat", lastActiveAt = 0L),
        )
        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.twoFactorSetupAccounts).isEqualTo("me@chat")
    }

    @Test
    fun `no notice when no account on this server owes setup`() = runTest {
        coEvery { accountSwitcher.awaitingTwoFactorSetupOn(any()) } returns emptyList()
        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.twoFactorSetupAccounts).isNull()
    }

    @Test
    fun `initial state has empty fields`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.email).isEmpty()
        assertThat(state.password).isEmpty()
        assertThat(state.isLoading).isFalse()
        assertThat(state.error).isNull()
        assertThat(state.isLoggedIn).isFalse()
    }

    @Test
    fun `onEmailChanged updates email and clears error`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEmailChanged("user@example.com")

        assertThat(viewModel.uiState.value.email).isEqualTo("user@example.com")
        assertThat(viewModel.uiState.value.error).isNull()
    }

    @Test
    fun `onPasswordChanged updates password and clears error`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onPasswordChanged("secret123")

        assertThat(viewModel.uiState.value.password).isEqualTo("secret123")
        assertThat(viewModel.uiState.value.error).isNull()
    }

    @Test
    fun `login with blank email shows error`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onPasswordChanged("secret123")
        viewModel.login()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.error).isEqualTo("Please enter email and password")
        assertThat(viewModel.uiState.value.isLoading).isFalse()
    }

    @Test
    fun `login with blank password shows error`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEmailChanged("user@example.com")
        viewModel.login()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.error).isEqualTo("Please enter email and password")
    }

    @Test
    fun `successful login sets isLoggedIn true`() = runTest {
        val user = User(email = "user@example.com", name = "Test User")
        coEvery { authRepository.login("user@example.com", "password123") } returns
            Result.Success(LoginOutcome.Success(user))

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEmailChanged("user@example.com")
        viewModel.onPasswordChanged("password123")
        viewModel.login()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isLoggedIn).isTrue()
        assertThat(state.isLoading).isFalse()
        assertThat(state.error).isNull()
    }

    @Test
    fun `login requiring 2FA sets twoFactorTempToken`() = runTest {
        coEvery { authRepository.login("user@example.com", "password123") } returns
            Result.Success(LoginOutcome.TwoFactorRequired("temp-token-123"))

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEmailChanged("user@example.com")
        viewModel.onPasswordChanged("password123")
        viewModel.login()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.twoFactorTempToken).isEqualTo("temp-token-123")
        assertThat(state.isLoggedIn).isFalse()
        assertThat(state.isLoading).isFalse()
    }

    @Test
    fun `consumeTwoFactorNavigation clears the temp token`() = runTest {
        coEvery { authRepository.login("user@example.com", "password123") } returns
            Result.Success(LoginOutcome.TwoFactorRequired("temp-token-123"))

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEmailChanged("user@example.com")
        viewModel.onPasswordChanged("password123")
        viewModel.login()
        advanceUntilIdle()

        viewModel.consumeTwoFactorNavigation()

        assertThat(viewModel.uiState.value.twoFactorTempToken).isNull()
    }

    @Test
    fun `login failure shows error message`() = runTest {
        coEvery { authRepository.login("user@example.com", "wrong") } returns
            Result.Error(message = "Invalid credentials")

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEmailChanged("user@example.com")
        viewModel.onPasswordChanged("wrong")
        viewModel.login()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.error).isEqualTo("Invalid credentials")
        assertThat(state.isLoading).isFalse()
        assertThat(state.isLoggedIn).isFalse()
    }

    /**
     * Upstream's `validateEmailLogin` answers `403 {"message": "Email login is not allowed."}` — a
     * LibreChat JSON envelope, so the client flags it server-authored. That is the one 403 that may
     * hide the form.
     */
    @Test
    fun `a server-authored 403 hides the email form`() = runTest {
        coEvery { authRepository.login("user@example.com", "pw") } returns Result.Error(
            exception = ApiException(403, "Email login is not allowed.", serverAuthored = true),
            message = "Email login is not allowed.",
        )

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEmailChanged("user@example.com")
        viewModel.onPasswordChanged("pw")
        viewModel.login()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.emailLoginEnabled).isFalse()
        assertThat(state.error).isEqualTo("Email and password sign-in is disabled on this server.")
        assertThat(state.offerDiagnosticLog).isFalse()
    }

    /**
     * A bouncer or WAF in front of the deployment answers the same route with a 403 too, and that is
     * a condition the user can wait out — it must not be read as "this server has email login off".
     */
    @Test
    fun `a 403 that is not LibreChat's keeps the email form`() = runTest {
        coEvery { authRepository.login("user@example.com", "pw") } returns Result.Error(
            exception = ApiException(403, "Access denied", serverAuthored = false),
            message = "Access denied",
        )

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEmailChanged("user@example.com")
        viewModel.onPasswordChanged("pw")
        viewModel.login()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.emailLoginEnabled).isTrue()
        assertThat(state.error).isEqualTo("Access denied")
        assertThat(state.offerDiagnosticLog).isTrue()
    }

    @Test
    fun `login requiring 2FA setup navigates to setup, not the code challenge`() = runTest {
        coEvery { authRepository.login("user@example.com", "pw") } returns
            Result.Success(LoginOutcome.TwoFactorSetupRequired("setup-token"))

        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onEmailChanged("user@example.com")
        viewModel.onPasswordChanged("pw")
        viewModel.login()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.twoFactorSetupTempToken).isEqualTo("setup-token")
        assertThat(state.twoFactorTempToken).isNull()
        viewModel.consumeTwoFactorSetupNavigation()
        assertThat(viewModel.uiState.value.twoFactorSetupTempToken).isNull()
    }

    /** v0.8.9-rc1: coded, server-authored 403s that are not "email login disabled". */
    @Test
    fun `the federated-blocked and tenant 403s keep the form and get their own wording`() = runTest {
        val bodies = mapOf(
            """{"code":"TWO_FACTOR_FEDERATED_LOGIN_BLOCKED","message":"Sign in with your identity provider to continue."}""" to
                AuthRefusal.FederatedBlocked,
            """{"message":"Tenant context required in strict isolation mode","code":"TENANT_CONTEXT_REQUIRED"}""" to
                AuthRefusal.TenantRequired,
        )
        bodies.forEach { (body, refusal) ->
            coEvery { authRepository.login("user@example.com", "pw") } returns Result.Error(
                exception = ApiException(403, "server message", body = body, serverAuthored = true),
                message = "server message",
            )
            viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onEmailChanged("user@example.com")
            viewModel.onPasswordChanged("pw")
            viewModel.login()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.emailLoginEnabled).isTrue()
            assertThat(state.refusal).isEqualTo(refusal)
            viewModel.onPasswordChanged("pw2")
            assertThat(viewModel.uiState.value.refusal).isNull()
        }
    }

    /** A ban is also a server-authored 403 and must keep the form; `isBanned` is that discriminator. */
    @Test
    fun `a ban keeps the email form and the server's own message`() = runTest {
        coEvery { authRepository.login("user@example.com", "pw") } returns Result.Error(
            exception = ApiException(403, "Your account has been temporarily banned", isBanned = true, serverAuthored = true),
            message = "Your account has been temporarily banned",
        )

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEmailChanged("user@example.com")
        viewModel.onPasswordChanged("pw")
        viewModel.login()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.emailLoginEnabled).isTrue()
        assertThat(state.error).isEqualTo("Your account has been temporarily banned")
        assertThat(state.offerDiagnosticLog).isFalse()
    }

    @Test
    fun `login failure with null message uses default`() = runTest {
        coEvery { authRepository.login("user@example.com", "wrong") } returns
            Result.Error(message = null)

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEmailChanged("user@example.com")
        viewModel.onPasswordChanged("wrong")
        viewModel.login()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.error).isEqualTo("Login failed")
    }

    private val defaultSocialLogins = listOf("google", "facebook", "openid", "github", "discord", "saml")

    private fun TestScope.stateFor(config: StartupConfig): LoginUiState {
        configFlow.value = config
        viewModel = createViewModel()
        advanceUntilIdle()
        return viewModel.uiState.value
    }

    @Test
    fun `startup config updates registration`() = runTest {
        assertThat(stateFor(StartupConfig(registrationEnabled = true)).registrationEnabled).isTrue()
    }

    @Test
    fun `the server's default provider list shows only the providers it configured`() = runTest {
        val state = stateFor(
            StartupConfig(socialLoginEnabled = true, socialLogins = defaultSocialLogins, googleLoginEnabled = true),
        )

        assertThat(state.socialProviders).containsExactly(SocialProvider("google"))
    }

    @Test
    fun `providers keep the server's order and drop unknown names`() = runTest {
        val state = stateFor(
            StartupConfig(
                socialLoginEnabled = true,
                socialLogins = listOf("github", "ldap", "google"),
                googleLoginEnabled = true,
                githubLoginEnabled = true,
            ),
        )

        assertThat(state.socialProviders.map { it.id }).containsExactly("github", "google").inOrder()
    }

    @Test
    fun `with OpenID and SAML both set up the server hides SAML, and so do we`() = runTest {
        val state = stateFor(
            StartupConfig(
                socialLoginEnabled = true,
                socialLogins = defaultSocialLogins,
                openidLoginEnabled = true,
                openidLabel = "Continue with OpenID",
                samlLoginEnabled = false,
            ),
        )

        assertThat(state.socialProviders).containsExactly(SocialProvider("openid", label = "Continue with OpenID"))
    }

    @Test
    fun `social login on with no provider configured shows no social section`() = runTest {
        val state = stateFor(StartupConfig(socialLoginEnabled = true, socialLogins = defaultSocialLogins))

        assertThat(state.socialProviders).isEmpty()
    }

    @Test
    fun `social login off hides configured providers`() = runTest {
        val state = stateFor(
            StartupConfig(socialLoginEnabled = false, socialLogins = defaultSocialLogins, googleLoginEnabled = true),
        )

        assertThat(state.socialProviders).isEmpty()
    }

    @Test
    fun `a custom OpenID label is used as given, and a blank SAML label falls back to ours`() = runTest {
        val state = stateFor(
            StartupConfig(
                socialLoginEnabled = true,
                socialLogins = listOf("openid", "saml"),
                openidLoginEnabled = true,
                openidLabel = "Sign in with Acme SSO",
                samlLoginEnabled = true,
                samlLabel = "",
            ),
        )

        assertThat(state.socialProviders).containsExactly(
            SocialProvider("openid", label = "Sign in with Acme SSO"),
            SocialProvider("saml", label = null),
        ).inOrder()
    }

    @Test
    fun `startup config with null socialLogins defaults to empty`() = runTest {
        assertThat(stateFor(StartupConfig(socialLoginEnabled = true, socialLogins = null)).socialProviders).isEmpty()
    }

    @Test
    fun `forgot password shows only when the server allows password reset`() = runTest {
        assertThat(stateFor(StartupConfig(passwordResetEnabled = false)).passwordResetEnabled).isFalse()
        assertThat(stateFor(StartupConfig(passwordResetEnabled = true)).passwordResetEnabled).isTrue()
    }

    @Test
    fun `forgot password stays hidden with email login off`() = runTest {
        val state = stateFor(StartupConfig(emailLoginEnabled = false, passwordResetEnabled = true))

        assertThat(state.passwordResetEnabled).isFalse()
    }

    @Test
    fun `LDAP username mode asks for a username`() = runTest {
        assertThat(stateFor(StartupConfig()).usernameLogin).isFalse()
        assertThat(stateFor(StartupConfig(ldap = LdapConfig(enabled = true, username = true))).usernameLogin).isTrue()

        viewModel.onPasswordChanged("pw")
        viewModel.login()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.error).isEqualTo("Please enter username and password")
    }

    private val autoRedirectConfig = StartupConfig(openidLoginEnabled = true, openidAutoRedirect = true)

    @Test
    fun `OpenID auto-redirect opens the provider without a tap, even with social login off`() = runTest {
        every { ssoRiskDataStore.acknowledged } returns flowOf(true)

        val state = stateFor(autoRedirectConfig.copy(socialLoginEnabled = false))

        assertThat(state.ssoProvider).isEqualTo("openid")
    }

    @Test
    fun `OpenID auto-redirect goes through the in-app-browser warning first`() = runTest {
        val state = stateFor(autoRedirectConfig)

        assertThat(state.pendingSsoProvider).isEqualTo("openid")
        assertThat(state.ssoProvider).isNull()
    }

    @Test
    fun `OpenID auto-redirect fires once, so backing out of SSO stays on the form`() = runTest {
        every { ssoRiskDataStore.acknowledged } returns flowOf(true)
        stateFor(autoRedirectConfig)
        viewModel.consumeSsoNavigation()

        configFlow.value = autoRedirectConfig.copy(registrationEnabled = true)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.ssoProvider).isNull()
        assertThat(viewModel.uiState.value.pendingSsoProvider).isNull()
    }

    @Test
    fun `auto-redirect needs OpenID itself enabled`() = runTest {
        every { ssoRiskDataStore.acknowledged } returns flowOf(true)

        assertThat(stateFor(StartupConfig(openidAutoRedirect = true)).ssoProvider).isNull()
    }

    @Test
    fun `login shows loading state during request`() = runTest {
        coEvery { authRepository.login(any(), any()) } returns
            Result.Success(LoginOutcome.Success(User(email = "user@example.com")))

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEmailChanged("user@example.com")
        viewModel.onPasswordChanged("password123")

        // Before login completes, loading should be false initially
        assertThat(viewModel.uiState.value.isLoading).isFalse()

        viewModel.login()
        advanceUntilIdle()

        // After login completes, loading should be false again
        assertThat(viewModel.uiState.value.isLoading).isFalse()
    }

    @Test
    fun `first social tap raises the risk dialog`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onSsoProviderSelected("google")
        advanceUntilIdle()

        with(viewModel.uiState.value) {
            assertThat(pendingSsoProvider).isEqualTo("google")
            assertThat(ssoProvider).isNull()
        }
        coVerify(exactly = 0) { ssoRiskDataStore.acknowledge() }
    }

    @Test
    fun `accepting the risk persists it and navigates`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onSsoProviderSelected("google")
        advanceUntilIdle()
        viewModel.onSsoRiskAccepted()
        advanceUntilIdle()

        coVerify(exactly = 1) { ssoRiskDataStore.acknowledge() }
        with(viewModel.uiState.value) {
            assertThat(pendingSsoProvider).isNull()
            assertThat(ssoProvider).isEqualTo("google")
        }
    }

    @Test
    fun `an acknowledged risk navigates straight to sso`() = runTest {
        every { ssoRiskDataStore.acknowledged } returns flowOf(true)
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onSsoProviderSelected("openid")
        advanceUntilIdle()

        with(viewModel.uiState.value) {
            assertThat(pendingSsoProvider).isNull()
            assertThat(ssoProvider).isEqualTo("openid")
        }
    }

    @Test
    fun `a second tap does not queue a second navigation`() = runTest {
        every { ssoRiskDataStore.acknowledged } returns flowOf(true)
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onSsoProviderSelected("google")
        viewModel.onSsoProviderSelected("google")
        advanceUntilIdle()
        // The screen navigates and consumes; a queued second selection would re-arm it here.
        viewModel.consumeSsoNavigation()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.ssoProvider).isNull()
    }

    @Test
    fun `a second tap on a different provider does not win`() = runTest {
        every { ssoRiskDataStore.acknowledged } returns flowOf(true)
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onSsoProviderSelected("google")
        viewModel.onSsoProviderSelected("github")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.ssoProvider).isEqualTo("google")
    }

    @Test
    fun `accepting the risk twice persists and navigates once`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onSsoProviderSelected("google")
        advanceUntilIdle()
        viewModel.onSsoRiskAccepted()
        viewModel.onSsoRiskAccepted()
        advanceUntilIdle()

        coVerify(exactly = 1) { ssoRiskDataStore.acknowledge() }
        assertThat(viewModel.uiState.value.ssoProvider).isEqualTo("google")
    }

    @Test
    fun `an unreadable risk store still warns instead of stranding the button`() = runTest {
        every { ssoRiskDataStore.acknowledged } returns flow { throw IOException("datastore") }
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onSsoProviderSelected("google")
        advanceUntilIdle()

        with(viewModel.uiState.value) {
            assertThat(pendingSsoProvider).isEqualTo("google")
            assertThat(ssoProvider).isNull()
        }
    }

    @Test
    fun `a failed acknowledgement still navigates`() = runTest {
        coEvery { ssoRiskDataStore.acknowledge() } throws IOException("datastore")
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onSsoProviderSelected("google")
        advanceUntilIdle()
        viewModel.onSsoRiskAccepted()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.ssoProvider).isEqualTo("google")
    }

    @Test
    fun `dismissing the risk dialog navigates nowhere`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onSsoProviderSelected("google")
        advanceUntilIdle()
        viewModel.onSsoRiskDismissed()

        with(viewModel.uiState.value) {
            assertThat(pendingSsoProvider).isNull()
            assertThat(ssoProvider).isNull()
        }
        coVerify(exactly = 0) { ssoRiskDataStore.acknowledge() }
    }

    @Test
    fun `a wrong password does not offer the diagnostic log`() = runTest {
        coEvery { authRepository.login("user@example.com", "wrong") } returns Result.Error(
            exception = ApiException(401, "Incorrect password.", serverAuthored = true),
            message = "Incorrect password.",
        )
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEmailChanged("user@example.com")
        viewModel.onPasswordChanged("wrong")
        viewModel.login()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.offerDiagnosticLog).isFalse()
    }

    @Test
    fun `a connection failure offers the log, and a later input error withdraws it`() = runTest {
        coEvery { authRepository.login("user@example.com", "pw") } returns
            Result.Error(exception = IOException("unreachable"), message = "Network unavailable.")
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEmailChanged("user@example.com")
        viewModel.onPasswordChanged("pw")
        viewModel.login()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.offerDiagnosticLog).isTrue()

        viewModel.onPasswordChanged("")
        viewModel.login()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.error).isEqualTo("Please enter email and password")
        assertThat(viewModel.uiState.value.offerDiagnosticLog).isFalse()
    }
}
