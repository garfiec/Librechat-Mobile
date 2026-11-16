package com.garfiec.librechat.core.network.client

import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders

/**
 * What lets a request through an access gate (Authelia) in front of the server: a bearer token the
 * app obtained by signing in to the gate, or the gate's session cookie captured from its sign-in page.
 *
 * Keyed by server, like the gateway headers: it belongs to the deployment and is needed before any
 * account exists.
 */
sealed interface GateCredential {
    /** Sent as `Proxy-Authorization: Bearer`, because LibreChat owns `Authorization`. */
    data class Bearer(val accessToken: String) : GateCredential {
        override fun toString(): String = "Bearer(<redacted>)"
    }

    /** One or more `name=value` cookie segments, merged into the request's `Cookie` header. */
    data class Cookie(val cookies: String) : GateCredential {
        override fun toString(): String = "Cookie(<redacted>)"
    }
}

/**
 * The per-server gate credential store, as the HTTP clients see it. Implemented in `:core:data`.
 */
interface GateCredentialProvider {
    /**
     * The credential for [baseUrl]'s server, renewed first when it is about to expire; null when the
     * server has none. Must not throw for a blank or unparseable [baseUrl].
     */
    suspend fun credentialFor(baseUrl: String): GateCredential?

    /**
     * The gate refused [used]. Returns a replacement to retry with once, or null when there is none
     * (the store then records that the server needs a gate sign-in, unless the failure was transient).
     */
    suspend fun onRejected(baseUrl: String, used: GateCredential): GateCredential?
}

/** No gate credentials for any server. The default binding for tests and unwired clients. */
object NoGateCredentials : GateCredentialProvider {
    override suspend fun credentialFor(baseUrl: String): GateCredential? = null
    override suspend fun onRejected(baseUrl: String, used: GateCredential): GateCredential? = null
}

internal fun HeadersBuilder.applyGateCredential(credential: GateCredential) {
    when (credential) {
        is GateCredential.Bearer -> set(HttpHeaders.ProxyAuthorization, "Bearer ${credential.accessToken}")
        is GateCredential.Cookie -> applyCustomHeaders(mapOf(HttpHeaders.Cookie to credential.cookies))
    }
}

internal fun HeadersBuilder.stripGateCredential(credential: GateCredential) {
    when (credential) {
        is GateCredential.Bearer -> remove(HttpHeaders.ProxyAuthorization)
        is GateCredential.Cookie -> stripCustomHeaders(mapOf(HttpHeaders.Cookie to credential.cookies))
    }
}
