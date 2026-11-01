package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.auth.AuthCookies
import com.garfiec.librechat.core.model.request.LoginRequest
import com.garfiec.librechat.core.model.request.OtpVerificationRequest
import com.garfiec.librechat.core.model.request.PasswordResetRequest
import com.garfiec.librechat.core.model.request.RegisterRequest
import com.garfiec.librechat.core.model.request.ResetPasswordRequest
import com.garfiec.librechat.core.model.request.TwoFactorDisableRequest
import com.garfiec.librechat.core.model.request.TwoFactorVerifyRequest
import com.garfiec.librechat.core.model.request.TwoFactorVerifyTempRequest
import com.garfiec.librechat.core.model.response.BackupCodesResponse
import com.garfiec.librechat.core.model.response.LoginResponse
import com.garfiec.librechat.core.model.response.RefreshResponse
import com.garfiec.librechat.core.model.response.RegisterResponse
import com.garfiec.librechat.core.model.response.TwoFactorSetupAcknowledgeResponse
import com.garfiec.librechat.core.model.response.TwoFactorSetupConfirmResponse
import com.garfiec.librechat.core.model.response.TwoFactorSetupResponse
import com.garfiec.librechat.core.network.api.dto.LoginResult
import com.garfiec.librechat.core.network.api.dto.RefreshResult
import com.garfiec.librechat.core.network.api.dto.TwoFactorConfirmRequest
import com.garfiec.librechat.core.network.api.dto.TwoFactorSetupAcknowledgeRequest
import com.garfiec.librechat.core.network.api.dto.TwoFactorSetupFinalizeRequest
import com.garfiec.librechat.core.network.api.dto.TwoFactorSetupRequest
import com.garfiec.librechat.core.network.client.CookieHelper
import com.garfiec.librechat.core.network.client.attachAuthCookies
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.path

