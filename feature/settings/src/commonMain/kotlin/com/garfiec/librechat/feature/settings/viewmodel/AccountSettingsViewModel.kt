package com.garfiec.librechat.feature.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.ServerDataStore
import com.garfiec.librechat.core.data.repository.AuthRepository
import com.garfiec.librechat.core.data.repository.BalanceRepository
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.KeyRepository
import com.garfiec.librechat.core.data.repository.PasskeyRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.data.repository.UserRepository
import com.garfiec.librechat.core.model.User
import com.garfiec.librechat.core.model.media.resolveAvatarUrl
import com.garfiec.librechat.core.ui.media.AvatarImagePreparer
import com.garfiec.librechat.feature.settings.model.UserDisplayData
import com.garfiec.librechat.feature.settings.util.ContentReader
import com.garfiec.librechat.feature.settings.viewmodel.delegate.AccountDelegate
import com.garfiec.librechat.feature.settings.viewmodel.delegate.EmailChangeDelegate
import com.garfiec.librechat.feature.settings.viewmodel.delegate.ProviderKeysSummaryDelegate
import com.garfiec.librechat.feature.settings.viewmodel.delegate.TwoFactorSecurityDelegate
import com.garfiec.librechat.feature.settings.viewmodel.delegate.isTwoFactorPolicyProvider
import com.garfiec.librechat.feature.settings.viewmodel.delegate.isTwoFactorRequiredFor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

