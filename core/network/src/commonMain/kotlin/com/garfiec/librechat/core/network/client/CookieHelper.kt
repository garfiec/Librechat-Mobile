package com.garfiec.librechat.core.network.client

import com.garfiec.librechat.core.model.auth.AuthCookies
import com.garfiec.librechat.core.model.auth.OpenIdMarkers
import io.ktor.http.Headers
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMessage
import io.ktor.http.setCookie
import kotlin.time.Clock

object CookieHelper {

    val authCookieNames: Set<String> = setOf("refreshToken", "token_provider", "openid_user_id")

    /** Cookie values stay opaque: in particular '+' and percent escapes must survive the handoff. */
    fun capture(cookieHeader: String?): AuthCookies? {
        val values = cookieHeader.orEmpty().split(';').associate { segment ->
            segment.trim().substringBefore('=') to segment.trim().substringAfter('=', "")
        }
        val refresh = values["refreshToken"]?.takeIf { it.isNotBlank() } ?: return null
        val marker = if (values["token_provider"] == "openid") {
            values["openid_user_id"]?.takeIf { it.isNotBlank() }?.let(::OpenIdMarkers) ?: return null
        } else {
            null
        }
        return AuthCookies(refresh, marker)
    }

    /** Merge only this response's explicit updates; absent Set-Cookie values retain their generation. */
    fun update(previous: AuthCookies, headers: Headers): AuthCookies {
        val updates = mutableMapOf<String, String?>()
        responseCookies(headers).forEach { parsed ->
            val name = parsed.name
            if (name in authCookieNames) {
                val value = parsed.value
                val expired = parsed.maxAge?.let { it <= 0 } ?: (
                    parsed.expires?.let { it.timestamp <= Clock.System.now().toEpochMilliseconds() } == true
                )
                updates[name] = value.takeUnless { it.isBlank() || expired }
            }
        }
        val refresh = if ("refreshToken" in updates) updates["refreshToken"] else previous.refreshToken
        check(!refresh.isNullOrBlank()) { "The server cleared the refresh token" }
        val provider = if ("token_provider" in updates) {
            updates["token_provider"]
        } else {
            if (previous.openId != null) "openid" else null
        }
        val marker = if (provider == "openid") {
            // A marker is bound to the refresh token's hash, so an old marker cannot follow rotation.
            check(refresh == previous.refreshToken || "openid_user_id" in updates) {
                "The server rotated an OpenID token without its session marker"
            }
            val value = if ("openid_user_id" in updates) updates["openid_user_id"] else previous.openId?.userIdCookie
            check(!value.isNullOrBlank()) { "The server's OpenID session marker was incomplete" }
            OpenIdMarkers(value)
        } else {
            null
        }
        return AuthCookies(refresh, marker)
    }

    /** One header, app values win, unrelated gateway segments survive. Null values remove owned names. */
    fun merge(headers: HeadersBuilder, values: Map<String, String?>) {
        val kept = headers.getAll(HttpHeaders.Cookie).orEmpty().flatMap(::cookieSegments)
            .filterNot { it.cookieName() in values }
        val added = values.mapNotNull { (name, value) -> value?.let { "$name=$it" } }
        headers.remove(HttpHeaders.Cookie)
        if (kept.isNotEmpty() || added.isNotEmpty()) headers.append(HttpHeaders.Cookie, (kept + added).joinToString("; "))
    }

    fun extractRefreshToken(headers: Headers): String? {
        return responseCookies(headers).firstOrNull { it.name == "refreshToken" }?.value
    }

    // Foundation combines repeated Set-Cookie fields. Ktor's parser separates them while
    // retaining commas inside Expires attributes and the default RAW cookie encoding.
    private fun responseCookies(responseHeaders: Headers) = object : HttpMessage {
        override val headers: Headers = responseHeaders
    }.setCookie()
}
