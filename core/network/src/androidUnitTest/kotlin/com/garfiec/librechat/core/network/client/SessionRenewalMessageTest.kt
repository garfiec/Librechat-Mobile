package com.garfiec.librechat.core.network.client

import com.garfiec.librechat.core.common.network.RequestActivityTracker
import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.logging.redact.LogRedactor
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
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
 * What a request's error says when its 401 could not be cleared by a refresh.
 *
 * The server's body is `{"message":"jwt expired"}`, which passes the "looks like a sentence" screen
 * and would reach the chat's error banner verbatim while the session is kept but unable to renew.
 * The auth plugin tags the call it gave up on; the validator — a separate plugin, installed
 * separately — reads the call's attributes (not the request builder's, which are a different
 * instance) and replaces the wording. Asserted through the real factory so it pins that the two
 * actually meet on one call.
 */
class SessionRenewalMessageTest {

    /** Refreshes always fail the recoverable way, as behind a 403 LibreChat did not author. */
    private class BlockedTokenManager : TokenManager {
        var refreshes = 0
        override val sessionExpiredFlow: SharedFlow<SessionEndReason> = MutableSharedFlow()
        override val isAuthenticated: Boolean = true
        override suspend fun getAccessToken(): String? = "stale-bearer"
        override suspend fun setTokens(accessToken: String, refreshToken: String) = Unit
        override suspend fun refreshAccessToken(usedAccessToken: String?): RefreshResult {
            refreshes++
            return RefreshResult.Transient
        }
        override suspend fun clearTokens() = Unit
        override suspend fun getAccessTokenFor(accountId: String): String? = "stale-bearer"
        override suspend fun getStagedAccessToken(): String? = null
        override suspend fun clearStagedTokens() = Unit
        override suspend fun selectAccount(accountId: String) = Unit
        override suspend fun removeAccount(accountId: String) = Unit
        override suspend fun refreshAccessTokenFor(
            accountId: String,
            baseUrl: String,
            usedAccessToken: String?,
        ): RefreshResult = refreshAccessToken(usedAccessToken)
        override suspend fun onAccountResolved(accountId: String) = Unit
        override fun emitSessionExpired(expiredAccountId: String?, reason: SessionEndReason) = Unit
    }

    private class FakeServerUrlProvider : ServerUrlProvider {
        override fun getBaseUrl(): String = "https://chat.example.com"
    }

    private val jsonHeaders = headersOf("Content-Type", ContentType.Application.Json.toString())

    private fun engineFactory(handler: MockRequestHandler) =
        object : HttpClientEngineFactory<MockEngineConfig> {
            override fun create(block: MockEngineConfig.() -> Unit): HttpClientEngine =
                MockEngine(
                    MockEngineConfig().apply(block).apply {
                        dispatcher = Dispatchers.Unconfined
                        addHandler(handler)
                    },
                )
        }

    /** The real factory, so the production plugin order is what runs. */
    private fun clientAnswering(body: String, tokenManager: TokenManager): HttpClient =
        LibreChatHttpClient.create(
            engineFactory = engineFactory { respond(body, HttpStatusCode.Unauthorized, jsonHeaders) },
            json = Json { ignoreUnknownKeys = true },
            tokenManager = tokenManager,
            serverUrlProvider = FakeServerUrlProvider(),
            redactor = LogRedactor(),
            refreshCookieCredentials = { null },
            requestActivityTracker = RequestActivityTracker(),
        )

    @Test
    fun `a 401 the refresh could not clear speaks about the session`() = runTest {
        val tokenManager = BlockedTokenManager()
        val client = clientAnswering("""{"message":"jwt expired"}""", tokenManager)

        val thrown = runCatching { client.get("https://chat.example.com/api/convos") }.exceptionOrNull()

        val api = thrown as? ApiException ?: error("expected ApiException, got $thrown")
        // The plugin did give up on this request — otherwise the wording below proves nothing.
        assertThat(tokenManager.refreshes).isEqualTo(1)
        assertThat(api.statusCode).isEqualTo(401)
        assertThat(api.message).isEqualTo(LibreChatHttpClient.SESSION_RENEWAL_FAILED_MESSAGE)
        assertThat(api.serverAuthored).isFalse()
    }

    /** An auth route's 401 is the endpoint's own verdict and never enters the refresh leg. */
    @Test
    fun `a 401 on a sign-in route keeps the server's wording`() = runTest {
        val tokenManager = BlockedTokenManager()
        val client = clientAnswering("""{"message":"Invalid credentials"}""", tokenManager)

        val thrown = runCatching { client.post("https://chat.example.com/api/auth/login") }.exceptionOrNull()

        val api = thrown as? ApiException ?: error("expected ApiException, got $thrown")
        assertThat(tokenManager.refreshes).isEqualTo(0)
        assertThat(api.message).isEqualTo("Invalid credentials")
        assertThat(api.serverAuthored).isTrue()
    }
}
