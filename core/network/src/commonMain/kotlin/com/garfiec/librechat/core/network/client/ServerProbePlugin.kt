package com.garfiec.librechat.core.network.client

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.authority
import io.ktor.http.isSecure
import io.ktor.http.takeFrom
import io.ktor.util.AttributeKey

/** Marks the server check's `GET /api/config`; set by `ConfigApi.probeStartupConfig`. */
val ServerProbeKey = AttributeKey<Unit>("ServerProbe")

/**
 * The address being checked redirected the server check to [location]. Carries the whole Location
 * for the caller to derive the server's address from; never put it in a message, since a gateway's
 * URL can carry a `meta=` JWT.
 */
class ServerRedirectException(val location: Url) : Exception("The server redirected the request")

/**
 * Stops a [ServerProbeKey] request at its first redirect instead of following it, so the caller can
 * store the address the server lives at and re-check it under that address's own identity. Followed,
 * the hop would go out without the gateway headers (stripped across authorities) and every later
 * POST would go to the typed address, which Ktor never redirects.
 *
 * An https → http Location is left to Ktor, which refuses the downgrade.
 */
class ServerProbePlugin private constructor() {

    class Config

    companion object : HttpClientPlugin<Config, ServerProbePlugin> {
        override val key = AttributeKey<ServerProbePlugin>("ServerProbe")

        private val REDIRECT_STATUSES = setOf(301, 302, 303, 307, 308)

        override fun prepare(block: Config.() -> Unit): ServerProbePlugin = ServerProbePlugin()

        override fun install(plugin: ServerProbePlugin, scope: HttpClient) {
            scope.plugin(HttpSend).intercept { request ->
                val call = execute(request)
                val location = call.response.headers[HttpHeaders.Location]
                if (ServerProbeKey in request.attributes && location != null &&
                    call.response.status.value in REDIRECT_STATUSES
                ) {
                    val target = URLBuilder().takeFrom(request.url).takeFrom(location).build()
                    // A trailing-slash or query rewrite of the same URL names no new address; Ktor follows it.
                    val from = request.url.build()
                    val sameUrl = target.protocol == from.protocol &&
                        target.authority.equals(from.authority, ignoreCase = true) &&
                        target.encodedPath.trimEnd('/') == from.encodedPath.trimEnd('/')
                    if (!sameUrl && (target.protocol.isSecure() || !request.url.protocol.isSecure())) {
                        throw ServerRedirectException(target)
                    }
                }
                call
            }
        }
    }
}
