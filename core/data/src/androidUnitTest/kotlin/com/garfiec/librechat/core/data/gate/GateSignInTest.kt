package com.garfiec.librechat.core.data.gate

import com.garfiec.librechat.core.data.datastore.SecureValueStore
import com.garfiec.librechat.core.network.api.GateOAuthApi
import com.garfiec.librechat.core.network.client.GateCredential
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test

class GateSignInTest {

    private companion object {
        const val SERVER = "https://chat.example.com/librechat"
        const val PORTAL = "https://auth.example.com/?rd=https%3A%2F%2Fchat.example.com&rm=GET"
        const val ISSUER = "https://auth.example.com"
        val JSON_HEADERS = headersOf(HttpHeaders.ContentType, "application/json")
        const val DISCOVERY = """{"issuer":"$ISSUER",
            "authorization_endpoint":"$ISSUER/api/oidc/authorization",
            "token_endpoint":"$ISSUER/api/oidc/token",
            "pushed_authorization_request_endpoint":"$ISSUER/api/oidc/pushed-authorization-request"}"""
    }

    private class MemorySecure : SecureValueStore {
        val values = mutableMapOf<String, String>()
        override fun readSecure(key: String): String? = values[key]
        override fun writeSecure(key: String, value: String) {
            values[key] = value
        }
    }

    private class FakeReceiver : LoopbackReceiver {
        val callback = CompletableDeferred<Map<String, String>>()
        var closed = false
        override fun start(): Int = 50123
        override suspend fun awaitCallback(page: String): Map<String, String> = callback.await()
        override fun close() {
            closed = true
        }
    }

