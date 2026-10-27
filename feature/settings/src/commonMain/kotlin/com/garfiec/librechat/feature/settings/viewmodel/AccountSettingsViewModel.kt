package com.garfiec.librechat.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.data.datastore.ServerDataStore
import com.garfiec.librechat.core.data.repository.AuthRepository
import com.garfiec.librechat.core.data.repository.BalanceRepository
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.KeyRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.data.repository.UserRepository
import com.garfiec.librechat.core.model.User
import com.garfiec.librechat.core.model.media.resolveAvatarUrl
import com.garfiec.librechat.core.ui.media.AvatarImagePreparer
import com.garfiec.librechat.feature.settings.model.AutoRefillInfo
import com.garfiec.librechat.feature.settings.model.UserDisplayData
import com.garfiec.librechat.feature.settings.util.ContentReader
import com.garfiec.librechat.feature.settings.viewmodel.delegate.AccountDelegate
import com.garfiec.librechat.feature.settings.viewmodel.delegate.ProviderKeysSummary
import com.garfiec.librechat.feature.settings.viewmodel.delegate.ProviderKeysSummaryDelegate
import com.garfiec.librechat.feature.settings.viewmodel.delegate.TwoFactorSecurityDelegate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock

@Immutable
data class AccountSettingsUiState(
    val user: UserDisplayData? = null,
    /**
     * Sticky inline error for profile-fetch failures. Rendered as an `ErrorBanner` inside
     * the Account section so the rest of the screen (Sign Out, Delete Account, etc.) stays
     * reachable while the server is unreachable. Cleared at the start of each `loadUser()`.
     */
    val profileLoadError: String? = null,
    /**
     * Transient errors surfaced via snackbar; cleared by [AccountSettingsViewModel.dismissError]
     * after the snackbar is shown/acted on. Used for one-shot failures: avatar upload,
     * account-deletion submit, 2FA mutations, etc. Do NOT use this for profile-fetch
     * failures — those are sticky and must remain visible until the user retries.
     * See [profileLoadError].
     */
    val error: String? = null,
    /** True only when the authenticated user's system role is ADMIN. Gates the
     *  admin role-skills row fail-CLOSED (defaults false until the profile loads). */
    val isAdmin: Boolean = false,
    // Balance
    /**
     * Mirrors `StartupConfig.balance.enabled`, as the web client does. With balance tracking off the
     * server answers `GET /api/balance` with an empty 204, so the section is hidden rather than
     * showing a zero it never sent.
     */
    val balanceEnabled: Boolean = false,
    val balance: BalanceUiState = BalanceUiState.Loading,
    // Avatar
    val showAvatarDialog: Boolean = false,
    val isAvatarUploading: Boolean = false,
    // Sign out & deletion
    val isLoggedOut: Boolean = false,
    val isDeletingAccount: Boolean = false,
    val isAccountDeleted: Boolean = false,
    val showDeleteAccountOtpDialog: Boolean = false,
    /**
     * Mirrors `StartupConfig.allowAccountDeletion`. New in v0.8.5 — older servers
     * don't send the flag, so it defaults to `true` and the Delete Account button
     * stays visible. When the server sends `false`, mobile hides the button to
     * avoid a guaranteed 403 on submission.
     */
    val allowAccountDeletion: Boolean = true,
    // Security (2FA)
    val isTwoFactorEnabled: Boolean = false,
    val isTwoFactorLoading: Boolean = false,
    val showTwoFactorSetupDialog: Boolean = false,
    val twoFactorOtpauthUrl: String? = null,
    val showBackupCodesDialog: Boolean = false,
    val backupCodes: List<String> = emptyList(),
    val showDisableTwoFactorDialog: Boolean = false,
    val showEnableTwoFactorOtpDialog: Boolean = false,
    val showBackupCodesOtpDialog: Boolean = false,
    val showRegenerateBackupCodesConfirm: Boolean = false,
    /** The Provider Keys row's summary; null hides the row (no endpoint takes a user-provided value). */
    val providerKeysSummary: ProviderKeysSummary = ProviderKeysSummary.Hidden,
)

/** The Balance section. Shown only while [AccountSettingsUiState.balanceEnabled]. */
@Immutable
sealed interface BalanceUiState {
    data object Loading : BalanceUiState

    data class Loaded(val tokenCredits: Double, val autoRefill: AutoRefillInfo) : BalanceUiState

    /** The request failed. A server with no balance record for the user answers 404, which is `Loaded(0)`. */
    data object Error : BalanceUiState
}

/** Backs the Account tab: profile, avatar, balance, two-factor, sign out and account deletion. */
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
        ) { state, config, baseUrl, providerKeysSummary ->
            state.copy(
                user = state.user?.let { it.copy(avatar = resolveAvatarUrl(it.avatar, baseUrl)) },
                allowAccountDeletion = config?.allowAccountDeletion ?: true,
                balanceEnabled = config?.balance?.enabled == true,
                providerKeysSummary = providerKeysSummary,
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
)
