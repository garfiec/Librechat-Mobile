package com.garfiec.librechat.core.model.response

import com.garfiec.librechat.core.model.User
import com.garfiec.librechat.core.model.error.ServerErrorCode
import kotlinx.serialization.Serializable

/**
 * Response body for POST /api/auth/login and the 2FA verify endpoints. The field is `twoFAPending`
 * on the wire (not `twoFactorRequired`).
 *
 * A server that enforces 2FA answers a sign-in by an account without it with
 * `{ code: "TWO_FACTOR_ENROLLMENT_REQUIRED", twoFAPending: true, twoFASetupRequired: true, tempToken }`
 * (v0.8.9-rc1). That `tempToken` is a **setup** token for `/api/auth/2fa/setup*`, not a login
 * challenge: sent to `verify-temp` it is refused with a 401. Read [isTwoFactorSetupRequired] before
 * [twoFAPending].
 */
@Serializable
data class LoginResponse(
    val token: String? = null,
    val user: User? = null,
    val twoFAPending: Boolean = false,
    val tempToken: String? = null,
    val code: String? = null,
    val twoFASetupRequired: Boolean = false,
) {
    val isTwoFactorSetupRequired: Boolean
        get() = twoFASetupRequired || code == ServerErrorCode.TWO_FACTOR_ENROLLMENT_REQUIRED
}
