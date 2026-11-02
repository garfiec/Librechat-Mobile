package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.LoginOutcome
import com.garfiec.librechat.core.model.User
import com.garfiec.librechat.core.model.VerifyTwoFactorOutcome
import com.garfiec.librechat.core.model.auth.AuthCookies
import com.garfiec.librechat.core.model.response.BackupCodesResponse
import com.garfiec.librechat.core.model.response.TwoFactorSetupConfirmResponse
import com.garfiec.librechat.core.model.response.TwoFactorSetupResponse

interface AuthRepository {
    suspend fun login(email: String, password: String): Result<LoginOutcome>
    suspend fun loginWithOAuthToken(cookies: AuthCookies): Result<User>

    /**
     * Verifies a 2FA temp-token session. Returns a closed [VerifyTwoFactorOutcome] (not a
     * [Result]) — this repository owns the backend's error contract and classifies every
     * failure exactly once, so callers get an exhaustive `when` instead of re-deriving
     * semantics from status codes and exception types.
     */
    suspend fun verifyTwoFactor(tempToken: String, code: String, isBackupCode: Boolean = false): VerifyTwoFactorOutcome
    suspend fun register(name: String, email: String, username: String, password: String): Result<Unit>
    suspend fun logout(): Result<Unit>

    /**
     * Revokes the active account's server session, best-effort, and changes nothing locally: the
     * caller ends the local session itself (declining required terms signs out of this account
     * only, with no failover, which [logout]'s teardown would do).
     */
    suspend fun revokeActiveSession()

    /**
     * Revokes [accountId]'s server session, best-effort, then removes the account and its local data.
     * The active account goes through [logout].
     */
    suspend fun removeAccount(accountId: String)
    suspend fun isLoggedIn(): Boolean

    /**
     * Cold-start safety net for the **upgrade path**: a user already logged in on a pre-tenancy build
     * has valid tokens but no persisted active account, and no login event fires to establish one. When
     * logged in but the registry seeded no account, fetch the current user and establish it (which also
     * runs the legacy claim). No-op when logged out or when the registry already restored an account.
     *
     * Returns `true` when the active account is resolved (already restored, just established, or not
     * needed because logged out) and `false` when a logged-in upgrade user still has no account because
     * the live `getUser()` failed (e.g. offline first launch) — the caller should retry on reconnect so
     * the user isn't stranded on an empty, account-blind app. Never throws on a network failure.
     */
    suspend fun restoreAccountIfNeeded(): Boolean
    suspend fun enableTwoFactor(token: String? = null, backupCode: String? = null): Result<TwoFactorSetupResponse>
    suspend fun confirmTwoFactor(code: String): Result<Unit>
    suspend fun disableTwoFactor(token: String? = null, backupCode: String? = null): Result<Unit>
    suspend fun regenerateBackupCodes(token: String? = null, backupCode: String? = null): Result<BackupCodesResponse>

    /*
     * Required 2FA enrollment (v0.8.9-rc1). Authorised only by the credentials each step hands the
     * next, starting from the setup tempToken; routed to the pending server in add-account mode. A
     * failure is a Result.Error whose ApiException status says what happened: 401/403 means the
     * setup credential is dead (start over from sign-in), 400 on confirm a wrong code, 400 on
     * acknowledge/finalize a credential already spent.
     */
    suspend fun startTwoFactorSetup(tempToken: String): Result<TwoFactorSetupResponse>
    suspend fun confirmTwoFactorSetup(tempToken: String, code: String): Result<TwoFactorSetupConfirmResponse>

    /** Acknowledges the backup codes; returns the finalization credential. */
    suspend fun acknowledgeTwoFactorSetup(acknowledgementToken: String): Result<String>

    /** Enables 2FA and signs in: on success the session is committed exactly as a 2FA verify commits it. */
    suspend fun finalizeTwoFactorSetup(finalizationToken: String): Result<User>
    suspend fun requestPasswordReset(email: String): Result<Unit>
    suspend fun resetPassword(userId: String, token: String, password: String, confirmPassword: String): Result<Unit>
}
