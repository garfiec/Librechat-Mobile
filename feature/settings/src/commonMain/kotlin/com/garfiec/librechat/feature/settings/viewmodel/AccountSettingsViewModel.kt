package com.garfiec.librechat.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
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
import com.garfiec.librechat.core.model.config.CurrencyConfig
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.model.media.resolveAvatarUrl
import com.garfiec.librechat.core.ui.media.AvatarImagePreparer
import com.garfiec.librechat.feature.settings.model.AutoRefillInfo
import com.garfiec.librechat.feature.settings.model.BalanceDisplay
import com.garfiec.librechat.feature.settings.model.UserDisplayData
import com.garfiec.librechat.feature.settings.model.usedPercent
import com.garfiec.librechat.feature.settings.util.ContentReader
import com.garfiec.librechat.feature.settings.viewmodel.delegate.AccountDelegate
import com.garfiec.librechat.feature.settings.viewmodel.delegate.EmailChangeDelegate
import com.garfiec.librechat.feature.settings.viewmodel.delegate.EmailChangeDialogState
import com.garfiec.librechat.feature.settings.viewmodel.delegate.EmailChangeNotice
import com.garfiec.librechat.feature.settings.viewmodel.delegate.ProviderKeysSummary
import com.garfiec.librechat.feature.settings.viewmodel.delegate.ProviderKeysSummaryDelegate
import com.garfiec.librechat.feature.settings.viewmodel.delegate.TwoFactorSecurityDelegate
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
    /**
     * False while the server enforces 2FA (`twoFactorAuthenticationRequired`, v0.8.9-rc1) for this
     * account's provider; the server then refuses to disable it, so the action is hidden.
     */
    val canDisableTwoFactor: Boolean = true,
    /** The Provider Keys row's summary; null hides the row (no endpoint takes a user-provided value). */
    val providerKeysSummary: ProviderKeysSummary = ProviderKeysSummary.Hidden,
    // Email change (v0.8.9-rc1)
    /** The "Email address" row: a local account on a server that allows the change. See [isEmailChangeAvailable]. */
    val canChangeEmail: Boolean = false,
    /** The change-email dialog; null while it is closed. */
    val emailChange: EmailChangeDialogState? = null,
    /** Send stays off after a 429 for the limiter's window. Outlives the dialog. */
    val emailChangeCoolingDown: Boolean = false,
    val emailChangeNotice: EmailChangeNotice? = null,
    // Passkeys (v0.8.9-rc1, management only)
    /** The Security section's Passkeys row. See [isPasskeyManagementAvailable]. */
    val showPasskeys: Boolean = false,
    /** How many passkeys the account has; null until known, or when the list failed. */
    val passkeyCount: Int? = null,
)

/** The Balance section. Shown only while [AccountSettingsUiState.balanceEnabled]. */
@Immutable
sealed interface BalanceUiState {
    data object Loading : BalanceUiState

    /**
     * [display] and [currency] come from the deployment's config; [usedPercent] is the share of the
     * period's allotment spent (null with no allotment). [allotmentFromRefill] is the record's own
     * allotment, read before the config's `startBalance` fallback is applied.
     */
    data class Loaded(
        val tokenCredits: Double,
        val autoRefill: AutoRefillInfo,
        val display: BalanceDisplay = BalanceDisplay.CREDITS,
        val usedPercent: Int? = null,
        val currency: CurrencyConfig? = null,
        val allotmentFromRefill: Double? = null,
    ) : BalanceUiState

    /** The request failed. A server with no balance record for the user answers 404, which is `Loaded(0)`. */
    data object Error : BalanceUiState
}

/**
 * The reading as the deployment wants it shown (`balance.display`, `interface.currency`), and the
 * share of the allotment spent: the record's refill amount, else the config's `startBalance`. Web's
 * `summarizeBalance`. Applied where the config is combined in, so a config change re-presents a
 * balance already loaded.
 */
internal fun BalanceUiState.presentedFor(config: StartupConfig?): BalanceUiState {
    if (this !is BalanceUiState.Loaded) return this
    val allotment = allotmentFromRefill ?: config?.balance?.startBalance?.takeIf { it.isFinite() && it > 0 }
    return copy(
        display = BalanceDisplay.fromWire(config?.balance?.display),
        usedPercent = usedPercent(tokenCredits, allotment),
        currency = config?.interfaceConfig?.currency,
    )
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
                canChangeEmail = isEmailChangeAvailable(config, state.user?.provider),
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
     * an avatar, name or email changed on the web shows up, and the passkey count with it.
     */
    fun refreshOnReturn() {
        accountDelegate.refreshQuietly(emailChangeDelegate::onProfileRefreshed)
        if (uiState.value.showPasskeys) loadPasskeyCount()
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
