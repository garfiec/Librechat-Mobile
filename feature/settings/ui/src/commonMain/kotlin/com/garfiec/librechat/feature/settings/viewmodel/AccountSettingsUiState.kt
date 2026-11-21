package com.garfiec.librechat.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.config.CurrencyConfig
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.feature.settings.model.AutoRefillInfo
import com.garfiec.librechat.feature.settings.model.BalanceDisplay
import com.garfiec.librechat.feature.settings.model.UserDisplayData
import com.garfiec.librechat.feature.settings.model.usedPercent
import com.garfiec.librechat.feature.settings.viewmodel.delegate.EmailChangeDialogState
import com.garfiec.librechat.feature.settings.viewmodel.delegate.EmailChangeNotice
import com.garfiec.librechat.feature.settings.viewmodel.delegate.ProviderKeysSummary
import org.jetbrains.compose.resources.StringResource

@Immutable
data class AccountSettingsUiState(
    val user: UserDisplayData? = null,
    /**
     * Sticky inline error for profile-fetch failures. Rendered as an `ErrorBanner` inside
     * the Account section so the rest of the screen (Sign Out, Delete Account, etc.) stays
     * reachable while the server is unreachable. Cleared at the start of each `loadUser()`.
     */
    val profileLoadError: StringResource? = null,
    /**
     * Transient errors surfaced via snackbar; cleared by [AccountSettingsViewModel.dismissError]
     * after the snackbar is shown/acted on. Used for one-shot failures: avatar upload,
     * account-deletion submit, 2FA mutations, etc. Do NOT use this for profile-fetch
     * failures — those are sticky and must remain visible until the user retries.
     * See [profileLoadError].
     */
    val error: String? = null,
    /**
     * A transient error with app copy instead of the server's words; shown and cleared like [error],
     * but with no Retry: each one names a step a retry cannot take (signing in again).
     */
    val errorRes: StringResource? = null,
    /** True only when the authenticated user's system role is ADMIN. Gates Administration
     *  fail-CLOSED (defaults false until the profile loads). */
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
    /** Leave for this account's own sign-in, keeping the account and its server: its session was retired. */
    val isSignInAgainRequested: Boolean = false,
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
    /** 2FA was enabled without a session to replace the retired one: closing the backup codes signs in again. */
    val twoFactorSignInRequired: Boolean = false,
    /** 2FA was enabled and the server signed out every other session; the backup codes say so. */
    val twoFactorOtherSessionsSignedOut: Boolean = false,
    /**
     * False while the server enforces 2FA (`twoFactorAuthenticationRequired`, v0.8.9-rc1) for this
     * account's provider; the server then refuses to disable it, so the action is hidden.
     */
    val canDisableTwoFactor: Boolean = true,
    /** The 2FA row: a loaded account whose provider 2FA applies to. See [isTwoFactorPolicyProvider]. */
    val showTwoFactor: Boolean = false,
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
 * share of the allotment spent: the record's grant or refill amount, else the config's `startBalance`. Web's
 * `summarizeBalance`. Applied where the config is combined in, so a config change re-presents a
 * balance already loaded.
 */
fun BalanceUiState.presentedFor(config: StartupConfig?): BalanceUiState {
    if (this !is BalanceUiState.Loaded) return this
    val allotment = allotmentFromRefill ?: config?.balance?.startBalance?.takeIf { it.isFinite() && it > 0 }
    return copy(
        display = BalanceDisplay.fromWire(config?.balance?.display),
        usedPercent = usedPercent(tokenCredits, allotment),
        currency = config?.interfaceConfig?.currency,
    )
}
