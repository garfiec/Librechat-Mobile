package com.garfiec.librechat.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.data.repository.AuthRepository
import com.garfiec.librechat.core.data.repository.BalanceRepository
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.UserRepository
import com.garfiec.librechat.core.model.User
import com.garfiec.librechat.feature.settings.model.UserDisplayData
import com.garfiec.librechat.feature.settings.util.ContentReader
import com.garfiec.librechat.feature.settings.viewmodel.delegate.AccountDelegate
import com.garfiec.librechat.feature.settings.viewmodel.delegate.TwoFactorSecurityDelegate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

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
    val tokenCredits: Long = 0,
    val isBalanceLoading: Boolean = false,
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
)

/** Backs the Account tab: profile, avatar, balance, two-factor, sign out and account deletion. */
@Suppress("LongParameterList", "TooManyFunctions") // one-line forwarders to the two delegates
class AccountSettingsViewModel(
    userRepository: UserRepository,
    authRepository: AuthRepository,
    balanceRepository: BalanceRepository,
    contentReader: ContentReader,
    configRepository: ConfigRepository,
    ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountSettingsUiState())
    private val stateHandle = SettingsStateHandle(_uiState, viewModelScope)

    private val accountDelegate =
        AccountDelegate(stateHandle, userRepository, balanceRepository, contentReader, ioDispatcher)
    private val twoFactorDelegate = TwoFactorSecurityDelegate(stateHandle, authRepository)

    /**
     * `allowAccountDeletion` defaults to `true` (older-server behaviour) when the field is absent
     * or the config hasn't loaded yet — see VERSION_GATES.md guideline #2.
     */
    val uiState: StateFlow<AccountSettingsUiState> =
        combine(_uiState, configRepository.startupConfig) { state, config ->
            state.copy(allowAccountDeletion = config?.allowAccountDeletion ?: true)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, AccountSettingsUiState())

    init {
        accountDelegate.loadUser()
        accountDelegate.loadBalance()
    }

    // Profile & avatar
    fun retry() = accountDelegate.retry()
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
    fun confirmDisableTwoFactor(code: String) = twoFactorDelegate.confirmDisableTwoFactor(code)
    fun dismissTwoFactorSetupDialog() = twoFactorDelegate.dismissTwoFactorSetupDialog()
    fun dismissDisableTwoFactorDialog() = twoFactorDelegate.dismissDisableTwoFactorDialog()
    fun dismissBackupCodesDialog() = twoFactorDelegate.dismissBackupCodesDialog()
    fun viewBackupCodes() = twoFactorDelegate.viewBackupCodes()
    fun viewBackupCodesWithOtp(token: String?, backupCode: String?) =
        twoFactorDelegate.viewBackupCodes(token = token, backupCode = backupCode)
    fun dismissBackupCodesOtpDialog() = twoFactorDelegate.dismissBackupCodesOtpDialog()
}

internal fun User.toDisplayData() = UserDisplayData(
    name = name ?: "",
    email = email,
    username = username ?: "",
    avatar = avatar,
)
