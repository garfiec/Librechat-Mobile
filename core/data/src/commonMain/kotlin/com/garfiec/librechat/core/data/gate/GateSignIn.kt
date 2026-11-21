package com.garfiec.librechat.core.data.gate

import com.garfiec.librechat.core.common.identity.Pkce
import com.garfiec.librechat.core.common.identity.cleanServerUrl
import com.garfiec.librechat.core.logging.Diag
import com.garfiec.librechat.core.network.api.GateDiscovery
import com.garfiec.librechat.core.network.api.GateOAuthApi
import com.garfiec.librechat.core.network.api.GateOAuthResult
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A one-shot HTTP listener on the loopback interface that receives the gate's authorization response.
 * Authelia only delivers it as a form POST to an http(s) address (it blanks custom schemes), so the
 * redirect target is `http://127.0.0.1:<port>/callback` (RFC 8252 §7.3).
 */
interface LoopbackReceiver {
    /** Binds 127.0.0.1 on a free port and returns it. */
    fun start(): Int

    /**
     * Waits for a request to `/callback`, answers it with [page] (an HTML document) and returns its
     * parameters: the form body for a POST, the query for a GET.
     */
    suspend fun awaitCallback(page: String): Map<String, String>

    fun close()
}

/** A fresh [LoopbackReceiver] per sign-in attempt. */
fun interface LoopbackReceiverFactory {
    fun create(): LoopbackReceiver
}

/** The system browser for the gate's own sign-in pages (Custom Tab, ASWebAuthenticationSession). */
interface SystemAuthBrowser {
    /** Opens [url] and suspends until the browser is gone, whether the user closed it or [close] did. */
    suspend fun open(url: String)

    /** Dismisses the browser and brings the app back, once the callback has arrived. */
    fun close()
}

sealed interface GateSignInResult {
    data object Success : GateSignInResult

    /** The user left the browser without finishing. */
    data object Cancelled : GateSignInResult

    /** The portal has no OAuth provider (no discovery document): only the in-app page sign-in can work. */
    data object NoProvider : GateSignInResult

    /** The provider doesn't know [clientId], or it isn't a public client. */
    data class UnknownClient(val clientId: String) : GateSignInResult

    /** [clientId] exists but doesn't list `http://127.0.0.1/callback` as a redirect URI. */
    data class RedirectNotRegistered(val clientId: String) : GateSignInResult

    /** Any other refusal: [description] is the provider's own explanation, when it gave one. */
    data class Refused(val error: String, val description: String?) : GateSignInResult

    /** Network trouble or a response that doesn't add up. */
    data class Failed(val reason: String) : GateSignInResult
}

/** What the sign-in screens and the shell need from the gate sign-in. */
interface GateAccess {
    /** Server ids whose gate sign-in has lapsed; see [GateCredentialStore.signInRequired]. */
    val signInRequired: StateFlow<Set<String>>

    /** Whether [portalUrl]'s gate offers the browser sign-in (an OAuth provider), not just its page. */
    suspend fun offersBrowserSignIn(portalUrl: String): Boolean

    suspend fun signIn(serverUrl: String, portalUrl: String, clientId: String): GateSignInResult

    /** Stores the gate session cookies captured from its sign-in page. */
    suspend fun savePageSignIn(serverUrl: String, portalUrl: String, cookies: String)

    suspend fun signInDetails(serverUrl: String): GateSignInDetails?

    /** Loads [serverUrl]'s record so [signInRequired] reflects a lapse from an earlier run. */
    suspend fun warm(serverUrl: String)
}

/**
 * Signs in to an access gate's identity provider in the system browser and stores the bearer token
 * for the server: discovery → loopback listener → pushed authorization request (PKCE S256, `state`,
 * `resource` = the server) → browser → callback (checked `state` and `iss`) → code exchange.
 */
