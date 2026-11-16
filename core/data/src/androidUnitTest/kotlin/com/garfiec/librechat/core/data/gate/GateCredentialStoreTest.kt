package com.garfiec.librechat.core.data.gate

import com.garfiec.librechat.core.common.identity.deriveServerId
import com.garfiec.librechat.core.data.datastore.SecureValueStore
import com.garfiec.librechat.core.network.api.GateOAuthApi
import com.garfiec.librechat.core.network.api.GateTokens
import com.garfiec.librechat.core.network.client.GateCredential
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test
import java.io.IOException

class GateCredentialStoreTest {

    private companion object {
        const val SERVER = "https://chat.example.com"
        const val PORTAL = "https://auth.example.com/?rd=x"
        const val TOKEN_ENDPOINT = "https://auth.example.com/api/oidc/token"
        val JSON_HEADERS = headersOf(HttpHeaders.ContentType, "application/json")
    }

    private class MemorySecure : SecureValueStore {
        val values = mutableMapOf<String, String>()
        override fun readSecure(key: String): String? = values[key]
        override fun writeSecure(key: String, value: String) {
            values[key] = value
        }
    }

    private val json = Json { ignoreUnknownKeys = true }
    private var clock = 1_000_000L
    private val refreshForms = mutableListOf<Map<String, String>>()

    private fun store(
        secure: SecureValueStore = MemorySecure(),
        respondRefresh: suspend (Int) -> Pair<HttpStatusCode, String>,
    ): GateCredentialStore {
        val engine = MockEngine { request ->
            val form = (request.body as FormDataContent).formData
            refreshForms += form.names().associateWith { form[it].orEmpty() }
            val (status, body) = respondRefresh(refreshForms.size)
            respond(body, status, JSON_HEADERS)
        }
        return GateCredentialStore(
            secure = secure,
            api = GateOAuthApi(HttpClient(engine), json),
            json = json,
            ioDispatcher = Dispatchers.Unconfined,
            now = { clock },
        )
    }

    private fun tokens(n: Int) =
        """{"access_token":"at-$n","refresh_token":"rt-$n","expires_in":3600,"token_type":"bearer"}"""

    private suspend fun GateCredentialStore.signedIn() =
        saveBearer(SERVER, PORTAL, "switchboard", TOKEN_ENDPOINT, GateTokens("at-0", "rt-0", 3600))

    @Test
    fun `a fresh token is used as is`() = runTest {
        val store = store { HttpStatusCode.OK to tokens(it) }
        store.signedIn()

        assertThat(store.credentialFor(SERVER)).isEqualTo(GateCredential.Bearer("at-0"))
        assertThat(refreshForms).isEmpty()
    }

    @Test
    fun `a token about to expire is renewed before use`() = runTest {
        val store = store { HttpStatusCode.OK to tokens(it) }
        store.signedIn()
        clock += 3_590_000L

        assertThat(store.credentialFor(SERVER)).isEqualTo(GateCredential.Bearer("at-1"))
        assertThat(refreshForms.single()).containsExactly(
            "grant_type", "refresh_token",
            "refresh_token", "rt-0",
            "client_id", "switchboard",
        )
    }

    /** An admin can make access tokens live a minute; renewing a minute ahead would renew every request. */
    @Test
    fun `a short-lived token is used for most of its life`() = runTest {
        val store = store { HttpStatusCode.OK to tokens(it) }
        store.saveBearer(SERVER, PORTAL, "switchboard", TOKEN_ENDPOINT, GateTokens("at-0", "rt-0", 60))
        clock += 30_000L

        assertThat(store.credentialFor(SERVER)).isEqualTo(GateCredential.Bearer("at-0"))
        assertThat(refreshForms).isEmpty()

        clock += 20_000L
        assertThat(store.credentialFor(SERVER)).isEqualTo(GateCredential.Bearer("at-1"))
    }

    /** Authelia revokes the token family when an old refresh token is replayed. */
    @Test
    fun `concurrent rejections of the same token refresh once`() = runTest {
        val store = store {
            delay(100)
            HttpStatusCode.OK to tokens(it)
        }
        store.signedIn()

        val renewed = List(5) { async { store.onRejected(SERVER, GateCredential.Bearer("at-0")) } }.awaitAll()

        assertThat(refreshForms).hasSize(1)
        assertThat(renewed.toSet()).containsExactly(GateCredential.Bearer("at-1"))
    }

    @Test
    fun `a refused refresh asks for a sign-in and drops the tokens, keeping where to sign in`() = runTest {
        val secure = MemorySecure()
        val store = store(secure) {
            HttpStatusCode.BadRequest to """{"error":"invalid_grant","error_description":"expired"}"""
        }
        store.signedIn()

        assertThat(store.onRejected(SERVER, GateCredential.Bearer("at-0"))).isNull()

        val serverId = deriveServerId(SERVER).value
        assertThat(store.signInRequired.value).containsExactly(serverId)
        assertThat(store.credentialFor(SERVER)).isNull()
        assertThat(secure.values.values.single()).doesNotContain("rt-0")
        assertThat(store.signInDetails(SERVER))
            .isEqualTo(GateSignInDetails(PORTAL, "switchboard", usesBrowserSignIn = true))
    }

    @Test
    fun `a refresh that fails in transit keeps the session`() = runTest {
        val store = store { throw IOException("offline") }
        store.signedIn()

        assertThat(store.onRejected(SERVER, GateCredential.Bearer("at-0"))).isNull()

        assertThat(store.signInRequired.value).isEmpty()
        assertThat(store.credentialFor(SERVER)).isEqualTo(GateCredential.Bearer("at-0"))
    }

    @Test
    fun `a refused cookie asks for a sign-in`() = runTest {
        val store = store { error("cookies are never refreshed") }
        store.saveCookie(SERVER, PORTAL, "authelia_session=s1")

        assertThat(store.onRejected(SERVER, GateCredential.Cookie("authelia_session=s1"))).isNull()

        assertThat(store.signInRequired.value).containsExactly(deriveServerId(SERVER).value)
        assertThat(store.signInDetails(SERVER)?.usesBrowserSignIn).isFalse()
    }

    @Test
    fun `a new sign-in clears the request`() = runTest {
        val store = store { HttpStatusCode.BadRequest to """{"error":"invalid_grant"}""" }
        store.signedIn()
        store.onRejected(SERVER, GateCredential.Bearer("at-0"))

        store.signedIn()

        assertThat(store.signInRequired.value).isEmpty()
    }

    @Test
    fun `the record survives a restart and reports a pending sign-in`() = runTest {
        val secure = MemorySecure()
        store(secure) { HttpStatusCode.BadRequest to """{"error":"invalid_grant"}""" }.apply {
            signedIn()
            onRejected(SERVER, GateCredential.Bearer("at-0"))
        }

        val restarted = store(secure) { error("unused") }
        restarted.warm(SERVER)

        assertThat(restarted.signInRequired.value).containsExactly(deriveServerId(SERVER).value)
    }

    @Test
    fun `another server has nothing`() = runTest {
        val store = store { error("unused") }
        store.signedIn()

        assertThat(store.credentialFor("https://other.example.com")).isNull()
        assertThat(store.credentialFor("")).isNull()
    }
}