class AuthApi constructor(
    private val client: HttpClient,
) {
    suspend fun login(email: String, password: String): LoginResult {
        val httpResponse = client.post {
            url { path("api/auth/login") }
            setBody(LoginRequest(email = email, password = password))
        }
        val body = httpResponse.body<LoginResponse>()
        val refreshToken = CookieHelper.extractRefreshToken(httpResponse.headers)
        return LoginResult(body, refreshToken)
    }

    suspend fun register(
        name: String,
        email: String,
        username: String,
        password: String,
        confirmPassword: String,
    ): RegisterResponse =
        client.post {
            url { path("api/auth/register") }
            setBody(
                RegisterRequest(
                    name = name,
                    email = email,
                    username = username,
                    password = password,
                    confirmPassword = confirmPassword,
                ),
            )
        }.body()

    /**
     * The server deletes the session [refreshToken] names. It reads the token from the cookie only, and
     * without one it deletes whichever of the user's sessions it finds first — possibly another device's.
     */
    suspend fun logout(refreshToken: String) = logout(AuthCookies(refreshToken))

    suspend fun logout(cookies: AuthCookies) {
        client.post {
            url { path("api/auth/logout") }
            attachAuthCookies(cookies)
        }
    }

    suspend fun refresh(refreshToken: String): RefreshResult = refresh(AuthCookies(refreshToken))

    suspend fun refresh(cookies: AuthCookies): RefreshResult {
        val httpResponse = client.post {
            url { path("api/auth/refresh") }
            // The backend reads the cookie only; the body copy is redundant.
            attachAuthCookies(cookies)
            setBody(mapOf("refreshToken" to cookies.refreshToken))
        }
        val body = httpResponse.body<RefreshResponse>()
        return RefreshResult(body, CookieHelper.update(cookies, httpResponse.headers))
    }

    suspend fun requestPasswordReset(email: String) {
        client.post {
            url { path("api/auth/requestPasswordReset") }
            setBody(PasswordResetRequest(email = email))
        }
    }

    suspend fun resetPassword(userId: String, token: String, password: String, confirmPassword: String) {
        client.post {
            url { path("api/auth/resetPassword") }
            setBody(
                ResetPasswordRequest(
                    userId = userId,
                    token = token,
                    password = password,
                    confirmPassword = confirmPassword,
                ),
            )
        }
    }

    /**
     * Verify 2FA for an already-authenticated user.
     * POST /api/auth/2fa/verify with { token: totpCode }
     */
    suspend fun verifyTwoFactor(totpCode: String): LoginResult {
        val httpResponse = client.post {
            url { path("api/auth/2fa/verify") }
            setBody(TwoFactorVerifyRequest(token = totpCode))
        }
        val body = httpResponse.body<LoginResponse>()
        val refreshToken = CookieHelper.extractRefreshToken(httpResponse.headers)
        return LoginResult(body, refreshToken)
    }

    suspend fun enableTwoFactor(token: String? = null, backupCode: String? = null): TwoFactorSetupResponse =
        client.post {
            url { path("api/auth/2fa/enable") }
            if (token != null || backupCode != null) {
                setBody(OtpVerificationRequest(token = token, backupCode = backupCode))
            }
        }.body()

    // confirm2FA returns an empty 200 body (`res.status(200).json()`); there is nothing to decode.
    suspend fun confirmTwoFactor(code: String) {
        client.post {
            url { path("api/auth/2fa/confirm") }
            setBody(TwoFactorConfirmRequest(token = code))
        }
    }

    /**
     * POST /api/auth/2fa/verify-temp with { tempToken, token: totpCode } or { tempToken, backupCode }.
     */
    suspend fun verifyTempToken(
        tempToken: String,
        totpCode: String? = null,
        backupCode: String? = null,
    ): LoginResult {
        val httpResponse = client.post {
            url { path("api/auth/2fa/verify-temp") }
            setBody(
                TwoFactorVerifyTempRequest(
                    tempToken = tempToken,
                    token = totpCode,
                    backupCode = backupCode,
                ),
            )
        }
        val body = httpResponse.body<LoginResponse>()
        val refreshToken = CookieHelper.extractRefreshToken(httpResponse.headers)
        return LoginResult(body, refreshToken)
    }

    /*
     * Required 2FA enrollment (v0.8.9-rc1), signed in with nothing but the setup `tempToken` a
     * login, refresh or gated request handed out. Four steps, each credential single-use:
     * setup -> { otpauthUrl, backupCodes } (stages a secret); confirm(code) -> { backupCodes,
     * acknowledgementToken }; acknowledge -> { finalizationToken }; finalize -> { token, user } plus
     * the refreshToken cookie, i.e. a sign-in.
     */

    suspend fun startTwoFactorSetup(tempToken: String): TwoFactorSetupResponse =
        client.post {
            url { path("api/auth/2fa/setup") }
            setBody(TwoFactorSetupRequest(tempToken = tempToken))
        }.body()

    suspend fun confirmTwoFactorSetup(tempToken: String, code: String): TwoFactorSetupConfirmResponse =
        client.post {
            url { path("api/auth/2fa/setup/confirm") }
            setBody(TwoFactorSetupRequest(tempToken = tempToken, token = code))
        }.body()

    suspend fun acknowledgeTwoFactorSetup(acknowledgementToken: String): TwoFactorSetupAcknowledgeResponse =
        client.post {
            url { path("api/auth/2fa/setup/acknowledge") }
            setBody(TwoFactorSetupAcknowledgeRequest(acknowledgementToken = acknowledgementToken))
        }.body()

    suspend fun finalizeTwoFactorSetup(finalizationToken: String): LoginResult {
        val httpResponse = client.post {
            url { path("api/auth/2fa/setup/finalize") }
            setBody(TwoFactorSetupFinalizeRequest(finalizationToken = finalizationToken))
        }
        val body = httpResponse.body<LoginResponse>()
        val refreshToken = CookieHelper.extractRefreshToken(httpResponse.headers)
        return LoginResult(body, refreshToken)
    }

    suspend fun regenerateBackupCodes(token: String? = null, backupCode: String? = null): BackupCodesResponse =
        client.post {
            url { path("api/auth/2fa/backup/regenerate") }
            if (token != null || backupCode != null) {
                setBody(OtpVerificationRequest(token = token, backupCode = backupCode))
            }
        }.body()

    /** The server verifies whichever of [token] and [backupCode] is sent, so either one disables 2FA. */
    suspend fun disableTwoFactor(token: String? = null, backupCode: String? = null) {
        client.post {
            url { path("api/auth/2fa/disable") }
            setBody(TwoFactorDisableRequest(token = token, backupCode = backupCode))
        }
    }
}