/** Backs the Account page and the Settings home's profile card: profile, avatar, balance, two-factor, sign out and account deletion. */
@Suppress("LongParameterList", "TooManyFunctions") // one-line forwarders to the two delegates
class AccountSettingsViewModel(
    userRepository: UserRepository,
    authRepository: AuthRepository,
    balanceRepository: BalanceRepository,
    contentReader: ContentReader,
    avatarImagePreparer: AvatarImagePreparer,
    configRepository: ConfigRepository,
    serverDataStore: ServerDataStore,
    keyRepository: KeyRepository,
    roleRepository: RoleRepository,
    private val passkeyRepository: PasskeyRepository,
    ioDispatcher: CoroutineDispatcher,
    clock: Clock = Clock.System,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountSettingsUiState())
    private val stateHandle = SettingsStateHandle(_uiState, viewModelScope)

    private val accountDelegate = AccountDelegate(
        stateHandle,
        userRepository,
        balanceRepository,
        contentReader,
        avatarImagePreparer,
        ioDispatcher,
    )
    private val twoFactorDelegate = TwoFactorSecurityDelegate(stateHandle, authRepository)
    private val emailChangeDelegate = EmailChangeDelegate(stateHandle, userRepository)
    private val providerKeysSummaryDelegate =
        ProviderKeysSummaryDelegate(configRepository, roleRepository, keyRepository, viewModelScope, clock)

    /**
     * `allowAccountDeletion` defaults to `true` (older-server behaviour) when the field is absent
     * or the config hasn't loaded yet — see VERSION_GATES.md guideline #2.
     *
     * The avatar is resolved here because local storage hands back a relative `/images/…` path,
     * which Coil cannot load; `_uiState` keeps what the server sent.
     */
    val uiState: StateFlow<AccountSettingsUiState> =
        combine(
            _uiState,
            configRepository.startupConfig,
            serverDataStore.currentUrlFlow,
            providerKeysSummaryDelegate.summary,
            configRepository.detectedBackend,
        ) { state, config, baseUrl, providerKeysSummary, backend ->
            val showPasskeys = isPasskeyManagementAvailable(config, backend)
            state.copy(
                user = state.user?.let { it.copy(avatar = resolveAvatarUrl(it.avatar, baseUrl)) },
                allowAccountDeletion = config?.allowAccountDeletion ?: true,
                balanceEnabled = config?.balance?.enabled == true,
                balance = state.balance.presentedFor(config),
                providerKeysSummary = providerKeysSummary,
                canDisableTwoFactor = !isTwoFactorRequiredFor(config?.twoFactorAuthenticationRequired, state.user?.provider),
                showTwoFactor = state.user?.let { isTwoFactorPolicyProvider(it.provider) } == true,
                canChangeEmail = isEmailChangeAvailable(config, state.user?.provider, backend),
                showPasskeys = showPasskeys,
                passkeyCount = state.passkeyCount.takeIf { showPasskeys },
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, AccountSettingsUiState())

    init {
        accountDelegate.loadUser()
        // Fetched only once the config says balance tracking is on; the config may land after init.
        viewModelScope.launch {
            configRepository.startupConfig
                .map { it?.balance?.enabled == true }
                .distinctUntilChanged()
                .filter { it }
                .collect { accountDelegate.loadBalance() }
        }
        // The Passkeys row's count rides the profile load; the gate may land after init.
        viewModelScope.launch {
            combine(configRepository.startupConfig, configRepository.detectedBackend) { config, backend ->
                isPasskeyManagementAvailable(config, backend)
            }
                .distinctUntilChanged()
                .filter { it }
                .collect { loadPasskeyCount() }
        }
    }

    private var passkeyCountJob: Job? = null

    private fun loadPasskeyCount() {
        passkeyCountJob?.cancel()
        passkeyCountJob = viewModelScope.launch {
            val count = (passkeyRepository.list() as? Result.Success)?.data?.size
            _uiState.update { it.copy(passkeyCount = count) }
        }
    }

    /**
     * A return to the tab (back from a page, or from another app): re-reads the profile quietly, so
     * an avatar, name or email changed on the web shows up, and the passkey count and balance with it.
     */
    fun refreshOnReturn() {
        accountDelegate.refreshQuietly(emailChangeDelegate::onProfileRefreshed)
        if (uiState.value.showPasskeys) loadPasskeyCount()
        refreshBalance()
    }

    /** Re-reads the balance quietly, keeping the shown one, when the server tracks balances. */
    fun refreshBalance() {
        if (uiState.value.balanceEnabled) accountDelegate.refreshBalanceQuietly()
    }

    private var providerKeysSummaryRequested = false

    /** The tab is showing: loads the Provider Keys row's summary the first time only. */
    fun loadProviderKeysSummary() {
        if (!providerKeysSummaryRequested) refreshProviderKeysSummary()
    }

    /** A resume of the screen (back from the Provider Keys page or another app): re-reads the summary. */
    fun refreshProviderKeysSummary() {
        providerKeysSummaryRequested = true
        providerKeysSummaryDelegate.refresh()
    }

    // Profile & avatar
    fun retry() = accountDelegate.retry()
    fun retryBalance() = accountDelegate.loadBalance()
    fun showAvatarDialog() = accountDelegate.showAvatarDialog()
    fun dismissAvatarDialog() = accountDelegate.dismissAvatarDialog()
    fun uploadAvatar(uri: Any) = accountDelegate.uploadAvatar(uri)
    fun dismissError() = accountDelegate.dismissError()

    // Email change
    fun openEmailChange() = emailChangeDelegate.open()
    fun dismissEmailChange() = emailChangeDelegate.dismiss()
    fun onEmailChangeNewEmail(value: String) = emailChangeDelegate.onNewEmailChanged(value)
    fun onEmailChangePassword(value: String) = emailChangeDelegate.onPasswordChanged(value)
    fun sendEmailChange() = emailChangeDelegate.send()
    fun consumeEmailChangeNotice() = emailChangeDelegate.consumeNotice()

    // Sign out & deletion
    fun logout() = accountDelegate.logout()
    fun deleteAccount(token: String? = null, backupCode: String? = null) =
        accountDelegate.deleteAccount(token = token, backupCode = backupCode)
    fun dismissDeleteAccountOtpDialog() = accountDelegate.dismissDeleteAccountOtpDialog()

    // Two-factor security
    fun toggleTwoFactor() = twoFactorDelegate.toggleTwoFactor()
    fun enableTwoFactorWithOtp(token: String?, backupCode: String?) =
        twoFactorDelegate.enableTwoFactor(token = token, backupCode = backupCode)
    fun dismissEnableTwoFactorOtpDialog() = twoFactorDelegate.dismissEnableTwoFactorOtpDialog()
    fun confirmEnableTwoFactor(code: String) = twoFactorDelegate.confirmEnableTwoFactor(code)
    fun confirmDisableTwoFactor(token: String?, backupCode: String?) =
        twoFactorDelegate.confirmDisableTwoFactor(token = token, backupCode = backupCode)
    fun dismissTwoFactorSetupDialog() = twoFactorDelegate.dismissTwoFactorSetupDialog()
    fun dismissDisableTwoFactorDialog() = twoFactorDelegate.dismissDisableTwoFactorDialog()
    fun dismissBackupCodesDialog() = twoFactorDelegate.dismissBackupCodesDialog()
    fun requestRegenerateBackupCodes() = twoFactorDelegate.requestRegenerateBackupCodes()
    fun dismissRegenerateBackupCodesConfirm() = twoFactorDelegate.dismissRegenerateBackupCodesConfirm()
    fun confirmRegenerateBackupCodes() = twoFactorDelegate.confirmRegenerateBackupCodes()
    fun regenerateBackupCodesWithOtp(token: String?, backupCode: String?) =
        twoFactorDelegate.regenerateBackupCodes(token = token, backupCode = backupCode)
    fun dismissBackupCodesOtpDialog() = twoFactorDelegate.dismissBackupCodesOtpDialog()
}

internal fun User.toDisplayData() = UserDisplayData(
    name = name ?: "",
    email = email,
    username = username ?: "",
    avatar = avatar,
    provider = provider,
)
