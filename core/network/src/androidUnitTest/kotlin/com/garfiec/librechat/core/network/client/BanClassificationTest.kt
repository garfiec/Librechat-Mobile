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
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test

/**
 * Which `403` ends the user's session (issue #376).
 *
 * The response validator used to decide this with `bodyText.contains("ban")` against the raw body,
 * which is true of any 403 whose body happens to contain those three letters — a reverse proxy's
 * block page, a WAF interstitial, a CrowdSec bouncer. Emitting
 * [SessionEndReason.BANNED] for one of those tears down a live session over a network-layer block the
 * user cannot clear, so they are logged out *and* unable to sign back in until it lifts. That is what
 * turned a temporary IP ban into a hard logout in the field.
 *
 * The assertions here are on the **emitted session-end reason**, not on the thrown exception: the
 * request failing is not in question either way, and only the emit is what logs the user out.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BanClassificationTest {

    private companion object {
        const val URL = "https://chat.example.com/api/convos"

        /** `checkBan`'s `banResponse` body, verbatim. */
        const val LIBRECHAT_BAN =
            """{"message":"Your account has been temporarily banned due to violations of our service."}"""
    }

    private class RecordingTokenManager : TokenManager {
        val expiries = mutableListOf<SessionEndReason>()

        override val isAuthenticated: Boolean get() = true
        override suspend fun getAccessToken(): String = "bearer"
        override suspend fun getAccessTokenFor(accountId: String): String = "bearer"
        override suspend fun setTokens(accessToken: String, refreshToken: String) = Unit
        override suspend fun refreshAccessToken(usedAccessToken: String?): RefreshResult =
            RefreshResult.Transient

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
    }

    private class FixedUrlProvider : ServerUrlProvider {
        override fun getBaseUrl(): String = "https://chat.example.com"
    }

    /** Issues one GET against a 403 carrying [body], and reports what the session was told. */
    private suspend fun sessionEndFor(body: String, json: Boolean): List<SessionEndReason> {
        val tokens = RecordingTokenManager()
        val client = LibreChatHttpClient.create(
            engineFactory = object : HttpClientEngineFactory<MockEngineConfig> {
                override fun create(block: MockEngineConfig.() -> Unit): HttpClientEngine =
                    MockEngine(
                        MockEngineConfig().apply(block).apply {
                            dispatcher = Dispatchers.Unconfined
                            addHandler {
                                respond(
                                    content = body,
                                    status = HttpStatusCode.Forbidden,
                                    headers = if (json) {
                                        headersOf("Content-Type", ContentType.Application.Json.toString())
                                    } else {
                                        headersOf()
                                    },
                                )
                            }
                        },
                    )
            },
            json = Json { ignoreUnknownKeys = true },
            tokenManager = tokens,
            serverUrlProvider = FixedUrlProvider(),
            redactor = LogRedactor(),
            imageCookieCredentials = { null },
            requestActivityTracker = RequestActivityTracker(),
        )

        // The request fails either way; only the emit decides whether the user is logged out.
        // Closed on the way out: one client per case otherwise retains its engine scope, plugin
        // state and the mock engine's recorded requests until the JVM exits, so the suite's
        // footprint would grow with its case count and leave stray coroutines to flake later.
        val thrown = client.use { runCatching { it.get(URL) }.exceptionOrNull() }
        assertThat(thrown).isInstanceOf(ApiException::class.java)
        return tokens.expiries
    }

    @Test
    fun `LibreChat's own ban response ends the session`() = runTest {
        // The positive control. Without it every assertion below could pass because ban detection is
        // simply broken, which would be a different bug with the same test output.
        assertThat(sessionEndFor(LIBRECHAT_BAN, json = true))
            .containsExactly(SessionEndReason.BANNED)
    }

    @Test
    fun `a reverse-proxy block page does not end the session`() = runTest {
        assertThat(
            sessionEndFor(
                "<html><head><title>403 Forbidden</title></head><body>IP banned by the bouncer</body></html>",
                json = false,
            ),
        ).isEmpty()
    }

    /**
     * The narrow case the old substring check got wrong most easily: a 403 that is LibreChat-shaped
     * JSON but is some other refusal. "Forbidden" alone must not read as a ban.
     */
    @Test
    fun `an unrelated json 403 does not end the session`() = runTest {
        assertThat(sessionEndFor("""{"message":"Forbidden"}""", json = true)).isEmpty()
    }

    /**
     * `uaParser` answers a non-browser User-Agent with `{"message":"Illegal request"}`. It is a real
     * LibreChat refusal and a real problem, but it is not a ban and must not log anyone out — the
     * recovery is a correct User-Agent, not a re-login.
     */
    @Test
    fun `the non-browser refusal does not end the session`() = runTest {
        assertThat(sessionEndFor("""{"message":"Illegal request"}""", json = true)).isEmpty()
    }

    /** A bare 403 with no body at all cannot be attributed to anything. */
    @Test
    fun `an empty 403 does not end the session`() = runTest {
        assertThat(sessionEndFor("", json = false)).isEmpty()
    }
}
