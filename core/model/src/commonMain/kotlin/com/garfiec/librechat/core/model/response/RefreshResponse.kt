package com.garfiec.librechat.core.model.response

import com.garfiec.librechat.core.model.User
import com.garfiec.librechat.core.model.error.ServerErrorCode
import kotlinx.serialization.Serializable

/**
 * Response body for POST /api/auth/refresh.
 *
 * [token] is nullable because a server that enforces 2FA answers the refresh of an account that has
 * not enrolled with a **200** carrying no token at all:
 * `{ code: "TWO_FACTOR_ENROLLMENT_REQUIRED", twoFAPending: true, twoFASetupRequired: true, tempToken }`
 * (`packages/api/src/auth/localRefresh.ts`, v0.8.9-rc1). The server has already deleted the session
 * and cleared the `refreshToken` cookie by then, so this is neither a renewal nor an expiry: the
 * session ends and the user is sent to set up 2FA with [tempToken]. Check [isTwoFactorSetupRequired]
 * before anything reads [token] or the response cookies.
 */
@Serializable
data class RefreshResponse(
    val token: String? = null,
    val user: User? = null,
    val code: String? = null,
    val twoFAPending: Boolean = false,
    val twoFASetupRequired: Boolean = false,
    val tempToken: String? = null,
) {
    val isTwoFactorSetupRequired: Boolean
        get() = twoFASetupRequired || code == ServerErrorCode.TWO_FACTOR_ENROLLMENT_REQUIRED
}
