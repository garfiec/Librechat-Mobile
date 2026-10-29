package com.garfiec.librechat.core.model.auth

import kotlinx.serialization.Serializable

/** Server-issued, opaque marker selecting OpenID token reuse. Never derive it from a user id. */
@Serializable
data class OpenIdMarkers(val userIdCookie: String) {
    init { require(userIdCookie.isNotBlank()) }
    override fun toString(): String = "OpenIdMarkers(<redacted>)"
}

@Serializable
data class AuthCookies(val refreshToken: String, val openId: OpenIdMarkers? = null) {
    override fun toString(): String = "AuthCookies(refreshToken=<redacted>, openId=${openId != null})"
}

/** One secure-storage value: rotation cannot persist a token with another generation's marker. */
@Serializable
data class SessionCredentials(val accessToken: String, val cookies: AuthCookies, val version: Int = 1) {
    override fun toString(): String = "SessionCredentials(<redacted>, version=$version)"
}

/** The short-lived credentials a request may snapshot; refresh tokens stay out of request identity. */
data class RequestAuth(val bearer: String?, val openId: OpenIdMarkers? = null) {
    override fun toString(): String = "RequestAuth(bearer=<redacted>, openId=${openId != null})"
}
