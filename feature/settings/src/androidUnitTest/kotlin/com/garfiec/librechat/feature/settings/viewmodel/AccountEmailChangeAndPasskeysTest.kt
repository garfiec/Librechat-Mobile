package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.common.DetectedBackend
import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.FailureKind
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
import com.garfiec.librechat.core.model.auth.EmailChangeError
import com.garfiec.librechat.core.model.auth.Passkey
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.account_profile_load_failed
import com.garfiec.librechat.feature.settings.util.ContentReader
import com.garfiec.librechat.feature.settings.viewmodel.delegate.EmailChangeNotice
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
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

/** The Account tab's email change (dialog, codes, cooldown, resume notice) and Passkeys row. */
class AccountEmailChangeAndPasskeysTest {

    private val testDispatcher = StandardTestDispatcher()
    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val configRepository = mockk<ConfigRepository>(relaxed = true)
    private val passkeyRepository = mockk<PasskeyRepository>(relaxed = true)
    private val startupConfig = MutableStateFlow<StartupConfig?>(
        StartupConfig(emailEnabled = true, allowEmailChange = true),
    )
    private val detectedBackend = MutableStateFlow<DetectedBackend?>(null)
    private val roleRepository = mockk<RoleRepository> {
        every { userPermissions } returns MutableStateFlow<UserRolePermissions?>(null)
    }
    private val serverDataStore = mockk<ServerDataStore> {
        every { currentUrlFlow } returns MutableStateFlow("https://chat.example.com")
    }
    private val localUser = User(email = "maya@example.com", provider = "local")

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        coEvery { userRepository.getUser() } returns Result.Success(localUser)
        every { configRepository.startupConfig } returns startupConfig
        every { configRepository.endpointConfigs } returns MutableStateFlow<Map<String, EndpointConfig>>(emptyMap())
        every { configRepository.detectedBackend } returns detectedBackend
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = AccountSettingsViewModel(
        userRepository = userRepository,
        authRepository = mockk<AuthRepository>(relaxed = true),
        balanceRepository = mockk<BalanceRepository>(relaxed = true),
        contentReader = mockk<ContentReader>(relaxed = true),
        avatarImagePreparer = { null },
        configRepository = configRepository,
        serverDataStore = serverDataStore,
        keyRepository = mockk<KeyRepository>(relaxed = true),
        roleRepository = roleRepository,
        passkeyRepository = passkeyRepository,
        ioDispatcher = testDispatcher,
    )

    private fun refusal(status: Int, code: String?, retryAfter: Long? = null) = Result.Error(
        exception = ApiException(
            statusCode = status,
            message = "refused",
            body = code?.let { """{"message":"x","code":"$it"}""" } ?: """{"message":"x"}""",
            retryAfterSeconds = retryAfter,
        ),
        message = "refused",
    )

    private fun AccountSettingsViewModel.fill(email: String = "new@example.com", password: String = "pw") {
        openEmailChange()
        onEmailChangeNewEmail(email)
        onEmailChangePassword(password)
    }

    // ── Gate ───────────────────────────────────────────────────────

