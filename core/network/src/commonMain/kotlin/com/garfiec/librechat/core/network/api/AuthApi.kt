package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.auth.AuthCookies
import com.garfiec.librechat.core.model.auth.SessionCredentials
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
import com.garfiec.librechat.core.network.client.unauthorizedIsVerdict
import com.garfiec.librechat.core.network.di.librechatJson
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.path
import kotlinx.serialization.Serializable

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
            // Only a request that carries a code can be refused for it; without one (first
            // enrolment) a 401 is an expired session and must still refresh.
            if (token != null || backupCode != null) {
                setBody(OtpVerificationRequest(token = token, backupCode = backupCode))
                unauthorizedIsVerdict()
            }
        }.body()

    /**
     * The server rotates the session [cookies] name in place and answers the replacement, signing out
     * every other session; without a live refresh cookie its 401 is the verdict, not an expired bearer.
     * Null when the server answers an empty 200 and keeps the session as it was.
     */
    suspend fun confirmTwoFactor(code: String, cookies: AuthCookies?): SessionCredentials? {
        val httpResponse = client.post {
            url { path("api/auth/2fa/confirm") }
            cookies?.let { attachAuthCookies(it) }
            setBody(TwoFactorConfirmRequest(token = code))
            unauthorizedIsVerdict()
        }
        if (cookies == null) return null
        val text = httpResponse.bodyAsText()
        val token = text.takeIf { it.isNotBlank() }
            ?.let { librechatJson.decodeFromString<ConfirmTwoFactorBody>(it).token }
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return SessionCredentials(token, CookieHelper.update(cookies, httpResponse.headers))
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

    /** Discards the staged secret and backup codes; accepts a setup token past its expiry. */
    suspend fun cancelTwoFactorSetup(tempToken: String) {
        client.post {
            url { path("api/auth/2fa/setup/cancel") }
            setBody(TwoFactorSetupRequest(tempToken = tempToken))
        }
    }

    suspend fun regenerateBackupCodes(token: String? = null, backupCode: String? = null): BackupCodesResponse =
        client.post {
            url { path("api/auth/2fa/backup/regenerate") }
            if (token != null || backupCode != null) {
                setBody(OtpVerificationRequest(token = token, backupCode = backupCode))
                unauthorizedIsVerdict()
            }
        }.body()

    /** The server verifies whichever of [token] and [backupCode] is sent, so either one disables 2FA. */
    suspend fun disableTwoFactor(token: String? = null, backupCode: String? = null) {
        client.post {
            url { path("api/auth/2fa/disable") }
            setBody(TwoFactorDisableRequest(token = token, backupCode = backupCode))
            if (token != null || backupCode != null) unauthorizedIsVerdict()
        }
    }
}

/** `{ token, user }`; only the bearer is read, the profile is reloaded the usual way. */
@Serializable
private class ConfirmTwoFactorBody(val token: String? = null)
