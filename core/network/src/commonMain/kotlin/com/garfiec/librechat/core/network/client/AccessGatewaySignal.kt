package com.garfiec.librechat.core.network.client

import io.ktor.http.Url

/**
 * The single definition of "an access gateway answered this, not the server" (issue #287).
 *
 * Must stay the only copy: the Ktor plugin and the iOS raw-socket SSE transport share nothing else,
 * and a second copy would let one platform drift and go quietly undetected.
 */
internal object AccessGatewaySignal {

    /** The `WWW-Authenticate` scheme Cloudflare Access answers a rejected request with. */
    const val SCHEME = "Cloudflare-Access"

    private const val PROXY_AUTH_REQUIRED = 407
    private const val UNAUTHORIZED = 401
    private val PORTAL_REDIRECTS = setOf(302, 303, 307)

    /**
     * Whether a `WWW-Authenticate` value is a gateway challenge. Substring, not equality: the header
     * is a comma-separated list and the scheme may carry parameters.
     */
    fun isGatewayChallenge(wwwAuthenticate: String?): Boolean =
        wwwAuthenticate?.contains(SCHEME, ignoreCase = true) == true

    /**
     * Whether a response is a forward-auth gate (Authelia, oauth2-proxy) turning the request away,
     * and if so, the gate's sign-in page (null when it named none, as with a 407).
     *
     * - **407**: LibreChat never sends it; Authelia answers an expired or revoked bearer with it.
     * - **401 with an absolute `Location` on another authority**: LibreChat's 401s carry no `Location`;
     *   a forward-auth gate answers a non-browser request this way.
     * - **302/303/307 to another authority with an `rd` return address**: the browser-shaped answer. The
     *   `rd` requirement is what keeps a presigned download redirect to object storage out.
     *
     * Never reads the body.
     */
    fun forwardAuthRejection(status: Int, location: String?, requestUrl: Url): Rejection? {
        if (status == PROXY_AUTH_REQUIRED) return Rejection(portalUrl = null)
        if (status != UNAUTHORIZED && status !in PORTAL_REDIRECTS) return null
        val target = location?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val portal = runCatching { Url(target) }.getOrNull() ?: return null
        // A relative or protocol-relative Location parses against nothing; only an absolute URL names
        // another authority with certainty.
        if (!target.startsWith("http://", ignoreCase = true) && !target.startsWith("https://", ignoreCase = true)) {
            return null
        }
        val sameAuthority = portal.host.equals(requestUrl.host, ignoreCase = true) &&
            portal.port == requestUrl.port &&
            portal.protocol.name.equals(requestUrl.protocol.name, ignoreCase = true)
        if (sameAuthority) return null
        if (status != UNAUTHORIZED && portal.parameters["rd"] == null) return null
        return Rejection(portalUrl = target)
    }

    data class Rejection(val portalUrl: String?)
}

/**
 * Marker for an engine-level exception that stands for a 407 the engine refused to hand back as a
 * response (OkHttp throws on a 407 when no proxy is in use). Matched on the cause chain.
 */
interface GateChallengeSignal