class GateSignIn(
    private val api: GateOAuthApi,
    private val store: GateCredentialStore,
    private val receivers: LoopbackReceiverFactory,
    private val browser: SystemAuthBrowser,
) : GateAccess {
    override val signInRequired: StateFlow<Set<String>> get() = store.signInRequired

    override suspend fun offersBrowserSignIn(portalUrl: String): Boolean = discover(portalUrl) != null

    override suspend fun savePageSignIn(serverUrl: String, portalUrl: String, cookies: String) =
        store.saveCookie(serverUrl, portalUrl, cookies)

    override suspend fun signInDetails(serverUrl: String): GateSignInDetails? = store.signInDetails(serverUrl)

    override suspend fun warm(serverUrl: String) = store.warm(serverUrl)

    suspend fun discover(portalUrl: String): GateDiscovery? =
        runCatching { api.discover(portalUrl) }.getOrElse {
            if (it is CancellationException) throw it
            Diag.w("Gate", throwable = it) { "gate discovery failed" }
            null
        }

    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    override suspend fun signIn(serverUrl: String, portalUrl: String, clientId: String): GateSignInResult {
        val discovery = discover(portalUrl) ?: return GateSignInResult.NoProvider
        val receiver = receivers.create()
        return try {
            val port = receiver.start()
            val redirectUri = "http://127.0.0.1:$port/callback"
            val pkce = Pkce.create()
            val state = Pkce.newState()
            val request = authorizationRequest(serverUrl, clientId, redirectUri, pkce, state)
            val authorizeUrl = when (val target = authorizeUrlFor(discovery, clientId, request)) {
                is Target.Ready -> target.url
                is Target.Refused -> return target.result
            }
            val callback = awaitBrowserCallback(receiver, authorizeUrl) ?: return GateSignInResult.Cancelled
            checkCallback(callback, state, discovery.issuer)?.let { return it }
            exchange(serverUrl, portalUrl, clientId, discovery, callback.getValue("code"), redirectUri, pkce)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Diag.w("Gate", throwable = e) { "gate sign-in failed" }
            GateSignInResult.Failed(e.message ?: e::class.simpleName.orEmpty())
        } finally {
            receiver.close()
        }
    }

    /**
     * The callback, with the app back in front, or null when the user left the browser first. Back in
     * front first: Android blocks a backgrounded app's network, so the code exchange would fail.
     */
    private suspend fun awaitBrowserCallback(receiver: LoopbackReceiver, authorizeUrl: String): Map<String, String>? =
        withTimeoutOrNull(SIGN_IN_TIMEOUT_MILLIS) {
            coroutineScope {
                val callback = async { receiver.awaitCallback(RETURN_PAGE) }
                val browserGone = async { browser.open(authorizeUrl) }
                val first = select {
                    callback.onAwait { it }
                    browserGone.onAwait { null }
                }
                // The browser can be gone a moment before the form post it sent lands.
                val result = first ?: withTimeoutOrNull(LATE_CALLBACK_MILLIS) { callback.await() }
                callback.cancel()
                if (result != null && first != null) {
                    browser.close()
                    withTimeoutOrNull(RETURN_TO_APP_MILLIS) { browserGone.await() }
                }
                browserGone.cancel()
                result
            }
        }

    private fun authorizationRequest(
        serverUrl: String,
        clientId: String,
        redirectUri: String,
        pkce: Pkce,
        state: String,
    ): Map<String, String> = mapOf(
        "client_id" to clientId,
        "response_type" to "code",
        "response_mode" to "form_post",
        "redirect_uri" to redirectUri,
        "scope" to "offline_access authelia.bearer.authz",
        // Authelia matches `audience` against the full request URL, so it would grant nothing;
        // `resource` matches by prefix, so it covers every path under the deployment root.
        "resource" to cleanServerUrl(serverUrl).trimEnd('/') + "/",
        "code_challenge" to pkce.challenge,
        "code_challenge_method" to "S256",
        "state" to state,
    )

    private sealed interface Target {
        data class Ready(val url: String) : Target
        data class Refused(val result: GateSignInResult) : Target
    }

    private suspend fun authorizeUrlFor(discovery: GateDiscovery, clientId: String, request: Map<String, String>): Target {
        val par = discovery.parEndpoint ?: return Target.Ready(withQuery(discovery.authorizationEndpoint, request))
        return when (val pushed = api.pushAuthorizationRequest(par, request)) {
            is GateOAuthResult.Ok -> Target.Ready(
                withQuery(discovery.authorizationEndpoint, mapOf("client_id" to clientId, "request_uri" to pushed.value)),
            )
            is GateOAuthResult.Refused -> Target.Refused(classify(pushed, clientId))
        }
    }

    private suspend fun exchange(
        serverUrl: String,
        portalUrl: String,
        clientId: String,
        discovery: GateDiscovery,
        code: String,
        redirectUri: String,
        pkce: Pkce,
    ): GateSignInResult {
        val form = mapOf(
            "grant_type" to "authorization_code",
            "code" to code,
            "redirect_uri" to redirectUri,
            "client_id" to clientId,
            "code_verifier" to pkce.verifier,
        )
        return when (val tokens = api.token(discovery.tokenEndpoint, form)) {
            is GateOAuthResult.Ok -> {
                store.saveBearer(serverUrl, portalUrl, clientId, discovery.tokenEndpoint, tokens.value)
                GateSignInResult.Success
            }
            is GateOAuthResult.Refused -> classify(tokens, clientId)
        }
    }

    internal companion object {
        const val SIGN_IN_TIMEOUT_MILLIS = 10 * 60_000L
        const val LATE_CALLBACK_MILLIS = 1_500L
        const val RETURN_TO_APP_MILLIS = 5_000L

        /** Answered to the browser at the loopback address once the code has arrived. */
        const val RETURN_PAGE = "<!doctype html><html><head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>Switchboard</title>" +
            "<style>body{font-family:system-ui,sans-serif;margin:24px;line-height:1.5}</style></head>" +
            "<body><h2>Signed in</h2><p>You can return to Switchboard.</p></body></html>"

        /** Null when the callback is good, else why not. */
        fun checkCallback(callback: Map<String, String>, state: String, issuer: String): GateSignInResult? {
            callback["error"]?.let { return GateSignInResult.Refused(it, callback["error_description"]) }
            if (callback["state"] != state) return GateSignInResult.Failed("state mismatch")
            // RFC 9207: a response from another issuer is a mix-up, not ours to redeem.
            val iss = callback["iss"]
            if (iss != null && iss.trimEnd('/') != issuer.trimEnd('/')) return GateSignInResult.Failed("issuer mismatch")
            if (callback["code"].isNullOrEmpty()) return GateSignInResult.Failed("no code")
            return null
        }

        fun classify(refused: GateOAuthResult.Refused, clientId: String): GateSignInResult = when {
            refused.error == "invalid_client" || refused.error == "unauthorized_client" ->
                GateSignInResult.UnknownClient(clientId)
            refused.error == "invalid_request" && refused.description.orEmpty().contains("redirect_uri") ->
                GateSignInResult.RedirectNotRegistered(clientId)
            else -> GateSignInResult.Refused(refused.error, refused.description)
        }

        fun withQuery(endpoint: String, params: Map<String, String>): String =
            URLBuilder(Url(endpoint)).apply { params.forEach { (k, v) -> parameters.append(k, v) } }.buildString()
    }
}
