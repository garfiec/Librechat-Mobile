package com.garfiec.librechat.feature.settings.viewmodel.delegate

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.AuthRepository
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.twofa_sign_in_again_to_enable
import com.garfiec.librechat.feature.settings.viewmodel.AccountSettingsUiState
import com.garfiec.librechat.feature.settings.viewmodel.SettingsStateHandle
import com.garfiec.librechat.feature.settings.viewmodel.isCredentialRetirementIncomplete
import com.garfiec.librechat.feature.settings.viewmodel.isHttpStatus
import com.garfiec.librechat.feature.settings.viewmodel.toTwoFactorCodeError
import kotlinx.coroutines.launch

/**
 * The account providers enforced 2FA applies to: upstream `TWO_FACTOR_POLICY_PROVIDERS`
 * (`packages/data-provider/src/config.ts`), registered in `scripts/mirrors.json`. Federated accounts
 * are left to their identity provider.
 */
internal val TWO_FACTOR_POLICY_PROVIDERS = setOf("local", "ldap")

/** Whether 2FA applies to an account of [provider] at all: upstream `isTwoFactorPolicyProvider` (unknown counts). */
internal fun isTwoFactorPolicyProvider(provider: String?): Boolean =
    provider == null || provider in TWO_FACTOR_POLICY_PROVIDERS

/** Whether the server refuses to disable 2FA for an account of [provider]. */
internal fun isTwoFactorRequiredFor(enforced: Boolean?, provider: String?): Boolean =
    enforced == true && isTwoFactorPolicyProvider(provider)

/**
 * Handles 2FA setup, backup codes, and disable flow.
 */
