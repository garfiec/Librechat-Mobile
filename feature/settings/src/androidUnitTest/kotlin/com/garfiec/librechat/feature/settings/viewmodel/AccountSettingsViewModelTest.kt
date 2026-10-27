package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.ServerDataStore
import com.garfiec.librechat.core.data.repository.AuthRepository
import com.garfiec.librechat.core.data.repository.BalanceRepository
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.KeyRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.data.repository.UserRepository
import com.garfiec.librechat.core.model.Balance
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.User
import com.garfiec.librechat.core.model.config.BalanceConfig
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.model.endpoint.KeyState
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import com.garfiec.librechat.core.model.response.BackupCodesResponse
import com.garfiec.librechat.core.ui.media.PreparedImage
import com.garfiec.librechat.feature.settings.model.AutoRefillInfo
import com.garfiec.librechat.feature.settings.util.ContentReader
import com.garfiec.librechat.feature.settings.viewmodel.delegate.ProviderKeysSummary
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
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class AccountSettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val contentReader = mockk<ContentReader>(relaxed = true)
    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val authRepository = mockk<AuthRepository>(relaxed = true)
    private val balanceRepository = mockk<BalanceRepository>(relaxed = true)
    private val configRepository = mockk<ConfigRepository>(relaxed = true)
    private val startupConfig = MutableStateFlow<StartupConfig?>(null)
    private val endpointConfigs = MutableStateFlow<Map<String, EndpointConfig>>(emptyMap())
    private val keyRepository = mockk<KeyRepository>(relaxed = true)
    private val roleRepository = mockk<RoleRepository> {
        every { userPermissions } returns MutableStateFlow<UserRolePermissions?>(null)
    }
    private val serverDataStore = mockk<ServerDataStore> {
        every { currentUrlFlow } returns MutableStateFlow("https://chat.example.com")
    }
    private var preparedImage: PreparedImage? = null

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
        every { configRepository.endpointConfigs } returns endpointConfigs
        coEvery { keyRepository.fetchKeyState(any()) } returns Result.Success(KeyState.Unset)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(clock: Clock = Clock.System) = AccountSettingsViewModel(
        userRepository = userRepository,
        authRepository = authRepository,
        balanceRepository = balanceRepository,
        contentReader = contentReader,
        avatarImagePreparer = { preparedImage },
        configRepository = configRepository,
        serverDataStore = serverDataStore,
        keyRepository = keyRepository,
        roleRepository = roleRepository,
        ioDispatcher = testDispatcher,
        clock = clock,
    )

    @Test
    fun `provider keys row is hidden when no endpoint takes a user value`() = runTest {
        endpointConfigs.value = mapOf("openAI" to EndpointConfig(userProvide = false))
        val viewModel = createViewModel()
        viewModel.refreshProviderKeysSummary()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.providerKeysSummary).isEqualTo(ProviderKeysSummary.Hidden)
    }

    @Test
    fun `provider keys summary counts live keys over listed endpoints`() = runTest {
        endpointConfigs.value = mapOf(
            "openAI" to EndpointConfig(userProvide = true),
            "anthropic" to EndpointConfig(userProvide = true),
            "selfhosted" to EndpointConfig(userProvideURL = true),
            "google" to EndpointConfig(userProvide = false),
        )
        coEvery { keyRepository.fetchKeyState("openAI") } returns
            Result.Success(KeyState.Set(expiresAt = null, neverExpires = true, wire = "never"))
        coEvery { keyRepository.fetchKeyState("anthropic") } returns Result.Success(EXPIRED_KEY)
        val viewModel = createViewModel()
        // Listed before any key state is known: the row shows the general line until refreshed.
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.providerKeysSummary).isEqualTo(ProviderKeysSummary.General)

        viewModel.refreshProviderKeysSummary()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.providerKeysSummary)
            .isEqualTo(ProviderKeysSummary.Count(setCount = 1, totalCount = 3))
    }

    @Test
    fun `provider keys summary does not count a key past its expiry`() = runTest {
        val now = Instant.parse("2026-10-26T12:00:00Z")
        val fixedClock = object : Clock {
            override fun now(): Instant = now
        }
        endpointConfigs.value = mapOf(
            "openAI" to EndpointConfig(userProvide = true),
            "anthropic" to EndpointConfig(userProvide = true),
        )
        // Both read as Set on the wire; the server deletes an expired key only about a minute later.
        val lapsed = now - 1.minutes
        val live = now + 1.hours
        coEvery { keyRepository.fetchKeyState("openAI") } returns
            Result.Success(KeyState.Set(expiresAt = lapsed, neverExpires = false, wire = lapsed.toString()))
        coEvery { keyRepository.fetchKeyState("anthropic") } returns
            Result.Success(KeyState.Set(expiresAt = live, neverExpires = false, wire = live.toString()))
        val viewModel = createViewModel(fixedClock)
        viewModel.refreshProviderKeysSummary()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.providerKeysSummary)
            .isEqualTo(ProviderKeysSummary.Count(setCount = 1, totalCount = 2))
    }

    @Test
    fun `provider keys summary loads once on first need and re-reads on a resume`() = runTest {
        endpointConfigs.value = mapOf("openAI" to EndpointConfig(userProvide = true))
        val viewModel = createViewModel()
        advanceUntilIdle()

        // The pager composing the tab, then composing it again after a swipe away and back.
        viewModel.loadProviderKeysSummary()
        advanceUntilIdle()
        viewModel.loadProviderKeysSummary()
        advanceUntilIdle()
        coVerify(exactly = 1) { keyRepository.fetchKeyState("openAI") }

        viewModel.refreshProviderKeysSummary()
        advanceUntilIdle()
        coVerify(exactly = 2) { keyRepository.fetchKeyState("openAI") }
    }

    @Test
    fun `provider keys summary keeps the last answer when a refresh fails`() = runTest {
        endpointConfigs.value = mapOf("openAI" to EndpointConfig(userProvide = true))
        coEvery { keyRepository.fetchKeyState("openAI") } returns
            Result.Success(KeyState.Set(expiresAt = null, neverExpires = true, wire = "never"))
        val viewModel = createViewModel()
        viewModel.refreshProviderKeysSummary()
        advanceUntilIdle()

        coEvery { keyRepository.fetchKeyState("openAI") } returns Result.Error(message = "offline")
        viewModel.refreshProviderKeysSummary()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.providerKeysSummary).isEqualTo(ProviderKeysSummary.Count(setCount = 1, totalCount = 1))
    }

    @Test
    fun `provider keys row shows and retries the configs while none has loaded`() = runTest {
        // Offline at launch: no endpoint config, which is not the same as none taking a key.
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.providerKeysSummary).isEqualTo(ProviderKeysSummary.General)

        viewModel.refreshProviderKeysSummary()
        advanceUntilIdle()

        coVerify(exactly = 1) { configRepository.fetchEndpoints() }
        coVerify(exactly = 0) { keyRepository.fetchKeyState(any()) }
    }

    @Test
    fun `provider keys summary follows an endpoint config change`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.providerKeysSummary).isEqualTo(ProviderKeysSummary.General)

        endpointConfigs.value = mapOf("openAI" to EndpointConfig(userProvide = true))
        advanceUntilIdle()

        // The change itself re-reads key states, without waiting for a resume.
        assertThat(viewModel.uiState.value.providerKeysSummary).isEqualTo(ProviderKeysSummary.Count(setCount = 0, totalCount = 1))
    }

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
    fun `2FA can be turned off with a backup code`() = runTest {
        coEvery { userRepository.getUser() } returns Result.Success(testUser.copy(twoFactorEnabled = true))
        coEvery { authRepository.disableTwoFactor(token = null, backupCode = "abcd1234") } returns Result.Success(Unit)

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.toggleTwoFactor()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.showDisableTwoFactorDialog).isTrue()

        viewModel.confirmDisableTwoFactor(token = null, backupCode = "abcd1234")
        advanceUntilIdle()

        coVerify { authRepository.disableTwoFactor(token = null, backupCode = "abcd1234") }
        assertThat(viewModel.uiState.value.isTwoFactorEnabled).isFalse()
        assertThat(viewModel.uiState.value.showDisableTwoFactorDialog).isFalse()
    }

    @Test
    fun `regenerating backup codes asks for confirmation, then the code, before any request`() = runTest {
        coEvery { userRepository.getUser() } returns Result.Success(testUser.copy(twoFactorEnabled = true))
        coEvery { authRepository.regenerateBackupCodes(token = "123456", backupCode = null) } returns
            Result.Success(BackupCodesResponse(backupCodes = listOf("code-1", "code-2")))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.requestRegenerateBackupCodes()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.showRegenerateBackupCodesConfirm).isTrue()
        coVerify(exactly = 0) { authRepository.regenerateBackupCodes(any(), any()) }

        viewModel.confirmRegenerateBackupCodes()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.showRegenerateBackupCodesConfirm).isFalse()
        assertThat(viewModel.uiState.value.showBackupCodesOtpDialog).isTrue()
        coVerify(exactly = 0) { authRepository.regenerateBackupCodes(any(), any()) }

        viewModel.regenerateBackupCodesWithOtp(token = "123456", backupCode = null)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.showBackupCodesOtpDialog).isFalse()
        assertThat(viewModel.uiState.value.showBackupCodesDialog).isTrue()
        assertThat(viewModel.uiState.value.backupCodes).containsExactly("code-1", "code-2")
    }

    @Test
    fun `cancelling the regenerate confirmation leaves the codes alone`() = runTest {
        coEvery { userRepository.getUser() } returns Result.Success(testUser.copy(twoFactorEnabled = true))

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.requestRegenerateBackupCodes()
        viewModel.dismissRegenerateBackupCodesConfirm()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.showRegenerateBackupCodesConfirm).isFalse()
        assertThat(viewModel.uiState.value.showBackupCodesOtpDialog).isFalse()
        coVerify(exactly = 0) { authRepository.regenerateBackupCodes(any(), any()) }
    }

    @Test
    fun `deleteAccount asks for the 2FA code before calling the server when 2FA is on`() = runTest {
        coEvery { userRepository.getUser() } returns Result.Success(testUser.copy(twoFactorEnabled = true))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.deleteAccount()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.showDeleteAccountOtpDialog).isTrue()
        assertThat(viewModel.uiState.value.error).isNull()
        coVerify(exactly = 0) { userRepository.deleteUser(any(), any()) }
    }

    @Test
    fun `deleteAccount sends the collected code and closes the prompt on success`() = runTest {
        coEvery { userRepository.getUser() } returns Result.Success(testUser.copy(twoFactorEnabled = true))
        coEvery { userRepository.deleteUser(token = "123456", backupCode = null) } returns Result.Success(Unit)

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.deleteAccount()
        advanceUntilIdle()

        viewModel.deleteAccount(token = "123456")
        advanceUntilIdle()

        coVerify { userRepository.deleteUser(token = "123456", backupCode = null) }
        assertThat(viewModel.uiState.value.isAccountDeleted).isTrue()
        assertThat(viewModel.uiState.value.showDeleteAccountOtpDialog).isFalse()
    }

    @Test
    fun `a 400 asking for the code opens the prompt even when the profile says 2FA is off`() = runTest {
        val message = "TOTP token or backup code is required to delete account with 2FA enabled"
        coEvery { userRepository.deleteUser() } returns Result.Error(
            exception = ApiException(statusCode = 400, message = message, serverAuthored = true),
            message = message,
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.deleteAccount()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.showDeleteAccountOtpDialog).isTrue()
        assertThat(viewModel.uiState.value.error).isNull()
    }

    @Test
    fun `a 403 shows the server's refusal instead of the code prompt`() = runTest {
        val message = "You do not have permission to delete this account"
        coEvery { userRepository.deleteUser() } returns Result.Error(
            exception = ApiException(statusCode = 403, message = message, serverAuthored = true),
            message = message,
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.deleteAccount()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.showDeleteAccountOtpDialog).isFalse()
        assertThat(viewModel.uiState.value.error).isEqualTo(message)
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
        coEvery { balanceRepository.getBalance() } returns Result.Success(Balance(tokenCredits = 1234.567))
        val viewModel = createViewModel()
        advanceUntilIdle()

        startupConfig.value = StartupConfig(balance = BalanceConfig(enabled = true))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.balanceEnabled).isTrue()
        assertThat(viewModel.uiState.value.balance)
            .isEqualTo(BalanceUiState.Loaded(tokenCredits = 1234.567, autoRefill = AutoRefillInfo.Off))
        coVerify(exactly = 1) { balanceRepository.getBalance() }
    }

    @Test
    fun `a failed balance load shows an error instead of a zero, and retry recovers`() = runTest {
        coEvery { balanceRepository.getBalance() } returns Result.Error(RuntimeException("boom"), "boom")
        val viewModel = createViewModel()
        startupConfig.value = StartupConfig(balance = BalanceConfig(enabled = true))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.balance).isEqualTo(BalanceUiState.Error)

        coEvery { balanceRepository.getBalance() } returns Result.Success(Balance(tokenCredits = 5.0))
        viewModel.retryBalance()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.balance)
            .isEqualTo(BalanceUiState.Loaded(tokenCredits = 5.0, autoRefill = AutoRefillInfo.Off))
    }

    @Test
    fun `a relative local-storage avatar is resolved against the server url`() = runTest {
        coEvery { userRepository.getUser() } returns
            Result.Success(testUser.copy(avatar = "/images/u1/avatar-1.png?manual=true"))

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.user?.avatar)
            .isEqualTo("https://chat.example.com/images/u1/avatar-1.png?manual=true")
    }

    @Test
    fun `uploading sends the prepared image and shows the new avatar`() = runTest {
        every { contentReader.readBytes(any()) } returns byteArrayOf(1, 2, 3)
        preparedImage = PreparedImage.jpeg(byteArrayOf(9))
        coEvery { userRepository.uploadAvatar(any(), any(), any()) } returns
            Result.Success("/images/u1/avatar-2.png?manual=true")
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.showAvatarDialog()
        viewModel.uploadAvatar(Any())
        advanceUntilIdle()

        coVerify { userRepository.uploadAvatar(byteArrayOf(9), "image/jpeg", "avatar.jpg") }
        val state = viewModel.uiState.value
        assertThat(state.user?.avatar).isEqualTo("https://chat.example.com/images/u1/avatar-2.png?manual=true")
        assertThat(state.showAvatarDialog).isFalse()
        assertThat(state.isAvatarUploading).isFalse()
        assertThat(state.error).isNull()
    }

    @Test
    fun `an image still over the server limit after preparing is refused without uploading`() = runTest {
        every { contentReader.readBytes(any()) } returns byteArrayOf(1)
        preparedImage = PreparedImage.png(ByteArray(2 * 1024 * 1024 + 1))
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.uploadAvatar(Any())
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.error).isEqualTo("Image is too large to use as a profile picture")
        assertThat(viewModel.uiState.value.isAvatarUploading).isFalse()
        coVerify(exactly = 0) { userRepository.uploadAvatar(any(), any(), any()) }
    }

    @Test
    fun `a file that is not a decodable image is refused without uploading`() = runTest {
        every { contentReader.readBytes(any()) } returns byteArrayOf(1)
        preparedImage = null
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.uploadAvatar(Any())
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.error).isEqualTo("Could not read selected image")
        coVerify(exactly = 0) { userRepository.uploadAvatar(any(), any(), any()) }
    }
}

private val EXPIRED_KEY = KeyState.Expired(
    expiresAt = Instant.parse("2026-01-01T00:00:00Z"),
    wire = "2026-01-01T00:00:00Z",
)