    /** Plays the user: on open, either posts [answer] to the receiver or closes the browser. */
    private class FakeBrowser(
        val receiver: FakeReceiver,
        val answer: ((state: String) -> Map<String, String>)?,
        val stateOf: () -> String,
        val onClose: () -> Unit = {},
    ) : SystemAuthBrowser {
        var opened: String? = null
        var closedByApp = false
        private val gone = CompletableDeferred<Unit>()
        override suspend fun open(url: String) {
            opened = url
            val reply = answer
            if (reply == null) gone.complete(Unit) else receiver.callback.complete(reply(stateOf()))
            gone.await()
        }

        override fun close() {
            closedByApp = true
            onClose()
            gone.complete(Unit)
        }
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val parForm = mutableMapOf<String, String>()
    private val tokenForm = mutableMapOf<String, String>()
    private val secure = MemorySecure()

    /** Set by the fake browser's close: Android blocks a backgrounded app's network. */
    private var appInFront = false
    private var exchangedInFront: Boolean? = null

    private fun engine(parReply: Pair<HttpStatusCode, String>) = MockEngine { request ->
        when (request.url.encodedPath) {
            "/.well-known/openid-configuration" -> respond(DISCOVERY, HttpStatusCode.OK, JSON_HEADERS)
            "/api/oidc/pushed-authorization-request" -> {
                val form = (request.body as FormDataContent).formData
                form.names().forEach { parForm[it] = form[it].orEmpty() }
                respond(parReply.second, parReply.first, JSON_HEADERS)
            }
            "/api/oidc/token" -> {
                exchangedInFront = appInFront
                val form = (request.body as FormDataContent).formData
                form.names().forEach { tokenForm[it] = form[it].orEmpty() }
                respond("""{"access_token":"at","refresh_token":"rt","expires_in":3600}""", HttpStatusCode.OK, JSON_HEADERS)
            }
            else -> respond("", HttpStatusCode.NotFound)
        }
    }

    private fun rig(
        parReply: Pair<HttpStatusCode, String> = HttpStatusCode.Created to """{"request_uri":"urn:par:1","expires_in":90}""",
        answer: ((String) -> Map<String, String>)?,
    ): Triple<GateSignIn, FakeBrowser, GateCredentialStore> {
        val api = GateOAuthApi(HttpClient(engine(parReply)), json)
        val store = GateCredentialStore(secure, api, json, Dispatchers.Unconfined)
        val receiver = FakeReceiver()
        val browser = FakeBrowser(
            receiver,
            answer,
            stateOf = { parForm.getValue("state") },
            onClose = { appInFront = true },
        )
        return Triple(GateSignIn(api, store, { receiver }, browser), browser, store)
    }

    @Test
    fun `a completed sign-in stores a bearer for the server`() = runTest {
        val (signIn, browser, store) = rig { state -> mapOf("code" to "c1", "state" to state, "iss" to ISSUER) }

        val result = signIn.signIn(SERVER, PORTAL, "switchboard")

        assertThat(result).isEqualTo(GateSignInResult.Success)
        assertThat(store.credentialFor(SERVER)).isEqualTo(GateCredential.Bearer("at"))
        assertThat(browser.closedByApp).isTrue()
        assertThat(exchangedInFront).isTrue()
        assertThat(Url(browser.opened!!).parameters["request_uri"]).isEqualTo("urn:par:1")
        assertThat(parForm).containsAtLeast(
            "client_id", "switchboard",
            "redirect_uri", "http://127.0.0.1:50123/callback",
            "response_mode", "form_post",
            "resource", "https://chat.example.com/librechat/",
            "code_challenge_method", "S256",
        )
        assertThat(parForm).doesNotContainKey("audience")
        assertThat(tokenForm).containsAtLeast("code", "c1", "redirect_uri", "http://127.0.0.1:50123/callback")
        assertThat(tokenForm["code_verifier"]).isNotEmpty()
    }

    @Test
    fun `a callback for another attempt is refused`() = runTest {
        val (signIn, _, store) = rig { mapOf("code" to "c1", "state" to "forged", "iss" to ISSUER) }

        assertThat(signIn.signIn(SERVER, PORTAL, "switchboard")).isInstanceOf(GateSignInResult.Failed::class.java)
        assertThat(store.credentialFor(SERVER)).isNull()
        assertThat(tokenForm).isEmpty()
    }

    @Test
    fun `a callback from another issuer is refused`() = runTest {
        val (signIn, _, _) = rig { state -> mapOf("code" to "c1", "state" to state, "iss" to "https://evil.example") }

        assertThat(signIn.signIn(SERVER, PORTAL, "switchboard")).isEqualTo(GateSignInResult.Failed("issuer mismatch"))
    }

    @Test
    fun `closing the browser without finishing cancels`() = runTest {
        val (signIn, _, store) = rig(answer = null)

        assertThat(signIn.signIn(SERVER, PORTAL, "switchboard")).isEqualTo(GateSignInResult.Cancelled)
        assertThat(store.credentialFor(SERVER)).isNull()
    }

    @Test
    fun `an unknown client is reported before the browser opens`() = runTest {
        val (signIn, browser, _) = rig(
            parReply = HttpStatusCode.Unauthorized to """{"error":"invalid_client","error_description":"unknown"}""",
            answer = { error("browser must not open") },
        )

        assertThat(signIn.signIn(SERVER, PORTAL, "other")).isEqualTo(GateSignInResult.UnknownClient("other"))
        assertThat(browser.opened).isNull()
    }

    @Test
    fun `an unregistered redirect is reported as such`() = runTest {
        val (signIn, _, _) = rig(
            parReply = HttpStatusCode.BadRequest to
                """{"error":"invalid_request","error_description":"The 'redirect_uri' parameter does not """ +
                    """match any of the OAuth 2.0 Client's pre-registered redirect urls."}""",
            answer = { error("browser must not open") },
        )

        assertThat(signIn.signIn(SERVER, PORTAL, "switchboard"))
            .isEqualTo(GateSignInResult.RedirectNotRegistered("switchboard"))
    }

    @Test
    fun `a denied consent is a refusal`() = runTest {
        val (signIn, _, _) = rig { state -> mapOf("error" to "access_denied", "state" to state) }

        assertThat(signIn.signIn(SERVER, PORTAL, "switchboard"))
            .isEqualTo(GateSignInResult.Refused("access_denied", null))
    }
}
