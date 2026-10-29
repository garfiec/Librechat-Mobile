package com.garfiec.librechat.core.data.datastore

import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.identity.AccountState
import com.garfiec.librechat.core.common.identity.InMemoryActiveAccountProvider
import com.garfiec.librechat.core.common.identity.deriveAccountId
import com.garfiec.librechat.core.common.identity.deriveServerId
import com.garfiec.librechat.core.logging.redact.LogRedactor
import com.garfiec.librechat.core.model.auth.AuthCookies
import com.garfiec.librechat.core.model.auth.OpenIdMarkers
import com.garfiec.librechat.core.model.auth.SessionCredentials
import com.garfiec.librechat.core.network.client.LibreChatHttpClient
import com.garfiec.librechat.core.network.client.ServerUrlProvider
import com.garfiec.librechat.core.network.client.SwitchGate
import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Clock

/**
 * Issue #653 end to end: after an OpenID reuse sign-in, an ordinary API request must carry the
 * markers `requireJwtAuth` selects the OpenID strategy on. A real [CommonTokenDataStore] behind a real
 * [SwitchGate] in the production [LibreChatHttpClient], so dropping the markers anywhere between the
 * stored session and the wire fails here.
 */
@OptIn(ExperimentalEncodingApi::class)
class OpenIdRequestThroughClientTest {

    private companion object {
        val ACCOUNT = deriveAccountId(deriveServerId(TEST_SERVER), "user-a").value
    }

    private fun freshJwt(): String {
        val exp = Clock.System.now().toEpochMilliseconds() / 1000 + 3600
        val encoder = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
        val header = encoder.encode("""{"alg":"HS256"}""".encodeToByteArray())
        val payload = encoder.encode("""{"id":"u1","exp":$exp}""".encodeToByteArray())
        return "$header.$payload.sig"
    }

    @Test
    fun `a signed in OpenId session sends its markers on ordinary and absolute requests`() = runTest {
        val bearer = freshJwt()
        val sent = mutableListOf<HttpRequestData>()
        val store = FakeTokenStore(lazy { error("Unexpected refresh") })
        store.setSession(SessionCredentials(bearer, AuthCookies("refresh", OpenIdMarkers("marker"))))
        store.onAccountResolved(ACCOUNT)
        val urls = object : ServerUrlProvider { override fun getBaseUrl(): String = TEST_SERVER }
        val gate = SwitchGate(
            activeAccountProvider = InMemoryActiveAccountProvider(AccountState.Resolved(AccountId(ACCOUNT))),
            serverUrlProvider = urls,
            tokenManager = store,
            accountReadyGate = null,
        )
        val client = LibreChatHttpClient.create(
            engineFactory = object : HttpClientEngineFactory<MockEngineConfig> {
                override fun create(block: MockEngineConfig.() -> Unit): HttpClientEngine = MockEngine(
                    MockEngineConfig().apply(block).apply {
                        dispatcher = Dispatchers.Unconfined
                        addHandler { request -> sent += request; respond("{}") }
                    },
                )
            },
            json = Json { ignoreUnknownKeys = true },
            tokenManager = store,
            serverUrlProvider = urls,
            redactor = LogRedactor(),
            refreshCookieCredentials = store,
            switchGate = gate,
        )

        client.use {
            // Relative: the barrier's proactive-renewal read. Absolute (Coil's shape): the snapshot's
            // first read alone, since renewal only runs for relative API paths.
            it.get("/api/convos")
            it.get("$TEST_SERVER/api/files/download/f1")
        }

        assertThat(sent).hasSize(2)
        sent.forEach { request ->
            assertThat(request.headers[HttpHeaders.Authorization]).isEqualTo("Bearer $bearer")
            assertThat(request.headers.getAll(HttpHeaders.Cookie))
                .containsExactly("token_provider=openid; openid_user_id=marker")
        }
    }
}
