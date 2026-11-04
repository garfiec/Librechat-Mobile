package com.garfiec.librechat.core.network.client

import com.garfiec.librechat.core.common.network.RequestActivityTracker
import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.logging.redact.LogRedactor
import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test

/**
 * The required-step 403s of v0.8.9-rc1 — 2FA enrollment on every authenticated route, terms
 * acceptance on writes — become an [AuthGate], keyed on the body's code. A 403 is also the user-agent
 * soft-ban, a ban and a proxy block, so the status alone must never raise one, and neither gate may
 * be read as a ban or an expiry.
 */
class AuthGateClassificationTest {

    private companion object {
        const val URL = "https://chat.example.com/api/agents/chat"
    }

    private class RecordingTokenManager : TokenManager {
        val expiries = mutableListOf<SessionEndReason>()
        val gates = mutableListOf<AuthGate>()

        override val isAuthenticated: Boolean get() = true
        override suspend fun getAccessToken(): String = "bearer"
        override suspend fun getAccessTokenFor(accountId: String): String = "bearer"
        override suspend fun setTokens(accessToken: String, refreshToken: String) = Unit
        override suspend fun refreshAccessToken(usedAccessToken: String?): RefreshResult = RefreshResult.Transient
        override suspend fun refreshAccessTokenFor(
            accountId: String,
            baseUrl: String,
            usedAccessToken: String?,
        ): RefreshResult = RefreshResult.Transient

        override suspend fun ensureFreshAccessToken(
            accountId: String?,
            baseUrl: String,
            currentAccessToken: String?,
        ): String? = currentAccessToken

        override suspend fun clearTokens() = Unit
        override suspend fun getStagedAccessToken(): String? = null
        override suspend fun clearStagedTokens() = Unit
        override suspend fun selectAccount(accountId: String) = Unit
        override suspend fun removeAccount(accountId: String) = Unit
        override suspend fun onAccountResolved(accountId: String) = Unit
        override fun emitSessionExpired(expiredAccountId: String?, reason: SessionEndReason) {
            expiries += reason
        }

        override val sessionExpiredFlow: SharedFlow<SessionEndReason> = MutableSharedFlow()
        override fun reportAuthGate(gate: AuthGate) {
            gates += gate
        }
    }

    private class FixedUrlProvider : ServerUrlProvider {
        override fun getBaseUrl(): String = "https://chat.example.com"
    }

    private suspend fun respondingTo(body: String, status: HttpStatusCode = HttpStatusCode.Forbidden): RecordingTokenManager {
        val tokens = RecordingTokenManager()
        val client = LibreChatHttpClient.create(
            engineFactory = object : HttpClientEngineFactory<MockEngineConfig> {
                override fun create(block: MockEngineConfig.() -> Unit): HttpClientEngine =
                    MockEngine(
                        MockEngineConfig().apply(block).apply {
                            dispatcher = Dispatchers.Unconfined
                            addHandler {
                                respond(body, status, headersOf("Content-Type", ContentType.Application.Json.toString()))
                            }
                        },
                    )
            },
            json = Json { ignoreUnknownKeys = true },
            tokenManager = tokens,
            serverUrlProvider = FixedUrlProvider(),
            redactor = LogRedactor(),
            refreshCookieCredentials = { null },
            requestActivityTracker = RequestActivityTracker(),
        )
        val thrown = client.use { runCatching { it.post(URL) }.exceptionOrNull() }
        assertThat(thrown).isInstanceOf(ApiException::class.java)
        return tokens
    }

    @Test
    fun `the enrollment 403 raises the setup gate with its token and is not a ban`() = runTest {
        val tokens = respondingTo(
            """{"code":"TWO_FACTOR_ENROLLMENT_REQUIRED","twoFAPending":true,"twoFASetupRequired":true,"tempToken":"setup-jwt"}""",
        )

        assertThat(tokens.gates).containsExactly(AuthGate.TwoFactorSetupRequired(null, "setup-jwt"))
        assertThat(tokens.expiries).isEmpty()
    }

    @Test
    fun `the flat and nested terms 403s raise the terms gate`() = runTest {
        val flat = respondingTo(
            """{"code":"terms_not_accepted","message":"Terms of service must be accepted before using this feature"}""",
        )
        val nested = respondingTo(
            """{"error":{"type":"invalid_request_error","code":"terms_not_accepted","message":"x","param":null}}""",
        )

        assertThat(flat.gates).containsExactly(AuthGate.TermsNotAccepted(null))
        assertThat(nested.gates).containsExactly(AuthGate.TermsNotAccepted(null))
        assertThat(flat.expiries).isEmpty()
    }

    @Test
    fun `the user-agent soft-ban and an uncoded 403 raise nothing`() = runTest {
        assertThat(respondingTo("""{"message":"Illegal request"}""").gates).isEmpty()
        assertThat(respondingTo("""{"message":"Forbidden"}""").gates).isEmpty()
    }

    @Test
    fun `an enrollment code on another status raises nothing`() = runTest {
        val tokens = respondingTo(
            """{"code":"TWO_FACTOR_ENROLLMENT_REQUIRED","tempToken":"t"}""",
            HttpStatusCode.BadRequest,
        )

        assertThat(tokens.gates).isEmpty()
    }
}
