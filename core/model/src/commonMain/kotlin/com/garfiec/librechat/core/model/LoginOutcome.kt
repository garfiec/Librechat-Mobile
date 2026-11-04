package com.garfiec.librechat.core.model

sealed interface LoginOutcome {
    data class Success(val user: User) : LoginOutcome
    data class TwoFactorRequired(val tempToken: String) : LoginOutcome

    /**
     * The server enforces 2FA and this account has not set it up. [tempToken] is a setup token for
     * the `/api/auth/2fa/setup` flow, never a login challenge for `verify-temp`.
     */
    data class TwoFactorSetupRequired(val tempToken: String) : LoginOutcome
}