class TwoFactorSecurityDelegate(
    private val stateHandle: SettingsStateHandle<AccountSettingsUiState>,
    private val authRepository: AuthRepository,
) {

    fun toggleTwoFactor() {
        if (stateHandle.state.isTwoFactorEnabled) {
            stateHandle.update { copy(showDisableTwoFactorDialog = true) }
        } else {
            enableTwoFactor()
        }
    }

    /**
     * Enable 2FA. When re-enrolling (2FA already enabled on server), OTP is required.
     */
    fun enableTwoFactor(token: String? = null, backupCode: String? = null) {
        stateHandle.scope.launch {
            stateHandle.update { copy(isTwoFactorLoading = true, twoFactorCodeError = null) }
            when (val result = authRepository.enableTwoFactor(token = token, backupCode = backupCode)) {
                is Result.Success -> {
                    stateHandle.update {
                        copy(
                            isTwoFactorLoading = false,
                            showTwoFactorSetupDialog = true,
                            showEnableTwoFactorOtpDialog = false,
                            twoFactorOtpauthUrl = result.data.otpauthUrl,
                            backupCodes = result.data.backupCodes,
                        )
                    }
                }
                is Result.Error -> {
                    // Server returns 400 when 2FA is already enabled and OTP is required to re-enroll
                    val codeSent = token != null || backupCode != null
                    val needsOtp = !codeSent && result.isHttpStatus(400)
                    val fallback = "Failed to enable two-factor authentication"
                    stateHandle.update {
                        copy(
                            isTwoFactorLoading = false,
                            showEnableTwoFactorOtpDialog = if (needsOtp) true else showEnableTwoFactorOtpDialog,
                            error = if (needsOtp || codeSent) null else (result.message ?: fallback),
                            twoFactorCodeError = if (codeSent) {
                                result.toTwoFactorCodeError(401, backupCode != null, fallback)
                            } else {
                                null
                            },
                        )
                    }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun dismissEnableTwoFactorOtpDialog() {
        stateHandle.update { copy(showEnableTwoFactorOtpDialog = false, twoFactorCodeError = null) }
    }

    fun confirmEnableTwoFactor(code: String) {
        stateHandle.scope.launch {
            stateHandle.update { copy(isTwoFactorLoading = true, twoFactorCodeError = null) }
            when (val result = authRepository.confirmTwoFactor(code)) {
                is Result.Success -> {
                    // The backup codes to display came with enable and are held in state.
                    stateHandle.update {
                        copy(
                            isTwoFactorLoading = false,
                            isTwoFactorEnabled = true,
                            showTwoFactorSetupDialog = false,
                            twoFactorOtpauthUrl = null,
                            showBackupCodesDialog = true,
                            twoFactorOtherSessionsSignedOut = result.data,
                        )
                    }
                }
                // 2FA is on, but no session replaced the retired one: keep the codes, then sign in again.
                is Result.Error -> if (result.isCredentialRetirementIncomplete()) {
                    stateHandle.update {
                        copy(
                            isTwoFactorLoading = false,
                            isTwoFactorEnabled = true,
                            showTwoFactorSetupDialog = false,
                            twoFactorOtpauthUrl = null,
                            showBackupCodesDialog = true,
                            twoFactorSignInRequired = true,
                        )
                    }
                } else if (result.isHttpStatus(401)) {
                    // No live refresh session to rotate: only a fresh sign-in can enable 2FA, so the
                    // setup closes (as Cancel does) and the message shows on the page, not behind it.
                    stateHandle.update {
                        copy(
                            isTwoFactorLoading = false,
                            showTwoFactorSetupDialog = false,
                            twoFactorOtpauthUrl = null,
                            errorRes = Res.string.twofa_sign_in_again_to_enable,
                        )
                    }
                } else {
                    stateHandle.update {
                        copy(
                            isTwoFactorLoading = false,
                            twoFactorCodeError = result.toTwoFactorCodeError(400, false, "Invalid verification code"),
                        )
                    }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    /** Either code works: losing the authenticator is the usual reason to turn 2FA off. */
    fun confirmDisableTwoFactor(token: String?, backupCode: String?) {
        stateHandle.scope.launch {
            stateHandle.update { copy(isTwoFactorLoading = true, twoFactorCodeError = null) }
            when (val result = authRepository.disableTwoFactor(token = token, backupCode = backupCode)) {
                is Result.Success -> {
                    stateHandle.update {
                        copy(
                            isTwoFactorLoading = false,
                            isTwoFactorEnabled = false,
                            showDisableTwoFactorDialog = false,
                        )
                    }
                }
                is Result.Error -> {
                    stateHandle.update {
                        copy(
                            isTwoFactorLoading = false,
                            twoFactorCodeError = result.toTwoFactorCodeError(
                                401,
                                backupCode != null,
                                "Failed to disable two-factor authentication",
                            ),
                        )
                    }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun dismissTwoFactorSetupDialog() {
        stateHandle.update { copy(showTwoFactorSetupDialog = false, twoFactorOtpauthUrl = null, twoFactorCodeError = null) }
    }

    fun dismissDisableTwoFactorDialog() {
        stateHandle.update { copy(showDisableTwoFactorDialog = false, twoFactorCodeError = null) }
    }

    fun dismissBackupCodesDialog() {
        val signInRequired = stateHandle.state.twoFactorSignInRequired
        stateHandle.update {
            copy(showBackupCodesDialog = false, backupCodes = emptyList(), twoFactorOtherSessionsSignedOut = false)
        }
        if (!signInRequired) return
        stateHandle.scope.launch {
            // The retirement may have missed this device's own server session: revoke it, as a
            // sign-out does, while the account still holds it. The sign-in then ends it locally.
            authRepository.revokeActiveSession()
            stateHandle.update { copy(isSignInAgainRequested = true) }
        }
    }

    /**
     * Regenerating is the only backup-code operation the server has: it stores hashes, so there
     * is nothing to "view". Every existing code stops working, which is why it is confirmed first.
     */
    fun requestRegenerateBackupCodes() {
        stateHandle.update { copy(showRegenerateBackupCodesConfirm = true) }
    }

    fun dismissRegenerateBackupCodesConfirm() {
        stateHandle.update { copy(showRegenerateBackupCodesConfirm = false) }
    }

    /**
     * The code is collected before the request: with 2FA on the server refuses a bare request, and
     * the action only exists while 2FA is on. The 400 fallback in [regenerateBackupCodes] remains
     * for a profile that is out of date.
     */
    fun confirmRegenerateBackupCodes() {
        stateHandle.update {
            copy(
                showRegenerateBackupCodesConfirm = false,
                showBackupCodesOtpDialog = isTwoFactorEnabled,
            )
        }
        if (!stateHandle.state.isTwoFactorEnabled) regenerateBackupCodes()
    }

    fun regenerateBackupCodes(token: String? = null, backupCode: String? = null) {
        stateHandle.scope.launch {
            stateHandle.update { copy(isTwoFactorLoading = true, twoFactorCodeError = null) }
            when (val result = authRepository.regenerateBackupCodes(token = token, backupCode = backupCode)) {
                is Result.Success -> {
                    stateHandle.update {
                        copy(
                            isTwoFactorLoading = false,
                            showBackupCodesDialog = true,
                            showBackupCodesOtpDialog = false,
                            backupCodes = result.data.backupCodes,
                        )
                    }
                }
                is Result.Error -> {
                    // Server returns 400 when 2FA is enabled and OTP is required to regenerate codes
                    val codeSent = token != null || backupCode != null
                    val needsOtp = !codeSent && result.isHttpStatus(400)
                    val fallback = "Failed to regenerate backup codes"
                    stateHandle.update {
                        copy(
                            isTwoFactorLoading = false,
                            showBackupCodesOtpDialog = if (needsOtp) true else showBackupCodesOtpDialog,
                            error = if (needsOtp || codeSent) null else (result.message ?: fallback),
                            twoFactorCodeError = if (codeSent) {
                                result.toTwoFactorCodeError(401, backupCode != null, fallback)
                            } else {
                                null
                            },
                        )
                    }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun dismissBackupCodesOtpDialog() {
        stateHandle.update { copy(showBackupCodesOtpDialog = false, twoFactorCodeError = null) }
    }
}