    @Test
    fun `email row shows for a local account when the server allows the change`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.canChangeEmail).isTrue()
    }

    @Test
    fun `email row is hidden for a federated or unknown provider`() = runTest {
        coEvery { userRepository.getUser() } returns Result.Success(localUser.copy(provider = "openid"))
        val federated = createViewModel()
        advanceUntilIdle()
        assertThat(federated.uiState.value.canChangeEmail).isFalse()

        coEvery { userRepository.getUser() } returns Result.Success(localUser.copy(provider = null))
        val unknown = createViewModel()
        advanceUntilIdle()
        assertThat(unknown.uiState.value.canChangeEmail).isFalse()
    }

    @Test
    fun `email row is hidden without mail or without the server flag`() = runTest {
        startupConfig.value = StartupConfig(emailEnabled = false, allowEmailChange = true)
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.canChangeEmail).isFalse()

        startupConfig.value = StartupConfig(emailEnabled = true)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.canChangeEmail).isFalse()
    }

    // ── Request ────────────────────────────────────────────────────

    @Test
    fun `a sent link closes the dialog and names the new address`() = runTest {
        coEvery { userRepository.requestEmailChange(any(), any()) } returns Result.Success(Unit)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.fill(email = "  new@example.com ")
        viewModel.sendEmailChange()
        advanceUntilIdle()

        coVerify { userRepository.requestEmailChange("new@example.com", "pw") }
        assertThat(viewModel.uiState.value.emailChange).isNull()
        assertThat(viewModel.uiState.value.emailChangeNotice)
            .isEqualTo(EmailChangeNotice.LinkSent("new@example.com"))
    }

    @Test
    fun `the same address is refused before sending, ignoring case`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.fill(email = "MAYA@example.com")
        viewModel.sendEmailChange()
        advanceUntilIdle()

        coVerify(exactly = 0) { userRepository.requestEmailChange(any(), any()) }
        assertThat(viewModel.uiState.value.emailChange?.emailError).isEqualTo(EmailChangeError.SameEmail)
    }

    @Test
    fun `a wrong password clears only the password and keeps the dialog`() = runTest {
        coEvery { userRepository.requestEmailChange(any(), any()) } returns refusal(403, "current_password_invalid")
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.fill()
        viewModel.sendEmailChange()
        advanceUntilIdle()

        val draft = viewModel.uiState.value.emailChange!!
        assertThat(draft.passwordError).isEqualTo(EmailChangeError.CurrentPasswordInvalid)
        assertThat(draft.password).isEmpty()
        assertThat(draft.newEmail).isEqualTo("new@example.com")
        assertThat(draft.passwordResets).isEqualTo(1)
        assertThat(draft.isSending).isFalse()
    }

    @Test
    fun `a dialog-level code shows in the dialog and keeps both fields`() = runTest {
        coEvery { userRepository.requestEmailChange(any(), any()) } returns refusal(503, "email_service_unavailable")
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.fill()
        viewModel.sendEmailChange()
        advanceUntilIdle()

        val draft = viewModel.uiState.value.emailChange!!
        assertThat(draft.dialogError).isEqualTo(EmailChangeError.ServiceUnavailable)
        assertThat(draft.password).isEqualTo("pw")
    }

    @Test
    fun `an unknown code reads as the generic failure`() = runTest {
        coEvery { userRepository.requestEmailChange(any(), any()) } returns refusal(500, null)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.fill()
        viewModel.sendEmailChange()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.emailChange?.dialogError).isEqualTo(EmailChangeError.Unknown)
    }

    @Test
    fun `offline reads as a network failure`() = runTest {
        coEvery { userRepository.requestEmailChange(any(), any()) } returns
            Result.Error(message = "offline", kind = FailureKind.Network)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.fill()
        viewModel.sendEmailChange()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.emailChange?.dialogError).isEqualTo(EmailChangeError.Network)
    }

    @Test
    fun `a 429 holds Send off for Retry-After, then lets it go`() = runTest {
        coEvery { userRepository.requestEmailChange(any(), any()) } returns refusal(429, null, retryAfter = 30)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.fill()
        viewModel.sendEmailChange()
        advanceTimeBy(1_000)
        assertThat(viewModel.uiState.value.emailChange?.dialogError).isEqualTo(EmailChangeError.RateLimited)
        assertThat(viewModel.uiState.value.emailChangeCoolingDown).isTrue()

        viewModel.sendEmailChange()
        advanceTimeBy(1_000)
        coVerify(exactly = 1) { userRepository.requestEmailChange(any(), any()) }

        advanceTimeBy(30_000)
        assertThat(viewModel.uiState.value.emailChangeCoolingDown).isFalse()
    }

    @Test
    fun `dismissing is ignored while sending, and clears the draft otherwise`() = runTest {
        coEvery { userRepository.requestEmailChange(any(), any()) } coAnswers {
            kotlinx.coroutines.delay(1_000)
            refusal(409, "email_in_use")
        }
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.fill()
        viewModel.sendEmailChange()
        advanceTimeBy(10)
        viewModel.dismissEmailChange()
        advanceTimeBy(10)
        assertThat(viewModel.uiState.value.emailChange).isNotNull()

        advanceUntilIdle()
        assertThat(viewModel.uiState.value.emailChange?.emailError).isEqualTo(EmailChangeError.EmailInUse)
        viewModel.dismissEmailChange()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.emailChange).isNull()
        viewModel.openEmailChange()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.emailChange?.newEmail).isEmpty()
    }

    // ── Resume ─────────────────────────────────────────────────────

    @Test
    fun `a resume re-reads the profile and announces a changed address only after a request`() = runTest {
        coEvery { userRepository.requestEmailChange(any(), any()) } returns Result.Success(Unit)
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Changed elsewhere, with no request from here: shown, not announced.
        coEvery { userRepository.getUser() } returns Result.Success(localUser.copy(email = "other@example.com"))
        viewModel.refreshOnReturn()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.user?.email).isEqualTo("other@example.com")
        assertThat(viewModel.uiState.value.emailChangeNotice).isNull()

        viewModel.fill()
        viewModel.sendEmailChange()
        advanceUntilIdle()
        viewModel.consumeEmailChangeNotice()

        coEvery { userRepository.getUser() } returns Result.Success(localUser.copy(email = "new@example.com"))
        viewModel.refreshOnReturn()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.user?.email).isEqualTo("new@example.com")
        assertThat(viewModel.uiState.value.emailChangeNotice).isEqualTo(EmailChangeNotice.Updated)
    }

    @Test
    fun `a failed resume refresh keeps the profile and raises no banner`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { userRepository.getUser() } returns Result.Error(message = "offline")
        viewModel.refreshOnReturn()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.user?.email).isEqualTo("maya@example.com")
        assertThat(viewModel.uiState.value.profileLoadError).isNull()
    }

    @Test
    fun `a failed resume refresh with no profile yet shows the localized banner, not the server text`() = runTest {
        coEvery { userRepository.getUser() } returns Result.Error(message = "offline")
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.refreshOnReturn()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.user).isNull()
        assertThat(viewModel.uiState.value.profileLoadError).isEqualTo(Res.string.account_profile_load_failed)
    }

    // ── Passkeys row ───────────────────────────────────────────────

    @Test
    fun `passkeys row follows the server flag and counts the list`() = runTest {
        coEvery { passkeyRepository.list() } returns Result.Success(
            listOf(Passkey(id = "a", name = "Pixel"), Passkey(id = "b", name = "YubiKey")),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.showPasskeys).isFalse()
        coVerify(exactly = 0) { passkeyRepository.list() }

        startupConfig.value = StartupConfig(passkeyLoginEnabled = true)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.showPasskeys).isTrue()
        assertThat(viewModel.uiState.value.passkeyCount).isEqualTo(2)
    }

    @Test
    fun `a failed count falls back to the general line`() = runTest {
        startupConfig.value = StartupConfig(passkeyLoginEnabled = true)
        coEvery { passkeyRepository.list() } returns Result.Error(message = "offline")
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.showPasskeys).isTrue()
        assertThat(viewModel.uiState.value.passkeyCount).isNull()
    }
}
